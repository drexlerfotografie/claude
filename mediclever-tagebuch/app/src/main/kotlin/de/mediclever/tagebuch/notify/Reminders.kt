package de.mediclever.tagebuch.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.mediclever.tagebuch.MainActivity
import de.mediclever.tagebuch.R
import de.mediclever.tagebuch.TagebuchApp
import de.mediclever.tagebuch.core.goals.GoalState
import de.mediclever.tagebuch.core.goals.goalTracker
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.stats.importantDayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Ein geplanter Hinweis. kind + id bestimmen beim Ausloesen, was gezeigt wird. */
data class Planned(val kind: String, val id: String, val at: LocalDateTime) {
    val code: Int get() = (kind + "|" + id + "|" + at.toLocalDate()).hashCode()
}

object Plan {
    const val ENTRY = "eintrag"
    const val GOAL = "ziel"
    const val DAY = "tag"
    const val REPORT_WEEK = "wochenbericht"
    const val REPORT_MONTH = "monatsbericht"
    private val MORNING: LocalTime = LocalTime.of(9, 0)

    /** Alle Hinweise der naechsten zwei Tage ab now. Reine Rechnung, deshalb gut pruefbar. */
    fun upcoming(d: Diary, now: LocalDateTime): List<Planned> {
        val out = mutableListOf<Planned>()
        for (offset in 0L..2L) {
            val date = now.toLocalDate().plusDays(offset)
            fun at(minute: Int) = date.atTime(minute / 60, minute % 60)
            for (r in d.reminders) if (r.active) out += Planned(ENTRY, r.id, at(r.minuteOfDay))
            for (g in d.goals) {
                val m = g.reminderMinuteOfDay ?: continue
                if (g.archivedAt == null && g.startDate <= date.toString() && (g.endDate == null || date.toString() <= g.endDate!!)) out += Planned(GOAL, g.id, at(m))
            }
            for (day in d.importantDays) {
                val wanted = (if (day.remindOnDay) listOf(0) else emptyList()) + day.remindDaysBefore
                for (before in wanted.distinct()) {
                    val info = importantDayInfo(day, date.plusDays(before.toLong())) ?: continue
                    if (info.isToday) out += Planned(DAY, day.id + "#" + before, date.atTime(MORNING))
                }
            }
            if (d.settings.reportNotifications && d.entries.isNotEmpty()) {
                if (date.dayOfWeek == DayOfWeek.of(d.settings.weekStart.coerceIn(1, 7))) out += Planned(REPORT_WEEK, "w", date.atTime(MORNING))
                if (date.dayOfMonth == 1) out += Planned(REPORT_MONTH, "m", date.atTime(MORNING))
            }
        }
        return out.filter { it.at > now }.sortedBy { it.at }
    }
}

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store = context.getSharedPreferences("geplant", Context.MODE_PRIVATE)

    private fun intent(p: Planned): PendingIntent = PendingIntent.getBroadcast(
        context, p.code,
        Intent(context, ReminderReceiver::class.java).setAction("de.mediclever.tagebuch.HINWEIS").putExtra("kind", p.kind).putExtra("id", p.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Plant alles neu. Vorher gesetzte Hinweise, die nicht mehr gelten, werden entfernt. */
    fun reschedule(d: Diary, now: LocalDateTime = LocalDateTime.now()) {
        val plan = Plan.upcoming(d, now)
        val old = store.getStringSet("codes", emptySet()).orEmpty()
        val keep = plan.map { it.code.toString() }.toSet()
        for (code in old - keep) {
            PendingIntent.getBroadcast(context, code.toInt(), Intent(context, ReminderReceiver::class.java).setAction("de.mediclever.tagebuch.HINWEIS"), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
        }
        for (p in plan) {
            val millis = p.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Ohne Sonderrecht fuer exakte Wecker: das System darf um wenige Minuten verschieben
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(p))
        }
        store.edit().putStringSet("codes", keep).apply()
    }

    companion object {
        const val CH_ENTRY = "erinnerung"
        const val CH_GOAL = "ziele"
        const val CH_DAY = "wichtige_tage"
        const val CH_REPORT = "berichte"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            for ((id, name, text) in listOf(
                Triple(CH_ENTRY, "Erinnerung ans Eintragen", "Zu deinen festgelegten Uhrzeiten"),
                Triple(CH_GOAL, "Ziele", "Erinnerungen an offene Ziele"),
                Triple(CH_DAY, "Wichtige Tage", "Jahrestage und Ereignisse"),
                Triple(CH_REPORT, "Berichte", "Wochen- und Monatsbericht"),
            )) nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply { description = text })
        }
    }
}

/** Zeigt den Hinweis und plant danach die naechsten. Inhalte des Tagebuchs erscheinen nie im Hinweis. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? TagebuchApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val d = app.container.repo.snapshot().let { s -> s.copy(
                    entries = s.entries.filter { !it.deleted }, goals = s.goals.filter { !it.deleted }, goalChecks = s.goalChecks.filter { !it.deleted },
                    importantDays = s.importantDays.filter { !it.deleted }, reminders = s.reminders.filter { !it.deleted }) }
                if (intent.action == "de.mediclever.tagebuch.HINWEIS") show(context, d, intent.getStringExtra("kind") ?: "", intent.getStringExtra("id") ?: "")
                app.container.reminders.reschedule(d)
            } finally { pending.finish() }
        }
    }

    private fun show(context: Context, d: Diary, kind: String, id: String) {
        val today = LocalDate.now()
        val (channel, title, text, route) = when (kind) {
            Plan.ENTRY -> {
                val r = d.reminders.firstOrNull { it.id == id && it.active } ?: return
                Quad(ReminderScheduler.CH_ENTRY, r.text.ifBlank { "Zeit für deinen Eintrag" }, "Wie geht es dir gerade?", "entry/new?date=")
            }
            Plan.GOAL -> {
                val t = goalTracker(d, id) ?: return
                if (t.stateOn(today) != GoalState.OPEN) return
                Quad(ReminderScheduler.CH_GOAL, t.goal.reminderText.ifBlank { t.goal.name }, "Dein Ziel für heute ist noch offen.", "goal/$id")
            }
            Plan.DAY -> {
                val dayId = id.substringBefore('#'); val before = id.substringAfter('#').toIntOrNull() ?: 0
                val day = d.importantDays.firstOrNull { it.id == dayId } ?: return
                Quad(ReminderScheduler.CH_DAY, day.name, if (before == 0) "Heute ist es so weit." else if (before == 1) "Morgen ist es so weit." else "In $before Tagen ist es so weit.", "days")
            }
            Plan.REPORT_WEEK -> Quad(ReminderScheduler.CH_REPORT, "Dein Wochenbericht ist da", "Sieh dir an, wie deine Woche war.", "reports")
            Plan.REPORT_MONTH -> Quad(ReminderScheduler.CH_REPORT, "Dein Monatsbericht ist da", "Sieh dir an, wie dein Monat war.", "reports")
            else -> return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, (kind + id).hashCode(), Intent(context, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        NotificationManagerCompat.from(context).notify((kind + id).hashCode(), n)
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)
}
