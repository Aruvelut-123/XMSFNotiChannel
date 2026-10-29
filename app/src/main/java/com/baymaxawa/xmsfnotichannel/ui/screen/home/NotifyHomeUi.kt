package com.baymaxawa.xmsfnotichannel.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.baymaxawa.xmsfnotichannel.ui.model.AppUi

/** 框架状态常量的可读名，避免首页直接依赖 utils 包 */
const val FRAMEWORK_ACTIVE = "ACTIVE"

/**
 * 通知通道首页的 UI 状态。
 *
 * 可变状态集中在这里，Activity 只负责驱动数据。
 * [apps] 里的 [AppUi] / [ChannelUi] 自带可变状态，
 * 单个通道开关**不需要**重建整个列表。
 */
class NotifyHomeUi {
    /** 框架状态（原始枚举名），与 [com.baymaxawa.xmsfnotichannel.utils.FrameworkStatus.State] 对应 */
    var state by mutableStateOf("INACTIVE")

    /** 状态卡上的大标题，例如「模块已激活」「模块未激活」 */
    var stateLabel by mutableStateOf("")

    /** 版本行，例如「模块版本 v1.0.0」 */
    var versionLine by mutableStateOf("")

    /** 框架描述，例如「LSPosed 2.2.1 API 102」 */
    var frameworkDesc by mutableStateOf("")

    /** 当前生效的搜索规则描述 */
    var ruleLabel by mutableStateOf("")

    /** null 表示还没开始检测 */
    var apps by mutableStateOf<List<AppUi>?>(null)

    var loading by mutableStateOf(false)
}