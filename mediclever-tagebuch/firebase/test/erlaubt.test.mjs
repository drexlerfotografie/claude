// Erlaubte Wege: Was die App und später die Website tun, muss durchgehen.

import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
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
  COLLECTIONS,
  OPTIONAL_FIELDS,
  SAMPLES,
  createTestEnv,
  dbOf,
  sampleProfile,
  sampleSettings,
} from './helpers.mjs';

let env;
let alice;

before(async () => {
  env = await createTestEnv();
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  alice = dbOf(env, ALICE);
});

for (const name of COLLECTIONS) {
  describe(`${name}: eigene Daten`, () => {
    const ref = (data) => doc(alice, 'users', ALICE, name, data.id);

    test('anlegen, lesen, ändern, als gelöscht markieren, löschen', async () => {
      const data = SAMPLES[name]();
      await assertSucceeds(setDoc(ref(data), data));

      const snap = await assertSucceeds(getDoc(ref(data)));
      assert.equal(snap.data().id, data.id);
      assert.ok(snap.data().syncedAt instanceof Timestamp, 'syncedAt ist ein Server-Zeitstempel');

      // Vollständiges Überschreiben, wie es der Abgleich macht.
      await assertSucceeds(setDoc(ref(data), { ...SAMPLES[name](), updatedAt: data.updatedAt + 1 }));

      // Löschmarke als Teil-Update.
      await assertSucceeds(
        updateDoc(ref(data), {
          deleted: true,
          updatedAt: data.updatedAt + 2,
          syncedAt: serverTimestamp(),
        }),
      );

      // Echtes Löschen, nötig für "Konto löschen".
      await assertSucceeds(deleteDoc(ref(data)));
    });

    test('Abholen neuer Änderungen: where syncedAt > Marke, orderBy syncedAt', async () => {
      const data = SAMPLES[name]();
      await setDoc(ref(data), data);
      const pull = query(
        collection(alice, 'users', ALICE, name),
        where('syncedAt', '>', Timestamp.fromMillis(0)),
        orderBy('syncedAt'),
      );
      const result = await assertSucceeds(getDocs(pull));
      assert.equal(result.size, 1);
      assert.equal(result.docs[0].id, data.id);

      // Mit der Marke des letzten Dokuments kommt nichts mehr.
      const cursor = result.docs[0].data().syncedAt;
      const next = await assertSucceeds(
        getDocs(
          query(
            collection(alice, 'users', ALICE, name),
            where('syncedAt', '>', cursor),
            orderBy('syncedAt'),
          ),
        ),
      );
      assert.equal(next.size, 0);
    });

    for (const field of OPTIONAL_FIELDS[name]) {
      test(`wahlfreies Feld ${field}: gesetzt, null oder weggelassen`, async () => {
        const withNull = { ...SAMPLES[name](), [field]: null };
        await assertSucceeds(setDoc(ref(withNull), withNull));

        const without = SAMPLES[name]();
        delete without[field];
        await assertSucceeds(setDoc(ref(without), without));
      });
    }
  });
}

