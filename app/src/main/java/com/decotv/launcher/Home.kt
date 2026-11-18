package com.decotv.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

sealed class Panel {
    data class Info(val title: String, val body: String) : Panel()
    data class Inputs(val inputs: List<TvInput>) : Panel()
}

/* ---------- home screen ---------- */

@Composable
fun DecoHome(apps: List<TvApp>) {
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
    var focusedApp by remember { mutableStateOf<TvApp?>(null) }

    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = panel == null) { /* already home: stay here */ }

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

            Column(Modifier.fillMaxSize()) {
                TopBar(
                    clock = android.text.format.DateFormat.getTimeFormat(context).format(now.time),
                    onCast = {
                        panel = Panel.Info(
                            "Cast to this TV",
                            "Chromecast is built in and always on. On your phone, open a Cast-enabled app, " +
                                "tap the Cast icon and choose “${Actions.deviceName(context)}”.",
                        )
                    },
                    onMirror = {
                        if (!Actions.openMirroring(context)) {
                            panel = Panel.Info(
                                "Mirror your screen",
                                "On your Galaxy, open Quick Settings, tap Smart View and choose " +
                                    "“${Actions.deviceName(context)}”. This TV mirrors through Google Cast.",
                            )
                        }
                    },
                    onInputs = {
                        val list = Actions.inputs(context)
                        panel = if (list.isEmpty()) Panel.Info(
                            "Inputs",
                            "This TV doesn’t list its HDMI inputs to other apps. Use the Input button on your remote.",
                        ) else Panel.Inputs(list)
                    },
                    onWifi = { Actions.openWifi(context) },
                    onSettings = { Actions.openSettings(context) },
                )

                Spacer(Modifier.weight(1f))

                BottomShelf(
                    apps = apps,
                    date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.time),
                    focusedLabel = focusedApp?.label ?: "",
                    onFocusApp = { focusedApp = it },
                    onOpen = { Apps.launch(context, it) },
                )
            }

            panel?.let { p ->
                PanelOverlay(p, onClose = { panel = null }, onPickInput = {
                    panel = null
                    if (!Actions.switchToInput(context, it)) {
                        panel = Panel.Info("Inputs", "Couldn’t switch to ${it.label}. Use the Input button on your remote.")
                    }
                })
            }
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

@Composable
private fun BottomShelf(
    apps: List<TvApp>,
    date: String,
    focusedLabel: String,
    onFocusApp: (TvApp) -> Unit,
    onOpen: (TvApp) -> Unit,
) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !focusedOnce) {
            delay(150)
            runCatching { first.requestFocus() }
            focusedOnce = true
        }
    }

    Column(Modifier.padding(bottom = s.dp(26f))) {
        Row(
            Modifier.padding(horizontal = s.dp(56f)),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("TODAY", style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(13f), letterSpacing = s.sp(6f), color = Gold))
            Spacer(Modifier.width(s.dp(18f)))
            Text(date, style = TextStyle(fontFamily = theme.font, fontSize = s.sp(30f), letterSpacing = s.sp(3f), color = theme.cream))
            Spacer(Modifier.width(s.dp(18f)))
            Text(
                focusedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(15f), letterSpacing = s.sp(2f), color = theme.muted),
            )
        }
        Spacer(Modifier.height(s.dp(8f)))
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(s.dp(150f)),
            contentPadding = PaddingValues(horizontal = s.dp(56f)),
            horizontalArrangement = Arrangement.spacedBy(s.dp(22f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                AppTile(
                    app = app,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onFocused = { onFocusApp(app) },
                    onClick = { onOpen(app) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }

    val w by animateDpAsState(if (focused) s.dp(208f) else s.dp(168f), tween(180), label = "w")
    val h by animateDpAsState(if (focused) s.dp(117f) else s.dp(95f), tween(180), label = "h")
    val outer = RoundedCornerShape(s.dp(if (focused) 14f else 12f))
    val inner = RoundedCornerShape(s.dp(if (focused) 10f else 8f))

    Box(
        modifier
            .width(w)
            .height(h)
            .shadow(if (focused) s.dp(22f) else s.dp(10f), outer, ambientColor = Color.Black, spotColor = if (focused) Gold else Color.Black)
            .glass(outer, if (focused) Gold.copy(alpha = 0.22f) else theme.glassTint, if (focused) 0.30f else 0.13f)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) Gold else Color.White.copy(alpha = 0.16f), outer)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(s.dp(5f)),
        contentAlignment = Alignment.Center,
    ) {
        if (app.hasRealBanner) {
            Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(inner),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = app.banner, contentDescription = null, modifier = Modifier.size(s.dp(if (focused) 46f else 38f)))
                Spacer(Modifier.height(s.dp(6f)))
                Text(
                    app.label.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(12f), letterSpacing = s.sp(2f), color = theme.cream),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanelOverlay(panel: Panel, onClose: () -> Unit, onPickInput: (TvInput) -> Unit) {
    val s = LocalScale.current
    val theme = LocalTheme.current
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(panel) {
        delay(50)
        runCatching { firstButton.requestFocus() }
    }
    val card = RoundedCornerShape(s.dp(24f))
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(s.dp(560f))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .shadow(s.dp(30f), card)
                .glass(card, Color.Black.copy(alpha = 0.35f), 0.14f)
                .border(1.dp, Gold.copy(alpha = 0.6f), card)
                .padding(s.dp(34f)),
            verticalArrangement = Arrangement.spacedBy(s.dp(14f)),
        ) {
            val title = when (panel) {
                is Panel.Info -> panel.title
                is Panel.Inputs -> "Inputs"
            }
            Text(title, style = TextStyle(fontFamily = theme.font, fontSize = s.sp(30f), letterSpacing = s.sp(2f), color = Gold))
            when (panel) {
                is Panel.Info -> {
                    Text(panel.body, style = TextStyle(fontFamily = BodyFont, fontSize = s.sp(18f), lineHeight = s.sp(26f), color = theme.cream))
                    PanelButton("OK", onClose, firstButton)
                }
                is Panel.Inputs -> {
                    panel.inputs.forEachIndexed { i, input ->
                        PanelButton(input.label, { onPickInput(input) }, if (i == 0) firstButton else null)
                    }
                    PanelButton("Close", onClose, null)
                }
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
        modifier = Modifier.fillMaxWidth().height(s.dp(48f)),
        focusRequester = focusRequester,
    ) { focused ->
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = s.sp(15f),
                letterSpacing = s.sp(4f), color = if (focused) Color.White else LocalTheme.current.cream,
            ),
        )
    }
}
