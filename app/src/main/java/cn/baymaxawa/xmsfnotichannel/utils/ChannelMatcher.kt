package cn.baymaxawa.xmsfnotichannel.utils

/**
 * 通知通道匹配引擎。
 *
 * ## 从单规则到多规则的演进
 *
 * 原实现只有「一个自定义正则 / 一套内置关键词」。实际上：
 * - 内置词表需要随 App 更新不断补词，靠发版推送太慢 → 需要云端同步
 * - 不同用户容忍度不同，有人想更激进 → 需要多条规则并存
 * - 用户还想自己加几条 → 需要自定义规则与官方规则并行
 *
 * 因此改成「规则集合」模型：
 *
 * 1. **排除优先**：先扫全部启用规则的 exclude，命中任何一个就直接放行。
 *    这一步必须在正向词之前，否则 `订单物流` 会因为含 `物流` 之外的词被误关。
 * 2. **正向取并集**：再扫全部启用规则的 positive，命中任意一个即判定为营销通道。
 * 3. **自定义正则**：设置页里仍可填一条正则，作为额外的正向条件（向后兼容旧行为）。
 *
 * 规则来源由 [ChannelRule.source] 标记，界面据此分组展示。
 * 规则的实际增删改与持久化在 [RuleRepository] 中，本类只做纯计算，便于单测。
 */
object ChannelMatcher {

    /**
     * 判断某个通道是否应纳入「营销/推广」范围。
     *
     * @param channelName 通道显示名，可能为空
     * @param channelId   通道 id，名称为空时用它兜底
     * @param rules       当前生效的全部规则（已由 Repository 过滤掉未启用的）
     * @param extraPattern 用户在设置里填的自定义正则；为空表示不使用
     */
    fun matches(
        channelName: String,
        channelId: String,
        rules: List<ChannelRule>,
        extraPattern: String = "",
    ): Boolean {
        // 名称和 id 都参与匹配：部分 App 通道名是英文占位符，真正的语义在 id 里
        val haystacks = listOf(channelName, channelId).filter { it.isNotEmpty() }
        if (haystacks.isEmpty()) return false

        // ① 排除优先：任何一条规则的排除词命中，都直接放行。
        //    放在正向判断之前，是为了让「激进规则」里的泛化正向词不会误伤功能性通道。
        rules.forEach { rule ->
            rule.exclude.forEach { word ->
                if (haystacks.any { it.contains(word, ignoreCase = true) }) return false
            }
        }

        // ② 正向取并集
        rules.forEach { rule ->
            rule.positive.forEach { word ->
                if (haystacks.any { it.contains(word, ignoreCase = true) }) return true
            }
        }

        // ③ 自定义正则作为额外正向条件（旧版行为，保持兼容）
        if (extraPattern.isNotBlank()) {
            val regex = runCatching { Regex(extraPattern) }.getOrNull()
                ?: return false
            return haystacks.any { regex.containsMatchIn(it) }
        }

        return false
    }

    /** 兼容旧调用点：只传正则、不带规则集合时使用内置规则 */
    fun matches(channelName: String, channelId: String, pattern: String): Boolean {
        val rules = if (pattern.isNotBlank()) emptyList() else RuleRepository.builtinRules()
        return matches(channelName, channelId, rules, pattern)
    }

    /** 供界面展示的规则摘要，例如「内置规则 + 我的一条规则」 */
    fun describe(rules: List<ChannelRule>, extraPattern: String): String {
        val names = rules.filter { it.usable }.map { it.name }
        val parts = ArrayList<String>(names.size + 1)
        parts.addAll(names)
        if (extraPattern.isNotBlank()) parts.add("自定义：$extraPattern")
        return if (parts.isEmpty()) "无规则" else parts.joinToString(" + ")
    }
}