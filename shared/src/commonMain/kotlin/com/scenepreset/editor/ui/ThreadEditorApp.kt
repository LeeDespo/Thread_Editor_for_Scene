// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.CoreTopology
import com.scenepreset.editor.data.HistoryEntry
import com.scenepreset.editor.data.HistoryKind
import com.scenepreset.editor.data.buildCoreMask
import com.scenepreset.editor.data.parseCoreMask
import com.scenepreset.editor.data.SETTING_COLOR_MODE
import com.scenepreset.editor.data.SETTING_GUIDE_SHOWN
import com.scenepreset.editor.data.SETTING_KEY_COLOR
import com.scenepreset.editor.data.SETTING_SCENE_PATH
import com.scenepreset.editor.data.SETTING_SCENE_VERSION
import com.scenepreset.editor.data.SETTING_EXPORT_BLOCK_IGNORE
import com.scenepreset.editor.data.SETTING_SHOW_SYSTEM_APPS
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.CategoriesCodec
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ThreadsCodec
import com.scenepreset.editor.model.adaptRulesToTopology
import com.scenepreset.editor.model.convertRules
import com.scenepreset.editor.model.parseArchitectureSpec
import com.scenepreset.editor.model.detectSceneVersion
import com.scenepreset.editor.model.orderCategories
import com.scenepreset.editor.model.orderRules
import com.scenepreset.editor.model.ruleEffectivePackages
import com.scenepreset.editor.model.wildcardIssues
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

enum class Tab(val label: String) { Threads("线程"), Categories("类目"), Settings("更多") }

@Serializable
sealed interface Secondary : NavKey {
    @Serializable data object Placeholder : Secondary
    @Serializable data class Edit(val index: Int) : Secondary
    @Serializable data class EditCategory(val index: Int) : Secondary
    @Serializable data object ThemeSettings : Secondary
    @Serializable data object History : Secondary
    @Serializable data object ImportExport : Secondary
    @Serializable data object About : Secondary
    @Serializable data object Topology : Secondary
}

enum class RuleFilter { Default, Advanced }

private fun pageIndex(tab: Tab, filter: RuleFilter): Int = when {
    tab == Tab.Threads && filter == RuleFilter.Advanced -> 1
    tab == Tab.Threads -> 0
    tab == Tab.Categories -> 2
    else -> 3
}

private fun pageAt(index: Int): Pair<Tab, RuleFilter> = when (index) {
    0 -> Tab.Threads to RuleFilter.Default
    1 -> Tab.Threads to RuleFilter.Advanced
    2 -> Tab.Categories to RuleFilter.Default
    else -> Tab.Settings to RuleFilter.Default
}

private const val SNACK_BLOCK_EXPORT = "还有修改未保存，无法进入导出模式"

sealed interface VersionPrompt {
    data class Mismatch(val installed: SceneVersion) : VersionPrompt
    data object Missing : VersionPrompt
}

private data class PendingImport(val text: String, val append: Boolean, val from: SceneVersion)

