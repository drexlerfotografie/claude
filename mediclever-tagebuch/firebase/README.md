# Firebase-Teil von MediClever Tagebuch

Hier liegt alles, was zu Firebase gehört und nicht in der Android-App steckt:

| Datei | Zweck |
| --- | --- |
| `firestore.rules` | Zugriffsregeln für Firestore. Jede Person erreicht nur `users/{eigene uid}/…`, jedes Dokument wird beim Schreiben geprüft. |
| `firestore.indexes.json` | Absichtlich leer, siehe unten. |
| `firebase.json` | Einstellungen für die Firebase-CLI und den Emulator (Auth auf Port 9099, Firestore auf 8080, ohne Oberfläche). |
| `.firebaserc` | Projektkennung `demo-mediclever-tagebuch`. Kennungen mit `demo-` laufen nur im Emulator und erreichen nie ein echtes Projekt. |
| `test/` | Regeltests: erlaubte Wege (`erlaubt.test.mjs`) und Angriffe (`angriffe.test.mjs`). |

Noch gibt es kein echtes Firebase-Projekt. Entwickelt wird gegen den lokalen Emulator.
Wie das echte Projekt angelegt wird, steht in [`../docs/firebase-einrichtung.md`](../docs/firebase-einrichtung.md).

## Voraussetzungen

- Node.js 22 oder neuer
- Java 21 oder neuer (der Emulator ist ein Java-Programm)

```bash
cd mediclever-tagebuch/firebase
npm install
```

## Tests ausführen

```bash
npm test
```

Der Befehl startet den Firestore-Emulator, lässt alle Tests in `test/` laufen und beendet
ihn wieder. Beim ersten Mal lädt die Firebase-CLI den Emulator herunter (nach
`~/.cache/firebase/emulators`), das dauert etwas.

Die Tests prüfen unter anderem: fremde Daten per Kennung lesen und schreiben, Zugriff ohne
Anmeldung und mit anonymer Anmeldung, unbekannte Felder, falsche Typen, zu lange Notizen,
abweichende `id`, fehlendes oder gefälschtes `syncedAt`, Abfragen über Sammlungsgruppen,
unbekannte Sammlungen – und für jede Sammlung den erlaubten Weg samt der Abfrage, mit der
die App neue Änderungen abholt.

## Emulator für die App starten

```bash
npm run emulators
```

Startet Auth (9099) und Firestore (8080) und bleibt offen, bis du mit Strg+C beendest. Die
Daten sind danach weg. Ein Android-Emulator erreicht den Rechner unter `10.0.2.2`, also
`10.0.2.2:9099` und `10.0.2.2:8080`.

## Warum die Indexdatei leer ist

Die App fragt je Sammlung nur so:
`where syncedAt > Marke, orderBy syncedAt`. Das ist eine Abfrage über ein einziges Feld, und
dafür legt Firestore den Index von selbst an. Zusammengesetzte Indizes braucht es erst, wenn
einmal nach zwei Feldern zugleich gefiltert oder sortiert wird. Die Datei gibt es trotzdem,
damit `firebase deploy` einen festen Stand ausrollt.

## Was die Regeln nicht prüfen können

Die Regelsprache kennt keine Schleifen und wertet je Anfrage höchstens 1000 Ausdrücke aus.
Listen aus Kennungen (`tagIds`, `assetIds`) werden trotzdem vollständig geprüft. Bei Listen
aus Maps (`Scale.points`, `Entry.scaleValues`) geht das nicht: geprüft werden nur Typ und
Länge der Liste (höchstens 100 Punkte, höchstens 200 Skalenwerte), nicht der Inhalt der
einzelnen Maps. App und Website müssen diese Maps beim Lesen deshalb vorsichtig auswerten.

Kennungen in `tagIds` und `assetIds` dürfen kein Komma enthalten (UUIDs tun das nie).

## Regeln später ausrollen

Sobald es das echte Projekt gibt und die Firestore-Datenbank dort angelegt ist:

```bash
npx firebase login
npm test
npx firebase deploy --only firestore:rules,firestore:indexes --project DEINE-PROJEKT-ID
```

Die Projektkennung steht in der Firebase-Konsole in den Projekteinstellungen. Sie wird
bewusst jedes Mal angegeben; so bleibt `.firebaserc` beim Demo-Projekt und niemand rollt
versehentlich etwas aus. Regeln immer erst nach grünem `npm test` ausrollen.
