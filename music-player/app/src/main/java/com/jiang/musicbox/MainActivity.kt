package com.jiang.musicbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
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
import com.jiang.musicbox.ui.AppTheme
import com.jiang.musicbox.ui.LocalAccent
import com.jiang.musicbox.ui.PlaybackConnection
import com.jiang.musicbox.ui.PlayerScreen
import com.jiang.musicbox.ui.SettingsScreen
import com.jiang.musicbox.ui.fmtSleep
import com.jiang.musicbox.ui.fmtTime
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PINK = Color(0xFFFF6B8A)

class MainActivity : ComponentActivity() {

    private lateinit var db: MusicDatabase
    private lateinit var connection: PlaybackConnection
    private val prefs by lazy { getSharedPreferences("jiang_settings", Context.MODE_PRIVATE) }

    private val tracks = mutableStateListOf<Track>()
    private val playlistNames = mutableStateListOf<String>()
    private val hiddenList = mutableStateListOf<Track>()

    /* ── 设置状态（SharedPreferences 持久化） ── */
    private var fontScale by mutableStateOf(1.0f)
    private var accentName by mutableStateOf("黛绿")
    private var themeMode by mutableStateOf("system")
    private var refreshRatePref by mutableStateOf("max")
    private var titleSource by mutableStateOf("metadata")
    private var wallpaperPath by mutableStateOf<String?>(null)

