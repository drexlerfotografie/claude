# Firebase einrichten – Schritt für Schritt

Diese Anleitung führt Dich vom leeren Google-Konto bis zum fertigen Firebase-Projekt für
MediClever Tagebuch. Du brauchst sie erst, wenn die App mit echten Konten laufen soll. Bis
dahin arbeitet alles gegen den lokalen Emulator (siehe `firebase/README.md`).

Ein Hinweis vorab: Google benennt Menüs in der Firebase-Konsole und in der Play Console
immer wieder um. Ich beschreibe deshalb, was Du erreichen willst, und nenne Menünamen nur
als Anhaltspunkt. Wenn ein Punkt bei Dir etwas anders heißt, ist das normal.

Was am Ende steht:

- ein Firebase-Projekt im kostenlosen Tarif (Spark)
- eine Firestore-Datenbank mit Standort in der EU
- Anmeldung mit E-Mail und Passwort sowie mit Google
- die Android-App `de.mediclever.tagebuch` als registrierte App
- die strengen Zugriffsregeln aus `firebase/firestore.rules`, ausgerollt

Nicht gebraucht werden in Version 1: Firebase Storage, Cloud Functions, Analytics. Fotos
und Sprachnotizen bleiben auf dem Gerät.

---

## 1. Projekt anlegen

1. Öffne <https://console.firebase.google.com> und melde Dich mit dem Google-Konto an, dem
   das Projekt gehören soll. Nimm dafür ein Konto, das Du dauerhaft behältst und mit
   Zwei-Faktor-Anmeldung schützt. Wer dieses Konto übernimmt, kann alle Tagebücher lesen.
2. Lege ein neues Projekt an. Den Namen kannst Du frei wählen, zum Beispiel
   „MediClever Tagebuch“.
3. Unter dem Namen zeigt die Konsole die **Projektkennung** (Project ID), etwa
   `mediclever-tagebuch-1a2b3`. Du kannst sie in diesem Moment noch anpassen, danach nie
   wieder. Notiere sie, Du brauchst sie in Schritt 7.
4. Google Analytics wird Dir angeboten. Schalte es **aus**. Die App braucht es nicht, und
   bei Gesundheitsdaten ist jede zusätzliche Auswertung ein Thema für die
   Datenschutzerklärung.

Das Projekt läuft danach im Tarif **Spark**. Er kostet nichts und verlangt keine
Zahlungsdaten.

## 2. Firestore-Datenbank anlegen und den Standort wählen

Suche in der Konsole den Bereich für Datenbanken und dort **Firestore** (nicht „Realtime
Database“, das ist ein anderes Produkt). Lege eine Datenbank an.

Dabei wirst Du zwei Dinge gefragt:

**Standort.** Das ist die wichtigste Entscheidung dieser Anleitung, denn **der Standort
lässt sich nachträglich nie mehr ändern**. Wer sich vertut, muss ein neues Projekt anlegen
und alle Daten umziehen. Für uns kommen zwei Standorte in Frage:

| Standort | Was es ist | Für uns |
| --- | --- | --- |
| `europe-west3` | Eine einzelne Region: Frankfurt. Die Daten liegen in Deutschland, verteilt auf mehrere Rechenzentren dieser Region. | Meine Empfehlung. „Die Daten liegen in Frankfurt“ lässt sich Nutzern und in der Datenschutzerklärung am einfachsten erklären. |
| `eur3` | Ein Verbund mehrerer Regionen in der EU (Belgien und Niederlande). Übersteht auch den Ausfall einer ganzen Region. | Ebenfalls in der EU und in Ordnung. Die höhere Ausfallsicherheit braucht ein Tagebuch kaum, und im Bezahltarif ist dieser Standort teurer. |

Beides bleibt in der EU. Nimm `europe-west3`, wenn Du keinen besonderen Grund für `eur3`
hast. Achte darauf, dass Du nicht aus Versehen die Vorauswahl stehen lässt; sie zeigt oft
auf einen Standort in den USA.

