package com.gebetszeiten.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize

private fun modeLabels() = listOf(t("mode_off"), t("mode_tone_long"), t("mode_adhan"))

internal fun displayName(ctx: Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "Audio"
} catch (e: Exception) {
    "Audio"
}

internal fun keep(ctx: Context, uri: Uri) {
    try {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: Exception) {
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

@Composable
fun SettingsScreen(v: Int, onBack: () -> Unit = {}) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var choice by remember { mutableStateOf<Choice?>(null) }
    var coords by remember { mutableStateOf(false) }
    var locStatus by remember { mutableStateOf<String?>(null) }

    fun changed() = Scheduler.reschedule(ctx)

    fun useGps() {
        locStatus = t("locating")
        Loc.fetch(ctx) { loc ->
            if (loc == null) {
                locStatus = t("no_loc")
            } else {
                Prefs.lat = loc.latitude
                Prefs.lng = loc.longitude
                Prefs.place = t("my_location")
                Prefs.autoLoc = true
                locStatus = null
                changed()
                Loc.placeName(ctx, loc.latitude, loc.longitude) { n ->
                    if (n != null) {
                        Prefs.place = n
                        Widgets.updateAll(ctx)
                    }
                }
            }
        }
    }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            useGps()
        } else {
            locStatus = t("no_perm")
        }
    }

    fun gps() {
        if (hasLocPermission(ctx)) useGps()
        else locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val pickAdhan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keep(ctx, uri)
            Prefs.adhanUri = uri.toString()
            Prefs.adhanName = displayName(ctx, uri)
        }
    }
    val pickFajr = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keep(ctx, uri)
            Prefs.fajrAdhanUri = uri.toString()
            Prefs.fajrAdhanName = displayName(ctx, uri)
        }
    }


    // Zustand, der Seitenwechsel überdauert
    var updRel by remember { mutableStateOf(Updater.latest) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var updBusy by remember { mutableStateOf(false) }
    var updPct by remember { mutableStateOf(-1) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player?.release() } }

    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(enabled = page.isNotEmpty()) { page = "" }
    // Ausgeblendete Orte alle wieder eingeblendet → zurück zur Übersicht
    if (page == "hidden" && Prefs.hiddenIds.isEmpty()) androidx.compose.runtime.LaunchedEffect(Unit) { page = "" }
    val dir = if (Prefs.lang.rtl) -1 else 1

    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            val fwd = targetState.isNotEmpty()
            val inSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            (androidx.compose.animation.slideInHorizontally(inSpec) { w -> if (fwd) w * dir else -w / 3 * dir } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)))
                .togetherWith(
                    androidx.compose.animation.slideOutHorizontally(inSpec) { w -> if (fwd) -w / 3 * dir else w * dir } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
                )
        },
        label = "einstellungen"
    ) { pg ->
        if (pg.isEmpty()) {
            Overview(onBack = onBack, updNewer = Updater.isNewer(updRel)) { page = it }
        } else {
            SubPage(pageTitle(pg), onBack = { page = "" }) {
                when (pg) {
                    "loc" -> {
                        // ---------- Sprache ----------
                        Section(t("sec_language")) {
                            ValueRow(t("sec_language"), Prefs.lang.native, first = true) {
                                choice = Choice(t("sec_language"), Lang.entries.map { it.native }, Prefs.lang.ordinal) { i ->
                                    Prefs.lang = Lang.entries[i]
                                    Notifs.channels(ctx)
                                    Widgets.updateAll(ctx)
                                }
                            }
                        }

                        // ---------- Standort ----------
                        Section(t("sec_location"), footer = locStatus ?: t("loc_footer")) {
                            ValueRow(t("place"), Prefs.place, first = true, sub = "%.4f, %.4f".format(Prefs.lat, Prefs.lng)) {
                                val names = Loc.cities.map { it.name } + t("enter_coords")
                                choice = Choice(t("choose_place"), names, Loc.cities.indexOfFirst { it.name == Prefs.place }) { i ->
                                    if (i < Loc.cities.size) {
                                        val c = Loc.cities[i]
                                        Prefs.lat = c.lat
                                        Prefs.lng = c.lng
                                        Prefs.place = c.name
                                        Prefs.autoLoc = false
                                        changed()
                                    } else {
                                        coords = true
                                    }
                                }
                            }
                            ToggleRow(t("auto_gps"), Prefs.autoLoc, sub = t("auto_gps_sub")) { on ->
                                if (on) {
                                    gps()
                                } else {
                                    Prefs.autoLoc = false
                                }
                            }
                            ButtonRow(t("locate_now")) { gps() }
                        }
                    }
                    "calc" -> {
                        // ---------- Asr ----------
                        Section(Kind.ASR.label) {
                            CheckRow(t("asr_std"), Prefs.asr == 1, first = true, sub = t("asr_std_sub")) {
                                Prefs.asr = 1
                                changed()
                            }
                            CheckRow(t("asr_hanafi"), Prefs.asr == 2, sub = t("asr_han_sub")) {
                                Prefs.asr = 2
                                changed()
                            }
                        }

                        // ---------- Berechnung ----------
                        val m = PrayerCalc.method(Prefs.method)
                        val fa = if (m.id == "custom") Prefs.customFajr.toDouble() else m.fajr
                        val ia = if (m.id == "custom") Prefs.customIsha.toDouble() else m.isha
                        val ishaText = if (m.ishaMinutes > 0) t("isha_min", m.ishaMinutes) else t("isha_deg", ia)
                        Section(t("sec_calc"), footer = t("calc_footer", fa, ishaText)) {
                            PrayerCalc.methods.forEachIndexed { i, me ->
                                CheckRow(me.label, me.id == m.id, first = i == 0) {
                                    Prefs.method = me.id
                                    changed()
                                }
                            }
                            if (m.id == "custom") {
                                StepperRow(t("fajr_angle"), "%.1f°".format(Prefs.customFajr),
                                    onMinus = { Prefs.customFajr = (Prefs.customFajr - 0.5f).coerceAtLeast(10f); changed() },
                                    onPlus = { Prefs.customFajr = (Prefs.customFajr + 0.5f).coerceAtMost(22f); changed() })
                                StepperRow(t("isha_angle"), "%.1f°".format(Prefs.customIsha),
                                    onMinus = { Prefs.customIsha = (Prefs.customIsha - 0.5f).coerceAtLeast(10f); changed() },
                                    onPlus = { Prefs.customIsha = (Prefs.customIsha + 0.5f).coerceAtMost(22f); changed() })
                            }
                        }
                        Section("") {
                            val hl = listOf("angle" to t("hl_angle"), "middle" to t("hl_middle"), "seventh" to t("hl_seventh"))
                            ValueRow(t("high_lat"), hl.firstOrNull { it.first == Prefs.highLat }?.second ?: hl[0].second, first = true) {
                                choice = Choice(t("high_lat"), hl.map { it.second }, hl.indexOfFirst { it.first == Prefs.highLat }) { i ->
                                    Prefs.highLat = hl[i].first
                                    changed()
                                }
                            }
                        }
                    }
                    "night" -> {
                        // ---------- Nacht ----------
                        Section(t("sec_night"), footer = t("night_footer")) {
                            val mm = listOf("fajr" to t("mm_fajr"), "sunrise" to t("mm_sunrise"))
                            ValueRow(Kind.MIDNIGHT.label, mm.firstOrNull { it.first == Prefs.midnightMode }?.second ?: mm[0].second, first = true) {
                                choice = Choice(t("midnight_calc"), mm.map { it.second }, mm.indexOfFirst { it.first == Prefs.midnightMode }) { i ->
                                    Prefs.midnightMode = mm[i].first
                                    changed()
                                }
                            }
                            val ml = modeLabels()
                            ValueRow(Kind.LAST_THIRD.label, ml[Prefs.mode(Kind.LAST_THIRD).coerceIn(0, 1)], sub = t("third_sub")) {
                                choice = Choice(Kind.LAST_THIRD.label, ml.take(2), Prefs.mode(Kind.LAST_THIRD)) { i ->
                                    Prefs.setMode(Kind.LAST_THIRD, i)
                                    changed()
                                }
                            }
                        }
                    }
                    "offsets" -> {
                        // ---------- Zeitkorrektur ----------
                        Section(t("sec_offsets"), footer = t("offsets_footer")) {
                            listOf(Kind.FAJR, Kind.SUNRISE, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA).forEachIndexed { idx, k ->
                                val o = Prefs.offset(k)
                                StepperRow(k.label, t("min_short", signed(o)), first = idx == 0,
                                    onMinus = { Prefs.setOffset(k, (o - 1).coerceAtLeast(-60)); changed() },
                                    onPlus = { Prefs.setOffset(k, (o + 1).coerceAtMost(60)); changed() })
                            }
                        }
                    }
                    "prayers" -> {
                        // ---------- Mitteilungen ----------
                        Section(t("sec_notif")) {
                            listOf(Kind.FAJR, Kind.SUNRISE, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA).forEachIndexed { idx, k ->
                                val opts = if (k.prayer) modeLabels() else modeLabels().take(2)
                                ValueRow(k.label, opts[Prefs.mode(k).coerceIn(0, opts.size - 1)], first = idx == 0) {
                                    choice = Choice(k.label, opts, Prefs.mode(k)) { i ->
                                        Prefs.setMode(k, i)
                                        changed()
                                    }
                                }
                            }
                            val rem = listOf(0, 5, 10, 15, 20, 30, 45, 60)
                            ValueRow(t("reminder_before"), if (Prefs.reminder == 0) t("mode_off") else t("min_short", Prefs.reminder)) {
                                choice = Choice(t("reminder_title"), rem.map { if (it == 0) t("mode_off") else t("minutes", it) }, rem.indexOf(Prefs.reminder)) { i ->
                                    Prefs.reminder = rem[i]
                                    changed()
                                }
                            }
                        }
                    }
                    "sound" -> {
                        Section(t("sec_tone"), footer = t("tone_footer")) {
                            Notifs.TONES.forEachIndexed { i, id ->
                                CheckRow(t("tone_$id"), Prefs.tone == id, first = i == 0) {
                                    player?.release()
                                    player = try {
                                        android.media.MediaPlayer.create(ctx, Notifs.toneUri(ctx, id))?.apply { start() }
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (Prefs.tone != id) {
                                        Prefs.tone = id
                                        Notifs.channels(ctx)
                                        changed()
                                    }
                                }
                            }
                        }

                        // ---------- Adhan ----------
                        Section(t("sec_adhan"), footer = t("adhan_footer")) {
                            ValueRow(t("adhan_file"), Prefs.adhanName ?: t("none_chosen"), first = true) { pickAdhan.launch(arrayOf("audio/*")) }
                            ValueRow(t("fajr_adhan"), Prefs.fajrAdhanName ?: t("same_as_above")) { pickFajr.launch(arrayOf("audio/*")) }
                            if (Prefs.fajrAdhanUri != null) {
                                ButtonRow(t("remove_fajr_adhan")) {
                                    Prefs.fajrAdhanUri = null
                                    Prefs.fajrAdhanName = null
                                }
                            }
                            ButtonRow(t("adhan_preview")) {
                                val u = Prefs.adhanUri
                                if (u != null) AdhanService.start(ctx, u, null)
                            }
                            ButtonRow(t("adhan_stop"), color = Color(0xFFFF3B30)) { AdhanService.stop(ctx) }
                        }
                    }
                    "live" -> {
                        // ---------- Live ----------
                        Section(t("sec_live"), footer = t("live_footer")) {
                            ToggleRow(t("sec_live"), Prefs.live, first = true) { Prefs.live = it; changed() }
                            ToggleRow(t("live_always"), Prefs.liveAlways, sub = t("live_always_sub")) { Prefs.liveAlways = it; changed() }
                            val cm = listOf(t("chip_remaining"), t("chip_clock"))
                            cm.forEachIndexed { i, label ->
                                CheckRow(label, Prefs.chipMode == i, sub = if (i == 0) t("chip_mode") else null) {
                                    Prefs.chipMode = i
                                    changed()
                                }
                            }
                            val lm = listOf(15, 30, 45, 60, 90, 120)
                            if (!Prefs.liveAlways) ValueRow(t("live_start"), t("min_short", Prefs.liveMin)) {
                                choice = Choice(t("live_start"), lm.map { t("minutes", it) }, lm.indexOf(Prefs.liveMin)) { i ->
                                    Prefs.liveMin = lm[i]
                                    changed()
                                }
                            }
                        }
                    }
                    "look" -> LookPage()
                    "calendar" -> {
                        // ---------- Kalender ----------
                        Section(t("sec_calendar"), footer = t("cal_footer")) {
                            val dm = listOf(t("cal_hijri"), t("cal_greg"), t("cal_both"))
                            dm.forEachIndexed { i, label ->
                                CheckRow(label, Prefs.dateMode == i, first = i == 0) {
                                    Prefs.dateMode = i
                                    Widgets.updateAll(ctx)
                                }
                            }
                        }
                    }
                    "widget" -> {
                        // ---------- Widget ----------
                        Section(t("sec_widget"), footer = t("widget_footer")) {
                            ToggleRow(Kind.SUNRISE.label, Prefs.wSunrise, first = true) { Prefs.wSunrise = it; Widgets.updateAll(ctx) }
                            ToggleRow(Kind.MIDNIGHT.label, Prefs.wMidnight) { Prefs.wMidnight = it; Widgets.updateAll(ctx) }
                            ToggleRow(Kind.LAST_THIRD.label, Prefs.wThird) { Prefs.wThird = it; Widgets.updateAll(ctx) }
                        }
                    }
                    "radar" -> {
                        // ---------- Moschee-Radar ----------
                        Section(t("sec_radar"), footer = t("radar_footer")) {
                            val rr = listOf(500, 1000, 1500, 2000, 3000, 5000)
                            ValueRow(t("radar_radius"), Mosques.fmtDist(Prefs.radarRadius.toFloat()), first = true) {
                                choice = Choice(t("radar_radius"), rr.map { Mosques.fmtDist(it.toFloat()) }, rr.indexOf(Prefs.radarRadius)) { i ->
                                    Prefs.radarRadius = rr[i]
                                }
                            }
                            ToggleRow(t("notif_design"), Prefs.notifDesign, sub = t("notif_design_sub")) {
                                Prefs.notifDesign = it
                                changed()
                                if (Prefs.radar) RadarService.start(ctx)
                            }
                        }
                    }
                    "hidden" -> {
                        // ---------- Ausgeblendete Orte ----------
                        val hidden = Prefs.hiddenIds
                        if (hidden.isNotEmpty()) {
                            val all = Mosques.listAll(ctx).filter { it.id in hidden }
                            Section(t("hidden_places"), footer = t("hidden_footer")) {
                                all.forEachIndexed { i, m ->
                                    RowBase(i == 0) {
                                        LabelCol(m.name, m.address.ifBlank { null })
                                        Txt(t("unhide"), 15, FontWeight.SemiBold, Color(0xFF007AFF),
                                            modifier = Modifier.tap { Prefs.hiddenIds = Prefs.hiddenIds - m.id }.padding(6.dp))
                                    }
                                }
                                ButtonRow(t("unhide_all")) { Prefs.hiddenIds = emptySet() }
                            }
                        }
                    }
                    "update" -> {
                        Section(t("sec_update"), footer = t("update_footer")) {
                            val verName = try {
                                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                            } catch (e: Exception) {
                                "?"
                            }
                            RowBase(true) {
                                LabelCol(t("update_installed"))
                                Txt(
                                    if (Updater.currentBuild > 0) t("update_ver", verName, Updater.currentBuild) else verName,
                                    16, color = p.sub, maxLines = 1
                                )
                            }
                            val newer = Updater.isNewer(updRel)
                            val status: String? = when {
                                updPct >= 0 -> t("update_loading", updPct)
                                updBusy -> t("update_checking")
                                updMsg != null -> updMsg
                                newer -> t("update_available", updRel?.build ?: 0)
                                updRel != null -> t("update_latest")
                                else -> null
                            }
                            if (status != null) RowBase(false) { LabelCol(status) }
                            ButtonRow(if (newer) t("update_install") else t("update_check")) {
                                if (updBusy) return@ButtonRow
                                updMsg = null
                                val rel = updRel
                                if (newer && rel != null) {
                                    if (!Updater.canInstall(ctx)) {
                                        updMsg = t("update_allow")
                                        Updater.openInstallPermission(ctx)
                                        return@ButtonRow
                                    }
                                    updBusy = true
                                    updPct = 0
                                    Updater.install(ctx, rel, { updPct = it }) { err ->
                                        updBusy = false
                                        updPct = -1
                                        updMsg = err
                                    }
                                } else {
                                    updBusy = true
                                    Updater.check { r, err ->
                                        updBusy = false
                                        if (r != null) updRel = r
                                        updMsg = err
                                    }
                                }
                            }
                        }
                    }
                    "diag" -> {
                        // ---------- Diagnose ----------
                        Section(t("sec_diag"), footer = t("diag_footer")) {
                            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                            val am = ctx.getSystemService(android.app.AlarmManager::class.java)
                            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
                            val net = try {
                                cm?.getNetworkCapabilities(cm.activeNetwork)
                                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            } catch (e: Exception) {
                                false
                            }
                            val exact = android.os.Build.VERSION.SDK_INT < 31 || (am?.canScheduleExactAlarms() == true)
                            var promotedOk = true
                            val promoted: String = if (android.os.Build.VERSION.SDK_INT >= 36) {
                                try {
                                    val r = android.app.NotificationManager::class.java.getMethod("canPostPromotedNotifications").invoke(nm)
                                    promotedOk = r == true
                                    if (r == true) t("allowed") else t("off")
                                } catch (e: Throwable) {
                                    t("unknown")
                                }
                            } else t("from16")
                            val version = try {
                                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                            } catch (e: Exception) {
                                "?"
                            }
                            val now = System.currentTimeMillis()
                            val nextAlarm = Scheduler.actions(now).firstOrNull { it.at > now + 1000 }
                            Mosques.list(ctx)
                            fun ok(b: Boolean) = if (b) t("ok") else t("missing")
                            val notifOk = nm?.areNotificationsEnabled() == true
                            DiagRow(t("app_version"), version, true, first = true)
                            DiagRow(t("sec_location"), ok(hasLocPermission(ctx)), hasLocPermission(ctx))
                            DiagRow(t("sec_notif"), ok(notifOk), notifOk)
                            DiagRow(t("d_exact"), ok(exact), exact)
                            DiagRow(t("d_live"), promoted, promotedOk)
                            DiagRow(t("d_internet"), if (net) t("online") else t("offline"), net)
                            DiagRow(t("d_osm"), "${Mosques.countOsm()}", Mosques.countOsm() > 0)
                            DiagRow("Mawaqit", "${Mosques.countMawaqit()}", Mosques.countMawaqit() > 0)
                            ToggleRow(t("use_lk"), Prefs.useLandkarte, sub = t("use_lk_sub")) { Prefs.useLandkarte = it; changed() }
                            DiagRow(t("d_own"), "${Mosques.countOwn()}", true)
                            DiagRow(t("d_next"), nextAlarm?.let { "${Fmt.time(it.at)} · ${it.ev?.title ?: t("sec_widget")}" } ?: t("none"), nextAlarm != null)
                            ButtonRow(t("reload_mosques")) {
                                Mosques.refresh(ctx, Prefs.lat, Prefs.lng) { okLoad ->
                                    locStatus = if (okLoad) t("reloaded", Mosques.countOsm())
                                    else if (Mosques.lastError == "offline") t("err_offline") else t("err_busy")
                                }
                                Mosques.refreshMawaqit(ctx, Prefs.lat, Prefs.lng) { }
                            }
                        }

                        // ---------- Testen ----------
                        Section(t("sec_test"), footer = t("test_footer")) {
                            val now = System.currentTimeMillis()
                            val next = Times.around(now).first { it.at > now && it.kind.prayer }
                            ButtonRow(t("t_prayer"), first = true) {
                                val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                                nm?.notify(Notifs.prayerId(next.kind), Notifs.buildPrayer(ctx, next.copy(at = System.currentTimeMillis()), Notifs.prayerChannel()))
                            }
                            ButtonRow(t("t_remind")) {
                                Notifs.reminder(ctx, next.copy(at = System.currentTimeMillis() + 15 * 60_000L))
                            }
                            ButtonRow(t("t_live")) {
                                val tm = System.currentTimeMillis()
                                Notifs.live(ctx, next.copy(at = tm + 2 * 60_000L), prevAt = tm)
                            }
                            ButtonRow(t("t_adhan")) {
                                val u = Prefs.adhanUri
                                if (u != null) AdhanService.start(ctx, u, next.copy(at = System.currentTimeMillis()))
                                else Notifs.prayerAlert(ctx, next.copy(at = System.currentTimeMillis()))
                            }
                            ButtonRow(t("open_notif_settings")) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            ButtonRow(t("rerun_setup")) { Prefs.setupDone = false }
                        }
                    }
                }
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
    if (coords) CoordDialog { coords = false }
}

@Composable
private fun DiagRow(label: String, value: String, good: Boolean, first: Boolean = false) {
    RowBase(first) {
        LabelCol(label)
        Txt(value, 15, FontWeight.SemiBold, if (good) Color(0xFF34C759) else Color(0xFFFF3B30), maxLines = 1)
    }
}

@Composable
private fun CoordDialog(onClose: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    val style = TextStyle(color = p.text, fontSize = 17.sp)

    @Composable
    fun CField(label: String, value: String, number: Boolean, onChange: (String) -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Txt(label, 13, color = p.sub)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.fill)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(p.accentText),
                    keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(p.card)
                .padding(vertical = 14.dp)
        ) {
            Txt(t("coords_title"), 17, FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            CField(t("name"), name, false) { name = it }
            CField(t("lat_hint"), lat, true) { lat = it }
            CField(t("lng_hint"), lng, true) { lng = it }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Txt(t("cancel"), 16, color = p.sub, modifier = Modifier.tap { onClose() }.padding(10.dp))
                Txt(t("save"), 16, FontWeight.SemiBold, p.accentText, modifier = Modifier.tap {
                    val la = lat.replace(',', '.').toDoubleOrNull()
                    val lo = lng.replace(',', '.').toDoubleOrNull()
                    if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                        Prefs.lat = la
                        Prefs.lng = lo
                        Prefs.place = name.ifBlank { t("own_place") }
                        Prefs.autoLoc = false
                        Scheduler.reschedule(ctx)
                        onClose()
                    }
                }.padding(10.dp))
            }
        }
    }
}

