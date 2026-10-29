package cn.baymaxawa.xmsfnotichannel.hook.dexkit

import cn.baymaxawa.xmsfnotichannel.hook.base.PackageTarget

/**
 * DexKit 门面。
 */
object DexKit {

    fun ready(target: PackageTarget, tag: String) = DexKitCacheManager.init(target, tag)

    fun <T> findMember(key: String, finder: IDexKit): T? = DexKitCacheManager.findMember(key, finder)

    fun <T> findMemberList(key: String, finder: IDexKitList): List<T>? =
        DexKitCacheManager.findMemberList(key, finder)

    fun close() = DexKitCacheManager.releaseBridge()

    fun clearAllCache() = DexKitCacheManager.clearAllCache()
}
