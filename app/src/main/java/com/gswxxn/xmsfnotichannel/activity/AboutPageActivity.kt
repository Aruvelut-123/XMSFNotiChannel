package com.gswxxn.xmsfnotichannel.activity

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.activity.MainActivity.Companion.defaultChannelName
import com.gswxxn.xmsfnotichannel.ui.AboutScreen
import com.gswxxn.xmsfnotichannel.ui.AppTheme
import com.gswxxn.xmsfnotichannel.ui.LicenseEntry

/**
 * 关于页面。UI 由 Miuix（Compose）实现。
 *
 * 隐藏功能：连续点击应用图标 5 次打开「通知通道搜索规则」设置。
 */
class AboutPageActivity : BaseActivity() {

    private var hideIcon by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var editingValue by mutableStateOf("")

    /** 连续点击计数与上次点击时间 */
    private var clickCount = 0
    private var lastClickTime = 0L

    override fun onCreateContent() {
        val componentName = ComponentName(this, MainActivity::class.java)
        hideIcon = packageManager.getComponentEnabledSetting(componentName) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val pref = PreferenceManager.getDefaultSharedPreferences(this)

        setContent {
            AppTheme {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    hideIcon = hideIcon,
                    onHideIconChange = { checked ->
                        hideIcon = checked
                        val newState =
                            if (checked) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        packageManager.setComponentEnabledSetting(
                            componentName,
                            newState,
                            PackageManager.DONT_KILL_APP
                        )
                    },
                    onBack = { finish() },
                    onOpenUrl = ::openUrl,
                    onIconClick = ::onIconClick,
                    editing = editing,
                    editingValue = editingValue,
                    onEditingValueChange = { editingValue = it },
                    onEditingConfirm = {
                        // 允许留空：留空表示使用内置关键词表
                        pref.edit().putString("search_name", editingValue.trim()).apply()
                        editing = false
                    },
                    onEditingDismiss = { editing = false },
                    onEditingReset = {
                        pref.edit().putString("search_name", defaultChannelName).apply()
                        editingValue = defaultChannelName
                        editing = false
                    },
                    editingSummary = getString(R.string.searching_name_summary),
                    editingHint = getString(R.string.searching_name_hint),
                    licenses = licenses(),
                )
            }
        }
    }

    /**
     * 连续点击应用图标 5 次打开规则设置。
     *
     * 500ms 内算连续点击，间隔超时则重新计数。
     */
    private fun onIconClick() {
        val now = System.currentTimeMillis()
        clickCount = if (now - lastClickTime < 500) clickCount + 1 else 1
        lastClickTime = now

        if (clickCount < 5) return
        clickCount = 0

        editingValue = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("search_name", defaultChannelName)
            .orEmpty()
        editing = true
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.check_active_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun licenses(): List<LicenseEntry> = listOf(
        LicenseEntry(
            "Miuix",
            "compose-miuix-ui",
            "https://github.com/compose-miuix-ui/miuix",
            "Apache License 2.0"
        ),
        LicenseEntry(
            "YukiHookAPI",
            "HighCapable",
            "https://github.com/HighCapable/YukiHookAPI",
            "Apache License 2.0"
        ),
        LicenseEntry(
            "MIUINativeNotifyIcon",
            "fankes",
            "https://github.com/fankes/MIUINativeNotifyIcon",
            "GNU Affero General Public License v3.0"
        ),
        LicenseEntry(
            "Hide-My-Applist",
            "Dr-TSNG",
            "https://github.com/Dr-TSNG/Hide-My-Applist",
            "GNU Affero General Public License v3.0"
        ),
        LicenseEntry(
            "libxposed API",
            "LSPosed",
            "https://github.com/libxposed/api",
            "Apache License 2.0"
        ),
        LicenseEntry(
            "HiddenApiBypass",
            "LSPosed",
            "https://github.com/LSPosed/AndroidHiddenApiBypass",
            "Apache License 2.0"
        ),
    )
}