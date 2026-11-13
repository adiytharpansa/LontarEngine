package com.lontar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lontar.app.ui.LontarApp
import com.lontar.app.ui.LontarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LontarTheme {
                LontarApp()
            }
        }
    }
}
