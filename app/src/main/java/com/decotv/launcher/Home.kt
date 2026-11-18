package com.decotv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.util.Calendar

/* ---------- scaling: the design was drawn on a 1280 x 720 screen ---------- */

class Scale(private val factor: Float, private val fontScale: Float) {
    fun dp(v: Float): Dp = (v * factor).dp
    fun sp(v: Float): TextUnit = (v * factor / fontScale).sp
}

val LocalScale = staticCompositionLocalOf { Scale(1f, 1f) }
val LocalTheme = staticCompositionLocalOf { TimeTheme.EVENING }

/* ---------- liquid glass: a pre-blurred copy of the art, cut to each element's spot ---------- */

class GlassSource(val blur: ImageBitmap, val rootSize: IntSize)

val LocalGlass = staticCompositionLocalOf<GlassSource?> { null }

fun Modifier.glass(shape: Shape, tint: Color, highlight: Float = 0.13f): Modifier = composed {
    val source = LocalGlass.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (source != null && source.rootSize.width > 0 && source.rootSize.height > 0) {
                val bw = source.blur.width
                val bh = source.blur.height
                val sx = bw / source.rootSize.width.toFloat()
                val sy = bh / source.rootSize.height.toFloat()
                val srcX = (pos.x * sx).toInt().coerceIn(0, bw - 1)
                val srcY = (pos.y * sy).toInt().coerceIn(0, bh - 1)
                val srcW = (size.width * sx).toInt().coerceIn(1, bw - srcX)
                val srcH = (size.height * sy).toInt().coerceIn(1, bh - srcY)
                drawImage(
                    image = source.blur,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
            drawRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = highlight), tint, Color.White.copy(alpha = 0.03f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
            drawLine(
                Color.White.copy(alpha = 0.32f),
                Offset(size.height / 3f, 1f),
                Offset(size.width - size.height / 3f, 1f),
                strokeWidth = 1.5f,
            )
        }
}

/* ---------- dialogs ---------- */


/* ---------- pop-up panels ---------- */

data class PanelButtonSpec(val label: String, val action: () -> Unit)

data class Panel(val title: String, val body: String?, val buttons: List<PanelButtonSpec>)

/* ---------- home screen ---------- */

@Composable
fun DecoHome(apps: List<TvApp>, favorites: List<String>, onFavoritesChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scale = remember(config.screenWidthDp, density.fontScale) {
        Scale(config.screenWidthDp / 1280f, density.fontScale)
    }

    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Calendar.getInstance()
        }
    }
    val theme = TimeTheme.forHour(now.get(Calendar.HOUR_OF_DAY))

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    val dockFocus = remember { FocusRequester() }
    var dockFocusedOnce by remember { mutableStateOf(false) }

    val byPackage = apps.associateBy { it.packageName }
    val favApps = favorites.mapNotNull { byPackage[it] }

    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = panel == null && drawerOpen) { drawerOpen = false }
    BackHandler(enabled = panel == null && !drawerOpen) { /* already home: stay here */ }

    // First focus lands on the dock once apps have loaded; it returns there when the drawer closes.
    LaunchedEffect(favApps.isNotEmpty(), drawerOpen, panel == null) {
        if (!drawerOpen && panel == null && (favApps.isNotEmpty() || apps.isNotEmpty())) {
            delay(if (dockFocusedOnce) 60 else 200)
            runCatching { dockFocus.requestFocus() }
            dockFocusedOnce = true
        }
    }

    fun info(title: String, body: String) {
        panel = Panel(title, body, listOf(PanelButtonSpec("OK") { panel = null }))
    }

    fun appOptions(app: TvApp) {
        val index = favorites.indexOf(app.packageName)
        val buttons = mutableListOf(PanelButtonSpec("Open") { panel = null; Apps.launch(context, app) })
        var body: String? = null
        if (index >= 0) {
            buttons += PanelButtonSpec("Remove from favorites") {
                panel = null
                onFavoritesChange(favorites - app.packageName)
            }
            if (index > 0) buttons += PanelButtonSpec("Move left") {
                panel = null
                onFavoritesChange(favorites.toMutableList().apply { add(index - 1, removeAt(index)) })
            }
            if (index < favorites.size - 1) buttons += PanelButtonSpec("Move right") {
                panel = null
                onFavoritesChange(favorites.toMutableList().apply { add(index + 1, removeAt(index)) })
            }
        } else if (favApps.size < Favorites.MAX) {
            buttons += PanelButtonSpec("Add to favorites") {
                panel = null
                drawerOpen = false
                onFavoritesChange(favorites.filter { it in byPackage } + app.packageName)
            }
        } else {
            body = "Your dock holds ${Favorites.MAX} apps. Remove one from the dock first, then add this one."
        }
        buttons += PanelButtonSpec("Close") { panel = null }
        panel = Panel(app.label, body, buttons)
    }

    val blur = ImageBitmap.imageResource(theme.blurred)
    val glass = remember(blur, rootSize) { GlassSource(blur, rootSize) }

    CompositionLocalProvider(
        LocalScale provides scale,
        LocalTheme provides theme,
        LocalGlass provides glass,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { rootSize = it }
        ) {
            Crossfade(targetState = theme, animationSpec = tween(1200), label = "art") { t ->
                Image(
                    painter = painterResource(t.background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopBar(
                    clock = android.text.format.DateFormat.getTimeFormat(context).format(now.time),
                    onCast = {
                        info(
                            "Cast to this TV",
                            "Chromecast is built in and always on. On your phone, open a Cast-enabled app, " +
                                "tap the Cast icon and choose “${Actions.deviceName(context)}”.",
                        )
                    },
                    onMirror = {
                        if (!Actions.openMirroring(context)) {
                            info(
                                "Mirror your screen",
                                "On your Galaxy, open Quick Settings, tap Smart View and choose " +
                                    "“${Actions.deviceName(context)}”. This TV mirrors through Google Cast.",
                            )
                        }
                    },
                    onInputs = {
                        val list = Actions.inputs(context)
                        if (list.isEmpty()) {
                            info("Inputs", "This TV doesn’t list its HDMI inputs to other apps. Use the Input button on your remote.")
                        } else {
                            panel = Panel("Inputs", null, list.map { input ->
                                PanelButtonSpec(input.label) {
                                    panel = null
                                    if (!Actions.switchToInput(context, input)) {
                                        info("Inputs", "Couldn’t switch to ${input.label}. Use the Input button on your remote.")
                                    }
                                }
                            } + PanelButtonSpec("Close") { panel = null })
                        }
                    },
                    onWifi = { Actions.openWifi(context) },
                    onSettings = { Actions.openSettings(context) },
                )

                Spacer(Modifier.weight(1f))

                Dock(
                    apps = favApps,
                    firstFocus = dockFocus,
                    enabled = !drawerOpen && panel == null,
                    onOpen = { Apps.launch(context, it) },
                    onOptions = { appOptions(it) },
                    onAdd = { drawerOpen = true },
                    onDown = { drawerOpen = true },
                )
                AllAppsHint()
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
            ) {
                AllAppsDrawer(
                    apps = apps,
                    favorites = favorites.toSet(),
                    enabled = panel == null,
                    onClose = { drawerOpen = false },
                    onOpen = { Apps.launch(context, it) },
                    onOptions = { appOptions(it) },
                )
            }

            panel?.let { PanelOverlay(it) }
        }
    }
}
@Composable
private fun GlassButton(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .glass(shape, if (focused) Gold.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.12f), if (focused) 0.30f else 0.12f)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) Gold else Color.White.copy(alpha = 0.16f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content(focused) }
}

