# Bildschirm-Inventar

**Wichtig:** Das Referenzvideo und die vier Screenshots lagen in dieser Sitzung nicht vor (`reference/` ist leer). Dieses Inventar ist deshalb aus dem Abschnitt „Funktionsumfang“ der Projektbeschreibung abgeleitet, nicht aus dem Video. Die Spalte „Sekunde im Video“ ist offen und wird nachgetragen, sobald das Video da ist:

```
ffmpeg -i reference/video.mp4 -vf fps=1 reference/frames/f_%04d.jpg
```

Spalte „Route“ nennt die Adresse in der Web-App, „Screenshot“ die Datei unter `screenshots/<variante>/` (erzeugt von `npm run screenshots`, nur Beispieldaten).

| Nr. | Bildschirm | Sekunde im Video | Zweck | Elemente | Aktionen | Route | Screenshot |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Einträge | offen | Monatsliste, neueste zuerst | Monatsleiste mit Vor/Zurück, Monatsauswahl, Suche; Satz des Tages; Zeile mit heutigen Zielen; Rückblick „Vor 1 Jahr“; Eintragskarten; Hinweis „x Tage fehlen“; Karte zum Monatsbericht; Karten für wichtige Tage | Monat wechseln, „Heute anzeigen“, Ziel abhaken, Eintrag öffnen, Favorit, Nachtragen | `/eintraege` | `eintraege.png` |
| 2 | Monatsauswahl | offen | Monat direkt wählen | Jahr vor/zurück, zwölf Monate mit Anzahl Einträge | Monat wählen | Dialog | `monatsauswahl.png` |
| 3 | Plus-Schnellwahl | offen | Schnell anlegen | Heute, Gestern, Anderer Tag, Neues Ziel, Neuer wichtiger Tag | je ein Tipp | Dialog | `plus-schnellwahl.png` |
| 4 | Eintrag, Schritt 1 | offen | Datum, Uhrzeit, Stimmung | Datum, Uhrzeit, fünf Stufen mit je mehreren Stimmungen, Link „Stimmungen bearbeiten“ | Stimmung wählen, weiter | `/eintrag/neu` | `eintrag-schritt-1.png` |
| 5 | Eintrag, Schritt 2 | offen | Was war los | einklappbare Gruppen mit Aktivitäten, Plus je Gruppe, Skalen (Punkte, Schieber, Plus/Minus), Ziele des Tages mit Serie, schnelle Notiz, ganze Notiz, Fotos, Sprachnotiz, Speichern oben und unten, Link „Einrichtung bearbeiten“ | Mehrfachauswahl, Wert setzen, Ziel abhaken, Foto aufnehmen oder wählen, aufnehmen, speichern | `/eintrag/neu`, `/eintrag/<id>` | `eintrag-schritt-2.png` |
| 6 | Ganze Notiz | offen | Titel und formatierter Text | Titel, Text mit fett und Listen, Schreibvorlagen | schreiben, Vorlage einfügen | Dialog | – |
| 7 | Suche | offen | Einträge finden | Textfeld, Stimmungen, Aktivitäten, Trefferzahl, Karten, Seiten | filtern, blättern | `/suche` | `suche.png` |
| 8 | Statistiken | offen | Auswertungen für Monat oder Jahr | Tage in Folge, Hinweise, Stimmungsverlauf (Linie/Balken, Aktivität überlagert), Stimmungszähler (Halbring), Aktivitätenzähler mit „Alle anzeigen“, Verteilung nach Gruppen (Ring), Wochentage, Einfluss, Stabilität, Oft zusammen, längste Serie guter Tage, je Skala ein Diagramm, Ziele, Leerzustand | Zeitraum wechseln, Aktivität einblenden, als Bild teilen | `/statistik` | `statistik.png` |
| 9 | Kalender | offen | Monat auf einen Blick | Monatsraster, Stimmung je Tag, Plus an leeren Tagen, Filter mit Zähler, „hinzu“, Hinweiszeile, Stimmungszähler | Tag öffnen, nachtragen, filtern | `/kalender` | `kalender.png` |
| 10 | Tag | offen | Einträge eines Tages | Karten, Plus | weiterer Eintrag | `/tag/<datum>` | `tag.png` |
| 11 | Mehr | offen | Übersicht aller weiteren Bereiche | drei Blöcke: Tagebuch, Einrichten, Daten und Konto | öffnen | `/mehr` | `mehr.png` |
| 12 | Ziele | offen | Ziele verwalten | Liste mit Haken, Häufigkeit, Serie, Erfolgsrate, Archiv, Sortiergriff | abhaken, sortieren, anlegen | `/mehr/ziele` | `ziele.png` |
| 13 | Ziel-Detail | offen | Kennzahlen eines Ziels | Serie, Erfolgsrate 7 und 30 Tage, gesamt, Stufe, Monatskalender, Abschlüsse je Monat | Tag abhaken, bearbeiten, archivieren, löschen | Dialog | `ziel-detail.png` |
| 14 | Berichte | offen | Woche oder Monat | Einträge, Aktivitäten, Stimmung, Wörter mit Veränderung, Verlauf, häufigste Aktivitäten, Ziele, Fotos, Benachrichtigung | Zeitraum wechseln | `/mehr/berichte` | `berichte.png` |
| 15 | Rückblick | offen | derselbe Tag früherer Jahre | Datum vor/zurück, Karten je Jahr | blättern | `/mehr/rueckblick` | `rueckblick.png` |
| 16 | Wichtige Tage | offen | Jahrestage | Name, Datum, Kategorie, Foto, Notiz, „seit x Tagen“, „nächster Jahrestag in y Tagen“ | anlegen, bearbeiten, löschen | `/mehr/wichtige-tage` | `wichtige-tage.png` |
| 17 | Favoriten | offen | markierte Einträge | Karten, Seiten | öffnen | `/mehr/favoriten` | `favoriten.png` |
| 18 | Fotos | offen | Galerie nach Monat | Raster je Monat, Großansicht | vergrößern, blättern | `/mehr/fotos` | `fotos.png` |
| 19 | Erfolge | offen | Abzeichen | Gruppen: Einträge, Tage in Folge, Ziele, Jahre, Fotos | ansehen | `/mehr/erfolge` | `erfolge.png` |
| 20 | Erinnerungen | offen | Uhrzeiten mit Text | Liste mit Schalter, Benachrichtigungen einschalten | anlegen, schalten, löschen | `/mehr/erinnerungen` | `erinnerungen.png` |
| 21 | Einrichtung | offen | Einstieg | Stimmungen, Aktivitäten und Gruppen, Skalen, Reihenfolge, Schreibvorlagen | öffnen | `/mehr/einrichtung` | `einrichtung.png` |
| 22 | Stimmungen | offen | Stimmungen je Stufe | fünf Stufen, Sortiergriff, Archiv | anlegen, Symbol wählen, sortieren, archivieren, löschen mit Ersatz | `/mehr/stimmungen` | `stimmungen.png` |
| 23 | Aktivitäten und Gruppen | offen | Aktivitäten verwalten | Gruppen mit Aktivitäten, Archiv | anlegen, umbenennen, verschieben, archivieren, zusammenführen, löschen | `/mehr/aktivitaeten` | `aktivitaeten.png`, `aktivitaet-bearbeiten.png` |
| 24 | Skalen | offen | Skalen verwalten | Typ, Punkte, Farbe, Symbol, Anzeige, Trendrichtung | anlegen, bearbeiten, archivieren, löschen | `/mehr/skalen` | `skalen.png`, `skala-bearbeiten.png` |
| 25 | Reihenfolge | offen | Gruppen und Skalen gemeinsam sortieren | Liste mit Griff | ziehen, Pfeiltasten | `/mehr/reihenfolge` | `reihenfolge.png` |
| 26 | Schreibvorlagen | offen | Vorlagen | Liste, Standard wiederherstellen | anlegen, bearbeiten, sortieren | `/mehr/vorlagen` | `vorlagen.png` |
| 27 | Aussehen | offen | Farbmodus, Schema, Schrift | Hell/Dunkel/Gerät, vier Schemata und eigenes, drei Schriften | wählen | `/mehr/aussehen` | `aussehen.png` |
| 28 | Sicherheit | offen | PIN-Sperre | PIN setzen, ausschalten | sperren, entsperren | `/mehr/sicherheit` | `sicherheit.png` |
| 29 | Einstellungen | offen | Wochenanfang, Mitternacht, Zitat, Rückblick | Schalter und Auswahl | ändern | `/mehr/einstellungen` | `einstellungen.png` |
| 30 | Daten | offen | Import, Export, Sicherung | Datei wählen, Vorschau, Fortschritt, Bericht; fünf Exporte | importieren, herunterladen | `/mehr/daten` | `daten.png` |
| 31 | KI-Zugriff | offen | Freigaben | Einwilligung, Adresse des Konnektors, verbundene Clients mit letzter Nutzung | einwilligen, widerrufen | `/mehr/ki` | `ki-zugriff.png` |
| 32 | MediClever-Konto | offen | Konto statt Premium-Werbeseite | Einwilligung, Abmelden, Tagebuch leeren, alles löschen | widerrufen, löschen | `/mehr/konto` | `konto.png` |
| 33 | Erster Start | offen | Einwilligung und Startpaket | zwei Schritte, Pakete „Allgemein“, „Schichtdienst“, „Leer“, Import | einwilligen, Paket wählen | automatisch | – |
| 34 | Freigabeseite für Assistenten | – (nicht im Vorbild) | Rechte wählen, KI-Einwilligung | Lesen, Schreiben, Löschen, Einwilligungstext | freigeben, ablehnen | `/?mct_oauth=authorize` | – |

Nicht nachgebaut, wie vorgegeben: Premium-Werbeseite, Hinweise auf andere Apps, Social-Media-Verweise.

Erst mit der Android-App möglich (Annahme 9): Homescreen-Widgets, Haptik, verlässlich pünktliche Benachrichtigungen.
