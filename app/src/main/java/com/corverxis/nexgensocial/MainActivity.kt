package com.corverxis.nexgensocial

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corverxis.nexgensocial.data.AuthViewModel
import com.corverxis.nexgensocial.services.NexgenFirebaseService
import com.corverxis.nexgensocial.services.PushRegistrar
import com.corverxis.nexgensocial.ui.RootScreen
import com.corverxis.nexgensocial.ui.theme.NexgenSocialTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) PushRegistrar.registerCurrentToken() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Channels must exist before the first notification is posted.
        NexgenFirebaseService.createChannels(this)
        requestNotificationPermissionIfNeeded()

        val deepLink = intent?.getStringExtra("deepLink")
        val answerCallId = if (intent?.action == "ANSWER_CALL") {
            intent.getStringExtra("callId")
        } else null

        setContent {
            NexgenSocialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = viewModel()
                    RootScreen(
                        authViewModel = authViewModel,
                        initialDeepLink = deepLink,
                        answerCallId = answerCallId,
                    )
                }
            }
        }
    }

    /**
     * Android 13+ requires runtime permission for notifications. Without it
     * every notify() silently fails, which looks exactly like a broken
     * push pipeline.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
