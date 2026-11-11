package com.example.cleanhome

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------- Palette ----------
private val Ink = Color(0xFF0C1117)
private val Panel = Color(0xFF161D27)
private val PanelFocus = Color(0xFF212B3A)
private val TextHi = Color(0xFFEAF0F7)
private val TextLo = Color(0xFF8A97A8)
private val Accent = Color(0xFF6FD3C4)

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel)) {
                BackHandler { /* a home screen never exits */ }
                HomeScreen(refresh)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }
}

// ---------- Data ----------
data class AppItem(
    val pkg: String,
    val label: String,
    val intent: Intent,
    val image: ImageBitmap,
    val isBanner: Boolean,
)

data class Featured(val pkg: String, val label: String, val color: Color)

val FEATURED = listOf(
    Featured("com.amazon.amazonvideo.livingroom", "Prime Video", Color(0xFF0B5C8E)),
    Featured("com.google.android.youtube.tv", "YouTube", Color(0xFF9E2A24)),
    Featured("com.sonyliv", "Sony LIV", Color(0xFF3A3A4A)),
    Featured("com.amazon.minitv.androidtv.app", "Amazon miniTV", Color(0xFF1B6B6B)),
    Featured("com.mxtech.videoplayer.television", "MX Player", Color(0xFF2D4F9E)),
    Featured("com.android.vending", "Play Store", Color(0xFF2E6B4F)),
)

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val phone = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return (pm.queryIntentActivities(tv, 0) + pm.queryIntentActivities(phone, 0))
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val banner = ri.activityInfo.loadBanner(pm)
            val drawable = banner ?: ri.loadIcon(pm)
            val bmp = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(192, 192)
            AppItem(
                pkg = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                intent = Intent(Intent.ACTION_MAIN)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                image = bmp.asImageBitmap(),
                isBanner = banner != null,
            )
        }
        .sortedBy { it.label.lowercase() }
}

private const val PREFS = "clean_home"
private const val KEY_PINS = "pins"

fun readPins(ctx: Context): List<String> =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_PINS, "")!!.split(",").filter { it.isNotBlank() }

fun writePins(ctx: Context, pins: List<String>) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_PINS, pins.joinToString(",")).apply()
}

// ---------- Screen ----------
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(refreshKey: Int) {
    val ctx = LocalContext.current
    val apps = remember(refreshKey) { loadApps(ctx) }
    var pins by remember { mutableStateOf(readPins(ctx)) }

    fun toggle(pkg: String) {
        pins = if (pkg in pins) pins - pkg else pins + pkg
        writePins(ctx, pins)
    }
    fun open(app: AppItem) = runCatching { ctx.startActivity(app.intent) }

    val featuredPkgs = FEATURED.map { it.pkg }.toSet()
    val pinned = pins.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
    val rest = apps.filter { it.pkg !in pins && it.pkg !in featuredPkgs }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { Header() }

        item { SectionTitle("Featured") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(FEATURED, key = { it.pkg }) { f ->
                    val installed = apps.firstOrNull { it.pkg == f.pkg }
                    if (installed != null) {
                        AppTile(installed, installed.pkg in pins, { open(installed) }, { toggle(installed.pkg) }, f.label)
                    } else {
                        MissingTile(f) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${f.pkg}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pinned.isNotEmpty()) {
            item { SectionTitle("Pinned") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    items(pinned, key = { it.pkg }) { app ->
                        AppTile(app, true, { open(app) }, { toggle(app.pkg) })
                    }
                }
            }
        }

        item { SectionTitle("All apps") }
        items(rest.chunked(5)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { app ->
                    AppTile(app, false, { open(app) }, { toggle(app.pkg) })
                }
            }
        }

        item {
            Text(
                "Hold OK on any app to pin or unpin it",
                color = TextLo, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Header() {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(10_000) }
    }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                now.format(DateTimeFormatter.ofPattern("h:mm")),
                color = TextHi, fontSize = 88.sp, fontWeight = FontWeight.Light,
            )
            Text(
                "$greeting  ·  " + now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                color = TextLo, fontSize = 20.sp,
            )
        }
        Surface(
            onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Panel, focusedContainerColor = Accent,
                contentColor = TextHi, focusedContentColor = Ink,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) {
            Text(
                "Settings", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppTile(
    app: AppItem, pinned: Boolean, onOpen: () -> Unit, onTogglePin: () -> Unit,
    labelOverride: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(200.dp)) {
        Surface(
            onClick = onOpen,
            onLongClick = onTogglePin,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Panel, focusedContainerColor = PanelFocus,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Accent), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (app.isBanner) {
                    Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Image(app.image, app.label, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                }
                if (pinned) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(10.dp)
                            .clip(CircleShape).background(Accent)
                    )
                }
            }
        }
        Text(
            labelOverride ?: app.label, color = TextLo, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MissingTile(f: Featured, onGet: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(200.dp)) {
        Surface(
            onClick = onGet,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = f.color, focusedContainerColor = f.color),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Accent), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(f.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Not installed. Press OK to get it", color = TextLo, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}
