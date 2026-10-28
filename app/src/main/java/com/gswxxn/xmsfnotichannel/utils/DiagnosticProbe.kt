package com.gswxxn.xmsfnotichannel.utils

import android.util.Log
import java.io.File

/**
 * 临时排查工具：把关键执行点落盘到 system_server 可写的位置。
 *
 * 为什么需要它：LSPosed 的 verbose 日志只会收录**模块自己调用了 Log** 的行，
 * 而 logcat 缓冲区又会被其它进程刷掉；remote prefs 只有在 hook 完全成功后才写。
 * 三者都无法回答「模块走到了哪一步就停住了」。
 *
 * 落盘位置选 `/data/system/`（只有 system_server 有权限写），
 * 因此「文件存在」本身就证明代码真的在 system_server 里跑起来了。
 *
 * 排查结束后整个文件可以直接删除，不影响任何正式逻辑。
 */
object DiagnosticProbe {

    private const val TAG = ModuleConstants.LOG_TAG

    private const val PROBE_PATH = "/data/system/xmsfnotichannel_probe.log"

    @Volatile
    private var fileReady: Boolean? = null

    /** 记录一个执行阶段。任何异常都吞掉，绝不影响主流程。 */
    fun mark(stage: String, extra: String = "") {
        runCatching { Log.i(TAG, "probe:$stage $extra") }
        runCatching {
            val file = File(PROBE_PATH)
            // 只探测一次写权限，避免每一步都触发一次失败的系统调用
            val ok = fileReady ?: run {
                val result = file.exists() || file.parentFile?.canWrite() == true
                fileReady = result
                result
            }
            if (ok != true) return
            file.appendText(
                "stage=$stage process=${processName()} pid=${android.os.Process.myPid()} " +
                    "uid=${android.os.Process.myUid()} at=${System.currentTimeMillis()} $extra\n"
            )
        }
    }

    private fun processName(): String = runCatching {
        File("/proc/self/cmdline").readText().trim().trimEnd('\u0000').ifEmpty { "unknown" }
    }.getOrDefault("unknown")
}