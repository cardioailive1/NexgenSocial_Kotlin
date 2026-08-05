package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagesViewModel : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { ApiClient.get<ConversationsResponse>("/api/messages") }
                .onSuccess { _conversations.value = it.conversations }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(navController: NavController, viewModel: MessagesViewModel = viewModel()) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Messages") }) }) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No conversations yet", fontWeight = FontWeight.SemiBold)
                    Text("Open someone's profile and tap Message.",
                        fontSize = 13.sp, color = Slate400)
                }
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(conversations, key = { it.id }) { convo ->
                ListItem(
                    modifier = Modifier.clickable {
                        navController.navigate("conversation/${convo.id}")
                    },
                    leadingContent = {
                        Avatar(convo.otherUser?.avatarUrl, convo.otherUser?.username ?: "?", 44.dp)
                    },
                    headlineContent = {
                        Text(convo.otherUser?.displayName ?: "Conversation",
                            fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(convo.lastMessage?.body ?: "No messages yet",
                            maxLines = 1, fontSize = 12.sp, color = Slate400)
                    },
                    trailingContent = {
                        if (convo.unreadCount > 0) {
                            Badge { Text("${convo.unreadCount}") }
                        }
                    },
                )
                HorizontalDivider(color = LineColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    navController: NavController,
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var otherUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load() {
        runCatching { ApiClient.get<MessagesResponse>("/api/messages/$conversationId/messages") }
            .onSuccess { messages = it.messages }
    }

    LaunchedEffect(conversationId) {
        load()
        // Polling rather than a socket: predictable, no reconnection
        // handling, and adequate at this scale.
        while (true) {
            delay(5000)
            load()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUser?.displayName ?: "Chat") },
                actions = {
                    IconButton(onClick = {
                        scope.launch { startCall(conversationId, false, navController) }
                    }) { Icon(Icons.Filled.Call, "Voice call", tint = Cyan400) }
                    IconButton(onClick = {
                        scope.launch { startCall(conversationId, true, navController) }
                    }) { Icon(Icons.Filled.Videocam, "Video call", tint = Cyan400) }
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
                    placeholder = { Text("Message…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        val text = draft
                        draft = ""
                        scope.launch {
                            runCatching {
                                ApiClient.upload<EmptyResponse>(
                                    "/api/messages/$conversationId/messages",
                                    fields = mapOf("body" to text),
                                )
                            }.onSuccess { load() }
                             .onFailure { draft = text }  // restore so it isn't lost
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) { Icon(Icons.Filled.Send, "Send", tint = if (draft.isBlank()) Slate400 else Cyan400) }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val isMine = message.sender?.id != otherUser?.id
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        color = if (isMine) Cyan400 else Navy800,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 280.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            message.body?.let {
                                Text(it, fontSize = 14.sp,
                                    color = if (isMine) Navy950 else androidx.compose.ui.graphics.Color.White)
                            }
                            if (message.attachments.isNotEmpty()) {
                                MediaCarousel(message.attachments)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun startCall(conversationId: String, video: Boolean, navController: NavController) {
    // The call endpoint takes a username, so the conversation is resolved
    // first rather than assuming the caller already has it.
    runCatching {
        val conversations = ApiClient.get<ConversationsResponse>("/api/messages").conversations
        val username = conversations.firstOrNull { it.id == conversationId }?.otherUser?.username
            ?: return
        val response = ApiClient.post<CallResponse>(
            "/api/messages/calls",
            mapOf("username" to username, "kind" to if (video) "VIDEO" else "AUDIO")
        )
        navController.navigate("call/${response.call.id}")
    }
}
