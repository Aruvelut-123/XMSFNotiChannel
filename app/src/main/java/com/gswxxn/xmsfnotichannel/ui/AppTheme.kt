package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局 Miuix 主题。
 *
 * 用 `MonetSystem` 让配色跟随系统（HyperOS 壁纸取色），
 * 取不到动态色时 Miuix 会自动回落到内置的浅色 / 深色调色板。
 *
 * ## 为什么关掉 squircle
 *
 * Miuix 的 `Card` / `Button` / `IconButton` 等组件默认走
 * `Modifier.squircleSurface(...)`，其实现是：
 *
 * ```
 * .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
 * .drawWithCache { onDrawWithContent { drawSquircleFast(...) } }
 * ```
 *
 * 也就是**每一个卡片都会单独开一个离屏 GraphicsLayer**，再在每帧绘制时
 * 用 RuntimeShader 算超椭圆 SDF 并做 `DstIn` 遮罩。列表里每行一张 Card，
 * 滚动时就是「N 个离屏纹理 + N 个 RuntimeShader 每帧求值」，
 * 低端 GPU / 高分辨率屏上直接表现为持续掉帧（不是首帧卡，是一直流畅不了）。
 *
 * `LocalSquircleEnabled = false` 会让整个 squircle 模块回落到
 * `Modifier.clip(RoundedCornerShape(...)) + background()`——
 * 前者是普通的硬件裁剪，没有离屏层、没有着色器，滚动立刻恢复顺滑。
 *
 * 视觉上唯一差别是圆角从「连续曲率超椭圆」变成「标准圆弧」，
 * 在 16dp 圆角下几乎看不出来。想恢复原样，把这里改回 `true` 即可。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    CompositionLocalProvider(LocalSquircleEnabled provides false) {
        MiuixTheme(controller = controller, content = content)
    }
}