package com.gswxxn.xmsfnotichannel.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.ui.AppTheme
import com.gswxxn.xmsfnotichannel.ui.MainScreen
import com.gswxxn.xmsfnotichannel.ui.MainUiState
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
    }

    private val nc: NCUtils by lazy { NCUtils(this) }

    /** Compose 侧的可变状态 */
    private val uiState = MainUiState()

    private var channelName = defaultChannelName
    private val appInfo: AppInfoHelper by lazy { AppInfoHelper(this) }

    /** 框架状态变化时刷新顶部状态条 */
    private val statusListener: () -> Unit = {
        runOnUiThread { syncFrameworkState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        uiState.versionName = BuildConfig.VERSION_NAME
        uiState.ruleLabel = currentRuleLabel()

        setContent {
            AppTheme {
                MainScreen(
                    ui = uiState,
                    onOpenAbout = { startActivity(Intent(this, AboutPageActivity::class.java)) },
                    onToggle = ::toggleChannel,
                    onRecheck = ::recheck,
                    onEnableAll = ::enableAll,
                    onDisableAll = ::disableAll,
                )
            }
        }

        // 启动时先探测一次包可见性。HyperOS 上 QUERY_ALL_PACKAGES 会显示已授予，
        // 但实际只能看到极少量的包，导致「开始检测」永远扫不到东西，提前提示用户。
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) { AppListPermission.isGranted(this@MainActivity) }
            if (!granted) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.hint_no_app_list_permission,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** 当前生效规则的可读描述 */
    private fun currentRuleLabel(): String =
        channelName.ifBlank { ChannelMatcher.defaultDescription() }

    private fun syncFrameworkState() {
        uiState.state = FrameworkStatus.state
        uiState.frameworkDesc = FrameworkStatus.describe()
    }

    /**
     * 扫描通道列表。[fresh] 为 true 时忽略 [AppInfoHelper] 内部缓存。
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
            val list = withContext(Dispatchers.IO) {
                if (fresh) appInfo.getAppInfoListForNew() else appInfo.getAppInfoList()
            }
            uiState.items = list
            uiState.loading = false
            syncFrameworkState()

            if (list.isEmpty()) {
                // 空结果优先怀疑包可见性：没开「获取应用列表」时扫描必然是空的
                val noPermission = !AppListPermission.isGranted(this@MainActivity)
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
        scan(fresh = true, forceProbe = uiState.items != null)
    }

    /**
     * 单个通道开关。修改失败时保持原状态，避免界面与系统不一致。
     */
    private fun toggleChannel(item: AppInfoHelper.MyAppInfo, enable: Boolean) {
        if (item.ncInfo.importance < 0) return

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (enable) nc.enableSpecificNotification(item)
                else nc.disableSpecificNotification(item)
            }

            if (!success) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toggle_failed, item.appName, item.ncInfo.channelName),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 触发重组：替换列表引用，让 Compose 感知到数据变化
            item.ncInfo.importance = if (enable) 3 else 0
            uiState.items = uiState.items?.toList()
        }
    }

    private fun enableAll() = bulkChange(enable = true)

    private fun disableAll() = bulkChange(enable = false)

    private fun bulkChange(enable: Boolean) {
        val current = uiState.items ?: return
        lifecycleScope.launch {
            var changed = 0
            withContext(Dispatchers.IO) {
                current.forEach {
                    if (enable) {
                        if (it.ncInfo.importance == 0) {
                            if (nc.enableSpecificNotification(it)) changed++
                            it.ncInfo.importance = 3
                        }
                    } else {
                        if (it.ncInfo.importance > 0) {
                            if (nc.disableSpecificNotification(it)) changed++
                            it.ncInfo.importance = 0
                        }
                    }
                }
            }
            uiState.items = current.toList()
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
        channelName = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString("search_name", defaultChannelName)
            .orEmpty()
            .ifEmpty { defaultChannelName }
        uiState.ruleLabel = currentRuleLabel()

        FrameworkStatus.addListener(statusListener)
        FrameworkStatus.refresh()
        syncFrameworkState()
    }

    override fun onPause() {
        super.onPause()
        FrameworkStatus.removeListener(statusListener)
    }
}