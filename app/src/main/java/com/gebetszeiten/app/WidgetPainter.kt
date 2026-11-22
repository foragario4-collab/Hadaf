package com.gebetszeiten.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Schrift der Vorschau (Inter), in den App-Daten enthalten; je Stärke einmal erzeugt. */
internal object WFont {
    private val cache = HashMap<Int, Typeface>()

    @Synchronized
    fun get(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        try {
            Typeface.Builder(ctx.assets, "fonts/InterVariable.ttf")
                .setFontVariationSettings("'wght' $weight")
                .build() ?: fallback(weight)
        } catch (e: Throwable) {
            fallback(weight)
        }
    }

    private fun fallback(weight: Int): Typeface = if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

/** Textschatten wie in CSS (Unschärfe in Pixeln der Vorschau, Versatz nach unten, Farbe). */
internal class Sh(val blur: Float, val dy: Float, val color: Int)

/** Werkzeug zum Zeichnen in „Vorschau-Pixeln“: u = echte Pixel pro Vorschau-Pixel. */
internal class Pen(private val ctx: Context, val c: Canvas, val u: Float) {

    fun paint(size: Float, weight: Int, color: Int, ls: Float = 0f, tnum: Boolean = false): TextPaint {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        p.typeface = WFont.get(ctx, weight)
        p.textSize = size * u
        p.color = color
        if (ls != 0f) p.letterSpacing = ls / size
        if (tnum) p.fontFeatureSettings = "'tnum'"
        return p
    }

    /** Höhe der Zeile: normal (Schrift-Metrik) oder line-height:1 (= Schriftgröße). */
    fun lineH(p: Paint, tight: Boolean = false): Float {
        val fm = p.fontMetrics
        return if (tight) p.textSize else fm.descent - fm.ascent
    }

    /** Abstand von der Zeilen-Oberkante bis zur Grundlinie. */
    fun ascentIn(p: Paint, tight: Boolean = false): Float {
        val fm = p.fontMetrics
        val content = fm.descent - fm.ascent
        return if (tight) (p.textSize - content) / 2f - fm.ascent else -fm.ascent
    }

    fun width(t: String, p: Paint): Float = p.measureText(t)

    /** Schrift so lange verkleinern, bis der Text in maxW passt (höchstens bis minSize). */
    fun fit(t: String, p: Paint, maxW: Float, minSize: Float) {
        while (p.measureText(t) > maxW && p.textSize > minSize * u) p.textSize = p.textSize - 0.5f * u
    }

    /** Text zeichnen; x ist links, Mitte oder rechts je nach align; top = Oberkante der Zeile. */
    fun text(t: String, x: Float, top: Float, p: Paint, align: Int = 0, tight: Boolean = false, shadows: List<Sh> = emptyList()) {
        if (t.isEmpty()) return
        val w = p.measureText(t)
        val left = when (align) { 1 -> x - w / 2f; 2 -> x - w; else -> x }
        val base = top + ascentIn(p, tight)
        for (s in shadows) {
            val sp = Paint(p)
            sp.color = s.color
            val sigma = s.blur / 2f * u
            val radius = max(0.5f, (sigma - 0.5f) / 0.57735f)
            sp.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            c.drawText(t, left, base + s.dy * u, sp)
        }
        c.drawText(t, left, base, p)
    }
}

internal object WidgetPainter {
    // Farben aus der Vorschau
    const val WHITE = 0xFFFFFFFF.toInt()
    const val GREEN = 0xFF7CF0C8.toInt()
    private const val GLASS = 0x4D0E1426          // rgba(14,20,38,.30)
    private val GLASS_RUN = 0x8C0E1426.toInt()  // rgba(14,20,38,.55) – laufendes Gebet
    private const val GLASS_IQ = 0x610E3C32       // rgba(14,60,50,.38)
    private const val BORDER = 0x42FFFFFF         // rgba(255,255,255,.26)
    private const val BORDER_RUN = 0xE6FFFFFF.toInt() // rgba(255,255,255,.9)
    private const val INSET = 0x2EFFFFFF          // rgba(255,255,255,.18)
    val SH_IN = listOf(Sh(14f, 0f, 0x47000000), Sh(2f, 1f, 0x8C000000.toInt()))
    private val SH_RING = listOf(Sh(3f, 1f, 0x66000000))

