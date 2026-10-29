package cn.baymaxawa.xmsfnotichannel.utils

/**
 * 排查期使用的落盘探针（**已停用**）。
 *
 * 保留类型是为了让既有调用点无需改动；[enabled] 为 false 时不产生任何
 * 文件 IO 和日志输出，正式发布态等同空实现。
 *
 * 历史作用：LSPosed 的 verbose 日志只收录模块自己 Log 的行，
 * 而 logcat 缓冲区会被其它进程刷掉，remote prefs 又只在 hook 完全成功后写。
 * 三者都无法回答「模块走到了哪一步」，因此曾把关键执行点写进
 * `/data/system/`（只有 system_server 能写，文件存在即证明代码在系统进程执行）。
 *
 * 重建那段排查结论时把 [enabled] 改回 true 即可。
 */
object DiagnosticProbe {

    private const val enabled = false

    private const val PROBE_PATH = "/data/system/xmsfnotichannel_probe.log"

    @Volatile
    private var fileReady: Boolean? = null

    /** 记录一个执行阶段。停用后直接返回。 */
    fun mark(stage: String, extra: String = "") {
        if (!enabled) return
        runCatching {
            val file = java.io.File(PROBE_PATH)
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
        java.io.File("/proc/self/cmdline").readText().trim().trimEnd('\u0000').ifEmpty { "unknown" }
    }.getOrDefault("unknown")
}