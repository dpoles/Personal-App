package com.dpoles.patrimonio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
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
        handle(intent)
        setContent {
            AppTheme {
                val db by vm.db.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::prepareImport)
                }
                PatrimonioApp(db, vm) {
                    picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream", "*/*"))
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