@Composable
private fun TopBar(
    clock: String,
    onCast: () -> Unit,
    onMirror: () -> Unit,
    onInputs: () -> Unit,
    onWifi: () -> Unit,
    onSettings: () -> Unit,
) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val pill = RoundedCornerShape(s.dp(30f))
    Row(
        Modifier
            .padding(start = s.dp(32f), end = s.dp(32f), top = s.dp(22f))
            .fillMaxWidth()
            .height(s.dp(60f))
            .shadow(s.dp(14f), pill, ambientColor = Color.Black, spotColor = Color.Black)
            .glass(pill, Color.Black.copy(alpha = 0.30f), 0.12f)
            .border(1.dp, Color.White.copy(alpha = 0.16f), pill)
            .padding(start = s.dp(26f), end = s.dp(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            theme.greeting,
            style = TextStyle(fontFamily = theme.font, fontSize = s.sp(26f), letterSpacing = s.sp(3f), color = Gold),
        )
        Spacer(Modifier.weight(1f))
        Chip(R.drawable.ic_cast, "CAST", onCast)
        Chip(R.drawable.ic_mirror, "MIRROR", onMirror)
        Chip(R.drawable.ic_inputs, "INPUTS", onInputs)
        Box(
            Modifier
                .padding(horizontal = s.dp(8f))
                .width(1.dp)
                .height(s.dp(24f))
                .background(Color.White.copy(alpha = 0.22f))
        )
        RoundIcon(R.drawable.ic_wifi, onWifi)
        Spacer(Modifier.width(s.dp(6f)))
        RoundIcon(R.drawable.ic_settings, onSettings)
        Text(
            clock,
            modifier = Modifier.padding(start = s.dp(14f), end = s.dp(12f)),
            style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(16f), letterSpacing = s.sp(2f), color = theme.muted),
        )
    }
}

