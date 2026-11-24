package com.gebetszeiten.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun hasLocPermission(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun TodayScreen(v: Int, onSettings: () -> Unit = {}, onOpenMap: () -> Unit = {}) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    // Automatischer Standort: beim Öffnen prüfen, ob man sich bewegt hat
    LaunchedEffect(Unit) {
        if (Prefs.autoLoc && hasLocPermission(ctx)) {
            Loc.fetch(ctx) { loc ->
                if (loc != null) {
                    val d = FloatArray(1)
                    Location.distanceBetween(Prefs.lat, Prefs.lng, loc.latitude, loc.longitude, d)
                    if (d[0] > 3000f) {
                        Prefs.lat = loc.latitude
                        Prefs.lng = loc.longitude
                        Prefs.place = t("my_location")
                        Scheduler.reschedule(ctx)
                        Loc.placeName(ctx, loc.latitude, loc.longitude) { n ->
                            if (n != null) {
                                Prefs.place = n
                                Widgets.updateAll(ctx)
                            }
                        }
                    }
                }
            }
        }
    }

    val minute = now / 60_000L
    // rev: steigt, wenn im Gebets-Blatt etwas geändert wird (z. B. Zeitkorrektur) → Zeiten neu rechnen
    var rev by remember { mutableIntStateOf(0) }
    var sheetKind by remember { mutableStateOf<Kind?>(null) }
    val evs = remember(minute, v, rev) { Times.around(now) }
    val today = Times.today(now)
    val ni = evs.indexOfFirst { it.at > now }.coerceAtLeast(1)
    val next = evs[ni]
    val prev = evs[ni - 1]
    val frac = ((now - prev.at).toFloat() / (next.at - prev.at).coerceAtLeast(1)).coerceIn(0f, 1f)
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val isToday = offset == 0
    val day = today.plusDays(offset.toLong())
    val dayMs = day.atTime(12, 0).atZone(Times.zone()).toInstant().toEpochMilli()
    val list = if (isToday) evs.filter { it.day == today } else remember(day, v, rev) { Times.events(day, 1) }
    // Gebete, die heute schon waren: wann sie morgen wiederkommen
    val tomorrowEvs = remember(today, v, rev) { Times.events(today.plusDays(1), 1) }
    fun nextOf(e: Ev): Long? = if (e.at > now) e.at else tomorrowEvs.firstOrNull { it.kind == e.kind }?.at?.takeIf { it > now }
    val title = when (offset) {
        0 -> t("today_title")
        1 -> t("tomorrow")
        -1 -> t("yesterday")
        else -> t("prayer_times")
    }

    // EIN Bildschirm ohne Scrollen: lebender Himmel, darauf echtes Milchglas (wie in der Probe)
    val sky = rememberSky(now)
    val reg = remember { GlassRegistry() }
    val tintA = (Math.round((0.18f + sky.listLum * 0.42f) * 20f) / 20f).coerceIn(0.2f, 0.5f)
    val tint = Color(0xFF0E1426).copy(alpha = tintA)
    val gPal = remember(Math.round(sky.listLum * 20f)) { glassPal(sky).copy(card = Color.Transparent) }
    val (fg, shadow) = rememberSkyText(sky)
    val wx = sky.wx
    val gpsFresh = Prefs.lastGpsAt > now - 6 * 3600_000L && Prefs.lastGpsLat != 0.0
    val awayShown = isToday && !Prefs.autoLoc && gpsFresh &&
        Mosques.distance(Prefs.lat, Prefs.lng, Prefs.lastGpsLat, Prefs.lastGpsLng) > 50_000f
    // Glasflächen, die gerade nicht sichtbar sind, aus dem Unschärfe-Bereich entfernen
    androidx.compose.runtime.SideEffect {
        val keep = buildSet { add("list"); add("datepill"); if (isToday && Prefs.showFav) add("fav"); if (awayShown) add("away") }
        reg.rects.keys.retainAll(keep)
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { reg.origin = it.positionInRoot() }) {
        SkyBackground(sky, Modifier.fillMaxSize())
        GlassBackdrop(sky, reg)
        androidx.compose.runtime.CompositionLocalProvider(LocalPal provides gPal) {
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp)
            ) {
                // Passt nicht alles auf den Bildschirm (viele Zeilen, Moschee-Karte, Hinweis), wird es Stufe für Stufe
                // kompakter – nie rutscht etwas unter die Tab-Leiste und nie muss man scrollen
                // Gemessen wird laufend: ragt die Liste unten heraus → eine Stufe kleiner; ist wieder genug Platz frei
                // (leere Flächen über und unter dem Countdown) → zurück zur normalen Größe. Kurz warten, damit
                // nachladende Teile (Wetter, Moschee-Karte) nicht zu einem falschen Verkleinern führen.
                val dens = androidx.compose.ui.platform.LocalDensity.current
                var fit by remember(maxHeight) { mutableIntStateOf(0) }
                var boxBottom by remember { mutableIntStateOf(0) }
                var listBottom by remember { mutableIntStateOf(0) }
                var gapA by remember { mutableIntStateOf(0) }
                var gapB by remember { mutableIntStateOf(0) }
                val rowsN = list.size
                LaunchedEffect(fit, boxBottom, listBottom, gapA + gapB, rowsN) {
                    if (boxBottom == 0 || listBottom == 0) return@LaunchedEffect
                    kotlinx.coroutines.delay(300)
                    if (listBottom > boxBottom + 1) {
                        if (fit < 2) fit += 1
                    } else if (fit > 0) {
                        // so viel Platz braucht die nächstgrößere Stufe zusätzlich (Zeilen + Countdown + Kopf)
                        val need = with(dens) { (if (fit == 2) rowsN * 5 + 20 else rowsN * 8 + 46).dp.toPx() + 24.dp.toPx() }
                        if (gapA + gapB > need) fit -= 1
                    }
                }
                val small = maxHeight < 700.dp || fit >= 1
                val tiny = fit >= 2
                Column(Modifier.fillMaxSize().onGloballyPositioned { c -> boxBottom = (c.positionInRoot().y + c.size.height).toInt() }) {
                    // Kopf: alles mittig – Ort, Wetter und Datum in einer Glas-Kapsel mit Pfeilen für die Tage
                    val ts = { size: Int, w: FontWeight -> androidx.compose.ui.text.TextStyle(fontSize = size.sp, fontWeight = w, shadow = shadow) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // links: zurück zu heute (nur wenn ein anderer Tag gezeigt wird), sonst gleich breiter Platzhalter
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            if (!isToday) SkyIconButton(Glyph.RESET, fg) { offset = 0 }
                        }
                        Row(
                            Modifier.weight(1f).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(Glyph.PIN, fg, 15.dp)
                            Spacer(Modifier.width(5.dp))
                            androidx.compose.material3.Text(
                                Prefs.place, color = fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = ts(17, FontWeight.SemiBold).copy(letterSpacing = (-0.1).sp)
                            )
                        }
                        SkyIconButton("M4 7h10M18 7h2M4 17h4M12 17h8M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", fg, onClick = onSettings)
                    }
                    if (wx != null) {
                        // Wetter groß und ruhig wie bei Apple Wetter: Symbol · Temperatur · Beschreibung
                        Row(
                            Modifier.fillMaxWidth().padding(top = if (small) 2.dp else 6.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(wxGlyph(wx, sky.st.night), fg, if (small) 26.dp else 30.dp)
                            wx.temp?.let {
                                Spacer(Modifier.width(10.dp))
                                androidx.compose.material3.Text(
                                    "$it°", color = fg, maxLines = 1,
                                    style = ts(if (small) 30 else 34, FontWeight.Light).copy(letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            androidx.compose.material3.Text(
                                wx.label(), color = fg, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = ts(17, FontWeight.SemiBold)
                            )
                        }
                    }
                    // Datum in einer breiten Kapsel – große, gut lesbare Schrift
                    Box(Modifier.fillMaxWidth().padding(top = if (small) 8.dp else 14.dp), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.fillMaxWidth().widthIn(max = 480.dp).glass(reg, "datepill", 30.dp, tint).padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillChevron(Glyph.CHEV_L) { offset-- }
                            Column(
                                Modifier.weight(1f).padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.Text(
                                    DateText.headerMain(dayMs), color = Color.White, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(fontSize = if (small) 16.sp else 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp)
                                )
                                DateText.headerLine2(dayMs)?.let {
                                    androidx.compose.material3.Text(
                                        it, color = Color.White.copy(alpha = 0.82f), maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = androidx.compose.ui.text.TextStyle(fontSize = if (small) 12.sp else 13.5.sp)
                                    )
                                }
                            }
                            PillChevron(Glyph.CHEV_R) { offset++ }
                        }
                    }
                    Spacer(Modifier.weight(1f).onSizeChanged { gapA = it.height })
                    // Mitte: nächstes Gebet groß (heute) bzw. Titel des gewählten Tages
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isToday) {
                            androidx.compose.material3.Text((if (next.kind.prayer) t("next_prayer_up") else t("next_up")).uppercase(), color = fg.copy(alpha = 0.9f), style = ts(13, FontWeight.SemiBold).copy(letterSpacing = 1.5.sp))
                            androidx.compose.material3.Text(next.title, color = fg, style = ts(if (tiny) 28 else if (small) 32 else 40, FontWeight.Bold), maxLines = 1)
                            androidx.compose.material3.Text(WidgetPainter.cd(next.at - now), color = fg, style = ts(if (tiny) 40 else if (small) 48 else 60, FontWeight.ExtraLight).copy(letterSpacing = (-2).sp), maxLines = 1)
                            androidx.compose.material3.Text(if (next.day != today) t("hero_tomorrow", Fmt.time(next.at)) else t("at_time", Fmt.time(next.at)), color = fg.copy(alpha = 0.92f), style = ts(15, FontWeight.Normal))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.width(170.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.25f))) {
                                Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(fg))
                            }
                        } else {
                            androidx.compose.material3.Text(title, color = fg, style = ts(if (small) 30 else 36, FontWeight.Bold), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f).onSizeChanged { gapB = it.height })
                    // Hinweis: weit weg vom eingestellten Ort
                    if (awayShown) {
                        Row(
                            Modifier.fillMaxWidth().glass(reg, "away", 16.dp, Color(0x55FF9500)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Txt(t("away_title", Prefs.place), 13, FontWeight.SemiBold, Color.White, Modifier.weight(1f), maxLines = 2)
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF9500)).tap {
                                    Prefs.autoLoc = true
                                    Loc.applyFix(ctx, Prefs.lastGpsLat, Prefs.lastGpsLng)
                                }.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Txt(t("apply"), 13, FontWeight.SemiBold, Color.White) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Meine Moschee (kompakt)
                    if (isToday && Prefs.showFav) {
                        Box(Modifier.fillMaxWidth().glass(reg, "fav", 18.dp, tint)) { MyMosqueCard(now, v, onOpenMap) }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Gebetszeiten auf Glas
                    Column(
                        Modifier.fillMaxWidth()
                            // volle Höhe messen, auch wenn die Spalte schon voll ist
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .onGloballyPositioned { c ->
                                // unterer Rand der Liste unter dem verfügbaren Platz? → eine Stufe kompakter
                                listBottom = (c.positionInRoot().y + c.size.height).toInt()
                            }
                            .glass(reg, "list", 24.dp, tint)
                    ) {
                        list.forEachIndexed { i, e ->
                            GlassPrayerRow(
                                e, first = i == 0, isNext = isToday && e == next, past = isToday && e.at <= now,
                                nextIn = if (isToday) nextOf(e)?.let { WidgetPainter.cd(it - now) } else null,
                                soon = isToday && e.at > now, small = small, tiny = tiny
                            ) { sheetKind = e.kind }
                        }
                    }
                }
            }
        }
    }

    // Gebet angetippt: Blatt mit Ton-Einstellungen gleitet über der ganzen App hoch
    val sheetEv = sheetKind?.let { k -> list.firstOrNull { it.kind == k } }
    val sheetNext = sheetEv?.let { if (isToday) nextOf(it) else it.at.takeIf { a -> a > now } }
    androidx.compose.runtime.SideEffect {
        SheetHost.content = if (sheetEv == null) null else {
            { PrayerSheet(sheetEv, now, sheetNext, onClose = { sheetKind = null }) { rev++ } }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SheetHost.content = null } }
}

