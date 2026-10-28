package com.gswxxn.xmsfnotichannel.hook

import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.ModuleConstants
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.runtime.libxposed.entry.LibXposed102Entry
import java.io.File

/**
 * libxposed API 102 的实际入口（由 `META-INF/xposed/java_init.list` 指向）。
 *
 * 正常情况下这个类由 yukihook-compiler 的 KSP 自动生成，但 KSP 目前还没有
 * 对应 Kotlin 2.4 的版本，而 YukiHook 1.5.0 的产物正是用 Kotlin 2.4 编译的。
 * 因此这里手写等价实现：框架加载本类，本类再把 [HookEntry] 交给 YukiHook。
 *
 * ## 为什么入口里有探针
 *
 * 排查「模块是否真的被加载进 system_server」时发现一个陷阱：
 * LSPosed 的 verbose 日志里只会出现**模块自己打了日志**的那些行，
 * 所以「日志里看不到本模块」既可能是没加载，也可能是加载了但没打印 ——
 * 两者无法区分。同机的 hyperceiler 就出现过「全局 83 行、(system) 里 0 行」
 * 的情况，说明这种判据不可靠。
 *
 * 因此在入口被调用的第一时间往文件里落一行痕迹：
 * 文件存在于 system_server 可写的目录且内容随进程名变化，
 * 就能确定「框架确实加载并执行了本入口」。
 * 探针全部包在 runCatching 里，失败不留副作用、不影响 hook 主流程。
 */
class YukiHookEntry : LibXposed102Entry() {

    override fun createHookEntry(): YukiHookXposedModule {
        probe("createHookEntry")
        return HookEntry
    }

    private companion object {

        const val TAG = ModuleConstants.LOG_TAG

        /** system_server 自身可写的目录，普通 App 进程无权限时探针静默失败。 */
        val PROBE_FILES = listOf(
            "/data/system/xmsfnotichannel_probe.log",
            "/data/local/tmp/xmsfnotichannel_probe.log",
            "/sdcard/xmsfnotichannel_probe.log"
        )

        fun probe(stage: String) {
            runCatching { Log.i(TAG, "probe:$stage pid=${android.os.Process.myPid()}") }
            val line = runCatching {
                val process = currentProcessName() ?: "unknown"
                "stage=$stage process=$process pid=${android.os.Process.myPid()} " +
                    "uid=${android.os.Process.myUid()} at=${System.currentTimeMillis()}\n"
            }.getOrDefault("stage=$stage\n")
            PROBE_FILES.forEach { path ->
                runCatching {
                    File(path).appendText(line)
                }
            }
        }

        /** 从 `/proc/self/cmdline` 读进程名，避免依赖框架回调时机。 */
        fun currentProcessName(): String? = runCatching {
            File("/proc/self/cmdline").readText().trim().trimEnd('\u0000').ifEmpty { null }
        }.getOrNull()
    }
}
