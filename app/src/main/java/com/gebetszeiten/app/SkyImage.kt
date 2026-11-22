package com.gebetszeiten.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Himmel für die Widgets – gleicher Shader wie „Heute“, mit den Einstellungen der Widget-Vorschau:
 * echter Sonnenstand und echtes Wetter, Himmel bis zum unteren Rand (kein dunkler Balken),
 * ohne Sonnenscheibe und ohne Mond (in kleinen Widgets wären das nur störende Punkte).
 */
internal object SkyImage {

    /** Himmel als Bild in der gewünschten Größe (Pixel). */
    fun sky(ctx: Context, w: Int, h: Int, now: Long): Bitmap {
        val st = skyState(now, Weather.current(ctx))
        return shaderBitmap(w, h, st, now) ?: gradientBitmap(w, h, st)
    }

    private fun shaderBitmap(w: Int, h: Int, st: SkyState, now: Long): Bitmap? {
        if (Build.VERSION.SDK_INT < 33) return null
        var reader: ImageReader? = null
        var hr: android.graphics.HardwareRenderer? = null
        return try {
            val shader = SkyShader.create() ?: return null
            // halbe Auflösung reicht – Wolken sind weich; danach weich hochskalieren
            val rw = (w / 2).coerceAtLeast(1)
            val rh = (h / 2).coerceAtLeast(1)
            shader.setFloatUniform("uRes", rw.toFloat(), rh.toFloat())
            shader.setFloatUniform("uTime", ((now / 60_000L) % 1440L) * 0.8f)
            shader.setFloatUniform("uTop", st.top[0], st.top[1], st.top[2])
            shader.setFloatUniform("uMid", st.mid[0], st.mid[1], st.mid[2])
            shader.setFloatUniform("uHor", st.hor[0], st.hor[1], st.hor[2])
            shader.setFloatUniform("uSun", st.sunX, st.sunY)
            shader.setFloatUniform("uSunVis", st.sunVis)
            shader.setFloatUniform("uSunCol", st.sunCol[0], st.sunCol[1], st.sunCol[2])
            shader.setFloatUniform("uSunLow", st.sunLow)
            shader.setFloatUniform("uMoon", st.moonX, st.moonY)
            shader.setFloatUniform("uMoonVis", 0f)
            shader.setFloatUniform("uPhase", st.phase)
            shader.setFloatUniform("uStars", st.stars)
            shader.setFloatUniform("uCloud", st.cloud)
            shader.setFloatUniform("uRain", st.rain)
            shader.setFloatUniform("uSnow", st.snow)
            shader.setFloatUniform("uFog", st.fog)
            shader.setFloatUniform("uFlash", if (st.thunder) 0.22f else 0f)
            // Horizont unter dem unteren Rand: Himmel und Wolken reichen bis ganz unten
            shader.setFloatUniform("uHorizon", -0.06f)
            shader.setFloatUniform("uFlashPos", 0.62f, 0.66f)
            shader.setFloatUniform("uWind", st.wind)
            shader.setFloatUniform("uCloudLit", st.lit[0], st.lit[1], st.lit[2])
            shader.setFloatUniform("uCloudShade", st.shade[0], st.shade[1], st.shade[2])
            shader.setFloatUniform("uSteps", 30f)
            shader.setFloatUniform("uLight", 1f)
            shader.setFloatUniform("uDisc", 0f)
            val paint = Paint()
            paint.shader = shader
            val rd = ImageReader.newInstance(
                rw, rh, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            )
            reader = rd
            val renderer = android.graphics.HardwareRenderer()
            hr = renderer
            renderer.setSurface(rd.surface)
            val node = RenderNode("hadafSky")
            node.setPosition(0, 0, rw, rh)
            val rc = node.beginRecording()
            rc.drawRect(0f, 0f, rw.toFloat(), rh.toFloat(), paint)
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val img = rd.acquireLatestImage() ?: return null
            val hb = img.hardwareBuffer
            if (hb == null) {
                img.close()
                return null
            }
            val hwBmp = Bitmap.wrapHardwareBuffer(hb, null)
            val soft = hwBmp?.copy(Bitmap.Config.ARGB_8888, true)
            hwBmp?.recycle()
            img.close()
            hb.close()
            soft?.let { small ->
                // leicht weichzeichnen, damit beim Hochskalieren kein Pixelrauschen entsteht
                val sp = IntArray(rw * rh)
                small.getPixels(sp, 0, rw, 0, 0, rw, rh)
                val tp = IntArray(sp.size)
                boxH(sp, tp, rw, rh, 1)
                boxV(tp, sp, rw, rh, 1)
                small.setPixels(sp, 0, rw, 0, 0, rw, rh)
                val big = Bitmap.createScaledBitmap(small, w, h, true)
                if (big != small) small.recycle()
                big
            }
        } catch (e: Throwable) {
            null
        } finally {
            try { hr?.destroy() } catch (e: Throwable) { }
            try { reader?.close() } catch (e: Throwable) { }
        }
    }

