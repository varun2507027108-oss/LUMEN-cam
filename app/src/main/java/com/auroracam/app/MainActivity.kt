package com.auroracam.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.auroracam.app.ui.CameraScreen
import com.auroracam.app.ui.PermissionGate
import com.auroracam.app.ui.theme.AuroraCamTheme
import com.auroracam.app.ui.theme.DarkBackground

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Keep screen on while camera is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AuroraCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    PermissionGate {
                        CameraScreen()
                    }
                }
            }
        }
    }
}
