// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

/**
 * Window insets applied to the inner screens. The app shell hosts the bottom
 * [NavigationBar], so inner screens only need to insets for the status bar.
 */
internal val contentInsetsTopOnly: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Top)
