package com.nuvio.app.features.bingers

import androidx.compose.runtime.Composable
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent

expect object BingersPlatform {
    fun onScrobble(profileId: Int, action: TrackingScrobbleAction, event: TrackingScrobbleEvent)
    fun removeProfile(profileId: Int)
}

@Composable
expect fun BingersSettingsContent(isTablet: Boolean)
