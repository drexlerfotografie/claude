# Abnahmeliste

Status: **erfüllt** (durch Test oder Screenshot belegt), **Gerät** (gebaut, aber nur auf einem echten Gerät prüfbar), **offen**, **abweichend** (bewusst anders als in der Projektbeschreibung, braucht Lukas’ Zustimmung).

Belege: `AppFlowTest` und `DataOpsTest` laufen durch die echte App mit echter Datenbank, `core`-Tests rechnen nach, Screenshots liegen nach `./verify.sh` unter `app/build/shots/`.

## Eintrag erfassen
| Punkt | Status | Beleg |
|---|---|---|
| Plus öffnet Schnellwahl: Heute, Gestern, Anderer Tag, Neues Ziel, Neuer wichtiger Tag | erfüllt | AppFlowTest |
| Schritt 1: Datum, Uhrzeit, Stimmung; fünf Stufen, mehrere Stimmungen je Stufe | erfüllt | eintrag_schritt1, AppFlowTest |
| Schritt 2: Gruppen und Skalen in Reihenfolge des Nutzers, einklappbar | erfüllt | eintrag_schritt2 |
| Aktivitäten mehrfach wählbar, je Gruppe „Neu“ | erfüllt | AppFlowTest |
| Skalen: feste Punkte, Zeit mit Plus und Minus | erfüllt | eintrag_schritt2 |
| Ziele des Tages mit Hinweis auf die Serie | erfüllt | Code, Screenshot unterhalb des sichtbaren Bereichs |
| Schnelle Notiz, ganze Notiz mit Titel, Formatierung und Vorlagen | erfüllt | NoteMarkupTest; Editor selbst: Gerät |
| Fotos aufnehmen oder aus Galerie, mehrere | Gerät | Kamera und Galerie gibt es im Test nicht |
| Sprachnotiz aufnehmen und abspielen | Gerät | Mikrofon gibt es im Test nicht |
| Speichern oben und unten, Links in die Einrichtung | erfüllt | AppFlowTest |
| Mehrere Einträge pro Tag, bearbeiten, löschen mit Rückfrage, Favorit | erfüllt | AppFlowTest |
| Einträge nach Mitternacht: fragen, Vortag, aktueller Tag | erfüllt (Logik), Dialog: Gerät | |

## Einträge, Kalender, Statistiken
| Punkt | Status | Beleg |
|---|---|---|
| Monatsliste, neueste zuerst, „Heute anzeigen“ | erfüllt | eintraege, eintraege_vormonat |
| Karte mit Stimmung, Datum, Uhrzeit, Aktivitäten, Skalen, Notiz, Fotos, Zielen | erfüllt | eintraege |
| „x Tage fehlen“, Monatsbericht-Karte, wichtige Tage | erfüllt | eintraege_vormonat |
| Gedanke des Tages (abschaltbar), Ziele heute, „Vor 1 Jahr“ | erfüllt | eintraege |
| Suche nach Text, Stimmung, Aktivität | erfüllt | SearchTest, suche |
| Kalender mit Wochenanfang, Farben, Plus zum Nachtragen, Filter mit „hinzu“, Hinweis, Zähler | erfüllt | kalender, CalendarTest |
| Alle 13 Auswertungen der Projektbeschreibung, Monat und Jahr, „zu wenig Daten“ | erfüllt | PeriodStatsTest (von Hand nachgerechnet), statistiken |
| Auswertung als Bild teilen | Gerät | |

