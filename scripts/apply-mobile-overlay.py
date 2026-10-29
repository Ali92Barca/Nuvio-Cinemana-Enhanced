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
# Downloads is a first-class mobile tab; retain existing download routes/deep links.
app = 'composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt'
replace(app, '    Search,\n    LiveTv,', '    Search,\n    Downloads,\n    LiveTv,')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/NativeTabBridge.kt',
        '    Search,\n    LiveTv,', '    Search,\n    Downloads,\n    LiveTv,')
replace(app, '    AppScreenTab.Search -> NativeNavigationTab.Search',
        '    AppScreenTab.Search -> NativeNavigationTab.Search\n    AppScreenTab.Downloads -> NativeNavigationTab.Downloads')
replace(app, '    NativeNavigationTab.Search -> AppScreenTab.Search',
        '    NativeNavigationTab.Search -> AppScreenTab.Search\n    NativeNavigationTab.Downloads -> AppScreenTab.Downloads')
replace(app, '            AppScreenTab.LiveTv -> Unit',
        '            AppScreenTab.Downloads -> Unit\n            AppScreenTab.LiveTv -> Unit')
replace(app, 'import androidx.compose.material.icons.filled.Tv',
        'import androidx.compose.material.icons.filled.Tv\nimport androidx.compose.material.icons.rounded.CloudDownload')
# Phone classic and floating styles use the same tab order.
for floating in (False, True):
    marker = ('                                        if (liveTvEnabled) {\n'
              '                                            NavItem(\n'
              '                                                selected = selectedTab == AppScreenTab.LiveTv,\n'
              '                                                onClick = { handleRootTabClick(AppScreenTab.LiveTv) },\n'
              '                                                icon = Icons.Filled.Tv,\n'
              '                                                contentDescription = stringResource(Res.string.compose_nav_live_tv),\n'
              + ('                                                label = stringResource(Res.string.compose_nav_live_tv),\n' if floating else '') +
              '                                            )\n'
              '                                        }')
    item = ('                                        NavItem(\n'
            '                                            selected = selectedTab == AppScreenTab.Downloads,\n'
            '                                            onClick = { handleRootTabClick(AppScreenTab.Downloads) },\n'
            '                                            icon = Icons.Rounded.CloudDownload,\n'
            '                                            contentDescription = stringResource(Res.string.compose_settings_root_downloads_title),\n'
            + ('                                            label = stringResource(Res.string.compose_settings_root_downloads_title),\n' if floating else '') +
            '                                        )\n')
    replace(app, marker, item + marker)
replace(app, '                AppScreenTab.LiveTv -> {',
        '                AppScreenTab.Downloads -> {\n'
        '                    DownloadsScreen(\n'
        '                        onBack = {},\n'
        '                        onOpenDownload = { item -> onLibraryDownloadClick?.invoke(item) },\n'
        '                        isRootTab = true,\n'
        '                    )\n'
        '                }\n\n'
        '                AppScreenTab.LiveTv -> {')
# Mobile tablets also expose Downloads between Search and Live TV.
marker = '                if (liveTvEnabled) {\n                    TabletTopPillItem('
item = '                TabletTopPillItem(\n                    label = stringResource(Res.string.compose_settings_root_downloads_title),\n                    selected = selectedTab == AppScreenTab.Downloads,\n                    onClick = { onTabSelected(AppScreenTab.Downloads) },\n                    icon = {\n                        Icon(\n                            imageVector = Icons.Rounded.CloudDownload,\n                            contentDescription = stringResource(Res.string.compose_settings_root_downloads_title),\n                            modifier = Modifier.size(NuvioTokens.Space.s18),\n                            tint = if (selectedTab == AppScreenTab.Downloads) tokens.colors.textPrimary else tokens.colors.textMuted,\n                        )\n                    },\n                )\n'
replace(app, marker, item + marker)
replace(app, '                                    NuvioNavigationBar(\n                                        modifier =',
        '                                    NuvioNavigationBar(\n                                        itemCount = if (liveTvEnabled) 6 else 5,\n                                        modifier =')
bar = 'composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/NavigationBar.kt'
replace(bar, 'fun NuvioNavigationBar(\n    modifier:',
        'fun NuvioNavigationBar(\n    itemCount: Int = 5,\n    modifier:')
replace(bar, '    val expandedHorizontalPadding = 28.dp\n    val collapsedHorizontalPadding = 58.dp',
        '    val expandedHorizontalPadding = if (itemCount > 5) 8.dp else 28.dp\n    val collapsedHorizontalPadding = if (itemCount > 5) 8.dp else 58.dp')
