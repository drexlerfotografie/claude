// Angriffe: Alles hier muss an den Regeln scheitern.

import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  collectionGroup,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import {
  ALICE,
  BOB,
  COLLECTIONS,
  SAMPLES,
  anonymousDb,
  createTestEnv,
  dbOf,
  sampleProfile,
  sampleSettings,
  signedOutDb,
} from './helpers.mjs';

let env;
let alice;
let bob;

before(async () => {
  env = await createTestEnv();
});

after(async () => {
  await env.cleanup();
});

// Vor jedem Test liegt bei Alice in jeder Sammlung ein gültiges Dokument,
// dazu Einstellungen und Profil. Geschrieben wird es über die Regeln, damit
// die Ausgangslage dieselbe ist wie im echten Betrieb.
beforeEach(async () => {
  await env.clearFirestore();
  alice = dbOf(env, ALICE);
  bob = dbOf(env, BOB);
  const batch = writeBatch(alice);
  for (const name of COLLECTIONS) {
    const data = SAMPLES[name]();
    batch.set(doc(alice, 'users', ALICE, name, data.id), data);
  }
  batch.set(doc(alice, 'users', ALICE, 'settings', 'main'), sampleSettings());
  batch.set(doc(alice, 'users', ALICE), sampleProfile());
  await assertSucceeds(batch.commit());
});

/** Dieselben Zugriffe für jede fremde oder ungültige Anmeldung. */
function strangerSuite(label, makeDb) {
  describe(label, () => {
    for (const name of COLLECTIONS) {
      test(`${name}: lesen per id, auflisten, abholen, schreiben, ändern, löschen`, async () => {
        const db = makeDb();
        const data = SAMPLES[name]();
        const ref = doc(db, 'users', ALICE, name, data.id);
        await assertFails(getDoc(ref));
        await assertFails(getDocs(collection(db, 'users', ALICE, name)));
        await assertFails(getDocs(query(collection(db, 'users', ALICE, name), limit(1))));
        await assertFails(
          getDocs(
            query(
              collection(db, 'users', ALICE, name),
              where('syncedAt', '>', Timestamp.fromMillis(0)),
              orderBy('syncedAt'),
            ),
          ),
        );
        await assertFails(setDoc(ref, data));
        await assertFails(setDoc(doc(db, 'users', ALICE, name, 'neu'), { ...data, id: 'neu' }));
        await assertFails(updateDoc(ref, { deleted: true, syncedAt: serverTimestamp() }));
        await assertFails(deleteDoc(ref));
      });
    }

    test('Einstellungen und Profil', async () => {
      const db = makeDb();
      await assertFails(getDoc(doc(db, 'users', ALICE, 'settings', 'main')));
      await assertFails(getDocs(collection(db, 'users', ALICE, 'settings')));
      await assertFails(setDoc(doc(db, 'users', ALICE, 'settings', 'main'), sampleSettings()));
      await assertFails(deleteDoc(doc(db, 'users', ALICE, 'settings', 'main')));
      await assertFails(getDoc(doc(db, 'users', ALICE)));
      await assertFails(setDoc(doc(db, 'users', ALICE), sampleProfile()));
      await assertFails(updateDoc(doc(db, 'users', ALICE), { appVersion: '9.9.9' }));
      await assertFails(deleteDoc(doc(db, 'users', ALICE)));
    });
  });
}

strangerSuite('Fremde Person (Bob) greift auf Alices Daten zu', () => bob);
strangerSuite('Nicht angemeldet', () => signedOutDb(env));
strangerSuite('Anonym angemeldet mit fremder uid', () => anonymousDb(env, 'anon-uid'));

