# MediClever Tagebuch

Stimmungs- und Aktivitäten-Tagebuch für mediclever.de: WordPress-Plugin mit Web-App, REST-Schnittstelle und MCP-Endpunkt. Jeder Nutzer richtet Stimmungen, Aktivitäten und Skalen selbst ein.

Einstieg für die Arbeit am Projekt: [`CLAUDE.md`](CLAUDE.md). Stand und offene Fragen: [`docs/fortschritt.md`](docs/fortschritt.md).

## Lokal starten

Voraussetzungen: PHP ab 8.1 mit GD, ZIP und OpenSSL, Composer, MariaDB oder MySQL (`mariadbd` im Pfad), Node ab 20.

```bash
npm install
composer install
scripts/wp.sh setup      # WordPress, Datenbank, Testkonten, Seite „Tagebuch“
npm run build
scripts/wp.sh start      # http://127.0.0.1:8888/tagebuch/
php scripts/seed.php anna
```

Anmelden mit `anna` / `anna-passwort`. Alles prüfen: `npm run verify`.

## Auf einer WordPress-Seite einbinden

1. `npm run zip` erzeugt `dist/mediclever-tagebuch.zip`.
2. Plugin hochladen und aktivieren.
3. Eine Seite mit dem Shortcode `[mediclever_tagebuch]` anlegen.

Vor dem Livegang die Punkte in `docs/fortschritt.md` klären, besonders Hosting, Speicherort der Fotos und die rechtliche Prüfung.

## Private Daten

`reference/` und `private/` stehen in `.gitignore` und werden nie eingecheckt. Tests und Screenshots laufen nur mit erfundenen Daten (`tests/fixtures/`, `scripts/seed.php`).