settings = 'composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsRootPage.kt'
old = '                    SettingsNavigationRow(\n                        title = stringResource(Res.string.compose_settings_root_downloads_title),\n                        description = stringResource(Res.string.compose_settings_root_downloads_description),\n                        icon = Icons.Rounded.CloudDownload,\n                        isTablet = isTablet,\n                        onClick = onDownloadsClick,\n                    )\n                    SettingsGroupDivider(isTablet = isTablet)\n'
replace(settings, old, '                    if (com.nuvio.app.core.ui.isTvLayoutProfileEnabled() || com.nuvio.app.isIos) {\n' + old + '                    } // Downloads is a mobile Android tab.\n')
downloads = 'composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsScreen.kt'
replace(downloads, '    initialShowId: String? = null,',
        '    initialShowId: String? = null,\n    isRootTab: Boolean = false,')
replace(downloads, '    val openDownloadsDirectoryFailedText =',
        '    com.nuvio.app.core.ui.PlatformBackHandler(enabled = isRootTab && selectedShowId != null) { selectedShowId = null }\n    val openDownloadsDirectoryFailedText =')
replace(downloads, '                onBack = {\n                    if (selectedShowId != null) {\n                        onBackFromShow?.invoke() ?: run { selectedShowId = null }\n                    } else {\n                        onBack()\n                    }\n                },\n                actions = {',
        '                onBack = if (isRootTab && selectedShowId == null) null else ({\n                    if (selectedShowId != null) {\n                        onBackFromShow?.invoke() ?: run { selectedShowId = null }\n                    } else {\n                        onBack()\n                    }\n                }),\n                actions = {')

# Preserve exact-edition subtitles through download, local caching and playback.
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamParser.kt', '                clientResolve = clientResolve,',
        '                clientResolve = clientResolve,\n                externalSubtitles = if (addonId == "org.nuvio.cinemana.builtin" || addonId.startsWith("addon:org.nuvio.cinemana.builtin:")) {\n                    (obj["subtitles"] as? JsonArray)?.mapNotNull { value ->\n                        val track = value as? JsonObject ?: return@mapNotNull null\n                        val trackUrl = track.string("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }\n                            ?: return@mapNotNull null\n                        StreamSubtitle(trackUrl, track.string("lang") ?: "und", track.string("name"))\n                    }.orEmpty()\n                } else emptyList(),')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt', '                sourceUrl = sourceUrl,\n                sourceHeaders = emptyMap(),\n                sourceResponseHeaders = emptyMap(),\n                externalSubtitles = emptyList(),',
        '                sourceUrl = sourceUrl,\n                sourceHeaders = emptyMap(),\n                sourceResponseHeaders = emptyMap(),\n                externalSubtitles = item.externalSubtitles.filter { it.url.startsWith("file:") || it.url.startsWith("content:") },')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt', '                        sourceUrl = localSourceUrl,\n                        sourceHeaders = emptyMap(),\n                        sourceResponseHeaders = emptyMap(),\n                        externalSubtitles = emptyList(),',
        '                        sourceUrl = localSourceUrl,\n                        sourceHeaders = emptyMap(),\n                        sourceResponseHeaders = emptyMap(),\n                        externalSubtitles = downloadedItem.externalSubtitles.filter { it.url.startsWith("file:") || it.url.startsWith("content:") },')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.kt', '    val destinationFileName: String,',
        '    val destinationFileName: String,\n    val subtitles: List<StreamSubtitle> = emptyList(),\n    val onSubtitlesCached: ((List<StreamSubtitle>) -> Unit)? = null,')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', 'import kotlinx.coroutines.flow.MutableStateFlow',
        'import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.SupervisorJob\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.flow.MutableStateFlow')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', '    private val stateLock = SynchronizedObject()',
        '    private val subtitleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n    private val subtitleRetries = mutableSetOf<String>()\n    private val stateLock = SynchronizedObject()')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', '    fun retryDownload(downloadId: String) {\n        resumeDownload(downloadId)\n    }',
        '    fun retryDownload(downloadId: String) {\n        val item = _uiState.value.items.firstOrNull { it.id == downloadId }\n        if (item?.status == DownloadStatus.Completed) {\n            if (!synchronized(stateLock) { subtitleRetries.add(downloadId) }) return\n            subtitleScope.launch {\n                try {\n                val cached = DownloadsPlatformDownloader.cacheSubtitleFiles(item.externalSubtitles, item.fileName)\n                mutateItem(downloadId) { current -> current.copy(\n                    externalSubtitles = cached.ifEmpty { current.externalSubtitles },\n                    errorMessage = if (cached.any { it.url.startsWith("http") }) "الفيديو محفوظ؛ تعذر حفظ بعض الترجمات. أعد محاولة الترجمة" else null,\n                ) }\n                } finally { synchronized(stateLock) { subtitleRetries.remove(downloadId) } }\n            }\n        } else resumeDownload(downloadId)\n    }')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', '            sourceHeaders = item.sourceHeaders,\n            destinationFileName = item.fileName,\n        )',
        '            sourceHeaders = item.sourceHeaders,\n            destinationFileName = item.fileName,\n            subtitles = item.externalSubtitles,\n            onSubtitlesCached = { cached ->\n                mutateItem(item.id) { current -> current.copy(externalSubtitles = cached.ifEmpty { current.externalSubtitles }) }\n            },\n        )')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', '                mutateItem(item.id) { current ->\n                    val cachedSubtitles = DownloadsPlatformDownloader.cacheSubtitleFiles(\n                        subtitles = current.externalSubtitles,\n                        companionBaseFileName = current.fileName,\n                    )\n                    current.copy(',
        '                val latestItem = _uiState.value.items.firstOrNull { it.id == item.id } ?: item\n                val cachedSubtitles = DownloadsPlatformDownloader.cacheSubtitleFiles(\n                    subtitles = latestItem.externalSubtitles,\n                    companionBaseFileName = latestItem.fileName,\n                )\n                mutateItem(item.id) { current ->\n                    current.copy(')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsRepository.kt', '                        totalBytes = totalBytes?.takeIf { it > 0L } ?: current.totalBytes,\n                        downloadSpeedBytesPerSecond = 0L,\n                        errorMessage = null,',
        '                        totalBytes = totalBytes?.takeIf { it > 0L } ?: current.totalBytes,\n                        downloadSpeedBytesPerSecond = 0L,\n                        errorMessage = if (cachedSubtitles.any { it.url.startsWith("http") }) "الفيديو محفوظ؛ تعذر حفظ بعض الترجمات. أعد محاولة الترجمة" else null,')
