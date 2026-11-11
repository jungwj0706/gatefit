package com.gatefit.app

import android.app.Application

class GatefitApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
