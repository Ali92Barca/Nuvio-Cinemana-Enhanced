package com.nuvio.android.trakt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class TraktDeviceFlowTest {
    private val code = """{"device_code":"private-code","user_code":"ABCD1234","verification_url":"https://trakt.tv/activate","expires_in":60,"interval":2}"""
    private val tokens = """{"access_token":"private-access","refresh_token":"private-refresh","expires_in":604800,"created_at":10}"""

    @Test fun publicClientPendingThenSuccess() = runBlocking {
        var time = 0L
        var polls = 0
        val flow = TraktDeviceFlow("our-client", { path, body ->
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("our-client", json["client_id"]?.jsonPrimitive?.content)
            assertFalse("client_secret" in json)
            assertFalse("redirect_uri" in json)
            if (path.endsWith("/code")) DeviceReply(200, code) else {
                assertEquals("private-code", json["code"]?.jsonPrimitive?.content)
                DeviceReply(if (++polls == 1) 400 else 200, if (polls == 1) "" else tokens)
            }
        }, { time }, { time += it })
        val session = flow.begin()
        assertEquals("ABCD1234", session.userCode)
        assertEquals("private-access", flow.awaitTokens(session).access)
        assertEquals(4000L, time)
        assertEquals(2, polls)
    }

    @Test fun slowsDownOn429() = runBlocking {
        var time = 0L
        val waits = mutableListOf<Long>()
        var polls = 0
        val flow = TraktDeviceFlow("id", { _, _ ->
            DeviceReply(if (++polls == 1) 429 else 200, tokens)
        }, { time }, { waits += it; time += it })
        flow.awaitTokens(DeviceSession("code", "user", "https://trakt.tv/activate", 60000, 2000))
        assertEquals(listOf(2000L, 7000L), waits)
    }

    @Test fun stopsAtExpiryWithoutPolling() = runBlocking {
        var time = 0L
        var polls = 0
        val flow = TraktDeviceFlow("id", { _, _ -> polls++; DeviceReply(400, "") }, { time }, { time += it })
        assertFailsWith<IllegalStateException> {
            flow.awaitTokens(DeviceSession("code", "user", "https://trakt.tv/activate", 2000, 2000))
        }
        assertEquals(0, polls)
    }

    @Test fun terminalStatusesStopImmediately() = runBlocking {
        for (status in listOf(404, 409, 410, 418, 403, 500)) {
            var polls = 0
            val flow = TraktDeviceFlow("id", { _, _ -> polls++; DeviceReply(status, "") }, { 0 }, {})
            assertFailsWith<IllegalStateException> {
                flow.awaitTokens(DeviceSession("code", "user", "https://trakt.tv/activate", 60000, 2000))
            }
            assertEquals(1, polls)
        }
    }

    @Test fun cancellationDoesNotReachNetwork() = runBlocking {
        var polls = 0
        val flow = TraktDeviceFlow("id", { _, _ -> polls++; DeviceReply(200, tokens) }, { 0 }, { throw CancellationException() })
        assertFailsWith<CancellationException> {
            flow.awaitTokens(DeviceSession("code", "user", "https://trakt.tv/activate", 60000, 2000))
        }
        assertEquals(0, polls)
    }

    @Test fun rejectsUntrustedActivationUrl() = runBlocking {
        val flow = TraktDeviceFlow("id", { _, _ -> DeviceReply(200, code.replace("https://trakt.tv/activate", "https://example.com/activate")) }, { 0 })
        assertFailsWith<IllegalStateException> { flow.begin() }
    }

    @Test fun rejectsMalformedTokens() = runBlocking {
        val flow = TraktDeviceFlow("id", { _, _ -> DeviceReply(200, "{}") }, { 0 }, {})
        assertFailsWith<IllegalStateException> {
            flow.awaitTokens(DeviceSession("code", "user", "https://trakt.tv/activate", 60000, 2000))
        }
    }
}
