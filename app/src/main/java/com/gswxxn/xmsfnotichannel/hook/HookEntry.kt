package com.gswxxn.xmsfnotichannel.hook

import android.os.Binder
import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.DiagnosticProbe
import com.gswxxn.xmsfnotichannel.utils.ModuleConstants
import com.gswxxn.xmsfnotichannel.utils.ModuleUidResolver
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

/**
 * YukiHook 1.5.x 入口（基于 libxposed API 102 运行时）。
 *
 * ## 到底要 hook 哪些东西
 *
 * 本模块唯一目的：让「本模块自己发起的」Binder 调用被 `NotificationManagerService`
 * 当成系统调用，从而能读写其它 App 的通知通道。
 *
 * Android 17 (SDK 37) 这条校验链的最后一道闸都在
 * `com.android.server.notification.NotificationManagerService` 的私有方法上：
 *
 * | App 侧调用 | 服务端入口 | 最终校验 |
 * | --- | --- | --- |
 * | `getNotificationChannelsForPackage` | `Stub#getNotificationChannelsForPackage` | `enforceSystemOrSystemUI()` → **`isCallerSystemOrPhone()`** |
 * | `getNotificationChannelGroupsForPackage` | `Stub#getNotificationChannelGroupsForPackage` | 同上 |
 * | `updateNotificationChannelForPackage` | `Stub#updateNotificationChannelForPackage` | `assertCallerIsSystemOrSystemUiOrShell()` → **`isCallerSystemOrPhone()`** |
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
 * ## 为什么必须延迟 hook（1.5.2 的关键修复）
 *
 * YukiHook 的 `encase { }` 只会在**第一次**收到 `PackageParam` 时执行一次。
 * 在 system_server 里这次回调发生在 `SystemServer.createSystemContext()` 阶段
 * （见 `LibXposedBaseEntry#onSystemServerStarting`），此时
 * `SystemServiceManager` 还没 `startService` 到 `NotificationManagerService`，
 * 直接 `Class.forName(...)` 必然 `ClassNotFoundException`。
 *
 * 同机器上其他模块的日志也印证了这一点：
 * `Reflection ABSENT: com.android.server.notification.NotificationManagerService
 * cause=java.lang.ClassNotFoundException`。
 *
 * 所以这里改成：先 hook `SystemServiceManager#startService`，
 * 等目标类真正被系统实例化（`getServiceClass() == TARGET_CLASS`）的那一刻
 * 再把校验方法挂上去。这样既不轮询、不占 CPU，也完全避开时序问题。
 *
 * ## 状态回写
 *
 * hook 装成功后往 `module_status` 远程偏好写 `hook_ready=true`，
 * App 侧 FrameworkStatus 读这个值把状态条从「需重启」切成「已激活」。
 */
@YukiHookLibXposedEntry(
    targetApiVersion = 102,
    scope = ["system"],
    // 与同机器上能正常装载的模块保持一致：由管理器按作用域注入，不走开机静态注入
    staticScope = false
)
object HookEntry : YukiHookXposedModule {

    private const val TAG = ModuleConstants.LOG_TAG

    private const val TARGET_CLASS =
        "com.android.server.notification.NotificationManagerService"

    /** 系统服务管理器，用来等待 [TARGET_CLASS] 被真正实例化的时机。 */
    private const val SERVICE_MANAGER_CLASS = "com.android.server.SystemServiceManager"

    /**
     * 需要拦截的校验方法名。
     * 只按方法名匹配：这些方法的签名在 Android 12～17 之间保持稳定，
     * 按名匹配可以一次覆盖同名的全部重载。
     */
    private val TARGET_METHODS = listOf(
        "isCallerSystemOrPhone",
        "isCallerSystem",
        "isCallerSystemOrSystemUi",
        "isCallerSystemOrSystemUiOrShell",
        "assertCallerIsSystemOrSystemUiOrShell"
    )

    /** 防止 startService 被多次触发时重复挂 hook。 */
    @Volatile
    private var installed = false

    override fun onHook() = encase {
        DiagnosticProbe.mark("onHook:encaseBlock")
        loadSystem {
            DiagnosticProbe.mark("onHook:loadSystem")
            awaitTargetService()
        }
        DiagnosticProbe.mark("onHook:encaseBlockEnd")
    }

