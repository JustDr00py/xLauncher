package io.github.justdr00py.xlauncher.prefs

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val XFIN_PACKAGE = "io.github.justdr00py.xfin"

enum class ClockMode { H12, H24, HIDDEN }

@Immutable
data class LauncherSettings(
    /** Package shown in the large featured card at the top of the home screen. */
    val heroPackage: String = XFIN_PACKAGE,
    /** Opens the featured app once after each boot. */
    val autoLaunchHero: Boolean = false,
    val accent: Int = 0,
    val background: Int = 0,
    val clockMode: ClockMode = ClockMode.H12,
    val showLabels: Boolean = true,
    val hidden: Set<String> = emptySet(),
    /** Ordered package names, or null until the user first edits favorites (defaults apply then). */
    val favorites: List<String>? = null,
    /** Boot count we last auto-launched on, so it only happens once per boot. */
    val lastAutoLaunchBoot: Int = -1,
)

private val Context.dataStore by preferencesDataStore(name = "launcher")

class LauncherPrefs(context: Context) {

    private val store = context.dataStore

    val settings: Flow<LauncherSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun setHero(pkg: String) = edit { it[HERO] = pkg }
    suspend fun setAutoLaunch(on: Boolean) = edit { it[AUTO_LAUNCH] = on }
    suspend fun setAccent(index: Int) = edit { it[ACCENT] = index }
    suspend fun setBackground(index: Int) = edit { it[BACKGROUND] = index }
    suspend fun setClockMode(mode: ClockMode) = edit { it[CLOCK] = mode.name }
    suspend fun setShowLabels(on: Boolean) = edit { it[LABELS] = on }
    suspend fun setHidden(packages: Set<String>) = edit { it[HIDDEN] = packages }
    suspend fun setFavorites(packages: List<String>) = edit { it[FAVORITES] = packages.joinToString("\n") }
    suspend fun setLastAutoLaunchBoot(boot: Int) = edit { it[LAST_BOOT] = boot }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun Preferences.toSettings() = LauncherSettings(
        heroPackage = this[HERO] ?: XFIN_PACKAGE,
        autoLaunchHero = this[AUTO_LAUNCH] ?: false,
        accent = this[ACCENT] ?: 0,
        background = this[BACKGROUND] ?: 0,
        clockMode = this[CLOCK]?.let { runCatching { ClockMode.valueOf(it) }.getOrNull() } ?: ClockMode.H12,
        showLabels = this[LABELS] ?: true,
        hidden = this[HIDDEN] ?: emptySet(),
        favorites = this[FAVORITES]?.split("\n")?.filter { it.isNotBlank() },
        lastAutoLaunchBoot = this[LAST_BOOT] ?: -1,
    )

    private companion object {
        val HERO = stringPreferencesKey("hero_package")
        val AUTO_LAUNCH = booleanPreferencesKey("auto_launch")
        val ACCENT = intPreferencesKey("accent")
        val BACKGROUND = intPreferencesKey("background")
        val CLOCK = stringPreferencesKey("clock_mode")
        val LABELS = booleanPreferencesKey("show_labels")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val FAVORITES = stringPreferencesKey("favorites")
        val LAST_BOOT = intPreferencesKey("last_boot")
    }
}