@Composable
fun ThreadEditorApp(context: ThreadEditorContext) {
    var settings by remember { mutableStateOf(loadSettings(context)) }
    val repo = context.sceneRepository
    // Loading the rules / categories hits the Scene files through root shells,
    // which can take hundreds of ms — never do it during first composition.
    var rules by remember { mutableStateOf(listOf<ThreadRule>()) }
    var saved by remember { mutableStateOf(rules) }
    var initialLoading by remember { mutableStateOf(true) }
    var disabled by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var threadQuery by remember { mutableStateOf("") }
    var saveConflicts by remember { mutableStateOf<List<ConflictReport>?>(null) }
    var pendingSave by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf(listOf<CategoryRule>()) }
    var savedCategories by remember { mutableStateOf(categories) }
    var categoryDisabled by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var categoryQuery by remember { mutableStateOf("") }
    var categorySaveConflicts by remember { mutableStateOf<List<ConflictReport>?>(null) }
    var pendingCategorySave by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(Tab.Threads) }
    var homeFilter by remember { mutableStateOf(RuleFilter.Default) }
    // Export mode (long-press gesture) per page; left on page switch.
    var threadsExportMode by remember { mutableStateOf(false) }
    var threadsExportSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var categoriesExportMode by remember { mutableStateOf(false) }
    var categoriesExportSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var exportBlockIgnore by remember { mutableStateOf(context.readSetting(SETTING_EXPORT_BLOCK_IGNORE) == "true") }
    val secondaryStack = rememberNavBackStack<Secondary>(Secondary.Placeholder)
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val appScope = rememberCoroutineScope()
    var versionPrompt by remember { mutableStateOf<VersionPrompt?>(null) }
    var pendingImportConfirm by remember { mutableStateOf<PendingImport?>(null) }
    var pendingArchImport by remember { mutableStateOf<PendingImport?>(null) }
    var pendingRollback by remember { mutableStateOf<HistoryEntry?>(null) }
    var showGuide by remember { mutableStateOf(context.readSetting(SETTING_GUIDE_SHOWN) == null) }
    var effectiveTopologyState by remember { mutableStateOf(CoreTopology(coreCount = 1)) }

    // Interaction gate: rapid double-taps must not push two pages, open two
    // dialogs, or fire a navigation while a dialog is already up (the source of
    // the "entered two pages / had to press back twice" crashes).
    var lastNavAtMs by remember { mutableStateOf(0L) }
    val anyDialogShowing = pendingSave || pendingCategorySave || pendingImportConfirm != null ||
        pendingArchImport != null || pendingRollback != null || versionPrompt != null || showGuide
    fun navAllowed(): Boolean {
        val now = System.currentTimeMillis()
        if (anyDialogShowing) return false
        if (now - lastNavAtMs < 400) return false
        lastNavAtMs = now
        return true
    }
    fun navigateTo(target: Secondary) {
        if (navAllowed()) secondaryStack.add(target)
    }
    LaunchedEffect(Unit) {
        // Load configuration off the main thread so the first frame is instant.
        val (loadedRules, loadedCategories) = withContext(Dispatchers.IO) {
            loadInitialRules(repo) to repo.loadCategories()
        }
        rules = loadedRules
        saved = loadedRules
        categories = loadedCategories
        savedCategories = loadedCategories
        initialLoading = false
        // Warm icon/name cache for every referenced package off the main thread
        // so first scroll through the lists never decodes icons on the UI thread.
        context.warmAppCache(
            (loadedRules.flatMap { ruleEffectivePackages(it, loadedCategories) } +
                loadedCategories.flatMap { it.packages.orEmpty() })
                .filter { it.isNotBlank() }
                .distinct(),
        )
        effectiveTopologyState = context.effectiveTopology()
        context.writeSetting(SETTING_GUIDE_SHOWN, "true")
        context.cpuClusters()
        // Snapshot the pristine categories.json once so "reset" can restore it
        // (Scene cannot re-fetch categories.json by switching profiles).
        repo.captureCategoriesBackupIfMissing()
        repo.ensureCategories()
        if (context.ignoreVersionPrompt()) return@LaunchedEffect
        if (!context.isSceneInstalled()) {
            versionPrompt = VersionPrompt.Missing
        } else {
            val installed = context.installedSceneVersion()
            if (installed != null && installed != settings.sceneVersion) {
                versionPrompt = VersionPrompt.Mismatch(installed)
            }
        }
    }

    fun updateSettings(new: AppSettings) {
        val pathChanged = new.scenePath != settings.scenePath
        settings = new
        context.writeSetting(SETTING_COLOR_MODE, new.colorMode.toString())
        context.writeSetting(SETTING_KEY_COLOR, new.keyColorIndex.toString())
        context.writeSetting(SETTING_SCENE_PATH, new.scenePath)
        context.writeSetting(SETTING_SCENE_VERSION, new.sceneVersion.id)
        context.writeSetting(SETTING_SHOW_SYSTEM_APPS, new.showSystemApps.toString())
        context.setSceneVersion(new.sceneVersion)
        repo.updatePath(new.scenePath)
        if (pathChanged) {
            // Pointing at a different config directory means a different
            // threads.json / categories.json: reload both from disk immediately
            // (same pipeline as app start) so the UI reflects the new files.
            // Unsaved edits are discarded — the dialog warns about this.
            appScope.launch {
                val (loadedRules, loadedCategories) = withContext(Dispatchers.IO) {
                    loadInitialRules(repo) to repo.loadCategories()
                }
                rules = loadedRules
                saved = loadedRules
                categories = loadedCategories
                savedCategories = loadedCategories
                disabled = emptySet()
                categoryDisabled = emptySet()
            }
        } else {
            repo.ensureExists()
            repo.ensureCategories()
        }
    }

    fun importCategoriesJson(text: String, append: Boolean) {
        runCatching { CategoriesCodec.decode(text) }
            .onSuccess { imported ->
                val merged = if (append) categories + imported else imported
                // Same staging contract as rule imports: memory only, save FAB writes.
                categories = merged
                categoryDisabled = emptySet()
                snackbarMessage = (if (append) "已追加 ${imported.size} 条类目" else "已覆盖为 ${merged.size} 条类目") +
                    "，请点击保存以写入文件"
            }
            .onFailure { snackbarMessage = "导入类目失败：JSON 格式错误" }
    }

    fun performSave(content: List<ThreadRule>, name: String, note: String) {
        val active = content.filterIndexed { i, _ -> i !in disabled }
        val error = repo.save(active, settings.sceneVersion)
        if (error == null) {
            saved = active
            rules = active
            disabled = emptySet()
            context.appendHistory(context.buildHistoryEntry(name, note, active, settings.sceneVersion))
            snackbarMessage = "已保存到 threads.json"
        } else {
            snackbarMessage = error
        }
    }

    fun performCategorySave(content: List<CategoryRule>, name: String, note: String) {
        val active = content.filterIndexed { i, _ -> i !in categoryDisabled }
        val error = repo.saveCategories(active)
        if (error == null) {
            savedCategories = active
            categories = active
            categoryDisabled = emptySet()
            context.appendHistory(context.buildCategoryHistoryEntry(name, note, active))
            snackbarMessage = "已保存到 categories.json"
        } else {
            snackbarMessage = error
        }
    }

    fun applyImportedRules(text: String, append: Boolean, convertFrom: SceneVersion?, adapted: List<ThreadRule>? = null) {
        runCatching { ThreadsCodec.decode(text) }
            .onSuccess { imported ->
                val prepared = when {
                    adapted != null -> convertRules(adapted, convertFrom ?: settings.sceneVersion, settings.sceneVersion)
                    convertFrom != null -> convertRules(imported, convertFrom, settings.sceneVersion)
                    else -> imported
                }
                val merged = if (append) rules + prepared else prepared
                // Import only stages the rules in memory. Nothing is written to
                // threads.json here: the user reviews, then hits the save FAB
                // (which asks for a history name/note) or restore to discard.
                rules = merged
                disabled = emptySet()
                snackbarMessage = (if (append) "已追加 ${imported.size} 条规则" else "已覆盖为 ${merged.size} 条规则") +
                    "，请点击保存以写入文件"
            }
            .onFailure { snackbarMessage = "导入失败：JSON 格式错误" }
    }

    /**
     * Import pipeline: parse -> architecture prompt (with adapt / as-is / reject
     * paths) -> optional Scene version conversion -> apply.
     */
    fun requestImportRules(text: String, append: Boolean) {
        runCatching { ThreadsCodec.decode(text) }
            .onSuccess { imported ->
                val detected = detectSceneVersion(imported)
                if (detected != settings.sceneVersion) {
                    pendingImportConfirm = PendingImport(text, append, detected)
                } else {
                    pendingArchImport = PendingImport(text, append, detected)
                }
            }
            .onFailure { snackbarMessage = "导入失败：JSON 格式错误" }
    }

    fun navigateBack() {
        val now = System.currentTimeMillis()
        if (!anyDialogShowing && now - lastNavAtMs >= 400) {
            lastNavAtMs = now
            if (secondaryStack.size > 1) secondaryStack.removeLastOrNull()
        }
    }

    fun commitRule(rule: ThreadRule) {
        val current = secondaryStack.lastOrNull()
        if (current is Secondary.Edit) {
            if (current.index < 0) {
                rules = orderRules(rules + rule)
            } else {
                rules = orderRules(rules.toMutableList().apply { set(current.index, rule) })
                disabled = disabled - current.index
            }
        }
        navigateBack()
    }

    fun commitCategory(rule: CategoryRule) {
        val current = secondaryStack.lastOrNull()
        if (current is Secondary.EditCategory) {
            if (current.index < 0) {
                categories = orderCategories(categories + rule)
            } else {
                categories = orderCategories(categories.toMutableList().apply { set(current.index, rule) })
                categoryDisabled = categoryDisabled - current.index
            }
        }
        navigateBack()
    }

    fun movePage(dir: Int) {
        val idx = pageIndex(tab, homeFilter)
        val target = idx + dir
        if (target in 0..3) {
            val (t, f) = pageAt(target)
            if (t != tab) {
                // Switching pages always leaves export mode.
                threadsExportMode = false
                threadsExportSelected = emptySet()
                categoriesExportMode = false
                categoriesExportSelected = emptySet()
            }
            tab = t
            homeFilter = f
        }
    }

    AppTheme(settings = settings) {
        val swipeThreshold = LocalDensity.current.run { 80.dp.toPx() }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == Tab.Threads, onClick = { tab = Tab.Threads; homeFilter = RuleFilter.Default }, icon = MiuixIcons.Edit, label = Tab.Threads.label)
                        NavigationBarItem(selected = tab == Tab.Categories, onClick = { tab = Tab.Categories }, icon = MiuixIcons.Folder, label = Tab.Categories.label)
                        NavigationBarItem(selected = tab == Tab.Settings, onClick = { tab = Tab.Settings }, icon = MiuixIcons.More, label = Tab.Settings.label)
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                        .pointerInput(tab, homeFilter) {
                            var accum = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accum = 0f },
                                onHorizontalDrag = { change, delta ->
                                    change.consume()
                                    accum += delta
                                    if (accum < -swipeThreshold) {
                                        movePage(1)
                                        accum = 0f
                                    } else if (accum > swipeThreshold) {
                                        movePage(-1)
                                        accum = 0f
                                    }
                                },
                            )
                        },
                ) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            if (forward) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "tab",
                    ) { page ->
                        when (page) {
                            Tab.Threads -> HomeScreen(
                                rules = rules,
                                categories = categories,
                                initialLoading = initialLoading,
                                query = threadQuery,
                                onQueryChange = { threadQuery = it },
                                context = context,
                                onAdd = { navigateTo(Secondary.Edit(-1)) },
                                onEdit = { navigateTo(Secondary.Edit(it)) },
                                onToggleDisabled = { index ->
                                    disabled = if (index in disabled) disabled - index else disabled + index
                                },
                                onSave = {
                                    val active = rules.filterIndexed { i, _ -> i !in disabled }
                                    val conflicts = findConflicts(active, categories)
                                    if (conflicts.isNotEmpty()) saveConflicts = conflicts else pendingSave = true
                                },
                                onRestore = {
                                    rules = saved
                                    disabled = emptySet()
                                    snackbarMessage = "已撤销更改"
                                },
                                homeFilter = homeFilter,
                                onHomeFilterChange = { homeFilter = it },
                                disabled = disabled,
                                dirty = rules != saved || disabled.isNotEmpty(),
                                exportMode = threadsExportMode,
                                selectedForExport = threadsExportSelected,
                                onToggleSelect = { i ->
                                    threadsExportSelected = if (i in threadsExportSelected) threadsExportSelected - i else threadsExportSelected + i
                                },
                                onEnterExport = { threadsExportMode = true; threadsExportSelected = emptySet() },
                                onExitExport = { threadsExportMode = false; threadsExportSelected = emptySet() },
                                onBlockedExport = {
                                    if (!exportBlockIgnore) snackbarMessage = SNACK_BLOCK_EXPORT
                                },
                                onExport = { picked ->
                                    threadsExportMode = false
                                    threadsExportSelected = emptySet()
                                    if (picked.isEmpty()) {
                                        snackbarMessage = "未选择任何规则"
                                    } else {
                                        context.exportJson("threads.json", ThreadsCodec.encode(picked, settings.sceneVersion)) { ok ->
                                            snackbarMessage = if (ok) "已导出 ${picked.size} 条规则" else "导出失败"
                                        }
                                    }
                                },
                                conflicts = saveConflicts,
                                onDismissConflicts = { saveConflicts = null },
                            )
                            Tab.Categories -> CategoriesScreen(
                                rules = categories,
                                query = categoryQuery,
                                onQueryChange = { categoryQuery = it },
                                context = context,
                                onAdd = { navigateTo(Secondary.EditCategory(-1)) },
                                onEdit = { navigateTo(Secondary.EditCategory(it)) },
                                onToggleDisabled = { index ->
                                    categoryDisabled = if (index in categoryDisabled) categoryDisabled - index else categoryDisabled + index
                                },
                                onSave = {
                                    val active = categories.filterIndexed { i, _ -> i !in categoryDisabled }
                                    val conflicts = findCategoryConflicts(active)
                                    if (conflicts.isNotEmpty()) categorySaveConflicts = conflicts else pendingCategorySave = true
                                },
                                onRestore = {
                                    categories = savedCategories
                                    categoryDisabled = emptySet()
                                    snackbarMessage = "已撤销更改"
                                },
                                disabled = categoryDisabled,
                                dirty = categories != savedCategories || categoryDisabled.isNotEmpty(),
                                exportMode = categoriesExportMode,
                                selectedForExport = categoriesExportSelected,
                                onToggleSelect = { i ->
                                    categoriesExportSelected = if (i in categoriesExportSelected) categoriesExportSelected - i else categoriesExportSelected + i
                                },
                                onEnterExport = { categoriesExportMode = true; categoriesExportSelected = emptySet() },
                                onExitExport = { categoriesExportMode = false; categoriesExportSelected = emptySet() },
                                onBlockedExport = {
                                    if (!exportBlockIgnore) snackbarMessage = SNACK_BLOCK_EXPORT
                                },
                                onExport = { picked ->
                                    categoriesExportMode = false
                                    categoriesExportSelected = emptySet()
                                    if (picked.isEmpty()) {
                                        snackbarMessage = "未选择任何类目"
                                    } else {
                                        context.exportCategories("categories.json", CategoriesCodec.encode(picked)) { ok ->
                                            snackbarMessage = if (ok) "已导出 ${picked.size} 个类目" else "导出失败"
                                        }
                                    }
                                },
                                conflicts = categorySaveConflicts,
                                onDismissConflicts = { categorySaveConflicts = null },
                            )
                            Tab.Settings -> SettingsScreen(
                                context = context,
                                settings = settings,
                                onOpenTheme = { navigateTo(Secondary.ThemeSettings) },
                                onOpenHistory = { navigateTo(Secondary.History) },
                                onOpenImportExport = { navigateTo(Secondary.ImportExport) },
                                onOpenAbout = { navigateTo(Secondary.About) },
                                onOpenTopology = { navigateTo(Secondary.Topology) },
                                onSettingsChange = { updateSettings(it) },
                                onBack = { navigateBack() },
                                onMessage = { snackbarMessage = it },
                                onVersionChange = { v ->
                                    updateSettings(settings.copy(sceneVersion = v))
                                    context.reloadApp()
                                },
                            )
                        }
                    }
                    SaveMetaDialog(
                        show = pendingSave,
                        onConfirm = { name, note ->
                            performSave(rules, name, note)
                            pendingSave = false
                        },
                        onDismiss = { pendingSave = false },
                    )
                    SaveMetaDialog(
                        show = pendingCategorySave,
                        title = "保存类目",
                        fileHint = "categories.json",
                        onConfirm = { name, note ->
                            performCategorySave(categories, name, note)
                            pendingCategorySave = false
                        },
                        onDismiss = { pendingCategorySave = false },
                    )
                }
            }
            NavDisplay(
                backStack = secondaryStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { navigateBack() },
                transition = NavTransitions.MiuixDefault,
                effects = NavDisplayEffects.None,
            ) {
                entry<Secondary.Placeholder>(swipeDismiss = NavSwipeDirection.None) { }
                entry<Secondary.Edit>(swipeDismiss = NavSwipeDirection.None) { target ->
                    EditScreen(
                        existing = if (target.index in rules.indices) rules[target.index] else null,
                        otherRules = rules.filterIndexed { i, _ -> i != target.index && i !in disabled },
                        allCategories = categories,
                        context = context,
                        showSystemApps = settings.showSystemApps,
                        version = settings.sceneVersion,
                        onBack = { navigateBack() },
                        onCommit = { commitRule(it) },
                    )
                }
                entry<Secondary.EditCategory>(swipeDismiss = NavSwipeDirection.None) { target ->
                    CategoryEditScreen(
                        existing = if (target.index in categories.indices) categories[target.index] else null,
                        otherRules = categories.filterIndexed { i, _ -> i != target.index && i !in categoryDisabled },
                        context = context,
                        showSystemApps = settings.showSystemApps,
                        onBack = { navigateBack() },
                        onCommit = { commitCategory(it) },
                    )
                }
                entry<Secondary.ThemeSettings>(swipeDismiss = NavSwipeDirection.None) {
                    ThemeSettingsScreen(
                        settings = settings,
                        onSettingsChange = { updateSettings(it) },
                        onBack = { navigateBack() },
                    )
                }
                entry<Secondary.History>(swipeDismiss = NavSwipeDirection.None) {
                    HistoryScreen(
                        context = context,
                        onBack = { navigateBack() },
                        onRollback = { entry ->
                            if (entry.kind == HistoryKind.Categories) {
                                categories = entry.categoryRules
                                snackbarMessage = "已回溯到「${entry.name.ifBlank { entry.timestamp }}」，请点击保存以写入 categories.json"
                                navigateBack()
                            } else {
                                val from = SceneVersion.fromId(entry.sceneVersion)
                                if (entry.sceneVersion != null && from != settings.sceneVersion) {
                                    pendingRollback = entry
                                } else {
                                    rules = entry.rules
                                    snackbarMessage = "已回溯到「${entry.name.ifBlank { entry.timestamp }}」，请点击保存以写入 threads.json"
                                    navigateBack()
                                }
                            }
                        },
                        onMessage = { snackbarMessage = it },
                    )
                }
                entry<Secondary.ImportExport>(swipeDismiss = NavSwipeDirection.None) {
                    ImportExportScreen(
                        context = context,
                        rules = rules,
                        categories = categories,
                        sceneVersion = settings.sceneVersion,
                        rulesJson = ThreadsCodec.encode(rules, settings.sceneVersion),
                        categoriesJson = CategoriesCodec.encode(categories),
                        onImportRules = { text, append -> requestImportRules(text, append) },
                        onImportCategories = { text, append -> importCategoriesJson(text, append) },
                        onMessage = { snackbarMessage = it },
                        onBack = { navigateBack() },
                    )
                }
                entry<Secondary.About>(swipeDismiss = NavSwipeDirection.None) {
                    AboutScreen(
                        context = context,
                        onBack = { navigateBack() },
                    )
                }
                entry<Secondary.Topology>(swipeDismiss = NavSwipeDirection.None) {
                    TopologyScreen(
                        context = context,
                        onBack = { navigateBack() },
                        onMessage = { snackbarMessage = it },
                        onSaved = { context.reloadApp() },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            ) {
                AppSnackbarHost(
                    message = snackbarMessage,
                    onShown = { snackbarMessage = null },
                    actionLabel = if (snackbarMessage == SNACK_BLOCK_EXPORT) "永久忽略" else null,
                    onAction = {
                        if (snackbarMessage == SNACK_BLOCK_EXPORT) {
                            exportBlockIgnore = true
                            context.writeSetting(SETTING_EXPORT_BLOCK_IGNORE, "true")
                        }
                        snackbarMessage = null
                    },
                )
            }
        }

        when (val prompt = versionPrompt) {
            is VersionPrompt.Mismatch -> VersionMismatchDialog(
                show = true,
                installed = prompt.installed,
                selected = settings.sceneVersion,
                onSwitch = {
                    context.setSceneVersion(prompt.installed)
                    context.writeSetting(SETTING_SCENE_VERSION, prompt.installed.id)
                    versionPrompt = null
                    context.reloadApp()
                },
                onIgnore = { versionPrompt = null },
                onIgnoreForever = {
                    context.setIgnoreVersionPrompt(true)
                    versionPrompt = null
                },
            )
            VersionPrompt.Missing -> VersionMissingDialog(
                show = true,
                onDismiss = { versionPrompt = null },
                onIgnoreForever = {
                    context.setIgnoreVersionPrompt(true)
                    versionPrompt = null
                },
            )
            null -> Unit
        }

        pendingImportConfirm?.let { pending ->
            ConvertConfirmDialog(
                show = true,
                title = "导入文件版本不同",
                message = "检测到导入的配置文件为「${pending.from.display}」，与当前选择的「${settings.sceneVersion.display}」不一致。若继续将进行版本转换，转换可能造成部分配置丢失。",
                confirmLabel = "转换并导入",
                onConfirm = {
                    pendingImportConfirm = null
                    pendingArchImport = pending
                },
                onCancel = { pendingImportConfirm = null },
            )
        }
        pendingArchImport?.let { pending ->
            ArchitecturePromptDialog(
                show = true,
                localTopology = effectiveTopologyState,
                onAdapt = { specText ->
                    val spec = parseArchitectureSpec(specText)
                    runCatching { ThreadsCodec.decode(pending.text) }.onSuccess { imported ->
                        val topo = effectiveTopologyState
                        val prepared = adaptRulesToTopology(imported, spec, topo)
                        applyImportedRules(pending.text, pending.append, pending.from, prepared)
                    }
                    pendingArchImport = null
                },
                onImportAsIs = {
                    applyImportedRules(pending.text, pending.append, pending.from)
                    pendingArchImport = null
                },
                onCancel = { pendingArchImport = null },
            )
        }
        pendingRollback?.let { entry ->
            val fromVersion = SceneVersion.fromId(entry.sceneVersion)
            ConvertConfirmDialog(
                show = true,
                title = "回溯版本不同",
                message = "该历史记录由「${fromVersion.display}」创建，与当前选择的「${settings.sceneVersion.display}」不一致。若继续将进行版本转换，可能效果不佳。",
                confirmLabel = "转换并回溯",
                onConfirm = {
                    rules = convertRules(entry.rules, fromVersion, settings.sceneVersion)
                    snackbarMessage = "已回溯到「${entry.name.ifBlank { entry.timestamp }}」，请点击保存以写入 threads.json"
                    pendingRollback = null
                    navigateBack()
                },
                onCancel = { pendingRollback = null },
            )
        }

        GuideDialog(
            show = showGuide,
            onDismiss = { showGuide = false },
        )
    }
}

