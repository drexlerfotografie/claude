# MediClever Tagebuch – Projektbeschreibung für Claude Code

> Textexport der Projektbeschreibung vom 16.11.2026 (Autor: Lukas), automatisch aus dem PDF erzeugt.
> Überschriften sind als Markdown gesetzt; Tabellen stehen als ausgerichteter Text in Codeblöcken, damit die Spalten lesbar bleiben. Links des Originals fehlen im Export.

```text
  ​Nov 16, 2026      ·   ​@Lukas
```

## Kurzfassung
```text
Gebaut wird ein Stimmungs- und Aktivitäten-Tagebuch für mediclever.de, das jeder
Nutzer selbst einrichtet und das sich per MCP mit einem KI-Assistenten verbinden lässt.

       Arbeitstitel: „MediClever Tagebuch“. Der endgültige Name ist offen.
       Vorbild: Funktionsumfang und Bedienlogik der App aus dem Referenzvideo. Design,
       Icons und Texte sind eigene MediClever-Arbeit.
       Bausteine: Web-App (mobil zuerst), WordPress-Plugin mit REST-Schnittstelle, MCP-
       Endpunkt.
       Erster Datenbestand: Import des vorhandenen Backups mit 1.765 Einträgen seit 2017
       und 1.751 Fotos.
       Arbeitsweise: Claude Code baut in Schleifen: bauen, starten, Screenshot aufnehmen,
       gegen Referenz und Abnahmeliste prüfen, nachbessern.
```

## Annahmen und offene Entscheidungen
```text
Das Dokument trifft neun Annahmen. Die Punkte 1 bis 4 prägen die Architektur und
sollten vor dem Start bestätigt sein.

  Nr.         Punkt                                 Annahme in diesem              Offen für Lukas
                                                    Dokument

  1           „Selbst einrichten“                   Jeder Nutzer legt              Stimmt die Lesart?
                                                    Stimmungen, Aktivitäten
                                                    und Skalen selbst an.
                                                    Gemeint ist nicht „selbst
                                                    hosten“.

  2           Plattform                             WordPress-Plugin auf           Bestätigen
                                                    mediclever.de, eingebunden
                                                    in die Lernzentrale, Nutzung
                                                    mit Konto.

  Nr.         Punkt                                 Annahme in diesem                Offen für Lukas
                                                    Dokument

  3           Hosting                               Entwicklung und Tests            Wer hat Datenbank-Zugriff?
                                                    laufen lokal. Der Server-        Vor dem Livegang klären,
                                                    Zugang ist ungeklärt, weil       siehe Datenschutz.
                                                    das Hosting-Konto nicht dir
                                                    gehört.

  4           Design                                Gleiche Funktionen und           CI-Farben und Schriften
                                                    Bedienlogik wie das Vorbild,     bereitstellen
                                                    aber eigenes Aussehen im
                                                    MediClever-Stil.

  5           Kostenlos oder                        Jede Funktion hängt an           Welche Teile sind Premium?
              Premium                               einem Schalter. Die Grenze
                                                    wird später festgelegt.

  6           Verhältnis zum                        Eigenständiges Tool. Das         Zusammenlegen oder
              Schichtjournal                        Tagebuch im Schichtjournal       trennen?
                                                    bleibt vorerst unberührt.

  7           Alter der Nutzer                      Nutzung ab 18, bis die           Mit Fachperson prüfen
                                                    Rechtslage für
                                                    Minderjährige geklärt ist.

  8           KI-Zugriff                            Standardmäßig aus. Jeder         Bestätigen
                                                    Nutzer schaltet ihn für sein
                                                    eigenes Konto frei. Löschen
                                                    per KI bleibt gesperrt.

  9           Nur als native App                    Homescreen-Widgets und           Bestätigen
              machbar                               Haptik kommen erst mit der
                                                    Android-App.
```

## Ausgangsmaterial
```text
Vier Quellen liegen vor. Sie kommen in zwei Ordner, die nie ins Repository eingecheckt
werden.

  Datei                             Ablage im Projekt                  Inhalt                     Wofür

  Bildschirmvideo                    reference/video.mp4               289 Sekunden, 1344 ×       Referenz für
  (.mp4)                                                               2992 px, zeigt fast alle   Bildschirme und
                                                                       Bereiche der App           Abläufe

  Datei                             Ablage im Projekt        Inhalt                   Wofür

  4 Screenshots                      reference/screens/      Stimmungswahl,           Referenz in voller
  (.png)                                                     Aktivitätenwahl, Plus-   Auflösung
                                                             Menü, Wichtige Tage

  Backup (.daylio)                   private/backup.daylio   325 MB, 1.765            Import und
                                                             Einträge, 1.751 Fotos,   Abnahmetest
                                                             komplette Einrichtung

  CSV-Export                         private/export.csv      1.765 Zeilen, ohne       Gegenprobe für
                                                             Fotos und Ziele          den Import

Regeln für den Umgang:

        reference/ und private/ stehen in .gitignore . Das Video zeigt private Fotos, das
       Backup ein komplettes Tagebuch.
       Tagebuchtexte, Namen und Fotos erscheinen nie in Commits, Testdaten, Logs,
       Screenshots im Repository oder in Fehlermeldungen.
       Automatische Tests laufen mit einer künstlichen Testdatei im selben Format
       ( tests/fixtures/sample.daylio ), die Claude Code selbst erzeugt.
       Das echte Backup dient nur dem lokalen Abnahmetest. Geprüft werden dort
       Zählwerte, keine Inhalte.

Erster Arbeitsschritt: Einzelbilder aus dem Video ziehen und ein Bildschirm-Inventar
anlegen.

  ffmpeg -i reference/video.mp4 -vf fps=1 reference/frames/f_%04d.jpg

Das Inventar ( docs/bildschirme.md ) listet jeden Bildschirm mit Sekundenangabe,
Zweck, Elementen und Aktionen. Es ist die Grundlage für die Abnahmeliste.
```

