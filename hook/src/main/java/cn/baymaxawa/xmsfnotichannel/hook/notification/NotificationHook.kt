package cn.baymaxawa.xmsfnotichannel.hook.notification

import android.os.Binder
import cn.baymaxawa.xmsfnotichannel.hook.base.BaseHook
import cn.baymaxawa.xmsfnotichannel.hook.base.BaseLoad
import cn.baymaxawa.xmsfnotichannel.hook.base.PackageTarget
import cn.baymaxawa.xmsfnotichannel.hook.xposed.HookHelper

/**
 * system_server 侧的通知通道权限 hook。
 *
 * ## 目的
 *
 * 本模块唯一目的：让「本模块自己发起的」Binder 调用被 `NotificationManagerService`
 * 当成系统调用，从而能读写其它 App 的通知通道。
 *
 * ## 要拦截什么
 *
 * Android 17 (SDK 37) 这条校验链的最后一道闸都在
 * `com.android.server.notification.NotificationManagerService` 的私有方法上：
 *
 * | App 侧调用 | 最终校验 |
 * | --- | --- |
 * | `getNotificationChannelsForPackage` | `enforceSystemOrSystemUI()` → **`isCallerSystemOrPhone()`** |
 * | `getNotificationChannelGroupsForPackage` | 同上 |
 * | `updateNotificationChannelForPackage` | `assertCallerIsSystemOrSystemUiOrShell()` → **`isCallerSystemOrPhone()`** |
 *
 * 所以只需要在**一个类**上拦截这几个「无参校验方法」：
 *
 * 1. `isCallerSystemOrPhone()` —— 核心闸门，所有路径最终都会走到
 * 2. `isCallerSystem()` —— 少数路径直接判断
 * 3. `isCallerSystemOrSystemUi()` —— 同上
 * 4. `isCallerSystemOrSystemUiOrShell()` —— 同上
 * 5. `assertCallerIsSystemOrSystemUiOrShell()` —— 更新类接口的断言方法
 *
 * **只改本模块自己的调用**（`Binder.getCallingUid() == moduleUid`），
 * 其余进程一律透传，把对系统的影响降到最小。
 *
 * ## 为什么必须延迟 hook
 *
 * `onSystemServerStarting` 阶段 `SystemServiceManager` 还没 `startService` 到
 * `NotificationManagerService`，直接 `Class.forName(...)` 必然 `ClassNotFoundException`。
 * 因此先 hook `SystemServiceManager#startService`，等目标类真正被实例化那一刻
 * 再把校验方法挂上去：不轮询、不占 CPU，也完全避开时序问题。
 *
 * 拿不到 `startService` 时退化为 hook `startBootPhase`，那时目标服务必然就位。
 */
class NotificationHook : BaseHook() {

    override val key: String = KEY

    /** 防止 startService / startBootPhase 被多次触发时重复挂载 */
    @Volatile
    private var installed = false

    override fun init() {
        val classLoader = target.classLoader
        val moduleUid = ModuleUid.resolve()

        HookHelper.log("NotificationHook: enter moduleUid=$moduleUid")

        // uid 解析失败时不能静默装一个永远不生效的钩子，
        // 否则现象是「hook 成功日志有、实际毫无作用」，极难排查。
        if (moduleUid <= 0) {
            HookHelper.log("NotificationHook: module uid unresolved, abort")
            return
        }

        val waitHookInstalled = runCatching { installWaitHook(classLoader, moduleUid) }
            .onFailure { HookHelper.log("NotificationHook: wait hook failed", it) }
            .getOrDefault(false)

        if (!waitHookInstalled) {
            // 连等待钩子都挂不上时，仍然试一次直接挂载，至少留下日志便于定位
            hookTargets(classLoader, moduleUid)
        }
    }

    /**
     * 挂一个「等待目标服务就绪」的钩子。
     *
     * 优先用 `SystemServiceManager#startService(Class)`：
     * 系统每启动一个服务都会经过它，此时目标类一定已经加载完成。
     * 如果这个入口拿不到（ROM 改签名等），退化为 `startBootPhase`。
     */
    private fun installWaitHook(classLoader: ClassLoader?, moduleUid: Int): Boolean {
        val manager = Class.forName(SERVICE_MANAGER_CLASS, false, classLoader)

        val startService = manager.declaredMethods.firstOrNull {
            it.name == "startService" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Class::class.java
        }

        if (startService != null) {
            startService.isAccessible = true
            HookHelper.hookAfter(startService) { param ->
                runCatching {
                    val started = param.args.firstOrNull() as? Class<*> ?: return@runCatching
                    if (started.name == TARGET_CLASS) {
                        HookHelper.log("NotificationHook: $TARGET_CLASS started")
                        hookTargets(classLoader, moduleUid)
                    }
                }.onFailure { HookHelper.log("NotificationHook: startService callback failed", it) }
            }
            HookHelper.log("NotificationHook: waiting $TARGET_CLASS via startService")
            return true
        }

        // 兜底：每次 boot phase 推进时检查一次目标类是否已可加载
        val startBootPhase = manager.declaredMethods.firstOrNull {
            it.name == "startBootPhase" && it.parameterTypes.size == 1
        } ?: run {
            HookHelper.log("NotificationHook: no usable entry on $SERVICE_MANAGER_CLASS")
            return false
        }

        startBootPhase.isAccessible = true
        HookHelper.hookAfter(startBootPhase) {
            runCatching { hookTargets(classLoader, moduleUid, quiet = true) }
        }
        HookHelper.log("NotificationHook: waiting $TARGET_CLASS via startBootPhase")
        return true
    }

