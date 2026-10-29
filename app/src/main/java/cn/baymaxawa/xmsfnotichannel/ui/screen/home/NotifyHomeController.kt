package cn.baymaxawa.xmsfnotichannel.ui.screen.home

import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import cn.baymaxawa.xmsfnotichannel.BuildConfig
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.ui.model.AppIconCache
import cn.baymaxawa.xmsfnotichannel.ui.model.AppUi
import cn.baymaxawa.xmsfnotichannel.ui.model.ChannelUi
import cn.baymaxawa.xmsfnotichannel.ui.model.toAppUiList
import cn.baymaxawa.xmsfnotichannel.utils.AppInfoHelper
import cn.baymaxawa.xmsfnotichannel.utils.AppListPermission
import cn.baymaxawa.xmsfnotichannel.utils.ChannelMatcher
import cn.baymaxawa.xmsfnotichannel.utils.FrameworkStatus
import cn.baymaxawa.xmsfnotichannel.utils.NCUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通知通道首页的数据驱动层。
 *
 * 从旧 MainActivity 搬过来的全部业务逻辑集中在这里，
 * Activity 只负责把它和 [NotifyHomeUi] 交给 Compose。
 *
 * 线程约定：通道读写、图标解码全部在 [Dispatchers.IO]；
 * 主线程只接收已经构造好的 [AppUi] 列表与单个 importance 赋值，
 * 因此列表滑动不会因为解码或 Binder 调用掉帧。
 */
