package com.jiang.musicbox.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 音乐库持久状态。v2 起把 uri 拆成 (bucket, docId)：MediaStore 的文件 Uri 在
 * 设备重启 / 重新挂载后会变化，直接存 uri 会让收藏和进度全部失联。
 * v1 数据自动迁移到 v2（按 uri 匹配）。
 */
class MusicDatabase(context: Context) : SQLiteOpenHelper(context, "jiang_music.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE track_state (" +
                "bucket TEXT NOT NULL, doc_id TEXT NOT NULL, category TEXT, manual INTEGER NOT NULL DEFAULT 0, " +
                "favorite INTEGER NOT NULL DEFAULT 0, plays INTEGER NOT NULL DEFAULT 0, " +
                "last_played INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(bucket, doc_id))"
        )
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, bucket TEXT NOT NULL, doc_id TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(playlist_id, bucket, doc_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE TABLE track_state_new (" +
                    "bucket TEXT NOT NULL, doc_id TEXT NOT NULL, category TEXT, manual INTEGER NOT NULL DEFAULT 0, " +
                    "favorite INTEGER NOT NULL DEFAULT 0, plays INTEGER NOT NULL DEFAULT 0, " +
                    "last_played INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(bucket, doc_id))"
            )
            // content://media/external/audio/media/<id> → bucket="media", docId="<id>"
            db.execSQL(
                "INSERT INTO track_state_new(bucket, doc_id, category, manual, favorite, plays, last_played, position) " +
                    "SELECT 'media', substr(uri, length('content://media/external/audio/media/') + 1), category, manual, favorite, plays, last_played, position FROM track_state"
            )
            db.execSQL("DROP TABLE track_state")
            db.execSQL("ALTER TABLE track_state_new RENAME TO track_state")
            db.execSQL("DROP TABLE playlist_tracks")
            db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, bucket TEXT NOT NULL, doc_id TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(playlist_id, bucket, doc_id))")
            // v1 歌单数据只有表结构没有入口写数据，直接弃置
        }
    }

    companion object {
        /** content://media/external/audio/media/123 → Pair("media", "123") */
        fun keyOf(uri: String): Pair<String, String> {
            val idx = uri.lastIndexOf('/')
            return if (idx > 0) uri.substring(0, idx) to uri.substring(idx + 1) else uri to ""
        }
    }

    private fun readState(bucket: String, docId: String): TrackState {
        readableDatabase.rawQuery(
            "SELECT category, manual, favorite, plays, last_played, position FROM track_state WHERE bucket=? AND doc_id=?",
            arrayOf(bucket, docId)
        ).use {
            if (it.moveToFirst()) return TrackState(
                it.getString(0), it.getInt(1) == 1, it.getInt(2) == 1,
                it.getInt(3), it.getLong(4), it.getLong(5)
            )
        }
        return TrackState()
    }

    fun state(uri: String): TrackState = keyOf(uri).let { (b, d) -> readState(b, d) }

    private fun upsert(bucket: String, docId: String, vararg pairs: Pair<String, Any?>) {
        val cv = ContentValues().apply {
            put("bucket", bucket); put("doc_id", docId)
            for ((k, v) in pairs) when (v) {
                null -> putNull(k)
                is String -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
            }
        }
        writableDatabase.insertWithOnConflict("track_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setCategory(uri: String, category: String) = keyOf(uri).let { (b, d) ->
        upsert(b, d, "category" to category, "manual" to 1)
    }

    fun setAutoCategory(uri: String, category: String) = keyOf(uri).let { (b, d) ->
        // 只有未手动设置过时才写
        if (readState(b, d).category == null) upsert(b, d, "category" to category, "manual" to 0)
    }

    fun toggleFavorite(uri: String): Boolean = keyOf(uri).let { (b, d) ->
        val next = if (readState(b, d).favorite) 0 else 1
        upsert(b, d, "favorite" to next)
        next == 1
    }

    fun played(uri: String) = keyOf(uri).let { (b, d) ->
        writableDatabase.execSQL(
            "INSERT INTO track_state(bucket,doc_id,plays,last_played) VALUES(?,?,1,?) " +
                "ON CONFLICT(bucket,doc_id) DO UPDATE SET plays=plays+1,last_played=excluded.last_played",
            arrayOf(b, d, System.currentTimeMillis())
        )
    }

    fun savePosition(uri: String, position: Long) = keyOf(uri).let { (b, d) ->
        if (position > 0) upsert(b, d, "position" to position)
    }

    /* ── 歌单 ─────────────────────────────────────── */

    fun createPlaylist(name: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO playlists(name) VALUES(?)", arrayOf(name))
    }

    fun deletePlaylist(name: String) {
        writableDatabase.execSQL(
            "DELETE FROM playlist_tracks WHERE playlist_id IN (SELECT id FROM playlists WHERE name=?)", arrayOf(name)
        )
        writableDatabase.execSQL("DELETE FROM playlists WHERE name=?", arrayOf(name))
    }

    fun renamePlaylist(old: String, new: String) {
        writableDatabase.execSQL("UPDATE playlists SET name=? WHERE name=?", arrayOf(new, old))
    }

    fun playlists(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM playlists ORDER BY name", null).use {
            while (it.moveToNext()) result.add(it.getString(0))
        }
        return result
    }

    fun addToPlaylist(name: String, uri: String) = keyOf(uri).let { (b, d) ->
        writableDatabase.execSQL(
            "INSERT OR IGNORE INTO playlist_tracks(playlist_id,bucket,doc_id) " +
                "SELECT id, ?, ? FROM playlists WHERE name=?",
            arrayOf(b, d, name)
        )
    }

    fun removeFromPlaylist(name: String, uri: String) = keyOf(uri).let { (b, d) ->
        writableDatabase.execSQL(
            "DELETE FROM playlist_tracks WHERE bucket=? AND doc_id=? AND playlist_id IN (SELECT id FROM playlists WHERE name=?)",
            arrayOf(b, d, name)
        )
    }

    fun playlistKeys(name: String): Set<Pair<String, String>> {
        val result = mutableSetOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT t.bucket, t.doc_id FROM playlist_tracks t JOIN playlists p ON p.id=t.playlist_id WHERE p.name=?",
            arrayOf(name)
        ).use { while (it.moveToNext()) result.add(it.getString(0) to it.getString(1)) }
        return result
    }
}