## Funktionsumfang
```text
Die App hat fünf Hauptbereiche in einer unteren Navigationsleiste: Einträge, Statistiken,
Plus-Schaltfläche in der Mitte, Kalender, Mehr. Die Kopfzeile zeigt den Monat mit Vor und
Zurück, Monatsauswahl und Suche.
```

### Eintrag erfassen
```text
   1. Plus öffnet eine Schnellwahl: Heute, Gestern, Anderer Tag, dazu Neues Ziel und Neuer
       wichtiger Tag.
   2. Schritt 1 fragt Datum, Uhrzeit und Stimmung ab. Es gibt fünf Stufen, je Stufe sind
       mehrere eigene Stimmungen möglich.

   3. Schritt 2 zeigt eine lange, einklappbare Liste aus Gruppen und Skalen in der vom
       Nutzer festgelegten Reihenfolge.
   4. Speichern ist oben und unten erreichbar. Von beiden Schritten führt ein Link direkt in
      die Einrichtung.

Bestandteile von Schritt 2:

       Aktivitäten: Symbole mit Beschriftung, Mehrfachauswahl durch Antippen, je Gruppe
       ein Plus zum direkten Anlegen.
       Skalen: Text- und Zahlenskalen als Schieber mit festen Punkten, Zeitskalen als Wert
       mit Plus und Minus.
       Ziele des Tages: zum Abhaken, mit Hinweis auf die laufende Serie.
       Notiz: schnelle Notiz als Einzeiler, ganze Notiz mit Titel, Formatierung (fett, Listen)
       und Schreibvorlagen.
       Fotos: aufnehmen oder aus der Galerie wählen, mehrere pro Eintrag.
       Sprachnotiz: aufnehmen und am Eintrag speichern.

Weitere Regeln: mehrere Einträge pro Tag sind erlaubt. Einträge nach Mitternacht folgen
der Einstellung (fragen, Vortag, aktueller Tag). Jeder Eintrag lässt sich bearbeiten,
löschen und als Favorit markieren.
```

### Einträge
```text
       Monatsweise Liste, neueste zuerst, mit Schaltfläche „Heute anzeigen“.
       Eintragskarte: Stimmung, Wochentag und Datum, Uhrzeit, Aktivitäten als Chips,
       Skalenwerte als Kacheln, Notiz, Fotoraster, erreichte Ziele.
       Zwischen den Karten: Hinweis „x Tage fehlen“, Karte zum Monatsbericht, Karten für
       wichtige Tage und Jahrestage.
       Oben: Zitat des Tages (abschaltbar), Zeile mit den heutigen Zielen, Rückblick „Vor 1
       Jahr“.
       Suche nach Text, Stimmung und Aktivität.
```

### Statistiken
```text
Alle Auswertungen gelten für den gewählten Monat. Eine Jahresansicht fasst zwölf
Monate zusammen.

       Tage in Folge mit längster Reihe
       Stimmungsverlauf als Linie oder Balken, mit überlagerter Aktivität
       Stimmungszähler: Anzahl je Stimmung als Halbring
       Aktivitätenzähler mit Rangfolge und „Alle anzeigen“
       Verteilung nach Gruppenfarbe als Ring

       Durchschnittliche Stimmung je Wochentag
       Einfluss einer Aktivität auf die Stimmung
       Stimmungsstabilität als Wert von 0 bis 100
       „Oft zusammen“: häufige Aktivitäten je Stimmung
       Längste Serie guter Tage mit den Aktivitäten dieser Tage
       Je Skala ein Diagramm mit Durchschnitt und Veränderung zum Vormonat
       Ziele-Übersicht und Erfolge
       Hinweiszeilen in Klartext, zum Beispiel bester Wochentag und Vergleich zum
       Vormonat
       Leerzustand „zu wenig Daten“ und Teilen einer Auswertung als Bild
```

### Kalender
```text
       Monatsraster mit Wochentagen, Wochenanfang einstellbar.
       Jeder Tag zeigt seine Stimmung farbig. Leere Tage tragen ein Plus zum Nachtragen.
       Filter nach Stimmung oder Aktivität mit Zähler, kombinierbar über „hinzu“.
       Unter dem Raster stehen Hinweiszeile und Stimmungszähler.
```

