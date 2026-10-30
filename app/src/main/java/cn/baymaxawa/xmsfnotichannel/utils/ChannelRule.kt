package cn.baymaxawa.xmsfnotichannel.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条规则的完整定义。
 *
 * 规则来源分三类，用 [source] 区分：
 * - [Source.BUILTIN]  ：云端默认规则库同步下来的（或首次启动时的兜底内置副本）
 * - [Source.CUSTOM]   ：用户自己在设置页添加的
 *
 * 匹配语义：**任意一条启用规则命中正向词、且没有任何一条规则命中排除词**，即判定为营销通道。
 * 也就是说多条规则之间是「并集」，不是覆盖关系。
 */
data class ChannelRule(
    val id: String,
    val name: String,
    val description: String = "",
    val version: Int = 1,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val positive: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val source: Source = Source.BUILTIN,
) {

    enum class Source(val label: String) {
        BUILTIN("内置"),
        CUSTOM("自定义"),
    }

    /** 关键词都不为空的规则才参与匹配 */
    val usable: Boolean get() = enabled && positive.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("version", version)
        put("enabled", enabled)
        put("priority", priority)
        put("positive", JSONArray(positive))
        put("exclude", JSONArray(exclude))
        put("source", source.name)
    }

    companion object {

        /** 单条规则解析；字段缺失时用保守默认值，解析失败返回 null */
        fun fromJson(json: JSONObject, source: Source): ChannelRule? = runCatching {
            val id = json.optString("id").ifBlank { return null }
            val name = json.optString("name").ifBlank { id }
            ChannelRule(
                id = id,
                name = name,
                description = json.optString("description"),
                version = json.optInt("version", 1),
                enabled = json.optBoolean("enabled", true),
                priority = json.optInt("priority", 0),
                positive = json.optJSONArray("positive").toStringList(),
                exclude = json.optJSONArray("exclude").toStringList(),
                source = source,
            )
        }.getOrNull()

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            val out = ArrayList<String>(length())
            for (i in 0 until length()) {
                val s = optString(i).trim()
                // 空串会 `contains("")` 恒真，必须剔除
                if (s.isNotEmpty()) out.add(s)
            }
            return out
        }
    }
}

/**
 * 一次规则同步的结果描述。
 */
data class RuleBundle(
    val version: Int = 0,
    val updatedAt: String = "",
    val name: String = "",
    val rules: List<ChannelRule> = emptyList(),
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("updatedAt", updatedAt)
        put("name", name)
        put("rules", JSONArray().apply { rules.forEach { put(it.toJson()) } })
    }

    companion object {

        fun parse(text: String, source: ChannelRule.Source): RuleBundle? = runCatching {
            val root = JSONObject(text)
            val arr = root.optJSONArray("rules") ?: return null
            val list = ArrayList<ChannelRule>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                ChannelRule.fromJson(obj, source)?.let { list.add(it) }
            }
            if (list.isEmpty()) return null
            RuleBundle(
                version = root.optInt("version", 1),
                updatedAt = root.optString("updatedAt"),
                name = root.optString("name"),
                rules = list,
            )
        }.getOrNull()

        fun fromJson(json: JSONObject): RuleBundle? = parse(json.toString(), ChannelRule.Source.BUILTIN)
    }
}