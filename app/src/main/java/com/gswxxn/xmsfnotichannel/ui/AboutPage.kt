package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gswxxn.xmsfnotichannel.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 开源许可条目 */
data class LicenseEntry(
    val project: String,
    val author: String,
    val url: String,
    val license: String,
)

/** 开发者条目 */
data class DeveloperEntry(
    val name: String,
    val subtitle: String,
    val url: String,
    val avatar: ImageBitmap?,
)

/**
 * 关于页（三页结构里的第三页）。
 *
 * 与旧版 [AboutScreen] 的区别：
 * - 不再自带 `Scaffold` 与返回按钮，顶栏由 [MainScreen] 提供；
 * - 整页铺在 [AboutBackdrop] 的渐变光斑背景上（对齐 HyperCeiler
 *   关于页「彩光缓慢游动」的观感，但用 Canvas 径向渐变实现，
 *   不上 RuntimeShader，避免上一版的离屏层掉帧）。
 */
@Composable
fun AboutPage(
    about: AboutUiState,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    val licenses = remember { defaultLicenses() }
    val developers = listOf(
        DeveloperEntry(
            name = context.getString(R.string.developer_milu),
            subtitle = "",
            url = "http://www.coolapk.com/u/1189245",
            avatar = about.devAvatars.first,
        ),
        DeveloperEntry(
            name = context.getString(R.string.developer_baymax),
            subtitle = "Android 17 适配 / libxposed 重构",
            url = "https://github.com/Aruvelut-123",
            avatar = about.devAvatars.second,
        ),
    )

    AboutBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Header(versionName = about.versionName, icon = about.appIcon)
            }

            // 开源仓库
            item { SmallTitle(text = context.getString(R.string.open_source_repo)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    BasicComponent(
                        title = "GitHub",
                        summary = "Aruvelut-123 / XMSFNotiChannel",
                        onClick = { onOpenUrl("https://github.com/Aruvelut-123/XMSFNotiChannel") },
                    )
                    BasicComponent(
                        title = context.getString(R.string.follow_me),
                        summary = context.getString(R.string.star_project),
                        onClick = { onOpenUrl("https://github.com/Aruvelut-123/XMSFNotiChannel") },
                    )
                }
            }

            // 开发者
            item { SmallTitle(text = context.getString(R.string.developer)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    developers.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(dev.url) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(dev.avatar)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = dev.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceContainer,
                                )
                                if (dev.subtitle.isNotEmpty()) {
                                    Text(
                                        text = dev.subtitle,
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceContainerVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 开放源代码许可
            item { SmallTitle(text = context.getString(R.string.open_source_license)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    licenses.forEach { entry ->
                        BasicComponent(
                            title = entry.project,
                            summary = "${entry.author} · ${entry.license}",
                            onClick = { onOpenUrl(entry.url) },
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = context.getString(R.string.version, about.versionName),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 许可列表是常量，用 remember 避免每次重组都重新构造 */
private fun defaultLicenses(): List<LicenseEntry> = listOf(
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

/** 页面头部：图标 + 名称 + 版本 */
@Composable
private fun Header(versionName: String, icon: ImageBitmap?) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(
                painter = BitmapPainter(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colorScheme.surfaceContainerHigh)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = context.getString(R.string.app_name),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = context.getString(R.string.version, versionName),
            fontSize = 13.sp,
            color = colorScheme.onSurfaceContainerVariant,
        )
    }
}

/** 头像 */
@Composable
private fun Avatar(avatar: ImageBitmap?) {
    val colorScheme = MiuixTheme.colorScheme
    if (avatar != null) {
        Image(
            painter = BitmapPainter(avatar),
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHigh)
        )
    }
}