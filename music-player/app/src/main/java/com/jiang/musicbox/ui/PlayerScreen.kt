package com.jiang.musicbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jiang.musicbox.data.Track

private val BG = Color(0xFF0B1010)
private val GREEN = Color(0xFF78D6B0)

/** 全屏播放页：大封面 + 进度 + 控制 + 睡眠定时入口 */
@Composable
fun PlayerScreen(
    track: Track?,
    playing: Boolean,
    position: Long,
    duration: Long,
    playMode: String,
    sleepRemaining: Long?,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onMode: () -> Unit,
    onFavorite: () -> Unit,
    onSleepTimer: () -> Unit,
    onClose: () -> Unit,
) {
    val current = track ?: return
    var target by remember { mutableFloatStateOf(position.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "收起", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (sleepRemaining != null) "睡眠定时 ${fmtSleep(sleepRemaining)}" else "正在播放",
                color = Color.Gray,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSleepTimer) {
                Icon(Icons.Default.Timer, "睡眠定时", tint = if (sleepRemaining != null) GREEN else Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF151D1C)),
            contentAlignment = Alignment.Center,
        ) {
            if (current.artworkUri != null) {
                AsyncImage(
                    model = current.artworkUri,
                    contentDescription = current.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.GraphicEq, null, tint = Color(0xFF2A3B37), modifier = Modifier.size(96.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            current.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${current.artist} · ${current.album}",
            color = Color.LightGray,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Slider(
            value = (if (dragging) target else position.toFloat())
                .coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = { target = it; dragging = true },
            onValueChangeFinished = { onSeek(target.toLong()); dragging = false },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(if (dragging) target.toLong() else position), color = Color.Gray, fontSize = 11.sp)
            Text(fmtTime(duration), color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMode) {
                val icon = when (playMode) {
                    PlaybackConnection.MODE_ONE_LOOP -> Icons.Default.RepeatOne
                    PlaybackConnection.MODE_SHUFFLE -> Icons.Default.Shuffle
                    else -> Icons.Default.Repeat
                }
                Icon(icon, "播放模式：$playMode", tint = GREEN, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onPrevious, enabled = true) {
                Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(GREEN),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放/暂停",
                        tint = BG,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (current.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "收藏",
                    tint = if (current.favorite) Color(0xFFFF6B8A) else Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

fun fmtTime(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun fmtSleep(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "%d:%02d".format(m, s) else "%d 秒".format(totalSec)
}
