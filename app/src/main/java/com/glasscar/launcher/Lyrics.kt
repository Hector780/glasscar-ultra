package com.glasscar.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val atMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine> = emptyList(), val plain: String = "", val message: String = "")

fun parseLrc(text: String): List<LyricLine> {
    val stamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val offset = Regex("\\[offset:([+-]?\\d+)]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return text.lineSequence().flatMap { line ->
        val content = line.replace(stamp, "").trim()
        stamp.findAll(line).map { m ->
            val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            LyricLine((m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + fraction + offset).coerceAtLeast(0), content)
        }
    }.sortedBy { it.atMs }.toList()
}

/** Fetch only when the user opens the lyrics view; never fetch in the background by default. */
object LyricsSource {
    private val cache = linkedMapOf<String, Lyrics>()
    suspend fun load(media: MediaInfo): Lyrics {
        if (media.title.isBlank() || media.artist.isBlank()) return Lyrics(message = "La app no comparte título y artista")
        val key = "${media.title}|${media.artist}|${media.durationMs / 1000}"
        cache[key]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
                val duration = if (media.durationMs > 0) "&duration=${media.durationMs / 1000}" else ""
                val url = URL("https://lrclib.net/api/get?track_name=${encode(media.title)}&artist_name=${encode(media.artist)}$duration")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000; readTimeout = 7000
                    setRequestProperty("User-Agent", "GlassCarUltra/4.0")
                }
                try {
                    if (conn.responseCode == 404) return@runCatching Lyrics(message = "Letra no disponible para esta canción")
                    check(conn.responseCode in 200..299)
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.optBoolean("instrumental")) Lyrics(message = "Pista instrumental")
                    else Lyrics(
                        lines = parseLrc(json.optString("syncedLyrics", "").takeUnless { it == "null" }.orEmpty()),
                        plain = json.optString("plainLyrics", "").takeUnless { it == "null" }.orEmpty()
                    )
                } finally { conn.disconnect() }
            }.getOrElse { Lyrics(message = "No se pudo cargar la letra. Revisa la conexión.") }
        }
        if (result.message.isBlank()) {
            if (cache.size >= 20) cache.remove(cache.keys.first())
            cache[key] = result
        }
        return result
    }
}
