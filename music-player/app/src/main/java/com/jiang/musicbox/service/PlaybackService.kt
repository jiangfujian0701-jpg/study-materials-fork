package com.jiang.musicbox.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jiang.musicbox.MainActivity
import com.jiang.musicbox.data.MusicDatabase

/**
 * 前台播放服务：锁屏不断、通知栏 / 耳机线控 / 车机蓝牙控制、来电自动暂停（音频焦点）。
 * UI 通过 MediaController 对接；每首歌播放完成回调里记录播放次数与最后播放时间。
 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "jiang_music_playback"
        /** 自定义会话指令：切换收藏 */
        const val CMD_TOGGLE_FAVORITE = "com.jiang.musicbox.TOGGLE_FAVORITE"
        const val RESULT_FAVORITE = "favorite"
    }

    private lateinit var db: MusicDatabase
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        db = MusicDatabase(this)
        createNotificationChannel()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        mediaSession?.let { if (it.isActive) return it }
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)   // 拔耳机自动暂停
            .setWakeMode(C.WAKE_MODE_LOCAL)      // 播放时保持 CPU
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.localConfiguration?.uri?.toString()?.let { db.played(it) }
            }
        })
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent())
            .setCallback(FavoriteCallback())
            .build()
        mediaSession = session
        return session
    }

    private fun sessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放的常驻通知与锁屏控制"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            val player = session.player
            player.currentMediaItem?.localConfiguration?.uri?.toString()
                ?.let { db.savePosition(it, player.currentPosition.coerceAtLeast(0L)) }
            player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** 自定义会话指令回调（收藏切换，UI 与通知可用） */
    private inner class FavoriteCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connection = super.onConnect(session, controller)
            val sessionCommands = connection.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TOGGLE_FAVORITE) {
                val uri = session.player.currentMediaItem?.localConfiguration?.uri?.toString()
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                val nowFavorite = db.toggleFavorite(uri)
                val result = Bundle().apply { putBoolean(RESULT_FAVORITE, nowFavorite) }
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS, result)
                )
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
