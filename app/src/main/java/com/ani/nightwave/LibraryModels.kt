package com.ani.nightwave

import android.net.Uri

/** A real folder in the Library screen. This IS an actual directory on
 *  disk (a SAF document tree entry) - uri is that directory's own document
 *  Uri, and parentUri is the real Uri of the directory (or the library
 *  root) it physically lives inside. Creating/deleting a LibraryFolder
 *  creates/deletes the real directory via DocumentFile (see MainActivity
 *  and MusicScanner). */
data class LibraryFolder(
    val uri: Uri,
    val name: String,
    val parentUri: Uri
)
