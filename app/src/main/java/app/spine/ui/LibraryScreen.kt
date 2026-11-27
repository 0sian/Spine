package app.spine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF161616)
private val TextHi = Color(0xFFF1F1F1)
private val TextLo = Color(0xFF9A9A9A)

@Composable
fun LibraryScreen(
    books: List<Book>,
    status: String?,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (Book) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(status, onRescan)
            if (books.isEmpty() && status == null) {
                EmptyState(hasAccess, onGrantAccess, Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.weight(1f).navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(books.chunked(2)) { row -> ShelfRow(row, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(status: String?, onRescan: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(status ?: "", color = TextLo, fontSize = 13.sp)
        Box {
            Text("⋮", color = TextHi, fontSize = 22.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rescan library") }, onClick = { menu = false; onRescan() })
            }
        }
    }
}

@Composable
private fun ShelfRow(row: List<Book>, onOpen: (Book) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // books standing on the plank
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            row.forEach { b -> Box(Modifier.weight(1f).padding(horizontal = 14.dp).clickable { onOpen(b) }, contentAlignment = Alignment.TopCenter) { BookCover(b) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        // the plank
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .shadow(8.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF3E3E3E), Color(0xFF242424))))
        )
        // labels
        Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            row.forEach { b -> BookLabel(b, Modifier.weight(1f).clickable { onOpen(b) }) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BookLabel(b: Book, modifier: Modifier) {
    Column(modifier) {
        Text(b.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(b.author, color = TextLo, fontSize = 12.sp, maxLines = 1)
        Text(if (b.progress == 0) "Unread" else "${b.progress}% read", color = TextLo, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(hasAccess: Boolean, onGrantAccess: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (hasAccess) "No EPUBs in Documents/Books" else "Spine needs to see your books", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasAccess) "Drop some .epub files in there and pull down the menu to rescan."
            else "Allow file access once and your Documents/Books folder becomes the shelf.",
            color = TextLo, fontSize = 14.sp,
        )
        if (!hasAccess) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Allow access", color = Color(0xFF161616), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(TextHi).clickable(onClick = onGrantAccess).padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}