// ======================= Übersicht und Unterseiten =======================

/** Linien-Symbole der Übersicht (Raster 24×24, weiß auf farbigem Quadrat). */
private object Sg {
    const val CLOCK = "M12 7v5l3 2M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"
    const val MOON = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
    const val PLUS = "M5 12h14M12 5v14"
    const val BELL = "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 20.5a2 2 0 0 0 4 0"
    const val SPEAKER = "M4 9.5v5h3.5L12 18V6L7.5 9.5zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"
    const val LIVE = "M3.5 12a4.5 4.5 0 0 1 4.5-4.5h8a4.5 4.5 0 0 1 0 9H8A4.5 4.5 0 0 1 3.5 12zM8 12h4"
    const val DESIGN = "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM12 4v16M12 8h3.5M12 12h6M12 16h3.5"
    const val CAL = "M4.5 8a2.5 2.5 0 0 1 2.5-2.5h10A2.5 2.5 0 0 1 19.5 8v9.5A2.5 2.5 0 0 1 17 20H7a2.5 2.5 0 0 1-2.5-2.5zM4.5 10h15M9 3.5v4M15 3.5v4"
    const val WIDGET = "M4.5 4.5h6v6h-6zM13.5 4.5h6v6h-6zM4.5 13.5h15v6h-15z"
    const val RADAR = "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5zM12 3.5a8.5 8.5 0 1 1-8.5 8.5"
    const val HIDE = "M3 3l18 18M10.6 6.1A9.7 9.7 0 0 1 12 6c5 0 9 6 9 6a16 16 0 0 1-2.6 3.2M6.4 7.4C4.2 9 3 12 3 12s4 6 9 6a9 9 0 0 0 4.3-1.1"
    const val DOWNLOAD = "M12 4v10M7.5 10l4.5 4.5 4.5-4.5M5 19.5h14"
    const val WRENCH = "M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17l3 3 5.3-5.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.5-.5-.5-2.5z"
    const val PIN = "M12 21s-6-6.2-6-11a6 6 0 0 1 12 0c0 4.8-6 11-6 11zM12 8a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    const val BACK = "M15 5l-7 7 7 7"
    const val STAR = "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z"
    const val BACK_RTL = "M9 5l7 7-7 7"
}

