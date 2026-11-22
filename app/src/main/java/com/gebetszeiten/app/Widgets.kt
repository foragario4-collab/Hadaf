package com.gebetszeiten.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Alle Widgets: von der App selbst gezeichnet, exakt nach der Widget-Vorschau. */
open class SkyWidget(private val kind: Int) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Prefs.init(ctx)
        val pr = goAsync()
        Widgets.run {
            try {
                ids.forEach { Widgets.push(ctx, mgr, it, kind) }
                Widgets.scheduleTick(ctx)
            } finally {
                pr.finish()
            }
        }
        Scheduler.schedule(ctx)
        Widgets.refreshWeather(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        Prefs.init(ctx)
        val pr = goAsync()
        Widgets.run {
            try { Widgets.push(ctx, mgr, id, kind) } finally { pr.finish() }
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { Widgets.forget(it) }
    }
}

class SmallWidget : SkyWidget(1)   // W1 Countdown
class RingWidget : SkyWidget(2)    // W2 Ring
class BarWidget : SkyWidget(3)     // W3 Laufendes Gebet
class MediumWidget : SkyWidget(4)  // W4 Gebetszeiten
class MosqueWidget : SkyWidget(5)  // W5 Meine Moschee
class DayWidget : SkyWidget(6)     // W6 Ganzer Tag
class BandWidget : SkyWidget(7)    // W7 Gebets-Band

/** Minutentakt: Countdowns springen jede Minute weiter (weckt das Handy nicht auf). */
class WidgetTick : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        val pr = goAsync()
        Widgets.run {
            try { Widgets.updateAllNow(ctx) } finally { pr.finish() }
        }
    }
}

object Widgets {
    private val classes = mapOf(
        1 to SmallWidget::class.java, 2 to RingWidget::class.java, 3 to BarWidget::class.java,
        4 to MediumWidget::class.java, 5 to MosqueWidget::class.java, 6 to DayWidget::class.java,
        7 to BandWidget::class.java
    )
    private val exec = Executors.newSingleThreadExecutor()
    private val bases = HashMap<Int, WidgetPainter.Base>()
    @Volatile private var favCache: Pair<Long, Mosque?>? = null

    fun run(block: () -> Unit) {
        exec.execute { try { block() } catch (e: Throwable) { } }
    }

    fun forget(id: Int) {
        run { synchronized(bases) { bases.remove(id) } }
    }

    /** Von überall aufrufbar (Gebetswechsel, Einstellungen, Wetter): zeichnet alle Widgets neu. */
    fun updateAll(ctx: Context) {
        val app = ctx.applicationContext
        run { updateAllNow(app) }
    }

    fun updateAllNow(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        var any = false
        for ((kind, cls) in classes) {
            m.getAppWidgetIds(ComponentName(ctx, cls)).forEach {
                any = true
                push(ctx, m, it, kind)
            }
        }
        if (any) {
            scheduleTick(ctx)
            refreshWeather(ctx)
        } else {
            cancelTick(ctx)
        }
    }

    /** Wetter aktuell halten (höchstens alle 30 Min), danach neu zeichnen. */
    fun refreshWeather(ctx: Context) {
        val app = ctx.applicationContext
        Weather.refreshIfNeeded(app, Prefs.lat, Prefs.lng) { updateAll(app) }
    }

    private fun tickIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, WidgetTick::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 200L
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.set(AlarmManager.RTC, next, tickIntent(ctx))
            else am.setExact(AlarmManager.RTC, next, tickIntent(ctx))
        } catch (e: Exception) {
            am.set(AlarmManager.RTC, next, tickIntent(ctx))
        }
    }

    private fun cancelTick(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Größe des Widgets in dp (Hochformat: Breite = min, Höhe = max). */
    private fun sizeDp(mgr: AppWidgetManager, id: Int, kind: Int): Pair<Int, Int> {
        val (dw, dh) = when (kind) { 1, 2 -> 170 to 170; 3, 7 -> 360 to 80; 6 -> 360 to 380; else -> 360 to 170 }
        val o = mgr.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: dw
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: dh
        return w to h
    }

    private fun favMosque(ctx: Context): Mosque? {
        val id = Prefs.favId
        if (id.isBlank()) return null
        val now = System.currentTimeMillis()
        favCache?.let { (at, m) -> if (now - at < 10 * 60_000L && m?.id == id) return m }
        val m = try { Mosques.listAll(ctx).firstOrNull { it.id == id } } catch (e: Exception) { null }
        favCache = now to m
        return m
    }

    private fun favData(ctx: Context, now: Long): WidgetPainter.Fav? {
        val m = favMosque(ctx) ?: return null
        val evs = Times.around(now)
        val dist = Mosques.distance(Prefs.lat, Prefs.lng, m.lat, m.lng)
        val travel = travelMin(dist, Prefs.travelMode)
        val niq = nextIqamah(m, evs, now, now + travel * 60_000L)
        val p = niq?.first ?: evs.first { it.at > now && it.kind.prayer }
        val adhan = niq?.second ?: Fmt.time(p.at)
        val adhanMs = try {
            val parts = adhan.split(":")
            p.day.atTime(parts[0].toInt(), parts[1].toInt()).atZone(Times.zone()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            p.at
        }
        return WidgetPainter.Fav(
            NameFmt.name(m.name), "${WidgetPainter.short(p)} · ${t("adhan")}".uppercase(),
            adhan, adhanMs, niq?.third, travel
        )
    }

    fun push(ctx: Context, mgr: AppWidgetManager, id: Int, kind: Int) {
        try {
            val now = System.currentTimeMillis()
            val (wDp, hDp) = sizeDp(mgr, id, kind)
            // volle Bildschirmschärfe für Schrift, höchstens Faktor 3
            val s = min(3f, ctx.resources.displayMetrics.density)
            val w = max(1, (wDp * s).toInt())
            val h = max(1, (hDp * s).toInt())
            // Himmel nur neu rechnen, wenn sich Größe, Wetter oder die Viertelstunde ändert
            val wx = Weather.current(ctx)
            val key = "$kind-$w-$h-${wx?.at ?: 0}-${now / 900_000L}"
            var base = synchronized(bases) { bases[id] }
            if (base == null || base.key != key) {
                val u = s * min(w / (170f * s), h / (170f * s)).coerceIn(0.78f, 1.12f)
                base = WidgetPainter.base(ctx, w, h, u, now, key)
                synchronized(bases) { bases[id] = base }
            }
            val bmp = WidgetPainter.draw(ctx, kind, base, s, now, if (kind == 5) favData(ctx, now) else null)
            val v = RemoteViews(ctx.packageName, R.layout.widget_img)
            v.setImageViewBitmap(R.id.bg, bmp)
            v.setContentDescription(R.id.bg, describe(kind, now))
            v.setOnClickPendingIntent(R.id.root, openApp(ctx))
            mgr.updateAppWidget(id, v)
        } catch (e: Throwable) {
        }
    }

    /** Vorlese-Text für Bedienungshilfen (das Widget ist ein Bild). */
    private fun describe(kind: Int, now: Long): String {
        val c = WidgetPainter.chain(now)
        return "${c.next.title} ${t("at_time", Fmt.time(c.next.at))}, ${WidgetPainter.cd(c.next.at - now)}"
    }
}
