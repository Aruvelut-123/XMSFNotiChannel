package cn.baymaxawa.xmsfnotichannel.ui.screen.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import cn.baymaxawa.xmsfnotichannel.ui.icons.CheckCircleOutlineIcon
import cn.baymaxawa.xmsfnotichannel.ui.icons.ErrorOutlineIcon
import cn.baymaxawa.xmsfnotichannel.ui.icons.RemoveCircleOutlineIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.utils.AppInfoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cn.baymaxawa.xmsfnotichannel.ui.screen.scope.ScopeListActivity
import cn.baymaxawa.xmsfnotichannel.ui.util.BlurredBar
import cn.baymaxawa.xmsfnotichannel.ui.util.blurSource
import cn.baymaxawa.xmsfnotichannel.ui.util.isInDarkTheme
import cn.baymaxawa.xmsfnotichannel.ui.util.pageScrollModifiers
import cn.baymaxawa.xmsfnotichannel.ui.util.rememberBlurState
import cn.baymaxawa.xmsfnotichannel.ui.util.shouldShowSplitPane
import cn.baymaxawa.xmsfnotichannel.util.SystemVersionDetector
import cn.baymaxawa.xmsfnotichannel.xposed.XposedServiceManager
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 模板主页。所有数据均为占位符，接入业务时替换为真实数据即可。
 */
