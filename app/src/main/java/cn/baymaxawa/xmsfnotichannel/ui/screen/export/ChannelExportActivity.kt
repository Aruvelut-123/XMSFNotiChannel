package cn.baymaxawa.xmsfnotichannel.ui.screen.export

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.baymaxawa.xmsfnotichannel.R
import cn.baymaxawa.xmsfnotichannel.ui.model.AppIconCache
import cn.baymaxawa.xmsfnotichannel.ui.screen.subpage.BaseSubPageActivity
import cn.baymaxawa.xmsfnotichannel.utils.NCUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 导出页的列表行模型。 */
private data class ExportApp(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val channelCount: Int,
)

/**
 * 通知通道导出页。
 *
 * 用途：把某个应用的**全部**通知通道（含已关闭的）导出成 JSON，
 * 方便离线分析各家 App 到底开了哪些营销类通道、命名规律是什么。
 *
 * 这里读的是不做关键词过滤的原始列表——
 * 分析场景要的是全量数据，而不是本项目默认匹配到的营销通道。
 */
class ChannelExportActivity : BaseSubPageActivity() {

    override val titleRes: Int = R.string.settings_export_channels

    @Composable
    override fun SubPageContent(
        isBlurEnabled: Boolean,
        contentPadding: PaddingValues,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var keyword by remember { mutableStateOf("") }
        var apps by remember { mutableStateOf<List<ExportApp>?>(null) }
        var pendingJson by remember { mutableStateOf<String?>(null) }

        // 一次性扫出应用与通道数（IO），结果常驻本次页面生命周期
        LaunchedEffect(Unit) {
            if (apps == null) {
                apps = withContext(Dispatchers.IO) { loadApps(context) }
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            val json = pendingJson
            if (uri != null && json != null) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) R.string.export_success else R.string.export_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            pendingJson = null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item {
                    TextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = stringResource(R.string.export_search_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp, bottom = 4.dp),
                        singleLine = true,
                    )
                }

                val all = apps
                if (all == null) {
                    item { LoadingRow() }
                } else {
                    // 这里是 LazyListScope（非 Composable 上下文），不能调 remember；
                    // 几百条字符串过滤本身很便宜，直接算即可。
                    val q = keyword.trim()
                    val filtered = if (q.isEmpty()) {
                        all
                    } else {
                        all.filter {
                            it.appName.contains(q, true) ||
                                it.packageName.contains(q, true)
                        }
                    }
                    item {
                        SmallTitle(
                            text = stringResource(R.string.export_app_count, filtered.size)
                        )
                    }
                    items(items = filtered, key = { it.packageName }) { app ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                        ) {
                            BasicComponent(
                                title = app.appName,
                                summary = stringResource(
                                    R.string.export_channel_count,
                                    app.channelCount,
                                ),
                                startAction = { AppIcon(app.icon) },
                                onClick = {
                                    // 点击即读取全量通道生成 JSON，再拉起保存对话框
                                    scope.launch {
                                        val json = withContext(Dispatchers.IO) {
                                            buildJson(context, app)
                                        }
                                        pendingJson = json
                                        val safe = app.appName
                                            .replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                                            .ifBlank { app.packageName }
                                        exportLauncher.launch("$safe.channels.json")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    val modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
    if (icon == null) {
        Box(modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh))
    } else {
        Image(
            painter = BitmapPainter(icon),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        MiuixText(
            text = stringResource(R.string.export_loading),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 扫描所有已安装应用，顺带数出各自的通知通道数量。
 *
 * 通道读取要跨进程调系统服务，数百个包全量扫一遍需要数秒，
 * 因此放在 IO 线程并只在页面首次进入时做一次。
 */
private fun loadApps(context: android.content.Context): List<ExportApp> {
    val pm = context.packageManager
    val nc = NCUtils(context)
    val installed = runCatching {
        pm.getInstalledApplications(0)
    }.getOrDefault(emptyList())

    val result = ArrayList<ExportApp>(installed.size)
    for (info in installed) {
        val pkg = info.packageName
        val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
        val channels = runCatching { nc.getNotificationChannels(pkg) }.getOrDefault(emptyList())
        val icon = runCatching {
            AppIconCache.peek(pkg) ?: AppIconCache.load(
                context = context,
                packageName = pkg,
                drawable = info.loadIcon(pm),
                size = 108,
            )
        }.getOrNull()
        result.add(
            ExportApp(
                packageName = pkg,
                appName = label,
                icon = icon,
                channelCount = channels.size,
            )
        )
    }
    // 有通道的排前面，便于一眼看出哪些应用是「重灾区」
    return result.sortedWith(
        compareByDescending<ExportApp> { it.channelCount }
            .thenBy { it.appName.lowercase() }
    )
}

/**
 * 生成某个应用的完整通道 JSON。
 *
 * 字段全部用系统原始值，不做本地化，方便脚本对比不同版本、不同机型。
 */
private fun buildJson(context: android.content.Context, app: ExportApp): String {
    val channels = runCatching {
        NCUtils(context).getNotificationChannels(app.packageName)
    }.getOrDefault(emptyList())

    val root = JSONObject()
    root.put("packageName", app.packageName)
    root.put("appName", app.appName)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("channelCount", channels.size)

    val array = JSONArray()
    channels.forEach { info ->
        val item = JSONObject()
        item.put("id", info.channelId)
        item.put("name", info.channelName)
        item.put("group", info.channelGroupName)
        item.put("importance", info.importance)
        item.put("enabled", info.importance > 0)
        array.put(item)
    }
    root.put("channels", array)
    return root.toString(2)
}