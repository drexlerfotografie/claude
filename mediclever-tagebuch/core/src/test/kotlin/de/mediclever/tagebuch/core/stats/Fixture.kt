package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.GoalCheck
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScalePoint
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import de.mediclever.tagebuch.core.model.Trend
import java.time.LocalDate
import java.time.ZoneOffset

val UTC: ZoneOffset = ZoneOffset.UTC

fun d(text: String): LocalDate = LocalDate.parse(text)

/** Stimmungen: m1 Stufe 1 (Punktwert 5) ... m5 Stufe 5 (Punktwert 1); m2b ist eine zweite, archivierte Stimmung der Stufe 2. */
val MOODS = listOf(
    Mood(id = "m1", level = 1, name = "sehr gut", icon = "a"),
    Mood(id = "m2", level = 2, name = "gut", icon = "b"),
    Mood(id = "m2b", level = 2, name = "zufrieden", icon = "b2", sortOrder = 1, archivedAt = 1),
    Mood(id = "m3", level = 3, name = "mittel", icon = "c"),
    Mood(id = "m4", level = 4, name = "schlecht", icon = "d"),
    Mood(id = "m5", level = 5, name = "sehr schlecht", icon = "e"),
    Mood(id = "gone", level = 1, name = "entfernt", icon = "x", deleted = true),
)

val GROUPS = listOf(
    TagGroup(id = "g1", name = "Bewegung", colorIndex = 3, sortOrder = 0),
    TagGroup(id = "g2", name = "Ruhe", colorIndex = 5, sortOrder = 1),
)

val TAGS = listOf(
    Tag(id = "sport", groupId = "g1", name = "Sport"),
    Tag(id = "lesen", groupId = "g2", name = "Lesen", archivedAt = 1),
    Tag(id = "freunde", groupId = "g2", name = "Freunde"),
    Tag(id = "arbeit", groupId = null, name = "Arbeit"),
    Tag(id = "old", groupId = "g1", name = "Alt", deleted = true),
)

val SCALES = listOf(
    Scale(id = "schlaf", type = ScaleType.DURATION, name = "Schlaf", sortOrder = 0, trend = Trend.HIGHER_IS_BETTER),
    Scale(
        id = "kaffee", type = ScaleType.NUMBER, name = "Kaffee", sortOrder = 1,
        chartMode = ChartMode.DAILY_SUM, trend = Trend.LOWER_IS_BETTER, archivedAt = 1,
    ),
    Scale(
        id = "energie", type = ScaleType.TEXT, name = "Energie", sortOrder = 2, trend = Trend.NEUTRAL,
        points = listOf(ScalePoint("p1", 1, "niedrig"), ScalePoint("p2", 2, "mittel"), ScalePoint("p3", 3, "hoch")),
    ),
)

fun entry(
    id: String,
    date: String,
    mood: String,
    vararg tags: String,
    minute: Int = 720,
    title: String = "",
    note: String = "",
    assets: List<String> = emptyList(),
    scales: List<ScaleValue> = emptyList(),
    deleted: Boolean = false,
    favorite: Boolean = false,
) = Entry(
    id = id, timestamp = 0, offsetMinutes = 60, localDate = date, minuteOfDay = minute, moodId = mood,
    title = title, note = note, tagIds = tags.toList(), assetIds = assets, scaleValues = scales,
    deleted = deleted, favorite = favorite,
)

fun schlaf(min: Int) = ScaleValue("schlaf", minutes = min)
fun kaffee(n: Int) = ScaleValue("kaffee", number = n)
fun energie(point: String) = ScaleValue("energie", pointId = point)

/**
 * Kleines Tagebuch. Maerz 2026 beginnt an einem Sonntag.
 *
 * Februar: f1 Fr 27.02. Stufe 4 (2 Punkte) [Arbeit]; f2 Sa 28.02. Stufe 2 (4) [Sport]
 * Maerz:   e1 Mo 02. (5) [Sport, Freunde]   e2 Di 03. (4) [Sport]
 *          e3 Mi 04. 08:00 (4) [Lesen]      e4 Mi 04. 20:00 (2) [Arbeit]   -> Tagesmittel 3,0
 *          e5 Do 05. (1) [Arbeit]           e6 Sa 07. (5) [Sport, Lesen]
 *          e7 So 08. (4, Stimmung m2b) [Sport, Freunde, geloeschte Aktivitaet]
 *          e8 Mo 09. (3) []                 x  Di 10. geloeschter Eintrag
 * Summe der Punktwerte im Maerz: 5+4+4+2+1+5+4+3 = 28 bei 8 Eintraegen an 7 Tagen.
 */
val ENTRIES = listOf(
    entry("f1", "2026-02-27", "m4", "arbeit"),
    entry("f2", "2026-02-28", "m2", "sport", scales = listOf(schlaf(480), kaffee(4), energie("p1"))),
    entry(
        "e1", "2026-03-02", "m1", "sport", "freunde", title = "Guter Start",
        note = "<p>Heute war ich <b>laufen</b> im Park.</p>", assets = listOf("a1", "a2", "a3"), favorite = true,
    ),
    entry("e2", "2026-03-03", "m2", "sport"),
    entry("e3", "2026-03-04", "m2", "lesen", minute = 480, scales = listOf(schlaf(420), kaffee(2), energie("p3"))),
    entry("e4", "2026-03-04", "m4", "arbeit", minute = 1200, scales = listOf(schlaf(480), kaffee(1), energie("p1"))),
    entry("e5", "2026-03-05", "m5", "arbeit", note = "Kaffee &amp; Kuchen", scales = listOf(schlaf(360), kaffee(1))),
    entry("e6", "2026-03-07", "m1", "sport", "lesen", assets = listOf("a4", "a1")),
    entry("e7", "2026-03-08", "m2b", "sport", "freunde", "old"),
    entry("e8", "2026-03-09", "m3"),
    entry("x", "2026-03-10", "m1", "sport", deleted = true),
)

val ASSETS = listOf(
    Asset(id = "a1", fileName = "1.jpg"),
    Asset(id = "a2", type = AssetType.AUDIO, fileName = "2.m4a"),
    Asset(id = "a3", fileName = "3.jpg", deleted = true),
    Asset(id = "a4", fileName = "4.jpg"),
)

/**
 * Ziele: "sportziel" taeglich ab 01.03., verknuepft mit Sport -> abgehakt am 02., 03., 07., 08. Maerz.
 * "schreibziel" samstags und sonntags ab 01.03., von Hand abgehakt am 01., 07., 08. Maerz.
 */
val GOALS = listOf(
    Goal(id = "sportziel", name = "Sport treiben", tagId = "sport", startDate = "2026-03-01", sortOrder = 0),
    Goal(
        id = "schreibziel", name = "Schreiben", repeat = GoalRepeat.WEEKDAYS, weekdays = listOf(6, 7),
        startDate = "2026-03-01", sortOrder = 1,
    ),
)

fun check(goal: String, date: String, deleted: Boolean = false) =
    GoalCheck(GoalCheck.idFor(goal, date), goal, date, deleted = deleted)

val CHECKS = listOf(
    check("schreibziel", "2026-03-01"),
    check("schreibziel", "2026-03-07"),
    check("schreibziel", "2026-03-08"),
)

val DIARY = Diary(
    moods = MOODS, groups = GROUPS, tags = TAGS, scales = SCALES, entries = ENTRIES, assets = ASSETS,
    goals = GOALS, goalChecks = CHECKS,
)
