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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
 * Self-updater backed by GitHub Releases. It only talks to the network when asked: when the user
 * presses the Settings row, or for the quiet check at most once a day. Installing always goes
 * through Android's own confirmation prompt.
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private var job: Job? = null

    /** Checks GitHub if the last check was more than a day ago. Stays silent on failure. */
    fun checkDaily() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
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
                val release = fetchLatest()
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                if (release != null && isNewer(release.version, BuildConfig.VERSION_NAME)) {
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

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val conn = open(LATEST_RELEASE_URL, "application/vnd.github+json")
        val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null
        Release(
            version = json.getString("tag_name").removePrefix("v"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
            notes = json.optString("body").trim(),
        )
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "xlauncher-${release.version}.apk")
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

    /** Rejects anything that isn't a newer xLauncher signed with the same key, before bothering Android. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val incoming = pm.getPackageArchiveInfo(apk.path, flags) ?: error("Downloaded file isn't a valid APK")
        check(incoming.packageName == context.packageName) { "Downloaded APK is a different app" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        check(incoming.longVersionCode > installed.longVersionCode) { "Downloaded APK isn't newer" }
        val mine = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val theirs = incoming.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        check(mine != null && mine == theirs) { "The update is signed with a different key" }
    }

    private suspend fun commit(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
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
                Intent(context, InstallResultReceiver::class.java),
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
        const val REPO = "JustDr00py/xLauncher"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
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
        app.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
