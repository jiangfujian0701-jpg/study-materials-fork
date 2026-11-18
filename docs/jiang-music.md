# Jiang Music 架构说明（0.3.0）

江的音乐是本仓库的 Android 本地音乐播放器（Kotlin + Jetpack Compose + media3 1.4.1），
与主应用（HGhub，Tauri）相互独立，位于 `music-player/`，由
`.github/workflows/music-player-apk.yml` 自动构建 debug APK。

## 模块划分

```
music-player/app/src/main/java/com/jiang/musicbox/
├── MainActivity.kt            界面入口：媒体库视图 + 迷你播放条 + 弹窗
├── data/
│   ├── Track.kt               音频实体 + 分类推断规则（Categories）
│   ├── MusicDatabase.kt       SQLite v2：收藏/分类/播放次数/进度/歌单
│   └── Library.kt             MediaStore 扫描器（一次查询合并 DB 状态）
├── service/
│   └── PlaybackService.kt     media3 MediaSessionService 前台服务
└── ui/
    ├── PlaybackConnection.kt  MediaController 封装：发命令、读状态、播放模式
    └── PlayerScreen.kt        全屏播放页（大封面、进度、睡眠定时入口）
```

## 播放链路

1. `MainActivity` 扫描 MediaStore，得到 `List<Track>`（含 DB 状态：收藏、分类、播放次数、断点）。
2. 点击播放 → `PlaybackConnection.playOrToggle()` → `MediaController.setMediaItems(items, start, startPos)`。
3. `PlaybackService.onGetSession()` 创建 `ExoPlayer`：
   - `setAudioAttributes(..., handleAudioFocus = true)` — 来电/其他应用请求焦点时自动暂停恢复；
   - `setHandleAudioBecomingNoisy(true)` — 拔耳机自动暂停；
   - `setWakeMode(C.WAKE_MODE_LOCAL)` — 播放期间保持 CPU；
   - `onMediaItemTransition` 回调里累计播放次数与最后播放时间。
4. media3 默认通知提供器自动生成通知栏/锁屏控制，元数据（标题/艺术家/专辑封面）来自
   `Track.toMediaItem()` 构建的 `MediaMetadata`。

## 数据键策略

MediaStore 的文件 uri（`content://media/external/audio/media/<id>`）在设备重启、
SD 卡重新挂载后可能变化。数据库以 `(bucket, docId)` 为主键（v2），
`MusicDatabase.keyOf(uri)` 从 uri 解析；v1 的 uri 键数据在 `onUpgrade` 自动迁移，
收藏与播放进度不会丢。

## 已知边界

- 音声/有声书等分类依赖标题关键词推断（`Categories.infer`），用户手动改过分类后以数据库为准。
- 封面取 `content://media/external/audio/albumart/<albumId>`，文件缺内嵌封面且系统
  没建 albumart 条目时显示占位图标。
- Issues 在仓库层面禁用；反馈请走 PR 或仓库主页渠道。

## 本机构建（手机 proot 环境）

工具链装在 `/opt`：JDK 17（apt）、Gradle 8.11.1、Android SDK（platform-34 +
build-tools 34.0.4）。aarch64 设备没有官方 aapt2，使用 AndroidIDE 的 34.0.4 构建
（aapt2/zipalign 已覆盖进 `build-tools/34.0.4`，`android.aapt2FromMavenOverride`
在用户级 `~/.gradle/gradle.properties` 指向它，均不进仓库）。

因此项目锁定 `compileSdk 34 + buildToolsVersion 34.0.4`，依赖用接受 34 的版本
（media3 1.4.1、activity-compose 1.9.3）。CI（x86）同样兼容这套配置。

构建命令：

```bash
source /root/android-env.sh
/opt/gradle-8.11.1/bin/gradle -p music-player :app:assembleDebug --no-daemon
```
