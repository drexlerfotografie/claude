# Abnahmeliste

Status je Prüfpunkt:

- **erfüllt** – automatisch getestet (Test genannt) und von der unabhängigen Gegenprüfung nicht beanstandet
- **umgesetzt, ungeprüft** – gebaut, aber ohne automatischen Test oder hier nicht prüfbar
- **offen** – fehlt oder hängt an fehlendem Material
- **bewusst abweichend** – anders gelöst als beschrieben; braucht die Zustimmung von Lukas

Kürzel der Tests: `P:` PHPUnit (`tests/php`), `E:` Playwright (`tests/e2e`). Zustände „leer, gefüllt, Fehler“ sind je Bildschirm genannt, wo es sie gibt.

Grundsatz: Der Vergleich mit dem Vorbild prüft Struktur und Vollständigkeit. Weil Video und Referenz-Screenshots fehlen, ist dieser Vergleich **für alle Bildschirme offen**. Geprüft ist bisher gegen die Projektbeschreibung.

## A. Übergreifend

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| A1 | Fünf Hauptbereiche in der unteren Leiste: Einträge, Statistiken, Plus in der Mitte, Kalender, Mehr | erfüllt | E: 02 „Einträge“ |
| A2 | Kopfzeile: Monat mit Vor und Zurück, Monatsauswahl, Suche | erfüllt | E: 02 „Einträge“ |
| A3 | Mobil zuerst (360 bis 430 px), kein Überlauf | erfüllt | E: 05 „schmales Gerät“, „alle Bildschirme: mobil-*“ |
| A4 | Desktop zweispaltig nutzbar | erfüllt | E: 05 „desktop-*“ |
| A5 | Hell und Dunkel, Standard nach Gerät | erfüllt | E: 02 „Aussehen“, 05 alle Varianten |
| A6 | Tippflächen mindestens 44 px | erfüllt | E: 05 (misst jede Schaltfläche auf 33 Bildschirmen) |
| A7 | Kontrast und Barrierefreiheit: axe ohne schwere Fehler | erfüllt | E: 05, alle vier Varianten |
| A8 | Tastaturbedienung, Dialoge schließen mit Escape, Fokus kehrt zurück | erfüllt | E: 05 „Tastaturbedienung“ |
| A9 | Deutsch, Du-Form, kein Denglisch, ruhiger Ton | umgesetzt, ungeprüft | Sichtprüfung nötig |
| A10 | Jede Stufe an Symbol und Beschriftung erkennbar, nicht nur an Farbe | erfüllt | eigene Symbole m1–m5, Beschriftung überall |
| A11 | Keine externen CDNs, keine Tracker | erfüllt | E: jeder Test prüft fremde Anfragen |
| A12 | Browser-Konsole ohne Fehler | erfüllt | E: jeder Test |
| A13 | Farben und Schrift aus der MediClever-CI | erfüllt | Styleguide 2.0: Roboto, #11202C, #2D323C, #B9303F |
| A14 | Nichts vom Vorbild übernommen (Name, Logo, Gesichter, Symbole, Texte) | umgesetzt, ungeprüft | ohne Referenz nicht gegenprüfbar |
| A15 | Struktur wie im Vorbild: dieselben Elemente in sinnvoller Reihenfolge | offen | Video fehlt |
| A16 | Cleverle höchstens in Leerzuständen | bewusst abweichend | Cleverle kommt gar nicht vor, weil keine Grafik vorliegt |

