// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.model

import com.scenepreset.editor.data.CoreTopology

/**
 * Cross-device adaptation for imported `threads.json` configs.
 *
 * The import flow:
 *  1. Ask the user for the SOURCE device's performance architecture, strongest
 *     first, e.g. "1,3,3" for one prime + three big + three little cores. That
 *     entry is the single source of truth for tiering: it is never inferred
 *     from the file, and never silently replaced by a detection heuristic. A
 *     config may deliberately keep threads off the strongest cores, and a
 *     "smarter" tiering would overrule exactly that intent.
 *  2. If it differs from this device, offer best-effort adaptation:
 *     - Map every core mask in the imported config to the source tier it
 *       touches (tiers are ordered strongest-first, sized by the entered spec).
 *     - Map those source tiers onto this device's clusters (strongest to
 *       weakest). Extra source tiers collapse into the weakest local cluster;
 *       extra local clusters are absorbed by the weakest imported tier that hit.
 *     The result is the union of the local clusters each tier maps onto.
 *  3. Without adaptation the file imports as-is. A core-count mismatch never
 *     blocks the import and never asks the user to retune custom topology; it
 *     only turns on mask text entry ([referencedCoreCount] drives that).
 */

/** The entered architecture ("1,3,3") as per-tier core counts, strongest first. */
fun parseArchitectureSpec(text: String): List<Int> =
    text.split(',', '，', ';', '；', ' ').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }

/**
 * Splits [spec] (strongest first) into tier index ranges over the source
 * device's core ids. Ascending core ids are ascending performance (little
 * cores first), the numbering Scene's own presets assume: the weakest tier
 * owns ids `0..n-1` and the strongest tier owns the HIGHEST ids (e.g. 8 cores,
 * 1+3+4 → 小核 0-3, 大核 4-6, 超大核 7).
 *
 * Returns a list indexed by tier rank (0 = strongest).
 */
internal fun tierRanges(spec: List<Int>): List<IntRange> {
    val total = spec.sum()
    // Assign the weakest tier to the lowest ids, then reverse so the result
    // stays strongest-first.
    val weakestFirst = mutableListOf<IntRange>()
    var start = 0
    spec.reversed().forEach { n ->
        weakestFirst += start until (start + n)
        start += n
    }
    return weakestFirst.reversed().also {
        check(it.sumOf { r -> r.count() } == total)
    }
}

/**
 * Upper bound used when a mask is parsed without knowing a device topology.
 * Masks come from files, and `parseCoreMask` walks ranges element by element,
 * so an absurd range like "0-99999999" must not be able to stall the UI.
 */
private const val MAX_PLAUSIBLE_CORES = 1024

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
 * @param sourceSpec per-tier core counts of the source device, strongest first,
 *   exactly as the user entered them
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
        val mask = com.scenepreset.editor.data.parseCoreMask(maskText, MAX_PLAUSIBLE_CORES)
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

    /**
     * `comm` maps a core mask (key) to the thread names bound to it, so the keys
     * are the masks to rewrite. Distinct source masks can land on the same local
     * cluster; their thread lists are then merged under that one key.
     */
    fun adaptComm(comm: Map<String, List<String>>?): Map<String, List<String>>? {
        if (comm == null) return null
        val merged = LinkedHashMap<String, List<String>>()
        comm.forEach { (mask, threads) ->
            val key = adaptMask(mask) ?: mask
            merged[key] = merged[key].orEmpty() + threads
        }
        return merged.mapValues { (_, threads) -> threads.distinct() }.takeIf { it.isNotEmpty() }
    }

    fun adaptCpuset(c: GameCpuset?): GameCpuset? {
        if (c == null) return null
        return c.copy(
            unityMain = adaptMask(c.unityMain),
            heaviestCores = adaptMask(c.heaviestCores),
            heavyCores = adaptMask(c.heavyCores),
            mainThread = adaptMask(c.mainThread),
            other = adaptMask(c.other),
            comm = adaptComm(c.comm),
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

/**
 * Highest core id referenced by any mask in [rules], +1 (i.e. the smallest
 * total core count consistent with the file), or null when the file has no
 * core masks to learn from.
 *
 * Used only to compare the file against the core count this app recorded for
 * the device, which decides whether the mask text entry has to stay on. It
 * never feeds back into [adaptRulesToTopology]: the user's entered spec is the
 * authority there.
 */
fun referencedCoreCount(rules: List<ThreadRule>): Int? {
    var max = -1
    fun check(mask: String?) {
        if (mask.isNullOrBlank()) return
        val top = com.scenepreset.editor.data.parseCoreMask(mask, MAX_PLAUSIBLE_CORES).maxOrNull() ?: return
        if (top > max) max = top
    }
    rules.forEach { rule ->
        rule.cpuset?.let { c ->
            check(c.unityMain); check(c.heaviestCores); check(c.heavyCores)
            check(c.mainThread); check(c.other)
            c.comm?.keys?.forEach { check(it) }
        }
        rule.appCpuset?.let { a ->
            check(a.main); check(a.render); check(a.other)
        }
    }
    return if (max >= 0) max + 1 else null
}
