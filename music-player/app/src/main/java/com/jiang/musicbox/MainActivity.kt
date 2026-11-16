package com.jiang.musicbox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private const val GREEN = 0xFF78D6B0L
private val categories = listOf("歌曲", "音声", "有声书", "播客", "其他音频", "待识别")

data class Track(
    val id: Long, val title: String, val artist: String, val album: String,
    val duration: Long, val uri: String, val category: String
)

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, "jiang_music.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE track_state (uri TEXT PRIMARY KEY, category TEXT, manual INTEGER NOT NULL DEFAULT 0, favorite INTEGER NOT NULL DEFAULT 0, plays INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, uri TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(playlist_id, uri))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun state(uri: String): State {
        readableDatabase.rawQuery("SELECT category,manual,favorite,plays,last_played,position FROM track_state WHERE uri=?", arrayOf(uri)).use {
            if (it.moveToFirst()) return State(it.getString(0), it.getInt(1) == 1, it.getInt(2) == 1, it.getInt(3), it.getLong(4), it.getLong(5))
        }
        return State(null, false, false, 0, 0, 0)
    }
    fun setCategory(uri: String, category: String) {
        writableDatabase.execSQL("INSERT INTO track_state(uri,category,manual) VALUES(?,?,1) ON CONFLICT(uri) DO UPDATE SET category=excluded.category,manual=1", arrayOf(uri, category))
    }
    fun toggleFavorite(uri: String) {
        val next = if (state(uri).favorite) 0 else 1
        writableDatabase.execSQL("INSERT INTO track_state(uri,favorite) VALUES(?,?) ON CONFLICT(uri) DO UPDATE SET favorite=?", arrayOf(uri, next, next))
    }
    fun played(uri: String) {
        writableDatabase.execSQL("INSERT INTO track_state(uri,plays,last_played) VALUES(?,1,?) ON CONFLICT(uri) DO UPDATE SET plays=plays+1,last_played=excluded.last_played", arrayOf(uri, System.currentTimeMillis()))
    }
    fun savePosition(uri: String, position: Long) {
        writableDatabase.execSQL("INSERT INTO track_state(uri,position) VALUES(?,?) ON CONFLICT(uri) DO UPDATE SET position=excluded.position", arrayOf(uri, position))
    }
    fun createPlaylist(name: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO playlists(name) VALUES(?)", arrayOf(name))
    }
    fun playlists(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM playlists ORDER BY name", null).use { while (it.moveToNext()) result.add(it.getString(0)) }
        return result
    }
    fun addToPlaylist(name: String, uri: String) {
        writableDatabase.rawQuery("SELECT id FROM playlists WHERE name=?", arrayOf(name)).use {
            if (it.moveToFirst()) writableDatabase.execSQL("INSERT OR IGNORE INTO playlist_tracks(playlist_id,uri) VALUES(?,?)", arrayOf(it.getLong(0), uri))
        }
    }
    fun playlistUris(name: String): Set<String> {
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT t.uri FROM playlist_tracks t JOIN playlists p ON p.id=t.playlist_id WHERE p.name=?", arrayOf(name)).use { while (it.moveToNext()) result.add(it.getString(0)) }
        return result
    }
}
data class State(val category: String?, val manual: Boolean, val favorite: Boolean, val plays: Int, val lastPlayed: Long, val position: Long)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var db: MusicDatabase
    private val tracks = mutableStateListOf<Track>()
    private val favorites = mutableStateListOf<String>()
    private val history = mutableStateListOf<String>()
    private val playlistNames = mutableStateListOf<String>()
    private var currentUri by mutableStateOf<String?>(null)
    private var playing by mutableStateOf(false)
    private var currentPosition by mutableLongStateOf(0L)
    private var currentDuration by mutableLongStateOf(1L)
    private var activeTab by mutableStateOf("全部")
    private var selectedPlaylist by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = MusicDatabase(this)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentUri = mediaItem?.localConfiguration?.uri?.toString()
                currentDuration = player.duration.coerceAtLeast(1L)
            }
        })
        refreshSavedLists()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) scan()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(GREEN), background = Color(0xFF0B1010), surface = Color(0xFF151D1C))) {
                LaunchedEffect(Unit) {
                    while (true) {
                        if (player.mediaItemCount > 0) {
                            currentPosition = player.currentPosition.coerceAtLeast(0L)
                            currentDuration = player.duration.coerceAtLeast(1L)
                        }
                        delay(300)
                    }
                }
                MusicHome(
                    tracks = visibleTracks(), allCount = tracks.size, tab = activeTab, currentUri = currentUri,
                    playing = playing, position = currentPosition, duration = currentDuration,
                    playlists = playlistNames, selectedPlaylist = selectedPlaylist,
                    onTab = { activeTab = it; selectedPlaylist = null },
                    onScan = { if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) scan() else permissionLauncher.launch(permission) },
                    onPlay = { play(it) },
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onNext = { skip(1) }, onPrevious = { skip(-1) },
                    onSeek = { player.seekTo(it) },
                    onFavorite = { db.toggleFavorite(it.uri); refreshSavedLists() },
                    onCategory = { track, category -> db.setCategory(track.uri, category); scan() },
                    onCreatePlaylist = { name -> db.createPlaylist(name); refreshSavedLists() },
                    onAddPlaylist = { track, name -> db.addToPlaylist(name, track.uri) },
                    onOpenPlaylist = { selectedPlaylist = it; activeTab = "歌单" }
                )
            }
        }
    }

    private fun scan() {
        val found = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        contentResolver.query(uri, projection, null, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val fileUri = ContentUris.withAppendedId(uri, c.getLong(id)).toString()
                val state = db.state(fileUri)
                val rawTitle = c.getString(title)?.takeIf { it.isNotBlank() } ?: "未知文件"
                val inferred = state.category ?: if (rawTitle.contains("朗读") || rawTitle.contains("对白") || rawTitle.contains("Drama", true)) "音声" else "待识别"
                found.add(Track(c.getLong(id), rawTitle, c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家", c.getString(album) ?: "", c.getLong(duration), fileUri, inferred))
            }
        }
        tracks.clear(); tracks.addAll(found)
        refreshSavedLists()
    }

    private fun refreshSavedLists() {
        favorites.clear(); favorites.addAll(tracks.filter { db.state(it.uri).favorite }.map { it.uri })
        history.clear()
        history.addAll(tracks.filter { db.state(it.uri).lastPlayed > 0 }.sortedByDescending { db.state(it.uri).lastPlayed }.map { it.uri })
        playlistNames.clear(); playlistNames.addAll(db.playlists())
    }

    private fun visibleTracks(): List<Track> {
        val base = when (activeTab) {
            "收藏" -> tracks.filter { db.state(it.uri).favorite }
            "历史" -> tracks.filter { db.state(it.uri).lastPlayed > 0 }.sortedByDescending { db.state(it.uri).lastPlayed }
            "歌单" -> tracks.filter { selectedPlaylist?.let { p -> it.uri in db.playlistUris(p) } ?: false }
            "歌曲", "音声", "有声书", "播客", "其他音频", "待识别" -> tracks.filter { it.category == activeTab }
            else -> tracks.toList()
        }
        return base
    }

    private fun play(track: Track) {
        val uri = android.net.Uri.parse(track.uri)
        if (currentUri == track.uri && player.mediaItemCount > 0) { player.play(); return }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        val saved = db.state(track.uri).position
        if (saved > 0 && saved < track.duration - 5000) player.seekTo(saved)
        player.play()
        currentUri = track.uri
        db.played(track.uri)
        refreshSavedLists()
    }

    private fun skip(direction: Int) {
        val list = visibleTracks()
        val index = list.indexOfFirst { it.uri == currentUri }
        val next = if (index < 0) 0 else (index + direction + list.size) % list.size
        if (list.isNotEmpty()) play(list[next])
    }

    override fun onPause() {
        currentUri?.let { db.savePosition(it, player.currentPosition.coerceAtLeast(0L)) }
        super.onPause()
    }
    override fun onDestroy() { player.release(); db.close(); super.onDestroy() }
}