/** Pfeil in der Datums-Kapsel. */
@Composable
private fun PillChevron(path: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)).tap(scale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(path, Color.White, 18.dp) }
}

/** Wettersymbol passend zum echten Wetter (nachts Mond statt Sonne). */
private fun wxGlyph(w: Wx, night: Boolean): String = when {
    w.thunder -> Glyph.WX_THUNDER
    w.snow && w.rain > 0.03f -> Glyph.WX_SNOW
    w.rain > 0.03f -> Glyph.WX_RAIN
    w.fog > 0.5f -> Glyph.WX_FOG
    w.cloud > 0.5f -> Glyph.WX_CLOUD
    night -> Glyph.WX_MOON
    else -> Glyph.WX_SUN
}

@Composable
private fun SkyIconButton(path: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).tap(scale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(path, color, 18.dp) }
}

@Composable
private fun PrayerRow(ctx: Context, e: Ev, first: Boolean, isNext: Boolean, past: Boolean, today: java.time.LocalDate, compact: Boolean = false, small: Boolean = false) {
    val p = LocalPal.current
    val weight = if (isNext) FontWeight.Bold else FontWeight.Medium
    val tag = when (e.kind) {
        Kind.SUNRISE -> t("end_fajr")
        Kind.MIDNIGHT -> t("end_isha")
        Kind.LAST_THIRD -> if (Times.today(e.at).isAfter(today)) t("tahajjud_tom") else t("tahajjud")
        else -> null
    }
    Column(Modifier.fillMaxWidth().background(if (isNext) p.hl else Color.Transparent)) {
        if (!first) Box(Modifier.padding(start = 36.dp).fillMaxWidth().height(0.5.dp).background(p.line))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (small) 34.dp else if (compact) 40.dp else 54.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isNext) p.accentText else if (past) p.line else p.sub)
            )
            Column(Modifier.weight(1f)) {
                Txt(e.title, if (compact) 16 else 17, weight, maxLines = 1)
                if (tag != null && !compact) Txt(tag, 12, color = p.sub)
            }
            if (e.kind != Kind.MIDNIGHT) ModeChip(ctx, e.kind)
            Txt(Fmt.time(e.at), if (compact) 18 else 17, if (compact) FontWeight.SemiBold else weight)
        }
    }
}