describe('Besonderheiten einzelner Sammlungen', () => {
  test('moods: Stufen 1 und 5 sind erlaubt', async () => {
    for (const level of [1, 5]) {
      const data = { ...SAMPLES.moods(), level };
      await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'moods', data.id), data));
    }
  });

  test('entries: Grenzen 00:00 und 23:59, leere Listen, lange Notiz', async () => {
    for (const minuteOfDay of [0, 1439]) {
      const data = {
        ...SAMPLES.entries(),
        minuteOfDay,
        tagIds: [],
        scaleValues: [],
        assetIds: [],
        note: 'x'.repeat(100_000),
        title: 't'.repeat(500),
      };
      await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'entries', data.id), data));
    }
  });

  // Die folgenden Tests schreiben jeweils das größte erlaubte Dokument. Sie
  // sichern zugleich ab, dass die Regeln damit unter der Grenze von 1000
  // ausgewerteten Ausdrücken je Anfrage bleiben.
  test('entries: 500 Aktivitäten, 500 Anhänge, 200 Skalenwerte, volle Notiz', async () => {
    const ids = (prefix) =>
      Array.from({ length: 500 }, (_, i) => `${prefix}b9d6c1e-6a52-4a55-9d2e-${String(i).padStart(12, '0')}`);
    const data = {
      ...SAMPLES.entries(),
      note: 'x'.repeat(100_000),
      title: 't'.repeat(500),
      tagIds: ids('a'),
      assetIds: ids('b'),
      scaleValues: Array.from({ length: 200 }, (_, i) => ({
        scaleId: `0b9d6c1e-6a52-4a55-9d2e-${String(i).padStart(12, '0')}`,
        pointId: null,
        number: i,
        minutes: null,
      })),
    };
    await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'entries', data.id), data));
  });

  test('entries: Kennungen, die wie Zahlen aussehen, bleiben Zeichenketten', async () => {
    const data = { ...SAMPLES.entries(), tagIds: ['7', '8', 'null', 'true'] };
    await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'entries', data.id), data));
  });

  test('scales: 100 benannte Punkte, alle Typen, archiviert', async () => {
    for (const type of ['NUMBER', 'TEXT', 'DURATION']) {
      const data = {
        ...SAMPLES.scales(),
        type,
        archivedAt: 1_794_000_000_000,
        points: Array.from({ length: 100 }, (_, i) => ({
          id: `punkt-${i}`,
          position: i + 1,
          label: `Punkt ${i + 1}`,
        })),
      };
      await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'scales', data.id), data));
    }
  });

  test('alle wahlfreien Felder gesetzt und alle Listen voll', async () => {
    const full = {
      moods: { archivedAt: 1_794_000_000_000 },
      tag_groups: { archivedAt: 1_794_000_000_000 },
      tags: { groupId: 'gruppe', colorIndex: 7, archivedAt: 1_794_000_000_000 },
      goals: {
        tagId: 'aktivitaet',
        reminderMinuteOfDay: 1439,
        endDate: '2027-12-31',
        archivedAt: 1_794_000_000_000,
        weekdays: [1, 2, 3, 4, 5, 6, 7],
        timesPerWeek: 7,
        note: 'x'.repeat(100_000),
        reminderText: 'x'.repeat(500),
        name: 'x'.repeat(200),
      },
      important_days: {
        assetId: 'bild',
        remindDaysBefore: [0, 1, 2, 3, 7, 14, 30, 90, 180, 366],
        note: 'x'.repeat(100_000),
      },
    };
    for (const [name, extra] of Object.entries(full)) {
      const data = { ...SAMPLES[name](), ...extra };
      await assertSucceeds(setDoc(doc(alice, 'users', ALICE, name, data.id), data));
    }
  });

  test('goals: alle Wiederholungsarten', async () => {
    for (const repeat of ['DAILY', 'TIMES_PER_WEEK', 'WEEKDAYS']) {
      const data = { ...SAMPLES.goals(), repeat, weekdays: repeat === 'WEEKDAYS' ? [1, 7] : [] };
      await assertSucceeds(setDoc(doc(alice, 'users', ALICE, 'goals', data.id), data));
    }
  });

  test('mehrere Sammlungen in einem Stapel schreiben', async () => {
    const batch = writeBatch(alice);
    for (const name of COLLECTIONS) {
      const data = SAMPLES[name]();
      batch.set(doc(alice, 'users', ALICE, name, data.id), data);
    }
    await assertSucceeds(batch.commit());
  });

  test('Anmeldung über Google gilt wie E-Mail und Passwort', async () => {
    const google = dbOf(env, ALICE, 'google.com');
    const data = SAMPLES.entries();
    await assertSucceeds(setDoc(doc(google, 'users', ALICE, 'entries', data.id), data));
    await assertSucceeds(getDoc(doc(google, 'users', ALICE, 'entries', data.id)));
  });
});

describe('settings/main', () => {
  const ref = () => doc(alice, 'users', ALICE, 'settings', 'main');

  test('anlegen, lesen, ändern, löschen', async () => {
    await assertSucceeds(setDoc(ref(), sampleSettings()));
    await assertSucceeds(getDoc(ref()));
    await assertSucceeds(
      updateDoc(ref(), { themeMode: 'DARK', updatedAt: 1_794_000_000_001, syncedAt: serverTimestamp() }),
    );
    await assertSucceeds(deleteDoc(ref()));
  });

  test('eigene Stimmungsfarben: genau fünf ARGB-Werte', async () => {
    await assertSucceeds(
      setDoc(ref(), {
        ...sampleSettings(),
        moodScheme: 'custom',
        customMoodColors: ['FF2E7D32', 'FF7CB342', 'FFFDD835', 'FFFB8C00', 'ffe53935'],
      }),
    );
  });
});

describe('Profil users/{uid}', () => {
  const ref = () => doc(alice, 'users', ALICE);

  test('anlegen, lesen, App-Version nachtragen, löschen', async () => {
    await assertSucceeds(setDoc(ref(), sampleProfile()));
    const snap = await assertSucceeds(getDoc(ref()));
    assert.ok(snap.data().createdAt instanceof Timestamp);
    await assertSucceeds(updateDoc(ref(), { appVersion: '1.0.1' }));
    await assertSucceeds(setDoc(ref(), { appVersion: '1.0.2' }, { merge: true }));
    await assertSucceeds(deleteDoc(ref()));
  });

  test('anlegen nur mit createdAt, Einwilligung später nachtragen und wieder entfernen', async () => {
    await assertSucceeds(setDoc(ref(), { createdAt: serverTimestamp() }));
    await assertSucceeds(
      updateDoc(ref(), { consentDiaryAt: serverTimestamp(), consentVersion: '2026-11' }),
    );
    await assertSucceeds(updateDoc(ref(), { consentDiaryAt: null, consentVersion: deleteField() }));
  });
});
