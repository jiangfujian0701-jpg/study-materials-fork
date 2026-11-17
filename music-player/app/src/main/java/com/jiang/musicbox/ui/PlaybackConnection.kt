package com.jiang.musicbox.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.jiang.musicbox.data.Track
import com.jiang.musicbox.service.PlaybackService

/**
 * UI ↔ PlaybackService 的桥：包一个 MediaController。
 * 只负责「发命令、读状态」，媒体库数据仍在 Activity 里。
 */
class PlaybackConnection(private val context: Context) {

    var controller: MediaController? = null
        private set
    private var pendingFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    var nowPlayingUri by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set

    var playMode by mutableStateOf(MODE_LIST_LOOP)
        private set

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        pendingFuture = future
        future.addListener({
            try {
                val c = future.get()
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        nowPlayingUri = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                            ?: mediaItem?.localConfiguration?.uri?.toString()
                    }
                })
                // 服务侧被系统重建后恢复模式
                applyMode(c)
                controller = c
                ready = true
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        pendingFuture?.let { MediaController.releaseFuture(it) }
        pendingFuture = null
        controller = null
        ready = false
    }

    /* ── 播放控制 ───────────────────────────────────── */

    /** 用给定列表建队列并从 start 开始播放；startPos 是该曲已存进度 */
    fun play(tracks: List<Track>, start: Int, startPos: Long = 0L) {
        val c = controller ?: return
        val items = tracks.map { it.toMediaItem() }
        if (start !in items.indices) return
        val saved = if (startPos > 0 && startPos < tracks[start].duration - 5000) startPos else 0L
        c.setMediaItems(items, start, saved)
        applyMode(c)
        c.prepare()
        c.play()
    }

    /** 若点的是当前曲则切播放/暂停，否则整个队列重设 */
    fun playOrToggle(tracks: List<Track>, track: Track, startPosOf: (Track) -> Long = { 0L }) {
        val c = controller ?: return
        if (track.uri == nowPlayingUri && c.mediaItemCount > 0) {
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        val index = tracks.indexOfFirst { it.uri == track.uri }
        if (index >= 0) {
            play(tracks, index, startPosOf(track))
        } else {
            play(tracks + track, tracks.size, startPosOf(track))
        }
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun position(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: 1L
    fun queueSize(): Int = controller?.mediaItemCount ?: 0

    fun setMode(mode: String) {
        playMode = mode
        controller?.let { applyMode(it) }
    }

    fun cycleMode() {
        setMode(
            when (playMode) {
                MODE_LIST_LOOP -> MODE_ONE_LOOP
                MODE_ONE_LOOP -> MODE_SHUFFLE
                else -> MODE_LIST_LOOP
            }
        )
    }

    private fun applyMode(c: Player) {
        when (playMode) {
            MODE_ONE_LOOP -> { c.repeatMode = Player.REPEAT_MODE_ONE; c.shuffleModeEnabled = false }
            MODE_SHUFFLE -> { c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = true }
            else -> { c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = false }
        }
    }

    companion object {
        const val MODE_LIST_LOOP = "列表循环"
        const val MODE_ONE_LOOP = "单曲循环"
        const val MODE_SHUFFLE = "随机播放"
    }
}

/** Track → 带元数据的 MediaItem（mediaId 即文件 uri，通知栏/锁屏显示靠 metadata） */
fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
        .build()
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(metadata)
        .build()
}
