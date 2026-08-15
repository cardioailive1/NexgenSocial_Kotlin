package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,
    val body: String,
    val createdAt: String? = null,
    val author: User? = null,
)

@Serializable
data class CommentsResponse(val comments: List<Comment>)

/** Comment thread for a post (UAT-014). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(postId: String, navController: NavController) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { ApiClient.get<CommentsResponse>("/api/posts/$postId/comments") }
            .onSuccess { comments = it.comments }
            .onFailure { error = it.message }
    }

    LaunchedEffect(postId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Add a comment…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        val text = draft
                        draft = ""
                        sending = true
                        scope.launch {
                            runCatching {
                                ApiClient.post<EmptyResponse>(
                                    "/api/posts/$postId/comments",
                                    mapOf("body" to text)
                                )
                            }.onSuccess { load() }
                             .onFailure {
                                 // Put the text back rather than losing it.
                                 draft = text
                                 error = it.message
                             }
                            sending = false
                        }
                    },
                ) {
                    Icon(Icons.Filled.Send, "Post comment",
                        tint = if (draft.isBlank()) Slate400 else Cyan400)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            }
            if (comments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Be the first.",
                        fontSize = 13.sp, color = Slate400)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(comment.author?.avatarUrl,
                                comment.author?.username ?: "?", 32.dp)
                            Column {
                                Text(comment.author?.displayName ?: "Unknown",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(comment.body, fontSize = 13.sp, color = Slate300)
                            }
                        }
                    }
                }
            }
        }
    }
}
