package com.gswxxn.xmsfnotichannel.utils

import android.util.Log
import com.gswxxn.xmsfnotichannel.App
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
    fun init() {
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    this@FrameworkStatus.onServiceBind(service)
                }

                override fun onServiceDied(service: XposedService) {
                    this@FrameworkStatus.onServiceDied(service)
                }
            })
        }.onFailure { Log.w(TAG, "注册 XposedService 监听失败", it) }
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
            svc.scope?.contains(ModuleConstants.SCOPE_SYSTEM) == true
        }.getOrDefault(false)

        if (!inScope) {
            state = State.NOT_IN_SCOPE
            notifyChanged()
            return
        }

        val hookReady = runCatching {
            svc.getRemotePreferences(ModuleConstants.PREFS_NAME)
                .getBoolean(ModuleConstants.KEY_HOOK_READY, false)
        }.getOrDefault(false)

        state = if (hookReady) State.ACTIVE else State.RESTART_NEEDED
        notifyChanged()
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