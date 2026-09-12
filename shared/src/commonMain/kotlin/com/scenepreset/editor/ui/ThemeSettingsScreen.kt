// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val modeNames = listOf("跟随系统", "浅色", "深色")

@Composable
fun ThemeSettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var showAccent by remember { mutableStateOf(false) }
    val baseMode = settings.colorMode % 3
    val monet = settings.colorMode >= 3
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            TopAppBar(
                title = "主题设置",
                largeTitle = "主题设置",
                subtitle = "颜色模式与强调色",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection).overScrollVertical(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SmallTitle("预览") }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    PhonePreview(
                        accentColor = if (monet) colors.primary else (keyColorFor(settings.keyColorIndex) ?: colors.primary),
                    )
                }
            }

            item { SmallTitle("颜色模式") }
            item {
                ModeSegmented(
                    selected = baseMode,
                    onSelect = { base ->
                        onSettingsChange(settings.copy(colorMode = if (monet) base + 3 else base))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            item { SmallTitle("强调色") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        SwitchPreference(
                            title = "启用 Monet 颜色",
                            summary = "开启后跟随系统壁纸动态取色",
                            checked = monet,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(colorMode = if (monet) baseMode else baseMode + 3))
                            },
                        )
                        ArrowPreference(
                            title = "强调色",
                            summary = if (settings.keyColorIndex <= 0) "默认" else PresetKeyColors.getOrNull(settings.keyColorIndex - 1)?.first ?: "默认",
                            onClick = { showAccent = true },
                        )
                    }
                }
            }
        }
    }

    AccentDialog(
        show = showAccent,
        selectedIndex = settings.keyColorIndex,
        onSelect = { index ->
            onSettingsChange(settings.copy(keyColorIndex = index))
            showAccent = false
        },
        onDismiss = { showAccent = false },
    )
}

@Composable
private fun ModeSegmented(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .background(colors.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        modeNames.forEachIndexed { index, name ->
            val isSel = index == selected
            val bg by animateColorAsState(if (isSel) colors.primaryContainer else colors.surfaceContainer, label = "modeBg")
            val fg by animateColorAsState(if (isSel) colors.onPrimaryContainer else colors.onSurfaceContainer, label = "modeFg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(bg, RoundedCornerShape(14.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, style = MiuixTheme.textStyles.footnote1, color = fg)
            }
        }
    }
}

@Composable
private fun PhonePreview(accentColor: Color) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .width(240.dp)
            .background(colors.surfaceContainerHighest, RoundedCornerShape(32.dp))
            .border(1.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Thread Editor", style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainer)
        Box(
            modifier = Modifier.fillMaxWidth().height(110.dp).background(accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp).background(colors.surfaceContainer, RoundedCornerShape(16.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(accentColor, RoundedCornerShape(7.dp)))
            PresetKeyColors.take(3).forEach { (_, color) ->
                Box(Modifier.size(26.dp).background(color, RoundedCornerShape(7.dp)))
            }
        }
    }
}

@Composable
private fun AccentDialog(show: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    WindowBottomSheet(
        show = show,
        title = "强调色",
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDotMini(Color.Transparent, selectedIndex == 0, "默认") { onSelect(0) }
            PresetKeyColors.forEachIndexed { index, (_, color) ->
                ColorDotMini(color, selectedIndex == index + 1, null) { onSelect(index + 1) }
            }
        }
    }
}

@Composable
private fun ColorDotMini(color: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (color == Color.Transparent) colors.surfaceContainerHighest else color, CircleShape)
                .then(
                    if (selected) Modifier.border(3.dp, colors.primary, CircleShape)
                    else Modifier.border(1.dp, colors.outline.copy(alpha = 0.3f), CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (color == Color.Transparent) {
                Text("A", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary)
            }
        }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
        }
    }
}
