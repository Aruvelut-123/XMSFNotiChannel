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
    const val KEY_HOOKED_METHODS = "hooked_methods"

    /**
     * 作用域：系统框架。
     *
     * LSPosed 对 system_server 的作用域标识是虚拟包名 `system`（不是 `android`），
     * `META-INF/xposed/scope.list` 里也必须写 `system` 才会被装载。
     * `android` 保留为兼容别名，两者任一命中都视为在作用域内。
     */
    const val SCOPE_SYSTEM = "system"

    /** 兼容别名：部分框架或旧版本用 `android` 表示系统框架 */
    const val SCOPE_ANDROID = "android"

    const val LOG_TAG = "XMSF_GG"
}