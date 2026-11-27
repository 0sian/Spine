package app.spine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.max

private val Page = Color(0xFFEDE4D0)
private val PageShade = Color(0xFFCFC3A8)

/**
 * A book seen from slightly above and to the right: you read the front cover,
 * the top page edge as a thin foreshortened strip, and a sliver of the fore-edge.
 */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth(0.905f)
            .aspectRatio(0.67f)
            .graphicsLayer {
                rotationZ = book.tilt
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        val sideW = size.width * 0.07f          // fore-edge depth
        val topH = size.height * 0.085f         // page-top depth
        val cw = size.width - sideW
        val ch = size.height - topH
        val top = topH

        drawShadow(cw, ch, top, sideW)
        drawPages(cw, ch, top, sideW, book)
        drawFront(book, cw, ch, top, measurer)
    }
}

private fun DrawScope.drawShadow(cw: Float, ch: Float, top: Float, sideW: Float) {
    drawRoundRect(
        Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(sideW * 0.2f, top + ch - 4f),
        size = Size(cw + sideW * 0.9f, 8f),
        cornerRadius = CornerRadius(8f),
    )
}

private fun DrawScope.drawPages(cw: Float, ch: Float, top: Float, s: Float, book: Book) {
    val board = book.cover.copy(alpha = 1f)
    // top face (parallelogram): front edge is the cover's top edge, back edge is shifted up/right
    val topFace = Path().apply {
        moveTo(0f, top); lineTo(cw, top); lineTo(cw + s, 0f); lineTo(s, 0f); close()
    }
    drawPath(topFace, Brush.verticalGradient(listOf(PageShade, Page), 0f, top))
    // fine page lines running along the top face
    for (i in 1..5) {
        val f = i / 6f
        val y = top * (1f - f)
        drawLine(PageShade.copy(alpha = 0.7f), Offset(s * f, y), Offset(cw + s * f, y), 1f)
    }
    // back board rim
    drawLine(board, Offset(s, 0f), Offset(cw + s, 0f), 3f)

    // fore-edge (right face)
    val side = Path().apply {
        moveTo(cw, top); lineTo(cw + s, 0f); lineTo(cw + s, ch); lineTo(cw, top + ch); close()
    }
    drawPath(side, Brush.horizontalGradient(listOf(Page, PageShade), cw, cw + s))
    for (i in 1..4) {
        val x = cw + s * i / 5f
        drawLine(PageShade.copy(alpha = 0.6f), Offset(x, top * (1f - i / 5f)), Offset(x, ch + top * (1f - i / 5f) ), 1f)
    }
    drawLine(board, Offset(cw + s, 0f), Offset(cw + s, ch), 3f)
}

private fun DrawScope.drawFront(book: Book, cw: Float, ch: Float, top: Float, tm: TextMeasurer) {
    val r = CornerRadius(4f)
    val img = book.image
    if (img != null) {
        // centre-crop the artwork to the board's aspect ratio
        val target = cw / ch
        val srcAspect = img.width.toFloat() / img.height
        val (sw, sh) = if (srcAspect > target) (img.height * target).toInt() to img.height else img.width to (img.width / target).toInt()
        drawImage(
            img,
            srcOffset = androidx.compose.ui.unit.IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
            srcSize = androidx.compose.ui.unit.IntSize(sw, sh),
            dstOffset = androidx.compose.ui.unit.IntOffset(0, top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(cw.toInt(), ch.toInt()),
        )
    } else {
        drawRoundRect(book.cover, Offset(0f, top), Size(cw, ch), r)
    }
    // hinge groove + soft light across the board
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(.35f), Color.Transparent), 0f, cw * 0.1f),
        Offset(0f, top), Size(cw * 0.1f, ch))
    drawLine(Color.Black.copy(.45f), Offset(cw * 0.065f, top), Offset(cw * 0.065f, top + ch), 2f)
    drawRect(Brush.linearGradient(listOf(Color.White.copy(.10f), Color.Transparent), Offset(0f, top), Offset(cw, top + ch)),
        Offset(0f, top), Size(cw, ch))
    drawRoundRect(Color.White.copy(.08f), Offset(0f, top), Size(cw, ch), r, style = Stroke(1.5f))

    if (img == null) drawTitle(book, cw, ch, top, tm)
}

private fun DrawScope.drawTitle(book: Book, cw: Float, ch: Float, top: Float, tm: TextMeasurer) {
    val maxW = (cw * 0.78f)
    val words = book.title.uppercase().split(" ")
    var sp = cw * 0.20f
    fun style(px: Float) = TextStyle(
        color = book.ink, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = (px / density / fontScale).sp, letterSpacing = (-0.5).sp, lineHeight = (px * 1.0f / density / fontScale).sp,
    )
    // shrink until the longest word fits on one line
    while (sp > 10f && words.maxOf { tm.measure(it, style(sp)).size.width } > maxW) sp *= 0.92f
    val text = words.joinToString("\n")
    val laid = tm.measure(text, style(sp))
    drawText(laid, topLeft = Offset(cw * 0.11f, top + ch * 0.07f))

    val au = tm.measure(book.author, TextStyle(color = book.ink.copy(alpha = .75f), fontSize = (cw * 0.07f / density / fontScale).sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.sp))
    drawText(au, topLeft = Offset(cw * 0.11f, top + ch * 0.93f - au.size.height))
}