internal fun findConflicts(rules: List<ThreadRule>, categories: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    fun collect(field: (ThreadRule) -> List<String>?, kind: String) {
        val map = LinkedHashMap<String, MutableList<Int>>()
        rules.forEachIndexed { i, r ->
            field(r).orEmpty().forEach { key -> map.getOrPut(key) { mutableListOf() }.add(i) }
        }
        map.forEach { (key, indexes) ->
            if (indexes.size > 1) {
                reports += ConflictReport(kind, key, indexes.map { rules[it].displayName })
            }
        }
    }
    collect({ it.packages }, "包名")
    collect({ it.categories }, "类目")
    // Wildcard rules must be self-contained: only "*" in packages, no categories.
    rules.forEach { r ->
        r.wildcardIssues().forEach { reports += ConflictReport("通配包名", r.displayName, listOf(r.displayName)) }
    }
    // Package conflicts must consider apps imported via referenced categories.
    val effMap = LinkedHashMap<String, MutableList<Int>>()
    rules.forEachIndexed { i, r ->
        ruleEffectivePackages(r, categories).forEach { key -> effMap.getOrPut(key) { mutableListOf() }.add(i) }
    }
    effMap.forEach { (key, indexes) ->
        if (indexes.size > 1) {
            reports += ConflictReport("包名", key, indexes.map { rules[it].displayName })
        }
    }
    reports += findThreadBindConflicts(rules)
    return reports.distinctBy { it.key to it.rules }
}

