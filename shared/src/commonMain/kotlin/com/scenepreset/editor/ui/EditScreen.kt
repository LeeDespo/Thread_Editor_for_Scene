// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.data.CpuClusterInfo
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.AppCpuset
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.GameCpuset
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ruleEffectivePackages
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

private enum class RuleType { Game, App }

private data class CommEntry(val coreMask: String, val threads: List<String>)

@OptIn(ExperimentalLayoutApi::class)
private class DraftState(rule: ThreadRule?) {
    var type by mutableStateOf(
        if (rule?.isGame == true || rule?.appCpuset == null) RuleType.Game else RuleType.App,
    )
    var friendly by mutableStateOf(rule?.friendly ?: "")
    var packages by mutableStateOf(rule?.packages.orEmpty())
    var categories by mutableStateOf(rule?.categories.orEmpty())

    // game cpuset
    var unityMain by mutableStateOf(rule?.cpuset?.unityMain ?: "")
    var heaviestThreads by mutableStateOf(splitThreads(rule?.cpuset?.heaviestThread))
    var heaviestCores by mutableStateOf(rule?.cpuset?.heaviestCores ?: "")
    var heavyThreads by mutableStateOf(splitThreads(rule?.cpuset?.heavyThread))
    var heavyCores by mutableStateOf(rule?.cpuset?.heavyCores ?: "")
    var mainThread by mutableStateOf(rule?.cpuset?.mainThread ?: "")
    var gameOther by mutableStateOf(rule?.cpuset?.other ?: "")
    var trashy by mutableStateOf(rule?.cpuset?.trashy.orEmpty())
    var ni by mutableStateOf(rule?.cpuset?.ni.orEmpty())
    var rr by mutableStateOf(rule?.cpuset?.rr.orEmpty())
    var commEntries by mutableStateOf(
        rule?.cpuset?.comm?.map { (k, v) -> CommEntry(k, v) }.orEmpty(),
    )

    // app cpuset
    var appMain by mutableStateOf(rule?.appCpuset?.main ?: "")
    var appRender by mutableStateOf(rule?.appCpuset?.render ?: "")
    var appOther by mutableStateOf(rule?.appCpuset?.other ?: "")
    var appWebview by mutableStateOf(rule?.appCpuset?.webview ?: false)
    var appChildren by mutableStateOf(rule?.appCpuset?.children ?: false)

    fun toRule(version: SceneVersion): ThreadRule = when (type) {
        RuleType.Game -> ThreadRule(
            friendly = friendly.trim(),
            packages = packages.toList(),
            categories = categories.toList(),
            cpuset = GameCpuset(
                unityMain = if (version != SceneVersion.SceneN1) unityMain.ifBlank { null } else null,
                heaviestThread = if (version == SceneVersion.SceneN1) heaviestThreads.joinToString(";").ifBlank { null } else null,
                heaviestCores = if (version == SceneVersion.SceneN1) heaviestCores.ifBlank { null } else null,
                heavyThread = heavyThreads.joinToString(";").ifBlank { null },
                heavyCores = heavyCores.ifBlank { null },
                mainThread = mainThread.ifBlank { null },
                comm = commEntries
                    .filter { it.coreMask.isNotBlank() && it.threads.isNotEmpty() }
                    .associate { it.coreMask to it.threads }
                    .takeIf { it.isNotEmpty() },
                other = gameOther.ifBlank { null },
                trashy = trashy.toList(),
                ni = if (version != SceneVersion.Scene8) ni.toList() else null,
                rr = if (version == SceneVersion.Scene9) rr.toList() else null,
            ),
            appCpuset = null,
        )
        RuleType.App -> ThreadRule(
            friendly = friendly.trim(),
            packages = packages.toList(),
            categories = categories.toList(),
            cpuset = null,
            appCpuset = AppCpuset(
                main = appMain.ifBlank { null },
                render = appRender.ifBlank { null },
                other = appOther.ifBlank { null },
                webview = if (version != SceneVersion.Scene8) appWebview else null,
                children = if (version != SceneVersion.Scene8) appChildren else null,
            ),
        )
    }
}

private fun splitThreads(value: String?): List<String> =
    value?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

