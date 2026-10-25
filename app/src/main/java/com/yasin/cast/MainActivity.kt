package com.yasin.cast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    private lateinit var libraryStore: AudioLibraryStore
    private val library = mutableStateListOf<AudioLibraryEntry>()
    private var selectedUri by mutableStateOf<String?>(null)
    private var selectedTitle by mutableStateOf("هنوز فایلی انتخاب نشده است")
    private var isPlaying by mutableStateOf(false)
    private var playbackStatus by mutableStateOf("برای افزودن فایل، از انتخاب‌گر اندروید استفاده کنید.")
    private var playbackSpeed by mutableStateOf(1.0f)
    private var pendingUri: Uri? = null

    private val openAudio = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val imported = uris.mapNotNull { uri ->
                persistReadGrant(uri)
                runCatching { queryAudioEntry(this, uri) }.getOrNull()
                    ?.takeIf { AudioLibraryRules.isAudio(it.mimeType, it.title) }
            }
            imported.forEach(libraryStore::upsert)
            refreshLibrary()
            if (imported.isEmpty()) {
                playbackStatus = "فایل صوتی قابل‌شناسایی انتخاب نشد."
            } else {
                playbackStatus = "${imported.size} فایل به کتابخانه اضافه شد."
                playEntry(imported.first())
            }
        }
    }

    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            persistReadGrant(treeUri)
            val found = runCatching { findAudioInTree(treeUri) }.getOrElse {
                playbackStatus = "خواندن پوشه ممکن نشد؛ پوشهٔ دیگری انتخاب کنید."
                emptyList()
            }
            found.forEach(libraryStore::upsert)
            refreshLibrary()
            playbackStatus = if (found.isEmpty()) {
                "در این پوشه فایل صوتی قابل‌شناسایی پیدا نشد."
            } else {
                "${found.size} فایل صوتی از پوشه اضافه شد."
            }
        }
    }

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
            playbackStatus = "این فایل قابل پخش نیست؛ فایل حذف‌شده، دسترسی یا قالب آن را بررسی کنید."
            isPlaying = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryStore = AudioLibraryStore(this)
        refreshLibrary()

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
                    LibraryAndPlayerScreen(
                        entries = library,
                        selectedUri = selectedUri,
                        title = selectedTitle,
                        status = playbackStatus,
                        playing = isPlaying,
                        speed = playbackSpeed,
                        onImportFiles = { openAudio.launch(arrayOf("audio/*")) },
                        onImportFolder = { openFolder.launch(null) },
                        onPlayEntry = { playEntry(it) },
                        onRemoveEntry = { removeEntry(it) },
                        onPlayPause = { togglePlayback() },
                        onBack = { seekBy(-15_000L) },
                        onForward = { seekBy(15_000L) },
                        onSpeed = { changeSpeed() }
                    )
                }
            }
        }
    }

    private fun refreshLibrary() {
        library.clear()
        library.addAll(libraryStore.all())
    }

    private fun persistReadGrant(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Providers without persistable grants remain usable for this session if the grant is still valid.
        } catch (_: IllegalArgumentException) {
            // A provider may return a URI that does not support persistable grants.
        }
    }

    private fun playEntry(entry: AudioLibraryEntry) {
        val uri = Uri.parse(entry.uri)
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { /* Verify that the document is still readable. */ }
                ?: run {
                    playbackStatus = "فایل در دسترس نیست؛ آن را دوباره وارد کنید یا از کتابخانه حذف کنید."
                    return
                }
        } catch (_: Exception) {
            playbackStatus = "فایل یا دسترسی آن در دسترس نیست؛ دوباره واردش کنید یا حذفش کنید."
            return
        }
        selectedUri = entry.uri
        selectedTitle = entry.title
        val controller = mediaController
        if (controller == null) {
            pendingUri = uri
            playbackStatus = "در حال اتصال به سرویس پخش…"
        } else {
            startPlayback(controller, uri)
        }
    }

    private fun startPlayback(controller: MediaController, uri: Uri) {
        selectedUri = uri.toString()
        selectedTitle = library.firstOrNull { it.uri == uri.toString() }?.title
            ?: queryAudioEntry(this, uri).title
        controller.setMediaItem(MediaItem.fromUri(uri))
        controller.prepare()
        controller.play()
        playbackStatus = "در حال آماده‌سازی…"
    }

    private fun removeEntry(entry: AudioLibraryEntry) {
        libraryStore.remove(entry.uri)
        refreshLibrary()
        if (selectedUri == entry.uri) {
            selectedUri = null
            selectedTitle = "هنوز فایلی انتخاب نشده است"
            playbackStatus = "از کتابخانه حذف شد؛ فایل اصلی دست‌نخورده باقی ماند."
        }
        // Do not revoke permissions or delete the original document.
    }

    private fun findAudioInTree(treeUri: Uri): List<AudioLibraryEntry> {
        val results = linkedMapOf<String, AudioLibraryEntry>()
        val visited = mutableSetOf<String>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)

        fun walk(documentId: String, depth: Int) {
            if (depth > 12 || !visited.add(documentId) || visited.size > 5000) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val children = mutableListOf<Triple<String, String, String?>>()
            try {
                contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        children += Triple(
                            cursor.getString(idColumn),
                            cursor.getString(nameColumn) ?: "فایل صوتی",
                            cursor.getString(mimeColumn)
                        )
                    }
                }
            } catch (_: Exception) {
                return
            }
            children.forEach { (id, name, mime) ->
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(id, depth + 1)
                } else if (AudioLibraryRules.isAudio(mime, name)) {
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val entry = runCatching { queryAudioEntry(this, childUri) }.getOrElse {
                        AudioLibraryEntry(childUri.toString(), name, mime, null)
                    }
                    results[entry.uri] = entry.copy(title = entry.title.ifBlank { name })
                }
            }
        }

        walk(rootId, 0)
        return results.values.toList()
    }

    private fun togglePlayback() {
        val controller = mediaController
        if (controller == null) {
            playbackStatus = "سرویس پخش هنوز آماده نیست."
            return
        }
        if (controller.isPlaying || controller.playWhenReady) controller.pause()
        else if (controller.mediaItemCount > 0) controller.play()
        else library.firstOrNull()?.let(::playEntry) ?: run {
            playbackStatus = "ابتدا یک فایل صوتی به کتابخانه اضافه کنید."
        }
    }

    private fun seekBy(offsetMs: Long) {
        val controller = mediaController ?: return
        val target = (controller.currentPosition + offsetMs).coerceAtLeast(0L)
        val duration = controller.duration
        controller.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
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
        if (::libraryStore.isInitialized) libraryStore.close()
        super.onDestroy()
    }
}

