// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

data class FieldInfo(val title: String, val text: String)

@Composable
fun InfoButton(title: String, text: String, onInfo: (FieldInfo) -> Unit) {
    IconButton(onClick = { onInfo(FieldInfo(title, text)) }) {
        Icon(
            imageVector = MiuixIcons.Info,
            contentDescription = "字段说明：$title",
        )
    }
}
