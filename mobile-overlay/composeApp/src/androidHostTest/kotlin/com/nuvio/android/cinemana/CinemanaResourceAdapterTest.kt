package com.nuvio.android.cinemana

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CinemanaResourceAdapterTest {
    private val base = "https://cinemana.nuvio.invalid"
    private fun adapter(fetch: suspend (String) -> String) = CinemanaResourceAdapter(
        CinemanaClient(transport = fetch), { TmdbCredentials("test-key", "en") }, fetch)

    @Test fun manifestAdvertisesMoviesSeriesAndExternalStreams() {
        val manifest = adapter { error("No network expected") }.manifest()
        assertEquals(32, manifest.getJSONArray("catalogs").length())
        val resources = manifest.getJSONArray("resources").toString()
        assertTrue(resources.contains("tmdb:"))
        assertTrue(resources.contains("subtitles"))
    }

    @Test fun neverInterceptsOtherHosts() = runBlocking {
        val provider = adapter { error("No network expected") }
        assertNull(provider.handle("https://example.org/a b"))
        assertNull(provider.handle("https://cinemana.nuvio.invalid.attacker.org/manifest.json"))
    }

    @Test fun preservesSearchEscapingAndStopsRepeatedPages() = runBlocking {
        var calls = 0
        val provider = adapter { url ->
            calls++
            assertTrue(url.contains("videoTitle=Tom+%26+Jerry"))
            """[{"nb":"42","en_title":"Tom & Jerry","year":"2021"}]"""
        }
        val response = JSONObject(provider.handle("$base/catalog/movie/cinemana_search_movie/search=Tom%20%26%20Jerry.json")!!)
        assertEquals("cinemana:42", response.getJSONArray("metas").getJSONObject(0).getString("id"))
        assertEquals(2, calls)
    }

    @Test fun paginationUsesOpaqueApiCursorsAndSeriesCategory() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = adapter { url ->
            calls.add(url)
            assertTrue(url.contains("videoKind=2"))
            assertTrue(url.contains("categoryID=97%2C84"))
            val start = if (url.contains("offset=500")) 31 else 1
            val rows = (start until start + 30).joinToString(",") { id ->
                """{"nb":"$id","rootSeries":"$id","en_title":"Example $id"}"""
            }
            """{"info":[$rows],"offset":500}"""
        }
        val response = JSONObject(provider.handle("$base/catalog/series/cinemana_action_series/skip=30.json")!!)
        assertEquals("cinemana-series:31", response.getJSONArray("metas").getJSONObject(0).getString("id"))
        assertEquals(2, calls.size)
        assertTrue(calls.last().contains("offset=500"))
    }

    @Test fun directPlaybackUsesCinemanaVideoIdAndFiltersBadUrls() = runBlocking {
        val provider = adapter { url ->
            assertTrue(url.endsWith("transcoddedFiles/id/42"))
            """[{"videoUrl":"https://cdn.example/movie.mp4","resolution":"1080p"},{"videoUrl":"javascript:bad"}]"""
        }
        val streams = JSONObject(provider.handle("$base/stream/movie/cinemana%3A42.json")!!).getJSONArray("streams")
        assertEquals(1, streams.length())
        assertEquals("https://cdn.example/movie.mp4", streams.getJSONObject(0).getString("url"))
    }

    @Test fun subtitlesPreferVttAndKeepFallback() = runBlocking {
        val provider = adapter {
            """{"nb":"42","translations":[{"type":"ar","extention":"srt","file":"https://cdn.example/ar.srt"},{"type":"ar","extention":"vtt","file":"https://cdn.example/ar.vtt"}],"enTranslationFilePath":"https://cdn.example/en.srt"}"""
        }
        val subtitles = JSONObject(provider.handle("$base/subtitles/movie/cinemana%3A42.json")!!).getJSONArray("subtitles")
        assertEquals(2, subtitles.length())
        assertEquals("https://cdn.example/ar.vtt", subtitles.getJSONObject(0).getString("url"))
    }

    @Test fun externalSeriesMatchesSeasonEpisodeInsteadOfFirstEpisode() = runBlocking {
        val provider = adapter { url ->
            when {
                url.contains("/tv/123?") -> """{"name":"Example","original_name":"Example","first_air_date":"2020-01-01"}"""
                url.contains("AdvancedSearch") -> """[{"nb":"40","rootSeries":"40","en_title":"Example","year":"2020"}]"""
                url.contains("videoSeason/id/40") -> """[{"nb":"41","season":1,"episodeNummer":1},{"nb":"42","season":1,"episodeNummer":2}]"""
                url.contains("transcoddedFiles/id/42") -> """[{"videoUrl":"https://cdn.example/episode2.mp4"}]"""
                else -> error("Unexpected request $url")
            }
        }
        val streams = JSONObject(provider.handle("$base/stream/series/tmdb%3Atv%3A123%3A1%3A2.json")!!).getJSONArray("streams")
        assertEquals("https://cdn.example/episode2.mp4", streams.getJSONObject(0).getString("url"))
    }

    @Test fun refusesSeriesWithoutEpisodeAndAmbiguousMovies() = runBlocking {
        val noCalls = adapter { error("Must not guess first episode") }
        val streams = JSONObject(noCalls.handle("$base/stream/series/tt123.json")!!).getJSONArray("streams")
        assertEquals(0, streams.length())
        val ambiguous = adapter { url ->
            if (url.contains("/movie/123?")) """{"title":"Example","release_date":"2020-01-01"}"""
            else """[{"nb":"41","en_title":"Example","year":"2020"},{"nb":"42","en_title":"Example","year":"2020"}]"""
        }
        assertEquals(0, JSONObject(ambiguous.handle("$base/stream/movie/tmdb%3A123.json")!!).getJSONArray("streams").length())
    }

    @Test fun cancellationIsNeverSwallowedByEpisodeTraversal() {
        assertFailsWith<CancellationException> {
            runBlocking { CinemanaClient(transport = { throw CancellationException() }).episodes("42") }
        }
    }

    @Test fun episodeMetadataCannotReplacePlaybackIds() {
        val merged = CinemanaEpisodeMapping.merge(
            listOf(CinemanaEpisodeMapping.EpisodeRef("42", 1, 2, "old")),
            listOf(CinemanaEpisodeMapping.EpisodeRef("43", 2, 1, "next")),
            listOf(CinemanaEpisodeMapping.TmdbEpisode(1, 2, "Official")))
        assertEquals(listOf("42", "43"), merged.map { it.videoId })
        assertEquals("Official", merged.first().title)
    }
}
