package com.gebetszeiten.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Apple-artiger Tipp-Effekt: kein eckiger grauer Kasten, sondern kurz kleiner und blasser.
 */
fun Modifier.tap(enabled: Boolean = true, scale: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) scale else 1f, label = "tapScale")
    val al by animateFloatAsState(if (pressed) 0.6f else 1f, label = "tapAlpha")
    this
        .graphicsLayer {
            scaleX = sc
            scaleY = sc
            alpha = al
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

data class Pal(
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val accentText: Color,
    val hl: Color,
    val fill: Color,
    val seg: Color,
    val dark: Boolean
)

val LightPal = Pal(
    bg = Color(0xFFF2F2F7), card = Color.White, text = Color(0xFF1C1C1E), sub = Color(0xFF6C6C70),
    line = Color(0xFFE5E5EA), accent = Color(0xFF0B6E5F), accentText = Color(0xFF0B6E5F),
    hl = Color(0x170B6E5F), fill = Color(0xFFE9E9EB), seg = Color.White, dark = false
)
val DarkPal = Pal(
    bg = Color.Black, card = Color(0xFF1C1C1E), text = Color.White, sub = Color(0xFFAEAEB2),
    line = Color(0xFF38383A), accent = Color(0xFF0B7A68), accentText = Color(0xFF4FD6BC),
    hl = Color(0x244FD6BC), fill = Color(0xFF39393D), seg = Color(0xFF636366), dark = true
)
val LocalPal = staticCompositionLocalOf { LightPal }

/**
 * Platz für Blätter über der ganzen App (über Tab-Leiste und Statusleiste), ohne eigenes Fenster –
 * so stimmen Ränder, Abdunkeln und Abstände zur Navigationsleiste immer.
 */
object SheetHost {
    var content: (@androidx.compose.runtime.Composable () -> Unit)? by androidx.compose.runtime.mutableStateOf(null)
}

/** Statusfarben wie bei Apple: auf hellem Grund dunkler, auf dunklem Grund heller – immer gut lesbar. */
fun okColor(p: Pal) = if (p.dark) Color(0xFF30D158) else Color(0xFF248A3D)
fun badColor(p: Pal) = if (p.dark) Color(0xFFFF6961) else Color(0xFFD70015)

@Composable
fun Txt(
    text: String,
    size: Int = 16,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPal.current.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        textAlign = align,
        maxLines = maxLines,
        // abgeschnittener Text endet mit „…“ statt mitten im Buchstaben
        overflow = if (maxLines == Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        lineHeight = (size * 1.25).sp
    )
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        content = content
    )
}

@Composable
fun LargeTitle(text: String) {
    Txt(text, 34, FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
}

@Composable
fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        if (title.isNotEmpty()) {
            Text(
                title.uppercase(), color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card),
            content = content
        )
        if (footer != null) {
            Txt(footer, 13, color = p.sub, modifier = Modifier.padding(start = 16.dp, top = 7.dp, end = 16.dp))
        }
    }
}

@Composable
fun RowBase(first: Boolean, onClick: (() -> Unit)? = null, inset: Dp = 16.dp, content: @Composable RowScope.() -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth()) {
        // Trennlinie beginnt bündig mit dem Text (bei Symbolen erst nach dem Symbol)
        if (!first) Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(p.line))
        val m = Modifier.fillMaxWidth()
        Row(
            (if (onClick != null) m.tap(scale = 0.985f, onClick = onClick) else m)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun RowScope.LabelCol(label: String, sub: String? = null) {
    Column(Modifier.weight(1f)) {
        Txt(label, 17, FontWeight.Normal)
        if (!sub.isNullOrEmpty()) Txt(sub, 13, color = LocalPal.current.sub, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    val off by animateDpAsState(if (checked) 22.dp else 2.dp, label = "knopf")
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Color(0xFF34C759) else p.fill)
            .tap(scale = 0.94f) { onChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = off, top = 2.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, first: Boolean = false, sub: String? = null, onChange: (Boolean) -> Unit) {
    RowBase(first) {
        LabelCol(label, sub)
        IosSwitch(checked, onChange)
    }
}

@Composable
fun ValueRow(label: String, value: String, first: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    RowBase(first, onClick) {
        LabelCol(label, sub)
        Txt(value, 16, color = p.sub, maxLines = 1, modifier = Modifier.widthIn(max = 170.dp))
        RowChevron()
    }
}

@Composable
fun ButtonRow(label: String, first: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    RowBase(first, onClick) {
        Txt(label, 17, color = color ?: LocalPal.current.accentText)
    }
}

@Composable
fun StepperRow(label: String, value: String, first: Boolean = false, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPal.current
    RowBase(first) {
        LabelCol(label)
        Txt(value, 16, color = p.sub, align = TextAlign.End, maxLines = 1, modifier = Modifier.width(70.dp))
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(p.fill)) {
            Box(Modifier.size(46.dp, 32.dp).tap(onClick = onMinus), contentAlignment = Alignment.Center) {
                Txt("−", 20)
            }
            Box(Modifier.width(1.dp).height(32.dp).padding(vertical = 7.dp).background(p.sub.copy(alpha = 0.3f)))
            Box(Modifier.size(46.dp, 32.dp).tap(onClick = onPlus), contentAlignment = Alignment.Center) {
                Txt("+", 20)
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(p.fill).padding(2.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (i == selected) p.seg else Color.Transparent)
                    .tap { onSelect(i) }
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Txt(o, 14, if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun SegRow(label: String, options: List<String>, selected: Int, first: Boolean = false, onSelect: (Int) -> Unit) {
    RowBase(first) {
        LabelCol(label)
        Segmented(options, selected, onSelect)
    }
}

/** Grüner Haken wie bei Apple. */
@Composable
fun GreenCheck(visible: Boolean = true, color: Color = Color(0xFF34C759)) {
    Canvas(Modifier.size(20.dp)) {
        if (!visible) return@Canvas
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.17f, w * 0.52f)
            lineTo(w * 0.38f, w * 0.73f)
            lineTo(w * 0.83f, w * 0.27f)
        }
        drawPath(path, color, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, first: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    RowBase(first, onClick) {
        LabelCol(label, sub)
        GreenCheck(checked, accentBlue(LocalPal.current))
    }
}

data class Choice(val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit)

@Composable
fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) {
    val p = LocalPal.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(p.card)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Txt(c.title, 17, FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            c.options.forEachIndexed { i, o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap(scale = 0.985f) { c.onPick(i); onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(o, 16, modifier = Modifier.weight(1f))
                    GreenCheck(i == c.selected, accentBlue(p))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}


/** Systemblau wie bei iOS, im Dunkeln etwas heller. */
fun accentBlue(p: Pal) = if (p.dark) Color(0xFF0A84FF) else Color(0xFF007AFF)

/** Kleiner grauer Pfeil am Zeilenende (bei Rechts-nach-links-Sprachen gespiegelt). */
@Composable
fun RowChevron() {
    val c = if (LocalPal.current.dark) Color(0xFF5A5A5E) else Color(0xFFC4C4C7)
    val rtl = Prefs.lang.rtl
    Canvas(Modifier.size(9.dp, 15.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (rtl) { moveTo(w * 0.83f, h * 0.1f); lineTo(w * 0.17f, h * 0.5f); lineTo(w * 0.83f, h * 0.9f) }
            else { moveTo(w * 0.17f, h * 0.1f); lineTo(w * 0.83f, h * 0.5f); lineTo(w * 0.17f, h * 0.9f) }
        }
        drawPath(path, c, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
