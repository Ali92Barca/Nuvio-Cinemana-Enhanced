# Mobile-first execution plan

**Scope lock:** Android Mobile only. Android TV development is deferred until Mobile has a tested release and the user approves moving on.

## Source baseline
- Candidate: `AKRusso/NuvioMobile-Enhanced`, default branch `enhanced`.
- Legacy code to review: `Ali92Barca/Nuvio-Cinemana` (branch `dev`), `Ali92Barca/cinemana-addon`, `Ali92Barca/Cinemana-plugin-for-Nuvio`.
- Upstream repositories remain untouched and are not automatically merged.

## Release gates
- [ ] Audit source tree, dependencies, license and Android build instructions
- [ ] Establish reproducible unsigned Mobile debug build
- [ ] Extract Cinemana catalog/search/details/streams behavior and create tests
- [ ] Port Cinemana API to Android in-process (no localhost/Node/Termux)
- [ ] Connect TMDB via the actual host settings/service interface
- [ ] Validate movie and series metadata/episode mapping
- [ ] Validate stream ordering and playback on user device/network
- [ ] Add secure GitHub release updater and signing
- [ ] Publish signed Mobile APK only after CI and device validation
- [ ] Obtain explicit go-ahead before starting Android TV

## Constraints
- Do not commit signing keys or TMDB credentials.
- Verify licensing and upstream attribution before importing code.
- Preserve all legacy repositories as backup.
- No claim of a completed APK until build and installation are tested.
