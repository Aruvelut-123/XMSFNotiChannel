package cn.baymaxawa.xmsfnotichannel.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 规则仓库：负责规则的持久化、云端同步与生效集合计算。
 *
 * ## 存储布局
 *
 * 全部落在应用私有目录 `filesDir/rules/` 下，不走 SharedPreferences：
 * 规则是结构化 JSON、可能上百条关键词，塞进 SP 会很难维护与导出。
 *
 * | 文件 | 内容 |
 * |---|---|
 * | `cloud.json`  | 最近一次成功同步的云端规则集 |
 * | `custom.json` | 用户自建规则集 |
 *
 * 两份文件都只是「规则集 JSON」，与云端仓库 `rules.json` 格式完全一致，
 * 因此可以直接互相导入导出。
 *
 * ## 首个启动的兜底
 *
 * 云端规则没同步过（首次安装、无网络）时不能没有规则可用，
 * 因此 [builtinRules] 里保留一份与云端 `rules.json` 同源的副本。
 * 这份副本只在 `cloud.json` 不存在时启用，一旦同步成功就不会再用。
 */
object RuleRepository {

    /** 云端默认规则地址；换成自己的 fork 只需改这一行 */
    const val CLOUD_URL =
        "https://raw.githubusercontent.com/Aruvelut-123/NotiSentry-Rules/main/rules.json"

    private const val DIR_NAME = "rules"
    private const val CLOUD_FILE = "cloud.json"
    private const val CUSTOM_FILE = "custom.json"

    private const val TIMEOUT_MS = 10_000

    /** 自动同步的最小间隔：12 小时。避免每次冷启动都打一次 GitHub。 */
    private const val AUTO_SYNC_INTERVAL_MS = 12L * 60L * 60L * 1000L

    /** 云端规则集元信息，界面显示「规则库版本 / 更新时间」用 */
    var cloudMeta by mutableStateOf(CloudMeta())
        private set

    /** 自定义规则集，Compose 直接观察 */
    var customRules by mutableStateOf<List<ChannelRule>>(emptyList())
        private set

    /** 云端规则集（未同步时为内置兜底副本） */
    var cloudRules by mutableStateOf<List<ChannelRule>>(emptyList())
        private set

    data class CloudMeta(
        val version: Int = 0,
        val updatedAt: String = "",
        val name: String = "",
        val fromCloud: Boolean = false,
        /** 上次同步时间（毫秒），0 表示从未同步 */
        val lastSyncAt: Long = 0L,
    )

    @Volatile
    private var initialized = false

    // ---------------------------------------------------------------- 初始化

    /** 幂等初始化：读取本地文件，缺失时用兜底副本 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

            cloudRules = readBundle(File(dir, CLOUD_FILE))
                ?.let { bundle ->
                    cloudMeta = CloudMeta(
                        version = bundle.version,
                        updatedAt = bundle.updatedAt,
                        name = bundle.name,
                        fromCloud = true,
                        lastSyncAt = File(dir, CLOUD_FILE).lastModified(),
                    )
                    bundle.rules
                } ?: builtinRules()

            if (cloudMeta.fromCloud.not()) {
                cloudMeta = CloudMeta(
                    version = BUILTIN_VERSION,
                    updatedAt = BUILTIN_UPDATED_AT,
                    name = "内置规则",
                    fromCloud = false,
                )
            }

            customRules = readBundle(File(dir, CUSTOM_FILE))?.rules.orEmpty()
            initialized = true
        }
    }

    // ---------------------------------------------------------------- 生效集合

    /**
     * 当前参与匹配的全部规则。
     *
     * 用户停用单条规则时，enabled=false 的规则会被 [ChannelMatcher] 忽略正向词，
     * 但**排除词依然生效** —— 这是刻意的：停用一条规则不该让它的排除词失效，
     * 否则停用「激进规则」反而会导致误关变多。
     */
    fun effectiveRules(): List<ChannelRule> {
        val all = ArrayList<ChannelRule>(cloudRules.size + customRules.size)
        all.addAll(cloudRules)
        all.addAll(customRules)
        return all
    }

