package com.gebetszeiten.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lädt eine Seite unsichtbar wie ein Browser (mit JavaScript) und gibt den fertig angezeigten Text zurück.
 * Nötig für Seiten wie MasjidBox, die Zeiten erst im Browser aufbauen – im rohen HTML stehen sie dann nicht.
 * Darf nur aus einem Hintergrund-Thread aufgerufen werden (wartet höchstens [timeoutMs]).
 */
object WebText {
    @Volatile
    var app: Context? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun render(url: String, timeoutMs: Long = 20_000L): String? {
        val ctx = app ?: return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val main = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var result: String? = null
        var view: WebView? = null
        main.post {
            try {
                val w = WebView(ctx)
                view = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.blockNetworkImage = true
                var asked = false
                fun read(attempt: Int) {
                    // Text holen; ist noch keine Uhrzeit drin, kurz warten und noch einmal (Seite lädt nach)
                    w.evaluateJavascript("(function(){return document.body ? document.body.innerText : ''})()") { raw ->
                        val txt = try {
                            org.json.JSONArray("[$raw]").optString(0)
                        } catch (e: Exception) {
                            null
                        }
                        val hasTime = txt != null && Regex("\\d{1,2}[:h.]\\d{2}").containsMatchIn(txt)
                        if ((hasTime && attempt >= 1) || attempt >= 5) {
                            result = txt
                            latch.countDown()
                        } else {
                            main.postDelayed({ read(attempt + 1) }, 1200L)
                        }
                    }
                }
                w.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, u: String?) {
                        if (asked) return
                        asked = true
                        main.postDelayed({ read(0) }, 1200L)
                    }
                }
                w.loadUrl(url)
            } catch (e: Throwable) {
                latch.countDown()
            }
        }
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
        }
        main.post {
            try {
                view?.stopLoading()
                view?.destroy()
            } catch (e: Throwable) {
            }
        }
        return result?.replace(Regex("\\s+"), " ")?.trim()
    }
}
