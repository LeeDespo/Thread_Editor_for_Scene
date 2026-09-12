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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.isWildcard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A MIUI-style centered info dialog that is guaranteed to render over any screen. */
@Composable
fun InfoDialog(
    show: Boolean,
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                color = colors.onSurfaceContainerHighest,
            )
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceContainerVariant,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("知道了", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** A package together with the category that provided it (if any). */
data class AppOrigin(
    val packageName: String,
    val categoryId: String?,
) {
    val fromCategory: Boolean get() = categoryId != null
}

/** A rule's manifest: its referenced categories plus every app it covers. */
data class AppManifest(
    val title: String,
    val categories: List<Pair<String, String>>,
    val apps: List<AppOrigin>,
)

/** Application manifest dialog (应用清单): categories first, then apps. */
@Composable
fun AppListDialog(
    show: Boolean,
    title: String,
    categories: List<Pair<String, String>>,
    apps: List<AppOrigin>,
    resolve: (String) -> AppEntry,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (categories.isNotEmpty()) {
                Text(
                    "所含类目",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                )
                categories.forEach { (id, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainerHighest, modifier = Modifier.weight(1f))
                        Text(id, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "所含应用（${apps.size}）",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            if (apps.isEmpty()) {
                Text(
                    "该规则未绑定任何包名",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceVariantSummary,
                )
            } else {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(apps) { origin ->
                        val app = resolve(origin.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppBadge(app = app, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        app.label,
                                        style = MiuixTheme.textStyles.main,
                                        color = colors.onSurfaceContainerHighest,
                                    )
                                    if (origin.categoryId != null) {
                                        CategoryTag(origin.categoryId)
                                    }
                                }
                                Text(
                                    origin.packageName,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("关闭", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CategoryTag(id: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = id,
        style = MiuixTheme.textStyles.footnote2,
        color = colors.primary,
        modifier = Modifier
            .background(colors.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Package conflicts found during a save. */
data class ConflictReport(
    val kind: String,
    val key: String,
    val rules: List<String>,
)

@Composable
fun ConflictDialog(
    show: Boolean,
    conflicts: List<ConflictReport>,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "发现配置冲突",
                style = MiuixTheme.textStyles.title3,
                color = colors.onSurfaceContainerHighest,
            )
            Text(
                "检测到以下冲突：",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceContainerVariant,
            )
            LazyColumn(modifier = Modifier.height(260.dp)) {
                items(conflicts) { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            "${c.kind}「${c.key}」",
                            style = MiuixTheme.textStyles.main,
                            color = colors.onSurfaceContainerHighest,
                        )
                        Text(
                            "冲突于：${c.rules.joinToString("、")}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("我知道了", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** Add packages by typing or toggling from the installed application list (with conflict check). */
@Composable
fun PackagePickerDialog(
    show: Boolean,
    current: List<String>,
    allOtherPackages: Set<String>,
    resolve: (String) -> AppEntry,
    installed: List<AppEntry>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    allowDuplicates: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    var selected by remember { mutableStateOf(current.toList()) }
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(show) {
        if (show) {
            selected = current.toList()
            input = ""
            query = ""
            conflict = null
        }
    }

    fun addOne(p: String) {
        if (p.isEmpty()) return
        if (p in selected) {
            conflict = "「$p」已在当前规则中"
            return
        }
        if (!allowDuplicates && p in allOtherPackages) {
            conflict = "「$p」已存在于其它规则"
            return
        }
        selected = selected + p
        conflict = null
    }

    // Accept half- and full-width separators for user convenience.
    fun addMany(raw: String) {
        raw.split(',', '，', ';', '；', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }.forEach { addOne(it) }
    }

    // Preselected apps lead the unfiltered list so they are reachable without
    // scrolling; ordering is a snapshot when the sheet opens, not live re-sort.
    val filtered = remember(query, installed, show) {
        val base = if (query.isBlank()) {
            val pre = installed.filter { it.packageName in selected.toSet() }
            val rest = installed.filter { it.packageName !in selected.toSet() }
            pre + rest
        } else {
            installed
        }
        if (query.isBlank()) base else base.filter {
            it.packageName.contains(query, true) || it.label.contains(query, true)
        }
    }

    WindowBottomSheet(
        show = show,
        title = "选择应用包名",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("勾选应用，或手动输入包名", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        conflict = null
                    },
                    label = "输入包名，逗号分隔",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMany(input); input = "" }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { addMany(input); input = "" },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("添加", style = MiuixTheme.textStyles.button)
                }
            }
            conflict?.let {
                Text(it, style = MiuixTheme.textStyles.footnote1, color = colors.error)
            }

            if (selected.isNotEmpty()) {
                Text("已选择 (${selected.size})", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selected) { pkg ->
                        AppChip(
                            app = resolve(pkg),
                            text = pkg,
                            onRemove = {
                                selected = selected.filter { it != pkg }
                                conflict = null
                            },
                        )
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索应用",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(filtered) { app ->
                    val inOther = !allowDuplicates && app.packageName in allOtherPackages
                    val isSelected = app.packageName in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !inOther) {
                                conflict = null
                                selected = if (isSelected) selected - app.packageName else selected + app.packageName
                            }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppBadge(app = app, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainerHighest)
                            Text(app.packageName, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
                            if (inOther) {
                                Text(
                                    "已用于其它规则",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.error,
                                )
                            }
                        }
                        SelectDot(isSelected)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", style = MiuixTheme.textStyles.button)
                }
                Button(
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定 (${selected.size})", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** Add category ids by typing or toggling from the categories.json list (with conflict check). */
@Composable
fun CategoryPickerDialog(
    show: Boolean,
    current: List<String>,
    allOtherCategoryIds: Set<String>,
    allCategories: List<CategoryRule>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var selected by remember { mutableStateOf(current.toList()) }
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showList by remember { mutableStateOf(true) }
    var conflict by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(show) {
        if (show) {
            selected = current.toList()
            input = ""
            query = ""
            conflict = null
        }
    }

    fun addOne(id: String) {
        val t = id.trim()
        if (t.isEmpty()) return
        if (t in selected) {
            conflict = "「$t」已在当前规则中"
            return
        }
        if (allCategories.any { it.category == t && it.isWildcard }) {
            conflict = "想使用通配，请在包名添加「*」"
            return
        }
        if (t in allOtherCategoryIds) {
            conflict = "「$t」已用于其它规则"
            return
        }
        selected = selected + t
        conflict = null
    }

    fun addMany(raw: String) {
        raw.split(',', '，', ';', '；', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }.forEach { addOne(it) }
    }

    val filtered = remember(query, allCategories) {
        if (query.isBlank()) allCategories else allCategories.filter {
            it.category.contains(query, true) || it.friendly.contains(query, true)
        }
    }

    WindowBottomSheet(
        show = show,
        title = "选择类目",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("键入类目 ID，或从类目列表导入", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it; conflict = null },
                    label = "输入类目 ID，逗号分隔",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMany(input); input = "" }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { addMany(input); input = "" },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("添加", style = MiuixTheme.textStyles.button)
                }
            }
            conflict?.let { Text(it, style = MiuixTheme.textStyles.footnote1, color = colors.error) }

            if (selected.isNotEmpty()) {
                Text("已选择 (${selected.size})", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selected) { id ->
                        Chip(label = id, onRemove = {
                            selected = selected.filter { it != id }
                            conflict = null
                        })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("从类目列表导入", style = MiuixTheme.textStyles.main, modifier = Modifier.weight(1f))
                Switch(checked = showList, onCheckedChange = { showList = it })
            }

            if (showList) {
                TextField(value = query, onValueChange = { query = it }, label = "搜索类目", singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered) { cat ->
                        val inOther = cat.category in allOtherCategoryIds
                        val isSelected = cat.category in selected
                        val isWild = cat.isWildcard
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !inOther && !isWild) {
                                    conflict = null
                                    selected = if (isSelected) selected - cat.category else selected + cat.category
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cat.friendly.ifBlank { cat.category }, style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainerHighest)
                                Text(cat.category, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
                                if (inOther) {
                                    Text("已用于其它规则", style = MiuixTheme.textStyles.footnote2, color = colors.error)
                                }
                                if (isWild) {
                                    Text("通配类目，请在包名添加「*」", style = MiuixTheme.textStyles.footnote2, color = colors.error)
                                }
                            }
                            SelectDot(isSelected)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", style = MiuixTheme.textStyles.button) }
                Button(
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定 (${selected.size})", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** A removable token chip with a colored block and rounded corners. */
@Composable
fun Chip(label: String, onRemove: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.secondaryContainer, CircleShape)
            .clickable(onClick = onRemove)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSecondaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "✕",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/** A removable tag showing the app icon (or first letter) plus the label. */
@Composable
fun AppChip(app: AppEntry, onRemove: () -> Unit, text: String = app.label) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .background(colors.secondaryContainer, CircleShape)
            .clickable(onClick = onRemove)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(app = app, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MiuixTheme.textStyles.footnote1, color = colors.onSecondaryContainer)
        Spacer(Modifier.width(6.dp))
        Text(
            "✕",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
}

/** Lets the user name/annotate a save before it writes to threads.json. */
@Composable
fun SaveMetaDialog(
    show: Boolean,
    title: String = "保存规则",
    fileHint: String = "threads.json",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MiuixTheme.textStyles.title3, color = colors.onSurfaceContainerHighest)
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "操作名称（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = note,
                onValueChange = { note = it },
                label = "备注（可选）",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "保存后会将当前内容写入 $fileHint，并生成一条可回溯的历史记录。",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", style = MiuixTheme.textStyles.button)
                }
                Button(
                    onClick = { onConfirm(name.trim(), note.trim()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** Compact circular check indicator shared by all picker rows (export sheet, package / category pickers). */
@Composable
internal fun SelectDot(selected: Boolean) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("✓", style = MiuixTheme.textStyles.footnote1, color = colors.onPrimary)
        }
    }
}
