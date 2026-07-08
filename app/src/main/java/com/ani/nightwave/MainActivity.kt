package com.ani.nightwave

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// Reactive theme colors - reading these inside a @Composable subscribes it to
// theme changes made in the Theme Editor, so every screen updates live without
// needing the color scheme threaded through as a parameter everywhere.
val NeonGreen: Color get() = ThemeColors.neonGreen
val Violet: Color get() = ThemeColors.violet
val Indigo: Color get() = ThemeColors.indigo
val Yellow: Color get() = ThemeColors.yellow

enum class Screen { PLAYER, LIBRARY, DOWNLOAD, SETTINGS, THEME_EDITOR }

class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var visualizer: Visualizer? = null

    private val waveformState = mutableStateOf(FloatArray(0))
    private val coverState = mutableStateOf<Bitmap?>(null)
    private val titleState = mutableStateOf("Kein Titel geladen")
    private val tracksState = mutableStateOf<List<Track>>(emptyList())
    private val currentIndexState = mutableIntStateOf(-1)
    private val folderUriState = mutableStateOf<Uri?>(null)

    // Real, on-disk library folders - populated by recursively scanning the
    // chosen music directory (folderUriState). These are actual SAF
    // directories, not a virtual/in-memory layer: createLibraryFolder and
    // deleteLibraryFolder below really create/delete them (see
    // LibraryFileOps), and this list is simply re-read from disk afterwards.
    private val libraryFoldersState = mutableStateOf<List<LibraryFolder>>(emptyList())

    // True while a real disk operation (scan, create/delete folder, move track)
    // is running on a background thread - drives a small loading overlay in
    // LibraryScreen so a slow SAF op (e.g. copy-fallback on a big file) reads
    // as "working" instead of "frozen/broken".
    private val libraryBusyState = mutableStateOf(false)

    // Which real folder the Library screen is currently browsing - hoisted
    // here (instead of local remember state inside LibraryScreen) so it
    // survives leaving/re-entering the Library (e.g. tapping a track jumps
    // to the Player screen, which used to unmount LibraryScreen and reset
    // this back to root every time).
    private val currentLibraryFolderState = mutableStateOf<Uri?>(null)

    private val downloadBusy = mutableStateOf(false)
    private val downloadStatus = mutableStateOf("")
    private val downloadProgress = mutableFloatStateOf(0f)
    private var downloadProcessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: content draws behind the status/nav bar instead of
        // leaving separate black boxes above and below the app. Since our
        // background is already black this makes the whole screen seamless.
        enableEdgeToEdge()

        // Immersive fullscreen: hide the bottom Android navigation bar
        // (back/home/recents, whether it's the 3-button or gesture bar) so
        // the app uses the entire screen. A swipe from the edge can still
        // reveal it temporarily without permanently exiting immersive mode.
        hideNavigationBar()

        ThemeColors.loadSaved(this)

        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(android.Manifest.permission.RECORD_AUDIO)

        // Android 13+ hides notifications, including the media one, unless
        // POST_NOTIFICATIONS is granted - without this the whole "Benachrichtigung
        // oben mit Steuerung" feature would silently never show up.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    setupVisualizer(player.audioSessionId)
                } else if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })

        // Hand the player to PlaybackService and start it so the system
        // media notification (stop/skip/back) shows up right away, driven
        // by this same ExoPlayer instance. onNext/onPrev are what make the
        // notification's Skip buttons reliably work (see PlaybackService).
        PlaybackService.player = player
        PlaybackService.onNext = { playNext() }
        PlaybackService.onPrev = { playPrev() }
        startService(Intent(this, PlaybackService::class.java))

        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                // keep yt-dlp itself current so YouTube's JS-challenge / signature
                // changes don't break downloads (the bundled binary goes stale fast)
                YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
            } catch (e: Exception) {
                // ignored - download screen will surface an error on first use
            }
        }.start()

        folderUriState.value = Prefs.getMusicFolder(this)
        // Initial scan runs off the main thread now - restoreLastTrack needs
        // tracksState to actually be filled first, so it runs as the
        // completion callback instead of right after (it used to run
        // immediately against an empty list because the scan was sync before).
        rescanTracks { restoreLastTrack() }

        setContent { NightWaveRoot() }
    }

    /** Hides the bottom Android navigation bar (the circle/square/triangle,
     *  or the gesture pill) so the app uses the full screen. Uses the
     *  "swipe" behavior so a person can still briefly reveal the bar by
     *  swiping from the bottom edge - it auto-hides again afterwards. */
    private fun hideNavigationBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    // Notification shades, dialogs, or a temporary swipe-reveal can cause the
    // system to show the navigation bar again - re-hide it whenever the
    // window regains focus so the app stays immersive.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    /** Re-reads the real folder structure and track list straight from disk
     *  (via SAF) - this is the single source of truth. Anything that changes
     *  what's on disk (creating/deleting a folder, moving a track, a
     *  download landing in the folder) calls this afterwards so the UI
     *  reflects reality. */
    private fun rescanTracks(onComplete: () -> Unit = {}) {
        val folder = folderUriState.value ?: return
        libraryBusyState.value = true
        lifecycleScope.launch {
            val contents = withContext(Dispatchers.IO) { MusicScanner.scanLibrary(this@MainActivity, folder) }
            libraryFoldersState.value = contents.folders

            val order = Prefs.getTrackOrder(this@MainActivity)
            tracksState.value = if (order.isNotEmpty()) {
                val byUri = contents.tracks.associateBy { it.uri.toString() }
                val known = order.mapNotNull { byUri[it] }
                val unknown = contents.tracks.filter { it.uri.toString() !in order }
                known + unknown
            } else {
                contents.tracks
            }
            libraryBusyState.value = false
            onComplete()
        }
    }

    private fun moveTrack(track: Track, delta: Int) {
        val list = tracksState.value.toMutableList()
        val from = list.indexOf(track)
        val to = from + delta
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        tracksState.value = list
        Prefs.setTrackOrder(this, list.map { it.uri.toString() })
    }

    /** Really creates a new subdirectory on disk under [parentUri] (or the
     *  library root if null), then rescans so the new real folder shows up. */
    private fun createLibraryFolder(name: String, parentUri: Uri?) {
        val root = folderUriState.value ?: return
        libraryBusyState.value = true
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { LibraryFileOps.createFolder(this@MainActivity, root, parentUri, name) }
            if (!ok) Toast.makeText(this@MainActivity, "Ordner konnte nicht erstellt werden.", Toast.LENGTH_SHORT).show()
            rescanTracks()
        }
    }

    /** Really moves the track's underlying file on disk into [targetFolderUri]
     *  (or back to the library root if null) - this is an actual file move,
     *  not just a label change, so it works correctly with any file manager
     *  or other app looking at the same storage. */
    private fun moveTrackToFolder(track: Track, targetFolderUri: Uri?) {
        val root = folderUriState.value ?: return
        val target = targetFolderUri ?: root
        libraryBusyState.value = true
        lifecycleScope.launch {
            val moved = withContext(Dispatchers.IO) { LibraryFileOps.moveDocument(this@MainActivity, track.uri, track.parentUri, target) }
            if (moved == null) Toast.makeText(this@MainActivity, "Verschieben fehlgeschlagen.", Toast.LENGTH_SHORT).show()
            rescanTracks()
        }
    }

    /** Really deletes a folder from disk. To match the friendly "nothing
     *  gets lost" behavior most file managers offer, its direct contents
     *  (subfolders and tracks) are first really moved up one level - to the
     *  folder's own real parent - so only the now-empty directory itself is
     *  removed. */
    private fun deleteLibraryFolder(folderUri: Uri) {
        val root = folderUriState.value ?: return
        val folders = libraryFoldersState.value
        val target = folders.find { it.uri == folderUri } ?: return
        val newParent = target.parentUri
        val childFolders = folders.filter { it.parentUri == folderUri }
        val childTracks = tracksState.value.filter { it.parentUri == folderUri }

        libraryBusyState.value = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                childFolders.forEach { child -> LibraryFileOps.moveDocument(this@MainActivity, child.uri, folderUri, newParent) }
                childTracks.forEach { track -> LibraryFileOps.moveDocument(this@MainActivity, track.uri, folderUri, newParent) }
                LibraryFileOps.deleteFolder(this@MainActivity, folderUri)
            }
            rescanTracks()
        }
    }

    private fun setupVisualizer(sessionId: Int) {
        try {
            visualizer?.release()
            if (sessionId == 0) return
            visualizer = Visualizer(sessionId).apply {
                // moderate capture size + rate: full max rate was hammering the UI thread
                captureSize = Visualizer.getCaptureSizeRange()[1] / 2
                val rate = minOf(15000, Visualizer.getMaxCaptureRate())
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        waveform ?: return
                        val floats = FloatArray(waveform.size) { i ->
                            val unsigned = waveform[i].toInt() and 0xFF
                            abs(unsigned - 128).toFloat()
                        }
                        waveformState.value = floats
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
                }, rate, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            // Visualizer can be blocked by OEM restrictions (e.g. MIUI) - bars stay flat then
        }
    }

    private fun playTrackAt(index: Int, autoPlay: Boolean = true) {
        val tracks = tracksState.value
        if (index !in tracks.indices) return
        currentIndexState.intValue = index
        val track = tracks[index]

        // Pull title + cover art first so they can ride along on the
        // MediaItem itself - that's what the system notification reads to
        // show the right song name and artwork.
        var art: ByteArray? = null
        var title = track.name
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, track.uri)
            art = retriever.embeddedPicture
            val meta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            title = meta ?: track.name
            retriever.release()
        } catch (e: Exception) {
            // no embedded metadata - fall back to the filename, no cover
        }

        coverState.value = art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        titleState.value = title

        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
        if (art != null) {
            metadataBuilder.setArtworkData(art, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        if (autoPlay) {
            player.playWhenReady = true
            player.play()
        } else {
            // ExoPlayer defaults playWhenReady to true - must explicitly disable it
            // here so restoring the last track on app launch doesn't auto-blast audio.
            player.playWhenReady = false
        }

        Prefs.setLastTrackUri(this, track.uri.toString())
    }

    /** Restores the last-played song (or the first track) on app launch, so the
     *  Play button on the main screen works right away - no need to open the
     *  library and tap a song first. */
    private fun restoreLastTrack() {
        val tracks = tracksState.value
        if (tracks.isEmpty()) return
        val lastUri = Prefs.getLastTrackUri(this)
        val idx = tracks.indexOfFirst { it.uri.toString() == lastUri }.let { if (it >= 0) it else 0 }
        playTrackAt(idx, autoPlay = false)
    }

    /** "Ordner abspielen" - starts playback with the first track found in
     *  [folder] (or, if the folder has no direct tracks, the first track in
     *  any of its subfolders), sorted the same way the Library shows them. */
    private fun playFolder(folder: LibraryFolder) {
        val allFolders = libraryFoldersState.value
        val descendantUris = mutableSetOf(folder.uri)
        var changed = true
        while (changed) {
            changed = false
            allFolders.forEach { f ->
                if (f.parentUri in descendantUris && descendantUris.add(f.uri)) changed = true
            }
        }
        val tracks = tracksState.value
        val idx = tracks.indexOfFirst { it.parentUri in descendantUris }
        if (idx >= 0) playTrackAt(idx) else Toast.makeText(this, "Dieser Ordner enthält keine Songs.", Toast.LENGTH_SHORT).show()
    }

    private fun playNext() {
        val tracks = tracksState.value
        if (tracks.isEmpty()) return
        val next = (currentIndexState.intValue + 1).let { if (it >= tracks.size) 0 else it }
        playTrackAt(next)
    }

    private fun playPrev() {
        val tracks = tracksState.value
        if (tracks.isEmpty()) return
        val prev = (currentIndexState.intValue - 1).let { if (it < 0) tracks.size - 1 else it }
        playTrackAt(prev)
    }

    private fun downloadFromYoutube(query: String, scope: kotlinx.coroutines.CoroutineScope) {
        val folder = folderUriState.value
        val processId = "nightwave-dl-${System.currentTimeMillis()}"
        downloadProcessId = processId
        scope.launch {
            downloadBusy.value = true
            downloadStatus.value = "Suche \"$query\"..."
            downloadProgress.floatValue = 0f
            try {
                val outDir = getExternalFilesDir(null)
                val request = YoutubeDLRequest("ytsearch1:$query")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
                request.addOption("--add-metadata")
                // Loudness-Normalization direkt über yt-dlps eingebautes FFmpeg
                // (statt die separate FFmpeg-Wrapper-API zu nutzen, deren Java-
                // Signatur in dieser Library-Version nicht zuverlässig ist).
                // Zielpegel -9 LUFS statt Spotifys -14 LUFS, weil Montagem/Funk-
                // Tracks bewusst lauter/druckvoller gemixt sind - wir wollen den
                // Charakter behalten, nur das Clipping (True Peak > 0 dBTP) fixen.
                request.addOption("--postprocessor-args", "ExtractAudio:-af loudnorm=I=-9:TP=-1:LRA=5")
                request.addOption("-o", "${outDir?.absolutePath}/%(title)s.%(ext)s")

                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                        downloadProgress.floatValue = (progress / 100f).coerceIn(0f, 1f)
                        downloadStatus.value = "Lädt... ${progress.toInt()}%"
                    }
                }

                if (folder != null) {
                    val docTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(this@MainActivity, folder)
                    outDir?.listFiles { f -> f.extension == "mp3" }?.forEach { file ->
                        val newDoc = docTree?.createFile("audio/mpeg", file.name)
                        newDoc?.uri?.let { destUri ->
                            contentResolver.openOutputStream(destUri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                        }
                        file.delete()
                    }
                    rescanTracks()
                }

                downloadStatus.value = "Fertig: \"$query\" heruntergeladen."
            } catch (e: YoutubeDLException) {
                downloadStatus.value = if (e.message?.contains("cancelled", true) == true) {
                    "Abgebrochen."
                } else {
                    "Fehler: ${e.message ?: "Download fehlgeschlagen"}"
                }
            } catch (e: Exception) {
                downloadStatus.value = "Fehler: ${e.message ?: "Download fehlgeschlagen"}"
            } finally {
                downloadBusy.value = false
                downloadProgress.floatValue = 0f
                downloadProcessId = null
            }
        }
    }

    private fun cancelDownload() {
        downloadProcessId?.let {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (e: Exception) {}
        }
        downloadStatus.value = "Abgebrochen."
        downloadBusy.value = false
    }

    override fun onDestroy() {
        visualizer?.release()
        stopService(Intent(this, PlaybackService::class.java))
        PlaybackService.player = null
        player.release()
        super.onDestroy()
    }

    @Composable
    fun NightWaveRoot() {
        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Prefs.setMusicFolder(this, it)
                folderUriState.value = it
                currentLibraryFolderState.value = null
                rescanTracks()
            }
        }

        var currentScreen by remember { mutableStateOf(Screen.PLAYER) }
        var isPlaying by remember { mutableStateOf(false) }
        var positionMs by remember { mutableLongStateOf(0L) }
        var durationMs by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                isPlaying = player.isPlaying
                positionMs = player.currentPosition
                durationMs = if (player.duration > 0) player.duration else 0L
                delay(200)
            }
        }

        // First launch ever (no music folder saved yet): open the SAF folder
        // picker right away instead of leaving the person to discover the
        // buried "Ordner wählen" button in Settings themselves - this is the
        // one prompt that actually grants real disk access.
        LaunchedEffect(Unit) {
            if (folderUriState.value == null) {
                folderPicker.launch(null)
            }
        }

        val scope = rememberCoroutineScope()
        val tracks = tracksState.value
        val currentIndex = currentIndexState.intValue
        val currentTrackUri = tracks.getOrNull(currentIndex)?.uri?.toString()
        val hasTrack = currentIndex in tracks.indices

        MaterialTheme(colorScheme = darkColorScheme(background = Color.Black)) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                // Edge-to-edge draws behind the status bar, so we pad the top
                // ourselves - background stays black and seamless, but text
                // doesn't sit under the notch. The nav bar is hidden entirely
                // (immersive fullscreen), so no bottom inset padding is needed.
                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {

                    if (currentScreen == Screen.PLAYER) {
                        // top bar: file-manager (library) + settings buttons, top-right
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 12.dp, 0.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NightWave", color = Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row {
                                IconButton(onClick = { currentScreen = Screen.LIBRARY }) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = "Bibliothek", tint = Color.White)
                                }
                                IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Einstellungen", tint = Color.White)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            Screen.PLAYER -> PlayerScreen(
                                title = titleState.value,
                                cover = coverState.value,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                isPlaying = isPlaying,
                                waveform = waveformState.value,
                                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                                onNext = { playNext() },
                                onPrev = { playPrev() }
                            )
                            Screen.LIBRARY -> LibraryScreen(
                                tracks = tracks,
                                folders = libraryFoldersState.value,
                                libraryRootUri = folderUriState.value,
                                currentTrackUriString = currentTrackUri,
                                isPlaying = isPlaying,
                                isBusy = libraryBusyState.value,
                                currentFolderUri = currentLibraryFolderState.value,
                                onFolderChange = { currentLibraryFolderState.value = it },
                                onTrackClick = { track ->
                                    val idx = tracks.indexOf(track)
                                    if (idx == currentIndex) {
                                        if (player.isPlaying) player.pause() else player.play()
                                    } else {
                                        playTrackAt(idx)
                                    }
                                    // jump straight to the player - no extra manual step needed
                                    currentScreen = Screen.PLAYER
                                },
                                onCreateFolder = { name, parentId -> createLibraryFolder(name, parentId) },
                                onPlayFolder = { folder -> playFolder(folder) },
                                onMoveTrack = { track, folderId -> moveTrackToFolder(track, folderId) },
                                onDeleteFolder = { folderId -> deleteLibraryFolder(folderId) },
                                onOpenDownload = { currentScreen = Screen.DOWNLOAD },
                                onPickFolder = { folderPicker.launch(null) },
                                onBack = { currentScreen = Screen.PLAYER }
                            )
                            Screen.DOWNLOAD -> DownloadScreen(
                                isBusy = downloadBusy.value,
                                statusText = downloadStatus.value,
                                progress = downloadProgress.floatValue,
                                onDownload = { query -> downloadFromYoutube(query, scope) },
                                onCancel = { cancelDownload() },
                                onBack = { currentScreen = Screen.LIBRARY }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                context = this@MainActivity,
                                folderUri = folderUriState.value,
                                tracks = tracks,
                                onPickFolder = { folderPicker.launch(null) },
                                onMoveUp = { track -> moveTrack(track, -1) },
                                onMoveDown = { track -> moveTrack(track, 1) },
                                onOpenThemeEditor = { currentScreen = Screen.THEME_EDITOR },
                                onBack = { currentScreen = Screen.PLAYER }
                            )
                            Screen.THEME_EDITOR -> ThemeEditorScreen(
                                context = this@MainActivity,
                                onDone = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }

                    // Spotify-style mini player, visible on every screen except the full player
                    if (currentScreen != Screen.PLAYER && hasTrack) {
                        MiniPlayerBar(
                            title = titleState.value,
                            cover = coverState.value,
                            isPlaying = isPlaying,
                            onOpenPlayer = { currentScreen = Screen.PLAYER },
                            onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                            onNext = { playNext() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    title: String,
    cover: Bitmap?,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onOpenPlayer() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Indigo, Violet))),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White)
        }
    }
}
