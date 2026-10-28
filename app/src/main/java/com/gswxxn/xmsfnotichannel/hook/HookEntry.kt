package com.gswxxn.xmsfnotichannel.hook

import android.os.Binder
import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.ModuleConstants
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

/**
 * YukiHook 1.5.x 入口（基于 libxposed API 102 运行时）。
 *
 * 只做一件事：当 Binder 调用者属于本模块时，让
 * `NotificationManagerService#isCallerSystemOrPhone()` 返回 true。
 *
 * Android 17 (SDK 37) 的服务端校验全部收敛到这一个方法：
 *  - Stub#getNotificationChannelsForPackage -> enforceSystemOrSystemUI() -> isCallerSystemOrPhone()
 *  - Stub#updateNotificationChannelForPackage -> assertCallerIsSystemOrSystemUiOrShell() -> isCallerSystemOrPhone()
 * 因此保持单点 hook，避免对系统造成额外影响。
 *
 * 注意：`META-INF/xposed/java_init.list`、`module.prop`、`scope.list` 由
 * yukihook-compiler 的 KSP 在编译期自动生成，不要再手工放进 sources。
 */
@YukiHookLibXposedEntry(
    targetApiVersion = 102,
    scope = ["system"],
    staticScope = true
)
object HookEntry : YukiHookXposedModule {

    private const val TAG = ModuleConstants.LOG_TAG

    private const val TARGET_CLASS =
        "com.android.server.notification.NotificationManagerService"

    private const val TARGET_METHOD = "isCallerSystemOrPhone"

    override fun onHook() = encase {
        loadSystem {
            runCatching {
                val moduleUid = module.appInfo.uid
                val clazz = Class.forName(TARGET_CLASS, false, hostClassLoader)
                val method = clazz.getDeclaredMethod(TARGET_METHOD)
                method.hook {
                    before {
                        // 只有本模块发起的 Binder 调用才伪装成系统，其余原样透传
                        if (Binder.getCallingUid() == moduleUid) result = true
                    }
                }
                Log.i(TAG, "hooked $TARGET_CLASS#$TARGET_METHOD for uid=$moduleUid")
            }.onFailure {
                Log.e(TAG, "hook $TARGET_CLASS#$TARGET_METHOD failed", it)
            }
        }
    }
}