@Composable
fun HomePageView(
    isBlurEnabled: Boolean = true,
    refreshKey: Int = 0,
    extraBottomPadding: Dp = 0.dp,
    onOpenExamples: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_home)

    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    // 每次切回主页时刷新作用域，保证授权变化能及时反映；
    // root 检测也移到首屏之后，冷启动不再先拉起 su。
    LaunchedEffect(refreshKey) {
        XposedServiceManager.refreshScope()
        XposedServiceManager.checkRootIfNeeded()
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                item {
                    val context = LocalContext.current
                    val isWideScreen = shouldShowSplitPane()
                    val darkTheme = isInDarkTheme()
                    val dynamicColor = MiuixTheme.isDynamicColor
                    val activated = XposedServiceManager.isActivated
                    val rootChecked = XposedServiceManager.rootChecked
                    val rootAvailable = XposedServiceManager.isRootAvailable
                    // 模块已激活但无 Root：重启等功能不可用，单独提示。
                    val rootMissing = activated && rootChecked && !rootAvailable
                    val scopeCount = XposedServiceManager.scope.size
                    // 概览统计：扫描真实通道数据（IO 线程，不阻塞首帧）
                    var statApps by remember { mutableIntStateOf(0) }
                    var statChannels by remember { mutableIntStateOf(0) }
                    var statEnabled by remember { mutableIntStateOf(0) }
                    // 未激活时不做匹配扫描：hook 没生效，扫出来的数字既不可信也白耗 Binder
                    LaunchedEffect(refreshKey, activated) {
                        if (!activated) {
                            statApps = 0
                            statChannels = 0
                            statEnabled = 0
                            return@LaunchedEffect
                        }
                        val stats = withContext(Dispatchers.IO) {
                            runCatching {
                                val list = AppInfoHelper(context).getAppInfoListForNew()
                                Triple(
                                    list.map { it.packageName }.distinct().size,
                                    list.size,
                                    list.count { it.ncInfo.importance > 0 },
                                )
                            }.getOrNull()
                        }
                        if (stats != null) {
                            statApps = stats.first
                            statChannels = stats.second
                            statEnabled = stats.third
                        }
                    }

                    val statusColor = when {
                        !activated -> when {
                            dynamicColor -> MiuixTheme.colorScheme.errorContainer
                            darkTheme -> Color(0xFF3D1C1C)
                            else -> Color(0xFFFDE8E8)
                        }
                        rootMissing -> when {
                            dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
                            darkTheme -> Color(0xFF3D3520)
                            else -> Color(0xFFFDF6E3)
                        }
                        else -> when {
                            dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
                            darkTheme -> Color(0xFF1A3825)
                            else -> Color(0xFFDFFAE4)
                        }
                    }
                    val iconTint = when {
                        !activated -> if (dynamicColor) MiuixTheme.colorScheme.error.copy(alpha = 0.8f) else Color(0xFFDC3545)
                        rootMissing -> if (dynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xFFE0A800)
                        else -> if (dynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
                    }
                    val statusSummary = stringResource(
                        when {
                            !activated -> R.string.module_status_summary_inactive
                            rootMissing -> R.string.module_status_summary_no_root
                            else -> R.string.module_status_summary_activated
                        }
                    )
                    val statusDetail = if (rootMissing) {
                        stringResource(R.string.module_status_root_hint)
                    } else {
                        ""
                    }
                    // 未激活 = 叉号；已激活但无 Root = 圈中横线；正常 = 对号。
                    val statusIcon: ImageVector = when {
                        !activated -> ErrorOutlineIcon
                        rootMissing -> RemoveCircleOutlineIcon
                        else -> CheckCircleOutlineIcon
                    }

                    val openScopeList = {
                        // 进入二级页面前先刷新作用域，避免页面内刷新打断打开动画。
                        XposedServiceManager.refreshScope()
                        context.startActivity(Intent(context, ScopeListActivity::class.java))
                    }

                    val cardsModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp)
                        .height(IntrinsicSize.Min)

                    if (isWideScreen) {
                        // 横屏：状态卡 + 三项统计横向四等分。
                        Row(
                            modifier = cardsModifier,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusCard(
                                titleText = stringResource(R.string.module_status),
                                versionText = statusSummary,
                                statusText = statusDetail,
                                imageVector = statusIcon,
                                iconTint = iconTint,
                                statusColor = statusColor,
                                onClick = { XposedServiceManager.checkRoot() },
                                compact = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            CountCard(
                                title = stringResource(R.string.home_stat_apps),
                                count = statApps.toString(),
                                onClick = onOpenExamples,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            CountCard(
                                title = stringResource(R.string.home_stat_channels),
                                count = statChannels.toString(),
                                onClick = onOpenExamples,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            CountCard(
                                title = stringResource(R.string.home_stat_enabled),
                                count = statEnabled.toString(),
                                onClick = onOpenExamples,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    } else {
                        Row(
                            modifier = cardsModifier,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusCard(
                                titleText = stringResource(R.string.module_status),
                                versionText = statusSummary,
                                statusText = statusDetail,
                                imageVector = statusIcon,
                                iconTint = iconTint,
                                statusColor = statusColor,
                                onClick = { XposedServiceManager.checkRoot() },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                CountCard(
                                    title = stringResource(R.string.home_stat_apps),
                                    count = statApps.toString(),
                                    onClick = onOpenExamples,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                                Spacer(Modifier.height(12.dp))
                                CountCard(
                                    title = stringResource(R.string.home_stat_channels),
                                    count = statChannels.toString(),
                                    onClick = onOpenExamples,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CountCard(
                                title = stringResource(R.string.home_stat_enabled),
                                count = statEnabled.toString(),
                                onClick = onOpenExamples,
                                modifier = Modifier.weight(1f),
                            )
                            CountCard(
                                title = stringResource(R.string.home_scope),
                                count = scopeCount.toString(),
                                onClick = openScopeList,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    val context = LocalContext.current
                    val deviceModel = remember {
                        SystemVersionDetector.getMarketName().ifEmpty { "Unknown" }
                    }
                    val deviceName = remember {
                        try {
                            Settings.Global.getString(
                                context.contentResolver,
                                Settings.Global.DEVICE_NAME
                            )?.takeIf { it.isNotBlank() } ?: deviceModel
                        } catch (_: Exception) {
                            deviceModel
                        }
                    }
                    val hyperOsVersion = remember { SystemVersionDetector.getHyperOsVersion() }
                    val androidVersion = remember { SystemVersionDetector.getAndroidVersion() }
                    val appVersion = remember {
                        try {
                            context.packageManager
                                .getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                        } catch (_: Exception) {
                            "1.0"
                        }
                    }

                    SmallTitle(
                        text = stringResource(R.string.home_device_info)
                    )
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                    ) {
                        Column {
                            BasicComponent(
                                title = stringResource(R.string.home_device_name),
                                summary = deviceName,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_device_model),
                                summary = deviceModel,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_hyperos_version),
                                summary = hyperOsVersion,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_android_version),
                                summary = androidVersion,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_app_version),
                                summary = appVersion
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    titleText: String,
    versionText: String,
    statusText: String,
    imageVector: ImageVector,
    iconTint: Color,
    statusColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val iconSize = if (compact) 100.dp else 170.dp
    val iconOffsetX = if (compact) 22.dp else 38.dp
    val iconOffsetY = if (compact) 26.dp else 45.dp
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = statusColor),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(iconOffsetX, iconOffsetY),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = imageVector,
                    tint = iconTint,
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 16.dp)
            ) {
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = versionText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                MiuixText(
                    modifier = Modifier.fillMaxWidth(),
                    text = statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CountCard(
    title: String,
    count: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        showIndication = true,
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            MiuixText(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            MiuixText(
                modifier = Modifier.fillMaxWidth(),
                text = count,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
