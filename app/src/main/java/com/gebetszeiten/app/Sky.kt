package com.gebetszeiten.app

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Blitz: zackiger Kanal mit Ästen, flackert 2–4 Mal. */
internal class Strike(val paths: List<List<Offset>>, val x: Float, val y: Float, val t0: Long, val pulses: List<Pair<Long, Float>>) {
    fun intensity(now: Long): Float {
        val dt = now - t0
        if (dt > 900) return 0f
        var v = 0f
        for ((at, amp) in pulses) { val x = dt - at; if (x >= 0) v = max(v, amp * exp(-x / 70f)) }
        return v
    }
}

private fun makeStrike(w: Float, h: Float, now: Long): Strike {
    val rnd = java.util.Random()
    fun jag(a: Offset, b: Offset, rough: Float, depth: Int, out: MutableList<Offset>) {
        if (depth == 0) { out.add(b); return }
        val mid = Offset((a.x + b.x) / 2 + (rnd.nextFloat() - 0.5f) * rough, (a.y + b.y) / 2 + (rnd.nextFloat() - 0.5f) * rough * 0.25f)
        jag(a, mid, rough * 0.55f, depth - 1, out); jag(mid, b, rough * 0.55f, depth - 1, out)
    }
    val s = Offset(w * (0.18f + rnd.nextFloat() * 0.64f), h * (0.12f + rnd.nextFloat() * 0.12f))
    val e = Offset(s.x + (rnd.nextFloat() - 0.5f) * w * 0.35f, h * 0.9f)
    val paths = ArrayList<List<Offset>>()
    val sheet = rnd.nextFloat() < 0.3f
    if (!sheet) {
        val main = mutableListOf(s); jag(s, e, w * 0.22f, 7, main); paths.add(main)
        repeat(3 + rnd.nextInt(3)) {
            val a = main[rnd.nextInt((main.size * 0.75f).toInt().coerceAtLeast(1))]
            val len = h * (0.08f + rnd.nextFloat() * 0.16f)
            val ang = (if (rnd.nextBoolean()) -1 else 1) * (0.35f + rnd.nextFloat() * 0.7f)
            val b = Offset(a.x + kotlin.math.sin(ang) * len, a.y + kotlin.math.cos(ang) * len)
            val br = mutableListOf(a); jag(a, b, w * 0.06f, 5, br); paths.add(br)
        }
    }
    val pulses = ArrayList<Pair<Long, Float>>(); var t = 0L
    repeat(2 + rnd.nextInt(3)) { k -> pulses.add(t to (if (k == 0) 1f else 0.45f + rnd.nextFloat() * 0.5f)); t += 55 + rnd.nextInt(120) }
    return Strike(paths, s.x / w, 1f - s.y / h, now, pulses)
}

internal val SKYLINE = "M0 120 V96 H18 V88 H30 V96 H48 V80 Q48 70 58 66 Q68 70 68 80 V96 H78 V58 L81 48 L84 58 V96 H96 V90 H120 V84 H134 V92 H150 V74 Q150 52 176 46 Q202 52 202 74 V92 H214 V40 L218 28 L222 40 V92 H236 V86 H254 V94 H272 V82 Q272 72 282 68 Q292 72 292 82 V94 H306 V88 H326 V96 H346 V86 H360 V96 H390 V120 Z"

/** Laufender Zustand des Himmels (Wetter, Animation, Blitz, Helligkeit hinter dem Text). */
class SkyInfo internal constructor(
    internal val st: SkyState, val wx: Wx?,
    internal val time: androidx.compose.runtime.MutableFloatState,
    internal val flash: androidx.compose.runtime.MutableFloatState,
    internal val strike: androidx.compose.runtime.MutableState<Strike?>,
    internal val size: androidx.compose.runtime.MutableState<androidx.compose.ui.geometry.Size>,
    val heroLum: Float, val listLum: Float,
    internal val tick: androidx.compose.runtime.MutableIntState,
    internal val cloudTime: androidx.compose.runtime.MutableFloatState
)

