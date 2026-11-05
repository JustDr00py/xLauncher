package io.github.justdr00py.xlauncher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import io.github.justdr00py.xlauncher.BuildConfig
import io.github.justdr00py.xlauncher.XLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An app that can be updated from a GitHub repo's releases. */
data class UpdateTarget(
    val label: String,
    val packageName: String,
    val repo: String,
    /** Also consider releases marked "Pre-release" (xFIN ships those). */
    val includePrereleases: Boolean,
)

/** A newer release found on GitHub. */
data class Release(val version: String, val apkUrl: String, val apkSize: Long, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    /** Waiting on Android's install prompt. */
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Updates one app from GitHub Releases. It only talks to the network when asked: when the user
 * presses its Settings row, or for the quiet check at most once a day. Installing always goes
 * through Android's own confirmation prompt.
 */
class Updater(
    private val context: Context,
    private val scope: CoroutineScope,
    val target: UpdateTarget,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val lastCheckKey = "last_check:${target.packageName}"
    private var job: Job? = null

    private val isSelf get() = target.packageName == context.packageName

    /** Installed version name, or null when the app isn't installed. */
    fun installedVersion(): String? =
        if (isSelf) {
            BuildConfig.VERSION_NAME
        } else {
            runCatching {
                context.packageManager.getPackageInfo(target.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            }.getOrNull()
        }

    /** Checks GitHub if the last check was more than a day ago. Stays silent on failure. */
    fun checkDaily() {
        val last = prefs.getLong(lastCheckKey, 0L)
        if (System.currentTimeMillis() - last < DAY_MS) {
            if (_state.value == UpdateState.Idle && last > 0) _state.value = UpdateState.UpToDate(last)
            return
        }
        check(quiet = true)
    }

    fun check(quiet: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!quiet) _state.value = UpdateState.Checking
            _state.value = try {
                val release = fetchNewest()
                val now = System.currentTimeMillis()
                prefs.edit().putLong(lastCheckKey, now).apply()
                val installed = installedVersion()
                if (release != null && (installed == null || isNewer(release.version, installed))) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate(now)
                }
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check: ${e.shortMessage()}")
            }
        }
    }

    /** Downloads [release] and hands it to Android's installer. */
    fun install(release: Release) {
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Failed("Allow xLauncher to install apps, then press Update again", release)
            openInstallPermissionSettings()
            return
        }
        job = scope.launch {
            try {
                val apk = download(release)
                verify(apk)
                _state.value = UpdateState.Installing(release)
                commit(apk)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.shortMessage(), release)
            }
        }
    }

    /** Called by [InstallResultReceiver] when the system installer finishes or is cancelled. */
    internal fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("The update is signed with a different key", release)
            else -> UpdateState.Failed(message ?: "Install failed", release)
        }
    }

    /** The highest-versioned release that has an APK attached. */
    private suspend fun fetchNewest(): Release? = withContext(Dispatchers.IO) {
        val base = "https://api.github.com/repos/${target.repo}/releases"
        val releases: List<JSONObject> = if (target.includePrereleases) {
            val text = open("$base?per_page=20", JSON).inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            (0 until array.length()).map { array.getJSONObject(it) }.filterNot { it.optBoolean("draft") }
        } else {
            listOf(open("$base/latest", JSON).inputStream.bufferedReader().use { JSONObject(it.readText()) })
        }
        releases.mapNotNull { it.toRelease() }.maxWithOrNull { a, b ->
            when {
                isNewer(a.version, b.version) -> 1
                isNewer(b.version, a.version) -> -1
                else -> 0
            }
        }
    }

    private fun JSONObject.toRelease(): Release? {
        val assets = optJSONArray("assets") ?: return null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return null
        return Release(
            version = getString("tag_name").removePrefix("v"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
            notes = optString("body").trim(),
        )
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates/${target.packageName}").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "${target.label}-${release.version}.apk")
        val conn = open(release.apkUrl, "application/octet-stream")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        _state.value = UpdateState.Downloading(release, 0f)
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    val pct = if (total > 0) (done * 100 / total).toInt().coerceAtMost(100) else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        _state.value = UpdateState.Downloading(release, pct / 100f)
                    }
                }
            }
        }
        out
    }

    /** Rejects anything that isn't the right app, newer, and signed with the installed copy's key. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val incoming = pm.getPackageArchiveInfo(apk.path, flags) ?: error("Downloaded file isn't a valid APK")
        check(incoming.packageName == target.packageName) { "Downloaded APK is a different app" }
        // Not installed yet: nothing to compare against, so this is a fresh install.
        val installed = runCatching { pm.getPackageInfo(target.packageName, flags) }.getOrNull() ?: return
        check(incoming.longVersionCode > installed.longVersionCode) { "Downloaded APK isn't newer" }
        val mine = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val theirs = incoming.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        check(mine != null && mine == theirs) { "The update is signed with a different key" }
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(target.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, InstallResultReceiver::class.java)
                    .putExtra(InstallResultReceiver.EXTRA_TARGET, target.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "xLauncher/${BuildConfig.VERSION_NAME}")
            if (responseCode !in 200..299) {
                error(if (responseCode == 404) "no releases published yet" else "HTTP $responseCode")
            }
        }

    companion object {
        private const val JSON = "application/vnd.github+json"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Compares dotted versions numerically, so 1.10.0 > 1.9.2. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

private fun Exception.shortMessage() = message?.take(80) ?: javaClass.simpleName

/** Receives the installer's result. On TV the user always has to confirm, so it launches that prompt. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val app = context.applicationContext as? XLauncherApp ?: return
        val target = intent.getStringExtra(EXTRA_TARGET)
        app.updaters.firstOrNull { it.target.packageName == target }
            ?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }

    companion object {
        const val EXTRA_TARGET = "target"
    }
}