@Composable
private fun Chip(icon: Int, label: String, onClick: () -> Unit) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    GlassButton(
        onClick = onClick,
        shape = RoundedCornerShape(s.dp(21f)),
        modifier = Modifier.padding(horizontal = s.dp(2f)).height(s.dp(42f)).padding(horizontal = 0.dp),
    ) { focused ->
        Spacer(Modifier.width(s.dp(14f)))
        Image(
            painterResource(icon), null,
            modifier = Modifier.size(s.dp(19f)),
            colorFilter = ColorFilter.tint(if (focused) GoldLight else Gold),
        )
        Spacer(Modifier.width(s.dp(8f)))
        Text(
            label,
            style = TextStyle(
                fontFamily = BodyFont, fontSize = s.sp(13f), letterSpacing = s.sp(2f),
                color = if (focused) Color.White else theme.cream,
            ),
        )
        Spacer(Modifier.width(s.dp(14f)))
    }
}

@Composable
private fun RoundIcon(icon: Int, onClick: () -> Unit) {
    val s = LocalScale.current
    GlassButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(s.dp(42f)),
    ) { focused ->
        Image(
            painterResource(icon), null,
            modifier = Modifier.size(s.dp(19f)),
            colorFilter = ColorFilter.tint(if (focused) GoldLight else Gold),
        )
    }
}


/* ---------- dock: the five favorites in one glass box ---------- */

