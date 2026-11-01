package com.dpoles.patrimonio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpoles.patrimonio.ui.AppTheme
import com.dpoles.patrimonio.ui.PatrimonioApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    vm.onConsentResult(it.data)
                }
                PatrimonioApp(
                    ui = ui,
                    onRefresh = vm::refresh,
                    onLogin = {
                        ui.pending?.let { consent.launch(IntentSenderRequest.Builder(it).build()) } ?: vm.refresh()
                    },
                )
            }
        }
    }
}
