package cn.baymaxawa.xmsfnotichannel.utils

import android.content.Context

/**
 * 包可见性探测。
 *
 * Manifest 里的 `android.permission.QUERY_ALL_PACKAGES` 是 normal 级权限，
 * 安装时就会 granted=true，所以**不能**靠 `checkSelfPermission` 判断；
 * 但 HyperOS / MIUI 会出现「权限显示已授予、实际只能看到 1 个包」的情况，
 * 导致扫描结果为空、功能看起来没反应。
 *
 * 这里用「实际能看到的包数量」做真实探测：数量过少即认为被限制，
 * 由调用方给出提示，引导用户去系统设置里打开「获取应用列表」。
 */
object AppListPermission {

    /**
     * 判定阈值。正常手机至少有几十个包，被限制时通常是 1～3 个，
     * 两者差距极大，20 这个阈值足够安全，不会误报。
     */
    private const val MIN_EXPECTED_PACKAGES = 20

    /** 当前能看到的已安装应用数量，调用失败返回 -1 */
    fun visiblePackageCount(context: Context): Int = runCatching {
        context.packageManager.getInstalledApplications(0).size
    }.getOrDefault(-1)

    /** 包可见性是否正常 */
    fun isGranted(context: Context): Boolean = visiblePackageCount(context) >= MIN_EXPECTED_PACKAGES
}