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
        "home", "cleaning_services", "local_laundry_service", "shopping_cart", "shopping_bag", "storefront",
        "kitchen", "shower", "bathtub", "yard", "construction", "build", "attach_money", "savings", "directions_car",
        "train", "phone", "mail", "weekend", "chair", "bed", "bedtime", "nightlight", "alarm", "self_improvement",
        "spa", "schedule", "restaurant", "local_cafe", "fastfood", "local_pizza", "cake", "icecream", "local_bar",
        "wine_bar", "water_drop", "smoking_rooms", "fitness_center", "directions_run", "directions_bike",
        "directions_walk", "pool", "hiking", "sports_soccer", "sports_basketball", "sports_tennis", "sailing",
        "work", "school", "menu_book", "book", "computer", "edit", "lightbulb", "science", "language", "emergency",
        "local_fire_department", "medical_services", "local_hospital", "monitor_heart", "medication", "vaccines",
        "healing", "sick", "psychology", "family_restroom", "group", "person", "favorite", "child_care", "pets",
        "chat", "videocam", "celebration", "volunteer_activism", "church", "movie", "tv", "sports_esports",
        "music_note", "headphones", "brush", "palette", "photo_camera", "theater_comedy", "casino", "extension",
        "flight", "beach_access", "park", "map", "explore", "public", "wb_sunny", "cloud", "ac_unit", "circle",
        "star", "flag", "check_circle", "tune", "bolt", "speed", "thumb_up", "thumb_down",
        "sentiment_very_satisfied", "sentiment_satisfied", "sentiment_neutral", "sentiment_dissatisfied",
        "sentiment_very_dissatisfied", "mood", "mood_bad", "emoji_emotions", "heart_broken", "whatshot",
        "auto_awesome", "help", "question_mark", "priority_high", "warning", "balance", "hotel", "loop", "waves",
        "diversity_1", "diversity_3", "shield", "visibility_off", "record_voice_over", "voice_over_off",
        "psychology_alt", "crisis_alert", "thumbs_up_down", "thermostat", "coronavirus", "masks", "bloodtype",
        "hearing", "visibility", "air", "elderly", "accessible", "pregnant_woman", "sanitizer",
        "medical_information", "local_pharmacy", "biotech", "monitor_weight", "scale", "height", "wc", "opacity",
        "grain", "flash_on", "compress", "vibration", "broken_image", "personal_injury", "health_and_safety",
        "breakfast_dining", "lunch_dining", "dinner_dining", "bakery_dining", "ramen_dining", "set_meal", "liquor",
        "sports_bar", "coffee", "emoji_food_beverage", "no_food", "egg", "local_dining", "outdoor_grill", "cookie",
        "kebab_dining", "soup_kitchen", "rice_bowl", "tapas", "delivery_dining", "takeout_dining", "no_drinks",
        "local_drink", "blender", "energy_savings_leaf", "grass", "microwave", "brunch_dining", "no_meals",
        "local_grocery_store", "bedtime_off", "snooze", "alarm_off", "alarm_on", "king_bed", "single_bed", "crib",
        "nights_stay", "dark_mode", "light_mode", "wb_twilight", "mode_night", "access_time", "more_time",
        "hourglass_bottom", "airline_seat_flat", "thunderstorm", "wb_cloudy", "storm", "tornado", "flood", "cyclone",
        "umbrella", "severe_cold", "filter_drama", "tsunami", "landslide", "sports_gymnastics", "sports_handball",
        "sports_volleyball", "sports_baseball", "sports_golf", "sports_hockey", "sports_kabaddi", "sports_mma",
        "sports_martial_arts", "skateboarding", "surfing", "kayaking", "snowboarding", "downhill_skiing",
        "ice_skating", "rowing", "sledding", "nordic_walking", "paragliding", "sports_score", "emoji_events",
        "roller_skating", "scuba_diving", "sports_cricket", "sports_rugby", "snowshoeing", "kitesurfing", "timer",
        "business_center", "badge", "groups", "handshake", "event", "event_available", "assignment", "calculate",
        "account_balance", "credit_card", "payments", "trending_up", "trending_down", "laptop", "print", "call",
        "meeting_room", "engineering", "campaign", "gavel", "inventory", "local_shipping", "store", "sell",
        "receipt", "request_quote", "euro", "analytics", "leaderboard", "work_off", "work_history", "support_agent",
        "co_present", "person_add", "videocam_off", "live_tv", "theaters", "piano", "mic", "auto_stories",
        "library_books", "draw", "color_lens", "cottage", "forest", "landscape", "deck", "festival", "nightlife",
        "attractions", "checkroom", "content_cut", "toys", "smart_toy", "videogame_asset", "card_giftcard", "redeem",
        "museum", "stadium", "local_florist", "camera", "create", "history_edu", "podcasts", "queue_music",
        "phishing", "carpenter", "mosque", "synagogue", "temple_buddhist", "temple_hindu", "flare", "brightness_7",
        "favorite_border", "man", "woman", "boy", "girl", "baby_changing_station", "stroller", "forum", "loyalty",
        "escalator_warning", "child_friendly", "diversity_2", "speaker_notes_off", "night_shelter", "local_activity",
        "iron", "dry_cleaning", "countertops", "garage", "roofing", "plumbing", "handyman", "delete", "recycling",
        "inventory_2", "checklist", "directions_bus", "directions_boat", "tram", "subway", "two_wheeler",
        "electric_scooter", "local_taxi", "local_gas_station", "ev_station", "luggage", "commute", "traffic",
        "place", "tour", "smartphone", "phone_disabled", "tv_off", "wifi_off", "do_not_disturb_on", "notifications",
        "notifications_off", "screen_lock_portrait", "block", "lock", "no_adult_content", "looks_one", "looks_two",
        "looks_3", "looks_4", "looks_5", "looks_6", "check", "close", "add", "remove", "do_not_disturb", "bookmark",
        "label", "tag", "grade", "verified", "new_releases",
    )

    /**
     * Reihenfolge zaehlt: der erste Treffer gewinnt, speziellere Begriffe stehen deshalb vorn.
     * Ein Stichwort trifft, wenn ein Wort des Namens damit beginnt; Stichwoerter ab fuenf
     * Buchstaben duerfen auch mitten im Wort stehen (deutsche Zusammensetzungen wie
     * "Nachtschicht"). Mit "=" davor muss das ganze Wort uebereinstimmen,
     * mit "^" davor muss ein Wort damit beginnen.
     */
    private val RULES: List<Pair<String, List<String>>> = listOf(
        // Genauere Begriffe zuerst: sie sollen vor den allgemeinen Regeln weiter unten greifen.
        "church" to listOf("gottesdienst", "kirche", "gebet", "beten"),
        "auto_stories" to listOf("bibel", "andacht"),
        "heart_broken" to listOf("trennung", "liebeskummer", "uneinig", "streit"),
        "favorite_border" to listOf("kompliment", "zuneigung"),
        "sentiment_very_satisfied" to listOf("happy", "glücklich", "gluecklich", "begeistert", "fröhlich", "froehlich"),
        "sentiment_dissatisfied" to listOf("traurig", "frustriert", "enttäuscht", "enttaeuscht", "unzufrieden", "geweint", "weinen"),
        "mood_bad" to listOf("bocklos", "lustlos", "antriebslos", "langweilig", "gelangweilt"),
        "whatshot" to listOf("ärgerlich", "aergerlich", "wütend", "wuetend", "aggressiv", "gereizt", "hangry", "=sauer"),
        "warning" to listOf("ängstlich", "aengstlich", "^sorge"),
        "loop" to listOf("launisch", "stimmungsschwank"),
        "sentiment_neutral" to listOf("=normal", "neutral", "=okay"),
        "auto_awesome" to listOf("stolz", "überrascht", "ueberrascht"),
        "psychology_alt" to listOf("gedanken", "kopf voll", "entscheidung", "nachdenk"),
        "waves" to listOf("unruhig", "hibbelig", "nervös", "nervoes", "aufgeregt"),
        "nights_stay" to listOf("albtr", "alptr", "luzid", "^traum"),
        "hourglass_bottom" to listOf("aufgewacht", "wach gelegen", "schlaflos"),
        "airline_seat_flat" to listOf("powernap"),
        "access_time" to listOf("=uhr"),
        "thermostat" to listOf("fieber"),
        "grain" to listOf("schwindel"),
        "compress" to listOf("kopfdruck"),
        "coronavirus" to listOf("erkält", "erkaelt", "schnupfen", "grippe", "infekt", "husten"),
        "wc" to listOf("diarrhoe", "durchfall", "verstopf"),
        "flash_on" to listOf("sodbrennen", "krampf"),
        "personal_injury" to listOf("knock out"),
        "no_food" to listOf("kein fast", "fasten", "verzicht"),
        "grass" to listOf("salat", "gemüse", "gemuese", "vegetar", "vegan"),
        "delivery_dining" to listOf("liefer", "bestellt"),
        "coffee" to listOf("koffein"),
        "set_meal" to listOf("fleisch", "fisch", "low carb", "tiefkühl", "tiefkuehl", "=tk"),
        "outdoor_grill" to listOf("grill", "=bbq"),
        "cookie" to listOf("süßes", "suesses", "süßigkeit", "suessigkeit", "schoko", "naschen"),
        "breakfast_dining" to listOf("frühstück", "fruehstueck"),
        "lunch_dining" to listOf("mittagessen"),
        "dinner_dining" to listOf("abendessen"),
        "diversity_3" to listOf("gemeinschaft", "gemeinde", "^verein"),
        "person_add" to listOf("neuer kontakt", "kennengelernt", "neue kollegen"),
        "handshake" to listOf("kollegial", "kundentermin"),
        "event" to listOf("=event", "veranstaltung"),
        "card_giftcard" to listOf("geschenk"),
        "work_history" to listOf("überstunden", "ueberstunden"),
        "store" to listOf("kleinunternehm", "selbstständig", "selbststaendig"),
        "weekend" to listOf("=frei", "freier tag"),
        "sports_martial_arts" to listOf("schwert", "kampfsport", "karate", "judo", "boxen"),
        "nightlife" to listOf("tanz"),
        "wb_cloudy" to listOf("wolkig", "bewölkt", "bewoelkt"),
        "water_drop" to listOf("^regen", "regnerisch"),
        "thunderstorm" to listOf("gewitter"),
        "storm" to listOf("sturm", "stürmisch", "stuermisch"),
        "theaters" to listOf("sneak"),
        "color_lens" to listOf("gestalten", "basteln"),
        "history_edu" to listOf("buch schreiben"),
        "piano" to listOf("instrument", "klavier", "gitarre"),
        "live_tv" to listOf("streaming", "^serie"),

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
        "cloud" to listOf("wolke", "bewölkt", "^regen", "cloud", "rain", "wetter", "weather", "nebel"),

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
