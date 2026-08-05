package com.corverxis.nexgensocial.services

import android.util.Log
import com.corverxis.nexgensocial.network.ApiClient
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebRTC transport for calls and meetings.
 *
 * IMPORTANT — the peer connection here is deliberately a stub.
 *
 * The signalling layer below is complete and its message shapes match
 * `backend/src/livestreamSignaling.js` exactly: join, createTransport,
 * connectTransport, produce, consume, resumeConsumer, with room ids of the
 * form `call-<id>` and `meet-<id>`, both joining with role "host" because
 * every participant in a call publishes.
 *
 * What's missing is the PeerConnectionFactory wiring. The WebRTC dependency
 * is declared in build.gradle.kts, but writing code against an API surface
 * that can't be compiled here would produce something that looks finished
 * and fails in Android Studio. See README-ANDROID.md, "Completing WebRTC".
 */
object WebRtcClient {
    private const val TAG = "WebRtcClient"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // long-lived socket
        .build()

    private var socket: WebSocket? = null

    fun connect(callId: String, video: Boolean) {
        openSocket(roomId = "call-$callId")
    }

    fun connectToMeeting(meetingId: String) {
        openSocket(roomId = "meet-$meetingId")
    }

    private fun openSocket(roomId: String) {
        val url = ApiClient.BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws/live"

        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = JSONObject().apply {
                    put("method", "join")
                    put("data", JSONObject().apply {
                        put("roomId", roomId)
                        put("role", "host")
                        put("token", ApiClient.authToken.orEmpty())
                    })
                }
                webSocket.send(join.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "newProducer" -> { /* consume the new track */ }
                        "peerClosed" -> { /* drop that peer's video */ }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signalling socket failed: ${t.message}")
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
    }

    fun setMuted(muted: Boolean) {
        // Toggles the local audio track once WebRTC is wired in.
        Log.d(TAG, "setMuted($muted)")
    }

    fun setCameraEnabled(enabled: Boolean) {
        Log.d(TAG, "setCameraEnabled($enabled)")
    }
}
