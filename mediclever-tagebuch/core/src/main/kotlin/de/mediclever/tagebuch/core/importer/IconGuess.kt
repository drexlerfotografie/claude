package de.mediclever.tagebuch.core.importer

/**
 * Waehlt fuer einen Aktivitaetsnamen ein Symbol aus dem eigenen Katalog.
 * Die Symbolnummern des Vorbilds lassen sich nicht uebernehmen, deshalb wird
 * ueber Stichwoerter (deutsch und englisch) geraten; ohne Treffer gibt es "circle".
 */
object IconGuess {

    const val FALLBACK = "circle"

    /** Alle Symbolschluessel, die der Katalog der App kennt. */
    val ALLOWED: Set<String> = setOf(
        "bed", "bedtime", "restaurant", "local_cafe", "fitness_center", "directions_run", "directions_bike",
        "directions_walk", "pool", "hiking", "self_improvement", "spa", "work", "school", "menu_book", "computer",
        "shopping_cart", "cleaning_services", "local_laundry_service", "home", "family_restroom", "group",
        "favorite", "person", "pets", "child_care", "celebration", "movie", "tv", "sports_esports", "music_note",
        "headphones", "brush", "palette", "photo_camera", "flight", "directions_car", "train", "beach_access",
        "park", "wb_sunny", "cloud", "ac_unit", "water_drop", "local_hospital", "medication", "healing", "sick",
        "monitor_heart", "psychology", "local_bar", "wine_bar", "smoking_rooms", "fastfood", "local_pizza", "cake",
        "icecream", "kitchen", "shower", "bathtub", "phone", "chat", "mail", "videocam", "book", "edit",
        "attach_money", "savings", "church", "volunteer_activism", "star", "flag", "check_circle", "alarm",
        "schedule", "nightlight", "emergency", "local_fire_department", "medical_services", "vaccines", "weekend",
        "chair", "yard", "construction", "build", "sports_soccer", "sports_basketball", "sports_tennis", "sailing",
        "theater_comedy", "casino", "extension", "lightbulb", "science", "language", "public", "map", "explore",
        "shopping_bag", "storefront", "circle", "tune", "bolt", "speed", "thumb_up", "thumb_down",
    )

