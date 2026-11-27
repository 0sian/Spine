package app.spine.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.security.MessageDigest

data class Entry(
    val uri: String,
    val size: Long,
    val modified: Long,
    val title: String,
    val author: String,
    val coverFile: String?,
)

/** Scans a folder tree for EPUBs, reads title/author/cover with Readium, caches the result as JSON. */
@OptIn(ExperimentalReadiumApi::class)
class Library(private val ctx: Context) {
    private val http = DefaultHttpClient()
    private val retriever = AssetRetriever(ctx.contentResolver, http)
    private val opener = PublicationOpener(DefaultPublicationParser(ctx, http, retriever, pdfFactory = null))
    private val coversDir = File(ctx.filesDir, "covers").apply { mkdirs() }
    private val cacheFile = File(ctx.filesDir, "library.json")

    fun coverPath(name: String) = File(coversDir, name)

    fun loadCache(): List<Entry> = runCatching {
        val arr = JSONArray(cacheFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getString("uri"), o.getLong("size"), o.getLong("modified"), o.getString("title"),
                o.getString("author"), o.optString("cover").ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    private fun saveCache(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("size", it.size).put("modified", it.modified)
                .put("title", it.title).put("author", it.author).put("cover", it.coverFile ?: ""))
        }
        cacheFile.writeText(arr.toString())
    }

    /** [onProgress] gets (done, total, entriesSoFar) after each book. */
    suspend fun scan(root: File, onProgress: (Int, Int, List<Entry>) -> Unit): List<Entry> = withContext(Dispatchers.IO) {
        val files = root.walkTopDown().filter { it.isFile && it.extension.equals("epub", ignoreCase = true) }.toList()

        val cached = loadCache().associateBy { it.uri }
        val result = mutableListOf<Entry>()
        files.forEachIndexed { i, f ->
            val uri = Uri.fromFile(f).toString()
            val old = cached[uri]
            val entry = if (old != null && old.size == f.length() && old.modified == f.lastModified()) old
            else readEntry(f) ?: Entry(uri, f.length(), f.lastModified(), f.nameWithoutExtension, "", null)
            result += entry
            onProgress(i + 1, files.size, result.toList())
        }
        saveCache(result)
        // drop covers of books that disappeared
        val keep = result.mapNotNull { it.coverFile }.toSet()
        coversDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        result
    }

    private suspend fun readEntry(f: File): Entry? {
        val asset = retriever.retrieve(f).getOrElse { return null }
        val pub = opener.open(asset, allowUserInteraction = false).getOrElse { return null }
        try {
            val title = pub.metadata.title?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension
            val author = pub.metadata.authors.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }.joinToString(", ")
            var coverName: String? = null
            pub.cover()?.let { bmp ->
                val scaled = if (bmp.width > 480) Bitmap.createScaledBitmap(bmp, 480, (bmp.height * 480f / bmp.width).toInt(), true) else bmp
                coverName = sha(f.path) + ".jpg"
                File(coversDir, coverName!!).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            return Entry(Uri.fromFile(f).toString(), f.length(), f.lastModified(), title, author, coverName)
        } finally {
            pub.close()
        }
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
