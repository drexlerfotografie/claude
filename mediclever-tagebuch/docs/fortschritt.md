# Fortschritt

Stand: 16.11.2026, Ende der ersten Sitzung.

## Kurz

Plugin, Web-App und MCP-Endpunkt sind gebaut und laufen lokal. `npm run verify` ist grün: 62 PHPUnit-Tests, 30 Playwright-Tests, Screenshots von 33 Bildschirmen in vier Varianten, axe ohne schwere Fehler. Eine unabhängige Gegenprüfung der Screenshots hat 34 Befunde geliefert; bis auf zwei geringe sind sie eingearbeitet (siehe `abnahme.md`, letzter Abschnitt). Eine zweite Prüfrunde steht aus.

**Fertig im Sinne der Projektbeschreibung ist das Projekt nicht.** Es fehlt die Abnahme gegen das Vorbild (Video) und gegen das echte Backup, weil beides in dieser Sitzung nicht vorlag. Dazu kommen Punkte, die nur auf dem Live-System oder am Handy prüfbar sind.

Abweichung von der Arbeitsweise: Die Projektbeschreibung sieht acht Meilensteine mit je einer Freigabe durch Lukas vor. Auf seine Anweisung „Feedback-Loop bis es fertig ist“ wurden alle Meilensteine in einem Zug umgesetzt, ohne Zwischenfreigaben. Die Regel „jede neue Funktion bekommt zuerst einen fehlschlagenden Test“ wurde nicht eingehalten: Meist entstand erst die Funktion, dann der Test. Die Tests haben dabei echte Fehler gefunden (siehe unten).

## Was vorlag und was fehlte