## B. Eintrag erfassen

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| B1 | Plus öffnet Schnellwahl: Heute, Gestern, Anderer Tag, Neues Ziel, Neuer wichtiger Tag | erfüllt | E: 02 „Plus-Schnellwahl“ |
| B2 | Schritt 1: Datum, Uhrzeit, Stimmung; fünf Stufen, mehrere Stimmungen je Stufe | erfüllt | E: 01, 02 „Stimmungen“ |
| B3 | Schritt 2: einklappbare Gruppen und Skalen in der Reihenfolge des Nutzers | erfüllt | E: 01, 02 „Reihenfolge“ |
| B4 | Speichern oben und unten | erfüllt | E: 01, 02 |
| B5 | Von beiden Schritten Link in die Einrichtung | erfüllt | Links „Stimmungen bearbeiten“, „Einrichtung bearbeiten“ |
| B6 | Aktivitäten: Symbol mit Beschriftung, Mehrfachauswahl, Plus je Gruppe | erfüllt | E: 01 „Einrichtung ändern“ |
| B7 | Skalen: Text als feste Punkte, Zahl als Schieber, Zeit mit Plus und Minus | erfüllt | E: 01 |
| B8 | Ziele des Tages zum Abhaken mit Serie | umgesetzt, ungeprüft | im Editor sichtbar, kein eigener Test |
| B9 | Schnelle Notiz als Einzeiler | erfüllt | E: 01 |
| B10 | Ganze Notiz mit Titel, fett, Listen, Schreibvorlagen | erfüllt | E: 01 „ganze Notiz“ |
| B11 | Fotos aufnehmen oder aus der Galerie, mehrere pro Eintrag | erfüllt (Galerie), umgesetzt, ungeprüft (Kamera) | E: 01; Kamera nur am Gerät prüfbar |
| B12 | Sprachnotiz aufnehmen und speichern | umgesetzt, ungeprüft | braucht Mikrofon |
| B13 | Mehrere Einträge pro Tag | erfüllt | P: SetupEntries „mehrere Einträge“ |
| B14 | Einträge nach Mitternacht: fragen, Vortag, aktueller Tag | erfüllt | E: 02 „nach Mitternacht“, P: Mcp „heute“ |
| B15 | Bearbeiten, Löschen, Favorit | erfüllt | E: 02 „Einträge“ |
| B16 | Fehlerzustand: ohne Stimmung kein Speichern, Meldung | umgesetzt, ungeprüft | Schaltfläche gesperrt, Meldung vorhanden |

## C. Einträge

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| C1 | Monatsliste, neueste zuerst, „Heute anzeigen“ | erfüllt | E: 02 |
| C2 | Karte: Stimmung, Wochentag und Datum, Uhrzeit, Aktivitäten als Chips, Skalen als Kacheln, Notiz, Fotoraster, erreichte Ziele | erfüllt | E: 01, 03 |
| C3 | Hinweis „x Tage fehlen“ zwischen den Karten | erfüllt | E: 02 |
| C4 | Karte zum Monatsbericht | erfüllt | E: 02 (vergangene Monate) |
| C5 | Karten für wichtige Tage und Jahrestage | umgesetzt, ungeprüft | kein eigener Test |
| C6 | Zitat des Tages, abschaltbar | erfüllt | E: 02; eigene Texte |
| C7 | Zeile mit den heutigen Zielen | erfüllt | E: 02 |
| C8 | Rückblick „Vor 1 Jahr“ | erfüllt | E: 02 |
| C9 | Suche nach Text, Stimmung, Aktivität | erfüllt | E: 02 „Suche“, P: „suche“ |
| C10 | Leerzustand | erfüllt | E: 01, 02 „Tagebuch leeren“ |

## D. Statistiken

