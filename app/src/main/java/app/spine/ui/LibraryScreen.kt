package app.spine.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.spine.library.OpenLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF161616)
private val TextHi = Color(0xFFF1F1F1)
private val TextLo = Color(0xFF9A9A9A)
private val Accent = Color(0xFFD7F25A)

@Composable
fun LibraryScreen(
    items: List<ShelfItem>,
    collections: List<String>,
    status: String?,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onRescan: () -> Unit,
    onOpen: (Book) -> Unit,
    lookupEnabled: Boolean,
    onToggleLookup: () -> Unit,
    onRename: (String, String) -> Unit,
    onSearchCovers: suspend (Book) -> List<Int>,
    onPickCover: (String, Int) -> Unit,
    onPickCoverFile: (String, Uri) -> Unit,
    onResetCover: (String) -> Unit,
    onGroup: (Set<String>, String) -> Unit,
    onUngroup: (Set<String>) -> Unit,
    onSort: (String, SortMode) -> Unit,
    onMove: (String, String, Int) -> Unit,
) {
    var openName by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var coverBook by remember { mutableStateOf<Book?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        val b = coverBook
        if (picked != null && b != null) onPickCoverFile(b.uri, picked)
        coverBook = null
    }

    val open = items.filterIsInstance<ShelfItem.Group>().firstOrNull { it.name == openName }
    LaunchedEffect(open) { if (openName != null && open == null) openName = null }
    val booksByUri = remember(items) {
        items.flatMap { if (it is ShelfItem.Single) listOf(it.book) else (it as ShelfItem.Group).books }.associateBy { it.uri }
    }

    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(open != null && selected.isEmpty()) { openName = null }

    val shown: List<ShelfItem> = open?.books?.map { ShelfItem.Single(it) } ?: items
    val badges = open?.books?.mapIndexed { i, b -> b.uri to i + 1 }?.toMap() ?: emptyMap()
    val selecting = selected.isNotEmpty()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                selecting -> SelectionBar(
                    count = selected.size,
                    canMove = open != null && selected.size == 1,
                    canEdit = selected.size == 1,
                    onRename = { showRename = true },
                    onCover = { coverBook = booksByUri[selected.first()]; selected = emptySet() },
                    canUngroup = selected.any { booksByUri[it]?.collection != null },
                    onMove = { d -> open?.let { g -> onMove(g.name, selected.first(), d) } },
                    onGroup = { showGroupDialog = true },
                    onUngroup = { onUngroup(selected); selected = emptySet() },
                    onCancel = { selected = emptySet() },
                )
                open != null -> CollectionBar(open, onBack = { openName = null }, onSort = { onSort(open.name, it) })
                else -> HomeBar(status, lookupEnabled, onToggleLookup, onRescan)
            }
            if (items.isEmpty() && status == null) {
                EmptyState(hasAccess, onGrantAccess, Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown.chunked(2)) { row ->
                        PairShelf(
                            row = row, selected = selected, badges = badges,
                            onTap = { item ->
                                when {
                                    item is ShelfItem.Single && selecting ->
                                        selected = if (item.book.uri in selected) selected - item.book.uri else selected + item.book.uri
                                    item is ShelfItem.Single -> onOpen(item.book)
                                    item is ShelfItem.Group && !selecting -> openName = item.name
                                }
                            },
                            onLong = { item -> if (item is ShelfItem.Single) selected = selected + item.book.uri },
                        )
                    }
                }
            }
        }
    }

    coverBook?.let { b ->
        CoverPage(
            book = b, onSearch = onSearchCovers,
            onPickId = { id -> onPickCover(b.uri, id); coverBook = null },
            onGallery = { gallery.launch("image/*") },
            onReset = { onResetCover(b.uri); coverBook = null },
            onBack = { coverBook = null },
        )
    }

    if (showRename && selected.size == 1) {
        val uri = selected.first()
        RenameDialog(
            current = booksByUri[uri]?.title.orEmpty(),
            onSave = { onRename(uri, it); showRename = false; selected = emptySet() },
            onDismiss = { showRename = false },
        )
    }

    if (showGroupDialog) {
        GroupDialog(
            existing = collections,
            onPick = { name -> onGroup(selected, name); selected = emptySet(); showGroupDialog = false },
            onDismiss = { showGroupDialog = false },
        )
    }
}

