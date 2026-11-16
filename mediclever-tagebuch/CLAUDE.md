# MediClever Tagebuch (Android)

Stimmungs- und Aktivitäten-Tagebuch als native Android-App. Paket-ID `de.mediclever.tagebuch`.

## Befehle
- `./verify.sh` – alles: Tests, Screenshots, Debug-APK, Release-Build, Lint, Firestore-Regeltests (`SKIP_FIREBASE=1` lässt Letztere aus)
- `./gradlew :core:test` – Logik (Auswertungen, Import, Abgleich)
- `./gradlew :app:testDebugUnitTest` – App-Durchläufe und Screenshots nach `app/build/shots/`
- `./gradlew :app:assembleDebug` – APK nach `app/build/outputs/apk/debug/`
- Android-SDK: `local.properties` mit `sdk.dir=…` (Plattform 37.2, Build-Tools 36)

## Aufbau
- `core/` reines Kotlin: Datenmodell (`model/`), Auswertungen (`stats/`, `goals/`, `search/`), Import/Export (`importer/`), Abgleich (`sync/`), Notiztext (`markup/`, `text/`)
- `app/` Compose-Oberfläche (`ui/`), Room und Dateien (`data/`), Firebase (`cloud/`), Hinweise (`notify/`), Widget (`widget/`)
- `firebase/` Firestore-Regeln mit Tests, `docs/` Entscheidungen, Abnahme, Stand

## Feste Regeln
- Texte deutsch, Du-Form, „du“ klein, ruhig und sachlich. Kein Denglisch („Serie“ statt „Streak“).
- Nichts vom Vorbild übernehmen: keine Namen, Grafiken, Symbole, Texte.
- Tagebuchinhalte nie in Logs, Fehlermeldungen, Benachrichtigungen, Commits oder Testdaten. Tests nutzen nur erfundene Daten.
- `reference/`, `private/`, `google-services.json`, Keystores bleiben außerhalb des Repositorys.
- Jede Datenänderung läuft über `DiaryRepository` (Zeitstempel, Löschmarken, Abgleich).
- Neue Funktion: erst ein fehlschlagender Test. Tests nie abschwächen, um grün zu werden.
- Änderungen am Datenmodell brauchen eine Room-Migration und passende Firestore-Regeln samt Regeltest.

Stand und nächste Schritte: `docs/fortschritt.md`. Begründungen: `docs/entscheidungen.md`. Prüfliste: `docs/abnahme.md`.
