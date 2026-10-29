package com.baymaxawa.xmsfnotichannel.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baymaxawa.xmsfnotichannel.R
import com.baymaxawa.xmsfnotichannel.ui.model.AppUi
import com.baymaxawa.xmsfnotichannel.ui.util.BlurredBar
import com.baymaxawa.xmsfnotichannel.ui.util.blurSource
import com.baymaxawa.xmsfnotichannel.ui.util.pageScrollModifiers
import com.baymaxawa.xmsfnotichannel.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 通知通道首页。
 *
 * 结构对齐 HyperCeiler：顶部是模块状态卡，下面是按应用分组的结果列表。
 * 点一条应用进入通道详情，右上角三点菜单做「全部开启 / 全部关闭」。
 */
@Composable
fun NotifyHomePageView(
    ui: NotifyHomeUi,
    isBlurEnabled: Boolean = true,
    extraBottomPadding: Dp = 0.dp,
    onOpenApp: (AppUi) -> Unit = {},
    onRecheck: () -> Unit = {},
    onEnableAll: () -> Unit = {},
    onDisableAll: () -> Unit = {},
    onToggleApp: (AppUi, Boolean) -> Unit = { _, _ -> },
) {
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_home)
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    var menuShow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onRecheck) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.action_recheck),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Box {
                            IconButton(onClick = { menuShow = true }) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                        text = stringResource(R.string.action_enable_all),
                                        optionSize = 2,
                                        isSelected = false,
                                        index = 0,
                                        onSelectedIndexChange = {
                                            menuShow = false
                                            onEnableAll()
                                        },
                                    )
                                    DropdownImpl(
                                        text = stringResource(R.string.action_disable_all),
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
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
                ),
            ) {
                item {
                    StatusCard(
                        ui = ui,
                        onRecheck = onRecheck,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                    )
                }

                val apps = ui.apps
                when {
                    ui.loading && apps == null -> item { LoadingHint() }
                    apps == null -> item { EmptyHint(text = stringResource(R.string.hint_start_check)) }
                    apps.isEmpty() -> item {
                        EmptyHint(
                            text = stringResource(R.string.hint_no_channel, ui.ruleLabel)
                        )
                    }

                    else -> {
                        item {
                            SmallTitle(
                                text = stringResource(R.string.section_apps, apps.size)
                            )
                        }
                        items(items = apps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                onClick = { onOpenApp(app) },
                                onToggle = { onToggleApp(app, it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 上面这张状态卡展示模块激活状态、版本与当前搜索规则。 */
@Composable
private fun StatusCard(
    ui: NotifyHomeUi,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = ui.state == FRAMEWORK_ACTIVE
    val tint = if (active) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.error
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
    ) {
        BasicComponent(
            title = ui.stateLabel,
            summary = ui.versionLine,
            titleColor = tint,
            onClick = onRecheck,
        )
        BasicComponent(
            title = stringResource(R.string.label_current_rule),
            summary = ui.ruleLabel,
        )
        BasicComponent(
            title = stringResource(R.string.label_framework),
            summary = ui.frameworkDesc.ifEmpty {
                stringResource(R.string.hint_not_active)
            },
        )
    }
}

/** 一条应用：图标 + 名称 + 已开启数 + 总开关。 */
@Composable
private fun AppRow(
    app: AppUi,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    text = stringResource(
                        R.string.label_app_enabled_summary,
                        app.enabledCount,
                        app.totalCount,
                    ),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = app.allEnabled, onCheckedChange = onToggle)
        }
    }
}

/** 应用图标（已在 IO 线程解码并缓存在 [AppUi.icon] 里）。 */
@Composable
private fun AppIcon(app: AppUi) {
    val icon = app.icon
    if (icon != null) {
        Image(
            painter = BitmapPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
private fun LoadingHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        MiuixText(
            text = stringResource(R.string.hint_loading),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiuixText(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}