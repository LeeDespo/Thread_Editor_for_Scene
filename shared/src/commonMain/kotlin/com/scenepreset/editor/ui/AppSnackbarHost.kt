// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SNACKBAR_MS = 4000L

/**
 * App-wide Snackbar that slides in/out HORIZONTALLY (parallel to the screen
 * bottom), avoiding the vertical "drop"/collapse of the default host.
 */
@Composable
fun AppSnackbarHost(
    message: String?,
    onShown: () -> Unit,
    onDelete: () -> Unit = onShown,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(message) {
        if (message != null) {
            delay(SNACKBAR_MS)
            onShown()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.onSecondaryVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    color = colors.secondaryVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                if (actionLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onAction)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            actionLabel,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colors.secondaryVariant.copy(alpha = 0.15f), CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.Delete,
                        contentDescription = "关闭",
                        tint = colors.secondaryVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
