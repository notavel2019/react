package com.example.update

import com.example.model.GitHubReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object GitHubUpdateRepository {

    private val REPO_SEGMENT_REGEX = Regex("^[A-Za-z0-9._-]{1,100}$")

    fun normalizeRepoSlug(rawInput: String): String? {
        val cleaned = rawInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split('/')
        if (parts.size != 2) return null
        val owner = parts[0]
        val repo = parts[1]
        if (!REPO_SEGMENT_REGEX.matches(owner) || !REPO_SEGMENT_REGEX.matches(repo)) {
            return null
        }
        return "$owner/$repo"
    }

    fun isSafeGitHubReleaseUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" || host.endsWith(".githubusercontent.com")
    }

    suspend fun fetchLatestRelease(repoSlug: String): Result<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeRepoSlug(repoSlug)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Use o formato usuario/repositorio (ex: meuperfil/react-studio).")
                )

            runCatching {
                val apiUrl = URL("https://api.github.com/repos/$normalized/releases/latest")
                val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ReactStudio-Android")
                }

                try {
                    val code = conn.responseCode
                    if (code == 404) {
                        throw IllegalStateException("Nenhuma release encontrada ainda neste repositório.")
                    }
                    if (code !in 200..299) {
                        throw IllegalStateException("GitHub retornou erro HTTP $code.")
                    }

                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(body)
                    val tagName = json.optString("tag_name", "").ifBlank { "v0.0.0" }
                    val versionName = tagName.removePrefix("v").removePrefix("V")
                    val title = json.optString("name", tagName).ifBlank { tagName }
                    val notes = json.optString("body", "Nova atualização disponível.").ifBlank {
                        "Nova versão publicada via GitHub Actions."
                    }
                    val publishedAt = json.optString("published_at", "").take(10)
                    val htmlUrl = json.optString("html_url", "https://github.com/$normalized/releases")

                    var apkUrl: String? = null
                    var apkName: String? = null
                    var apkSize = 0L

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && isSafeGitHubReleaseUrl(downloadUrl)) {
                                apkUrl = downloadUrl
                                apkName = name
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    GitHubReleaseInfo(
                        tagName = tagName,
                        versionName = versionName,
                        releaseTitle = title,
                        releaseNotes = notes,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkAssetName = apkName,
                        apkSizeBytes = apkSize
                    )
                } finally {
                    conn.disconnect()
                }
            }
        }

    fun isRemoteNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = parseVersionParts(currentVersion)
        val remoteParts = parseVersionParts(remoteVersion)
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val cur = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem != cur) return rem > cur
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
