// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A single entry inside Scene's `threads.json`.
 *
 * A rule either describes a set of thread->core bindings for a game
 * ([GameCpuset], the `cpuset` field) or the touched/rendering thread binding
 * strategy for a regular app ([AppCpuset], the `app_cpuset` field).
 */
@Serializable
data class ThreadRule(
    val friendly: String = "",
    val categories: List<String>? = null,
    val packages: List<String>? = null,
    val cpuset: GameCpuset? = null,
    @SerialName("app_cpuset") val appCpuset: AppCpuset? = null,
) {
    val isGame: Boolean
        get() = cpuset != null

    val displayName: String
        get() = friendly.ifBlank {
            packages?.firstOrNull() ?: categories?.firstOrNull() ?: "未命名规则"
        }

    /** Collapses all empty containers to `null` so they are omitted when written. */
    fun normalized(version: SceneVersion = SceneVersion.SceneN1): ThreadRule = copy(
        categories = categories?.takeIf { it.isNotEmpty() },
        packages = packages?.takeIf { it.isNotEmpty() },
        cpuset = cpuset?.normalized(version)?.takeIf { it.hasAnyField(version) },
        appCpuset = appCpuset?.normalized(version)?.takeIf { it.hasAnyField(version) },
    )
}

/** Game thread -> CPU core binding configuration (`cpuset`). */
@Serializable
data class GameCpuset(
    @SerialName("unity_main") val unityMain: String? = null,
    @SerialName("heaviest_thread") val heaviestThread: String? = null,
    @SerialName("heaviest_cores") val heaviestCores: String? = null,
    @SerialName("heavy_thread") val heavyThread: String? = null,
    @SerialName("heavy_cores") val heavyCores: String? = null,
    @SerialName("main_thread") val mainThread: String? = null,
    val comm: Map<String, List<String>>? = null,
    val other: String? = null,
    val trashy: List<String>? = null,
    val ni: List<String>? = null,
    val rr: List<String>? = null,
) {
    fun normalized(version: SceneVersion = SceneVersion.SceneN1): GameCpuset {
        val keepUnity = version != SceneVersion.SceneN1
        val keepHeaviest = version == SceneVersion.SceneN1
        val keepNi = version != SceneVersion.Scene8
        val keepRr = version == SceneVersion.Scene9
        return GameCpuset(
            unityMain = if (keepUnity) unityMain?.ifBlank { null } else null,
            heaviestThread = if (keepHeaviest) heaviestThread?.ifBlank { null } else null,
            heaviestCores = if (keepHeaviest) heaviestCores?.ifBlank { null } else null,
            heavyThread = heavyThread?.ifBlank { null },
            heavyCores = heavyCores?.ifBlank { null },
            mainThread = mainThread?.ifBlank { null },
            other = other?.ifBlank { null },
            comm = comm?.filterValues { it.isNotEmpty() }?.takeIf { it.isNotEmpty() },
            trashy = trashy?.takeIf { it.isNotEmpty() },
            ni = if (keepNi) ni?.takeIf { it.isNotEmpty() } else null,
            rr = if (keepRr) rr?.takeIf { it.isNotEmpty() } else null,
        )
    }

    fun hasAnyField(version: SceneVersion = SceneVersion.SceneN1): Boolean = normalized(version).let {
        it.unityMain != null || it.heaviestThread != null || it.heaviestCores != null ||
            it.heavyThread != null || it.heavyCores != null || it.mainThread != null ||
            it.comm != null || it.other != null || it.trashy != null || it.ni != null || it.rr != null
    }
}

/** Regular app thread binding configuration (`app_cpuset`). */
@Serializable
data class AppCpuset(
    val main: String? = null,
    val render: String? = null,
    val other: String? = null,
    val webview: Boolean? = null,
    val children: Boolean? = null,
) {
    fun normalized(version: SceneVersion = SceneVersion.SceneN1): AppCpuset {
        val keepScope = version != SceneVersion.Scene8
        return copy(
            main = main?.ifBlank { null },
            render = render?.ifBlank { null },
            other = other?.ifBlank { null },
            webview = if (keepScope) webview?.takeIf { it } else null,
            children = if (keepScope) children?.takeIf { it } else null,
        )
    }

    fun hasAnyField(version: SceneVersion = SceneVersion.SceneN1): Boolean = normalized(version).let {
        it.main != null || it.render != null || it.other != null ||
            it.webview != null || it.children != null
    }
}

/** Lenient, human readable codec for the Scene `threads.json` array. */
object ThreadsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(text: String): List<ThreadRule> = json.decodeFromString<List<ThreadRule>>(text)

    fun encode(rules: List<ThreadRule>, version: SceneVersion = SceneVersion.SceneN1): String =
        json.encodeToString(orderRules(rules).map { it.normalized(version) })
}

/** Scene-facing alias for a single thread preset rule. */
typealias PresetRule = ThreadRule

/**
 * The packages a rule actually covers: its own `packages` plus every package of
 * the categories it references (from categories.json).
 */
fun ruleEffectivePackages(rule: ThreadRule, categories: List<CategoryRule>): List<String> {
    val own = rule.packages.orEmpty().filter { it.isNotBlank() && it != "*" }
    val catIds = rule.categories.orEmpty().map { it.trim() }.toSet()
    val catPkgs = categories
        .filter { it.category.trim() in catIds }
        .flatMap { it.packages.orEmpty() }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "*" }
    return (own + catPkgs).distinct()
}

