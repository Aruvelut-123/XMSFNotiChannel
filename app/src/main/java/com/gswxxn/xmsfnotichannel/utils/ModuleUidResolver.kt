package com.gswxxn.xmsfnotichannel.utils

import java.io.File

/**
 * 解析模块自身的 uid。
 *
 * ## 为什么不能直接用 `PackageParam.module.appInfo.uid`
 *
 * YukiHook 的实现是：
 * ```kotlin
 * val appInfo get() = XposedModuleRuntime.moduleAppInfo
 *     ?: error("The current hook framework does not support module application info.")
 * ```
 * 在 LSPosed 2.2.1 + libxposed API 102 的 system_server 里
 * `moduleAppInfo` 为 null，这个 getter 直接抛异常。
 * 原代码用 `runCatching { }.getOrDefault(-1)` 包住，于是 uid 静默变成 **-1**，
 * 而 hook 条件 `Binder.getCallingUid() == moduleUid` 永远不成立 ——
 * 钩子挂上了却完全不生效，且没有任何报错。
 *
 * 因此这里按可靠性从高到低准备多条路径：
 *
 * 1. `/data/system/packages.list`：`system_server` 以 system(1000) 身份运行，
 *    该文件属主正是 system，可直接读取。格式为
 *    `包名 uid debuggable 数据目录 seiinfo …`。
 * 2. 反射 `ActivityThread#getPackageManager()` 后调用任意 `getPackageUid` 重载。
 * 3. YukiHook 的 `module.appInfo`（部分框架实现支持）。
 */
object ModuleUidResolver {

    private const val MODULE_PACKAGE = "com.gswxxn.xmsfnotichannel"

    private const val PACKAGES_LIST = "/data/system/packages.list"

    /** 结果缓存：同一个进程内包名与 uid 不会变，解析一次即可。 */
    @Volatile
    private var cached: Int? = null

    /**
     * 解析模块 uid。
     * @return 解析成功返回 uid，全部失败返回 -1。
     */
    fun resolve(): Int {
        cached?.let { return it }
        val uid = fromPackagesList()
            .takeIf { it > 0 }
            ?: fromPackageManager()
                .takeIf { it > 0 }
            ?: -1
        if (uid > 0) cached = uid
        return uid
    }

    /** 直接从 `packages.list` 里按包名取第二列。无需任何权限反射，最稳。 */
    private fun fromPackagesList(): Int = runCatching {
        val file = File(PACKAGES_LIST)
        if (!file.canRead()) return@runCatching -1
        file.useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(' ')
                if (parts.size >= 2 && parts[0] == MODULE_PACKAGE) {
                    return@useLines parts[1].toIntOrNull() ?: -1
                }
            }
            -1
        }
    }.getOrDefault(-1)

    /** 反射系统 PackageManager 查询 uid，兼容不同版本的方法签名。 */
    private fun fromPackageManager(): Int = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val getPackageManager = activityThread.getDeclaredMethod("getPackageManager")
            .apply { isAccessible = true }
        val pm = getPackageManager.invoke(null) ?: return@runCatching -1

        // 在 getPackageUid 的全部重载里，挑第一个「首参为 String」的尝试
        val candidate = pm.javaClass.methods.firstOrNull {
            it.name == "getPackageUid" && it.parameterTypes.firstOrNull() == String::class.java
        } ?: return@runCatching -1

        // 逐个位置按形参类型填值：String / int / long，其余补 null
        val args: Array<Any?> = Array(candidate.parameterTypes.size) { index ->
            when (val type = candidate.parameterTypes[index]) {
                String::class.java -> if (index == 0) MODULE_PACKAGE else null
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }

        val result = candidate.invoke(pm, *args) as? Int ?: -1
        result
    }.getOrDefault(-1)
}