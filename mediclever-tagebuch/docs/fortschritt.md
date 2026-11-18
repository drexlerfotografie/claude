# Stand

**17.11.2026:** Version 0.3.0 mit neuem Aufbau: Monitor-Startseite, Messung mit Barometer, keine untere Leiste, BOS-Startpaket als Vorgabe, Hell als Standard, Cleverle mit Frage des Tages (vorläufig das Logo-Symbol).

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

## Stand 18.11.2026 – Version 0.4.0: neue Bedienung nach den Entwürfen F bis I
Umgesetzt und automatisch geprüft:
- Startseite mit Barometer, Ring der laufenden Woche, Skalen-Kacheln, Monatsmatrix (geteilt bei zwei Einträgen, Verlauf ab drei), einem Satz im gewählten Tonfall, optional Ziele und letzter Eintrag
- Leiste überall gleich: Start, Einträge, Einsprechen, Auswertung, Menü
- Eintrag: Wert klein oben rechts, Dienstart (genau eine), Skalen als Zeilen, Aktivitäten mit „Alle Aktivitäten“, Notiz; Reihenfolge einstellbar
- Dienstarten: ein- und ausschalten, eigene anlegen, Vorlagen, Kurzname, freiwillige Zeiten
- Auswertung: das Wichtigste, Wert nach Dienstart, Verlauf im Dienstblock; alles Weitere hinter einem Knopf
- Kalender mit Dienststreifen, Eintragsliste mit Zahlfeld und Dienstmarke
- Sprachnotiz: Aufnahme und Text, Vorschläge für Dienstart, Aktivitäten und Skalen nach festen Regeln, eigene Stichwörter und Sperrwörter, Lernen aus Ablehnungen
- Typ (frei), Detailtiefe, Tonfall mit Cleverle, Reihenfolge, Startseite anpassen (Plus), Plus als Schalter für die Testphase

Nur auf einem echten Gerät prüfbar, bisher ungeprüft:
- Sprachnotiz: Aufnahme (WAV, 16 kHz) und gleichzeitige Spracherkennung über `RecognizerIntent.EXTRA_AUDIO_SOURCE` (ab Android 13). Ob die Spracherkennung des Geräts den Tonstrom annimmt, hängt vom Gerät ab. Ohne Erkennung bleibt die Aufnahme, der Text wird getippt.
- Ziehen am Barometer auf der Startseite, Dialog zum Ändern des Werts

Offen:
- Start über Widget und Assistent direkt in die Sprachnotiz
- Verstärker („sehr“, „etwas“) bei Skalen aus Sprache
- Dienstart aus hinterlegten Zeiten vorwählen
- Plus als echter Kauf (Play Billing)
- Module außer Schichtdienst
