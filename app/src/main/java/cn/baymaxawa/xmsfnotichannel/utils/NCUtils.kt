package cn.baymaxawa.xmsfnotichannel.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * 通知通道读写工具。
 *
 * Android 17 适配要点：
 * 1. `ServiceManager#getService` / `INotificationManager$Stub#asInterface` /
 *    `ParceledListSlice#getList` 都是 hidden API；`getList()` 在 Android 17 上被标为
 *    greylist-max-r，targetSdk >= 30 会被直接拦截。这里用 HiddenApiBypass 统一豁免。
 * 2. Android 17 的 `getNotificationChannelGroupsForPackage` 引入了 bundle channel 过滤，
 *    因此改用「通道粒度」(getNotificationChannelsForPackage) 为主，分组接口仅用于补全组名。
 * 3. `updateNotificationChannelForPackage` 仍可用，但它要求调用方被视为 system/phone，
 *    这由 Xposed 侧 hook `isCallerSystemOrPhone()` 保证。
 */
@SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
class NCUtils(private val context: Context) {

    private companion object {
        const val TAG = "XMSF_NCUtils"

        /**
         * 以类描述符形式豁免，比逐个成员精确、也更抗版本变化。
         *
         * **注意**：`addHiddenApiExemptions` 是按**字符串前缀**匹配的。
         * 旧写法带了结尾分号 `Landroid/app/INotificationManager;`，
         * 只能匹配外层类本身，匹配不到内嵌的
         * `Landroid/app/INotificationManager$Stub$Proxy;`。
         *
         * 结果：Android 17 上对 Proxy 做 `getDeclaredMethod` 时，
         * hidden API 屏蔽会抛 `NoSuchMethodException`（假装方法不存在），
         * 表现为「通道读不出来 = 功能不生效」。
         * 因此这里全部去掉结尾分号，改成前缀匹配。
         */
        private val HIDDEN_API_EXEMPTIONS = arrayOf(
            "Landroid/os/ServiceManager",
            "Landroid/app/INotificationManager",
            "Landroid/content/pm/ParceledListSlice"
        )

        @Volatile
        private var bypassInstalled = false
    }

