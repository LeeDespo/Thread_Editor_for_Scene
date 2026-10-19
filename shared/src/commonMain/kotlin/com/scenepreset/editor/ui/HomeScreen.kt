// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ruleCategoryLabels
import com.scenepreset.editor.model.ruleEffectivePackages
import com.scenepreset.editor.model.hasWildcardPackage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    rules: List<ThreadRule>,
    categories: List<CategoryRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    context: ThreadEditorContext,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onToggleDisabled: (Int) -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    homeFilter: RuleFilter,
    onHomeFilterChange: (RuleFilter) -> Unit,
    disabled: Set<Int>,
    dirty: Boolean,
    exportMode: Boolean = false,
    selectedForExport: Set<Int> = emptySet(),
    onToggleSelect: (Int) -> Unit = {},
    onEnterExport: () -> Unit = {},
    onExport: (List<ThreadRule>) -> Unit = {},
    onExitExport: () -> Unit = {},
    onBlockedExport: () -> Unit = {},
    initialLoading: Boolean = false,
    conflicts: List<ConflictReport>?,
    onDismissConflicts: () -> Unit,
) {
    var appManifest by remember { mutableStateOf<AppManifest?>(null) }
    // Search lives in the app bar: a large circle rides the large title and
    // shrinks into the collapsed bar; activating it clears the titles and
    // expands a search field pinned below the bar.
    var searchActive by remember { mutableStateOf(query.isNotEmpty()) }
    val listState = rememberLazyListState()
    // Non-saveable bar state: a restored collapsed offset would fight the
    // frozen expanded content padding (and short tabs could never scroll it
    // back open).
    val scrollBehavior = MiuixScrollBehavior(
        state = remember { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) },
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )
    val canScrollState by androidx.compose.runtime.derivedStateOf {
        listState.canScrollForward || listState.canScrollBackward
    }
    // A short page cannot scroll the bar through its collapse animation, so a
    // mid-collapse offset would stick forever (and leave both search buttons
    // visible at partial alpha). Force full expansion whenever scrolling is
    // impossible.
    androidx.compose.runtime.LaunchedEffect(canScrollState) {
        if (!canScrollState) scrollBehavior.state.heightOffset = 0f
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = if (searchActive) "" else "线程",
                    largeTitle = if (searchActive) "" else "线程",
                    subtitle = if (searchActive) "" else "共 ${rules.size} 条线程规则",
                    navigationIcon = {},
                    actions = {
                        if (!searchActive) {
                            Box(
                                Modifier.graphicsLayer {
                                    alpha = ((scrollBehavior.state.collapsedFraction - 0.2f) * 4f).coerceIn(0f, 1f)
                                },
                            ) {
                                CircularIconAction(
                                    icon = MiuixIcons.Search,
                                    contentDescription = "搜索",
                                    onClick = { searchActive = true },
                                )
                            }
                        }
                    },
                        scrollBehavior = scrollBehavior,
                    bottomContent = {
                        AnimatedVisibility(
                            visible = searchActive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SearchFieldRow(
                                query = query,
                                onQueryChange = onQueryChange,
                                onClose = {
                                    searchActive = false
                                    onQueryChange("")
                                },
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                HomeFabStack(
                    exportMode = exportMode,
                    dirty = dirty,
                    addLabel = "添加规则",
                    onSave = onSave,
                    onRestore = onRestore,
                    onAdd = onAdd,
                    onExitExport = onExitExport,
                    onExportSelected = { onExport(selectedForExport.mapNotNull { rules.getOrNull(it) }) },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                HomeFilterSegment(
                    filter = homeFilter,
                    onSelect = onHomeFilterChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AnimatedContent(
                    targetState = homeFilter,
                    transitionSpec = {
                        if (initialState == RuleFilter.Default && targetState == RuleFilter.Advanced) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "homeFilter",
                ) { filter ->
                    val shown = remember(filter, rules, query, categories, context) {
                        rules.mapIndexed { index, rule -> index to rule }
                            .filter { (_, rule) ->
                                val typeOk = if (filter == RuleFilter.Advanced) rule.isGame else !rule.isGame
                                typeOk && (
                                    query.isBlank() ||
                                        rule.displayName.contains(query, true) ||
                                        ruleEffectivePackages(rule, categories).any { pkg ->
                                            pkg.contains(query, true) || context.resolveApp(pkg).label.contains(query, true)
                                        } ||
                                        ruleCategoryLabels(rule, categories).any { (id, label) ->
                                            id.contains(query, true) || label.contains(query, true)
                                        }
                                    )
                            }
                            .sortedWith(
                                compareBy<Pair<Int, ThreadRule>> {
                                    if (it.second.hasWildcardPackage()) 2 else if (it.second.isGame) 1 else 0
                                }.thenBy { it.first },
                            )
                    }
                    // Pre-resolve every card's derived data once per list change so
                    // RuleCard no longer recomputes catLabels/packages (and icon
                    // LRU lookups) on every recomposition while flinging.
                    val cardData = remember(shown, categories, context) {
                        shown.map { (index, rule) ->
                            RuleCardData(
                                index = index,
                                rule = rule,
                                catLabels = ruleCategoryLabels(rule, categories),
                                packages = ruleEffectivePackages(rule, categories),
                                entries = ruleEffectivePackages(rule, categories).map { context.resolveApp(it) },
                            )
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    val shortList = shown.size <= 4
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .exportLongPress(enabled = { !exportMode }) {
                                if (dirty) onBlockedExport() else onEnterExport()
                            }
                            .edgeFade(top = 22.dp, topStrength = { barFadeStrength(scrollBehavior.state.collapsedFraction) }),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp,
                            bottom = if (shortList) maxHeight else 240.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (initialLoading) {
                            item { LoadingHint() }
                        } else if (shown.isEmpty()) {
                            item { EmptyHint(hasQuery = query.isNotBlank()) }
                        }
                        items(
                            items = cardData,
                            key = { it.index },
                            contentType = { "rule" },
                        ) { card ->
                            RuleCard(
                                rule = card.rule,
                                catLabels = card.catLabels,
                                packages = card.packages,
                                entries = card.entries,
                                disabled = card.index in disabled,
                                exportMode = exportMode,
                                selected = card.index in selectedForExport,
                                selectable = ruleSelectable(card.rule),
                                onEdit = { onEdit(card.index) },
                                onToggleDisabled = { onToggleDisabled(card.index) },
                                onShowAppList = { appManifest = buildAppManifest(card.rule, categories) },
                                onToggleSelect = { onToggleSelect(card.index) },
                            )
                        }
                    }
                    }
                }
            }
        }
        if (!searchActive) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp, end = 16.dp)
                    .graphicsLayer {
                        val f = scrollBehavior.state.collapsedFraction
                        alpha = (1f - f * 5f).coerceIn(0f, 1f)
                        translationY = scrollBehavior.state.heightOffset
                    },
            ) {
                LargeSearchButton(onClick = { searchActive = true })
            }
        }
    }

    AppListDialog(
        show = appManifest != null,
        title = appManifest?.title ?: "",
        categories = appManifest?.categories.orEmpty(),
        apps = appManifest?.apps.orEmpty(),
        resolve = { context.resolveApp(it) },
        onDismiss = { appManifest = null },
    )
    ConflictDialog(
        show = conflicts != null,
        conflicts = conflicts.orEmpty(),
        onDismiss = onDismissConflicts,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
/** Everything a [RuleCard] renders, precomputed once per list change. */
internal class RuleCardData(
    val index: Int,
    val rule: ThreadRule,
    val catLabels: List<Pair<String, String>>,
    val packages: List<String>,
    val entries: List<AppEntry>,
)

@Composable
private fun RuleCard(
    rule: ThreadRule,
    catLabels: List<Pair<String, String>>,
    packages: List<String>,
    entries: List<AppEntry>,
    disabled: Boolean,
    exportMode: Boolean,
    selected: Boolean,
    selectable: Boolean,
    onEdit: () -> Unit,
    onToggleDisabled: () -> Unit,
    onShowAppList: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val isAdvanced = rule.isGame
    val (blockColor, contentColor) =
        if (isAdvanced) {
            MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
        } else {
            MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onSecondaryContainer
        }
    // Press feedback: card sinks in slightly while pressed (quick spring in,
    // gentle spring back on release).
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
            exportMode && !selectable -> 0.35f
            disabled -> 0.4f
            exportMode && selectable && !selected -> 0.45f
            else -> 1f
        },
        label = "exportAlpha",
    )
    val selectEnabled = exportMode && selectable && !disabled
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    when {
                        selectEnabled -> onToggleSelect()
                        !exportMode && !disabled -> onEdit()
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(contentColor.copy(alpha = 0.9f), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = rule.displayName,
                style = MiuixTheme.textStyles.title3,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                textDecoration = if (disabled) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            RuleTypeBadge(rule = rule, contentColor = contentColor)
            }

            if (catLabels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                catLabels.forEach { (id, label) ->
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = contentColor.copy(alpha = 0.9f),
                        modifier = Modifier
                            .background(contentColor.copy(alpha = 0.16f), CircleShape)
                            .clickable(onClick = onShowAppList)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        if (packages.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppBadgeRow(
                apps = entries,
                onClick = onShowAppList,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            CardActionButton(
                icon = if (disabled) MiuixIcons.Undo else MiuixIcons.Delete,
                colors = if (disabled) {
                    // The restore action must stay clearly visible on a dimmed card.
                    CardActionColors(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.onPrimary)
                } else {
                    cardDeleteActionColors()
                },
                onClick = onToggleDisabled,
                // Export mode is selection-only: stop/restore stays visible but not tappable.
                enabled = !exportMode,
            )
            }
        }
    }
}

@Composable
private fun HomeFilterSegment(filter: RuleFilter, onSelect: (RuleFilter) -> Unit, modifier: Modifier = Modifier) {
    SegmentedControl(
        items = listOf("默认", "进阶"),
        selectedIndex = if (filter == RuleFilter.Advanced) 1 else 0,
        onSelect = { index -> onSelect(if (index == 0) RuleFilter.Default else RuleFilter.Advanced) },
        modifier = modifier,
    )
}

@Composable
private fun RuleTypeBadge(rule: ThreadRule, contentColor: Color) {
    val label = when {
        rule.isGame -> "进阶"
        rule.appCpuset != null -> "默认"
        else -> "规则"
    }
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote2,
        color = contentColor.copy(alpha = 0.85f),
        modifier = Modifier
            .background(contentColor.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun ruleSelectable(rule: ThreadRule): Boolean = !rule.hasWildcardPackage()

@Composable
private fun LoadingHint() {
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

@Composable
private fun EmptyHint(hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasQuery) "没有匹配的规则" else "还没有线程规则",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasQuery) "换个关键词试试" else "点击右下角 + 添加一条",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun buildAppManifest(rule: ThreadRule, categories: List<CategoryRule>): AppManifest {
    val cats = ruleCategoryLabels(rule, categories)
    val own = rule.packages.orEmpty().map { it.trim() }.toSet()
    val eff = ruleEffectivePackages(rule, categories)
    val apps = eff.map { pkg ->
        val catId = if (pkg in own) {
            null
        } else {
            cats.firstOrNull { (id, _) ->
                categories.firstOrNull { it.category == id }?.packages.orEmpty().contains(pkg)
            }?.first
        }
        AppOrigin(pkg, catId)
    }
    return AppManifest(rule.displayName, cats, apps)
}
