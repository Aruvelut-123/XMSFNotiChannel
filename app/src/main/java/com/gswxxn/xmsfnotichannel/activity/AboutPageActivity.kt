package com.gswxxn.xmsfnotichannel.activity

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.gswxxn.xmsfnotichannel.BuildConfig
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.ui.AboutScreen
import com.gswxxn.xmsfnotichannel.ui.AppTheme
import com.gswxxn.xmsfnotichannel.ui.DeveloperEntry
import com.gswxxn.xmsfnotichannel.ui.LicenseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关于页面。UI 由 Miuix（Compose）实现，布局参考 HyperCeiler。
 */
class AboutPageActivity : BaseActivity() {

    private var appIcon by mutableStateOf<ImageBitmap?>(null)

    override fun onCreateContent() {
        setContent {
            AppTheme {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    appIcon = appIcon,
                    onBack = { finish() },
                    onOpenUrl = ::openUrl,
                    licenses = licenses(),
                    developers = developers(),
                )
            }
        }

        // 头像和图标都在 IO 线程解码，避免首帧卡顿
        lifecycleScope.launch {
            val icon = withContext(Dispatchers.IO) { loadDrawableBitmap(R.mipmap.ic_launcher, 180) }
            appIcon = icon
            val milu = withContext(Dispatchers.IO) { loadDrawableBitmap(R.mipmap.img_developer, 96) }
            val baymax = withContext(Dispatchers.IO) { loadDrawableBitmap(R.mipmap.img_developer_baymax, 96) }
            devAvatars = milu to baymax
        }
    }

    private var devAvatars by mutableStateOf<Pair<ImageBitmap?, ImageBitmap?>>(null to null)

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

    private fun developers(): List<DeveloperEntry> = listOf(
        DeveloperEntry(
            name = getString(R.string.developer_milu),
            subtitle = "",
            url = "http://www.coolapk.com/u/1189245",
            avatar = devAvatars.first,
        ),
        DeveloperEntry(
            name = getString(R.string.developer_baymax),
            subtitle = "Android 17 适配 / libxposed 重构",
            url = "https://github.com/Aruvelut-123",
            avatar = devAvatars.second,
        ),
    )

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