import type { JSX } from 'preact';
import { useRef, useState } from 'preact/hooks';
import { get, post, downloadUrl, ApiError } from './api';
import { useStore, changed, fail, feature } from './store';
import { Icon } from './icons';
import { Card, Field, Page, Segmented, ask } from './ui';
import { LEVEL_NAMES, plural } from './format';
import { parseCsv, readZip, sha256, zipBlob, type ZipEntry } from './zip';
import type { ImportResult } from './types';

type Json = Record<string, unknown>;
interface Backup { file: File; entries: ZipEntry[]; json: Json; runKey: string; own: boolean }
interface Line { label: string; created: number; skipped: number; errors: number }
interface Progress { phase: string; done: number; total: number }

const list = (j: Json, k: string): Json[] => (Array.isArray(j[k]) ? (j[k] as Json[]) : []);

/** Öffnet die Datei lokal, entpackt das JSON und liefert alles für die Vorschau. */
async function openBackup(file: File): Promise<Backup> {
  const entries = await readZip(file);
  const main = entries.find((e) => e.base === 'backup.daylio') || entries.find((e) => e.base === 'backup.json');
  if (!main) throw new Error('In dieser Datei steckt keine Sicherung.');
  const text = await (await zipBlob(file, main)).text();
  const own = main.base === 'backup.json';
  let json: Json;
  try {
    json = JSON.parse(own ? text : new TextDecoder().decode(Uint8Array.from(atob(text.replace(/\s/g, '')), (c) => c.charCodeAt(0))));
  } catch { throw new Error('Die Sicherung lässt sich nicht lesen.'); }
  return { file, entries, json, runKey: (await sha256(text)).slice(0, 40), own };
}

const sum = (a: Line, r: ImportResult): Line => ({ ...a, created: a.created + r.created, skipped: a.skipped + r.skipped, errors: a.errors + r.errors });