Wirst Du nach einem Namen für die Datenbank gefragt, lass die Vorgabe `(default)` stehen.
Die App und die Regeln gehen davon aus.

**Startmodus der Regeln.** Wähle den gesperrten Modus („Produktionsmodus“ oder ähnlich),
**nicht** den Testmodus. Im Testmodus darf für einige Wochen jeder alles lesen und
schreiben. Im gesperrten Modus darf zunächst niemand etwas; die richtigen Regeln rollst Du
in Schritt 7 aus.

## 3. Anmeldung einschalten

Suche den Bereich **Authentication** und starte ihn. Dort gibt es eine Liste der
Anmeldemethoden.

**E-Mail/Passwort**

- Schalte „E-Mail/Passwort“ ein.
- Die Variante „Anmeldung per E-Mail-Link (ohne Passwort)“ bleibt aus.

**Google**

- Schalte „Google“ ein.
- Du wirst nach einem öffentlichen Projektnamen und einer Support-E-Mail-Adresse gefragt.
  Beides sehen Nutzer im Google-Anmeldefenster. Nimm den App-Namen und eine Adresse, die Du
  wirklich liest.

**Anonyme Anmeldung** bleibt aus. Die Regeln lehnen anonyme Konten ohnehin ab.

Sinnvoll, solange Du schon hier bist:

- **Vorlagen für E-Mails** (Passwort zurücksetzen, Adresse bestätigen): Stelle die Sprache
  auf Deutsch und prüfe Absendername und Text.
- **Schutz vor dem Ausspähen von E-Mail-Adressen** („E-Mail-Enumeration-Schutz“): Lass ihn
  eingeschaltet. Dann verrät die Anmeldung nicht, ob zu einer Adresse ein Konto besteht.
  Bei einer Gesundheits-App ist schon das eine heikle Auskunft.
- **Passwortrichtlinie**: Wenn die Konsole eine Mindestlänge anbietet, stelle sie so ein,
  wie die App sie auch verlangt.

Wichtig für die Datenschutzerklärung: Die Kontodaten der Anmeldung (E-Mail-Adresse,
Passwort-Hash, Zeitpunkt der letzten Anmeldung) verarbeitet Firebase Authentication nach
meinem Kenntnisstand in den USA, unabhängig vom Standort der Firestore-Datenbank. Die
Tagebuchinhalte liegen in Firestore und damit in der EU. Mehr dazu in Abschnitt 11.

## 4. Android-App hinzufügen

In der Projektübersicht oder in den Projekteinstellungen kannst Du eine App hinzufügen.
Wähle Android.

- **Paketname:** `de.mediclever.tagebuch` – genau so, ohne Leerzeichen. Er muss mit der
  `applicationId` in `app/build.gradle.kts` übereinstimmen und lässt sich später nicht
  ändern.
- **Spitzname:** frei, zum Beispiel „Tagebuch Android“.
- **SHA-1:** kannst Du hier schon eintragen oder gleich in Schritt 5.

Die Konsole zeigt danach Schritte zum Einbau des SDK. Die kannst Du überspringen; das ist
im Projekt bereits erledigt. Du brauchst nur die Datei aus Schritt 6.

## 5. Fingerabdrücke (SHA-1 und SHA-256) für die Google-Anmeldung

Die Anmeldung mit Google funktioniert nur, wenn Google die App an ihrer Signatur erkennt.
Dazu trägst Du die Fingerabdrücke der Schlüssel ein, mit denen die App signiert wird. Der
Ort dafür: Projekteinstellungen, dort Deine Android-App, dort „Fingerabdruck hinzufügen“.
Trage jeweils SHA-1 **und** SHA-256 ein.

Es gibt **drei** Schlüssel. Fehlt einer, klappt die Google-Anmeldung in genau der
zugehörigen Fassung der App nicht – meist mit einer nichtssagenden Fehlermeldung
(häufig Fehlercode 10 oder „DEVELOPER_ERROR“).

