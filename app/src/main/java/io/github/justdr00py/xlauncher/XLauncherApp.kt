package io.github.justdr00py.xlauncher

import android.app.Application
import io.github.justdr00py.xlauncher.apps.AppRepository
import io.github.justdr00py.xlauncher.prefs.LauncherPrefs
import io.github.justdr00py.xlauncher.prefs.XFIN_PACKAGE
import io.github.justdr00py.xlauncher.update.UpdateTarget
import io.github.justdr00py.xlauncher.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the two process-wide singletons. Plain fields instead of a DI framework keep startup fast. */
class XLauncherApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var prefs: LauncherPrefs
        private set
    lateinit var apps: AppRepository
        private set
    /** xLauncher itself first, then xFIN. Both update from their GitHub Releases. */
    lateinit var updaters: List<Updater>
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = LauncherPrefs(this)
        apps = AppRepository(this, scope).also { it.start() }
        updaters = listOf(
            Updater(this, scope, UpdateTarget("xLauncher", packageName, "JustDr00py/xLauncher", includePrereleases = false)),
            Updater(this, scope, UpdateTarget("xFIN", XFIN_PACKAGE, "JustDr00py/xFIN", includePrereleases = true)),
        )
    }
}
