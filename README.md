# Nuvio Cinemana Enhanced

Community project to integrate a Cinemana provider directly into Nuvio-based Android mobile and Android TV applications, without a persistent local Node.js/Termux server.

## Project goals
- **Mobile:** evaluate the NuvioMobile-Enhanced codebase and maintain a separately signed Android build.
- **TV:** evaluate upstream NuvioTV and maintain a separately signed Android TV build.
- **Cinemana integration:** migrate the existing catalog, search, metadata mapping, season/episode mapping, and stream-resolution logic into an in-app provider.
- **TMDB:** reuse the host application's configured TMDB credentials/services where supported; never commit API keys.
- **Updates:** GitHub Actions CI, upstream monitoring, signed releases, and in-app update checking with user-confirmed installation.
- **Connectivity:** use the device's own network connection to access authorized Cinemana endpoints. No Cloudflare proxy required.

## Status
**Planning / source audit.** No working APK, embedded provider, automated upstream sync, or updater is claimed yet.

## Proposed layout
- `docs/` architecture, requirements and migration notes
- `mobile/` Android mobile integration (after upstream code/license review)
- `tv/` Android TV integration (after upstream code/license review)
- `shared/` provider contracts and test fixtures (where practical)
- `.github/workflows/` CI and guarded update workflows

## Upstream candidates
- Mobile: https://github.com/AKRusso/NuvioMobile-Enhanced (candidate; to be audited)
- TV: https://github.com/NuvioMedia/NuvioTV (candidate; to be audited)

This is an independent community project and is not an official Nuvio release. Preserve upstream licenses and attribution when importing source code. Do not publish proprietary credentials, private user data, or unauthorized content.
