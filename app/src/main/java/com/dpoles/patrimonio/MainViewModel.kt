package com.dpoles.patrimonio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dpoles.patrimonio.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.random.Random

data class ImportPreview(val result: ExcelImport.Result, val replacing: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "data.json")
    private val lock = Mutex()
    private val _db = MutableStateFlow(load())
    val db: StateFlow<Db> = _db

    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val message = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)
    val googlePending = MutableStateFlow<PendingIntent?>(null)
    val quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())   // "kind|nome" -> quotazione live
    val quoteError = MutableStateFlow<String?>(null)
    private var quoting = false

    /** Aggiorna le quotazioni in tempo reale degli strumenti che hanno un simbolo; il prezzo attuale viene salvato. */
    fun refreshQuotes() {
        if (quoting) return
        val d = _db.value
        val targets = listOf(AZIONI, MONETARI).flatMap { k ->
            d.portfolio(k).instruments.mapNotNull { i -> i.symbol?.let { Triple(d.priceKey(k, i.name), i.name, it) } }
        }
        if (targets.isEmpty()) return
        quoting = true
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    coroutineScope { targets.map { t -> async { t to runCatching { QuoteApi.fetch(t.third) } } }.awaitAll() }
                }
                val ok = HashMap<String, Quote>(); val errors = mutableListOf<String>()
                for ((t, r) in res) {
                    val q = r.getOrNull()
                    when {
                        q == null -> errors += r.exceptionOrNull()?.message ?: "${t.third}: non disponibile"
                        q.currency != "EUR" -> errors += "${t.third} è quotato in ${q.currency}: non convertito"
                        else -> ok[t.first] = q
                    }
                }
                if (ok.isNotEmpty()) {
                    quotes.update { it + ok }
                    mutate { db -> db.copy(prices = db.prices + ok.mapValues { it.value.price }, quoteAt = db.quoteAt + ok.mapValues { it.value.timeMs }) }
                }
                quoteError.value = errors.firstOrNull()
            } finally {
                quoting = false
            }
        }
    }

    private val client by lazy { Identity.getAuthorizationClient(getApplication<Application>()) }
    private val request by lazy {
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SheetsApi.SCOPE))).build()
    }

    /** Legge il documento Balance da Google Sheets (sola lettura) e mostra il riepilogo dell'import. */
    fun importFromGoogle() {
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                val res = client.authorize(request).await()
                if (res.hasResolution()) {
                    googlePending.value = res.pendingIntent   // schermata di consenso: la apre l'Activity
                    importing.value = false
                } else {
                    downloadFromGoogle(res.accessToken ?: error("Token non disponibile"))
                }
            } catch (e: Throwable) { fail(e) }
        }
    }

    fun onConsentResult(data: Intent?) {
        googlePending.value = null
        importing.value = true
        viewModelScope.launch {
            try {
                val res = client.getAuthorizationResultFromIntent(data)
                downloadFromGoogle(res.accessToken ?: error("Accesso non concesso"))
            } catch (e: Throwable) { fail(e) }
        }
    }
    fun consentLaunched() { googlePending.value = null }

    private suspend fun downloadFromGoogle(token: String) {
        try {
            val res = withContext(Dispatchers.IO) { ExcelImport.build(SheetsApi.parse(SheetsApi.fetchRaw(token))) }
            val d = _db.value
            importPreview.value = ImportPreview(res, d.months.isNotEmpty() || d.fills.isNotEmpty() || d.lots.isNotEmpty())
            importing.value = false
        } catch (e: Throwable) { fail(e) }
    }

    private fun fail(e: Throwable) {
        importing.value = false
        val m = e.message ?: e.javaClass.simpleName
        message.value = when {
            "UNREGISTERED_ON_API_CONSOLE" in m -> "Google non riconosce l'app. Controlla su Google Cloud il client OAuth Android: nome pacchetto com.dpoles.patrimonio e impronta SHA-1 della chiave di debug.\n\n($m)"
            e is AuthExpired -> "Sessione scaduta: riprova l'importazione."
            else -> "Import non riuscito: $m"
        }
    }

    /** Legge il file Excel sul telefono e mostra un riepilogo; i dati cambiano solo dopo la conferma. */
    fun prepareImport(uri: Uri) {
        importing.value = true
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val grids = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        XlsxReader.read(it, ExcelImport.SHEETS)
                    }
                    ExcelImport.build(grids)
                }
                val d = _db.value
                importPreview.value = ImportPreview(res, d.months.isNotEmpty() || d.fills.isNotEmpty() || d.lots.isNotEmpty())
            } catch (e: Exception) {
                message.value = "Import non riuscito: " + (e.message ?: e.javaClass.simpleName)
            } catch (e: Throwable) {
                message.value = "Import non riuscito: " + e.javaClass.simpleName
            } finally {
                importing.value = false
            }
        }
    }
    fun confirmImport() {
        importPreview.value?.let { p ->
            mutate { p.result.db }
            message.value = "Importati ${p.result.months} mesi, ${p.result.vehicles} veicoli (${p.result.fills} rifornimenti) e ${p.result.lots} acquisti."
        }
        importPreview.value = null
    }
    fun cancelImport() { importPreview.value = null }
    fun dismissMessage() { message.value = null }

    fun saveVehicle(v: VehicleE) = mutate { d -> d.copy(vehicles = d.vehicles.upsert(v) { it.id }) }
    fun deleteVehicle(id: Long) = mutate { d ->
        d.copy(vehicles = d.vehicles.filter { it.id != id }, fills = d.fills.filter { it.vehicleId != id })
    }

    private fun load(): Db = runCatching { Db.fromJson(file.readText()) }.getOrDefault(Db())

    private fun mutate(f: (Db) -> Db) {
        val next = _db.updateAndGet(f)
        viewModelScope.launch(Dispatchers.IO) {
            lock.withLock {
                val tmp = File(file.parentFile, "data.json.tmp")
                tmp.writeText(next.toJson())
                tmp.renameTo(file)
            }
        }
    }

    private fun newId() = Random.nextLong(1, Long.MAX_VALUE)
    private fun <T> List<T>.upsert(item: T, id: (T) -> Long) =
        if (any { id(it) == id(item) }) map { if (id(it) == id(item)) item else it } else this + item

    fun saveMonth(m: MonthE) = mutate { d ->
        val e = if (m.id == 0L) m.copy(id = newId()) else m
        d.copy(months = d.months.upsert(e) { it.id })
    }
    fun deleteMonth(id: Long) = mutate { d -> d.copy(months = d.months.filter { it.id != id }) }

    fun saveLot(l: LotE) = mutate { d ->
        val e = if (l.id == 0L) l.copy(id = newId()) else l
        d.copy(lots = d.lots.upsert(e) { it.id })
    }
    fun deleteLot(id: Long) = mutate { d -> d.copy(lots = d.lots.filter { it.id != id }) }
    fun setPrice(kind: String, name: String, price: Double, symbol: String, taxRate: Double) = mutate { d ->
        val k = d.priceKey(kind, name)
        val sym = symbol.trim().uppercase()
        d.copy(
            prices = d.prices + (k to price), taxRates = d.taxRates + (k to taxRate),
            symbols = if (sym.isEmpty()) d.symbols - k else d.symbols + (k to sym),
        )
    }

    /** Se [newVehicle] non è nullo viene creato un veicolo nuovo e il rifornimento gli viene assegnato. */
    fun saveFill(f: FillE, newVehicle: String?) = mutate { d ->
        var vehicles = d.vehicles
        var vid = f.vehicleId
        if (newVehicle != null) { vid = newId(); vehicles = vehicles + VehicleE(vid, newVehicle.trim()) }
        val e = f.copy(id = if (f.id == 0L) newId() else f.id, vehicleId = vid)
        d.copy(vehicles = vehicles, fills = d.fills.upsert(e) { it.id })
    }
    fun deleteFill(id: Long) = mutate { d -> d.copy(fills = d.fills.filter { it.id != id }) }
}
