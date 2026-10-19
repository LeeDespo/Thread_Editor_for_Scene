// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.CoreTopology
import com.scenepreset.editor.data.HistoryEntry
import com.scenepreset.editor.data.HistoryKind
import com.scenepreset.editor.data.SETTING_COLOR_MODE
import com.scenepreset.editor.data.SETTING_CORE_TEXT_ENTRY
import com.scenepreset.editor.data.SETTING_GUIDE_SHOWN
import com.scenepreset.editor.data.SETTING_KEY_COLOR
import com.scenepreset.editor.data.SETTING_SCENE_PATH
import com.scenepreset.editor.data.SETTING_SCENE_VERSION
import com.scenepreset.editor.data.SETTING_EXPORT_BLOCK_IGNORE
import com.scenepreset.editor.data.SETTING_SHOW_SYSTEM_APPS
import com.scenepreset.editor.data.SceneRepository
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.CategoriesCodec
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ThreadsCodec
import com.scenepreset.editor.model.adaptRulesToTopology
import com.scenepreset.editor.model.convertRules
import com.scenepreset.editor.model.parseArchitectureSpec
import com.scenepreset.editor.model.referencedCoreCount
import com.scenepreset.editor.model.detectSceneVersion
import com.scenepreset.editor.model.orderCategories
import com.scenepreset.editor.model.orderRules
import com.scenepreset.editor.model.ruleEffectivePackages
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

/**
 * A threads.json import waiting in the confirmation pipeline. Carries the
 * already-decoded rules so each stage (version confirm → architecture prompt →
 * apply) works on the same parsed list instead of re-decoding the text.
 */
