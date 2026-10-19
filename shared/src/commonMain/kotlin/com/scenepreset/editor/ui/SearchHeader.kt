// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop

import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The HyperOS-style frosted band that floats over the top of the scrolling
 * content: a tall gradient region whose blur strength ramps down from the
 * status bar while a surface-colored wash fades it into the page — content
 * scrolling under it ghosts out gradually instead of being cut (or crudely
 * blurred behind the bar itself).
 *
 * Must be placed as a SIBLING AFTER the node carrying
 * [top.yukonga.miuix.kmp.blur.layerBackdrop], never inside it: sampling the
 * capture from within itself creates a RenderNode cycle (native crash).
 */
internal fun Modifier.barBlur(backdrop: LayerBackdrop): Modifier =
    if (!isRuntimeShaderSupported()) this
    else drawBackdrop(
        backdrop,
        shape = { RectangleShape },
        effects = { blur(24f * density) },
    )

/** Bar background color paired with the frost band: transparent when it renders. */
@Composable
internal fun frostedBarColor(canBlur: Boolean): Color =
    if (canBlur) Color.Transparent else MiuixTheme.colorScheme.surface

/**
 * The expanded search entry: a large circle floating over the large-title row.
 * Alpha/translation are driven by the caller's graphicsLayer so it tracks the
 * collapsing large title without recomposition.
 */
@Composable
internal fun LargeSearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MiuixIcons.Search,
            contentDescription = "搜索",
            tint = MiuixTheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The active search field, hosted in the TopAppBar's bottomContent slot: sits
 * right below the bar, clears on the trailing button, closes when already
 * empty. Icon-only trailing affordance per the search-button design.
 */
@Composable
internal fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        label = "搜索规则名称 / 包名 / 应用名 / 类目",
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) {
                Icon(
                    if (query.isEmpty()) MiuixIcons.Close else MiuixIcons.Clear,
                    contentDescription = if (query.isEmpty()) "关闭搜索" else "清除",
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * The narrow transition band at the boundary between an opaque top bar and the
 * scrolling content: the bar's own surface color fading downward to clear, so
 * content emerges from under the bar softly. Drawn as a plain gradient scrim
 * (NOT an alpha dissolve) so it stays visible over light-colored content.
 * Place it as the last child of the content container, aligned TopCenter.
 */
@Composable
internal fun TopScrim(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 22.dp,
    strength: () -> Float = { 1f },
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = strength().coerceIn(0f, 1f) }
            .background(
                Brush.verticalGradient(
                    0f to colors.surface,
                    0.55f to colors.surface.copy(alpha = 0.72f),
                    1f to colors.surface.copy(alpha = 0f),
                ),
            ),
    )
}

/**
 * Strength ramp for the top fade band on collapsible-bar pages: fully hidden
 * while the bar is expanded, ramping in as the bar collapses to its compact
 * position. The band matters only once content actually scrolls under the
 * short bar; tying it to the live collapse fraction makes the appear/disappear
 * transition track the scroll instead of popping.
 */
internal fun barFadeStrength(collapsedFraction: Float): Float =
    ((collapsedFraction - 0.12f) / 0.45f).coerceIn(0f, 1f)
