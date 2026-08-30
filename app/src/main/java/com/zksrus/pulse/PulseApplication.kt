package com.zksrus.pulse

import android.app.Application

class PulseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PulseApplication
            private set
    }
}
