// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.data.ThreadEditorContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SettingsScreen(
    context: ThreadEditorContext,
    settings: AppSettings,
    onOpenTheme: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTopology: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onVersionChange: (SceneVersion) -> Unit,
) {
    var showPathEdit by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showCategoryReset by remember { mutableStateOf(false) }
    var showLogCap by remember { mutableStateOf(false) }
    var showVersion by remember { mutableStateOf(false) }
    var locked by remember(settings.scenePath) { mutableStateOf(context.sceneRepository.isLocked()) }
    var categoriesLocked by remember(settings.scenePath) { mutableStateOf(context.sceneRepository.isCategoriesLocked()) }
    var logCapKb by remember { mutableStateOf(context.logCapKb()) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            TopAppBar(
                title = "更多",
                largeTitle = "更多",
                subtitle = "配置与工具",
                navigationIcon = {},
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection).overScrollVertical(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SmallTitle("外观") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "主题设置",
                            summary = "颜色模式 / 主题色",
                            onClick = onOpenTheme,
                        )
                    }
                }
            }

            item { SmallTitle("Scene") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "Scene 版本",
                            summary = settings.sceneVersion.display,
                            onClick = { showVersion = true },
                        )
                        ArrowPreference(
                            title = "自定义核心 / 核心簇",
                            summary = "让应用按你指定的核心结构工作",
                            onClick = onOpenTopology,
                        )
                        ArrowPreference(
                            title = "自定义Scene性能调节配置文件路径",
                            summary = settings.scenePath,
                            onClick = { showPathEdit = true },
                        )
                        SwitchPreference(
                            title = "锁定 threads.json",
                            summary = "防止线程配置文件被 Scene 覆盖",
                            checked = locked,
                            onCheckedChange = {
                                val error = context.sceneRepository.setLocked(!locked)
                                if (error == null) {
                                    locked = !locked
                                    onMessage(if (locked) "已锁定 threads.json" else "已解除锁定 threads.json")
                                } else onMessage(error)
                            },
                        )
                        SwitchPreference(
                            title = "锁定 categories.json",
                            summary = "防止类目配置文件被 Scene 覆盖",
                            checked = categoriesLocked,
                            onCheckedChange = {
                                val error = context.sceneRepository.setCategoriesLocked(!categoriesLocked)
                                if (error == null) {
                                    categoriesLocked = !categoriesLocked
                                    onMessage(if (categoriesLocked) "已锁定 categories.json" else "已解除锁定 categories.json")
                                } else onMessage(error)
                            },
                        )
                        ArrowPreference(
                            title = "重置 threads.json",
                            summary = "删除配置文件，使 Scene 重新生成",
                            onClick = { showReset = true },
                        )
                        ArrowPreference(
                            title = "重置 categories.json",
                            summary = "恢复首次打开应用时的官方类目备份",
                            onClick = { showCategoryReset = true },
                        )
                    }
                }
            }

            item { SmallTitle("编辑行为") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        SwitchPreference(
                            title = "显示系统应用",
                            summary = "在“添加应用”中显示系统应用包名",
                            checked = settings.showSystemApps,
                            onCheckedChange = { onSettingsChange(settings.copy(showSystemApps = it)) },
                        )
                    }
                }
            }

            item { SmallTitle("管理") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "历史记录",
                            summary = "线程与类目的保存快照，可回溯或删除",
                            onClick = onOpenHistory,
                        )
                        ArrowPreference(
                            title = "导入 / 导出",
                            summary = "导入导出规则与类目，并按需自动转换版本",
                            onClick = onOpenImportExport,
                        )
                    }
                }
            }

            item { SmallTitle("日志") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "日志存储上限",
                            summary = if (logCapKb <= 0) "禁用（0 KB）" else "$logCapKb KB",
                            onClick = { showLogCap = true },
                        )
                        ArrowPreference(
                            title = "导出日志",
                            summary = "把已记录的日志保存为文本文件",
                            onClick = {
                                context.exportLogs { ok ->
                                    onMessage(if (ok) "日志已导出" else "导出失败或已取消")
                                }
                            },
                        )
                        ArrowPreference(
                            title = "清空日志",
                            summary = "删除当前日志内容，便于抓取最新日志",
                            onClick = {
                                context.clearLogs()
                                onMessage("日志已清空")
                            },
                        )
                    }
                }
            }

            item { SmallTitle("关于") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "关于本应用",
                            summary = "开发者、鸣谢与开源协议",
                            onClick = onOpenAbout,
                        )
                    }
                }
            }
        }
    }

    PathEditDialog(
        show = showPathEdit,
        current = settings.scenePath,
        onConfirm = { path ->
            onSettingsChange(settings.copy(scenePath = path))
            onMessage("已保存配置路径，正在重新加载配置文件")
            showPathEdit = false
        },
        onDismiss = { showPathEdit = false },
    )

    ResetConfirmDialog(
        show = showReset,
        onConfirm = {
            val err = context.sceneRepository.reset()
            onMessage(err ?: "已删除 threads.json。请到 Scene 切换一次调度档位（LP/HP/EP），Scene 会自动重新生成")
            showReset = false
        },
        onDismiss = { showReset = false },
    )

    ResetConfirmDialog(
        show = showCategoryReset,
        title = "重置 categories.json",
        message = "categories.json 无法通过切换性能调度重新拉取官方配置。\n\n" +
            "此操作只是恢复第一次打开本应用时获取的备份文件；若备份不存在（或此功能不能满足要求），" +
            "清除 Scene 的全部数据可拉取官方配置（会同时清空 Scene 的其它设置）。\n\n当前类目配置将被覆盖，确定继续吗？",
        confirmLabel = "恢复备份",
        onConfirm = {
            val err = context.sceneRepository.restoreCategoriesBackup()
            onMessage(err ?: "已恢复 categories.json 至首次打开时的备份")
            showCategoryReset = false
        },
        onDismiss = { showCategoryReset = false },
    )

    LogCapDialog(
        show = showLogCap,
        currentKb = logCapKb,
        onSelect = { kb ->
            logCapKb = kb
            context.setLogCapKb(kb)
            onMessage(if (kb <= 0) "日志已禁用" else "日志上限 $kb KB（文件位于应用缓存目录）")
            showLogCap = false
        },
        onDismiss = { showLogCap = false },
    )

    VersionDialog(
        show = showVersion,
        selected = settings.sceneVersion,
        onSelect = { version ->
            showVersion = false
            onVersionChange(version)
        },
        onDismiss = { showVersion = false },
    )
}