@Composable
fun rememberSky(now: Long): SkyInfo {
    val ctx = LocalContext.current
    var wx by remember { mutableStateOf(Weather.current(ctx)) }
    LaunchedEffect(now / 600_000L) {
        val fresh = Prefs.lastGpsAt > System.currentTimeMillis() - 6 * 3600_000L && Prefs.lastGpsLat != 0.0
        Weather.refreshIfNeeded(ctx, if (fresh) Prefs.lastGpsLat else Prefs.lat, if (fresh) Prefs.lastGpsLng else Prefs.lng) {
            wx = Weather.current(ctx)
        }
    }
    val st = remember(now / 30_000L, wx) { skyState(now, wx) }
    val reduceMotion = remember {
        try { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }
    }
    val time = remember { mutableFloatStateOf(0f) }
    val flash = remember { mutableFloatStateOf(0f) }
    val strike = remember { mutableStateOf<Strike?>(null) }
    val size = remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val tick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val cloudTime = remember { mutableFloatStateOf(0f) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, reduceMotion, st.thunder, SkyQuality.level == 0) {
        if (SkyQuality.level == 0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            var last = 0L
            var lastCloud = 0L
            var nextBolt = System.currentTimeMillis() + 2500
            var late = 0; var count = 0
            while (true) {
                withFrameMillis { ms ->
                    // Bildrate: voll 30, sparsam 20 Bilder pro Sekunde (Wolken ziehen langsam)
                    val target = if (SkyQuality.level >= 2) 33L else 50L
                    if (ms - last >= target) {
                        // Ruckelt es oft, Qualität für diese Sitzung automatisch senken
                        if (last > 0) { count++; if (ms - last > target * 2) late++ }
                        if (count >= 60) { if (late > 18 && SkyQuality.level > 1) SkyQuality.level--; late = 0; count = 0 }
                        last = ms
                        if (!reduceMotion) time.floatValue = ms / 1000f
                        val wall = System.currentTimeMillis()
                        val sz = size.value
                        if (st.thunder && wall > nextBolt && sz.width > 0f) {
                            strike.value = makeStrike(sz.width, sz.height * 0.55f, wall)
                            nextBolt = wall + 2600 + (Math.random() * 6500).toLong()
                        }
                        flash.floatValue = strike.value?.intensity(wall) ?: 0f
                        // Teurer Teil (Wolken) nur alle 150–250 ms neu rechnen, bei Blitz jedes Bild.
                        // Dazwischen wird das fertige Bild wiederverwendet – sieht gleich aus, kostet ein Bruchteil.
                        val cloudEvery = if (SkyQuality.level >= 2) 100L else 200L
                        if (ms - lastCloud >= cloudEvery || flash.floatValue > 0.02f) {
                            lastCloud = ms
                            if (!reduceMotion) cloudTime.floatValue = ms / 1000f
                            tick.intValue++
                        }
                    }
                }
            }
        }
    }
    // Helligkeit hinter dem großen Countdown und hinter der Liste (für Schrift und Glas)
    var hero = max(lum(st.mid), lum(st.hor) * 0.6f)
    if (!st.night) hero = hero * (1 - st.cloud * 0.5f) + 0.85f * st.cloud * 0.5f
    val dx = st.sunX - 0.5f; val dy = st.sunY - 0.62f
    hero += st.sunVis * exp(-sqrt(dx * dx + dy * dy) * 5f) * 0.6f + st.fog * 0.25f + st.snow * 0.2f
    var list = max(lum(st.mid), lum(st.hor)) + st.fog * 0.3f + st.snow * 0.2f
    if (!st.night) list += st.cloud * 0.15f
    return SkyInfo(st, wx, time, flash, strike, size, hero.coerceIn(0f, 1f), list.coerceIn(0f, 1f), tick, cloudTime)
}

