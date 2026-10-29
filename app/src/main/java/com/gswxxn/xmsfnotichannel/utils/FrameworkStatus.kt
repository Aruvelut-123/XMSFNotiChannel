package com.gswxxn.xmsfnotichannel.utils

import android.util.Log
import com.gswxxn.xmsfnotichannel.App
import com.gswxxn.xmsfnotichannel.utils.AppProbe
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 替代原 YukiHookAPI.Status / dataChannel 的状态查询。
 * 数据来源：libxposed 服务（XposedService）+ 系统进程写入的远程偏好。
 */
object FrameworkStatus {

    private const val TAG = "FrameworkStatus"

    enum class State {
        /** 未拿到框架服务（模块未激活 / 框架未运行） */
        INACTIVE,
        /** 框架已连接，但作用域未勾选 system framework */
        NOT_IN_SCOPE,
        /** 作用域正常，但 system_server 尚未挂上 hook，需重启 */
        RESTART_NEEDED,
        /** 完全就绪 */
        ACTIVE
    }

    @Volatile
    private var service: XposedService? = null

    /** Application context，用于读应用私有目录下的状态文件 */
    @Volatile
    private var appContext: android.content.Context? = null

    @Volatile
    var state: State = State.INACTIVE
        private set

    @Volatile
    var frameworkName: String = ""
        private set

    @Volatile
    var frameworkVersion: String = ""
        private set

    @Volatile
    var apiVersion: Int = 0
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** 在 Application#onCreate 调用一次即可 */
    fun init(context: android.content.Context? = null) {
        if (context != null) {
            appContext = context.applicationContext ?: context
            AppProbe.init(context)
        }
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    this@FrameworkStatus.onServiceBind(service)
                }

                override fun onServiceDied(service: XposedService) {
                    this@FrameworkStatus.onServiceDied(service)
                }
            })
            AppProbe.mark("FrameworkStatus:listenerRegistered")
        }.onFailure {
            Log.w(TAG, "注册 XposedService 监听失败", it)
            AppProbe.mark("FrameworkStatus:registerFail", "${it::class.java.name}: ${it.message}")
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun onServiceBind(service: XposedService) {
        this.service = service
        runCatching {
            frameworkName = service.frameworkName.orEmpty()
            frameworkVersion = service.frameworkVersion.orEmpty()
            apiVersion = service.apiVersion
        }.onFailure { Log.w(TAG, "读取框架信息失败", it) }
        refresh()
    }

    private fun onServiceDied(service: XposedService) {
        if (this.service === service) this.service = null
        refresh()
    }

    val isServiceBound: Boolean
        get() = service != null

    /**
     * 重新计算状态。跨进程调用可能抛异常，全部降级处理。
     */
    fun refresh() {
        val svc = service
        if (svc == null) {
            state = State.INACTIVE
            notifyChanged()
            return
        }

        val inScope = runCatching {
            val scopes = svc.scope
            scopes != null && (
                scopes.contains(ModuleConstants.SCOPE_SYSTEM) ||
                    scopes.contains(ModuleConstants.SCOPE_ANDROID)
                )
        }.getOrDefault(false)

        if (!inScope) {
            state = State.NOT_IN_SCOPE
            notifyChanged()
            return
        }

        val hookReadyByPrefs = runCatching {
            svc.getRemotePreferences(ModuleConstants.PREFS_NAME)
                .getBoolean(ModuleConstants.KEY_HOOK_READY, false)
        }.getOrDefault(false)

        // 降级 1：读 system_server 写进本应用私有目录的状态文件。
        // 远程偏好在本机实测退化为 system_server 私有 SharedPreferences，永远读不到，
        // 因此以文件为准。App 读自己私有目录不需要任何特权。
        val hookReadyByFile = readLocalStatusFile()

        // 降级 2：/data/system/ 下的状态文件（普通 App 无权读，仅当设备给过权限时有效）
        val hookReadyBySystemFile = runCatching {
            val f = java.io.File(ModuleConstants.STATUS_FILE_PATH)
            f.exists() && f.readText().contains("${ModuleConstants.KEY_HOOK_READY}=true")
        }.getOrDefault(false)

        // 降级 3：/data/local/tmp 下的状态文件（实测唯一 App 真能读到的位置）
        val hookReadyByTmpFile = runCatching {
            val f = java.io.File(ModuleConstants.STATUS_FILE_TMP)
            f.exists() && f.readText().contains("${ModuleConstants.KEY_HOOK_READY}=true")
        }.getOrDefault(false)

        val hookReady =
            hookReadyByPrefs || hookReadyByFile || hookReadyBySystemFile || hookReadyByTmpFile

        AppProbe.mark(
            "FrameworkStatus:refresh",
            "inScope=$inScope prefs=$hookReadyByPrefs file=$hookReadyByFile " +
                "sysFile=$hookReadyBySystemFile tmpFile=$hookReadyByTmpFile " +
                "name=$frameworkName ver=$frameworkVersion api=$apiVersion"
        )

        state = if (hookReady) State.ACTIVE else State.RESTART_NEEDED
        notifyChanged()
    }

    /**
     * 读本应用私有目录里的状态文件。
     * 内容格式为 `key=value` 每行一条，只需判断 hook_ready。
     */
    private fun readLocalStatusFile(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            val f = java.io.File(ctx.filesDir, ModuleConstants.STATUS_FILE_NAME)
            f.exists() && f.readText().lineSequence().any {
                it.trim() == "${ModuleConstants.KEY_HOOK_READY}=true"
            }
        }.getOrDefault(false)
    }

    private fun notifyChanged() {
        listeners.forEach { runCatching { it() } }
    }

    /** 供界面显示，例如 "LSPosed 2.2.1 API 102" */
    fun describe(): String {
        if (service == null) return ""
        val name = frameworkName.ifEmpty { "Xposed" }
        return "$name $frameworkVersion API $apiVersion"
    }
}