describe('Anonym angemeldet im eigenen Zweig', () => {
  test('weder lesen noch schreiben', async () => {
    const db = anonymousDb(env, ALICE);
    for (const name of COLLECTIONS) {
      const data = SAMPLES[name]();
      const ref = doc(db, 'users', ALICE, name, data.id);
      await assertFails(getDoc(ref));
      await assertFails(getDocs(collection(db, 'users', ALICE, name)));
      await assertFails(setDoc(ref, data));
      await assertFails(deleteDoc(ref));
    }
    await assertFails(getDoc(doc(db, 'users', ALICE)));
    await assertFails(setDoc(doc(db, 'users', ALICE, 'settings', 'main'), sampleSettings()));
  });

  test('auch ein frischer anonymer Zweig bleibt zu', async () => {
    const db = anonymousDb(env, 'anon-uid');
    const data = SAMPLES.entries();
    await assertFails(setDoc(doc(db, 'users', 'anon-uid', 'entries', data.id), data));
    await assertFails(setDoc(doc(db, 'users', 'anon-uid'), sampleProfile()));
    await assertFails(getDocs(collection(db, 'users', 'anon-uid', 'entries')));
  });
});

describe('Bob schmuggelt Daten in Alices Zweig', () => {
  test('gültiges Dokument an fremden Pfad, auch im Stapel mit eigenem Schreiben', async () => {
    const data = SAMPLES.entries();
    const batch = writeBatch(bob);
    batch.set(doc(bob, 'users', BOB, 'entries', data.id), data);
    batch.set(doc(bob, 'users', ALICE, 'entries', 'von-bob'), { ...data, id: 'von-bob' });
    await assertFails(batch.commit());
    // Der Stapel ist als Ganzes gescheitert: auch Bobs eigener Teil fehlt.
    const own = await assertSucceeds(getDocs(collection(bob, 'users', BOB, 'entries')));
    if (own.size !== 0) throw new Error('Stapel wurde teilweise geschrieben');
  });
});

describe('Breite Abfragen', () => {
  test('Sammlungsgruppen sind für alle zu, auch für die Besitzerin', async () => {
    for (const db of [alice, bob, signedOutDb(env)]) {
      for (const name of [...COLLECTIONS, 'settings']) {
        await assertFails(getDocs(collectionGroup(db, name)));
        await assertFails(
          getDocs(query(collectionGroup(db, name), where('id', '==', SAMPLES.entries().id))),
        );
      }
    }
  });

  test('die Liste aller Nutzer ist für alle zu', async () => {
    for (const db of [alice, bob, signedOutDb(env)]) {
      await assertFails(getDocs(collection(db, 'users')));
      await assertFails(getDocs(query(collection(db, 'users'), limit(1))));
    }
  });
});

describe('Unbekannte Orte', () => {
  test('unbekannte Sammlung im eigenen Zweig', async () => {
    for (const name of ['photos', 'entries_backup', 'Entries', 'goalChecks', 'private']) {
      const ref = doc(alice, 'users', ALICE, name, 'x');
      await assertFails(setDoc(ref, { ...SAMPLES.entries(), id: 'x' }));
      await assertFails(getDoc(ref));
      await assertFails(getDocs(collection(alice, 'users', ALICE, name)));
    }
  });

  test('Sammlung auf oberster Ebene', async () => {
    for (const name of ['public', 'entries', 'config', 'admins']) {
      await assertFails(setDoc(doc(alice, name, 'x'), { a: 1 }));
      await assertFails(getDoc(doc(alice, name, 'x')));
      await assertFails(getDocs(collection(alice, name)));
    }
  });

  test('Untersammlung unter einem Tagebuchdokument', async () => {
    const id = SAMPLES.entries().id;
    const ref = doc(alice, 'users', ALICE, 'entries', id, 'comments', 'x');
    await assertFails(setDoc(ref, { text: 'hallo' }));
    await assertFails(getDoc(ref));
  });

  test('settings: nur das Dokument main', async () => {
    await assertFails(setDoc(doc(alice, 'users', ALICE, 'settings', 'other'), sampleSettings()));
  });
});

