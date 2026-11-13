package com.lontar.app.player

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lontar.app.LontarApplication
import com.lontar.app.ui.LontarTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerState by mutableStateOf<ExoPlayer?>(null)
    private var playerView: PlayerView? = null
    private var isInPip by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var playbackError by mutableStateOf<String?>(null)
    private var videoTitle by mutableStateOf("")
    private var currentPersonaName: String? = null
    private var resumePositionMs = 0L
    private var manifestFile: File? = null
    private var sourceCandidates: List<PlaybackSource> = emptyList()
    private var currentSourceIndex = -1
    private var automaticRefreshAttempted = false
    private var streamResolveJob: Job? = null
    private var sourcePrepareJob: Job? = null
    private var delayedStopJob: Job? = null
    private lateinit var httpDataSourceFactory: DefaultHttpDataSource.Factory

    private val pipController: PipController by lazy {
        PipController(
            activity = this,
            player = { player },
            sourceView = { playerView as View? },
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pipController.updateParams()
        override fun onPlaybackStateChanged(playbackState: Int) = pipController.updateParams()
        override fun onVideoSizeChanged(videoSize: VideoSize) = pipController.updateParams()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            handlePlayerError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        if (videoTitle.isBlank()) videoTitle = "Video"
        setContent {
            LontarTheme {
                PlayerContent(
                    title = videoTitle,
                    player = playerState,
                    isInPip = isInPip,
                    isLoading = isLoading,
                    errorMessage = playbackError,
                    canEnterPip = pipController.isSupported,
                    onPlayerViewReady = { playerView = it },
                    onBack = { finish() },
                    onEnterPip = pipController::enter,
                    onRetry = { loadStreams(refresh = true) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        delayedStopJob?.cancel()
        delayedStopJob = null
        if (player == null) {
            createPlayer()
            loadStreams(refresh = false)
        } else {
            pipController.attach()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isInPictureInPictureMode) return
        resumePositionMs = player?.currentPosition ?: resumePositionMs
        delayedStopJob?.cancel()
        delayedStopJob = lifecycleScope.launch {
            // Give the platform's PiP mode callback time to arrive before releasing playback.
            delay(PIP_TRANSITION_GRACE_MS)
            if (!isInPictureInPictureMode && !isChangingConfigurations) {
                pipController.detach()
                releasePlayer()
            }
        }
    }

    override fun onDestroy() {
        delayedStopJob?.cancel()
        pipController.detach()
        releasePlayer()
        manifestFile?.delete()
        manifestFile = null
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipController.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        pipController.updateParams()
    }

    private fun createPlayer() {
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .also { activePlayer ->
                activePlayer.addListener(playerListener)
                playerState = activePlayer
            }
        pipController.attach()
    }

    private fun releasePlayer() {
        streamResolveJob?.cancel()
        streamResolveJob = null
        sourcePrepareJob?.cancel()
        sourcePrepareJob = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        playerState = null
        sourceCandidates = emptyList()
        currentSourceIndex = -1
    }

    private fun loadStreams(
        refresh: Boolean,
        automaticRefresh: Boolean = false,
        positionToRestore: Long? = null,
    ) {
        val activePlayer = player ?: return
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (videoId.isBlank()) {
            isLoading = false
            playbackError = "ID video tidak tersedia. Kembali lalu pilih video lagi."
            return
        }

        val position = positionToRestore
            ?: if (refresh) activePlayer.currentPosition else resumePositionMs
        if (!automaticRefresh) automaticRefreshAttempted = false
        streamResolveJob?.cancel()
        sourcePrepareJob?.cancel()
        sourceCandidates = emptyList()
        currentSourceIndex = -1
        isLoading = true
        playbackError = null
        activePlayer.pause()

        streamResolveJob = lifecycleScope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    val backend = (application as LontarApplication).backend
                    if (refresh) {
                        backend.getRefreshedPlaybackStreams(videoId, currentPersonaName)
                    } else {
                        backend.getPlaybackStreams(videoId)
                    }
                }
                val candidates = selectPlaybackSources(streams)
                if (candidates.isEmpty()) throw MissingPlaybackSourceException()
                currentPersonaName = streams.personaName
                sourceCandidates = candidates
                startCandidate(index = 0, positionToRestore = position)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                isLoading = false
                playbackError = if (failure is MissingPlaybackSourceException) {
                    "Engine tidak menemukan stream yang bisa diputar untuk video ini."
                } else {
                    "Stream video tidak bisa dimuat. Periksa koneksi lalu coba lagi."
                }
            }
        }
    }

    private fun handlePlayerError() {
        val position = player?.currentPosition ?: resumePositionMs
        val nextIndex = nextPlaybackSourceIndex(sourceCandidates, currentSourceIndex)
        if (nextIndex != null) {
            startCandidate(index = nextIndex, positionToRestore = position)
        } else if (!automaticRefreshAttempted) {
            automaticRefreshAttempted = true
            loadStreams(
                refresh = true,
                automaticRefresh = true,
                positionToRestore = position,
            )
        } else {
            isLoading = false
            playbackError = "Semua stream yang tersedia gagal. Periksa koneksi lalu coba muat ulang."
        }
    }

    private fun startCandidate(index: Int, positionToRestore: Long) {
        val source = sourceCandidates.getOrNull(index) ?: return
        val activePlayer = player ?: return
        currentSourceIndex = index
        sourcePrepareJob?.cancel()
        isLoading = true
        playbackError = null
        sourcePrepareJob = lifecycleScope.launch {
            try {
                httpDataSourceFactory.setUserAgent(source.userAgent ?: FALLBACK_USER_AGENT)
                val (mediaItem, newManifestFile) = createMediaItem(source, intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty())
                if (player !== activePlayer || currentSourceIndex != index) return@launch
                activePlayer.setMediaItem(mediaItem)
                if (positionToRestore > 0L) activePlayer.seekTo(positionToRestore)
                activePlayer.prepare()
                activePlayer.playWhenReady = true
                resumePositionMs = 0L
                val previousManifest = manifestFile
                manifestFile = newManifestFile
                if (previousManifest != newManifestFile) previousManifest?.delete()
                isLoading = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (currentSourceIndex != index) return@launch
                val nextIndex = nextPlaybackSourceIndex(sourceCandidates, index)
                if (nextIndex != null) {
                    startCandidate(index = nextIndex, positionToRestore = positionToRestore)
                } else {
                    handlePlayerError()
                }
            }
        }
    }

    private suspend fun createMediaItem(
        source: PlaybackSource,
        videoId: String,
    ): Pair<MediaItem, File?> {
        val (uri, tempFile) = withContext(Dispatchers.IO) {
            if (source.manifestContent != null) {
                val file = File.createTempFile("lontar-${videoId.take(8)}-", ".mpd", cacheDir)
                file.writeText(source.manifestContent, Charsets.UTF_8)
                Uri.fromFile(file) to file
            } else {
                val sourceUrl = source.url ?: throw MissingPlaybackSourceException()
                Uri.parse(sourceUrl) to null
            }
        }
        val builder = MediaItem.Builder().setUri(uri)
        source.mimeType?.let { builder.setMimeType(it) }
        return builder.build() to tempFile
    }

    companion object {
        const val EXTRA_VIDEO_ID = "com.lontar.app.extra.VIDEO_ID"
        const val EXTRA_VIDEO_TITLE = "com.lontar.app.extra.VIDEO_TITLE"
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val PIP_TRANSITION_GRACE_MS = 350L
        private const val FALLBACK_USER_AGENT = "Lontar/0.1.0 (Android)"
    }
}

private class MissingPlaybackSourceException : Exception()

@Composable
private fun PlayerContent(
    title: String,
    player: Player?,
    isInPip: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    canEnterPip: Boolean,
    onPlayerViewReady: (PlayerView) -> Unit,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isInPip) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹  Kembali") }
                if (canEnterPip) OutlinedButton(onClick = onEnterPip) { Text("PiP") }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isInPip) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        useController = !isInPip
                        this.player = player
                        onPlayerViewReady(this)
                    }
                },
                update = { view ->
                    view.player = player
                    view.useController = !isInPip
                },
            )
            if (isLoading) CircularProgressIndicator()
        }

        if (!isInPip) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.secondary)
                    Button(onClick = onRetry) { Text("Muat ulang stream") }
                } else if (!canEnterPip) {
                    Text("Picture-in-Picture tidak tersedia di perangkat ini.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
