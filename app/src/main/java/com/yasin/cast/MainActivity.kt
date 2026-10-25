package com.yasin.cast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var selectedTitle by mutableStateOf("هنوز فایلی انتخاب نشده است")
    private var isPlaying by mutableStateOf(false)
    private var playbackStatus by mutableStateOf("برای شروع، یک فایل صوتی انتخاب کنید.")
    private var playbackSpeed by mutableStateOf(1.0f)

    private val openAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers do not offer persistable grants; the current grant may still work.
            }
            selectedTitle = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "فایل صوتی انتخاب‌شده"
            val controller = mediaController
            if (controller == null) {
                playbackStatus = "در حال اتصال به سرویس پخش…"
                pendingUri = uri
            } else {
                startPlayback(controller, uri)
            }
        }
    }

    private var pendingUri: Uri? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            playbackStatus = when {
                playing -> "در حال پخش"
                mediaController?.playbackState == Player.STATE_ENDED -> "پخش به پایان رسید"
                mediaController?.playbackState == Player.STATE_BUFFERING -> "در حال آماده‌سازی…"
                else -> "متوقف"
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackStatus = when (playbackState) {
                Player.STATE_BUFFERING -> "در حال آماده‌سازی…"
                Player.STATE_READY -> if (mediaController?.isPlaying == true) "در حال پخش" else "آمادهٔ پخش"
                Player.STATE_ENDED -> "پخش به پایان رسید"
                else -> "آماده"
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            playbackStatus = "خطا در پخش فایل؛ قالب یا فایل را بررسی کنید."
            isPlaying = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                pendingUri?.let {
                    startPlayback(controller, it)
                    pendingUri = null
                }
                isPlaying = controller.isPlaying
                playbackSpeed = controller.playbackParameters.speed
            } catch (_: Exception) {
                playbackStatus = "اتصال به سرویس پخش ناموفق بود."
            }
        }, mainExecutor)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(
                        title = selectedTitle,
                        status = playbackStatus,
                        playing = isPlaying,
                        speed = playbackSpeed,
                        onChooseFile = { openAudio.launch(arrayOf("audio/*")) },
                        onPlayPause = { togglePlayback() },
                        onBack = { mediaController?.seekBack() },
                        onForward = { mediaController?.seekForward() },
                        onSpeed = { changeSpeed() }
                    )
                }
            }
        }
    }

    private fun startPlayback(controller: MediaController, uri: Uri) {
        controller.setMediaItem(MediaItem.fromUri(uri))
        controller.prepare()
        controller.play()
        playbackStatus = "در حال آماده‌سازی…"
    }

    private fun togglePlayback() {
        val controller = mediaController
        if (controller == null) {
            playbackStatus = "سرویس پخش هنوز آماده نیست."
            return
        }
        if (controller.isPlaying || controller.playWhenReady) controller.pause()
        else if (controller.mediaItemCount > 0) controller.play()
        else openAudio.launch(arrayOf("audio/*"))
    }

    private fun changeSpeed() {
        val controller = mediaController ?: return
        val next = if (playbackSpeed >= 1.99f) 0.8f else (playbackSpeed + 0.2f).coerceAtMost(2.0f)
        playbackSpeed = next
        controller.setPlaybackSpeed(next)
    }

    override fun onDestroy() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        mediaController = null
        controllerFuture = null
        super.onDestroy()
    }
}

@Composable
private fun PlayerScreen(
    title: String,
    status: String,
    playing: Boolean,
    speed: Float,
    onChooseFile: () -> Unit,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeed: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "یاسین‌کست",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = status,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onChooseFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text("انتخاب فایل صوتی")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack) { Text("۱۵ ثانیه عقب") }
                Button(onClick = onPlayPause) { Text(if (playing) "توقف" else "پخش") }
                Button(onClick = onForward) { Text("۱۵ ثانیه جلو") }
            }
            Button(
                onClick = onSpeed,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("سرعت پخش: %.1fx".format(speed))
            }
            Text(
                text = "پخش پس‌زمینه و کنترل اعلان با Media3",
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
