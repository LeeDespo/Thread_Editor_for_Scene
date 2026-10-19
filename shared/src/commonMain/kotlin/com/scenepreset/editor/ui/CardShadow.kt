// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft elevation shadow for list cards, rendered on the RenderNode. The spot
 * shadow (cast by the top light, which grows/offsets toward the bottom of the
 * screen — why cards lower down "shadowed bigger") is disabled; only the
 * uniform ambient shadow remains, so every card reads identically wherever it
 * sits. Must be applied BEFORE any clip/background modifier so the shadow is
 * not cut.
 */
fun Modifier.cardShadow(shape: Shape, elevation: Dp = 2.dp): Modifier =
    if (elevation <= 0.dp) this
    else graphicsLayer {
        this.shape = shape
        this.shadowElevation = elevation.toPx()
        this.spotShadowColor = Color.Transparent
        this.clip = false
    }

/**
 * Dissolve bands at the top and/or bottom edge of a scroll container: content
 * fades out over [top] / [bottom] instead of being cut hard by the bar or the
 * screen edge. Keep the bands short (a hint, not a wall). Requires offscreen
 * compositing so the DstIn ramp only thins this node's own alpha and never
 * punches through to the window behind it.
 */
fun Modifier.edgeFade(
    top: Dp = 22.dp,
    bottom: Dp = 0.dp,
    topStrength: () -> Float = { 1f },
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = top.toPx()
            val strength = topStrength().coerceIn(0f, 1f)
            if (topPx > 0f && size.height > topPx && strength > 0f) {
                // DstIn ramp: source alpha at the top edge = 1 - strength, so a
                // fractional strength thins the dissolve instead of hard-cutting
                // it on. Read in the draw phase, so a scroll-driven strength is
                // frame-smooth without recomposition.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - strength),
                        1f to Color.Black,
                        startY = 0f,
                        endY = topPx,
                    ),
                    blendMode = BlendMode.DstIn,
                    size = Size(size.width, topPx),
                )
            }
            val bottomPx = bottom.toPx()
            if (bottomPx > 0f && size.height > bottomPx) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                    // endY overshoots by 2px so the boundary row is fully
                    // faded — an exact-fit ramp leaves a dark 1px seam.
                    topLeft = Offset(0f, size.height - bottomPx),
                    size = Size(size.width, bottomPx + 2f),
                )
            }
        }
