package com.glassdialer.ui

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassdialer.data.PhoneRepository
import com.glassdialer.data.formatDuration
import com.glassdialer.data.formatTime
import com.glassdialer.recording.CallRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RecordingsPage(repo: PhoneRepository) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(CallRecorder.autoRecord(ctx)) }
    var files by remember { mutableStateOf(emptyList<File>()) }
    var tick by remember { mutableIntStateOf(0) }
    val recording by CallRecorder.status.collectAsState()
    val player = remember { MediaPlayer() }
    var playing by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val twoWay = remember { CallRecorder.canRecordCallAudio(ctx) }

    LaunchedEffect(tick, recording.recording) {
        files = withContext(Dispatchers.IO) {
            CallRecorder.dir(ctx).listFiles()
                ?.filter { it.extension == "m4a" && it.length() > 0L }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    LaunchedEffect(playing) {
        while (playing != null) {
            progress = runCatching {
                if (player.duration > 0) {
                    player.currentPosition.toFloat() / player.duration.toFloat()
                } else 0f
            }.getOrDefault(0f)
            delay(200)
        }
        progress = 0f
    }

    fun toggle(file: File) {
        runCatching { player.reset() }
        if (playing == file) {
            playing = null
            return
        }
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { playing = null }
            player.prepare()
            player.start()
            playing = file
        }.onFailure { playing = null }
    }

    Column {
        Text(
            "Recordings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp),
        )

        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(g.key)
                .border(0.5.dp, g.border, shape)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-record all calls", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = auto,
                    onCheckedChange = {
                        auto = it
                        CallRecorder.setAutoRecord(ctx, it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = CallGreen),
                )
            }
            Text(
                if (twoWay) {
                    "Two-way call audio is available on this phone."
                } else {
                    "Microphone mode turns on speakerphone so the other voice may be picked up."
                },
                fontSize = 12.sp,
                color = g.text2,
            )
        }

        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp)) {
            if (files.isEmpty()) {
                item {
                    Text(
                        "Recorded calls will appear here",
                        color = g.text2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(files, key = { it.name }) { file ->
                RecordingRow(
                    file = file,
                    repo = repo,
                    isPlaying = playing == file,
                    progress = if (playing == file) progress else 0f,
                    onToggle = { toggle(file) },
                    onDelete = {
                        if (playing == file) {
                            runCatching { player.reset() }
                            playing = null
                        }
                        file.delete()
                        tick++
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    file: File,
    repo: PhoneRepository,
    isPlaying: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val g = LocalGlass.current
    val number = file.nameWithoutExtension.substringBefore("_")
    val name by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { repo.lookupName(number) }
    }
    val duration by produceState("", file) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                formatDuration(ms / 1000L)
            } catch (_: Exception) {
                ""
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name ?: number, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatTime(file.lastModified())} · $duration",
                    fontSize = 12.sp,
                    color = g.text2,
                )
            }
            PillKey(
                Modifier.size(44.dp),
                height = 44.dp,
                color = if (isPlaying) CallGreen else null,
                onClick = onToggle,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) Color.White else g.text,
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(40.dp).clip(Pill).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = g.text2)
            }
        }
        if (isPlaying) {
            Box(
                Modifier.padding(start = 56.dp, top = 8.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(Pill)
                    .background(g.line),
            ) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(CallGreen),
                )
            }
        }
    }
}
