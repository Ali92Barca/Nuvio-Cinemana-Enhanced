# Mobile validation and TV gate

Target: Android mobile only. TV work must remain blocked until all device checks below pass.

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