### Mehr
```text
  Bereich                      Inhalt

  Ziele                        Häufigkeit täglich, x-mal pro Woche oder feste
                               Wochentage. Verknüpft mit einer Aktivität oder
                               frei. Erinnerung mit Uhrzeit und Text, Startdatum,
                               Serie, Erfolgsrate 7 und 30 Tage, Abschlüsse je
                               Monat, Kalender, Stufen, Archiv, eigene
                               Reihenfolge.

  Berichte                     Wöchentlich und monatlich: Einträge, Aktivitäten,
                               Stimmung und Wörter mit Veränderung zur
                               Vorperiode, Verlauf, Ziele, Fotos. Benachrichtigung
                               abschaltbar.

  Rückblick                    Fotos und Einträge vom selben Tag früherer Jahre.

  Wichtige Tage                Name, Datum, Kategorie, Foto, Notiz, jährliche
                               Wiederholung. Anzeige „seit x Tagen“ und
                               „nächster Jahrestag in y Tagen“. Erinnerungen am
                               Tag und davor.

  Favoriten                    Alle markierten Einträge.

  Bereich                      Inhalt

  Fotos                        Galerie aller Fotos nach Monat.

  Erfolge                      Abzeichen für Einträge, Serien, Ziele und Jahre.
                               Eigene Namen und Grafiken.

  Erinnerungen                 Mehrere Uhrzeiten mit eigenem Text.

  Einrichtung                  Stimmungen, Aktivitäten, Gruppen, Skalen,
                               Schreibvorlagen. Siehe nächster Abschnitt.

  Aussehen                     Farbschema, Schriftart, Hell, Dunkel oder nach
                               System.

  Sicherheit                   PIN-Sperre.

  Einstellungen                Zitate, Einträge nach Mitternacht, Rückblick,
                               Wochenanfang.

  Daten                        Sichern und Wiederherstellen, Import, Export als
                               PDF und CSV, Fotos exportieren.

Nicht nachgebaut werden die Premium-Werbeseite, Hinweise auf andere Apps und
Social-Media-Verweise des Vorbilds. An ihre Stelle tritt eine schlichte Seite zum
MediClever-Konto.
```

## Selbst einrichten
```text
Jeder Nutzer baut sich sein Tagebuch selbst zusammen. Nichts ist fest vorgegeben außer
den fünf Stimmungsstufen.

  Baustein                        Was der Nutzer festlegt

  Stimmungen                      Name und Symbol je Stimmung, mehrere
                                  Stimmungen pro Stufe, Reihenfolge, Archiv. Die
                                  fünf Stufen selbst bleiben fest, damit
                                  Auswertungen vergleichbar sind.

  Gruppen                         Name (Emoji erlaubt), Farbe, Reihenfolge,
                                  eingeklappt oder offen, Archiv.

  Aktivitäten                     Name, Symbol aus einer durchsuchbaren Auswahl
                                  mit Kategorien, Farbe, Gruppe. Aktionen:
                                  umbenennen, verschieben, archivieren, ersetzen
                                  (zwei zusammenführen), löschen.

  Baustein                        Was der Nutzer festlegt

  Skalen                          Typ Zahlenskala, Textskala mit benannten Punkten
                                  oder Zeitdauer. Dazu Farbe, Symbol, Diagramm-
                                  Anzeige (Tagesdurchschnitt oder Tagessumme)
                                  und Trendrichtung (höher ist gut oder schlecht).

  Reihenfolge                     Gruppen und Skalen teilen sich eine gemeinsame
                                  Reihenfolge und lassen sich per Ziehen sortieren.

  Schreibvorlagen                 Titel und formatierter Text, Reihenfolge, Standard
                                  wiederherstellen.

  Erinnerungen                    Beliebig viele Uhrzeiten mit eigenem Text.

  Aussehen                        Farbschema für die fünf Stufen, eigenes Schema,
                                  Schriftart, Farbmodus.

Verhalten, das stimmen muss:

       Archivierte Stimmungen, Aktivitäten und Skalen bleiben in alten Einträgen und
       Auswertungen sichtbar. Sie fehlen nur beim Erfassen.
       Löschen fragt nach und nennt die Zahl der betroffenen Einträge. Archivieren wird als
       Alternative angeboten.
       Jede Änderung gilt nur für das eigene Konto.

Erster Start: Ein kurzer Einrichtungsweg bietet Startpakete an, zum Beispiel „Allgemein“
und „Schichtdienst“. Ein Paket ist nur ein Vorschlag und danach frei änderbar. Alternativ
startet der Nutzer leer oder mit einem Import.
```

## Design
```text
Das Vorbild liefert Aufbau, Reihenfolge und Informationsdichte der Bildschirme. Das
Aussehen ist eigene MediClever-Arbeit.

       Nicht übernehmen: Name, Logo, Stimmungsgesichter, Symbolpakete, Illustrationen,
       Abzeichen-Grafiken, Farbschema-Namen und Texte des Vorbilds.
       Stimmungen: fünf eigene Symbole in eigener Formensprache. Jede Stufe ist an
       Symbol und Beschriftung erkennbar, nicht nur an der Farbe.
       Symbole für Aktivitäten: ein frei lizenziertes Icon-Set. Claude Code schlägt eines vor
       und legt die Lizenz in docs/lizenzen.md ab.
       Farben und Schrift: aus der MediClever-CI. Claude Code liest sie aus dem
       bestehenden Theme oder fragt nach einer CI-Datei.
       Modi: Dunkel und Hell, Standard nach Geräteeinstellung.

       Format: mobil zuerst für 360 bis 430 px Breite. Auf dem Desktop zweispaltig nutzbar.
       Sprache: Deutsch, durchgehend Du-Form, kein Denglisch. Also „Serie“ statt „Streak“.
       Ton: ruhig und sachlich. Ein Stimmungstagebuch wird auch an schlechten Tagen
       geöffnet. Cleverle tritt hier höchstens in Leerzuständen auf und ohne Sarkasmus.
       Bedienbarkeit: Tippflächen mindestens 44 px, ausreichender Kontrast,
       Tastaturbedienung, sinnvolle Beschriftungen für Screenreader.

Der Vergleich mit der Referenz prüft deshalb Struktur und Vollständigkeit: Sind dieselben
Elemente da, in sinnvoller Reihenfolge, mit denselben Aktionen? Pixelgleichheit ist kein
Ziel.
```

