package com.nd2.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Aggiornamenti dalle release di GitHub: controlla l'ultima release, scarica l'APK
 * e apre l'installer di sistema. Lo stato vive qui (non nella schermata), così un download
 * in corso continua anche se si esce dalle Impostazioni.
 */
object Updater {
    const val REPO = "carellice/ND2.0"
    const val REPO_URL = "https://github.com/$REPO"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    fun check(context: Context) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Ready) return
        _state.value = UpdateState.Checking
        val local = currentVersion(context)
        scope.launch {
            _state.value = runCatching {
                val release = fetchLatest()
                when {
                    release == null -> UpdateState.UpToDate
                    isNewer(release.version, local) -> UpdateState.Available(release)
                    else -> UpdateState.UpToDate
                }
            }.getOrElse { UpdateState.Error("Impossibile contattare GitHub. Controlla la connessione.") }
        }
    }

    fun download(context: Context, release: ReleaseInfo) {
        if (_state.value is UpdateState.Downloading) return
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        _state.value = UpdateState.Downloading(release, 0f)
        scope.launch {
            _state.value = runCatching {
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "ND2.0-${release.version}.apk")
                val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            var lastEmit = 0f
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                val p = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                                if (p - lastEmit >= 0.01f) {
                                    lastEmit = p
                                    _state.value = UpdateState.Downloading(release, p)
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                UpdateState.Ready(release, file)
            }.getOrElse { UpdateState.Error("Download non riuscito. Riprova.") }
        }
    }

    /** Su Android l'utente deve consentire una volta a ND2.0 di installare app. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode == 404) return null // nessuna release pubblicata
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
            return ReleaseInfo(
                version = json.optString("tag_name").removePrefix("v"),
                notes = json.optString("body").takeIf { it != "null" }.orEmpty(),
                apkUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Confronta versioni "1.10" > "1.9" numericamente, parte per parte. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
