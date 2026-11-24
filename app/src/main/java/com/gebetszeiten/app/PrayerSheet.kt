package com.gebetszeiten.app

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Symbole für das Gebets-Blatt. */
private object Pg {
    const val DAWN = "M3 18h18M7 18a5 5 0 0 1 10 0M12 5v4M5.6 10.6l1.4 1.4M18.4 10.6L17 12M2 14.5h2M20 14.5h2"
    const val DUSK = "M3 18h18M7 18a5 5 0 0 1 10 0M12 4v5M9.5 6.5L12 9l2.5-2.5"
    const val OFF = "M3 3l18 18M17 17H4a5 5 0 0 0 2-4V10a6 6 0 0 1 9.4-4.9M18 10v3a5 5 0 0 0 2 4M10 20a2 2 0 0 0 4 0"
    const val BELL = "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M10 21a2 2 0 0 0 4 0"
    const val SPEAKER = "M11 5L6 9H3v6h3l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"
    const val NOTE = "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0a3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0a3 3 0 0 1 6 0z"
    const val CLOCK = "M12 7v5l3 2M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"
    const val PLUSMINUS = "M12 5v14M5 12h14"
    const val PLAY = "M7 4l13 8-13 8z"
    const val STOP = "M6 6h12v12H6z"
    const val NEXT = "M9 5l7 7-7 7"
    const val CHECK = "M5 12l5 5 9-10"
    const val MINUS = "M6 12h12"
    const val PLUS = "M12 6v12M6 12h12"
}

private val OFFSET_KINDS = setOf(Kind.FAJR, Kind.SUNRISE, Kind.DHUHR, Kind.ASR, Kind.MAGHRIB, Kind.ISHA)
private val REMINDERS = listOf(0, 5, 10, 15, 20, 30, 45, 60)

private fun kindGlyph(k: Kind): String = when (k) {
    Kind.FAJR, Kind.SUNRISE -> Pg.DAWN
    Kind.DHUHR, Kind.ASR -> Glyph.WX_SUN
    Kind.MAGHRIB -> Pg.DUSK
    else -> Glyph.WX_MOON
}

/**
 * Blatt beim Antippen eines Gebets: gleitet weich von unten hoch, der Hintergrund dunkelt ab.
 * Aus / Ton / Adhan für dieses Gebet, welcher Ton oder Adhan, Erinnerung vorher (für alle),
 * Zeit korrigieren und Probehören. Nach unten wischen oder daneben tippen schließt es.
 */
