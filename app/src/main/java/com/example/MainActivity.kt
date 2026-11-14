package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.StormLauncherTheme
import com.stormrevolution.launcher.LauncherViewModel
import com.stormrevolution.launcher.ui.StormLauncherRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StormLauncherTheme {
                val launcherViewModel: LauncherViewModel = viewModel()
                StormLauncherRoot(viewModel = launcherViewModel)
            }
        }
    }
}
