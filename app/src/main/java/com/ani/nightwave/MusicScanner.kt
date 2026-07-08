package com.ani.nightwave

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class Track(
    val name: String,
    val uri: Uri,
    val documentId: String,
    // The real directory this file currently lives in on disk (the library
    // root Uri if it sits directly under the chosen music folder).
    val parentUri: Uri
)

/** Result of a single recursive disk scan: every real subfolder and every
 *  audio file found anywhere under the chosen music root, each carrying
 *  its real parent directory Uri. There is no separate "virtual" folder
 *  layer anymore - this list of folders IS the actual folder structure on
 *  disk. */
data class LibraryContents(
    val folders: List<LibraryFolder>,
    val tracks: List<Track>
)

object MusicScanner {

    private fun isAudioFile(file: DocumentFile): Boolean {
        val type = file.type ?: ""
        val name = file.name ?: ""
        return file.isFile && (
            type.startsWith("audio/") ||
                name.endsWith(".mp3") || name.endsWith(".m4a") ||
                name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
            )
    }

    /** Recursively walks every real subdirectory under [folderUri], collecting
     *  both the folder structure and the audio files found in it. This talks
     *  to the SAF document provider (real disk / real content provider), so
     *  folders created or deleted elsewhere (or by this app) are always
     *  reflected here - there is nothing cached in memory or prefs. */
    fun scanLibrary(context: Context, folderUri: Uri): LibraryContents {
        val root = DocumentFile.fromTreeUri(context, folderUri)
            ?: return LibraryContents(emptyList(), emptyList())

        val folders = mutableListOf<LibraryFolder>()
        val tracks = mutableListOf<Track>()

        fun walk(dir: DocumentFile, parentUri: Uri) {
            dir.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val name = child.name ?: return@forEach
                    folders.add(LibraryFolder(uri = child.uri, name = name, parentUri = parentUri))
                    walk(child, child.uri)
                } else if (isAudioFile(child)) {
                    tracks.add(
                        Track(
                            name = child.name ?: "Unbenannt",
                            uri = child.uri,
                            documentId = child.uri.toString(),
                            parentUri = parentUri
                        )
                    )
                }
            }
        }
        walk(root, folderUri)

        return LibraryContents(
            folders = folders.sortedBy { it.name.lowercase() },
            tracks = tracks.sortedBy { it.name.lowercase() }
        )
    }

    /** Kept for compatibility with call sites that only need the flat track
     *  list (e.g. simple rescans) - internally does the same recursive scan. */
    fun listTracks(context: Context, folderUri: Uri): List<Track> {
        return scanLibrary(context, folderUri).tracks
    }
}
