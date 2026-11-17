package com.jiang.musicbox.data

/** 媒体库中的一条音频（MediaStore 行 + 本地状态） */
data class Track(
    val id: Long,
    val title: String,
    val fileName: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val category: String,
    val favorite: Boolean,
    val plays: Int,
    val lastPlayed: Long,
    val position: Long,
    val dateAdded: Long,
    val size: Long,
    val artworkUri: String?,
    val hidden: Boolean,
)

/** 数据库里的持久状态（与 MediaStore 行合并成 Track） */
data class TrackState(
    val category: String? = null,
    val manual: Boolean = false,
    val favorite: Boolean = false,
    val plays: Int = 0,
    val lastPlayed: Long = 0L,
    val position: Long = 0L,
    val hidden: Boolean = false,
)

object Categories {
    val ALL = listOf("歌曲", "音声", "有声书", "播客", "其他音频", "待识别")

    /** 按标题关键词做本地推断；用户手动设置过的一律以数据库为准 */
    fun infer(title: String): String = when {
        title.contains("朗读", true) || title.contains("对白", true) || title.contains("Drama", true) || title.contains("音声", true) -> "音声"
        title.contains("书", true) || title.contains("章", true) || title.contains("novel", true) || title.contains("有声", true) -> "有声书"
        title.contains("播客", true) || title.contains("podcast", true) || title.contains("电台", true) || title.contains("episode", true) -> "播客"
        else -> "歌曲"
    }
}
