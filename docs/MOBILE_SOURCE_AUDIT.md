# Mobile upstream audit — initial verified findings

Source: https://github.com/AKRusso/NuvioMobile-Enhanced/tree/enhanced

Verified directly from source files on branch `enhanced`:
- `settings.gradle.kts`: Kotlin/Gradle project with modules `:composeApp` and `:androidApp`.
- `gradle/libs.versions.toml`: Kotlin 2.3.0, Android Gradle Plugin 9.2.0, compile SDK 37, min SDK 24, target SDK 36, Ktor 3.4.1, Compose Multiplatform 1.11.1, Media3 1.8.0, and QuickJS-KT 1.0.5.
- `LICENSE`: GNU GPL version 3. Attribution and GPL obligations must be preserved when importing code.
- `README.md`: upstream claims Enhanced features including libmpv playback, M3U/EPG Live TV, Trakt/Simkl, AI assistants and GitHub Actions Android builds. These are upstream descriptions, not independently tested functionality.
- Upstream default branch is `enhanced` (not `main`).

## Engineering implications
1. Reuse Kotlin/Ktor networking and existing host metadata/settings integrations rather than running a Node HTTP bridge.
2. Inspect existing addon/plugin and QuickJS code before deciding between a native Kotlin provider and in-process JavaScript reuse.
3. Inspect TMDB settings storage and existing metadata service before wiring a credential adapter. Do not presume the API key is accessible as plain text.
4. Preserve GPL-3.0 license and attribution when copying the upstream project.
5. Establish a reproducible debug build **before** altering playback or catalogs.

## Not yet verified
- Exact TMDB settings classes or credential exposure.
- Source paths for addon catalogs, stream resolvers and QuickJS integration.
- Compatibility of prior Cinemana service implementation with in-process execution.
- A successful build, installation or playback.
- A release-signing strategy.

## Next inspection
Read Android app Gradle configuration, shared module source, settings provider, addon interfaces and legacy Cinemana code. Import source only after identifying a safe integration seam.