replace('composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.android.kt', 'private const val downloadBufferBytes',
        'private val subtitleHttpClient = downloadHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()\n\nprivate const val downloadBufferBytes')
replace('composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.android.kt', '                var lastFailure: Throwable? = null\n                repeat(downloadMaxAttempts)',
        '                if (request.subtitles.isNotEmpty()) {\n                    val cached = cacheSubtitleFiles(request.subtitles, request.destinationFileName)\n                    ensureActive()\n                    request.onSubtitlesCached?.invoke(cached)\n                }\n                var lastFailure: Throwable? = null\n                repeat(downloadMaxAttempts)')
replace('composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.android.kt', 'downloadHttpClient.newCall(request).execute().use { response ->',
        'subtitleHttpClient.newCall(request).execute().use { response ->')
replace('composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.android.kt', '                subtitle.copy(\n                    url = destination.toURI().toString(),\n                    headers = null,\n                )\n            }.getOrNull()',
        '                subtitle.copy(\n                    url = destination.toURI().toString(),\n                    headers = null,\n                )\n            }.getOrNull() ?: subtitle')
replace('composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsPlatformDownloader.android.kt', '                    destination.outputStream().use { output ->\n                        body.byteStream().copyTo(output)\n                    }',
        '                    val bytes = body.byteStream().use { input ->\n                        val output = java.io.ByteArrayOutputStream()\n                        val buffer = ByteArray(8192)\n                        while (true) {\n                            val count = input.read(buffer)\n                            if (count < 0) break\n                            check(output.size() + count <= 5 * 1024 * 1024)\n                            output.write(buffer, 0, count)\n                        }\n                        output.toByteArray()\n                    }\n                    check(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024)\n                    check(!String(bytes.take(256).toByteArray(), Charsets.UTF_8).contains("<html", true))\n                    val temporary = File(destination.parentFile, destination.name + ".part")\n                    try {\n                        temporary.writeBytes(bytes)\n                        check(temporary.renameTo(destination))\n                    } finally { temporary.delete() }')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsScreen.kt', '                        DownloadStatus.Completed -> {\n                            IconButton(onClick = onOpen)',
        '                        DownloadStatus.Completed -> {\n                            if (!item.errorMessage.isNullOrBlank()) {\n                                IconButton(onClick = onRetry) {\n                                    Icon(Icons.Rounded.Refresh, contentDescription = "إعادة تحميل الترجمة")\n                                }\n                            }\n                            IconButton(onClick = onOpen)')
replace('composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadsScreen.kt', '        DownloadStatus.Completed -> stringResource(\n            Res.string.downloads_status_completed,',
        '        DownloadStatus.Completed -> item.errorMessage ?: stringResource(\n            Res.string.downloads_status_completed,')

shutil.copytree(overlay, root, dirs_exist_ok=True)
print('Mobile Cinemana transport and bundled provider applied')