    /**
     * 真正挂载校验方法。可被多次调用，内部有 [installed] 幂等保护。
     *
     * @param quiet 为 true 时，在目标类尚未加载的情况下不打错误日志。
     */
    private fun hookTargets(classLoader: ClassLoader?, moduleUid: Int, quiet: Boolean = false) {
        if (installed) return

        val clazz = runCatching {
            Class.forName(TARGET_CLASS, false, classLoader)
        }.getOrElse {
            if (!quiet) HookHelper.log("NotificationHook: $TARGET_CLASS not loadable yet", it)
            return
        }

        val hooked = mutableListOf<String>()
        TARGET_METHODS.forEach { name ->
            clazz.declaredMethods
                .filter { it.name == name }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        HookHelper.hookReplace(method) { param ->
                            // 只放行「本模块自身的调用」，其余进程一律走原逻辑，
                            // 避免把系统行为改成全局后门。
                            val callerUid = Binder.getCallingUid()
                            if (callerUid == moduleUid) {
                                if (method.returnType == Boolean::class.javaPrimitiveType) {
                                    true
                                } else {
                                    null
                                }
                            } else {
                                param.result = method.invoke(param.thisObject, *param.args.toTypedArray())
                                param.result
                            }
                        }
                        hooked += name
                    }.onFailure {
                        HookHelper.log("NotificationHook: hook $TARGET_CLASS#$name failed", it)
                    }
                }
        }

        val distinct = hooked.distinct()
        if (distinct.isEmpty()) {
            if (!quiet) HookHelper.log("NotificationHook: no method hooked in $TARGET_CLASS")
            return
        }

        installed = true
        HookHelper.log("NotificationHook: hooked $distinct for uid=$moduleUid")
    }

    companion object {
        const val KEY = "notification_channel_keyhook"

        private const val TARGET_CLASS =
            "com.android.server.notification.NotificationManagerService"

        /** 系统服务管理器，用来等待 [TARGET_CLASS] 被真正实例化的时机。 */
        private const val SERVICE_MANAGER_CLASS = "com.android.server.SystemServiceManager"

        /**
         * 需要拦截的校验方法名。
         *
         * 只按方法名匹配：这些方法的签名在 Android 12～17 之间保持稳定，
         * 按名匹配可以一次覆盖同名的全部重载。
         */
        private val TARGET_METHODS = listOf(
            "isCallerSystemOrPhone",
            "isCallerSystem",
            "isCallerSystemOrSystemUi",
            "isCallerSystemOrSystemUiOrShell",
            "assertCallerIsSystemOrSystemUiOrShell",
        )
    }
}

/**
 * system_server 侧 Load：负责把 [NotificationHook] 装上。
 *
 * 目标包名是 libxposed 对 system_server 的虚拟标识 `system`
 * （见 `META-INF/xposed/scope.list`）。
 */
class NotificationLoad : BaseLoad() {
    override val targetPackages: List<String> = listOf("system", "android")

    override fun onPackageLoaded(target: PackageTarget) {
        initHook(NotificationHook(), true)
    }
}

/**
 * 解析模块自身的 uid。
 *
 * 在 LSPosed 2.2.1 + libxposed API 102 的 system_server 里拿不到模块的
 * ApplicationInfo，因此按可靠性从高到低准备多条路径：
 *
 * 1. `/data/system/packages.list`：system_server 以 system(1000) 身份运行，
 *    该文件属主正是 system，可直接读取。格式为 `包名 uid debuggable 数据目录 …`。
 * 2. 反射 `ActivityThread#getPackageManager()` 后调用任意 `getPackageUid` 重载。
 */
internal object ModuleUid {

    private const val MODULE_PACKAGE = "cn.baymaxawa.xmsfnotichannel"

    private const val PACKAGES_LIST = "/data/system/packages.list"

    /** 同一个进程内包名与 uid 不会变，解析一次即可。 */
    @Volatile
    private var cached: Int? = null

    fun resolve(): Int {
        cached?.let { return it }
        val uid = fromPackagesList()
            .takeIf { it > 0 }
            ?: fromPackageManager().takeIf { it > 0 }
            ?: -1
        if (uid > 0) cached = uid
        return uid
    }

    /** 直接从 `packages.list` 里按包名取第二列。无需任何权限反射，最稳。 */
    private fun fromPackagesList(): Int = runCatching {
        val file = java.io.File(PACKAGES_LIST)
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

        val candidate = pm.javaClass.methods.firstOrNull {
            it.name == "getPackageUid" && it.parameterTypes.firstOrNull() == String::class.java
        } ?: return@runCatching -1

        val args: Array<Any?> = Array(candidate.parameterTypes.size) { index ->
            when (val type = candidate.parameterTypes[index]) {
                String::class.java -> if (index == 0) MODULE_PACKAGE else null
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }

        candidate.invoke(pm, *args) as? Int ?: -1
    }.getOrDefault(-1)
}