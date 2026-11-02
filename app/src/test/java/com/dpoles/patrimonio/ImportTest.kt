package com.dpoles.patrimonio

import com.dpoles.patrimonio.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Costruisce in memoria un .xlsx con la struttura del foglio reale (dati inventati) e prova l'import. */
class ImportTest {
    private class Sheet(val name: String, val rows: List<List<Any?>>)

    private fun col(i: Int): String { var n = i; var s = ""; do { s = ('A' + n % 26) + s; n = n / 26 - 1 } while (n >= 0); return s }

    private fun xlsx(sheets: List<Sheet>): ByteArray {
        val strings = ArrayList<String>()
        fun sid(s: String) = strings.indexOf(s).let { if (it >= 0) it else { strings += s; strings.size - 1 } }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
            val wbSheets = sheets.mapIndexed { i, s -> """<sheet name="${s.name}" sheetId="${i + 1}" state="visible" r:id="rId${i + 3}"/>""" }.joinToString("")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$wbSheets</sheets></workbook>""")
            val rels = sheets.mapIndexed { i, _ -> """<Relationship Id="rId${i + 3}" Type="x" Target="/xl/worksheets/sheet${i + 1}.xml"/>""" }.joinToString("")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
            sheets.forEachIndexed { i, s ->
                val rows = s.rows.mapIndexed { r, row ->
                    val cells = row.mapIndexedNotNull { c, v ->
                        val ref = "${col(c)}${r + 1}"
                        when (v) {
                            null -> null
                            is String -> """<c r="$ref" t="s"><v>${sid(v)}</v></c>"""
                            else -> """<c r="$ref"><v>$v</v></c>"""
                        }
                    }.joinToString("")
                    """<row r="${r + 1}">$cells</row>"""
                }.joinToString("")
                put("xl/worksheets/sheet${i + 1}.xml", """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""")
            }
            put("xl/sharedStrings.xml", """<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">${strings.joinToString("") { "<si><t>$it</t></si>" }}</sst>""")
        }
        return bos.toByteArray()
    }

    @Test
    fun importa_i_quattro_fogli() {
        val hdr = listOf("Y", "M", "T", "IN", "OUT", "MOVE DEP", "MOVE INV", "BAL.", "BAL. TOT. ", "C/C", "MOVE DEP", "DEP", "INT", "PRG INT", "SANTANDER", "INT", "PRG INT", "AZIONI", "MONETARI", "%", "ETC", "OBBL", "PRG YEAR", "PRG NET", "PRG NET AVG", "TOT NO AZ", "TOT AZ", "% A", "% M", "% C", "% O", "% L", "TOT")
        fun bal(y: Int, m: Int, t: String, tot: Double) = MutableList<Any?>(33) { null }.also {
            it[0] = y.toDouble(); it[1] = m.toDouble(); it[2] = t; it[3] = 2600.0; it[4] = 1000.0
            it[14] = 50000.0; it[17] = 30000.0; it[18] = 10000.0; it[32] = tot
        }
        val balRows = listOf(hdr, bal(2026, 9, "A", 95000.0), bal(2026, 10, "C", 96000.0), bal(2026, 11, "E", 97000.0))

        val rifHdr = listOf("Data", "Importo", "Prg", "Pieno", "Prezzo", "KM", "GG", "LT")
        val rif = listOf(
            rifHdr,
            listOf(46151.0, 0.0, 0.0, "No", 1.0, 119228.0, 12.0, 0.0),
            listOf(46162.0, 80.43, 80.43, "Si", 1.939, 119472.0, 60.0, 41.48),
            listOf(null, null, null, null, null, 121937.0),
            listOf(null),
            listOf("Auto 2025"),
            rifHdr,
            listOf(45787.0, 77.7, 77.7, "Si", 1.515, 110787.0, 53.0, 51.29),
            listOf(45850.0, 81.09, 158.8, "Si", 1.627, 111851.0, 39.0, 49.84),
        )
        val azHdr = listOf("Nome Investimento", "ISIN", "Http", "Data Inv", "Prz Acq", "Qtà", "Comm", "Val Acq", "Bollo", "Prz Act")
        val azioni = listOf(
            listOf(null), azHdr,
            listOf(null, null, null, null, null, null, null, null, null, null, null, null, 4500.2),
            listOf("VWCE", "IE00", "http", 45679.0, 137.1, 36.0, 9.4, 4935.0, 0.0, 168.9),
            listOf("VWCE", "IE00", "http", 45812.0, 129.0, 39.0, 9.5, 5032.0, 0.0, 168.9),
            listOf("TOTALE INVESTITO", null, null, null, null, 75.0),
        )
        val mon = listOf(listOf(null), azHdr, listOf("XEON", "LU00", "http", 46255.0, 150.0, 89.0, 0.0, 13350.0, 0.0, 150.7), listOf("TOTALE INVESTITO"))

        val bytes = xlsx(listOf(Sheet("Bal", balRows), Sheet("Rif", rif), Sheet("Azioni", azioni), Sheet("Monetari", mon), Sheet("Altro", listOf(listOf("x")))))
        val grids = XlsxReader.read(ByteArrayInputStream(bytes), ExcelImport.SHEETS)
        val r = ExcelImport.build(grids)

        assertEquals("mesi reali (A e C, senza stime)", 2, r.months)
        assertEquals("veicoli (un blocco ciascuno)", 2, r.vehicles)
        assertEquals("rifornimenti con importo", 3, r.fills)
        assertEquals("acquisti", 3, r.lots)
        // il totale dell'app deve coincidere con la colonna TOT del foglio
        assertEquals(96000.0, r.db.balRows().last().total, 0.01)
        assertEquals(2, r.db.portfolio(AZIONI).lots.size)
        assertEquals(168.9, r.db.portfolio(AZIONI).instruments.single().price, 0.001)
        val auto = r.db.vehicleList().first { it.name.startsWith("Auto 2026") || it.name.startsWith("Auto") && it.forecast != null }
        assertNotNull(auto.forecast)
        // serializzazione e ricarica
        val again = Db.fromJson(r.db.toJson())
        assertEquals(r.db.months.size, again.months.size)
        assertEquals(r.db.vehicles.size, again.vehicles.size)
    }
}
