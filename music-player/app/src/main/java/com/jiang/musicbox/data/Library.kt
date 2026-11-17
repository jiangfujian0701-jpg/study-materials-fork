package com.jiang.musicbox.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** MediaStore 扫描器：一次查询全部音频 + 合并数据库状态 */
object Library {

    fun scan(context: Context, db: MusicDatabase): List<Track> {
        val found = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
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
                val rawTitle = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "未知文件"
                val albumId = c.getLong(iAlbumId)
                found.add(
                    Track(
                        id = id,
                        title = rawTitle,
                        artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家",
                        album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "未知专辑",
                        duration = c.getLong(iDur),
                        uri = uri,
                        category = state.category ?: Categories.infer(rawTitle),
                        favorite = state.favorite,
                        plays = state.plays,
                        lastPlayed = state.lastPlayed,
                        position = state.position,
                        dateAdded = c.getLong(iDate) * 1000L,
                        size = c.getLong(iSize),
                        artworkUri = if (albumId > 0)
                            ContentUris.withAppendedId("content://media/external/audio/albumart".let { android.net.Uri.parse(it) }, albumId).toString()
                        else null,
                    )
                )
            }
        }
        return found
    }
}
