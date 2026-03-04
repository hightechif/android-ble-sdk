package com.edts.blesample.di

import com.edts.blesample.ui.MainViewModel
import com.edts.blesdk.core.BleManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { BleManager(androidContext()) }
    viewModel { MainViewModel(androidApplication(), get()) }
}
