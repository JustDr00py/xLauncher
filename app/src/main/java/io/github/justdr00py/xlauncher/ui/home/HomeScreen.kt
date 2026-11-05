package io.github.justdr00py.xlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.justdr00py.xlauncher.HomeUiState
import io.github.justdr00py.xlauncher.LauncherViewModel
import io.github.justdr00py.xlauncher.apps.AppEntry
import io.github.justdr00py.xlauncher.prefs.ClockMode
import io.github.justdr00py.xlauncher.system.NetworkType
import io.github.justdr00py.xlauncher.system.SystemPage
import io.github.justdr00py.xlauncher.system.launchApp
import io.github.justdr00py.xlauncher.system.openAppInfo
import io.github.justdr00py.xlauncher.system.openSystemPage
import io.github.justdr00py.xlauncher.system.rememberNetworkType
import io.github.justdr00py.xlauncher.system.rememberNow
import io.github.justdr00py.xlauncher.system.requestUninstall
import io.github.justdr00py.xlauncher.ui.settings.SettingsPanel
import io.github.justdr00py.xlauncher.ui.theme.backdropAt
import io.github.justdr00py.xlauncher.update.UpdateState
import kotlinx.coroutines.flow.Flow
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EdgePadding = 48.dp
private const val GRID_COLUMNS = 5

@Composable
fun LauncherRoot(state: HomeUiState, vm: LauncherViewModel, homePresses: Flow<Unit>) {
    val context = LocalContext.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    val update by vm.updater.state.collectAsState()
    val updateAvailable = update is UpdateState.Available

    // Pressing Home while already home: close any overlay and jump back to the featured app.
    LaunchedEffect(homePresses) {
        homePresses.collect {
            settingsOpen = false
            menuFor = null
            listState.scrollToItem(0)
            withFrameNanos { }
            runCatching { heroFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            withFrameNanos { }
            runCatching { heroFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(state)
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                updateAvailable = updateAvailable,
                onAndroidSettings = { context.openSystemPage(SystemPage.All) },
                onLauncherSettings = { settingsOpen = true },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") {
                    HeroCard(
                        hero = state.hero,
                        loading = state.loading,
                        onClick = {
                            val hero = state.hero
                            if (hero != null) context.launchApp(hero) else settingsOpen = true
                        },
                        onLongClick = { state.hero?.let { menuFor = it.packageName } },
                        modifier = Modifier
                            .padding(horizontal = EdgePadding)
                            .focusRequester(heroFocus),
                    )
                }
                if (state.favorites.isNotEmpty()) {
                    item(key = "favorites") {
                        Section("Favorites") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = EdgePadding, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.focusRestorer(),
                            ) {
                                items(state.favorites, key = { it.packageName }) { app ->
                                    AppTile(
                                        entry = app,
                                        showLabel = state.settings.showLabels,
                                        onClick = { context.launchApp(app) },
                                        onLongClick = { menuFor = app.packageName },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.apps.isNotEmpty()) {
                    item(key = "apps-title") { SectionTitle("Apps", count = state.apps.size) }
                    items(state.apps.chunked(GRID_COLUMNS), key = { "row:" + it.first().packageName }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.padding(horizontal = EdgePadding),
                        ) {
                            row.forEach { app ->
                                AppTile(
                                    entry = app,
                                    showLabel = state.settings.showLabels,
                                    onClick = { context.launchApp(app) },
                                    onLongClick = { menuFor = app.packageName },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val menuEntry = menuFor?.let { pkg -> state.allApps.firstOrNull { it.packageName == pkg } }
    if (menuEntry != null) {
        val pkg = menuEntry.packageName
        val favIndex = state.favorites.indexOfFirst { it.packageName == pkg }
        val close = { menuFor = null }
        AppOptionsDialog(
            entry = menuEntry,
            isFavorite = favIndex >= 0,
            favoriteIndex = favIndex,
            favoriteCount = state.favorites.size,
            isHero = state.hero?.packageName == pkg,
            onDismiss = close,
            onOpen = { close(); context.launchApp(menuEntry) },
            onToggleFavorite = { close(); vm.toggleFavorite(pkg) },
            onMove = { delta -> close(); vm.moveFavorite(pkg, delta) },
            onSetHero = { close(); vm.setHero(pkg) },
            onHide = { close(); vm.setHidden(pkg, true) },
            onAppInfo = { close(); context.openAppInfo(pkg) },
            onUninstall = { close(); context.requestUninstall(pkg) },
        )
    }

    if (settingsOpen) {
        SettingsPanel(state = state, vm = vm, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun Backdrop(state: HomeUiState) {
    val backdrop = backdropAt(state.settings.background)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(backdrop.top, backdrop.bottom))),
    ) {
        val art = state.hero?.banner
        if (backdrop.usesHeroArt && art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.fillMaxSize().blur(48.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        // A soft accent glow in the top-left corner gives the flat gradient some depth.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                            center = center.copy(x = size.width * 0.12f, y = size.height * 0.05f),
                            radius = size.width * 0.55f,
                        ),
                    )
                },
        )
    }
}

@Composable
private fun TopBar(state: HomeUiState, updateAvailable: Boolean, onAndroidSettings: () -> Unit, onLauncherSettings: () -> Unit) {
    val now by rememberNow()
    val network by rememberNetworkType()
    val clockMode = state.settings.clockMode
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EdgePadding, end = EdgePadding, top = 28.dp, bottom = 16.dp),
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = when (network) {
                NetworkType.Ethernet -> Icons.Rounded.SettingsEthernet
                NetworkType.Wifi -> Icons.Rounded.Wifi
                NetworkType.Offline -> Icons.Rounded.WifiOff
            },
            contentDescription = network.name,
            tint = if (network == NetworkType.Offline) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        TopBarButton(Icons.Rounded.Settings, "Android settings", onAndroidSettings)
        Spacer(Modifier.width(8.dp))
        TopBarButton(Icons.Rounded.Tune, "Launcher settings", onLauncherSettings, badge = updateAvailable)
        if (clockMode != ClockMode.HIDDEN) {
            Spacer(Modifier.width(20.dp))
            val pattern = if (clockMode == ClockMode.H24) "HH:mm" else "h:mm"
            Text(text = now.format(DateTimeFormatter.ofPattern(pattern)), style = MaterialTheme.typography.headlineSmall)
            if (clockMode == ClockMode.H12) {
                Text(
                    text = " " + now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopBarButton(icon: ImageVector, description: String, onClick: () -> Unit, badge: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.align(Alignment.Center).size(22.dp))
        if (badge) {
            // Update available: a small accent dot in the corner.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun HeroCard(
    hero: AppEntry?,
    loading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, accent), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(accent.copy(alpha = 0.45f), 18.dp)),
    ) {
        val art = hero?.banner
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize().blur(36.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(28.dp),
        ) {
            if (hero != null) {
                ArtworkFrame(hero, width = 320.dp, height = 180.dp)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(320.dp, 180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                ) {
                    Icon(Icons.Rounded.Apps, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "FEATURED",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        hero != null -> hero.label
                        loading -> " "
                        else -> "Featured app not installed"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        hero != null -> "Press OK to open  ·  Hold OK for options"
                        loading -> " "
                        else -> "Press OK to choose a featured app"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SectionTitle(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = EdgePadding)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (count != null) {
            Text(
                text = "  $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
