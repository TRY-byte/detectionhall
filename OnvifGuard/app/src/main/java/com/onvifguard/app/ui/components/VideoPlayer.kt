package com.onvifguard.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.onvifguard.app.ui.theme.DarkSurface
import com.onvifguard.app.ui.theme.SecurityRed
import com.onvifguard.app.ui.theme.TextSecondary

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    rtspUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(rtspUrl) {
        if (rtspUrl.isBlank()) return@remember null

        try {
            // Low latency buffer configuration for live surveillance
            // Constraints: minBufferMs >= bufferForPlaybackMs && minBufferMs >= bufferForPlaybackAfterRebufferMs
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1500, // minBufferMs
                    3000, // maxBufferMs
                    500,  // bufferForPlaybackMs
                    1000  // bufferForPlaybackAfterRebufferMs
                )
                .build()

            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    val mediaItem = MediaItem.fromUri(rtspUrl)
                    val mediaSource = RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true) // Force TCP for stability over WiFi
                        .createMediaSource(mediaItem)

                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isBuffering = playbackState == Player.STATE_BUFFERING
                            if (playbackState == Player.STATE_READY) {
                                errorMessage = null
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            isBuffering = false
                            errorMessage = "Stream error: ${error.errorCodeName}"
                        }
                    })
                }
        } catch (e: Throwable) {
            errorMessage = "Player init error: ${e.message}"
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.release()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (rtspUrl.isBlank()) {
            Text(
                text = "No RTSP Stream configured\nConfigure camera in Setup tab",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering && errorMessage == null) {
                CircularProgressIndicator(color = SecurityRed)
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = SecurityRed,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