@Composable
private fun Dock(
    apps: List<TvApp>,
    firstFocus: FocusRequester,
    enabled: Boolean,
    onOpen: (TvApp) -> Unit,
    onOptions: (TvApp) -> Unit,
    onAdd: () -> Unit,
    onDown: () -> Unit,
) {
    val s = LocalScale.current
    val box = RoundedCornerShape(s.dp(28f))
    Row(
        Modifier
            .onPreviewKeyEvent { e ->
                if (enabled && e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                    onDown(); true
                } else false
            }
            .shadow(s.dp(24f), box, ambientColor = Color.Black, spotColor = Color.Black)
            .glass(box, Color.Black.copy(alpha = 0.16f), 0.16f)
            .border(1.dp, Color.White.copy(alpha = 0.18f), box)
            .padding(horizontal = s.dp(24f), vertical = s.dp(20f)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(20f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEachIndexed { i, app ->
            AppTile(
                app = app,
                modifier = Modifier.width(s.dp(200f)).height(s.dp(112.5f)),
                focusRequester = if (i == 0) firstFocus else null,
                canFocus = enabled,
                onClick = { onOpen(app) },
                onOptions = { onOptions(app) },
            )
        }
        repeat(Favorites.MAX - apps.size) { i ->
            AddTile(
                modifier = Modifier.width(s.dp(200f)).height(s.dp(112.5f)),
                focusRequester = if (apps.isEmpty() && i == 0) firstFocus else null,
                canFocus = enabled,
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun AllAppsHint() {
    val s = LocalScale.current
    val theme = LocalTheme.current
    Row(
        Modifier.padding(top = s.dp(10f), bottom = s.dp(18f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(s.dp(14f), s.dp(9f))) {
            val p = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.2f)
                lineTo(size.width * 0.5f, size.height * 0.8f)
                lineTo(size.width * 0.9f, size.height * 0.2f)
            }
            drawPath(p, Gold, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.width(s.dp(10f)))
        Text(
            "ALL APPS",
            style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(12f), letterSpacing = s.sp(5f), color = theme.muted),
        )
    }
}

/* ---------- one app: OK opens it, hold OK (or Menu) for options ---------- */

@Composable
private fun AppTile(
    app: TvApp,
    modifier: Modifier,
    focusRequester: FocusRequester?,
    canFocus: Boolean,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    onFocused: (() -> Unit)? = null,
    badge: Boolean = false,
) {
    TileFrame(modifier, focusRequester, canFocus, onClick, onOptions, onFocused) { focused ->
        val s = LocalScale.current
        val theme = LocalTheme.current
        if (app.hasRealBanner) {
            Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF16181D)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(bitmap = app.banner, contentDescription = null, modifier = Modifier.size(s.dp(40f)))
                Spacer(Modifier.height(s.dp(6f)))
                Text(
                    app.label.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = s.dp(8f)),
                    style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(12f), letterSpacing = s.sp(2f), color = theme.cream),
                )
            }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(s.dp(7f))
                    .size(s.dp(10f))
                    .background(Gold, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, focusRequester: FocusRequester?, canFocus: Boolean, onClick: () -> Unit) {
    TileFrame(modifier, focusRequester, canFocus, onClick, onClick, null, dashed = true) { focused ->
        val s = LocalScale.current
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("+", style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(34f), color = if (focused) GoldLight else Gold))
            Text(
                "ADD APP",
                style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(11f), letterSpacing = s.sp(3f), color = LocalTheme.current.muted),
            )
        }
    }
}

@Composable
private fun TileFrame(
    modifier: Modifier,
    focusRequester: FocusRequester?,
    canFocus: Boolean,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    onFocused: (() -> Unit)?,
    dashed: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(focused: Boolean) -> Unit,
) {
    val s = LocalScale.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }
    val grow by animateFloatAsState(if (focused) 1.10f else 1f, tween(160), label = "grow")
    var held by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(12f))

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .shadow(if (focused) s.dp(18f) else s.dp(4f), shape, ambientColor = Color.Black, spotColor = if (focused) Gold else Color.Black)
            .clip(shape)
            .background(if (dashed) Color.White.copy(alpha = if (focused) 0.12f else 0.05f) else Color(0xFF16181D))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Gold else Color.White.copy(alpha = if (dashed) 0.28f else 0.12f),
                shape,
            )
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .focusProperties { this.canFocus = canFocus }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    e.key == Key.Menu && e.type == KeyEventType.KeyDown -> { onOptions(); true }
                    !ok -> false
                    e.type == KeyEventType.KeyDown -> {
                        val repeats = e.nativeKeyEvent.repeatCount
                        if (repeats == 0) held = false
                        if (repeats >= 1 && !held) { held = true; onOptions() }
                        true
                    }
                    e.type == KeyEventType.KeyUp -> {
                        if (!held) onClick()
                        held = false
                        true
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content(focused) }
}

