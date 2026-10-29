package cn.baymaxawa.xmsfnotichannel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

class AppInfoHelper(private val context: Context) {

    private var appInfoList: MutableList<MyAppInfo> = mutableListOf()

    var pattern: String = ""

    data class MyAppInfo(
        val appName: String,
        val packageName: String,
        val icon: Drawable,
        var ncInfo: NCInfo,
        val isSystemApp: Boolean
    )

    data class NCInfo(
        val channelGroupName: String,
        val channelName: String,
        var importance: Int,
        /** Android 17 起以通道 id 作为唯一标识，名称可能局部化或重复 */
        val channelId: String = channelName
    )

    fun getAppInfoList(): MutableList<MyAppInfo> {
        if (appInfoList.isNotEmpty()) {
            return sorted(appInfoList)
        }
        return getAppInfoListForNew()
    }

    fun getAppInfoListForNew(): MutableList<MyAppInfo> {
        val result = mutableListOf<MyAppInfo>()
        val pm = context.packageManager
        val ncUtils = NCUtils(context)

        val packages = runCatching {
            pm.getInstalledApplications(0)
        }.getOrDefault(emptyList())

        for (appInfo in packages) {
            // 跳过没有通道的应用，减少无效反射调用
            val channels = runCatching {
                ncUtils.getNotificationChannelInfoByRegex(appInfo.packageName, pattern)
            }.getOrDefault(emptyList())

            if (channels.isEmpty()) continue

            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(appInfo.packageName)
            val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull() ?: continue

            channels.forEach { info ->
                result.add(
                    MyAppInfo(
                        appName = label,
                        packageName = appInfo.packageName,
                        icon = icon,
                        ncInfo = info,
                        isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    )
                )
            }
        }

        appInfoList = result
        return sorted(result)
    }

    private fun sorted(list: List<MyAppInfo>): MutableList<MyAppInfo> =
        list.sortedWith(
            compareBy<MyAppInfo> { it.appName }
                .thenByDescending { it.ncInfo.importance }
        ).toMutableList()
}