// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.CoreTopology
import com.scenepreset.editor.data.SETTING_TOPOLOGY_INTRO_SHOWN
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.data.validationIssues
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Custom core-count / cluster editor. First card: the assumed core count with a
 * +/- stepper; second card: one editable row per cluster (name + core spec,
 * e.g. "0,1,3,5-7"), plus add/delete. Save validates coverage and duplicate
 * names; reset re-detects the real hardware and restores it.
 */
@Composable
fun TopologyScreen(
    context: ThreadEditorContext,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onSaved: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    var coreCount by remember { mutableStateOf(1) }
    var clusters by remember { mutableStateOf(listOf(CoreTopology.CoreCluster("", ""))) }
    var loaded by remember { mutableStateOf(false) }
    var showFirstUse by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Show the intro only after the page has fully settled (the LaunchedEffect
    // below completes), so the page transition and the dialog never race.
    LaunchedEffect(Unit) {
        val topo = context.effectiveTopology()
        coreCount = topo.coreCount
        clusters = topo.clusters.ifEmpty { listOf(CoreTopology.CoreCluster("", "")) }
        loaded = true
        // Page fully settled (transition done + data loaded) before the intro shows.
        if (context.readSetting(SETTING_TOPOLOGY_INTRO_SHOWN) != "true") showFirstUse = true
    }

    fun draft() = CoreTopology(coreCount = coreCount, clusters = clusters.filter { it.label.isNotBlank() || it.cores.isNotBlank() })

    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            TopAppBar(
                title = "自定义核心",
                largeTitle = "自定义核心 / 核心簇",
                subtitle = "让应用按你指定的核心结构工作",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { showResetConfirm = true }) {
                    Icon(MiuixIcons.Undo, contentDescription = "重置")
                }
                // Tinted save FAB — mirror the primary FAB used on the home pages.
                FloatingActionButton(
                    onClick = {
                        if (!loaded) return@FloatingActionButton
                        val issues = draft().validationIssues()
                        if (issues.isNotEmpty()) {
                            onMessage(issues.first())
                        } else {
                            context.writeSetting(SETTING_TOPOLOGY_INTRO_SHOWN, "true")
                            context.setCustomTopology(draft())
                            onMessage("已保存自定义核心配置，即将重载应用")
                            onSaved()
                        }
                    },
                    containerColor = colors.primary,
                ) {
                    Icon(MiuixIcons.Ok, contentDescription = "保存", tint = colors.onPrimary)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupCard("核心数量", colors.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("本应用会将本机CPU核心数量视为", Modifier.weight(1f))
                    StepperButton("−", enabled = coreCount > 1) { coreCount = (coreCount - 1).coerceAtLeast(1) }
                    // Centered between the two stepper buttons, shows "8颗".
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "${coreCount}颗",
                            style = MiuixTheme.textStyles.main,
                            color = colors.primary,
                        )
                    }
                    StepperButton("+", enabled = true) { coreCount += 1 }
                }
            }

            GroupCard("核心簇（从强到弱）", colors.primary) {
                clusters.forEachIndexed { index, cluster ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FieldLabel("核心簇 ${index + 1}", Modifier.weight(1f))
                            if (clusters.size > 1) {
                                IconButton(onClick = { clusters = clusters.filterIndexed { i, _ -> i != index } }) {
                                    Icon(MiuixIcons.Delete, contentDescription = "删除核心簇")
                                }
                            }
                        }
                        TextField(
                            value = cluster.label,
                            onValueChange = { v -> clusters = clusters.mapIndexed { i, c -> if (i == index) c.copy(label = v) else c } },
                            label = "核心簇名称（如 Ultra_Core）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            value = cluster.cores,
                            onValueChange = { v -> clusters = clusters.mapIndexed { i, c -> if (i == index) c.copy(cores = v) else c } },
                            label = "对应核心（如 0,1,3,5-7）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { clusters = clusters + CoreTopology.CoreCluster("", "") }) {
                        Text("新增核心簇", style = MiuixTheme.textStyles.button)
                    }
                }
            }

            // Bottom whitespace for the FABs.
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showFirstUse) {
        // Countdown forces the warning to be read before it can be dismissed.
        var remaining by remember { mutableStateOf(5) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1000)
                remaining--
            }
        }
        val ready = remaining <= 0
        WindowDialog(show = true, onDismissRequest = { if (ready) showFirstUse = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("自定义核心 / 核心簇", style = MiuixTheme.textStyles.title3)
                Spacer(Modifier.height(8.dp))
                Text(
                    "自定义后，应用会将本机的 CPU 视为拥有你自定义的核心数与核心簇，" +
                        "配置的写入与导入映射都会按此结构进行，可能会有负面影响。\n\n" +
                        "如需恢复为本机真实核心结构，请点击右下角的重置按钮。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        showFirstUse = false
                        context.writeSetting(SETTING_TOPOLOGY_INTRO_SHOWN, "true")
                    },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (ready) "知道了" else "请阅读（${remaining}s 后可关闭）")
                }
            }
        }
    }

    if (showResetConfirm) {
        WindowDialog(show = true, onDismissRequest = { showResetConfirm = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("重置为真实硬件", style = MiuixTheme.textStyles.title3)
                Spacer(Modifier.height(8.dp))
                Text(
                    "将重新检测本机的 CPU 核心数与核心簇，并覆盖当前的自定义配置。确定继续吗？",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showResetConfirm = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = {
                            showResetConfirm = false
                            context.writeSetting(SETTING_TOPOLOGY_INTRO_SHOWN, "true")
                            context.setCustomTopology(null)
                            onMessage("已重置为本机真实核心结构，即将重载应用")
                            onSaved()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("重置") }
                }
            }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (enabled) colors.primaryContainer else colors.surfaceContainer,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MiuixTheme.textStyles.main,
            color = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariantSummary,
            modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}