    /** Feste Bestandteile je Widget: abgedunkelter Himmel und Milchglas-Vorlage. */
    class Base(val sky: Bitmap, val frosted: Bitmap, val key: String)

    fun base(ctx: Context, w: Int, h: Int, u: Float, now: Long, key: String): Base {
        val sky = SkyImage.sky(ctx, w, h, now)
        val dim = SkyImage.neededDim(sky)
        val c = Canvas(sky)
        if (dim > 0f) c.drawColor(android.graphics.Color.argb((dim * 255).toInt(), 10, 14, 28))
        val frosted = SkyImage.frosted(sky, 14f * u)
        return Base(sky, frosted, key)
    }

    // ---------- Bausteine ----------

    private fun rr(r: RectF, rad: Float) = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }

    /** Milchglas-Fläche wie .glass in der Vorschau. */
    fun glass(c: Canvas, r: RectF, rad: Float, frosted: Bitmap, u: Float, tint: Int = GLASS, border: Int = BORDER, bw: Float = 1f) {
        val path = rr(r, rad)
        c.save()
        c.clipPath(path)
        c.drawBitmap(frosted, 0f, 0f, null)
        c.drawColor(tint)
        // innere Lichtkante oben (box-shadow: inset 0 1px 0)
        val inner = RectF(r.left + bw * u, r.top + bw * u, r.right - bw * u, r.bottom - bw * u)
        val ir = max(0f, rad - bw * u)
        val a = rr(inner, ir)
        val b = rr(RectF(inner.left, inner.top + u, inner.right, inner.bottom + u), ir)
        a.op(b, Path.Op.DIFFERENCE)
        val hp = Paint(Paint.ANTI_ALIAS_FLAG)
        hp.color = INSET
        c.drawPath(a, hp)
        c.restore()
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.style = Paint.Style.STROKE
        bp.strokeWidth = bw * u
        bp.color = border
        val half = bw * u / 2f
        c.drawRoundRect(RectF(r.left + half, r.top + half, r.right - half, r.bottom - half), rad - half, rad - half, bp)
    }

    /** Kachel wie .c in der Vorschau (Name, Uhrzeit, Countdown). */
    private fun tile(pen: Pen, r: RectF, frosted: Bitmap, name: String, time: String, rest: String, running: Boolean) {
        val u = pen.u
        glass(pen.c, r, 13f * u, frosted, u, if (running) GLASS_RUN else GLASS, if (running) BORDER_RUN else BORDER, if (running) 1.5f else 1f)
        val pn = pen.paint(11f, 600, WHITE)
        pen.fit(name, pn, r.width() - 2f * u, 8f)
        val pt = pen.paint(15f, 700, WHITE, tnum = true)
        pen.fit(time, pt, r.width() - 4f * u, 11f)
        val pr = pen.paint(10f, 600, if (running) WHITE else GREEN, tnum = true)
        pen.fit(rest, pr, r.width() - 4f * u, 8f)
        val hN = pen.lineH(pn); val hT = pen.lineH(pt); val hR = max(pen.lineH(pr), 12f * u)
        val total = hN + u + hT + u + hR
        var y = r.top + (r.height() - total) / 2f
        val cx = r.centerX()
        pen.text(name, cx, y, pn, 1); y += hN + u
        pen.text(time, cx, y, pt, 1); y += hT + u
        pen.text(rest, cx, y + (hR - pen.lineH(pr)) / 2f, pr, 1)
    }

    // ---------- Daten ----------

