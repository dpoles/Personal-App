package com.dpoles.patrimonio.data

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Lettore minimale di file .xlsx (zip + XML). Restituisce, per ogni foglio richiesto, la stessa
 * griglia che arriverebbe dalla Sheets API: numeri veri, date come numeri seriali, testo come String.
 * Usa solo SAX (presente sia su Android sia sulla JVM, quindi testabile). Tutto avviene sul telefono.
 */
object XlsxReader {

    fun read(input: InputStream, wanted: Collection<String>): Map<String, Grid> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                ) files[n] = zip.readBytes()
            }
        }
        val workbook = files["xl/workbook.xml"] ?: error("Non sembra un file Excel (.xlsx): manca xl/workbook.xml")
        val rels = files["xl/_rels/workbook.xml.rels"] ?: error("File Excel incompleto: mancano le relazioni dei fogli")

        // id relazione -> percorso del file del foglio
        val target = HashMap<String, String>()
        sax(rels, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                if (q == "Relationship") {
                    val t = a.getValue("Target") ?: return
                    val id = a.getValue("Id") ?: return
                    target[id] = if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
                }
            }
        })
        // nome foglio -> percorso
        val sheets = LinkedHashMap<String, String>()
        sax(workbook, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                if (q == "sheet") {
                    val name = a.getValue("name") ?: return
                    val rid = a.getValue("r:id") ?: return
                    target[rid]?.let { sheets[name] = it }
                }
            }
        })

        val shared = files["xl/sharedStrings.xml"]?.let(::readShared) ?: emptyList()
        val out = HashMap<String, Grid>()
        for (name in wanted) {
            val path = sheets[name] ?: error("Foglio '$name' non trovato nel file (fogli presenti: ${sheets.keys.take(6).joinToString()}…)")
            val bytes = files[path] ?: error("Contenuto del foglio '$name' mancante")
            out[name] = readSheet(bytes, shared)
        }
        return out
    }

    private fun sax(bytes: ByteArray, h: DefaultHandler) {
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        f.newSAXParser().parse(ByteArrayInputStream(bytes), h)
    }

    private fun readShared(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        var sb: StringBuilder? = null
        var inT = false
        sax(bytes, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                when (q) { "si" -> sb = StringBuilder(); "t" -> inT = true }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb?.append(ch, start, length) }
            override fun endElement(u: String?, l: String?, q: String) {
                when (q) { "t" -> inT = false; "si" -> { out += sb.toString(); sb = null } }
            }
        })
        return out
    }

    private fun colIndex(ref: String): Int {
        var c = 0
        for (ch in ref) { if (ch in 'A'..'Z') c = c * 26 + (ch - 'A' + 1) else break }
        return c - 1
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): Grid {
        val rows = ArrayList<MutableList<Any?>>()
        var ref = ""; var type: String? = null; var text = StringBuilder(); var inV = false; var inT = false
        sax(bytes, object : DefaultHandler() {
            override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
                when (q) {
                    "c" -> { ref = a.getValue("r") ?: ""; type = a.getValue("t"); text = StringBuilder() }
                    "v" -> inV = true
                    "t" -> inT = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inV || inT) text.append(ch, start, length) }
            override fun endElement(u: String?, l: String?, q: String) {
                when (q) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = text.toString()
                        val value: Any? = when {
                            raw.isEmpty() -> null
                            type == "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1)?.takeIf { it.isNotEmpty() }
                            type == "str" || type == "inlineStr" || type == "e" -> raw
                            type == "b" -> raw == "1"
                            else -> raw.toDoubleOrNull() ?: raw
                        }
                        if (value != null) {
                            val r = ref.dropWhile { it in 'A'..'Z' }.toIntOrNull()?.minus(1) ?: 0
                            val c = colIndex(ref)
                            while (rows.size <= r) rows.add(ArrayList())
                            val row = rows[r]
                            while (row.size <= c) row.add(null)
                            row[c] = value
                        }
                    }
                }
            }
        })
        return rows
    }
}
