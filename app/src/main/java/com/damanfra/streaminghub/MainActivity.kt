package com.damanfra.streaminghub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.damanfra.streaminghub.ui.StreamingHubApp
import com.damanfra.streaminghub.ui.StreamingHubTheme
import com.damanfra.streaminghub.ui.isTvDevice

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tv = isTvDevice(this)
        setContent {
            StreamingHubTheme(tv) {
                StreamingHubApp()
            }
        }
    }
}
