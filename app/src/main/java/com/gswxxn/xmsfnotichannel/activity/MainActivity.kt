package com.gswxxn.xmsfnotichannel.activity

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.databinding.ActivityMainBinding
import com.gswxxn.xmsfnotichannel.databinding.AdapterConfigBinding
import com.gswxxn.xmsfnotichannel.utils.AppInfoHelper
import com.gswxxn.xmsfnotichannel.utils.AppProbe
import com.gswxxn.xmsfnotichannel.utils.ChannelMatcher
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus
import com.gswxxn.xmsfnotichannel.utils.NCUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity(), CoroutineScope by MainScope() {

    companion object {
        /**
         * 默认搜索规则。
         *
         * **空字符串表示使用 [com.gswxxn.xmsfnotichannel.utils.ChannelMatcher] 的内置关键词表**，
         * 覆盖 运营消息 / 营销推广 / 活动 / 推荐 / 商城 / 直播 等一整类通道。
         * 旧版本这里是单个词「运营消息」，只能命中一小部分，已废弃。
         */
        const val defaultChannelName = ""
    }

    private lateinit var binding: ActivityMainBinding
    private var channelName = defaultChannelName
    private var appInfoFilter = listOf<AppInfoHelper.MyAppInfo>()
    private var onRefreshList: (() -> Unit)? = null

    private val nc: NCUtils by lazy { NCUtils(this) }

    /** 框架状态变化时刷新顶部状态条 */
    private val statusListener: () -> Unit = { runOnUiThread { refreshState() } }

    override fun onCreate() {
        val appInfo = AppInfoHelper(this)

        binding = ActivityMainBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.titleAboutPage.setOnClickListener {
            startActivity(Intent(this, AboutPageActivity::class.java))
        }

        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)

        // 开始检测
        binding.actionButton.setOnClickListener {
            val state = FrameworkStatus.state
            AppProbe.init(this)
            AppProbe.mark("button:click", "state=$state")
            // 状态条只作为提示，不再阻断操作：
            // 远程偏好在本机可能读不到（hook 实际已生效但状态显示「需重启」），
            // 若在这里 return，用户会永远无法验证功能是否可用。
            if (state != FrameworkStatus.State.ACTIVE) {
                AppProbe.mark("button:stateNotActiveProceed", "state=$state")
                Toast.makeText(this, describeStateHint(state), Toast.LENGTH_SHORT).show()
            }
            showView(false, binding.actionButton)
            showView(true, binding.configListLoadingView)

            appInfo.pattern = channelName
            launch {
                appInfoFilter = withContext(Dispatchers.IO) { appInfo.getAppInfoList() }
                showView(false, binding.configListLoadingView)
                showView(true, binding.configListView, binding.afterActions)
                onRefreshList?.invoke()

                if (appInfoFilter.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.no_channel_found, currentRuleLabel()),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // 一键开启
        binding.enableAll.setOnClickListener {
            launch {
                var changed = 0
                withContext(Dispatchers.IO) {
                    appInfoFilter.forEach {
                        if (it.ncInfo.importance == 0) {
                            if (nc.enableSpecificNotification(it)) changed++
                            it.ncInfo.importance = 3
                        }
                    }
                }
                onRefreshList?.invoke()
                reportBulkResult(changed)
            }
        }

        // 一键关闭
        binding.disableAll.setOnClickListener {
            launch {
                var changed = 0
                withContext(Dispatchers.IO) {
                    appInfoFilter.forEach {
                        if (it.ncInfo.importance > 0) {
                            if (nc.disableSpecificNotification(it)) changed++
                            it.ncInfo.importance = 0
                        }
                    }
                }
                onRefreshList?.invoke()
                reportBulkResult(changed)
            }
        }

        // 重新检测
        binding.recheck.setOnClickListener {
            appInfo.pattern = channelName
            // 强制重新做一次能力探测，不吃缓存
            FrameworkStatus.invalidateProbe()
            FrameworkStatus.refresh()
            launch {
                showView(false, binding.configListView, binding.afterActions)
                showView(true, binding.configListLoadingView)
                appInfoFilter = withContext(Dispatchers.IO) { appInfo.getAppInfoListForNew() }
                delay(300)
                showView(true, binding.configListView, binding.afterActions)
                showView(false, binding.configListLoadingView)
                onRefreshList?.invoke()
            }
        }

        setupList()
    }

    /** 当前生效规则的可读描述，用于「未找到通道」提示 */
    private fun currentRuleLabel(): String =
        channelName.ifBlank { ChannelMatcher.defaultDescription() }

    private fun reportBulkResult(changed: Int) {
        val message = if (changed > 0) {
            getString(R.string.bulk_success, changed)
        } else {
            getString(R.string.bulk_no_change)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupList() {
        binding.configListView.apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = appInfoFilter.size
                override fun getItem(position: Int) = appInfoFilter[position]
                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val holder: AdapterConfigBinding
                    val view: View
                    if (convertView == null) {
                        holder = AdapterConfigBinding.inflate(LayoutInflater.from(context))
                        view = holder.root
                        view.tag = holder
                    } else {
                        view = convertView
                        holder = convertView.tag as AdapterConfigBinding
                    }

                    val item = getItem(position)
                    val info = item.ncInfo

                    val openHint: String
                    val closeHint: String
                    if (channelName.isBlank()) {
                        holder.adpChannelInfo.visibility = View.GONE
                        openHint = getString(R.string.enabled_operation_notification)
                        closeHint = getString(R.string.disabled_operation_notification)
                    } else {
                        holder.adpChannelInfo.visibility = View.VISIBLE
                        openHint = getString(R.string.enabled_notification_other)
                        closeHint = getString(R.string.disabled_notification_other)
                    }

                    holder.adpAppIcon.setImageDrawable(item.icon)
                    holder.adpAppName.text = item.appName
                    holder.adpChannelGroup.text = info.channelGroupName.ifEmpty {
                        getString(R.string.no_group)
                    }
                    holder.adpChannelName.text = info.channelName.ifEmpty { info.channelId }
                    holder.adpAppStatus.apply {
                        text = if (info.importance == 0) closeHint else openHint
                        setTextColor(
                            getColor(
                                when {
                                    info.importance < 0 -> R.color.colorTextGray
                                    info.importance == 0 -> R.color.colorTextRed
                                    else -> R.color.green
                                }
                            )
                        )
                    }

                    holder.adapterLayout.setOnClickListener { toggleChannel(item, holder, openHint, closeHint) }

                    return view
                }
            }.apply { onRefreshList = { notifyDataSetChanged() } }
        }
    }

    /**
     * 单个通道开关。修改失败时保持原状态并提示，避免界面与系统不一致。
     */
    private fun toggleChannel(
        item: AppInfoHelper.MyAppInfo,
        holder: AdapterConfigBinding,
        openHint: String,
        closeHint: String
    ) {
        if (item.ncInfo.importance < 0) {
            AppProbe.mark("toggle:skipNegative", "${item.packageName} imp=${item.ncInfo.importance}")
            return
        }

        val enable = item.ncInfo.importance == 0
        AppProbe.mark(
            "toggle:click", "${item.packageName}/${item.ncInfo.channelId} imp=${item.ncInfo.importance} enable=$enable"
        )
        launch {
            val success = withContext(Dispatchers.IO) {
                if (enable) nc.enableSpecificNotification(item)
                else nc.disableSpecificNotification(item)
            }
            AppProbe.mark("toggle:result", "${item.packageName}/${item.ncInfo.channelId} success=$success")

            if (!success) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toggle_failed, item.appName, item.ncInfo.channelName),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            item.ncInfo.importance = if (enable) 3 else 0
            holder.adpAppStatus.apply {
                text = if (enable) openHint else closeHint
                setTextColor(getColor(if (enable) R.color.green else R.color.colorTextRed))
            }
        }
    }

    private fun describeStateHint(state: FrameworkStatus.State): String = getString(
        when (state) {
            FrameworkStatus.State.INACTIVE -> R.string.hint_not_active
            FrameworkStatus.State.NOT_IN_SCOPE -> R.string.hint_not_in_scope
            FrameworkStatus.State.RESTART_NEEDED -> R.string.hint_restart_needed
            FrameworkStatus.State.ACTIVE -> R.string.hint_ready
        }
    )

    private fun refreshState() {
        val state = FrameworkStatus.state

        val (bgRes, iconRes, textRes, colorRes) = when (state) {
            FrameworkStatus.State.ACTIVE ->
                StatusStyle(R.drawable.bg_green_round, R.drawable.ic_success, R.string.module_is_active, R.color.green)
            FrameworkStatus.State.RESTART_NEEDED ->
                StatusStyle(R.drawable.bg_yellow_round, R.drawable.ic_warn, R.string.try_reboot, R.color.yellow)
            FrameworkStatus.State.NOT_IN_SCOPE ->
                StatusStyle(R.drawable.bg_dark_round, R.drawable.ic_warn, R.string.module_not_in_scope, R.color.gray)
            FrameworkStatus.State.INACTIVE ->
                StatusStyle(R.drawable.bg_dark_round, R.drawable.ic_warn, R.string.module_is_not_active, R.color.gray)
        }

        binding.mainStatus.setBackgroundResource(bgRes)
        binding.mainImgStatus.setImageResource(iconRes)
        binding.mainTextStatus.text = getString(textRes)
        // Android 15 起 window.statusBarColor 已失效，改由 BaseActivity 的 insets scrim 着色
        setStatusBarTint(getColor(colorRes))

        val description = FrameworkStatus.describe()
        val visible = description.isNotEmpty()
        showView(visible, binding.mainTextApiWay)
        if (visible) {
            binding.mainTextApiWay.text = getString(R.string.xposed_framework_version, description)
        }
    }

    private data class StatusStyle(
        val background: Int,
        val icon: Int,
        val text: Int,
        val color: Int
    )

    override fun onResume() {
        super.onResume()
        channelName = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("search_name", defaultChannelName)
            .orEmpty()
            .ifEmpty { defaultChannelName }

        FrameworkStatus.addListener(statusListener)
        FrameworkStatus.refresh()
        refreshState()
    }

    override fun onPause() {
        super.onPause()
        FrameworkStatus.removeListener(statusListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}