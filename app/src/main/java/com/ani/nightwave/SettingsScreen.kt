package com.ani.nightwave

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.content.Context

@Composable
fun SettingsScreen(
    context: Context,
    folderUri: Uri?,
    tracks: List<Track>,
    onPickFolder: () -> Unit,
    onMoveUp: (Track) -> Unit,
    onMoveDown: (Track) -> Unit,
    onOpenThemeEditor: () -> Unit,
    onBack: () -> Unit
) {
    var motionEnabled by remember { mutableStateOf(ThemeColors.motionEnabled) }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 24.dp, 20.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Zurück", tint = Color.White)
            }
            Text("Einstellungen", color = Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text("Musik-Ordner", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                folderUri?.lastPathSegment ?: "Kein Ordner gewählt",
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPickFolder,
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ordner wählen", color = Color.White)
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenThemeEditor() }
                .padding(20.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = Violet)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Theme", color = Color.White, fontSize = 14.sp)
                    Text("Farben & Effekte anpassen", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Animation, contentDescription = null, tint = Violet)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Motion-Effekte", color = Color.White, fontSize = 14.sp)
                    Text("Pulsierendes Cover & Visualizer", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Switch(
                checked = motionEnabled,
                onCheckedChange = {
                    motionEnabled = it
                    ThemeColors.motionEnabled = it
                    Prefs.setMotionEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Violet,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text(
            "Reihenfolge anpassen (${tracks.size} Songs)",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        track.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Row {
                        IconButton(
                            onClick = { onMoveUp(track) },
                            enabled = index > 0
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Nach oben",
                                tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f)
                            )
                        }
                        IconButton(
                            onClick = { onMoveDown(track) },
                            enabled = index < tracks.size - 1
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Nach unten",
                                tint = if (index < tracks.size - 1) Color.White else Color.White.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        }
    }
}
