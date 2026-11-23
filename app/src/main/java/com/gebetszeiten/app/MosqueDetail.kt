package com.gebetszeiten.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ------------------------------------------------------------------
// Symbole (24er Raster, als Linie gezeichnet)
// ------------------------------------------------------------------
private object Dg {
    const val LINK = "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"
    const val CLOCK = "M12 7v5l3 2M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"
    const val RELOAD = "M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5"
    const val GLOBE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"
    const val MOVE = "M12 3v18M3 12h18M12 3l-3 3M12 3l3 3M12 21l-3-3M12 21l3-3M3 12l3-3M3 12l3 3M21 12l-3-3M21 12l-3 3"
    const val HIDE = "M3 3l18 18M10.6 6.1A9.7 9.7 0 0 1 12 6c5 0 9 6 9 6a16 16 0 0 1-2.6 3.2M6.4 7.4C4.2 9 3 12 3 12s4 6 9 6a9 9 0 0 0 4.3-1.1"
    const val WOMEN = "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 12v9M9 18h6"
    const val DROP = "M12 3c3 4 5 6.5 5 9.5a5 5 0 0 1-10 0C7 9.5 9 7 12 3z"
    const val PARK = "M7 20V4h6a4 4 0 0 1 0 8H7"
    const val ACCESS = "M12 3a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM12 7v6h5l2 6M12 10H8M9 13a5 5 0 1 0 6 6"
    const val CHECK = "M5 12l5 5 9-10"
    const val HOUSE = "M3 11l9-7 9 7M5 10v10h14V10"
    const val SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5"
    const val NAME = "M4 20h4L19 9l-4-4L4 16zM13 7l4 4"
    const val DUP = "M8 8h11v11H8zM5 15V5h10"
    const val PIN = "M12 21s-6-6.2-6-11a6 6 0 0 1 12 0c0 4.8-6 11-6 11z"
    const val UP = "M6 15l6-6 6 6"
    const val NEXT = "M9 5l7 7-7 7"
}

private val BLUE = Color(0xFF0A84FF)
private val RED = Color(0xFFFF3B30)

/** Farbe der großen Route je Art: Moschee grün, Gebetsplatz lila, Wudu blau. */
internal fun routeColor(m: Mosque): Color = when (m.category) {
    Places.CAT_PRAYER -> Color(0xFF5E5CE6)
    Places.CAT_WUDU -> Color(0xFF0A84FF)
    else -> Color(0xFF13917C)
}

private fun hasTimesM(m: Mosque) = m.adhan.isNotEmpty() || m.iqamah.isNotEmpty()

private fun openRoute(ctx: Context, m: Mosque): Boolean = try {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(NameFmt.name(m.name))})"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}

