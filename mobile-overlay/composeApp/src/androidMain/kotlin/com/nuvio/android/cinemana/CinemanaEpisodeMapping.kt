package com.nuvio.android.cinemana

/**
 * Pure normalization for merging Cinemana series/episodes with TMDB metadata.
 * Stream selection must always resolve to [videoId], never a TMDB ID.
 */
object CinemanaEpisodeMapping {
    data class EpisodeRef(
        val videoId: String,
        val season: Int,
        val episode: Int,
        val title: String
    )
    data class TmdbEpisode(val season: Int, val episode: Int, val title: String)

    fun merge(
        seasonEndpoint: List<EpisodeRef>,
        searchFallback: List<EpisodeRef>,
        tmdbEpisodes: List<TmdbEpisode> = emptyList()
    ): List<EpisodeRef> {
        val byVideoId = linkedMapOf<String, EpisodeRef>()
        for (entry in seasonEndpoint + searchFallback) {
            if (!validId(entry.videoId) || entry.season < 1 || entry.episode < 1) continue
            byVideoId.putIfAbsent(entry.videoId, entry)
        }
        val officialTitles = tmdbEpisodes.associateBy { it.season to it.episode }
        return byVideoId.values.map { item ->
            val official = officialTitles[item.season to item.episode]
            if (official != null && official.title.isNotBlank())
                item.copy(title = official.title)
            else item
        }.sortedWith(compareBy(EpisodeRef::season, EpisodeRef::episode, EpisodeRef::videoId))
    }

    fun rootlessSeriesKey(title: String, year: String?): String =
        title.lowercase().trim().replace(Regex("\\s+"), " ") + "|" + (year ?: "")

    private fun validId(id: String) = id.isNotBlank() && id.all(Char::isDigit)
}
