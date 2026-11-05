package io.github.justdr00py.xlauncher.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.justdr00py.xlauncher.apps.AppEntry

val TileWidth = 156.dp
val TileHeight = 88.dp
private val TileShape = RoundedCornerShape(12.dp)

@Composable
fun AppTile(
    entry: AppEntry,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Column(modifier.width(TileWidth)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(TileWidth, TileHeight),
            shape = ClickableSurfaceDefaults.shape(TileShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceVariant,
                focusedContainerColor = colors.surfaceVariant,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = TileShape),
            ),
            interactionSource = interaction,
        ) {
            AppArtwork(entry)
        }
        if (showLabel) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp, start = 2.dp),
            )
        }
    }
}

/** The app's TV banner, or its icon (and name) on a tinted card when it has no banner. */
@Composable
fun AppArtwork(entry: AppEntry, modifier: Modifier = Modifier, iconSize: Dp = 40.dp, showName: Boolean = true) {
    val banner = entry.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = entry.label,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), MaterialTheme.colorScheme.surfaceVariant)),
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entry.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(iconSize)) }
        if (showName) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Artwork clipped to a rounded rectangle of a fixed size (hero card, picker rows). */
@Composable
fun ArtworkFrame(entry: AppEntry, width: Dp, height: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AppArtwork(entry, iconSize = height * 0.5f, showName = false)
    }
}

/** Centered dialog card used for menus and pickers. Focus lands on its first item when it opens. */
@Composable
fun XDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    width: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focus = remember { FocusRequester() }
        // A menu opened by holding OK would otherwise receive that same held button: its repeats
        // and release would click the first item. Ignore OK until it is pressed again from scratch.
        var armed by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier
                .width(width)
                .onPreviewKeyEvent { event ->
                    val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                    when {
                        !isSelect || armed -> false
                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                            armed = true
                            false
                        }
                        else -> true
                    }
                },
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.focusRequester(focus), content = content)
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(text) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(ListItemDefaults.IconSize)) },
    )
}

@Composable
fun AppOptionsDialog(
    entry: AppEntry,
    isFavorite: Boolean,
    favoriteIndex: Int,
    favoriteCount: Int,
    isHero: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: (Int) -> Unit,
    onSetHero: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    XDialog(title = entry.label, onDismiss = onDismiss) {
        MenuItem(Icons.Rounded.PlayArrow, "Open", onOpen)
        if (isFavorite) {
            MenuItem(Icons.Rounded.StarBorder, "Remove from Favorites", onToggleFavorite)
            if (favoriteIndex > 0) MenuItem(Icons.AutoMirrored.Rounded.ArrowBack, "Move left", { onMove(-1) })
            if (favoriteIndex in 0 until favoriteCount - 1) {
                MenuItem(Icons.AutoMirrored.Rounded.ArrowForward, "Move right", { onMove(1) })
            }
        } else {
            MenuItem(Icons.Rounded.Star, "Add to Favorites", onToggleFavorite)
        }
        if (!isHero) MenuItem(Icons.Rounded.Whatshot, "Make featured app", onSetHero, "Shows it in the large card on top")
        MenuItem(Icons.Rounded.VisibilityOff, "Hide from home", onHide, "Bring it back in Settings › Hidden apps")
        MenuItem(Icons.Rounded.Info, "App info", onAppInfo)
        if (!entry.isSystem) MenuItem(Icons.Rounded.Delete, "Uninstall", onUninstall)
    }
}
