package cn.baymaxawa.xmsfnotichannel.utils

/**
 * 通知通道匹配规则。
 *
 * 原实现只支持「单个正则」，默认值 `运营消息`。但各家 App 的营销通道
 * 名字五花八门，实测本机 774 个通道里光是 `营销推广` 就有 19 个，
 * 另有 `热门推广`、`优惠促销`、`精选活动`、`智能推荐`、`商城消息` 等等，
 * 用单一名字必然漏掉一大片。
 *
 * 因此改成两层：
 * 1. 用户在设置里填了自定义内容 → 按正则处理（保持原有能力，且从全匹配放宽为包含匹配）
 * 2. 用户没填 → 走内置关键词表
 *
 * 内置表分「正向词」和「排除词」。排除词是必需的：
 * `活动` 这个词会命中 `后台活动`、`模拟活动`、`安装进度实时活动`，
 * `推送` 会命中 `新版本推送`，这些误关会直接影响正常功能。
 */
object ChannelMatcher {

    /**
     * 正向关键词：通道名或通道 id 包含任意一个即视为营销/推广类。
     *
     * 词表来自本机 774 个真实通道名的统计，按语义分组便于维护。
     */
    private val POSITIVE_KEYWORDS = listOf(
        // 运营与推送
        "运营消息", "运营活动",
        // 运营通道 / 个性化通道（QQ音乐实测：广告走「运营通道」「个性化通道」，
        // 名字里完全没有营销词，旧词表因此整条漏掉。这里只加带「通道/推送/推荐」
        // 后缀的完整词，避免把「运营消息」这类用户可能想要的通道一起扫进来。）
        "运营通道", "运营推送", "个性化通道", "个性化推荐", "个性化推送",
        // 广告营销
        "营销", "推广", "广告", "促销", "特惠", "优惠", "优惠券", "红包",
        "折扣", "满减", "秒杀", "团购", "补贴", "返利", "福利",
        // 活动
        "活动通知", "平台活动", "精选活动", "活动优惠", "业务活动", "周年庆", "大促",
        // 推荐与内容
        "推荐", "精选", "猜你喜欢", "为你推荐", "兴趣内容", "智能推送",
        "资讯", "热点", "头条", "每日推荐",
        // 商城电商
        "商城", "店铺", "上新", "新品", "开售", "商品",
        // 直播
        "直播提醒", "直播动态", "直播预告"
    )

    /**
     * 排除词：通道名包含任意一个就跳过，即使命中了正向词。
     * 这些通道属于功能性的，关掉会导致 App 无法正常使用或收不到重要信息。
     */
    private val EXCLUDE_KEYWORDS = listOf(
        // 「活动」类误伤
        "后台活动", "模拟活动", "实时活动", "安装进度",
        // 下载与文件
        "下载", "上传", "转码", "安装", "更新服务", "版本更新", "新版本",
        // 安全与支付
        "安全检测", "支付", "付款", "转账", "收款", "订单", "物流", "退款", "验证码",
        // 设备状态
        "电量", "充电", "网络", "蓝牙", "热点", "定位",
        // 播放与控件
        "播放器", "通知栏", "控件", "前台服务"
    )

    /**
     * 判断某个通道是否应纳入「营销/推广」范围。
     *
     * @param channelName 通道显示名，可能为空
     * @param channelId   通道 id，名称为空时用它兜底
     * @param pattern     用户在设置里填的自定义规则；为空表示使用内置关键词表
     */
    fun matches(channelName: String, channelId: String, pattern: String): Boolean {
        if (pattern.isNotBlank()) {
            val regex = runCatching { Regex(pattern) }.getOrNull() ?: return false
            // 从原来的全匹配放宽为包含匹配，这样填「运营消息」也能命中「运营消息通知」
            return regex.containsMatchIn(channelName) || regex.containsMatchIn(channelId)
        }

        val text = channelName.ifEmpty { channelId }
        if (text.isEmpty()) return false

        if (EXCLUDE_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return false
        return POSITIVE_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    /** 供界面展示的默认规则说明 */
    fun defaultDescription(): String = "运营消息 / 营销推广 / 活动 / 推荐 / 商城…（内置规则）"
}