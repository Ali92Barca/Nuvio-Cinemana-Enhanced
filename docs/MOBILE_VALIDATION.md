# Mobile validation and TV gate

Target: Android mobile only. TV work must remain blocked until the user tests the mobile APK in practical use, reported defects are fixed, and the user confirms the mobile version is ready. Automated tests or a successful APK build do not open the TV gate.

## Reproducible source

Upstream: AKRusso/NuvioMobile-Enhanced at `ac03c46a159e7e4bbbcd77ad4bdf6ea604b34107`.
The CI workflow checks out that exact source, applies `scripts/apply-mobile-overlay.py`, tests the Cinemana resource adapter, and assembles FullDebug.
The patcher checks every modified upstream seam and is idempotent. Upstream drift fails the build explicitly.

The provider is bundled in Android's existing addon repository and can be disabled with its normal switch. Its reserved HTTPS address is an in-process dispatch key; no listener or port is opened and the reserved host is never contacted. Movie and series catalogs, global search, details, sources and subtitle requests use the existing Nuvio UI and parsers.

TMDB credentials come from Nuvio's settings at request time. Cinemana catalog browsing and direct playback do not require TMDB. External TMDB/IMDb identity resolution requires enabled TMDB settings and rejects ambiguous matches. Streams always use Cinemana video IDs. No API key is committed.

## Automated coverage

The standalone JVM contract suite compiles the same client, resource adapter, matcher and episode mapping shipped in the Android overlay. It checks manifest resources; URL routing/escaping; repeated search pages; opaque category cursors; movie/series identities; stream filtering; subtitle selection; external series S/E resolution; ambiguous matches; cancellation; rootless episode expansion; TMDB failure; and continuous anime numbering.

Run using the pinned upstream Gradle wrapper:

```sh
/path/to/upstream/gradlew -p tests/cinemana-jvm test
python3 scripts/apply-mobile-overlay.py /path/to/upstream
cd /path/to/upstream
touch local.properties
./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -PnuvioDebugApplicationId=com.ali92barca.nuvio.cinemana.debug
```

CI artifact `mobile-build-reports` holds test reports. `nuvio-cinemana-mobile-debug` contains APKs only when assembly succeeds. A green fixture suite alone does not prove installation or live Cinemana access.

## Verified build — 29 October 2026

Code commit: `7b4c3840a9b21e6d437e2d07ebabbf81f650d7a0`.

- All 14 Cinemana JVM resource-contract tests passed.
- `:androidApp:assembleFullDebug` succeeded on GitHub Actions.
- Build reports and the mobile APK artifact were uploaded successfully.
- Run: https://github.com/Ali92Barca/Nuvio-Cinemana-Enhanced/actions/runs/40850276657
- APK artifact: `nuvio-cinemana-mobile-debug` (ZIP, approximately 170 MB).
- Installation and live playback await the user's practical mobile testing. No emulator or real-device playback result is claimed.

## Required device acceptance (not yet verified)

- Install and cold-launch the APK on Android mobile; verify the independent app ID.
- Cinemana appears on home/discover and in global search; both movies and series have separate category catalogs.
- Browse multiple pages; confirm series are not repeated per episode.
- Open a movie and play each available quality; select Arabic/English subtitles.
- Open a multi-season series and select an episode from each season.
- Verify a rootSeries=0 anime with a known missing episode and continuous episode numbering.
- Open IMDb/TMDB content from another addon and verify Cinemana and other providers both appear.
- Disable TMDB or simulate a TMDB failure; direct Cinemana metadata and playback remain usable.
- Disable/re-enable Cinemana; switch profiles; verify other addons and enabled states survive server sync.
- Disconnect networking; confirm errors are shown without crashing or selecting an unrelated episode.

Cinemana API/CDN availability depends on the device network. Fixture tests cannot certify live endpoint behavior. No TV completion or production release is claimed until these checks pass.

## Regression update 0.7.1 — build verified, pending device retest

The user's first mobile trial reported missing TMDB matches, no Cinemana streams for some external-catalog series (including المدينة البعيدة and القضاء), and ascending source quality.

Changes:

- Match all Cinemana title aliases across configured, English, Arabic and Turkish TMDB locales, while keeping exact normalized identity and rejecting ambiguous works.
- Accept a Cinemana season's later year only within a verified TMDB first/last-air interval; movie identity still requires the movie's release year.
- Include TMDB alternative titles in external-catalog search and continue paging past additional episodes of an already seen series.
- Pass Nuvio's parent-show ID and explicit season/episode into Cinemana stream requests in both source selection and player requests. Preserve other providers' IDs and real Cinemana episode IDs.
- Resolve an existing season-endpoint episode immediately; perform broad episode expansion only if it is missing.
- Sort direct sources by numeric height descending, including 4K/UHD, pixel dimensions, FHD/HD/SD and p/i labels; unknown qualities come last.

