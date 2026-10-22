package com.ralledger

import android.app.Application
import com.ralledger.di.AppContainer

class RalLedgerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
