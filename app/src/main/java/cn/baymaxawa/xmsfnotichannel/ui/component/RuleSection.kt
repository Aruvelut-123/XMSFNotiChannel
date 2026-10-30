package cn.baymaxawa.xmsfnotichannel.ui.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.utils.ChannelRule
import cn.baymaxawa.xmsfnotichannel.utils.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页的「规则管理」区。
 *
 * 拆成独立 Composable 而不是塞进 SettingsPage：
 * 这里要维护「编辑中的规则 / 删除确认」两组对话框状态，
 * 混在主设置页里会让那个文件继续膨胀，也不利于复用。
 *
 * 规则同步的自动触发在 [cn.baymaxawa.xmsfnotichannel.TemplateApp] 启动时完成，
 * 本区只提供手动同步按钮与结果反馈。
 */
@Composable
fun RuleSection(
    onKeywordsChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 规则仓库是单例，这里只保证已初始化（Application 里已 init，双保险）
    remember(context) { RuleRepository.init(context) }
    val cloudRules = RuleRepository.cloudRules
    val customRules = RuleRepository.customRules
    val meta = RuleRepository.cloudMeta

    var syncBusy by remember { mutableStateOf(false) }
    // 编辑中的自定义规则；null 表示对话框关闭
    var editing by remember { mutableStateOf<ChannelRule?>(null) }
    // 待删除确认的自定义规则
    var deleting by remember { mutableStateOf<ChannelRule?>(null) }

    val syncFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val lastSyncText = if (meta.lastSyncAt > 0L) {
        stringResource(R.string.rule_sync_last, syncFmt.format(Date(meta.lastSyncAt)))
    } else {
        stringResource(R.string.rule_sync_never)
    }
    val cloudSummary = buildString {
        append(lastSyncText)
        if (meta.version > 0) {
            append(" · ")
            append(context.getString(R.string.rule_sync_meta, meta.version, meta.updatedAt.ifBlank { "—" }))
        }
    }

    SmallTitle(text = stringResource(R.string.settings_channel_rule))
    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Column {
            // 手动同步。自动同步每次启动会跑（12 小时内不重复），这里留给想立刻拉的人
            BasicComponent(
                title = stringResource(R.string.rule_sync_now),
                summary = if (syncBusy) stringResource(R.string.rule_sync_summary) else cloudSummary,
                onClick = {
                    if (!syncBusy) {
                        syncBusy = true
                        scope.launch {
                            val result = RuleRepository.syncFromCloud(context)
                            syncBusy = false
                            Toast.makeText(
                                context,
                                when (result) {
                                    is RuleRepository.SyncResult.Success -> context.getString(
                                        R.string.msg_rule_sync_ok, result.count, result.version
                                    )
                                    is RuleRepository.SyncResult.Failure -> context.getString(
                                        R.string.msg_rule_sync_failed, result.message
                                    )
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                            onKeywordsChanged()
                        }
                    }
                },
            )
            // 云端规则：只允许启停，不允许改词（下次同步会被覆盖）
            cloudRules.forEach { rule ->
                SwitchPreference(
                    title = rule.name,
                    summary = ruleSummary(rule, context),
                    checked = rule.enabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                RuleRepository.setEnabled(context, rule.id, checked)
                            }
                            onKeywordsChanged()
                        }
                    },
                )
            }
        }
    }

    SmallTitle(text = stringResource(R.string.rule_section_custom))
    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Column {
            BasicComponent(
                title = stringResource(R.string.rule_add_custom),
                summary = stringResource(R.string.rule_add_custom_summary),
                onClick = { editing = ChannelRule(id = "", name = "", positive = emptyList()) },
            )
            customRules.forEach { rule ->
                BasicComponent(
                    title = rule.name,
                    summary = ruleSummary(rule, context),
                    onClick = { editing = rule },
                    endActions = {
                        IconButton(onClick = { deleting = rule }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.rule_delete),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    },
                )
            }
        }
    }

    // ---- 编辑对话框 ----
    val editTarget = editing
    if (editTarget != null) {
        RuleEditDialog(
            initial = editTarget,
            onDismiss = { editing = null },
            onConfirm = { name, positive, exclude ->
                if (positive.isEmpty()) {
                    Toast.makeText(context, R.string.rule_invalid, Toast.LENGTH_SHORT).show()
                    return@RuleEditDialog
                }
                // 新建时用时间戳做 id，避免重名冲突；编辑时沿用原 id
                val id = editTarget.id.ifBlank { "custom_${System.currentTimeMillis()}" }
                val rule = editTarget.copy(
                    id = id,
                    name = name.ifBlank { id },
                    positive = positive,
                    exclude = exclude,
                    source = ChannelRule.Source.CUSTOM,
                    enabled = true,
                )
                scope.launch {
                    withContext(Dispatchers.IO) { RuleRepository.upsertCustom(context, rule) }
                    editing = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.rule_saved_multi, rule.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onKeywordsChanged()
                }
            },
        )
    }

    // ---- 删除确认 ----
    val deleteTarget = deleting
    if (deleteTarget != null) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.rule_delete),
            summary = stringResource(R.string.rule_delete_confirm, deleteTarget.name),
            onDismissRequest = { deleting = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { deleting = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val id = deleteTarget.id
                        deleting = null
                        scope.launch {
                            withContext(Dispatchers.IO) { RuleRepository.deleteCustom(context, id) }
                            Toast.makeText(context, R.string.rule_deleted, Toast.LENGTH_SHORT).show()
                            onKeywordsChanged()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 「来源 · N 个关键词」的副标题 */
private fun ruleSummary(rule: ChannelRule, context: Context): String {
    val source = when {
        rule.source == ChannelRule.Source.CUSTOM -> context.getString(R.string.rule_section_custom)
        RuleRepository.cloudMeta.fromCloud -> context.getString(R.string.rule_source_cloud)
        else -> context.getString(R.string.rule_source_builtin)
    }
    val count = context.getString(R.string.rule_count_summary, rule.positive.size)
    return "$source · $count"
}

/**
 * 自定义规则编辑对话框。
 *
 * 关键词用「一行一个」的文本块而不是逐个输入框：
 * 用户实际是从通道导出里复制一串名字过来，多行文本框最省事。
 */
@Composable
private fun RuleEditDialog(
    initial: ChannelRule,
    onDismiss: () -> Unit,
    onConfirm: (name: String, positive: List<String>, exclude: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var positiveText by remember { mutableStateOf(initial.positive.joinToString("\n")) }
    var excludeText by remember { mutableStateOf(initial.exclude.joinToString("\n")) }

    fun parse(text: String): List<String> =
        text.split('\n', ',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    WindowDialog(
        show = true,
        title = stringResource(R.string.rule_add_custom),
        summary = stringResource(R.string.rule_add_custom_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.rule_name_label),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            TextField(
                value = positiveText,
                onValueChange = { positiveText = it },
                label = stringResource(R.string.rule_positive_label),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                singleLine = false,
            )
            TextField(
                value = excludeText,
                onValueChange = { excludeText = it },
                label = stringResource(R.string.rule_exclude_label),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                singleLine = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = { onConfirm(name.trim(), parse(positiveText), parse(excludeText)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}