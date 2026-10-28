package org.athar.app.quran

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class TafsirBook(
    val id: Int,
    val name: String,
    val author: String,
)

data class TafsirBlock(
    val text: String,
    val part: Int,
    val page: Int,
    val references: String,
)

data class TafsirPagination(
    val page: Int,
    val perPage: Int,
    val total: Int,
    val pages: Int,
    val hasMore: Boolean,
) {
    init {
        require(page >= 1)
        require(perPage in 1..50)
        require(total >= 0)
        require(pages >= 0)
        require(pages == if (total == 0) 0 else (total - 1) / perPage + 1)
        require(hasMore == (pages > 0 && page < pages))
    }
    val hasPrevious: Boolean get() = page > 1
}

data class TafsirCatalog(
    val books: List<TafsirBook>,
    val pagination: TafsirPagination,
    val sourceUrl: String,
)

data class TafsirReading(
    val surah: Int,
    val ayah: Int,
    val book: TafsirBook,
    val blocks: List<TafsirBlock>,
    val pagination: TafsirPagination,
    val sourceUrl: String,
)

object TafsirContract {
    private fun JsonObject.string(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun pagination(root: JsonObject): TafsirPagination {
        val node = requireNotNull(root["pagination"]?.takeIf { it.isJsonObject }?.asJsonObject)
        val total = node["total"].asInt
        val perPage = node["per_page"].asInt
        require(perPage in 1..50 && total >= 0)
        return TafsirPagination(
            page = node["page"].asInt,
            perPage = perPage,
            total = total,
            // Old encrypted catalog cache entries predate the additive pages field.
            pages = node["pages"]?.asInt ?: if (total == 0) 0 else (total - 1) / perPage + 1,
            hasMore = node["has_more"].asBoolean,
        )
    }

    private fun book(node: JsonObject): TafsirBook {
        val id = node["id"].asInt
        val name = node.string("name").trim()
        require(id > 0 && name.isNotEmpty() && name.length <= 300)
        val author = node.string("author").trim().take(300)
        return TafsirBook(id, name, author)
    }

    private fun sourceUrl(root: JsonObject): String {
        val source = requireNotNull(root["source"]?.takeIf { it.isJsonObject }?.asJsonObject)
        val url = source.string("url")
        require(
            url == "https://quranpedia.net" ||
                url.startsWith("https://api.quranpedia.net/"),
        )
        return url
    }

    fun catalog(data: JsonElement): TafsirCatalog {
        val root = data.asJsonObject
        val books = requireNotNull(root["items"]?.takeIf { it.isJsonArray }?.asJsonArray)
            .map { book(it.asJsonObject) }
        require(books.map { it.id }.distinct().size == books.size)
        return TafsirCatalog(
            books = books,
            pagination = pagination(root),
            sourceUrl = sourceUrl(root),
        )
    }

    fun reading(data: JsonElement): TafsirReading {
        val root = data.asJsonObject
        val surah = root["surah"].asInt
        val ayah = root["ayah"].asInt
        require(surah in 1..114 && ayah > 0)
        val blocks = requireNotNull(root["items"]?.takeIf { it.isJsonArray }?.asJsonArray)
            .map { item ->
                val node = item.asJsonObject
                val text = node.string("text").trim()
                require(text.isNotEmpty() && text.length <= 200_000)
                TafsirBlock(
                    text = text,
                    part = node["part"]?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                    page = node["page"]?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                    references = node.string("references").take(500),
                )
            }
        return TafsirReading(
            surah = surah,
            ayah = ayah,
            book = book(requireNotNull(root["book"]?.asJsonObject)),
            blocks = blocks,
            pagination = pagination(root),
            sourceUrl = sourceUrl(root),
        )
    }
}
