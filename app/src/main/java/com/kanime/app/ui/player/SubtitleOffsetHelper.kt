package com.kanime.app.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * Downloads, caches, and time-shifts SRT / WebVTT subtitle files locally so ExoPlayer
 * can apply real-time subtitle sync offsets (e.g., -1.5s / +0.5s) with near-zero latency.
 */
object SubtitleOffsetHelper {

    private const val TAG = "SubtitleOffsetHelper"
    private const val MAX_CACHE_ENTRIES = 24

    private val rawTextCache = ConcurrentHashMap<String, String>()

    // Matches:
    //  SRT: 00:01:23,456 --> 00:01:25,789
    //  VTT: 00:01:23.456 --> 00:01:25.789  OR  01:23.456 --> 01:25.789
    private val CUE_TIMING_REGEX = Regex(
        """((?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{3})(\s*-->\s*)((?:\d{1,2}:)?\d{2}:\d{2}[,.]\d{3})"""
    )

    internal fun parseTimestampMs(raw: String): Long? {
        val normalized = raw.trim().replace(',', '.')
        val parts = normalized.split(':')
        return try {
            val (hours, minutes, secMillis) = when (parts.size) {
                3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
                2 -> Triple(0L, parts[0].toLong(), parts[1])
                else -> return null
            }
            val secParts = secMillis.split('.')
            val seconds = secParts[0].toLong()
            val millis = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
        } catch (_: Exception) {
            null
        }
    }

    internal fun formatTimestampMs(ms: Long, useComma: Boolean, includeHours: Boolean): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1000L
        val millis = clamped % 1000L
        val sep = if (useComma) ',' else '.'
        return if (includeHours || hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d%c%03d", hours, minutes, seconds, sep, millis)
        } else {
            String.format(Locale.US, "%02d:%02d%c%03d", minutes, seconds, sep, millis)
        }
    }

    /**
     * Shifts every SRT or WebVTT cue timing line by [offsetMs].
     * Positive [offsetMs] delays captions (appear later); negative [offsetMs] advances them (appear earlier).
     */
    fun shiftSubtitleText(content: String, offsetMs: Long, scale: Double = 1.0): String {
        if ((offsetMs == 0L && scale == 1.0) || content.isBlank()) return content
        return CUE_TIMING_REGEX.replace(content) { match ->
            val startRaw = match.groupValues[1]
            val arrow = match.groupValues[2]
            val endRaw = match.groupValues[3]

            val startMs = parseTimestampMs(startRaw) ?: return@replace match.value
            val endMs = parseTimestampMs(endRaw) ?: return@replace match.value

            val useComma = startRaw.contains(',')
            val includeHours = startRaw.count { it == ':' } >= 2 || endRaw.count { it == ':' } >= 2
            // new = old * scale + offset  (scale corrects 25fps <-> 23.976fps drift)
            val newStart = ((startMs * scale).toLong() + offsetMs).coerceAtLeast(0L)
            val newEnd = ((endMs * scale).toLong() + offsetMs).coerceAtLeast(newStart + 100L)

            formatTimestampMs(newStart, useComma, includeHours) +
                arrow +
                formatTimestampMs(newEnd, useComma, includeHours)
        }
    }

    private fun downloadSubtitleText(url: String, referer: String?): String? {
        rawTextCache[url]?.let { return it }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept-Encoding", "gzip")
                if (!referer.isNullOrBlank()) {
                    setRequestProperty("Referer", referer)
                }
            }
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream
            val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isNotBlank()) {
                if (rawTextCache.size >= MAX_CACHE_ENTRIES) {
                    rawTextCache.clear()
                }
                rawTextCache[url] = text
                text
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading subtitle for offset: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun resolveOffsetSubtitleUri(
        context: Context,
        url: String,
        referer: String?,
        offsetMs: Long,
        scale: Double = 1.0
    ): Uri {
        if ((offsetMs == 0L && scale == 1.0) || url.contains(".m3u8", ignoreCase = true)) {
            return Uri.parse(url)
        }
        return withContext(Dispatchers.IO) {
            val raw = downloadSubtitleText(url, referer) ?: return@withContext Uri.parse(url)
            val shifted = shiftSubtitleText(raw, offsetMs, scale)
            val isVtt = shifted.trimStart().startsWith("WEBVTT", ignoreCase = true) ||
                url.substringBefore('?').endsWith(".vtt", ignoreCase = true)
            val ext = if (isVtt) "vtt" else "srt"
            val dir = File(context.cacheDir, "subtitles_shifted").apply { mkdirs() }
            val file = File(dir, "sub_${url.hashCode().toUInt()}_${offsetMs}_${(scale * 10000).toLong()}.$ext")
            try {
                file.writeText(shifted, Charsets.UTF_8)
                Uri.fromFile(file)
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing shifted subtitle file: ${e.message}")
                Uri.parse(url)
            }
        }
    }
}