Alle Zahlen rechnet der Server. Jede Auswertung ist gegen eine von Hand nachgerechnete Stichprobe getestet (`P: StatsGoalsTest`).

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| D1 | Auswertungen für den gewählten Monat, Jahresansicht über zwölf Monate | erfüllt | P, E: 02 „Statistiken“ |
| D2 | Tage in Folge mit längster Reihe | erfüllt | P |
| D3 | Stimmungsverlauf als Linie oder Balken, Aktivität überlagert | erfüllt | E: 02 |
| D4 | Stimmungszähler als Halbring | erfüllt | E: 02 |
| D5 | Aktivitätenzähler mit Rangfolge und „Alle anzeigen“ | erfüllt | E: 02 (Überschrift), P (Zahlen) |
| D6 | Verteilung nach Gruppenfarbe als Ring | erfüllt | E: 02, P |
| D7 | Durchschnitt je Wochentag | erfüllt | P |
| D8 | Einfluss einer Aktivität | erfüllt | P |
| D9 | Stabilität 0 bis 100 | erfüllt | P |
| D10 | „Oft zusammen“ je Stimmung | erfüllt | P |
| D11 | Längste Serie guter Tage mit Aktivitäten | erfüllt | P |
| D12 | Je Skala ein Diagramm mit Durchschnitt und Veränderung zum Vormonat | erfüllt | P, E: 02 |
| D13 | Ziele-Übersicht | erfüllt | E: 02 |
| D14 | Hinweiszeilen in Klartext | erfüllt | P |
| D15 | Leerzustand „zu wenig Daten“ | erfüllt | P, E: 02 |
| D16 | Auswertung als Bild teilen | umgesetzt, ungeprüft | kein Test |
| D17 | Erfolge je Ziel („Ziele-Übersicht und Erfolge“) | erfüllt | Stufen im Ziel-Detail, P |

## E. Kalender

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| E1 | Monatsraster mit Wochentagen, Wochenanfang einstellbar | erfüllt | E: 02 „Kalender“ |
| E2 | Stimmung je Tag farbig und als Symbol, leere Tage mit Plus | erfüllt | E: 02 |
| E3 | Filter nach Stimmung oder Aktivität mit Zähler, kombinierbar über „hinzu“ | erfüllt | E: 02, P „kalender“ |
| E4 | Hinweiszeile und Stimmungszähler unter dem Raster | erfüllt | E: 05 Screenshot, P |

## F. Mehr

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| F1 | Ziele: täglich, x-mal pro Woche, feste Wochentage; mit Aktivität oder frei | erfüllt | P „serien“, E: 02 |
| F2 | Ziele: Erinnerung, Startdatum, Serie, Erfolgsrate 7 und 30 Tage, Abschlüsse je Monat, Kalender, Stufen, Archiv, Reihenfolge | erfüllt | P, E: 02 „Ziele“ |
| F3 | Berichte wöchentlich und monatlich mit Veränderung, Verlauf, Zielen, Fotos; Benachrichtigung abschaltbar | erfüllt | P „bericht“, E: 02 |
| F4 | Rückblick | erfüllt | P, E: 02 |
| F5 | Wichtige Tage: Name, Datum, Kategorie, Foto, Notiz, jährlich, „seit x“, „nächster Jahrestag in y“, Erinnerungen | erfüllt | P „wichtige tage“, E: 02 |
| F6 | Favoriten | erfüllt | E: 02 |
| F7 | Fotogalerie nach Monat | erfüllt | E: 02, 03 |
| F8 | Erfolge mit eigenen Namen und Grafiken | erfüllt | P, E: 02 |
| F9 | Erinnerungen: mehrere Uhrzeiten mit Text | erfüllt (Verwaltung), umgesetzt, ungeprüft (Zustellung) | E: 02, P: Reminders |
| F10 | Aussehen: Farbschema, eigenes Schema, Schrift, Hell/Dunkel/System | erfüllt | E: 02 |
| F11 | PIN-Sperre | erfüllt | E: 02 |
| F12 | Einstellungen: Zitate, Mitternacht, Rückblick, Wochenanfang | erfüllt | E: 02 |
| F13 | Daten: Sichern und Wiederherstellen, Import, Export als PDF und CSV, Fotos exportieren | erfüllt | E: 02 „Export“, 03; P |
| F14 | Schlichte Konto-Seite statt Premium-Werbung | erfüllt | E: 02 „Konto“ |

