// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.model

import com.scenepreset.editor.data.CoreTopology

/**
 * Cross-device adaptation for imported `threads.json` configs.
 *
 * The import flow:
 *  1. Ask the user for the source device's performance architecture, strongest
 *     first, e.g. "1,3,3" for one prime + three big + three little cores.
 *  2. If it differs from this device, offer best-effort adaptation:
 *     - Map every core mask in the imported config to the source tier it
 *       touches (tiers are ordered strongest-first, sized by the entered spec).
 *     - Map those source tiers onto this device's clusters (strongest to
 *       weakest). Extra source tiers collapse into the weakest local cluster;
 *     - extra local clusters are absorbed by the weakest imported tier that hit.
 *     The result is the union of the local clusters each tier maps onto.
 *  3. Without adaptation: import as-is only when the core counts match,
 *     otherwise reject and point the user to custom topology.
 */

/** The entered architecture ("1,3,3") as per-tier core counts, strongest first. */
fun parseArchitectureSpec(text: String): List<Int> =
    text.split(',', '，', ';', '；', ' ').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }

/**
 * Splits [spec] (strongest first) into tier index ranges over the source
 * device's cores. Tier i covers the next [spec[i]] core ids starting from 0.
 * Returns list of (startInclusive, endExclusive).
 */
internal fun tierRanges(spec: List<Int>): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var start = 0
    spec.forEach { n ->
        out += start until (start + n)
        start += n
    }
    return out
}

/**
 * Maps a core mask (set of source core ids) to the source tiers it touches.
 */
internal fun maskTiers(mask: Set<Int>, ranges: List<IntRange>): List<Int> =
    ranges.indices.filter { t -> ranges[t].any { it in mask } }

/**
 * Adaptation engine: binds source tiers to local clusters, then rewrites every
 * core mask in the rules to the union of the local clusters each source tier
 * maps to.
 *
 * @param rules the imported rule set (authored for the source device)
 * @param sourceSpec per-tier core counts of the source device, strongest first
 * @param local this device's effective topology (custom or detected)
 */
fun adaptRulesToTopology(
    rules: List<ThreadRule>,
    sourceSpec: List<Int>,
    local: CoreTopology,
): List<ThreadRule> {
    val ranges = tierRanges(sourceSpec)
    val localClusters = local.clusters
        .map { it to com.scenepreset.editor.data.parseCoreSpec(it.cores, local.coreCount) }
        .filter { it.second.isNotEmpty() }
    if (localClusters.isEmpty()) return rules

    // Strongest-first local order as entered by the user. More source tiers
    // than local clusters: extra tiers merge into the weakest local cluster.
    fun clusterUnion(tier: Int): Set<Int> =
        localClusters.getOrNull(tier)?.second ?: localClusters.last().second

    fun adaptMask(maskText: String?): String? {
        if (maskText.isNullOrBlank()) return maskText
        val mask = com.scenepreset.editor.data.parseCoreMask(maskText, Int.MAX_VALUE)
        if (mask.isEmpty()) return maskText
        val tiers = maskTiers(mask, ranges)
        if (tiers.isEmpty()) return maskText
        val union = mutableSetOf<Int>()
        tiers.forEach { t -> union.addAll(clusterUnion(t)) }
        // Leftover local clusters (more local clusters than imported tiers) are
        // absorbed by the weakest imported tier that hit.
        val weakestHitTier = tiers.max()
        if (weakestHitTier == ranges.lastIndex && localClusters.size > sourceSpec.size) {
            localClusters.drop(sourceSpec.size).forEach { (_, cores) -> union.addAll(cores) }
        }
        return com.scenepreset.editor.data.buildCoreMask(union)
    }

    fun adaptCpuset(c: GameCpuset?): GameCpuset? {
        if (c == null) return null
        return c.copy(
            unityMain = adaptMask(c.unityMain),
            heaviestCores = adaptMask(c.heaviestCores),
            heavyCores = adaptMask(c.heavyCores),
            mainThread = adaptMask(c.mainThread),
            other = adaptMask(c.other),
            comm = c.comm?.mapValues { (_, v) -> v.mapNotNull { adaptMask(it) } }?.takeIf { it.values.any { l -> l.isNotEmpty() } },
        )
    }

    fun adaptAppCpuset(c: AppCpuset?): AppCpuset? {
        if (c == null) return null
        return c.copy(
            main = adaptMask(c.main),
            render = adaptMask(c.render),
            other = adaptMask(c.other),
        )
    }

    return rules.map { rule ->
        rule.copy(cpuset = adaptCpuset(rule.cpuset), appCpuset = adaptAppCpuset(rule.appCpuset))
    }
}
