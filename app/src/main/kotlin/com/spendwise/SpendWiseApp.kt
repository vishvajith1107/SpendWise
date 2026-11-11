package com.spendwise

import android.app.Application
import com.spendwise.receiver.MidnightWidgetReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpendWiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MidnightWidgetReceiver.scheduleNextMidnightUpdate(this)
    }
}
