package com.gswxxn.xmsfnotichannel.utils

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

        /** 以类描述符形式豁免，比逐个成员精确、也更抗版本变化 */
        private val HIDDEN_API_EXEMPTIONS = arrayOf(
            "Landroid/os/ServiceManager;",
            "Landroid/app/INotificationManager;",
            "Landroid/app/INotificationManager\$Stub;",
            "Landroid/content/pm/ParceledListSlice;"
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

    private val getNotificationChannelsForPackage: Method by lazy {
        iNotificationManager.javaClass.getDeclaredMethod(
            "getNotificationChannelsForPackage",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    private val getNotificationChannelGroupsForPackage: Method by lazy {
        iNotificationManager.javaClass.getDeclaredMethod(
            "getNotificationChannelGroupsForPackage",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
    }

    private val updateNotificationChannelForPackage: Method by lazy {
        iNotificationManager.javaClass.getDeclaredMethod(
            "updateNotificationChannelForPackage",
            String::class.java,
            Int::class.javaPrimitiveType,
            NotificationChannel::class.java
        ).apply { isAccessible = true }
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
        if (slice == null) return emptyList()
        if (slice is List<*>) return slice.filterNotNull()

        val clazz = Class.forName("android.content.pm.ParceledListSlice")

        runCatching {
            clazz.getDeclaredMethod("getList").apply { isAccessible = true }.invoke(slice)
        }.onSuccess { result ->
            @Suppress("UNCHECKED_CAST")
            (result as? List<Any>)?.let { return it }
        }.onFailure { Log.w(TAG, "getList() 失败，改用字段读取", it) }

        return runCatching {
            @Suppress("UNCHECKED_CAST")
            clazz.getDeclaredField("mList").apply { isAccessible = true }.get(slice) as? List<Any>
        }.getOrNull().orEmpty()
    }

    private fun getUid(pkgName: String): Int =
        runCatching { context.packageManager.getPackageUid(pkgName, 0) }.getOrDefault(-1)

    /**
     * 读取指定包的全部通知通道（包含 importance == 0 的已关闭通道）。
     */
    fun getNotificationChannels(pkgName: String): List<AppInfoHelper.NCInfo> {
        val uid = getUid(pkgName)
        if (uid < 0) return emptyList()

        val result = LinkedHashMap<String, AppInfoHelper.NCInfo>()

        // 通道粒度：主力来源
        runCatching {
            val slice = getNotificationChannelsForPackage.invoke(
                iNotificationManager, pkgName, uid, true
            )
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
            val slice = getNotificationChannelGroupsForPackage.invoke(
                iNotificationManager, pkgName, uid, true
            )
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
     * 按名称正则匹配通道。同时匹配通道名与通道 ID。
     */
    fun getNotificationChannelInfoByRegex(
        pkgName: String,
        channelNameRegex: String
    ): List<AppInfoHelper.NCInfo> {
        val regex = runCatching { Regex(channelNameRegex) }.getOrNull() ?: return emptyList()
        return getNotificationChannels(pkgName).filter {
            regex.matches(it.channelName) || regex.matches(it.channelId)
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
            updateNotificationChannelForPackage.invoke(
                iNotificationManager, appInfo.packageName, uid, channel
            )
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