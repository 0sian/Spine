package app.spine.reader

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import org.readium.r2.shared.publication.Link
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {

    private lateinit var root: FrameLayout
    private val containerId = View.generateViewId()
    private var navigator: EpubNavigatorFragment? = null

    private var themeIndex by mutableIntStateOf(0)
    private var fontScale by mutableDoubleStateOf(1.0)
    private var chromeVisible by mutableStateOf(false)
    private var title by mutableStateOf("")
    private var progress by mutableFloatStateOf(0f)
    private var chapter by mutableStateOf("")
    private var tocVisible by mutableStateOf(false)
    private var toc by mutableStateOf<List<Pair<Link, Int>>>(emptyList())
    private var positions: List<Locator> = emptyList()

    private val store by lazy { getSharedPreferences("spine_reader", MODE_PRIVATE) }
    private val bookKey get() = "book:" + intent.data.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // the navigator can't be restored without its Publication; always start fresh
        themeIndex = store.getInt("theme", 0).coerceIn(readerThemes.indices)
        fontScale = Math.round(store.getFloat("fontScale", 1f) * 10) / 10.0

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        root = FrameLayout(this).apply { setBackgroundColor(readerThemes[themeIndex].bg) }
        root.addView(FragmentContainerView(this).apply { id = containerId },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(ComposeView(this).apply { setContent { Chrome() } },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        lifecycleScope.launch { openBook() }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private suspend fun openBook() {
        val uri = intent.data ?: return finish()
        val http = DefaultHttpClient()
        val retriever = AssetRetriever(contentResolver, http)
        val asset = retriever.retrieve(uri.toAbsoluteUrl()!!).getOrElse { return fail("Couldn't read that file") }
        val parser = DefaultPublicationParser(applicationContext, http, retriever, pdfFactory = null)
        val publication = PublicationOpener(parser).open(asset, allowUserInteraction = false)
            .getOrElse { return fail("Couldn't open that book") }
        title = publication.metadata.title ?: ""
        showNavigator(publication)
    }

    private fun fail(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showNavigator(publication: Publication) {
        val saved = store.getString(bookKey, null)?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        val factory = EpubNavigatorFactory(publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = saved,
            initialPreferences = readerThemes[themeIndex].preferences(fontScale),
        )
        supportFragmentManager.commitNow {
            add(containerId, EpubNavigatorFragment::class.java, Bundle(), "navigator")
        }
        val nav = supportFragmentManager.findFragmentByTag("navigator") as EpubNavigatorFragment
        navigator = nav
        toc = flatten(publication.tableOfContents)
        lifecycleScope.launch { positions = publication.positions() }

        nav.addInputListener(DirectionalNavigationAdapter(nav))
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                chromeVisible = !chromeVisible
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                nav.currentLocator.collect {
                    progress = (it.locations.totalProgression ?: 0.0).toFloat()
                    chapter = it.title ?: ""
                    store.edit().putString(bookKey, it.toJSON().toString())
                        .putLong("read:" + intent.data.toString(), System.currentTimeMillis()).apply()
                }
            }
        }
    }

    private fun flatten(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> =
        links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

    private fun seekTo(fraction: Float) {
        if (positions.isEmpty()) return
        navigator?.go(positions[(fraction * (positions.size - 1)).roundToInt().coerceIn(0, positions.size - 1)])
    }

    private fun applyAppearance() {
        val t = readerThemes[themeIndex]
        root.setBackgroundColor(t.bg)
        navigator?.submitPreferences(t.preferences(fontScale))
        store.edit().putInt("theme", themeIndex).putFloat("fontScale", fontScale.toFloat()).apply()
    }

    @Composable
    private fun Chrome() {
        BackHandler(tocVisible) { tocVisible = false }
        BackHandler(chromeVisible && !tocVisible) { chromeVisible = false }
        if (!chromeVisible) return
        if (tocVisible) { Contents(); return }
        val panel = Color(0xFF1E1E1E).copy(alpha = 0.96f)
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { chromeVisible = false }) {
            Text(
                title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)
                    .clip(RoundedCornerShape(50)).background(panel).padding(horizontal = 18.dp, vertical = 10.dp),
            )
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp, start = 16.dp)
                    .size(44.dp).clip(CircleShape).background(panel).clickable { finish() },
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 3.dp)) }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).navigationBarsPadding()
                    .clip(RoundedCornerShape(24.dp)).background(panel)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        chapter.ifBlank { title }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (toc.isNotEmpty()) SizeButton("Contents") { tocVisible = true }
                }
                var dragging by remember { mutableStateOf<Float?>(null) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = dragging ?: progress, onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let(::seekTo); dragging = null },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF4A4A4A)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${((dragging ?: progress) * 100).roundToInt()}%", color = Color.White, fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    readerThemes.forEachIndexed { i, th ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { themeIndex = i; applyAppearance() }) {
                            Box(
                                Modifier.size(58.dp).clip(CircleShape).background(Color(th.bg))
                                    .border(if (i == themeIndex) 3.dp else 1.dp, if (i == themeIndex) Color.White else Color(0x55FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("Aa", color = Color(th.text), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                            Spacer(Modifier.height(6.dp))
                            Text(th.name, color = Color(0xFFBBBBBB), fontSize = 12.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SizeButton("A−") { fontScale = (Math.round((fontScale - 0.1) * 10) / 10.0).coerceAtLeast(0.7); applyAppearance() }
                    Text("${(fontScale * 100).roundToInt()}%", color = Color.White, fontSize = 15.sp)
                    SizeButton("A+") { fontScale = (Math.round((fontScale + 0.1) * 10) / 10.0).coerceAtMost(2.5); applyAppearance() }
                }
            }
        }
    }

    @Composable
    private fun Contents() {
        Column(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Contents", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { tocVisible = false }.padding(8.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(toc) { (link, depth) ->
                    Text(
                        link.title ?: "Untitled",
                        color = if (depth == 0) Color.White else Color(0xFFBBBBBB),
                        fontSize = if (depth == 0) 16.sp else 14.sp,
                        fontWeight = if (depth == 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { navigator?.go(link); tocVisible = false; chromeVisible = false }
                            .padding(start = (20 + depth * 18).dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun SizeButton(label: String, onClick: () -> Unit) {
        Text(
            label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2E2E2E)).clickable(onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 10.dp),
        )
    }
}