function ImportBackup({ b, onDone }: { b: Backup; onDone: () => void }): JSX.Element {
  const [mode, setMode] = useState<'new' | 'merge'>('new');
  const [progress, setProgress] = useState<Progress | null>(null);
  const [report, setReport] = useState<{ lines: Line[]; warnings: string[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paused, setPaused] = useState(false);
  const pauseRef = useRef(false);
  const j = b.json;
  const assets = list(j, 'assets');
  const files = new Map(b.entries.map((e) => [e.base, e]));
  const present = assets.filter((a) => files.has(String(a.checksum))).length;
  const tags = list(j, 'tags');

  const wait = async () => { while (pauseRef.current) await new Promise((r) => setTimeout(r, 300)); };

  const run = async (confirmWipe = false): Promise<void> => {
    setError(null);
    try {
      const start = await post<{ needs_confirm: boolean; message?: string }>('/import/start', { run_key: b.runKey, mode, confirm_wipe: confirmWipe });
      if (start.needs_confirm) {
        const k = await ask({ title: 'Vorhandene Einträge löschen?', text: `${start.message} Wenn du sie behalten willst, wähle „Zusammenführen“.`,
          buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, { key: 'yes', label: 'Löschen und importieren', kind: 'danger' }] });
        if (k === 'yes') return run(true);
        return;
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Fehler');
      return;
    }
    const lines: Line[] = [];
    const warnings: string[] = [];
    try {
      setProgress({ phase: 'Einrichtung', done: 0, total: 1 });
      const { dayEntries: _d, goalEntries: _g, assets: _a, ...setup } = j;
      const rep = await post<Record<string, ImportResult>>('/import/setup', { run_key: b.runKey, data: setup });
      const names: Record<string, string> = { moods: 'Stimmungen', groups: 'Gruppen', tags: 'Aktivitäten', scales: 'Skalen', goals: 'Ziele', milestones: 'Wichtige Tage', templates: 'Schreibvorlagen', reminders: 'Erinnerungen', prefs: 'Einstellungen' };
      for (const [k, r] of Object.entries(rep)) { lines.push({ label: names[k] || k, created: r.created, skipped: r.skipped, errors: r.errors }); warnings.push(...r.warnings); }

      const entries = list(j, 'dayEntries');
      let maxTime = 0;
      for (const e of entries) for (const v of Object.values(e)) if (Array.isArray(v)) for (const x of v as Json[]) if (x && typeof x === 'object' && typeof x.time_value === 'number') maxTime = Math.max(maxTime, x.time_value);
      const divisor = maxTime > 6000000 ? 60000 : maxTime > 6000 ? 60 : 1;
      let line: Line = { label: 'Einträge', created: 0, skipped: 0, errors: 0 };
      for (let i = 0; i < entries.length; i += 100) {
        await wait();
        const r = await post<ImportResult>('/import/entries', { run_key: b.runKey, items: entries.slice(i, i + 100), time_divisor: divisor });
        line = sum(line, r);
        warnings.push(...r.warnings);
        setProgress({ phase: 'Einträge', done: Math.min(entries.length, i + 100), total: entries.length });
      }
      lines.push(line);

      const checks = list(j, 'goalEntries');
      // Zählt der Monat ab 0 oder ab 1? Eine 0 belegt „ab 0“, eine 12 belegt „ab 1“. Ohne Beleg gilt „ab 1“.
      const months = checks.map((c) => c.month).filter((m) => typeof m === 'number') as number[];
      const base = months.includes(0) && !months.includes(12) ? 0 : 1;
      if (months.length && !months.includes(0) && !months.includes(12)) warnings.push('Ziel-Haken: Die Monatszählung ließ sich nicht eindeutig belegen, angenommen wurde Januar = 1.');
      line = { label: 'Abgehakte Ziel-Tage', created: 0, skipped: 0, errors: 0 };
      for (let i = 0; i < checks.length; i += 500) {
        await wait();
        line = sum(line, await post<ImportResult>('/import/checks', { run_key: b.runKey, items: checks.slice(i, i + 500), month_base: base }));
        setProgress({ phase: 'Ziele', done: Math.min(checks.length, i + 500), total: checks.length });
      }
      lines.push(line);

      const status = await get<{ assets_done: string[] }>('/import/status', { run_key: b.runKey });
      const done = new Set(status.assets_done);
      line = { label: 'Fotos', created: 0, skipped: 0, errors: 0 };
      let n = 0;
      for (const a of assets) {
        await wait();
        n++;
        const entry = files.get(String(a.checksum));
        if (done.has(String(a.id))) line.skipped++;
        else if (!entry) line.errors++;
        else {
          const fd = new FormData();
          fd.append('run_key', b.runKey);
          fd.append('source_id', String(a.id));
          fd.append('checksum', String(a.checksum));
          const ms = typeof a.createdAt === 'number' ? a.createdAt + (typeof a.createdAtOffset === 'number' ? a.createdAtOffset : 0) : null;
          fd.append('taken_at', ms ? new Date(ms).toISOString().slice(0, 19).replace('T', ' ') : String(a.taken_at || ''));
          fd.append('file', await zipBlob(b.file, entry), 'foto');
          let ok = false;
          for (let attempt = 0; attempt < 3 && !ok; attempt++) {
            try { const r = await post<{ skipped: boolean }>('/import/asset', fd); if (r.skipped) line.skipped++; else line.created++; ok = true; } catch (e) {
              if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 429) break;
              await new Promise((r) => setTimeout(r, 1500 * (attempt + 1)));
            }
          }
          if (!ok) line.errors++;
        }
        if (n % 3 === 0 || n === assets.length) setProgress({ phase: 'Fotos', done: n, total: assets.length });
      }
      lines.push(line);
      setProgress(null);
      setReport({ lines, warnings: [...new Set(warnings)] });
      await changed();
    } catch (e) {
      setProgress(null);
      setError((e instanceof Error ? e.message : 'Fehler') + ' Der Import lässt sich mit derselben Datei fortsetzen, ohne dass Doppelte entstehen.');
    }
  };

  if (report) {
    const total = report.lines.reduce((a, l) => ({ c: a.c + l.created, s: a.s + l.skipped, e: a.e + l.errors }), { c: 0, s: 0, e: 0 });
    return (
      <Card title="Importbericht">
        <p role="status"><strong>{total.c}</strong> übernommen, <strong>{total.s}</strong> übersprungen, <strong>{total.e}</strong> Fehler.</p>
        <table class="mct-table">
          <thead><tr><th scope="col">Bereich</th><th scope="col">Übernommen</th><th scope="col">Übersprungen</th><th scope="col">Fehler</th></tr></thead>
          <tbody>{report.lines.map((l) => <tr key={l.label}><th scope="row">{l.label}</th><td>{l.created}</td><td>{l.skipped}</td><td>{l.errors}</td></tr>)}</tbody>
        </table>
        {report.warnings.length > 0 && <><h3>Hinweise</h3><ul class="mct-hints">{report.warnings.slice(0, 20).map((w) => <li key={w}>{w}</li>)}</ul></>}
        <p class="mct-muted small">Übersprungen heißt: schon vorhanden. Symbole wurden über den Namen zugeordnet, sonst neutral gesetzt. Erfolge berechnet die App aus den Einträgen neu.</p>
        <div class="mct-btnrow"><a class="mct-btn primary" href="#/eintraege">Zu den Einträgen</a><button type="button" class="mct-btn ghost" onClick={onDone}>Weitere Datei</button></div>
      </Card>
    );
  }
  if (progress) {
    const pct = progress.total ? Math.round((progress.done / progress.total) * 100) : 0;
    return (
      <Card title="Import läuft">
        <p role="status" aria-live="polite"><strong>{progress.phase}</strong>: {progress.done} von {progress.total}</p>
        <progress max={100} value={pct} aria-label={`Fortschritt ${progress.phase}`} />
        <p class="mct-muted small">Lass diese Seite geöffnet. Du kannst pausieren und später mit derselben Datei fortsetzen.</p>
        <button type="button" class="mct-btn ghost" onClick={() => { pauseRef.current = !paused; setPaused(!paused); }}>{paused ? 'Fortsetzen' : 'Pause'}</button>
      </Card>
    );
  }
  return (
    <Card title="Vorschau">
      <p class="mct-muted">{b.own ? 'MediClever-Sicherung' : 'Sicherung aus anderer App'} · {b.file.name}</p>
      <table class="mct-table">
        <tbody>
          <tr><th scope="row">Einträge</th><td>{list(j, 'dayEntries').length}</td></tr>
          <tr><th scope="row">Stimmungen</th><td>{list(j, 'customMoods').length}</td></tr>
          <tr><th scope="row">Gruppen / Aktivitäten</th><td>{list(j, 'tag_groups').length} / {tags.length} (davon {tags.filter((t) => t.state === 1).length} archiviert)</td></tr>
          <tr><th scope="row">Skalen</th><td>{list(j, 'scales').length}</td></tr>
          <tr><th scope="row">Ziele / abgehakte Tage</th><td>{list(j, 'goals').length} / {list(j, 'goalEntries').length}</td></tr>
          <tr><th scope="row">Wichtige Tage</th><td>{list(j, 'milestones').length}</td></tr>
          <tr><th scope="row">Vorlagen / Erinnerungen</th><td>{list(j, 'writingTemplates').length} / {list(j, 'reminders').length}</td></tr>
          <tr><th scope="row">Fotos</th><td>{assets.length} ({present} in der Datei gefunden)</td></tr>
        </tbody>
      </table>
      <span class="mct-field-label">Wohin importieren?</span>
      <Segmented label="Importziel" value={mode} onChange={setMode} options={[{ value: 'new', label: 'In ein leeres Tagebuch' }, { value: 'merge', label: 'Zusammenführen' }]} />
      <p class="mct-muted small">{mode === 'new' ? 'Vorhandene Einträge und Einrichtung werden vorher gelöscht. Du wirst noch einmal gefragt.' : 'Gleichnamige Stimmungen, Gruppen, Aktivitäten und Skalen werden wiederverwendet. Einträge kommen hinzu.'}</p>
      {error && <p class="mct-error" role="alert">{error}</p>}
      <div class="mct-btnrow"><button type="button" class="mct-btn ghost" onClick={onDone}>Abbrechen</button><button type="button" class="mct-btn primary" onClick={() => run()}><Icon name="upload" size={18} />Import starten</button></div>
    </Card>
  );
}

