# Technische Entscheidungen

| Thema | Entscheidung | Begründung |
|---|---|---|
| Plattform | Native Android-App statt WordPress-Plugin mit Web-App | Vorgabe von Lukas am 16.11.2026. Die Projektbeschreibung gilt weiter für Funktionsumfang, Import, Datenschutz und Arbeitsweise; die Abschnitte zu WordPress, REST und Web-Push entfallen. |
| Sprache und Oberfläche | Kotlin, Jetpack Compose, Material 3 | Beste Unterstützung für Widgets, Erinnerungen, Kamera und Offline-Betrieb. |
| Speicherung | Room auf dem Gerät als maßgeblicher Bestand, Firestore als Abgleich | Die App funktioniert vollständig ohne Netz und ohne Konto. Firestore kommt dazu, sobald man sich anmeldet. |
| Listen im Eintrag | Aktivitäten, Skalenwerte und Anhänge stehen als Listen im Eintrag, nicht in Zuordnungstabellen | Ein Eintrag ist dann genau ein Firestore-Dokument. Bei wenigen tausend Einträgen rechnet die App Auswertungen im Arbeitsspeicher (Monat mit 2.000 Einträgen: rund 7 ms). |
| IDs | Zufällige UUID je Zeile, beim Import aus der Quell-ID abgeleitet | Gleiche ID auf allen Geräten; ein zweiter Import erzeugt keine Doppelten. Ersetzt die Tabelle `import_map`. |
| Abgleich | Je Zeile `updatedAt`, Löschmarke und lokales `dirty`; die jüngere Änderung gewinnt; Server-Zeitstempel `syncedAt` als Merker | Einfach, nachvollziehbar und mit einem Server im Arbeitsspeicher testbar (`SyncEngineTest`). |
| Fotos und Sprachnotizen | Nur auf dem Gerät, privat unter `files/assets/`; Fotos werden neu kodiert (ohne EXIF) | Firebase Storage verlangt den Bezahltarif. Abgeglichen werden nur die Angaben zur Datei. |
| Firebase ohne Konfiguration | Fehlt `app/google-services.json`, baut und läuft die App rein lokal | Lukas hat noch kein Firebase-Projekt. `-PfirebaseEmulator=true` verbindet Debug-Builds mit dem lokalen Emulator. |
| Anmeldung | E-Mail mit Passwort und Google (Credential Manager) | Vorgabe von Lukas. |
| MCP-Schnittstelle | Verschoben | Bräuchte Cloud Functions (Bezahltarif) und einen OAuth-Server. Das Feld `Entry.source` kennt `MCP` bereits. |
| Notizen | Gespeichert als bereinigtes HTML, bearbeitet als Text mit Auszeichnungen (`**fett**`, `_kursiv_`, `- Liste`) | Ein vollwertiger Formatier-Editor ist in Compose fehleranfällig. Die Umwandlung ist reine Logik und getestet. |
| Stimmungssymbole | Selbst gezeichnet (`MoodFace`): Kachel mit Augen und Mund, vier Varianten | Eigene Formensprache; jede Stufe ist an der Mundform erkennbar, nicht nur an der Farbe. |
| Symbole für Aktivitäten | Material Icons (Rounded), 386 Stück in 21 Themenpaketen mit deutschen Suchbegriffen | Apache-Lizenz 2.0, einheitliche Strichstärke, siehe `lizenzen.md`. |
| Diagramme | Eigene Canvas-Komponenten | Kein zusätzliches Paket, volle Kontrolle über Farben und Beschriftung. |
| Erinnerungen | `AlarmManager` ohne exakte Wecker, Neuplanung für jeweils drei Tage | Exakte Wecker brauchen ein Sonderrecht, das der Play Store nur Wecker- und Kalender-Apps zugesteht. Das System darf um wenige Minuten verschieben. |
| System-Backup | Abgeschaltet (`allowBackup=false`, Ausschlussregeln) | Gesundheitsdaten sollen nicht unbemerkt im Google-Konto landen. Gesichert wird bewusst über die App. |
| PIN | Gesalzener, mehrfach gehashter Wert in den App-Einstellungen; mit PIN sind Screenshots gesperrt | Sperrt die Oberfläche, ersetzt nicht die Anmeldung. |
| Tests ohne Emulator | Robolectric für App-Durchläufe, Roborazzi für Screenshots | In der Bauumgebung gibt es keine Hardware-Virtualisierung, also kein Android-Emulator. |
| Werkzeuge | Gradle 9.9, Android-Gradle-Plugin 9.5, Kotlin 2.4, compileSdk 37.2, minSdk 26 | Stand November 2026. minSdk 26 deckt praktisch alle aktiven Geräte ab. |
| Farben aus dem Import | Feste Tabelle von den Farbnummern der Quelle auf die eigene Palette (18 Farben) | Die Nummern sind nur Verweise in die Farbliste der anderen App. Bekannte Nummern wurden anhand von Bildschirmfotos zugeordnet, unbekannte bekommen eine feste Ersatzfarbe und einen Hinweis. |
| CSV-Import | Die Stufe unbekannter Stimmungen wählt der Nutzer | Eine CSV-Datei enthält die Stufe nicht. Raten hat „Spitze“ in die Mitte gelegt. |
| Echtes Backup | Liegt nur lokal unter `private/`, Tests prüfen ausschließlich Zählwerte und werden ohne Datei übersprungen | Vorgabe der Projektbeschreibung. |
| Aufbau und Navigation | „Monitor“ als Startseite (Verlaufskurve, Kacheln, Lasche mit Menü), Eintrag als „Messung“ mit Barometer und der Frage „Was hat den Wert beeinflusst?“; keine Leiste am unteren Rand | Entscheidung von Lukas und seiner Frau am 17.11.2026 (Entwurf C): eigenständiger Auftritt statt Nähe zum Vorbild. Zielgruppe zuerst BOS und Schichtdienst, hell als Grundstimmung, Cleverle stellt die Frage des Tages. |

## Dienstarten als Aktivitäten einer Gruppe (18.11.2026)
Eine Dienstart ist eine Aktivität aus der Dienstgruppe, kein eigenes Datenformat. So laufen Abgleich, Sicherung, Import, Farben und die vorhandenen Auswertungen ohne Room-Migration mit. Welche Gruppe das ist, steht in `UserSettings.shiftGroupId` (leer: Gruppe namens „Dienst“). Kurzname, Zeiten und „zählt als frei“ liegen als `ShiftTime` in den Einstellungen. Im Eintrag ist genau eine Dienstart wählbar (`Shifts.select`). Ausschalten heißt archivieren; alte Einträge behalten ihre Dienstart.

## Neue Bedienung (18.11.2026)
Startseite: eine Frage, Barometer mit Ring der laufenden Woche, darunter Skalen-Kacheln, Monatsmatrix, ein Satz. Ein Tipp auf den Bogen öffnet den Eintrag mit diesem Wert. Leiste überall gleich: Start, Einträge, Einsprechen, Auswertung, Menü. Der Kalender öffnet sich über die Matrix und das Menü. Im Eintrag steht der Wert klein oben rechts; Skalen sind flache Zeilen und stehen vor den Aktivitäten. Tageskacheln: ein Eintrag einfarbig, zwei geteilt, ab drei Verlauf. Typ, Detailtiefe, Tonfall, Reihenfolge und Startblöcke liegen in den Einstellungen und werden abgeglichen.
