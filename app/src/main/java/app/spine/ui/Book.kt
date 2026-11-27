package app.spine.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

enum class Motif { None }

data class Book(
    val uri: String,
    val title: String,
    val author: String,
    val progress: Int,
    val image: ImageBitmap?,
    val cover: Color,
    val ink: Color,
    val tilt: Float,
    val accent: Color = ink,
    val motif: Motif = Motif.None,
    val collection: String? = null,
    val kind: String = "book",
)

/** What the shelf shows: a lone book, or a whole collection that gets a shelf to itself. */
enum class SortMode(val label: String) {
    Series("Series order"), Release("Release date"), Title("A–Z"), Custom("Custom order")
}

sealed interface ShelfItem {
    data class Single(val book: Book) : ShelfItem
    data class Group(val name: String, val books: List<Book>, val sort: SortMode) : ShelfItem
}

private val fallbackPalette = listOf(
    Color(0xFF1B1B1B) to Color(0xFFF2EEE6),
    Color(0xFFD9503A) to Color(0xFF1A1210),
    Color(0xFF3C6E9E) to Color(0xFFF1EBDD),
    Color(0xFF9E2F2A) to Color(0xFFEFD9C0),
    Color(0xFFE6C93A) to Color(0xFF1A1A1A),
    Color(0xFF2F5D50) to Color(0xFFEAF2E3),
)

/** Stable per-book look so the shelf doesn't reshuffle between launches. */
fun fallbackColors(seed: String) = fallbackPalette[(seed.hashCode() and 0x7fffffff) % fallbackPalette.size]

/** Small hand-placed lean, between -3° and +3°, stable per book. */
fun tiltFor(seed: String): Float = (((seed.hashCode() and 0x7fffffff) % 61) - 30) / 10f
