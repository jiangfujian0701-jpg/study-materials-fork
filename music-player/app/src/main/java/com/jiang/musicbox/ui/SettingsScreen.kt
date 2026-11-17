package com.jiang.musicbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jiang.musicbox.data.Track

/**
 * 设置页。
 * themeMode: system/dark/light；refreshRate: auto/max/60/90/120；titleSource: metadata/filename。
 */
@Composable
fun SettingsScreen(
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    accentName: String,
    onAccent: (String) -> Unit,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    refreshRate: String,
    onRefreshRate: (String) -> Unit,
    titleSource: String,
    onTitleSource: (String) -> Unit,
    hasWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    hiddenTracks: List<Track>,
    onLoadHidden: () -> Unit,
    onRestore: (Track) -> Unit,
    versionName: String,
    onBack: () -> Unit,
) {
    var showHidden by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (showHidden) showHidden = false else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = scheme.onBackground)
            }
            Text(
                if (showHidden) "已隐藏的音频" else "设置",
                color = scheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (showHidden) {
            if (hiddenTracks.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("没有已隐藏的音频", color = scheme.onSurfaceVariant)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(hiddenTracks, key = { it.uri }) { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = scheme.onSurface, maxLines = 1, fontSize = 14.sp)
                                Text(track.fileName, color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                            }
                            TextButton(onClick = { onRestore(track) }) {
                                Icon(Icons.Default.Restore, null, tint = accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("恢复", color = accent)
                            }
                        }
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            /* ── 外观 ── */
            item { SectionTitle("外观") }

            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("强调色", color = scheme.onSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ACCENTS.forEach { (name, color) ->
                            val selected = name == accentName
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = scheme.onBackground,
                                        shape = CircleShape,
                                    )
                                    .clickable { onAccent(name) },
                            )
                        }
                    }
                }
            }

            item {
                SettingCard {
                    Text("深浅模式", color = scheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (v, label) ->
                            FilterChip(selected = themeMode == v, onClick = { onThemeMode(v) }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                }
            }

            item {
                SettingCard {
                    Text("字体大小", color = scheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = fontScale,
                            onValueChange = { onFontScale(((it * 20).toInt() / 20.0f).coerceIn(0.85f, 1.35f)) },
                            valueRange = 0.85f..1.35f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(fontScale * 100).toInt()}%", color = scheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                    }
                }
            }

            item {
                SettingCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("背景壁纸", color = scheme.onSurface)
                            Text("选一张图片铺在列表底下，内容会垫半透明底", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onPickWallpaper) { Text("选择图片") }
                        if (hasWallpaper) TextButton(onClick = onClearWallpaper) { Text("清除壁纸") }
                    }
                }
            }

            item {
                SettingCard {
                    Text("屏幕刷新率", color = scheme.onSurface)
                    Text("跟随系统在高性能手机上常被限到 60Hz；选「最高」可解锁 144Hz", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("auto" to "跟随系统", "max" to "最高", "60" to "60", "90" to "90", "120" to "120").forEach { (v, label) ->
                            FilterChip(selected = refreshRate == v, onClick = { onRefreshRate(v) }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                }
            }

            /* ── 媒体库 ── */
            item { SectionTitle("媒体库") }

            item {
                SettingCard {
                    Text("歌曲名来源", color = scheme.onSurface)
                    Text(
                        "「元数据标题」读音频内嵌信息（常带原始名或频道水印）；「文件名」直接读你本地改好的名字",
                        color = scheme.onSurfaceVariant, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = titleSource == "metadata", onClick = { onTitleSource("metadata") }, label = { Text("元数据标题", fontSize = 12.sp) })
                        FilterChip(selected = titleSource == "filename", onClick = { onTitleSource("filename") }, label = { Text("文件名", fontSize = 12.sp) })
                    }
                }
            }

            item {
                SettingCard(
                    Modifier.clickable { onLoadHidden(); showHidden = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("已隐藏的音频", color = scheme.onSurface)
                            Text("扫描错了的可以移出媒体库，这里能恢复", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Text("${hiddenTracks.size} 首", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }

            /* ── 关于 ── */
            item { SectionTitle("关于") }
            item {
                SettingCard {
                    Text("江的音乐 v$versionName", color = scheme.onSurface)
                    Text("本地音频播放器 · 基于 Kotlin + Compose + media3", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = LocalAccent.current,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}
