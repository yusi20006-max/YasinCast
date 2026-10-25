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
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    private val bookmarks = mutableStateListOf<AudioBookmark>()
    private val bookmarkStore by lazy { BookmarkStore(this) }
    private var sleepTimerStatus by mutableStateOf("تایمر خواب خاموش است")
    private var progressJob: Job? = null
    private var completedUri: String? = null
    private var selectedUri by mutableStateOf<String?>(null)
    private var selectedTitle by mutableStateOf("هنوز فایلی انتخاب نشده است")
    private var isPlaying by mutableStateOf(false)
    private var playbackStatus by mutableStateOf("برای افزودن فایل، از انتخاب‌گر اندروید استفاده کنید.")
    private var playbackSpeed by mutableStateOf(1.0f)
    private var currentPositionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(0L)
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
            updatePlaybackPosition()
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
            updatePlaybackPosition()
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
            refreshBookmarks()
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
                        positionMs = currentPositionMs,
                        durationMs = durationMs,
                        history = recentHistory,
                        playedUris = playedUris,
                        progressLabels = progressLabels,
                        bookmarks = bookmarks,
                        sleepTimerStatus = sleepTimerStatus,
                        onImportFiles = { openAudio.launch(arrayOf("audio/*")) },
                        onImportFolder = { openFolder.launch(null) },
                        onPlayEntry = { playEntry(it) },
                        onReplayFromStart = { replayFromStart(it) },
                        onRemoveEntry = { removeEntry(it) },
                        onPlayPause = { togglePlayback() },
                        onBack = { seekBy(-15_000L) },
                        onForward = { seekBy(15_000L) },
                        onSpeed = { changeSpeed() },
                        onSeekTo = { seekTo(it) },
                        onAddBookmark = { addBookmark(it) },
                        onDeleteBookmark = { deleteBookmark(it) },
                        onSelectBookmark = { selectBookmark(it) },
                        onSetSleepTimer = { setSleepTimer(it) },
                        onCancelSleepTimer = { cancelSleepTimer() }
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

    private suspend fun refreshBookmarks() {
        bookmarks.clear()
        bookmarks.addAll(bookmarkStore.all())
    }

    private fun addBookmark(note: String) {
        val uri = selectedUri
        if (uri == null) {
            playbackStatus = "ابتدا یک فایل را پخش یا انتخاب کنید."
            return
        }
        bookmarkStore.add(uri, selectedTitle, mediaController?.currentPosition ?: currentPositionMs, note)
        lifecycleScope.launch { refreshBookmarks() }
        playbackStatus = "نشانک ذخیره شد."
    }

    private fun deleteBookmark(id: Long) {
        bookmarkStore.remove(id)
        lifecycleScope.launch { refreshBookmarks() }
    }

    private fun selectBookmark(bookmark: AudioBookmark) {
        lifecycleScope.launch {
            if (mediaController == null) {
                playbackStatus = "سرویس پخش هنوز آماده نیست."
                return@launch
            }
            if (selectedUri != bookmark.uri) {
                val entry = library.firstOrNull { it.uri == bookmark.uri }
                if (entry == null) {
                    playbackStatus = "فایل نشانک در کتابخانه نیست؛ آن را دوباره وارد کنید."
                    return@launch
                }
                playEntryInternal(entry)
                delay(600L)
            }
            mediaController?.seekTo(bookmark.positionMs)
            updatePlaybackPosition()
            playbackStatus = "رفتن به نشانک: " + bookmark.title
        }
    }

    private fun setSleepTimer(minutes: Int) {
        val intent = Intent(this, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_SET_SLEEP_TIMER)
            .putExtra(PlaybackService.EXTRA_MINUTES, minutes)
        startService(intent)
        sleepTimerStatus = "توقف پخش پس از " + minutes + " دقیقه"
    }

    private fun cancelSleepTimer() {
        startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_CANCEL_SLEEP_TIMER))
        sleepTimerStatus = "تایمر خواب لغو شد."
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
            playEntryInternal(entry)
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
            var elapsedSinceSaveMs = 0L
            while (isPlaying) {
                delay(500L)
                updatePlaybackPosition()
                elapsedSinceSaveMs += 500L
                if (elapsedSinceSaveMs >= 5_000L) {
                    saveCurrentProgress()
                    elapsedSinceSaveMs = 0L
                }
            }
        }
    }

    private fun updatePlaybackPosition() {
        val controller = mediaController ?: return
        currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
        durationMs = controller.duration.takeIf { it > 0L } ?: 0L
        if (controller.playbackParameters.speed != playbackSpeed) playbackSpeed = controller.playbackParameters.speed
    }

    private fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val target = positionMs.coerceAtLeast(0L).let { if (controller.duration > 0L) it.coerceAtMost(controller.duration) else it }
        controller.seekTo(target)
        currentPositionMs = target
        saveCurrentProgress()
    }

    private fun saveCurrentProgress() {
        val controller = mediaController ?: return
        val uri = selectedUri ?: return
        val position = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0L }
        lifecycleScope.launch {
            libraryRepository.saveProgress(uri, position, duration)
            progressLabels[uri] = if (position > 0L) "ادامه از ${position / 1000} ثانیه" else "شروع‌نشده"
            if (duration != null && PlaybackCompletionRules.isCompleted(position, duration)) {
                markCurrentItemCompleted()
            }
        }
    }

    private fun markCurrentItemCompleted() {
        val controller = mediaController ?: return
        val uri = selectedUri ?: return
        if (completedUri == uri || playedUris[uri] == true) return
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
    positionMs: Long,
    durationMs: Long,
    history: List<ListeningEventEntity>,
    playedUris: Map<String, Boolean>,
    progressLabels: Map<String, String>,
    bookmarks: List<AudioBookmark>,
    sleepTimerStatus: String,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onPlayEntry: (AudioLibraryEntry) -> Unit,
    onReplayFromStart: (AudioLibraryEntry) -> Unit,
    onRemoveEntry: (AudioLibraryEntry) -> Unit,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSelectBookmark: (AudioBookmark) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("library") }
    var searchQuery by remember { mutableStateOf("") }
    var bookmarkNote by remember { mutableStateOf("") }
    val filteredEntries = entries.filter { entry ->
        val query = normalizeSearchText(searchQuery)
        query.isBlank() || normalizeSearchText(entry.title).contains(query) || normalizeSearchText(entry.uri.substringAfterLast('/')).contains(query)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "یاسین‌کست",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "کتابخانه و پخش‌کنندهٔ صوتی آفلاین",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { selectedTab = "library" }, modifier = Modifier.weight(1f), enabled = selectedTab != "library") { Text("کتابخانه") }
                Button(onClick = { selectedTab = "player" }, modifier = Modifier.weight(1f), enabled = selectedTab != "player") { Text("در حال پخش") }
                Button(onClick = { selectedTab = "settings" }, modifier = Modifier.weight(1f), enabled = selectedTab != "settings") { Text("تنظیمات") }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (selectedUri != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onPlayPause) { Text(if (playing) "مکث" else "پخش") }
                            Text("سرعت %.1fx".format(speed), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            when (selectedTab) {
                "library" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onImportFiles, modifier = Modifier.weight(1f)) { Text("افزودن فایل") }
                        Button(onClick = onImportFolder, modifier = Modifier.weight(1f)) { Text("افزودن پوشه") }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("جست‌وجو در کتابخانه") },
                        placeholder = { Text("عنوان یا نام فایل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    if (entries.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("کتابخانه خالی است", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            Text(
                                "برای شروع، فایل صوتی یا پوشه‌ای را از حافظهٔ گوشی انتخاب کنید. فایل اصلی حذف یا جابه‌جا نمی‌شود.",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (filteredEntries.isEmpty()) {
                        Text("نتیجه‌ای برای این جست‌وجو پیدا نشد.", modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), textAlign = TextAlign.Center)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredEntries, key = { it.uri }) { entry ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(
                                            text = if (entry.uri == selectedUri) "▶  ${entry.title}" else entry.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = if (playedUris[entry.uri] == true) "شنیده‌شده" else (progressLabels[entry.uri] ?: "شروع‌نشده"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 3.dp)
                                        )
                                        Text(
                                            text = listOfNotNull(entry.mimeType, AudioLibraryRules.formatSize(entry.sizeBytes)).joinToString(" · ").ifBlank { "اطلاعات فایل موجود نیست" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                                            Button(onClick = { onPlayEntry(entry) }, modifier = Modifier.weight(1f)) { Text("پخش / ادامه") }
                                            Button(onClick = { onReplayFromStart(entry) }, modifier = Modifier.weight(1f)) { Text("از اول") }
                                        }
                                        Button(onClick = { onRemoveEntry(entry) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) { Text("حذف از فهرست") }
                                    }
                                }
                            }
                        }
                    }
                }
                "player" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 3)
                        Text(
                            if (durationMs > 0L) formatPlaybackTime(durationMs) else "مدت فایل نامشخص است",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Slider(
                            value = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs).toFloat() else 0f,
                            onValueChange = { if (durationMs > 0L) onSeekTo(it.toLong()) },
                            valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
                            enabled = selectedUri != null && durationMs > 0L,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatPlaybackTime(positionMs), style = MaterialTheme.typography.bodySmall)
                            Text(if (durationMs > 0L) formatPlaybackTime((durationMs - positionMs).coerceAtLeast(0L)) + " باقی‌مانده" else "—", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = onBack, enabled = selectedUri != null) { Text("۱۵− ثانیه") }
                            Button(onClick = onPlayPause, enabled = selectedUri != null || entries.isNotEmpty()) { Text(if (playing) "مکث" else "پخش") }
                            Button(onClick = onForward, enabled = selectedUri != null) { Text("۱۵+ ثانیه") }
                        }
                        Button(onClick = onSpeed, modifier = Modifier.padding(top = 12.dp)) { Text("تغییر سرعت · %.1fx".format(speed)) }
                        OutlinedTextField(
                            value = bookmarkNote,
                            onValueChange = { bookmarkNote = it.take(240) },
                            label = { Text("یادداشت نشانک (اختیاری)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = { onAddBookmark(bookmarkNote); bookmarkNote = "" },
                            enabled = selectedUri != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) { Text("ذخیرهٔ نشانک در این لحظه") }
                        val currentBookmarks = bookmarks.filter { it.uri == selectedUri }.sortedBy { it.positionMs }
                        if (currentBookmarks.isNotEmpty()) {
                            Text("نشانک‌های این فایل", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                            currentBookmarks.forEach { bookmark ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(formatPlaybackTime(bookmark.positionMs) + " · " + bookmark.title, style = MaterialTheme.typography.bodyMedium)
                                        if (bookmark.note.isNotBlank()) Text(bookmark.note, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onSelectBookmark(bookmark) }, modifier = Modifier.weight(1f)) { Text("رفتن به نشانک") }
                                            Button(onClick = { onDeleteBookmark(bookmark.id) }) { Text("حذف") }
                                        }
                                    }
                                }
                            }
                        }
                        if (selectedUri == null) {
                            Text("برای پخش، یک فایل را از بخش کتابخانه انتخاب کنید.", modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("تنظیمات پخش", style = MaterialTheme.typography.headlineSmall)
                        Text("سرعت فعلی: %.1fx".format(speed), style = MaterialTheme.typography.bodyLarge)
                        Text("با هر بار انتخاب، سرعت بین ۰٫۸ تا ۲ برابر تغییر می‌کند.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onSpeed, modifier = Modifier.fillMaxWidth()) { Text("تغییر سرعت پخش") }
                        Text("تایمر خواب", style = MaterialTheme.typography.titleMedium)
                        Text(sleepTimerStatus, style = MaterialTheme.typography.bodyMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30, 45).forEach { minutes ->
                                Button(onClick = { onSetSleepTimer(minutes) }, modifier = Modifier.weight(1f)) { Text(minutes.toString() + " دقیقه") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60, 90).forEach { minutes ->
                                Button(onClick = { onSetSleepTimer(minutes) }, modifier = Modifier.weight(1f)) { Text(minutes.toString() + " دقیقه") }
                            }
                            Button(onClick = onCancelSleepTimer, modifier = Modifier.weight(1f)) { Text("لغو تایمر") }
                        }
                        Text("وضعیت پخش: $status", style = MaterialTheme.typography.bodyMedium)
                        Text("YasinCast نسخهٔ ۰٫۱ · طراحی‌شده برای استفادهٔ آفلاین", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (history.isNotEmpty()) {
                            Text("تاریخچهٔ اخیر", style = MaterialTheme.typography.titleMedium)
                            history.take(8).forEach { event ->
                                Text(
                                    text = "${event.title} · ${if (event.eventType == "completed") "کامل‌شده" else "شروع‌شده"} · ${formatPlaybackTime(event.positionMs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

internal fun normalizeSearchText(value: String): String =
    value.lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .trim()