    private val iNotificationManager: Any by lazy {
        val binder = Class.forName("android.os.ServiceManager")
            .getDeclaredMethod("getService", String::class.java)
            .invoke(null, "notification") as IBinder
        Class.forName("android.app.INotificationManager\$Stub")
            .getDeclaredMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private val allMethods: Array<Method> by lazy {
        // declaredMethods 的平台类型是 Array<Method!>；直接返回即可，
        // 不要经过 onEach（它返回 Array<out Method>，与属性类型不兼容）。
        val arr = iNotificationManager.javaClass.declaredMethods
        arr.forEach { m -> runCatching { m.isAccessible = true } }
        arr
    }

    /**
     * 把 INotificationManager 上所有「通道相关」方法的真实签名落盘一次。
     * Android 大版本之间这些方法的形参经常增删，硬编码签名会直接
     * NoSuchMethodException —— 这正是本模块在 Android 17 上失效的原因。
     */
    private fun dumpChannelMethodSignatures() {
        runCatching {
            val sigs = allMethods
                .filter { it.name.contains("hannel", ignoreCase = true) }
                .joinToString(" | ") { m ->
                    "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"
                }
            writeAppProbe("INM:signatures", sigs)
        }
    }

    /**
     * 按名字精确匹配候选方法，形参数量少的优先（少参数版本通常是新签名）。
     */
    private fun candidates(name: String): List<Method> =
        allMethods.filter { it.name == name }.sortedBy { it.parameterCount }

    /**
     * 按形参类型自动填充实参：
     * - String  -> 包名
     * - int     -> 第一个填 uid，其余填 1（通常是 flags / includeDeleted 之类的开关）
     * - boolean -> true
     * - 其它类型 -> 该候选不可用，返回 null
     */
    private fun fillArgs(method: Method, pkgName: String, uid: Int): Array<Any?>? {
        val out = ArrayList<Any?>(method.parameterCount)
        var firstIntUsed = false
        for (type in method.parameterTypes) {
            when {
                type == String::class.java -> out.add(pkgName)
                type == Int::class.javaPrimitiveType -> {
                    if (!firstIntUsed) {
                        out.add(uid)
                        firstIntUsed = true
                    } else {
                        out.add(1)
                    }
                }
                type == Boolean::class.javaPrimitiveType -> out.add(true)
                else -> return null
            }
        }
        return out.toTypedArray()
    }

    /**
     * 依次尝试同名方法的各个重载，返回第一个调用成功的结果。
     * 全部失败时返回 null，并把每次尝试的签名与异常落盘，便于下一轮定位。
     */
    private fun invokeFlexible(name: String, pkgName: String, uid: Int): Any? {
        val list = candidates(name)
        if (list.isEmpty()) {
            writeAppProbe("INM:noSuchName", name)
            return null
        }
        for (method in list) {
            val args = fillArgs(method, pkgName, uid) ?: continue
            val sig = "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})"
            val r = runCatching { method.invoke(iNotificationManager, *args) }
            if (r.isSuccess) {
                writeAppProbe("INM:ok", sig)
                return r.getOrNull()
            }
            writeAppProbe(
                "INM:tryFail", "$sig -> ${r.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message}" }}"
            )
        }
        return null
    }

    /**
     * 调用 updateNotificationChannelForPackage。
     * 形参含 [NotificationChannel] 对象，不能走 [fillArgs] 的按类型填充，
     * 因此单独按「String / int / 通道对象」的顺序拼实参。
     */
    private fun invokeUpdateChannel(
        pkgName: String,
        uid: Int,
        channel: NotificationChannel
    ): Throwable? {
        val list = candidates("updateNotificationChannelForPackage")
        if (list.isEmpty()) {
            writeAppProbe("INM:noSuchName", "updateNotificationChannelForPackage")
            return IllegalStateException("no such method")
        }
        var last: Throwable? = null
        for (method in list) {
            val out = ArrayList<Any?>(method.parameterCount)
            var firstIntUsed = false
            if (method.parameterTypes.any {
                    it != String::class.java &&
                        it != Int::class.javaPrimitiveType &&
                        !NotificationChannel::class.java.isAssignableFrom(it)
                }
            ) continue

            for (type in method.parameterTypes) {
                when {
                    type == String::class.java -> out.add(pkgName)
                    type == Int::class.javaPrimitiveType -> {
                        if (!firstIntUsed) {
                            out.add(uid)
                            firstIntUsed = true
                        } else out.add(1)
                    }

                    NotificationChannel::class.java.isAssignableFrom(type) -> out.add(channel)
                }
            }
            val sig = "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})"
            val r = runCatching { method.invoke(iNotificationManager, *out.toTypedArray()) }
            if (r.isSuccess) {
                writeAppProbe("INM:updateOk", sig)
                return null
            }
            last = r.exceptionOrNull()
            writeAppProbe(
                "INM:updateTryFail",
                "$sig -> ${last?.let { "${it::class.java.simpleName}:${it.message}" }}"
            )
        }
        return last ?: IllegalStateException("no compatible overload")
    }

    /**
     * 能力探测：实际读一次**其它应用**的通知通道，用来判断 hook 是否真的生效。
     *
     * 为什么不能靠远程偏好或状态文件：
     * - `prefs(...)` 在本机实测退化为 system_server 进程内的私有 SharedPreferences，
     *   App 侧 `getRemotePreferences` 永远读到 false；
     * - 状态文件也不行：`/data/system/` 目录 0700 system:system，App 进不去；
     *   system_server 又写不进 `/data/user/0/<包名>/files` 和 `/data/local/tmp`（EACCES）；
     * - `svc.scope` 只反映用户勾选的作用域，不代表 hook 已经挂上。
     *
     * 只有「真的跨进程读到了别人的通道」才能证明：
     * 服务端 `isCallerSystemOrPhone()` 被我们的 hook 放行了。
     *
     * @return true 表示 hook 确实生效；false 表示未生效或探测失败
     */
    fun probeHookEffective(): Boolean {
        // 自己的包不需要特权就能读，不能用来判断，必须挑别人
        val otherPkg = runCatching {
            context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .firstOrNull { it != context.packageName && it != "android" }
        }.getOrNull() ?: return false

        val otherUid = getUid(otherPkg)
        if (otherUid < 0) return false

        val result = runCatching {
            invokeFlexible("getNotificationChannelsForPackage", otherPkg, otherUid)
        }

        // 关键判据：抛 SecurityException = hook 没生效；
        // 正常返回（哪怕是空列表）= 服务端校验被放行了
        return result.isSuccess && result.getOrNull() != null
    }

    init {
        ensureHiddenApiBypass()
    }

    private fun ensureHiddenApiBypass() {
        if (bypassInstalled) return
        synchronized(NCUtils::class.java) {
            if (bypassInstalled) return
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions(*HIDDEN_API_EXEMPTIONS)
                Log.i(TAG, "已豁免 hidden API: ${HIDDEN_API_EXEMPTIONS.joinToString()}")
            }.onFailure { Log.w(TAG, "豁免 hidden API 失败", it) }
            bypassInstalled = true
        }
    }
    /**
     * App 侧落盘日志，转发到 [AppProbe]。
     * 这里在构造时确保目录存在，避免「探针不生效」的情况。
     */
    private fun writeAppProbe(stage: String, extra: String = "") {
        AppProbe.init(context)
        AppProbe.mark(stage, extra)
    }

    /** ParceledListSlice -> List，优先 getList()，失败则读 mList 字段 */
    private fun unwrapParceledList(slice: Any?): List<Any> {
        if (slice == null) {
            writeAppProbe("unwrap:nullSlice")
            return emptyList()
        }
        if (slice is List<*>) {
            val l = slice.filterNotNull()
            writeAppProbe("unwrap:alreadyList", "size=${l.size}")
            return l
        }

        writeAppProbe("unwrap:type", "cls=${slice.javaClass.name}")

        val clazz = Class.forName("android.content.pm.ParceledListSlice")

        runCatching {
            clazz.getDeclaredMethod("getList").apply { isAccessible = true }.invoke(slice)
        }.onSuccess { result ->
            @Suppress("UNCHECKED_CAST")
            val list = result as? List<Any>
            if (list != null) {
                writeAppProbe("unwrap:getListOk", "size=${list.size} first=${list.firstOrNull()?.javaClass?.name}")
                return list
            }
            writeAppProbe("unwrap:getListNotList", "result=${result?.javaClass?.name}")
        }.onFailure {
            writeAppProbe(
                "unwrap:getListFail", "${it::class.java.simpleName}: ${it.message}"
            )
        }

        return runCatching {
            @Suppress("UNCHECKED_CAST")
            clazz.getDeclaredField("mList").apply { isAccessible = true }.get(slice) as? List<Any>
        }.onSuccess { l ->
            writeAppProbe("unwrap:fieldOk", "size=${l?.size}")
        }.onFailure {
            writeAppProbe("unwrap:fieldFail", "${it::class.java.simpleName}: ${it.message}")
        }.getOrNull().orEmpty()
    }

    private fun getUid(pkgName: String): Int =
        runCatching { context.packageManager.getPackageUid(pkgName, 0) }.getOrDefault(-1)

    /**
     * 读取指定包的全部通知通道（包含 importance == 0 的已关闭通道）。
     */
    fun getNotificationChannels(pkgName: String): List<AppInfoHelper.NCInfo> {
        val uid = getUid(pkgName)
        if (uid < 0) {
            writeAppProbe("readChannels:uidFail", "$pkgName")
            return emptyList()
        }

        dumpChannelMethodSignatures()

        val result = LinkedHashMap<String, AppInfoHelper.NCInfo>()

        // 通道粒度：主力来源
        runCatching {
            val slice = invokeFlexible("getNotificationChannelsForPackage", pkgName, uid)
            unwrapParceledList(slice)
                .filterIsInstance<NotificationChannel>()
                .forEach { channel ->
                    val id = runCatching { channel.id }.getOrNull() ?: return@forEach
                    result[id] = AppInfoHelper.NCInfo(
                        channelGroupName = runCatching { channel.group }.getOrNull().orEmpty(),
                        channelName = runCatching { channel.name?.toString() }.getOrNull().orEmpty(),
                        importance = runCatching { channel.importance }.getOrDefault(0),
                        channelId = id
                    )
                }
        }.onFailure {
            Log.w(TAG, "读取 $pkgName 通道失败", it)
            writeAppProbe("readChannels:fail", "$pkgName ${it::class.java.name}: ${it.message}")
        }

        // 分组粒度：补全组名 + 兜底漏项
        runCatching {
            val slice = invokeFlexible("getNotificationChannelGroupsForPackage", pkgName, uid)
            unwrapParceledList(slice)
                .filterIsInstance<NotificationChannelGroup>()
                .forEach { group ->
                    val groupName = runCatching { group.name?.toString() }.getOrNull().orEmpty()
                    runCatching { group.channels }.getOrNull()?.forEach { channel ->
                        val id = runCatching { channel.id }.getOrNull() ?: return@forEach
                        val existing = result[id]
                        if (existing == null) {
                            result[id] = AppInfoHelper.NCInfo(
                                channelGroupName = groupName,
                                channelName = runCatching { channel.name?.toString() }
                                    .getOrNull().orEmpty(),
                                importance = runCatching { channel.importance }.getOrDefault(0),
                                channelId = id
                            )
                        } else if (existing.channelGroupName.isEmpty()) {
                            result[id] = existing.copy(channelGroupName = groupName)
                        }
                    }
                }
        }.onFailure { Log.w(TAG, "读取 $pkgName 通道分组失败", it) }

        return result.values.toList()
    }

    /**
     * 按名称筛选通道，同时匹配通道名与通道 ID。
     *
     * pattern 为空时走 [ChannelMatcher] 的内置关键词表（营销/推广/活动/推荐等），
     * 非空时按用户自定义正则做包含匹配。
     */
    fun getNotificationChannelInfoByRegex(
        pkgName: String,
        channelNameRegex: String
    ): List<AppInfoHelper.NCInfo> {
        val all = getNotificationChannels(pkgName)
        if (all.isEmpty()) return emptyList()

        // 自定义正则非法时直接返回空，避免静默退化成内置规则造成困惑
        if (channelNameRegex.isNotBlank() &&
            runCatching { Regex(channelNameRegex) }.isFailure
        ) {
            return emptyList()
        }

        return all.filter {
            ChannelMatcher.matches(it.channelName, it.channelId, channelNameRegex)
        }
    }

    /**
     * 修改通道重要性。
     * @param targetImportance IMPORTANCE_NONE(0) 关闭 / IMPORTANCE_DEFAULT(3) 开启
     */
    fun setChannelImportance(appInfo: AppInfoHelper.MyAppInfo, targetImportance: Int): Boolean {
        writeAppProbe("setChannelImportance:enter", "${appInfo.packageName}/${appInfo.ncInfo.channelId}")

        val uid = getUid(appInfo.packageName)
        if (uid < 0) {
            writeAppProbe("setChannelImportance:uidFail", appInfo.packageName)
            return false
        }

        val all = getNotificationChannels(appInfo.packageName)
        writeAppProbe(
            "setChannelImportance:read", "count=${all.size} ids=${all.map { it.channelId }}"
        )

        val target = all.firstOrNull { it.channelId == appInfo.ncInfo.channelId }
        if (target == null) {
            writeAppProbe(
                "setChannelImportance:targetMissing",
                "want=${appInfo.ncInfo.channelId} have=${all.map { it.channelId }}"
            )
            return false
        }
        writeAppProbe("setChannelImportance:targetFound", target.channelId)

        return runCatching {
            val channel = NotificationChannel(
                target.channelId,
                target.channelName,
                targetImportance
            ).apply {
                if (target.channelGroupName.isNotEmpty()) {
                    runCatching { group = target.channelGroupName }
                }
                setShowBadge(targetImportance != NotificationManager.IMPORTANCE_NONE)
            }
            val err = invokeUpdateChannel(appInfo.packageName, uid, channel)
            if (err != null) throw err
            Log.i(TAG, "已设置 ${appInfo.packageName}/${target.channelId} -> importance=$targetImportance")
            writeAppProbe("setChannelImportance:ok", "${appInfo.packageName}/${target.channelId}")
            true
        }.onFailure {
            Log.w(TAG, "修改 ${appInfo.packageName}/${target.channelId} 失败", it)
            writeAppProbe(
                "setChannelImportance:fail",
                "${appInfo.packageName}/${target.channelId} ${it::class.java.name}: ${it.message}"
            )
        }.getOrDefault(false)
    }

    fun enableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo): Boolean =
        setChannelImportance(appInfo, NotificationManager.IMPORTANCE_DEFAULT)

    fun disableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo): Boolean =
        setChannelImportance(appInfo, NotificationManager.IMPORTANCE_NONE)
}