/** Himmel über die ganze Fläche: Shader (ab Android 13), Blitzkanal, Silhouette mit Kuppeln und Minaretten. */
@Composable
fun SkyBackground(info: SkyInfo, modifier: Modifier = Modifier) {
    val st = info.st
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        if (Build.VERSION.SDK_INT >= 33 && SkyQuality.level > 0 && SkyShader.ok()) {
            ShaderSky(info, wPx, hPx, blur = false)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(st.top[0], st.top[1], st.top[2]), Color(st.mid[0], st.mid[1], st.mid[2]), Color(st.hor[0], st.hor[1], st.hor[2])))))
        }
        SkyParticles(info)
        Canvas(Modifier.fillMaxSize()) {
            info.size.value = this.size
            val s = info.strike.value ?: return@Canvas
            val v = info.flash.floatValue
            if (v < 0.03f || s.paths.isEmpty()) return@Canvas
            for ((k, pts) in s.paths.withIndex()) {
                val wgt = if (k == 0) 1f else 0.5f
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (q in pts.drop(1)) lineTo(q.x, q.y) }
                drawPath(path, Color(0xFFBECDFF).copy(alpha = 0.35f * v * wgt), style = Stroke(width = 14f * wgt, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color(0xFFDDE4FF).copy(alpha = 0.6f * v * wgt), style = Stroke(width = 6f * wgt, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color.White.copy(alpha = v * (0.6f + 0.4f * wgt)), style = Stroke(width = 2.4f * wgt + 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        val sky = remember { PathParser().parsePathString(SKYLINE).toPath() }
        Canvas(Modifier.fillMaxSize()) {
            val sc = this.size.width / 390f
            val skyCol = if (st.night) Color(0xFF0A0F22) else if (st.dusk) Color(0xFF2A2340) else Color(0xFF24324F)
            val tr = androidx.compose.ui.graphics.Matrix().apply { translate(0f, this@Canvas.size.height - 120f * sc); scale(sc, sc) }
            val p2 = Path().apply { addPath(sky); transform(tr) }
            drawPath(p2, skyCol)
            if (st.night || st.dusk) {
                val wc = Color(0xFFFFD27A).copy(alpha = 0.85f + 0.15f * sin(info.time.floatValue * 1.3f))
                listOf(168f to 83f, 176f to 83f, 184f to 83f, 55f to 87f, 61f to 87f, 279f to 88f, 285f to 88f, 218f to 34f).forEach { (x, y) ->
                    drawCircle(wc, 1.8f * sc, Offset(x * sc, this.size.height - (120f - y) * sc))
                }
            }
        }
    }
}

/** Datum, Ort, Wetter und nächstes Gebet direkt auf dem Himmel – Schrift passt sich der Helligkeit an. */
@Composable
fun SkyHero(
    info: SkyInfo, nextTitle: String, countdown: String, atText: String, progress: Float,
    dateText: String, place: String, onSettings: () -> Unit
) {
    var darkText by remember { mutableStateOf(false) }
    darkText = if (darkText) info.heroLum > 0.56f else info.heroLum > 0.66f
    val fg = if (darkText) Color(0xFF14213D) else Color.White
    val sh = min(0.6f, 0.25f + max(0f, info.heroLum - 0.35f) * 1.1f)
    val shadow = if (darkText) Shadow(Color.White.copy(alpha = 0.55f), Offset(0f, 1f), 12f)
    else Shadow(Color.Black.copy(alpha = sh), Offset(0f, 2f), 16f)
    val wx = info.wx
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(dateText, color = fg.copy(alpha = 0.9f), style = TextStyle(fontSize = 13.sp, shadow = shadow), maxLines = 1)
                val wtxt = listOfNotNull(place, wx?.temp?.let { "$it°" }, wx?.label()).joinToString(" · ")
                Text(wtxt, color = fg, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = shadow), maxLines = 1)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).tap(scale = 0.9f, onClick = onSettings),
                contentAlignment = Alignment.Center
            ) { GlyphIcon("M4 7h10M18 7h2M4 17h4M12 17h8M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", fg, 20.dp) }
        }
        Spacer(Modifier.height(84.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("next_up").uppercase(), color = fg.copy(alpha = 0.9f), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, shadow = shadow))
            Text(nextTitle, color = fg, style = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Bold, shadow = shadow), maxLines = 1)
            Text(countdown, color = fg, style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp, shadow = shadow), maxLines = 1)
            Text(atText, color = fg.copy(alpha = 0.92f), style = TextStyle(fontSize = 15.sp, shadow = shadow))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(fg))
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** Schriftfarbe und Schatten direkt auf dem Himmel (weiß mit Schatten-Rand, bei sehr hellem Himmel dunkel). */
@Composable
fun rememberSkyText(info: SkyInfo): Pair<Color, Shadow> {
    var darkText by remember { mutableStateOf(false) }
    darkText = if (darkText) info.heroLum > 0.56f else info.heroLum > 0.66f
    val fg = if (darkText) Color(0xFF14213D) else Color.White
    val sh = min(0.6f, 0.25f + max(0f, info.heroLum - 0.35f) * 1.1f)
    val shadow = if (darkText) Shadow(Color.White.copy(alpha = 0.55f), Offset(0f, 1f), 12f)
    else Shadow(Color.Black.copy(alpha = sh), Offset(0f, 2f), 16f)
    return fg to shadow
}

