// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft elevation shadow for list cards, rendered on the RenderNode (hardware
 * accelerated, no extra rendering cost while scrolling). Deliberately subtle —
 * HyperOS-style layering still relies mostly on surface color contrast.
 *
 * Must be applied BEFORE any clip/background modifier so the shadow is not cut.
 */
fun Modifier.cardShadow(shape: Shape, elevation: Dp = 2.dp): Modifier =
    if (elevation <= 0.dp) this
    else graphicsLayer {
        this.shape = shape
        this.shadowElevation = elevation.toPx()
        this.clip = false
    }
