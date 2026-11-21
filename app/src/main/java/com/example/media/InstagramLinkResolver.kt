package com.example.media

import com.example.model.VideoSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

data class ResolvedVideoLink(
    val playableUri: String,
    val originalLink: String,
    val title: String,
    val sourceType: VideoSourceType
)

object InstagramLinkResolver {

    private val INSTAGRAM_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com",
        "m.instagram.com",
        "instagr.am",
        "www.instagr.am"
    )

    private val ALLOWED_EMBED_HOST_SUFFIXES = listOf(
        "instagram.com",
        "cdninstagram.com",
        "fbcdn.net",
        "facebook.com"
    )

    private val INSTAGRAM_PATH_REGEX = Regex(
        pattern = "^/(reel|reels|p|tv)/([A-Za-z0-9_-]{5,32})(?:/.*)?$",
        option = RegexOption.IGNORE_CASE
    )

    private val OG_VIDEO_META_REGEX = Regex(
        pattern = """<meta[^>]+(?:property|name)=["']og:video(?::secure_url)?["'][^>]+content=["'](https://[^"']+)["']""",
        option = RegexOption.IGNORE_CASE
    )

    fun isSafeExternalHttpsUrl(rawUrl: String): Boolean {
        val parsed = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local")) return false

        val isPrivateOrLoopback = runCatching {
            val addr = InetAddress.getByName(host)
            addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress
        }.getOrDefault(false)
        return !isPrivateOrLoopback
    }

    fun isAllowedInstagramEmbedUrl(rawUrl: String): Boolean {
        val parsed = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (parsed.scheme?.lowercase() != "https") return false
        val host = parsed.host?.lowercase() ?: return false
        return ALLOWED_EMBED_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    fun isInstagramUrl(rawUrl: String): Boolean {
        val parsed = runCatching { URI(normalizeInputUrl(rawUrl)) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase() ?: return false
        return host in INSTAGRAM_HOSTS
    }

    fun extractInstagramMedia(rawUrl: String): Pair<String, String>? {
        val normalized = normalizeInputUrl(rawUrl)
        val parsed = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        if (host !in INSTAGRAM_HOSTS) return null
        val path = parsed.path ?: return null
        val match = INSTAGRAM_PATH_REGEX.find(path) ?: return null
        val type = match.groupValues[1].lowercase().let { if (it == "reels") "reel" else it }
        val shortcode = match.groupValues[2]
        return type to shortcode
    }

    suspend fun resolve(rawInput: String): Result<ResolvedVideoLink> = withContext(Dispatchers.IO) {
        val normalized = normalizeInputUrl(rawInput)
        if (!isSafeExternalHttpsUrl(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Informe um link HTTPS público válido (ex: Instagram Reels ou .mp4).")
            )
        }

        val igMedia = extractInstagramMedia(normalized)
        if (igMedia != null) {
            val (type, shortcode) = igMedia
            val canonicalUrl = "https://www.instagram.com/$type/$shortcode/"
            val embedUrl = "https://www.instagram.com/$type/$shortcode/embed/captioned/"
            val label = if (type == "reel") "Instagram Reel · $shortcode" else "Instagram · $shortcode"

            // Try extracting public og:video stream first for native ExoPlayer + split-screen compositing
            val directVideoUrl = probePublicOgVideo(canonicalUrl)
            if (directVideoUrl != null && isAllowedInstagramEmbedUrl(directVideoUrl)) {
                return@withContext Result.success(
                    ResolvedVideoLink(
                        playableUri = directVideoUrl,
                        originalLink = canonicalUrl,
                        title = label,
                        sourceType = VideoSourceType.DIRECT_STREAM
                    )
                )
            }

            // Fallback cleanly to official Instagram Embed Canvas (supports full framing, zoom & pan)
            return@withContext Result.success(
                ResolvedVideoLink(
                    playableUri = embedUrl,
                    originalLink = canonicalUrl,
                    title = label,
                    sourceType = VideoSourceType.INSTAGRAM_EMBED
                )
            )
        }

        if (isInstagramUrl(normalized)) {
            return@withContext Result.failure(
                IllegalArgumentException("Use um link direto de Reel ou publicação (ex: instagram.com/reel/...).")
            )
        }

        val label = normalized
            .substringAfterLast('/')
            .substringBefore('?')
            .ifBlank { "Vídeo online" }

        Result.success(
            ResolvedVideoLink(
                playableUri = normalized,
                originalLink = normalized,
                title = label,
                sourceType = VideoSourceType.DIRECT_STREAM
            )
        )
    }

    private fun probePublicOgVideo(canonicalInstagramUrl: String): String? {
        return runCatching {
            val connection = (URL(canonicalInstagramUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val buffer = CharArray(32_768)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
                val match = OG_VIDEO_META_REGEX.find(html) ?: return@runCatching null
                match.groupValues[1]
                    .replace("&amp;", "&")
                    .takeIf { it.startsWith("https://") }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun normalizeInputUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.removePrefix("http://")
            trimmed.startsWith("instagram.com", ignoreCase = true) ||
                trimmed.startsWith("www.instagram.com", ignoreCase = true) -> "https://$trimmed"
            else -> trimmed
        }
    }
}
