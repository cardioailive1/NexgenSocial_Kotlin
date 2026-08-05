package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.components.VideoPlayer
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { ApiClient.get<FeedResponse>("/api/posts/feed") }
                .onSuccess { _posts.value = it.posts; _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /**
     * Optimistic: the heart fills immediately and reverts if the request
     * fails. Waiting on the network makes every tap feel broken.
     */
    fun toggleLike(post: Post) {
        val index = _posts.value.indexOfFirst { it.id == post.id }
        if (index < 0) return
        val wasLiked = _posts.value[index].likedByViewer

        _posts.value = _posts.value.toMutableList().also {
            it[index] = it[index].copy(
                likedByViewer = !wasLiked,
                likeCount = it[index].likeCount + if (wasLiked) -1 else 1,
            )
        }

        viewModelScope.launch {
            runCatching {
                if (wasLiked) ApiClient.delete("/api/posts/${post.id}/like")
                else ApiClient.post<EmptyResponse>("/api/posts/${post.id}/like")
            }.onFailure {
                _posts.value = _posts.value.toMutableList().also { list ->
                    val i = list.indexOfFirst { it.id == post.id }
                    if (i >= 0) list[i] = list[i].copy(
                        likedByViewer = wasLiked,
                        likeCount = list[i].likeCount + if (wasLiked) 1 else -1,
                    )
                }
            }
        }
    }
}

@Composable
fun FeedScreen(viewModel: FeedViewModel = viewModel()) {
    val posts by viewModel.posts.collectAsState()
    val error by viewModel.error.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        error?.let {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }
        items(posts, key = { it.id }) { post ->
            PostCard(post) { viewModel.toggleLike(post) }
        }
        if (posts.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Your feed is empty", fontWeight = FontWeight.SemiBold)
                        Text("Follow a few people, or post something yourself.",
                            fontSize = 13.sp, color = Slate400)
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: Post, onLike: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(post.author?.avatarUrl, post.author?.username ?: "?", 38.dp)
                Column {
                    Text(post.author?.displayName ?: "Unknown",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("@${post.author?.username.orEmpty()}",
                        fontSize = 12.sp, color = Slate400)
                }
            }

            post.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 14.sp, color = Slate300)
            }

            if (post.displayMedia.isNotEmpty()) {
                MediaCarousel(post.displayMedia)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.clip(MaterialTheme.shapes.small),
                ) {
                    IconButton(onClick = onLike, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (post.likedByViewer) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.likedByViewer) Cyan400 else Slate400,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text("${post.likeCount}", fontSize = 13.sp, color = Slate400)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null,
                        tint = Slate400, modifier = Modifier.size(18.dp))
                    Text("${post.commentCount}", fontSize = 13.sp, color = Slate400)
                }
            }
        }
    }
}

@Composable
fun MediaCarousel(items: List<MediaItem>) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPlayer(url = ApiClient.mediaUrl(item.url), modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = ApiClient.mediaUrl(item.url),
                    contentDescription = item.caption,
                    // Fit, not Crop: cropping a portrait photo into a
                    // landscape box hides part of the picture.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                )
            }
        }
        if (items.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(items.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(Modifier)
                    ) {
                        Surface(
                            color = if (pagerState.currentPage == index) Cyan400 else Slate400,
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.small,
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun Avatar(url: String?, seed: String, size: androidx.compose.ui.unit.Dp) {
    val resolved = ApiClient.mediaUrl(url)
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
        )
    } else {
        Box(
            Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = Navy800, modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.CircleShape) {}
            Text(seed.take(1).uppercase(), color = Cyan300, fontWeight = FontWeight.SemiBold)
        }
    }
}