private data class PendingImport(val rules: List<ThreadRule>, val append: Boolean, val from: SceneVersion)

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
    // Saving shells out through su (~100ms+); the flags close the race between
    // "dialog confirmed" and "rules actually replaced" for navigation.
    var savingThreads by remember { mutableStateOf(false) }
    var savingCategories by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf(listOf<CategoryRule>()) }
    var savedCategories by remember { mutableStateOf(categories) }
    var categoryDisabled by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var categoryQuery by remember { mutableStateOf("") }
    var categorySaveConflicts by remember { mutableStateOf<List<ConflictReport>?>(null) }
    var pendingCategorySave by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(Tab.Threads) }
    var homeFilter by remember { mutableStateOf(RuleFilter.Default) }
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
    // Core count the loaded / imported file's masks reference. Only an overflow
    // (more cores than this device has, custom topology counting as the device's
    // own count) forces mask text entry on — a mask can then hold ids the core
    // buttons cannot express — and it stays locked on in that case.
    val fileCoreCount = remember(rules, effectiveTopologyState.coreCount) { referencedCoreCount(rules) }
    val maskEntryLocked = fileCoreCount != null && fileCoreCount > effectiveTopologyState.coreCount
    val coreTextEntry = settings.coreTextEntry || maskEntryLocked
    val maskCoreLimit = maxOf(effectiveTopologyState.coreCount, fileCoreCount ?: 0)
    var lastNavAtMs by remember { mutableStateOf(0L) }
    val anyDialogShowing = pendingSave || pendingCategorySave || pendingImportConfirm != null ||
        pendingArchImport != null || pendingRollback != null || versionPrompt != null || showGuide ||
        savingThreads || savingCategories
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
    fun exitExportModes() {
        threadsExportMode = false
        threadsExportSelected = emptySet()
        categoriesExportMode = false
        categoriesExportSelected = emptySet()
    }
    fun updateSettings(new: AppSettings) {
        val pathChanged = new.scenePath != settings.scenePath
        settings = new
        context.writeSetting(SETTING_COLOR_MODE, new.colorMode.toString())
        context.writeSetting(SETTING_KEY_COLOR, new.keyColorIndex.toString())
        context.writeSetting(SETTING_SCENE_PATH, new.scenePath)
        context.writeSetting(SETTING_SCENE_VERSION, new.sceneVersion.id)
        context.writeSetting(SETTING_SHOW_SYSTEM_APPS, new.showSystemApps.toString())
        context.writeSetting(SETTING_CORE_TEXT_ENTRY, new.coreTextEntry.toString())
        context.setSceneVersion(new.sceneVersion)
        repo.updatePath(new.scenePath)
        if (pathChanged) {
            appScope.launch {
                val (loadedRules, loadedCategories) = withContext(Dispatchers.IO) {
                    loadInitialRules(repo) to orderCategories(repo.loadCategories())
                }
                rules = loadedRules
                saved = loadedRules
                categories = loadedCategories
                savedCategories = loadedCategories
                disabled = emptySet()
                categoryDisabled = emptySet()
                exitExportModes()
            }
        }
    }

    /**
     * Import / read-time guard. Only an overflow matters: when the file's masks
     * reference MORE cores than this device has (custom topology counts as the
     * device's own count, see effectiveTopology), mask text entry is turned on
     * so those ids are not silently trimmed by the core buttons, and it stays
     * locked on ([maskEntryLocked]). A file referencing FEWER cores is left
     * alone — every id it uses is pickable with the buttons.
     */
    fun syncMaskEntryToCoreCount(file: List<ThreadRule>) {
        val fileCores = referencedCoreCount(file) ?: return
        val localCores = effectiveTopologyState.coreCount
        if (fileCores <= localCores) return
        if (!settings.coreTextEntry) updateSettings(settings.copy(coreTextEntry = true))
        val note = "文件引用的核心数（$fileCores）超过本机的 $localCores 核，已开启「手动输入核心掩码」以免编号被丢掉"
        snackbarMessage = snackbarMessage?.let { "$it；$note" } ?: note
    }

    LaunchedEffect(Unit) {
        val (loadedRules, loadedCategories) = withContext(Dispatchers.IO) {
            loadInitialRules(repo) to orderCategories(repo.loadCategories())
        }
        rules = loadedRules
        saved = loadedRules
        categories = loadedCategories
        savedCategories = loadedCategories
        initialLoading = false
        context.warmAppCache(
            (loadedRules.flatMap { ruleEffectivePackages(it, loadedCategories) } +
                loadedCategories.flatMap { it.packages.orEmpty() })
                .filter { it.isNotBlank() }
                .distinct(),
        )
        effectiveTopologyState = context.effectiveTopology()
        syncMaskEntryToCoreCount(loadedRules)
        context.cpuClusters()
        withContext(Dispatchers.IO) {
            repo.captureCategoriesBackupIfMissing()
            repo.ensureCategories()
        }
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

    fun importCategoriesJson(text: String, append: Boolean) {
        runCatching { CategoriesCodec.decode(text) }
            .onSuccess { imported ->
                val merged = if (append) categories + imported else imported
                categories = merged
                categoryDisabled = emptySet()
                exitExportModes()
                snackbarMessage = (if (append) "已追加 ${imported.size} 条类目" else "已覆盖为 ${merged.size} 条类目") +
                    "，请点击保存以写入文件"
            }
            .onFailure { snackbarMessage = "导入类目失败：JSON 格式错误" }
    }

    fun performSave(content: List<ThreadRule>, name: String, note: String) {
        if (savingThreads) return
        savingThreads = true
        val active = content.filterIndexed { i, _ -> i !in disabled }
        appScope.launch {
            val error = withContext(Dispatchers.IO) {
                val err = repo.save(active, settings.sceneVersion)
                if (err == null) {
                    context.appendHistory(context.buildHistoryEntry(name, note, active, settings.sceneVersion))
                }
                err
            }
            savingThreads = false
            if (error == null) {
                saved = active
                rules = active
                disabled = emptySet()
                snackbarMessage = "已保存到 threads.json"
            } else {
                snackbarMessage = error
            }
        }
    }

    fun performCategorySave(content: List<CategoryRule>, name: String, note: String) {
        if (savingCategories) return
        savingCategories = true
        val active = content.filterIndexed { i, _ -> i !in categoryDisabled }
        appScope.launch {
            val error = withContext(Dispatchers.IO) {
                val err = repo.saveCategories(active)
                if (err == null) {
                    context.appendHistory(context.buildCategoryHistoryEntry(name, note, active))
                }
                err
            }
            savingCategories = false
            if (error == null) {
                savedCategories = active
                categories = active
                categoryDisabled = emptySet()
                snackbarMessage = "已保存到 categories.json"
            } else {
                snackbarMessage = error
            }
        }
    }

    fun applyImportedRules(imported: List<ThreadRule>, append: Boolean, convertFrom: SceneVersion?, adapted: List<ThreadRule>? = null) {
        val prepared = when {
            adapted != null -> convertRules(adapted, convertFrom ?: settings.sceneVersion, settings.sceneVersion)
            convertFrom != null -> convertRules(imported, convertFrom, settings.sceneVersion)
            else -> imported
        }
        val merged = if (append) rules + prepared else prepared
        rules = merged
        disabled = emptySet()
        exitExportModes()
        snackbarMessage = (if (append) "已追加 ${imported.size} 条规则" else "已覆盖为 ${merged.size} 条规则") +
            "，请点击保存以写入文件"
    }

    fun requestImportRules(text: String, append: Boolean) {
        runCatching { ThreadsCodec.decode(text) }
            .onSuccess { imported ->
                val detected = detectSceneVersion(imported)
                val pending = PendingImport(imported, append, detected)
                if (detected != settings.sceneVersion) {
                    pendingImportConfirm = pending
                } else {
                    pendingArchImport = pending
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
            val old = rules
            val updated = if (current.index < 0) {
                orderRules(old + rule)
            } else {
                orderRules(old.toMutableList().apply { set(current.index, rule) })
            }
            rules = updated
            disabled = remapIndexes(old, updated, disabled) - updated.indexOfFirst { it === rule }
        }
        navigateBack()
    }

    fun commitCategory(rule: CategoryRule) {
        val current = secondaryStack.lastOrNull()
        if (current is Secondary.EditCategory) {
            val old = categories
            val updated = if (current.index < 0) {
                orderCategories(old + rule)
            } else {
                orderCategories(old.toMutableList().apply { set(current.index, rule) })
            }
            categories = updated
            categoryDisabled = remapIndexes(old, updated, categoryDisabled) - updated.indexOfFirst { it === rule }
        }
        navigateBack()
    }

    fun movePage(dir: Int) {
        val idx = pageIndex(tab, homeFilter)
        val target = idx + dir
        if (target in 0..3) {
            val (t, f) = pageAt(target)
            if (t != tab) {
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
                                coreTextEntryLocked = maskEntryLocked,
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
                        coreTextEntry = coreTextEntry,
                        maskCoreLimit = maskCoreLimit,
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
                                categoryDisabled = emptySet()
                                exitExportModes()
                                snackbarMessage = "已回溯到「${entry.name.ifBlank { entry.timestamp }}」，请点击保存以写入 categories.json"
                                navigateBack()
                            } else {
                                val from = SceneVersion.fromId(entry.sceneVersion)
                                if (entry.sceneVersion != null && from != settings.sceneVersion) {
                                    pendingRollback = entry
                                } else {
                                    rules = entry.rules
                                    disabled = emptySet()
                                    exitExportModes()
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
                fileCoreCount = referencedCoreCount(pending.rules),
                onAdapt = { specText ->
                    val spec = parseArchitectureSpec(specText)
                    val prepared = adaptRulesToTopology(pending.rules, spec, effectiveTopologyState)
                    applyImportedRules(pending.rules, pending.append, pending.from, prepared)
                    syncMaskEntryToCoreCount(pending.rules)
                    pendingArchImport = null
                },
                onImportAsIs = {
                    applyImportedRules(pending.rules, pending.append, pending.from)
                    syncMaskEntryToCoreCount(pending.rules)
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
                    disabled = emptySet()
                    exitExportModes()
                    snackbarMessage = "已回溯到「${entry.name.ifBlank { entry.timestamp }}」，请点击保存以写入 threads.json"
                    pendingRollback = null
                    navigateBack()
                },
                onCancel = { pendingRollback = null },
            )
        }

        GuideDialog(
            show = showGuide,
            onDismiss = {
                showGuide = false
                context.writeSetting(SETTING_GUIDE_SHOWN, "true")
            },
        )
    }
}

private fun loadSettings(context: ThreadEditorContext): AppSettings {
    fun intOr(key: String, default: Int): Int = context.readSetting(key)?.toIntOrNull() ?: default
    return AppSettings(
        colorMode = intOr(SETTING_COLOR_MODE, 0),
        keyColorIndex = intOr(SETTING_KEY_COLOR, 1),
        scenePath = context.readSetting(SETTING_SCENE_PATH) ?: context.defaultSceneBasePath(),
        sceneVersion = SceneVersion.fromId(context.readSetting(SETTING_SCENE_VERSION)),
        showSystemApps = context.readSetting(SETTING_SHOW_SYSTEM_APPS)?.toBooleanStrict() ?: false,
        coreTextEntry = context.readSetting(SETTING_CORE_TEXT_ENTRY)?.toBooleanStrict() ?: false,
    )
}

private fun loadInitialRules(repo: SceneRepository): List<ThreadRule> {
    repo.ensureExists()
    // Canonicalize the order immediately: every later index-based state
    // (disabled set, edit navigation) assumes the ordering orderRules produces.
    return orderRules(repo.load().rules)
}

/**
 * Re-maps list indexes from [old] to [new] by object identity (orderRules /
 * orderCategories build a new list but keep the same element references).
 * Indexes whose object no longer exists in [new] are dropped.
 */
private fun <T> remapIndexes(old: List<T>, new: List<T>, indexes: Set<Int>): Set<Int> =
    indexes.mapNotNull { i ->
        val obj = old.getOrNull(i) ?: return@mapNotNull null
        new.indexOfFirst { it === obj }.takeIf { it >= 0 }
    }.toSet()

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
 * Import-time architecture prompt: asks for the tiers of the chip the imported
 * file was authored for (strongest first, "1,3,4"), then offers best-effort
 * adaptation (mask remap) or a straight as-is import. A core-count mismatch
 * never blocks the import here — the app switches the editor to mask text entry
 * after the fact instead.
 */
@Composable
private fun ArchitecturePromptDialog(
    show: Boolean,
    localTopology: CoreTopology,
    fileCoreCount: Int?,
    onAdapt: (String) -> Unit,
    onImportAsIs: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var spec by remember(show) { mutableStateOf("") }
    val parsed = parseArchitectureSpec(spec)
    val specCores = parsed.sum()
    WindowDialog(show = show, onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导入配置的 CPU 架构", style = MiuixTheme.textStyles.title3)
                Text(
                    "填该文件对应芯片的档位：从强到弱每档核心数，逗号分隔，" +
                        "例如 1 超大核 + 3 大核 + 4 小核填 1,3,4。这里报的是导入文件所属机型的档位，不是你本机的。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceContainerVariant,
                )
            }
            TextField(
                value = spec,
                onValueChange = { spec = it },
                label = "如 1,3,4",
                singleLine = true,
            )
            if (fileCoreCount != null) {
                Text(
                    "文件中的掩码引用到核心 ${fileCoreCount - 1}，即该文件至少按 $fileCoreCount 核编写（仅供参考，不会覆盖你填的档位）。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceContainerVariant,
                )
            }
            if (parsed.isNotEmpty() && specCores != localTopology.coreCount) {
                Text(
                    "输入总数 $specCores 与本机记录的 ${localTopology.coreCount} 核不同：可以直接按原样导入，也可以自适应转化。" +
                        "若文件引用的核心编号超过本机核心数，导入后会自动开启「手动输入核心掩码」，以免这些编号被丢掉。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceContainerVariant,
                )
            }
            Text(
                "自适应转化：按原机各档核心的实际编号归档文件中的掩码，再映射到本机同档的核心簇上" +
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
