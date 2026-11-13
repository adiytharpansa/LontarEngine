package com.lontar.app.player

import com.bunny.core.model.PlaybackStreams

internal enum class PlaybackSourceType {
    DASH_CONTENT,
    DASH_URL,
    HLS_URL,
    PROGRESSIVE_URL,
}

internal data class PlaybackSource(
    val type: PlaybackSourceType,
    val url: String? = null,
    val manifestContent: String? = null,
    val mimeType: String? = null,
    val userAgent: String? = null,
)

internal fun selectPlaybackSources(streams: PlaybackStreams): List<PlaybackSource> {
    val userAgent = streams.playbackUserAgent?.takeIf(String::isNotBlank)
    return buildList {
        streams.dashManifestContent?.takeIf(String::isNotBlank)?.let { content ->
            add(
                PlaybackSource(
                    type = PlaybackSourceType.DASH_CONTENT,
                    manifestContent = content,
                    mimeType = "application/dash+xml",
                    userAgent = userAgent,
                ),
            )
        }
        streams.dashManifestUrl?.takeIf(String::isNotBlank)?.let { url ->
            add(
                PlaybackSource(
                    type = PlaybackSourceType.DASH_URL,
                    url = url,
                    mimeType = "application/dash+xml",
                    userAgent = userAgent,
                ),
            )
        }
        streams.hlsUrl?.takeIf(String::isNotBlank)?.let { url ->
            add(
                PlaybackSource(
                    type = PlaybackSourceType.HLS_URL,
                    url = url,
                    mimeType = "application/x-mpegURL",
                    userAgent = userAgent,
                ),
            )
        }
        streams.progressiveUrl?.takeIf(String::isNotBlank)?.let { url ->
            add(
                PlaybackSource(
                    type = PlaybackSourceType.PROGRESSIVE_URL,
                    url = url,
                    userAgent = userAgent,
                ),
            )
        }
    }
}

internal fun nextPlaybackSourceIndex(
    candidates: List<PlaybackSource>,
    currentIndex: Int,
): Int? = (currentIndex + 1).takeIf { it in candidates.indices }
