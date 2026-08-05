package com.corverxis.nexgensocial.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.corverxis.nexgensocial.MainActivity
import com.corverxis.nexgensocial.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives push messages. Runs even when the app has been swiped away,
 * which is the whole point -- this is what a web app can't do.
 */
class NexgenFirebaseService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_MESSAGES = "messages"
        const val CALL_NOTIFICATION_ID = 1001

        /**
         * Channels must exist before any notification is posted, and their
         * importance is fixed once created -- changing it later in code has
         * no effect on an installed app. Created at app start, not lazily.
         */
        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)

            val callChannel = NotificationChannel(
                CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video calls"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Direct messages and mentions" }

            manager.createNotificationChannels(listOf(callChannel, messageChannel))
        }
    }

    override fun onNewToken(token: String) {
        // FCM rotates tokens on reinstall and app-data clear. A stale token
        // silently stops receiving anything, so re-register immediately.
        CoroutineScope(Dispatchers.IO).launch { PushRegistrar.sendToServer(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "incoming-call" -> showIncomingCall(
                callId = data["callId"].orEmpty(),
                callerName = data["callerName"] ?: "Incoming call",
                isVideo = data["kind"] == "VIDEO",
            )
            else -> showMessage(
                title = data["title"] ?: message.notification?.title ?: "NexgenSocial",
                body = data["body"] ?: message.notification?.body.orEmpty(),
                url = data["url"] ?: "/",
            )
        }
    }

    private fun showIncomingCall(callId: String, callerName: String, isVideo: Boolean) {
        val answerIntent = Intent(this, MainActivity::class.java).apply {
            action = "ANSWER_CALL"
            putExtra("callId", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val answerPending = PendingIntent.getActivity(
            this, 0, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "DECLINE_CALL"
            putExtra("callId", callId)
        }
        val declinePending = PendingIntent.getBroadcast(
            this, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(callerName)
            .setContentText(if (isVideo) "Incoming video call" else "Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // fullScreenIntent is what takes over the lock screen instead of
            // showing a small heads-up banner. Requires USE_FULL_SCREEN_INTENT,
            // which on Android 14+ is granted only to calling/alarm apps.
            .setFullScreenIntent(answerPending, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)   // stop ringing rather than hanging forever
            .addAction(R.drawable.ic_call, "Answer", answerPending)
            .addAction(R.drawable.ic_call_end, "Decline", declinePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, notification)
        }
        // A missing POST_NOTIFICATIONS permission throws on Android 13+;
        // swallowing it keeps the service alive rather than crashing.
    }

    private fun showMessage(title: String, body: String, url: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("deepLink", url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, url.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