/* ---------- all apps: slides up when you press Down on the dock ---------- */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AllAppsDrawer(
    apps: List<TvApp>,
    favorites: Set<String>,
    enabled: Boolean,
    onClose: () -> Unit,
    onOpen: (TvApp) -> Unit,
    onOptions: (TvApp) -> Unit,
) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val cols = 5
    val first = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    val box = RoundedCornerShape(topStart = s.dp(28f), topEnd = s.dp(28f), bottomStart = s.dp(28f), bottomEnd = s.dp(28f))

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .padding(start = s.dp(32f), end = s.dp(32f), bottom = s.dp(22f))
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .onPreviewKeyEvent { e ->
                    if (enabled && e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && focusedIndex < cols) {
                        onClose(); true
                    } else false
                }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .shadow(s.dp(30f), box, ambientColor = Color.Black, spotColor = Color.Black)
                .glass(box, Color.Black.copy(alpha = 0.40f), 0.14f)
                .border(1.dp, Color.White.copy(alpha = 0.18f), box)
                .padding(start = s.dp(34f), end = s.dp(34f), top = s.dp(26f)),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("All Apps", style = TextStyle(fontFamily = theme.font, fontSize = s.sp(28f), letterSpacing = s.sp(3f), color = Gold))
                Spacer(Modifier.width(s.dp(20f)))
                Text(
                    "Hold OK on an app to add it to your dock",
                    style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(14f), letterSpacing = s.sp(1f), color = theme.muted),
                )
            }
            Spacer(Modifier.height(s.dp(12f)))
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = s.dp(16f), horizontal = s.dp(12f)),
                horizontalArrangement = Arrangement.spacedBy(s.dp(28f)),
                verticalArrangement = Arrangement.spacedBy(s.dp(28f)),
            ) {
                itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                    AppTile(
                        app = app,
                        modifier = Modifier.aspectRatio(16f / 9f),
                        focusRequester = if (i == 0) first else null,
                        canFocus = enabled,
                        onClick = { onOpen(app) },
                        onOptions = { onOptions(app) },
                        onFocused = { focusedIndex = i },
                        badge = app.packageName in favorites,
                    )
                }
            }
        }
    }
}

/* ---------- pop-up panel ---------- */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanelOverlay(panel: Panel) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(panel) {
        delay(60)
        runCatching { firstButton.requestFocus() }
    }
    val card = RoundedCornerShape(s.dp(24f))
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(s.dp(520f))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .shadow(s.dp(30f), card)
                .glass(card, Color.Black.copy(alpha = 0.40f), 0.14f)
                .border(1.dp, Gold.copy(alpha = 0.6f), card)
                .padding(s.dp(32f)),
            verticalArrangement = Arrangement.spacedBy(s.dp(12f)),
        ) {
            Text(
                panel.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = theme.font, fontSize = s.sp(28f), letterSpacing = s.sp(2f), color = Gold),
            )
            panel.body?.let {
                Text(it, style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(17f), lineHeight = s.sp(25f), color = theme.cream))
            }
            Spacer(Modifier.height(s.dp(4f)))
            panel.buttons.forEachIndexed { i, b ->
                PanelButton(b.label, b.action, if (i == 0) firstButton else null)
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val s = LocalScale.current
    GlassButton(
        onClick = onClick,
        shape = RoundedCornerShape(s.dp(24f)),
        modifier = Modifier.fillMaxWidth().height(s.dp(46f)),
        focusRequester = focusRequester,
    ) { focused ->
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = s.sp(14f),
                letterSpacing = s.sp(4f), color = if (focused) Color.White else LocalTheme.current.cream,
            ),
        )
    }
}