## Mehr
| Punkt | Status | Beleg |
|---|---|---|
| Ziele: täglich, x-mal pro Woche, Wochentage, Verknüpfung, Erinnerung, Serie, Erfolgsrate 7/30, Monate, Kalender, Stufen, Archiv, Reihenfolge | erfüllt | GoalsTest, ziele, ziel_detail |
| Berichte Woche und Monat mit Veränderung | erfüllt | ReportsTest, berichte |
| Rückblick, Wichtige Tage, Favoriten, Fotos, Erfolge | erfüllt | Screenshots, EntryListTest, AchievementsTest |
| Erfolge mit eigenen Grafiken | abweichend | eigene Namen, aber nur Symbol je Kategorie statt eigener Abzeichen |
| Einrichtung: Stimmungen, Gruppen, Aktivitäten (umbenennen, verschieben, archivieren, ersetzen, löschen), Skalen, Vorlagen, Sortieren per Ziehen | erfüllt, Ziehen: Gerät | einrichtung_* |
| Archiviertes bleibt in alten Einträgen sichtbar; Löschen nennt Zahl der Einträge und bietet Archivieren an | erfüllt | Code, Dialoge |
| Erster Start mit Startpaketen „Allgemein“, „Schichtdienst“, leer | erfüllt | AppFlowTest |
| Erinnerungen, Ziel-Erinnerungen, wichtige Tage, Berichte als Benachrichtigung | Gerät | Planung getestet (DataOpsTest), Zustellung nicht |
| Aussehen: Hell, Dunkel, System; vier Farbschemata und eigenes; Schrift | erfüllt | aussehen |
| PIN-Sperre | erfüllt | AppFlowTest |
| Sichern und Wiederherstellen, CSV-Export, Fotos exportieren | erfüllt | DataOpsTest |
| PDF-Export | Gerät | Testumgebung kann keine PDFs erzeugen |
| Homescreen-Widget | Gerät | |
| Konto-Seite statt Werbeseite | erfüllt | konto |

## Import
| Punkt | Status | Beleg |
|---|---|---|
| `.daylio`-Backup lesen, Vorschau mit Zählwerten, leer oder zusammenführen, Bericht | erfüllt mit künstlicher Datei | DataOpsTest, DaylioBackupTest |
| Zweiter Lauf ohne Doppelte, unterbrechbar | erfüllt | DataOpsTest |
| Stolperstellen (Monat ab 0, Zeitzonen, HTML, Pfade, Dateityp am Inhalt, Namen kürzen) | erfüllt | DaylioBackupTest |
| **Prüfzahlen des echten Backups (1.765 Einträge usw.)** | **offen** | Das echte Backup lag nicht vor. Feldnamen für Skalen und wichtige Tage sind Annahmen. Die Vorschau in der App zeigt alle Zählwerte und Hinweise zum Abgleich. |
| CSV-Import mit Rückfrage, Export zeilengleich zur Vorlage | erfüllt für eigenes Format, Vergleich mit echter Datei offen | CsvFormatTest |

## Konto und Abgleich
| Punkt | Status | Beleg |
|---|---|---|
| Firestore-Regeln: nur eigenes Konto, Feldprüfung, Angriffe abgewehrt | erfüllt | 458 Regeltests gegen den Emulator |
| Abgleich-Logik: zwei Geräte, Konflikte, Löschungen, Abbruch | erfüllt | SyncEngineTest, DataOpsTest |
| Anmeldung (E-Mail, Google), Firestore-Anbindung, Konto löschen | **offen bis Firebase-Projekt** | Der Code baut, lief aber noch nie gegen Firebase. |
| MCP-Schnittstelle | abweichend | verschoben, siehe entscheidungen.md |
| Funktionsschalter für kostenlos und Premium | offen | noch nicht eingebaut |

## Datenschutz
| Punkt | Status |
|---|---|
| Einwilligung vor dem ersten Eintrag, Alter 18, Hinweis zu Patientendaten | erfüllt |
| Vollständiger Export und vollständige Löschung als Selbstbedienung | erfüllt (lokal), Server: offen bis Firebase |
| Fotos privat, ohne Aufnahmeort und Gerätedaten | erfüllt (Neu-Kodierung), Sichtprüfung der EXIF-Daten: Gerät |
| Keine Inhalte in Logs, Benachrichtigungen, System-Backup | erfüllt |
| Rechtliche Prüfung (Texte, Auftragsverarbeitung, Folgenabschätzung, Minderjährige) | offen, Fachperson |

## Was „fertig“ heißt
| Punkt | Status |
|---|---|
| `./verify.sh` läuft fehlerfrei | erfüllt |
| Monatsansicht unter 300 ms | erfüllt (PerformanceTest: 2.000 Einträge in rund 7 ms) |
| Barrierefreiheit | teilweise: Tippflächen ab 48 dp, Beschriftungen für Screenreader, Farbe nie einziges Merkmal; kein automatischer Prüflauf |
| Test auf dem eigenen Handy | offen, Lukas |
