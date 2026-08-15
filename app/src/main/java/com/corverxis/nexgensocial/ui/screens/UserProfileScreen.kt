package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val user: User,
    val posts: List<Post> = emptyList(),
    val isFollowing: Boolean = false,
)

/** Another user's profile, opened by tapping a feed item (UAT-011). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(username: String, navController: NavController) {
    var profile by remember { mutableStateOf<UserProfileResponse?>(null) }
    var following by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        runCatching { ApiClient.get<UserProfileResponse>("/api/users/$username") }
            .onSuccess { profile = it; following = it.isFollowing }
            .onFailure { error = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.user?.displayName ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error!!, color = Danger, fontSize = 13.sp)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(profile?.user?.avatarUrl, username, 84.dp)
                        Text(profile?.user?.displayName.orEmpty(),
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("@$username", fontSize = 13.sp, color = Slate400)
                        profile?.user?.bio?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, color = Slate300,
                                textAlign = TextAlign.Center)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            if (following) ApiClient.delete("/api/follows/$username")
                                            else ApiClient.post<EmptyResponse>("/api/follows/$username")
                                        }.onSuccess { following = !following }
                                        busy = false
                                    }
                                },
                            ) { Text(if (following) "Following" else "Follow") }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val convo = ApiClient.post<Map<String, String>>(
                                                "/api/messages/with/$username"
                                            )
                                            navController.navigate("messages")
                                        }
                                    }
                                },
                            ) { Text("Message") }
                        }
                    }
                }
            }

            items(profile?.posts.orEmpty(), key = { it.id }) { post ->
                PostCard(post = post, onLike = {})
            }

            if (profile?.posts.orEmpty().isEmpty() && profile != null) {
                item {
                    Text("No posts yet.", fontSize = 13.sp, color = Slate400,
                        modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}