/** Milchglas-Palette: alle Karten der Startseite liegen getönt auf dem Himmel, Tönung je nach Helligkeit. */
fun glassPal(info: SkyInfo): Pal {
    val a = (Math.round((0.25f + info.listLum * 0.45f) * 20f) / 20f).coerceIn(0.32f, 0.62f)
    return Pal(
        bg = Color.Transparent,
        card = Color(0xFF0E1426).copy(alpha = a),
        text = Color.White,
        sub = Color.White.copy(alpha = 0.72f),
        line = Color.White.copy(alpha = 0.16f),
        accent = Color.White.copy(alpha = 0.22f),
        accentText = Color(0xFF7CF0C8),
        hl = Color.White.copy(alpha = 0.16f),
        fill = Color.White.copy(alpha = 0.14f),
        seg = Color.White.copy(alpha = 0.28f),
        dark = true
    )
}

@RequiresApi(33)
@Composable
private fun ShaderSky(info: SkyInfo, wPx: Float, hPx: Float, blur: Boolean) {
    val shader = remember { if (blur) SkyShader.blurred() else SkyShader.sharp() } ?: return
    val brush = remember(shader) { ShaderBrush(shader) }
    val q = SkyQuality.level
    val base = if (q >= 2) 2.2f else 3.0f
    val scale = if (blur) base * 1.8f else base
    val density = LocalDensity.current
    Canvas(
        Modifier
            .size(with(density) { (wPx / scale).toDp() }, with(density) { (hPx / scale).toDp() })
            .graphicsLayer(
                scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f),
                // Ergebnis bleibt als Bild erhalten und wird nur beim nächsten Takt neu gerechnet
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen,
                renderEffect = if (blur && Build.VERSION.SDK_INT >= 31)
                    androidx.compose.ui.graphics.BlurEffect(5f, 5f, androidx.compose.ui.graphics.TileMode.Clamp) else null
            )
    ) {
        @Suppress("UNUSED_VARIABLE") val tk = info.tick.intValue  // nur bei neuem Takt neu zeichnen
        val st = info.st
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", info.cloudTime.floatValue)
        shader.setFloatUniform("uTop", st.top[0], st.top[1], st.top[2])
        shader.setFloatUniform("uMid", st.mid[0], st.mid[1], st.mid[2])
        shader.setFloatUniform("uHor", st.hor[0], st.hor[1], st.hor[2])
        shader.setFloatUniform("uSun", st.sunX, st.sunY)
        shader.setFloatUniform("uSunVis", st.sunVis)
        shader.setFloatUniform("uSunCol", st.sunCol[0], st.sunCol[1], st.sunCol[2])
        shader.setFloatUniform("uSunLow", st.sunLow)
        shader.setFloatUniform("uMoon", st.moonX, st.moonY)
        shader.setFloatUniform("uMoonVis", st.moonVis)
        shader.setFloatUniform("uPhase", st.phase)
        shader.setFloatUniform("uStars", 0f)   // Sterne, Regen, Schnee zeichnet die schnelle Ebene
        shader.setFloatUniform("uCloud", st.cloud)
        shader.setFloatUniform("uRain", if (st.rain > 0.05f) 0.001f else 0f)
        shader.setFloatUniform("uSnow", 0f)
        shader.setFloatUniform("uFog", st.fog)
        shader.setFloatUniform("uFlash", info.flash.floatValue)
        shader.setFloatUniform("uHorizon", 0.12f)
        shader.setFloatUniform("uFlashPos", info.strike.value?.x ?: 0.6f, info.strike.value?.y ?: 0.6f)
        shader.setFloatUniform("uWind", st.wind)
        shader.setFloatUniform("uCloudLit", st.lit[0], st.lit[1], st.lit[2])
        shader.setFloatUniform("uCloudShade", st.shade[0], st.shade[1], st.shade[2])
        shader.setFloatUniform("uSteps", if (blur) 10f else if (q >= 2) 18f else 12f)
        shader.setFloatUniform("uDisc", 1f)
        shader.setFloatUniform("uLight", if (!blur && q >= 2) 1f else 0f)
        drawRect(brush)
    }
}

