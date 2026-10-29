package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局 Miuix 主题。
 *
 * 用 `MonetSystem` 让配色跟随系统（HyperOS 壁纸取色），
 * 取不到动态色时 Miuix 会自动回落到内置的浅色 / 深色调色板。
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    MiuixTheme(controller = controller, content = content)
}