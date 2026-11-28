package de.mediclever.schichtclever.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Export {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private fun ts(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: ""
    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun einsaetzeCsv(data: AppData): String {
        val custom = data.settings.customSkills
        val sb = StringBuilder("Alarmzeit;Schicht;Kategorie;Dringlichkeit;Ausgang;Maßnahmen (selbst);Maßnahmen (assistiert);Notiz\n")
        data.einsaetze.sortedBy { it.alarmAt }.forEach { e ->
            val shift = data.shifts.firstOrNull { it.id == e.shiftId }
            fun names(inv: Involvement) = e.measures.filter { it.involvement == inv }
                .joinToString(", ") { Catalog.byId(it.skillId, custom)?.name ?: it.skillId }
            sb.append(listOf(
                ts(e.alarmAt), shift?.type?.label ?: "", e.category.label, e.urgency.label, e.outcome?.label ?: "",
                esc(names(Involvement.SELBST)), esc(names(Involvement.ASSISTIERT)), esc(e.note),
            ).joinToString(";")).append('\n')
        }
        sb.append("\nSchichtbeginn;Schichtende;Typ;Fahrzeug/Station;Einsätze;Bewertung;Notiz\n")
        data.shifts.sortedBy { it.startAt }.forEach { s ->
            sb.append(listOf(
                ts(s.startAt), ts(s.endAt), s.type.label, esc(s.unit),
                data.einsaetze.count { it.shiftId == s.id }.toString(), s.rating?.toString() ?: "", esc(s.note),
            ).joinToString(";")).append('\n')
        }
        sb.append("\nSkill;Datum;Art\n")
        data.practices.sortedBy { it.at }.forEach { p ->
            sb.append(listOf(Catalog.byId(p.skillId, custom)?.name ?: p.skillId, ts(p.at), p.kind.label).joinToString(";")).append('\n')
        }
        return sb.toString()
    }

    fun share(context: Context, data: AppData) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "schichtclever-export.csv")
        // BOM, damit Excel Umlaute richtig erkennt
        file.writeText("﻿" + einsaetzeCsv(data))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SchichtClever Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
