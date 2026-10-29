package cn.baymaxawa.xmsfnotichannel.utils

/**
 * 模块与系统进程共享的常量。
 * 注意：不要放在 hook 包里的 XposedModule 子类中——那些类在 App 进程里不可加载
 * （libxposed api 是 compileOnly），引用其常量会触发 NoClassDefFoundError。
 */
object ModuleConstants {

    /** 远程偏好（RemotePreferences）名称，系统进程写、App 读 */
    const val PREFS_NAME = "module_status"

    /**
     * 状态文件路径（system_server 写、App 读）。
     *
     * 为什么需要它：libxposed 的远程偏好依赖框架的 `PROP_CAP_REMOTE` 能力。
     * 本机实测 `prefs(...).edit { }` 返回成功，但
     * `/data/misc/apexdata/<lspd-uuid>/prefs/` 下并没有生成本模块目录 ——
     * 说明它退化成了 system_server 进程内的私有 SharedPreferences，
     * App 侧通过 `getRemotePreferences` 永远读不到 `hook_ready`，
     * 状态条因此一直停在「需重启」，界面按钮也会提前 return。
     *
     * `/data/system/` 只有 system_server 能写，App 读取需要 root，
     * 因此 [FrameworkStatus] 里对读不到的情况做了降级处理。
     */
    const val STATUS_FILE_PATH = "/data/system/xmsfnotichannel_status.txt"

    /**
     * 注意：上面这个状态文件**不能**用来判断 hook 是否生效。
     *
     * 实测结论（Android 17 / HyperOS 4.0）：
     * - system_server 能写 `/data/system/`，但该目录 0700 system:system，App 进不去，读不到；
     * - system_server 写不进 `/data/user/0/<包名>/files/`（SELinux EACCES）；
     * - system_server 也写不进 `/data/local/tmp/`（SELinux EACCES）。
     *
     * 因此真正的判据改为「能力探测」：App 直接尝试跨进程读一个**其它应用**的
     * 通知通道，抛 SecurityException 就是没生效，正常返回就是生效。
     * 见 [FrameworkStatus.probeByCapability] 与 [NCUtils.probeHookEffective]。
     *
     * 这个常量仅为排查期留存，正式逻辑不再读取。
     */
    @Deprecated("仅排查期留存，正式逻辑改用能力探测")
    const val UNUSED_STATUS_HINT = ""

    /** 模块包名（hook 侧在 system_server 里执行，不能依赖 BuildConfig 之外的上下文） */
    const val MODULE_PACKAGE = "cn.baymaxawa.xmsfnotichannel"

    /**
     * 写进模块私有目录的状态文件名（绝对路径 `/data/user/0/<包名>/files/hook_status.txt`）。
     *
     * 为什么还要这一份：上面的 [STATUS_FILE_PATH] 在 `/data/system/`，
     * 普通 App 进程读不到（该目录 0700 system:system）。
     * system_server 有 DAC override，可以直接把文件写进 App 私有目录并 chmod 644，
     * App 侧就能以自身身份读到，不需要 root。
     */
    const val STATUS_FILE_NAME = "hook_status.txt"

    /**
     * `/data/local/tmp` 下的状态文件。
     *
     * 为什么还要这一份：本机实测 system_server 写
     * `/data/user/0/<包名>/files/` 会被 SELinux 拒绝（`EACCES Permission denied`），
     * `/data/system/` 又因目录 0700 普通 App 进不去。
     * `/data/local/tmp` 的 SELinux 标签是 `shell_data_file`，
     * system_server（untrusted_app 之外的 system 域）可写、App 侧也能读。
     */
    const val STATUS_FILE_TMP = "/data/local/tmp/xmsfnotichannel_status.txt"

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