private fun openWeb(ctx: Context, url: String) {
    if (!Mosques.isWebLink(url)) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

// ------------------------------------------------------------------
// Bausteine
// ------------------------------------------------------------------

/** Die große Route – das Wichtigste im Blatt. */
@Composable
internal fun BigRoute(m: Mosque, travelMin: Int?, full: Boolean, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val c = routeColor(m)
    Row(
        Modifier.fillMaxWidth().height(if (full) 50.dp else 56.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = c, spotColor = c)
            .clip(RoundedCornerShape(16.dp)).background(c)
            .tap(scale = 0.97f) { if (!openRoute(ctx, m)) onFail() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.ROUTE, Color.White, 20.dp)
        Spacer(Modifier.width(10.dp))
        Txt(t("route"), 18, FontWeight.Bold, Color.White, maxLines = 1)
        if (travelMin != null) {
            Txt("  ·  " + Fmt.duration(travelMin * 60_000L), 15, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}

/** Suchen · Kopieren · Teilen – drei gleich große Felder. */
@Composable
internal fun ThreeActions(m: Mosque, addr: String) {
    val ctx = LocalContext.current
    val text = listOf(NameFmt.name(m.name), addr).filter { it.isNotBlank() }.joinToString(", ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigAction(Glyph.SEARCH, t("search_web")) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(text)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
            }
        }
        BigAction(Glyph.COPY, t("sh_copy")) {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText(m.name, text))
            android.widget.Toast.makeText(ctx, t("copied"), android.widget.Toast.LENGTH_SHORT).show()
        }
        BigAction(Glyph.SHARE, t("share")) {
            val body = text + "\nhttps://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=18/%.5f/%.5f".format(
                java.util.Locale.US, m.lat, m.lng, m.lat, m.lng
            ) + (m.url?.let { "\n$it" } ?: "")
            try {
                ctx.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), m.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
private fun RowScope.BigAction(glyph: String, label: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(p.bg).tap(scale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlyphIcon(glyph, BLUE, 19.dp)
        Spacer(Modifier.height(3.dp))
        Txt(label, 12, FontWeight.SemiBold, p.text, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun RowScope.SmallAction(glyph: String, label: String, enabled: Boolean = true, color: Color = BLUE, onClick: () -> Unit) {
    val p = LocalPal.current
    val a = if (enabled) 1f else 0.4f
    Column(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.bg)
            .tap(enabled = enabled, scale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlyphIcon(glyph, color.copy(alpha = a), 16.dp)
        Spacer(Modifier.height(3.dp))
        Txt(label, 11, FontWeight.SemiBold, p.text.copy(alpha = a), maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun SecLabel(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, 12, FontWeight.SemiBold, p.sub, Modifier.weight(1f), maxLines = 1)
        if (action != null) Txt(action, 13, FontWeight.SemiBold, BLUE, Modifier.tap(onClick = onAction).padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

/** Angabe als Kachel: Symbol, Bezeichnung, Wert. Unbekanntes blass. */
@Composable
private fun RowScope.FactTile(glyph: String, tint: Color, label: String, value: String?, known: Boolean, highlight: Boolean = false) {
    val p = LocalPal.current
    val a = if (known) 1f else 0.5f
    Row(
        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (highlight) tint.copy(alpha = 0.18f) else p.bg).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.18f * a + 0.04f)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, tint.copy(alpha = a), 14.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Txt(label, 13, FontWeight.SemiBold, p.text.copy(alpha = a), maxLines = 1)
            if (value != null) Txt(value, 11, color = p.sub, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------
// Zeiten
// ------------------------------------------------------------------

private class TRow(val label: String, val key: String, val ev: Ev?)

private fun tableRows(m: Mosque, evs: List<Ev>, now: Long): List<TRow> {
    val today = Times.today(now)
    val friday = today.dayOfWeek == java.time.DayOfWeek.FRIDAY
    val hasJum = m.adhan.containsKey("JUMUAH") || m.iqamah.containsKey("JUMUAH")
    val rows = listOf(Kind.FAJR, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA).map { k ->
        val e = evs.firstOrNull { it.kind == k && it.day == today }
        if (friday && k == Kind.DHUHR) TRow(t("k_JUMUAH"), if (hasJum) "JUMUAH" else "DHUHR", e)
        else TRow(k.label, k.name, e)
    }
    return if (!friday && hasJum) rows + TRow(t("k_JUMUAH"), "JUMUAH", null) else rows
}

/** Zeiten der Moschee: oben (halb offen) das aktuelle Gebet groß, darunter die Tabelle. */
@Composable
internal fun TimesCard(m: Mosque, evs: List<Ev>, now: Long, focus: MosqueFocus?, showFocus: Boolean, showTable: Boolean = true) {
    val p = LocalPal.current
    val red = Color(0xFFFF6B5E)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg)) {
        if (showFocus && focus != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(focus.ev.title, 16, FontWeight.SemiBold, maxLines = 1)
                    Txt(
                        focus.statusText(now), 13, FontWeight.SemiBold,
                        when (focus.phase) { 2 -> red; 3 -> p.sub; else -> p.accentText }, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val bigIq = focus.phase >= 1 && focus.iqMs != null
                    Txt(
                        if (bigIq) Fmt.time(focus.iqMs ?: focus.adhanMs) else focus.adhanTxt, 26, FontWeight.SemiBold,
                        if (focus.phase == 2) p.sub else p.accentText, maxLines = 1
                    )
                    Txt(
                        if (bigIq) t("adhan") + " " + focus.adhanTxt else (focus.iqMs?.let { t("iqamah") + " " + Fmt.time(it) } ?: t("adhan")),
                        12, color = p.sub, maxLines = 1
                    )
                }
            }
            if (showTable) Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.line))
        }
        if (showTable) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp)) {
                Txt(t("prayer").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.weight(1f), maxLines = 1)
                Txt(t("adhan").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.width(66.dp), align = TextAlign.End, maxLines = 1)
                Txt(t("iqamah").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.width(66.dp), align = TextAlign.End, maxLines = 1)
            }
            val today = Times.today(now)
            tableRows(m, evs, now).forEach { r ->
                val isFocus = focus != null && r.ev != null && focus.ev.kind == r.ev.kind && focus.ev.day == today
                val adTxt = m.adhan[r.key] ?: r.ev?.let { Fmt.time(it.at) } ?: "–"
                val iqTxt = m.iqamah[r.key] ?: "–"
                val past = !isFocus && r.ev != null && r.ev.at < now - 60_000L
                val c = when {
                    isFocus -> p.accentText
                    past -> p.sub.copy(alpha = 0.75f)
                    else -> p.text
                }
                Row(
                    Modifier.fillMaxWidth().background(if (isFocus) p.accentText.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Txt(r.label, 15, if (isFocus) FontWeight.Bold else FontWeight.Medium, c, maxLines = 1)
                        if (isFocus && !showFocus && focus != null && focus.phase <= 1) {
                            val ms = if (focus.phase == 0) focus.adhanMs else focus.iqMs ?: focus.adhanMs
                            Spacer(Modifier.width(6.dp))
                            Txt(t("in_time", WidgetPainter.cd(ms - now)), 12, FontWeight.SemiBold, p.accentText, maxLines = 1)
                        }
                    }
                    Txt(adTxt, 15, color = c, modifier = Modifier.width(66.dp), align = TextAlign.End, maxLines = 1)
                    Txt(iqTxt, 15, FontWeight.Bold, c, Modifier.width(66.dp), align = TextAlign.End, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ------------------------------------------------------------------
// Moschee-Blatt
// ------------------------------------------------------------------

/**
 * Inhalt des Blatts für eine Moschee.
 * Halb offen: Route groß, Ankunft, Zeiten. Hochgezogen: Suchen/Kopieren/Teilen, Tabelle,
 * Fotos vom Weg, Angaben und alle Aktionen – alles auf einem Bildschirm.
 */
@Composable
internal fun MosqueDetailNew(
    m: Mosque, evs: List<Ev>, now: Long, myLat: Double, myLng: Double, listSec: Int?, full: Boolean,
    onExpand: () -> Unit, onUpdated: (Mosque) -> Unit, onHide: (Mosque) -> Unit, onMove: (Mosque) -> Unit,
    onHalfHeight: (Float) -> Unit = {}
) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var info by remember(m.id) { mutableStateOf<String?>(null) }
    var busy by remember(m.id) { mutableStateOf(false) }
    var finding by remember(m.id) { mutableStateOf(false) }
    var showLink by remember(m.id) { mutableStateOf(false) }
    var link by remember(m.id) { mutableStateOf("") }
    var preview by remember(m.id) { mutableStateOf<Mosques.Draft?>(null) }
    var editTimes by remember(m.id) { mutableStateOf(false) }
    var editFacts by remember(m.id) { mutableStateOf(false) }
    var rename by remember(m.id) { mutableStateOf(false) }
    var report by remember(m.id) { mutableStateOf(false) }
    var addr by remember(m.id) { mutableStateOf(m.address) }

    LaunchedEffect(m.id) {
        if (addr.isBlank()) {
            addr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Mosques.lookupAddress(ctx, m) } ?: ""
            if (addr.isNotBlank()) onUpdated(m.copy(address = addr))
        }
        // Mawaqit-Suche liefert oft keine Iqamah – dann die Moschee-Seite selbst lesen
        if (m.source == "Mawaqit" && m.iqamah.isEmpty() && m.url != null) {
            val d = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { Mosques.fetchDraft(m.url) } catch (e: Exception) { null }
            }
            if (d != null && d.iqamah.isNotEmpty()) onUpdated(Mosques.attachLink(ctx, m, d))
        }
    }

    // Fahr-/Gehzeit: dieselbe Position wie in der Liste
    val dist = Mosques.distance(myLat, myLng, m.lat, m.lng)
    var sec by remember(m.id, Prefs.travelMode) { mutableStateOf(listSec) }
    LaunchedEffect(m.id, Prefs.travelMode) {
        if (sec == null) {
            sec = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Routing.durations(myLat, myLng, listOf(m), Prefs.travelMode)[m.id]
            }
        }
    }
    val tr = Routing.minutes(sec, dist, Prefs.travelMode)
    val focus = remember(m, now / 60_000L) { if (hasTimesM(m)) mosqueFocus(m, evs, now) else null }

    fun findTimes() {
        if (finding) return
        finding = true
        info = null
        Thread {
            val d = try { Mosques.findTimes(m) } catch (e: Exception) { null }
            main.post {
                finding = false
                if (d != null && (d.adhan.isNotEmpty() || d.iqamah.isNotEmpty())) onUpdated(Mosques.attachLink(ctx, m, d))
                else info = t("not_found_times")
            }
        }.start()
    }

    fun reload() {
        val u = m.url
        if (u == null) {
            findTimes()
            return
        }
        if (busy) return
        busy = true
        info = null
        Thread {
            val d = try { Mosques.fetchDraft(u) } catch (e: Exception) { null }
            main.post {
                busy = false
                if (d != null && (d.adhan.isNotEmpty() || d.iqamah.isNotEmpty())) onUpdated(Mosques.attachLink(ctx, m, d))
                else info = t("page_failed") + Mosques.friendlyInfo()
            }
        }.start()
    }

    fun loadLink() {
        if (link.isBlank() || busy) return
        busy = true
        info = null
        Thread {
            val res = try { Mosques.fetchDraft(link) } catch (e: Exception) { null }
            main.post {
                busy = false
                when {
                    res == null -> info = t("page_failed") + Mosques.friendlyInfo()
                    res.adhan.isEmpty() && res.iqamah.isEmpty() -> info = t("no_times_found") + Mosques.friendlyInfo()
                    else -> preview = res
                }
            }
        }.start()
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            // volle Höhe des Inhalts (halbe Ansicht) melden – danach richtet sich die Höhe des halb offenen Blatts
            .onSizeChanged { if (!full) onHalfHeight(it.height.toFloat()) }
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (full) 9.dp else 10.dp)
    ) {
        BigRoute(m, tr, full) { info = t("no_nav") }
        if (full) {
            ThreeActions(m, addr)
        } else if (hasTimesM(m)) {
            ArrivalBar(now + tr * 60_000L, focus?.takeIf { it.phase <= 1 }?.iqMs)
        }

        if (hasTimesM(m)) {
            // eingeklappt: nur das aktuelle Gebet; hochgezogen: die ganze Tabelle
            TimesCard(m, evs, now, focus, showFocus = !full, showTable = full)
        } else if (!full) {
            Txt(t("mq_times_hint"), 13, color = p.sub, maxLines = 2)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Txt(t("mq_times_hint"), 14, color = p.sub)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(routeColor(m).copy(alpha = 0.16f))
                            .tap { findTimes() },
                        contentAlignment = Alignment.Center
                    ) { Txt(if (finding) t("finding") else t("find_times"), 14, FontWeight.SemiBold, routeColor(m), maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(p.card).tap { showLink = true },
                        contentAlignment = Alignment.Center
                    ) { Txt(t("a_link_add"), 14, FontWeight.SemiBold, BLUE, maxLines = 1) }
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(p.card).tap { editTimes = true },
                        contentAlignment = Alignment.Center
                    ) { Txt(t("a_times"), 14, FontWeight.SemiBold, BLUE, maxLines = 1) }
                }
            }
        }

        // Link eingeben (nach „Link hinzufügen/ändern“ oder „Link führt zur falschen Moschee“)
        if (showLink) LinkEditor(m, link, busy, onChange = { link = it }, onLoad = { loadLink() }, onRemove = {
            onUpdated(Mosques.removeLink(ctx, m))
            showLink = false
        }, onPaste = { txt ->
            link = txt
            loadLink()
        })
        preview?.let { d ->
            DraftPreview(d, onApply = {
                onUpdated(Mosques.attachLink(ctx, m, d))
                preview = null
                showLink = false
            }, onDiscard = { preview = null })
        }
        info?.let { Txt(it, 13, FontWeight.SemiBold, RED) }

        if (!full) {
            Row(
                Modifier.fillMaxWidth().tap(scale = 0.98f, onClick = onExpand).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Dg.UP, p.sub, 13.dp)
                Spacer(Modifier.width(6.dp))
                Txt(t("sh_more"), 12, color = p.sub, maxLines = 1)
            }
        } else {
            // Fotos vom Weg
            SecLabel(t("sh_photos"))
            PhotoStrip(m.id)
            // Angaben
            val facts = MosqueExtra.get(ctx, m.id)?.facts ?: emptyMap()
            SecLabel(t("sh_facts"), t("sh_edit")) { editFacts = true }
            FactsGrid(facts)
            // Aktionen
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Dg.LINK, if (m.url != null) t("a_link_change") else t("a_link_add")) { showLink = !showLink }
                SmallAction(Dg.CLOCK, t("a_times")) { editTimes = true }
                SmallAction(Dg.RELOAD, if (busy || finding) t("loading") else t("a_reload"), enabled = !busy && !finding) { reload() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val web = m.url ?: m.website
                SmallAction(Dg.GLOBE, t("a_web"), enabled = web != null) { web?.let { openWeb(ctx, it) } }
                SmallAction(Dg.MOVE, t("a_move")) { onMove(m) }
                SmallAction(Dg.HIDE, t("hide"), color = p.sub) { onHide(m) }
            }
            Box(Modifier.fillMaxWidth().tap { report = true }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Txt(t("report"), 14, FontWeight.SemiBold, Color(0xFFFF6B5E))
            }
        }
    }

    if (editTimes) TimesEditor(m, onClose = { editTimes = false }) {
        editTimes = false
        onUpdated(m)
    }
    if (editFacts) FactsEditor(m, onClose = { editFacts = false }) {
        editFacts = false
        onUpdated(m)
    }
    if (rename) RenameDialog(m, onClose = { rename = false }) {
        rename = false
        onUpdated(m)
    }
    if (report) ReportSheet(m, onClose = { report = false }) { choice ->
        report = false
        when (choice) {
            0 -> editTimes = true
            1 -> onMove(m)
            2 -> rename = true
            3 -> { showLink = true; onExpand() }
            4 -> editFacts = true
            else -> onHide(m)
        }
    }
}

