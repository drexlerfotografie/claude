// Gemeinsame Hilfen für die Regeltests: Testumgebung, Anmeldungen und je
// Sammlung ein gültiges Beispieldokument, abgeleitet aus Model.kt (Modul core).

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { serverTimestamp } from 'firebase/firestore';

export const PROJECT_ID = 'demo-mediclever-tagebuch';

export const ALICE = 'alice-uid';
export const BOB = 'bob-uid';

export async function createTestEnv() {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(port),
      rules: readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8'),
    },
  });
}

/** Firestore-Zugang einer mit E-Mail und Passwort angemeldeten Person. */
export function dbOf(env, uid, provider = 'password') {
  return env
    .authenticatedContext(uid, { firebase: { sign_in_provider: provider, identities: {} } })
    .firestore();
}

export function anonymousDb(env, uid) {
  return dbOf(env, uid, 'anonymous');
}

export function signedOutDb(env) {
  return env.unauthenticatedContext().firestore();
}

const ID = {
  mood: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0001',
  group: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0002',
  tag: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0003',
  scale: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0004',
  point: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0005',
  entry: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0006',
  asset: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0007',
  goal: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0008',
  day: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a0009',
  template: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a000a',
  reminder: '0b9d6c1e-6a52-4a55-9d2e-0e3f6c1a000b',
};

const NOW = 1_794_000_000_000;

const sync = (id) => ({ id, updatedAt: NOW, deleted: false, syncedAt: serverTimestamp() });

/**
 * Je Sammlung eine Funktion, die ein frisches, gültiges Dokument liefert.
 * Wahlfreie Felder sind hier gesetzt; die Tests prüfen zusätzlich die Fassung
 * mit null und die ohne das Feld.
 */
export const SAMPLES = {
  moods: () => ({
    ...sync(ID.mood),
    level: 2,
    name: 'Gut',
    icon: 'mood_good',
    sortOrder: 1,
    archivedAt: null,
  }),
  tag_groups: () => ({
    ...sync(ID.group),
    name: 'Bewegung',
    colorIndex: 3,
    sortOrder: 0,
    collapsed: false,
    archivedAt: null,
  }),
  tags: () => ({
    ...sync(ID.tag),
    groupId: ID.group,
    name: 'Spazieren',
    icon: 'walk',
    colorIndex: null,
    sortOrder: 2,
    archivedAt: null,
  }),
  scales: () => ({
    ...sync(ID.scale),
    type: 'TEXT',
    name: 'Schlafqualität',
    icon: 'bed',
    colorIndex: 4,
    sortOrder: 1,
    chartMode: 'DAILY_AVERAGE',
    trend: 'HIGHER_IS_BETTER',
    min: 1,
    max: 3,
    step: 1,
    points: [
      { id: ID.point, position: 1, label: 'schlecht' },
      { id: ID.point + '-2', position: 2, label: 'mittel' },
      { id: ID.point + '-3', position: 3, label: 'gut' },
    ],
    archivedAt: null,
  }),
  entries: () => ({
    ...sync(ID.entry),
    timestamp: NOW - 3_600_000,
    offsetMinutes: 60,
    localDate: '2026-11-16',
    minuteOfDay: 20 * 60 + 15,
    moodId: ID.mood,
    title: 'Ruhiger Abend',
    note: '<p>Kopfschmerzen am Nachmittag, <b>früh</b> ins Bett.</p>',
    favorite: true,
    source: 'APP',
    tagIds: [ID.tag],
    scaleValues: [
      { scaleId: ID.scale, pointId: ID.point, number: null, minutes: null },
      { scaleId: ID.scale + '-n', pointId: null, number: 7, minutes: null },
      { scaleId: ID.scale + '-d', minutes: 450 },
    ],
    assetIds: [ID.asset],
  }),
  assets: () => ({
    ...sync(ID.asset),
    type: 'PHOTO',
    fileName: ID.asset + '.jpg',
    checksum: 'sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
    takenAt: NOW - 7_200_000,
    sizeBytes: 2_345_678,
    durationMs: 0,
  }),
  goals: () => ({
    ...sync(ID.goal),
    name: 'Dreimal pro Woche spazieren',
    tagId: ID.tag,
    icon: 'flag',
    colorIndex: 2,
    repeat: 'WEEKDAYS',
    timesPerWeek: 3,
    weekdays: [1, 3, 5],
    reminderMinuteOfDay: 18 * 60,
    reminderText: 'Zeit für eine Runde',
    startDate: '2026-11-01',
    endDate: null,
    note: '',
    sortOrder: 0,
    archivedAt: null,
  }),
  goal_checks: () => ({
    ...sync(ID.goal + '|2026-11-16'),
    goalId: ID.goal,
    date: '2026-11-16',
  }),
  important_days: () => ({
    ...sync(ID.day),
    name: 'Jahrestag',
    date: '2019-05-04',
    category: 'Familie',
    note: '',
    assetId: null,
    yearly: true,
    remindOnDay: true,
    remindDaysBefore: [1, 7],
    sortOrder: 0,
  }),
  templates: () => ({
    ...sync(ID.template),
    title: 'Abendrückblick',
    body: '<p>Was war heute gut?</p>',
    sortOrder: 0,
  }),
  reminders: () => ({
    ...sync(ID.reminder),
    minuteOfDay: 21 * 60,
    text: 'Wie war dein Tag?',
    active: true,
  }),
};

/** Wahlfreie Felder je Sammlung: dürfen null sein oder ganz fehlen. */
export const OPTIONAL_FIELDS = {
  moods: ['archivedAt'],
  tag_groups: ['archivedAt'],
  tags: ['groupId', 'colorIndex', 'archivedAt'],
  scales: ['archivedAt'],
  entries: [],
  assets: [],
  goals: ['tagId', 'reminderMinuteOfDay', 'endDate', 'archivedAt'],
  goal_checks: [],
  important_days: ['assetId'],
  templates: [],
  reminders: [],
};

export const COLLECTIONS = Object.keys(SAMPLES);

export const sampleSettings = () => ({
  weekStart: 1,
  midnightRule: 'ASK',
  showQuote: true,
  showMemories: true,
  themeMode: 'SYSTEM',
  moodScheme: 'klar',
  customMoodColors: [],
  fontChoice: 'roboto',
  reportNotifications: true,
  shiftGroupId: '',
  shiftTimes: [{ tagId: 'tag-1', short: 'Nacht', startMinute: 1140, endMinute: 420, free: false }],
  userType: 'AUSGEWOGEN',
  detail: 'NORMAL',
  tone: 'FREUNDLICH',
  cleverleHints: true,
  startBlocks: ['scales', 'matrix', 'line'],
  startScaleIds: [],
  entryOrder: ['shift', 'scales', 'tags', 'note'],
  modules: ['schicht'],
  voiceAutoApply: false,
  voiceLearn: true,
  keywords: [{ tagId: 'tag-1', words: ['laufen'], blocked: [] }],
  updatedAt: NOW,
  syncedAt: serverTimestamp(),
});

export const sampleProfile = () => ({
  createdAt: serverTimestamp(),
  consentDiaryAt: serverTimestamp(),
  consentVersion: '2026-11',
  appVersion: '1.0.0',
});
