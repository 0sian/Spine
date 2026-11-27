package app.spine.reader

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.spine.library.ComicArchive
import app.spine.library.Comics
import app.spine.library.Titles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/** Reads comics (CBZ and CBR): swipe or tap the edges to turn pages, pinch or double-tap to zoom, optional right-to-left for manga. */
class ComicActivity : ComponentActivity() {
    private val store: SharedPreferences by lazy { getSharedPreferences("spine_reader", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        val file = File(uri.path ?: return finish())
        val key = uri.toString()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        store.edit().putLong("read:$key", System.currentTimeMillis()).apply()

        setContent { ComicScreen(file, key) }
    }

    @Composable
    private fun ComicScreen(file: File, key: String) {
        val loaded by produceState<Pair<ComicArchive?, Boolean>>(null to false, file) {
            value = withContext(Dispatchers.IO) { Comics.open(file) } to true
        }
        val archive = loaded.first
        DisposableEffect(archive) { onDispose { archive?.close() } }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                !loaded.second -> Text("Opening…", color = Color(0xFF9A9A9A), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
                archive == null || archive.pageCount == 0 -> Text(
                    "Couldn't open this comic.\nIt may be damaged, or use a format Spine can't read.",
                    color = Color(0xFF9A9A9A), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(40.dp),
                )
                else -> Pages(archive, file, key)
            }
        }
    }

    @Composable
    private fun Pages(archive: ComicArchive, file: File, key: String) {
        val count = archive.pageCount
        val pager = rememberPagerState(initialPage = store.getInt("page:$key", 0).coerceIn(0, count - 1)) { count }
        val cache = remember(archive) { PageCache(archive) }
        val scope = rememberCoroutineScope()
        var rtl by remember { mutableStateOf(store.getBoolean("rtl:$key", false)) }
        var chrome by remember { mutableStateOf(false) }
        var zoomed by remember { mutableStateOf(false) }
        val title = remember { Titles.clean(file.nameWithoutExtension) }

        BackHandler(chrome) { chrome = false }
        LaunchedEffect(pager.currentPage) {
            val p = pager.currentPage
            val frac = if (count > 1) p.toDouble() / (count - 1) else 1.0
            // same shape the library already reads progress from
            store.edit().putInt("page:$key", p)
                .putString("book:$key", JSONObject().put("locations", JSONObject().put("totalProgression", frac)).toString())
                .putLong("read:$key", System.currentTimeMillis()).apply()
        }

        HorizontalPager(
            state = pager, reverseLayout = rtl, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
            key = { it }, modifier = Modifier.fillMaxSize(),
        ) { page ->
            PageView(cache, page, onZoom = { zoomed = it }, onTap = { zone ->
                if (zone == 0) chrome = !chrome
                else {
                    chrome = false
                    val next = (zone == 1) != rtl
                    scope.launch { pager.animateScrollToPage((pager.currentPage + if (next) 1 else -1).coerceIn(0, count - 1)) }
                }
            })
        }

        if (chrome) {
            val panel = Color(0xFF1E1E1E).copy(alpha = 0.96f)
            Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { chrome = false }) {
                Box(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp, start = 16.dp)
                        .size(44.dp).clip(CircleShape).background(panel).clickable { finish() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 3.dp)) }
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp, start = 72.dp, end = 24.dp)
                        .clip(RoundedCornerShape(50)).background(panel).padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).navigationBarsPadding()
                        .clip(RoundedCornerShape(24.dp)).background(panel)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    Text("Page ${((dragging ?: pager.currentPage.toFloat()).roundToInt()) + 1} of $count", color = Color.White, fontSize = 15.sp)
                    // the slider runs the way the pages run
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Slider(
                            value = dragging ?: pager.currentPage.toFloat(), onValueChange = { dragging = it },
                            onValueChangeFinished = { dragging?.let { scope.launch { pager.scrollToPage(it.roundToInt().coerceIn(0, count - 1)) } }; dragging = null },
                            valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF4A4A4A)),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(false to "Left to right", true to "Right to left (manga)").forEach { (value, label) ->
                            Text(
                                label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = if (rtl == value) Color(0xFF161616) else Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (rtl == value) Color(0xFFD7F25A) else Color(0xFF2E2E2E))
                                    .clickable { rtl = value; store.edit().putBoolean("rtl:$key", value).apply() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Decodes pages off the main thread, keeps the last few, and reads the archive one page at a time. */
private class PageCache(private val archive: ComicArchive) {
    private val recent = object : LinkedHashMap<Int, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?) = size > 6
    }
    private val mutex = Mutex()

    suspend fun get(index: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(recent) { recent[index] }?.let { return@withContext it }
        val bytes = mutex.withLock { archive.page(index) } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2400) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            ?: return@withContext null
        synchronized(recent) { recent[index] = bmp }
        bmp
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PageView(cache: PageCache, index: Int, onZoom: (Boolean) -> Unit, onTap: (Int) -> Unit) {
    val image by produceState<ImageBitmap?>(null, index) { value = cache.get(index) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val s = (scale * zoom).coerceIn(1f, 5f)
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
        scale = s
        onZoom(s > 1.01f)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { scale = if (scale > 1.01f) 1f else 2.5f; offset = Offset.Zero; onZoom(scale > 1.01f) },
                    onTap = { o -> onTap(when { o.x < size.width / 3f -> -1; o.x > size.width * 2f / 3f -> 1; else -> 0 }) },
                )
            }
            .transformable(transform, canPan = { scale > 1.01f }),
    ) {
        image?.let {
            Image(
                it, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
        } ?: Text("…", color = Color(0xFF6C6C6C), fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
    }
}