@Composable
private fun LinkEditor(m: Mosque, link: String, busy: Boolean, onChange: (String) -> Unit, onLoad: () -> Unit, onRemove: () -> Unit, onPaste: (String) -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bg).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (link.isEmpty()) Txt(t("link_hint"), 14, color = p.sub, maxLines = 1)
                BasicTextField(
                    value = link, onValueChange = onChange, singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp), cursorBrush = SolidColor(BLUE),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(BLUE).tap { onLoad() }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Txt(if (busy) t("loading") else t("load"), 14, FontWeight.SemiBold, Color.White, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(p.bg).tap {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    val txt = cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                    if (txt.isNotBlank() && txt.length < 600) onPaste(txt)
                },
                contentAlignment = Alignment.Center
            ) { Txt(t("paste") + " & " + t("load"), 14, FontWeight.SemiBold, BLUE, maxLines = 1) }
            if (m.url != null && m.custom) {
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(RED.copy(alpha = 0.10f)).tap(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) { Txt(t("a_link_remove"), 14, FontWeight.SemiBold, RED, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun DraftPreview(d: Mosques.Draft, onApply: () -> Unit, onDiscard: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GreenCheck()
            Txt(t("preview_found", d.name, d.adhan.size, d.iqamah.size), 14, FontWeight.SemiBold)
        }
        Txt(
            MosqueExtra.KEYS.mapNotNull { k ->
                val a = d.adhan[k]
                val i = d.iqamah[k]
                if (a == null && i == null) null else t("k_$k") + " " + (a ?: "–") + (if (i != null) " / $i" else "")
            }.joinToString(" · "),
            12, color = p.sub
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(p.accent).tap(onClick = onApply), contentAlignment = Alignment.Center) {
                Txt(t("apply"), 15, FontWeight.SemiBold, Color.White)
            }
            Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(p.fill).tap(onClick = onDiscard), contentAlignment = Alignment.Center) {
                Txt(t("discard"), 15, FontWeight.SemiBold, p.text)
            }
        }
    }
}

