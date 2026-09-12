// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.scenepreset.editor.ui.ThreadEditorApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = rememberThreadEditorContext()
            ThreadEditorApp(context = context)
        }
    }
}
