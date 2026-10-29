package com.nuvio.app.features.bingers

import androidx.compose.runtime.Composable
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent

actual object BingersPlatform {
    actual fun onScrobble(profileId: Int, action: TrackingScrobbleAction, event: TrackingScrobbleEvent) {}
    actual fun removeProfile(profileId: Int) {}
}
@Composable
actual fun BingersSettingsContent(isTablet: Boolean) {}