/** Fotos vom Weg: niedrige Leiste, erstes Bild „Eingang“, am Ende „+ Foto“. */
@Composable
private fun PhotoStrip(mosqueId: String) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    var version by remember(mosqueId) { mutableStateOf(0) }
    var viewer by remember { mutableStateOf<String?>(null) }
    val photos = remember(mosqueId, version) { Places.mosquePhotos(ctx, mosqueId) }
    val camera = rememberCamera(dest = { Places.newMosquePhoto(ctx, mosqueId) }, onSaved = { version++ })
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.forEachIndexed { i, f ->
            Box(Modifier.clip(RoundedCornerShape(12.dp))) {
                androidx.compose.runtime.key(f.path, version) { PhotoThumb(f.path, if (i == 0) 128.dp else 92.dp, 64.dp) { viewer = f.path } }
                if (i == 0) Txt(t("entrance"), 11, FontWeight.SemiBold, Color.White, Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
        }
        if (photos.size < 10) {
            Box(
                Modifier.size(if (photos.isEmpty()) 128.dp else 92.dp, 64.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, p.line, RoundedCornerShape(12.dp)).tap { camera() },
                contentAlignment = Alignment.Center
            ) { Txt(if (photos.isEmpty()) t("entrance_add") else t("sh_photo_add"), 12, FontWeight.SemiBold, BLUE, maxLines = 1) }
        }
    }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}