@Composable
internal fun PrayerSheet(e: Ev, now: Long, nextAt: Long?, onClose: () -> Unit, onChanged: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = p.accentText
    val blue = Color(0xFF0A84FF)

    // 1 = ganz unten (versteckt), 0 = offen
    val off = remember { Animatable(1f) }
    var sheetH by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            off.animateTo(1f, tween(230, easing = FastOutLinearInEasing))
            onClose()
        }
    }
    LaunchedEffect(Unit) { off.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f)) }

    var mode by remember(e.kind) { mutableIntStateOf(Prefs.mode(e.kind)) }
    var offset by remember(e.kind) { mutableIntStateOf(Prefs.offset(e.kind)) }
    // Erinnerung: entweder wie bei allen Gebeten oder nur für dieses (bei Sonnenaufgang, Mitternacht usw. immer nur dieses)
    var onlyThis by remember(e.kind) { mutableStateOf(!e.kind.prayer || Prefs.reminderOwn(e.kind) >= 0) }
    var reminder by remember(e.kind) { mutableIntStateOf(Prefs.reminderFor(e.kind)) }
    var tone by remember { mutableStateOf(Prefs.tone) }
    var adhanName by remember(e.kind) {
        mutableStateOf(if (e.kind == Kind.FAJR) Prefs.fajrAdhanName ?: Prefs.adhanName else Prefs.adhanName)
    }
    var openTone by remember { mutableStateOf(false) }
    var openRem by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAll() {
        player?.release()
        player = null
        if (playing && mode == 2) AdhanService.stop(ctx)
        playing = false
    }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            if (playing) AdhanService.stop(ctx)
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keep(ctx, uri)
            val name = displayName(ctx, uri)
            if (e.kind == Kind.FAJR) {
                Prefs.fajrAdhanUri = uri.toString()
                Prefs.fajrAdhanName = name
            } else {
                Prefs.adhanUri = uri.toString()
                Prefs.adhanName = name
            }
            adhanName = name
        }
    }

    fun changed() {
        Scheduler.reschedule(ctx)
        Widgets.updateAll(ctx)
        onChanged()
    }

    // Gebete: Aus · Ton · Adhan. Sonnenaufgang, Mitternacht, Letztes Drittel: Aus · Ton
    val maxMode = if (e.kind.prayer) 2 else 1

    // liegt über der ganzen App (SheetHost) – deckt Statusleiste und Tab-Leiste mit ab
    androidx.activity.compose.BackHandler { close() }
    run {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = (1f - off.value).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { close() } }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { sheetH = it.height.toFloat() }
                    .graphicsLayer { translationY = off.value * (if (sheetH > 0f) sheetH else 3000f) }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(p.card)
                    .pointerInput(Unit) { detectTapGestures { } }  // Tipps im Blatt schließen es nicht
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d ->
                            if (sheetH > 0f) scope.launch { off.snapTo((off.value + d / sheetH).coerceIn(0f, 1f)) }
                        },
                        onDragStopped = { v ->
                            if (v > 900f || off.value > 0.28f) close()
                            else off.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f))
                        }
                    )
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp)
                    .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 520f)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(p.line))

                // Kopf
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        GlyphIcon(kindGlyph(e.kind), accent, 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(e.title + " · " + Fmt.time(e.at), 21, FontWeight.Bold, maxLines = 1)
                        if (nextAt != null && nextAt > now) {
                            Txt(t("ps_next", WidgetPainter.cd(nextAt - now)), 13, color = p.sub, maxLines = 1)
                        }
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.fill).tap(scale = 0.88f) { close() },
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(Glyph.CLOSE, p.sub, 13.dp) }
                }

                // Aus · Ton · Adhan
                if (maxMode >= 1) {
                    val opts = listOf(Triple(Pg.OFF, t("mode_off"), 0), Triple(Pg.BELL, t("mode_tone"), 1), Triple(Pg.SPEAKER, t("mode_adhan"), 2))
                        .take(maxMode + 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        opts.forEach { (g, label, i) ->
                            val on = mode == i
                            val shape = RoundedCornerShape(16.dp)
                            Column(
                                Modifier.weight(1f).height(78.dp).clip(shape)
                                    .background(if (on) accent.copy(alpha = 0.16f) else p.bg)
                                    .then(if (on) Modifier.border(2.dp, accent, shape) else Modifier)
                                    .tap(scale = 0.95f) {
                                        if (mode != i) {
                                            stopAll()
                                            mode = i
                                            Prefs.setMode(e.kind, i)
                                            changed()
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                GlyphIcon(g, if (on) accent else p.sub, 22.dp)
                                Spacer(Modifier.height(7.dp))
                                Txt(label, 13, FontWeight.SemiBold, if (on) p.text else p.sub, maxLines = 1, align = TextAlign.Center)
                            }
                        }
                    }
                }

                // Einstellungen
                val showOffset = e.kind in OFFSET_KINDS
                val showRem = mode >= 1
                if (mode >= 1 || showOffset) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.bg)) {
                        var first = true
                        if (mode == 2) {
                            if (!first) Sep()
                            first = false
                            SheetRow(Pg.NOTE, accent, t("mode_adhan"), adhanName ?: t("none_chosen"), chevron = true) {
                                stopAll()
                                pick.launch(arrayOf("audio/*"))
                            }
                        }
                        if (mode == 1) {
                            if (!first) Sep()
                            first = false
                            SheetRow(Pg.NOTE, accent, t("mode_tone"), t("tone_$tone"), chevron = true, open = openTone) { openTone = !openTone }
                            if (openTone) {
                                Notifs.TONES.forEach { id ->
                                    Row(
                                        Modifier.fillMaxWidth().tap(scale = 0.99f) {
                                            player?.release()
                                            player = try {
                                                MediaPlayer.create(ctx, Notifs.toneUri(ctx, id))?.apply { start() }
                                            } catch (ex: Exception) {
                                                null
                                            }
                                            if (tone != id) {
                                                tone = id
                                                Prefs.tone = id
                                                Notifs.channels(ctx)
                                                changed()
                                            }
                                        }.padding(start = 54.dp, end = 14.dp).height(42.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Txt(t("tone_$id"), 15, if (tone == id) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
                                        if (tone == id) GlyphIcon(Pg.CHECK, accent, 16.dp)
                                    }
                                }
                            }
                        }
                        if (showRem) {
                            if (!first) Sep()
                            first = false
                            SheetRow(
                                Pg.CLOCK, Color(0xFFFF9F0A), t("reminder_before"),
                                (if (reminder == 0) t("mode_off") else t("min_short", reminder)) + " · " +
                                    (if (onlyThis) t("ps_only", e.title) else t("ps_all")),
                                chevron = true, open = openRem
                            ) { openRem = !openRem }
                            if (openRem) {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                        .padding(start = 54.dp, end = 14.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    REMINDERS.forEach { r ->
                                        val on = reminder == r
                                        Box(
                                            Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                                                .background(if (on) accent else p.card)
                                                .tap(scale = 0.94f) {
                                                    reminder = r
                                                    if (onlyThis) Prefs.setReminderOwn(e.kind, r) else Prefs.reminder = r
                                                    changed()
                                                }.padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Txt(
                                                if (r == 0) t("mode_off") else t("min_short", r), 14, FontWeight.SemiBold,
                                                if (on) Color.White else p.text, maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (e.kind.prayer) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 54.dp, end = 14.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Txt(t("ps_only_long", e.title), 15, modifier = Modifier.weight(1f), maxLines = 2)
                                        Spacer(Modifier.width(10.dp))
                                        IosSwitch(onlyThis) { on ->
                                            onlyThis = on
                                            if (on) {
                                                Prefs.setReminderOwn(e.kind, reminder)
                                            } else {
                                                Prefs.setReminderOwn(e.kind, -1)
                                                reminder = Prefs.reminder
                                            }
                                            changed()
                                        }
                                    }
                                }
                            }
                        }
                        if (showOffset) {
                            if (!first) Sep()
                            first = false
                            Row(
                                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RowIcon(Pg.PLUSMINUS, blue)
                                Spacer(Modifier.width(12.dp))
                                Txt(t("ps_offset"), 15, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                                StepBtn(Pg.MINUS, offset > -60) {
                                    offset -= 1
                                    Prefs.setOffset(e.kind, offset)
                                    changed()
                                }
                                Txt(
                                    t("min_short", if (offset > 0) "+$offset" else "$offset"), 15, FontWeight.SemiBold,
                                    modifier = Modifier.width(64.dp), align = TextAlign.Center, maxLines = 1
                                )
                                StepBtn(Pg.PLUS, offset < 60) {
                                    offset += 1
                                    Prefs.setOffset(e.kind, offset)
                                    changed()
                                }
                            }
                        }
                    }
                }

                // Probehören
                if (mode >= 1) {
                    val uri = if (e.kind == Kind.FAJR) Prefs.fajrAdhanUri ?: Prefs.adhanUri else Prefs.adhanUri
                    val can = mode == 1 || uri != null
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(blue.copy(alpha = if (can) 0.14f else 0.06f))
                            .tap(enabled = can, scale = 0.97f) {
                                if (playing) {
                                    stopAll()
                                } else if (mode == 2 && uri != null) {
                                    playing = AdhanService.start(ctx, uri, null)
                                } else if (mode == 1) {
                                    player?.release()
                                    player = try {
                                        MediaPlayer.create(ctx, Notifs.toneUri(ctx, tone))?.apply {
                                            setOnCompletionListener { playing = false }
                                            start()
                                        }
                                    } catch (ex: Exception) {
                                        null
                                    }
                                    playing = player != null
                                }
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphIcon(if (playing) Pg.STOP else Pg.PLAY, blue.copy(alpha = if (can) 1f else 0.4f), 15.dp)
                        Spacer(Modifier.width(8.dp))
                        Txt(
                            if (playing) t("ps_stop") else if (mode == 2) t("ps_listen_adhan") else t("ps_listen_tone"),
                            15, FontWeight.SemiBold, blue.copy(alpha = if (can) 1f else 0.4f), maxLines = 1
                        )
                    }
                }

                if (maxMode >= 1) {
                    Txt(
                        if (e.kind.prayer) t("ps_note2", e.title) else t("ps_note_other", e.title),
                        12, color = p.sub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Sep() {
    Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(LocalPal.current.line))
}

@Composable
private fun RowIcon(glyph: String, c: Color) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, c, 16.dp)
    }
}

/** Zeile im Blatt: Symbol, Bezeichnung, Wert, Pfeil (dreht sich beim Aufklappen). */
@Composable
private fun SheetRow(glyph: String, c: Color, label: String, value: String, chevron: Boolean, open: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "rowChev")
    Row(
        Modifier.fillMaxWidth().height(52.dp).tap(scale = 0.99f, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(glyph, c)
        Spacer(Modifier.width(12.dp))
        Txt(label, 15, FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Txt(value, 15, color = p.sub, modifier = Modifier.weight(1f), align = TextAlign.End, maxLines = 1)
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.graphicsLayer { rotationZ = rot }) { GlyphIcon(Pg.NEXT, p.sub, 13.dp) }
        }
    }
}

@Composable
private fun StepBtn(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(p.fill).tap(enabled = enabled, scale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(glyph, p.text.copy(alpha = if (enabled) 1f else 0.3f), 14.dp) }
}