/** The referenced categories as `(id, displayLabel)` pairs. */
fun ruleCategoryLabels(rule: ThreadRule, categories: List<CategoryRule>): List<Pair<String, String>> {
    val catIds = rule.categories.orEmpty().map { it.trim() }.toSet()
    return categories
        .filter { it.category.trim() in catIds }
        .map { it.category to (it.friendly.ifBlank { it.category }) }
}

/** Whether a rule uses the `"*"` wildcard package (matches every app). */
fun ThreadRule.hasWildcardPackage(): Boolean = packages?.contains("*") == true

/** Validation problems for a wildcard rule, or empty if valid. */
fun ThreadRule.wildcardIssues(): List<String> {
    if (!hasWildcardPackage()) return emptyList()
    val issues = mutableListOf<String>()
    val others = packages.orEmpty().filter { it != "*" }
    if (others.isNotEmpty()) issues += "通配包名规则不允许再包含其它应用包名"
    if (!categories.isNullOrEmpty()) issues += "通配包名规则不允许引用类目"
    return issues
}

/**
 * Order rules the way Scene expects: all default (`app_cpuset`) rules first, then
 * advanced (`cpuset`) rules, with any `"*"` wildcard rules last.
 */
fun orderRules(rules: List<ThreadRule>): List<ThreadRule> =
    rules.sortedWith(compareBy { r -> if (r.hasWildcardPackage()) 2 else if (r.isGame) 1 else 0 })

/**
 * Infers which Scene release a rule set was authored for by inspecting the
 * game `cpuset` fields Scene introduced over time.
 */
fun detectSceneVersion(rules: List<ThreadRule>): SceneVersion {
    val cpusets = rules.mapNotNull { it.cpuset }
    if (cpusets.any { it.heaviestThread != null }) return SceneVersion.SceneN1
    if (cpusets.any { it.rr != null }) return SceneVersion.Scene9
    if (cpusets.any { it.ni != null }) return SceneVersion.Scene9
    if (cpusets.any { it.unityMain != null }) return SceneVersion.Scene8
    return SceneVersion.SceneN1
}

/**
 * Best-effort conversion of a rule set from one Scene release to another.
 * Field mapping (see Scene docs):
 *   - Scene8/9 use `unity_main` for the UnityMain-thread core; Scene N1 replaced
 *     it with `heaviest_thread` + `heaviest_cores`.
 *   - `rr` exists only in Scene9; `ni` exists in Scene9 + Scene N1.
 *   - `app_cpuset` webview/children exist in Scene9 + Scene N1 only.
 * Conversions are lossy where a target has no equivalent field; the result is
 * normalized for the target version at the end.
 */
fun convertRules(rules: List<ThreadRule>, from: SceneVersion, to: SceneVersion): List<ThreadRule> {
    if (from == to) return rules
    return rules.map { rule ->
        rule.copy(cpuset = convertGameCpuset(rule.cpuset, from, to)).normalized(to)
    }
}

private fun convertGameCpuset(c: GameCpuset?, from: SceneVersion, to: SceneVersion): GameCpuset? {
    if (c == null) return null
    val wantUnity = to != SceneVersion.SceneN1
    val wantHeaviest = to == SceneVersion.SceneN1
    val wantNi = to != SceneVersion.Scene8
    val wantRr = to == SceneVersion.Scene9

    var unity = c.unityMain
    var heaviestThread = c.heaviestThread
    var heaviestCores = c.heaviestCores
    var heavyThread = c.heavyThread
    var heavyCores = c.heavyCores

    // N1 -> 8/9: heaviest(thread+cores) has no generic equivalent, map to unity_main
    // when the thread is UnityMain, otherwise promote to heavy when still free.
    if (from == SceneVersion.SceneN1 && wantUnity) {
        if (heaviestThread != null && heaviestCores != null) {
            if (heaviestThread.contains("unitymain", ignoreCase = true)) {
                unity = heaviestCores
            } else if (heavyThread.isNullOrBlank() && heavyCores.isNullOrBlank()) {
                heavyThread = heaviestThread
                heavyCores = heaviestCores
            }
            // otherwise the non-Unity thead config is dropped (target has no slot).
        }
        heaviestThread = null
        heaviestCores = null
    } else if (from != SceneVersion.SceneN1 && wantHeaviest) {
        // 8/9 -> N1: unity_main is the UnityMain-thread core.
        if (!unity.isNullOrBlank()) {
            heaviestThread = "UnityMain"
            heaviestCores = unity
        }
        unity = null
    }

    return GameCpuset(
        unityMain = if (wantUnity) unity else null,
        heaviestThread = if (wantHeaviest) heaviestThread else null,
        heaviestCores = if (wantHeaviest) heaviestCores else null,
        heavyThread = heavyThread,
        heavyCores = heavyCores,
        mainThread = c.mainThread,
        comm = c.comm,
        other = c.other,
        trashy = c.trashy,
        ni = if (wantNi) c.ni else null,
        rr = if (wantRr) c.rr else null,
    )
}
