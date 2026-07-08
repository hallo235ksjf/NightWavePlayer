package com.ani.nightwave

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/** All real, on-disk folder/file operations for the Library ("Dateimanager")
 *  screen. Everything here goes through Android's Storage Access Framework
 *  against the actual chosen music directory - no virtual/in-memory folder
 *  layer. Creating a folder really creates a directory; deleting one really
 *  removes it from disk; moving a track really relocates the file. */
object LibraryFileOps {

    /** Creates a real subdirectory named [name] inside [parentUri] (or the
     *  library root if [parentUri] is null). Returns true on success. */
    fun createFolder(context: Context, rootUri: Uri, parentUri: Uri?, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val parentDoc = DocumentFile.fromTreeUri(context, parentUri ?: rootUri) ?: return false
        return parentDoc.createDirectory(trimmed) != null
    }

    /** Really deletes the real directory at [folderUri] from disk, including
     *  everything still inside it (SAF removes a directory document and its
     *  full contents together - there's no way to delete "just the folder"
     *  and keep its files in place on the same path). Callers that want a
     *  softer "move contents up, then delete the now-empty folder" behavior
     *  should call [moveOut] on every direct child first. */
    fun deleteFolder(context: Context, folderUri: Uri): Boolean {
        val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        return doc.delete()
    }

    /** Moves the real document at [sourceUri] (a track or a folder) from
     *  [sourceParentUri] to [targetParentUri]. Tries the fast native SAF
     *  move first; if the storage backend doesn't support that (some
     *  providers throw UnsupportedOperationException), falls back to a
     *  manual copy + delete so the move still actually happens. Returns the
     *  document's Uri after the move (may differ from [sourceUri] since
     *  moving/copying can assign a new document id), or null on failure. */
    fun moveDocument(context: Context, sourceUri: Uri, sourceParentUri: Uri, targetParentUri: Uri): Uri? {
        if (sourceParentUri == targetParentUri) return sourceUri
        return try {
            DocumentsContract.moveDocument(context.contentResolver, sourceUri, sourceParentUri, targetParentUri)
        } catch (e: Exception) {
            copyThenDelete(context, sourceUri, targetParentUri)
        }
    }

    private fun copyThenDelete(context: Context, sourceUri: Uri, targetParentUri: Uri): Uri? {
        // Must use fromTreeUri, not fromSingleUri: androidx's SingleDocumentFile
        // throws UnsupportedOperationException from listFiles(), which would
        // break copying a folder's contents below. Every Uri we handle here
        // originates from the same chosen tree, so fromTreeUri is always
        // correct and gives us full read/write/list access.
        val sourceDoc = DocumentFile.fromTreeUri(context, sourceUri) ?: return null
        val targetDir = DocumentFile.fromTreeUri(context, targetParentUri) ?: return null
        val name = sourceDoc.name ?: return null

        return if (sourceDoc.isDirectory) {
            val newDir = targetDir.createDirectory(name) ?: return null
            sourceDoc.listFiles().forEach { child ->
                copyThenDelete(context, child.uri, newDir.uri)
            }
            DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            newDir.uri
        } else {
            val mime = sourceDoc.type ?: "application/octet-stream"
            val newDoc = targetDir.createFile(mime, name) ?: return null
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(newDoc.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                return null
            }
            DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            newDoc.uri
        }
    }
}
