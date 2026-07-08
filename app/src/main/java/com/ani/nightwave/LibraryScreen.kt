package com.ani.nightwave

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Library screen, organized like a real file manager:
 *  - Folders are real on-disk directories (see LibraryFolder / MusicScanner /
 *    LibraryFileOps) - creating, moving into, or deleting one is a real SAF
 *    file operation, not just an in-memory label.
 *  - Tapping a folder opens it; the back arrow goes up one level (or exits
 *    the screen entirely once you're back at the root).
 *  - Each track has a "move" action to file it into any folder, or back to
 *    the root - this really moves the underlying file on disk.
 */
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    folders: List<LibraryFolder>,
    libraryRootUri: Uri?,
    currentTrackUriString: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onCreateFolder: (String, Uri?) -> Unit,
    onMoveTrack: (Track, Uri?) -> Unit,
    onDeleteFolder: (Uri) -> Unit,
    onOpenDownload: () -> Unit,
    onBack: () -> Unit
) {
    var currentFolderUri by remember { mutableStateOf<Uri?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var moveDialogTrack by remember { mutableStateOf<Track?>(null) }
    var deleteDialogFolder by remember { mutableStateOf<LibraryFolder?>(null) }

    // "Here" means: at the real directory currentFolderUri, or the library
    // root itself when currentFolderUri is null.
    val effectiveHere = currentFolderUri ?: libraryRootUri
    val currentFolder = folders.find { it.uri == currentFolderUri }
    val subfolders = folders.filter { it.parentUri == effectiveHere }.sortedBy { it.name.lowercase() }
    val tracksHere = tracks.filter { it.parentUri == effectiveHere }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 24.dp, 16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = {
                    if (currentFolderUri != null) {
                        val parent = currentFolder?.parentUri
                        currentFolderUri = if (parent == null || parent == libraryRootUri) null else parent
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Zurück", tint = Color.White)
                }
                Text(
                    currentFolder?.name ?: "Bibliothek",
                    color = Yellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            IconButton(onClick = { showNewFolderDialog = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Neuer Ordner", tint = Violet)
            }
            IconButton(onClick = onOpenDownload) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = NeonGreen)
            }
        }

        if (subfolders.isEmpty() && tracksHere.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (currentFolderUri == null && tracks.isEmpty() && folders.isEmpty())
                            "Kein Ordner gewählt oder keine Songs gefunden.\nGeh zu den Einstellungen und wähl einen Musik-Ordner."
                        else
                            "Dieser Ordner ist leer.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subfolders, key = { "folder_${it.uri}" }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentFolderUri = folder.uri }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = Violet, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                folder.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { deleteDialogFolder = folder }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Ordner löschen", tint = Color.White.copy(alpha = 0.4f))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }

                items(tracksHere, key = { "track_${it.uri}" }) { track ->
                    val isCurrent = track.uri.toString() == currentTrackUriString
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track) }
                            .background(if (isCurrent) Violet.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            track.name,
                            color = if (isCurrent) NeonGreen else Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        IconButton(onClick = { moveDialogTrack = track }) {
                            Icon(
                                Icons.Filled.DriveFileMove,
                                contentDescription = "Verschieben",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Icon(
                            if (isCurrent && isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = if (isCurrent) NeonGreen else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(30.dp).clip(CircleShape)
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }
        }
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                onCreateFolder(name, currentFolderUri)
                showNewFolderDialog = false
            }
        )
    }

    moveDialogTrack?.let { track ->
        MoveTrackDialog(
            folders = folders,
            libraryRootUri = libraryRootUri,
            onDismiss = { moveDialogTrack = null },
            onSelect = { folderUri ->
                onMoveTrack(track, folderUri)
                moveDialogTrack = null
            }
        )
    }

    deleteDialogFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteDialogFolder = null },
            title = { Text("Ordner löschen?", color = Color.White) },
            text = {
                Text(
                    "\"${folder.name}\" wird wirklich vom Gerät gelöscht. Enthaltene Songs und Unterordner werden zuerst eine Ebene nach oben verschoben.",
                    color = Color.White.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folder.uri)
                    deleteDialogFolder = null
                }) {
                    Text("Löschen", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogFolder = null }) {
                    Text("Abbrechen", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1A1A22)
        )
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Ordner", color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Ordnername", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Violet,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Violet
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Erstellen", color = Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1A1A22)
    )
}

@Composable
private fun MoveTrackDialog(
    folders: List<LibraryFolder>,
    libraryRootUri: Uri?,
    onDismiss: () -> Unit,
    onSelect: (Uri?) -> Unit
) {
    // Build "Parent / Child" style labels so nested folders are distinguishable.
    fun pathFor(folder: LibraryFolder): String {
        val parts = mutableListOf(folder.name)
        var parentUri: Uri? = folder.parentUri
        while (parentUri != null && parentUri != libraryRootUri) {
            val parent = folders.find { it.uri == parentUri } ?: break
            parts.add(0, parent.name)
            parentUri = parent.parentUri
        }
        return parts.joinToString(" / ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verschieben nach...", color = Color.White) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Yellow, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Bibliothek (Hauptordner)", color = Color.White, fontSize = 14.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(folders.sortedBy { pathFor(it).lowercase() }, key = { it.uri.toString() }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(folder.uri) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = Violet, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(pathFor(folder), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1A1A22)
    )
}
