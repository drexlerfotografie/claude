// Vergleicht zwei CSV-Exporte zeilenweise: Datum, Uhrzeit, Stimmung, Aktivitäten und Skalenwerte.
// Gibt nur Zählwerte und Zeilennummern aus, keine Inhalte.
// Aufruf: node scripts/csv-vergleich.mjs private/export.csv tagebuch.csv
import { readFileSync } from 'node:fs';

function parse(text) {
  const rows = []; let row = []; let cell = ''; let q = false;
  const t = text.replace(/^﻿/, '');
  for (let i = 0; i < t.length; i++) {
    const c = t[i];
    if (q) { if (c === '"' && t[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') q = false; else cell += c; }
    else if (c === '"') q = true;
    else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && t[i + 1] === '\n') i++; row.push(cell); cell = ''; if (row.length > 1 || row[0] !== '') rows.push(row); row = []; }
    else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  const head = rows.shift().map((h) => h.trim());
  return rows.map((r) => Object.fromEntries(head.map((h, i) => [h, r[i] ?? ''])));
}
const time = (s) => { const m = /^(\d{1,2})[:.](\d{2})\s*([AaPp])?/.exec(s.trim()); if (!m) return s.trim(); let h = Number(m[1]); if (m[3]) h = (h % 12) + (m[3].toLowerCase() === 'p' ? 12 : 0); return `${String(h).padStart(2, '0')}:${m[2]}`; };
const set = (s) => s.split('|').map((x) => x.trim().replace(/\s+/g, ' ')).filter(Boolean).sort().join(' | ');
const key = (r) => [r.full_date.slice(0, 10), time(r.time), r.mood.trim(), set(r.activities), set(r.scales || '')].join(' ¦ ');

const [a, b] = process.argv.slice(2).map((f) => parse(readFileSync(f, 'utf8')));
const sort = (rows) => rows.map(key).sort();
const A = sort(a), B = sort(b);
let same = 0; const diff = [];
for (let i = 0; i < Math.max(A.length, B.length); i++) { if (A[i] === B[i]) same++; else diff.push(i + 1); }
const fields = ['Datum', 'Uhrzeit', 'Stimmung', 'Aktivitäten', 'Skalenwerte'];
const byField = fields.map((_, f) => { let n = 0; for (let i = 0; i < Math.min(A.length, B.length); i++) if (A[i].split(' ¦ ')[f] !== B[i].split(' ¦ ')[f]) n++; return n; });
console.log(`Zeilen: ${A.length} / ${B.length}`);
console.log(`gleich: ${same}, abweichend: ${diff.length}`);
fields.forEach((f, i) => console.log(`  Abweichungen ${f}: ${byField[i]}`));
if (diff.length) console.log(`erste abweichende Positionen (sortiert): ${diff.slice(0, 20).join(', ')}`);
process.exit(diff.length || A.length !== B.length ? 1 : 0);
