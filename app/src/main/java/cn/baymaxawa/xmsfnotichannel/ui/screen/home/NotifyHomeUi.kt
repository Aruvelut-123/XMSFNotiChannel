package cn.baymaxawa.xmsfnotichannel.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.baymaxawa.xmsfnotichannel.ui.model.AppUi

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
    /** 框架状态（原始枚举名），与 [cn.baymaxawa.xmsfnotichannel.utils.FrameworkStatus.State] 对应 */
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

/**
 * 功能页状态的进程级存放点。
 *
 * 为什么不能只用 `remember { NotifyHomeUi() }`：
 * 主页用的是 HorizontalPager，`beyondViewportPageCount = 1` 只会保留相邻一页，
 * 从功能页切到设置/关于时整页会被移出组合，remember 里的扫描结果随之销毁，
 * 再切回来就变成空列表，得重新扫一遍（几百次 Binder 调用，要等好几秒）。
 *
 * 因此把状态提到进程级：只要 App 没被杀，检测结果就一直留着。
 * 需要强制重扫时走「重新检测」按钮（会显式调 scan(fresh = true)），
 * 不受这里缓存的影响。
 */
object NotifyHomeUiHolder {
    val instance: NotifyHomeUi by lazy { NotifyHomeUi() }
}