// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.data

import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.ThreadRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What kind of configuration a history entry snapshots. */
@Serializable
enum class HistoryKind {
    /** Snapshot of threads.json (the default for entries saved before tagging existed). */
    Threads,

    /** Snapshot of categories.json. */
    Categories,
}

/** A backwards-restorable snapshot of the rule set, captured at each save. */
@Serializable
data class HistoryEntry(
    val id: String,
    val timestamp: String,
    val name: String,
    val note: String,
    val rules: List<ThreadRule> = emptyList(),
    val sceneVersion: String? = null,
    /** Which config file this entry snapshots (older entries default to threads). */
    val kind: HistoryKind = HistoryKind.Threads,
    /** Snapshot of categories.json; only set when [kind] is [HistoryKind.Categories]. */
    val categoryRules: List<CategoryRule> = emptyList(),
)

object HistoryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(entry: HistoryEntry): String = json.encodeToString(entry)

    fun decode(text: String): HistoryEntry = json.decodeFromString(text)
}