/** Schnelle, billige Ebene: Sterne, Regen, Schnee – jedes Bild, flüssig. */
@Composable
private fun SkyParticles(info: SkyInfo) {
    val st = info.st
    val rnd = remember { java.util.Random(7) }
    val stars = remember { List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.7f, 0.6f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 6.28f) } }
    val drops = remember { List(150) { floatArrayOf(rnd.nextFloat() * 1.3f, rnd.nextFloat(), 0.9f + rnd.nextFloat() * 0.8f, rnd.nextInt(3).toFloat()) } }
    val flakes = remember { List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 6.28f, rnd.nextInt(3).toFloat()) } }
    if (st.stars < 0.02f && st.rain < 0.02f && st.snow < 0.02f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = info.time.floatValue
        val w = this.size.width; val h = this.size.height
        if (st.stars > 0.02f) {
            for (s in stars) {
                val tw = 0.55f + 0.45f * sin(t * (1.1f + s[2]) + s[3])
                drawCircle(Color.White.copy(alpha = (st.stars * tw).coerceIn(0f, 1f)), s[2] * 1.2f, Offset(s[0] * w, s[1] * h))
            }
        }
        if (st.rain > 0.02f) {
            // Regenfäden in drei Tiefen: hinten fein und blass, vorne dicker; oben ausgeblendet (Bewegungsunschärfe)
            val n = (drops.size * min(1f, 0.35f + st.rain)).toInt()
            val slant = 0.06f + 0.16f * ((st.wind - 0.4f) / 2.2f).coerceIn(0f, 1f)
            for (i in 0 until n) {
                val d = drops[i]; val layer = d[3]
                val speed = h * (1.1f + layer * 0.55f) * d[2]
                val len = h * (0.03f + layer * 0.02f) * (0.7f + d[2] * 0.4f)
                val y = ((d[1] * h + t * speed) % (h + len)) - len
                val x = (d[0] * w) - y * slant
                val a0 = Offset(x, y); val a1 = Offset(x - len * slant, y + len)
                drawLine(
                    Brush.linearGradient(listOf(Color(0x00DCE6FF), Color(0xFFDCE6FF).copy(alpha = 0.22f + layer * 0.2f)), a0, a1),
                    a0, a1, strokeWidth = 0.8f + layer * 0.9f, cap = StrokeCap.Round
                )
            }
            // leichter Sprühschleier
            drawRect(Color(0xFF9AA6BF).copy(alpha = 0.06f * st.rain))
        }
        if (st.snow > 0.02f) {
            // Flocken in drei Tiefen: vorne groß und weich (unscharf), hinten klein und klar
            val n = (flakes.size * min(1f, 0.4f + st.snow)).toInt()
            for (i in 0 until n) {
                val f = flakes[i]; val layer = f[3]
                val y = (f[1] * h + t * h * (0.04f + layer * 0.045f)) % h
                val x = f[0] * w + sin(t * 0.6f + f[2]) * (8f + layer * 7f)
                val r = 1.5f + layer * layer * 2.2f
                if (layer >= 2f) {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), Offset(x, y), r * 1.8f), r * 1.8f, Offset(x, y))
                } else {
                    drawCircle(Color.White.copy(alpha = 0.6f + layer * 0.15f), r, Offset(x, y))
                }
            }
        }
    }
}

/** Echtes Milchglas: derselbe Himmel unscharf, nur innerhalb der Glasflächen sichtbar. */
class GlassRegistry {
    val rects = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    var origin by mutableStateOf(Offset.Zero)
}

fun Modifier.glass(reg: GlassRegistry, key: String, radius: Dp = 24.dp, tint: Color): Modifier = this
    .shadow(10.dp, RoundedCornerShape(radius), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
    .onGloballyPositioned { reg.rects[key] = it.boundsInRoot() }
    .clip(RoundedCornerShape(radius))
    .background(tint)
    // feiner Lichtrand oben wie bei Liquid Glass
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.12f to Color.White.copy(alpha = 0.0f)))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.10f))), RoundedCornerShape(radius))

@Composable
fun GlassBackdrop(info: SkyInfo, reg: GlassRegistry, radiusDp: Dp = 22.dp) {
    if (Build.VERSION.SDK_INT < 33 || SkyQuality.level == 0 || !SkyShader.ok()) return
    val r = with(LocalDensity.current) { radiusDp.toPx() }
    BoxWithConstraints(
        Modifier.fillMaxSize().drawWithContent {
            val path = Path()
            for (rc in reg.rects.values) {
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(rc.translate(-reg.origin.x, -reg.origin.y), androidx.compose.ui.geometry.CornerRadius(r)))
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        }
    ) {
        val density = LocalDensity.current
        ShaderSky(info, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, blur = true)
    }
}

/** Qualität des Himmels: 0 = aus (ruhiger Verlauf), 1 = sparsam, 2 = voll. Startwert aus den Einstellungen. */
object SkyQuality {
    var level by androidx.compose.runtime.mutableIntStateOf(2)
    fun init() { level = Prefs.skyQuality }
}
