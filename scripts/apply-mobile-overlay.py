#!/usr/bin/env python3
"""Apply checked integration seams; fail on upstream drift instead of silently omitting wiring."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1])
overlay = Path(__file__).resolve().parents[1] / 'mobile-overlay'

def replace(path, old, new):
    target = root / path
    text = target.read_text()
    if new in text:
        return
    if text.count(old) != 1:
        raise RuntimeError(f'Upstream integration seam changed: {path}: {old[:70]!r}')
    target.write_text(text.replace(old, new))

platform = 'composeApp/src/androidMain/kotlin/com/nuvio/app/features/addons/AddonPlatform.android.kt'
replace(platform, 'actual suspend fun httpGetText(url: String): String =\n    executeTextRequest(',
        'actual suspend fun httpGetText(url: String): String =\n    com.nuvio.android.cinemana.CinemanaProvider.handle(url) ?: executeTextRequest(')
# The built-in provider is always discoverable and can be disabled through the normal addon switch.
# Keep its enabled state in existing per-profile storage; no new account or server is needed.
repo = 'composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonRepository.kt'
# Preserve upstream empty-server migration semantics before appending the local provider.
replace(repo, 'addons = urls.map { url ->', 'addons = dedupeManifestUrls(urls).map { url ->')
replace(repo, '            urls.forEach { url ->', '            dedupeManifestUrls(urls).forEach { url ->')
replace(repo, 'val addons = localUrls.mapIndexed { index, addonUrl ->',
        'val addons = localUrls.filterNot { it in AddonStorage.bundledAddonUrls() }.mapIndexed { index, addonUrl ->')
replace(repo, '.distinctBy { it.manifestUrl }\n                    .mapIndexed',
        '.distinctBy { it.manifestUrl }\n                    .filterNot { it.manifestUrl in AddonStorage.bundledAddonUrls() }\n                    .mapIndexed')
replace(repo, 'urls.map(::ensureManifestSuffix).distinct()',
        '(urls + AddonStorage.bundledAddonUrls()).map(::ensureManifestSuffix).distinct()')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/AddonPlatform.kt',
        'internal expect object AddonStorage {',
        'internal expect object AddonStorage {\n    fun bundledAddonUrls(): List<String>')
replace(platform, 'actual object AddonStorage {',
        'actual object AddonStorage {\n    actual fun bundledAddonUrls(): List<String> = listOf(com.nuvio.android.cinemana.CinemanaProvider.MANIFEST_URL)')
replace('composeApp/src/iosMain/kotlin/com/nuvio/app/features/addons/AddonPlatform.ios.kt',
        'actual object AddonStorage {',
        'actual object AddonStorage {\n    actual fun bundledAddonUrls(): List<String> = emptyList()')
for filename in ('features/streams/StreamsRepository.kt', 'features/player/PlayerStreamsRepository.kt'):
    path = 'composeApp/src/commonMain/kotlin/com/nuvio/app/' + filename
    replace(path, 'val supportsRequestedStream = manifest.resources.any { resource ->',
        'val requestedVideoId = com.nuvio.android.cinemana.CinemanaRequestIdentity.streamId(manifest.id, type, videoId, parentMetaId, season, episode)\n                val supportsRequestedStream = manifest.resources.any { resource ->')
    replace(path, 'resource.idPrefixes.any { videoId.startsWith(it) }',
        'resource.idPrefixes.any { requestedVideoId.startsWith(it) }')
    replace(path, 'resource = "stream",\n                        type = type,\n                        id = videoId,',
        'resource = "stream",\n                        type = type,\n                        id = com.nuvio.android.cinemana.CinemanaRequestIdentity.streamId(addon.manifest.id, type, videoId, parentMetaId, season, episode),')
replace('composeApp/build.gradle.kts', 'getByName("androidHostTest") {',
        'getByName("androidHostTest") {\n            dependencies { implementation("org.json:json:20240303") }')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/SubtitleRepository.kt',
        'display = getString(\n                                            Res.string.player_addon_subtitle_display_format,',
        'display = obj.stringValue("label")?.takeIf { manifest.id == "org.nuvio.cinemana.builtin" && it.isNotBlank() } ?: getString(\n                                            Res.string.player_addon_subtitle_display_format,')
# Bingers remains optional and profile-scoped, independent of Trakt/Simkl.
replace(platform, 'preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)',
        'preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)\n        com.nuvio.app.features.bingers.BingersPlatform.initialize(context)')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/tracking/TrackingScrobbleCoordinator.kt',
        '        TrackingProviderRegistry.ensureLoaded()\n        val failures = dispatchTrackingScrobble(',
        '        com.nuvio.app.features.bingers.BingersPlatform.onScrobble(profileId, action, event)\n        TrackingProviderRegistry.ensureLoaded()\n        val failures = dispatchTrackingScrobble(')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/TrackingSettingsPage.kt',
        '    if (!simklUiState.credentialsConfigured || simklUiState.manualClientId.isNotBlank()) {',
        '    item { com.nuvio.app.features.bingers.BingersSettingsContent(isTablet) }\n\n    if (!simklUiState.credentialsConfigured || simklUiState.manualClientId.isNotBlank()) {')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/profiles/ProfileRepository.kt',
        '    suspend fun deleteProfile(profileIndex: Int) {',
        '    suspend fun deleteProfile(profileIndex: Int) {\n        com.nuvio.app.features.bingers.BingersPlatform.removeProfile(profileIndex)')
shutil.copytree(overlay, root, dirs_exist_ok=True)
print('Mobile Cinemana transport and bundled provider applied')
