package com.sunstrinq.blesample.di

import com.sunstrinq.blesample.ui.MainViewModel
import com.sunstrinq.blesdk.core.BleManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { BleManager(androidContext()) }
    viewModel { MainViewModel(androidApplication(), get()) }
}
