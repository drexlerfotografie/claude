# MediClever Tagebuch

Stimmungs- und Aktivitäten-Tagebuch als WordPress-Plugin mit Web-App, REST-Schnittstelle und MCP-Endpunkt.

## Zuerst lesen

- `docs/fortschritt.md` – Stand, nächster Schritt, offene Fragen an Lukas
- `docs/abnahme.md` – Prüfpunkte mit Status
- `docs/entscheidungen.md` – technische Entscheidungen mit Begründung
- `docs/projektbeschreibung.md` – Auftrag (Textexport des PDF)
- `docs/bildschirme.md` – Inventar der Bildschirme
- `docs/lizenzen.md` – Icon-Set, Schrift, Abhängigkeiten

## Befehle

| Befehl | Zweck |
| --- | --- |
| `scripts/wp.sh setup` / `start` / `stop` / `reset` | lokales WordPress (MariaDB + PHP-Server, ohne Docker), http://127.0.0.1:8888/tagebuch/ |
| `npm run verify` | Lint, PHP-Syntax, Typprüfung, Build, PHPUnit, Playwright samt Screenshots |
| `npm run build` | Web-App nach `plugin/mediclever-tagebuch/build` bauen |
| `npm run test:php` / `npm run test:e2e` | einzelne Testarten |
| `php scripts/seed.php anna` | künstliche Beispieldaten für ein Testkonto (`leer` als zweites Argument leert es) |
| `npm run fixture` | `tests/fixtures/sample.daylio` neu erzeugen |
| `node scripts/backup-struktur.mjs private/backup.daylio` | Struktur und Zählwerte des echten Backups, ohne Inhalte |
| `node scripts/csv-vergleich.mjs private/export.csv tagebuch.csv` | zeilenweiser Abgleich zweier CSV-Exporte |
| `npm run zip` | installierbares Plugin nach `dist/` |

Testkonten lokal: `admin`/`admin`, `anna`, `ben`, `leer` (Passwort jeweils `<name>-passwort`).

## Aufbau

- `plugin/mediclever-tagebuch/` – Plugin. `includes/*.php`: Fachlogik (`setup`, `entries`, `assets`, `goals`, `stats`, `import`, `export`), `rest.php`, `oauth.php`, `mcp.php`, `reminders.php`.
- `app/src/` – Web-App (Preact, TypeScript). `icons.gen.ts` entsteht aus `scripts/make-icons.mjs`.
- `tests/php/` PHPUnit gegen die Datenbank `mct_test`, `tests/e2e/` Playwright gegen `mct_dev`.

## Feste Regeln

- `reference/` und `private/` werden nie eingecheckt. Tagebuchtexte, Namen und Fotos erscheinen nie in Commits, Testdaten, Logs, Screenshots oder Fehlermeldungen. Tests und Screenshots nutzen nur erfundene Daten.
- Jede Abfrage enthält die Nutzer-ID (`Db::own`, `Db::insert`, `Db::update`, `Db::delete`). Neue Tabellen und Endpunkte bekommen einen Test in `tests/php/IsolationTest.php`.
- REST und MCP rufen dieselbe Fachlogik auf. Auswertungen rechnet nur der Server.
- Neue Funktion: zuerst ein Test, der fehlschlägt. Tests werden nie abgeschwächt oder gelöscht, um grün zu werden. Kein Punkt gilt als erfüllt, solange ein Test fehlschlägt.
- Höchstens fünf Durchläufe je Prüfpunkt, dann Stand in `docs/fortschritt.md` festhalten und Lukas fragen.
- Anhalten und fragen bei: Änderungen am Datenmodell, neuen Abhängigkeiten mit unklarer Lizenz, allem, was den Live-Server berührt, Konflikten zwischen Referenz und eigenem Design.
- Texte: Deutsch, Du-Form, ruhig und sachlich, kein Denglisch („Serie“ statt „Streak“). Tippflächen mindestens 44 px.
- Aus dem Vorbild stammen nur Aufbau und Abläufe. Keine Grafiken, Symbole, Namen oder Texte übernehmen.
- Jede Funktion hängt an einem Schalter (`Features::KEYS`, Filter `mct_feature_enabled`).
