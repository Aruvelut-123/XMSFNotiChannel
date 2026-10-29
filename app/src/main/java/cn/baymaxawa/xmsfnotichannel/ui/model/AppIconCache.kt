package cn.baymaxawa.xmsfnotichannel.ui.model

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用图标缓存。
 *
 * 为什么需要它：`Drawable#toBitmap()` 会真正解码位图，属于耗时操作。
 * 旧实现把它放在 `remember { }` 里直接在组合阶段执行，LazyColumn 滚动时
 * 复用槽位会不断重新解码，主线程被拖慢 → 掉帧。
 *
 * 这里做两层缓存：
 * 1. [bitmapCache] 存已解码的 ImageBitmap，命中后零成本；
 * 2. 解码统一在扫描线程（IO）执行，绝不在组合阶段调用。
 */
object AppIconCache {

    /** 缓存条目上限（按应用个数计） */
    private const val MAX_ENTRIES = 128

    private val bitmapCache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    /** 正在解码中的包名，避免同一图标被并发重复解码 */
    private val inFlight = ConcurrentHashMap<String, Any>()

    /** 按包名取已缓存的图标，没有则返回 null（不会触发解码） */
    fun peek(packageName: String): ImageBitmap? = bitmapCache.get(packageName)

    /**
     * 解码并缓存图标。**必须在后台线程调用。**
     *
     * @param size 目标边长（px）
     */
    fun load(context: Context, packageName: String, drawable: Drawable?, size: Int): ImageBitmap? {
        peek(packageName)?.let { return it }
        if (drawable == null) return null

        val token = Any()
        // 已有其它线程在解码同一个包名：本次不去抢，直接返回当前缓存
        if (inFlight.putIfAbsent(packageName, token) != null) return peek(packageName)

        val bitmap = try {
            runCatching { drawable.toBitmap(size, size).asImageBitmap() }.getOrNull()
        } finally {
            inFlight.remove(packageName, token)
        }

        if (bitmap != null) {
            bitmapCache.put(packageName, bitmap)
        }
        return bitmap
    }

    fun clear() {
        bitmapCache.evictAll()
        inFlight.clear()
    }
}