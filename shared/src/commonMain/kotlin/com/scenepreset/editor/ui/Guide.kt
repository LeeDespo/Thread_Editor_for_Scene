// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val GUIDE_COUNTDOWN_SECONDS = 10

/**
 * First-launch guide content, rendered as markdown in [GuideDialog].
 * Mirrors the "如何使用" section of the project README.
 */
private const val FIRST_USE_GUIDE = """
本应用用于编辑 Scene 的 `threads.json`（线程 / 核心分配）与 `categories.json`（应用类目），**需要 root 权限**。

## 编辑线程规则
- 在“线程”页点右下角按钮新增规则，或点卡片上的编辑按钮进入编辑页面。
- 可切换默认 / 进阶模式，按需分配核心与线程。
- 可在包名中添加通配符 `*`，为所有未被其他规则命中的应用提供核心分配。

## 编辑类目
- 与“编辑线程规则”类似。
- 类目所包含的应用包名，可通过线程规则的类目 `Categories` 项引用对应的类目 ID 导入。
- Scene 对官方自带的类目 ID 有不同的调度策略，强烈建议不要删除或更改官方原有的类目 ID，建议只在原有的类目卡片中按需增添应用包名。

## 保存
! 不会自动保存，请点击线程 / 类目主页面右下角的保存按钮！
! 编辑 / 新增规则后，同样需要回到一级页面点保存，才会真正写入配置！
- 保存前会做包名 / 类目 / 线程名冲突检测；确认后写入 `threads.json` / `categories.json`，并生成一条可回溯的历史记录（可命名或备注）。

## 其他
- 主题：更多 → 主题设置，切换模式与强调色；开启 Monet 后实时生效。
- 导入导出：更多 → 导入 / 导出，进行文件操作；导入时按版本自动转换。
- 锁定文件：更多 → 锁定文件，防止 Scene 覆盖配置。
"""

/**
 * First-launch usage guide. The dismiss button (and outside-click dismissal)
 * stays disabled for [GUIDE_COUNTDOWN_SECONDS] so the guide is actually read.
 */
@Composable
fun GuideDialog(show: Boolean, onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    var remaining by remember { mutableStateOf(GUIDE_COUNTDOWN_SECONDS) }
    LaunchedEffect(show) {
        if (show) {
            remaining = GUIDE_COUNTDOWN_SECONDS
            while (remaining > 0) {
                delay(1000)
                remaining--
            }
        }
    }
    val ready = remaining <= 0
    WindowDialog(show = show, onDismissRequest = { if (ready) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "使用指南",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceContainerHighest,
            )
            MarkdownText(
                markdown = FIRST_USE_GUIDE,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Button(
                onClick = onDismiss,
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    if (ready) "开始使用" else "请阅读（${remaining}s 后可关闭）",
                    style = MiuixTheme.textStyles.button,
                )
            }
        }
    }
}

/**
 * Minimal markdown renderer for in-app text: `#`/`##`/`###` headings, `- `
 * bullets, `**bold**` and `` `inline code` `` spans. Covers exactly the subset
 * used by the first-use guide, without pulling in an external dependency.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val codeBackground = colors.primaryContainer.copy(alpha = 0.45f)
    val codeColor = colors.onSurfaceContainerHighest
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(3.dp))
                line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") -> Text(
                    text = inlineMarkdown(line.substringAfter(' ').trim(), codeBackground, codeColor),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceContainerHighest,
                )
                line.startsWith("- ") -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "•",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = inlineMarkdown(line.removePrefix("- ").trim(), codeBackground, codeColor),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceContainerVariant,
                    )
                }
                line.startsWith("! ") -> Text(
                    text = inlineMarkdown(line.removePrefix("! ").trim(), codeBackground, codeColor),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                else -> Text(
                    text = inlineMarkdown(line, codeBackground, codeColor),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceContainerVariant,
                )
            }
        }
    }
}

/** Renders `**bold**` and `` `inline code` `` spans into an [AnnotatedString]. */
private fun inlineMarkdown(text: String, codeBackground: Color, codeColor: Color): AnnotatedString {
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val boldAt = text.indexOf("**", index)
            val codeAt = text.indexOf('`', index)
            val next = when {
                boldAt >= 0 && codeAt >= 0 -> minOf(boldAt, codeAt)
                boldAt >= 0 -> boldAt
                else -> codeAt
            }
            if (next < 0) {
                append(text.substring(index))
                break
            }
            if (next > index) append(text.substring(index, next))
            if (next == boldAt) {
                val end = text.indexOf("**", next + 2)
                if (end < 0) {
                    append(text.substring(next))
                    break
                }
                withStyle(boldStyle) { append(text.substring(next + 2, end)) }
                index = end + 2
            } else {
                val end = text.indexOf('`', next + 1)
                if (end < 0) {
                    append(text.substring(next))
                    break
                }
                withStyle(codeStyle) { append(text.substring(next + 1, end)) }
                index = end + 1
            }
        }
    }
}
