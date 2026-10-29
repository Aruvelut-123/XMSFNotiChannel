package cn.baymaxawa.xmsfnotichannel.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.baymaxawa.xmsfnotichannel.ui.model.AppUi

/**
 * 首页宿主：把通知通道首页与通道详情页装成一个可切换的整体。
 *
 * 详情页不做成独立 Activity，而是本页内的一个覆盖层——
 * 这样悬浮底栏、模糊背景、返回手势全部保持同一套宿主结构，
 * 也和 HyperCeiler 的「首页 → 应用详情」体验一致。
 *
 * @param refreshKey 每次切换标签页时自增，用于重新拉一次数据
 */
@Composable
fun NotifyHostPageView(
    isBlurEnabled: Boolean = true,
    refreshKey: Int = 0,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 状态用进程级单例而不是 remember：
    // Pager 只保留相邻一页，切到设置/关于时本页会离开组合，
    // remember 里的扫描结果会被销毁，切回来就空了（见 NotifyHomeUiHolder 注释）。
    val ui = NotifyHomeUiHolder.instance
    // controller 仍需跟随 composition 重建（它持有 lifecycleScope 与监听器），
    // 但 ui 是共享的，重建时列表数据还在，不会重新扫描。
    val controller = remember(lifecycleOwner, context) {
        NotifyHomeController(owner = lifecycleOwner, ui = ui, context = context)
    }

    var openedApp by remember { mutableStateOf<AppUi?>(null) }

    // 首次进入自动检测一次，但只在模块已激活时进行：
    // 未激活时 hook 没生效，扫出来的通道也改不动，白跑一遍 Binder 而已。
    LaunchedEffect(ui.state) {
        if (ui.apps == null && ui.state == FRAMEWORK_ACTIVE) {
            controller.scan(fresh = true)
        }
    }

    // refreshKey 变化（切回本页）时静默刷新一次状态，不重建列表
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) controller.syncFrameworkState()
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.onDestroy()
        }
    }

    val app = openedApp
    if (app == null) {
        NotifyHomePageView(
            ui = ui,
            isBlurEnabled = isBlurEnabled,
            extraBottomPadding = extraBottomPadding,
            onOpenApp = { openedApp = it },
            onRecheck = { controller.recheck() },
            onEnableAll = { controller.enableAll() },
            onDisableAll = { controller.disableAll() },
            onToggleApp = { target, enable -> controller.toggleApp(target, enable) },
        )
    } else {
        // 详情页打开时，返回键先关闭详情而不是退出应用
        BackHandler(enabled = true) { openedApp = null }
        ChannelDetailPageView(
            app = app,
            isBlurEnabled = isBlurEnabled,
            hitBottomPadding = extraBottomPadding,
            onBack = { openedApp = null },
            onToggleApp = { target, enable -> controller.toggleApp(target, enable) },
            onToggleChannel = { channel, enable -> controller.toggleChannel(channel, enable) },
        )
    }
}
