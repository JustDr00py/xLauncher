package io.github.justdr00py.xlauncher.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One launchable app. Artwork is decoded once, at tile size, and reused until the app updates. */
@Immutable
data class AppEntry(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    /** True when the app declares a TV (leanback) launcher activity. */
    val isTvApp: Boolean,
    val isSystem: Boolean,
    /** 16:9 TV banner, or null when the app only ships an icon. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

/**
 * Keeps the list of launchable apps up to date. It loads once at start and reloads whenever a
 * package is installed, removed or updated.
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val pm: PackageManager = context.packageManager
    private val mutex = Mutex()

    /** Decoded entries keyed by "component@lastUpdateTime", so unchanged apps skip decoding. */
    private val cache = HashMap<String, AppEntry>()

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Every launchable app sorted by label, or null while the first load is running. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Package broadcasts come from the system, which can always reach a non-exported receiver.
        context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { _apps.value = load() }
        }
    }

    private fun load(): List<AppEntry> {
        val self = context.packageName
        // A TV activity wins over a phone-style one from the same package.
        val byPackage = LinkedHashMap<String, Pair<ResolveInfo, Boolean>>()
        for (info in query(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            byPackage.putIfAbsent(info.activityInfo.packageName, info to true)
        }
        for (info in query(Intent.CATEGORY_LAUNCHER)) {
            byPackage.putIfAbsent(info.activityInfo.packageName, info to false)
        }
        byPackage.remove(self)

        val live = HashSet<String>()
        val result = byPackage.values.mapNotNull { (info, isTv) ->
            runCatching { entryFor(info, isTv) }.getOrNull()?.let { (key, entry) ->
                live += key
                entry
            }
        }
        cache.keys.retainAll(live)
        return result.sortedBy { it.label.lowercase() }
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category),
            PackageManager.ResolveInfoFlags.of(0),
        )

    private fun entryFor(info: ResolveInfo, isTv: Boolean): Pair<String, AppEntry> {
        val activity = info.activityInfo
        val appInfo = activity.applicationInfo
        val updated = runCatching {
            pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
        }.getOrDefault(0L)
        val key = "${activity.packageName}/${activity.name}@$updated"
        cache[key]?.let { return key to it }

        val banner = (activity.loadBanner(pm) ?: appInfo.loadBanner(pm))?.render(BANNER_MAX_W, BANNER_MAX_H)
        val entry = AppEntry(
            packageName = activity.packageName,
            component = ComponentName(activity.packageName, activity.name),
            label = info.loadLabel(pm).toString().trim().ifEmpty { activity.packageName },
            isTvApp = isTv,
            isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            banner = banner,
            icon = if (banner == null) info.loadIcon(pm)?.render(ICON_SIZE, ICON_SIZE) else null,
        )
        cache[key] = entry
        return key to entry
    }

    private companion object {
        // Pixels: enough for the hero card on a 1080p UI without wasting memory on 4K-sized art.
        const val BANNER_MAX_W = 640
        const val BANNER_MAX_H = 360
        const val ICON_SIZE = 192
    }
}

/** Rasterises a drawable once, scaled down to fit inside [maxW] x [maxH] and keeping its aspect ratio. */
private fun Drawable.render(maxW: Int, maxH: Int): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxW
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxH
    val scale = minOf(maxW.toFloat() / w, maxH.toFloat() / h, 1f)
    val bitmap = toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
}