| Material | Stand |
| --- | --- |
| Projektbeschreibung (PDF) | vorhanden, als `docs/projektbeschreibung.md` exportiert |
| CI-Styleguide 2.0 und Logo-Paket | vorhanden, umgesetzt (Roboto, #11202C, #2D323C, #B9303F, Bildmarke) |
| `reference/video.mp4`, vier Screenshots | **fehlen** |
| `private/backup.daylio`, `private/export.csv` | **fehlen** |

## Meilensteine

| Nr. | Inhalt | Stand |
| --- | --- | --- |
| 0 | Repository, `CLAUDE.md`, lokales WordPress, `npm run verify`, Inventar, Abnahmeliste, MCP-Versuch | erledigt, **außer:** Inventar stammt aus der Beschreibung statt aus dem Video; Backup nicht untersucht; PHP-/WordPress-Version und Speichergrenzen des Live-Systems nicht geprüft |
| 1 | Datenmodell, REST, Einrichtung, erster Start | erledigt, Rechte-Tests bestehen |
| 2 | Eintrag erfassen, Liste, Bearbeiten, Favorit, Suche, Fotos | erledigt |
| 3 | Import von Backup und CSV, Importbericht | gebaut und mit künstlicher Testdatei geprüft; **Prüfzahlen des echten Backups offen** |
| 4 | Kalender und Statistiken | erledigt, jede Auswertung gegen Handrechnung getestet |
| 5 | Ziele, wichtige Tage, Berichte, Rückblick, Favoriten, Fotogalerie | erledigt; „wie im Video“ offen |
| 6 | MCP, OAuth, Freigaben, Protokoll | lokal erledigt; **Verbindung aus Claude offen** |
| 7 | Erinnerungen, PIN, Aussehen, Export, Sichern, Erfolge, Sprachnotiz, Einwilligungen, Konto löschen | erledigt; Zustellung von Push und Sprachnotiz ungeprüft |

## Fehler, die der Loop gefunden und behoben hat

- ZIP-Export (Sicherung, Fotos) lieferte über HTTP einen Serverfehler, obwohl der Unit-Test grün war (`wp_tempnam` gibt es nur im Adminbereich).
- Notizfeld mit Formatierung: Bei schnellem Tippen ging Text verloren und die Schreibmarke sprang, weil ein verspäteter älterer Stand zurückgeschrieben wurde.
- Nach einem Import standen die fünf Standard-Stimmungen doppelt neben den importierten.
- Wiederherstellung aus der Sicherung vertauschte die Reihenfolge zeitgleicher Einträge.
- Die Plus-Schnellwahl blieb beim Seitenwechsel offen und blockierte die Seite.
- Adresswechsel direkt nach dem Laden wurde verschluckt.
- Zeilenumbrüche in per MCP angelegten Notizen wurden verdoppelt.
- Im Wochentags-Diagramm war der Balken der schlechtesten Stufe unsichtbar.
- Barrierefreiheit: drei Schaltflächen, deren vorgelesener Name nicht zur sichtbaren Beschriftung passte.
- Aus der Gegenprüfung: Hinweis „bester Wochentag“ widersprach dem Diagramm; Inhalte in Dialogen wurden am Desktop gestaucht (Fortschrittsbalken fehlte); Erfolge und Galerie zählten Fotos unterschiedlich.

## Nächster Schritt

1. Lukas legt Video und Screenshots nach `reference/`, Backup und CSV nach `private/` (lokal, nicht in der Cloud-Sitzung, siehe Frage 2).
2. `node scripts/backup-struktur.mjs private/backup.daylio` ausführen und die Ausgabe (nur Struktur und Zählwerte) mit `includes/import.php` abgleichen. Erwartete Nacharbeit: Feldnamen der Skalen und Skalenwerte, Wiederholung der Ziele, Felder der wichtigen Tage, Einheit der Zeitwerte, Monatszählung der Ziel-Haken.
3. Echtes Backup lokal in ein leeres Konto importieren, `GET /counts` gegen die Prüfzahlen legen, CSV exportieren und mit `scripts/csv-vergleich.mjs` vergleichen.
4. Einzelbilder aus dem Video ziehen, `docs/bildschirme.md` um Sekundenangaben und fehlende Bildschirme ergänzen, je Bildschirm eine Abweichungsliste schreiben.
5. Test am eigenen Handy; Rückmeldungen als neue Prüfpunkte in `docs/abnahme.md`.

## Offene Fragen an Lukas

Zu den Annahmen der Projektbeschreibung (alle neun wurden so umgesetzt, wie sie dort stehen; keine ist bestätigt):

1. **Annahmen 1 bis 4** (selbst einrichten, Plattform WordPress-Plugin, Hosting, eigenes Design): Stimmen sie? Sie prägen die Architektur.
2. **Echtes Backup:** Es enthält Namen und Angaben Dritter. Soll die Abnahme auf deinem Rechner laufen (empfohlen), statt das Backup in eine Cloud-Sitzung hochzuladen?
3. **Repository:** Das Projekt liegt im Unterordner `mediclever-tagebuch/` des Marktplatz-Repositorys, weil nur dieses erreichbar war. Soll es ein eigenes Repository bekommen?
4. **Kostenlos oder Premium (Annahme 5):** Jede Funktion hat einen Schalter. Welche Teile sind Premium?
5. **Schichtjournal (Annahme 6):** zusammenlegen oder trennen?
6. **Live-System:** PHP- und WordPress-Version, Speicherplatz, Upload-Grenze, Apache oder nginx, echter Cron-Aufruf möglich? Kommt der `Authorization`-Kopf bei PHP an (manche Hoster entfernen ihn)? Werden Adressen unter `/.well-known/` an WordPress durchgereicht?
7. **Konto löschen:** entschieden und umgesetzt (zwei Haken). Offen bleibt nur: Darf das Konto wirklich nie ohne die Daten gelöscht werden? Siehe Entscheidung 14.
8. **Stimmung löschen:** entschieden und umgesetzt (Ersatz-Stimmung wählen und bestätigen).
9. **Cleverle:** Es gibt keine Grafik im Material. Soll Cleverle in Leerzuständen erscheinen? Dann bitte eine Datei.
10. **Name:** „MediClever Tagebuch“ ist Arbeitstitel.
11. **Farbschemata der Stufen:** Die vier Schemata (Klar, Ruhig, Warm, Zurückhaltend) sind eigene Vorschläge; der Styleguide regelt keine Stimmungsfarben. Passt das zur Marke?
12. **Icon-Set:** Vorschlag Lucide (ISC), siehe `docs/lizenzen.md`. Einverstanden?

Nicht von Claude Code lösbar (stehen so in der Projektbeschreibung): Hosting-Frage und Vertrag, rechtliche Prüfung (Einwilligungstexte, Auftragsverarbeitung, Datenschutz-Folgenabschätzung, Nutzung unter 18). Die Einwilligungstexte in der App sind Entwürfe ohne rechtliche Prüfung.

## Nicht geprüft oder nicht prüfbar in dieser Umgebung

- Vergleich mit dem Vorbild (kein Video).
- Import des echten Backups, Prüfzahlen, CSV-Abgleich.
- Verbindung als Konnektor in Claude, `claude mcp add`, MCP Inspector (kein öffentlicher HTTPS-Server).
- Zustellung von Web-Push über echte Push-Dienste; WP-Cron ohne Seitenaufrufe.
- Kamera, Mikrofon, Teilen als Bild, Ziehen mit dem Finger: nur am echten Gerät.
- nginx: Der Fotoordner ist per `.htaccess` gesperrt; unter nginx braucht es `MCT_STORAGE_DIR` außerhalb des Dokumentenstamms.
- MySQL statt MariaDB, PHP 8.1 und 8.2, WordPress unter 7.1.
- Export sehr großer Bestände (207 MB Fotos als ZIP) auf Webspace mit Zeitgrenze.
- Lokal installiertes WordPress hat keine deutsche Sprachdatei; die Anmeldeseite ist deshalb englisch. Die App selbst ist deutsch.

## Rückmeldung von Lukas am 16.11.2026

- Import zuerst mit der CSV-Datei ausprobieren. MCP erst testen, wenn alles lokal läuft. Gerätetests macht Lukas nach der Installation. Live-System später.
- Repository bleibt vorerst, wo es ist. Screenshots des Vorbilds fertigt Lukas an. Die neun Annahmen liest er noch.
- Cleverle kommt noch dazu, Material folgt.
- **Neu:** Bei der Android-App soll von Anfang an mitgedacht werden, dass App und Website synchron sind. Die REST-Schnittstelle ist dafür die Grundlage; was für eine App noch fehlt (Anmeldung ohne Browser-Cookie, Änderungen seit einem Zeitpunkt abrufen, Umgang mit gleichzeitigen Änderungen), ist nicht gebaut und braucht eine eigene Entscheidung.
- Lukas klickt die App in der Vorschau durch und meldet, was ihm gefällt und was nicht. Die Rückmeldung wandert als neue Prüfpunkte in `abnahme.md`.

Lokale Vorführung: `/?demo=anna` meldet das Testkonto ohne Passwort an (nur im Entwicklungs-Theme, nicht im Plugin).
