// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Undo

/**
 * Card action buttons shared by the thread and category home screens.
 *
 * - [CircularActionButton]: save / restore action floating above the add
 *   [top.yukonga.miuix.kmp.basic.FloatingActionButton]; matches the Miuix FAB
 *   defaults (60dp circle, 24dp icon) so the three buttons read as one family.
 * - [CardActionButton]: delete (stop) / restore action inside cards. Opaque
 *   filled container from the Monet palette (primary for both states) with its
 *   matching "on" content color, so the button stays readable and never blends
 *   into the primary/secondary container card background.
 * - [HomeFabStack]: the bottom-anchored FAB overlay shared by both home
 *   screens — normal mode (add + dirty save/restore) and export mode
 *   (exit + share) occupy the same slot via opacity/translation animation.
 */
@Composable
internal fun CircularActionButton(icon: ImageVector, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Where an opaque card action button sits (container + content color pair). */
internal data class CardActionColors(val container: Color, val content: Color)

/**
 * Monet-derived opaque colors for in-card edit / delete buttons. Uses the
 * tertiary container (distinct from the card's primary/secondary container)
 * for edit and the error container for delete; falls back to the card content
 * color for the delete tint when the theme has no error container.
 */
/** Accent pair for the delete (stop) action — uses the theme primary, not error red. */
@Composable
internal fun cardDeleteActionColors(): CardActionColors = CardActionColors(
    container = MiuixTheme.colorScheme.primary,
    content = MiuixTheme.colorScheme.onPrimary,
)

/** Dimmed pair for the disabled (stopped) state of a card action. */
@Composable
internal fun cardDisabledActionColors(content: Color): CardActionColors = CardActionColors(
    container = content.copy(alpha = 0.12f),
    content = content.copy(alpha = 0.45f),
)

@Composable
internal fun CardActionButton(
    icon: ImageVector,
    colors: CardActionColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.container, CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Circular icon-only button for top-bar actions (e.g. the search toggle). */
@Composable
internal fun CircularIconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun HomeFabStack(
    exportMode: Boolean,
    dirty: Boolean,
    addLabel: String,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onAdd: () -> Unit,
    onExitExport: () -> Unit,
    onExportSelected: () -> Unit,
) {
    // Constant-size bottom-anchored overlay: the export stack and the normal
    // stack occupy the same slot; only opacity + translation animate, so
    // nothing jumps when entering/leaving export mode.
    Box(contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = !exportMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = dirty,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularActionButton(
                            icon = MiuixIcons.Download,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            bg = MiuixTheme.colorScheme.primary,
                            onClick = onSave,
                        )
                        CircularActionButton(
                            icon = MiuixIcons.Undo,
                            tint = MiuixTheme.colorScheme.onSecondaryContainer,
                            bg = MiuixTheme.colorScheme.secondaryContainer,
                            onClick = onRestore,
                        )
                    }
                }
                FloatingActionButton(onClick = onAdd) {
                    Icon(MiuixIcons.Add, contentDescription = addLabel)
                }
            }
        }
        AnimatedVisibility(
            visible = exportMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularActionButton(
                    icon = MiuixIcons.Close,
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                    bg = MiuixTheme.colorScheme.secondaryContainer,
                    onClick = onExitExport,
                )
                CircularActionButton(
                    icon = MiuixIcons.Share,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    bg = MiuixTheme.colorScheme.primary,
                    onClick = onExportSelected,
                )
            }
        }
    }
}
