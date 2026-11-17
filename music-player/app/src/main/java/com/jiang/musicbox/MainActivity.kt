package com.jiang.musicbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.jiang.musicbox.data.Categories
import com.jiang.musicbox.data.Library
import com.jiang.musicbox.data.MusicDatabase
import com.jiang.musicbox.data.Track
import com.jiang.musicbox.ui.PlaybackConnection
import com.jiang.musicbox.ui.PlayerScreen
import com.jiang.musicbox.ui.fmtSleep
import com.jiang.musicbox.ui.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF0B1010)
private val SURFACE = Color(0xFF151D1C)
private val GREEN = Color(0xFF78D6B0)

private enum class Sort(val label: String) {
    TITLE("标题"), ARTIST("艺术家"), ALBUM("专辑"), DURATION("时长"),
    RECENT_PLAYED("最近播放"), RECENT_ADDED("最近添加"), PLAYS("播放次数"),
}

class MainActivity : ComponentActivity() {

    private lateinit var db: MusicDatabase
    private lateinit var connection: PlaybackConnection

    private val tracks = mutableStateListOf<Track>()
    private val playlistNames = mutableStateListOf<String>()

    private var activeTab by mutableStateOf("全部")
    private var selectedPlaylist by mutableStateOf<String?>(null)
    private var selectedGroup by mutableStateOf<Pair<String, String>?>(null)
    private var searching by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var sortMode by mutableStateOf(Sort.TITLE)
    private var playerExpanded by mutableStateOf(false)
    private var sleepRemaining by mutableStateOf<Long?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) scan() }

    private val audioPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = MusicDatabase(this)
        connection = PlaybackConnection(this)
        connection.connect()

        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            scan()
        }
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = GREEN, background = BG, surface = SURFACE,
                )
            ) { AppRoot() }
        }
    }

    private fun scan() {
        lifecycleScope.launch(Dispatchers.IO) {
            val found = Library.scan(applicationContext, db)
            withContext(Dispatchers.Main) {
                tracks.clear()
                tracks.addAll(found)
                refreshPlaylists()
            }
        }
    }

    /** 只重读数据库状态（收藏/播放次数/进度），不重新扫 MediaStore */
    private fun syncStates() {
        val updated = tracks.toList().map { t ->
            val s = db.state(t.uri)
            t.copy(favorite = s.favorite, plays = s.plays, lastPlayed = s.lastPlayed, position = s.position)
        }
        tracks.clear()
        tracks.addAll(updated)
    }

    private fun refreshPlaylists() {
        playlistNames.clear()
        playlistNames.addAll(db.playlists())
    }

    private fun updateTrack(uri: String, transform: (Track) -> Track) {
        val i = tracks.indexOfFirst { it.uri == uri }
        if (i >= 0) tracks[i] = transform(tracks[i])
    }

    private fun visibleTracks(): List<Track> {
        val base: List<Track> = when (activeTab) {
            "收藏" -> tracks.filter { it.favorite }
            "历史" -> tracks.filter { it.lastPlayed > 0 }
            "歌单" -> {
                val name = selectedPlaylist
                if (name == null) tracks
                else {
                    val keys = db.playlistKeys(name)
                    tracks.filter { MusicDatabase.keyOf(it.uri) in keys }
                }
            }
            "专辑" -> selectedGroup?.let { (_, name) -> tracks.filter { it.album == name } } ?: tracks
            "艺术家" -> selectedGroup?.let { (_, name) -> tracks.filter { it.artist == name } } ?: tracks
            else -> if (activeTab == "全部") tracks else tracks.filter { it.category == activeTab }
        }
        val filtered = if (searchQuery.isNotBlank()) base.filter {
            it.title.contains(searchQuery, true) ||
                it.artist.contains(searchQuery, true) ||
                it.album.contains(searchQuery, true)
        } else base
        return applySort(filtered)
    }

    private fun applySort(list: List<Track>): List<Track> = when (sortMode) {
        Sort.TITLE -> list.sortedBy { it.title.lowercase() }
        Sort.ARTIST -> list.sortedBy { it.artist.lowercase() }
        Sort.ALBUM -> list.sortedBy { it.album.lowercase() }
        Sort.DURATION -> list.sortedByDescending { it.duration }
        Sort.RECENT_PLAYED -> list.sortedByDescending { it.lastPlayed }
        Sort.RECENT_ADDED -> list.sortedByDescending { it.dateAdded }
        Sort.PLAYS -> list.sortedByDescending { it.plays }
    }

    private fun toggleFavorite(track: Track) {
        val now = db.toggleFavorite(track.uri)
        updateTrack(track.uri) { it.copy(favorite = now) }
    }

    private fun currentTrack(): Track? =
        connection.nowPlayingUri?.let { uri -> tracks.firstOrNull { it.uri == uri } }

    override fun onPause() {
        connection.nowPlayingUri?.let { db.savePosition(it, connection.position()) }
        super.onPause()
    }

    override fun onDestroy() {
        connection.nowPlayingUri?.let { db.savePosition(it, connection.position()) }
        connection.release()
        db.close()
        super.onDestroy()
    }

    /* ══════════════════ UI ══════════════════ */

    @Composable
    private fun AppRoot() {
        // 播放进度轮询
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(1L) }
        LaunchedEffect(Unit) {
            while (true) {
                if (connection.ready && connection.queueSize() > 0) {
                    position = connection.position()
                    duration = connection.duration()
                }
                delay(300)
            }
        }
        // 睡眠定时倒计时
        LaunchedEffect(sleepRemaining) {
            val remain = sleepRemaining ?: return@LaunchedEffect
            if (remain <= 1000) {
                delay(1000)
                connection.pause()
                sleepRemaining = null
            } else {
                delay(1000)
                sleepRemaining = remain - 1000
            }
        }
        // 回到前台时同步收藏/播放次数
        LaunchedEffect(Unit) {
            repeatOnLifecycle(Lifecycle.State.STARTED) { syncStates() }
        }

        val current = currentTrack()
        if (playerExpanded) {
            PlayerScreen(
                track = current,
                playing = connection.playing,
                position = position,
                duration = duration,
                playMode = connection.playMode,
                sleepRemaining = sleepRemaining,
                onToggle = connection::togglePlay,
                onNext = connection::next,
                onPrevious = connection::previous,
                onSeek = { connection.seekTo(it) },
                onMode = connection::cycleMode,
                onFavorite = { current?.let { toggleFavorite(it) } },
                onSleepTimer = { showSleepDialog = true },
                onClose = { playerExpanded = false },
            )
        } else {
            LibraryScaffold(current, position, duration)
        }
        if (showSleepDialog) SleepTimerDialog()
        Dialogs()
    }

    private var showSleepDialog by mutableStateOf(false)

    @Composable
    private fun SleepTimerDialog() {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    if (sleepRemaining != null) {
                        Text("剩余 ${fmtSleep(sleepRemaining!!)}", color = Color(0xFFFF6B8A))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("播完当前剩余时间后自动暂停")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        TextButton(onClick = {
                            sleepRemaining = min * 60_000L
                            showSleepDialog = false
                        }) { Text("$min 分") }
                    }
                    TextButton(onClick = { sleepRemaining = null; showSleepDialog = false }) {
                        Text("关闭定时")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("取消") }
            },
        )
    }

    @Composable
    private fun LibraryScaffold(current: Track?, position: Long, duration: Long) {
        Scaffold(containerColor = BG, bottomBar = {
            Column {
                MiniPlayer(current, position, duration)
                NavigationBar(containerColor = Color(0xFF101817)) {
                    listOf(
                        "全部" to Icons.Default.LibraryMusic,
                        "收藏" to Icons.Default.Favorite,
                        "历史" to Icons.Default.History,
                        "歌单" to Icons.Default.QueueMusic,
                    ).forEach { (name, icon) ->
                        val selected = if (name == "全部") activeTab in Categories.ALL
                        else activeTab == name
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                activeTab = if (name == "全部") "全部" else name
                                selectedPlaylist = null
                                selectedGroup = null
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Header()
                if (activeTab == "全部" || activeTab in Categories.ALL || activeTab == "专辑" || activeTab == "艺术家") {
                    CategoryChips()
                }
                Content()
            }
        }
    }

    @Composable
    private fun Header() {
        val title = when {
            activeTab == "歌单" && selectedPlaylist != null -> selectedPlaylist!!
            activeTab == "专辑" && selectedGroup != null -> selectedGroup!!.second
            activeTab == "艺术家" && selectedGroup != null -> selectedGroup!!.second
            else -> activeTab
        }
        val canBack = (activeTab == "歌单" && selectedPlaylist != null) ||
            ((activeTab == "专辑" || activeTab == "艺术家") && selectedGroup != null)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canBack) {
                IconButton(onClick = {
                    if (selectedPlaylist != null) selectedPlaylist = null else selectedGroup = null
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text("JIANG MUSIC", color = GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("已扫描 ${tracks.size} 个音频文件", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { searching = !searching; if (!searching) searchQuery = "" }) {
                Icon(if (searching) Icons.Default.SearchOff else Icons.Default.Search, "搜索", tint = GREEN)
            }
            Box {
                var sortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.Sort, "排序", tint = GREEN)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(if (sortMode == s) "✓ ${s.label}" else s.label) },
                            onClick = { sortMode = s; sortMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = {
                if (ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_GRANTED) scan()
                else permissionLauncher.launch(audioPermission)
            }) { Icon(Icons.Default.Refresh, "重新扫描", tint = GREEN) }
            if (activeTab == "歌单") {
                IconButton(onClick = { showCreatePlaylist = true }) {
                    Icon(Icons.Default.Add, "新建歌单", tint = GREEN)
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索标题 / 艺术家 / 专辑") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun CategoryChips() {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (Categories.ALL + listOf("专辑", "艺术家")).forEach { c ->
                FilterChip(selected = activeTab == c, onClick = {
                    activeTab = c
                    selectedGroup = null
                }, label = { Text(c, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    @Composable
    private fun MiniPlayer(current: Track?, position: Long, duration: Long) {
        if (current == null) return
        var target by remember { mutableFloatStateOf(position.toFloat()) }
        var dragging by remember { mutableStateOf(false) }
        Surface(color = Color(0xFF17211F), tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A3029))
                            .clickable { playerExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (current.artworkUri != null) {
                            AsyncImage(
                                model = current.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.GraphicEq, null, tint = GREEN, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { playerExpanded = true }
                    ) {
                        Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = connection::previous) { Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White) }
                    IconButton(onClick = connection::togglePlay) {
                        Icon(if (connection.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停", tint = GREEN)
                    }
                    IconButton(onClick = connection::next) { Icon(Icons.Default.SkipNext, "下一首", tint = Color.White) }
                    IconButton(onClick = connection::cycleMode) {
                        val icon = when (connection.playMode) {
                            PlaybackConnection.MODE_ONE_LOOP -> Icons.Default.RepeatOne
                            PlaybackConnection.MODE_SHUFFLE -> Icons.Default.Shuffle
                            else -> Icons.Default.Repeat
                        }
                        Icon(icon, "播放模式", tint = GREEN)
                    }
                }
                Slider(
                    value = (if (dragging) target else position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { target = it; dragging = true },
                    onValueChangeFinished = { connection.seekTo(target.toLong()); dragging = false },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(if (dragging) target.toLong() else position), color = Color.LightGray, fontSize = 10.sp)
                    Text(fmtTime(duration), color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }

    @Composable
    private fun Content() {
        val list = visibleTracks()
        when {
            activeTab == "歌单" && selectedPlaylist == null -> PlaylistList()
            activeTab == "专辑" && selectedGroup == null -> GroupList(
                tracks.groupBy { it.album }.map { (name, ts) -> Triple(name, ts.size, ts.maxOf { it.dateAdded }) },
                Icons.Default.Album,
            )
            activeTab == "艺术家" && selectedGroup == null -> GroupList(
                tracks.groupBy { it.artist }.map { (name, ts) -> Triple(name, ts.size, ts.maxOf { it.dateAdded }) },
                Icons.Default.Person,
            )
            list.isEmpty() && (searching || searchQuery.isNotBlank()) -> EmptyView("没有匹配「$searchQuery」的结果")
            list.isEmpty() -> EmptyView()
            else -> TrackList(list)
        }
    }

    @Composable
    private fun EmptyView(text: String = "还没有扫描到音频") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LibraryMusic, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Text(text, color = Color.White)
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_GRANTED) scan()
                    else permissionLauncher.launch(audioPermission)
                }) { Text("扫描本地音频") }
            }
        }
    }

    @Composable
    private fun GroupList(groups: List<Triple<String, Int, Long>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val sorted = groups.sortedBy { it.first.lowercase() }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(sorted, key = { it.first }) { (name, count, _) ->
                ListItem(
                    headlineContent = { Text(name, color = Color.White) },
                    supportingContent = { Text("$count 首", color = Color.Gray, fontSize = 12.sp) },
                    leadingContent = { Icon(icon, null, tint = GREEN) },
                    modifier = Modifier.clickable { selectedGroup = activeTab to name },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    @Composable
    private fun PlaylistList() {
        if (playlistNames.isEmpty()) {
            Text("还没有歌单，点击右上角 + 创建", color = Color.Gray)
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(playlistNames, key = { it }) { name ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(name, color = Color.White) },
                    leadingContent = { Icon(Icons.Default.QueueMusic, null, tint = GREEN) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = Color.LightGray) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("删除歌单") }, onClick = {
                                    menu = false
                                    db.deletePlaylist(name)
                                    refreshPlaylists()
                                })
                            }
                        }
                    },
                    modifier = Modifier.clickable { selectedPlaylist = name },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    @Composable
    private fun TrackList(list: List<Track>) {
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(list, key = { it.uri }) { track ->
                TrackRow(track)
            }
        }
    }

    @Composable
    private fun TrackRow(track: Track) {
        var menu by remember { mutableStateOf(false) }
        val selected = track.uri == connection.nowPlayingUri
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Color(0xFF1A3029) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A3029))
                    .clickable { playTrack(track) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Default.GraphicEq, null, tint = GREEN, modifier = Modifier.size(22.dp))
                } else if (track.artworkUri != null) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = GREEN, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { playTrack(track) }
            ) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extra = if (track.plays > 0) " · ${track.plays} 次" else ""
                Text("${track.artist} · ${track.category} · ${fmtTime(track.duration)}$extra", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { toggleFavorite(track) }) {
                Icon(
                    Icons.Default.Favorite, "收藏",
                    tint = if (track.favorite) Color(0xFFFF6B8A) else Color.Gray,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = Color.LightGray) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("修改分类") }, onClick = { menu = false; editingTrack = track })
                    DropdownMenuItem(text = { Text("添加到歌单") }, onClick = { menu = false; addingTrack = track })
                    if (activeTab == "歌单" && selectedPlaylist != null) {
                        DropdownMenuItem(text = { Text("从本歌单移除") }, onClick = {
                            menu = false
                            db.removeFromPlaylist(selectedPlaylist!!, track.uri)
                        })
                    }
                }
            }
        }
    }

    private fun playTrack(track: Track) {
        val list = visibleTracks().ifEmpty { tracks.toList() }
        connection.playOrToggle(list, track) { t -> db.state(t.uri).position }
    }

    /* ── 弹窗状态 ── */

    private var editingTrack by mutableStateOf<Track?>(null)
    private var addingTrack by mutableStateOf<Track?>(null)
    private var showCreatePlaylist by mutableStateOf(false)
    private var playlistName by mutableStateOf("")

    @Composable
    private fun Dialogs() {
        editingTrack?.let { track ->
            var chosen by remember(track.uri) { mutableStateOf(track.category) }
            AlertDialog(
                onDismissRequest = { editingTrack = null },
                title = { Text("修改媒体分类") },
                text = {
                    Column {
                        Text(track.title)
                        Categories.ALL.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen == item, onClick = { chosen = item })
                                Text(item)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        db.setCategory(track.uri, chosen)
                        updateTrack(track.uri) { it.copy(category = chosen) }
                        editingTrack = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { editingTrack = null }) { Text("取消") } },
            )
        }
        if (showCreatePlaylist) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylist = false },
                title = { Text("新建歌单") },
                text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("歌单名称") }) },
                confirmButton = {
                    TextButton(onClick = {
                        if (playlistName.isNotBlank()) {
                            db.createPlaylist(playlistName.trim())
                            refreshPlaylists()
                        }
                        playlistName = ""
                        showCreatePlaylist = false
                    }) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("取消") } },
            )
        }
        addingTrack?.let { track ->
            AlertDialog(
                onDismissRequest = { addingTrack = null },
                title = { Text("添加到歌单") },
                text = {
                    Column {
                        if (playlistNames.isEmpty()) Text("请先创建歌单")
                        else playlistNames.forEach { name ->
                            TextButton(onClick = {
                                db.addToPlaylist(name, track.uri)
                                addingTrack = null
                            }) { Text(name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { addingTrack = null }) { Text("关闭") } },
            )
        }
    }
}