@Composable
fun EditScreen(
    existing: ThreadRule?,
    otherRules: List<ThreadRule>,
    allCategories: List<CategoryRule>,
    context: ThreadEditorContext,
    showSystemApps: Boolean,
    version: SceneVersion,
    onBack: () -> Unit,
    onCommit: (ThreadRule) -> Unit,
) {
    var draft by remember(existing) { mutableStateOf(DraftState(existing)) }
    var info by remember { mutableStateOf<FieldInfo?>(null) }
    var showPackages by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var commitConflicts by remember { mutableStateOf<List<ConflictReport>?>(null) }
    var installed by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var clusters by remember { mutableStateOf<List<CpuClusterInfo>>(emptyList()) }
    LaunchedEffect(showSystemApps) { installed = context.installedApps(showSystemApps) }
    LaunchedEffect(Unit) { clusters = context.cpuClusters() }
    val otherPackages = remember(otherRules, allCategories) {
        otherRules.flatMap { ruleEffectivePackages(it, allCategories) }.toSet()
    }
    val otherCategoryIds = remember(otherRules) {
        otherRules.flatMap { it.categories.orEmpty() }.toSet()
    }
    val isNew = existing == null
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = if (draft.type == RuleType.Game) 1 else 0, pageCount = { 2 })
    LaunchedEffect(pagerState.currentPage) {
        draft.type = if (pagerState.currentPage == 0) RuleType.App else RuleType.Game
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = contentInsetsTopOnly,
            topBar = {
                SmallTopAppBar(
                    title = if (isNew) "新增规则" else "编辑规则",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    val candidate = draft.toRule(version).normalized(version)
                    val conflicts = findIntraRuleConflicts(candidate, allCategories)
                    if (conflicts.isNotEmpty()) commitConflicts = conflicts else onCommit(candidate)
                }) {
                    Icon(MiuixIcons.Download, contentDescription = "保存")
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RuleTypeSelector(
                        type = draft.type,
                        onSelect = { new ->
                            if (new != draft.type) {
                                draft.type = new
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        if (new == RuleType.App) 0 else 1,
                                        animationSpec = tween(350),
                                    )
                                }
                            }
                        },
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GroupCard("基本信息", MiuixTheme.colorScheme.primary) {
                            InfoTextRow(
                                label = "备注名称",
                                value = draft.friendly,
                                onValueChange = { draft.friendly = it },
                                infoTitle = "friendly",
                                infoText = "规则的备注名，用以提升配置文件可读性，无实际功能影响。",
                                onInfo = { info = it },
                            )
                            PackagesSection(
                                packages = draft.packages,
                                onRemovePackage = { pkg -> draft.packages = draft.packages - pkg },
                                onOpenPicker = { showPackages = true },
                                resolve = { context.resolveApp(it) },
                                onInfo = { info = it },
                            )
                            CategoriesSection(
                                categories = draft.categories,
                                onRemove = { c -> draft.categories = draft.categories - c },
                                onOpenPicker = { showCategories = true },
                                onInfo = { info = it },
                            )
                        }

                        if (page == 0) {
                            AppGroups(draft = draft, clusters = clusters, version = version, onInfo = { info = it })
                        } else {
                            GameGroups(draft = draft, clusters = clusters, version = version, onInfo = { info = it })
                        }
                        // Bottom whitespace so the FAB never covers the last card.
                        Spacer(Modifier.height(96.dp))
                    }
                }
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
            current = draft.packages,
            allOtherPackages = otherPackages,
            resolve = { context.resolveApp(it) },
            installed = installed,
            onConfirm = {
                draft.packages = it
                showPackages = false
            },
            onDismiss = { showPackages = false },
        )
        CategoryPickerDialog(
            show = showCategories,
            current = draft.categories,
            allOtherCategoryIds = otherCategoryIds,
            allCategories = allCategories,
            onConfirm = {
                draft.categories = it
                showCategories = false
            },
            onDismiss = { showCategories = false },
        )
        ConflictDialog(
            show = commitConflicts != null,
            conflicts = commitConflicts.orEmpty(),
            onDismiss = { commitConflicts = null },
        )
    }
}

