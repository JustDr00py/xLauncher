package io.github.justdr00py.xlauncher

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.justdr00py.xlauncher.apps.AppEntry
import io.github.justdr00py.xlauncher.prefs.ClockMode
import io.github.justdr00py.xlauncher.prefs.LauncherSettings
import io.github.justdr00py.xlauncher.prefs.XFIN_PACKAGE
import io.github.justdr00py.xlauncher.system.bootCount
import io.github.justdr00py.xlauncher.update.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val settings: LauncherSettings = LauncherSettings(),
    /** The featured app, or null when the chosen package isn't installed. */
    val hero: AppEntry? = null,
    val favorites: List<AppEntry> = emptyList(),
    /** Visible apps, sorted by name. */
    val apps: List<AppEntry> = emptyList(),
    /** Every installed app including hidden ones, for the settings pickers. */
    val allApps: List<AppEntry> = emptyList(),
)

/** Streaming apps placed in Favorites on first run, if they are installed. Order is preserved. */
private val DEFAULT_FAVORITES = listOf(
    XFIN_PACKAGE,
    "org.jellyfin.androidtv",
    "com.plexapp.android",
    "com.google.android.youtube.tv",
    "com.netflix.ninja",
    "com.disney.disneyplus",
    "com.amazon.amazonvideo.livingroom",
    "com.hulu.livingroomplus",
    "com.wbd.stream",
    "com.google.android.youtube.tvmusic",
    "com.spotify.tv.android",
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app as XLauncherApp
    private val prefs = graph.prefs
    val updaters = graph.updaters

    /** True when any app has an update waiting; drives the dot on the settings button. */
    val updateAvailable: StateFlow<Boolean> =
        combine(updaters.map { it.state }) { states -> states.any { it is UpdateState.Available } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val state: StateFlow<HomeUiState> =
        combine(graph.apps.apps, prefs.settings) { apps, settings -> build(apps, settings) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    private fun build(apps: List<AppEntry>?, s: LauncherSettings): HomeUiState {
        if (apps == null) return HomeUiState(loading = true, settings = s)
        val byPackage = apps.associateBy { it.packageName }
        val visible = apps.filter { it.packageName !in s.hidden }
        return HomeUiState(
            loading = false,
            settings = s,
            hero = byPackage[s.heroPackage],
            favorites = favoritePackages(s, byPackage.keys)
                .filter { it !in s.hidden }
                .mapNotNull(byPackage::get),
            apps = visible,
            allApps = apps,
        )
    }

    /** The saved favorites, or (before the user has edited them) installed defaults minus the hero. */
    private fun favoritePackages(s: LauncherSettings, installed: Set<String>): List<String> =
        s.favorites ?: DEFAULT_FAVORITES.filter { it in installed && it != s.heroPackage }

    private fun currentFavorites(): List<String> = state.value.favorites.map { it.packageName }

    fun toggleFavorite(pkg: String) = viewModelScope.launch {
        val current = currentFavorites()
        prefs.setFavorites(if (pkg in current) current - pkg else current + pkg)
    }

    /** Moves a favorite one slot left (-1) or right (+1). */
    fun moveFavorite(pkg: String, delta: Int) = viewModelScope.launch {
        val list = currentFavorites().toMutableList()
        val from = list.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in list.indices) return@launch
        list.add(to, list.removeAt(from))
        prefs.setFavorites(list)
    }

    fun setHero(pkg: String) = viewModelScope.launch { prefs.setHero(pkg) }

    fun setHidden(pkg: String, hidden: Boolean) = viewModelScope.launch {
        val set = state.value.settings.hidden
        prefs.setHidden(if (hidden) set + pkg else set - pkg)
    }

    fun setAutoLaunch(on: Boolean) = viewModelScope.launch {
        // Count the current boot as already handled, so turning this on doesn't fire until the next restart.
        if (on) prefs.setLastAutoLaunchBoot(getApplication<Application>().bootCount())
        prefs.setAutoLaunch(on)
    }
    fun setAccent(index: Int) = viewModelScope.launch { prefs.setAccent(index) }
    fun setBackground(index: Int) = viewModelScope.launch { prefs.setBackground(index) }
    fun setClockMode(mode: ClockMode) = viewModelScope.launch { prefs.setClockMode(mode) }
    fun setShowLabels(on: Boolean) = viewModelScope.launch { prefs.setShowLabels(on) }
}