    /** Countdown in ganzen Minuten: „59 Min“, ab einer Stunde „1:12“. */
    fun cd(ms: Long): String {
        val m = max(1L, ceil(max(0L, ms) / 60_000.0).toLong())
        return if (m < 60) t("w_min", m) else "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** Kurzname für kleine Flächen (Kacheln, W1, W2). */
    fun short(e: Ev): String = when (e.kind) {
        Kind.SUNRISE -> t("s_SUNRISE")
        Kind.MIDNIGHT -> t("k_MIDNIGHT")
        Kind.LAST_THIRD -> t("w_third")
        else -> e.shortTitle
    }

    class Chain(val ch: List<Ev>, val cur: Ev, val next: Ev)

    /** Reihenfolge nach Einstellung: Fajr → Sonnenaufgang → … → Isha → Mitternacht → (letztes Drittel) → Fajr. */
    fun chain(now: Long): Chain {
        val ch = Times.forWidget(now).sortedBy { it.at }
        val ni = ch.indexOfFirst { it.at > now }.let { if (it <= 0) 1 else it }
        return Chain(ch, ch[ni - 1], ch[ni])
    }

    class Fav(val name: String, val adhanLabel: String, val adhan: String, val adhanMs: Long?, val iq: Long?, val travel: Int)

    // ---------- Widgets ----------

    /**
     * Zeichnet ein Widget. kind: 1 Countdown, 2 Ring, 3 laufendes Gebet, 4 Gebetszeiten,
     * 5 Meine Moschee, 6 ganzer Tag, 7 Gebets-Band. w/h in Pixeln, s = Pixel pro dp.
     */
    fun draw(ctx: Context, kind: Int, base: Base, s: Float, now: Long, fav: Fav?): Bitmap {
        val w = base.sky.width
        val h = base.sky.height
        val (dw, dh) = when (kind) { 1, 2 -> 170f to 170f; 3, 7 -> 360f to 80f; 6 -> 360f to 380f; else -> 360f to 170f }
        val k = min(w / (dw * s), h / (dh * s)).coerceIn(0.78f, 1.12f)
        val u = s * k
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.clipPath(rr(RectF(0f, 0f, w.toFloat(), h.toFloat()), 26f * u))
        c.drawBitmap(base.sky, 0f, 0f, null)
        val pen = Pen(ctx, c, u)
        val chn = chain(now)
        when (kind) {
            1 -> w1(pen, w, h, chn, now)
            2 -> w2(pen, w, h, chn, now, base.frosted)
            3 -> w3(pen, w, h, chn, now)
            4 -> w4(pen, w, h, chn, now, base.frosted)
            5 -> w5(pen, w, h, fav, now, base.frosted)
            6 -> w6(pen, w, h, chn, now, base.frosted)
            7 -> w7(pen, w, h, chn, now, base.frosted)
        }
        return out
    }

    private fun frac(chn: Chain, now: Long): Float =
        ((now - chn.cur.at).toFloat() / max(1L, chn.next.at - chn.cur.at)).coerceIn(0f, 1f)

    /** W1 · Countdown: alles mittig. */
    private fun w1(pen: Pen, w: Int, h: Int, chn: Chain, now: Long) {
        val u = pen.u
        val maxW = w - 24f * u
        val pl = pen.paint(11f, 700, 0xEBFFFFFF.toInt(), ls = 0.5f)
        val pn = pen.paint(22f, 700, WHITE); val name = short(chn.next); pen.fit(name, pn, maxW, 15f)
        val pc = pen.paint(31f, 300, WHITE, ls = -1f, tnum = true); val cdt = cd(chn.next.at - now); pen.fit(cdt, pc, maxW, 20f)
        val pa = pen.paint(13f, 400, WHITE); val at = t("at_time", Fmt.time(chn.next.at)); pen.fit(at, pa, maxW, 10f)
        val label = t("next_up").uppercase()
        val total = pen.lineH(pl) + 2f * u + pen.lineH(pn) + 6f * u + pc.textSize + 4f * u + pen.lineH(pa) + 10f * u + 5f * u
        var y = (h - total) / 2f
        val cx = w / 2f
        pen.text(label, cx, y, pl, 1, shadows = SH_IN); y += pen.lineH(pl) + 2f * u
        pen.text(name, cx, y, pn, 1, shadows = SH_IN); y += pen.lineH(pn) + 6f * u
        pen.text(cdt, cx, y, pc, 1, tight = true, shadows = SH_IN); y += pc.textSize + 4f * u
        pen.text(at, cx, y, pa, 1, shadows = SH_IN); y += pen.lineH(pa) + 10f * u
        val bw = 120f * u
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        bar.color = 0x47FFFFFF
        pen.c.drawRoundRect(RectF(cx - bw / 2, y, cx + bw / 2, y + 5f * u), 3f * u, 3f * u, bar)
        bar.color = WHITE
        val fw = bw * frac(chn, now)
        if (fw > 0.5f) pen.c.drawRoundRect(RectF(cx - bw / 2, y, cx - bw / 2 + max(fw, 5f * u), y + 5f * u), 3f * u, 3f * u, bar)
    }

    /** W2 · Ring auf rundem Glas. */
    private fun w2(pen: Pen, w: Int, h: Int, chn: Chain, now: Long, frosted: Bitmap) {
        val u = pen.u
        val d = 134f * u
        val cx = w / 2f; val cy = h / 2f
        glass(pen.c, RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), d / 2, frosted, u)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE; ring.strokeWidth = 8f * u; ring.strokeCap = Paint.Cap.ROUND
        val r = 57f * u
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        ring.color = 0x3DFFFFFF
        pen.c.drawArc(oval, -90f, 360f, false, ring)
        ring.color = WHITE
        val sweep = 360f * frac(chn, now)
        if (sweep > 0.5f) pen.c.drawArc(oval, -90f, sweep, false, ring)
        val maxW = 2f * (r - 10f * u)
        val pn = pen.paint(14f, 700, WHITE); val name = short(chn.next); pen.fit(name, pn, maxW, 10f)
        val pc = pen.paint(22f, 300, WHITE, ls = -1f, tnum = true); val cdt = cd(chn.next.at - now); pen.fit(cdt, pc, maxW, 14f)
        val pt = pen.paint(11f, 400, WHITE)
        val total = pen.lineH(pn) + 2f * u + pc.textSize + 2f * u + pen.lineH(pt)
        var y = cy - total / 2f
        pen.text(name, cx, y, pn, 1, shadows = SH_RING); y += pen.lineH(pn) + 2f * u
        pen.text(cdt, cx, y, pc, 1, tight = true, shadows = SH_RING); y += pc.textSize + 2f * u
        pen.text(Fmt.time(chn.next.at), cx, y, pt, 1, shadows = SH_RING)
    }