## G. Selbst einrichten

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| G1 | Stimmungen: Name, Symbol, mehrere pro Stufe, Reihenfolge, Archiv; fünf Stufen fest | erfüllt | E: 02, P |
| G2 | Gruppen: Name mit Emoji, Farbe, Reihenfolge, eingeklappt, Archiv | erfüllt | E: 01, P |
| G3 | Aktivitäten: Name, durchsuchbare Symbolauswahl mit Kategorien, Farbe, Gruppe; umbenennen, verschieben, archivieren, ersetzen, löschen | erfüllt | E: 01, 02 |
| G4 | Skalen: Zahl, Text mit Punkten, Zeit; Farbe, Symbol, Anzeige, Trendrichtung | erfüllt | E: 01, P |
| G5 | Gruppen und Skalen teilen eine Reihenfolge, per Ziehen sortierbar | erfüllt (Tastatur), umgesetzt, ungeprüft (Ziehen mit dem Finger) | E: 02, P |
| G6 | Schreibvorlagen: Titel, formatierter Text, Reihenfolge, Standard wiederherstellen | erfüllt | E: 02 |
| G7 | Archiviertes bleibt in alten Einträgen und Auswertungen sichtbar, fehlt beim Erfassen | erfüllt | P, E: 02 |
| G8 | Löschen fragt nach, nennt die Zahl betroffener Einträge, bietet Archivieren an | erfüllt | E: 02, P |
| G9 | Jede Änderung gilt nur für das eigene Konto | erfüllt | P: IsolationTest |
| G10 | Erster Start mit Startpaketen „Allgemein“, „Schichtdienst“, leer oder Import | erfüllt | E: 01, 03 |
| G11 | Neues Konto in unter 3 Minuten eingerichtet | erfüllt (automatisch in Sekunden) | E: 01; Zeit eines Menschen offen |

## H. Datenmodell und Rechte

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| H1 | Jede Tabelle trägt eine Nutzer-ID, Präfix `mct_` | erfüllt | `includes/schema.php` |
| H2 | Fremde IDs gezielt angefragt: 24 Zugriffe, alle abgewiesen | erfüllt | P: IsolationTest |
| H3 | Archivieren ist ein Zeitstempel; Löschen entfernt abhängige Zeilen und Dateien | erfüllt; bei Stimmungen bewusst abweichend, von Lukas bestätigt | P, E: 02 „Stimmungen“; Entscheidung 13 |
| H4 | Notizen durch Positivliste | erfüllt | P „positivliste“ |
| H5 | Lokales Datum gespeichert und indiziert | erfüllt | Schema, P |
| H6 | Jede Funktion hängt an einem Schalter | erfüllt | P „funktionsschalter“ |
| H7 | Monatsansicht mit großem Bestand unter 300 ms | erfüllt (rund 1.800 erfundene Einträge) | P „antwortet schnell“; echter Bestand offen |

## I. Import

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| I1 | Browser öffnet die Datei lokal, Vorschau mit Zählwerten | erfüllt | E: 03 |
| I2 | Wahl: leeres Tagebuch oder zusammenführen | erfüllt | E: 03, P |
| I3 | Pakete, unterbrechbar und fortsetzbar | erfüllt (Server), umgesetzt, ungeprüft (Pause-Knopf) | P „unterbrechbar“ |
| I4 | Zweiter Lauf erzeugt keine Doppelten | erfüllt | P, E: 03 |
| I5 | Bericht: übernommen, übersprungen, Fehler | erfüllt | E: 03 |
| I6 | Stolperstellen: Monat ab 0, wichtige Tage ab 1, Zeitzonen, Skalenformen, HTML, Pfade, Prüfsumme als Name, Symbole, Leerzeichen | erfüllt für die künstliche Testdatei | P „stolperstellen“ |
| I7 | CSV-Import mit Rückfrage bei Fehlendem | erfüllt | P, E: 03 |
| I8 | **Prüfzahlen des echten Backups** (1.765 Einträge, 375/680/426/154/130 usw.) | **offen** | Backup liegt nicht vor |
| I9 | **CSV-Export stimmt zeilenweise mit `private/export.csv` überein** | **offen** | Datei liegt nicht vor; Skript `csv-vergleich.mjs` steht bereit |
| I10 | Monatszählung der Ziel-Haken an `goalSuccessWeeks` geprüft | **offen** | braucht das echte Backup |
| I11 | Einheit der Zeitwerte an der CSV gegengeprüft | **offen** | Erkennung nach Größenordnung, unbelegt |

