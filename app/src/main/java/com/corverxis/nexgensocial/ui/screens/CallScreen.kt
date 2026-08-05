package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.corverxis.nexgensocial.data.Call
import com.corverxis.nexgensocial.data.CallResponse
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.services.WebRtcClient
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(callId: String, navController: NavController) {
    var call by remember { mutableStateOf<Call?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(callId) {
        // Fetches this specific call rather than scanning history -- the
        // web app had exactly that bug, and a just-created call could be
        // missing from the list, silently degrading a video call to audio.
        runCatching { ApiClient.get<CallResponse>("/api/messages/calls/$callId/details") }
            .onSuccess {
                call = it.call
                WebRtcClient.connect(callId, video = it.call.kind == "VIDEO")
            }
            .onFailure { errorMessage = "Couldn't load this call. It may have ended." }

        while (true) { delay(1000); elapsed++ }
    }

    DisposableEffect(Unit) {
        onDispose { WebRtcClient.disconnect() }
    }

    Box(Modifier.fillMaxSize().background(Navy950)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val other = call?.callee ?: call?.caller
            Avatar(other?.avatarUrl, other?.username ?: "?", 96.dp)

            Text(
                other?.displayName ?: "Call",
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "%02d:%02d".format(elapsed / 60, elapsed % 60),
                fontSize = 14.sp, color = Slate400,
            )
            errorMessage?.let {
                Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(60.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                CallControl(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    background = Navy800,
                ) {
                    isMuted = !isMuted
                    WebRtcClient.setMuted(isMuted)
                }

                if (call?.kind == "VIDEO") {
                    CallControl(
                        icon = if (isCameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        label = if (isCameraOff) "Camera on" else "Camera off",
                        background = Navy800,
                    ) {
                        isCameraOff = !isCameraOff
                        WebRtcClient.setCameraEnabled(!isCameraOff)
                    }
                }

                CallControl(
                    icon = Icons.Filled.CallEnd,
                    label = "End",
                    background = Danger,
                ) {
                    scope.launch {
                        runCatching {
                            ApiClient.patch<CallResponse>(
                                "/api/messages/calls/$callId",
                                mapOf("status" to "ENDED")
                            )
                        }
                        WebRtcClient.disconnect()
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = background,
            shape = CircleShape,
            modifier = Modifier.size(62.dp).clip(CircleShape),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp))
            }
        }
        Text(label, fontSize = 11.sp, color = Slate400,
            modifier = Modifier.padding(top = 6.dp))
    }
}