private fun pageTitle(pg: String): String = when (pg) {
    "loc" -> t("sec_location")
    "calc" -> t("sec_calc")
    "night" -> t("sec_night")
    "offsets" -> t("sec_offsets")
    "prayers" -> t("st_prayers")
    "sound" -> t("st_sound")
    "live" -> t("sec_live")
    "look" -> t("st_look")
    "calendar" -> t("sec_calendar")
    "widget" -> t("sec_widget")
    "radar" -> t("st_radar")
    "hidden" -> t("hidden_places")
    "update" -> t("sec_update")
    "diag" -> t("st_diag")
    else -> ""
}

private val PRAYER_KINDS = listOf(Kind.FAJR, Kind.SUNRISE, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA)

/** Alles, was Gebetsmitteilungen braucht, erlaubt? (für „Alles in Ordnung“ in der Übersicht) */
private fun diagOk(ctx: Context): Boolean {
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
    val am = ctx.getSystemService(android.app.AlarmManager::class.java)
    val exact = android.os.Build.VERSION.SDK_INT < 31 || (am?.canScheduleExactAlarms() == true)
    return nm?.areNotificationsEnabled() == true && exact && hasLocPermission(ctx)
}

/** Runder Zurück-Knopf wie bei iOS. */
@Composable
private fun BackCircle(onBack: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.card).tap(scale = 0.9f, onClick = onBack),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        GlyphIcon(if (Prefs.lang.rtl) Sg.BACK_RTL else Sg.BACK, p.text, 19.dp)
    }
}

