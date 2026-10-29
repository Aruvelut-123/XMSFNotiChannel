package cn.baymaxawa.xmsfnotichannel.ui.screen.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.baymaxawa.xmsfnotichannel.LocaleHelper
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.hook.device.DeviceType
import cn.baymaxawa.xmsfnotichannel.prefs.ConfigBackup
import cn.baymaxawa.xmsfnotichannel.prefs.ConfigState
import cn.baymaxawa.xmsfnotichannel.ui.screen.export.ChannelExportActivity
import cn.baymaxawa.xmsfnotichannel.ui.screen.safemode.SafeModeActivity
import cn.baymaxawa.xmsfnotichannel.ui.util.BlurredBar
import cn.baymaxawa.xmsfnotichannel.ui.util.MiuixExpandSpec
import cn.baymaxawa.xmsfnotichannel.ui.util.blurSource
import cn.baymaxawa.xmsfnotichannel.ui.util.pageScrollModifiers
import cn.baymaxawa.xmsfnotichannel.ui.util.rememberBlurState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SettingsPageView(
    currentMode: ColorSchemeMode,
    onModeChange: (ColorSchemeMode) -> Unit,
    isFloatingNavbar: Boolean,
    onFloatingNavbarChange: (Boolean) -> Unit,
    isLiquidGlass: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    isBlurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    hideLauncherIcon: Boolean,
    onHideLauncherIconChange: (Boolean) -> Unit,
    checkUpdateOnLaunch: Boolean,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    isCheckingUpdate: Boolean,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context as? Activity
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_settings)


    // 「通道规则」相关：自定义关键词对话框
    val rulePrefs = context.getSharedPreferences(
        context.packageName + "_preferences",
        android.content.Context.MODE_PRIVATE,
    )
    var ruleKeyword by remember { mutableStateOf(rulePrefs.getString("search_name", "").orEmpty()) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var ruleInput by remember { mutableStateOf(ruleKeyword) }
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val json = ConfigBackup.exportJson()
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(json.toByteArray())
                }
                Toast.makeText(context, resources.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, resources.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()
                inputStream?.close()
                if (ConfigBackup.importJson(json)) {
                    Toast.makeText(context, resources.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                } else {
                    Toast.makeText(context, resources.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, resources.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource(if (isBlurEnabled) hazeState else null)
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                item {
                    Column {
                        SmallTitle(text = stringResource(R.string.settings_channel_rule))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            Column {
                                BasicComponent(
                                    title = stringResource(R.string.label_current_rule),
                                    summary = ruleKeyword.ifEmpty {
                                        stringResource(R.string.rule_builtin_default)
                                    },
                                    onClick = {
                                        ruleInput = ruleKeyword
                                        showRuleDialog = true
                                    },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.rule_reset),
                                    summary = stringResource(R.string.rule_reset_summary),
                                    onClick = {
                                        rulePrefs.edit().remove("search_name").apply()
                                        ruleKeyword = ""
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.rule_reset_done),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                        SmallTitle(text = stringResource(R.string.settings_interface))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            Column {
                                val modes = listOf(
                                    stringResource(R.string.theme_system),
                                    stringResource(R.string.theme_light),
                                    stringResource(R.string.theme_dark),
                                    stringResource(R.string.theme_monet_system),
                                    stringResource(R.string.theme_monet_light),
                                    stringResource(R.string.theme_monet_dark)
                                )
                                val modesEnum = listOf(
                                    ColorSchemeMode.System,
                                    ColorSchemeMode.Light,
                                    ColorSchemeMode.Dark,
                                    ColorSchemeMode.MonetSystem,
                                    ColorSchemeMode.MonetLight,
                                    ColorSchemeMode.MonetDark
                                )
                                val currentIndex = modesEnum.indexOf(currentMode).takeIf { it >= 0 } ?: 0

                                WindowDropdownPreference(
                                    title = stringResource(R.string.theme_mode),
                                    summary = modes[currentIndex],
                                    items = modes,
                                    selectedIndex = currentIndex,
                                    onSelectedIndexChange = { onModeChange(modesEnum[it]) },
                                    onExpandedChange = { }
                                )

                                SwitchPreference(
                                    title = stringResource(R.string.floating_navbar),
                                    summary = stringResource(R.string.floating_navbar_summary),
                                    checked = isFloatingNavbar,
                                    onCheckedChange = onFloatingNavbarChange
                                )

                                AnimatedVisibility(
                                    visible = isFloatingNavbar,
                                    enter = expandVertically(animationSpec = MiuixExpandSpec),
                                    exit = shrinkVertically(animationSpec = MiuixExpandSpec),
                                ) {
                                    SwitchPreference(
                                        title = stringResource(R.string.liquid_glass),
                                        summary = stringResource(R.string.liquid_glass_summary),
                                        checked = isLiquidGlass,
                                        onCheckedChange = onLiquidGlassChange
                                    )
                                }

                                SwitchPreference(
                                    title = stringResource(R.string.blur_enabled),
                                    summary = stringResource(R.string.blur_enabled_summary),
                                    checked = isBlurEnabled,
                                    onCheckedChange = onBlurEnabledChange
                                )

                                SwitchPreference(
                                    title = stringResource(R.string.hide_launcher_icon),
                                    summary = stringResource(R.string.hide_launcher_icon_summary),
                                    checked = hideLauncherIcon,
                                    onCheckedChange = onHideLauncherIconChange
                                )
                            }
                        }

                        SmallTitle(text = stringResource(R.string.settings_language))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            val languageNames = listOf(
                                stringResource(R.string.language_default),
                                stringResource(R.string.language_zh_cn),
                                stringResource(R.string.language_en)
                            )
                            val languageValues = listOf(
                                LocaleHelper.Language.SYSTEM,
                                LocaleHelper.Language.ZH_CN,
                                LocaleHelper.Language.EN
                            )
                            val savedLanguage = LocaleHelper.getSavedLanguage(context)
                            val langCurrentIndex = languageValues.indexOf(savedLanguage)
                                .takeIf { it >= 0 } ?: 0

                            WindowDropdownPreference(
                                title = stringResource(R.string.settings_language),
                                summary = languageNames[langCurrentIndex],
                                items = languageNames,
                                selectedIndex = langCurrentIndex,
                                onSelectedIndexChange = {
                                    LocaleHelper.setLanguage(context, languageValues[it])
                                    activity?.recreate()
                                },
                                onExpandedChange = { }
                            )
                        }

                        SmallTitle(text = stringResource(R.string.settings_update))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(R.string.check_update_on_launch),
                                    summary = stringResource(R.string.check_update_on_launch_summary),
                                    checked = checkUpdateOnLaunch,
                                    onCheckedChange = onCheckUpdateOnLaunchChange,
                                )

                                ArrowPreference(
                                    title = stringResource(R.string.check_update),
                                    summary = if (isCheckingUpdate) {
                                        stringResource(R.string.check_update_checking)
                                    } else {
                                        stringResource(R.string.check_update_summary, appVersion)
                                    },
                                    onClick = onCheckUpdate,
                                )
                            }
                        }

                        SmallTitle(text = stringResource(R.string.settings_analysis))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.settings_export_channels),
                                summary = stringResource(R.string.settings_export_channels_summary),
                                onClick = {
                                    context.startActivity(
                                        Intent(context, ChannelExportActivity::class.java)
                                    )
                                },
                            )
                        }
                        SmallTitle(text = stringResource(R.string.settings_data))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            Column {
                                ArrowPreference(
                                    title = stringResource(R.string.export_settings),
                                    summary = stringResource(R.string.export_settings_summary),
                                    onClick = {
                                        exportLauncher.launch("XMSFNotiChannel_settings.json")
                                    }
                                )

                                ArrowPreference(
                                    title = stringResource(R.string.import_settings),
                                    summary = stringResource(R.string.import_settings_summary),
                                    onClick = { importLauncher.launch("application/json") }
                                )
                            }
                        }

                        MiuixText(
                            text = stringResource(R.string.copyright),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // 自定义规则对话框
    // 注意：这里必须用 WindowDialog 而不是 OverlayDialog。
    // OverlayDialog 内部走 MiuixPopupUtils.DialogLayout，作用是把对话框塞进一个
    // 全局队列，由根布局里的 MiuixPopupHost() 统一渲染；本项目没有挂载
    // MiuixPopupHost（挂在 MainActivity 的话子页面 Activity 又拿不到），
    // 结果就是 show 变成 true 但没有任何东西被绘制，表现为「点了没反应」。
    // WindowDialog 自己持有 Dialog 窗口，不依赖宿主。
    WindowDialog(
        show = showRuleDialog,
        title = stringResource(R.string.label_current_rule),
        summary = stringResource(R.string.rule_dialog_summary),
        onDismissRequest = { showRuleDialog = false },
    ) {
        Column {
            TextField(
                value = ruleInput,
                onValueChange = { ruleInput = it },
                label = stringResource(R.string.rule_dialog_label),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showRuleDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val value = ruleInput.trim()
                        rulePrefs.edit().putString("search_name", value).apply()
                        ruleKeyword = value
                        showRuleDialog = false
                        Toast.makeText(
                            context,
                            resources.getString(R.string.rule_saved),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 设备类型对应的显示文案资源。 */
private fun deviceTypeLabelRes(type: DeviceType): Int = when (type) {
    DeviceType.PHONE -> R.string.device_type_phone
    DeviceType.PAD -> R.string.device_type_pad
    DeviceType.FOLD -> R.string.device_type_fold
}