**a) Debug-Schlüssel – für die App, die Du aus Android Studio startest**

Jeder Entwicklungsrechner hat seinen eigenen. So liest Du ihn aus:

```bash
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
```

Unter Windows liegt die Datei unter `%USERPROFILE%\.android\debug.keystore`. Oder im
Projektordner:

```bash
./gradlew signingReport
```

Der Befehl listet die Fingerabdrücke aller Varianten auf.

**b) Dein eigener Upload-Schlüssel – für Release-Fassungen, die Du selbst baust**

Das ist die Keystore-Datei, mit der Du das App-Bundle für den Play Store signierst:

```bash
keytool -list -v -alias DEIN-ALIAS -keystore PFAD/ZU/DEINEM/upload-keystore.jks
```

Diese Datei und ihr Passwort gehören nicht ins Git (`*.jks`, `*.keystore` und
`keystore.properties` sind in `.gitignore` eingetragen). Bewahre eine Sicherung an einem
zweiten Ort auf.

**c) Der Schlüssel von Play App Signing – für die App, die Nutzer aus dem Play Store laden**

**Das ist der, den fast alle vergessen.** Google signiert die App vor der Auslieferung
noch einmal mit einem eigenen Schlüssel, den nur Google besitzt. Die Fassung aus dem Play
Store trägt also einen anderen Fingerabdruck als die, die Du gebaut hast. Die Folge, wenn
er fehlt: Auf Deinem Gerät geht alles, im internen Test über den Play Store schlägt die
Google-Anmeldung fehl.

Du findest ihn in der Play Console bei Deiner App, im Bereich zur App-Signatur oder
App-Integrität (der Menüpunkt hieß schon „App-Signatur“, „App-Integrität“ und „Einrichten“,
je nach Stand der Konsole). Dort gibt es zwei Zertifikate:

- „Zertifikat für den App-Signaturschlüssel“ – **dieses** brauchst Du.
- „Zertifikat für den Upload-Schlüssel“ – das ist der aus b).

Kopiere SHA-1 und SHA-256 des App-Signaturschlüssels und trage sie in Firebase ein. Das
geht erst, nachdem Du die App in der Play Console angelegt und Play App Signing aktiviert
hast, also meist erst kurz vor dem ersten Upload. Setz Dir dafür eine Erinnerung.

Fingerabdrücke sind keine Geheimnisse. Du darfst sie weitergeben; geheim ist nur die
Keystore-Datei selbst.

## 6. `google-services.json` herunterladen

In den Projekteinstellungen bei Deiner Android-App gibt es einen Knopf zum Herunterladen
von `google-services.json`. Lege die Datei hierhin:

```
mediclever-tagebuch/app/google-services.json
```

- Die Datei ist in `.gitignore` eingetragen und wird nicht eingecheckt. Jeder, der die App
  bauen soll, braucht sie von Dir.
- **Lade sie neu herunter, wenn Du Fingerabdrücke ergänzt oder die Google-Anmeldung
  eingeschaltet hast.** Die Datei enthält die Kennungen, die die Google-Anmeldung braucht,
  und eine alte Fassung kennt die neuen nicht.
- Die Datei enthält einen API-Schlüssel. Der ist kein Passwort; er steckt später in jeder
  ausgelieferten App. Den Schutz der Daten leisten die Firestore-Regeln, nicht dieser
  Schlüssel.

## 7. Regeln ausrollen

Die Regeln liegen in `mediclever-tagebuch/firebase/firestore.rules`. Ausgerollt werden sie
mit der Firebase-CLI. Du brauchst Node.js 22 und Java 21.

```bash
cd mediclever-tagebuch/firebase
npm install
npm test
```

`npm test` prüft die Regeln im lokalen Emulator. Rolle nur aus, wenn alles grün ist.

```bash
npx firebase login
npx firebase deploy --only firestore:rules,firestore:indexes --project DEINE-PROJEKT-ID
```

