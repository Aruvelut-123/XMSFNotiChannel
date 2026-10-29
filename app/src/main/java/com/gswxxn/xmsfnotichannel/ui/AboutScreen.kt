package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gswxxn.xmsfnotichannel.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 的 TextButton 别名，统一弹窗里的三个按钮样式 */
@Composable
private fun TextButtonMiuix(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(text = text, onClick = onClick, modifier = modifier)
}

/** 开源许可条目 */
data class LicenseEntry(
    val project: String,
    val author: String,
    val url: String,
    val license: String,
)

/**
 * 关于页面。
 *
 * @param versionName 当前版本号
 * @param hideIcon 是否隐藏桌面图标
 * @param onHideIconChange 切换隐藏桌面图标
 * @param onBack 返回
 * @param onOpenUrl 打开链接
 * @param onIconClick 点击应用图标（连续 5 次会由 Activity 侧弹出规则设置）
 * @param editing 是否显示规则编辑弹窗
 * @param editingValue 弹窗内正在编辑的文本
 * @param onEditingValueChange 编辑内容变化
 * @param onEditingConfirm 确认保存
 * @param onEditingDismiss 取消
 * @param onEditingReset 恢复默认
 * @param editingSummary 弹窗说明文案
 * @param editingHint 输入框提示
 */
@Composable
fun AboutScreen(
    versionName: String,
    hideIcon: Boolean,
    onHideIconChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onIconClick: () -> Unit,
    editing: Boolean,
    editingValue: String,
    onEditingValueChange: (String) -> Unit,
    onEditingConfirm: () -> Unit,
    onEditingDismiss: () -> Unit,
    onEditingReset: () -> Unit,
    editingSummary: String,
    editingHint: String,
    licenses: List<LicenseEntry>,
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
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 头部：图标 + 名称 + 版本
            item {
                Header(
                    versionName = versionName,
                    onIconClick = onIconClick,
                )
            }

            // 设置
            item { SmallTitle(text = context.getString(R.string.hide_icon)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = context.getString(R.string.hide_icon),
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = hideIcon, onCheckedChange = onHideIconChange)
                    }
                }
            }

            // 开发者
            item { SmallTitle(text = context.getString(R.string.developer)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 6.dp),
                ) {
                    DeveloperRow(
                        resId = R.mipmap.img_developer,
                        name = context.getString(R.string.developer_milu),
                        subtitle = null,
                        onClick = { onOpenUrl("http://www.coolapk.com/u/1189245") },
                    )
                    DeveloperRow(
                        resId = R.mipmap.img_developer_baymax,
                        name = context.getString(R.string.developer_baymax),
                        subtitle = "Android 17 适配 / libxposed 重构",
                        onClick = { onOpenUrl("https://github.com/Aruvelut-123") },
                    )
                }
            }

            // 仓库
            item { SmallTitle(text = context.getString(R.string.open_source_repo)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable { onOpenUrl("https://github.com/Aruvelut-123/XMSFNotiChannel") },
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "GitHub — Aruvelut-123 / XMSFNotiChannel",
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceContainer,
                    )
                }
            }

            // 开源许可
            item { SmallTitle(text = context.getString(R.string.open_source_license)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 6.dp),
                ) {
                    licenses.forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(entry.url) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${entry.project} - ${entry.author}",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceContainer,
                            )
                            Text(
                                text = "${entry.url}\n${entry.license}",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceContainerVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // 规则编辑弹窗
    OverlayDialog(
        show = editing,
        title = context.getString(R.string.searching_name),
        summary = editingSummary,
        onDismissRequest = onEditingDismiss,
    ) {
        Column {
            TextField(
                value = editingValue,
                onValueChange = onEditingValueChange,
                label = editingHint,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButtonMiuix(
                    text = context.getString(R.string.alert_reset),
                    onClick = onEditingReset,
                    modifier = Modifier.weight(1f),
                )
                TextButtonMiuix(
                    text = context.getString(R.string.alert_cancel),
                    onClick = onEditingDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButtonMiuix(
                    text = context.getString(R.string.alert_ok),
                    onClick = onEditingConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 页面头部 */
@Composable
private fun Header(versionName: String, onIconClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val icon = remember {
        runCatching {
            context.getDrawable(R.mipmap.ic_launcher)
                ?.toBitmap(180, 180)
                ?.asImageBitmap()
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(
                painter = BitmapPainter(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onIconClick() },
            )
        } else {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .clickable { onIconClick() }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = context.getString(R.string.app_name),
            fontSize = 15.sp,
            color = colorScheme.onSurfaceContainer,
        )
        Text(
            text = context.getString(R.string.version, versionName),
            fontSize = 13.sp,
            color = colorScheme.onSurfaceContainerVariant,
        )
    }
}

/** 开发者一行 */
@Composable
private fun DeveloperRow(
    resId: Int,
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val avatar: ImageBitmap? = remember(resId) {
        runCatching { context.getDrawable(resId)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = name,
                fontSize = 15.sp,
                color = colorScheme.onSurfaceContainer,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}