## J. MCP

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| J1 | Zehn Tools mit Titel und Kennzeichen „nur lesen“ und „löschend“ | erfüllt | P, E: 04 |
| J2 | Unscharfe Zuordnung, bei Unbekanntem Trefferliste, nichts wird stillschweigend angelegt | erfüllt | P |
| J3 | Wiederholungsschlüssel verhindert zweiten Eintrag | erfüllt | P, E: 04 |
| J4 | Ohne Datum gilt heute in der Zeitzone des Nutzers, mit Mitternachtsregel | erfüllt | P |
| J5 | Fotos und Sprachnotizen nicht über MCP | erfüllt | kein Tool dafür |
| J6 | Antworten knapp und seitenweise | erfüllt | P |
| J7 | Streamable HTTP ohne Sitzungszustand | erfüllt | E: 04 mit offiziellem SDK |
| J8 | OAuth mit PKCE, dynamische Registrierung, Ablauf und Erneuerung, Rückruf-Adressen von Claude | erfüllt | P, E: 04 |
| J9 | Drei Rechtestufen auf der Freigabeseite, Löschen standardmäßig gesperrt | erfüllt | P, E: 04 |
| J10 | Verbundene Clients mit letzter Nutzung, Widerruf sperrt sofort | erfüllt | P, E: 04 |
| J11 | Protokoll ohne Inhalte, Begrenzung pro Minute | erfüllt | P |
| J12 | Eintrag per MCP angelegt und in der App sichtbar | erfüllt | E: 04 |
| J13 | **Als eigener Konnektor in Claude hinzugefügt, mit `claude mcp add --transport http` verbunden, mit dem MCP Inspector getestet** | **offen** | braucht einen von außen erreichbaren HTTPS-Server |

## K. Datenschutz und Sicherheit

| Nr. | Prüfpunkt | Status | Beleg |
| --- | --- | --- | --- |
| K1 | Einwilligung vor dem ersten Eintrag, getrennte Einwilligung für KI, beide widerrufbar | erfüllt | P, E: 01, 02, 04 |
| K2 | Text zur KI-Freigabe nennt die Weitergabe an den Anbieter | erfüllt | E: 04 |
| K3 | Vollständiger Export und vollständige Löschung samt Fotos; Daten und MediClever-Konto getrennt wählbar | erfüllt nach Vorgabe von Lukas | P, E: 02 „Konto“; Entscheidung 14 |
| K4 | Fotos nie öffentlich abrufbar; Aufnahmeort und Gerätedaten entfernt | erfüllt | P: IsolationTest |
| K5 | Keine Inhalte in Logs, Fehlermeldungen, Statistik | erfüllt | P „protokoll“ |
| K6 | Hinweis: keine Patienten- und Einsatzdaten | erfüllt | Erster Start, Mehr, Konto |
| K7 | Schutz gegen XSS, gefälschte Anfragen, Durchprobieren von Token | erfüllt | P; Nonce, PKCE, Begrenzungen |
| K8 | Nutzung ab 18 | erfüllt (Selbstauskunft) | E: 01 |
| K9 | Rechtliche Prüfung, Hosting-Frage | **offen** | kann Claude Code nicht lösen |

## Was „fertig“ heißt – Stand

