package com.nuvio.android.cinemana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Optional TMDB metadata enrichment. Never replace a Cinemana playback ID with a TMDB ID.
 * Supply the app's existing TMDB API key at runtime, not a committed secret.
 */
class CinemanaTmdbMatcher(
    private val apiKey: String,
    private val language: String = "ar-SA",
    private val fetch: suspend (String) -> String = ::tmdbHttpGet
) {
    data class Match(
        val tmdbId: Int,
        val title: String,
        val overview: String?,
        val poster: String?,
        val year: Int?,
        val score: Double
    )

    suspend fun match(title: String, year: Int?, series: Boolean): Match? {
        if (apiKey.isBlank() || title.isBlank()) return null
        val kind = if (series) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/search/$kind?api_key=${encode(apiKey)}&language=${encode(language)}&query=${encode(title)}"
        val results = JSONObject(fetch(url)).optJSONArray("results") ?: return null
        val normalized = normalize(title)
        val candidates = (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optInt("id")
            if (id <= 0) return@mapNotNull null
            val candidateTitle = item.optString(if (series) "name" else "title")
                .ifBlank { item.optString(if (series) "original_name" else "original_title") }
            val originalTitle = item.optString(if (series) "original_name" else "original_title")
            val candidateNorm = normalize(candidateTitle)
            val originalNorm = normalize(originalTitle)
            val date = item.optString(if (series) "first_air_date" else "release_date")
            val candidateYear = date.take(4).toIntOrNull()
            var score = when {
                normalized.isNotEmpty() && (normalized == candidateNorm || normalized == originalNorm) -> 100.0
                normalized.isNotEmpty() && candidateNorm.isNotEmpty() &&
                    (normalized.contains(candidateNorm) || candidateNorm.contains(normalized)) -> 45.0
                else -> 0.0
            }
            if (year != null && candidateYear != null)
                score += (25 - abs(year - candidateYear) * 5).coerceAtLeast(0)
            score += (item.optDouble("popularity", 0.0) / 10.0).coerceIn(0.0, 10.0)
            val posterPath = item.optString("poster_path")
            Match(
                tmdbId = id, title = candidateTitle,
                overview = item.optString("overview").ifBlank { null },
                poster = posterPath.takeIf { it.startsWith("/") }
                    ?.let { "https://image.tmdb.org/t/p/w500$it" },
                year = candidateYear, score = score
            )
        }
        // Reject low-confidence matches rather than attaching the wrong film or series.
        val ranked = candidates.sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        // Require exact title identity, reject conflicting known years and ties.
        return best.takeIf {
            it.score >= 100.0 && (year == null || it.year == null || abs(year - it.year) <= 1) &&
                (ranked.size < 2 || it.score - ranked[1].score >= 5.0)
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            .replace(Regex("\\s+"), " ")

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}

private suspend fun tmdbHttpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/json")
    }
    try {
        if (connection.responseCode !in 200..299) error("TMDB HTTP ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
}
