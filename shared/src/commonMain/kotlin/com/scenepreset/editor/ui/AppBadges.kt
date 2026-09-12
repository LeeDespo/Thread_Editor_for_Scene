// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.AppEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Deterministic accent color for a package badge. */
fun badgeColor(pkg: String): Color {
    val hue = (pkg.hashCode() % 360).let { if (it < 0) it + 360 else it }
    return Color.hsv(hue.toFloat(), 0.5f, 0.82f)
}

@Composable
fun AppBadge(app: AppEntry, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val bg = badgeColor(app.packageName)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (app.icon != null) Color.Transparent else bg),
        contentAlignment = Alignment.Center,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = app.letter,
                style = MiuixTheme.textStyles.button,
                color = Color.White,
            )
        }
    }
}

/** Overlapping app-icon avatars plus a colored count block (for the rule card). */
@Composable
fun AppBadgeRow(
    packages: List<String>,
    resolve: (String) -> AppEntry,
    onClick: () -> Unit,
) = AppBadgeRow(
    apps = packages.map(resolve),
    onClick = onClick,
)

/** [AppBadgeRow] over pre-resolved entries — no per-recomposition lookups. */
@Composable
fun AppBadgeRow(
    apps: List<AppEntry>,
    onClick: () -> Unit,
) {
    val size = 30.dp
    val overlap = 12.dp
    val step = size - overlap
    val count = apps.size
    val shown = count.coerceAtMost(10)
    val hasMore = count > 10
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (shown > 0) {
            Box(
                modifier = Modifier.size(
                    width = size + step * (shown - 1),
                    height = size,
                ),
            ) {
                apps.take(shown).forEachIndexed { index, app ->
                    AppBadge(
                        app = app,
                        size = size,
                        modifier = Modifier.offset(x = (step * index)),
                    )
                }
            }
        }
        if (hasMore) {
            Spacer(Modifier.width(overlap))
            Text(
                text = "...",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (count > 0) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
