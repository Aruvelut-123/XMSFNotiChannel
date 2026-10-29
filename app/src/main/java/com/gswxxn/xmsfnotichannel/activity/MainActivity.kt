package com.gswxxn.xmsfnotichannel.activity

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.ui.AboutUiState
import com.gswxxn.xmsfnotichannel.ui.AppIconCache
import com.gswxxn.xmsfnotichannel.ui.AppTheme
import com.gswxxn.xmsfnotichannel.ui.AppUi
import com.gswxxn.xmsfnotichannel.ui.ChannelUi
import com.gswxxn.xmsfnotichannel.ui.MainScreen
import com.gswxxn.xmsfnotichannel.ui.MainUiState
import com.gswxxn.xmsfnotichannel.ui.SettingsUiState
import com.gswxxn.xmsfnotichannel.ui.toAppUiList
import com.gswxxn.xmsfnotichannel.utils.AppInfoHelper
import com.gswxxn.xmsfnotichannel.utils.AppListPermission
import com.gswxxn.xmsfnotichannel.utils.ChannelMatcher
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus
import com.gswxxn.xmsfnotichannel.utils.NCUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面。UI 由 Miuix（Compose）实现，本类只负责数据驱动。
 *
 * 结构对齐 HyperCeiler：[MainScreen] 内部是「首页 / 设置 / 关于」三页
 * 加悬浮底部导航，设置与关于不再是独立 Activity，
 * 因此状态全部托管在这里，切页不重建页面也不丢状态。
 */
class MainActivity : BaseActivity() {

    companion object {
        /**
         * 默认搜索规则。
         *
         * **空字符串表示使用 [ChannelMatcher] 的内置关键词表**，
         * 覆盖 运营消息 / 营销推广 / 活动 / 推荐 / 商城 / 直播 等一整类通道。
         */
        const val defaultChannelName = ""

        /** 图标解码目标边长（px），40dp @3x 足够清晰又不浪费内存 */
        private const val ICON_SIZE = 120
    }

    private val nc: NCUtils by lazy { NCUtils(this) }

    /** Compose 侧的可变状态 */
    private val uiState = MainUiState()
    private val settingsState = SettingsUiState()
    private val aboutState = AboutUiState()

    private var channelName = defaultChannelName
    private val appInfo: AppInfoHelper by lazy { AppInfoHelper(this) }

    /** 隐藏桌面图标时的目标组件（避免与 Activity.getComponentName 同名） */
    private val targetComponent by lazy { ComponentName(this, MainActivity::class.java) }

    private val pref by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    /** 框架状态变化时刷新顶部状态条 */
    private val statusListener: () -> Unit = {
        runOnUiThread { syncFrameworkState() }
    }

