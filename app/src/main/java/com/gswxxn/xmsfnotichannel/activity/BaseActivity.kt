package com.gswxxn.xmsfnotichannel.activity

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.gswxxn.xmsfnotichannel.R

/**
 * 所有页面的基类。
 *
 * Android 15（API 35）起系统强制 edge-to-edge：窗口铺满整屏，系统栏不再为内容留出空间，
 * `Window#setStatusBarColor` 也随之失效。若仍按旧写法，标题会直接顶到状态栏下面，
 * 被时间、信号等图标压住。
 *
 * 这里改为自行处理 WindowInsets：
 * 1. 把内容包进一层 [FrameLayout]，内容整体让出状态栏 / 导航栏 / 刘海区域，彻底避免重叠；
 * 2. 在状态栏位置铺一层高度等于状态栏的 scrim，用来还原“状态栏跟随主题色”的观感；
 * 3. 依据 scrim 颜色亮度切换状态栏图标明暗，保证任何时候文字都清晰。
 */
abstract class BaseActivity : Activity() {

    private var statusBarScrim: View? = null

    /** 状态栏区域的当前底色，默认与页面背景一致 */
    @ColorInt
    private var statusBarTint: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        statusBarTint = getColor(R.color.colorThemeBackground)
        // 显式声明不依赖系统栏自动留白，实际留白由下面的 insets 逻辑负责
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarAppearance(statusBarTint)

        onCreate()
    }

    abstract fun onCreate()

    override fun setContentView(view: View) {
        val container = FrameLayout(this).apply {
            // 让状态栏 / 导航栏外露的区域与页面同色
            setBackgroundColor(getColor(R.color.colorThemeBackground))
        }
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val scrim = View(this).apply { setBackgroundColor(statusBarTint) }
        container.addView(
            scrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        )

        super.setContentView(container)
        statusBarScrim = scrim

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // 让出系统栏区域，标题不会再被状态栏文字盖住
            target.updatePadding(top = bars.top, bottom = bars.bottom)
            scrim.updateLayoutParams { height = bars.top }
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * 设置状态栏区域底色，替代 Android 15 起已失效的 `window.statusBarColor`。
     *
     * @param color 希望铺在状态栏下方的颜色
     */
    protected fun setStatusBarTint(@ColorInt color: Int) {
        statusBarTint = color
        statusBarScrim?.setBackgroundColor(color)
        applySystemBarAppearance(color)
    }

    /** 按底色亮度决定系统栏图标用深色还是浅色 */
    private fun applySystemBarAppearance(@ColorInt color: Int) {
        val darkIcons = ColorUtils.calculateLuminance(color) > 0.5
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = true
        }
    }

    open fun showView(isShow: Boolean = true, vararg views: View?) {
        for (element in views) {
            element?.visibility = if (isShow) View.VISIBLE else View.GONE
        }
    }
}