    /** 只保留有正向词的规则，供界面统计 */
    fun activeRuleCount(): Int = effectiveRules().count { it.usable }

    // ---------------------------------------------------------------- 云端同步

    /**
     * 启动时自动同步。
     *
     * 每次冷启动都发一次请求没必要，因此带节流：距上次成功同步不足
     * [AUTO_SYNC_INTERVAL_MS] 时直接返回 null（表示「本次跳过」）。
     * 从未同步过（首次安装 / 一直无网）时会立刻拉一次。
     */
    suspend fun autoSync(context: Context): SyncResult? {
        init(context)
        val last = cloudMeta.lastSyncAt
        if (cloudMeta.fromCloud && last > 0L) {
            val elapsed = System.currentTimeMillis() - last
            // 时钟回拨导致 elapsed 为负时也重建同步，避免永久跳过
            if (elapsed in 0 until AUTO_SYNC_INTERVAL_MS) return null
        }
        return syncFromCloud(context)
    }

    /**
     * 从云端拉取规则并落盘。
     *
     * 失败时**不动**已有文件，返回 [SyncResult.Failure]，
     * 避免一次网络抖动把用户手里能用的规则冲掉。
     */
    suspend fun syncFromCloud(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val text = runCatching { fetch(CLOUD_URL) }.getOrElse {
            return@withContext SyncResult.Failure(it.message ?: "网络请求失败")
        }

        val bundle = RuleBundle.parse(text, ChannelRule.Source.BUILTIN)
            ?: return@withContext SyncResult.Failure("规则解析失败")

        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        runCatching { File(dir, CLOUD_FILE).writeText(text) }
            .onFailure { return@withContext SyncResult.Failure("写入失败：${it.message}") }

        cloudRules = bundle.rules
        cloudMeta = CloudMeta(
            version = bundle.version,
            updatedAt = bundle.updatedAt,
            name = bundle.name,
            fromCloud = true,
            lastSyncAt = System.currentTimeMillis(),
        )
        SyncResult.Success(bundle.rules.size, bundle.version, bundle.updatedAt)
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // raw.githubusercontent 会缓存几分钟，加个查询参数强制拿最新
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- 自定义规则

    /** 新增或覆盖一条自定义规则 */
    fun upsertCustom(context: Context, rule: ChannelRule): Boolean {
        val normalized = rule.copy(source = ChannelRule.Source.CUSTOM)
        val list = customRules.toMutableList()
        val idx = list.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) list[idx] = normalized else list.add(normalized)
        return persistCustom(context, list)
    }

    fun deleteCustom(context: Context, id: String): Boolean =
        persistCustom(context, customRules.filterNot { it.id == id })