The 22-test suite includes synthetic fixtures using the reported titles. Those fixtures are not recordings of live Cinemana responses. Actual playback for both reported series still requires the user's device/network retest. TV remains blocked.

Automated validation for code commit `ac1454798bb0bd20ba2aed8dc266465f8897e446`: 22 tests passed, FullDebug assembly succeeded, and the APK was uploaded. Build run: https://github.com/Ali92Barca/Nuvio-Cinemana-Enhanced/actions/runs/40875986729

## Combined 0.7.2 — Bingers and Cinemana editions

- Bingers is an optional Android completion-only integration, disabled by default. The card appears under Settings → Tracking. The user links a fresh official magic link inside the app and separately enables sync.
- Nuvio's 80% STOP completion event queues a movie or episode; Bingers identity is resolved from external IDs and exact season/episode. Native Cinemana content is enriched with available title/year/TMDB identity before catalog matching.
- Remote watch history is checked before sending; existing watches are not counted again. Queued writes are encrypted per Nuvio account/profile, and retry uses the original persisted batch IDs.
- Cinemana stream resolution retains separate dubbed/subtitled roots and multiple same-numbered episode videos under one verified series root. Rootless grouping includes explicit edition markers from all title aliases.
- Source descriptions show the available title, explicit edition, resolution and container. Duration appears only when its format or units are known. Unknown audio/subtitle edition is labelled generically rather than guessed.
- Subtitle picker labels distinguish editions when Cinemana provides that information. Different cuts or dubbed episode numbering are not automatically aligned by guessed runtime offsets.
- A failed source edition does not hide the other edition's playable links.

Bingers authentication and free-account write permission remain unverified without the user's own account/device. The reported القضاء series still requires live retesting on the user's Cinemana network. No TV work has started.

Build verification for combined code commit `7e8cfccc8817456073a47d8eacff2965431e6dca`:
- All 39 protocol/resource fixture tests passed (28 Cinemana, 11 Bingers).
- `:composeApp:compileAndroidMain` passed.
- `:androidApp:assembleFullDebug` passed; the combined APK artifact was uploaded.
- Run: https://github.com/Ali92Barca/Nuvio-Cinemana-Enhanced/actions/runs/40884080564

This verifies compilation and fixture behavior. It does not certify a live Bingers session/free-account write, live Cinemana sources for القضاء, or real-device playback. Those remain the user's acceptance tests.

## Follow-up: Far Away sources and pending Bingers watch

User confirmed Yargi sources now appear in 0.7.2, but Far Away still does not,
and a completed episode produces one pending Bingers item. The investigation initially excluded APK creation. The user subsequently
authorized a combined APK after adding a Downloads navigation tab.

Code changes in ccce2563526a23f98e5bab3cf1c3bdbafbb32f64:
- Search Cinemana with both original and accent-free Turkish spellings, plus
  edition-free titles. Identity matching remains exact after normalization.
- Merge aliases across search queries for a common series root, rather than
  keeping only the first result. Isolate each series root's episode lookup failure.
- Recover typed TMDB IDs (e.g. `tmdb:tv:274556`) from queued catalog IDs; the host
  tracker parser currently only understands the untyped `tmdb:274556` form.
  This also applies when retrying already queued items. Prefer verified TMDB
  show titles over episode-specific playback titles when searching Bingers.
- Persist Bingers status messages across settings refreshes, separate unresolved
  titles from queued sends, retain each unresolved reason, and identify the
  resolve/read/push stage. Previously refresh replaced an error with 'linked'.

Public Bingers GET requests verified Far Away's show metadata and season index:
TMDB 274556, IMDb tt34063491, official show name Uzak Şehir, Arabic title
المدينة البعيدة. No authenticated history reads or writes were performed.
Cinemana API returned a Site Unavailable HTML page in this environment; therefore
neither the exact cause of this user's missing source nor their Bingers queue
failure is proven. The new spelling fixture reproduces one possible search failure,
not a capture of the live Cinemana response.

Before deciding to build, request the failing Far Away season/episode and edition,
and the watched Bingers show/episode, playback catalog, and status text immediately
after pressing 'check session and retry'. No credentials are needed.

### Downloads mobile navigation

Downloads now appears between Search and Live TV in classic/floating phone bars
and the mobile tablet top bar. Android settings no longer includes its old row;
legacy routes/deep links and Library entry points still work. The tab opens the
existing downloads list with playback, management and nested series browsing.
It hides the redundant root back arrow and handles Back from a nested series.
Six-item floating bars use narrower outer margins to preserve item space on phones.
TV navigation is not extended. No device/UI runtime verification is available here.

The prior fix-only workflow 40909008570 passed tests and Android compilation,
with APK assembly skipped as requested at that point. The following navigation
commit requests the user-authorized combined APK via the normal workflow marker.
