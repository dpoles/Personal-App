package com.dpoles.patrimonio.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lettore minimale di file .xlsx (zip + XML). Restituisce, per ogni foglio richiesto, la stessa
 * griglia che arriverebbe dalla Sheets API: numeri veri, date come numeri seriali, testo come String.
 * Tutto avviene sul telefono: il file non viene inviato da nessuna parte.
 */
object XlsxReader {

    fun read(input: InputStream, wanted: Collection<String>): Map<String, Grid> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name
                if (n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
                ) files[n] = zip.readBytes()
            }
        }
        val workbook = files["xl/workbook.xml"] ?: error("Non sembra un file Excel (.xlsx)")
        val rels = files["xl/_rels/workbook.xml.rels"] ?: error("File Excel incompleto")

        // rId -> percorso del foglio
        val target = HashMap<String, String>()
        parse(rels) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val t = p.getAttributeValue(null, "Target")
                target[p.getAttributeValue(null, "Id")] = if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
            }
        }
        // nome foglio -> percorso
        val sheets = LinkedHashMap<String, String>()
        parse(workbook) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val rid = p.getAttributeValue(null, "r:id")
                val path = target[rid]
                if (path != null) sheets[p.getAttributeValue(null, "name")] = path
            }
        }

        val shared = files["xl/sharedStrings.xml"]?.let(::readShared) ?: emptyList()
        val out = HashMap<String, Grid>()
        for (name in wanted) {
            val bytes = sheets[name]?.let { files[it] } ?: error("Foglio '$name' non trovato nel file")
            out[name] = readSheet(bytes, shared)
        }
        return out
    }

    private inline fun parse(bytes: ByteArray, crossinline onEvent: (XmlPullParser) -> Unit) {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        while (p.eventType != XmlPullParser.END_DOCUMENT) { onEvent(p); p.next() }
    }

    private fun readShared(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var sb: StringBuilder? = null
        var inT = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> sb = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { out += sb.toString(); sb = null } }
            }
            p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var c = 0
        for (ch in ref) { if (ch in 'A'..'Z') c = c * 26 + (ch - 'A' + 1) else break }
        return c - 1
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): Grid {
        val rows = ArrayList<MutableList<Any?>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var ref = ""; var type: String? = null; var text: StringBuilder? = null; var inV = false; var inT = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "c" -> { ref = p.getAttributeValue(null, "r") ?: ""; type = p.getAttributeValue(null, "t"); text = StringBuilder() }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) text?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = text?.toString().orEmpty()
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
                        text = null
                    }
                }
            }
            p.next()
        }
        return rows
    }
}
