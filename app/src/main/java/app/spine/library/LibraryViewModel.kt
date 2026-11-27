package app.spine.library

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.spine.ui.Book
import app.spine.ui.fallbackColors
import app.spine.ui.tiltFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val library = Library(app)
    private val booksDir = java.io.File(Environment.getExternalStorageDirectory(), "Documents/Books")
    private val reading = app.getSharedPreferences("spine_reader", 0)

    private var entries: List<Entry> = emptyList()
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
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

    /** Call when returning from the system settings page. */
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
            } finally {
                _status.value = null
                publish()
            }
        }
    }

    /** Re-read reading progress (after returning from the reader). */
    fun refreshProgress() { viewModelScope.launch { publish() } }

    private fun progressOf(uri: String): Int = runCatching {
        val loc = JSONObject(reading.getString("book:$uri", null) ?: return 0).optJSONObject("locations")
        ((loc?.optDouble("totalProgression", 0.0) ?: 0.0) * 100).toInt().coerceIn(0, 100)
    }.getOrDefault(0)

    private suspend fun publish() {
        val list = withContext(Dispatchers.IO) {
            entries.sortedWith(
                compareByDescending<Entry> { reading.getLong("read:${it.uri}", 0L) }.thenBy { it.title.lowercase() }
            ).map { e ->
                val bmp = e.coverFile?.let { BitmapFactory.decodeFile(library.coverPath(it).path) }
                val (bg, ink) = fallbackColors(e.uri)
                Book(e.uri, e.title, e.author, progressOf(e.uri), bmp?.asImageBitmap(), bg, ink, tiltFor(e.uri))
            }
        }
        _books.value = list
    }
}
