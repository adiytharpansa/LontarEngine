package com.lontar.app

import android.app.Application
import com.bunny.engine.lontar.LontarEngine
import okhttp3.OkHttpClient

class LontarApplication : Application() {
    val backend: LontarEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LontarEngine(
            httpClient = OkHttpClient(),
            remoteConfigEnabled = false,
        )
    }
}
