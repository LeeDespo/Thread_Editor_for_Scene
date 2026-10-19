// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.data

import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.SceneVersion

/** Parsed content of the target `threads.json`. */
data class PresetFile(
    val path: String,
    val rules: List<ThreadRule>,
)

/**
 * Owns a single Scene `threads.json` (the "preset" target path). All rule
 * operations in the editor read/write this file, which may be the default
 * Scene location or a user customized path.
 *
 * Every method shells out through `su` and must be called off the main thread.
 */
interface SceneRepository {
    val path: String

    fun updatePath(path: String)

    fun exists(): Boolean

    fun load(): PresetFile

    /** @return null on success, otherwise an error message. */
    fun save(rules: List<ThreadRule>, version: SceneVersion = SceneVersion.SceneN1): String?

    fun isLocked(): Boolean

    /** @return null on success, otherwise an error message. */
    fun setLocked(locked: Boolean): String?

    /** Creates the target file (as `[]`) if it does not exist. */
    fun ensureExists(): Boolean

    /** Deletes the target threads.json (unlocking it first). @return null on success. */
    fun reset(): String?

    /** The categories.json file path derived from the base configuration path. */
    val categoriesPath: String

    fun categoriesExists(): Boolean

    fun loadCategories(): List<CategoryRule>

    /** @return null on success, otherwise an error message. */
    fun saveCategories(rules: List<CategoryRule>): String?

    /** Creates the categories.json (as `[]`) if it does not exist. */
    fun ensureCategories(): Boolean

    /**
     * Captures the pristine categories.json into app-private storage once,
     * on first launch, before any user edits.
     */
    fun captureCategoriesBackupIfMissing()

    /**
     * Restores categories.json from the backup captured the first time the app
     * ran (if any). Scene does NOT regenerate categories.json by switching
     * performance profiles, so "reset" here means restoring that backup.
     * @return null on success, otherwise an error message.
     */
    fun restoreCategoriesBackup(): String?

    fun isCategoriesLocked(): Boolean

    /** @return null on success, otherwise an error message. */
    fun setCategoriesLocked(locked: Boolean): String?
}
