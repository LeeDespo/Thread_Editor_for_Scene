// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.ui

import com.scenepreset.editor.data.buildCoreMask
import com.scenepreset.editor.data.parseCoreMask
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.ThreadRule
import com.scenepreset.editor.model.ruleEffectivePackages
import com.scenepreset.editor.model.wildcardIssues

/**
 * Conflict detection shared by the save flow (whole rule set), the category
 * save flow and the in-editor pre-commit check (single rule).
 */
internal fun findConflicts(rules: List<ThreadRule>, categories: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    fun collect(field: (ThreadRule) -> List<String>?, kind: String) {
        val map = LinkedHashMap<String, MutableList<Int>>()
        rules.forEachIndexed { i, r ->
            field(r).orEmpty().forEach { key -> map.getOrPut(key) { mutableListOf() }.add(i) }
        }
        map.forEach { (key, indexes) ->
            if (indexes.size > 1) {
                reports += ConflictReport(kind, key, indexes.map { rules[it].displayName })
            }
        }
    }
    collect({ it.packages }, "包名")
    collect({ it.categories }, "类目")
    // Wildcard rules must be self-contained: only "*" in packages, no categories.
    rules.forEach { r ->
        r.wildcardIssues().forEach { reports += ConflictReport("通配包名", r.displayName, listOf(r.displayName)) }
    }
    // Package conflicts must consider apps imported via referenced categories.
    val effMap = LinkedHashMap<String, MutableList<Int>>()
    rules.forEachIndexed { i, r ->
        ruleEffectivePackages(r, categories).forEach { key -> effMap.getOrPut(key) { mutableListOf() }.add(i) }
    }
    effMap.forEach { (key, indexes) ->
        if (indexes.size > 1) {
            reports += ConflictReport("包名", key, indexes.map { rules[it].displayName })
        }
    }
    reports += findThreadBindConflicts(rules)
    return reports.distinctBy { it.key to it.rules }
}

internal fun findCategoryConflicts(rules: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    val idMap = LinkedHashMap<String, MutableList<Int>>()
    rules.forEachIndexed { i, r ->
        if (r.category.isNotBlank()) idMap.getOrPut(r.category) { mutableListOf() }.add(i)
    }
    idMap.forEach { (key, indexes) ->
        if (indexes.size > 1) {
            reports += ConflictReport("类目", key, indexes.map { rules[it].displayName })
        }
    }
    // Scene only allows the wildcard "*" group for a single category ("Apps").
    rules.forEachIndexed { i, r ->
        if (r.category != "Apps" && r.packages?.contains("*") == true) {
            reports += ConflictReport("通配包名", r.category, listOf(r.displayName))
        }
    }
    return reports
}

/** Intra-rule conflict checks for the thread-rule editor (only the rule being edited). */
internal fun findIntraRuleConflicts(rule: ThreadRule, categories: List<CategoryRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    rule.wildcardIssues().forEach { reports += ConflictReport("通配包名", rule.displayName, listOf(rule.displayName)) }
    // Duplicate packages inside this single rule (own packages vs imported categories).
    val seen = HashSet<String>()
    ruleEffectivePackages(rule, categories).forEach { pkg ->
        if (!seen.add(pkg)) reports += ConflictReport("包名", pkg, listOf(rule.displayName))
    }
    reports += findThreadBindConflicts(listOf(rule))
    return reports.distinctBy { it.key to it.rules }
}

private data class ThreadCore(val thread: String, val core: String?)

/**
 * Detects same-rule thread-name conflicts. Scene matches threads by prefix
 * (e.g. "thread" also matches "thread-11"), so within a rule two core-affecting
 * thread names may not overlap unless they are bound to the same core. This
 * covers heavy_thread / heaviest_thread entries that also appear in comm: an
 * overlap with the same allocated core is not a conflict. "调度" fields
 * (ni / trashy / rr) do not affect core allocation and are excluded.
 */
private fun findThreadBindConflicts(rules: List<ThreadRule>): List<ConflictReport> {
    val reports = mutableListOf<ConflictReport>()
    rules.forEach { rule ->
        val c = rule.cpuset ?: return@forEach
        val entries = mutableListOf<ThreadCore>()
        fun addThreads(field: String?, core: String?) {
            field?.split(';', ',', '\n', '\t')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
                entries += ThreadCore(it, core)
            }
        }
        addThreads(c.heaviestThread, c.heaviestCores)
        addThreads(c.heavyThread, c.heavyCores)
        c.comm?.forEach { (core, list) ->
            list.forEach { t -> if (t.trim().isNotEmpty()) entries += ThreadCore(t.trim(), core) }
        }
        for (i in 0 until entries.size) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (overlapsPrefix(a.thread, b.thread) && !sameCore(a.core, b.core)) {
                    reports += ConflictReport("线程名", a.thread, listOf(rule.displayName))
                }
            }
        }
    }
    return reports.distinctBy { it.key to it.rules }
}

private fun sameCore(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return normalizeMask(a) == normalizeMask(b)
}

// Comparison-only normalization must not truncate to a default core count
// (custom topologies can exceed 8 cores), hence Int.MAX_VALUE.
private fun normalizeMask(mask: String): String =
    runCatching { buildCoreMask(parseCoreMask(mask, Int.MAX_VALUE)) }.getOrDefault(mask.replace(" ", ""))

private fun overlapsPrefix(a: String, b: String): Boolean =
    a == b || a.startsWith(b) || b.startsWith(a)