function ImportCsv({ file, onDone }: { file: File; onDone: () => void }): JSX.Element {
  const [state, setState] = useState<{ rows: Record<string, string>[]; runKey: string; check: { rows: number; invalid: number; missing: { moods: string[]; tags: string[]; scales: string[] } } } | null>(null);
  const [levels, setLevels] = useState<Record<string, number>>({});
  const [result, setResult] = useState<ImportResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);
  if (!started.current) {
    started.current = true;
    (async () => {
      try {
        const text = await file.text();
        const rows = parseCsv(text);
        if (!rows.length || !('full_date' in rows[0]) || !('mood' in rows[0])) throw new Error('Die Datei hat nicht die erwarteten Spalten (full_date, time, mood, activities, scales, note_title, note).');
        const check = { rows: 0, invalid: 0, missing: { moods: [] as string[], tags: [] as string[], scales: [] as string[] } };
        for (let i = 0; i < rows.length; i += 500) {
          const r = await post<typeof check>('/import/csv-check', { rows: rows.slice(i, i + 500) });
          check.rows += r.rows; check.invalid += r.invalid;
          (['moods', 'tags', 'scales'] as const).forEach((k) => { check.missing[k] = [...new Set([...check.missing[k], ...r.missing[k]])]; });
        }
        setState({ rows, runKey: (await sha256(text)).slice(0, 40), check });
      } catch (e) { setError(e instanceof Error ? e.message : 'Fehler'); }
    })();
  }
  const run = async (create: boolean) => {
    if (!state) return;
    setBusy(true);
    let total: ImportResult = { created: 0, skipped: 0, errors: 0, warnings: [] };
    try {
      for (let i = 0; i < state.rows.length; i += 200) {
        const r = await post<ImportResult>('/import/csv', { run_key: state.runKey, rows: state.rows.slice(i, i + 200), options: { create_missing: create, mood_levels: levels, offset: i } });
        total = { created: total.created + r.created, skipped: total.skipped + r.skipped, errors: total.errors + r.errors, warnings: [] };
      }
      setResult(total);
      await changed();
    } catch (e) { fail(e); }
    setBusy(false);
  };
  if (error) return <Card title="CSV-Import"><p class="mct-error" role="alert">{error}</p><button type="button" class="mct-btn ghost" onClick={onDone}>Zurück</button></Card>;
  if (!state) return <Card title="CSV-Import"><p role="status">Datei wird geprüft …</p></Card>;
  if (result) {
    return <Card title="Importbericht"><p role="status"><strong>{result.created}</strong> übernommen, <strong>{result.skipped}</strong> übersprungen, <strong>{result.errors}</strong> Fehler.</p>
      <div class="mct-btnrow"><a class="mct-btn primary" href="#/eintraege">Zu den Einträgen</a><button type="button" class="mct-btn ghost" onClick={onDone}>Weitere Datei</button></div></Card>;
  }
  const m = state.check.missing;
  const anyMissing = m.moods.length + m.tags.length + m.scales.length > 0;
  return (
    <Card title="CSV-Vorschau">
      <p>{plural(state.check.rows, 'Zeile', 'Zeilen')}{state.check.invalid > 0 && `, davon ${state.check.invalid} ohne gültiges Datum oder Uhrzeit`}. Die CSV-Datei enthält keine Fotos und Ziele.</p>
      {anyMissing && (
        <>
          <div class="mct-hint warn">In deiner Einrichtung fehlt noch etwas aus der Datei. Soll der Import es anlegen?</div>
          {m.moods.length > 0 && <fieldset class="mct-points-edit"><legend>Fehlende Stimmungen: Stufe wählen</legend>
            {m.moods.map((name) => (
              <Field key={name} label={name}><select value={levels[name] || 3} onChange={(e) => setLevels({ ...levels, [name]: Number((e.target as HTMLSelectElement).value) })}>
                {[1, 2, 3, 4, 5].map((l) => <option key={l} value={l}>{l} · {LEVEL_NAMES[l]}</option>)}</select></Field>
            ))}</fieldset>}
          {m.tags.length > 0 && <p><strong>Fehlende Aktivitäten:</strong> {m.tags.join(', ')}</p>}
          {m.scales.length > 0 && <p><strong>Fehlende Skalen:</strong> {m.scales.join(', ')}</p>}
        </>
      )}
      <div class="mct-btnrow wrap">
        <button type="button" class="mct-btn ghost" onClick={onDone}>Abbrechen</button>
        {anyMissing && <button type="button" class="mct-btn ghost" disabled={busy} onClick={() => run(false)}>Ohne Fehlendes importieren</button>}
        <button type="button" class="mct-btn primary" disabled={busy} onClick={() => run(true)}>{anyMissing ? 'Fehlendes anlegen und importieren' : 'Import starten'}</button>
      </div>
    </Card>
  );
}

