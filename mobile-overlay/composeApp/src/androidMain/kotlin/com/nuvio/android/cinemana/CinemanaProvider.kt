package com.nuvio.android.cinemana

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native resource transport. No socket, HTTP listener, foreground service or Node. */
object CinemanaProvider {
    const val MANIFEST_URL = "https://cinemana.nuvio.invalid/manifest.json"
    private val cache = CinemanaResponseCache(::httpGetText)
    private val provider = CinemanaResourceAdapter(
        client = CinemanaClient(transport = cache::get),
        settings = {
            val value = TmdbSettingsRepository.snapshot()
            TmdbCredentials(if (value.enabled) value.apiKey else "", value.language,
                value.useArtwork, value.useBasicInfo, value.useDetails, value.useEpisodes)
        },
        fetch = cache::get,
    )

    suspend fun handle(url: String): String? = provider.handle(url)
}

/** Bounded, short-lived response cache, including signed stream/subtitle paths.
 * Failed requests are never cached. Keys include TMDB credentials and language.
 */
private class CinemanaResponseCache(private val fetch: suspend (String) -> String) {
    private data class Cached(val at: Long, val payload: String)
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, Cached>()

    suspend fun get(url: String): String {
        val now = System.currentTimeMillis()
        mutex.withLock {
            entries[url]?.takeIf { now - it.at in 0..60_000 }?.let { return it.payload }
        }
        val payload = fetch(url)
        mutex.withLock {
            entries[url] = Cached(System.currentTimeMillis(), payload)
            while (entries.size > 128) entries.remove(entries.keys.first())
        }
        return payload
    }
}
