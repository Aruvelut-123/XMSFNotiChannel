package cn.baymaxawa.xmsfnotichannel.ui.screen.home

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
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.ui.model.AppUi
import cn.baymaxawa.xmsfnotichannel.ui.util.BlurredBar
import cn.baymaxawa.xmsfnotichannel.ui.util.blurSource
import cn.baymaxawa.xmsfnotichannel.ui.util.pageScrollModifiers
import cn.baymaxawa.xmsfnotichannel.ui.util.rememberBlurState
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.window.WindowListPopup
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
    val title = stringResource(R.string.tab_features)
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    var menuShow by remember { mutableStateOf(false) }
    // 功能页搜索：按应用名或包名过滤已匹配到的应用列表
    var keyword by remember { mutableStateOf("") }

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
                            // 必须用 WindowListPopup：OverlayListPopup 走 MiuixPopupUtils 的
                            // 全局队列，需要根布局挂 MiuixPopupHost 才会被渲染。本项目没挂，
                            // 所以点三点菜单毫无反应。WindowListPopup 自己开窗口，不依赖宿主。
                            WindowListPopup(
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
            Column(modifier = Modifier.fillMaxSize()) {
                // 搜索框固定在顶栏下方，不随列表滚动。
                // 过滤在 composable 上下文里直接算，几百个字符串本身很便宜，
                // 不需要 remember（放在 LazyListScope 里还会编译不过）。
                TextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = stringResource(R.string.features_search_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = innerPadding.calculateTopPadding(), bottom = 8.dp),
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pageScrollModifiers(
                            showTopAppBar = true,
                            topAppBarScrollBehavior = scrollBehavior,
                        ),
                    contentPadding = PaddingValues(
                        bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
                    ),
                ) {
                    val apps = ui.apps
                    val q = keyword.trim()
                    // 显式声明为可空：apps 为 null 时保持 null，交给下面的三态分支处理。
                    val filtered: List<AppUi>? = when {
                        apps == null -> null
                        q.isEmpty() -> apps
                        else -> apps.filter { app ->
                            app.appName.contains(q, ignoreCase = true) ||
                                app.packageName.contains(q, ignoreCase = true)
                        }
                    }
                    when {
                        ui.loading && apps == null -> item { LoadingHint() }
                        apps == null -> item { EmptyHint(text = stringResource(R.string.hint_start_check)) }
                        apps.isEmpty() -> item {
                            EmptyHint(
                                text = stringResource(R.string.hint_no_channel, ui.ruleLabel)
                            )
                        }

                        filtered.isNullOrEmpty() -> item {
                            EmptyHint(text = stringResource(R.string.features_search_empty))
                        }

                        else -> {
                            // here apps != null 且 filtered 非空
                            val list = filtered ?: apps
                            item {
                                SmallTitle(
                                    text = if (q.isEmpty()) {
                                        stringResource(R.string.section_apps, list.size)
                                    } else {
                                        stringResource(
                                            R.string.section_apps_search,
                                            list.size,
                                            q,
                                        )
                                    }
                                )
                            }
                            items(items = list, key = { it.packageName }) { app ->
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