private fun factValue(key: String, v: String?): String = when (v) {
    null, "", "unknown" -> t("v_unknown")
    "yes" -> t("v_yes")
    "no" -> t("v_no")
    "street" -> t("p_street")
    "lot" -> t("p_lot")
    "none" -> t("p_none")
    else -> v
}

@Composable
private fun FactsGrid(facts: Map<String, String>) {
    val items = listOf(
        Triple("women", Dg.WOMEN, Color(0xFFFF8CC6)) to t("f_women"),
        Triple("wudu", Dg.DROP, BLUE) to t("f_wudu"),
        Triple("parking", Dg.PARK, LocalPal.current.text) to t("f_parking"),
        Triple("access", Dg.ACCESS, LocalPal.current.text) to t("f_access")
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (tr, label) ->
                    val v = facts[tr.first]
                    FactTile(tr.second, tr.third, label, factValue(tr.first, v), known = v != null && v != "unknown" && v != "no")
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Blätter: Zeiten ändern, Angaben, Name, Problem melden
// ------------------------------------------------------------------

/** Ganzseitiges Blatt mit Kopf „Zurück/Abbrechen – Titel – Sichern“. */
@Composable
private fun Sheet(title: String, left: String, right: String?, rightEnabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPal.current
    Dialog(onDismissRequest = onLeft, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(p.card)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(p.line))
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Txt(left, 16, color = BLUE, modifier = Modifier.tap(onClick = onLeft).padding(vertical = 8.dp), maxLines = 1) }
                Txt(title, 17, FontWeight.SemiBold, align = TextAlign.Center, maxLines = 1)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (right != null) Txt(
                        right, 16, FontWeight.Bold, if (rightEnabled) BLUE else p.sub.copy(alpha = 0.5f),
                        Modifier.tap(enabled = rightEnabled, onClick = onRight).padding(vertical = 8.dp), maxLines = 1
                    )
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Umschalter über die ganze Breite. */
@Composable
private fun WideSeg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.fill).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (i == selected) p.seg else Color.Transparent)
                    .tap { onSelect(i) }.heightIn(min = 34.dp).padding(horizontal = 6.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) { Txt(o, 13, if (i == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, align = TextAlign.Center) }
        }
    }
}

private fun minsOf(v: String): Int? = try {
    val p = v.trim().split(":")
    val h = p[0].toInt()
    val m = p[1].take(2).toInt()
    if (h in 0..23 && m in 0..59) h * 60 + m else null
} catch (e: Exception) {
    null
}

private fun hm(x: Int): String {
    val v = ((x % 1440) + 1440) % 1440
    return "%02d:%02d".format(v / 60, v % 60)
}

/**
 * Zeiten von Hand ändern: Adhan und Iqamah je Gebet (und Jumuah).
 * Iqamah als feste Uhrzeit oder „Minuten nach Adhan“. Gespeichert werden nur geänderte Felder.
 */