- `firebase login` öffnet den Browser. Melde Dich mit dem Konto aus Schritt 1 an.
- `DEINE-PROJEKT-ID` ist die Kennung aus Schritt 1.
- Die Firestore-Datenbank aus Schritt 2 muss schon bestehen.

Prüfe das Ergebnis in der Konsole: Im Firestore-Bereich gibt es eine Ansicht der Regeln.
Dort muss jetzt der Inhalt von `firestore.rules` stehen.

Ändere Regeln **nie** direkt in der Konsole. Sonst weicht der Stand im Projekt von dem ab,
den die Tests prüfen, und das nächste Ausrollen überschreibt Deine Änderung kommentarlos.

## 8. Kosten: Kontingente und Budgetwarnung

**Im Tarif Spark** kann nichts kosten, denn es ist kein Zahlungsmittel hinterlegt. Dafür
gibt es Tagesgrenzen. Für Firestore sind das derzeit rund 50 000 gelesene, 20 000
geschriebene und 20 000 gelöschte Dokumente pro Tag sowie 1 GiB Speicher. Schau die
aktuellen Zahlen bei Firebase nach, sie ändern sich gelegentlich. Ist eine Grenze erreicht,
lehnt Firestore für den Rest des Tages weitere Zugriffe ab. Die App arbeitet dann lokal
weiter und gleicht später ab. Für die ersten paar hundert Nutzer reicht das.

**Wenn Du später auf Blaze wechselst** (Abrechnung nach Verbrauch), richte am selben Tag
eine Budgetwarnung ein:

1. Öffne die Abrechnung in der Google-Cloud-Konsole (<https://console.cloud.google.com>,
   Bereich „Abrechnung“, dort „Budgets und Benachrichtigungen“ oder ähnlich).
2. Lege für das Projekt ein Budget an, zum Beispiel 10 € im Monat, mit Benachrichtigungen
   bei 50 %, 90 % und 100 %.

Wichtig zu wissen: **Eine Budgetwarnung schickt nur E-Mails. Sie stoppt nichts.** Eine
feste Obergrenze gibt es bei Blaze nicht. Der wirksame Schutz vor Überraschungen sind die
strengen Regeln (kein Zugriff ohne Anmeldung, Größengrenzen je Dokument) und später App
Check.

## 9. App Check – ein späterer Schritt zur Härtung

Die Regeln stellen sicher, dass jede Person nur ihre eigenen Daten erreicht. Sie können
aber nicht erkennen, ob eine Anfrage von der echten App kommt oder von einem Skript, das
sich mit einem gültigen Konto anmeldet. **App Check** schließt diese Lücke: Firestore
nimmt dann nur Anfragen an, die nachweislich von Deiner unveränderten App stammen (auf
Android über „Play Integrity“).

Für Version 1 ist das nicht nötig. Plane es ein, bevor die App viele Nutzer hat:

1. App Check in der Firebase-Konsole für die Android-App mit Play Integrity einrichten.
   Dafür wird der SHA-256-Fingerabdruck aus Schritt 5 c) gebraucht.
2. Das App-Check-SDK in die App einbauen und eine Fassung veröffentlichen.
3. Einige Wochen nur beobachten. Die Konsole zeigt, welcher Anteil der Anfragen bestätigt
   ist.
4. Erst wenn fast alle Nutzer die neue Fassung haben, die Durchsetzung für Firestore
   einschalten. Schaltest Du zu früh, sperrst Du alle mit älterer App aus.

Sobald es die Website gibt, braucht auch sie einen App-Check-Anbieter (für Websites
reCAPTCHA), sonst sperrt die Durchsetzung die Website aus.

## 10. Play Console: Datensicherheit und Gesundheitsdaten

Vor der Veröffentlichung verlangt die Play Console mehrere Erklärungen. Sie stehen im
Bereich zu App-Inhalten (Name kann abweichen). Die Angaben müssen stimmen; Google gleicht
sie mit dem Verhalten der App ab.

### Formular „Datensicherheit“

