# Technische Entscheidungen

Jede Entscheidung mit Begründung. Neueste unten.

## 1. Ablage im Repository `drexlerfotografie/claude`

Das Projekt liegt im Unterordner `mediclever-tagebuch/` des Marktplatz-Repositorys, weil die Sitzung nur auf dieses Repository zugreifen darf. Der Ordner ist in sich geschlossen und lässt sich später ohne Umbau in ein eigenes Repository verschieben (`git subtree split`).

## 2. Lokales WordPress ohne Docker

`wp-env` braucht Docker. In der Cloud-Umgebung läuft kein Docker-Dienst, und wordpress.org ist gesperrt. Deshalb: WordPress-Kern über Composer (`johnpbloch/wordpress-core`), MariaDB aus den Paketquellen, eingebauter PHP-Server. `scripts/wp.sh setup|start|stop|reset` startet und setzt alles zurück. Vorteil gegenüber SQLite: dieselbe Datenbankfamilie wie auf üblichem Webspace. Für Rechner mit Docker bleibt `wp-env` möglich, ist hier aber nicht getestet.

Geprüft mit WordPress 7.1.4, PHP 8.3.6, MariaDB 10.11. Das Plugin verlangt mindestens WordPress 6.5 und PHP 8.1 (wegen `wp_enqueue_script_module`, `array_is_list`, `never`-freiem Code). Die Versionen des Live-Systems sind **nicht** geprüft, siehe `fortschritt.md`.

## 3. Framework der Web-App: Preact

Preact statt React, Vue oder Svelte: 4 kB Laufzeit, JSX mit vollständiger TypeScript-Prüfung, keine Build-Magie. Zustand über einen kleinen eigenen Speicher, Routing über den Hash (funktioniert auf jeder WordPress-Seite ohne Rewrite-Regeln). Paketgröße der App: rund 64 kB (gzip) Skript, 7 kB Stile, Schriften lokal. Keine externen CDNs, keine Tracker.

## 4. Diagramme: eigene SVG-Komponenten

Linie, Balken, Ring und Halbring sind eigene Komponenten (`app/src/charts.tsx`, rund 150 Zeilen). Eine Bibliothek wäre größer als die ganze übrige App. Alle Zahlen kommen vom Server (`includes/stats.php`), damit App und MCP dieselben Werte liefern. Farben werden als feste Werte gesetzt, damit sich eine Auswertung als Bild teilen lässt.

## 5. MCP-Weg: eigener schlanker Endpunkt im Plugin

Zur Wahl standen (1) MCP Adapter plus eigene OAuth-Schicht und (2) ein eigener Endpunkt mit eigener OAuth-Schicht. Entschieden: **Weg 2.**

Versuch: Der Adapter (`wordpress/mcp-adapter` 0.7.1) wurde über Composer installiert und gelesen. Ergebnis:

- Er verlangt WordPress 6.9 (Abilities-Schnittstelle).
- Er bringt keine OAuth-Anmeldung mit. Seine eigene Anleitung für Seitenbetreiber sagt sinngemäß: Die Web-Versionen von Claude und ChatGPT verbinden Konnektoren über eine OAuth-Anmeldung und haben kein Feld für ein Anwendungspasswort; WordPress enthält keinen OAuth-Server und der Adapter ergänzt keinen, deshalb können sich diese Apps heute nicht von sich aus mit dem Adapter verbinden. Die OAuth-Schicht wäre also in beiden Wegen Eigenbau.
- Ein Anwendungspasswort gäbe einem Assistenten Zugriff auf das ganze WordPress-Konto, nicht nur auf das Tagebuch. Das widerspricht den drei Rechtestufen und dem Widerruf je Client.
- Der Adapter führt Sitzungen in den Nutzer-Metadaten (`SessionManager`). Gefordert ist ein Transport ohne Sitzungszustand.
- Der zustandslose Streamable-HTTP-Transport ist ein einzelner POST-Endpunkt mit JSON-RPC. Der eigene Endpunkt umfasst rund 300 Zeilen (`includes/mcp.php`), ohne Abhängigkeit und ohne Mindestversion 6.9.

Nicht verglichen wurde das Laufzeitverhalten beider Wege nebeneinander; der Adapter lief nicht in der lokalen Umgebung. Wenn Lukas den Adapter später bevorzugt, lassen sich die zehn Tools als Abilities registrieren; die Logik in `Mcp::call()` bleibt dieselbe, die OAuth-Schicht ebenso.

Entscheidungskriterium aus der Projektbeschreibung: als eigener Konnektor hinzufügbar und per `claude mcp add --transport http` verbindbar, ohne Zusatzprogramm. Erfüllt sind die technischen Voraussetzungen: `.well-known`-Metadaten, dynamische Client-Registrierung, Autorisierungscode mit PKCE, Token mit Ablauf und Erneuerung, Rückruf-Adressen von claude.ai und claude.com sowie lokale Adressen für Kommandozeilen-Clients. Getestet mit dem offiziellen MCP-SDK (`tests/e2e/04-mcp.spec.ts`). **Nicht getestet:** die Verbindung aus Claude selbst und der MCP Inspector, weil der lokale Server von außen nicht erreichbar ist und kein HTTPS hat.

## 6. OAuth im Plugin

