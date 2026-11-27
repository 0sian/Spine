package app.spine.library

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.spine.ui.Book
import app.spine.ui.ShelfItem
import app.spine.ui.SortMode
import app.spine.ui.fallbackColors
import app.spine.ui.tiltFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val library = Library(app)
    private val booksDir = java.io.File(Environment.getExternalStorageDirectory(), "Documents/Books")
    private val reading = app.getSharedPreferences("spine_reader", 0)
    private val prefs = app.getSharedPreferences("spine_library", 0)

    private var entries: List<Entry> = emptyList()

    /** Manual choices: uri -> collection name, or "" meaning "keep this one out of any collection". */
    private val manual: MutableMap<String, String> = loadMap("collections")
    private val sortModes: MutableMap<String, String> = loadMap("sort")
    /** Titles you typed in yourself; these beat every automatic cleanup. */
    private val renames: MutableMap<String, String> = loadMap("rename")
    private val customOrder: MutableMap<String, List<String>> = loadOrders()

    /** uris of each shown collection in their current display order, for move up/down. */
    private var shownOrder: Map<String, List<String>> = emptyMap()

    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    val items: StateFlow<List<ShelfItem>> = _items
    private val _collections = MutableStateFlow<List<String>>(emptyList())
    val collections: StateFlow<List<String>> = _collections
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _lookup = MutableStateFlow(prefs.getBoolean("lookup", true))
    val lookupEnabled: StateFlow<Boolean> = _lookup
    private val _hasAccess = MutableStateFlow(Environment.isExternalStorageManager())
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private var job: Job? = null

    init {
        viewModelScope.launch {
            entries = withContext(Dispatchers.IO) { library.loadCache() }
            publish()
            if (_hasAccess.value) rescan()
        }
    }

    fun recheckAccess() {
        val granted = Environment.isExternalStorageManager()
        val changed = granted != _hasAccess.value
        _hasAccess.value = granted
        if (granted && changed) rescan()
    }

    fun rescan() {
        if (!Environment.isExternalStorageManager() || !booksDir.isDirectory) return
        job?.cancel()
        job = viewModelScope.launch {
            _status.value = "Scanning…"
            try {
                library.scan(booksDir) { done, total, list ->
                    entries = list
                    _status.value = "Scanning $done/$total"
                    viewModelScope.launch { publish() }
                }
                enrich()
            } finally {
                _status.value = null
                publish()
            }
        }
    }

    fun refreshProgress() { viewModelScope.launch { publish() } }

    // ---- collection actions -------------------------------------------------

    /** Put these books in [name] (creating it if new). */
    fun group(uris: Set<String>, name: String) {
        uris.forEach { manual[it] = name.trim() }
        saveMaps(); viewModelScope.launch { publish() }
    }

    /** Take these books out of whatever collection they are in, including automatic ones. */
    fun ungroup(uris: Set<String>) {
        uris.forEach { manual[it] = "" }
        saveMaps(); viewModelScope.launch { publish() }
    }

    fun setSort(name: String, mode: SortMode) {
        sortModes[name] = mode.name
        saveMaps(); viewModelScope.launch { publish() }
    }

    /** Move a book one place earlier (-1) or later (+1); this switches the collection to custom order. */
    fun move(name: String, uri: String, delta: Int) {
        val order = (shownOrder[name] ?: return).toMutableList()
        val i = order.indexOf(uri)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        order[i] = order[j].also { order[j] = order[i] }
        customOrder[name] = order
        sortModes[name] = SortMode.Custom.name
        saveMaps(); viewModelScope.launch { publish() }
    }

    fun rename(uri: String, title: String) {
        if (title.isBlank()) renames.remove(uri) else renames[uri] = title.trim()
        saveMaps(); viewModelScope.launch { publish() }
    }

    fun toggleLookup() {
        _lookup.value = !_lookup.value
        prefs.edit().putBoolean("lookup", _lookup.value).apply()
        if (_lookup.value) rescan()
    }

    /** Match books against Open Library for clean titles and real first-publication years. */
    private suspend fun enrich() {
        if (!_lookup.value) return
        val todo = entries.filter { !it.looked }
        for ((i, e) in todo.withIndex()) {
            _status.value = "Matching titles ${i + 1}/${todo.size}"
            val match = try {
                withContext(Dispatchers.IO) { OpenLibrary.lookup(Titles.clean(e.title, e.author), e.author) }
            } catch (_: IOException) {
                return // offline or rate limited: leave the rest for the next scan
            }
            entries = entries.map { if (it.uri == e.uri) it.copy(looked = true, canonicalTitle = match?.title, canonicalAuthor = match?.author, year = match?.year) else it }
            withContext(Dispatchers.IO) { library.saveCache(entries) }
            publish()
            kotlinx.coroutines.delay(200)
        }
    }

    // ---- persistence --------------------------------------------------------

    private fun loadMap(key: String): MutableMap<String, String> = runCatching {
        val o = JSONObject(prefs.getString(key, "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun loadOrders(): MutableMap<String, List<String>> = runCatching {
        val o = JSONObject(prefs.getString("order", "{}")!!)
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } } }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveMaps() {
        prefs.edit()
            .putString("collections", JSONObject(manual as Map<*, *>).toString())
            .putString("sort", JSONObject(sortModes as Map<*, *>).toString())
            .putString("rename", JSONObject(renames as Map<*, *>).toString())
            .putString("order", JSONObject().also { o -> customOrder.forEach { (k, v) -> o.put(k, JSONArray(v)) } }.toString())
            .apply()
    }

    // ---- building the shelf -------------------------------------------------

    private fun progressOf(uri: String): Int = runCatching {
        val loc = JSONObject(reading.getString("book:$uri", null) ?: return 0).optJSONObject("locations")
        ((loc?.optDouble("totalProgression", 0.0) ?: 0.0) * 100).toInt().coerceIn(0, 100)
    }.getOrDefault(0)

    /** Manual choice wins, then the book's own series metadata, then the sub-folder it sits in. */
    private fun rawCollection(e: Entry): String? = (manual[e.uri] ?: e.series ?: e.folder)?.takeIf { it.isNotBlank() }

    /** Your own rename, else Open Library's title when ours is incomplete, else our offline-cleaned title. */
    private fun displayTitle(e: Entry): String {
        renames[e.uri]?.takeIf { it.isNotBlank() }?.let { return it }
        val cleaned = Titles.clean(e.title, e.author)
        val canon = e.canonicalTitle
        // when they only differ in casing/punctuation keep ours; otherwise theirs is the fuller, properly spelled one
        return if (canon != null && Titles.norm(canon) != Titles.norm(cleaned)) canon else cleaned
    }

    private fun displayAuthor(e: Entry): String {
        val canon = e.canonicalAuthor
        val ours = e.author
        return if (canon != null && (ours.isBlank() || Regex("^[^,]+,\\s*[^,]+$").matches(ours))) canon else ours
    }

    private fun releaseYear(e: Entry): String =
        (e.year ?: e.published?.take(4)?.toIntOrNull() ?: 9999).toString().padStart(4, '0')

    private fun numberIn(title: String, collection: String): Double? =
        Regex("(?i)${Regex.escape(collection)}\\s*0*(\\d{1,2})\\b").find(title)?.groupValues?.get(1)?.toDoubleOrNull()

    private suspend fun publish() {
        val built = withContext(Dispatchers.IO) {
            val manuallyNamed = entries.filter { !manual[it.uri].isNullOrBlank() }.mapNotNull { manual[it.uri] }.toSet()
            val raw = entries.associateWith { rawCollection(it) }.toMutableMap()
            // an automatic collection needs at least two books; one you made yourself can hold one
            fun shownNames() = raw.values.filterNotNull().groupingBy { it }.eachCount().filter { (name, n) -> n >= 2 || name in manuallyNamed }.keys
            // books with no series data join a collection whose name their title starts with ("Harry Potter 4 - …")
            val first = shownNames().filter { it.length >= 4 }.sortedByDescending { it.length }
            entries.filter { raw[it] == null && manual[it.uri] == null }.forEach { e ->
                first.firstOrNull { e.title.startsWith(it, ignoreCase = true) || displayTitle(e).startsWith(it, ignoreCase = true) }?.let { raw[e] = it }
            }
            val shown = shownNames()

            data class Row(val book: Book, val entry: Entry, val lastRead: Long, val collection: String?)
            val rows = entries.map { e ->
                val col = raw[e]?.takeIf { it in shown }
                val bmp = e.coverFile?.let { BitmapFactory.decodeFile(library.coverPath(it).path) }
                val (bg, ink) = fallbackColors(e.uri)
                Row(
                    Book(e.uri, displayTitle(e), displayAuthor(e), progressOf(e.uri), bmp?.asImageBitmap(), bg, ink, tiltFor(e.uri), collection = col),
                    e, reading.getLong("read:${e.uri}", 0L), col,
                )
            }

            val keyed = mutableListOf<Triple<Long, String, ShelfItem>>()
            val orders = mutableMapOf<String, List<String>>()
            rows.filter { it.collection == null }.forEach { keyed += Triple(it.lastRead, it.book.title.lowercase(), ShelfItem.Single(it.book)) }
            rows.filter { it.collection != null }.groupBy { it.collection!! }.forEach { (name, members) ->
                val mode = runCatching { SortMode.valueOf(sortModes[name] ?: "Series") }.getOrDefault(SortMode.Series)
                // Books with no number in their metadata or title take the gaps in the sequence
                // (a series 2..7 plus one unnumbered book means the unnumbered one is book 1).
                val known = members.associateWith { it.entry.seriesIndex ?: numberIn(it.entry.title, name) }
                val taken = known.values.filterNotNull().map { it.toInt() }.toSet()
                val gaps = (1..members.size).filter { it !in taken }.iterator()
                val filled = HashMap<String, Double>()
                members.filter { known[it] == null }
                    .sortedWith(compareBy<Row> { releaseYear(it.entry) }.thenBy { it.book.title.lowercase() })
                    .forEach { filled[it.entry.uri] = if (gaps.hasNext()) gaps.next().toDouble() else Double.MAX_VALUE }
                fun series(r: Row) = known[r] ?: filled[r.entry.uri] ?: Double.MAX_VALUE
                fun date(r: Row) = releaseYear(r.entry)
                val ordered = when (mode) {
                    SortMode.Series -> members.sortedWith(compareBy<Row> { series(it) }.thenBy { date(it) }.thenBy { it.book.title.lowercase() })
                    SortMode.Release -> members.sortedWith(compareBy<Row> { date(it) }.thenBy { series(it) }.thenBy { it.book.title.lowercase() })
                    SortMode.Title -> members.sortedBy { it.book.title.lowercase() }
                    SortMode.Custom -> {
                        val saved = customOrder[name].orEmpty()
                        members.sortedWith(compareBy<Row> { saved.indexOf(it.entry.uri).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { series(it) })
                    }
                }
                orders[name] = ordered.map { it.entry.uri }
                keyed += Triple(members.maxOf { it.lastRead }, name.lowercase(), ShelfItem.Group(name, ordered.map { it.book }, mode))
            }
            shownOrder = orders
            val sorted = keyed.sortedWith(compareByDescending<Triple<Long, String, ShelfItem>> { it.first }.thenBy { it.second }).map { it.third }
            sorted to (shown + manuallyNamed).sorted()
        }
        _items.value = built.first
        _collections.value = built.second
    }
}
