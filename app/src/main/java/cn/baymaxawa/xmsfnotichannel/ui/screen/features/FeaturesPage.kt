package cn.baymaxawa.xmsfnotichannel.ui.screen.features

import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.prefs.OptionSpec
import cn.baymaxawa.xmsfnotichannel.prefs.OptionType

/**
 * 功能页的配置项注册。
 *
 * 本模块的功能不在「开关型 hook 配置」这个抽象里，真正的功能界面是
 * [cn.baymaxawa.xmsfnotichannel.ui.screen.home.NotifyHostPageView]
 * （已直接装进 MainActivity 的功能页）。
 *
 * 这里只保留「通道搜索关键词」一项，因为它需要走模板的配置存储、
 * 导入导出与全局搜索，方便用户在设置里改、在功能页里搜。
 */
internal fun featureSpecs(): List<OptionSpec> = listOf(
    OptionSpec(
        key = "search_name",
        type = OptionType.TEXT,
        titleRes = R.string.label_current_rule,
        summaryRes = R.string.home_channels_entry_summary,
        defaultString = "",
    ),
)
