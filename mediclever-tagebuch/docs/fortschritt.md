# Stand

**16.11.2026, abends:** Version 0.2.0. Der Daylio-Import ist gegen das echte Backup geprüft und korrigiert (Skalenpunkte, Farben, Stimmungsfarben, Wochentags-Ziele). Ursache der falschen Stufen auf dem Handy war ein CSV-Import, bei dem die App die Stufe geraten hat; jetzt fragt sie. Neu: Tagebuch beim Import ersetzen, einzelne Datei `backup.daylio`, 386 Symbole in Themenpaketen, 18 Farben, mehrere Aktivitäten auf einmal in eine Gruppe holen.

**16.11.2026, erste Fassung:** Erste vollständige Fassung der Android-App. Sie baut, alle Tests sind grün, die App lief bisher nur in der Testumgebung, noch auf keinem echten Gerät.

## Was es gibt
- Komplette App nach Projektbeschreibung, Abschnitte Funktionsumfang und Selbst einrichten (Einzelheiten und Belege in `abnahme.md`)
- Abgleich mit Firestore vorbereitet, Regeln und Abgleich-Logik getestet
- Import von Daylio-Backups, CSV und eigener Sicherung
- Debug-APK und Release-Build (unsigniert)

## Nächste Schritte für Lukas
1. APK aufs Handy laden und durchspielen. Rückmeldungen wandern als neue Punkte in `abnahme.md`.
2. Echtes Backup auf dem Handy importieren (Mehr › Daten) und die Vorschau mit den Prüfzahlen vergleichen. Weichen Zahlen ab, die Hinweise aus der Vorschau weitergeben; sie enthalten nur Feldnamen und Anzahlen, keine Inhalte.
3. Firebase-Projekt nach `docs/firebase-einrichtung.md` anlegen, `google-services.json` nach `app/` legen, neu bauen, Anmeldung und Abgleich mit zwei Geräten testen.
4. Eigenes Repository anlegen und für Claude freigeben. Der Code liegt vorläufig im Ordner `mediclever-tagebuch/` des Repositorys `drexlerfotografie/claude`.
5. Für den Play Store: Signaturschlüssel erzeugen (`keystore.properties`), Datenschutzerklärung, Angaben zur Datensicherheit.

## Offene Fragen an Lukas
- Welche Funktionen werden Premium? Die Schalter dafür fehlen noch.
- Sollen Fotos später in die Cloud (Bezahltarif von Firebase) oder dauerhaft nur aufs Gerät?
- MCP-Schnittstelle: weiterhin gewünscht, und darf dafür der Bezahltarif genutzt werden?
- Erfolge: reichen Symbole je Kategorie oder sollen eigene Abzeichen gezeichnet werden?

## Bekannte Grenzen
- Im Daylio-Backup unbekannt: Bedeutung der Erinnerungs- und Kategorienummern wichtiger Tage sowie einzelne Farbnummern (im Code mit `ANNAHME` markiert).
- Symbole der Aktivitäten lassen sich nicht übernehmen; die App wählt anhand des Namens ein eigenes.
- Ein Ziel mit verknüpfter Aktivität lässt sich an einem Tag nicht abwählen, solange ein Eintrag die Aktivität enthält.
- Monatliche Ziele aus dem Import werden zu „einmal pro Woche“.
- Erinnerungen können wenige Minuten später kommen (kein Sonderrecht für exakte Wecker).
