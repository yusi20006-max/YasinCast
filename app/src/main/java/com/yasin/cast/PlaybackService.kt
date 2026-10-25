package com.yasin.cast

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns playback independently of the Activity/Compose lifecycle.
 * Media3 publishes session state and transport controls to Android system UI.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_SET_SLEEP_TIMER = "com.yasin.cast.action.SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.yasin.cast.action.CANCEL_SLEEP_TIMER"
        const val EXTRA_MINUTES = "minutes"
    }

    private var mediaSession: MediaSession? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerTask: Runnable? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).coerceIn(0, 24 * 60)
                sleepTimerTask?.let(timerHandler::removeCallbacks)
                sleepTimerTask = null
                if (minutes > 0) {
                    val task = Runnable {
                        mediaSession?.player?.pause()
                        sleepTimerTask = null
                    }
                    sleepTimerTask = task
                    timerHandler.postDelayed(task, minutes * 60_000L)
                }
            }
            ACTION_CANCEL_SLEEP_TIMER -> {
                sleepTimerTask?.let(timerHandler::removeCallbacks)
                sleepTimerTask = null
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        sleepTimerTask?.let(timerHandler::removeCallbacks)
        sleepTimerTask = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
