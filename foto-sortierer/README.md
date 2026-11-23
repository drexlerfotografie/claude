# Foto-Sortierer (Android)

Sortiert Fotos und Videos vom Handy nach Aufnahmedatum in eine saubere Ordnerstruktur:

```
Pictures/Sortiert/
  2026/
    2026_05_Urlaub_und_Ausflüge/
      2026_05_24_Gardasee_001.jpg
      2026_05_24_Gardasee_002.heic
    2026_05_Arbeit/
      2026_05_26_001.jpg
    2026_06/                      (ohne Kategorie)
      2026_06_02_001.mp4
  _Duplikate/
```

## Funktionen

- **Sortieren:** Quellordner frei wählbar. Das Aufnahmedatum kommt aus EXIF, bei Videos aus den Metadaten,
  sonst aus dem Dateinamen (z. B. `IMG-20231105-WA0001.jpg`) und zuletzt aus dem Änderungsdatum
  (markiert als „unsicher“).
- **Ereignisse:** Fotos mit kurzer Pause dazwischen (einstellbar, Standard 3 Std.) werden gruppiert – auch über
  Mitternacht.
- **Vorschläge:** Kategorie per Bilderkennung (ML Kit, offline auf dem Handy) und Regeln (Screenshots); Anlass aus
  dem GPS-Ort, ergänzt um Ideen aus dem Bildinhalt und zuletzt benutzte Anlässe. Die App lernt aus deinen
  Kategorie-Zuordnungen.
- **Kategorie und Anlass** pro Ereignis setzen oder für markierte Fotos abweichend. Kategorien sind im Ordner-Tab
  frei bearbeitbar.
- **Vorschau:** zeigt vor jedem Lauf, was wohin geht. Danach wählst du *Verschieben* oder *Kopieren*.
- **Rückgängig:** Jeder Lauf wird protokolliert und lässt sich unter „Verlauf“ komplett zurückdrehen.
- **Duplikate:** findet inhaltlich identische Dateien (SHA-256) und verschiebt überzählige nach `_Duplikate`,
  ohne sie zu löschen.
- **Suchen:** nach Name, Ordner, Zeitraum, Foto/Video und unsicherem Datum filtern.
- Bereits sortierte Dateien werden erkannt, die Nummerierung läuft bei weiteren Läufen fort.
- Keine Internetberechtigung: Die Bilder verlassen das Handy nicht. Für Ortsnamen werden nur die GPS-Koordinaten an
  den Ortsdienst von Android geschickt (abschaltbar).

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