    /* ── 界面状态 ── */
    private var activeTab by mutableStateOf("全部")
    private var selectedPlaylist by mutableStateOf<String?>(null)
    private var selectedGroup by mutableStateOf<Pair<String, String>?>(null)
    private var searching by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var sortMode by mutableStateOf(Sort.TITLE)
    private var playerExpanded by mutableStateOf(false)
    private var sleepRemaining by mutableStateOf<Long?>(null)
    private var showSettings by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) scan() }

    private val wallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) saveWallpaper(uri) }

    private val audioPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = MusicDatabase(this)
        connection = PlaybackConnection(this)
        connection.connect()
        crashLog = JiangMusicApp.readCrashLog(this)

        fontScale = prefs.getFloat("fontScale", 1.0f)
        accentName = prefs.getString("accent", "黛绿") ?: "黛绿"
        themeMode = prefs.getString("themeMode", "system") ?: "system"
        refreshRatePref = prefs.getString("refreshRate", "max") ?: "max"
        titleSource = prefs.getString("titleSource", "metadata") ?: "metadata"
        wallpaperPath = prefs.getString("wallpaper", null)
        applyRefreshRate(refreshRatePref)

        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            scan()
        }
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        applyRefreshRate(refreshRatePref)
    }

    /* ── 设置读写 ── */

    private fun setFontScale(v: Float) { fontScale = v; prefs.edit().putFloat("fontScale", v).apply() }
    private fun setAccent(v: String) { accentName = v; prefs.edit().putString("accent", v).apply() }
    private fun setThemeMode(v: String) { themeMode = v; prefs.edit().putString("themeMode", v).apply() }
    private fun setRefreshRate(v: String) { refreshRatePref = v; prefs.edit().putString("refreshRate", v).apply(); applyRefreshRate(v) }
    private fun setTitleSource(v: String) { titleSource = v; prefs.edit().putString("titleSource", v).apply(); scan() }
    private fun clearWallpaper() {
        wallpaperPath?.let { File(it).delete() }
        wallpaperPath = null
        prefs.edit().remove("wallpaper").apply()
    }

    private fun saveWallpaper(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = File(filesDir, "wallpaper.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                } ?: return@launch
                withContext(Dispatchers.Main) {
                    wallpaperPath = f.absolutePath
                    prefs.edit().putString("wallpaper", f.absolutePath).apply()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { toast("壁纸保存失败") }
            }
        }
    }

    /** 高性能手机默认把应用限在 60Hz，这里按用户选择直接锁定显示模式 */
    private fun applyRefreshRate(pref: String) {
        val display = (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val target = when (pref) {
            "auto" -> return
            "max" -> display.supportedModes.maxByOrNull { it.refreshRate }
            else -> {
                val want = pref.toFloatOrNull() ?: return
                display.supportedModes.minByOrNull { abs(it.refreshRate - want) }
            }
        } ?: return
        window?.attributes = window?.attributes?.apply { preferredDisplayModeId = target.modeId }
    }

    /* ── 媒体库 ── */

    private fun scan() {
        val src = titleSource
        lifecycleScope.launch(Dispatchers.IO) {
            val found = Library.scan(applicationContext, db, src)
            withContext(Dispatchers.Main) {
                tracks.clear()
                tracks.addAll(found)
                refreshPlaylists()
            }
        }
    }

    private fun loadHidden() {
        val src = titleSource
        lifecycleScope.launch(Dispatchers.IO) {
            val list = Library.scan(applicationContext, db, src, includeHidden = true).filter { it.hidden }
            withContext(Dispatchers.Main) {
                hiddenList.clear()
                hiddenList.addAll(list)
            }
        }
    }

    private fun hideTrack(track: Track) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.setHidden(track.uri, true)
            withContext(Dispatchers.Main) {
                tracks.removeAll { it.uri == track.uri }
                hiddenList.removeAll { it.uri == track.uri }
                toast("已移出媒体库（本地文件未删除）")
            }
        }
    }

    private fun restoreHidden(track: Track) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.setHidden(track.uri, false)
            withContext(Dispatchers.Main) {
                hiddenList.removeAll { it.uri == track.uri }
                scan()
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

    private fun playTrack(track: Track) {
        val list = visibleTracks().ifEmpty { tracks.toList() }
        connection.playOrToggle(list, track) { t -> db.state(t.uri).position }
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

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
    private fun Root() {
        AppTheme(themeMode, accentName) {
            val scaled = LocalDensity.current.copy(fontScale = fontScale)
            CompositionLocalProvider(scaled) {
                val scheme = MaterialTheme.colorScheme
                Box(Modifier.fillMaxSize()) {
                    // 背景层：壁纸（垫半透明底保证可读）或纯主题色
                    if (wallpaperPath != null) {
                        AsyncImage(
                            model = wallpaperPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(scheme.background.copy(alpha = 0.84f)))
                    } else {
                        Box(Modifier.fillMaxSize().background(scheme.background))
                    }
                    AppBody()
                }
            }
        }
    }

    @Composable
    private fun AppBody() {
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
        LaunchedEffect(Unit) {
            repeatOnLifecycle(Lifecycle.State.STARTED) { syncStates() }
        }

        val current = currentTrack()
        if (crashLog != null) CrashLogDialog()
        if (showSettings) {
            SettingsScreen(
                fontScale = fontScale,
                onFontScale = ::setFontScale,
                accentName = accentName,
                onAccent = ::setAccent,
                themeMode = themeMode,
                onThemeMode = ::setThemeMode,
                refreshRate = refreshRatePref,
                onRefreshRate = ::setRefreshRate,
                titleSource = titleSource,
                onTitleSource = ::setTitleSource,
                hasWallpaper = wallpaperPath != null,
                onPickWallpaper = {
                    wallpaperLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearWallpaper = ::clearWallpaper,
                hiddenTracks = hiddenList.toList(),
                onLoadHidden = ::loadHidden,
                onRestore = ::restoreHidden,
                versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" },
                onBack = { showSettings = false },
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
                        Text("剩余 ${fmtSleep(sleepRemaining!!)}", color = PINK)
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
        val scheme = MaterialTheme.colorScheme
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            Column {
                MiniPlayer(current, position, duration)
                NavigationBar(containerColor = scheme.surface) {
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
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
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
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = scheme.onBackground) }
            }
            Column(Modifier.weight(1f)) {
                Text("JIANG MUSIC", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(title, color = scheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("已扫描 ${tracks.size} 个音频文件", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "设置", tint = accent)
            }
            IconButton(onClick = { searching = !searching; if (!searching) searchQuery = "" }) {
                Icon(if (searching) Icons.Default.SearchOff else Icons.Default.Search, "搜索", tint = accent)
            }
            Box {
                var sortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.Sort, "排序", tint = accent)
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
            }) { Icon(Icons.Default.Refresh, "重新扫描", tint = accent) }
            if (activeTab == "歌单") {
                IconButton(onClick = { showCreatePlaylist = true }) {
                    Icon(Icons.Default.Add, "新建歌单", tint = accent)
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
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
        var target by remember { mutableFloatStateOf(position.toFloat()) }
        var dragging by remember { mutableStateOf(false) }
        Surface(color = scheme.surfaceVariant, tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primaryContainer)
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
                            Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { playerExpanded = true }
                    ) {
                        Text(current.title, color = scheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = connection::previous) { Icon(Icons.Default.SkipPrevious, "上一首", tint = scheme.onSurface) }
                    IconButton(onClick = connection::togglePlay) {
                        Icon(if (connection.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放/暂停", tint = accent)
                    }
                    IconButton(onClick = connection::next) { Icon(Icons.Default.SkipNext, "下一首", tint = scheme.onSurface) }
                    IconButton(onClick = connection::cycleMode) {
                        val icon = when (connection.playMode) {
                            PlaybackConnection.MODE_ONE_LOOP -> Icons.Default.RepeatOne
                            PlaybackConnection.MODE_SHUFFLE -> Icons.Default.Shuffle
                            else -> Icons.Default.Repeat
                        }
                        Icon(icon, "播放模式", tint = accent)
                    }
                }
                Slider(
                    value = (if (dragging) target else position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { target = it; dragging = true },
                    onValueChangeFinished = { connection.seekTo(target.toLong()); dragging = false },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(if (dragging) target.toLong() else position), color = scheme.onSurfaceVariant, fontSize = 10.sp)
                    Text(fmtTime(duration), color = scheme.onSurfaceVariant, fontSize = 10.sp)
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
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LibraryMusic, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Text(text, color = scheme.onBackground)
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) == PackageManager.PERMISSION_GRANTED) scan()
                    else permissionLauncher.launch(audioPermission)
                }) { Text("扫描本地音频") }
            }
        }
    }

    @Composable
    private fun GroupList(groups: List<Triple<String, Int, Long>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
        val sorted = groups.sortedBy { it.first.lowercase() }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(sorted, key = { it.first }) { (name, count, _) ->
                ListItem(
                    headlineContent = { Text(name, color = scheme.onSurface) },
                    supportingContent = { Text("$count 首", color = scheme.onSurfaceVariant, fontSize = 12.sp) },
                    leadingContent = { Icon(icon, null, tint = accent) },
                    modifier = Modifier.clickable { selectedGroup = activeTab to name },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    @Composable
    private fun PlaylistList() {
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
        if (playlistNames.isEmpty()) {
            Text("还没有歌单，点击右上角 + 创建", color = scheme.onSurfaceVariant)
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(playlistNames, key = { it }) { name ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(name, color = scheme.onSurface) },
                    leadingContent = { Icon(Icons.Default.QueueMusic, null, tint = accent) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = scheme.onSurfaceVariant) }
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
        val scheme = MaterialTheme.colorScheme
        val accent = LocalAccent.current
        var menu by remember { mutableStateOf(false) }
        val selected = track.uri == connection.nowPlayingUri
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primaryContainer)
                    .clickable { playTrack(track) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(22.dp))
                } else if (track.artworkUri != null) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { playTrack(track) }
            ) {
                Text(track.title, color = scheme.onSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val extra = if (track.plays > 0) " · ${track.plays} 次" else ""
                Text("${track.artist} · ${track.category} · ${fmtTime(track.duration)}$extra", color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { toggleFavorite(track) }) {
                Icon(
                    Icons.Default.Favorite, "收藏",
                    tint = if (track.favorite) PINK else scheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多", tint = scheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("修改分类") }, onClick = { menu = false; editingTrack = track })
                    DropdownMenuItem(text = { Text("添加到歌单") }, onClick = { menu = false; addingTrack = track })
                    DropdownMenuItem(text = { Text("移出媒体库") }, onClick = { menu = false; hideTrack(track) })
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

    /* ── 弹窗状态 ── */

    private var editingTrack by mutableStateOf<Track?>(null)
    private var addingTrack by mutableStateOf<Track?>(null)
    private var showCreatePlaylist by mutableStateOf(false)
    private var playlistName by mutableStateOf("")
    private var crashLog by mutableStateOf<String?>(null)

    @Composable
    private fun CrashLogDialog() {
        val text = crashLog ?: return
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("检测到上次异常退出", color = PINK) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("jiang_music_crash", text))
                    toast("已复制崩溃日志")
                }) { Text("复制日志") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { crashLog = null }) { Text("先关掉") }
                    TextButton(onClick = {
                        JiangMusicApp.clearCrashLog(this@MainActivity)
                        crashLog = null
                    }) { Text("清除") }
                }
            },
        )
    }

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

    private enum class Sort(val label: String) {
        TITLE("标题"), ARTIST("艺术家"), ALBUM("专辑"), DURATION("时长"),
        RECENT_PLAYED("最近播放"), RECENT_ADDED("最近添加"), PLAYS("播放次数"),
    }
}
