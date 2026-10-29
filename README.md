# Nuvio Cinemana Enhanced

Native Cinemana integration for **Nuvio Android Mobile**, without Node, Termux or a local HTTP server. Android TV remains deferred until mobile acceptance is complete.

## Implemented

- Bundled provider using Nuvio's existing addon catalog, global search, metadata, stream and subtitle paths.
- Fifteen categories for both movies and series, with cursor-aware paging.
- Cinemana metadata fallback, optional TMDB enrichment from the app's existing settings, quality selection and signed subtitle tracks.
- Multi-season episode traversal, missing-episode search expansion, rootless anime grouping and conservative continuous-number mapping.
- Cinemana sources for external TMDB/IMDb catalog items alongside existing providers; ambiguous identities are rejected.
- Separate Android debug identity: `com.ali92barca.nuvio.cinemana.debug`.

## Build and validation

[Mobile build workflow](../../actions/workflows/mobile-build.yml) pins upstream source, applies the checked overlay, runs JVM resource-contract tests and assembles a mobile debug APK. Download APKs from a **successful** run's `nuvio-cinemana-mobile-debug` artifact. No signed production release is claimed.

[Validation and device acceptance](docs/MOBILE_VALIDATION.md) records the remaining live checks. Compilation and fixture tests do not establish successful playback on a real device. **Mobile is not considered complete until device acceptance passes.**

## Source layout

- `mobile-overlay/composeApp/src/androidMain/`: native Cinemana data and resource adapter.
- `mobile-overlay/composeApp/src/androidHostTest/`: deterministic resource fixtures.
- `tests/cinemana-jvm/`: JVM runner for the same provider code, without requiring an Android emulator.
- `scripts/apply-mobile-overlay.py`: checks and applies host integration seams.
- `.github/workflows/mobile-build.yml`: reproducible build and test reports.

Upstream: [AKRusso/NuvioMobile-Enhanced](https://github.com/AKRusso/NuvioMobile-Enhanced), pinned to `ac03c46a159e7e4bbbcd77ad4bdf6ea604b34107`. Its GPL-3.0 license and notices remain in the checked-out source and apply to derivative distributions. This repository supplies the overlay and exact reconstruction steps; it is an independent community project.

TMDB is optional and requires your configured credentials. Cinemana access uses the device's network directly. Bingers remains an optional experiment and does not block mobile Cinemana integration.
