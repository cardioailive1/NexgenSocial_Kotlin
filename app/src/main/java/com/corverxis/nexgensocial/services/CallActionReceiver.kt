package com.corverxis.nexgensocial.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.corverxis.nexgensocial.data.CallResponse
import com.corverxis.nexgensocial.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles Decline tapped straight from the notification, without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("callId") ?: return
        NotificationManagerCompat.from(context)
            .cancel(NexgenFirebaseService.CALL_NOTIFICATION_ID)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ApiClient.patch<CallResponse>(
                    "/api/messages/calls/$callId",
                    mapOf("status" to "DECLINED")
                )
            }
        }
    }
}
