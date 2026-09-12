// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.HistoryEntry
import com.scenepreset.editor.data.HistoryKind
import com.scenepreset.editor.data.ThreadEditorContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The history page filters entries by the config they snapshot. */
private enum class HistoryTab(val label: String, val kind: HistoryKind) {
    Threads("线程", HistoryKind.Threads),
    Categories("类目", HistoryKind.Categories),
}

@Composable
fun HistoryScreen(
    context: ThreadEditorContext,
    onBack: () -> Unit,
    onRollback: (HistoryEntry) -> Unit,
    onMessage: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(HistoryTab.Threads) }
    var entries by remember { mutableStateOf(context.loadHistory(HistoryTab.Threads.kind)) }
    Scaffold(
        contentWindowInsets = contentInsetsTopOnly,
        topBar = {
            SmallTopAppBar(
                title = "历史记录",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedControl(
                items = HistoryTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { index ->
                    tab = HistoryTab.entries[index]
                    entries = context.loadHistory(tab.kind)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == HistoryTab.Threads) "暂无线程历史记录" else "暂无类目历史记录",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryCard(
                            entry = entry,
                            onRollback = { onRollback(entry) },
                            onDelete = {
                                context.deleteHistory(entry.id)
                                entries = context.loadHistory(tab.kind)
                                onMessage("已删除历史记录")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onRollback: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HistoryKindBadge(entry.kind)
            Spacer(Modifier.width(10.dp))
            Text(
                entry.timestamp,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.primary,
            )
            Spacer(Modifier.weight(1f))
            val count = if (entry.kind == HistoryKind.Categories) entry.categoryRules.size else entry.rules.size
            Text(
                "$count 条规则",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
        }
        if (entry.name.isNotBlank()) {
            Text(entry.name, style = MiuixTheme.textStyles.main, color = colors.onSurfaceContainer)
        }
        if (entry.note.isNotBlank()) {
            Text(entry.note, style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceVariantSummary)
        }
        Spacer(Modifier.width(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRollback, modifier = Modifier.weight(1f)) {
                Text(
                    if (entry.kind == HistoryKind.Categories) "回溯类目" else "回溯线程",
                    style = MiuixTheme.textStyles.button,
                )
            }
            Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("删除", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** Colored tag marking which config file the entry snapshots. */
@Composable
private fun HistoryKindBadge(kind: HistoryKind) {
    val colors = MiuixTheme.colorScheme
    val isThreads = kind == HistoryKind.Threads
    val bg = if (isThreads) colors.primaryContainer else colors.secondaryContainer
    val fg = if (isThreads) colors.onPrimaryContainer else colors.onSecondaryContainer
    Text(
        text = if (isThreads) "线程" else "类目",
        style = MiuixTheme.textStyles.footnote2,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
