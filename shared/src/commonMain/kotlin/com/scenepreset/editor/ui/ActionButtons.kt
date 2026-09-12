// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
