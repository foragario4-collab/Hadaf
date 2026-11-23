package com.gebetszeiten.app

/**
 * Welches Gebet „Meine Moschee“, das Widget und der Radar gerade zeigen – und in welcher Phase:
 *
 *  0 = vor dem Adhan        → „Dhuhr · Adhan in 25 Min“
 *  1 = Adhan war, Iqamah kommt → „Dhuhr · Iqamah in 8 Min“
 *  2 = Iqamah ist vorbei    → „Iqamah verpasst · vor 15 Min“
 *  3 = Adhan war, keine Iqamah bekannt → „begonnen“
 *
 * Das Gebet wechselt erst 30 Minuten vor dem nächsten Adhan – bis dahin bleibt „Iqamah verpasst“ stehen.
 */
internal class MosqueFocus(
    val ev: Ev,
    val adhanTxt: String,
    val adhanMs: Long,
    val iqMs: Long?,
    val phase: Int,
    /** das Gebet danach (für „danach: Asr · Adhan in …“) */
    val nextTitle: String? = null,
    val nextAdhanMs: Long? = null
)

private const val SWITCH_BEFORE = 30 * 60_000L

internal fun mosqueFocus(m: Mosque, evs: List<Ev>, now: Long): MosqueFocus? {
    fun msOf(e: Ev, hhmm: String): Long? = try {
        val p = hhmm.trim().split(":")
        e.day.atTime(p[0].toInt(), p[1].take(2).toInt()).atZone(Times.zone()).toInstant().toEpochMilli()
    } catch (ex: Exception) {
        null
    }
    val list = evs.filter { it.kind.prayer }.map { e ->
        val jum = e.isJumuah && (m.iqamah.containsKey("JUMUAH") || m.adhan.containsKey("JUMUAH"))
        val key = if (jum) "JUMUAH" else e.kind.name
        val adTxt = m.adhan[key]?.takeIf { msOf(e, it) != null } ?: Fmt.time(e.at)
        val adMs = msOf(e, adTxt) ?: e.at
        MosqueFocus(e, adTxt, adMs, m.iqamah[key]?.let { msOf(e, it) }, 0)
    }.sortedBy { it.adhanMs }
    if (list.isEmpty()) return null
    val fi = list.indexOfLast { it.adhanMs - SWITCH_BEFORE <= now }.coerceAtLeast(0)
    val f = list[fi]
    val nx = list.getOrNull(fi + 1)
    val iq = f.iqMs
    val phase = when {
        now < f.adhanMs -> 0
        iq != null && now < iq -> 1
        iq != null -> 2
        else -> 3
    }
    return MosqueFocus(f.ev, f.adhanTxt, f.adhanMs, iq, phase, nx?.ev?.title, nx?.adhanMs)
}

/** Kurzer Statustext, z. B. „Adhan in 25 Min“, „Iqamah in 8 Min“, „Iqamah verpasst · vor 15 Min“. */
internal fun MosqueFocus.statusText(now: Long): String = when (phase) {
    0 -> t("mq_adhan_in", WidgetPainter.cd(adhanMs - now))
    1 -> t("mq_iqamah_in", WidgetPainter.cd((iqMs ?: now) - now))
    2 -> t("mq_missed", WidgetPainter.cd(now - (iqMs ?: now)))
    else -> t("adhan") + " " + t("w_begun")
}