/**
 * Zeile wie in der Himmel-Probe: Punkt (beim nächsten Gebet leuchtend grün), Name,
 * beim nächsten Gebet „in 1:12“ in Grün, große Uhrzeit. Vergangene Gebete gedämpft.
 * Antippen wechselt Aus → Ton → Adhan (kurze Bestätigung unten).
 */
@Composable
private fun GlassPrayerRow(e: Ev, first: Boolean, isNext: Boolean, past: Boolean, nextIn: String?, soon: Boolean, small: Boolean, tiny: Boolean = false, onTap: () -> Unit) {
    val green = Color(0xFF7CF0C8)
    val alpha = if (past && !isNext) 0.55f else 1f
    Column(
        Modifier.fillMaxWidth()
            .background(if (isNext) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .tap(scale = 0.99f, onClick = onTap)
    ) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (tiny) 31.dp else if (small) 36.dp else 44.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                if (isNext) drawCircle(green.copy(alpha = 0.35f), size.minDimension * 0.45f)
                drawCircle(if (isNext) green else Color.White.copy(alpha = 0.35f * alpha), size.minDimension * 0.22f)
            }
            Spacer(Modifier.width(8.dp))
            // Name und Countdown teilen sich den Platz – die Uhrzeit rechts bleibt immer ganz sichtbar
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    e.title, color = Color.White.copy(alpha = alpha), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium)
                )
                if (nextIn != null) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        t("in_time", nextIn), color = if (soon) green else Color.White.copy(alpha = 0.55f), maxLines = 1,
                        softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.Text(
                Fmt.time(e.at), color = Color.White.copy(alpha = alpha), maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 19.sp, fontWeight = if (past && !isNext) FontWeight.Medium else FontWeight.SemiBold,
                    fontFeatureSettings = "tnum", letterSpacing = 0.2.sp
                )
            )
        }
    }
}

