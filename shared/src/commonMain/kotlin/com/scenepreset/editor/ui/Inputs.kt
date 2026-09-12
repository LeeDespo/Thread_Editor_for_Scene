// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.scenepreset.editor.data.CpuClusterInfo
import com.scenepreset.editor.data.parseCoreMask
import com.scenepreset.editor.data.buildCoreMask
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A token input: type comma-separated values and press Done; each becomes a removable
 * chip rendered above the field. Chips wrap onto the next line automatically.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInputField(
    label: String,
    chips: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    infoTitle: String,
    infoText: String,
    onInfo: (FieldInfo) -> Unit,
    placeholder: String = "可输入多个，用逗号分隔，回车确认",
) {
    val colors = MiuixTheme.colorScheme
    var input by remember { mutableStateOf("") }

    fun commit() {
        val tokens = input.split(',', '，', ';', '；', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        tokens.forEach { onAdd(it) }
        input = ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(label, Modifier.weight(1f))
            InfoButton(title = infoTitle, text = infoText, onInfo = onInfo)
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip -> Chip(label = chip, onRemove = { onRemove(chip) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = placeholder,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }, onSend = { commit() }),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Selectable CPU core buttons that produce a compact mask ("0-6", "7", "4-6,7"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreSelector(
    mask: String,
    onMaskChange: (String) -> Unit,
    clusters: List<CpuClusterInfo> = emptyList(),
    coreCount: Int = 8,
    single: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val presentCount = clusters.flatMap { it.cpus }.maxOrNull()?.plus(1) ?: coreCount
    val selected = remember(mask, presentCount) { parseCoreMask(mask, presentCount) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (clusters.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clusters.forEach { cluster ->
                    val clusterSet = cluster.cpus.toSet()
                    val active = clusterSet.all { it in selected }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (active) colors.primary else colors.surfaceContainer,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable {
                                val next = when {
                                    single -> if (active) emptySet() else clusterSet
                                    active -> selected - clusterSet
                                    else -> selected + clusterSet
                                }
                                onMaskChange(buildCoreMask(next))
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = cluster.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (active) colors.onPrimary else colors.onSurfaceContainer,
                        )
                        Text(
                            text = cluster.rangeText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (active) colors.onPrimary else colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0 until presentCount).forEach { core ->
                    val isSelected = core in selected
                    Column(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) colors.primary else colors.surfaceContainer,
                                CircleShape,
                            )
                            .clickable {
                                val next = when {
                                    single -> if (isSelected) emptySet() else setOf(core)
                                    isSelected -> selected - core
                                    else -> selected + core
                                }
                                onMaskChange(buildCoreMask(next))
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = core.toString(),
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (isSelected) colors.onPrimary else colors.onSurfaceContainer,
                        )
                    }
                }
            }
        } else {
            Text(
                "正在读取核心拓扑…",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}
