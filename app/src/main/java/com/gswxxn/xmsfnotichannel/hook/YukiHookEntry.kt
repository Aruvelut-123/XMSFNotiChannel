package com.gswxxn.xmsfnotichannel.hook

import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.runtime.libxposed.entry.LibXposed102Entry

/**
 * libxposed API 102 的实际入口（由 `META-INF/xposed/java_init.list` 指向）。
 *
 * 正常情况下这个类由 yukihook-compiler 的 KSP 自动生成，但 KSP 目前还没有
 * 对应 Kotlin 2.4 的版本，而 YukiHook 1.5.0 的产物正是用 Kotlin 2.4 编译的。
 * 因此这里手写等价实现：框架加载本类，本类再把 [HookEntry] 交给 YukiHook。
 */
class YukiHookEntry : LibXposed102Entry() {

    override fun createHookEntry(): YukiHookXposedModule = HookEntry
}