# Architecture and delivery plan

## Non-negotiable requirements
1. Separate Android mobile and Android TV APKs with stable package names and independent signing keys.
2. Catalog, search, details, episode mapping, and streams provided **in process**; no localhost HTTP server, Termux, or always-on background process.
3. Requests to Cinemana originate on the user's device/network. Check endpoint reachability and access rights; do not assume cloud connectivity.
4. Reuse host TMDB configuration through an adapter. Never hardcode or expose user API keys.
5. Keep upstream changes isolated from the Cinemana provider to ease rebasing.

## Components
- **Host adapters (Mobile / TV):** navigation, settings, metadata, playback and update UI.
- **Cinemana provider:** API client, title/ID matching, catalog, search, detail, seasons/episodes, stream resolution and quality ordering.
- **TMDB adapter:** host-managed TMDB configuration and ID resolution.
- **Updater:** check our GitHub Releases; download platform-specific APK; validate release metadata and signing continuity; request Android user confirmation to install.
- **CI:** compile and test on every PR. Upstream sync should open a PR, never publish untested changes.

## Migration acceptance tests
- Film catalog and series catalog load.
- Search returns relevant results.
- IMDb/TMDB mapping resolves the intended title.
- Season/episode mapping handles absolute-numbered episodes (including previously tested S2E2 -> episode 30 scenario).
- Stream links are ordered 1080p, 720p, 480p, 360p, 240p where available.
- Playback works on an eligible Iraqi network.
- No Termux/Node processes needed.
- Update flow preserves application data and requires compatible signature.

## Milestones
- [x] Initialize project repository and architecture
- [ ] Audit mobile and TV upstream code, build requirements and licenses
- [ ] Import permitted upstream source with attribution
- [ ] Recover and audit previous Cinemana JavaScript implementation
- [ ] Implement in-process provider and tests
- [ ] Integrate host TMDB settings
- [ ] Build and test Mobile APK
- [ ] Build and test TV APK
- [ ] Configure CI, signed releases and update checking

## Security and licensing
- Never commit keystores, signing passwords, access tokens, or private API credentials.
- Follow applicable GPLv3 source-distribution and attribution obligations for upstream code.
- Do not automatically publish upstream merges if tests fail.
- Android package updates require matching package ID and signing certificate; silent installation is not generally available.
