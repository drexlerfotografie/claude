package app.fotosortierer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateParsingTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun cameraNames() {
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), FilenameDate.parse("IMG_20231105_143512.jpg", today))
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), FilenameDate.parse("PXL_20231105_143512345.jpg", today))
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), FilenameDate.parse("VID_20231105_143512.mp4", today))
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), FilenameDate.parse("20231105_143512.heic", today))
    }

    @Test fun whatsappAndScreenshots() {
        assertEquals(LocalDateTime.of(2023, 11, 5, 0, 0), FilenameDate.parse("IMG-20231105-WA0001.jpg", today))
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), FilenameDate.parse("Screenshot_2023-11-05-14-35-12.png", today))
    }

    @Test fun alreadySortedNames() {
        assertEquals(LocalDateTime.of(2023, 11, 5, 0, 0), FilenameDate.parse("2023_11_05_Rom_001.jpg", today))
        assertEquals(LocalDateTime.of(2023, 11, 5, 0, 0), FilenameDate.parse("2023_11_05_001.jpg", today))
    }

    @Test fun rejectsNonsense() {
        assertNull(FilenameDate.parse("DSC01234.JPG", today))
        assertNull(FilenameDate.parse("IMG_20231340_101010.jpg", today))
        assertNull(FilenameDate.parse("IMG_20301105_101010.jpg", today)) // Zukunft
        assertNull(FilenameDate.parse("1234567890123.jpg", today))
    }

    @Test fun exif() {
        assertEquals(LocalDateTime.of(2023, 11, 5, 14, 35, 12), ExifDate.parse("2023:11:05 14:35:12"))
        assertNull(ExifDate.parse("0000:00:00 00:00:00"))
        assertNull(ExifDate.parse(null))
    }

    @Test fun video() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(LocalDateTime.of(2023, 11, 5, 15, 35, 12), VideoDate.parse("20231105T143512.000Z", berlin))
        assertEquals(LocalDateTime.of(2023, 7, 5, 16, 35, 12), VideoDate.parse("20230705T143512.000Z", berlin))
        assertNull(VideoDate.parse("19040101T000000.000Z", berlin))
    }
}

class NamingTest {
    @Test fun sanitize() {
        assertEquals("Urlaub_Rom", Naming.sanitizeAnlass("  Urlaub  Rom "))
        assertEquals("ABBild", Naming.sanitizeAnlass("A/B:Bild?"))
        assertEquals("", Naming.sanitizeAnlass("  "))
    }

    @Test fun prefixAndNumber() {
        val d = LocalDate.of(2026, 11, 23)
        assertEquals("2026_11_23_Rom_", Naming.prefix(d, "Rom"))
        assertEquals("2026_11_23_", Naming.prefix(d, ""))
        assertEquals(7, Naming.numberFor("2026_11_23_Rom_", "2026_11_23_Rom_007.jpg"))
        assertNull(Naming.numberFor("2026_11_23_", "2026_11_23_Rom_007.jpg"))
        assertEquals(12, Naming.numberFor("2026_11_23_", "2026_11_23_012.mp4"))
        assertTrue(Naming.sortedName.matches("2026_11_23_Urlaub_Rom_001.jpg"))
        assertTrue(Naming.sortedName.matches("2026_11_23_001.jpg"))
    }
}

class PlannerTest {
    private fun item(path: String, taken: LocalDateTime) =
        MediaItem(path, 100, 0, path.endsWith(".mp4"), taken, DateSource.EXIF)

    @Test fun numbersChronologicallyAndPerAnlass() {
        val a = item("/s/DCIM/b.jpg", LocalDateTime.of(2026, 11, 23, 15, 0))
        val b = item("/s/DCIM/a.JPEG", LocalDateTime.of(2026, 11, 23, 10, 0))
        val c = item("/s/DCIM/c.mp4", LocalDateTime.of(2026, 11, 23, 12, 0))
        val d = item("/s/DCIM/d.jpg", LocalDateTime.of(2025, 1, 2, 12, 0))
        val plan = Planner("/s/Pictures/Sortiert/") { emptyList() }.plan(
            listOf(Assignment(a, "Rom"), Assignment(b, "Rom"), Assignment(c, ""), Assignment(d, "Neujahr"))
        ).associate { it.item.name to it.target }

        assertEquals("/s/Pictures/Sortiert/2026/2026_11_23_Rom_001.jpg", plan["a.JPEG"])
        assertEquals("/s/Pictures/Sortiert/2026/2026_11_23_Rom_002.jpg", plan["b.jpg"])
        assertEquals("/s/Pictures/Sortiert/2026/2026_11_23_001.mp4", plan["c.mp4"])
        assertEquals("/s/Pictures/Sortiert/2025/2025_01_02_Neujahr_001.jpg", plan["d.jpg"])
    }

    @Test fun continuesNumberingAndAvoidsCollisions() {
        val existing = mapOf("/t/2026" to listOf("2026_11_23_Rom_001.jpg", "2026_11_23_Rom_005.jpg", "other.jpg"))
        val x = item("/s/x.jpg", LocalDateTime.of(2026, 11, 23, 9, 0))
        val plan = Planner("/t") { existing[it].orEmpty() }.plan(listOf(Assignment(x, "Rom")))
        assertEquals("/t/2026/2026_11_23_Rom_006.jpg", plan.single().target)
    }

    @Test fun leavesAlreadySortedFilesAlone() {
        val sorted = item("/t/2026/2026_11_23_Rom_001.jpg", LocalDateTime.of(2026, 11, 23, 9, 0))
        val plan = Planner("/t") { listOf("2026_11_23_Rom_001.jpg") }.plan(listOf(Assignment(sorted, "Rom")))
        assertTrue(plan.isEmpty())
    }
}

class DuplicatesTest {
    @Test fun findsIdenticalFiles() {
        val dir = createTempDirSafe()
        val big = ByteArray(200_000) { (it % 251).toByte() }
        val other = big.copyOf().also { it[150_000] = 1 }
        val f1 = File(dir, "a.jpg").apply { writeBytes(big) }
        val f2 = File(dir, "b.jpg").apply { writeBytes(big) }
        val f3 = File(dir, "c.jpg").apply { writeBytes(other) } // gleiche Größe, gleicher Anfang, anderer Inhalt
        val items = listOf(f1, f2, f3).map {
            MediaItem(it.path, it.length(), 0, false, LocalDateTime.of(2026, 1, 1, 0, 0), DateSource.EXIF)
        }
        val groups = Duplicates.find(items)
        assertEquals(1, groups.size)
        assertEquals(setOf("a.jpg", "b.jpg"), groups.single().map { it.name }.toSet())
        dir.deleteRecursively()
    }

    private fun createTempDirSafe(): File =
        File(System.getProperty("java.io.tmpdir"), "dup" + System.nanoTime()).apply { mkdirs() }
}
