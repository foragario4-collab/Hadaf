package com.gebetszeiten.app

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Eigene Korrekturen je Moschee, getrennt von den geladenen Daten:
 * Zeiten (Adhan/Iqamah, Iqamah auch als „+10“ = Minuten nach Adhan), Name und Angaben
 * (Frauenbereich, Wudu, Parken, Barrierefrei). Sie bleiben erhalten, wenn die App neu lädt.
 */
internal object MosqueExtra {
    val KEYS = listOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA", "JUMUAH")
    val FACTS = listOf("women", "wudu", "parking", "access")

    class Entry(
        val adhan: Map<String, String> = emptyMap(),
        val iqamah: Map<String, String> = emptyMap(),
        val name: String? = null,
        val facts: Map<String, String> = emptyMap()
    ) {
        val isEmpty get() = adhan.isEmpty() && iqamah.isEmpty() && name.isNullOrBlank() && facts.isEmpty()
        fun copy(
            adhan: Map<String, String> = this.adhan, iqamah: Map<String, String> = this.iqamah,
            name: String? = this.name, facts: Map<String, String> = this.facts
        ) = Entry(adhan, iqamah, name, facts)
    }

    @Volatile
    private var map: MutableMap<String, Entry>? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "moschee_korrekturen.json")

    private fun toMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.optString(k) }
        return out
    }

    private fun toJson(m: Map<String, String>): JSONObject {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    @Synchronized
    private fun load(ctx: Context): MutableMap<String, Entry> {
        map?.let { return it }
        val m = java.util.concurrent.ConcurrentHashMap<String, Entry>()
        try {
            val f = file(ctx)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.keys().forEach { id ->
                    val e = o.optJSONObject(id) ?: return@forEach
                    m[id] = Entry(
                        toMap(e.optJSONObject("adhan")), toMap(e.optJSONObject("iqamah")),
                        e.optString("name").ifBlank { null }, toMap(e.optJSONObject("facts"))
                    )
                }
            }
        } catch (e: Exception) {
        }
        map = m
        return m
    }

    @Synchronized
    private fun save(ctx: Context) {
        val o = JSONObject()
        (map ?: return).forEach { (id, e) ->
            if (e.isEmpty) return@forEach
            o.put(
                id, JSONObject().put("adhan", toJson(e.adhan)).put("iqamah", toJson(e.iqamah))
                    .put("name", e.name ?: "").put("facts", toJson(e.facts))
            )
        }
        try { file(ctx).writeText(o.toString()) } catch (e: Exception) { }
    }

    fun get(ctx: Context, id: String): Entry? = load(ctx)[id]

    private fun update(ctx: Context, id: String, f: (Entry) -> Entry) {
        val m = load(ctx)
        val n = f(m[id] ?: Entry())
        if (n.isEmpty) m.remove(id) else m[id] = n
        save(ctx)
    }

    /** Eigene Zeiten setzen (nur geänderte Felder; leerer Text = Feld leeren). */
    fun setTimes(ctx: Context, id: String, adhan: Map<String, String>, iqamah: Map<String, String>) =
        update(ctx, id) { it.copy(adhan = adhan, iqamah = iqamah) }

    fun clearTimes(ctx: Context, id: String) = update(ctx, id) { it.copy(adhan = emptyMap(), iqamah = emptyMap()) }

    fun setName(ctx: Context, id: String, name: String?) = update(ctx, id) { it.copy(name = name?.trim()?.ifBlank { null }) }

    fun setFacts(ctx: Context, id: String, facts: Map<String, String>) =
        update(ctx, id) { it.copy(facts = facts.filterValues { v -> v.isNotBlank() && v != "unknown" }) }

    /** Wenn eine Moschee eine neue Kennung bekommt (Link angehängt, Ort korrigiert): Korrekturen mitnehmen. */
    fun moveId(ctx: Context, from: String, to: String) {
        if (from == to) return
        val m = load(ctx)
        val e = m.remove(from) ?: return
        val old = m[to]
        m[to] = if (old == null) e else Entry(
            old.adhan + e.adhan, old.iqamah + e.iqamah, e.name ?: old.name, old.facts + e.facts
        )
        save(ctx)
    }

    /** „+10“ → 10 (Minuten nach Adhan), sonst null. */
    fun relMinutes(v: String): Int? = v.trim().takeIf { it.startsWith("+") }?.drop(1)?.trim()?.toIntOrNull()

    private fun hhmm(v: String): Int? = try {
        val p = v.trim().split(":")
        val h = p[0].toInt()
        val mi = p[1].take(2).toInt()
        if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    } catch (e: Exception) {
        null
    }

    private fun fmt(min: Int): String {
        val x = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(x / 60, x % 60)
    }

    /**
     * Korrekturen auf eine Moschee anwenden. Leerer Text löscht das Feld, „+N“ wird mit dem
     * Adhan des Tages zu einer Uhrzeit (Adhan der Moschee, sonst die berechnete Zeit).
     */
    fun apply(ctx: Context, m: Mosque, calc: () -> Map<String, Int>): Mosque {
        val e = load(ctx)[m.id] ?: return m
        if (e.isEmpty) return m
        var out = m
        if (!e.name.isNullOrBlank()) out = out.copy(name = e.name)
        if (e.adhan.isNotEmpty() || e.iqamah.isNotEmpty()) {
            val ad = LinkedHashMap(m.adhan)
            e.adhan.forEach { (k, v) -> if (v.isBlank()) ad.remove(k) else if (hhmm(v) != null) ad[k] = fmt(hhmm(v)!!) }
            val iq = LinkedHashMap(m.iqamah)
            var calcCache: Map<String, Int>? = null
            e.iqamah.forEach { (k, v) ->
                when {
                    v.isBlank() -> iq.remove(k)
                    relMinutes(v) != null -> {
                        val base = ad[k]?.let { hhmm(it) } ?: (calcCache ?: calc().also { calcCache = it })[k]
                        if (base != null) iq[k] = fmt(base + relMinutes(v)!!)
                    }
                    hhmm(v) != null -> iq[k] = fmt(hhmm(v)!!)
                }
            }
            out = out.copy(adhan = ad, iqamah = iq)
        }
        return out
    }

    /** Hat die Moschee eigene Zeiten? */
    fun hasOwnTimes(ctx: Context, id: String): Boolean = load(ctx)[id]?.let { it.adhan.isNotEmpty() || it.iqamah.isNotEmpty() } == true
}
