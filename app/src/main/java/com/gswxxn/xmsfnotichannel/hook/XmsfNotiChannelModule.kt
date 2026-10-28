package com.gswxxn.xmsfnotichannel.hook

import android.os.Binder
import android.util.Log
import com.gswxxn.xmsfnotichannel.utils.ModuleConstants
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed (API 102) 入口，运行在 system_server。
 *
 * 只做一件事：当 Binder 调用者属于本模块时，让
 * NotificationManagerService#isCallerSystemOrPhone() 返回 true。
 *
 * Android 17 (SDK 37) 的服务端校验全部收敛到这一个方法：
 *  - Stub#getNotificationChannelGroupsForPackage -> enforceSystemOrSystemUI()      -> isCallerSystemOrPhone()
 *  - Stub#updateNotificationChannelForPackage     -> assertCallerIsSystemOrSystemUiOrShell() -> isCallerSystemOrPhone()
 * 因此保持单点 hook，避免对系统造成额外影响。
 */
class XmsfNotiChannelModule : XposedModule() {

    private companion object {
        const val TAG = ModuleConstants.LOG_TAG

        /** AID_USER_OFFSET：uid = userId * 100000 + appId */
        const val AID_USER_OFFSET = 100000
    }

    @Volatile
    private var hooked = false

    @Volatile
    private var moduleUid: Int = -1

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Log.i(
            TAG,
            "onModuleLoaded process=${param.processName} isSystemServer=${param.isSystemServer} " +
                "framework=$frameworkName($frameworkVersionCode) api=$apiVersion"
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        installHook(param.classLoader)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // 部分框架只在 package ready 阶段回调系统框架
        if (param.packageName == "android") installHook(param.classLoader)
    }

    private fun installHook(classLoader: ClassLoader) {
        if (hooked) return
        hooked = true

        moduleUid = runCatching { moduleApplicationInfo.uid }.getOrElse {
            Log.w(TAG, "获取模块 UID 失败，将退化为包名比对", it)
            -1
        }

        try {
            val nms = Class.forName(
                "com.android.server.notification.NotificationManagerService",
                false,
                classLoader
            )
            val target = nms.getDeclaredMethod("isCallerSystemOrPhone")

            hook(target)
                .setId("xmsf_isCallerSystemOrPhone")
                .intercept { chain ->
                    if (isCallerOurModule()) true else chain.proceed()
                }

            markHookReady(nms.name)
            Log.i(TAG, "已挂载 ${nms.name}#isCallerSystemOrPhone, moduleUid=$moduleUid")
        } catch (t: Throwable) {
            Log.e(TAG, "挂载失败", t)
        }
    }

    /**
     * 判断 Binder 调用者是否为本模块。
     * 用 appId 比对，兼容多用户与工作资料。
     * 注意：UserHandle.getAppId 是 hidden API，这里直接按 AID_USER_OFFSET 取模。
     */
    private fun isCallerOurModule(): Boolean {
        val callingUid = runCatching { Binder.getCallingUid() }.getOrDefault(-1)
        if (callingUid <= 0) return false

        val localUid = moduleUid
        if (localUid > 0) {
            return callingUid % AID_USER_OFFSET == localUid % AID_USER_OFFSET
        }

        return runCatching {
            val pm = Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("getPackageManager")
                .invoke(null) as android.content.pm.PackageManager
            pm.getPackageUid(moduleApplicationInfo.packageName, 0) == callingUid
        }.getOrDefault(false)
    }

    private fun markHookReady(targetClass: String) {
        runCatching {
            getRemotePreferences(ModuleConstants.PREFS_NAME).edit()
                .putBoolean(ModuleConstants.KEY_HOOK_READY, true)
                .putLong(ModuleConstants.KEY_LOADED_AT, System.currentTimeMillis())
                .putString(ModuleConstants.KEY_TARGET_CLASS, targetClass)
                .putString("framework_name", frameworkName)
                .putString("framework_version", frameworkVersion)
                .putInt("api_version", apiVersion)
                .apply()
        }.onFailure { Log.w(TAG, "写入远程偏好失败", it) }
    }
}