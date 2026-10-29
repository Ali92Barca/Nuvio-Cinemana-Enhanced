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
