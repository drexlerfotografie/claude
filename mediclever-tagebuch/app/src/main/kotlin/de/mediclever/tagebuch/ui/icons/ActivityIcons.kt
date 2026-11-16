package de.mediclever.tagebuch.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Symbolkatalog fuer Aktivitaeten, Skalen und Ziele. Quelle: Material Icons (Rounded),
 * Apache-Lizenz 2.0, siehe docs/lizenzen.md. Die Schluessel stehen so in den Daten;
 * bestehende Schluessel deshalb nie umbenennen.
 */
data class IconInfo(val key: String, val label: String, val category: String, val vector: ImageVector)

val ActivityIcons: List<IconInfo> = listOf(
    // Alltag
    IconInfo("home", "Zuhause", "Alltag", Icons.Rounded.Home),
    IconInfo("cleaning_services", "Putzen", "Alltag", Icons.Rounded.CleaningServices),
    IconInfo("local_laundry_service", "Wäsche", "Alltag", Icons.Rounded.LocalLaundryService),
    IconInfo("shopping_cart", "Einkaufen", "Alltag", Icons.Rounded.ShoppingCart),
    IconInfo("shopping_bag", "Shoppen", "Alltag", Icons.Rounded.ShoppingBag),
    IconInfo("storefront", "Laden", "Alltag", Icons.Rounded.Storefront),
    IconInfo("kitchen", "Küche", "Alltag", Icons.Rounded.Kitchen),
    IconInfo("shower", "Duschen", "Alltag", Icons.Rounded.Shower),
    IconInfo("bathtub", "Baden", "Alltag", Icons.Rounded.Bathtub),
    IconInfo("yard", "Garten", "Alltag", Icons.Rounded.Yard),
    IconInfo("construction", "Baustelle", "Alltag", Icons.Rounded.Construction),
    IconInfo("build", "Reparieren", "Alltag", Icons.Rounded.Build),
    IconInfo("attach_money", "Geld", "Alltag", Icons.Rounded.AttachMoney),
    IconInfo("savings", "Sparen", "Alltag", Icons.Rounded.Savings),
    IconInfo("directions_car", "Auto", "Alltag", Icons.Rounded.DirectionsCar),
    IconInfo("train", "Bahn", "Alltag", Icons.Rounded.Train),
    IconInfo("phone", "Telefon", "Alltag", Icons.Rounded.Phone),
    IconInfo("mail", "Post", "Alltag", Icons.Rounded.Mail),
    IconInfo("weekend", "Sofa", "Alltag", Icons.Rounded.Weekend),
    IconInfo("chair", "Sitzen", "Alltag", Icons.Rounded.Chair),
    // Schlaf & Erholung
    IconInfo("bed", "Schlaf", "Schlaf & Erholung", Icons.Rounded.Bed),
    IconInfo("bedtime", "Früh schlafen", "Schlaf & Erholung", Icons.Rounded.Bedtime),
    IconInfo("nightlight", "Nacht", "Schlaf & Erholung", Icons.Rounded.Nightlight),
    IconInfo("alarm", "Wecker", "Schlaf & Erholung", Icons.Rounded.Alarm),
    IconInfo("self_improvement", "Meditation", "Schlaf & Erholung", Icons.Rounded.SelfImprovement),
    IconInfo("spa", "Entspannung", "Schlaf & Erholung", Icons.Rounded.Spa),
    IconInfo("schedule", "Zeit", "Schlaf & Erholung", Icons.Rounded.Schedule),
    // Essen & Trinken
    IconInfo("restaurant", "Essen", "Essen & Trinken", Icons.Rounded.Restaurant),
    IconInfo("local_cafe", "Kaffee", "Essen & Trinken", Icons.Rounded.LocalCafe),
    IconInfo("fastfood", "Fast Food", "Essen & Trinken", Icons.Rounded.Fastfood),
    IconInfo("local_pizza", "Pizza", "Essen & Trinken", Icons.Rounded.LocalPizza),
    IconInfo("cake", "Kuchen", "Essen & Trinken", Icons.Rounded.Cake),
    IconInfo("icecream", "Eis", "Essen & Trinken", Icons.Rounded.Icecream),
    IconInfo("local_bar", "Cocktail", "Essen & Trinken", Icons.Rounded.LocalBar),
    IconInfo("wine_bar", "Wein", "Essen & Trinken", Icons.Rounded.WineBar),
    IconInfo("water_drop", "Wasser", "Essen & Trinken", Icons.Rounded.WaterDrop),
    IconInfo("smoking_rooms", "Rauchen", "Essen & Trinken", Icons.Rounded.SmokingRooms),
    // Bewegung
    IconInfo("fitness_center", "Training", "Bewegung", Icons.Rounded.FitnessCenter),
    IconInfo("directions_run", "Laufen", "Bewegung", Icons.AutoMirrored.Rounded.DirectionsRun),
    IconInfo("directions_bike", "Radfahren", "Bewegung", Icons.AutoMirrored.Rounded.DirectionsBike),
    IconInfo("directions_walk", "Spazieren", "Bewegung", Icons.AutoMirrored.Rounded.DirectionsWalk),
    IconInfo("pool", "Schwimmen", "Bewegung", Icons.Rounded.Pool),
    IconInfo("hiking", "Wandern", "Bewegung", Icons.Rounded.Hiking),
    IconInfo("sports_soccer", "Fußball", "Bewegung", Icons.Rounded.SportsSoccer),
    IconInfo("sports_basketball", "Basketball", "Bewegung", Icons.Rounded.SportsBasketball),
    IconInfo("sports_tennis", "Tennis", "Bewegung", Icons.Rounded.SportsTennis),
    IconInfo("sailing", "Segeln", "Bewegung", Icons.Rounded.Sailing),
    // Arbeit & Lernen
    IconInfo("work", "Arbeit", "Arbeit & Lernen", Icons.Rounded.Work),
    IconInfo("school", "Schule", "Arbeit & Lernen", Icons.Rounded.School),
    IconInfo("menu_book", "Lernen", "Arbeit & Lernen", Icons.AutoMirrored.Rounded.MenuBook),
    IconInfo("book", "Buch", "Arbeit & Lernen", Icons.Rounded.Book),
    IconInfo("computer", "Computer", "Arbeit & Lernen", Icons.Rounded.Computer),
    IconInfo("edit", "Schreiben", "Arbeit & Lernen", Icons.Rounded.Edit),
    IconInfo("lightbulb", "Idee", "Arbeit & Lernen", Icons.Rounded.Lightbulb),
    IconInfo("science", "Labor", "Arbeit & Lernen", Icons.Rounded.Science),
    IconInfo("language", "Sprache", "Arbeit & Lernen", Icons.Rounded.Language),
    // Dienst & Medizin
    IconInfo("emergency", "Dienst", "Dienst & Medizin", Icons.Rounded.Emergency),
    IconInfo("local_fire_department", "Feuerwehr", "Dienst & Medizin", Icons.Rounded.LocalFireDepartment),
    IconInfo("medical_services", "Sanitätsdienst", "Dienst & Medizin", Icons.Rounded.MedicalServices),
    IconInfo("local_hospital", "Klinik", "Dienst & Medizin", Icons.Rounded.LocalHospital),
    IconInfo("monitor_heart", "Puls", "Dienst & Medizin", Icons.Rounded.MonitorHeart),
    IconInfo("medication", "Medikament", "Dienst & Medizin", Icons.Rounded.Medication),
    IconInfo("vaccines", "Impfung", "Dienst & Medizin", Icons.Rounded.Vaccines),
    IconInfo("healing", "Heilung", "Dienst & Medizin", Icons.Rounded.Healing),
    IconInfo("sick", "Krank", "Dienst & Medizin", Icons.Rounded.Sick),
    IconInfo("psychology", "Kopf", "Dienst & Medizin", Icons.Rounded.Psychology),
    // Menschen
    IconInfo("family_restroom", "Familie", "Menschen", Icons.Rounded.FamilyRestroom),
    IconInfo("group", "Freunde", "Menschen", Icons.Rounded.Group),
    IconInfo("person", "Allein", "Menschen", Icons.Rounded.Person),
    IconInfo("favorite", "Partnerschaft", "Menschen", Icons.Rounded.Favorite),
    IconInfo("child_care", "Kinder", "Menschen", Icons.Rounded.ChildCare),
    IconInfo("pets", "Haustier", "Menschen", Icons.Rounded.Pets),
    IconInfo("chat", "Gespräch", "Menschen", Icons.AutoMirrored.Rounded.Chat),
    IconInfo("videocam", "Videoanruf", "Menschen", Icons.Rounded.Videocam),
    IconInfo("celebration", "Feier", "Menschen", Icons.Rounded.Celebration),
    IconInfo("volunteer_activism", "Ehrenamt", "Menschen", Icons.Rounded.VolunteerActivism),
    IconInfo("church", "Glaube", "Menschen", Icons.Rounded.Church),
    // Freizeit
    IconInfo("movie", "Film", "Freizeit", Icons.Rounded.Movie),
    IconInfo("tv", "Fernsehen", "Freizeit", Icons.Rounded.Tv),
    IconInfo("sports_esports", "Zocken", "Freizeit", Icons.Rounded.SportsEsports),
    IconInfo("music_note", "Musik", "Freizeit", Icons.Rounded.MusicNote),
    IconInfo("headphones", "Hören", "Freizeit", Icons.Rounded.Headphones),
    IconInfo("brush", "Malen", "Freizeit", Icons.Rounded.Brush),
    IconInfo("palette", "Kreativ", "Freizeit", Icons.Rounded.Palette),
    IconInfo("photo_camera", "Fotografie", "Freizeit", Icons.Rounded.PhotoCamera),
    IconInfo("theater_comedy", "Theater", "Freizeit", Icons.Rounded.TheaterComedy),
    IconInfo("casino", "Spieleabend", "Freizeit", Icons.Rounded.Casino),
    IconInfo("extension", "Puzzle", "Freizeit", Icons.Rounded.Extension),
    // Unterwegs & Wetter
    IconInfo("flight", "Reise", "Unterwegs & Wetter", Icons.Rounded.Flight),
    IconInfo("beach_access", "Strand", "Unterwegs & Wetter", Icons.Rounded.BeachAccess),
    IconInfo("park", "Natur", "Unterwegs & Wetter", Icons.Rounded.Park),
    IconInfo("map", "Ausflug", "Unterwegs & Wetter", Icons.Rounded.Map),
    IconInfo("explore", "Entdecken", "Unterwegs & Wetter", Icons.Rounded.Explore),
    IconInfo("public", "Welt", "Unterwegs & Wetter", Icons.Rounded.Public),
    IconInfo("wb_sunny", "Sonne", "Unterwegs & Wetter", Icons.Rounded.WbSunny),
    IconInfo("cloud", "Wolken", "Unterwegs & Wetter", Icons.Rounded.Cloud),
    IconInfo("ac_unit", "Kälte", "Unterwegs & Wetter", Icons.Rounded.AcUnit),
    // Allgemein
    IconInfo("circle", "Punkt", "Allgemein", Icons.Rounded.Circle),
    IconInfo("star", "Stern", "Allgemein", Icons.Rounded.Star),
    IconInfo("flag", "Ziel", "Allgemein", Icons.Rounded.Flag),
    IconInfo("check_circle", "Erledigt", "Allgemein", Icons.Rounded.CheckCircle),
    IconInfo("tune", "Skala", "Allgemein", Icons.Rounded.Tune),
    IconInfo("bolt", "Energie", "Allgemein", Icons.Rounded.Bolt),
    IconInfo("speed", "Tempo", "Allgemein", Icons.Rounded.Speed),
    IconInfo("thumb_up", "Gut", "Allgemein", Icons.Rounded.ThumbUp),
    IconInfo("thumb_down", "Schlecht", "Allgemein", Icons.Rounded.ThumbDown),
)

private val byKey = ActivityIcons.associateBy { it.key }

fun iconFor(key: String): ImageVector = (byKey[key] ?: byKey.getValue("circle")).vector

val IconCategories: List<String> = ActivityIcons.map { it.category }.distinct()
