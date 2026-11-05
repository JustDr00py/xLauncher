package io.github.justdr00py.xlauncher.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.collectAsState
import io.github.justdr00py.xlauncher.update.UpdateState
import io.github.justdr00py.xlauncher.update.Updater
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.github.justdr00py.xlauncher.BuildConfig
import io.github.justdr00py.xlauncher.HomeUiState
import io.github.justdr00py.xlauncher.LauncherViewModel
import io.github.justdr00py.xlauncher.apps.AppEntry
import io.github.justdr00py.xlauncher.prefs.ClockMode
import io.github.justdr00py.xlauncher.system.SystemPage
import io.github.justdr00py.xlauncher.system.openSystemPage
import io.github.justdr00py.xlauncher.ui.home.ArtworkFrame
import io.github.justdr00py.xlauncher.ui.home.XDialog
import io.github.justdr00py.xlauncher.ui.theme.Accents
import io.github.justdr00py.xlauncher.ui.theme.Backgrounds
import io.github.justdr00py.xlauncher.ui.theme.accentAt
import io.github.justdr00py.xlauncher.ui.theme.backdropAt

private enum class Picker { Hero, Hidden }

/** Side panel with every launcher option on one short list, opened from the top bar. */
@Composable
fun SettingsPanel(state: HomeUiState, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = state.settings
    var picker by remember { mutableStateOf<Picker?>(null) }
    val firstItem = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val update by vm.updater.state.collectAsState()
    val updateAvailable = update is UpdateState.Available

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(460.dp),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }

                        // A pending update goes first so it gets focus when the panel opens.
                        if (updateAvailable) {
                            item {
                                UpdateRow(update, vm.updater, Modifier.padding(top = 16.dp).focusRequester(firstItem))
                            }
                        }

                        item { Header("Home") }
                        item {
                            SettingRow(
                                icon = Icons.Rounded.Whatshot,
                                title = "Featured app",
                                value = state.hero?.label ?: "Not installed",
                                onClick = { picker = Picker.Hero },
                                modifier = if (updateAvailable) Modifier else Modifier.focusRequester(firstItem),
                            )
                        }
                        item {
                            SettingToggle(
                                icon = Icons.Rounded.PowerSettingsNew,
                                title = "Open featured app at startup",
                                value = "Once each time the device restarts",
                                checked = s.autoLaunchHero,
                                onToggle = vm::setAutoLaunch,
                            )
                        }
                        item {
                            SettingRow(
                                icon = Icons.Rounded.VisibilityOff,
                                title = "Hidden apps",
                                value = if (s.hidden.isEmpty()) "None" else "${s.hidden.size} hidden",
                                onClick = { picker = Picker.Hidden },
                            )
                        }

                        item { Header("Appearance") }
                        item {
                            val accent = accentAt(s.accent)
                            SettingRow(
                                icon = Icons.Rounded.Palette,
                                title = "Accent color",
                                value = accent.name,
                                onClick = { vm.setAccent((s.accent + 1).mod(Accents.size)) },
                                trailing = {
                                    Box(
                                        Modifier
                                            .size(20.dp)
                                            .background(accent.color, CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                    )
                                },
                            )
                        }
                        item {
                            SettingRow(
                                icon = Icons.Rounded.Wallpaper,
                                title = "Background",
                                value = backdropAt(s.background).name,
                                onClick = { vm.setBackground((s.background + 1).mod(Backgrounds.size)) },
                            )
                        }
                        item {
                            SettingRow(
                                icon = Icons.Rounded.AccessTime,
                                title = "Clock",
                                value = when (s.clockMode) {
                                    ClockMode.H12 -> "12-hour"
                                    ClockMode.H24 -> "24-hour"
                                    ClockMode.HIDDEN -> "Hidden"
                                },
                                onClick = {
                                    vm.setClockMode(ClockMode.entries[(s.clockMode.ordinal + 1) % ClockMode.entries.size])
                                },
                            )
                        }
                        item {
                            SettingToggle(
                                icon = Icons.AutoMirrored.Rounded.ShortText,
                                title = "Show app names",
                                value = "Under each tile",
                                checked = s.showLabels,
                                onToggle = vm::setShowLabels,
                            )
                        }

                        item { Header("Device") }
                        items(SystemPage.entries) { page ->
                            SettingRow(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                title = page.title,
                                value = null,
                                onClick = { context.openSystemPage(page) },
                            )
                        }

                        item { Header("About") }
                        item { UpdateRow(update, vm.updater) }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstItem.requestFocus() }
        }
    }

    when (picker) {
        Picker.Hero -> AppPicker(
            title = "Featured app",
            subtitle = "Shown in the large card at the top of Home",
            apps = state.allApps,
            isSelected = { it.packageName == s.heroPackage },
            onPick = { vm.setHero(it.packageName); picker = null },
            onDismiss = { picker = null },
        )
        Picker.Hidden -> AppPicker(
            title = "Hidden apps",
            subtitle = "Checked apps are hidden from Home",
            apps = state.allApps,
            isSelected = { it.packageName in s.hidden },
            onPick = { vm.setHidden(it.packageName, it.packageName !in s.hidden) },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

/** One row that shows the update state and does the next sensible thing when pressed. */
@Composable
private fun UpdateRow(state: UpdateState, updater: Updater, modifier: Modifier = Modifier) {
    val current = "xLauncher ${BuildConfig.VERSION_NAME}"
    val (title, value) = when (state) {
        UpdateState.Idle -> current to "Press OK to check for updates"
        UpdateState.Checking -> current to "Checking GitHub…"
        is UpdateState.UpToDate -> current to "Up to date · checked ${ago(state.checkedAt)}"
        is UpdateState.Available -> "Update to ${state.release.version}" to "Press OK to download and install"
        is UpdateState.Downloading ->
            "Downloading ${state.release.version}" to "${(state.progress * 100).toInt()}%"
        is UpdateState.Installing -> "Installing ${state.release.version}" to "Confirm on the next screen"
        is UpdateState.Failed -> current to state.message
    }
    SettingRow(
        icon = if (state is UpdateState.Available) Icons.Rounded.SystemUpdate else Icons.Rounded.Info,
        title = title,
        value = value,
        modifier = modifier,
        onClick = {
            when (state) {
                is UpdateState.Available -> updater.install(state.release)
                is UpdateState.Failed -> state.release?.let(updater::install) ?: updater.check()
                UpdateState.Idle, is UpdateState.UpToDate -> updater.check()
                else -> Unit
            }
        },
        trailing = if (state is UpdateState.Available) {
            { Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
        } else {
            null
        },
    )
}

private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 48 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(ListItemDefaults.IconSize)) },
        trailingContent = trailing,
    )
}

@Composable
private fun SettingToggle(icon: ImageVector, title: String, value: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    SettingRow(
        icon = icon,
        title = title,
        value = value,
        onClick = { onToggle(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun AppPicker(
    title: String,
    subtitle: String,
    apps: List<AppEntry>,
    isSelected: (AppEntry) -> Boolean,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    XDialog(title = title, subtitle = subtitle, onDismiss = onDismiss, width = 520.dp) {
        LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(apps, key = { it.packageName }) { app ->
                val selected = isSelected(app)
                ListItem(
                    selected = selected,
                    onClick = { onPick(app) },
                    headlineContent = { Text(app.label) },
                    leadingContent = { ArtworkFrame(app, width = 64.dp, height = 36.dp, corner = 6.dp) },
                    trailingContent = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