| Kriterium | Stand |
| --- | --- |
| Alle Punkte erfüllt oder mit Zustimmung bewusst abweichend | **nein** – siehe offene Punkte A15, I8–I11, J13, K9 und die Zustimmung zu A16, H3, K3 |
| `npm run verify` fehlerfrei, Konsole ohne Fehler | ja |
| Import erfüllt alle Prüfzahlen | **offen** – nur mit künstlicher Testdatei belegt |
| Eintrag über MCP anlegen, erscheint in der App | ja (lokal, mit dem offiziellen SDK) |
| Monatsansicht unter 300 ms | ja (rund 1.800 erfundene Einträge) |
| axe ohne schwere Fehler | ja (33 Bildschirme, vier Varianten) |

## Unabhängige Gegenprüfung (16.11.2026)

Ein frischer Prüfer ohne Kenntnis der Umsetzung hat 67 Screenshots (alle 33 in „mobil-hell“, dazu 18, 11 und 5 aus den anderen Varianten) gegen `docs/bildschirme.md` geprüft. Referenzbilder des Vorbilds hatte auch er nicht.

Urteil vor der Nachbesserung: 15 Bildschirme „erfüllt“, 15 „erfüllt mit Mängeln“, 3 „nicht erfüllt“ (Eintrag Schritt 1, Statistik, Ziel-Detail am Desktop). 34 Einzelbefunde.

| Befund | Umgang |
| --- | --- |
| Hinweis „bester Wochentag“ widersprach dem Diagramm | behoben, Test `wochentags_hinweis_widerspricht_dem_diagramm_nicht` |
| Fortschrittsbalken im Ziel-Detail fehlte am Desktop; Rahmen im Skalen-Dialog überlappt | behoben (Inhalte wurden im scrollenden Dialog gestaucht) |
| Balken lag auf der Achsenbeschriftung; Achse „++ … −−“ ohne Erklärung | behoben, Legende ergänzt |
| Aktivitätsnamen mitten im Wort umbrochen | behoben (breitere Spalten, deutsche Silbentrennung) |
| Erfolge zählten 9 Fotos, Galerie 8 | behoben, Test `erfolge_zaehlen_fotos_wie_die_galerie` |
| „Vor 1 Jahr · 2 Einträge“ meinte alle Jahre | behoben |
| Ziel-Detail: Abschlüsse vor dem Startdatum, Monatssumme ohne Hinweis | Beispieldaten korrigiert, Überschrift präzisiert |
| Sichtbarer Fokusrahmen um den ganzen Inhalt; Ziffern der Anleitung ragten aus der Karte; fehlende Abstände (PIN, Überschriften) | behoben |
| „1k“, „Längste Reihe“, „Vor einem Jahr“ neben „Vor 1 Jahr“, „aus“, Dateiendung der anderen App im Text | behoben |
| Ziele-Liste ohne Kartenform; Kalender am Desktop schmaler; Fotoraster am Desktop grob; Dialog im dunklen Modus ohne Kante | behoben |
| Datum und Uhrzeit im US-Format (Schritt 1) | **kein Fehler der App:** native Eingabefelder folgen der Sprache des Browsers, und der Test-Browser läuft englisch. Am deutschen Gerät erscheint TT.MM.JJJJ. Am Handy gegenprüfen. |
| Desktop: ungleich hohe Karten im zweispaltigen Raster, einzelne Karte neben leerer Fläche | **offen**, gering |
| Stimmungsfilter in der Suche nur als Symbole (mit Vorlesetext und Tooltip) | **offen**, gering |
| Fotos im Bericht, Monatsbericht-Karte, Karte für wichtige Tage, Archive, Schieber-Skala, Import-Vorschau | aus den Screenshots nicht beurteilbar (zustandsabhängig); teils durch Ablauf-Tests belegt |

Eine zweite Gegenprüfung nach der Nachbesserung hat **nicht** stattgefunden. Die Spalte „erfüllt“ oben stützt sich deshalb auf die automatischen Tests und diese eine Prüfrunde, nicht auf ein abschließendes „erfüllt“ des Prüfers je Punkt.