/** Unterseite: oben Zurück und Titel mittig, darunter scrollbarer Inhalt. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
            BackCircle(onBack)
            Txt(
                title, 17, FontWeight.SemiBold, maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp),
            content = content
        )
    }
}

/** Zeile der Übersicht: farbiges Symbol, Titel, aktueller Wert darunter, Pfeil. */
@Composable
private fun NavRow(glyph: String, bg: Color, label: String, sub: String?, first: Boolean, onClick: () -> Unit) {
    RowBase(first, onClick, inset = 61.dp) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = androidx.compose.ui.Alignment.Center) {
            GlyphIcon(glyph, Color.White, 19.dp)
        }
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f)) {
            Txt(label, 17, maxLines = 1)
            if (!sub.isNullOrEmpty()) Txt(sub, 13, color = LocalPal.current.sub, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
        }
        RowChevron()
    }
}

@Composable
private fun Overview(onBack: () -> Unit, updNewer: Boolean, open: (String) -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
            BackCircle(onBack)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 28.dp)
        ) {
            Txt(t("tab_settings"), 32, FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 14.dp))

            // Ort und Sprache als große Karte oben
            Box(Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(14.dp)).background(p.card)) {
                Row(
                    Modifier.fillMaxWidth().tap(scale = 0.985f) { open("loc") }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0EA5E9)))),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { GlyphIcon(Sg.PIN, Color.White, 27.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(Prefs.place, 19, FontWeight.SemiBold, maxLines = 1)
                        Txt(
                            (if (Prefs.autoLoc) t("st_auto") else t("st_manual")) + " · " + Prefs.lang.native,
                            14, color = p.sub, maxLines = 1, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RowChevron()
                }
            }

            // Gebetszeiten
            val m = PrayerCalc.method(Prefs.method)
            val offs = PRAYER_KINDS.filter { Prefs.offset(it) != 0 }
            Section(t("grp_times")) {
                NavRow(Sg.CLOCK, Color(0xFF0A84FF), t("sec_calc"),
                    m.label + " · " + Kind.ASR.label + " " + (if (Prefs.asr == 2) t("asr_hanafi") else t("asr_std")), true) { open("calc") }
                NavRow(Sg.MOON, Color(0xFF5E5CE6), t("sec_night"), Kind.MIDNIGHT.label + " · " + Kind.LAST_THIRD.label, false) { open("night") }
                NavRow(Sg.PLUS, Color(0xFF8E8E93), t("sec_offsets"),
                    if (offs.isEmpty()) t("st_offsets_sub")
                    else offs.joinToString(" · ") { k -> k.label + " " + t("min_short", signed(Prefs.offset(k))) }, false) { open("offsets") }
            }

            // Mitteilungen
            val ml = modeLabels()
            val modes = PRAYER_KINDS.filter { it.prayer }.map { Prefs.mode(it) }.distinct()
            val modeText = if (modes.size == 1) ml[modes[0].coerceIn(0, ml.size - 1)] else null
            val remText = t("reminder_before") + " " + (if (Prefs.reminder == 0) t("mode_off") else t("min_short", Prefs.reminder))
            Section(t("sec_notif")) {
                NavRow(Sg.BELL, Color(0xFFFF3B30), t("st_prayers"), listOfNotNull(modeText, remText).joinToString(" · "), true) { open("prayers") }
                NavRow(Sg.SPEAKER, Color(0xFFFF9500), t("st_sound"),
                    t("tone_${Prefs.tone}") + " · " + (Prefs.adhanName ?: t("none_chosen")), false) { open("sound") }
                NavRow(Sg.LIVE, Color(0xFF34C759), t("sec_live"),
                    if (!Prefs.live) t("mode_off")
                    else if (Prefs.liveAlways) t("st_on") + " · " + t("live_always")
                    else t("st_on") + " · " + t("min_short", Prefs.liveMin), false) { open("live") }
            }

            // Darstellung
            val themes = listOf(t("d_auto"), t("d_light"), t("d_dark"))
            val skies = listOf(t("sky_off"), t("sky_eco"), t("sky_full"))
            val dm = listOf(t("cal_hijri"), t("cal_greg"), t("cal_both"))
            val wl = listOfNotNull(
                if (Prefs.wSunrise) Kind.SUNRISE.label else null,
                if (Prefs.wMidnight) Kind.MIDNIGHT.label else null,
                if (Prefs.wThird) Kind.LAST_THIRD.label else null
            )
            Section(t("sec_display")) {
                NavRow(Sg.DESIGN, Color(0xFF3A3A3C), t("st_look"),
                    themes[Prefs.theme.coerceIn(0, 2)] + " · " + skies[Prefs.skyQuality.coerceIn(0, 2)], true) { open("look") }
                NavRow(Sg.CAL, Color(0xFFFF2D55), t("sec_calendar"), dm[Prefs.dateMode.coerceIn(0, 2)], false) { open("calendar") }
                NavRow(Sg.WIDGET, Color(0xFF30B0C7), t("sec_widget"), if (wl.isEmpty()) t("mode_off") else wl.joinToString(", "), false) { open("widget") }
            }

            // Moscheen
            val hiddenN = Prefs.hiddenIds.size
            Section(t("grp_mosques")) {
                NavRow(Sg.RADAR, Color(0xFF13917C), t("st_radar"), Mosques.fmtDist(Prefs.radarRadius.toFloat()), true) { open("radar") }
                // Karte „Meine Moschee“ auf „Heute“ ein-/ausblenden – direkt hier umschaltbar
                RowBase(false, inset = 61.dp) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF34C759)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        GlyphIcon(Sg.STAR, Color.White, 19.dp)
                    }
                    Spacer(Modifier.width(3.dp))
                    Txt(t("show_fav"), 17, maxLines = 2, modifier = Modifier.weight(1f))
                    IosSwitch(Prefs.showFav) { Prefs.showFav = it }
                }
                if (hiddenN > 0) NavRow(Sg.HIDE, Color(0xFF8E8E93), t("hidden_places"), "$hiddenN", false) { open("hidden") }
            }

            // Weiteres
            val verName = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
            } catch (e: Exception) {
                "?"
            }
            Section(t("grp_more")) {
                NavRow(Sg.DOWNLOAD, Color(0xFF636366), t("sec_update"),
                    verName + " · " + (if (updNewer) t("st_new") else t("st_current")), true) { open("update") }
                NavRow(Sg.WRENCH, Color(0xFF48484A), t("st_diag"), if (diagOk(ctx)) t("st_all_ok") else t("st_check"), false) { open("diag") }
            }
        }
    }
}

