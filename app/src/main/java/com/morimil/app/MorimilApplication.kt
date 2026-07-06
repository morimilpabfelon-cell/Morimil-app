package com.morimil.app

import android.app.Application
import com.morimil.app.net.NativeBrowserRuntime

class MorimilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeBrowserRuntime.install(this)
    }

    val container: MorimilAppContainer by lazy {
        MorimilAppContainer(this)
    }
}