// ---- top bars ----------------------------------------------------------------

@Composable
private fun HomeBar(status: String?, lookupEnabled: Boolean, onToggleLookup: () -> Unit, onRescan: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(status ?: "", color = TextLo, fontSize = 13.sp)
        Box {
            Text("⋮", color = TextHi, fontSize = 22.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rescan library") }, onClick = { menu = false; onRescan() })
                DropdownMenuItem(
                    text = { Text("Look up clean titles online  " + if (lookupEnabled) "✓" else "✗") },
                    onClick = { menu = false; onToggleLookup() },
                )
            }
        }
    }
}

@Composable
private fun CollectionBar(group: ShelfItem.Group, onBack: () -> Unit, onSort: (SortMode) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(group.name, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.books.size} books", color = TextLo, fontSize = 12.sp)
        }
        Box {
            Text(
                group.sort.label + " ▾", color = TextHi, fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2A2A2A)).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            DropdownMenu(menu, { menu = false }) {
                listOf(SortMode.Series, SortMode.Release, SortMode.Title).forEach { m ->
                    DropdownMenuItem(text = { Text(m.label + if (m == group.sort) "  ✓" else "") }, onClick = { menu = false; onSort(m) })
                }
                if (group.sort == SortMode.Custom) DropdownMenuItem(text = { Text("Custom order  ✓") }, onClick = { menu = false })
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int, canMove: Boolean, canEdit: Boolean, canUngroup: Boolean,
    onRename: () -> Unit, onCover: () -> Unit, onMove: (Int) -> Unit, onGroup: () -> Unit, onUngroup: () -> Unit, onCancel: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✕", color = TextHi, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 10.dp, vertical = 8.dp))
        Text("$count selected", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (canMove) {
            Pill("◀") { onMove(-1) }
            Pill("▶") { onMove(1) }
        }
        Pill("Group…", highlight = true, onClick = onGroup)
        if (canEdit || canUngroup) {
            Box {
                Pill("⋮") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    if (canEdit) DropdownMenuItem(text = { Text("Change cover") }, onClick = { menu = false; onCover() })
                    if (canEdit) DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    if (canUngroup) DropdownMenuItem(text = { Text("Ungroup") }, onClick = { menu = false; onUngroup() })
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, highlight: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (highlight) Color(0xFF161616) else TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (highlight) Accent else Color(0xFF2A2A2A)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// ---- shelves -----------------------------------------------------------------

@Composable
private fun Plank() {
    Box(
        Modifier.fillMaxWidth().height(12.dp).shadow(8.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF3E3E3E), Color(0xFF242424))))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PairShelf(
    row: List<ShelfItem>, selected: Set<String>, badges: Map<String, Int>,
    onTap: (ShelfItem) -> Unit, onLong: (ShelfItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            row.forEach { item ->
                Box(
                    Modifier.weight(1f).padding(horizontal = 14.dp).combinedClickable(onClick = { onTap(item) }, onLongClick = { onLong(item) }),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (item) {
                        is ShelfItem.Single -> {
                            Box {
                                BookCover(item.book)
                                if (item.book.uri in selected) SelectedMark(Modifier.matchParentSize())
                                badges[item.book.uri]?.let { Badge(it, Modifier.align(Alignment.TopStart)) }
                            }
                        }
                        is ShelfItem.Group -> CollectionStack(item.books)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Plank()
        Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            row.forEach { item ->
                Column(Modifier.weight(1f).combinedClickable(onClick = { onTap(item) }, onLongClick = { onLong(item) })) {
                    when (item) {
                        is ShelfItem.Single -> {
                            val b = item.book
                            Text(b.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(b.author, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (b.progress == 0) "Unread" else "${b.progress}% read", color = TextLo, fontSize = 12.sp)
                        }
                        is ShelfItem.Group -> {
                            val read = item.books.count { it.progress >= 98 }
                            Text(item.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.books.size} books", color = TextLo, fontSize = 12.sp)
                            Text(if (read == 0) "Collection" else "$read of ${item.books.size} read", color = TextLo, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Several books standing behind each other, the first one in front: one tile for a whole collection. */
@Composable
private fun CollectionStack(books: List<Book>) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth()) {
        val back = books.drop(1).take(2)
        // furthest back first so the front book is painted last
        back.reversed().forEachIndexed { i, b ->
            val k = back.size - i // 2 = furthest
            BookCover(
                b.copy(tilt = 3f * k + 1f),
                Modifier.graphicsLayer {
                    translationX = with(density) { (15.dp * k).toPx() }
                    translationY = with(density) { (-9.dp * k).toPx() }
                    alpha = 0.92f
                },
            )
        }
        BookCover(books.first().copy(tilt = -1f))
    }
}

@Composable
private fun SelectedMark(modifier: Modifier) {
    Box(modifier.background(Color(0x33D7F25A)).border(3.dp, Accent, RoundedCornerShape(4.dp))) {
        Text(
            "✓", color = Color(0xFF161616), fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Accent).wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
private fun Badge(n: Int, modifier: Modifier) {
    Text(
        "$n", color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(6.dp).size(24.dp).clip(CircleShape).background(Color(0xCC161616)).wrapContentSize(Alignment.Center),
    )
}

// ---- dialog / empty state ----------------------------------------------------------

@Composable
private fun GroupDialog(existing: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF242424), titleContentColor = TextHi, textContentColor = TextHi,
        title = { Text("Add to collection", fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                existing.forEach { name ->
                    Text(
                        name, color = TextHi, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(name) }.padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    placeholder = { Text("New collection name…", color = TextLo) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextHi, unfocusedTextColor = TextHi, cursorColor = TextHi,
                        focusedBorderColor = TextHi, unfocusedBorderColor = Color(0xFF555555),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { onPick(newName.trim()) }) { Text("Create", color = if (newName.isNotBlank()) TextHi else TextLo) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) } },
    )
}

private sealed interface CoverState {
    data object Loading : CoverState
    data object Empty : CoverState
    data object Offline : CoverState
    data class Found(val ids: List<Int>) : CoverState
}

/** Pick another cover for a book: other editions from Open Library, a photo from the gallery, or the original. */
@Composable
private fun CoverPage(
    book: Book, onSearch: suspend (Book) -> List<Int>,
    onPickId: (Int) -> Unit, onGallery: () -> Unit, onReset: () -> Unit, onBack: () -> Unit,
) {
    var state by remember(book.uri) { mutableStateOf<CoverState>(CoverState.Loading) }
    LaunchedEffect(book.uri) {
        state = try {
            onSearch(book).let { if (it.isEmpty()) CoverState.Empty else CoverState.Found(it) }
        } catch (_: java.io.IOException) {
            CoverState.Offline
        } catch (_: org.json.JSONException) {
            CoverState.Empty
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 14.dp))
            Column(Modifier.weight(1f)) {
                Text("Choose cover", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(book.title, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("From photos", highlight = true, onClick = onGallery)
            Pill("Original cover", onClick = onReset)
        }
        when (val st = state) {
            CoverState.Loading -> CoverMessage("Searching Open Library…")
            CoverState.Empty -> CoverMessage("No other covers found for this book.\nUse “From photos” to pick your own.")
            CoverState.Offline -> CoverMessage("Can't reach Open Library.\nCheck your connection, or pick a photo instead.")
            is CoverState.Found -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                gridItems(st.ids) { id ->
                    RemoteImage(OpenLibrary.thumbUrl(id), Modifier.fillMaxWidth().aspectRatio(0.67f).clip(RoundedCornerShape(6.dp)).clickable { onPickId(id) })
                }
            }
        }
    }
}

@Composable
private fun CoverMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextLo, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun RemoteImage(url: String, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { runCatching { OpenLibrary.image(url, 360)?.asImageBitmap() }.getOrNull() }
    }
    Box(modifier.background(Color(0xFF242424))) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF242424), titleContentColor = TextHi, textContentColor = TextHi,
        title = { Text("Rename book", fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextHi, unfocusedTextColor = TextHi, cursorColor = TextHi,
                        focusedBorderColor = TextHi, unfocusedBorderColor = Color(0xFF555555),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Back to automatic title", color = TextLo, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { onSave("") }.padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text("Save", color = if (text.isNotBlank()) TextHi else TextLo) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextLo) } },
    )
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