    /** W3 · Laufendes Gebet groß, Countdown bis zum nächsten, darunter das nächste klein. */
    private fun w3(pen: Pen, w: Int, h: Int, chn: Chain, now: Long) {
        val u = pen.u
        val pc = pen.paint(28f, 300, WHITE, ls = -1f, tnum = true)
        val cdt = cd(chn.next.at - now)
        val wc = pen.width(cdt, pc)
        pen.text(cdt, w - 18f * u, (h - pc.textSize) / 2f, pc, 2, tight = true, shadows = SH_IN)
        val maxW = w - 36f * u - 12f * u - wc
        val pn = pen.paint(22f, 700, WHITE)
        val pt = pen.paint(13f, 400, 0xE6FFFFFF.toInt(), tnum = true)
        val name = chn.cur.title
        val time = Fmt.time(chn.cur.at)
        pen.fit(name, pn, maxW - 8f * u - pen.width(time, pt), 15f)
        val p2 = pen.paint(12f, 400, 0xE6FFFFFF.toInt(), tnum = true)
        val line2 = chn.next.title + " " + Fmt.time(chn.next.at)
        pen.fit(line2, p2, maxW, 9f)
        val h1 = pen.lineH(pn); val h2 = pen.lineH(p2)
        var y = (h - (h1 + 2f * u + h2)) / 2f
        val x = 18f * u
        pen.text(name, x, y, pn, shadows = SH_IN)
        // Uhrzeit auf derselben Grundlinie wie der Name
        val baseY = y + pen.ascentIn(pn)
        pen.text(time, x + pen.width(name, pn) + 8f * u, baseY - pen.ascentIn(pt), pt, shadows = SH_IN)
        y += h1 + 2f * u
        pen.text(line2, x, y, p2, shadows = SH_IN)
    }

    /** W7 · Gebets-Band: laufendes Ereignis links, danach die nächsten fünf. */
    private fun w7(pen: Pen, w: Int, h: Int, chn: Chain, now: Long, frosted: Bitmap) {
        val u = pen.u
        val ci = chn.ch.indexOf(chn.cur)
        val items = chn.ch.subList(ci, min(chn.ch.size, ci + 6))
        val gap = 5f * u
        val tw = (w - 16f * u - gap * 5) / 6f
        var x = 8f * u
        for (e in items) {
            val running = e == chn.cur
            tile(pen, RectF(x, 8f * u, x + tw, h - 8f * u), frosted, short(e), Fmt.time(e.at), if (running) t("w_now") else cd(e.at - now), running)
            x += tw + gap
        }
    }

