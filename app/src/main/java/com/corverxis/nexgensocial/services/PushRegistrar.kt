package com.corverxis.nexgensocial.services

import android.util.Log
import com.corverxis.nexgensocial.data.EmptyResponse
import com.corverxis.nexgensocial.network.ApiClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FCM token registration.
 *
 * Simpler than APNs in one important way: a single token handles both
 * normal notifications and call wake-ups. Android has no separate VoIP
 * channel, and no equivalent of Apple's rule that a VoIP push must produce
 * a call -- so a high-priority data message is enough.
 */
object PushRegistrar {
    private const val TAG = "PushRegistrar"
    private var lastToken: String? = null

    /** Called after sign-in, and whenever FCM rotates the token. */
    fun registerCurrentToken() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                sendToServer(token)
            }.onFailure { Log.e(TAG, "Could not fetch FCM token: ${it.message}") }
        }
    }

    suspend fun sendToServer(token: String) {
        lastToken = token
        runCatching {
            ApiClient.post<EmptyResponse>(
                "/api/push/fcm-subscribe",
                mapOf("deviceToken" to token, "platform" to "android")
            )
        }.onFailure {
            // Fails quietly: an unreachable server shouldn't surface an
            // error at sign-in. It re-registers on next launch.
            Log.w(TAG, "FCM registration failed: ${it.message}")
        }
    }

    suspend fun unregister() {
        val token = lastToken ?: return
        runCatching {
            ApiClient.post<EmptyResponse>(
                "/api/push/fcm-unsubscribe",
                mapOf("deviceToken" to token)
            )
        }
        lastToken = null
    }
}
