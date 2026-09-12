// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CategoryEditScreen(
    existing: CategoryRule?,
    otherRules: List<CategoryRule>,
    context: ThreadEditorContext,
    showSystemApps: Boolean,
    onBack: () -> Unit,
    onCommit: (CategoryRule) -> Unit,
) {
    var friendly by remember(existing) { mutableStateOf(existing?.friendly ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "") }
    var packages by remember(existing) { mutableStateOf(existing?.packages.orEmpty()) }
    var activities by remember(existing) { mutableStateOf(existing?.activities.orEmpty()) }
    var info by remember { mutableStateOf<FieldInfo?>(null) }
    var showPackages by remember { mutableStateOf(false) }
    var commitConflicts by remember { mutableStateOf<List<ConflictReport>?>(null) }
    var installed by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(showSystemApps) { installed = context.installedApps(showSystemApps) }
    val otherPackages = remember(otherRules) {
        otherRules.flatMap { it.packages.orEmpty() }.toSet()
    }
    val isNew = existing == null

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = contentInsetsTopOnly,
            topBar = {
                SmallTopAppBar(
                    title = if (isNew) "新增类目" else "编辑类目",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    val candidate = CategoryRule(
                        friendly = friendly.trim(),
                        category = category.trim(),
                        packages = packages.toList(),
                        activities = activities.toList(),
                    ).normalized()
                    val conflicts = findCategoryConflicts(otherRules + candidate)
                    if (conflicts.isNotEmpty()) commitConflicts = conflicts else onCommit(candidate)
                }) {
                    Icon(MiuixIcons.Download, contentDescription = "保存")
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GroupCard("基本信息", MiuixTheme.colorScheme.primary) {
                    CategoryInfoTextRow("备注名称", friendly, { friendly = it },
                        "friendly", "类目的备注名，用以提升可读性，无实际功能影响。", { info = it })
                    CategoryInfoTextRow("类目 ID", category, { category = it },
                        "category",
                        "类目的唯一标识，由 threads.json 中规则的 categories 字段引用。不可与其他类目重复。",
                        { info = it })
                    CategoryPackagesSection(
                        packages = packages,
                        onRemovePackage = { pkg -> packages = packages - pkg },
                        onOpenPicker = { showPackages = true },
                        resolve = { context.resolveApp(it) },
                        onInfo = { info = it },
                    )
                    ChipInputField(
                        label = "组件 Activities",
                        chips = activities,
                        onAdd = { activities = activities + it },
                        onRemove = { activities = activities - it },
                        infoTitle = "activities",
                        infoText = "归入该类目的 Activity 组件名，用于按界面场景识别应用。可指定多个。",
                        onInfo = { info = it },
                    )
                }
                Spacer(Modifier.height(96.dp))
            }
        }

        InfoDialog(
            show = info != null,
            title = info?.title ?: "",
            text = info?.text ?: "",
            onDismiss = { info = null },
        )
        PackagePickerDialog(
            show = showPackages,
            current = packages,
            allOtherPackages = otherPackages,
            resolve = { context.resolveApp(it) },
            installed = installed,
            onConfirm = {
                packages = it
                showPackages = false
            },
            onDismiss = { showPackages = false },
            allowDuplicates = true,
        )
        ConflictDialog(
            show = commitConflicts != null,
            conflicts = commitConflicts.orEmpty(),
            onDismiss = { commitConflicts = null },
        )
    }
}

@Composable
private fun CategoryInfoTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    infoTitle: String,
    infoText: String,
    onInfo: (FieldInfo) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        InfoButton(title = infoTitle, text = infoText, onInfo = onInfo)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPackagesSection(
    packages: List<String>,
    onRemovePackage: (String) -> Unit,
    onOpenPicker: () -> Unit,
    resolve: (String) -> AppEntry,
    onInfo: (FieldInfo) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("包名 Packages")
            Spacer(Modifier.width(6.dp))
            Text("(${packages.size})", style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Spacer(Modifier.weight(1f))
            InfoButton("packages", "归入该类目的应用包名。可指定多个。", onInfo = onInfo)
        }
        if (packages.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                packages.forEach { pkg ->
                    AppChip(app = resolve(pkg), text = pkg, onRemove = { onRemovePackage(pkg) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer, CircleShape)
                    .clickable(onClick = onOpenPicker),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MiuixIcons.Add, contentDescription = "添加应用", tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}
