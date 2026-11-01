package com.dpoles.patrimonio

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dpoles.patrimonio.data.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class Ui(
    val data: AppData? = null,
    val loading: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
    val pending: PendingIntent? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val cache = Cache(app)
    private val client = Identity.getAuthorizationClient(app)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SheetsApi.SCOPE))).build()

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    init {
        cache.load()?.let { (json, ts) ->
            runCatching { buildAppData(SheetsApi.parse(json), ts) }.onSuccess { d -> _ui.update { it.copy(data = d) } }
        }
        refresh()
    }

    fun refresh() {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val res = client.authorize(request).await()
                if (res.hasResolution()) {
                    _ui.update { it.copy(loading = false, needsLogin = true, pending = res.pendingIntent) }
                } else {
                    download(res.accessToken ?: error("Token non disponibile"))
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Errore sconosciuto") }
            }
        }
    }

    /** Chiamato quando l'utente ha concesso l'accesso nella schermata di consenso di Google. */
    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val res = client.getAuthorizationResultFromIntent(data)
                download(res.accessToken ?: error("Accesso non concesso"))
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Accesso annullato") }
            }
        }
    }

    private suspend fun download(token: String) {
        val json = withContext(Dispatchers.IO) { SheetsApi.fetchRaw(token) }
        val now = System.currentTimeMillis()
        val data = withContext(Dispatchers.Default) { buildAppData(SheetsApi.parse(json), now) }
        cache.save(json)
        _ui.update { it.copy(data = data, loading = false, needsLogin = false, pending = null, error = null) }
    }
}