/**
 * Prüft für eine Sammlung, dass jede der Abwandlungen abgelehnt wird, und
 * zwar beim Neuanlegen, beim Überschreiben und – wo möglich – als Teil-Update.
 */
function rejects(name, cases) {
  describe(`${name}: ungültige Inhalte`, () => {
    for (const [label, mutate] of Object.entries(cases)) {
      test(label, async () => {
        const base = SAMPLES[name]();
        const bad = mutate({ ...base }) ?? base;
        const id = bad.__docId ?? base.id;
        delete bad.__docId;
        // Überschreiben des vorhandenen Dokuments (oder Anlegen unter anderer id).
        await assertFails(setDoc(doc(alice, 'users', ALICE, name, id), bad));
        // Neuanlegen in einem leeren Zweig derselben Person.
        const fresh = dbOf(env, 'carol-uid');
        await assertFails(setDoc(doc(fresh, 'users', 'carol-uid', name, id), bad));
      });
    }
  });
}

const without = (key) => (d) => {
  delete d[key];
  return d;
};
const withValue = (key, value) => (d) => ({ ...d, [key]: value });

/** Angriffe, die für jede Sammlung gleich aussehen. */
const COMMON = {
  'unbekanntes Feld': withValue('isAdmin', true),
  'lokales Feld dirty': withValue('dirty', false),
  'id weicht von der Dokument-id ab': withValue('id', 'eine-andere-id'),
  'id fehlt': without('id'),
  'id ist keine Zeichenkette': withValue('id', 42),
  'syncedAt fehlt': without('syncedAt'),
  'syncedAt gefälscht (Vergangenheit)': withValue('syncedAt', Timestamp.fromMillis(1_000_000)),
  'syncedAt gefälscht (Zukunft)': withValue('syncedAt', Timestamp.fromMillis(4_102_444_800_000)),
  'syncedAt gefälscht (Uhr des Geräts, 5 s nach)': (d) => ({
    ...d,
    syncedAt: Timestamp.fromMillis(Date.now() - 5000),
  }),
  'syncedAt gefälscht (Uhr des Geräts, 5 s vor)': (d) => ({
    ...d,
    syncedAt: Timestamp.fromMillis(Date.now() + 5000),
  }),
  'syncedAt als Zahl': withValue('syncedAt', 1_794_000_000_000),
  'syncedAt null': withValue('syncedAt', null),
  'updatedAt fehlt': without('updatedAt'),
  'updatedAt als Zeichenkette': withValue('updatedAt', '1794000000000'),
  'updatedAt als Zeitstempel': withValue('updatedAt', Timestamp.fromMillis(1_794_000_000_000)),
  'updatedAt als Kommazahl': withValue('updatedAt', 1.5),
  'deleted fehlt': without('deleted'),
  'deleted als Zeichenkette': withValue('deleted', 'true'),
  'leeres Dokument': () => ({}),
};

const long = (n) => 'x'.repeat(n);

