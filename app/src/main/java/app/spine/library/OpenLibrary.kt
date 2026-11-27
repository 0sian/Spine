package app.spine.library

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Match(val title: String, val author: String?, val year: Int?)

/**
 * Open Library (free, no account). Only a book's title and its author's surname are ever sent.
 * Functions throw [IOException] when the network is unavailable and return null/empty for "no good match".
 */
object OpenLibrary {
    private val junk = Regex("(?i)sparknotes|study guide|summary|movie book|workbook|cliffsnotes|trivia|analysis|screenplay|coloring|\\[\\d+/\\d+]|audio")
    private const val UA = "Spine/0.1 (personal reading app)"

    private fun surnameOf(author: String): String =
        author.substringBefore(",").trim().let { a ->
            if (author.contains(",")) a else a.substringBefore(" and ").trim().split(' ').lastOrNull().orEmpty()
        }.lowercase()

    /** How well a search result fits the wanted title, or null if it should be ignored. */
    private fun score(want: Set<String>, surname: String, d: JSONObject): Double? {
        val t = d.optString("title")
        if (t.isBlank() || junk.containsMatchIn(t) || want.isEmpty()) return null
        val authors = d.optJSONArray("author_name")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        if (surname.isNotBlank() && authors.none { it.lowercase().contains(surname) }) return null
        val have = Titles.tokens(t)
        val hit = want.count { it in have }.toDouble()
        val contained = hit / want.size
        val precise = if (have.isEmpty()) 0.0 else hit / have.size
        return if (contained < 0.8) null else contained * 0.7 + precise * 0.3
    }

    private fun queriesFor(title: String) = listOf(title, title.substringBefore(":").substringBefore(" - ").trim()).filter { it.length >= 3 }.distinct()

    fun lookup(title: String, author: String): Match? {
        val surname = surnameOf(author)
        for (q in queriesFor(title)) {
            val want = Titles.tokens(q)
            val best = search(q, surname).mapNotNull { d -> score(want, surname, d)?.let { it to d } }
                .sortedWith(compareByDescending<Pair<Double, JSONObject>> { it.first }.thenBy { if (it.second.has("first_publish_year")) it.second.getInt("first_publish_year") else 9999 })
                .firstOrNull() ?: continue
            val d = best.second
            val authors = d.optJSONArray("author_name")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            return Match(d.getString("title"), authors.firstOrNull(), if (d.has("first_publish_year")) d.getInt("first_publish_year") else null)
        }
        return null
    }

    /** Ids of cover images for every edition of the book, best matches first. */
    fun coverIds(title: String, author: String): List<Int> {
        val surname = surnameOf(author)
        val ids = LinkedHashSet<Int>()
        for (q in queriesFor(title)) {
            val want = Titles.tokens(q)
            val works = search(q, surname).mapNotNull { d -> score(want, surname, d)?.let { it to d } }
                .sortedByDescending { it.first }.map { it.second }.take(3)
            for (d in works) {
                if (d.optInt("cover_i", -1) > 0) ids += d.getInt("cover_i")
                val key = d.optString("key")
                if (key.startsWith("/works/")) editionCovers(key).forEach { ids += it }
            }
            if (ids.size >= 6) break
        }
        return ids.take(36)
    }

    private fun editionCovers(workKey: String): List<Int> {
        val body = get("https://openlibrary.org$workKey/editions.json?limit=60") ?: return emptyList()
        val entries = JSONObject(body).optJSONArray("entries") ?: return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until entries.length()) {
            val covers = entries.getJSONObject(i).optJSONArray("covers") ?: continue
            for (j in 0 until covers.length()) covers.optInt(j, -1).takeIf { it > 0 }?.let(out::add)
        }
        return out
    }

    fun thumbUrl(id: Int) = "https://covers.openlibrary.org/b/id/$id-M.jpg?default=false"
    fun fullUrl(id: Int) = "https://covers.openlibrary.org/b/id/$id-L.jpg?default=false"

    private fun search(title: String, surname: String): List<JSONObject> {
        val url = "https://openlibrary.org/search.json?limit=10&fields=key,title,author_name,first_publish_year,cover_i" +
            "&title=" + URLEncoder.encode(title, "UTF-8") +
            (if (surname.isNotBlank()) "&author=" + URLEncoder.encode(surname, "UTF-8") else "")
        val docs = JSONObject(get(url) ?: return emptyList()).optJSONArray("docs") ?: return emptyList()
        return List(docs.length()) { docs.getJSONObject(it) }
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode == 429 || c.responseCode >= 500) throw IOException("Open Library busy: ${c.responseCode}")
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    /** Downloads an image, or null if it doesn't exist. */
    fun image(url: String, maxWidth: Int): android.graphics.Bitmap? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode != 200) null
            else android.graphics.BitmapFactory.decodeStream(c.inputStream)?.let { b ->
                if (b.width > maxWidth) android.graphics.Bitmap.createScaledBitmap(b, maxWidth, (b.height * maxWidth.toFloat() / b.width).toInt(), true) else b
            }
        } finally {
            c.disconnect()
        }
    }
}