internal fun findCategoryConflicts(rules: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    val idMap = LinkedHashMap<String, MutableList<Int>>()
    rules.forEachIndexed { i, r ->
        if (r.category.isNotBlank()) idMap.getOrPut(r.category) { mutableListOf() }.add(i)
    }
    idMap.forEach { (key, indexes) ->
        if (indexes.size > 1) {
            reports += ConflictReport("类目", key, indexes.map { rules[it].displayName })
        }
    }
    // Scene only allows the wildcard "*" group for a single category ("Apps").
    rules.forEachIndexed { i, r ->
        if (r.category != "Apps" && r.packages?.contains("*") == true) {
            reports += ConflictReport("通配包名", r.category, listOf(r.displayName))
        }
    }
    return reports
}

/** Intra-rule conflict checks for the thread-rule editor (only the rule being edited). */
internal fun findIntraRuleConflicts(rule: ThreadRule, categories: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    rule.wildcardIssues().forEach { reports += ConflictReport("通配包名", rule.displayName, listOf(rule.displayName)) }
    // Duplicate packages inside this single rule (own packages vs imported categories).
    val seen = HashSet<String>()
    ruleEffectivePackages(rule, categories).forEach { pkg ->
        if (!seen.add(pkg)) reports += ConflictReport("包名", pkg, listOf(rule.displayName))
    }
    reports += findThreadBindConflicts(listOf(rule))
    return reports.distinctBy { it.key to it.rules }
}

