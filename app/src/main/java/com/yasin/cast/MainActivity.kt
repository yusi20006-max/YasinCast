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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var libraryRepository: AudioLibraryRepository
    private val library = mutableStateListOf<AudioLibraryEntry>()
    private val playedUris = mutableStateMapOf<String, Boolean>()
    private val progressLabels = mutableStateMapOf<String, String>()
    private val recentHistory = mutableStateListOf<ListeningEventEntity>()
    private var progressJob: Job? = null
    private var completedUri: String? = null
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
            lifecycleScope.launch {
                imported.forEach { libraryRepository.upsert(it) }
                refreshLibrary()
                if (imported.isEmpty()) {
                    playbackStatus = "فایل صوتی قابل‌شناسایی انتخاب نشد."
                } else {
                    playbackStatus = "${imported.size} فایل به کتابخانه اضافه شد."
                    playEntry(imported.first())
                }
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
            lifecycleScope.launch {
                found.forEach { libraryRepository.upsert(it) }
                refreshLibrary()
                playbackStatus = if (found.isEmpty()) {
                    "در این پوشه فایل صوتی قابل‌شناسایی پیدا نشد."
                } else {
                    "${found.size} فایل صوتی از پوشه اضافه شد."
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) startProgressCheckpoint() else saveCurrentProgress()
            playbackStatus = when {
                playing -> "در حال پخش"
                mediaController?.playbackState == Player.STATE_ENDED -> {
                    markCurrentItemCompleted()
                    "پخش به پایان رسید"
                }
                mediaController?.playbackState == Player.STATE_BUFFERING -> "در حال آماده‌سازی…"
                else -> "متوقف"
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackStatus = when (playbackState) {
                Player.STATE_BUFFERING -> "در حال آماده‌سازی…"
                Player.STATE_READY -> if (mediaController?.isPlaying == true) "در حال پخش" else "آمادهٔ پخش"
                Player.STATE_ENDED -> {
                    markCurrentItemCompleted()
                    "پخش به پایان رسید"
                }
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
        libraryRepository = AudioLibraryRepository.create(this)
        lifecycleScope.launch {
            libraryRepository.initialize()
            refreshLibrary()
            refreshRecentHistory()
        }

        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                pendingUri?.let {
                    lifecycleScope.launch { startPlayback(controller, it) }
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
                        history = recentHistory,
                        playedUris = playedUris,
                        progressLabels = progressLabels,
                        onImportFiles = { openAudio.launch(arrayOf("audio/*")) },
                        onImportFolder = { openFolder.launch(null) },
                        onPlayEntry = { playEntry(it) },
                        onReplayFromStart = { replayFromStart(it) },
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

    private suspend fun refreshLibrary() {
        val items = libraryRepository.all()
        library.clear()
        library.addAll(items.map { it.toLibraryEntry() })
        playedUris.clear()
        progressLabels.clear()
        items.forEach { item ->
            playedUris[item.uri] = item.played
            progressLabels[item.uri] = if (item.lastPositionMs > 0L) "ادامه از ${item.lastPositionMs / 1000} ثانیه" else "شروع‌نشده"
        }
    }

    private suspend fun refreshRecentHistory() {
        recentHistory.clear()
        recentHistory.addAll(libraryRepository.recentHistory(5))
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
        lifecycleScope.launch { playEntryInternal(entry) }
    }

    private suspend fun playEntryInternal(entry: AudioLibraryEntry) {
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

    private suspend fun startPlayback(controller: MediaController, uri: Uri) {
        val uriString = uri.toString()
        val state = libraryRepository.playbackState(uriString)
        selectedUri = uriString
        selectedTitle = state?.title ?: library.firstOrNull { it.uri == uriString }?.title
            ?: queryAudioEntry(this, uri).title
        completedUri = null
        controller.setMediaItem(MediaItem.fromUri(uri))
        controller.prepare()
        val resumeAt = PlaybackCompletionRules.resumePosition(state?.lastPositionMs ?: 0L, state?.durationMs)
        if (resumeAt > 0L) controller.seekTo(resumeAt)
        controller.play()
        libraryRepository.recordStarted(uriString, selectedTitle, resumeAt)
        refreshRecentHistory()
        playbackStatus = if (resumeAt > 0L) "ادامه از ${resumeAt / 1000} ثانیه" else "در حال آماده‌سازی…"
    }

    private fun replayFromStart(entry: AudioLibraryEntry) {
        lifecycleScope.launch {
            libraryRepository.resetPlayback(entry.uri)
            refreshLibrary()
            playEntry(entry)
        }
    }

    private fun removeEntry(entry: AudioLibraryEntry) {
        lifecycleScope.launch {
            libraryRepository.remove(entry.uri)
            refreshLibrary()
            if (selectedUri == entry.uri) {
                selectedUri = null
                selectedTitle = "هنوز فایلی انتخاب نشده است"
                playbackStatus = "از کتابخانه حذف شد؛ فایل اصلی دست‌نخورده باقی ماند."
            }
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

    private fun startProgressCheckpoint() {
        if (progressJob?.isActive == true) return
        progressJob = lifecycleScope.launch {
            while (isPlaying) {
                delay(5_000L)
                saveCurrentProgress()
            }
        }
    }

    private fun saveCurrentProgress() {
        val controller = mediaController ?: return
        val uri = selectedUri ?: return
        val position = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0L }
        lifecycleScope.launch {
            libraryRepository.saveProgress(uri, position, duration)
        }
    }

    private fun markCurrentItemCompleted() {
        val controller = mediaController ?: return
        val uri = selectedUri ?: return
        if (completedUri == uri) return
        val duration = controller.duration
        val position = if (duration > 0L) duration else controller.currentPosition
        if (duration <= 0L || !PlaybackCompletionRules.isCompleted(position, duration)) return
        completedUri = uri
        val title = selectedTitle
        lifecycleScope.launch {
            libraryRepository.markPlayed(uri, title, position, duration)
            refreshLibrary()
            refreshRecentHistory()
        }
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

    override fun onStop() {
        saveCurrentProgress()
        super.onStop()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        mediaController = null
        controllerFuture = null
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
    history: List<ListeningEventEntity>,
    playedUris: Map<String, Boolean>,
    progressLabels: Map<String, String>,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onPlayEntry: (AudioLibraryEntry) -> Unit,
    onReplayFromStart: (AudioLibraryEntry) -> Unit,
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
                                text = if (playedUris[entry.uri] == true) "شنیده‌شده · ${progressLabels[entry.uri] ?: ""}" else (progressLabels[entry.uri] ?: "شروع‌نشده"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = listOfNotNull(entry.mimeType, AudioLibraryRules.formatSize(entry.sizeBytes))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onPlayEntry(entry) }) { Text("ادامه/پخش") }
                                Button(onClick = { onReplayFromStart(entry) }) { Text("از اول") }
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
            if (history.isNotEmpty()) {
                Text("تاریخچهٔ اخیر", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall)
                history.take(3).forEach { event ->
                    Text(
                        text = "${event.title} · ${if (event.eventType == "completed") "کامل‌شده" else "شروع‌شده"}",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
