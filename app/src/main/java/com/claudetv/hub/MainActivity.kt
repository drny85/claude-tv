package com.claudetv.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.ClaudeTvApp
import com.claudetv.hub.ui.theme.ClaudeTvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaudeTvTheme {
                ClaudeTvApp(vm)
            }
        }
    }
}
