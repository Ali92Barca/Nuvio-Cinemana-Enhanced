package com.nuvio.android.trakt

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

data class DeviceReply(val status: Int, val body: String)
// Deliberately not data classes: accidental logging must not reveal device/access tokens.
class DeviceSession(val code: String, val userCode: String, val verificationUrl: String,
    val expiresAt: Long, val intervalMs: Long)
class DeviceTokens(val access: String, val refresh: String, val type: String,
    val createdAt: Long, val expiresIn: Int)

class TraktDeviceFlow(
    private val clientId: String,
    private val request: suspend (String, String) -> DeviceReply,
    private val now: () -> Long,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private fun body(vararg pairs: Pair<String, String>) = buildJsonObject {
        put("client_id", clientId)
        pairs.forEach { (key, value) -> put(key, value) }
    }.toString()

    suspend fun begin(): DeviceSession {
        require(clientId.isNotBlank()) { "Trakt Client ID is missing" }
        val reply = request("/oauth/device/code", body())
        check(reply.status in 200..299) { "تعذر طلب رمز Trakt (HTTP ${reply.status})" }
        val obj = Json.parseToJsonElement(reply.body).jsonObject
        val code = obj.string("device_code")
        val userCode = obj.string("user_code")
        val url = obj.string("verification_url")
        // Only open Trakt's HTTPS activation pages, never an arbitrary API-provided URL.
        check(Regex("https://(?:app\\.)?trakt\\.tv/(?:activate|auth/activate)(?:[/?#].*)?").matches(url)) {
            "عنوان تفعيل Trakt غير صالح"
        }
        val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull
        val interval = obj["interval"]?.jsonPrimitive?.longOrNull
        check(code.isNotBlank() && userCode.isNotBlank() && expires != null && expires in 1..86400 && interval != null && interval in 1..3600) {
            "استجابة رمز Trakt غير مكتملة"
        }
        return DeviceSession(code, userCode, url, now() + expires * 1000, interval * 1000)
    }

    suspend fun awaitTokens(session: DeviceSession): DeviceTokens {
        var interval = session.intervalMs
        while (now() < session.expiresAt) {
            wait(interval)
            if (now() >= session.expiresAt) break
            val reply = request("/oauth/device/token", body("code" to session.code))
            when (reply.status) {
                200 -> {
                    val obj = Json.parseToJsonElement(reply.body).jsonObject
                    val access = obj.string("access_token")
                    val refresh = obj.string("refresh_token")
                    val expires = obj["expires_in"]?.jsonPrimitive?.intOrNull ?: 0
                    check(access.isNotBlank() && refresh.isNotBlank() && expires > 0) { "استجابة جلسة Trakt غير مكتملة" }
                    return DeviceTokens(access, refresh, obj.string("token_type").ifBlank { "bearer" },
                        obj["created_at"]?.jsonPrimitive?.longOrNull ?: now() / 1000, expires)
                }
                400 -> Unit // pending authorization, as specified by Trakt's device endpoint
                429 -> interval = (interval + 5000).coerceAtMost(3600000)
                418 -> error("تم رفض ربط Trakt؛ ابدأ الربط مجدداً")
                404, 409, 410 -> error("انتهت صلاحية رمز Trakt أو استُخدم؛ اطلب رمزاً جديداً")
                else -> error("تعذر إكمال ربط Trakt (HTTP ${reply.status})؛ أعد المحاولة")
            }
        }
        error("انتهت صلاحية رمز Trakt؛ اطلب رمزاً جديداً")
    }
}

private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
