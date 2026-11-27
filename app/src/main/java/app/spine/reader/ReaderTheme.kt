package app.spine.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

/** A reading theme: page colours, typography and spacing, all pushed to the EPUB as preferences. */
@OptIn(ExperimentalReadiumApi::class)
data class ReaderTheme(
    val name: String,
    val bg: Int,
    val text: Int,
    val font: FontFamily,
    val lineHeight: Double = 1.5,
    val margins: Double = 1.3,
    val align: org.readium.r2.navigator.preferences.TextAlign = org.readium.r2.navigator.preferences.TextAlign.JUSTIFY,
) {
    fun preferences(fontScale: Double) = EpubPreferences(
        backgroundColor = Color(bg),
        textColor = Color(text),
        fontFamily = font,
        fontSize = fontScale,
        lineHeight = lineHeight,
        pageMargins = margins,
        publisherStyles = false,
        textAlign = align,
    )
}

@OptIn(ExperimentalReadiumApi::class)
val readerThemes = listOf(
    ReaderTheme("Midnight", 0xFF121212.toInt(), 0xFFD6D2C8.toInt(), FontFamily.SERIF),
    ReaderTheme("Parchment", 0xFFF0E4C8.toInt(), 0xFF3A2D1E.toInt(), FontFamily.SERIF, lineHeight = 1.6),
    ReaderTheme("Terminal", 0xFF07130C.toInt(), 0xFF5FE08A.toInt(), FontFamily.MONOSPACE, lineHeight = 1.55,
        align = org.readium.r2.navigator.preferences.TextAlign.START),
    ReaderTheme("Paper", 0xFFFAFAFA.toInt(), 0xFF1C1C1C.toInt(), FontFamily.SANS_SERIF),
)
