package com.corverxis.nexgensocial.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer wrapped for Compose.
 *
 * The player is released in onDispose deliberately: leaving several alive
 * while scrolling a feed leaks memory and plays overlapping audio, which is
 * the most common complaint about video feeds built quickly.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    loop: Boolean = false,
    showControls: Boolean = true,
    onPositionChanged: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            url?.let { setMediaItem(MediaItem.fromUri(it)) }
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            prepare()
        }
    }

    // Reports playback position so reel watch-time can be sent to the
    // server, which is what drives ranking.
    LaunchedEffect(player, onPositionChanged) {
        if (onPositionChanged == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            onPositionChanged(player.currentPosition)
        }
    }

    DisposableEffect(url) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = showControls
            }
        },
        modifier = modifier,
    )
}