@Composable
private fun PathEditDialog(
    show: Boolean,
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(current) }
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("自定义Scene性能调节配置文件路径", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onSurfaceContainerHighest)
            TextField(value = path, onValueChange = { path = it }, label = "目录路径（含 threads.json 与 categories.json）", modifier = Modifier.fillMaxWidth())
            Text(
                "更改路径后应用会立即重新加载该目录下的 threads.json 与 categories.json，未保存的修改将会丢失。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", style = MiuixTheme.textStyles.button) }
                Button(onClick = { onConfirm(path.trim()) }, modifier = Modifier.weight(1f)) { Text("确定", style = MiuixTheme.textStyles.button) }
            }
        }
    }
}

@Composable
private fun ResetConfirmDialog(
    show: Boolean,
    title: String = "重置 threads.json",
    message: String = "该操作将删除当前的 threads.json 配置。删除后请在联网状态下于 Scene 中切换一次调度档位（LP/HP/EP），" +
        "Scene 会自动重建并拉取新的配置文件。此操作不可撤销，确定继续吗？",
    confirmLabel: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onSurfaceContainerHighest)
            Text(
                message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", style = MiuixTheme.textStyles.button) }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(confirmLabel, style = MiuixTheme.textStyles.button) }
            }
        }
    }
}

@Composable
private fun LogCapDialog(
    show: Boolean,
    currentKb: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("日志存储上限", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onSurfaceContainerHighest)
            Text(
                "0 KB 表示禁用日志（不消耗性能）。设为 256/512/1024 KB 后，日志会写入应用缓存目录并自动修剪到该上限。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            listOf(0, 256, 512, 1024).forEach { kb ->
                val label = if (kb == 0) "禁用" else "$kb KB"
                val sel = kb == currentKb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer)
                        .clickable { onSelect(kb) }
                        .padding(12.dp),
                ) {
                    Text(
                        label,
                        style = MiuixTheme.textStyles.main,
                        color = if (sel) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionDialog(
    show: Boolean,
    selected: SceneVersion,
    onSelect: (SceneVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("选择 Scene 版本", style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onSurfaceContainerHighest)
            SceneVersion.entries.forEach { v ->
                val sel = v == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer)
                        .clickable { onSelect(v) }
                        .padding(12.dp),
                ) {
                    Text(
                        v.display,
                        style = MiuixTheme.textStyles.main,
                        color = if (sel) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("取消", style = MiuixTheme.textStyles.button)
            }
        }
    }
}
