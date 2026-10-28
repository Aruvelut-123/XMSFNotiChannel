package com.gswxxn.xmsfnotichannel.hook

import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.DiagnosticProbe
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
 * 1.5.4 起带临时探针（[DiagnosticProbe]），排查结束后删除。
 */
class YukiHookEntry : LibXposed102Entry() {

    override fun createHookEntry(): YukiHookXposedModule {
        DiagnosticProbe.mark("createHookEntry:enter")
        val module = try {
            HookEntry
        } catch (t: Throwable) {
            DiagnosticProbe.mark("createHookEntry:failed", "err=${t::class.java.name}:${t.message}")
            throw t
        }
        DiagnosticProbe.mark("createHookEntry:return")
        return module
    }
}