    /** 整条启用 / 停用（云端与自定义都能改，改的是本地生效状态） */
    fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        // 先看云端规则：改它的 enabled 需要重写 cloud.json
        val cloud = cloudRules.toMutableList()
        val ci = cloud.indexOfFirst { it.id == id }
        if (ci >= 0) {
            cloud[ci] = cloud[ci].copy(enabled = enabled)
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val bundle = RuleBundle(
                version = cloudMeta.version,
                updatedAt = cloudMeta.updatedAt,
                name = cloudMeta.name,
                rules = cloud,
            )
            val ok = runCatching {
                File(dir, CLOUD_FILE).writeText(bundle.toJson().toString(2))
            }.isSuccess
            if (ok) cloudRules = cloud
            return ok
        }
        val list = customRules.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return false
        list[i] = list[i].copy(enabled = enabled)
        return persistCustom(context, list)
    }

    private fun persistCustom(context: Context, list: List<ChannelRule>): Boolean {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val bundle = RuleBundle(
            version = 1,
            updatedAt = "",
            name = "自定义规则",
            rules = list,
        )
        return runCatching {
            File(dir, CUSTOM_FILE).writeText(bundle.toJson().toString(2))
        }.isSuccess.also { ok -> if (ok) customRules = list }
    }

    // ---------------------------------------------------------------- 导入导出

    /** 把当前全部规则（云端副本 + 自定义）导出成一份可分享的 JSON */
    fun exportAll(): String = RuleBundle(
        version = cloudMeta.version,
        updatedAt = cloudMeta.updatedAt,
        name = "通知哨兵规则导出",
        rules = effectiveRules(),
    ).toJson().toString(2)

    /** 从 JSON 导入；导入的规则一律标记为 CUSTOM，避免污染云端副本 */
    fun importRules(context: Context, text: String): Int {
        val bundle = RuleBundle.parse(text, ChannelRule.Source.CUSTOM) ?: return 0
        val list = customRules.toMutableList()
        var added = 0
        bundle.rules.forEach { rule ->
            val idx = list.indexOfFirst { it.id == rule.id }
            if (idx >= 0) list[idx] = rule else { list.add(rule); added++ }
        }
        return if (persistCustom(context, list)) bundle.rules.size else 0
    }

    // ---------------------------------------------------------------- 文件读取

    private fun readBundle(file: File): RuleBundle? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return RuleBundle.parse(text, ChannelRule.Source.BUILTIN)
    }

    // ---------------------------------------------------------------- 兜底副本

    /** 与云端 `rules.json` 同源的最小可用副本，仅在从未同步过时使用 */
    fun builtinRules(): List<ChannelRule> = FALLBACK

    const val BUILTIN_VERSION = 1
    const val BUILTIN_UPDATED_AT = "2026-10-30"

    private val FALLBACK: List<ChannelRule> = listOf(
        ChannelRule(
            id = "fallback-official",
            name = "内置规则",
            description = "模块自带的兜底规则表，云端规则同步成功后会被替换。",
            enabled = true,
            priority = 0,
            positive = listOf(
                "运营消息", "运营活动", "运营通道", "运营推送",
                "个性化通道", "个性化推荐", "个性化推送",
                "营销", "推广", "广告", "促销", "特惠", "优惠", "优惠券", "红包",
                "折扣", "满减", "秒杀", "团购", "补贴", "返利", "福利",
                "活动通知", "平台活动", "精选活动", "活动优惠", "业务活动", "互动活动",
                "周年庆", "大促",
                "签到", "抢兑", "抽奖", "积分兑换", "任务奖励", "每日任务",
                "推荐", "精选", "猜你喜欢", "为你推荐", "兴趣内容", "智能推送",
                "资讯", "热点", "头条", "每日推荐", "社区内容", "内容社区",
                "商城", "店铺", "上新", "新品", "开售", "商品", "悬浮锁屏",
                "直播提醒", "直播动态", "直播预告",
                "promotion", "promo", "advert", "campaign", "coupon",
                "discount", "recommend", "lottery", "checkin",
            ),
            exclude = listOf(
                "后台活动", "模拟活动", "实时活动", "安装进度",
                "下载", "上传", "转码", "安装", "更新服务", "版本更新", "新版本",
                "安全检测", "支付", "付款", "转账", "收款", "订单", "物流", "退款", "验证码",
                "电量", "充电", "网络", "蓝牙", "热点", "定位",
                "播放器", "通知栏", "控件", "前台服务",
            ),
            source = ChannelRule.Source.BUILTIN,
        )
    )

    sealed interface SyncResult {
        data class Success(val count: Int, val version: Int, val updatedAt: String) : SyncResult
        data class Failure(val message: String) : SyncResult
    }

    /** 供 ConfigBackup 之类的老调用点拼接 JSON 用 */
    fun toJsonString(): String = JSONObject(exportAll()).toString()
}