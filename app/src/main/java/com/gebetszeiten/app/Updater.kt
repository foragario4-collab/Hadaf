package com.gebetszeiten.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-App-Update: prüft bei GitHub, ob es eine neuere Version gibt (Release „build-N“),
 * lädt die APK und übergibt sie dem Android-Installer. Android fragt vor dem Installieren immer nach.
 */
object Updater {
    private const val REPO = "foragario4-collab/Hadaf"
    private const val CH_UPDATE = "app_update"
    private const val NOTIF_ID = 4711
    const val ACTION_INSTALLED = "com.gebetszeiten.app.UPDATE_STATUS"

    /** Eine Version auf GitHub. */
    class Release(val build: Int, val url: String, val size: Long)

    @Volatile var latest: Release? = null
        private set
    @Volatile var lastError: String? = null
        private set

    /** Build-Nummer dieser App (vom GitHub-Build eingetragen; 0 = unbekannt). */
    val currentBuild: Int get() = BuildConfig.BUILD_RUN

    fun isNewer(r: Release?): Boolean = r != null && r.build > currentBuild

    private val main = Handler(Looper.getMainLooper())

    /** Neueste Version abfragen; Ergebnis kommt im Haupt-Thread zurück. */
    fun check(done: (Release?, String?) -> Unit) {
        Thread {
            var rel: Release? = null
            var err: String? = null
            try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 12_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "Hadaf-Gebetszeiten")
                val code = conn.responseCode
                if (code != 200) {
                    err = t("update_error")
                } else {
                    val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val build = o.optString("tag_name").substringAfter("build-", "").toIntOrNull()
                    val assets = o.optJSONArray("assets")
                    var url: String? = null
                    var size = 0L
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk")) {
                                url = a.optString("browser_download_url")
                                size = a.optLong("size", 0L)
                                break
                            }
                        }
                    }
                    if (build != null && !url.isNullOrBlank()) rel = Release(build, url, size)
                    else err = t("update_error")
                }
                conn.disconnect()
            } catch (e: Exception) {
                err = t("update_error")
            }
            latest = rel ?: latest
            lastError = err
            main.post { done(rel, err) }
        }.start()
    }

    /** Einmal am Tag beim Öffnen der App still prüfen; bei neuer Version eine Mitteilung zeigen. */
    fun autoCheck(ctx: Context) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("lastCheck", 0L) < 20 * 3600_000L) return
        sp.edit().putLong("lastCheck", now).apply()
        check { rel, _ ->
            if (rel != null && isNewer(rel) && currentBuild > 0 && sp.getInt("notified", 0) != rel.build) {
                sp.edit().putInt("notified", rel.build).apply()
                notifyNew(app, rel)
            }
        }
    }

    private fun notifyNew(ctx: Context, rel: Release) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, t("ch_update"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 31,
            Intent(ctx, MainActivity::class.java).putExtra("openSettings", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_live_dot)
            .setContentTitle(t("n_update_title"))
            .setContentText(t("n_update_text", rel.build))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (e: SecurityException) { }
    }

    /** Darf die App Updates installieren? Sonst muss der Nutzer es einmal erlauben. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    /** APK laden (mit Fortschritt in Prozent) und an den Installer übergeben. */
    fun install(ctx: Context, rel: Release, progress: (Int) -> Unit, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            var err: String? = null
            try {
                val pi = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(app.packageName)
                val sid = pi.createSession(params)
                val session = pi.openSession(sid)
                try {
                    val conn = URL(rel.url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Hadaf-Gebetszeiten")
                    val total = if (rel.size > 0) rel.size else conn.contentLengthLong
                    conn.inputStream.use { input ->
                        session.openWrite("hadaf.apk", 0, if (total > 0) total else -1).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct = (read * 100 / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        main.post { progress(pct) }
                                    }
                                }
                            }
                            session.fsync(out)
                        }
                    }
                    conn.disconnect()
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(
                        app, sid, Intent(app, UpdateReceiver::class.java).setAction(ACTION_INSTALLED), flags
                    )
                    session.commit(status.intentSender)
                } finally {
                    session.close()
                }
            } catch (e: Exception) {
                err = t("update_error")
            }
            main.post { done(err) }
        }.start()
    }
}

/** Rückmeldung des Android-Installers: Bestätigung anzeigen oder Ergebnis melden. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Prefs.init(ctx)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { if (confirm != null) ctx.startActivity(confirm) } catch (e: Exception) { }
            }
            PackageInstaller.STATUS_SUCCESS -> { }
            else -> {
                android.widget.Toast.makeText(ctx, t("update_failed"), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
