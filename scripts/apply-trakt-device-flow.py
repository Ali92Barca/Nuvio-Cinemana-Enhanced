"""Called by apply-mobile-overlay.py: checked patches for public-client Trakt device login."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
repo = root / 'composeApp/src/commonMain/kotlin/com/nuvio/app/features/trakt/TraktAuthRepository.kt'
text = repo.read_text()
if '// Cinemana public-client device authorization' not in text:
    def replace(old, new):
        global text
        if text.count(old) != 1:
            raise RuntimeError('Trakt integration seam changed: ' + old[:100])
        text = text.replace(old, new)

    replace('import kotlinx.coroutines.CancellationException', '''import com.nuvio.android.trakt.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException''')
    replace('    private val refreshMutex = Mutex()', '''    private val refreshMutex = Mutex()
    // Cinemana public-client device authorization: no secret, callback or localhost listener.
    private var deviceJob: Job? = null
    private var deviceSession: DeviceSession? = null
    private var deviceGeneration = 0L

    private fun cancelDeviceLogin() {
        deviceGeneration += 1
        deviceJob?.cancel()
        deviceJob = null
        deviceSession = null
    }

    private fun startDeviceLogin(profileId: Int) {
        cancelDeviceLogin()
        clearPendingAuthorization()
        val generation = deviceGeneration
        val profileVersion = profileGeneration
        fun isCurrent() = generation == deviceGeneration && profileVersion == profileGeneration &&
            currentProfileId == profileId && ProfileRepository.activeProfileId == profileId
        publish(isLoading = true, statusMessage = "جاري طلب رمز تفعيل Trakt…", errorMessage = null)
        deviceJob = scope.launch {
            try {
                val flow = TraktDeviceFlow(TraktConfig.CLIENT_ID, { path, body ->
                    val reply = httpRequestRaw(method = "POST", url = "$BASE_URL$path", body = body,
                        headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"))
                    DeviceReply(reply.status, reply.body)
                }, TraktPlatformClock::nowEpochMs)
                val session = flow.begin()
                if (!isCurrent()) return@launch
                deviceSession = session
                authState = authState.copy(pendingAuthorizationState = "device",
                    pendingAuthorizationStartedAtMillis = TraktPlatformClock.nowEpochMs())
                publish(isLoading = false, statusMessage = "رمز Trakt: ${session.userCode}\\nافتح ${session.verificationUrl} وأدخل الرمز، أو اضغط فتح تسجيل الدخول. يرجع الاتصال تلقائياً بعد الموافقة.", errorMessage = null)
                val token = flow.awaitTokens(session)
                if (!isCurrent()) return@launch
                authState = authState.copy(accessToken = token.access, refreshToken = token.refresh,
                    tokenType = token.type, createdAt = token.createdAt, expiresIn = token.expiresIn,
                    pendingAuthorizationState = null, pendingAuthorizationStartedAtMillis = null)
                deviceSession = null
                persist(profileId)
                publish(isLoading = false, statusMessage = localizedString(Res.string.trakt_connected_status), errorMessage = null)
                refreshUserSettings(profileId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (isCurrent()) {
                    deviceSession = null
                    clearPendingAuthorization()
                    persist(profileId)
                    // Never log response bodies, device codes, or tokens.
                    publish(isLoading = false, statusMessage = null,
                        errorMessage = error.message?.takeIf { it.startsWith("تعذر") || it.startsWith("انتهت") || it.startsWith("تم رفض") }
                            ?: "تعذر الاتصال بـTrakt؛ أعد المحاولة")
                }
            }
        }
    }''')
    replace('TraktConfig.CLIENT_ID.isNotBlank() && TraktConfig.CLIENT_SECRET.isNotBlank()',
            'TraktConfig.CLIENT_ID.isNotBlank()')
    start = text.index('        val oauthState = generateOauthState()')
    end = text.index('\n    fun onCancelAuthorization(', start)
    text = text[:start] + '''        startDeviceLogin(profileId)
        return null
    }

    fun pendingAuthorizationUrl(profileId: Int = ProfileRepository.activeProfileId): String? {
        ensureLoaded(profileId)
        return deviceSession?.takeIf { TraktPlatformClock.nowEpochMs() < it.expiresAt }?.verificationUrl
    }
''' + text[end:]
    replace('        clearPendingAuthorization()\n        persist(profileId)\n        publish(statusMessage = null, errorMessage = null)',
            '        cancelDeviceLogin()\n        clearPendingAuthorization()\n        persist(profileId)\n        publish(isLoading = false, statusMessage = null, errorMessage = null)')
    replace('    override fun clearLocalState() {', '    override fun clearLocalState() {\n        cancelDeviceLogin()')
    replace('    override fun removeStoredProfile(profileId: Int) {',
            '    override fun removeStoredProfile(profileId: Int) {\n        if (profileId == currentProfileId) cancelDeviceLogin()')
    replace('    fun onDisconnectRequested(profileId: Int = currentProfileId) {\n        ensureLoaded(profileId)',
            '    fun onDisconnectRequested(profileId: Int = currentProfileId) {\n        ensureLoaded(profileId)\n        cancelDeviceLogin()\n        clearPendingAuthorization()')
    replace('    private fun loadFromDisk(profileId: Int) {',
            '    private fun loadFromDisk(profileId: Int) {\n        cancelDeviceLogin()')
    replace('        publish(statusMessage = null, errorMessage = null)\n    }\n\n    private fun clearPendingAuthorization()',
            '        clearPendingAuthorization()\n        publish(isLoading = false, statusMessage = null, errorMessage = null)\n    }\n\n    private fun clearPendingAuthorization()')
    # Refresh/revoke for the new public client must not send an empty/deprecated secret.
    replace('''                clientSecret = TraktConfig.CLIENT_SECRET,
                redirectUri = TraktConfig.REDIRECT_URI,
            ),
        )

        val response = runCatching {
            httpRequestRaw(''', '''            ),
        )

        val response = runCatching {
            httpRequestRaw(''')
    replace('''                    clientSecret = TraktConfig.CLIENT_SECRET,
                ),''', '''                ),''')
    replace('''private data class TraktRefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("redirect_uri") val redirectUri: String,''', '''private data class TraktRefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,''')
    replace('''private data class TraktRevokeRequest(
    @SerialName("token") val token: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,''', '''private data class TraktRevokeRequest(
    @SerialName("token") val token: String,
    @SerialName("client_id") val clientId: String,''')
    repo.write_text(text)

card = root / 'composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/TrackingProviderCards.kt'
text = card.read_text()
old = 'approvalDescription = stringResource(Res.string.settings_trakt_approval_redirect),'
new = 'approvalDescription = "أدخل رمز التفعيل في موقع Trakt. يتم إكمال الربط تلقائياً؛ لا تحتاج الرجوع عبر رابط خاص.",'
if new not in text:
    if text.count(old) != 1:
        raise RuntimeError('Trakt settings seam changed')
    card.write_text(text.replace(old, new))