    /**
     * Reihenfolge zaehlt: der erste Treffer gewinnt, speziellere Begriffe stehen deshalb vorn.
     * Ein Stichwort trifft, wenn ein Wort des Namens damit beginnt; Stichwoerter ab fuenf
     * Buchstaben duerfen auch mitten im Wort stehen (deutsche Zusammensetzungen wie
     * "Nachtschicht"). Mit "=" davor muss das ganze Wort uebereinstimmen,
     * mit "^" davor muss ein Wort damit beginnen.
     */
    private val RULES: List<Pair<String, List<String>>> = listOf(
        // Dienst und Rettung zuerst, damit "Nachtdienst" nicht bei "Nacht" landet.
        "local_fire_department" to listOf("feuerwehr", "^brand", "fire"),
        "emergency" to listOf("dienst", "schicht", "einsatz", "rettung", "notfall", "bereitschaft", "^wache", "shift", "emergency", "=rd", "=nef", "=rtw"),
        "medical_services" to listOf("arzt", "ärztin", "praxis", "doctor", "zahnarzt", "physio"),
        "local_hospital" to listOf("klinik", "krankenhaus", "hospital", "station"),
        "vaccines" to listOf("impf", "vaccin", "spritze"),
        "medication" to listOf("medikament", "tablette", "medizin", "pille", "medication", "pills"),
        "sick" to listOf("krank", "erkält", "erkaelt", "grippe", "fieber", "übel", "sick", "=ill"),
        "healing" to listOf("schmerz", "verletz", "kopfweh", "migräne", "migraene", "pain", "headache"),
        "monitor_heart" to listOf("puls", "blutdruck", "herz", "heart"),
        "psychology" to listOf("therapie", "psycho", "grübel", "gruebel", "therapy", "angst", "panik", "anxiety"),

        "bedtime" to listOf("früh ins bett", "nickerchen", "mittagsschlaf", "=nap", "bedtime"),
        "bed" to listOf("schlaf", "schlecht geschlafen", "bett", "sleep", "ausgeschlafen", "müde", "muede", "tired"),
        "nightlight" to listOf("nacht", "night"),
        "alarm" to listOf("wecker", "aufstehen", "alarm", "früh auf"),

        "local_cafe" to listOf("kaffee", "café", "cafe", "coffee", "=tee", "=tea", "frühstück", "fruehstueck", "breakfast"),
        "local_pizza" to listOf("pizza"),
        "fastfood" to listOf("fast food", "fastfood", "burger", "imbiss", "döner", "doener", "pommes", "junk"),
        "cake" to listOf("kuchen", "torte", "cake", "geburtstag", "birthday", "backen", "baking", "süß", "sweets"),
        "icecream" to listOf("=eis", "ice cream", "icecream"),
        "kitchen" to listOf("kochen", "gekocht", "küche", "kueche", "cook", "kitchen"),
        "restaurant" to listOf("^essen", "restaurant", "abendessen", "mittagessen", "mahlzeit", "mittag", "gesund ess", "eat", "food", "meal", "dinner", "lunch"),
        "wine_bar" to listOf("wein", "wine", "sekt"),
        "local_bar" to listOf("alkohol", "bier", "cocktail", "kneipe", "=bar", "alcohol", "beer", "drink", "trinken"),
        "smoking_rooms" to listOf("rauch", "zigarette", "smok", "cigarette"),
        "water_drop" to listOf("wasser", "water", "hydrat"),

        "pets" to listOf("hund", "katze", "haustier", "tier", "gassi", "=dog", "=cat", "=pet"),
        "sports_soccer" to listOf("fußball", "fussball", "soccer", "football"),
        "sports_basketball" to listOf("basketball", "handball", "volleyball"),
        "sports_tennis" to listOf("tennis", "badminton", "squash", "tischtennis"),
        "pool" to listOf("schwimm", "swim", "freibad", "hallenbad", "pool"),
        "directions_run" to listOf("laufen", "joggen", "jogging", "rennen", "running", "=run", "=lauf"),
        "directions_bike" to listOf("fahrrad", "radfahren", "radtour", "=rad", "bike", "cycling", "rennrad"),
        "hiking" to listOf("wandern", "wanderung", "hiking", "hike", "berg", "klettern", "climb"),
        "directions_walk" to listOf("spazier", "^gehen", "walk", "schritte", "steps"),
        "self_improvement" to listOf("meditat", "yoga", "achtsam", "mindful", "atem", "entspann", "relax"),
        "spa" to listOf("wellness", "sauna", "massage", "=spa", "pflege", "selfcare", "self care", "kosmetik"),
        "fitness_center" to listOf("sport", "fitness", "training", "=gym", "workout", "kraft", "exercise", "krafttraining"),
        "sailing" to listOf("segeln", "boot", "sail", "kajak", "kanu"),

        "school" to listOf("schule", "=uni", "studium", "studier", "lernen", "kurs", "fortbildung", "weiterbildung", "prüfung", "pruefung", "school", "study", "class", "exam", "learn"),
        "menu_book" to listOf("lesen", "gelesen", "reading", "read", "lektüre"),
        "book" to listOf("buch", "book", "hörbuch"),
        "edit" to listOf("schreiben", "tagebuch", "journal", "writing", "write", "notiz"),
        "computer" to listOf("computer", "laptop", "programmier", "coding", "=pc", "internet", "bildschirm", "homeoffice", "home office"),
        "yard" to listOf("garten", "garden", "pflanzen", "plants", "gärtnern"),
        "construction" to listOf("baustelle", "renovier", "umbau", "handwerk", "heimwerk"),
        "build" to listOf("reparatur", "reparier", "basteln", "werkstatt", "repair", "=fix"),
        "work" to listOf("arbeit", "büro", "buero", "=job", "work", "office", "meeting", "besprechung", "projekt", "überstunden"),

        "local_laundry_service" to listOf("wäsche", "waesche", "waschen", "bügeln", "laundry"),
        "cleaning_services" to listOf("putzen", "aufräumen", "aufraeumen", "haushalt", "saubermachen", "staubsaug", "clean", "chores", "tidy"),
        "shopping_cart" to listOf("einkauf", "einkaufen", "supermarkt", "grocer", "besorgung"),
        "shopping_bag" to listOf("shopping", "bummel", "kleidung", "klamotten"),
        "storefront" to listOf("markt", "^laden", "geschäft", "store", "shop"),
        "shower" to listOf("dusche", "duschen", "shower"),
        "bathtub" to listOf("baden", "badewanne", "bath"),

        "child_care" to listOf("kind", "baby", "tochter", "sohn", "kita", "child", "kids"),
        "family_restroom" to listOf("familie", "family", "eltern", "parents", "mutter", "vater", "=oma", "=opa", "geschwister"),
        "favorite" to listOf("partner", "liebe", "=date", "freundin", "=ehe", "beziehung", "love", "relationship", "romant", "kuscheln", "=sex"),
        "group" to listOf("freunde", "friends", "bekannte", "kollegen", "treffen", "verabredung", "gesellschaft", "social", "besuch"),
        "celebration" to listOf("party", "feier", "=fest", "hochzeit", "celebrat", "silvester"),
        "person" to listOf("allein", "alone", "für mich", "me time"),

        "sports_esports" to listOf("zocken", "gaming", "videospiel", "konsole", "playstation", "game", "spielen am"),
        "extension" to listOf("puzzle", "brettspiel", "spieleabend", "spiel", "rätsel", "raetsel", "board game"),
        "casino" to listOf("casino", "poker", "würfel"),
        "movie" to listOf("kino", "film", "movie", "cinema"),
        "tv" to listOf("fernseh", "serie", "netflix", "=tv", "streaming"),
        "theater_comedy" to listOf("theater", "oper", "kabarett", "comedy", "konzertsaal", "musical"),
        "headphones" to listOf("podcast", "hörspiel", "hoerspiel", "kopfhörer", "headphone"),
        "music_note" to listOf("musik", "music", "konzert", "singen", "gitarre", "klavier", "instrument", "chor"),
        "palette" to listOf("malen", "zeichnen", "kunst", "kreativ", "paint", "draw", "=art", "creative"),
        "brush" to listOf("pinsel", "streichen", "schminken", "makeup"),
        "photo_camera" to listOf("foto", "photo", "kamera", "camera"),
        "videocam" to listOf("video", "videocall", "zoom"),

        "flight" to listOf("flug", "fliegen", "flight", "flughafen"),
        "train" to listOf("bahn", "=zug", "train", "pendeln", "=bus", "öpnv", "commute"),
        "directions_car" to listOf("auto", "fahrt", "driving", "=car", "fahren"),
        "beach_access" to listOf("urlaub", "strand", "ferien", "vacation", "holiday", "beach", "meer"),
        "explore" to listOf("ausflug", "^reise", "travel", "trip", "entdecken"),
        "map" to listOf("karte", "route", "stadtbummel", "sightseeing"),
        "park" to listOf("park", "natur", "wald", "draußen", "draussen", "nature", "outdoor", "forest", "frische luft"),
        "wb_sunny" to listOf("sonne", "sonnig", "sunny", "=sun", "hitze", "heiß", "warm"),
        "ac_unit" to listOf("schnee", "kalt", "frost", "snow", "cold", "winter"),
        "cloud" to listOf("wolke", "bewölkt", "regen", "cloud", "rain", "wetter", "weather", "nebel"),

        "phone" to listOf("telefon", "anruf", "handy", "phone", "call"),
        "chat" to listOf("chat", "gespräch", "gespraech", "reden", "unterhalt", "talk", "whatsapp"),
        "mail" to listOf("mail", "post", "brief"),
        "savings" to listOf("sparen", "saving"),
        "attach_money" to listOf("geld", "finanz", "rechnung", "steuer", "money", "bank", "gehalt"),
        "church" to listOf("kirche", "gottesdienst", "gebet", "beten", "church", "pray", "glaube"),
        "volunteer_activism" to listOf("ehrenamt", "spende", "helfen", "geholfen", "volunteer", "charity"),
        "language" to listOf("sprache", "englisch", "spanisch", "französisch", "vokabel", "language"),
        "science" to listOf("forsch", "labor", "experiment", "science"),
        "lightbulb" to listOf("idee", "idea", "planen", "planung"),
        "public" to listOf("nachrichten", "news", "politik"),
        "weekend" to listOf("sofa", "couch", "faulenzen", "chillen", "nichts tun", "ausruhen", "wochenende", "lazy", "rest"),
        "chair" to listOf("sitzen", "möbel", "moebel"),
        "home" to listOf("zuhause", "zu hause", "daheim", "wohnung", "home", "heim"),
        "schedule" to listOf("termin", "zeitdruck", "pünktlich", "appointment", "warten"),
        "bolt" to listOf("energie", "energy", "power", "produktiv", "productive"),
        "speed" to listOf("stress", "hektik", "eile", "tempo", "busy"),
        "thumb_up" to listOf("erfolg", "geschafft", "success", "gut gelaufen"),
        "thumb_down" to listOf("ärger", "aerger", "streit", "konflikt", "argument", "fight"),
        "star" to listOf("highlight", "besonder", "special", "stolz"),
        "check_circle" to listOf("erledigt", "done", "todo", "aufgabe", "task"),
        "flag" to listOf("ziel", "goal"),
    )

    fun guess(name: String): String {
        val text = name.trim().lowercase().replace("dienstag", "") // der Wochentag ist kein Dienst
        if (text.isEmpty()) return FALLBACK
        val words = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        for ((icon, keywords) in RULES) {
            if (keywords.any { matches(it, text, words) }) return icon
        }
        return FALLBACK
    }

    private fun matches(keyword: String, text: String, words: List<String>): Boolean = when {
        keyword.startsWith("=") -> keyword.substring(1) in words
        keyword.startsWith("^") -> words.any { it.startsWith(keyword.substring(1)) }
        ' ' in keyword -> keyword in text
        keyword.length >= 5 -> keyword in text
        else -> words.any { it.startsWith(keyword) }
    }

    /** Fuer Tests: alle Symbole, die die Regeln vergeben koennen. */
    internal fun usedIcons(): Set<String> = RULES.map { it.first }.toSet() + FALLBACK
}