@Composable
private fun MusicHome(
    tracks: List<Track>, allCount: Int, tab: String, currentUri: String?, playing: Boolean,
    position: Long, duration: Long, playlists: List<String>, selectedPlaylist: String?,
    onTab: (String) -> Unit, onScan: () -> Unit, onPlay: (Track) -> Unit, onToggle: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    onFavorite: (Track) -> Unit, onCategory: (Track, String) -> Unit,
    onCreatePlaylist: (String) -> Unit, onAddPlaylist: (Track, String) -> Unit, onOpenPlaylist: (String) -> Unit
) {
    var editing by remember { mutableStateOf<Track?>(null) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var addingTrack by remember { mutableStateOf<Track?>(null) }
    var targetTime by remember { mutableFloatStateOf(position.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val current = tracks.firstOrNull { it.uri == currentUri }
    Scaffold(
        containerColor = Color(0xFF0B1010),
        bottomBar = {
            Column {
                if (current != null) {
                    Surface(color = Color(0xFF17211F), tonalElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(current.artist, color = Color.LightGray, fontSize = 11.sp)
                                }
                                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White) }
                                IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放暂停", tint = Color(GREEN)) }
                                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "下一首", tint = Color.White) }
                            }
                            Slider(value = (if (dragging) targetTime else position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1L).toFloat()), onValueChange = { targetTime = it; dragging = true }, onValueChangeFinished = { onSeek(targetTime.toLong()); dragging = false }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(timeText(if (dragging) targetTime.toLong() else position), color = Color.LightGray, fontSize = 10.sp)
                                Text(timeText(duration), color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
                NavigationBar(containerColor = Color(0xFF101817)) {
                    listOf("全部" to Icons.Default.LibraryMusic, "收藏" to Icons.Default.Favorite, "历史" to Icons.Default.History, "歌单" to Icons.Default.QueueMusic).forEach { (name, icon) ->
                        NavigationBarItem(selected = tab == name, onClick = { onTab(name) }, icon = { Icon(icon, null) }, label = { Text(name) })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JIANG MUSIC", color = Color(GREEN), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (tab == "歌单" && selectedPlaylist != null) selectedPlaylist else tab, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("已扫描 $allCount 个音频文件", color = Color.Gray, fontSize = 12.sp)
                }
                IconButton(onClick = onScan) { Icon(Icons.Default.Refresh, "重新扫描", tint = Color(GREEN)) }
                if (tab == "歌单") IconButton(onClick = { creatingPlaylist = true }) { Icon(Icons.Default.Add, "新建歌单", tint = Color(GREEN)) }
            }
            if (tab == "全部") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    categories.forEach { category ->
                        FilterChip(selected = false, onClick = { onTab(category) }, label = { Text(category, fontSize = 11.sp) })
                    }
                }
                Text("媒体库", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
            }
            if (tab == "歌单" && selectedPlaylist == null) {
                playlists.forEach { name -> ListItem(headlineContent = { Text(name, color = Color.White) }, leadingContent = { Icon(Icons.Default.QueueMusic, null, tint = Color(GREEN)) }, modifier = Modifier.clickable { onOpenPlaylist(name) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
                if (playlists.isEmpty()) Text("还没有歌单，点击右上角 + 创建", color = Color.Gray)
            } else if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, tint = Color.Gray, modifier = Modifier.size(48.dp)); Text("还没有扫描到音频", color = Color.White); Button(onClick = onScan) { Text("扫描本地音频") } } }
            } else if (tracks.isNotEmpty()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(tracks, key = { it.uri }) { track ->
                        var menu by remember { mutableStateOf(false) }
                        val selected = track.uri == currentUri
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF1A3029) else Color.Transparent).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onPlay(track) }) { Icon(if (selected && playing) Icons.Default.GraphicEq else Icons.Default.PlayArrow, null, tint = Color(GREEN)) }
                            Column(Modifier.weight(1f).clickable { onPlay(track) }) {
                                Text(track.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} · ${track.category} · ${timeText(track.duration)}", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onFavorite(track) }) { Icon(Icons.Default.Favorite, "收藏", tint = Color.Gray) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = Color.LightGray) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("修改分类") }, onClick = { menu = false; editing = track })
                                    DropdownMenuItem(text = { Text("添加到歌单") }, onClick = { menu = false; addingTrack = track })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { track ->
        var chosen by remember(track.uri) { mutableStateOf(track.category) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("修改媒体分类") }, text = {
            Column { Text(track.title); categories.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = chosen == item, onClick = { chosen = item }); Text(item) } } }
        }, confirmButton = { TextButton(onClick = { onCategory(track, chosen); editing = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
    if (creatingPlaylist) AlertDialog(onDismissRequest = { creatingPlaylist = false }, title = { Text("新建歌单") }, text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("歌单名称") }) }, confirmButton = { TextButton(onClick = { if (playlistName.isNotBlank()) onCreatePlaylist(playlistName.trim()); playlistName = ""; creatingPlaylist = false }) { Text("创建") } }, dismissButton = { TextButton(onClick = { creatingPlaylist = false }) { Text("取消") } })
    addingTrack?.let { track ->
        AlertDialog(onDismissRequest = { addingTrack = null }, title = { Text("添加到歌单") }, text = { Column { if (playlists.isEmpty()) Text("请先创建歌单") else playlists.forEach { name -> TextButton(onClick = { onAddPlaylist(track, name); addingTrack = null }) { Text(name) } } } }, confirmButton = { TextButton(onClick = { addingTrack = null }) { Text("关闭") } })
    }
}

private fun timeText(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
