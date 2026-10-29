# Cinemana mobile feature parity — mandatory acceptance contract

**Source of truth:** existing working addon at `Ali92Barca/cinemana-addon/server.js` (Stremio addon SDK manifest v0.5.1). Port its *behavior* into Nuvio Mobile's native data/provider APIs; do **not** ship Node, localhost, or require an external addon.

## Required feature parity

| Feature | Existing implementation | Native mobile acceptance |
|---|---|---|
| Cinemana catalogs | `catalog` handler, movie and series, category IDs | Cinemana shelves on home/discover; independent Cinemana catalog screens; category navigation, paging |
| Search | `cinemana_search` and `cinemana_series_search` | Search returns Cinemana movies/series inside Nuvio global search as well as Cinemana-only search; distinguish sources and dedupe safely |
| TMDB | `searchTmdb`, `enrichMetaWithTmdb` | Reuse app's existing TMDB credentials, locale and metadata service; preserve Cinemana playback IDs and fallback when TMDB match fails |
| Movies | `meta` and `stream` handlers | Posters, descriptions, cast/ratings where available, Cinemana sources in Nuvio details and player |
| Series & numbering | `getSeriesEpisodes`, `findCinemanaEpisodesForSeries`, `enrichSeriesVideosWithTmdb` | Accurate season/episode numbers and ordering, episode-to-Cinemana-ID mapping, support nonzero and zero `rootSeries`, multiple seasons and anime; no wrong episode stream |
| Streams | `stream` handler | Fetch playable sources inside native app, present quality/available sources, play through Nuvio player |
| Subtitles | `subtitles` handler, `allVideoInfo.translations` | Show Cinemana subtitle languages and tracks in Nuvio player with correct per-episode mapping |
| Catalog integration | addon resources `catalog/meta/stream/subtitles` | Cinemana appears in Nuvio catalogs/search/detail/source selection while retaining independent browsing |
| Fallbacks | Cinemana metadata on TMDB failures | Search/catalog/stream still usable when TMDB enrichment fails |
| Updates | Nuvio updater runtime config | Separate app identity and GitHub update source; do not overwrite official Nuvio |

## Verified old-addon details

- Category catalogs include action, thriller, crime, western, sci-fi, fantasy, drama, animation, horror, romance, sport, mystery, comedy, adventure and documentary.
- Series season endpoint: `/api/android/videoSeason/id/{id}`. Iterates additional seasons up to bounded lookups.
- Episode identifiers use `nb` or `id`, with `season` and `episodeNummer` for season/episode numbering.
- When series `rootSeries=0` or the season endpoint is incomplete, searches series by title and merges missing episodes by real Cinemana video ID.
- TMDB is **metadata enrichment**; the player must use the corresponding Cinemana video ID, never a TMDB ID as a stream ID.
- Cinemana subtitles are obtained from `allVideoInfo` `translations`.
- Current addon has an independent TMDB API key configuration; mobile must instead reuse existing app settings where feasible, not demand a second key.

## Implementation gates

1. Inspect upstream Nuvio Mobile catalog/search/details/stream/subtitle provider interfaces and existing TMDB settings.
2. Extract/port old addon Cinemana HTTP and normalization logic into native Android/Kotlin components; add fixture tests.
3. Integrate into app global catalogs, global search, details, episode selection and playback/subtitle picker.
4. Verify movies, multi-season series, anime with `rootSeries=0`, missing episodes, subtitle language selection, TMDB mismatch, and offline/HTTP failures.
5. Build and test installable Android mobile APK; only after acceptance begin TV.

Bingers remains optional and must not block these core Cinemana requirements.
