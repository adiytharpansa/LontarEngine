package com.lontar.app.player

import com.bunny.core.model.PlaybackStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceSelectorTest {
    @Test
    fun keepsAllAvailableRoutesInEnginePriorityOrderAndPreservesUserAgent() {
        val result = selectPlaybackSources(
            PlaybackStreams(
                dashManifestUrl = "https://example.test/video.mpd",
                hlsUrl = "https://example.test/video.m3u8",
                progressiveUrl = "https://example.test/video.mp4",
                expiresAtEpochMs = null,
                dashManifestContent = "<MPD />",
                playbackUserAgent = "engine-agent",
            ),
        )

        assertEquals(
            listOf(
                PlaybackSourceType.DASH_CONTENT,
                PlaybackSourceType.DASH_URL,
                PlaybackSourceType.HLS_URL,
                PlaybackSourceType.PROGRESSIVE_URL,
            ),
            result.map { it.type },
        )
        assertTrue(result.all { it.userAgent == "engine-agent" })
        assertEquals("<MPD />", result.first().manifestContent)
        assertEquals(1, nextPlaybackSourceIndex(result, currentIndex = 0))
        assertNull(nextPlaybackSourceIndex(result, currentIndex = result.lastIndex))
    }

    @Test
    fun omitsUnavailableRoutesWithoutReorderingFallbacks() {
        val result = selectPlaybackSources(
            streams(hlsUrl = "https://example.test/a.m3u8", progressiveUrl = "https://example.test/a.mp4"),
        )
        assertEquals(
            listOf(PlaybackSourceType.HLS_URL, PlaybackSourceType.PROGRESSIVE_URL),
            result.map { it.type },
        )
    }

    @Test
    fun ignoresBlankRoutesAndBlankUserAgent() {
        val result = selectPlaybackSources(
            PlaybackStreams(
                dashManifestUrl = "   ",
                hlsUrl = "",
                progressiveUrl = "https://example.test/video.mp4",
                expiresAtEpochMs = null,
                dashManifestContent = "  ",
                playbackUserAgent = "  ",
            ),
        )

        assertEquals(listOf(PlaybackSourceType.PROGRESSIVE_URL), result.map { it.type })
        assertNull(result.single().userAgent)
    }

    @Test
    fun returnsNoCandidatesWhenEveryRouteIsAbsent() {
        assertTrue(selectPlaybackSources(streams()).isEmpty())
    }

    private fun streams(
        dashUrl: String? = null,
        hlsUrl: String? = null,
        progressiveUrl: String? = null,
    ) = PlaybackStreams(
        dashManifestUrl = dashUrl,
        hlsUrl = hlsUrl,
        progressiveUrl = progressiveUrl,
        expiresAtEpochMs = null,
    )
}
