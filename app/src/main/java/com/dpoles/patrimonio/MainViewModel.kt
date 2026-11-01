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
import android.net.Uri
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

    /** Legge il file Excel sul telefono e mostra un riepilogo; i dati cambiano solo dopo la conferma. */
    fun prepareImport(uri: Uri) {
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
            }
        }
    }
    fun confirmImport() { importPreview.value?.let { p -> mutate { p.result.db } }; importPreview.value = null }
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
    fun setPrice(kind: String, name: String, price: Double) = mutate { d ->
        d.copy(prices = d.prices + (d.priceKey(kind, name) to price))
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