@Composable
private fun LibraryAndPlayerScreen(
    entries: List<AudioLibraryEntry>,
    selectedUri: String?,
    title: String,
    status: String,
    playing: Boolean,
    speed: Float,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onPlayEntry: (AudioLibraryEntry) -> Unit,
    onRemoveEntry: (AudioLibraryEntry) -> Unit,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeed: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "یاسین‌کست",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = status,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(onClick = onImportFiles) { Text("افزودن فایل") }
                Button(onClick = onImportFolder) { Text("افزودن پوشه") }
            }
            if (entries.isEmpty()) {
                Text(
                    text = "کتابخانه خالی است. فایل صوتی یا پوشه‌ای را انتخاب کنید تا اینجا نمایش داده شود.",
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries, key = { it.uri }) { entry ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (entry.uri == selectedUri) "▶ ${entry.title}" else entry.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = listOfNotNull(entry.mimeType, AudioLibraryRules.formatSize(entry.sizeBytes))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onPlayEntry(entry) }) { Text("پخش") }
                                Button(onClick = { onRemoveEntry(entry) }) { Text("حذف از فهرست") }
                            }
                        }
                    }
                }
            }
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack) { Text("۱۵ ثانیه عقب") }
                Button(onClick = onPlayPause) { Text(if (playing) "توقف" else "پخش") }
                Button(onClick = onForward) { Text("۱۵ ثانیه جلو") }
            }
            Button(onClick = onSpeed, modifier = Modifier.padding(top = 4.dp)) {
                Text("سرعت پخش: %.1fx".format(speed))
            }
        }
    }
}
