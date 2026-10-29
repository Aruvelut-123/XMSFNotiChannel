package com.gswxxn.xmsfnotichannel.activity

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.activity.MainActivity.Companion.defaultChannelName
import com.gswxxn.xmsfnotichannel.ui.AppTheme
import com.gswxxn.xmsfnotichannel.ui.SettingsScreen
import com.gswxxn.xmsfnotichannel.utils.AppListPermission
import com.gswxxn.xmsfnotichannel.utils.ChannelMatcher
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页。
 *
 * 原先藏在关于页「连点图标 5 次」里的搜索规则设置已经搬到这里的明面上。
 */
class SettingsActivity : BaseActivity() {

    private val componentName by lazy { ComponentName(this, MainActivity::class.java) }

    private var hideIcon by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var editingValue by mutableStateOf("")

    /** 包可见性状态，进入页面时在 IO 线程探测一次 */
    private var permissionGranted by mutableStateOf(true)

    private var frameworkDesc by mutableStateOf("")
    private var ruleLabel by mutableStateOf("")

    override fun onCreateContent() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)

        hideIcon = packageManager.getComponentEnabledSetting(componentName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        ruleLabel = currentRuleLabel(pref.getString("search_name", defaultChannelName).orEmpty())
        frameworkDesc = FrameworkStatus.describe()

        setContent {
            AppTheme {
                SettingsScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    frameworkDesc = frameworkDesc,
                    ruleLabel = ruleLabel,
                    permissionGranted = permissionGranted,
                    hideIcon = hideIcon,
                    onHideIconChange = ::setHideIcon,
                    onBack = { finish() },
                    onOpenAbout = {
                        startActivity(Intent(this, AboutPageActivity::class.java))
                    },
                    editing = editing,
                    editingValue = editingValue,
                    onEditingValueChange = { editingValue = it },
                    onEditingStart = {
                        editingValue = PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .getString("search_name", defaultChannelName)
                            .orEmpty()
                        editing = true
                    },
                    onEditingConfirm = {
                        // 允许留空：留空表示使用内置关键词表
                        val value = editingValue.trim()
                        pref.edit().putString("search_name", value).apply()
                        ruleLabel = currentRuleLabel(value)
                        editing = false
                    },
                    onEditingDismiss = { editing = false },
                    onEditingReset = {
                        pref.edit().putString("search_name", defaultChannelName).apply()
                        editingValue = defaultChannelName
                        ruleLabel = currentRuleLabel(defaultChannelName)
                        editing = false
                    },
                )
            }
        }

        lifecycleScope.launch {
            permissionGranted = withContext(Dispatchers.IO) {
                AppListPermission.isGranted(this@SettingsActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        frameworkDesc = FrameworkStatus.describe()
        hideIcon = packageManager.getComponentEnabledSetting(componentName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun currentRuleLabel(value: String): String =
        value.ifBlank { ChannelMatcher.defaultDescription() }

    private fun setHideIcon(hidden: Boolean) {
        hideIcon = hidden
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}