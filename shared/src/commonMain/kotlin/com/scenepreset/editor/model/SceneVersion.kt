// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.model

/**
 * Target Scene release. Newer releases support more fields; the editor must
 * serialize only the fields a given version understands and show the matching
 * form fields.
 */
enum class SceneVersion(
    val id: String,
    val display: String,
) {
    Scene8(id = "scene8", display = "Scene 8"),
    Scene9(id = "scene9", display = "Scene 9"),
    SceneN1(id = "sceneN1", display = "Scene N1"),
    ;

    companion object {
        fun fromId(id: String?): SceneVersion =
            entries.firstOrNull { it.id == id } ?: SceneN1

        fun fromVersionName(versionName: String): SceneVersion? =
            when {
                versionName.contains("N1", ignoreCase = true) -> SceneN1
                versionName.trimStart().startsWith("8.") -> Scene8
                versionName.trimStart().startsWith("9.") -> Scene9
                else -> null
            }
    }
}
