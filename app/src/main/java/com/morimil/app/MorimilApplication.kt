package com.morimil.app

import android.app.Application

class MorimilApplication : Application() {
    val container: MorimilAppContainer by lazy {
        MorimilAppContainer(this)
    }
}
