package io.github.justdr00py.xlauncher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.justdr00py.xlauncher.apps.AppEntry
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

fun Context.launchApp(entry: AppEntry) {
    val category = if (entry.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(category)
        .setComponent(entry.component)
        .addFlags(LAUNCH_FLAGS)
    safeStart(intent, "Couldn't open ${entry.label}")
}

/** Opens [pkg] by package name, which is used for the boot auto-launch before the app list is loaded. */
fun Context.launchPackage(pkg: String): Boolean {
    val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        ?: packageManager.getLaunchIntentForPackage(pkg)
        ?: return false
    return runCatching { startActivity(intent.addFlags(LAUNCH_FLAGS)) }.isSuccess
}

fun Context.openAppInfo(pkg: String) = safeStart(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

fun Context.requestUninstall(pkg: String) = safeStart(
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

/** Android TV settings pages that are useful to reach straight from the launcher. */
enum class SystemPage(val title: String, val action: String) {
    Network("Network & Internet", Settings.ACTION_WIFI_SETTINGS),
    Display("Display & Sound", Settings.ACTION_DISPLAY_SETTINGS),
    Apps("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    About("About this device", Settings.ACTION_DEVICE_INFO_SETTINGS),
    All("All Android settings", Settings.ACTION_SETTINGS),
}

/** Opens a settings page, falling back to the main settings screen if the device lacks that page. */
fun Context.openSystemPage(page: SystemPage) {
    val ok = runCatching { startActivity(Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok && page != SystemPage.All) openSystemPage(SystemPage.All)
}

private fun Context.safeStart(intent: Intent, failure: String = "Not available on this device") {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failure, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, failure, Toast.LENGTH_SHORT).show()
    }
}

fun Context.bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

enum class NetworkType { Ethernet, Wifi, Offline }

/** Current default network type. It updates live as the connection changes. */
@Composable
fun rememberNetworkType(): State<NetworkType> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NetworkType.Offline) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    NetworkType.Ethernet
                } else {
                    NetworkType.Wifi
                }
            }

            override fun onLost(network: Network) {
                state.value = NetworkType.Offline
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return state
}

/** The current time, ticking exactly on each minute boundary. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(60_000L - System.currentTimeMillis() % 60_000L)
    }
}
