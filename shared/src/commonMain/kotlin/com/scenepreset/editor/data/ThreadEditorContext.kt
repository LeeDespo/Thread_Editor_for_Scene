// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.data

import androidx.compose.ui.graphics.ImageBitmap
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.data.HistoryKind

/** A resolved application (for badges, package picking and the app list). */
data class AppEntry(
    val packageName: String,
    val label: String,
    val letter: String,
    val icon: ImageBitmap? = null,
)

/** A detected CPU core cluster with a stable display label. */
data class CpuClusterInfo(
    val label: String,
    val cpus: List<Int>,
) {
    val mask: String get() = buildCoreMask(cpus.toSet())
    val rangeText: String get() = cpus.toRangeText()
}

private fun List<Int>.toRangeText(): String {
    val sorted = distinct().sorted()
    if (sorted.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (i in 1..sorted.size) {
        val cur = sorted.getOrNull(i)
        if (cur == null || cur != prev + 1) {
            if (start == prev) parts += "$start" else parts += "$start-$prev"
            start = cur ?: return parts.joinToString(",")
        }
        prev = cur
    }
    return parts.joinToString(",")
}

/**
 * Platform services exposed to the shared UI. The Android app implements this
 * in the application module so the UI layer stays platform-agnostic.
 */
interface ThreadEditorContext {
    /** The Scene profile directory (default files/), the parent of threads.json + categories.json. */
    fun defaultSceneBasePath(): String

    /** The Scene threads.json repository backing the editor. */
    val sceneRepository: SceneRepository

    /** Opens a document picker and returns the picked file's text, or null. */
    fun importJson(onResult: (String?) -> Unit)

    /** Opens a save picker and writes [content], invoking [onResult] with success. */
    fun exportJson(defaultName: String, content: String, onResult: (Boolean) -> Unit)

    fun readSetting(key: String): String?

    fun writeSetting(key: String, value: String)

    /** Appends a line to the on-device log. No-op when logging is disabled. */
    fun log(message: String)

    /** Whether user-facing log recording is currently enabled. */
    /** Log storage cap in KB (0 = disabled, max 1024). */
    /** Clears the on-device log file (keeps future logging intact). */
    fun clearLogs()

    fun logCapKb(): Int

    /** Sets the log storage cap in KB; 0 disables logging entirely. */
    fun setLogCapKb(kb: Int)

    /** Opens a save picker to export the on-device log. */
    fun exportLogs(onResult: (Boolean) -> Unit)

    /** Resolves a single package into a badge entry (loads the icon when possible). */
    fun resolveApp(packageName: String): AppEntry

    /**
     * Warm the app cache for [packageNames] ahead of first display so the UI
     * thread never blocks on PackageManager lookups / icon decoding while a
     * list is being flung. Runs off the main thread; fire-and-forget.
     */
    fun warmAppCache(packageNames: List<String>)

    /** The currently selected Scene version. */
    fun sceneVersion(): SceneVersion

    fun setSceneVersion(version: SceneVersion)

    /** Detects the installed Scene version from its package versionName, or null. */
    fun installedSceneVersion(): SceneVersion?

    /** Whether the Scene package is installed at all. */
    fun isSceneInstalled(): Boolean

    /** Whether the startup version mismatch prompt should be suppressed permanently. */
    fun ignoreVersionPrompt(): Boolean

    fun setIgnoreVersionPrompt(ignored: Boolean)

    /** Fully recreates the Activity so the app reloads cleanly (e.g. after a version switch). */
    fun reloadApp()

    /** Opens a picker and returns picked text, or null. */
    fun importCategories(onResult: (String?) -> Unit)

    /** Opens a save picker and writes categories [content], invoking [onResult] with success. */
    fun exportCategories(defaultName: String, content: String, onResult: (Boolean) -> Unit)

    /**
     * Lists installed apps ordered by label. When [includeSystem] is true, system
     * apps are included. Otherwise only user-installed applications are shown.
     */
    suspend fun installedApps(includeSystem: Boolean): List<AppEntry>

    /** Detects the CPU core topology and returns clusters ordered by performance. */
    suspend fun cpuClusters(): List<CpuClusterInfo>

    /**
     * The topology the app should present: user customization when set, the
     * detected hardware otherwise. Persisted in app user data (filesDir), so
     * it survives cache clears.
     */
    suspend fun effectiveTopology(): CoreTopology

    /** The real hardware detection, ignoring any user customization. */
    suspend fun detectedTopology(): CoreTopology

    /** Persists the custom topology (pass null to clear the override). */
    fun setCustomTopology(topology: CoreTopology?)

    /** Builds a history snapshot with platform-generated id and timestamp. */
    fun buildHistoryEntry(name: String, note: String, rules: List<ThreadRule>, version: SceneVersion): HistoryEntry

    /** Builds a category history snapshot with platform-generated id and timestamp. */
    fun buildCategoryHistoryEntry(name: String, note: String, rules: List<CategoryRule>): HistoryEntry

    /** Loads all saved history snapshots, newest first. When [kind] is null, loads every entry. */
    fun loadHistory(kind: HistoryKind? = null): List<HistoryEntry>

    /** Persists a new history snapshot. */
    fun appendHistory(entry: HistoryEntry)

    /** Deletes a history snapshot by id. */
    fun deleteHistory(id: String)

    /** Opens an external link (project homepage, author profile) in a browser. */
    fun openUrl(url: String)
}

const val SETTING_COLOR_MODE = "theme.colorMode"
const val SETTING_KEY_COLOR = "theme.keyColorIndex"
const val SETTING_SCENE_PATH = "scene.path"
const val SETTING_SCENE_VERSION = "scene.version"
const val SETTING_IGNORE_VERSION_PROMPT = "scene.ignoreVersionPrompt"
const val SETTING_LOG_CAP_KB = "log.capKb"
const val SETTING_SHOW_SYSTEM_APPS = "apps.showSystem"
const val SETTING_GUIDE_SHOWN = "ui.guideShown"
const val FILE_CUSTOM_TOPOLOGY = "custom_topology.json"
const val SETTING_EXPORT_BLOCK_IGNORE = "ui.exportBlockIgnore"
const val SETTING_TOPOLOGY_INTRO_SHOWN = "topology.introShown"
