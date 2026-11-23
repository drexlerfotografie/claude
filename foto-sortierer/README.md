# Foto-Sortierer (Android)

Sortiert Fotos und Videos vom Handy nach Aufnahmedatum in eine saubere Ordnerstruktur:

```
Pictures/Sortiert/
  2026/
    2026_11_23_Urlaub_Rom_001.jpg
    2026_11_23_Urlaub_Rom_002.heic
    2026_11_24_001.mp4
  _Duplikate/
```

## Funktionen

- **Sortieren:** Quellordner frei wählbar. Das Aufnahmedatum kommt aus EXIF, bei Videos aus den Metadaten,
  sonst aus dem Dateinamen (z. B. `IMG-20231105-WA0001.jpg`) und zuletzt aus dem Änderungsdatum
  (markiert als „unsicher“).
- **Anlass:** pro Tag eintragen oder für einzelne markierte Fotos abweichend setzen. Ohne Anlass heißt die Datei
  `2026_11_23_001.jpg`.
- **Vorschau:** zeigt vor jedem Lauf, was wohin geht. Danach wählst du *Verschieben* oder *Kopieren*.
- **Rückgängig:** Jeder Lauf wird protokolliert und lässt sich unter „Verlauf“ komplett zurückdrehen.
- **Duplikate:** findet inhaltlich identische Dateien (SHA-256) und verschiebt überzählige nach `_Duplikate`,
  ohne sie zu löschen.
- **Suchen:** nach Name, Ordner, Zeitraum, Foto/Video und unsicherem Datum filtern.
- Bereits sortierte Dateien werden erkannt, die Nummerierung läuft bei weiteren Läufen fort.
- Keine Internetberechtigung: Die Bilder verlassen das Handy nicht.

## Installation

1. Auf GitHub unter **Releases** die neueste `FotoSortierer-XX.apk` herunterladen
   (alternativ unter *Actions* beim letzten Lauf als Artefakt).
2. Auf dem Handy öffnen und „Installation aus unbekannten Quellen“ für Browser bzw. Dateimanager erlauben.
3. Beim ersten Start „Zugriff auf alle Dateien“ erteilen.

Neue Versionen lassen sich einfach über die alte installieren, weil sie immer mit demselben Schlüssel
(`app/signing.keystore`) signiert werden. Dieser Schlüssel ist bewusst öffentlich im Repo und nur für die private
Verteilung gedacht.

## Entwicklung

```
./gradlew testDebugUnitTest   # Unit-Tests + End-to-End-Test (Robolectric)
./gradlew assembleRelease     # APK unter app/build/outputs/apk/release/
```

Aufbau:

- `core/` – reine Logik ohne Android (Datumserkennung, Namensschema, Planung, Duplikate), vollständig getestet
- `data/` – Dateisystem, EXIF/Video-Metadaten, Datums-Cache, Protokoll, Einstellungen
- `ui/` – Jetpack Compose Oberfläche
