package com.jiang.musicbox.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * MediaStore 扫描器。
 * - titleSource: "metadata"（默认，读音频内嵌标题）| "filename"（读文件名，去掉扩展名——
 *   本地改过名的文件用这个，绕过内嵌元数据里的原始名）。
 * - includeHidden: true 时返回包含已隐藏音频的完整列表（设置里的「已隐藏」管理用）。
 */
object Library {

    fun scan(
        context: Context,
        db: MusicDatabase,
        titleSource: String = "metadata",
        includeHidden: Boolean = false,
    ): List<Track> {
        val found = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iDisplay = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val state = db.state(uri)
                if (state.hidden && !includeHidden) continue

                val display = c.getString(iDisplay)?.takeIf { it.isNotBlank() } ?: ""
                val stem = display.substringBeforeLast('.', display)
                val embedded = c.getString(iTitle)?.takeIf { it.isNotBlank() }
                    ?: stem.ifBlank { "未知文件" }
                val title = if (titleSource == "filename" && stem.isNotBlank()) stem else embedded

                val albumId = c.getLong(iAlbumId)
                val artistRaw = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家"
                val albumRaw = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "未知专辑"
                found.add(
                    Track(
                        id = id,
                        title = title,
                        fileName = stem,
                        artist = artistRaw,
                        album = albumRaw,
                        duration = c.getLong(iDur),
                        uri = uri,
                        category = state.category ?: Categories.infer(title),
                        favorite = state.favorite,
                        plays = state.plays,
                        lastPlayed = state.lastPlayed,
                        position = state.position,
                        dateAdded = c.getLong(iDate) * 1000L,
                        size = c.getLong(iSize),
                        artworkUri = if (albumId > 0)
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                        else null,
                        hidden = state.hidden,
                    )
                )
            }
        }
        return found
    }
}
