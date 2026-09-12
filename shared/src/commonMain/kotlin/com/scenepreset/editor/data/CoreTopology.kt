// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * User-customized CPU topology. When active, the app treats the device as if
 * it had exactly these clusters (and total core count) instead of the real
 * hardware detection, so imported configs for other SoCs can be mapped.
 */
@Serializable
data class CoreTopology(
    /** Total number of cores the app should assume. */
    val coreCount: Int,
    /** Clusters ordered from strongest to weakest. */
    val clusters: List<CoreCluster> = emptyList(),
) {
    @Serializable
    data class CoreCluster(
        val label: String,
        /** Human-entered core spec, e.g. "0,1,3,5-7" (half/full-width separators allowed). */
        val cores: String,
    )

    fun normalized(): CoreTopology = copy(
        coreCount = coreCount.coerceAtLeast(1),
        clusters = clusters.map { it.copy(label = it.label.trim(), cores = it.cores.trim()) },
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

        fun encode(topology: CoreTopology): String = json.encodeToString(topology)

        fun decode(text: String): CoreTopology? = runCatching { json.decodeFromString<CoreTopology>(text) }.getOrNull()
    }
}

/** Splits a user-entered core list ("0,1，3；5-7") into a set of indices. */
fun parseCoreSpec(spec: String, coreCount: Int = Int.MAX_VALUE): Set<Int> {
    val out = mutableSetOf<Int>()
    // Accept half- and full-width commas / semicolons / spaces as separators.
    spec.split(',', '，', ';', '；', ' ').forEach { raw ->
        val part = raw.trim()
        if (part.isEmpty()) return@forEach
        val range = part.split('-', '－')
        if (range.size == 2) {
            val a = range[0].trim().toIntOrNull() ?: return@forEach
            val b = range[1].trim().toIntOrNull() ?: return@forEach
            for (i in minOf(a, b)..maxOf(a, b)) if (i in 0 until coreCount) out += i
        } else {
            part.toIntOrNull()?.let { if (it in 0 until coreCount) out += it }
        }
    }
    return out
}

/** Validation problems of a custom topology, or empty when usable. */
fun CoreTopology.validationIssues(): List<String> {
    val issues = mutableListOf<String>()
    val seen = mutableMapOf<Int, String>()
    clusters.forEach { cluster ->
        if (cluster.label.isBlank()) issues += "存在未命名的核心簇"
        val cores = parseCoreSpec(cluster.cores, coreCount)
        if (cluster.cores.isNotBlank() && cores.isEmpty()) issues += "核心簇「${cluster.label}」没有有效核心"
        cores.forEach { core ->
            val owner = seen.put(core, cluster.label)
            if (owner != null) issues += "核心 $core 同时属于「$owner」和「${cluster.label}」"
        }
    }
    val covered = seen.keys.size
    if (covered < coreCount) {
        val missing = (0 until coreCount).filter { it !in seen.keys }
        issues += "核心 ${missing.joinToString("、")} 没有归属任何核心簇"
    }
    val labels = clusters.map { it.label.trim() }
    labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
        issues += "核心簇名称「$it」重复"
    }
    return issues
}
