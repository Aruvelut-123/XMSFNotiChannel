package com.baymaxawa.xmsfnotichannel

import android.app.Application
import com.baymaxawa.xmsfnotichannel.prefs.ConfigState
import com.baymaxawa.xmsfnotichannel.prefs.OptionRegistry
import com.baymaxawa.xmsfnotichannel.prefs.PrefsStore
import com.baymaxawa.xmsfnotichannel.ui.screen.features.featureSpecs
import com.baymaxawa.xmsfnotichannel.xposed.HookStatusStore
import com.baymaxawa.xmsfnotichannel.xposed.SafeModeReader
import com.baymaxawa.xmsfnotichannel.xposed.XposedServiceManager

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