private data class ThreadCore(val thread: String, val core: String?)

/**
 * Detects same-rule thread-name conflicts. Scene matches threads by prefix
 * (e.g. "thread" also matches "thread-11"), so within a rule two core-affecting
 * thread names may not overlap unless they are bound to the same core. This
 * covers heavy_thread / heaviest_thread entries that also appear in comm: an
 * overlap with the same allocated core is not a conflict. "调度" fields
 * (ni / trashy / rr) do not affect core allocation and are excluded.
 */
private fun findThreadBindConflicts(rules: List<ThreadRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    rules.forEach { rule ->
        val c = rule.cpuset ?: return@forEach
        val entries = mutableListOf<ThreadCore>()
        fun addThreads(field: String?, core: String?) {
            field?.split(';', ',', '\n', '\t')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
                entries += ThreadCore(it, core)
            }
        }
        addThreads(c.heaviestThread, c.heaviestCores)
        addThreads(c.heavyThread, c.heavyCores)
        c.comm?.forEach { (core, list) ->
            list.forEach { t -> if (t.trim().isNotEmpty()) entries += ThreadCore(t.trim(), core) }
        }
        for (i in 0 until entries.size) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (overlapsPrefix(a.thread, b.thread) && !sameCore(a.core, b.core)) {
                    reports += ConflictReport("线程名", a.thread, listOf(rule.displayName))
                }
            }
        }
    }
    return reports.distinctBy { it.key to it.rules }
}

