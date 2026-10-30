package cn.baymaxawa.xmsfnotichannel

import android.app.Application
import cn.baymaxawa.xmsfnotichannel.prefs.ConfigState
import cn.baymaxawa.xmsfnotichannel.prefs.OptionRegistry
import cn.baymaxawa.xmsfnotichannel.prefs.PrefsStore
import cn.baymaxawa.xmsfnotichannel.ui.screen.features.featureSpecs
import cn.baymaxawa.xmsfnotichannel.utils.RuleRepository
import cn.baymaxawa.xmsfnotichannel.xposed.HookStatusStore
import cn.baymaxawa.xmsfnotichannel.xposed.SafeModeReader
import cn.baymaxawa.xmsfnotichannel.xposed.XposedServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TemplateApp : Application() {

    /** 进程级协程域：只跑启动同步这类一次性后台任务，不随界面销毁 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PrefsStore.init(this)
        ConfigState.init(this)
        OptionRegistry.registerAll(featureSpecs())
        HookStatusStore.initialize(this)
        SafeModeReader.initialize(this)
        XposedServiceManager.init()
        // 规则仓库要在任何匹配发生前就绪，hook 侧读的是同一份文件
        RuleRepository.init(this)
        // 每次启动静默同步一次云端规则（12 小时内同步过则自动跳过）。
        // 放后台跑，失败不打扰用户——设置页里仍可手动同步。
        appScope.launch {
            runCatching { RuleRepository.autoSync(applicationContext) }
        }
    }
}