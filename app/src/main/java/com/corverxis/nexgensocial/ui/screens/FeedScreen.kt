package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
fun FeedScreen(
    viewModel: FeedViewModel = viewModel(),
    onOpenProfile: (String) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
) {
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
            PostCard(
                post = post,
                onLike = { viewModel.toggleLike(post) },
                onOpenProfile = onOpenProfile,
                onOpenComments = { onOpenComments(it.id) },
            )
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
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenComments: (Post) -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The whole author row is the tap target, not just the avatar --
            // a 38dp circle is below the 48dp minimum touch size and is
            // easy to miss.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clickable(enabled = post.author != null) {
                        post.author?.username?.let(onOpenProfile)
                    }
                    .padding(vertical = 2.dp),
            ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenComments(post) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments",
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
    var fullScreenItem by remember { mutableStateOf<MediaItem?>(null) }

    fullScreenItem?.let { item ->
        ZoomableImageDialog(item = item, onDismiss = { fullScreenItem = null })
    }

    Column {
        // heightIn(max=) rather than a fixed height: a fixed box forces
        // ContentScale.Fit to pad portrait media with large black bars.
        // Capping the height instead lets each item size to its own aspect
        // ratio, so there's nothing to pad.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPlayer(
                    url = ApiClient.mediaUrl(item.url),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                )
            } else {
                AsyncImage(
                    model = ApiClient.mediaUrl(item.url),
                    contentDescription = item.caption,
                    // Fit, not Crop: cropping a portrait photo into a
                    // landscape box hides part of the picture.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { fullScreenItem = item },
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


/**
 * Full-screen image viewer with pinch-to-zoom and pan (UAT-012).
 *
 * Scale is clamped to 1x–5x: below 1x the image would float away from the
 * frame, and above 5x an ordinary photo is just blur. Panning is only
 * allowed while zoomed in, so a stray drag at 1x doesn't shift the picture
 * off-centre.
 */
@Composable
fun ZoomableImageDialog(item: MediaItem, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            // Snap back to centre when zoomed all the way out.
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ApiClient.mediaUrl(item.url),
                contentDescription = item.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}
