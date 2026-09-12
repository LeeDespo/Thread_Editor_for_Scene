// Copyright 2026, ThreadEditor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
/**
 * Long-press-anywhere-on-the-list gesture used to enter export mode.
 *
 * Runs on the Initial pass so it observes before child clickables and never
 * swallows ordinary interactions:
 * - press released before the long-press timeout → ordinary tap, falls
 *   through to the card (edit / select);
 * - press held past the timeout without moving beyond the touch slop →
 *   [onLongPress] fires and the rest of the gesture is consumed so the card
 *   underneath does not also react;
 * - press moves beyond the touch slop → cancelled, the list scrolls normally;
 * - a second finger joins → cancelled (never fights pinch / two-finger
 *   scrolling).
 *
 * [enabled] and [onLongPress] are captured through [onLongPressState] (a
 * lambda-of-lambda) so every gesture re-reads the latest dirty/mode state;
 * a plain captured boolean goes stale until the next recomposition of the
 * pointerInput key, which is why the blocked check used to lag one page
 * switch behind.
 */
fun Modifier.exportLongPress(
    enabled: () -> Boolean,
    onLongPress: () -> Unit,
): Modifier = composed {
    // Remembered wrappers: pointerInput never restarts, but reads the latest
    // values through the state objects on every gesture.
    val enabledState = rememberUpdatedState(enabled)
    val longPressState = rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            // Every path below must suspend at least once per gesture; a
            // synchronous return here would spin the awaitEachGesture wrapper
            // loop on the main thread (ANR). awaitFirstDown always suspends.
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            if (!enabledState.value()) return@awaitEachGesture
            val downPos = down.position

            var fired = false
            var moved = false
            withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    if (event.changes.size > 1) return@withTimeoutOrNull // multi-touch
                    val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull
                    if ((change.position - downPos).getDistance() > slop) {
                        moved = true
                        return@withTimeoutOrNull
                    }
                    if (!change.pressed) return@withTimeoutOrNull // released: tap
                }
            } ?: run {
                // Timed out while still held near the initial position.
                fired = true
            }

            if (fired) {
                longPressState.value()
                // Consume the remainder so the pressed card does not click.
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.all { !it.pressed }) break
                }
            }
            // Tap and scroll fall through untouched.
        }
    }
}
