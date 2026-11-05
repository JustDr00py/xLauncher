package io.github.justdr00py.xlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justdr00py.xlauncher.system.bootCount
import io.github.justdr00py.xlauncher.system.launchPackage
import io.github.justdr00py.xlauncher.ui.home.LauncherRoot
import io.github.justdr00py.xlauncher.ui.theme.XTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {

    /** Fires when Home is pressed while the launcher is already running. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home is the bottom of the stack: Back has nowhere to go, so it does nothing here.
        onBackPressedDispatcher.addCallback(this) { }
        if (savedInstanceState == null) maybeAutoLaunchHero()

        setContent {
            val vm: LauncherViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            XTheme(accentIndex = state.settings.accent) {
                LauncherRoot(state = state, vm = vm, homePresses = homePresses)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as XLauncherApp).updater.checkDaily()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homePresses.tryEmit(Unit)
    }

    /** Opens the featured app once per boot when that option is on. */
    private fun maybeAutoLaunchHero() {
        val prefs = (application as XLauncherApp).prefs
        lifecycleScope.launch {
            val settings = prefs.settings.first()
            if (!settings.autoLaunchHero) return@launch
            val boot = bootCount()
            if (boot == settings.lastAutoLaunchBoot) return@launch
            prefs.setLastAutoLaunchBoot(boot)
            launchPackage(settings.heroPackage)
        }
    }
}
