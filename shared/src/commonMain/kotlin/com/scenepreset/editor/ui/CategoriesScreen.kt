// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.isWildcard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CategoriesScreen(
    rules: List<CategoryRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    context: ThreadEditorContext,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onToggleDisabled: (Int) -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    disabled: Set<Int>,
    dirty: Boolean,
    exportMode: Boolean = false,
    selectedForExport: Set<Int> = emptySet(),
    onToggleSelect: (Int) -> Unit = {},
    onEnterExport: () -> Unit = {},
    onExport: (List<CategoryRule>) -> Unit = {},
    onExitExport: () -> Unit = {},
    onBlockedExport: () -> Unit = {},
    initialLoading: Boolean = false,
    conflicts: List<ConflictReport>?,
    onDismissConflicts: () -> Unit,
) {
    var manifest by remember { mutableStateOf<AppManifest?>(null) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "类目",
                largeTitle = "类目",
                subtitle = "共 ${rules.size} 种类目",
                navigationIcon = {},
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = contentInsetsTopOnly,
        floatingActionButton = {
            // Constant-size bottom-anchored overlay (same layout as HomeScreen):
            // only opacity + translation animate, no cross-fade height jumps.
            Box(contentAlignment = Alignment.BottomEnd) {
                AnimatedVisibility(
                    visible = !exportMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedVisibility(
                            visible = dirty,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 },
                        ) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularActionButton(
                                    icon = MiuixIcons.Download,
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    bg = MiuixTheme.colorScheme.primary,
                                    onClick = onSave,
                                )
                                CircularActionButton(
                                    icon = MiuixIcons.Undo,
                                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                                    bg = MiuixTheme.colorScheme.secondaryContainer,
                                    onClick = onRestore,
                                )
                            }
                        }
                        FloatingActionButton(onClick = onAdd) {
                            Icon(MiuixIcons.Add, contentDescription = "添加类目")
                        }
                    }
                }
                AnimatedVisibility(
                    visible = exportMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularActionButton(
                            icon = MiuixIcons.Close,
                            tint = MiuixTheme.colorScheme.onSecondaryContainer,
                            bg = MiuixTheme.colorScheme.secondaryContainer,
                            onClick = onExitExport,
                        )
                        CircularActionButton(
                            icon = MiuixIcons.Share,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            bg = MiuixTheme.colorScheme.primary,
                            onClick = { onExport(selectedForExport.mapNotNull { i -> rules.getOrNull(i) }) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoriesSearch(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val shown = remember(rules, query, context) {
                rules.mapIndexed { index, rule -> index to rule }
                    .filter { (_, rule) ->
                        query.isBlank() ||
                            (rule.packages?.any { pkg ->
                                pkg.contains(query, true) || context.resolveApp(pkg).label.contains(query, true)
                            } == true) ||
                            (rule.activities?.any { it.contains(query, true) } == true) ||
                            rule.category.contains(query, true) ||
                            rule.displayName.contains(query, true)
                    }
                    .sortedBy { (_, rule) -> if (rule.isWildcard) 1 else 0 }
            }
            // Pre-resolved badge entries: no per-recomposition resolveApp lookups.
            val cardData = remember(shown, context) {
                shown.map { (index, rule) ->
                    CategoryCardData(
                        index = index,
                        rule = rule,
                        entries = rule.packages.orEmpty().filter { it.isNotBlank() }.map { context.resolveApp(it) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .exportLongPress(enabled = { !exportMode }) {
                        if (dirty) onBlockedExport() else onEnterExport()
                    },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (initialLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "正在读取配置…",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else if (shown.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (query.isNotBlank()) "没有匹配的类目" else "还没有类目",
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (query.isNotBlank()) "换个关键词试试" else "点击右下角 + 添加一个类目",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                items(cardData, key = { it.index }, contentType = { "category" }) { card ->
                    CategoryCard(
                        rule = card.rule,
                        entries = card.entries,
                        disabled = card.index in disabled,
                        wildcard = card.rule.isWildcard,
                        exportMode = exportMode,
                        selected = card.index in selectedForExport,
                        selectable = !card.rule.isWildcard,
                        onEdit = { onEdit(card.index) },
                        onToggleDisabled = { onToggleDisabled(card.index) },
                        onToggleSelect = { onToggleSelect(card.index) },
                        onShowAppList = {
                            manifest = AppManifest(
                                title = card.rule.displayName,
                                categories = listOf(card.rule.category to (card.rule.friendly.ifBlank { card.rule.category })),
                                apps = card.rule.packages.orEmpty().map { AppOrigin(it, null) },
                            )
                        },
                    )
                }
            }
        }
    }

    AppListDialog(
        show = manifest != null,
        title = manifest?.title ?: "",
        categories = manifest?.categories.orEmpty(),
        apps = manifest?.apps.orEmpty(),
        resolve = { context.resolveApp(it) },
        onDismiss = { manifest = null },
    )
    ConflictDialog(
        show = conflicts != null,
        conflicts = conflicts.orEmpty(),
        onDismiss = onDismissConflicts,
    )
}

@Composable
private fun CategoriesSearch(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        label = "搜索类目名称 / 包名 / 应用名 / 组件",
        singleLine = true,
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(MiuixIcons.Clear, contentDescription = "清除")
                }
            }
        } else {
            null
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
/** Everything a [CategoryCard] renders, precomputed once per list change. */
internal class CategoryCardData(
    val index: Int,
    val rule: CategoryRule,
    val entries: List<AppEntry>,
)

@Composable
private fun CategoryCard(
    rule: CategoryRule,
    entries: List<AppEntry>,
    disabled: Boolean,
    wildcard: Boolean,
    exportMode: Boolean,
    selected: Boolean,
    selectable: Boolean,
    onEdit: () -> Unit,
    onToggleDisabled: () -> Unit,
    onShowAppList: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val blockColor = MiuixTheme.colorScheme.primaryContainer
    val contentColor = MiuixTheme.colorScheme.onPrimaryContainer
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !exportMode) 0.96f else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPress",
    )
    val exportScale by animateFloatAsState(
        if (exportMode && selectable && !selected) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "exportScale",
    )
    val cardAlpha by animateFloatAsState(
        when {
            wildcard -> 0.35f
            disabled -> 0.4f
            exportMode && selectable && !selected -> 0.45f
            else -> 1f
        },
        label = "exportAlpha",
    )
    val selectEnabled = exportMode && selectable && !disabled && !wildcard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    when {
                        selectEnabled -> onToggleSelect()
                        !exportMode && !wildcard -> onEdit()
                    }
                },
                onLongClick = if (exportMode && selectable) ({ onToggleSelect() }) else null,
            )
            .graphicsLayer {
                scaleX = scale * exportScale
                scaleY = scale * exportScale
                alpha = cardAlpha
            }
            .cardShadow(RoundedCornerShape(20.dp)),
        cornerRadius = 20.dp,
        colors = CardColors(color = blockColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rule.displayName,
                style = MiuixTheme.textStyles.title3,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                textDecoration = if (disabled) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (rule.category.isNotBlank()) {
                Text(
                    text = rule.category,
                    style = MiuixTheme.textStyles.footnote2,
                    color = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .background(contentColor.copy(alpha = 0.16f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppBadgeRow(
                apps = entries,
                onClick = onShowAppList,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            CardActionButton(
                icon = if (disabled) MiuixIcons.Undo else MiuixIcons.Delete,
                colors = when {
                    wildcard -> cardDisabledActionColors(contentColor)
                    disabled -> CardActionColors(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.onPrimary)
                    else -> cardDeleteActionColors()
                },
                onClick = onToggleDisabled,
                enabled = !wildcard && !exportMode,
            )
            }
        }
    }
}

