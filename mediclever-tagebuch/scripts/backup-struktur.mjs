// Untersucht eine Backup-Datei und gibt NUR Struktur und Zählwerte aus – keine Inhalte, keine Namen, keine Notizen.
// Aufruf: node scripts/backup-struktur.mjs private/backup.daylio
import { openSync, readSync, fstatSync, closeSync } from 'node:fs';
import { inflateRawSync } from 'node:zlib';

const file = process.argv[2];
if (!file) { console.error('Aufruf: node scripts/backup-struktur.mjs <Datei>'); process.exit(2); }
const fd = openSync(file, 'r');
const size = fstatSync(fd).size;
const read = (pos, len) => { const b = Buffer.alloc(len); readSync(fd, b, 0, len, pos); return b; };

const tailLen = Math.min(size, 65557);
const tail = read(size - tailLen, tailLen);
let eocd = -1;
for (let i = tailLen - 22; i >= 0; i--) if (tail.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
if (eocd < 0) { console.error('Kein ZIP-Archiv.'); process.exit(1); }
const count = tail.readUInt16LE(eocd + 10);
const cd = read(tail.readUInt32LE(eocd + 16), tail.readUInt32LE(eocd + 12));
const entries = [];
for (let p = 0, i = 0; i < count; i++) {
  const n = cd.readUInt16LE(p + 28);
  entries.push({ name: cd.subarray(p + 46, p + 46 + n).toString('utf8'), method: cd.readUInt16LE(p + 10), comp: cd.readUInt32LE(p + 20), size: cd.readUInt32LE(p + 24), offset: cd.readUInt32LE(p + 42) });
  p += 46 + n + cd.readUInt16LE(p + 30) + cd.readUInt16LE(p + 32);
}
const data = (e) => { const h = read(e.offset, 30); const raw = read(e.offset + 30 + h.readUInt16LE(26) + h.readUInt16LE(28), e.comp); return e.method === 8 ? inflateRawSync(raw) : raw; };

const main = entries.find((e) => /backup\.(daylio|json)$/.test(e.name));
if (!main) { console.error('Keine Sicherung im Archiv.'); process.exit(1); }
const text = data(main).toString('utf8');
const json = JSON.parse(main.name.endsWith('.json') ? text : Buffer.from(text, 'base64').toString('utf8'));
closeSync(fd);

const out = [];
const say = (...a) => out.push(a.join(' '));
const photos = entries.filter((e) => e !== main);
say(`Archiv: ${entries.length} Dateien, ${(size / 1048576).toFixed(1)} MB, JSON entpackt ${(Buffer.byteLength(JSON.stringify(json)) / 1048576).toFixed(2)} MB`);
say(`Fotos im Archiv: ${photos.length}, zusammen ${(photos.reduce((s, e) => s + e.size, 0) / 1048576).toFixed(1)} MB`);
say(`Dateinamen mit führendem Schrägstrich: ${photos.filter((e) => e.name.startsWith('/')).length}, mit „..“: ${photos.filter((e) => e.name.includes('..')).length}`);
say(`Formatversion: ${json.version}, Plattform: ${json.metadata?.platform ?? '?'}`);

say('\n== Listen und Felder (Typ, keine Werte) ==');
const typeOf = (v) => (v === null ? 'null' : Array.isArray(v) ? 'Liste' : typeof v);
for (const [k, v] of Object.entries(json)) {
  if (Array.isArray(v)) {
    const keys = new Map();
    for (const item of v) if (item && typeof item === 'object') for (const [f, x] of Object.entries(item)) { const t = keys.get(f) || new Set(); t.add(typeOf(x)); keys.set(f, t); }
    say(`${k}: ${v.length}` + (keys.size ? `  { ${[...keys].map(([f, t]) => `${f}: ${[...t].join('|')}`).join(', ')} }` : ''));
  } else say(`${k}: ${typeOf(v)}${v && typeof v === 'object' ? ` { ${Object.keys(v).join(', ')} }` : ''}`);
}

const E = json.dayEntries || [];
const hist = (arr) => { const m = new Map(); for (const x of arr) m.set(x, (m.get(x) || 0) + 1); return [...m].sort((a, b) => (a[0] > b[0] ? 1 : -1)).map(([k, n]) => `${k}: ${n}`).join(', '); };
say('\n== Einträge ==');
say(`gesamt: ${E.length}`);
const moodLevel = new Map((json.customMoods || []).map((m) => [m.id, m.mood_group_id]));
say(`je Stufe 1–5: ${[1, 2, 3, 4, 5].map((l) => E.filter((e) => moodLevel.get(e.mood) === l).length).join(' / ')}`);
say(`mit Notiz: ${E.filter((e) => (e.note || '').length > 0).length}, mit Titel: ${E.filter((e) => (e.note_title || '').length > 0).length}`);
const favKey = ['isFavorite', 'is_favorite', 'favorite'].find((k) => E.some((e) => k in e));
say(`Favoriten (Feld ${favKey ?? 'nicht gefunden'}): ${favKey ? E.filter((e) => e[favKey]).length : '?'}`);
say(`mit Fotos: ${E.filter((e) => (e.assets || []).length).length}, Zuordnungen: ${E.reduce((s, e) => s + (e.assets || []).length, 0)}, max. Fotos je Eintrag: ${Math.max(0, ...E.map((e) => (e.assets || []).length))}`);
say(`Monatswerte (Feld month): ${hist(E.map((e) => e.month))}`);
say(`Zeitzonen-Versatz in Stunden: ${hist(E.map((e) => (e.timeZoneOffset ?? 0) / 3600000))}`);
let agree = 0;
for (const e of E) { const d = new Date(e.datetime + (e.timeZoneOffset || 0)); if (d.getUTCFullYear() === e.year && d.getUTCMonth() === e.month && d.getUTCDate() === e.day && d.getUTCHours() === e.hour) agree++; }
say(`datetime + timeZoneOffset stimmt mit year/month(ab 0)/day/hour überein: ${agree} von ${E.length}`);
const dates = E.map((e) => new Date(e.datetime + (e.timeZoneOffset || 0)).toISOString().slice(0, 10)).sort();
say(`frühester / spätester Eintrag: ${dates[0]} / ${dates[dates.length - 1]}`);
const perDay = new Map(); for (const d of dates) perDay.set(d, (perDay.get(d) || 0) + 1);
say(`max. Einträge pro Tag: ${Math.max(0, ...perDay.values())}`);
say(`Notizen mit HTML: ${E.filter((e) => /<[a-z]/i.test(e.note || '')).length}, mit &nbsp;: ${E.filter((e) => /&nbsp;| /.test(e.note || '')).length}`);
const scaleKey = E.length ? Object.keys(E.find((e) => Object.values(e).some((v) => Array.isArray(v) && v[0] && typeof v[0] === 'object')) || {}).find((k) => Array.isArray(E.find((e) => Array.isArray(e[k]) && e[k][0] && typeof e[k][0] === 'object')?.[k])) : null;
if (scaleKey) {
  const vals = E.flatMap((e) => e[scaleKey] || []);
  say(`Skalenwerte (Feld ${scaleKey}): ${vals.length}, Felder: ${[...new Set(vals.flatMap((v) => Object.keys(v)))].join(', ')}`);
  const tv = vals.map((v) => v.time_value).filter((v) => typeof v === 'number' && v > 0);
  if (tv.length) say(`Zeitwerte: ${tv.length}, kleinster ${Math.min(...tv)}, größter ${Math.max(...tv)} → Einheit vermutlich ${Math.max(...tv) > 6000000 ? 'Millisekunden' : Math.max(...tv) > 6000 ? 'Sekunden' : 'Minuten'}`);
}

say('\n== Einrichtung ==');
say(`Stimmungen: ${(json.customMoods || []).length} (Stufen: ${hist((json.customMoods || []).map((m) => m.mood_group_id))}; state: ${hist((json.customMoods || []).map((m) => m.state))})`);
say(`Gruppen: ${(json.tag_groups || []).length}`);
say(`Aktivitäten: ${(json.tags || []).length} (state: ${hist((json.tags || []).map((t) => t.state))}); Namen mit Leerzeichen am Rand: ${(json.tags || []).filter((t) => t.name !== (t.name || '').trim()).length}`);
say(`Skalen: ${(json.scales || []).length}`);
for (const s of json.scales || []) say(`  Skala: Felder ${Object.keys(s).join(', ')}; ${Object.entries(s).filter(([, v]) => Array.isArray(v)).map(([k, v]) => `${k}: ${v.length} Punkte`).join(', ') || 'keine Punkte'}`);
say(`Ziele: ${(json.goals || []).length} (state: ${hist((json.goals || []).map((g) => g.state))}; repeat_type/value: ${hist((json.goals || []).map((g) => `${g.repeat_type}/${g.repeat_value}`))}; mit Aktivität: ${(json.goals || []).filter((g) => (g.id_tag ?? 0) > 0).length})`);
const G = json.goalEntries || [];
say(`Abgehakte Ziel-Tage: ${G.length}; Monatswerte: ${hist(G.map((g) => g.month))}`);
say(`goalSuccessWeeks: ${(json.goalSuccessWeeks || []).length}${(json.goalSuccessWeeks || [])[0] ? ` { ${Object.keys(json.goalSuccessWeeks[0]).join(', ')} }` : ''}`);
say(`Wichtige Tage: ${(json.milestones || []).length}; Monatswerte: ${hist((json.milestones || []).map((m) => m.month))}`);
say(`Vorlagen: ${(json.writingTemplates || []).length}, Erinnerungen: ${(json.reminders || []).length}, Einstellungen: ${Array.isArray(json.prefs) ? json.prefs.length : Object.keys(json.prefs || {}).length}`);
const A = json.assets || [];
const names = new Set(photos.map((e) => e.name.split('/').pop()));
say(`Dateien laut JSON: ${A.length}, davon im Archiv vorhanden: ${A.filter((a) => names.has(String(a.checksum))).length}`);

const soll = { 'Einträge gesamt': [E.length, 1765], 'Einträge mit Notiz': [E.filter((e) => (e.note || '').length > 0).length, 375], 'Fotos': [A.length, 1751],
  'Zuordnungen Foto–Eintrag': [E.reduce((s, e) => s + (e.assets || []).length, 0), 1743], 'Aktivitäten': [(json.tags || []).length, 138], 'Gruppen': [(json.tag_groups || []).length, 14],
  'Skalen': [(json.scales || []).length, 10], 'Ziele': [(json.goals || []).length, 15], 'Abgehakte Tage': [G.length, 1553], 'Wichtige Tage': [(json.milestones || []).length, 12],
  'Vorlagen': [(json.writingTemplates || []).length, 11], 'Erinnerungen': [(json.reminders || []).length, 2] };
say('\n== Abgleich mit der Projektbeschreibung (Ist / Soll) ==');
for (const [k, [ist, s]] of Object.entries(soll)) say(`${ist === s ? 'gleich   ' : 'ABWEICHUNG'} ${k}: ${ist} / ${s}`);
console.log(out.join('\n'));