export function DataScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [backup, setBackup] = useState<Backup | null>(null);
  const [csv, setCsv] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pick = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    if (/\.csv$/i.test(file.name)) { setCsv(file); return; }
    setBusy(true);
    try { setBackup(await openBackup(file)); } catch (e) { setError(e instanceof Error ? e.message : 'Die Datei lässt sich nicht lesen.'); }
    setBusy(false);
  };
  const reset = () => { setBackup(null); setCsv(null); };
  return (
    <Page title="Daten" back="/mehr">
      {feature('import') && !backup && !csv && (
        <Card title="Import und Wiederherstellen">
          {!boot.settings.consent_at && <div class="mct-hint warn">Für den Import brauchst du zuerst deine Einwilligung unter „MediClever-Konto“.</div>}
          <p>Du kannst die Sicherungsdatei deiner bisherigen Tagebuch-App, eine MediClever-Sicherung (.mctb) oder eine CSV-Datei importieren. Die Datei wird auf deinem Gerät geöffnet und in kleinen Paketen übertragen.</p>
          <label class="mct-btn primary"><Icon name="upload" size={18} />{busy ? 'Datei wird gelesen …' : 'Datei wählen'}
            <input class="mct-sr" type="file" accept=".daylio,.mctb,.zip,.csv,text/csv,application/zip" disabled={busy} onChange={(e) => pick((e.target as HTMLInputElement).files?.[0])} /></label>
          {error && <p class="mct-error" role="alert">{error}</p>}
        </Card>
      )}
      {backup && <ImportBackup b={backup} onDone={reset} />}
      {csv && <ImportCsv file={csv} onDone={reset} />}
      {(feature('export') || feature('backup')) && (
        <Card title="Export und Sicherung">
          <p>Deine Daten gehören dir. Jede Datei enthält nur dein eigenes Tagebuch.</p>
          <div class="mct-rows">
            {feature('backup') && <a class="mct-row" href={downloadUrl('/export/backup', { photos: true })}><span class="mct-row-icon"><Icon name="download" /></span><span class="mct-row-text">Vollständige Sicherung<small>Einrichtung, Einträge, Ziele und Fotos (.mctb)</small></span></a>}
            {feature('backup') && <a class="mct-row" href={downloadUrl('/export/backup')}><span class="mct-row-icon"><Icon name="download" /></span><span class="mct-row-text">Sicherung ohne Fotos<small>Kleine Datei, schnell erstellt</small></span></a>}
            {feature('export') && <a class="mct-row" href={downloadUrl('/export/csv')}><span class="mct-row-icon"><Icon name="file-text" /></span><span class="mct-row-text">Einträge als CSV<small>Für Tabellenprogramme</small></span></a>}
            {feature('export') && <a class="mct-row" href={downloadUrl('/export/pdf')}><span class="mct-row-icon"><Icon name="file-text" /></span><span class="mct-row-text">Einträge als PDF<small>Zum Lesen und Ausdrucken, ohne Fotos</small></span></a>}
            {feature('export') && feature('photos') && <a class="mct-row" href={downloadUrl('/export/photos')}><span class="mct-row-icon"><Icon name="image" /></span><span class="mct-row-text">Fotos exportieren<small>Alle Fotos als ZIP-Datei</small></span></a>}
          </div>
          <p class="mct-muted small">Wiederherstellen: Wähle oben die Sicherungsdatei (.mctb) aus.</p>
        </Card>
      )}
    </Page>
  );
}
