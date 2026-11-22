package com.gebetszeiten.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Schlichte, gefüllte Symbole im Stil von Apples SF Symbols (Raster 24×24). */
object Glyph {
    const val CAR = "M6.2 5.8A2.5 2.5 0 0 1 8.6 4h6.8a2.5 2.5 0 0 1 2.4 1.8L19 10h.5A1.5 1.5 0 0 1 21 11.5V17a1 1 0 0 1-1 1h-1v1.5a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1V18H8v1.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V18H4a1 1 0 0 1-1-1v-5.5A1.5 1.5 0 0 1 4.5 10H5z"
    const val CAR_WINDOW = "M7.4 10l.95-3.1a1 1 0 0 1 .96-.7h5.38a1 1 0 0 1 .96.7l.95 3.1z"
    const val SHARE = "M12 3v12M8 7l4-4 4 4M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7"
    const val COPY = "M9 9h11v11H9zM5 15H4V4h11v1"
    const val SEARCH = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13zM15.5 15.5L20 20"
    const val ROUTE = "M3 11l18-8-8 18-2-8z"
    // Karten-Symbole (24er Raster): Moschee M10, Gebetsraum G4, Wudu W2
    const val MOSQUE = "M12 3c1 2 6 4 6 9H6c0-5 5-7 6-9zM4 12h16v9H4zM1.5 8h2v13h-2zM1.5 8l1-4 1 4z"
    const val PRAYER_LINE = "M4 22V10a8 8 0 0 1 16 0v12"
    const val PRAYER_FILL = "M9 22v-7a3 3 0 0 1 6 0v7z"
    const val WUDU = "M4 8h9a3 3 0 0 1 3 3v2h-3v-2H4zM6 5h4v3H6zM14.5 16c1.4 2 2.2 3.2 2.2 4.2a2.2 2.2 0 0 1-4.4 0c0-1 0.8-2.2 2.2-4.2z"
    const val PIN = "M12 21s-6-6.2-6-11a6 6 0 0 1 12 0c0 4.8-6 11-6 11zM12 8.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z"
    // Wetter (Kopf der Startseite)
    const val WX_SUN = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
    const val WX_MOON = "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"
    const val WX_CLOUD = "M7 18h10.5a4 4 0 0 0 .4-7.98A6 6 0 0 0 6.4 9.1A4.5 4.5 0 0 0 7 18z"
    const val WX_RAIN = "M7 14h10.5a3.5 3.5 0 0 0 .4-6.98A5.5 5.5 0 0 0 6.4 6.1A4 4 0 0 0 7 14zM8.5 17l-1 3M12.5 17l-1 3M16.5 17l-1 3"
    const val WX_SNOW = "M7 14h10.5a3.5 3.5 0 0 0 .4-6.98A5.5 5.5 0 0 0 6.4 6.1A4 4 0 0 0 7 14zM8 18h.01M12 20h.01M16 18h.01"
    const val WX_THUNDER = "M7 14h10.5a3.5 3.5 0 0 0 .4-6.98A5.5 5.5 0 0 0 6.4 6.1A4 4 0 0 0 7 14zM13 14l-3 4h4l-3 4"
    const val WX_FOG = "M4 8h16M3 12h18M6 16h12M8 20h8"
    const val CHEV_L = "M15 5l-7 7 7 7"
    const val CHEV_R = "M9 5l7 7-7 7"
    const val CLOSE = "M5 5l14 14M19 5L5 19"
    const val RESET = "M4 12a8 8 0 1 0 2.6-5.9M4 4v5h5"
    const val BELL = "M6 17V11a6 6 0 0 1 12 0v6l2 2H4zM10 21h4"
    const val WALK = "M12.4 7.2c.9-.3 1.9.1 2.3 1l1.3 2.7 2.4 1.1a1.1 1.1 0 0 1-.9 2l-2.8-1.3a1.6 1.6 0 0 1-.8-.8l-.5-1-1 4 2.4 2.6c.2.2.3.5.3.8v3.5a1.1 1.1 0 0 1-2.2 0v-3.1l-2.5-2.6-1.1 4.2a1.1 1.1 0 0 1-2.1-.5l2.2-8.8-1.6.9-.9 2.6a1.1 1.1 0 0 1-2-.7l1-3a1.6 1.6 0 0 1 .7-.9z"
}

/** Auto (mode 0) oder zu Fuß (mode 1). [cut] = Farbe für Scheibe/Lichter (meist Hintergrund). */
@Composable
fun TravelIcon(mode: Int, color: Color, cut: Color, size: Dp = 18.dp) {
    val car = remember { PathParser().parsePathString(Glyph.CAR).toPath() }
    val win = remember { PathParser().parsePathString(Glyph.CAR_WINDOW).toPath() }
    val walk = remember { PathParser().parsePathString(Glyph.WALK).toPath() }
    Canvas(Modifier.size(size)) {
        val f = this.size.width / 24f
        scale(f, f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (mode == 0) {
                drawPath(car, color)
                drawPath(win, cut)
                drawCircle(cut, 1.3f, androidx.compose.ui.geometry.Offset(6.8f, 13.6f))
                drawCircle(cut, 1.3f, androidx.compose.ui.geometry.Offset(17.2f, 13.6f))
            } else {
                drawCircle(color, 2.2f, androidx.compose.ui.geometry.Offset(13.6f, 3.9f))
                drawPath(walk, color)
            }
        }
    }
}


/** Linien-Symbol aus einem SVG-Pfad (Raster 24×24). */
@Composable
fun GlyphIcon(path: String, color: Color, size: Dp = 20.dp) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(Modifier.size(size)) {
        val f = this.size.width / 24f
        scale(f, f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                p, color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}


/** Symbol je Art: Moschee (M10), Gebetsraum (G4), Wudu (W2) – weiß auf farbigem Kreis. */
@Composable
fun KindIcon(category: String, size: Dp = 34.dp) {
    val (bg, kind) = when (category) {
        Places.CAT_WUDU -> Color(0xFF0A84FF) to 2
        Places.CAT_PRAYER -> Color(0xFF5E5CE6) to 1
        "Moschee" -> Color(0xFF13917C) to 0
        else -> Color(0xFF5E5CE6) to 1   // Gebetsraum / Verein
    }
    val mosque = remember { PathParser().parsePathString(Glyph.MOSQUE).toPath() }
    val pLine = remember { PathParser().parsePathString(Glyph.PRAYER_LINE).toPath() }
    val pFill = remember { PathParser().parsePathString(Glyph.PRAYER_FILL).toPath() }
    val wudu = remember { PathParser().parsePathString(Glyph.WUDU).toPath() }
    Canvas(Modifier.size(size)) {
        drawCircle(bg)
        val g = this.size.width * 0.58f
        val off = (this.size.width - g) / 2f
        translate(off, off) {
            scale(g / 24f, g / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                when (kind) {
                    0 -> drawPath(mosque, Color.White)
                    1 -> {
                        drawPath(pLine, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
                        drawPath(pFill, Color.White)
                    }
                    else -> drawPath(wudu, Color.White)
                }
            }
        }
    }
}