private fun sameCore(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return normalizeMask(a) == normalizeMask(b)
}

private fun normalizeMask(mask: String): String =
    runCatching { buildCoreMask(parseCoreMask(mask)) }.getOrDefault(mask.replace(" ", ""))

private fun overlapsPrefix(a: String, b: String): Boolean =
    a == b || a.startsWith(b) || b.startsWith(a)

private fun loadSettings(context: ThreadEditorContext): AppSettings {
    fun intOr(key: String, default: Int): Int = context.readSetting(key)?.toIntOrNull() ?: default
    return AppSettings(
        colorMode = intOr(SETTING_COLOR_MODE, 0),
        keyColorIndex = intOr(SETTING_KEY_COLOR, 1),
        scenePath = context.readSetting(SETTING_SCENE_PATH) ?: context.defaultSceneBasePath(),
        sceneVersion = SceneVersion.fromId(context.readSetting(SETTING_SCENE_VERSION)),
        showSystemApps = context.readSetting(SETTING_SHOW_SYSTEM_APPS)?.toBooleanStrict() ?: false,
    )
}

private fun loadInitialRules(repo: com.scenepreset.editor.data.SceneRepository): List<ThreadRule> {
    repo.ensureExists()
    return repo.load().rules
}

@Composable
private fun VersionMismatchDialog(
    show: Boolean,
    installed: SceneVersion,
    selected: SceneVersion,
    onSwitch: () -> Unit,
    onIgnore: () -> Unit,
    onIgnoreForever: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onIgnore) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("检测到 Scene 版本不同", style = MiuixTheme.textStyles.title3)
                Text(
                    "已安装的 Scene 为「${installed.display}」，但当前选择「${selected.display}」。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSwitch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("切换")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Button(onClick = onIgnore, modifier = Modifier.weight(1f)) {
                        Text("忽略")
                    }
                    Button(onClick = onIgnoreForever, modifier = Modifier.weight(1f)) {
                        Text("永久忽略")
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionMissingDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onIgnoreForever: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("未检测到 Scene", style = MiuixTheme.textStyles.title3)
            Text(
                "没有检测到已安装的 Scene 应用。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("知道了") }
                Button(
                    onClick = onIgnoreForever,
                    modifier = Modifier.weight(1f),
                ) { Text("永久忽略") }
            }
        }
    }
}

