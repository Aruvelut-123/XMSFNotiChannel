package cn.baymaxawa.xmsfnotichannel.utils

import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 替代原 YukiHookAPI.Status / dataChannel 的状态查询。
 * 数据来源：libxposed 服务（XposedService）+ 系统进程写入的远程偏好。
 */
object FrameworkStatus {

    private const val TAG = "FrameworkStatus"

    /** 能力探测结果缓存时长：避免每次 onResume 都打一次 Binder */
    private const val PROBE_CACHE_MS = 15_000L

    /** 单次能力探测超时：Binder 卡住时不能把界面拖死 */
    private const val PROBE_TIMEOUT_MS = 1_500L

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

    /** 最近一次能力探测结果（时间戳 -> 是否生效） */
    @Volatile
    private var cachedProbe: Pair<Long, Boolean>? = null

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

        // 主判据：真的跨进程读一次别人的通知通道。
        // 这是唯一能证明「服务端校验被 hook 放行」的方式，
        // 远程偏好和状态文件在本机都不可用（见 ModuleConstants 里的说明）。
        val hookEffective = if (hookReadyByPrefs) true else probeByCapability()

        AppProbe.mark(
            "FrameworkStatus:refresh",
            "inScope=$inScope prefs=$hookReadyByPrefs probe=$hookEffective " +
                "name=$frameworkName ver=$frameworkVersion api=$apiVersion"
        )

        state = if (hookEffective) State.ACTIVE else State.RESTART_NEEDED
        notifyChanged()
    }

    /**
     * 用一次真实的跨进程通道读取来验证 hook 是否生效。
     *
     * 之所以要放到后台线程并加超时：这是 Binder 调用，
     * 在 UI 线程做可能触发 StrictMode 或在 system_server 卡顿时拖慢界面。
     * 探测结果短时缓存，避免每次 onResume 都打一次 Binder。
     */
    private fun probeByCapability(): Boolean {
        val now = System.currentTimeMillis()
        cachedProbe?.let { (at, value) ->
            if (now - at < PROBE_CACHE_MS) return value
        }

        val ctx = appContext ?: return false
        val value = runCatching {
            val task = java.util.concurrent.FutureTask { NCUtils(ctx).probeHookEffective() }
            val thread = Thread(task, "xmsf-hook-probe").apply { isDaemon = true }
            thread.start()
            task.get(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrDefault(false)

        cachedProbe = now to value
        return value
    }

    /** 清掉探测缓存，下一次 refresh 会重新做一次真实的跨进程读取 */
    fun invalidateProbe() {
        cachedProbe = null
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