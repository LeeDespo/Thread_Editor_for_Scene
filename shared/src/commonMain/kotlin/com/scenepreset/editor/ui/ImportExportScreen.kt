// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn as VertLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ThreadsCodec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ImportExportScreen(
    context: ThreadEditorContext,
    rules: List<ThreadRule>,
    categories: List<CategoryRule>,
    sceneVersion: SceneVersion,
    rulesJson: String,
    categoriesJson: String,
    onImportRules: (String, Boolean) -> Unit,
    onImportCategories: (String, Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var showRuleExport by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            TopAppBar(
                title = "导入 / 导出",
                largeTitle = "导入 / 导出",
                subtitle = "在线程与类目间转移配置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(MiuixIcons.Info, contentDescription = "说明")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection).overScrollVertical(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SmallTitle("线程规则 threads.json") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "导出规则",
                            summary = "选择要导出的规则卡片，导出为 JSON 文件",
                            onClick = { showRuleExport = true },
                        )
                        ArrowPreference(
                            title = "导入并追加规则",
                            summary = "在现有规则后追加导入内容",
                            onClick = { context.importJson { text -> if (text != null) onImportRules(text, true) else onMessage("已取消导入") } },
                        )
                        ArrowPreference(
                            title = "导入并覆盖规则",
                            summary = "用导入内容替换全部规则",
                            onClick = { context.importJson { text -> if (text != null) onImportRules(text, false) else onMessage("已取消导入") } },
                        )
                    }
                }
            }

            item { SmallTitle("类目 categories.json") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        ArrowPreference(
                            title = "导出类目",
                            summary = "把当前类目导出为 JSON 文件",
                            onClick = { context.exportCategories("categories.json", categoriesJson) { ok -> onMessage(if (ok) "导出成功" else "导出失败") } },
                        )
                        ArrowPreference(
                            title = "导入并追加类目",
                            summary = "在现有类目后追加导入内容",
                            onClick = { context.importCategories { text -> if (text != null) onImportCategories(text, true) else onMessage("已取消导入") } },
                        )
                        ArrowPreference(
                            title = "导入并覆盖类目",
                            summary = "用导入内容替换全部类目",
                            onClick = { context.importCategories { text -> if (text != null) onImportCategories(text, false) else onMessage("已取消导入") } },
                        )
                    }
                }
            }

            item { SmallTitle("说明") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Text(
                        "导入或导出时会自动检测配置文件的 Scene 版本。若与当前所选版本不同，将进行自适应转换（尽力而为），转换可能会丢失某些配置信息，实际效果可能不佳。\n\n" +
                            "导入的核心数与本机不同时，会询问原机的 CPU 架构（从强到弱每档核心数）。选择自适应转化后，文件中的核心掩码会按原机各档归档，再映射到本机同档的核心簇；" +
                            "若不想转换，也可以到「更多 → 自定义核心 / 核心簇」把本机核心数改为与文件一致后按原样导入。\n\n" +
                            "导入的规则与类目先暂存在应用内，确认无误后点击列表页的保存按钮才会写入文件。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    RuleExportSheet(
        show = showRuleExport,
        rules = rules,
        context = context,
        sceneVersion = sceneVersion,
        onExport = { selected ->
            showRuleExport = false
            if (selected.isEmpty()) {
                onMessage("未选择任何规则")
            } else {
                context.exportJson("threads.json", ThreadsCodec.encode(selected, sceneVersion)) { ok ->
                    onMessage(if (ok) "已导出 ${selected.size} 条规则" else "导出失败")
                }
            }
        },
        onDismiss = { showRuleExport = false },
    )

    WindowDialog(show = showInfo, onDismissRequest = { showInfo = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("导入 / 导出说明", style = MiuixTheme.textStyles.title3)
            Text(
                "导入或导出时会自动检测配置文件的 Scene 版本。若与当前所选版本不同，将进行自适应转换（尽力而为），转换可能会丢失某些配置信息，实际效果可能不佳。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Button(onClick = { showInfo = false }, modifier = Modifier.fillMaxWidth()) {
                Text("知道了", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/**
 * Export picker: a bottom sheet listing the current rule cards with search and
 * select-all, mirroring the package-picker layout (no manual input field).
 */
@Composable
private fun RuleExportSheet(
    show: Boolean,
    rules: List<ThreadRule>,
    context: ThreadEditorContext,
    sceneVersion: SceneVersion,
    onExport: (List<ThreadRule>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(show) {
        if (show) {
            query = ""
            selected = rules.indices.toSet()
        }
    }
    val shown = remember(rules, query) {
        rules.mapIndexed { i, r -> i to r }.filter { (_, r) ->
            query.isBlank() || r.displayName.contains(query, true) ||
                r.packages.orEmpty().any { it.contains(query, true) } ||
                r.categories.orEmpty().any { it.contains(query, true) } ||
                r.packages.orEmpty().any { pkg -> context.resolveApp(pkg).label.contains(query, true) }
        }
    }
    val allShownSelected = shown.isNotEmpty() && shown.all { (i, _) -> i in selected }

    WindowBottomSheet(
        show = show,
        title = "选择要导出的规则",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索规则名 / 包名 / 应用名 / 类目",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "已选 ${selected.size} / ${rules.size}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (allShownSelected) "取消全选" else "全选",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier.clickable {
                        selected = if (allShownSelected) {
                            selected - shown.map { it.first }.toSet()
                        } else {
                            selected + shown.map { it.first }.toSet()
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            VertLazyColumn(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                items(shown, key = { it.first }) { (index, rule) ->
                    val isSelected = index in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selected = if (isSelected) selected - index else selected + index
                            }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                    ) {
                        val pkg = rule.packages?.firstOrNull { it != "*" } ?: rule.packages?.firstOrNull()
                        if (pkg != null) {
                            AppBadge(app = context.resolveApp(pkg), size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    rule.displayName.take(1),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = colors.onSecondaryContainer,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(rule.displayName, style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainerHighest)
                            val sub = buildString {
                                rule.packages.orEmpty().take(2).forEach { append(it).append(' ') }
                                if ((rule.packages?.size ?: 0) > 2) append("…")
                            }
                            if (sub.isNotBlank()) {
                                Text(sub.trim(), style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
                            }
                        }
                        SelectDot(isSelected)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onExport(selected.mapNotNull { i -> rules.getOrNull(i) }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("导出所选（${selected.size} 条）", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