    override fun onCreateContent() {
        uiState.versionName = BuildConfig.VERSION_NAME
        uiState.ruleLabel = currentRuleLabel()
        settingsState.versionName = BuildConfig.VERSION_NAME
        settingsState.ruleLabel = currentRuleLabel()
        aboutState.versionName = BuildConfig.VERSION_NAME
        settingsState.hideIcon = isIconHidden()

        setContent {
            AppTheme {
                MainScreen(
                    ui = uiState,
                    settings = settingsState,
                    about = aboutState,
                    onOpenAboutUrl = ::openUrl,
                    onRecheck = ::recheck,
                    onToggleChannel = ::toggleChannel,
                    onToggleApp = ::toggleApp,
                    onEnableAll = ::enableAll,
                    onDisableAll = ::disableAll,
                    onHideIconChange = ::applyHideIcon,
                    onEditingValueChange = { settingsState.editingValue = it },
                    onEditingStart = {
                        settingsState.editingValue =
                            pref.getString("search_name", defaultChannelName).orEmpty()
                        settingsState.editing = true
                    },
                    onEditingConfirm = {
                        // 允许留空：留空表示使用内置关键词表
                        val value = settingsState.editingValue.trim()
                        pref.edit().putString("search_name", value).apply()
                        channelName = value.ifEmpty { defaultChannelName }
                        val label = currentRuleLabel()
                        uiState.ruleLabel = label
                        settingsState.ruleLabel = label
                        settingsState.editing = false
                        // 规则变了，立刻按新规则重扫一遍
                        scan(fresh = true)
                    },
                    onEditingDismiss = { settingsState.editing = false },
                    onEditingReset = {
                        pref.edit().putString("search_name", defaultChannelName).apply()
                        channelName = defaultChannelName
                        settingsState.editingValue = defaultChannelName
                        val label = currentRuleLabel()
                        uiState.ruleLabel = label
                        settingsState.ruleLabel = label
                        settingsState.editing = false
                        scan(fresh = true)
                    },
                )
            }
        }

        // 需求：打开软件没有数据就自动开始检测，用户不必先点一次按钮
        scan(fresh = true)

        // 关于页的图标与头像都在 IO 线程解码，避免首帧卡顿
        lifecycleScope.launch {
            aboutState.appIcon = withContext(Dispatchers.IO) {
                loadDrawableBitmap(R.mipmap.ic_launcher, 180)
            }
            val milu = withContext(Dispatchers.IO) {
                loadDrawableBitmap(R.mipmap.img_developer, 96)
            }
            val baymax = withContext(Dispatchers.IO) {
                loadDrawableBitmap(R.mipmap.img_developer_baymax, 96)
            }
            aboutState.devAvatars = milu to baymax
        }

        // 启动时先探测一次包可见性。HyperOS 上 QUERY_ALL_PACKAGES 会显示已授予，
        // 但实际只能看到极少量的包，导致检测永远扫不到东西，提前提示用户。
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) { AppListPermission.isGranted(this@MainActivity) }
            settingsState.permissionGranted = granted
            if (!granted) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.hint_no_app_list_permission,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadDrawableBitmap(resId: Int, size: Int): ImageBitmap? = runCatching {
        getDrawable(resId)?.toBitmap(size, size)?.asImageBitmap()
    }.getOrNull()

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.check_active_toast, Toast.LENGTH_SHORT).show()
        }
    }

    /** 当前生效规则的可读描述 */
    private fun currentRuleLabel(): String =
        channelName.ifBlank { ChannelMatcher.defaultDescription() }

    private fun isIconHidden(): Boolean =
        packageManager.getComponentEnabledSetting(targetComponent) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun applyHideIcon(hidden: Boolean) {
        settingsState.hideIcon = hidden
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(
            targetComponent,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun syncFrameworkState() {
        uiState.state = FrameworkStatus.state
        uiState.frameworkDesc = FrameworkStatus.describe()
        settingsState.frameworkDesc = FrameworkStatus.describe()
    }

    /**
     * 扫描通道列表。[fresh] 为 true 时忽略 [AppInfoHelper] 内部缓存。
     *
     * 全程在 IO 线程：读通道 + 解视频图标都在这里完成，
     * 主线程只接收构造好的 [AppUi] 列表，因此滑动不会掉帧。
     */
    private fun scan(fresh: Boolean, forceProbe: Boolean = false) {
        appInfo.pattern = channelName
        uiState.ruleLabel = currentRuleLabel()
        uiState.loading = true

        if (forceProbe) {
            FrameworkStatus.invalidateProbe()
            FrameworkStatus.refresh()
        }

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val list = if (fresh) appInfo.getAppInfoListForNew() else appInfo.getAppInfoList()

                // 同一包名只解一次图标，结果复用到所有通道上。
                // 用 groupBy 一次分组，避免每个包都去 firstOrNull 扫全表（O(n²)）。
                val icons = HashMap<String, ImageBitmap?>()
                list.groupBy { it.packageName }.forEach { (pkg, items) ->
                    icons[pkg] = AppIconCache.load(
                        context = this@MainActivity,
                        packageName = pkg,
                        drawable = items.first().icon,
                        size = ICON_SIZE,
                    )
                }
                list.toAppUiList(icons)
            }

            uiState.apps = apps
            uiState.loading = false
            syncFrameworkState()

            if (apps.isEmpty()) {
                // 空结果优先怀疑包可见性：没开「获取应用列表」时扫描必然是空的
                val noPermission = withContext(Dispatchers.IO) {
                    !AppListPermission.isGranted(this@MainActivity)
                }
                val message = if (noPermission) {
                    getString(R.string.hint_no_app_list_permission)
                } else {
                    getString(R.string.no_channel_found, currentRuleLabel())
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun recheck() {
        AppIconCache.clear()
        scan(fresh = true, forceProbe = uiState.apps != null)
    }

    /**
     * 单个通道开关。
     *
     * 只改 [ChannelUi.importance] 这一个值 → 只有那一个 Switch 重组，
     * 不再像旧实现那样复制整个列表。
     */
    private fun toggleChannel(channel: ChannelUi, enable: Boolean) {
        if (channel.source.ncInfo.importance < 0) return

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (enable) nc.enableSpecificNotification(channel.source)
                else nc.disableSpecificNotification(channel.source)
            }

            if (!success) {
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.toggle_failed,
                        channel.source.appName,
                        channel.displayName,
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            channel.importance = if (enable) 3 else 0
        }
    }

    /** 应用总开关：批量改这个应用下的所有通道 */
    private fun toggleApp(app: AppUi, enable: Boolean) {
        lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) {
                var count = 0
                app.channels.forEach { channel ->
                    val ok = if (enable) {
                        if (channel.importance == 0) {
                            nc.enableSpecificNotification(channel.source).also { if (it) count++ }
                        } else false
                    } else {
                        if (channel.importance > 0) {
                            nc.disableSpecificNotification(channel.source).also { if (it) count++ }
                        } else false
                    }
                    if (ok) {
                        channel.importance = if (enable) 3 else 0
                    }
                }
                count
            }
            if (changed == 0) {
                Toast.makeText(this@MainActivity, R.string.bulk_no_change, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableAll() = bulkChange(enable = true)

    private fun disableAll() = bulkChange(enable = false)

    private fun bulkChange(enable: Boolean) {
        val apps = uiState.apps ?: return
        lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) {
                var count = 0
                apps.forEach { app ->
                    app.channels.forEach { channel ->
                        val ok = if (enable) {
                            if (channel.importance == 0) {
                                nc.enableSpecificNotification(channel.source)
                            } else false
                        } else {
                            if (channel.importance > 0) {
                                nc.disableSpecificNotification(channel.source)
                            } else false
                        }
                        if (ok) {
                            channel.importance = if (enable) 3 else 0
                            count++
                        }
                    }
                }
                count
            }
            val message = if (changed > 0) {
                getString(R.string.bulk_success, changed)
            } else {
                getString(R.string.bulk_no_change)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        channelName = pref.getString("search_name", defaultChannelName)
            .orEmpty()
            .ifEmpty { defaultChannelName }
        val label = currentRuleLabel()
        uiState.ruleLabel = label
        settingsState.ruleLabel = label
        settingsState.hideIcon = isIconHidden()

        FrameworkStatus.addListener(statusListener)
        FrameworkStatus.refresh()
        syncFrameworkState()
    }

    override fun onPause() {
        super.onPause()
        FrameworkStatus.removeListener(statusListener)
    }
}