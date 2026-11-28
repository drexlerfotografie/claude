# SchichtClever

Schicht-Begleiter für Rettungsdienst, Klinikpflege, Ärzt:innen und Azubis. Teil der mediclever-Produkte.

**Version 0.1.0**: erste lauffähige Version, nativ in Kotlin mit Jetpack Compose und Material 3.

## Was die App kann

| Bereich | Funktion |
|---|---|
| **Schicht** | Früh, Spät, Nacht, 12 h Tag/Nacht oder 24 h starten. Fahrzeug oder Station optional. Laufzeit, Restzeit und Überstunden werden angezeigt. Am Ende eine Zusammenfassung mit Bewertung und Notiz. |
| **Einsätze** | Alarmzeit, Kategorie, Dringlichkeit, Ausgang und Maßnahmen, jeweils „selbst“ oder „assistiert“. Eigene Maßnahmen lassen sich anlegen. Ein deutlicher Hinweis „Keine Patientendaten“ steht im Formular. |
| **Statistik** | Woche, Monat, Jahr oder gesamt. Einsätze nach Uhrzeit mit Peak, Kategorien, Ø pro Schichttyp, häufigste Maßnahmen, Nachtschicht-Serie. |
| **Skills** | Pro Maßnahme: wie oft, wann zuletzt, Frische-Ampel je nach Übungsintervall. Dazu Übungsliste, Übungen aus Simulation oder Skillstraining nachtragen und eine Erinnerung von Cleverle. |
| **Fokus-Modus** | Schaltet bei Schichtbeginn automatisch „Bitte nicht stören“ ein und pausiert ausgewählte Apps (z. B. Instagram, TikTok). Mit Sperrbildschirm und „5 Minuten freigeben“. |
| **Blaufilter** | Warmer Filter plus Abdunklung über dem ganzen Bildschirm. Zeitplan: mit Nachtschicht, 22–06 Uhr oder manuell. |
| **Schnelleinstellungen** | Drei Kacheln: **Schicht** (starten bzw. öffnen), **+ Einsatz** (ein Tipp speichert den Zeitstempel, Details später nachtragen) und **Fokus** (an/aus). |
| **Design** | Dunkel, hell oder „automatisch nach Schicht“ (Nacht dunkel, Tag hell). Farben, Oswald-Schrift und eckige Formen von mediclever.de. |
| **Rollen** | Der Maßnahmen-Katalog passt sich an. In der Pflege heißt es „Notfälle/Eintrag“ statt „Einsatz“. |
| **Export** | CSV mit Einsätzen, Schichten und Übungen (Excel-kompatibel, mit Umlauten). |

## Datenschutz

- **Keine Internet-Berechtigung** im Manifest. Die App kann technisch nichts senden.
- Alle Daten liegen in einer JSON-Datei im privaten App-Speicher und sind vom Cloud-Backup ausgeschlossen.
- Der Bedienungshilfe-Dienst liest nur den Paketnamen der Vordergrund-App (`canRetrieveWindowContent=false`).

## Bauen

Voraussetzungen: JDK 17+ und Android SDK (Platform 35).

```bash
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # Unit-Tests (Statistik, Skill-Frische)
./gradlew recordPaparazziDebug     # Screenshots der echten Screens → app/src/test/snapshots/images
```

## Ordner

- `app/`: Quellcode
  - `data/`: Modelle, Katalog, Speicher, Auswertung, Export
  - `focus/`: Bitte nicht stören, Bedienungshilfe-Dienst, Sperrbildschirm
  - `tiles/`: Kacheln für die Schnelleinstellungen
  - `ui/`: Compose-Screens und Theme
- `design/png/`: die 20 Entwurfsbilder (Quellen in `design/src/`)
- `design/app-screenshots/`: Screenshots der tatsächlichen App, gerendert aus dem Code

## Bekannte Grenzen von v0.1

- **Noch nicht auf einem echten Gerät getestet.** Kompiliert ist alles, die Screens sind per Paparazzi gerendert. Ob „Bitte nicht stören“, die App-Sperre und das Overlay auf jedem Hersteller-Android (Samsung, Xiaomi …) gleich funktionieren, muss ein Gerätetest zeigen.
- **Google Play:** Apps, die die Accessibility-API nutzen, müssen das im Play-Store-Formular begründen. Für die Veröffentlichung sollte die App-Sperre deshalb später auf UsageStats und eine Overlay-Lösung umgestellt oder sauber deklariert werden.
- Der Blaufilter ist ein Overlay. Das systemeigene Android-Nachtlicht dürfen normale Apps nicht schalten.
- Ein PDF-Export ist geplant, vorerst gibt es nur CSV.

Schrift Oswald: SIL Open Font License 1.1.