@Composable
private fun ModeChip(ctx: Context, k: Kind) {
    val p = LocalPal.current
    val mode = Prefs.mode(k)
    val labels = listOf(t("mode_off"), t("mode_tone"), t("mode_adhan"))
    val max = if (k.prayer) 2 else 1
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (mode > 0) p.hl else p.fill)
            .tap {
                Prefs.setMode(k, (mode + 1) % (max + 1))
                Scheduler.reschedule(ctx)
            }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Txt(labels[mode.coerceIn(0, 2)], 12, FontWeight.SemiBold, if (mode > 0) p.accentText else p.sub)
    }
}


@Composable
private fun DayArrow(left: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val rtl = Prefs.lang.rtl
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).tap(scale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val c = p.accentText
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val pointLeft = left != rtl
            val path = androidx.compose.ui.graphics.Path().apply {
                if (pointLeft) {
                    moveTo(w * 0.62f, w * 0.2f); lineTo(w * 0.32f, w * 0.5f); lineTo(w * 0.62f, w * 0.8f)
                } else {
                    moveTo(w * 0.38f, w * 0.2f); lineTo(w * 0.68f, w * 0.5f); lineTo(w * 0.38f, w * 0.8f)
                }
            }
            drawPath(
                path, c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.14f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun SlidersIcon(c: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val s = size.width
        val sw = s * 0.085f
        drawLine(c, androidx.compose.ui.geometry.Offset(s * 0.1f, s * 0.3f), androidx.compose.ui.geometry.Offset(s * 0.9f, s * 0.3f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c, androidx.compose.ui.geometry.Offset(s * 0.1f, s * 0.7f), androidx.compose.ui.geometry.Offset(s * 0.9f, s * 0.7f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(c, s * 0.13f, androidx.compose.ui.geometry.Offset(s * 0.66f, s * 0.3f))
        drawCircle(c, s * 0.13f, androidx.compose.ui.geometry.Offset(s * 0.34f, s * 0.7f))
    }
}


private data class FavInfo(val name: String, val focus: MosqueFocus?, val hasTimes: Boolean, val travel: Int)

@Composable
private fun MyMosqueCard(now: Long, v: Int, onOpenMap: () -> Unit) {
    val p = LocalPal.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val favId = Prefs.favId
    var info by remember(favId) { mutableStateOf<FavInfo?>(null) }
    // im Hintergrund laden (Dateien lesen, Route berechnen), jede Minute auffrischen
    androidx.compose.runtime.LaunchedEffect(favId, now / 60_000L, v) {
        if (favId.isBlank()) {
            info = null
            return@LaunchedEffect
        }
        info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val m = Mosques.listAll(ctx).firstOrNull { it.id == favId } ?: return@withContext null
            val fresh = Prefs.lastGpsAt > System.currentTimeMillis() - 3 * 3600_000L && Prefs.lastGpsLat != 0.0
            val la = if (fresh) Prefs.lastGpsLat else Prefs.lat
            val lo = if (fresh) Prefs.lastGpsLng else Prefs.lng
            val dist = Mosques.distance(la, lo, m.lat, m.lng)
            val sec = Routing.durations(la, lo, listOf(m), Prefs.travelMode)[m.id]
            val travel = Routing.minutes(sec, dist, Prefs.travelMode)
            val t0 = System.currentTimeMillis()
            FavInfo(NameFmt.name(m.name), mosqueFocus(m, Times.around(t0), t0), m.adhan.isNotEmpty() || m.iqamah.isNotEmpty(), travel)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val i = info
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindIcon("Moschee", 26.dp)
            Spacer(Modifier.width(8.dp))
            Txt(if (i != null) t("my_mosque") + " · " + i.name else t("my_mosque"), 15, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Txt(t("change"), 13, color = Color(0xFF007AFF), modifier = Modifier.tap(onClick = onOpenMap).padding(4.dp))
        }
        if (i == null) {
            Txt(t("my_mosque_hint"), 13, color = p.sub)
        } else if (!i.hasTimes || i.focus == null) {
            Txt(t("no_times"), 14, color = p.sub)
        } else {
            // Phasen: Adhan in … → Iqamah in … → Iqamah verpasst (bis 30 Min vor dem nächsten Adhan)
            val f = i.focus!!
            val red = Color(0xFFFF6B5E)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(f.ev.title, 15, FontWeight.SemiBold, maxLines = 1)
                    Txt(
                        f.statusText(now), 13, FontWeight.SemiBold,
                        when (f.phase) { 2 -> red; 3 -> p.sub; else -> p.accentText }, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    // groß die Zeit, um die es gerade geht; klein die andere
                    val bigIq = f.phase >= 1 && f.iqMs != null
                    Txt(if (bigIq) Fmt.time(f.iqMs ?: f.adhanMs) else f.adhanTxt, 24, FontWeight.SemiBold, if (f.phase == 2) p.sub else p.accentText)
                    Txt(
                        if (bigIq) t("adhan") + " " + f.adhanTxt else (f.iqMs?.let { t("iqamah") + " " + Fmt.time(it) } ?: t("adhan")),
                        12, color = p.sub, maxLines = 1
                    )
                }
            }
            val target = f.iqMs ?: f.adhanMs
            val leave = target - i.travel * 60_000L
            val missed = f.phase == 2
            val late = leave < now
            val c = if (missed) p.sub else if (late) badColor(p) else okColor(p)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(
                    if (missed) (f.nextTitle?.let { n -> n + " · " + t("mq_adhan_in", WidgetPainter.cd((f.nextAdhanMs ?: now) - now)) } ?: "")
                    else t("leave_by", Fmt.time(leave)),
                    14, FontWeight.SemiBold, c, Modifier.weight(1f), maxLines = 1
                )
                if (!missed) {
                    TravelIcon(Prefs.travelMode, c, p.card, 15.dp)
                    Spacer(Modifier.width(4.dp))
                    Txt(Fmt.duration(i.travel * 60_000L), 14, FontWeight.Bold, c)
                }
            }
        }
    }
}
