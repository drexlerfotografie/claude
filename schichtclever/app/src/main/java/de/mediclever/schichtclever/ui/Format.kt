package de.mediclever.schichtclever.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val de = Locale.GERMANY
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", de)
private val dateFmt = DateTimeFormatter.ofPattern("EE, dd.MM.", de)
private val dateLongFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", de)
private val dateShortFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", de)

private fun Long.zoned() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

fun Long.time(): String = zoned().format(timeFmt)
fun Long.date(): String = zoned().format(dateFmt)
fun Long.dateLong(): String = zoned().format(dateLongFmt)
fun Long.dateShort(): String = zoned().format(dateShortFmt)

/** Dauer als h:mm, z. B. 4:07 */
fun durationHm(ms: Long): String {
    val m = (ms.coerceAtLeast(0) / 60_000)
    return "%d:%02d".format(m / 60, m % 60)
}

fun greeting(hour: Int): String = when (hour) {
    in 5..10 -> "Guten Morgen."
    in 11..17 -> "Hallo."
    in 18..22 -> "Guten Abend."
    else -> "Gute Nacht."
}

fun daysAgoText(days: Long?): String = when (days) {
    null -> "noch nie"
    0L -> "heute"
    1L -> "gestern"
    in 2..59 -> "vor $days Tagen"
    else -> "vor ${days / 30} Monaten"
}
