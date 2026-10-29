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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 开源许可条目 */
data class LicenseEntry(
    val project: String,
    val author: String,
    val url: String,
    val license: String,
)

/**
 * 关于页，布局参考 HyperCeiler：
 * 顶部居中显示图标 + 名称 + 版本，下面按「开源仓库 / 开发者 / 开放源代码许可」分卡片。
 *
 * 注意：搜索规则设置已搬到独立的设置页，本页不再有隐藏的连点入口。
 */
@Composable
fun AboutScreen(
    versionName: String,
    appIcon: ImageBitmap?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    licenses: List<LicenseEntry>,
    developers: List<DeveloperEntry>,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.about),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = context.getString(R.string.alert_cancel),
                            tint = colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Header(versionName = versionName, icon = appIcon)
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
                        text = context.getString(R.string.version, versionName),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 开发者条目 */
data class DeveloperEntry(
    val name: String,
    val subtitle: String,
    val url: String,
    val avatar: ImageBitmap?,
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