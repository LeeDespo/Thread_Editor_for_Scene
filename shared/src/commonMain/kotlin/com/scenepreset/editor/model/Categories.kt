// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A single entry inside Scene's `categories.json`. Each entry bundles a set of
 * packages/activities under a named "category" id that rules in threads.json can
 * reference via their `categories` list.
 */
@Serializable
data class CategoryRule(
    val friendly: String = "",
    val category: String = "",
    val packages: List<String>? = null,
    val activities: List<String>? = null,
) {
    val displayName: String
        get() = friendly.ifBlank { category.ifBlank { categoriesFirstOrNull() } }

    private fun categoriesFirstOrNull(): String =
        (packages?.firstOrNull() ?: activities?.firstOrNull()).orEmpty()

    fun normalized(): CategoryRule = copy(
        packages = packages?.takeIf { it.isNotEmpty() },
        activities = activities?.takeIf { it.isNotEmpty() },
    )
}

/** Whether this category is the wildcard `"*"` group. */
val CategoryRule.isWildcard: Boolean
    get() = packages?.contains("*") == true

/** Order categories so the wildcard `"*"` group is always last. */
fun orderCategories(rules: List<CategoryRule>): List<CategoryRule> =
    rules.sortedWith(compareBy { r -> if (r.isWildcard) 1 else 0 })

/** Lenient, human readable codec for the Scene `categories.json` array. */
object CategoriesCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(text: String): List<CategoryRule> = json.decodeFromString<List<CategoryRule>>(text)

    fun encode(rules: List<CategoryRule>): String =
        json.encodeToString(orderCategories(rules).map { it.normalized() })
}
