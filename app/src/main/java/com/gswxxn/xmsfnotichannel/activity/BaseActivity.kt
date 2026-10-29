package com.gswxxn.xmsfnotichannel.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils

/**
 * 所有页面的基类。
 *
 * 界面已迁移到 Compose + Miuix：
 * - 主题、配色、状态栏图标明暗全部交给 Miuix 的 Scaffold / TopAppBar 处理；
 * - 系统栏留白由 Miuix Scaffold 的 `contentWindowInsets` 负责，
 *   这里只开启 edge-to-edge，不再手工铺 scrim、不再手工改 padding。
 *
 * 继承 [ComponentActivity] 是为了拿到 `setContent`、`lifecycleScope`
 * 以及 Compose 需要的 ViewTreeLifecycleOwner / SavedStateRegistryOwner。
 */
abstract class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        // Android 15+ 强制 edge-to-edge；这里显式开启，让下方留白完全交给 Miuix 处理
        enableEdgeToEdge()

        onCreateContent()
    }

    /**
     * 子类在这里构建界面。
     *
     * 之所以不叫 `onCreate()`：那样会和 `onCreate(savedInstanceState: Bundle?)`
     * 构成两个同名重载，子类只重写带参数的版本时编译器会认为无参版本未实现。
     */
    abstract fun onCreateContent()

    /**
     * 设置状态栏图标明暗。
     *
     * Compose 页面本身不需要它（Miuix 会按主题自动处理），
     * 保留是为了子类在特殊场景下能强制指定。
     */
    protected fun setLightStatusBarIcons(light: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** 按给定底色亮度自动决定图标明暗 */
    protected fun setStatusBarTint(colorInt: Int) {
        setLightStatusBarIcons(ColorUtils.calculateLuminance(colorInt) > 0.5)
    }
}