## Architektur und Technik
```text
Ein WordPress-Plugin enthält alles: Datenhaltung, REST-Schnittstelle, die Web-App und
den MCP-Endpunkt. Es läuft auf einfachem PHP-Webspace ohne eigene Serverprozesse.

  Baustein                    Umsetzung

  Plugin                       mediclever-tagebuch , eigene Datenbanktabellen,
                              keine Abhängigkeit vom Theme. Mindestversionen
                              von PHP und WordPress richten sich nach dem
                              Live-System und werden in Meilenstein 0 geprüft.

  REST                        Namensraum mc-tagebuch/v1 . Anmeldung über
                              das WordPress-Konto mit Nonce. Jede Abfrage ist
                              auf den angemeldeten Nutzer begrenzt.

  Web-App                     TypeScript mit Vite, eingebunden per Shortcode
                              auf einer Lernzentrale-Seite. Das Framework wählt
                              Claude Code und begründet es in
                               docs/entscheidungen.md . Vorgabe: kleines Paket,
                              keine externen CDNs, keine Tracker.

  Diagramme                   Eigene SVG-Komponenten oder eine kleine
                              Bibliothek. Alle Auswertungen rechnet der Server,
                              damit App und MCP dieselben Zahlen liefern.

  Fotos                       Ablage außerhalb des öffentlichen Upload-Pfads.
                              Auslieferung nur über einen angemeldeten
                              Endpunkt. Vorschaubilder in zwei Größen.

  Erinnerungen                Web-Push, wo der Browser es erlaubt. Sonst
                              Hinweis beim Öffnen. Zuverlässige
                              Benachrichtigungen kommen mit der Android-App.

  Baustein                    Umsetzung

  PIN-Sperre                  Sperrt die Oberfläche auf dem Gerät. Sie ersetzt
                              nicht die Anmeldung.

  MCP                         Endpunkt im selben Plugin, greift auf dieselbe
                              Logik zu wie die REST-Schnittstelle.

  Schalter                    Jede Funktion hängt an einem Funktionsschalter,
                              damit die Grenze zwischen kostenlos und Premium
                              später ohne Umbau gesetzt wird.

Lokale Entwicklungsumgebung:

       WordPress lokal über wp-env oder eine gleichwertige Lösung, die Claude Code
       selbst starten kann.
       PHPUnit für Import, Rechte und Auswertungen. Playwright für Abläufe und
       Screenshots.
       Ein einziger Befehl npm run verify führt Lint, Typprüfung, alle Tests und die
       Screenshot-Aufnahme aus.

Ungeklärt und in Meilenstein 0 zu prüfen: Speicherplatz und Upload-Grenzen des
Webspace. Allein der vorhandene Datenbestand bringt rund 207 MB Fotos mit.
```

## Datenmodell
```text
Jede Tabelle trägt eine Nutzer-ID. Kein Datensatz ist ohne sie les- oder schreibbar.
Tabellen-Präfix: mct_ .

  Tabelle                                  Zweck                    Wichtige Felder

   moods                                   Stimmungen               Stufe 1 bis 5, Name, Symbol, Reihenfolge,
                                                                    archiviert

   groups                                  Aktivitätsgruppen        Name, Farbe, Reihenfolge, eingeklappt,
                                                                    archiviert

   tags                                    Aktivitäten              Gruppe, Name, Symbol, Farbe,
                                                                    Reihenfolge, archiviert

   scales                                  Skalen                   Typ (Zahl, Text, Zeit), Name, Farbe,
                                                                    Symbol, Reihenfolge, Anzeige,
                                                                    Trendrichtung, Min und Max

   scale_points                            Punkte einer Textskala   Skala, Position, Bezeichnung

  Tabelle                                  Zweck                    Wichtige Felder

   entries                                 Einträge                 Zeitpunkt in UTC, Zeitzonen-Versatz,
                                                                    lokales Datum, Stimmung, Titel, Notiz
                                                                    (bereinigtes HTML), Favorit, Herkunft
                                                                    (App, Import, MCP)

   entry_tags                              Aktivitäten je Eintrag   Eintrag, Aktivität

   entry_scale_values                      Skalenwerte je Eintrag   Eintrag, Skala, Punkt oder Zahl oder
                                                                    Minuten

   assets                                  Fotos und                Prüfsumme, Typ, Aufnahmezeit, Pfad,
                                           Sprachnotizen            Größe

   entry_assets                            Zuordnung                Eintrag, Datei, Position

   goals                                   Ziele                    Name, Aktivität (optional),
                                                                    Wiederholung, Erinnerung, Startdatum,
                                                                    Enddatum, Notiz, Reihenfolge, archiviert

   goal_checks                             Abgehakte Ziele          Ziel, Datum

   milestones                              Wichtige Tage            Name, Datum, Kategorie, Notiz, Foto,
                                                                    jährlich, Erinnerungen

   templates                               Schreibvorlagen          Titel, Text, Reihenfolge

   reminders                               Erinnerungen             Uhrzeit, Text, aktiv

   import_map                              Zuordnung beim           Importlauf, Art, Quell-ID, neue ID
```

## Import
```text
   mcp_grants                              Freigaben für KI-        Client, Rechte, erteilt am, zuletzt
                                           Zugriff                  genutzt, widerrufen

   audit_log                               Protokoll                Zeitpunkt, Herkunft, Aktion, betroffene
                                                                    ID. Keine Inhalte.

Regeln:

       Archivieren ist ein Zeitstempel, kein Löschen. Echte Löschung entfernt abhängige
       Zeilen und Dateien mit.
       Einstellungen pro Nutzer (Wochenanfang, Farbschema, Mitternachtsregel) liegen als
       Nutzer-Metadaten vor.
       Das lokale Datum eines Eintrags ist gespeichert und indiziert. Monatsansichten
       rechnen nicht bei jeder Abfrage Zeitzonen um.
       Notizen laufen beim Speichern durch eine Positivliste erlaubter HTML-Elemente.
```

