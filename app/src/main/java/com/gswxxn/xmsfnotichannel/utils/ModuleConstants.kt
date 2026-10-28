package com.gswxxn.xmsfnotichannel.utils

/**
 * 模块与系统进程共享的常量。
 * 注意：不要放在 hook 包里的 XposedModule 子类中——那些类在 App 进程里不可加载
 * （libxposed api 是 compileOnly），引用其常量会触发 NoClassDefFoundError。
 */
object ModuleConstants {

    /** 远程偏好（RemotePreferences）名称，系统进程写、App 读 */
    const val PREFS_NAME = "module_status"

    const val KEY_HOOK_READY = "hook_ready"
    const val KEY_LOADED_AT = "loaded_at"
    const val KEY_TARGET_CLASS = "target_class"

    /** 作用域：系统框架 */
    const val SCOPE_SYSTEM = "android"

    const val LOG_TAG = "XMSF_GG"
}