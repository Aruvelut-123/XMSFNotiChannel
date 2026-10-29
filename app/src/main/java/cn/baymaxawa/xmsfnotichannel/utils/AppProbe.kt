package cn.baymaxawa.xmsfnotichannel.utils

import android.content.Context

/**
 * App 侧落盘日志（**已停用**）。
 *
 * 保留类型是为了让既有调用点无需改动；[enabled] 为 false 时不产生任何
 * 文件 IO，正式发布态等同空实现。
 *
 * 历史作用：logcat 的 main 缓冲区只有 64 KiB，系统噪音几秒内就会把
 * 模块自己的日志刷掉，因此曾把关键分支写进应用私有目录。
 * 那段排查已经完成，需要复现时把 [enabled] 改回 true 即可。
 */
object AppProbe {

    private const val enabled = false

    @Volatile
    private var dir: java.io.File? = null

    /** 在 Application 或 Activity 里调用一次即可。 */
    fun init(context: Context) {
        if (!enabled) return
        if (dir != null) return
        runCatching {
            val d = java.io.File(context.filesDir, "probe")
            if (!d.exists()) d.mkdirs()
            dir = d
        }
    }

    fun mark(stage: String, extra: String = "") {
        if (!enabled) return
        runCatching {
            val d = dir ?: return
            java.io.File(d, "app_probe.log").appendText(
                "stage=$stage pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()} " +
                    "at=${System.currentTimeMillis()} $extra\n"
            )
        }
    }
}