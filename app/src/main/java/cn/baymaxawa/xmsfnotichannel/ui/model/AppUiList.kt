package cn.baymaxawa.xmsfnotichannel.ui.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import cn.baymaxawa.xmsfnotichannel.utils.AppInfoHelper

/**
 * 单个通知通道的界面模型。
 *
 * 关键点：importance 用 [mutableIntStateOf] 承载。
 * 这样点开关时只需改这一个值，Compose 只重组读它的那个 Switch，
 * 不需要像旧实现那样 `uiState.items = items.toList()` 复制整张表。
 */
@Stable
class ChannelUi(
    val channelName: String,
    val channelId: String,
    initialImportance: Int,
    /** 原始数据，调用 NCUtils 反向写系统时需要它 */
    val source: AppInfoHelper.MyAppInfo,
) {
    var importance by mutableIntStateOf(initialImportance)

    val enabled: Boolean get() = importance > 0

    /** 通道名可能为空（系统默认通道），此时退回通道 ID */
    val displayName: String get() = channelName.ifBlank { channelId }
}

/**
 * 按应用分组后的界面模型。
 *
 * 所有属性要么 val、要么是 [ChannelUi] 内部的可变状态，
 * 因此本类可以标 [Stable]，LazyColumn 会跳过未变化的行。
 */
@Stable
class AppUi(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val channels: List<ChannelUi>,
) {
    val totalCount: Int get() = channels.size

    val enabledCount: Int get() = channels.count { it.enabled }

    /** 总开关状态：全部开启才算开 */
    val allEnabled: Boolean get() = channels.isNotEmpty() && enabledCount == totalCount
}

/**
 * 把扁平的通道列表按 packageName 折叠成应用分组。
 *
 * @param icons 包名 → 已解码图标。**图标必须在 IO 线程提前解好**，
 *              这里不做任何解码，避免主线程阻塞。
 */
fun List<AppInfoHelper.MyAppInfo>.toAppUiList(
    icons: Map<String, ImageBitmap?>,
): List<AppUi> = groupBy { it.packageName }
    .map { (packageName, items) ->
        val first = items.first()
        AppUi(
            packageName = packageName,
            appName = first.appName,
            icon = icons[packageName],
            channels = items.map { info ->
                ChannelUi(
                    channelName = info.ncInfo.channelName,
                    channelId = info.ncInfo.channelId,
                    initialImportance = info.ncInfo.importance,
                    source = info,
                )
            },
        )
    }
    // 有开启通道的应用排前面，同类按名称排，避免每次刷新顺序乱跳
    .sortedWith(
        compareByDescending<AppUi> { it.enabledCount }
            .thenBy { it.appName }
    )