@Composable
private fun TimesEditor(m: Mosque, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val extra = remember(m.id) { MosqueExtra.get(ctx, m.id) }
    // berechnete Zeiten heute als Ausgangswert für leere Felder
    val calc = remember {
        val now = System.currentTimeMillis()
        val today = Times.today(now)
        val mm = Times.events(today, 1).filter { it.day == today && it.kind.prayer }.associate { e ->
            val z = java.time.Instant.ofEpochMilli(e.at).atZone(Times.zone())
            e.kind.name to (z.hour * 60 + z.minute)
        }
        mm + ("JUMUAH" to (mm["DHUHR"] ?: 780))
    }
    val start = remember(m.id) {
        val a = HashMap<String, String>()
        val i = HashMap<String, String>()
        MosqueExtra.KEYS.forEach { k ->
            a[k] = m.adhan[k] ?: ""
            i[k] = extra?.iqamah?.get(k)?.takeIf { MosqueExtra.relMinutes(it) != null } ?: (m.iqamah[k] ?: "")
        }
        a to i
    }
    val ad = remember(m.id) { mutableStateMapOf<String, String>().apply { putAll(start.first) } }
    val iq = remember(m.id) { mutableStateMapOf<String, String>().apply { putAll(start.second) } }
    var sel by remember(m.id) { mutableStateOf<Pair<String, Boolean>?>("DHUHR" to true) }  // (Gebet, ist Iqamah)
    val changed = MosqueExtra.KEYS.any { ad[it] != start.first[it] || iq[it] != start.second[it] }

    fun shown(v: String): String = MosqueExtra.relMinutes(v)?.let { t("te_plus", it) } ?: v.ifBlank { "–" }

    Sheet(t("a_times"), t("cancel"), t("save"), changed, onLeft = onClose, onRight = {
        val na = HashMap(extra?.adhan ?: emptyMap())
        val ni = HashMap(extra?.iqamah ?: emptyMap())
        MosqueExtra.KEYS.forEach { k ->
            if (ad[k] != start.first[k]) na[k] = ad[k] ?: ""
            if (iq[k] != start.second[k]) ni[k] = iq[k] ?: ""
        }
        MosqueExtra.setTimes(ctx, m.id, na, ni)
        Widgets.updateAll(ctx)
        onSaved()
    }) {
        Txt(NameFmt.name(m.name) + " · " + t("te_hint"), 13, color = p.sub)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg).padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(t("prayer").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.weight(1f), maxLines = 1)
                Txt(t("adhan").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.width(84.dp), align = TextAlign.Center, maxLines = 1)
                Txt(t("iqamah").uppercase(), 11, FontWeight.SemiBold, p.sub, Modifier.width(84.dp), align = TextAlign.Center, maxLines = 1)
            }
            MosqueExtra.KEYS.forEach { k ->
                val rowSel = sel?.first == k
                Row(
                    Modifier.fillMaxWidth().background(if (rowSel) p.accentText.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Txt(t("k_$k"), 15, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    listOf(false, true).forEach { isIq ->
                        val on = sel == (k to isIq)
                        val v = if (isIq) iq[k] ?: "" else ad[k] ?: ""
                        Box(
                            Modifier.width(84.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(p.card)
                                .then(if (on) Modifier.border(2.dp, p.accentText, RoundedCornerShape(10.dp)) else Modifier)
                                .tap(scale = 0.95f) { sel = k to isIq },
                            contentAlignment = Alignment.Center
                        ) {
                            Txt(shown(v), 15, FontWeight.SemiBold, if (isIq) p.accentText else p.text, maxLines = 1)
                        }
                    }
                }
            }
        }
        sel?.let { (k, isIq) ->
            val cur = if (isIq) iq[k] ?: "" else ad[k] ?: ""
            val rel = if (isIq) MosqueExtra.relMinutes(cur) else null
            val adMin = minsOf(ad[k] ?: "") ?: calc[k] ?: 720
            fun set(v: String) { if (isIq) iq[k] = v else ad[k] = v }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(56.dp))
                    Txt((if (isIq) t("iqamah") else t("adhan")) + " " + t("k_$k"), 13, FontWeight.SemiBold, p.sub, Modifier.weight(1f), align = TextAlign.Center, maxLines = 1)
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                        Txt(t("te_clear"), 13, FontWeight.SemiBold, BLUE, Modifier.tap { set("") }.padding(4.dp), maxLines = 1)
                    }
                }
                // Rad: ziehen = ±1 Min, links/rechts antippen = ±5 Min
                if (rel != null) {
                    Wheel(rel, 0, 120, { t("te_plus", it) }) { set("+$it") }
                } else {
                    val base = minsOf(cur) ?: if (isIq) adMin + 10 else adMin
                    Wheel(base, 0, 1439, { hm(it) }, wrap = true) { set(hm(it)) }
                }
                if (isIq) {
                    WideSeg(listOf(t("te_fixed"), t("te_after")), if (rel != null) 1 else 0) { i ->
                        if (i == 1 && rel == null) {
                            val iqMin = minsOf(cur)
                            val n = if (iqMin != null) (iqMin - adMin).coerceIn(0, 120) else 10
                            set("+$n")
                        } else if (i == 0 && rel != null) {
                            set(hm(adMin + rel))
                        }
                    }
                }
            }
        }
        if (MosqueExtra.hasOwnTimes(ctx, m.id)) {
            Box(Modifier.fillMaxWidth().tap {
                MosqueExtra.clearTimes(ctx, m.id)
                Mosques.clearIqamahOverride(ctx, m.id)
                Widgets.updateAll(ctx)
                onSaved()
            }.padding(10.dp), contentAlignment = Alignment.Center) { Txt(t("te_clear_all"), 15, FontWeight.SemiBold, RED) }
        }
    }
}