/** Ganz breite Auswahl in eigener Karte (Design, Himmel). */
@Composable
private fun FullSeg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(p.fill).padding(2.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).height(34.dp)
                        .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (on) p.seg else Color.Transparent)
                        .tap(scale = 0.97f) { onSelect(i) },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Txt(o, 14, if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun LookPage() {
    val ctx = LocalContext.current
    Section(t("design")) {
        FullSeg(listOf(t("d_auto"), t("d_light"), t("d_dark")), Prefs.theme) {
            Prefs.theme = it
            Widgets.updateAll(ctx)
        }
    }
    Section(t("sky_q")) {
        FullSeg(listOf(t("sky_off"), t("sky_eco"), t("sky_full")), Prefs.skyQuality) {
            Prefs.skyQuality = it
            SkyQuality.level = it
        }
    }
    Section(t("sec_display")) {
        ToggleRow(t("h24"), Prefs.use24, first = true) { Prefs.use24 = it; Widgets.updateAll(ctx) }
        StepperRow(t("hijri_date"), t("days", signed(Prefs.hijriAdj)),
            onMinus = { Prefs.hijriAdj = (Prefs.hijriAdj - 1).coerceAtLeast(-2); Widgets.updateAll(ctx) },
            onPlus = { Prefs.hijriAdj = (Prefs.hijriAdj + 1).coerceAtMost(2); Widgets.updateAll(ctx) })
    }
}
