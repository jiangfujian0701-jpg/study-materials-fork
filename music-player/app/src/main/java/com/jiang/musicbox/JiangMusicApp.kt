package com.jiang.musicbox

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局崩溃记录：任何线程的未捕获异常都会写进 crash_last.txt，下次启动可在应用内查看复制 */
class JiangMusicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val ver = try {
                    val p = getPackageManager().getPackageInfo(packageName, 0)
                    "${p.versionName} (${p.longVersionCode})"
                } catch (_: Throwable) { "?" }
                val text = buildString {
                    appendLine("time: $time")
                    appendLine("thread: ${thread.name}")
                    appendLine("version: $ver")
                    appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine(Log.getStackTraceString(throwable))
                }
                File(filesDir, "crash_last.txt").writeText(text)
            } catch (_: Throwable) {
            }
            systemHandler?.uncaughtException(thread, throwable)
        }
    }

    private object Log {
        fun getStackTraceString(t: Throwable): String = t.stackTraceToString() +
            generateSequence(t.cause) { it.cause }.take(6)
                .joinToString("\n") { "Caused by: $it" }
    }

    companion object {
        /** 读上次崩溃日志；没有返回 null */
        fun readCrashLog(context: android.content.Context): String? = try {
            val f = File(context.filesDir, "crash_last.txt")
            if (f.exists() && f.length() > 0) f.readText() else null
        } catch (_: Throwable) { null }

        fun clearCrashLog(context: android.content.Context) {
            try { File(context.filesDir, "crash_last.txt").delete() } catch (_: Throwable) {}
        }
    }
}
