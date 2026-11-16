# Lizenzen

Stand: 16.11.2026. Alles, was mit dem Plugin ausgeliefert wird, steht hier. Entwicklungswerkzeuge stehen unten getrennt.

## Ausgeliefert

| Bestandteil | Zweck | Lizenz | Quelle |
| --- | --- | --- | --- |
| Lucide (Auswahl von 220 Symbolen) | Symbole für Aktivitäten, Skalen und Bedienung | ISC | lucide.dev, npm `lucide` 1.73 |
| Preact | Oberfläche der Web-App | MIT | preactjs.com, npm `preact` 10 |
| Roboto (Latin, Latin Extended; 400, 500, 700, 900) | Markenschrift laut Styleguide, lokal eingebunden | Apache-2.0 (Fontsource-Paket: SIL OFL 1.1 / Apache-2.0) | npm `@fontsource/roboto` 5 |
| MediClever-Bildmarke (`logo-72.png`) | Absender in Kopf und Navigation | Eigentum von MediClever | Logo-Paket vom 05.08.2026 |
| Plugin-Code, Stimmungssymbole m1 bis m5, Texte | – | GPL-2.0-or-later, eigene Arbeit | dieses Repository |

**Vorschlag Icon-Set:** Lucide. Gründe: freie ISC-Lizenz ohne Namensnennungspflicht in der Oberfläche, einheitliche Strichstärke, sehr große Auswahl mit Medizin- und Alltagsmotiven, als reine Pfaddaten nutzbar. Ausgeliefert wird nur die kuratierte Auswahl aus `scripts/make-icons.mjs` (rund 60 kB), nicht das ganze Set.

Die fünf Stimmungssymbole sind selbst gezeichnet (`app/src/icons.tsx`) und stammen nicht aus Lucide oder dem Vorbild. Lucide enthält zusätzlich neutrale Gesichter (`face-*`), die Nutzer als Alternative wählen können.

Nicht übernommen aus dem Vorbild: Name, Logo, Stimmungsgesichter, Symbolpakete, Illustrationen, Abzeichen-Grafiken, Namen der Farbschemata, Texte.

Es werden keine externen CDNs, Schriftdienste oder Tracker eingebunden. Die Playwright-Tests prüfen bei jedem Lauf, dass die App keine Anfrage an fremde Server stellt.

## Nur für Entwicklung und Tests

| Werkzeug | Lizenz |
| --- | --- |
| Vite, @preact/preset-vite, ESLint, typescript-eslint | MIT |
| TypeScript, Playwright | Apache-2.0 |
| @axe-core/playwright | MPL-2.0 |
| @modelcontextprotocol/sdk (nur im MCP-Test) | MIT |
| PHPUnit | BSD-3-Clause |
| WordPress (johnpbloch/wordpress-core, lokale Umgebung) | GPL-2.0-or-later |
| MariaDB (lokale Umgebung) | GPL-2.0 |