/**
 * Import-time architecture prompt: asks for the source device's tier sizes
 * ("1,3,3"), then offers best-effort adaptation (mask remap) when it differs
 * from this device, direct import when the core counts match, and a reject
 * path pointing at the custom-topology page otherwise.
 */
@Composable
private fun ArchitecturePromptDialog(
    show: Boolean,
    localTopology: CoreTopology,
    onAdapt: (String) -> Unit,
    onImportAsIs: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var spec by remember(show) { mutableStateOf("") }
    val parsed = parseArchitectureSpec(spec)
    val specCores = parsed.sum()
    val coreMatch = specCores == localTopology.coreCount
    WindowDialog(show = show, onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导入配置的 CPU 架构", style = MiuixTheme.textStyles.title3)
                Text(
                    "该文件对应的 CPU 从强到弱每档核心数（逗号分隔），" +
                        "例如 1 超大核 + 3 大核 + 3 小核填 1,3,3。本机当前为 ${localTopology.coreCount} 核。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceContainerVariant,
                )
            }
            TextField(
                value = spec,
                onValueChange = { spec = it },
                label = "如 1,3,3",
                singleLine = true,
            )
            if (spec.isNotBlank() && !coreMatch && parsed.isNotEmpty()) {
                Text(
                    "输入的核心数（$specCores）与本机（${localTopology.coreCount}）不同：可选择自适应转化（效果可能一般），" +
                        "或到「更多 → 自定义核心 / 核心簇」把核心数改为 $specCores 后再导入。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.error,
                )
            }
            Text(
                "自适应转化：把文件中的核心掩码按原机各档核心归档，再映射到本机同档的核心簇上" +
                    "（原机多出的档并入本机最弱簇，本机多出的簇被最弱命中档吸收）；" +
                    "comm 及各掩码字段均会转换，线程名保持不变。",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceContainerVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onAdapt(spec) },
                    modifier = Modifier.weight(1f),
                    enabled = parsed.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("自适应转化") }
            }
            Button(
                onClick = onImportAsIs,
                modifier = Modifier.fillMaxWidth(),
                enabled = parsed.isNotEmpty() && coreMatch,
            ) { Text("按原样导入") }
        }
    }
}

@Composable
private fun ConvertConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MiuixTheme.textStyles.title3)
            Text(
                message,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
