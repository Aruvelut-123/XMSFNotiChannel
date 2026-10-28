package com.gswxxn.xmsfnotichannel.hook

import android.os.Binder
import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.ModuleConstants
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.param.hook
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

    override fun onHook() = encase {
        loadSystem {
            val moduleUid = runCatching { module.appInfo.uid }.getOrDefault(-1)
            val hooked = mutableListOf<String>()

            runCatching {
                val clazz = Class.forName(TARGET_CLASS, false, hostClassLoader)

                TARGET_METHODS.forEach { name ->
                    clazz.declaredMethods
                        .filter { it.name == name }
                        .forEach { method ->
                            runCatching {
                                method.isAccessible = true
                                method.hook {
                                    before { param ->
                                        // 只有本模块发起的 Binder 调用才伪装成系统，其余原样透传
                                        if (Binder.getCallingUid() == moduleUid) param.resultTrue()
                                    }
                                }
                                hooked += name
                            }.onFailure {
                                Log.w(TAG, "hook $TARGET_CLASS#$name failed", it)
                            }
                        }
                }
            }.onFailure { Log.e(TAG, "loadSystem failed", it) }

            val distinct = hooked.distinct()
            if (distinct.isEmpty()) {
                Log.e(TAG, "no method hooked in $TARGET_CLASS")
            } else {
                Log.i(TAG, "hooked $distinct in $TARGET_CLASS for uid=$moduleUid")
            }
            writeStatus(ready = distinct.isNotEmpty(), methods = distinct)
        }
    }

    /**
     * 把装载结果写进远程偏好，供 App 侧读取。
     * 写失败不影响 hook 本身，异常全部吞掉。
     */
    private fun com.highcapable.yukihookapi.hook.param.PackageParam.writeStatus(
        ready: Boolean,
        methods: List<String>
    ) {
        runCatching {
            prefs(ModuleConstants.PREFS_NAME).edit {
                putBoolean(ModuleConstants.KEY_HOOK_READY, ready)
                putLong(ModuleConstants.KEY_LOADED_AT, System.currentTimeMillis())
                putString(ModuleConstants.KEY_TARGET_CLASS, TARGET_CLASS)
                putString(ModuleConstants.KEY_HOOKED_METHODS, methods.joinToString(","))
            }
        }.onFailure { Log.w(TAG, "write status failed", it) }
    }
}