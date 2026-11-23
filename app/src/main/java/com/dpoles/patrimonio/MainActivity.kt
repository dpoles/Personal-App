package com.dpoles.patrimonio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.dpoles.patrimonio.ui.AppTheme
import com.dpoles.patrimonio.ui.PatrimonioApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            AppTheme {
                val db by vm.db.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::prepareImport)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(Unit) {
                    // quotazioni in tempo reale: all'apertura e poi ogni minuto, finché l'app è visibile
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) { vm.refreshQuotes(); delay(60_000) }
                    }
                }
                val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    vm.onConsentResult(it.data)
                }
                val pending by vm.googlePending.collectAsStateWithLifecycle()
                LaunchedEffect(pending) {
                    pending?.let { consent.launch(IntentSenderRequest.Builder(it).build()); vm.consentLaunched() }
                }
                PatrimonioApp(db, vm) {
                    picker.launch(arrayOf("*/*"))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Apertura del file Excel dal gestore file, da Drive o da "Condividi". */
    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        val uri: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> i.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let(vm::prepareImport)
    }
}
