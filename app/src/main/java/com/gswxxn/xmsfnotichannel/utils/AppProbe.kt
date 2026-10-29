package com.gswxxn.xmsfnotichannel.utils

import android.content.Context
import java.io.File

/**
 * App 侧落盘日志（排查用）。
 *
 * 为什么需要：logcat 的 main 缓冲区只有 64 KiB，
 * 系统的噪音会在几秒内把它刷掉，导致「模块自己的日志」几乎抓不到。
 * 因此把关键分支写到应用私有目录，用 root 一定能读到。
 *
 * 排查结束后可直接删除本文件与调用点。
 */
object AppProbe {

    @Volatile
    private var dir: File? = null

    /** 在 Application 或 Activity 里调用一次即可。 */
    fun init(context: Context) {
        if (dir != null) return
        runCatching {
            val d = File(context.filesDir, "probe")
            if (!d.exists()) d.mkdirs()
            dir = d
        }
    }

    fun mark(stage: String, extra: String = "") {
        runCatching {
            val d = dir ?: return
            File(d, "app_probe.log").appendText(
                "stage=$stage pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()} " +
                    "at=${System.currentTimeMillis()} $extra\n"
            )
        }
    }
}