## Import
```text
Der Import übernimmt ein .daylio -Backup vollständig: Einrichtung, Einträge, Fotos,
Ziele, wichtige Tage, Vorlagen und Erinnerungen. Die CSV-Datei ist ein zweiter,
einfacherer Weg ohne Fotos und Ziele.
```

### Aufbau der Backup-Datei
```text
Die Datei ist ein ZIP-Archiv. Darin liegen backup.daylio (Base64-kodiertes JSON,
entpackt rund 1,1 MB) und die Fotos unter assets/photos/JAHR/MONAT/PRÜFSUMME ohne
Dateiendung. Die Angaben unten stammen aus dem vorliegenden Backup (Formatversion
15, Android).

  Liste im JSON                        Anzahl           Ziel               Hinweise

   customMoods                         5                    moods          mood_group_id 1 bis 5 ist die Stufe,
                                                                           1 ist die beste.

   tag_groups                          14                   groups         Name, Reihenfolge, Farbnummer,
                                                                           eingeklappt.

   tags                                138                  tags           104 aktiv, 34 archiviert ( state 1).
                                                                           Symbol und Farbe sind Nummern
                                                                           des Vorbilds.

   scales                              10                   scales ,       8 Textskalen mit 4 oder 5
                                                            scale_points   benannten Punkten, 2 Zeitskalen.
                                                                           trend und Diagramm-Anzeige
                                                                           übernehmen.

   dayEntries                          1.765                entries und    375 mit Notiz, 1 mit Titel, 546 mit
                                                        Zuordnungen        Fotos, 20 Favoriten, bis zu 4
                                                                           Einträge pro Tag und 9 Fotos pro
                                                                           Eintrag.

   assets                              1.751                assets         Alle 1.751 Dateien sind im Archiv
                                                                           vorhanden. JPEG, zusammen rund
                                                                           207 MB.

   goals                               15                   goals          5 aktiv, 10 archiviert. 13 sind mit
                                                                           einer Aktivität verknüpft.

   goalEntries                         1.553                goal_checks    Ein Datensatz je abgehaktem Tag.

   milestones                          12                   milestones     Mit Foto, Notiz, Kategorie und
                                                                           Erinnerungen.

  Liste im JSON                        Anzahl           Ziel            Hinweise

   writingTemplates                    11                   templates   Text als HTML.

   reminders                           2                    reminders   Uhrzeit und eigener Text.

   prefs                               40               Nutzer-         Nur übernehmen, was es hier gibt:
                                                        Einstellungen   Wochenanfang, Mitternachtsregel,
                                                                        eigene Farben.

Nicht übernommen werden achievements und goalSuccessWeeks . Erfolge und
Wochenwerte rechnet die App aus den Einträgen neu.
```

### Stolperstellen
```text
       Monat bei Einträgen zählt ab 0. In dayEntries ist Januar die 0. Maßgeblich ist
        datetime (Millisekunden, UTC) zusammen mit timeZoneOffset . Geprüft an allen
       1.765 Einträgen.
       Monat bei wichtigen Tagen zählt ab 1. In milestones ist Januar die 1.
       Monat bei goalEntries ist nicht sicher belegt. Vermutlich ab 1. Claude Code prüft
       das an den Wochenwerten in goalSuccessWeeks , bevor der Import fertig ist.
       Drei Zeitzonen-Versätze kommen vor (+1, +2 und +3 Stunden). Die Ortszeit des
       Eintrags bleibt beim Import erhalten.
       Skalenwerte haben zwei Formen: Verweis auf einen Textpunkt
       ( id_text_scale_value ) oder Zeitwert ( time_value ). Die Einheit des Zeitwerts ist
       an der CSV gegenzuprüfen.
       Notizen enthalten HTML (fett, Zeilenumbruch, Listen, geschützte Leerzeichen).
       Durch die Positivliste schicken.
       Dateinamen im Archiv beginnen mit einem Schrägstrich. Pfade vor dem Entpacken
       normalisieren und gegen Ausbruch aus dem Zielordner sichern.
       Die Prüfsumme ist nur ein Dateiname. Sie ist kein MD5 des Inhalts. Dateityp am Inhalt
       erkennen.
       Symbolnummern lassen sich nicht übernehmen. Der Import wählt ein eigenes
       Symbol über eine Namens-Zuordnung und nimmt sonst ein neutrales. Farbnummern
       werden auf die eigene Palette abgebildet.
       Namen mit Leerzeichen am Ende kommen vor. Für den Abgleich kürzen, den
       sichtbaren Namen bereinigen.
```

### Ablauf
```text
   1. Der Browser öffnet die Datei lokal, entpackt das JSON und zeigt eine Vorschau mit
       den Zählwerten.
   2. Der Nutzer wählt: in ein leeres Tagebuch importieren oder mit dem bestehenden
       zusammenführen.
   3. Einrichtung und Einträge gehen in kleinen Paketen an den Server. Danach folgen die
       Fotos einzeln oder in kleinen Gruppen.
   4. Der Import ist unterbrechbar und fortsetzbar. Ein zweiter Lauf derselben Datei
       erzeugt keine Doppelten ( import_map ).
   5. Am Ende steht ein Bericht: übernommen, übersprungen, Fehler, jeweils mit Anzahl.

Das Entpacken im Browser ist nötig, weil ein Upload von 325 MB an den Grenzen eines
einfachen Webspace scheitert.
```

