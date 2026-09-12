// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.scenepreset.editor.model.SceneVersion
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

data class AppSettings(
    val colorMode: Int = 0,
    val keyColorIndex: Int = 1,
    val scenePath: String = "/data/user/0/com.omarea.vtools/files",
    val sceneVersion: SceneVersion = SceneVersion.SceneN1,
    val showSystemApps: Boolean = false,
)

val PresetKeyColors: List<Pair<String, Color>> = listOf(
    "蓝" to Color(0xFF3482FF),
    "绿" to Color(0xFF36D167),
    "紫" to Color(0xFF7C4DFF),
    "橙" to Color(0xFFFF5722),
    "黄" to Color(0xFFFFB21D),
    "粉" to Color(0xFFE91E63),
)

fun keyColorFor(index: Int): Color? = if (index <= 0) null else PresetKeyColors.getOrNull(index - 1)?.second

@Composable
fun AppTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val keyColor = keyColorFor(settings.keyColorIndex)
    val controller = remember(settings.colorMode, settings.keyColorIndex) {
        val mode = when (settings.colorMode) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            3 -> ColorSchemeMode.MonetSystem
            4 -> ColorSchemeMode.MonetLight
            5 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.System
        }
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
        )
    }
    // MiuixTheme's controller overload internally holds the resolved colors in an
    // unkeyed `remember { rawColors.copy() }`, so changing the controller does not
    // re-propagate a fresh scheme. Keying on the theme inputs forces that subtree to
    // re-enter composition and pick up the newly computed colors.
    key(settings.colorMode, settings.keyColorIndex) {
        MiuixTheme(
            controller = controller,
            content = content,
        )
    }
}