@Composable
private fun GameGroups(draft: DraftState, clusters: List<CpuClusterInfo>, version: SceneVersion, onInfo: (FieldInfo) -> Unit) {
    val colors = MiuixTheme.colorScheme
    if (version == SceneVersion.SceneN1) {
        GroupCard("负载最重的线程", colors.primary) {
            ChipInputField("线程名", draft.heaviestThreads, { draft.heaviestThreads = draft.heaviestThreads + it },
                { draft.heaviestThreads = draft.heaviestThreads - it }, "heaviest_thread",
                "指定负载最重的线程名。可指定多个。Scene 会对这些线程进行额外优化：可能提升 L2/L3 缓存使用优先级；提高线程优先级；与 FAS、辅助调速器进行联动，优化表现。", onInfo)
            CoreField("分配核心", draft.heaviestCores, { draft.heaviestCores = it }, "heaviest_cores",
                "指定负载最重的线程使用的 CPU 核心。", onInfo, clusters = clusters)
        }
    } else {
        GroupCard("Unity 主线程核心", colors.primary) {
            CoreField("UnityMain 核心", draft.unityMain, { draft.unityMain = it }, "unity_main",
                "UnityMain 线程可以使用的 CPU 核心。如果进程中存在多个 UnityMain 线程，则只会命中负载最高的那一个。", onInfo, clusters = clusters)
        }
    }
    GroupCard("重载线程", colors.primary) {
        ChipInputField("线程名", draft.heavyThreads, { draft.heavyThreads = draft.heavyThreads + it },
            { draft.heavyThreads = draft.heavyThreads - it }, "heavy_thread",
            "指定重载进程的线程名。可指定多个。不可与负载最重线程重复。Scene 会对这些线程进行额外优化：可能提升 L2/L3 缓存使用优先级；提高线程优先级；与 FAS、辅助调速器进行联动，优化表现。", onInfo)
        CoreField("分配核心", draft.heavyCores, { draft.heavyCores = it }, "heavy_cores",
            "指定重载线程使用的 CPU 核心。", onInfo, clusters = clusters)
    }
    GroupCard("主线程", colors.primary) {
        CoreField("主线程核心", draft.mainThread, { draft.mainThread = it }, "main_thread",
            "指定应用主线程使用的 CPU 核心。", onInfo, single = true, clusters = clusters)
    }
    GroupCard("comm", colors.primary) {
        draft.commEntries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("绑定 ${index + 1}", Modifier.weight(1f))
                IconButton(onClick = { draft.commEntries = draft.commEntries - entry }) {
                    Text("✕", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipInputField(
                        label = "绑定到该核心的线程",
                        chips = entry.threads,
                        onAdd = { t -> draft.commEntries = draft.commEntries.map {
                            if (it == entry) it.copy(threads = it.threads + t) else it
                        } },
                        onRemove = { t -> draft.commEntries = draft.commEntries.map {
                            if (it == entry) it.copy(threads = it.threads - t) else it
                        } },
                        infoTitle = "comm",
                        infoText = "将指定线程分配选定的 CPU 核心",
                        onInfo = onInfo,
                    )
                    CoreSelector(
                        mask = entry.coreMask,
                        clusters = clusters,
                        onMaskChange = { mask ->
                            draft.commEntries = draft.commEntries.map {
                                if (it == entry) it.copy(coreMask = mask) else it
                            }
                        },
                    )
                }
            }
        }
        Button(
            onClick = { draft.commEntries = draft.commEntries + CommEntry("", emptyList()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(MiuixIcons.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("添加绑定", style = MiuixTheme.textStyles.button)
        }
    }
    GroupCard("其它线程", colors.primary) {
        CoreField("其它线程核心", draft.gameOther, { draft.gameOther = it }, "other",
            "未被核心分配规则命中的线程，运行于此核心范围。", onInfo, clusters = clusters)
    }
    GroupCard("调度", colors.primary) {
        ChipInputField("垃圾线程 trashy", draft.trashy, { draft.trashy = draft.trashy + it },
            { draft.trashy = draft.trashy - it }, "trashy",
            "指定需限制性能的线程名。Scene 会通过 cpuctl 控制组向该线程写入 cpu.uclamp.max 值，限制其最高可占用的 CPU 性能，从而抑制低优先级后台进程的资源争抢。需要 Linux 内核版本 >= 5.0 支持", onInfo)
        if (version != SceneVersion.Scene8) {
            ChipInputField("提权线程 ni", draft.ni, { draft.ni = draft.ni + it },
                { draft.ni = draft.ni - it }, "ni",
                "指定需要提高调度优先级的线程名。Scene 会将其 nice 值设为 -10，使其更容易抢占 CPU 资源。默认已包含前面\"负载最重进程\"和\"重载进程\"指定的线程，只需要补充除二者以外的重要线程。", onInfo)
        }
        if (version == SceneVersion.Scene9) {
            ChipInputField("实时调度 rr", draft.rr, { draft.rr = draft.rr + it },
                { draft.rr = draft.rr - it }, "rr",
                "将匹配到的线程调度模式改为 SCHED_RR（priority=1），可能带来极致稳定的帧率，但有时会导致游戏严重卡顿甚至卡死，需谨慎使用。", onInfo)
        }
    }
}

@Composable
private fun AppGroups(draft: DraftState, clusters: List<CpuClusterInfo>, version: SceneVersion, onInfo: (FieldInfo) -> Unit) {
    val colors = MiuixTheme.colorScheme
    GroupCard("主线程", colors.primary) {
        CoreField("主线程核心", draft.appMain, { draft.appMain = it }, "main",
            "指定应用主线程使用的 CPU 核心。", onInfo, clusters = clusters)
    }
    GroupCard("渲染线程", colors.primary) {
        CoreField("渲染线程核心", draft.appRender, { draft.appRender = it }, "render",
            "指定应用渲染线程（RenderThread）使用的 CPU 核心。", onInfo, clusters = clusters)
    }
    GroupCard("其它线程", colors.primary) {
        CoreField("其它线程核心", draft.appOther, { draft.appOther = it }, "other",
            "指定除主线程与渲染线程外，其余线程使用的 CPU 核心。", onInfo, clusters = clusters)
    }
    if (version != SceneVersion.Scene8) {
        GroupCard("检测范围", colors.primary) {
            SwitchRow("检测 WebView", draft.appWebview, { draft.appWebview = it }, "webview",
                "是否开启对 WebView 沙盒进程中渲染线程的检测与绑定。", onInfo)
            SwitchRow("检测子进程", draft.appChildren, { draft.appChildren = it }, "children",
                "是否开启对应用子进程的检测与绑定。", onInfo)
        }
    }
}

@Composable
private fun CoreField(
    label: String,
    mask: String,
    onMaskChange: (String) -> Unit,
    infoTitle: String,
    infoText: String,
    onInfo: (FieldInfo) -> Unit,
    single: Boolean = false,
    clusters: List<CpuClusterInfo> = emptyList(),
) {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(label, Modifier.weight(1f))
            if (mask.isNotBlank()) {
                Text(mask, style = MiuixTheme.textStyles.footnote2, color = colors.primary)
            }
            InfoButton(title = infoTitle, text = infoText, onInfo = onInfo)
        }
        CoreSelector(mask = mask, clusters = clusters, onMaskChange = onMaskChange, single = single)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    infoTitle: String,
    infoText: String,
    onInfo: (FieldInfo) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MiuixTheme.textStyles.main, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
        Spacer(Modifier.width(4.dp))
        InfoButton(title = infoTitle, text = infoText, onInfo = onInfo)
    }
}

@Composable
private fun InfoTextRow(
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
private fun PackagesSection(
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
            InfoButton(
                "packages",
                "规则匹配的应用包名。可指定多个。",
                onInfo = onInfo,
            )
        }
        if (packages.isNotEmpty()) {
            FlowRow(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesSection(
    categories: List<String>,
    onRemove: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onInfo: (FieldInfo) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("类目 Categories")
            Spacer(Modifier.width(6.dp))
            Text("(${categories.size})", style = MiuixTheme.textStyles.footnote1, color = colors.primary)
            Spacer(Modifier.weight(1f))
            InfoButton("categories", "应用子类，由 categories.json 配置区分应用子类。可指定多个。", onInfo = onInfo)
        }
        if (categories.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { c -> Chip(label = c, onRemove = { onRemove(c) }) }
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
                Icon(MiuixIcons.Add, contentDescription = "添加类目", tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RuleTypeSelector(type: RuleType, onSelect: (RuleType) -> Unit) {
    SegmentedControl(
        items = listOf("默认", "进阶"),
        selectedIndex = if (type == RuleType.Game) 1 else 0,
        onSelect = { index -> onSelect(if (index == 0) RuleType.App else RuleType.Game) },
        modifier = Modifier.fillMaxWidth(),
    )
}
