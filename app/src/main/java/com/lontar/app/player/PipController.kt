package com.lontar.app.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

internal class PipController(
    private val activity: Activity,
    private val player: () -> Player?,
    private val sourceView: () -> View?,
) {
    private var attachedPlayer: Player? = null
    private var receiverRegistered = false

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateParams()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateParams()
        override fun onPlaybackStateChanged(playbackState: Int) = updateParams()
        override fun onVideoSizeChanged(videoSize: VideoSize) = updateParams()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val activePlayer = player() ?: return
            when (intent?.action) {
                ACTION_REWIND -> activePlayer.seekBack()
                ACTION_FORWARD -> activePlayer.seekForward()
                ACTION_TOGGLE -> {
                    if (activePlayer.isPlaying) {
                        activePlayer.pause()
                    } else {
                        if (activePlayer.playbackState == Player.STATE_ENDED) {
                            activePlayer.seekToDefaultPosition()
                        }
                        activePlayer.play()
                    }
                }
            }
            updateParams()
        }
    }

    fun attach() {
        if (!isSupported) return
        val activePlayer = player() ?: return
        if (attachedPlayer !== activePlayer) {
            attachedPlayer?.removeListener(listener)
            attachedPlayer = activePlayer.also { it.addListener(listener) }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_REWIND)
                addAction(ACTION_TOGGLE)
                addAction(ACTION_FORWARD)
            }
            ContextCompat.registerReceiver(
                activity,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        updateParams()
    }

    fun detach() {
        attachedPlayer?.removeListener(listener)
        attachedPlayer = null
        if (receiverRegistered) {
            activity.unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoEnter()) enter()
    }

    fun enter() {
        if (!isSupported || activity.isInPictureInPictureMode) return
        try {
            activity.enterPictureInPictureMode(buildParams())
        } catch (_: IllegalStateException) {
            // A transient activity transition can make PiP entry unavailable.
        }
    }

    fun updateParams() {
        if (!isSupported) return
        try {
            activity.setPictureInPictureParams(buildParams())
        } catch (_: IllegalStateException) {
            // Ignore updates while the activity is transitioning.
        }
    }

    private fun shouldAutoEnter(): Boolean {
        val activePlayer = player() ?: return false
        return activePlayer.playWhenReady &&
            activePlayer.playbackState != Player.STATE_IDLE &&
            activePlayer.playbackState != Player.STATE_ENDED
    }

    private fun buildParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(currentAspectRatio())
            .setActions(buildActions())
        sourceRect()?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoEnter())
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun currentAspectRatio(): Rational {
        val size = player()?.videoSize ?: return DEFAULT_ASPECT_RATIO
        if (size.width <= 0 || size.height <= 0) return DEFAULT_ASPECT_RATIO
        val ratio = size.width * size.pixelWidthHeightRatio / size.height
        return when {
            ratio > MAX_ASPECT_RATIO -> Rational(239, 100)
            ratio < MIN_ASPECT_RATIO -> Rational(100, 239)
            else -> Rational((ratio * 1000).toInt().coerceAtLeast(1), 1000)
        }
    }

    private fun sourceRect(): Rect? {
        val view = sourceView() ?: return null
        val rect = Rect()
        return if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) rect else null
    }

    private fun buildActions(): List<RemoteAction> {
        val isPlaying = player()?.isPlaying == true
        return listOf(
            action(android.R.drawable.ic_media_rew, "Mundur 10 detik", ACTION_REWIND, REQUEST_REWIND),
            if (isPlaying) {
                action(android.R.drawable.ic_media_pause, "Jeda", ACTION_TOGGLE, REQUEST_TOGGLE)
            } else {
                action(android.R.drawable.ic_media_play, "Putar", ACTION_TOGGLE, REQUEST_TOGGLE)
            },
            action(android.R.drawable.ic_media_ff, "Maju 10 detik", ACTION_FORWARD, REQUEST_FORWARD),
        )
    }

    private fun action(
        @DrawableRes iconRes: Int,
        title: String,
        actionName: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(actionName).setPackage(activity.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(activity, iconRes), title, title, pendingIntent)
    }

    private companion object {
        const val ACTION_REWIND = "com.lontar.app.pip.REWIND"
        const val ACTION_TOGGLE = "com.lontar.app.pip.TOGGLE"
        const val ACTION_FORWARD = "com.lontar.app.pip.FORWARD"
        const val REQUEST_REWIND = 1
        const val REQUEST_TOGGLE = 2
        const val REQUEST_FORWARD = 3
        val DEFAULT_ASPECT_RATIO = Rational(16, 9)
        const val MAX_ASPECT_RATIO = 2.39f
        const val MIN_ASPECT_RATIO = 1f / 2.39f
    }
}
