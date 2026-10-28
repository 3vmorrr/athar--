package org.athar.app.core.ui

import com.google.gson.JsonElement

/** Missing or malformed cached/server data is unavailable, never invented zero/empty state. */
internal object HomeProjection {
    fun metric(data: JsonElement, name: String): Long? = runCatching {
        val value = data.asJsonObject.getAsJsonObject("activity").get(name).asJsonPrimitive
        if (!value.isNumber && !value.isString) return null
        value.asString.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
    }.getOrNull()

    fun items(data: JsonElement, name: String): List<JsonElement>? = runCatching {
        val items = data.asJsonObject.getAsJsonArray(name).toList()
        if (items.any { item ->
                val id = item.text("id").toIntOrNull()
                id == null || id <= 0 || item.text("title").isBlank()
            }) return null
        items
    }.getOrNull()
}

internal data class NavigationLayout(val rail: Boolean, val minimumBarHeightDp: Int)

internal object AtharNavigationLayoutPolicy {
    fun resolve(widthDp: Float, heightDp: Float, fontScale: Float): NavigationLayout {
        require(widthDp.isFinite() && widthDp > 0 && heightDp.isFinite() && heightDp > 0)
        require(fontScale.isFinite() && fontScale > 0)
        return NavigationLayout(
            rail = widthDp >= 840f || (widthDp >= 600f && heightDp < 480f),
            minimumBarHeightDp = if (fontScale >= 1.3f) (80 + 44 * (fontScale - 1).coerceAtMost(2f)).toInt() else 80,
        )
    }
}
