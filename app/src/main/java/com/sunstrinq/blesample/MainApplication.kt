package com.sunstrinq.blesample

import android.app.Application
import com.sunstrinq.blesample.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@MainApplication)
            modules(appModule)
        }
    }
}
