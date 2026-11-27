package app.spine.library

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Match(val title: String, val author: String?, val year: Int?)

/**
 * Looks a book up on Open Library (free, no account). Only the title and the author's surname are sent.
 * Throws [IOException] when the network is unavailable; returns null when there is simply no good match.
 */
object OpenLibrary {
    private val junk = Regex("(?i)sparknotes|study guide|summary|movie book|workbook|cliffsnotes|trivia|analysis|screenplay|coloring|\\[\\d+/\\d+]|audio")

    fun lookup(title: String, author: String): Match? {
        val surname = author.substringBefore(",").trim().let { a ->
            if (author.contains(",")) a else a.substringBefore(" and ").trim().split(' ').lastOrNull().orEmpty()
        }.lowercase()
        val queries = listOf(title, title.substringBefore(":").substringBefore(" - ").trim()).filter { it.length >= 3 }.distinct()
        for (q in queries) {
            val docs = search(q, surname)
            val want = Titles.tokens(q)
            if (want.isEmpty()) continue
            val scored = docs.mapNotNull { d ->
                val t = d.optString("title")
                if (t.isBlank() || junk.containsMatchIn(t)) return@mapNotNull null
                val authors = d.optJSONArray("author_name")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
                if (surname.isNotBlank() && authors.none { it.lowercase().contains(surname) }) return@mapNotNull null
                val have = Titles.tokens(t)
                val contained = want.count { it in have }.toDouble() / want.size
                val precise = if (have.isEmpty()) 0.0 else want.count { it in have }.toDouble() / have.size
                if (contained < 0.8) return@mapNotNull null
                Triple(contained * 0.7 + precise * 0.3, d, authors)
            }
            val best = scored.sortedWith(compareByDescending<Triple<Double, JSONObject, List<String>>> { it.first }
                .thenBy { if (it.second.has("first_publish_year")) it.second.getInt("first_publish_year") else 9999 }).firstOrNull() ?: continue
            val d = best.second
            return Match(d.getString("title"), best.third.firstOrNull(), if (d.has("first_publish_year")) d.getInt("first_publish_year") else null)
        }
        return null
    }

    private fun search(title: String, surname: String): List<JSONObject> {
        val url = "https://openlibrary.org/search.json?limit=10&fields=title,author_name,first_publish_year" +
            "&title=" + URLEncoder.encode(title, "UTF-8") +
            (if (surname.isNotBlank()) "&author=" + URLEncoder.encode(surname, "UTF-8") else "")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "Spine/0.1 (personal reading app)")
            if (c.responseCode == 429 || c.responseCode >= 500) throw IOException("Open Library busy: ${c.responseCode}")
            if (c.responseCode != 200) return emptyList()
            val docs = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("docs") ?: return emptyList()
            return List(docs.length()) { docs.getJSONObject(it) }
        } finally {
            c.disconnect()
        }
    }
}
