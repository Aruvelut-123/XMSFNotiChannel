package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.utils.AppInfoHelper
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

/**
 * 主页面 UI 状态。
 *
 * 把所有可变状态集中到这里，Activity 只负责驱动数据。
 */
class MainUiState {
    var state by mutableStateOf(FrameworkStatus.State.INACTIVE)
    var frameworkDesc by mutableStateOf("")
    var versionName by mutableStateOf("")
    var ruleLabel by mutableStateOf("")

    /** null 表示还没开始检测（显示开始按钮） */
    var items by mutableStateOf<List<AppInfoHelper.MyAppInfo>?>(null)
    var loading by mutableStateOf(false)
}

/**
 * 主页面。
 *
 * @param ui 界面状态
 * @param onOpenAbout 打开关于页
 * @param onToggle 点击某一项开关（参数：目标项、是否要开启）
 * @param onRecheck 重新检测
 * @param onEnableAll 全部开启
 * @param onDisableAll 全部关闭
 */
@Composable
fun MainScreen(
    ui: MainUiState,
    onOpenAbout: () -> Unit,
    onToggle: (AppInfoHelper.MyAppInfo, Boolean) -> Unit,
    onRecheck: () -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringOf(R.string.app_name),
                subtitle = ui.frameworkDesc,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StatusCard(ui = ui, onOpenAbout = onOpenAbout)

            val items = ui.items
            if (items == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TextButton(
                        text = stringOf(R.string.start_check),
                        onClick = onRecheck,
                    )
                }
            } else if (ui.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringOf(R.string.loading),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringOf(R.string.no_channel_found, ui.ruleLabel),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                ChannelList(items = items, onToggle = onToggle)
            }

            if (items != null && !ui.loading) {
                BottomActions(
                    onRecheck = onRecheck,
                    onEnableAll = onEnableAll,
                    onDisableAll = onDisableAll,
                )
            }
        }
    }
}

/** 顶部状态卡：模块激活状态、版本、当前规则 */
@Composable
private fun StatusCard(ui: MainUiState, onOpenAbout: () -> Unit) {
    val colorScheme = MiuixTheme.colorScheme
    val (title, tint) = when (ui.state) {
        FrameworkStatus.State.ACTIVE -> stringOf(R.string.module_is_active) to colorScheme.primary
        FrameworkStatus.State.RESTART_NEEDED -> stringOf(R.string.try_reboot) to colorScheme.error
        FrameworkStatus.State.NOT_IN_SCOPE -> stringOf(R.string.module_not_in_scope) to colorScheme.error
        FrameworkStatus.State.INACTIVE -> stringOf(R.string.module_is_not_active) to colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = tint,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringOf(R.string.module_version, ui.versionName),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                if (ui.frameworkDesc.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringOf(R.string.xposed_framework_version, ui.frameworkDesc),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            IconButton(onClick = onOpenAbout) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = stringOf(R.string.about),
                    tint = colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 通道列表 */
@Composable
private fun ChannelList(
    items: List<AppInfoHelper.MyAppInfo>,
    onToggle: (AppInfoHelper.MyAppInfo, Boolean) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val showChannelName = items.any { it.ncInfo.channelName.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            val enabled = item.ncInfo.importance > 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(item.packageName) {
                        runCatching { item.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                    if (icon != null) {
                        Image(
                            painter = BitmapPainter(icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.surfaceContainerHigh)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceContainer,
                        )
                        if (showChannelName && item.ncInfo.channelName.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.ncInfo.channelName,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceContainerVariant,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle(item, it) },
                    )
                }
            }
        }
    }
}

/** 底部三个操作按钮 */
@Composable
private fun BottomActions(
    onRecheck: () -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(text = stringOf(R.string.recheck), onClick = onRecheck, modifier = Modifier.weight(1f))
        TextButton(text = stringOf(R.string.enable_all), onClick = onEnableAll, modifier = Modifier.weight(1f))
        TextButton(text = stringOf(R.string.disable_all), onClick = onDisableAll, modifier = Modifier.weight(1f))
    }
}

/**
 * 取字符串资源。
 *
 * Compose 里没有 Context 时从 `App.instance` 取（Application 在 onCreate 已初始化）。
 */
@Composable
private fun stringOf(id: Int, vararg args: Any): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return context.getString(id, *args)
}