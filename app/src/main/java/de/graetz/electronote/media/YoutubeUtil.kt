package de.graetz.electronote.media

import java.net.HttpURLConnection
import java.net.URL

/** Extracts a video ID from any common YouTube URL shape, mirroring the iOS regex. */
object YoutubeUtil {
    private val idRegex = Regex("""(?:youtu\.be/|[?&]v=|embed/|shorts/)([A-Za-z0-9_-]{11})""")
    private val bareIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        idRegex.find(trimmed)?.let { return it.groupValues[1] }
        if (bareIdRegex.matches(trimmed)) return trimmed
        return null
    }

    fun thumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    /** Blocking network fetch — call from a background dispatcher. */
    fun fetchThumbnailBytes(videoId: String): ByteArray? {
        return try {
            val connection = URL(thumbnailUrl(videoId)).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