    /** Zeile mit zwei Texten auf gemeinsamer Grundlinie (z. B. „Asr  51 Min“), mittig. */
    private fun baselineRow(pen: Pen, cx: Float, top: Float, a: String, pa: Paint, b: String, pb: Paint, gap: Float, tightB: Boolean): Float {
        val upA = pen.ascentIn(pa); val downA = pen.lineH(pa) - upA
        val upB = pen.ascentIn(pb, tightB); val downB = pen.lineH(pb, tightB) - upB
        val up = max(upA, upB)
        val total = pen.width(a, pa) + gap + pen.width(b, pb)
        val x = cx - total / 2f
        val base = top + up
        pen.text(a, x, base - upA, pa, shadows = SH_IN)
        pen.text(b, x + pen.width(a, pa) + gap, base - upB, pb, tight = tightB, shadows = SH_IN)
        return up + max(downA, downB)
    }

    /** W4 · Gebetszeiten: Kopf mittig, darunter fünf Kacheln. */
    private fun w4(pen: Pen, w: Int, h: Int, chn: Chain, now: Long, frosted: Bitmap) {
        val u = pen.u
        val cx = w / 2f
        var y = 12f * u
        val pl = pen.paint(11f, 700, 0xEBFFFFFF.toInt(), ls = 0.5f)
        val label = (Prefs.place + " · " + t("next_up")).uppercase()
        pen.fit(label, pl, w - 28f * u, 8f)
        pen.text(label, cx, y, pl, 1, shadows = SH_IN); y += pen.lineH(pl)
        val pn = pen.paint(20f, 700, WHITE)
        val pc = pen.paint(26f, 300, WHITE, ls = -1f, tnum = true)
        y += baselineRow(pen, cx, y, chn.next.title, pn, cd(chn.next.at - now), pc, 10f * u, true)
        y += 8f * u
        val all = Times.around(now).sortedBy { it.at }
        val kinds = listOf(Kind.FAJR, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA)
        val gap = 5f * u
        val tw = (w - 28f * u - gap * 4) / 5f
        var x = 14f * u
        for (kd in kinds) {
            val running = chn.cur.kind == kd
            val e = if (running) chn.cur else all.firstOrNull { it.kind == kd && it.at > now }
            if (e != null) tile(pen, RectF(x, y, x + tw, h - 12f * u), frosted, short(e), Fmt.time(e.at), if (running) t("w_now") else cd(e.at - now), running)
            x += tw + gap
        }
    }

