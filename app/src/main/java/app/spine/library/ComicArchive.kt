package app.spine.library

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** The pages of a comic book, whether it is a CBZ (zip) or a CBR (rar). */
interface ComicArchive : AutoCloseable {
    val pageCount: Int
    /** The raw image bytes of a page (0-based), or null if it can't be read. */
    fun page(index: Int): ByteArray?
}

object Comics {
    val extensions = setOf("cbz", "cbr")
    private val images = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

    private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in images && !name.contains("__MACOSX")

    /** "page 2" sorts before "page 10". */
    private val natural = Comparator<String> { a, b ->
        val x = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
        val y = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger()) else p.compareTo(q)
            if (c != 0) return@Comparator c
        }
        x.size - y.size
    }

    /** Opens by what the file really is, not what it's called: plenty of .cbr files are zips and vice versa. */
    fun open(f: File): ComicArchive? = runCatching {
        val magic = f.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        val isZip = magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        val isRar = magic[0] == 'R'.code.toByte() && magic[1] == 'a'.code.toByte() && magic[2] == 'r'.code.toByte()
        when {
            isZip -> ZipComic(f)
            isRar -> RarComic(f)
            else -> null
        }
    }.getOrNull()

    private class ZipComic(f: File) : ComicArchive {
        private val zip = ZipFile(f)
        private val entries: List<ZipEntry> = zip.entries().asSequence().filter { !it.isDirectory && isImage(it.name) }.sortedWith(compareBy(natural) { it.name }).toList()
        override val pageCount get() = entries.size
        override fun page(index: Int): ByteArray? = runCatching { zip.getInputStream(entries[index]).use { it.readBytes() } }.getOrNull()
        override fun close() = zip.close()
    }

    private class RarComic(f: File) : ComicArchive {
        private val archive = Archive(f)
        private val headers: List<FileHeader> = archive.fileHeaders.filter { !it.isDirectory && isImage(it.fileName) }.sortedWith(compareBy(natural) { it.fileName })
        override val pageCount get() = headers.size
        // junrar isn't thread-safe, and a solid archive must be walked in order, so one extraction at a time
        override fun page(index: Int): ByteArray? = synchronized(archive) {
            runCatching { ByteArrayOutputStream().also { archive.extractFile(headers[index], it) }.toByteArray() }.getOrNull()
        }
        override fun close() = archive.close()
    }
}