/** Zahlen-Rad: Mitte groß, links/rechts ±5 (antippen), waagrecht ziehen = ±1. */
@Composable
private fun Wheel(value: Int, min: Int, max: Int, label: (Int) -> String, wrap: Boolean = false, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    val stepPx = with(LocalDensity.current) { 14.dp.toPx() }
    var acc by remember { mutableFloatStateOf(0f) }
    var cur by remember(value) { mutableStateOf(value) }
    fun norm(v: Int): Int = if (wrap) ((v - min) % (max - min + 1) + (max - min + 1)) % (max - min + 1) + min else v.coerceIn(min, max)
    Row(
        Modifier.fillMaxWidth().height(56.dp).draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { d ->
                acc += d
                while (acc <= -stepPx) { acc += stepPx; cur = norm(cur + 1); onChange(cur) }
                while (acc >= stepPx) { acc -= stepPx; cur = norm(cur - 1); onChange(cur) }
            },
            onDragStopped = { acc = 0f }
        ),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(label(norm(cur - 5)), 17, FontWeight.Medium, p.sub.copy(alpha = 0.6f), Modifier.tap { cur = norm(cur - 5); onChange(cur) }.padding(8.dp), maxLines = 1)
        Txt(label(cur), 30, FontWeight.SemiBold, maxLines = 1)
        Txt(label(norm(cur + 5)), 17, FontWeight.Medium, p.sub.copy(alpha = 0.6f), Modifier.tap { cur = norm(cur + 5); onChange(cur) }.padding(8.dp), maxLines = 1)
    }
}

