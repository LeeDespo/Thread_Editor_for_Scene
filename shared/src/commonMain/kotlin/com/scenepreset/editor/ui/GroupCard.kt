// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Section card used by the rule / category edit screens. The card title acts
 * as the first-level heading (bold, largest), each field label a second-level
 * heading (bold, mid-size); chip / badge text stays at footnote size.
 */
@Composable
internal fun GroupCard(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        content()
    }
}

/** Second-level field label inside a section card. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onSurfaceContainer) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier,
    )
}