    /**
     * 挂一个「等待目标服务就绪」的钩子。
     *
     * 优先用 `SystemServiceManager#startService(Class)`：
     * 系统每启动一个服务都会经过它，此时目标类一定已经加载完成。
     * 如果这个入口拿不到（ROM 改签名等），退化为 `startBootPhase` ——
     * 那时 `PHASE_BOOT_COMPLETED` 已经走完，目标服务必然就位。
     */
    private fun PackageParam.awaitTargetService() {
        // 注意：不能再用 module.appInfo.uid。
        // 在 LSPosed 2.2.1 + API 102 的 system_server 里 moduleAppInfo 为 null，
        // 该 getter 会直接抛异常，被 runCatching 吞掉后静默变成 -1，
        // 导致 hook 条件永远不成立（钩子挂上但不生效）。
        val moduleUid = ModuleUidResolver.resolve()
        DiagnosticProbe.mark("awaitTargetService:enter", "moduleUid=$moduleUid")

        val waitHookInstalled = runCatching {
            val manager = Class.forName(SERVICE_MANAGER_CLASS, false, hostClassLoader)

            // 首选：startService(Class) —— 参数就是即将启动的服务类
            val startService = manager.declaredMethods.firstOrNull {
                it.name == "startService" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Class::class.java
            }

            if (startService != null) {
                startService.isAccessible = true
                startService.hook {
                    after {
                        runCatching {
                            val started = args.firstOrNull() as? Class<*> ?: return@runCatching
                            if (started.name == TARGET_CLASS) {
                                DiagnosticProbe.mark("startService:targetStarted")
                                hookTargets(moduleUid)
                            }
                        }.onFailure { Log.w(TAG, "startService callback failed", it) }
                    }
                }
                Log.i(TAG, "waiting $TARGET_CLASS via $SERVICE_MANAGER_CLASS#startService")
                DiagnosticProbe.mark("awaitTargetService:startServiceHook")
                true
            } else {
                // 兜底：每次 boot phase 推进时检查一次目标类是否已可加载
                val startBootPhase = manager.declaredMethods.firstOrNull {
                    it.name == "startBootPhase" && it.parameterTypes.size == 1
                }
                if (startBootPhase == null) {
                    Log.e(TAG, "no usable entry on $SERVICE_MANAGER_CLASS")
                    false
                } else {
                    startBootPhase.isAccessible = true
                    startBootPhase.hook {
                        after { runCatching { hookTargets(moduleUid, quiet = true) } }
                    }
                    Log.i(TAG, "waiting $TARGET_CLASS via $SERVICE_MANAGER_CLASS#startBootPhase")
                    true
                }
            }
        }.getOrElse {
            Log.e(TAG, "failed to install wait hook", it)
            DiagnosticProbe.mark("awaitTargetService:fail", "err=${it::class.java.name}:${it.message}")
            false
        }

        if (!waitHookInstalled) {
            // 连等待钩子都挂不上时，仍然试一次直接挂载，至少留下日志便于定位
            hookTargets(moduleUid)
        }
    }

    /**
     * 真正挂载校验方法。可被多次调用，内部有 [installed] 幂等保护。
     *
     * @param quiet 为 true 时，在目标类尚未加载的情况下不写状态、不打错误日志。
     */
    private fun PackageParam.hookTargets(moduleUid: Int, quiet: Boolean = false) {
        if (installed) return

        // uid 解析失败时不能静默装一个永远不生效的钩子，
        // 否则现象是「hook 成功日志有、实际毫无作用」，极难排查。
        if (moduleUid <= 0) {
            Log.e(TAG, "module uid unresolved, abort hooking")
            DiagnosticProbe.mark("hookTargets:abortUid", "moduleUid=$moduleUid")
            return
        }

        val clazz = runCatching {
            Class.forName(TARGET_CLASS, false, hostClassLoader)
        }.getOrElse {
            if (!quiet) {
                Log.e(TAG, "$TARGET_CLASS not loadable yet", it)
                DiagnosticProbe.mark("hookTargets:classFail", "err=${it::class.java.name}:${it.message}")
            }
            return
        }

        val hooked = mutableListOf<String>()
        TARGET_METHODS.forEach { name ->
            clazz.declaredMethods
                .filter { it.name == name }
                .forEach { method ->
                    runCatching {
                        method.isAccessible = true
                        method.hook {
                            before {
                                // 只记录「本模块自身的调用」，避免被系统其它进程刷爆探针文件。
                                // 用途：区分「hook 没挂上」与「hook 挂上了但 App 走的是别的方法」。
                                val callerUid = Binder.getCallingUid()
                                if (callerUid == moduleUid) {
                                    DiagnosticProbe.mark(
                                        "hit:$name",
                                        "callerUid=$callerUid"
                                    )
                                    result = true
                                }
                            }
                        }
                        hooked += name
                    }.onFailure {
                        Log.w(TAG, "hook $TARGET_CLASS#$name failed", it)
                    }
                }
        }

        val distinct = hooked.distinct()
        if (distinct.isEmpty()) {
            if (!quiet) Log.e(TAG, "no method hooked in $TARGET_CLASS")
            return
        }

        installed = true
        Log.i(TAG, "hooked $distinct in $TARGET_CLASS for uid=$moduleUid")
        DiagnosticProbe.mark("hookTargets:success", "methods=$distinct")
        writeStatus(methods = distinct)
    }

    /**
     * 把装载结果写进远程偏好，供 App 侧读取。
     * 写失败不影响 hook 本身，异常全部吞掉。
     */
    private fun PackageParam.writeStatus(methods: List<String>) {
        runCatching {
            prefs(ModuleConstants.PREFS_NAME).edit {
                putBoolean(ModuleConstants.KEY_HOOK_READY, true)
                putLong(ModuleConstants.KEY_LOADED_AT, System.currentTimeMillis())
                putString(ModuleConstants.KEY_TARGET_CLASS, TARGET_CLASS)
                putString(ModuleConstants.KEY_HOOKED_METHODS, methods.joinToString(","))
            }
            DiagnosticProbe.mark("writeStatus:ok")
        }.onFailure {
            Log.w(TAG, "write status failed", it)
            DiagnosticProbe.mark("writeStatus:fail", "err=${it::class.java.name}:${it.message}")
        }
    }
}