    /** Rückfall ohne Shader (Android unter 13): weicher Verlauf in den Himmelsfarben. */
    private fun gradientBitmap(w: Int, h: Int, st: SkyState): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        fun col(a: FloatArray) = Color.rgb(
            (a[0] * 255).toInt().coerceIn(0, 255), (a[1] * 255).toInt().coerceIn(0, 255), (a[2] * 255).toInt().coerceIn(0, 255)
        )
        val p = Paint()
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(col(st.top), col(st.mid), col(st.hor)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }

    /**
     * Lesbarkeit wie in der Vorschau: Helligkeit über das ganze Widget messen (90. Perzentil),
     * daraus die gleichmäßige Abdunklung, damit weiße Schrift mindestens 4,5 : 1 Kontrast hat.
     */
    fun neededDim(sky: Bitmap): Float {
        val w = sky.width
        val h = sky.height
        val px = IntArray(w * h)
        sky.getPixels(px, 0, w, 0, 0, w, h)
        val n = (px.size + 6) / 7
        val lums = FloatArray(n)
        var k = 0
        var i = 0
        while (i < px.size && k < n) {
            val c = px[i]
            lums[k++] = 0.2126f * lin((c shr 16) and 255) + 0.7152f * lin((c shr 8) and 255) + 0.0722f * lin(c and 255)
            i += 7
        }
        lums.sort(0, k)
        val l = if (k == 0) 0f else lums[min(k - 1, (k * 0.9f).toInt())]
        val target = 0.18f
        val dark = 0.005f
        return if (l <= target) 0f else min(0.9f, (l - target) / (l - dark))
    }

    private fun lin(v: Int): Float {
        val x = v / 255f
        return if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    /**
     * Milchglas-Hintergrund: weichgezeichnete, kräftigere Kopie des (abgedunkelten) Himmels.
     * Entspricht backdrop-filter: blur(14px) saturate(150%) der Vorschau.
     */
    fun frosted(src: Bitmap, sigmaPx: Float): Bitmap {
        val f = 4
        val sw = max(1, src.width / f)
        val sh = max(1, src.height / f)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sigma = max(0.5f, sigmaPx / f)
        // Drei Kastenfilter hintereinander ≈ Gauß-Weichzeichner
        val box = max(1, ((sqrt(12f * sigma * sigma / 3f + 1f) - 1f) / 2f).toInt())
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val tmp = IntArray(px.size)
        repeat(3) {
            boxH(px, tmp, sw, sh, box)
            boxV(tmp, px, sw, sh, box)
        }
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val cm = android.graphics.ColorMatrix()
        cm.setSaturation(1.5f)
        p.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        c.drawBitmap(small, null, android.graphics.Rect(0, 0, src.width, src.height), p)
        if (small != src) small.recycle()
        return out
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val d = 2 * r + 1
        for (y in 0 until h) {
            var sr = 0; var sg = 0; var sb = 0
            val row = y * w
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                val cOut = src[row + (x - r).coerceIn(0, w - 1)]
                val cIn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                sr += ((cIn shr 16) and 255) - ((cOut shr 16) and 255)
                sg += ((cIn shr 8) and 255) - ((cOut shr 8) and 255)
                sb += (cIn and 255) - (cOut and 255)
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val d = 2 * r + 1
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                val cOut = src[(y - r).coerceIn(0, h - 1) * w + x]
                val cIn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                sr += ((cIn shr 16) and 255) - ((cOut shr 16) and 255)
                sg += ((cIn shr 8) and 255) - ((cOut shr 8) and 255)
                sb += (cIn and 255) - (cOut and 255)
            }
        }
    }
}