class NotifyHomeController(
    private val owner: LifecycleOwner,
    private val ui: NotifyHomeUi,
    private val context: android.content.Context,
    private val ncOverride: NCUtils? = null,
) {

    companion object {
        /** 图标解码目标边长（px），40dp @3x 足够清晰又不浪费内存 */
        private const val ICON_SIZE = 120

        /** 空字符串表示使用 [ChannelMatcher] 的内置关键词表 */
        const val DEFAULT_CHANNEL_NAME = ""
    }

    private val nc: NCUtils by lazy { ncOverride ?: NCUtils(context) }
    private val appInfo: AppInfoHelper by lazy { AppInfoHelper(context) }

    private var channelName: String = DEFAULT_CHANNEL_NAME

    /** 框架状态变化时刷新状态卡 */
    private val statusListener: () -> Unit = {
        owner.lifecycleScope.launch(Dispatchers.Main) { syncFrameworkState() }
    }

    init {
        ui.versionLine = context.getString(R.string.label_module_version, BuildConfig.VERSION_NAME)
        ui.ruleLabel = currentRuleLabel()
        FrameworkStatus.addListener(statusListener)
        syncFrameworkState()
    }

    /** Activity 的 onResume 调用：重读规则并刷新状态 */
    fun onResume() {
        // 与旧工程一致：默认 SharedPreferences（<包名>_preferences）
        val pref = context.getSharedPreferences(
            context.packageName + "_preferences",
            android.content.Context.MODE_PRIVATE,
        )
        channelName = pref.getString("search_name", DEFAULT_CHANNEL_NAME)
            .orEmpty()
            .ifEmpty { DEFAULT_CHANNEL_NAME }
        ui.ruleLabel = currentRuleLabel()
        FrameworkStatus.refresh()
        syncFrameworkState()
    }

    fun onPause() {
        FrameworkStatus.removeListener(statusListener)
    }

    fun onDestroy() {
        FrameworkStatus.removeListener(statusListener)
    }

    /** 把框架状态映射到界面文案 */
    fun syncFrameworkState() {
        val state = FrameworkStatus.state
        ui.state = state.name
        ui.stateLabel = context.getString(
            when (state) {
                FrameworkStatus.State.ACTIVE -> R.string.state_module_active
                FrameworkStatus.State.INACTIVE -> R.string.state_module_inactive
                FrameworkStatus.State.NOT_IN_SCOPE -> R.string.state_module_not_in_scope
                FrameworkStatus.State.RESTART_NEEDED -> R.string.state_module_restart_needed
            }
        )
        ui.frameworkDesc = FrameworkStatus.describe()
    }

    /** 当前生效规则的可读描述 */
    fun currentRuleLabel(): String =
        channelName.ifBlank { ChannelMatcher.defaultDescription() }

    /**
     * 扫描通道列表。
     *
     * @param fresh 为 true 时忽略 [AppInfoHelper] 内部缓存
     * @param forceProbe 为 true 时先清掉能力探测缓存再判定框架状态
     */
    fun scan(fresh: Boolean, forceProbe: Boolean = false) {
        appInfo.pattern = channelName
        ui.ruleLabel = currentRuleLabel()
        ui.loading = true
        if (forceProbe) {
            FrameworkStatus.invalidateProbe()
            FrameworkStatus.refresh()
        }
        owner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val list = if (fresh) appInfo.getAppInfoListForNew() else appInfo.getAppInfoList()
                // 同一包名只解一次图标，结果复用到该应用的所有通道上。
                // groupBy 一次分组，避免每个包都去 firstOrNull 扫全表（O(n²)）。
                val icons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
                list.groupBy { it.packageName }.forEach { (pkg, items) ->
                    icons[pkg] = AppIconCache.load(
                        context = context,
                        packageName = pkg,
                        drawable = items.first().icon,
                        size = ICON_SIZE,
                    )
                }
                list.toAppUiList(icons)
            }
            ui.apps = apps
            ui.loading = false
            syncFrameworkState()
            if (apps.isEmpty()) {
                // 空结果优先怀疑包可见性：HyperOS 上权限显示已授予但实际只能看到极少数包
                val noPermission = withContext(Dispatchers.IO) {
                    !AppListPermission.isGranted(context)
                }
                toast(
                    if (noPermission) context.getString(R.string.hint_no_app_list_permission)
                    else context.getString(R.string.hint_no_channel, currentRuleLabel())
                )
            }
        }
    }

    fun recheck() {
        AppIconCache.clear()
        scan(fresh = true, forceProbe = ui.apps != null)
    }

    /**
     * 单个通道开关。
     *
     * 只改 [ChannelUi.importance] 一个值 → 只有那一个 Switch 重组，
     * 不像旧实现那样复制整个列表。
     */
    fun toggleChannel(channel: ChannelUi, enable: Boolean) {
        if (channel.source.ncInfo.importance < 0) return
        owner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (enable) nc.enableSpecificNotification(channel.source)
                else nc.disableSpecificNotification(channel.source)
            }
            if (!success) {
                toast(
                    context.getString(
                        R.string.msg_toggle_failed,
                        channel.source.appName,
                        channel.displayName,
                    )
                )
                return@launch
            }
            channel.importance = if (enable) 3 else 0
        }
    }

    /** 应用总开关：批量改这个应用下的所有通道 */
    fun toggleApp(app: AppUi, enable: Boolean) {
        owner.lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) { applyBulk(app.channels, enable) }
            if (changed == 0) {
                toast(context.getString(R.string.msg_bulk_no_change))
            }
        }
    }

    /** 全部开启 */
    fun enableAll() = bulkChange(enable = true)

    /** 全部关闭 */
    fun disableAll() = bulkChange(enable = false)

    private fun bulkChange(enable: Boolean) {
        if (ui.apps.isNullOrEmpty()) {
            toast(context.getString(R.string.msg_bulk_no_change))
            return
        }
        owner.lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) {
                var count = 0
                ui.apps.orEmpty().forEach { app ->
                    count += applyBulk(app.channels, enable)
                }
                count
            }
            toast(
                if (changed > 0) context.getString(R.string.msg_bulk_success, changed)
                else context.getString(R.string.msg_bulk_no_change)
            )
        }
    }

    /** 实际写系统；调用方保证在 IO 线程 */
    private fun applyBulk(channels: List<ChannelUi>, enable: Boolean): Int {
        var count = 0
        channels.forEach { channel ->
            val ok = if (enable) {
                if (channel.importance == 0) nc.enableSpecificNotification(channel.source) else false
            } else {
                if (channel.importance > 0) nc.disableSpecificNotification(channel.source) else false
            }
            if (ok) {
                channel.importance = if (enable) 3 else 0
                count++
            }
        }
        return count
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