- Token sind zufällige 256-Bit-Werte und liegen nur als SHA-256-Hash in der Datenbank. Zugriffstoken gelten 1 Stunde, Erneuerungstoken 60 Tage und wechseln bei jeder Nutzung.
- Ein MCP-Token gilt ausschließlich am MCP-Endpunkt. Die REST-Schnittstelle der App nimmt nur die Cookie-Anmeldung mit Nonce an.
- Die Tabelle `mcp_grants` hält Client, Rechte, Erteilung, letzte Nutzung und Widerruf. Zusätzlich gibt es `oauth_clients` und `oauth_tokens`; beide standen nicht im Datenmodell der Projektbeschreibung, sind für OAuth aber nötig.
- Begrenzung: 60 MCP-Aufrufe pro Minute und Freigabe, dazu Grenzen für Registrierung, Token-Endpunkt und Fehlversuche je IP-Adresse.

## 7. Fotos

Ablage in `wp-content/mct-data/<Nutzer>/…` mit zufälligen Dateinamen, gesperrt per `.htaccess`, verschiebbar über die Konstante `MCT_STORAGE_DIR` (empfohlen: ein Ordner außerhalb des Dokumentenstamms). Auslieferung nur über den angemeldeten Endpunkt. Jedes Bild wird mit GD neu kodiert; dabei fallen Aufnahmeort und Gerätedaten weg. Vorschaubilder in 360 px und 1280 px. Achtung: `.htaccess` wirkt nur unter Apache. Unter nginx muss der Ordner außerhalb des Dokumentenstamms liegen oder per Serverregel gesperrt werden.

## 8. Import im Browser, Zuordnung auf dem Server

Der Browser liest das ZIP-Archiv abschnittsweise (eigener Leser in `app/src/zip.ts`), entpackt das JSON und schickt Pakete. Die Zuordnung der Felder passiert auf dem Server (`includes/import.php`), damit sie mit PHPUnit geprüft ist und alle Stolperstellen an einer Stelle stehen. Pfade aus dem Archiv werden normalisiert; Dateien werden nie unter ihrem Archivnamen gespeichert, ein Ausbruch aus dem Zielordner ist dadurch ausgeschlossen.

Weil das echte Backup hier nicht vorliegt, liest der Import Feldnamen tolerant (mehrere Schreibweisen je Feld) und meldet Unbekanntes als Hinweis im Bericht. Betroffen sind vor allem Skalen, Skalenwerte je Eintrag, Wiederholung der Ziele und die Felder der wichtigen Tage. `scripts/backup-struktur.mjs` zeigt die tatsächliche Struktur ohne Inhalte.

## 9. Eigenes Sicherungsformat

„Sichern und Wiederherstellen“ nutzt eine ZIP-Datei (`.mctb`) mit `backup.json` und den Fotos. Das JSON verwendet dieselben Feldnamen, die der Import versteht. Dadurch ist die Wiederherstellung derselbe geprüfte Weg wie der Import, inklusive Fortsetzen und Schutz vor Doppelten.

## 10. PDF-Export ohne Bibliothek

Der PDF-Export ist ein schlichtes Text-PDF mit Standardschrift, erzeugt in rund 60 Zeilen PHP. Eine PDF-Bibliothek wäre die größte Abhängigkeit des Plugins. Fotos stehen nicht im PDF; dafür gibt es den Foto-Export.

## 11. Erinnerungen

Web-Push nach RFC 8291 und 8292 mit OpenSSL, ohne Bibliothek. Ver- und Entschlüsselung sind per Test belegt, die Zustellung über echte Push-Dienste ist hier nicht prüfbar. Ausgelöst wird über WP-Cron, das von Seitenaufrufen abhängt. Für pünktliche Erinnerungen braucht das Live-System einen echten Cron-Aufruf von `wp-cron.php` im 5-Minuten-Takt. Beim Öffnen der App erscheint zusätzlich ein Hinweis, wenn eine Erinnerung fällig war und heute noch kein Eintrag existiert.

## 12. PIN-Sperre

Die PIN wird nur im Browser gespeichert (gesalzener SHA-256-Hash im lokalen Speicher) und sperrt die Oberfläche auf diesem Gerät. Sie ist kein Schutz gegen jemanden mit Zugriff auf das Konto oder die Entwicklerwerkzeuge des Browsers und ersetzt nicht die Anmeldung.

## 13. Löschen von Stimmungen

Abweichend von „echte Löschung entfernt abhängige Zeilen“ löscht das Entfernen einer Stimmung keine Einträge. Der Dialog nennt die Zahl der betroffenen Einträge; sie bekommen eine Ersatz-Stimmung derselben Stufe. Sonst würde ein Tippfehler in der Einrichtung Tagebucheinträge vernichten.

## 14. „Konto löschen“

Gelöscht werden alle Tagebuchdaten, Fotos, Freigaben, Protokoll, Einstellungen und Einwilligungen. Das MediClever-Konto (WordPress-Nutzer) bleibt, weil daran die übrigen Lernzentrale-Tools hängen. Ob auch der WordPress-Nutzer gelöscht werden soll, ist eine offene Frage an Lukas.

## 15. Auswertungen: Rechenregeln

- Stimmungswert: 5 = beste Stufe, 1 = schlechteste (6 minus Stufe). Tageswert: Durchschnitt der Einträge des Tages.
- Stabilität: 100 × (1 − mittlere Schwankung zwischen aufeinanderfolgenden Tagen mit Eintrag ÷ 4).
- Einfluss einer Aktivität: Durchschnitt der Einträge mit ihr minus Durchschnitt der Einträge ohne sie; mindestens zwei Einträge.
- Guter Tag: Tagesdurchschnitt ab 4. Serien zählen nur lückenlose Kalendertage.
- Zu wenig Daten: weniger als drei Einträge im Zeitraum.
- Ziel-Serie: Der noch offene heutige Tag (bzw. die laufende Woche) bricht die Serie nicht.

Jede Regel ist in `tests/php/StatsGoalsTest.php` gegen eine von Hand nachgerechnete Stichprobe geprüft.
