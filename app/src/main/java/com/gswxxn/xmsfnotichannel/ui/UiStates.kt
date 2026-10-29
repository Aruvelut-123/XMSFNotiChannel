package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 设置页的 UI 状态。
 *
 * 改成三页结构后，设置不再是独立 Activity，
 * 因此这些状态由 [com.gswxxn.xmsfnotichannel.activity.MainActivity] 统一托管，
 * 页面只负责渲染与回调。
 */
class SettingsUiState {
    /** 版本名，用于「关于」条目摘要 */
    var versionName by mutableStateOf("")

    /** 框架状态描述（Activated by ... / 未激活） */
    var frameworkDesc by mutableStateOf("")

    /** 当前生效的搜索规则可读描述 */
    var ruleLabel by mutableStateOf("")

    /** 是否已获得「获取应用列表」能力 */
    var permissionGranted by mutableStateOf(true)

    /** 是否已隐藏桌面图标 */
    var hideIcon by mutableStateOf(false)

    /** 搜索规则编辑弹窗是否展开 */
    var editing by mutableStateOf(false)

    /** 编辑弹窗里的临时输入值 */
    var editingValue by mutableStateOf("")
}

/**
 * 关于页的 UI 状态。
 *
 * 图标与头像都在 IO 线程解码后写回，主线程只做绘制。
 */
class AboutUiState {
    var versionName by mutableStateOf("")

    /** 应用图标 */
    var appIcon by mutableStateOf<ImageBitmap?>(null)

    /** 两位开发者的头像（milu, baymax） */
    var devAvatars by mutableStateOf<Pair<ImageBitmap?, ImageBitmap?>>(null to null)
}
