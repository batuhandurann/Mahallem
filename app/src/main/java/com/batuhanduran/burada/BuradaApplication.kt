package com.batuhanduran.burada

import android.app.Application
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.remote.PushTokenLifecycle

class BuradaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseServices.initialize(this)
        PushTokenLifecycle.start(this)
    }
}