Was nach heutigem Stand der App zutrifft:

| Frage | Antwort für diese App |
| --- | --- |
| Erhebt oder teilt die App Nutzerdaten? | Ja, sie erhebt Daten. „Erheben“ heißt hier: Daten verlassen das Gerät. |
| Werden Daten bei der Übertragung verschlüsselt? | Ja. Firebase überträgt ausschließlich über TLS. |
| Können Nutzer die Löschung ihrer Daten verlangen? | Ja. Die App braucht dafür die Funktion „Konto löschen“, **und** Google verlangt zusätzlich eine Webadresse, über die man die Löschung auch ohne installierte App anstoßen kann. Diese Seite musst Du bereitstellen. |
| Welche Datenarten? | **Personenbezogene Daten:** E-Mail-Adresse; Nutzerkennung (die Firebase-uid); bei Google-Anmeldung je nach Umsetzung auch der Name. **Gesundheit und Fitness:** Gesundheitsdaten (Stimmung, Schlaf, Schmerz und Ähnliches, dazu die Notizen). |
| Was ist mit Fotos und Sprachnotizen? | Sie bleiben in Version 1 auf dem Gerät und gelten damit nicht als erhoben. Abgeglichen werden nur Angaben dazu (Dateiname, Größe, Prüfsumme). Sobald Dateien einmal hochgeladen werden, muss das Formular angepasst werden. |
| Wozu werden die Daten erhoben? | App-Funktionalität (Abgleich zwischen Geräten und später der Website) und Kontoverwaltung. Nicht: Werbung, Analyse, Personalisierung. |
| Sind die Angaben erforderlich oder freiwillig? | Der Abgleich hängt am Konto. Wenn die App auch ohne Konto nutzbar ist, sind sie freiwillig, sonst erforderlich. |
| Werden Daten an Dritte weitergegeben? | Nein. Google verarbeitet die Daten als Dienstleister in Deinem Auftrag; das zählt im Formular nicht als Weitergabe. |

Wenn Du später Absturzberichte oder Analyse einbaust (Crashlytics, Analytics), ändern sich
diese Antworten. Passe das Formular dann vor der Veröffentlichung an.

### Erklärung zu Gesundheits-Apps

Die Play Console fragt außerdem ab, ob die App Gesundheitsfunktionen hat, und lässt Dich
Kategorien ankreuzen. Für ein Stimmungstagebuch passen Kategorien rund um psychisches
Wohlbefinden, Stimmung, Stress, Schlaf und gegebenenfalls Symptom- oder Schmerztagebuch.
Die genaue Liste gibt Google vor; wähle, was die App tatsächlich kann, und nicht mehr.

Dazu gehören üblicherweise:

- **Datenschutzerklärung:** eine öffentlich erreichbare Webadresse, eingetragen in der
  Play Console und verlinkt in der App. Ohne sie wird die App abgelehnt.
- **Kein Medizinprodukt:** Die App misst, diagnostiziert und behandelt nichts. Das muss
  in der Store-Beschreibung und in der App klar stehen, samt dem Hinweis, dass sie
  ärztlichen Rat nicht ersetzt. Verzichte auf Aussagen wie „hilft gegen Depressionen“.
- **Zielgruppe und Alter:** Gib bei der Zielgruppe ausschließlich 18 Jahre und älter an,
  passend zur Altersgrenze der App.
- **Keine Nutzung der Gesundheitsdaten für Werbung.** Das trifft hier zu, weil die App
  keine Werbung und keine Auswertung enthält.

## 11. Datenschutz: Was nur eine Fachperson klären kann

Ich bin keine Juristin und kein Jurist, und diese Anleitung ist keine Rechtsberatung.
Stimmungs-, Schlaf- und Schmerzdaten sind Gesundheitsdaten und damit nach Art. 9 DSGVO
besonders geschützt. Fehler können hier teuer werden. Lass die folgenden Punkte vor der
Veröffentlichung von einer Fachperson für Datenschutzrecht prüfen:

