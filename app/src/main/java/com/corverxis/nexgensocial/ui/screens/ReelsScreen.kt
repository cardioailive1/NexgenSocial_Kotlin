package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.components.VideoPlayer
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReelsViewModel : ViewModel() {
    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels = _reels.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { ApiClient.get<ReelsResponse>("/api/reels/discover") }
                .onSuccess { _reels.value = it.reels }
        }
    }

    /** Watch time drives server-side ranking, so it's reported per reel. */
    fun reportView(reel: Reel, watchedSec: Double, completed: Boolean) {
        viewModelScope.launch {
            runCatching {
                ApiClient.post<EmptyResponse>(
                    "/api/reels/${reel.id}/view",
                    mapOf("watchedSec" to watchedSec, "completed" to completed)
                )
            }
        }
    }

    fun toggleLike(reel: Reel) {
        val index = _reels.value.indexOfFirst { it.id == reel.id }
        if (index < 0) return
        val wasLiked = _reels.value[index].likedByViewer

        _reels.value = _reels.value.toMutableList().also {
            it[index] = it[index].copy(
                likedByViewer = !wasLiked,
                likeCount = it[index].likeCount + if (wasLiked) -1 else 1,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (wasLiked) ApiClient.delete("/api/reels/${reel.id}/like")
                else ApiClient.post<EmptyResponse>("/api/reels/${reel.id}/like")
            }
        }
    }
}

@Composable
fun ReelsScreen(viewModel: ReelsViewModel = viewModel()) {
    val reels by viewModel.reels.collectAsState()

    if (reels.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No reels yet", fontWeight = FontWeight.SemiBold)
                Text(
                    "Record the first one — reels reach people who don't follow you yet.",
                    fontSize = 13.sp, color = Slate400,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { reels.size })

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        ReelPage(
            reel = reels[page],
            isActive = pagerState.currentPage == page,
            onLike = { viewModel.toggleLike(reels[page]) },
            onWatched = { seconds, completed ->
                viewModel.reportView(reels[page], seconds, completed)
            },
        )
    }
}

@Composable
private fun ReelPage(
    reel: Reel,
    isActive: Boolean,
    onLike: () -> Unit,
    onWatched: (Double, Boolean) -> Unit,
) {
    var watchedSeconds by remember(reel.id) { mutableStateOf(0.0) }

    // Report what was watched when scrolling away, so partial views still
    // count as ranking signal rather than being lost.
    DisposableEffect(reel.id, isActive) {
        onDispose {
            if (watchedSeconds > 0.5) {
                val completed = reel.durationSec?.let { watchedSeconds >= it * 0.9 } ?: false
                onWatched(watchedSeconds, completed)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoPlayer(
            url = ApiClient.mediaUrl(reel.videoUrl),
            modifier = Modifier.fillMaxSize(),
            autoPlay = isActive,
            loop = true,
            showControls = false,
            onPositionChanged = { millis -> watchedSeconds = millis / 1000.0 },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(16.dp)
                .padding(bottom = 60.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(reel.author?.avatarUrl, reel.author?.username ?: "?", 32.dp)
                    Text("@${reel.author?.username.orEmpty()}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                reel.caption?.let {
                    Text(it, fontSize = 13.sp, color = Color.White, maxLines = 3)
                }
                if (reel.hashtags.isNotEmpty()) {
                    Text(reel.hashtags.joinToString(" ") { "#$it" },
                        fontSize = 12.sp, color = Cyan300)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onLike) {
                        Icon(
                            if (reel.likedByViewer) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (reel.likedByViewer) Cyan400 else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text("${reel.likeCount}", fontSize = 11.sp, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Visibility, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("${reel.viewCount}", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}
