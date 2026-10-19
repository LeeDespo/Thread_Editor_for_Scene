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

/** Shared token splitter: half/full-width commas & semicolons, spaces, newlines, tabs. */
internal fun splitTokens(raw: String): List<String> =
    raw.split(',', '，', ';', '；', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }

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
        splitTokens(input).forEach { onAdd(it) }
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

/** Selectable CPU core buttons (or, in text-entry mode, a validated mask field). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreSelector(
    mask: String,
    onMaskChange: (String) -> Unit,
    clusters: List<CpuClusterInfo> = emptyList(),
    coreCount: Int = 8,
    textEntry: Boolean = false,
    /**
     * Highest core count the mask may reference, beyond the device's own cores:
     * imported configs from a higher-core SoC keep their ids instead of having
     * them silently trimmed by the buttons / rejected by the text field.
     */
    maskCoreLimit: Int = 0,
) {
    val colors = MiuixTheme.colorScheme
    val presentCount = clusters.flatMap { it.cpus }.maxOrNull()?.plus(1) ?: coreCount
    val maskCount = maxOf(presentCount, maskCoreLimit)
    val selected = remember(mask, maskCount) { parseCoreMask(mask, maskCount) }
    if (textEntry) {
        CoreTextInput(mask = mask, coreCount = maskCount, onMaskChange = onMaskChange)
        return
    }
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
                                val next = if (active) selected - clusterSet else selected + clusterSet
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
                                val next = if (isSelected) selected - core else selected + core
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

private val CORE_TOKEN = Regex("\\d+\\s*-\\s*\\d+|\\d+")
private val CORE_SEPARATOR = Regex("[,;\\s，；、]")

/**
 * Free-form core-mask input used when "手动输入核心掩码" is enabled. Accepts
 * half/full-width separators (leniently converted to ","), ranges ("0-3") and
 * spaces. Only canonical, in-range strings propagate to the draft; anything
 * else stays in the field with an inline error hint.
 */
@Composable
private fun CoreTextInput(
    mask: String,
    coreCount: Int,
    onMaskChange: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var text by remember(mask) { mutableStateOf(mask) }

    fun canonical(v: String): String =
        CORE_TOKEN.findAll(v).map { it.value.replace(" ", "").replace("\t", "") }.joinToString(",")

    fun errorOf(v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        if (t.replace(CORE_TOKEN, "").replace(CORE_SEPARATOR, "").isNotEmpty()) {
            return "格式应为 0-3,7：支持 , ; 空格分隔（全角自动转换）与 0-3 范围写法"
        }
        val nums = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
        if (nums.any { it < 0 || it >= coreCount }) return "核心编号需在 0–${coreCount - 1} 范围内"
        return null
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextField(
            value = text,
            onValueChange = { raw ->
                // Lenient input: full-width commas/semicolons/dashes become ASCII.
                val v = raw.replace('，', ',').replace('、', ',').replace('；', ';').replace('－', '-')
                val err = errorOf(v)
                if (err == null) {
                    val canon = canonical(v)
                    text = canon
                    onMaskChange(canon)
                } else {
                    text = v
                }
            },
            label = "核心掩码（如 0-3,7）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        errorOf(text)?.let { err ->
            Text(err, style = MiuixTheme.textStyles.footnote2, color = colors.error)
        }
    }
}
