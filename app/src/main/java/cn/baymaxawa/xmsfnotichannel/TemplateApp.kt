package cn.baymaxawa.xmsfnotichannel

import android.app.Application
import cn.baymaxawa.xmsfnotichannel.prefs.ConfigState
import cn.baymaxawa.xmsfnotichannel.prefs.OptionRegistry
import cn.baymaxawa.xmsfnotichannel.prefs.PrefsStore
import cn.baymaxawa.xmsfnotichannel.ui.screen.features.featureSpecs
import cn.baymaxawa.xmsfnotichannel.xposed.HookStatusStore
import cn.baymaxawa.xmsfnotichannel.xposed.SafeModeReader
import cn.baymaxawa.xmsfnotichannel.xposed.XposedServiceManager

class TemplateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PrefsStore.init(this)
        ConfigState.init(this)
        OptionRegistry.registerAll(featureSpecs())
        HookStatusStore.initialize(this)
        SafeModeReader.initialize(this)
        XposedServiceManager.init()
    }
}