const SPECIFIC = {
  moods: {
    'level 0': withValue('level', 0),
    'level 6': withValue('level', 6),
    'level als Zeichenkette': withValue('level', '3'),
    'level als Kommazahl': withValue('level', 2.5),
    'level fehlt': without('level'),
    'name zu lang': withValue('name', long(201)),
    'name ist keine Zeichenkette': withValue('name', 7),
    'icon zu lang': withValue('icon', long(101)),
    'sortOrder als Zeichenkette': withValue('sortOrder', '1'),
    'archivedAt als Zeichenkette': withValue('archivedAt', 'gestern'),
  },
  tag_groups: {
    'name zu lang': withValue('name', long(201)),
    'name fehlt': without('name'),
    'colorIndex negativ': withValue('colorIndex', -1),
    'colorIndex als Zeichenkette': withValue('colorIndex', 'rot'),
    'collapsed als Zahl': withValue('collapsed', 1),
    'archivedAt als Wahrheitswert': withValue('archivedAt', true),
  },
  tags: {
    'name zu lang': withValue('name', long(201)),
    'groupId als Zahl': withValue('groupId', 5),
    'groupId zu lang': withValue('groupId', long(151)),
    'colorIndex als Zeichenkette': withValue('colorIndex', '3'),
    'icon fehlt': without('icon'),
    'sortOrder fehlt': without('sortOrder'),
  },
  scales: {
    'type unbekannt': withValue('type', 'EMOJI'),
    'type klein geschrieben': withValue('type', 'number'),
    'chartMode unbekannt': withValue('chartMode', 'MEDIAN'),
    'trend unbekannt': withValue('trend', 'UP'),
    'min als Zeichenkette': withValue('min', '1'),
    'max fehlt': without('max'),
    'step als Kommazahl': withValue('step', 0.5),
    'name zu lang': withValue('name', long(201)),
    'points ist keine Liste': withValue('points', { 0: { id: 'a', position: 1, label: 'x' } }),
    'points fehlt': without('points'),
    'points mit 101 Einträgen': withValue(
      'points',
      Array.from({ length: 101 }, (_, i) => ({ id: `p${i}`, position: i + 1, label: 'x' })),
    ),
    'points als Zeichenkette': withValue('points', 'schlecht,mittel,gut'),
    'points null': withValue('points', null),
  },
  entries: {
    'Notiz mit 100 001 Zeichen': withValue('note', long(100_001)),
    'Notiz ist keine Zeichenkette': withValue('note', { html: '<p>x</p>' }),
    'Titel mit 501 Zeichen': withValue('title', long(501)),
    'minuteOfDay -1': withValue('minuteOfDay', -1),
    'minuteOfDay 1440': withValue('minuteOfDay', 1440),
    'minuteOfDay als Zeichenkette': withValue('minuteOfDay', '20:15'),
    'localDate im deutschen Format': withValue('localDate', '16.11.2026'),
    'localDate mit Uhrzeit': withValue('localDate', '2026-11-16T20:15'),
    'localDate mit Anhang': withValue('localDate', '2026-11-16\n2026-11-17'),
    'localDate als Zeitstempel': withValue('localDate', Timestamp.fromMillis(1_794_000_000_000)),
    'timestamp als Zeitstempel': withValue('timestamp', Timestamp.fromMillis(1_794_000_000_000)),
    'timestamp fehlt': without('timestamp'),
    'offsetMinutes außerhalb': withValue('offsetMinutes', 5000),
    'moodId fehlt': without('moodId'),
    'moodId leer': withValue('moodId', ''),
    'favorite als Zahl': withValue('favorite', 1),
    'source unbekannt': withValue('source', 'ADMIN'),
    'tagIds ist keine Liste': withValue('tagIds', 'a,b'),
    'tagIds mit 501 Einträgen': withValue('tagIds', Array.from({ length: 501 }, (_, i) => `t${i}`)),
    'tagIds enthält eine Zahl': withValue('tagIds', ['a', 7]),
    'tagIds enthält eine Map': withValue('tagIds', ['a', { id: 'b' }]),
    'tagIds enthält überlange Einträge': withValue('tagIds', [long(5000)]),
    'assetIds enthält null': withValue('assetIds', [null]),
    'assetIds enthält einen Wahrheitswert': withValue('assetIds', ['a', true]),
    'assetIds enthält eine leere Zeichenkette': withValue('assetIds', ['a', '']),
    'assetIds enthält eine Kennung mit Komma': withValue('assetIds', ['a,b']),
    'assetIds mit 501 Einträgen': withValue('assetIds', Array.from({ length: 501 }, (_, i) => `a${i}`)),
    'tagIds enthält null zwischen gültigen Kennungen': withValue('tagIds', ['a', null, 'b']),
    'tagIds enthält eine Kommazahl': withValue('tagIds', [1.5]),
    'assetIds fehlt': without('assetIds'),
    'scaleValues ist keine Liste': withValue('scaleValues', { scaleId: 'a' }),
    'scaleValues mit 201 Einträgen': withValue(
      'scaleValues',
      Array.from({ length: 201 }, (_, i) => ({ scaleId: `s${i}`, number: 1 })),
    ),
    'scaleValues null': withValue('scaleValues', null),
    'scaleValues fehlt': without('scaleValues'),
  },
  assets: {
    'type unbekannt': withValue('type', 'VIDEO'),
    'fileName leer': withValue('fileName', ''),
    'fileName zu lang': withValue('fileName', long(301)),
    'fileName fehlt': without('fileName'),
    'checksum zu lang': withValue('checksum', long(201)),
    'sizeBytes negativ': withValue('sizeBytes', -1),
    'sizeBytes als Zeichenkette': withValue('sizeBytes', '2 MB'),
    'durationMs negativ': withValue('durationMs', -5),
    'takenAt als Zeitstempel': withValue('takenAt', Timestamp.fromMillis(1_794_000_000_000)),
    'Dateiinhalt als zusätzliches Feld': withValue('bytes', 'AAAA'),
  },
  goals: {
    'repeat unbekannt': withValue('repeat', 'MONTHLY'),
    'timesPerWeek 0': withValue('timesPerWeek', 0),
    'timesPerWeek 8': withValue('timesPerWeek', 8),
    'weekdays mit 0': withValue('weekdays', [0, 1]),
    'weekdays mit 8': withValue('weekdays', [8]),
    'weekdays mit Zeichenkette': withValue('weekdays', ['Mo']),
    'weekdays mit mehr als 7 Einträgen': withValue('weekdays', [1, 2, 3, 4, 5, 6, 7, 1]),
    'weekdays ist keine Liste': withValue('weekdays', 5),
    'reminderMinuteOfDay 1440': withValue('reminderMinuteOfDay', 1440),
    'reminderMinuteOfDay negativ': withValue('reminderMinuteOfDay', -1),
    'reminderText zu lang': withValue('reminderText', long(501)),
    'startDate fehlt': without('startDate'),
    'startDate falsches Format': withValue('startDate', '1.11.2026'),
    'endDate falsches Format': withValue('endDate', '2026-12'),
    'note zu lang': withValue('note', long(100_001)),
    'name zu lang': withValue('name', long(201)),
    'tagId als Liste': withValue('tagId', ['a']),
  },
  goal_checks: {
    'id passt nicht zu goalId und date': (d) => ({ ...d, date: '2026-11-17' }),
    'id ohne Trennstrich': (d) => ({ ...d, id: `${d.goalId}${d.date}`, __docId: `${d.goalId}${d.date}` }),
    'frei gewählte id': (d) => ({ ...d, id: 'frei', __docId: 'frei' }),
    'date falsches Format': (d) => ({
      ...d,
      date: '16.11.2026',
      id: `${d.goalId}|16.11.2026`,
      __docId: `${d.goalId}|16.11.2026`,
    }),
    'goalId leer': (d) => ({ ...d, goalId: '', id: `|${d.date}`, __docId: `|${d.date}` }),
    'goalId als Zahl': withValue('goalId', 7),
    'date fehlt': without('date'),
  },
  important_days: {
    'date falsches Format': withValue('date', '4. Mai 2019'),
    'name zu lang': withValue('name', long(201)),
    'category zu lang': withValue('category', long(201)),
    'note zu lang': withValue('note', long(100_001)),
    'yearly als Zeichenkette': withValue('yearly', 'ja'),
    'remindOnDay fehlt': without('remindOnDay'),
    'remindDaysBefore mit Zeichenkette': withValue('remindDaysBefore', ['7']),
    'remindDaysBefore negativ': withValue('remindDaysBefore', [-1]),
    'remindDaysBefore mit 11 Einträgen': withValue('remindDaysBefore', Array.from({ length: 11 }, () => 1)),
    'remindDaysBefore ist keine Liste': withValue('remindDaysBefore', 7),
    'assetId als Zahl': withValue('assetId', 1),
  },
  templates: {
    'title zu lang': withValue('title', long(501)),
    'title fehlt': without('title'),
    'body zu lang': withValue('body', long(100_001)),
    'body als Liste': withValue('body', ['<p>x</p>']),
    'sortOrder als Zeichenkette': withValue('sortOrder', 'erste'),
  },
  reminders: {
    'minuteOfDay -1': withValue('minuteOfDay', -1),
    'minuteOfDay 1440': withValue('minuteOfDay', 1440),
    'minuteOfDay fehlt': without('minuteOfDay'),
    'text zu lang': withValue('text', long(501)),
    'active als Zeichenkette': withValue('active', 'true'),
  },
};

