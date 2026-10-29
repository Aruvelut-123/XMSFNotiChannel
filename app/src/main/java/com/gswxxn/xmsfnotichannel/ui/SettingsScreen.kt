package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gswxxn.xmsfnotichannel.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页。
 *
 * 把原先藏在「关于页连点图标 5 次」里的搜索规则搬到这里，
 * 同时把主设置（自动检测、应用列表权限、框架状态、关于入口）统一收齐。
 */
@Composable
fun SettingsScreen(
    versionName: String,
    frameworkDesc: String,
    ruleLabel: String,
    permissionGranted: Boolean,
    hideIcon: Boolean,
    onHideIconChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    editing: Boolean,
    editingValue: String,
    onEditingValueChange: (String) -> Unit,
    onEditingStart: () -> Unit,
    onEditingConfirm: () -> Unit,
    onEditingDismiss: () -> Unit,
    onEditingReset: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = context.getString(R.string.alert_cancel),
                            tint = colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 通知通道
            item { SmallTitle(text = context.getString(R.string.settings_notification)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    BasicComponent(
                        title = context.getString(R.string.searching_name),
                        summary = context.getString(R.string.settings_rule_summary, ruleLabel),
                        onClick = onEditingStart,
                    )
                    BasicComponent(
                        title = context.getString(R.string.settings_permission),
                        summary = if (permissionGranted) {
                            context.getString(R.string.settings_permission_ok)
                        } else {
                            context.getString(R.string.settings_permission_missing)
                        },
                        summaryColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(
                            color = if (permissionGranted) colorScheme.onSurfaceVariantSummary else colorScheme.error,
                        ),
                    )
                    BasicComponent(
                        title = context.getString(R.string.settings_framework),
                        summary = frameworkDesc.ifEmpty { context.getString(R.string.hint_not_active) },
                    )
                }
            }

            // 外观
            item { SmallTitle(text = context.getString(R.string.settings_appearance)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    BasicComponent(
                        title = context.getString(R.string.hide_icon),
                        summary = null,
                        endActions = {
                            Switch(checked = hideIcon, onCheckedChange = onHideIconChange)
                        },
                    )
                }
            }

            // 其他
            item { SmallTitle(text = context.getString(R.string.settings_other)) }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(vertical = 2.dp),
                ) {
                    BasicComponent(
                        title = context.getString(R.string.about),
                        summary = context.getString(R.string.version, versionName),
                        onClick = onOpenAbout,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = context.getString(R.string.app_name),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
        }
    }

    // 搜索规则编辑弹窗
    OverlayDialog(
        show = editing,
        title = context.getString(R.string.searching_name),
        summary = context.getString(R.string.searching_name_summary),
        onDismissRequest = onEditingDismiss,
    ) {
        Column {
            TextField(
                value = editingValue,
                onValueChange = onEditingValueChange,
                label = context.getString(R.string.searching_name_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = context.getString(R.string.alert_reset),
                    onClick = onEditingReset,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = context.getString(R.string.alert_cancel),
                    onClick = onEditingDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = context.getString(R.string.alert_ok),
                    onClick = onEditingConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}