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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gswxxn.xmsfnotichannel.R
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主页面 UI 状态。
 *
 * 可变状态全部集中在这里，Activity 只负责驱动数据。
 * 注意：[apps] 里的 [AppUi] / [ChannelUi] 自带可变状态，
 * 单个通道开关**不需要**重建整个列表。
 */
class MainUiState {
    var state by mutableStateOf(FrameworkStatus.State.INACTIVE)
    var frameworkDesc by mutableStateOf("")
    var versionName by mutableStateOf("")
    var ruleLabel by mutableStateOf("")

    /** null 表示还没开始检测 */
    var apps by mutableStateOf<List<AppUi>?>(null)
    var loading by mutableStateOf(false)
}

/**
 * 主页面：应用分组列表 + 应用详情页。
 *
 * 结构参考 HyperCeiler：
 * - 首页按应用分组，一行一个应用，右侧是这个应用的总开关；
 * - 点进应用才看到每个通道的名字和独立开关；
 * - 「全部开启 / 全部关闭」收在右上角三点菜单里；
 * - 设置、关于各有独立页面。
 */
@Composable
fun MainScreen(
    ui: MainUiState,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onRecheck: () -> Unit,
    onToggleChannel: (ChannelUi, Boolean) -> Unit,
    onToggleApp: (AppUi, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
) {
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    val apps = ui.apps
    val current = apps?.firstOrNull { it.packageName == selectedPackage }

    if (current != null) {
        AppDetailScreen(
            app = current,
            onBack = { selectedPackage = null },
            onToggleChannel = onToggleChannel,
            onToggleApp = onToggleApp,
        )
        return
    }

    HomeScreen(
        ui = ui,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
        onRecheck = onRecheck,
        onToggleApp = onToggleApp,
        onEnableAll = onEnableAll,
        onDisableAll = onDisableAll,
        onOpenApp = { selectedPackage = it.packageName },
    )
}

/** 首页 */
@Composable
private fun HomeScreen(
    ui: MainUiState,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onRecheck: () -> Unit,
    onToggleApp: (AppUi, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onOpenApp: (AppUi) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    var menuShow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.app_name),
                subtitle = ui.frameworkDesc,
                actions = {
                    // 三点菜单：全部开启 / 全部关闭
                    Box {
                        IconButton(onClick = { menuShow = true }) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = context.getString(R.string.more_menu),
                                tint = colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        OverlayListPopup(
                            show = menuShow,
                            alignment = PopupPositionProvider.Align.End,
                            onDismissRequest = { menuShow = false },
                            onDismissFinished = { menuShow = false },
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = context.getString(R.string.enable_all),
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 0,
                                    onSelectedIndexChange = {
                                        menuShow = false
                                        onEnableAll()
                                    },
                                )
                                DropdownImpl(
                                    text = context.getString(R.string.disable_all),
                                    optionSize = 2,
                                    isSelected = false,
                                    index = 1,
                                    onSelectedIndexChange = {
                                        menuShow = false
                                        onDisableAll()
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = context.getString(R.string.settings),
                            tint = colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StatusCard(
                ui = ui,
                onOpenAbout = onOpenAbout,
                onRecheck = onRecheck,
            )

            val apps = ui.apps
            when {
                ui.loading -> CenterHint {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = context.getString(R.string.loading),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }

                apps == null -> CenterHint {
                    TextButton(
                        text = context.getString(R.string.start_check),
                        onClick = onRecheck,
                    )
                }

                apps.isEmpty() -> CenterHint {
                    Text(
                        text = context.getString(R.string.no_channel_found, ui.ruleLabel),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> AppGroupList(
                    apps = apps,
                    onOpenApp = onOpenApp,
                    onToggleApp = onToggleApp,
                )
            }
        }
    }
}

/** 应用详情页：展示该应用下每个检测到的通道 */
@Composable
private fun AppDetailScreen(
    app: AppUi,
    onBack: () -> Unit,
    onToggleChannel: (ChannelUi, Boolean) -> Unit,
    onToggleApp: (AppUi, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val allOn = app.allEnabled

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = app.appName,
                subtitle = context.getString(
                    R.string.app_enabled_summary,
                    app.enabledCount,
                    app.totalCount,
                ),
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
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.toggle_all_for_app),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceContainer,
                            )
                            Text(
                                text = context.getString(
                                    R.string.app_enabled_summary,
                                    app.enabledCount,
                                    app.totalCount,
                                ),
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceContainerVariant,
                            )
                        }
                        Switch(
                            checked = allOn,
                            onCheckedChange = { onToggleApp(app, it) },
                        )
                    }
                }
            }

            item { SmallTitle(text = context.getString(R.string.channel_list)) }

            items(items = app.channels, key = { it.channelId + "|" + it.displayName }) { channel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.displayName,
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = channel.enabled,
                            onCheckedChange = { onToggleChannel(channel, it) },
                        )
                    }
                }
            }
        }
    }
}

/** 应用分组列表 */
@Composable
private fun AppGroupList(
    apps: List<AppUi>,
    onOpenApp: (AppUi) -> Unit,
    onToggleApp: (AppUi, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = apps, key = { it.packageName }) { app ->
            // 开关状态直接由通道的 importance 派生：点击后 Activity 会改
            // ChannelUi.importance，这里读到的就是最新值，无需本地副本。
            val switchOn = app.allEnabled

            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp),
                onClick = { onOpenApp(app) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceContainer,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = context.getString(
                                R.string.app_enabled_summary,
                                app.enabledCount,
                                app.totalCount,
                            ),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceContainerVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = switchOn,
                        onCheckedChange = { onToggleApp(app, it) },
                    )
                }
            }
        }
    }
}

/** 应用图标（图标已在 IO 线程解码好并缓存在 [AppUi.icon] 里） */
@Composable
private fun AppIcon(app: AppUi) {
    val colorScheme = MiuixTheme.colorScheme
    val icon = app.icon
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
}

/** 顶部状态卡：模块激活状态、版本、当前规则 */
@Composable
private fun StatusCard(
    ui: MainUiState,
    onOpenAbout: () -> Unit,
    onRecheck: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val (title, tint) = when (ui.state) {
        FrameworkStatus.State.ACTIVE -> context.getString(R.string.module_is_active) to colorScheme.primary
        FrameworkStatus.State.RESTART_NEEDED -> context.getString(R.string.try_reboot) to colorScheme.error
        FrameworkStatus.State.NOT_IN_SCOPE -> context.getString(R.string.module_not_in_scope) to colorScheme.error
        FrameworkStatus.State.INACTIVE -> context.getString(R.string.module_is_not_active) to colorScheme.error
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
                    text = context.getString(R.string.module_version, ui.versionName),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = context.getString(R.string.current_rule, ui.ruleLabel),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            TextButton(
                text = context.getString(R.string.recheck),
                onClick = onRecheck,
            )
            IconButton(onClick = onOpenAbout) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = context.getString(R.string.about),
                    tint = colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 居中提示块 */
@Composable
private fun CenterHint(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}