### CSV-Import
```text
Spalten: full_date , date , weekday , time , mood , activities , scales , note_title ,
 note . Aktivitäten sind mit | getrennt. Skalen stehen als Name: Bezeichnung 2/5 oder
bei Zeit als Name: 6:00 . Fehlende Stimmungen, Aktivitäten und Skalen legt der Import
nach Rückfrage an.
```

### Prüfzahlen für die Abnahme
```text
Nach dem Import des echten Backups in ein leeres Konto müssen diese Werte stimmen:

  Wert                                                         Soll

  Einträge gesamt                                              1.765

  Einträge je Stufe 1 bis 5                                    375 / 680 / 426 / 154 / 130

  Einträge mit Notiz                                           375

  Favoriten                                                    20

  Fotos                                                        1.751 Dateien, 1.743 Zuordnungen zu Einträgen

  Aktivitäten                                                  138, davon 34 archiviert

  Gruppen / Skalen                                             14 / 10

  Ziele / abgehakte Tage                                       15 / 1.553

  Wichtige Tage / Vorlagen / Erinnerungen                      12 / 11 / 2

  Frühester und spätester Eintrag                              27.11.2017 und 16.11.2026

Zusätzlich muss ein CSV-Export aus der neuen App zeilenweise mit private/export.csv
übereinstimmen: Datum, Uhrzeit, Stimmung, Aktivitäten und Skalenwerte.
```

## MCP-Schnittstelle
```text
Das Plugin stellt einen MCP-Endpunkt bereit, über den ein KI-Assistent im Namen eines
Nutzers Einträge anlegt und liest. Jeder Nutzer verbindet nur sein eigenes Tagebuch.
```

### Tools
```text
  Tool                                           Zweck                                                Art

   einrichtung_lesen                             Stimmungen, Gruppen, Aktivitäten, Skalen und         lesen
                                                 Ziele des Nutzers mit ihren Namen

   eintrag_anlegen                               Eintrag mit Datum, Uhrzeit, Stimmung, Aktivitäten,   schreiben
                                                 Skalenwerten, Titel und Notiz

   eintrag_aendern                               Einzelne Felder eines Eintrags ändern                schreiben

   eintrag_loeschen                              Eintrag löschen. Nur mit eigener Freigabe,           löschen
                                                 standardmäßig gesperrt.

   tag_lesen                                     Alle Einträge eines Datums                           lesen

  Tool                                           Zweck                                          Art

   eintraege_suchen                              Suche nach Zeitraum, Text, Stimmung und        lesen
                                                 Aktivität, seitenweise

   auswertung_lesen                              Kennzahlen für einen Zeitraum: Durchschnitt,   lesen
                                                 Zähler, Serien

   ziel_abhaken                                  Ziel für ein Datum abhaken oder zurücknehmen   schreiben

   wichtige_tage_lesen                           Wichtige Tage mit nächstem Jahrestag           lesen

   wichtigen_tag_anlegen                         Neuen wichtigen Tag anlegen                    schreiben

Regeln für die Tools:

       Namen werden unscharf zugeordnet. Bei unbekannter Stimmung oder Aktivität
       kommt eine Liste möglicher Treffer zurück. Stillschweigend angelegt wird nichts.
        eintrag_anlegen nimmt einen Wiederholungsschlüssel an, damit ein erneuter Aufruf
       keinen zweiten Eintrag erzeugt.
       Fehlt das Datum, gilt heute in der Zeitzone des Nutzers. Die Mitternachtsregel greift
       auch hier.
       Jedes Tool trägt Titel und die Kennzeichen für „nur lesen“ und „löschend“ (Vorgabe
       von Anthropic).
       Fotos und Sprachnotizen sind in Version 1 über MCP weder lesbar noch schreibbar.
       Antworten sind knapp und seitenweise. Kein Tool gibt das ganze Tagebuch auf einmal
       aus.
```

### Transport und Anmeldung
```text
       Transport: Streamable HTTP ohne Sitzungszustand, erreichbar über HTTPS auf Port
       443 (Anforderungen für eigene Konnektoren).
       Anmeldung: OAuth mit Autorisierungscode und PKCE, dynamische Client-
       Registrierung, Token mit Ablauf und Erneuerung. Als Rückruf-Adressen sind
        https://claude.ai/api/mcp/auth_callback und
        https://claude.com/api/mcp/auth_callback zuzulassen (Support-Artikel).
       Rechte in drei Stufen: lesen, schreiben, löschen. Der Nutzer wählt sie auf der
       Freigabeseite.
       In den Einstellungen sieht der Nutzer verbundene Clients mit letzter Nutzung und
       kann jede Freigabe widerrufen.
       Jeder Aufruf landet im Protokoll, ohne Inhalte. Dazu kommt eine Begrenzung der
       Aufrufe pro Minute.
```

### Vorab zu klären in Meilenstein 0
```text
WordPress liefert ab Version 6.9 eine Abilities-Schnittstelle. Der offizielle MCP Adapter
macht daraus MCP-Tools. Seine Beispiele verbinden sich über Anwendungspasswörter
oder ein lokales Brückenprogramm. Claude Code baut einen kleinen Versuch und
entscheidet dann zwischen zwei Wegen:

   1. MCP Adapter plus eigene OAuth-Schicht im Plugin.
   2. Eigener, schlanker MCP-Endpunkt im Plugin mit eigener OAuth-Schicht.

Entscheidungskriterium: Der Endpunkt lässt sich als eigener Konnektor in Claude
hinzufügen und in Claude Code mit claude mcp add --transport http verbinden, ohne
Zusatzprogramm auf dem Gerät des Nutzers. Getestet wird mit dem MCP Inspector.
```

