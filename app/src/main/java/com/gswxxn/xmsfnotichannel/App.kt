package com.gswxxn.xmsfnotichannel

import android.app.Application
import com.gswxxn.xmsfnotichannel.utils.FrameworkStatus

/**
 * 应用入口。
 *
 * libxposed 下模块应用不再被自身 hook，框架通过 XposedProvider 把 XposedService 送进来，
 * 因此需要在这里注册服务监听，界面才能查询激活状态。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        FrameworkStatus.init(this)
    }
}