for (const name of COLLECTIONS) {
  rejects(name, { ...COMMON, ...SPECIFIC[name] });
}

describe('Teil-Updates an vorhandenen Dokumenten', () => {
  for (const name of COLLECTIONS) {
    test(`${name}: Update ohne neues syncedAt, mit Fremdfeld, mit geänderter id, Pflichtfeld entfernt`, async () => {
      const data = SAMPLES[name]();
      const ref = doc(alice, 'users', ALICE, name, data.id);
      // Der alte Zeitstempel bliebe stehen und wäre nicht mehr request.time.
      await assertFails(updateDoc(ref, { deleted: true }));
      await assertFails(
        updateDoc(ref, { deleted: true, syncedAt: Timestamp.fromMillis(Date.now() - 5000) }),
      );
      await assertFails(updateDoc(ref, { isAdmin: true, syncedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { id: 'andere', syncedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { updatedAt: deleteField(), syncedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { updatedAt: 'jetzt', syncedAt: serverTimestamp() }));
      await assertFails(setDoc(ref, { dirty: true, syncedAt: serverTimestamp() }, { merge: true }));
    });
  }

  test('entries: übergroße Notiz und ungültige Uhrzeit per Update', async () => {
    const ref = doc(alice, 'users', ALICE, 'entries', SAMPLES.entries().id);
    await assertFails(updateDoc(ref, { note: long(100_001), syncedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { minuteOfDay: 1440, syncedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { tagIds: ['a', 7], syncedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { scaleValues: 'keine Liste', syncedAt: serverTimestamp() }));
  });
});

describe('settings/main: ungültige Inhalte', () => {
  const cases = {
    'unbekanntes Feld': withValue('premium', true),
    'id-Feld gehört nicht hinein': withValue('id', 'main'),
    'syncedAt fehlt': without('syncedAt'),
    'syncedAt gefälscht': withValue('syncedAt', Timestamp.fromMillis(1_000_000)),
    'updatedAt als Zeichenkette': withValue('updatedAt', 'heute'),
    'weekStart 0': withValue('weekStart', 0),
    'weekStart 8': withValue('weekStart', 8),
    'midnightRule unbekannt': withValue('midnightRule', 'NEXT_DAY'),
    'themeMode unbekannt': withValue('themeMode', 'AMOLED'),
    'showQuote als Zeichenkette': withValue('showQuote', 'ja'),
    'showMemories fehlt': without('showMemories'),
    'moodScheme zu lang': withValue('moodScheme', long(51)),
    'fontChoice leer': withValue('fontChoice', ''),
    'customMoodColors mit vier Farben': withValue('customMoodColors', ['FF000000', 'FF000000', 'FF000000', 'FF000000']),
    'customMoodColors mit sechs Farben': withValue('customMoodColors', Array.from({ length: 6 }, () => 'FF000000')),
    'customMoodColors mit Raute': withValue('customMoodColors', ['#FF0000', 'FF000000', 'FF000000', 'FF000000', 'FF000000']),
    'customMoodColors mit Zahl': withValue('customMoodColors', [1, 2, 3, 4, 5]),
    'customMoodColors ist keine Liste': withValue('customMoodColors', 'FF000000'),
    'reportNotifications als Zahl': withValue('reportNotifications', 0),
    'shiftGroupId fehlt': without('shiftGroupId'),
    'shiftGroupId zu lang': withValue('shiftGroupId', long(151)),
    'shiftTimes ist keine Liste': withValue('shiftTimes', 'nacht'),
    'shiftTimes mit 61 Einträgen': withValue('shiftTimes', Array.from({ length: 61 }, () => ({ tagId: 'a' }))),
    'userType unbekannt': withValue('userType', 'PROFI'),
    'detail unbekannt': withValue('detail', 'MAXIMAL'),
    'tone unbekannt': withValue('tone', 'FRECH'),
    'cleverleHints als Zeichenkette': withValue('cleverleHints', 'ja'),
    'startBlocks mit Zahl': withValue('startBlocks', [1, 2]),
    'startBlocks zu viele': withValue('startBlocks', Array.from({ length: 13 }, (_, i) => 'b' + i)),
    'startScaleIds zu viele': withValue('startScaleIds', Array.from({ length: 7 }, (_, i) => 's' + i)),
    'entryOrder ist keine Liste': withValue('entryOrder', 'shift'),
    'modules mit leerem Eintrag': withValue('modules', ['']),
    'voiceAutoApply fehlt': without('voiceAutoApply'),
    'voiceLearn als Zahl': withValue('voiceLearn', 1),
    'keywords ist keine Liste': withValue('keywords', {}),
    'keywords mit 601 Einträgen': withValue('keywords', Array.from({ length: 601 }, () => ({ tagId: 'a' }))),
  };
  for (const [label, mutate] of Object.entries(cases)) {
    test(label, async () => {
      const bad = mutate(sampleSettings());
      await assertFails(setDoc(doc(alice, 'users', ALICE, 'settings', 'main'), bad));
      const fresh = dbOf(env, 'carol-uid');
      await assertFails(setDoc(doc(fresh, 'users', 'carol-uid', 'settings', 'main'), bad));
    });
  }

  test('Update ohne neues syncedAt', async () => {
    await assertFails(updateDoc(doc(alice, 'users', ALICE, 'settings', 'main'), { themeMode: 'DARK' }));
  });
});

describe('Profil users/{uid}: ungültige Inhalte', () => {
  const carolRef = () => doc(dbOf(env, 'carol-uid'), 'users', 'carol-uid');

  test('anlegen: unbekanntes Feld, fehlendes oder gefälschtes createdAt, falsche Typen', async () => {
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), role: 'admin' }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), email: 'carol@example.org' }));
    await assertFails(setDoc(carolRef(), { appVersion: '1.0.0' }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), createdAt: Timestamp.fromMillis(1_000_000) }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), createdAt: 1_794_000_000_000 }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), appVersion: 100 }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), appVersion: long(51) }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), consentVersion: 3 }));
    await assertFails(setDoc(carolRef(), { ...sampleProfile(), consentDiaryAt: 'ja' }));
    await assertFails(
      setDoc(carolRef(), { ...sampleProfile(), consentDiaryAt: Timestamp.fromMillis(4_102_444_800_000) }),
    );
  });

  test('ändern: createdAt bleibt, keine Fremdfelder', async () => {
    const ref = doc(alice, 'users', ALICE);
    await assertFails(updateDoc(ref, { createdAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { createdAt: Timestamp.fromMillis(1_000_000) }));
    await assertFails(updateDoc(ref, { createdAt: deleteField() }));
    await assertFails(updateDoc(ref, { role: 'admin' }));
    await assertFails(setDoc(ref, sampleProfile()));
  });
});
