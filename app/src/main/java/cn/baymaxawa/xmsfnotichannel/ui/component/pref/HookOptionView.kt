package cn.baymaxawa.xmsfnotichannel.ui.component.pref

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.baymaxawa.xmsfnotichannel.prefs.OptionSpec
import cn.baymaxawa.xmsfnotichannel.prefs.OptionType

/** 根据 [OptionSpec.type] 渲染对应组件。 */
@Composable
fun HookOptionView(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
    onArrowClick: () -> Unit = {},
) {
    when (spec.type) {
        OptionType.SWITCH -> HookSwitchCard(spec, modifier)
        OptionType.CHECKBOX -> HookCheckboxCard(spec, modifier)
        OptionType.ARROW -> HookArrowCard(spec, modifier, onArrowClick)
        OptionType.DROPDOWN -> HookDropdownCard(spec, modifier)
        OptionType.RADIO -> HookRadioCard(spec, modifier)
        OptionType.SLIDER -> HookSliderCard(spec, modifier)
        OptionType.TEXT -> HookTextCard(spec, modifier)
        OptionType.PACKAGE_LIST -> HookPackageListCard(spec, modifier)
        OptionType.SPINNER -> HookDropdownCard(spec, modifier)
    }
}