- **Auftragsverarbeitung mit Google.** Google verarbeitet die Daten in Deinem Auftrag. Die
  nötige Vereinbarung ist Teil der Firebase- und Google-Cloud-Bedingungen, denen Du beim
  Anlegen des Projekts zustimmst. Ob das für Gesundheitsdaten genügt, wie Du es
  dokumentierst und was für die Übermittlung in die USA gilt (die Anmeldedaten liegen dort,
  siehe Schritt 3), muss jemand beurteilen, der dafür haftet.
- **Rechtsgrundlage und Einwilligung.** Für Gesundheitsdaten braucht es in aller Regel eine
  ausdrückliche Einwilligung. Die App speichert dafür im Profil, wann und zu welcher
  Fassung des Textes eingewilligt wurde (`consentDiaryAt`, `consentVersion`). Ob Wortlaut,
  Zeitpunkt und Widerruf so genügen, ist eine Rechtsfrage.
- **Datenschutzerklärung.** Sie muss zu dem passen, was die App wirklich tut: Firebase
  Authentication, Firestore in der EU, Speicherdauer, Löschung, Rechte der Betroffenen,
  Kontaktadresse. Ein Generator aus dem Netz reicht für eine Gesundheits-App nicht.
- **Altersgrenze 18.** Die Grenze ist so gewählt, dass keine Einwilligung von Eltern nötig
  wird. Ob eine Abfrage in der App genügt und wie sie zu formulieren ist, gehört geprüft.
- **Datenschutz-Folgenabschätzung und Verzeichnis der Verarbeitungstätigkeiten.** Bei
  Gesundheitsdaten ist eine Folgenabschätzung häufig Pflicht. Ob sie hier nötig ist und wie
  sie aussieht, entscheidet die Fachperson.
- **Abgrenzung zum Medizinprodukt.** Je nach Funktionen und Werbeaussagen kann eine
  Gesundheits-App unter das Medizinprodukterecht fallen. Das sollte einmal ausdrücklich
  verneint und begründet sein.
- **Impressum und Löschweg.** Anbieterkennzeichnung in App und Store, dazu die in
  Abschnitt 10 genannte Webadresse zum Löschen des Kontos.

Was die Technik dazu beiträgt, damit Du es der Fachperson sagen kannst: Tagebuchinhalte
liegen in Firestore in der EU; jede Person erreicht ausschließlich ihren eigenen Zweig
`users/{uid}`; ohne Anmeldung und mit anonymer Anmeldung gibt es keinen Zugriff; Fotos und
Sprachnotizen verlassen das Gerät nicht; „Konto löschen“ entfernt alle Dokumente der
Person. Zur Ehrlichkeit gehört auch: Die Inhalte sind bei Google verschlüsselt gespeichert,
aber nicht Ende-zu-Ende. Wer Zugriff auf das Projektkonto aus Schritt 1 hat, kann sie
lesen.

---

## Kurzliste zum Abhaken

- [ ] Projekt angelegt, Projektkennung notiert, Analytics aus
- [ ] Firestore angelegt, Standort in der EU (`europe-west3` oder `eur3`), gesperrter Modus
- [ ] Anmeldung: E-Mail/Passwort und Google ein, anonym aus
- [ ] Android-App `de.mediclever.tagebuch` hinzugefügt
- [ ] Fingerabdrücke eingetragen: Debug, Upload-Schlüssel, **Play App Signing**
- [ ] `google-services.json` nach `mediclever-tagebuch/app/` gelegt (nach Schritt 5 neu geladen)
- [ ] `npm test` grün, Regeln ausgerollt und in der Konsole geprüft
- [ ] Bei Wechsel auf Blaze: Budgetwarnung eingerichtet
- [ ] Play Console: Datensicherheit, Gesundheits-Erklärung, Datenschutzerklärung, Löschadresse
- [ ] Datenschutz von einer Fachperson geprüft
- [ ] Später: App Check
