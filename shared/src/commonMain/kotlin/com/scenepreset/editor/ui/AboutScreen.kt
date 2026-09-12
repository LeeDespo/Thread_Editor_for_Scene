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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.ThreadEditorContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** An acknowledgement entry: what it is and where it lives online. */
private data class CreditEntry(
    val name: String,
    val summary: String,
    val url: String,
    val linkLabel: String,
)

private val APP_REPO_URL = "https://github.com/LeeDespo/Thread_Editor_for_Scene"

/** Reference documents shown at the bottom of the About page. */
private data class ReferenceEntry(val name: String, val url: String)

private val references = listOf(
    ReferenceEntry("Miuix", "https://compose-miuix-ui.github.io/miuix/zh_CN/"),
    ReferenceEntry("Scene N1 开发者文档", "https://download.omarea.com/#/versions?folder=sceneN1&dir=docs/"),
    ReferenceEntry("Scene 9 开发者文档", "https://download.omarea.com/#/versions?folder=scene9&dir=docs/"),
    ReferenceEntry("Scene 8 开发者文档", "https://download.omarea.com/#/versions?folder=scene8&dir=docs/"),
)

/** Third-party projects this app builds on, with links to their home/author pages. */
private val credits = listOf(
    CreditEntry(
        name = "Scene",
        summary = "面向安卓系统的系统优化和性能调校工具箱，本项目的编辑对象 threads.json / categories.json 的来源（作者 嘟嘟斯基）",
        url = "https://omarea.com/#/",
        linkLabel = "访问官网",
    ),
    CreditEntry(
        name = "Miuix / compose-miuix-ui",
        summary = "HyperOS 设计语言 Compose 组件库，本项目 UI 的基石",
        url = "https://github.com/compose-miuix-ui/miuix",
        linkLabel = "访问项目仓库",
    ),
    CreditEntry(
        name = "MaterialKolor",
        summary = "Material You / HCT 动态配色算法，经 Miuix 传递使用，实现莫奈取色",
        url = "https://github.com/jordond/MaterialKolor",
        linkLabel = "访问项目仓库",
    ),
    CreditEntry(
        name = "自定义线程编辑器",
        summary = "基于 KernelSU WebUI 的 threads.json 编辑模块，本应用开发的灵感来源（作者 gyimo）",
        url = "https://www.coolapk.com/feed/73634984?s=YTUyMjVkODcxNDRmM2U2ZzZhYTBkYTlkegi1656",
        linkLabel = "查看原帖",
    ),
    CreditEntry(
        name = "Kotlin 与 Compose Multiplatform",
        summary = "JetBrains 出品的语言与跨平台 UI 框架",
        url = "https://www.jetbrains.com/",
        linkLabel = "访问 JetBrains",
    ),
    CreditEntry(
        name = "AndroidX",
        summary = "activity-compose、navigationevent-compose 等库",
        url = "https://developer.android.com/jetpack/androidx",
        linkLabel = "访问 AndroidX",
    ),
    CreditEntry(
        name = "kotlinx.serialization",
        summary = "Kotlin 多平台 JSON 编解码",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        linkLabel = "访问项目仓库",
    ),
)

/**
 * The About page: developer entry, acknowledgement list and reference links.
 * Styled like every other secondary page (large title TopAppBar + grouped cards).
 */
@Composable
fun AboutScreen(
    context: ThreadEditorContext,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            TopAppBar(
                title = "关于",
                largeTitle = "关于",
                subtitle = "Thread Editor",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SmallTitle("开发者") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    ArrowPreference(
                        title = "LeeDespo",
                        summary = APP_REPO_URL.removePrefix("https://"),
                        onClick = { context.openUrl(APP_REPO_URL) },
                    )
                }
            }

            item { SmallTitle("鸣谢") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        credits.forEach { credit ->
                            ArrowPreference(
                                title = credit.name,
                                summary = credit.summary,
                                onClick = { context.openUrl(credit.url) },
                            )
                        }
                    }
                }
            }

            item { SmallTitle("参考资料") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Column {
                        references.forEach { ref ->
                            ArrowPreference(
                                title = ref.name,
                                summary = ref.url.removePrefix("https://").removePrefix("http://"),
                                onClick = { context.openUrl(ref.url) },
                            )
                        }
                    }
                }
            }

            item { SmallTitle("开源协议") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    ArrowPreference(
                        title = "GNU General Public License v3.0",
                        summary = "www.gnu.org/licenses/gpl-3.0",
                        onClick = { context.openUrl("https://www.gnu.org/licenses/gpl-3.0") },
                    )
                }
            }
        }
    }
}
