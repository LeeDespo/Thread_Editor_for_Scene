// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.data

/** Parses a Scene core mask such as "0-6", "7", "4,6" into a set of core indices. */
fun parseCoreMask(mask: String, coreCount: Int = 8): Set<Int> {
    val set = mutableSetOf<Int>()
    mask.split(',', ' ', ';').forEach { raw ->
        val part = raw.trim()
        if (part.isEmpty()) return@forEach
        val range = part.split('-')
        if (range.size == 2) {
            val a = range[0].trim().toIntOrNull() ?: return@forEach
            val b = range[1].trim().toIntOrNull() ?: return@forEach
            for (i in a..b) if (i in 0 until coreCount) set.add(i)
        } else {
            part.toIntOrNull()?.let { if (it in 0 until coreCount) set.add(it) }
        }
    }
    return set
}

/** Builds a compact core mask ("0-3", "4-6,7") from a set of indices. */
fun buildCoreMask(selected: Set<Int>): String {
    if (selected.isEmpty()) return ""
    val sorted = selected.sorted()
    val parts = mutableListOf<String>()
    var start = sorted[0]
    var prev = sorted[0]
    for (i in 1..sorted.size) {
        val current = if (i < sorted.size) sorted[i] else Int.MIN_VALUE
        if (current == prev + 1) {
            prev = current
            continue
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        if (i < sorted.size) {
            start = current
            prev = current
        }
    }
    return parts.joinToString(",")
}