/** Angaben bearbeiten: Frauenbereich, Wudu, Parken, Barrierefrei. */
@Composable
private fun FactsEditor(m: Mosque, onClose: () -> Unit, onSaved: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val start = remember(m.id) { MosqueExtra.get(ctx, m.id)?.facts ?: emptyMap() }
    val f = remember(m.id) { mutableStateMapOf<String, String>().apply { putAll(start) } }
    val yn = listOf("unknown", "yes", "no")
    val park = listOf("unknown", "street", "lot", "none")
    Sheet(t("fx_title"), t("cancel"), t("save"), f.toMap() != start, onLeft = onClose, onRight = {
        MosqueExtra.setFacts(ctx, m.id, f.toMap())
        onSaved()
    }) {
        Txt(NameFmt.name(m.name), 13, color = p.sub)
        listOf("women" to t("f_women"), "wudu" to t("f_wudu"), "access" to t("f_access")).forEach { (k, label) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(label, 15, FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                WideSeg(yn.map { factValue(k, it) }, yn.indexOf(f[k] ?: "unknown").coerceAtLeast(0)) { f[k] = yn[it] }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(t("f_parking"), 15, FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            WideSeg(park.map { factValue("parking", it) }, park.indexOf(f["parking"] ?: "unknown").coerceAtLeast(0)) { f["parking"] = park[it] }
        }
    }
}

/** Namen ändern (nur auf diesem Handy). */
@Composable
private fun RenameDialog(m: Mosque, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember(m.id) { mutableStateOf(m.name) }
    Sheet(t("rn_title"), t("cancel"), t("save"), name.isNotBlank() && name.trim() != m.name, onLeft = onClose, onRight = {
        MosqueExtra.setName(ctx, m.id, name)
        onSaved()
    }) {
        Field(t("rn_label"), name) { name = it }
    }
}

/** Problem melden: fertige Vorschläge, jeder führt direkt zur Lösung. */
@Composable
private fun ReportSheet(m: Mosque, onClose: () -> Unit, onPick: (Int) -> Unit) {
    val p = LocalPal.current
    val opts = listOf(
        Triple(Dg.CLOCK, BLUE, "rp_times"), Triple(Dg.PIN, Color(0xFF13917C), "rp_pos"), Triple(Dg.NAME, Color(0xFF5E5CE6), "rp_name"),
        Triple(Dg.LINK, BLUE, "rp_link"), Triple(Dg.CHECK, Color(0xFF34C759), "rp_facts"), Triple(Dg.DUP, Color(0xFFFF9F0A), "rp_dup"),
        Triple(Dg.HIDE, Color(0xFFFF6B5E), "rp_gone")
    )
    Sheet(t("rp_title"), t("back"), null, false, onLeft = onClose, onRight = {}) {
        Txt(NameFmt.name(m.name) + " · " + t("rp_hint"), 13, color = p.sub)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg)) {
            opts.forEachIndexed { i, (g, c, key) ->
                if (i > 0) Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(p.line))
                Row(
                    Modifier.fillMaxWidth().tap(scale = 0.99f) { onPick(i) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        GlyphIcon(g, c, 17.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(t(key), 15, FontWeight.SemiBold, maxLines = 2)
                        Txt(t(key + "_s"), 12, color = p.sub, maxLines = 1)
                    }
                    GlyphIcon(Dg.NEXT, p.sub.copy(alpha = 0.6f), 14.dp)
                }
            }
        }
        Txt(t("rp_note"), 12, color = p.sub, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// ------------------------------------------------------------------
// Gebets- und Wudu-Platz
// ------------------------------------------------------------------

/** „06:00 – 22:00“ → offen/geschlossen jetzt; sonst null (z. B. freier Text). */
private fun openNow(hours: String, now: Long): Boolean? {
    val r = Regex("(\\d{1,2})[:.](\\d{2})\\s*[-–]\\s*(\\d{1,2})[:.](\\d{2})").find(hours) ?: return null
    val (h1, m1, h2, m2) = r.destructured
    val a = h1.toInt() * 60 + m1.toInt()
    val b = h2.toInt() * 60 + m2.toInt()
    val z = java.time.Instant.ofEpochMilli(now).atZone(Times.zone())
    val x = z.hour * 60 + z.minute
    return if (a <= b) x in a until b else (x >= a || x < b)
}

@Composable
internal fun PlaceDetailNew(
    pl: Place, m: Mosque, now: Long, myLat: Double, myLng: Double, listSec: Int?, full: Boolean,
    onExpand: () -> Unit, onHalfHeight: (Float) -> Unit = {}, onDelete: () -> Unit
) {
    val p = LocalPal.current
    var info by remember(m.id) { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    val dist = Mosques.distance(myLat, myLng, m.lat, m.lng)
    val tr = Routing.minutes(listSec, dist, Prefs.travelMode)
    val c = routeColor(m)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .onSizeChanged { if (!full) onHalfHeight(it.height.toFloat()) }
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BigRoute(m, tr, full) { info = t("no_nav") }
        if (full) ThreeActions(m, m.address)
        // Öffnungszeit + überdacht / im Freien
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pl.hours.isNotBlank()) {
                val open = openNow(pl.hours, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (open != null) {
                        val oc = if (open) Color(0xFF34C759) else Color(0xFFFF6B5E)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(oc))
                        Spacer(Modifier.width(7.dp))
                        Txt(if (open) t("pl_open") else t("pl_closed"), 14, FontWeight.SemiBold, oc, maxLines = 1)
                        Txt("  ·  " + t("pl_today", pl.hours), 14, color = p.sub, maxLines = 1)
                    } else {
                        Txt(t("hours") + "  ·  " + pl.hours, 14, color = p.sub, maxLines = 2)
                    }
                }
            }
            if (pl.kind != "wudu") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val inside = "t_in" in pl.tags
                    val outside = "t_out" in pl.tags
                    FactTile(Dg.HOUSE, c, t("pl_covered"), if (inside) t("v_yes") else if (outside) t("v_no") else t("v_unknown"), inside, highlight = inside)
                    FactTile(Dg.SUN, c, t("pl_outside"), if (outside) t("v_yes") else if (inside) t("v_no") else t("v_unknown"), outside, highlight = outside)
                }
            }
        }
        // übrige Angaben
        val rest = pl.tags.filter { it != "t_in" && it != "t_out" }
        if (full && rest.isNotEmpty()) {
            SecLabel(t("sh_facts"))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            val (g, col) = when (k) {
                                "t_women" -> Dg.WOMEN to Color(0xFFFF8CC6)
                                "t_access" -> Dg.ACCESS to p.text
                                "t_sink", "t_foot" -> Dg.DROP to BLUE
                                else -> Dg.CHECK to Color(0xFF34C759)
                            }
                            FactTile(g, col, if (k == "t_women") t("pl_women") else t(k), null, true)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (full && pl.photos.isNotEmpty()) {
            SecLabel(t("sh_photos"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pl.photos.forEachIndexed { i, ph ->
                    Box(Modifier.clip(RoundedCornerShape(12.dp))) { PhotoThumb(ph, if (i == 0) 128.dp else 92.dp, 64.dp) { viewer = ph } }
                }
            }
        }
        info?.let { Txt(it, 13, FontWeight.SemiBold, RED) }
        if (!full) {
            Row(
                Modifier.fillMaxWidth().tap(scale = 0.98f, onClick = onExpand).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(Dg.UP, p.sub, 13.dp)
                Spacer(Modifier.width(6.dp))
                Txt(t("sh_more"), 12, color = p.sub, maxLines = 1)
            }
        } else {
            Box(Modifier.fillMaxWidth().tap(onClick = onDelete).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Txt(t("delete"), 14, FontWeight.SemiBold, Color(0xFFFF6B5E))
            }
        }
    }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}
