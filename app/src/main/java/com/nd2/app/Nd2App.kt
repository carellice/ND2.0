package com.nd2.app

import android.app.Application
import com.nd2.app.core.Channels
import com.nd2.app.data.Store

class Nd2App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Channels.ensure(this)
    }
}
