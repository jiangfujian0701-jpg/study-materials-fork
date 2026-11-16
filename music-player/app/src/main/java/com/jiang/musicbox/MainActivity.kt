package com.jiang.musicbox

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class Song(val id: Long, val title: String, val artist: String, val album: String, val duration: Long)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private val songs = mutableStateListOf<Song>()
    private var permissionGranted by mutableStateOf(false)
    private var currentIndex by mutableIntStateOf(-1)
    private var isPlaying by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) scanMusic()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
            }
        })
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) scanMusic()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF78D6B0),
                background = Color(0xFF0B1010),
                surface = Color(0xFF151D1C)
            )) {
                MusicHome(
                    songs = songs,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    onScan = {
                        if (permissionGranted) scanMusic() else permissionLauncher.launch(permission)
                    },
                    onPlay = { playSong(it) },
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onNext = { if (songs.isNotEmpty()) playSong((currentIndex + 1).mod(songs.size)) },
                    onPrevious = { if (songs.isNotEmpty()) playSong(if (currentIndex <= 0) songs.lastIndex else currentIndex - 1) }
                )
            }
        }
    }

    private fun scanMusic() {
        songs.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                songs.add(Song(
                    cursor.getLong(idCol),
                    cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                    cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家",
                    cursor.getString(albumCol) ?: "",
                    cursor.getLong(durationCol)
                ))
            }
        }
    }

    private fun playSong(index: Int) {
        if (index !in songs.indices) return
        currentIndex = index
        val song = songs[index]
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun MusicHome(
    songs: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onScan: () -> Unit,
    onPlay: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val current = songs.getOrNull(currentIndex)
    Scaffold(
        containerColor = Color(0xFF0B1010),
        bottomBar = {
            if (current != null) {
                Surface(color = Color(0xFF17211F), tonalElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = Color(0xFF78D6B0), modifier = Modifier.size(38.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White) }
                            IconButton(onClick = onToggle) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放暂停", tint = Color(0xFF78D6B0))
                            }
                            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "下一首", tint = Color.White) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JIANG MUSIC", color = Color(0xFF78D6B0), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("我的音乐", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${songs.size} 首本地歌曲", color = Color.Gray, fontSize = 13.sp)
                }
                FilledTonalIconButton(onClick = onScan) {
                    Icon(Icons.Default.Refresh, "扫描音乐")
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("歌曲", songs.size.toString(), Modifier.weight(1f))
                StatCard("音源", "本地文件", Modifier.weight(1f))
            }
            Spacer(Modifier.height(22.dp))
            Text("全部歌曲", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFF52645E), modifier = Modifier.size(58.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("还没有扫描到音乐", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("点击右上角扫描，读取手机本地音频", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onScan) { Text("扫描本地音乐") }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(songs) { index, song ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (index == currentIndex) Color(0xFF1A3029) else Color.Transparent)
                                .clickable { onPlay(index) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF20312D)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF78D6B0))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(song.title, color = Color.White, fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatDuration(song.duration), color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0xFF151D1C), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