## Datenschutz und Sicherheit
```text
Stimmung, Schlaf, Schmerzen, Krankheitstage und Beziehungsleben sind
Gesundheitsdaten und weitere besonders geschützte Angaben. Das Tool wird deshalb
strenger gebaut als die übrigen Lernzentrale-Tools.

Technische Vorgaben für Claude Code:

       Strikte Trennung nach Nutzer in jeder Abfrage, abgesichert durch Tests, die fremde
       IDs gezielt anfragen.
       Ausdrückliche Einwilligung vor dem ersten Eintrag, getrennt davon eine zweite für
       den KI-Zugriff. Beide sind jederzeit widerrufbar.
       Der Text zur KI-Freigabe sagt klar: Gelesene Einträge gehen an den Anbieter des
       verbundenen Assistenten.
       Vollständiger Export und vollständige Löschung des Kontos samt Fotos als
       Selbstbedienung.
       Fotos sind nie über eine öffentliche Adresse abrufbar. Aufnahmeort und Gerätedaten
       werden aus den Bilddateien entfernt.
       Keine Tagebuchinhalte in Logs, Fehlermeldungen, Statistik oder E-Mails. Die
       Nutzungsstatistik zählt nur Aufrufe.
       Hinweis in der App: keine Patienten- und Einsatzdaten eintragen.
       Schutz gegen Cross-Site-Scripting in Notizen, gegen gefälschte Anfragen und gegen
       Durchprobieren von Token.

Offene Punkte, die Claude Code nicht lösen kann:

       Hosting. Wer das Hosting-Konto besitzt, kann die Datenbank lesen. Verschlüsselung
       auf dem Server hilft dagegen nicht, weil der Schlüssel dort liegt. Vor dem Livegang
       braucht es ein eigenes Hosting-Konto oder einen Vertrag mit dem Kontoinhaber.

       Rechtliche Prüfung. Einwilligungstexte, Auftragsverarbeitung, Datenschutz-
       Folgenabschätzung und die Frage der Nutzung unter 18 gehören vor dem Start für
       andere Nutzer zu einer Fachperson.
       Das eigene Backup. Es enthält Namen und Angaben Dritter. Der Import auf den Live-
       Server erfolgt erst, wenn die Hosting-Frage geklärt ist. Bis dahin bleibt er lokal.
```

## Arbeitsweise im Feedback-Loop
```text
Claude Code prüft jede Änderung selbst, bevor ein Punkt als erledigt gilt: mit Tests, mit
Screenshots und mit einer unabhängigen Gegenprüfung. Das ist laut den Empfehlungen
für Claude Code der wirksamste Hebel für gute Ergebnisse.
```

### Dateien, die den Loop tragen
```text
  Datei                                                     Inhalt

   CLAUDE.md                                                Kurz: Befehle, feste Regeln, Verweis auf die
                                                            Dateien unten. Keine langen Texte.

   docs/projektbeschreibung.md                              Dieses Dokument als Markdown-Export.

   docs/bildschirme.md                                      Inventar aller Bildschirme aus dem Video mit
                                                            Sekundenangabe.

   docs/abnahme.md                                          Prüfpunkte je Bildschirm und Funktion mit Status:
                                                            offen, erfüllt, bewusst abweichend.

   docs/fortschritt.md                                      Aktueller Stand, nächster Schritt, offene Fragen an
                                                            Lukas. Damit setzt eine neue Sitzung nahtlos fort.

   docs/entscheidungen.md                                   Technische Entscheidungen mit Begründung.
```

### Eine Schleife
```text
   1. Nächsten offenen Punkt aus docs/abnahme.md wählen und die Referenzbilder dazu
       ansehen.
   2. Prüfpunkte schärfen: Elemente, Aktionen und die Zustände leer, gefüllt, Fehler.
   3. Umsetzen in kleinen Commits.
   4. npm run verify ausführen.
   5. Playwright nimmt Screenshots mit künstlichen Beispieldaten auf: 412 × 915 px und
       1280 × 800 px, jeweils Hell und Dunkel.

   6. Screenshot neben das Referenzbild legen und eine Abweichungsliste schreiben:
       fehlendes Element, falsche Reihenfolge, Überlauf, abgeschnittener Text, zu kleine
       Tippfläche, schwacher Kontrast.
   7. Nachbessern und zurück zu Schritt 4, bis die Liste leer ist.
   8. Gegenprüfung durch einen frischen Subagenten. Er kennt die Umsetzung nicht und
       erhält nur Referenzbild, Screenshot und Prüfpunkte.
   9. Erst nach dessen „erfüllt“ den Punkt abhaken, docs/fortschritt.md aktualisieren
       und committen.
```

### Feste Regeln
```text
       Höchstens fünf Durchläufe je Punkt. Danach Stand festhalten und Lukas fragen.
       Kein Punkt gilt als erfüllt, solange ein Test fehlschlägt. Tests werden nie
       abgeschwächt oder gelöscht, um grün zu werden.
       Jede neue Funktion bekommt zuerst einen Test, der fehlschlägt.
       Anhalten und fragen bei: Änderungen am Datenmodell nach Meilenstein 2, neuen
       Abhängigkeiten mit unklarer Lizenz, allem, was den Live-Server berührt, und
       Konflikten zwischen Referenz und eigenem Design.
       Private Daten bleiben in private/ . Screenshots entstehen nur mit Beispieldaten.
```