    /** W5 · Meine Moschee. */
    private fun w5(pen: Pen, w: Int, h: Int, fav: Fav?, now: Long, frosted: Bitmap) {
        val u = pen.u
        val cx = w / 2f
        var y = 12f * u
        val pl = pen.paint(11f, 700, 0xEBFFFFFF.toInt(), ls = 0.5f)
        val title = (if (fav != null) t("my_mosque") + " · " + fav.name else t("my_mosque")).uppercase()
        pen.fit(title, pl, w - 28f * u, 8f)
        pen.text(title, cx, y, pl, 1, shadows = SH_IN); y += pen.lineH(pl) + 8f * u
        if (fav == null) {
            val r = RectF(14f * u, y, w - 14f * u, h - 12f * u)
            glass(pen.c, r, 14f * u, frosted, u)
            val tp = pen.paint(12f, 500, WHITE)
            val lw = (r.width() - 20f * u).toInt().coerceAtLeast(1)
            val text = t("my_mosque_hint")
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, tp, lw).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4).build()
            pen.c.save()
            pen.c.translate(r.left + 10f * u, r.top + (r.height() - sl.height) / 2f)
            sl.draw(pen.c)
            pen.c.restore()
            return
        }
        // Zwei Glas-Kacheln: Adhan und Iqamah
        val gap = 8f * u
        val pw = (w - 28f * u - gap) / 2f
        val pk = pen.paint(11f, 700, 0xEBFFFFFF.toInt(), ls = 0.5f)
        val pkG = pen.paint(11f, 700, 0xEB7CF0C8.toInt(), ls = 0.5f)
        val pt = pen.paint(21f, 700, WHITE, tnum = true)
        val ptG = pen.paint(21f, 700, GREEN, tnum = true)
        val pr = pen.paint(10f, 600, GREEN, tnum = true)
        val ph = 14f * u + pen.lineH(pk) + pen.lineH(pt) + pen.lineH(pr)
        val r1 = RectF(14f * u, y, 14f * u + pw, y + ph)
        val r2 = RectF(r1.right + gap, y, w - 14f * u, y + ph)
        glass(pen.c, r1, 14f * u, frosted, u)
        glass(pen.c, r2, 14f * u, frosted, u, GLASS_IQ)
        fun panel(r: RectF, lab: String, lp: TextPaint, time: String, tp: TextPaint, rest: String) {
            pen.fit(lab, lp, r.width() - 20f * u, 8f)
            var yy = r.top + 7f * u
            pen.text(lab, r.centerX(), yy, lp, 1); yy += pen.lineH(lp)
            pen.text(time, r.centerX(), yy, tp, 1); yy += pen.lineH(tp)
            pen.text(rest, r.centerX(), yy, pr, 1)
        }
        val adhanRest = fav.adhanMs?.let { if (it > now) cd(it - now) else t("w_begun") } ?: ""
        panel(r1, fav.adhanLabel, pk, fav.adhan, pt, adhanRest)
        panel(r2, t("iqamah").uppercase(), pkG, fav.iq?.let { Fmt.time(it) } ?: "–", ptG, fav.iq?.let { if (it > now) cd(it - now) else t("w_begun") } ?: "")
        y += ph + 8f * u
        // Leiste „Losfahren bis …“ – grün, rot wenn es zu spät ist
        val leave = fav.iq?.let { it - fav.travel * 60_000L }
        val late = leave != null && leave < now
        val bar = RectF(14f * u, y, w - 14f * u, 0f)
        val pb = pen.paint(13f, 700, if (late) 0xFFFFD5D0.toInt() else 0xFFC8FFD5.toInt())
        bar.bottom = y + 12f * u + pen.lineH(pb) + 2f * u
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = if (late) 0x80962828.toInt() else 0x801E783C.toInt()
        pen.c.drawRoundRect(bar, 12f * u, 12f * u, fill)
        fill.style = Paint.Style.STROKE; fill.strokeWidth = u
        fill.color = if (late) 0x66FFAAA0 else 0x668EF0A8
        pen.c.drawRoundRect(RectF(bar.left + u / 2, bar.top + u / 2, bar.right - u / 2, bar.bottom - u / 2), 12f * u, 12f * u, fill)
        val parts = if (leave != null) listOf(t("leave_by", Fmt.time(leave)), "·", Fmt.duration(fav.travel * 60_000L)) else listOf(t("no_times"))
        val g = 10f * u
        var tot = parts.sumOf { pen.width(it, pb).toDouble() }.toFloat() + g * (parts.size - 1)
        while (tot > bar.width() - 20f * u && pb.textSize > 9f * u) {
            pb.textSize = pb.textSize - 0.5f * u
            tot = parts.sumOf { pen.width(it, pb).toDouble() }.toFloat() + g * (parts.size - 1)
        }
        var x = bar.centerX() - tot / 2f
        val ty = bar.top + (bar.height() - pen.lineH(pb)) / 2f
        for (p in parts) { pen.text(p, x, ty, pb); x += pen.width(p, pb) + g }
    }

    /** W6 · Ganzer Tag: islamischer Tag von Fajr bis zum nächsten Fajr. */
    private fun w6(pen: Pen, w: Int, h: Int, chn: Chain, now: Long, frosted: Bitmap) {
        val u = pen.u
        val cx = w / 2f
        var y = 14f * u
        val pd = pen.paint(12f, 400, 0xEBFFFFFF.toInt())
        val date = DateText.widgetLine(now); pen.fit(date, pd, w - 32f * u, 9f)
        pen.text(date, cx, y, pd, 1, shadows = SH_IN); y += pen.lineH(pd)
        val pp = pen.paint(14f, 600, WHITE)
        val place = Prefs.place; pen.fit(place, pp, w - 32f * u, 10f)
        pen.text(place, cx, y, pp, 1, shadows = SH_IN); y += pen.lineH(pp) + 6f * u + 2f * u
        val pk = pen.paint(11f, 700, 0xEBFFFFFF.toInt(), ls = 1f)
        pen.text(t("next_up").uppercase(), cx, y, pk, 1, shadows = SH_IN); y += pen.lineH(pk)
        val pn = pen.paint(22f, 700, WHITE); val name = chn.next.title; pen.fit(name, pn, w - 32f * u, 15f)
        pen.text(name, cx, y, pn, 1, shadows = SH_IN); y += pen.lineH(pn)
        val pc = pen.paint(32f, 300, WHITE, ls = -1f, tnum = true)
        pen.text(cd(chn.next.at - now), cx, y, pc, 1, tight = true, shadows = SH_IN); y += pc.textSize + 4f * u + 6f * u
        // Liste auf Milchglas
        val list = RectF(16f * u, y, w - 16f * u, h - 16f * u)
        glass(pen.c, list, 18f * u, frosted, u)
        val fi = chn.ch.indexOfLast { it.kind == Kind.FAJR && it.at <= now }.coerceAtLeast(0)
        var end = chn.ch.drop(fi + 1).indexOfFirst { it.kind == Kind.FAJR }
        end = if (end < 0) chn.ch.size else fi + 1 + end
        val rows = chn.ch.subList(fi, end)
        if (rows.isEmpty()) return
        val inner = RectF(list.left + 4f * u, list.top + 4f * u, list.right - 4f * u, list.bottom - 4f * u)
        val rh = inner.height() / rows.size
        val pName = pen.paint(14f, 500, WHITE); val pNameB = pen.paint(14f, 700, WHITE)
        val pRest = pen.paint(11f, 600, GREEN, tnum = true)
        val pTime = pen.paint(15f, 600, WHITE, tnum = true)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((i, e) in rows.withIndex()) {
            val running = e == chn.cur
            val past = e.at <= now && !running
            val top = inner.top + i * rh
            val row = RectF(inner.left, top, inner.right, top + rh)
            if (running) {
                fill.style = Paint.Style.FILL; fill.color = 0x29FFFFFF
                pen.c.drawRoundRect(row, 12f * u, 12f * u, fill)
                fill.style = Paint.Style.STROKE; fill.strokeWidth = 1.5f * u; fill.color = BORDER_RUN
                val hb = 0.75f * u
                pen.c.drawRoundRect(RectF(row.left + hb, row.top + hb, row.right - hb, row.bottom - hb), 12f * u - hb, 12f * u - hb, fill)
            } else if (i > 0 && rows[i - 1] != chn.cur) {
                fill.style = Paint.Style.FILL; fill.color = 0x1FFFFFFF
                pen.c.drawRect(row.left + 6f * u, top, row.right - 6f * u, top + u, fill)
            }
            val alpha = if (past) 128 else 255
            val x0 = row.left + 10f * u
            val x1 = row.right - 10f * u
            fill.style = Paint.Style.FILL
            fill.color = if (running) GREEN else 0x73FFFFFF
            fill.alpha = fill.alpha * alpha / 255
            pen.c.drawCircle(x0 + 3.5f * u, row.centerY(), 3.5f * u, fill)
            val time = Fmt.time(e.at)
            pTime.alpha = alpha
            val tW = max(46f * u, pen.width(time, pTime))
            pen.text(time, x1, row.centerY() - pen.lineH(pTime) / 2f, pTime, 2)
            val rest = if (running) t("w_now") else if (past) "" else cd(e.at - now)
            pRest.alpha = alpha
            val restRight = x1 - tW - 8f * u
            if (rest.isNotEmpty()) pen.text(rest, restRight, row.centerY() - pen.lineH(pRest) / 2f, pRest, 2)
            val np = if (running) pNameB else pName
            np.alpha = alpha
            val nx = x0 + 7f * u + 8f * u
            val nameMax = restRight - max(64f * u, pen.width(rest, pRest)) - 8f * u - nx
            val nm = e.title
            val npc = TextPaint(np); pen.fit(nm, npc, nameMax, 10f)
            pen.text(nm, nx, row.centerY() - pen.lineH(npc) / 2f, npc)
        }
    }
}
