package app.spine.library

/** Offline title cleanup: strips the junk that file-derived metadata drags in. */
object Titles {
    private val bracketed = Regex("\\s*[\\(\\[\\{][^\\(\\)\\[\\]\\{\\}]*[\\)\\]\\}]")
    private val danglingOpen = Regex("\\s*[\\(\\[\\{].*$")
    private val siteTags = Regex("(?i)\\b(example-site)[\\w.\\-, ]*")
    private val editionNoise = Regex("(?i)\\s*[:–-]\\s*(the\\s+)?(illustrated|deluxe|special|anniversary|collector'?s|annotated|unabridged|expanded|revised|updated|movie tie-in)\\b.*$")
    private val trailingEdition = Regex("(?i)\\s+(the\\s+)?((\\d+(st|nd|rd|th)\\s+)?anniversary|deluxe|special|collector'?s|absolute|complete|definitive)(\\s+deluxe)?\\s+edition\\s*$")
    private val numberedSeries = Regex("^(\\S+(?:\\s+\\S+)+?)\\s+0*\\d{1,2}\\s*[&:]\\s*(.+)$")
    private val seriesNumber = Regex("(?i)^.{0,40}\\s0*\\d{1,2}$")

    fun tokens(s: String): Set<String> =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() && it != "the" }.toSet()

    /** Comparable form: lowercase words only, no leading article. */
    fun norm(s: String): String = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().removePrefix("the ").trim()

    fun clean(raw: String, author: String = ""): String {
        var t = raw.replace('_', ' ')
        var prev: String
        do { prev = t; t = t.replace(bracketed, "") } while (t != prev)
        t = t.replace(danglingOpen, "").replace(siteTags, "")

        val authorTokens = tokens(author)
        fun looksAuthor(p: String) = p.contains(",") || (authorTokens.isNotEmpty() && tokens(p).let { it.isNotEmpty() && it.all { w -> w in authorTokens } })
        var parts = t.split(Regex("\\s+[-–—]\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        parts = parts.filterNot { looksAuthor(it) }.ifEmpty { parts }
        // leading "Harry Potter 03" style parts are series markers, not the title, if something else follows
        while (parts.size > 1 && (seriesNumber.matches(parts.first()) || Regex("^\\d{1,3}$").matches(parts.first()))) parts = parts.drop(1)
        t = parts.joinToString(" - ")

        numberedSeries.find(t)?.let { t = it.groupValues[2] }
        t = t.replace(editionNoise, "").replace(trailingEdition, "")
        if (authorTokens.isNotEmpty()) {
            Regex("(?i)\\s+by\\s+(.+)$").find(t)?.let { m -> if (tokens(m.groupValues[1]).all { it in authorTokens }) t = t.removeRange(m.range) }
        }
        t = t.replace(Regex("\\s+"), " ").trim(' ', '-', '–', '—', '.', ',', ':', ';')
        return t.ifBlank { raw.trim() }
    }
}