### Was „fertig“ heißt
```text
       Alle Punkte in docs/abnahme.md sind erfüllt oder mit Zustimmung von Lukas als
       bewusst abweichend markiert.
        npm run verify läuft fehlerfrei, die Browser-Konsole bleibt ohne Fehler.
       Der Import erfüllt alle Prüfzahlen.
       Ein Eintrag lässt sich über MCP anlegen und erscheint danach in der App.
       Eine Monatsansicht mit dem importierten Bestand antwortet lokal in unter 300 ms.
       Die Prüfung auf Barrierefreiheit mit axe meldet keine schweren Fehler.
```

### Was der Loop nicht leistet
```text
Ob sich die App gut anfühlt, entscheidet kein Screenshot-Vergleich. Nach jedem
Meilenstein testet Lukas auf dem eigenen Handy. Seine Rückmeldung wandert als neue
Prüfpunkte in docs/abnahme.md .
```

## Meilensteine
```text
Acht Meilensteine, jeder endet mit lauffähigem Stand und einer Abnahme durch Lukas.
Der Import kommt früh, damit alle späteren Bildschirme mit einem großen Datenbestand
geprüft werden.

  Nr.         Inhalt                                        Abnahme

  0           Repository, CLAUDE.md , lokales               Inventar deckt alle Bildschirme des
              WordPress, npm run verify ,                   Videos ab. MCP-Weg ist entschieden
              Bildschirm-Inventar, Abnahmeliste,            und begründet.
              Versuch zum MCP-Weg, Prüfung von
              PHP- und WordPress-Version sowie
              Speichergrenzen des Live-Systems

  1           Datenmodell, REST, Einrichtung von            Ein neues Konto ist in unter 3 Minuten
              Stimmungen, Gruppen, Aktivitäten und          eingerichtet. Rechte-Tests bestehen.
              Skalen, erster Start mit Startpaketen

  2           Eintrag erfassen, Einträge-Liste,             Ein vollständiger Eintrag mit Notiz und
              Bearbeiten, Favorit, Suche, Fotos             Foto ist in zwei Schritten gespeichert.

  3           Import von Backup und CSV,                    Alle Prüfzahlen stimmen. Zweiter Lauf
              Importbericht                                 erzeugt keine Doppelten.

  4           Kalender und Statistiken                      Jede Auswertung ist gegen eine von
                                                            Hand nachgerechnete Stichprobe
                                                            getestet.

  5           Ziele, wichtige Tage, Berichte, Rückblick,    Importierte Ziele zeigen Serien und
              Favoriten, Fotogalerie                        Abschlüsse wie im Video.

  6           MCP-Endpunkt, OAuth, Freigaben,               Eintrag per Claude angelegt und in der
              Protokoll                                     App sichtbar. Widerruf sperrt den
                                                            Zugriff sofort.

  7           Erinnerungen, PIN, Aussehen, Export,          Alle Punkte der Abnahmeliste erfüllt
              Sichern, Erfolge, Sprachnotiz,                oder bewusst abweichend.
              Einwilligungen, Konto löschen,
              Feinschliff
```

## Startprompt für Claude Code
```text
Vorbereitung: leeren Projektordner anlegen, dieses Dokument als Markdown nach
 docs/projektbeschreibung.md exportieren, Video und Screenshots nach reference/ ,
Backup und CSV nach private/ legen. Dann Claude Code im Ordner starten und diesen

Text einfügen:

  Lies docs/projektbeschreibung.md vollständig, bevor du etwas anlegst.

  Deine Aufgabe ist Meilenstein 0. Schreibe noch keinen Code für die App.

  1. Lege .gitignore an. reference/ und private/ sind ausgeschlossen. Prüfe das
  mit git status.
  2. Ziehe Einzelbilder aus reference/video.mp4 (1 Bild pro Sekunde) und sieh
  sie dir an.
  3. Schreibe docs/bildschirme.md: jeder Bildschirm mit Sekundenangabe, Zweck,
  Elementen, Aktionen.
  4. Leite daraus docs/abnahme.md ab: prüfbare Punkte je Bildschirm und
  Funktion, alle mit Status offen.
  5. Richte ein lokales WordPress ein, das du selbst starten und zurücksetzen
  kannst, dazu das leere Plugin mediclever-tagebuch.
  6. Richte npm run verify ein: Lint, Typprüfung, PHPUnit, Playwright,
  Screenshot-Aufnahme.
  7. Untersuche private/backup.daylio mit einem Skript. Gib nur Struktur und
  Zählwerte aus, keine Inhalte. Gleiche sie mit dem Abschnitt Import ab und
  melde Abweichungen.
  8. Erzeuge tests/fixtures/sample.daylio mit erfundenen Daten im selben
  Format.
  9. Baue den Versuch zum MCP-Weg und halte die Entscheidung in
  docs/entscheidungen.md fest.
  10. Schreibe CLAUDE.md kurz und docs/fortschritt.md mit Stand und nächstem
  Schritt.

  Halte dich an den Abschnitt "Arbeitsweise im Feedback-Loop". Das Aussehen ist
  eigenes MediClever-Design: übernimm aus dem Video Aufbau und Abläufe, aber
  keine Grafiken, Symbole, Namen oder Texte.

  Wenn eine Annahme aus dem Abschnitt "Annahmen und offene Entscheidungen"
  deine Arbeit blockiert, halte an und frage. Am Ende von Meilenstein 0 gibst
  du mir eine Zusammenfassung und wartest auf meine Freigabe für Meilenstein 1.

Für jeden weiteren Meilenstein genügt: „Lies docs/fortschritt.md und arbeite
Meilenstein N nach dem Feedback-Loop ab.“
```
