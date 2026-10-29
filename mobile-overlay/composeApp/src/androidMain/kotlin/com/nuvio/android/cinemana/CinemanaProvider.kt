package com.nuvio.android.cinemana

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** In-process Stremio resource adapter. The reserved host is intercepted before HTTP.
 * It opens no port, starts no service, and keeps the host's catalog/player/addon UI.
 */
object CinemanaProvider {
    const val MANIFEST_URL = "https://cinemana.nuvio.invalid/manifest.json"
    private val provider = CinemanaResourceAdapter(
        client = CinemanaClient(transport = ::httpGetText),
        settings = {
            val value = TmdbSettingsRepository.snapshot()
            TmdbCredentials(if (value.enabled) value.apiKey else "", value.language)
        },
        fetch = ::httpGetText,
    )

    suspend fun handle(url: String): String? = provider.handle(url)
}

data class TmdbCredentials(val apiKey: String, val language: String)

/** Injectable transport and settings make all resource routes testable without a device. */
class CinemanaResourceAdapter(
    private val client: CinemanaClient,
    private val settings: () -> TmdbCredentials,
    private val fetch: suspend (String) -> String,
) {
    suspend fun handle(url: String): String? {
        if (!url.startsWith("https://cinemana.nuvio.invalid/")) return null
        val uri = URI(url)
        if (uri.host != "cinemana.nuvio.invalid") return null
        require(uri.scheme == "https" && uri.port == -1 && uri.userInfo == null)
        val rawParts = uri.rawPath.trim('/').split('/')
        val parts = rawParts.map(::decode)
        if (parts == listOf("manifest.json")) return manifest().toString()
        require(parts.size in 3..4 && parts.last().endsWith(".json")) { "Invalid Cinemana resource route" }
        val resource = parts[0]
        val type = parts[1]
        require(type == "movie" || type == "series") { "Unsupported Cinemana content type" }
        val id = if (parts.size == 3) parts[2].removeSuffix(".json") else parts[2]
        val extra = if (parts.size == 4) rawParts[3].removeSuffix(".json").split('&').associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        } else emptyMap()
        val result = when (resource) {
            "catalog" -> {
                val query = extra["search"].orEmpty()
                val entries = if (query.isNotBlank()) client.search(query, type == "series")
                else {
                    val category = CinemanaCatalogs.category(id) ?: error("Unknown Cinemana catalog")
                    client.catalogPage(category.remoteIds, type == "series", extra["skip"]?.toIntOrNull() ?: 0)
                }
                JSONObject().put("metas", JSONArray(entries.map { preview(it, type) }))
            }
            "meta" -> JSONObject().put("meta", metadata(id, type))
            "stream", "subtitles" -> {
                val videoId = resolve(id, type)
                if (videoId == null) JSONObject().put(if (resource == "stream") "streams" else "subtitles", JSONArray())
                else if (resource == "subtitles") JSONObject().put("subtitles", subtitleArray(videoId))
                else JSONObject().put("streams", JSONArray(client.streams(videoId).map { stream ->
                    JSONObject().put("name", "Cinemana").put("title", "${stream.quality} • ${stream.container}")
                        .put("url", stream.url)
                        .put("behaviorHints", JSONObject().put("bingeGroup", "cinemana-${stream.quality}"))
                }))
            }
            else -> error("Unknown Cinemana resource")
        }
        return result.toString()
    }

    fun manifest(): JSONObject {
        val types = listOf("movie", "series")
        val resources = JSONArray().put("catalog")
        resources.put(JSONObject().put("name", "meta").put("types", JSONArray(types)).put("idPrefixes", JSONArray(listOf("cinemana:"))))
        for (resource in listOf("stream", "subtitles")) {
            resources.put(JSONObject().put("name", resource).put("types", JSONArray(types))
                .put("idPrefixes", JSONArray(listOf("cinemana:", "tt", "tmdb:"))))
        }
        val catalogs = JSONArray()
        for (type in types) {
            catalogs.put(JSONObject().put("id", "cinemana_search_$type").put("type", type)
                .put("name", "Cinemana").put("extra", JSONArray().put(JSONObject().put("name", "search").put("isRequired", true))))
            for (category in CinemanaCatalogs.categories) {
                catalogs.put(JSONObject().put("id", category.id + if (type == "series") "_series" else "")
                    .put("type", type).put("name", "Cinemana • ${category.title}")
                    .put("extra", JSONArray().put(JSONObject().put("name", "skip"))))
            }
        }
        return JSONObject().put("id", "org.nuvio.cinemana.builtin").put("name", "Cinemana")
            .put("version", "0.7.0").put("description", "Built-in Cinemana movies and series")
            .put("types", JSONArray(types)).put("resources", resources).put("catalogs", catalogs)
    }

    private fun preview(entry: CinemanaClient.Entry, type: String): JSONObject =
        JSONObject().put("id", "cinemana:${entry.id}").put("type", type).put("name", entry.title)
            .put("poster", entry.poster).put("releaseInfo", entry.year)

    private suspend fun metadata(id: String, type: String): JSONObject {
        val rawId = id.removePrefix("cinemana:").substringBefore(':')
        require(id.startsWith("cinemana:") && rawId.all(Char::isDigit) && rawId.isNotEmpty())
        val record = client.info(rawId)
        val entry = client.entry(record, type == "series") ?: error("Cinemana metadata unavailable")
        val meta = preview(entry.copy(id = rawId), type)
            .put("description", record.optString("en_description").ifBlank { record.optString("ar_description") })
        val credentials = settings()
        val match = optional {
            CinemanaTmdbMatcher(credentials.apiKey, credentials.language, fetch)
                .match(entry.title, entry.year?.toIntOrNull(), type == "series")
        }
        if (match != null) {
            meta.put("name", match.title).put("description", match.overview)
            if (match.poster != null) meta.put("poster", match.poster)
            val imdb = optional { tmdb("${if (type == "series") "tv" else "movie"}/${match.tmdbId}/external_ids").optString("imdb_id") }
            meta.put("moviedb_id", match.tmdbId.toString())
            if (!imdb.isNullOrBlank() && imdb != "null") meta.put("imdb_id", imdb)
        }
        if (type == "series") {
            val episodes = client.episodes(rawId)
            val titles = mutableListOf<CinemanaEpisodeMapping.TmdbEpisode>()
            if (match != null) for (season in episodes.map { it.season }.distinct()) {
                val data = optional { tmdb("tv/${match.tmdbId}/season/$season").optJSONArray("episodes") }
                if (data != null) for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    titles.add(CinemanaEpisodeMapping.TmdbEpisode(season, item.optInt("episode_number"), item.optString("name")))
                }
            }
            val merged = CinemanaEpisodeMapping.merge(episodes.map {
                CinemanaEpisodeMapping.EpisodeRef(it.videoId, it.season, it.episode, it.title)
            }, emptyList(), titles)
            meta.put("videos", JSONArray(merged.map { episode ->
                JSONObject().put("id", "cinemana:${episode.videoId}").put("season", episode.season)
                    .put("episode", episode.episode).put("title", episode.title)
            }))
        }
        return meta
    }

    private suspend fun subtitleArray(id: String): JSONArray = JSONArray(client.subtitles(id).mapIndexed { index, subtitle ->
        JSONObject().put("id", "cinemana-$id-$index").put("url", subtitle.url)
            .put("lang", subtitle.language).put("label", subtitle.label)
    })

    /** Resolve external catalog IDs conservatively; every series request requires S/E. */
    private suspend fun resolve(id: String, type: String): String? {
        if (id.startsWith("cinemana:")) return id.removePrefix("cinemana:").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        val series = type == "series"
        val tokens = id.split(':')
        val season = if (series) tokens.getOrNull(tokens.size - 2)?.toIntOrNull() else null
        val episode = if (series) tokens.lastOrNull()?.toIntOrNull() else null
        if (series && (season == null || episode == null || season < 1 || episode < 1)) return null
        val kind = if (series) "tv" else "movie"
        val tmdbId = when {
            tokens[0] == "tmdb" -> {
                val index = if (tokens.getOrNull(1) in listOf("movie", "tv", "series")) 2 else 1
                tokens.getOrNull(index)?.toIntOrNull()?.takeIf { it > 0 }
            }
            tokens[0].matches(Regex("tt[0-9]+")) -> {
                val array = tmdb("find/${tokens[0]}", "external_source=imdb_id").optJSONArray(if (series) "tv_results" else "movie_results")
                array?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
            }
            else -> null
        } ?: return null
        val data = tmdb("$kind/$tmdbId")
        val year = data.optString(if (series) "first_air_date" else "release_date").take(4).toIntOrNull()
        val queries = listOf(data.optString(if (series) "name" else "title"), data.optString(if (series) "original_name" else "original_title"))
            .filter { it.isNotBlank() }.distinct()
        val entries = queries.flatMap { client.search(it, series, 2) }.distinctBy { it.id }
        val matches = entries.filter { entry ->
            queries.any { normalize(it) == normalize(entry.title) } &&
                (year == null || entry.year?.toIntOrNull() == year)
        }
        val selected = matches.singleOrNull() ?: return null
        if (!series) return selected.id
        return client.episodes(selected.id).firstOrNull { it.season == season && it.episode == episode }?.videoId
    }

    private suspend fun tmdb(path: String, extra: String = ""): JSONObject {
        val credentials = settings()
        require(credentials.apiKey.isNotBlank()) { "Enable TMDB in Nuvio settings to resolve external catalogs" }
        val url = "https://api.themoviedb.org/3/$path?api_key=${encode(credentials.apiKey)}&language=${encode(credentials.language)}&$extra"
        return JSONObject(fetch(url))
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}
