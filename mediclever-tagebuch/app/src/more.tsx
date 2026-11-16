import type { JSX } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import { get, post, del, assetUrl, cfg } from './api';
import { state, useStore, useLoad, changed, fail, toast, lookup, feature, go, type Route } from './store';
import { Icon } from './icons';
import { Card, Chip, Empty, Field, Loading, MoodIcon, Page, Row, Segmented, Sheet, Toggle, confirmDelete, SortList, ask } from './ui';
import { EntryCard, Lightbox } from './home';
import { LineChart } from './charts';
import { addDays, addMonths, dayLabel, dayLabelYear, daysInMonth, levelColors, monthLabel, num, parseDate, plural, SCHEMES, shortDate, signed, WD_SHORT, MONTHS } from './format';
import type { Achievement, Entry, Goal, Grant, Milestone, Reminder, Report, Settings } from './types';
import { hasPin, setPin, clearPin } from './pin';

export function MoreScreen(): JSX.Element {
  const boot = useStore().boot!;
  const f = feature;
  return (
    <Page title="Mehr">
      <div class="mct-grid">
        <Card title="Dein Tagebuch">
          <div class="mct-rows">
            {f('goals') && <Row icon="target" label="Ziele" hint={plural(boot.goals.filter((g) => !g.archived).length, 'aktives Ziel', 'aktive Ziele')} to="/mehr/ziele" />}
            {f('reports') && <Row icon="file-text" label="Berichte" hint="Wöchentlich und monatlich" to="/mehr/berichte" />}
            {f('lookback') && <Row icon="rotate-ccw" label="Rückblick" hint="Dieser Tag in früheren Jahren" to="/mehr/rueckblick" />}
            {f('milestones') && <Row icon="flag" label="Wichtige Tage" hint={plural(boot.milestones.length, 'Tag', 'Tage')} to="/mehr/wichtige-tage" />}
            {f('favorites') && <Row icon="star" label="Favoriten" to="/mehr/favoriten" />}
            {f('photos') && <Row icon="image" label="Fotos" to="/mehr/fotos" />}
            {f('achievements') && <Row icon="trophy" label="Erfolge" to="/mehr/erfolge" />}
          </div>
        </Card>
        <Card title="Einrichten">
          <div class="mct-rows">
            <Row icon="settings" label="Einrichtung" hint="Stimmungen, Aktivitäten, Skalen, Vorlagen" to="/mehr/einrichtung" />
            {f('reminders') && <Row icon="bell" label="Erinnerungen" hint={plural(boot.setup.reminders.filter((r) => r.active).length, 'aktive Erinnerung', 'aktive Erinnerungen')} to="/mehr/erinnerungen" />}
            {f('appearance') && <Row icon="palette" label="Aussehen" hint="Farbschema, Schrift, Hell oder Dunkel" to="/mehr/aussehen" />}
            {f('pin') && <Row icon="lock" label="Sicherheit" hint={hasPin() ? 'PIN-Sperre ist an' : 'PIN-Sperre ist aus'} to="/mehr/sicherheit" />}
            <Row icon="sliders-horizontal" label="Einstellungen" hint="Wochenanfang, Mitternacht, Rückblick" to="/mehr/einstellungen" />
          </div>
        </Card>
        <Card title="Daten und Konto">
          <div class="mct-rows">
            {(f('import') || f('export') || f('backup')) && <Row icon="download" label="Daten" hint="Import, Export, Sicherung" to="/mehr/daten" />}
            {f('mcp') && <Row icon="bot" label="KI-Zugriff" hint={boot.settings.ai_consent_at ? 'eingewilligt' : 'aus'} to="/mehr/ki" />}
            <Row icon="user" label="MediClever-Konto" hint={boot.user.name} to="/mehr/konto" />
          </div>
        </Card>
      </div>
      <p class="mct-legal">Bitte trag keine Patienten- und Einsatzdaten ein. Das Tagebuch ist keine medizinische Beratung. Im Notfall wähle 112.</p>
    </Page>
  );
}

// ---- Tagesansicht ---------------------------------------------------------

export function DayScreen({ route }: { route: Route }): JSX.Element {
  const boot = useStore().boot!;
  const date = route.parts[1];
  const { data, error, loading, reload } = useLoad(() => get<{ entries: Entry[] }>('/entries', { date }), [date]);
  return (
    <Page title={dayLabelYear(date)} back="/kalender" actions={<a class="mct-iconbtn" href={`#/eintrag/neu?date=${date}&fix=1`} aria-label="Weiteren Eintrag für diesen Tag schreiben"><Icon name="plus" /></a>}>
      {!data ? <Loading error={loading ? null : error} reload={reload} /> : data.entries.length === 0
        ? <Empty title="Kein Eintrag an diesem Tag"><a class="mct-btn primary" href={`#/eintrag/neu?date=${date}&fix=1`}>Nachtragen</a></Empty>
        : <div class="mct-feed">{data.entries.map((e) => <EntryCard key={e.id} e={e} l={lookup(boot)} showYear />)}</div>}
    </Page>
  );
}

// ---- Ziele ----------------------------------------------------------------

const FREQ = (g: Goal): string => g.freq_type === 'daily' ? 'Täglich' : g.freq_type === 'weekly' ? `${g.freq_count}-mal pro Woche` : g.freq_days.map((d) => WD_SHORT[d % 7]).join(', ');

export function GoalSheet({ goal, onClose }: { goal: Goal | null; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [g, setG] = useState({ name: goal?.name || '', tag_id: goal?.tag_id ?? null, freq_type: goal?.freq_type || 'daily', freq_count: goal?.freq_count || 3, freq_days: goal?.freq_days || [1, 3, 5],
    reminder_time: goal?.reminder_time || '', reminder_text: goal?.reminder_text || '', start_date: goal?.start_date || boot.today, end_date: goal?.end_date || '', note: goal?.note || '' });
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try { await post(goal ? `/goals/${goal.id}` : '/goals', { ...g, reminder_time: g.reminder_time || null, end_date: g.end_date || null }); await changed(); toast('Ziel gespeichert.'); onClose(); } catch (e) { fail(e); }
    setBusy(false);
  };
  return (
    <Sheet title={goal ? 'Ziel bearbeiten' : 'Neues Ziel'} onClose={onClose} footer={<button type="button" class="mct-btn primary" disabled={busy || !g.name.trim()} onClick={save}>Speichern</button>}>
      <Field label="Name"><input type="text" value={g.name} maxLength={150} onInput={(e) => setG({ ...g, name: (e.target as HTMLInputElement).value })} /></Field>
      <Field label="Verknüpfte Aktivität" hint="Mit Verknüpfung gilt das Ziel als erreicht, sobald ein Eintrag die Aktivität enthält.">
        <select value={g.tag_id ?? ''} onChange={(e) => setG({ ...g, tag_id: Number((e.target as HTMLSelectElement).value) || null })}>
          <option value="">Frei, ohne Aktivität</option>
          {boot.setup.tags.filter((t) => !t.archived || t.id === g.tag_id).map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </select>
      </Field>
      <span class="mct-field-label" id="freq-label">Häufigkeit</span>
      <Segmented label="Häufigkeit" value={g.freq_type} onChange={(v) => setG({ ...g, freq_type: v })} options={[{ value: 'daily', label: 'Täglich' }, { value: 'weekly', label: 'Pro Woche' }, { value: 'days', label: 'Wochentage' }]} />
      {g.freq_type === 'weekly' && <Field label="Wie oft pro Woche?"><input type="number" min={1} max={7} value={g.freq_count} onInput={(e) => setG({ ...g, freq_count: Number((e.target as HTMLInputElement).value) })} /></Field>}
      {g.freq_type === 'days' && (
        <div class="mct-days" role="group" aria-label="Wochentage">
          {[1, 2, 3, 4, 5, 6, 7].map((d) => { const on = g.freq_days.includes(d); return (
            <button type="button" key={d} aria-pressed={on} class={on ? 'on' : ''} aria-label={['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'][d]}
              onClick={() => setG({ ...g, freq_days: on ? g.freq_days.filter((x) => x !== d) : [...g.freq_days, d].sort() })}>{WD_SHORT[d % 7]}</button>); })}
        </div>
      )}
      <div class="mct-when">
        <Field label="Start"><input type="date" value={g.start_date} onInput={(e) => setG({ ...g, start_date: (e.target as HTMLInputElement).value })} /></Field>
        <Field label="Ende (optional)"><input type="date" value={g.end_date} onInput={(e) => setG({ ...g, end_date: (e.target as HTMLInputElement).value })} /></Field>
      </div>
      {feature('reminders') && (
        <div class="mct-when">
          <Field label="Erinnerung (optional)"><input type="time" value={g.reminder_time} onInput={(e) => setG({ ...g, reminder_time: (e.target as HTMLInputElement).value })} /></Field>
          <Field label="Text der Erinnerung"><input type="text" value={g.reminder_text} maxLength={255} onInput={(e) => setG({ ...g, reminder_text: (e.target as HTMLInputElement).value })} /></Field>
        </div>
      )}
      <Field label="Notiz (optional)"><input type="text" value={g.note} maxLength={1000} onInput={(e) => setG({ ...g, note: (e.target as HTMLInputElement).value })} /></Field>
    </Sheet>
  );
}

function GoalDetail({ goal, onClose, onEdit }: { goal: Goal; onClose: () => void; onEdit: () => void }): JSX.Element {
  const boot = state.boot!;
  const [month, setMonth] = useState(boot.today.slice(0, 7));
  const { data } = useLoad(() => get<Goal[]>('/goals', { month }), [month]);
  const g = data?.find((x) => x.id === goal.id) || goal;
  const checks = new Set(g.month_checks || []);
  const first = parseDate(`${month}-01`);
  const lead = (first.getDay() - boot.settings.week_start + 7) % 7;
  const toggle = async (d: string) => { try { await post(`/goals/${g.id}/check`, { date: d, done: !checks.has(d) }); await changed(); } catch (e) { fail(e); } };
  const months = Object.entries(g.per_month).sort().slice(-6);
  const act = async (kind: 'archive' | 'delete') => {
    try {
      if (kind === 'archive') await post('/archive', { kind: 'goals', id: g.id, archived: !g.archived });
      else {
        const k = await ask({ title: 'Ziel löschen?', text: `„${g.name}“ wurde ${g.total}-mal abgehakt. Beim Löschen gehen diese Haken verloren. Im Archiv bleiben sie erhalten.`,
          buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, ...(g.archived ? [] : [{ key: 'arch', label: 'Archivieren', kind: 'primary' as const }]), { key: 'del', label: 'Löschen', kind: 'danger' }] });
        if (k === 'arch') await post('/archive', { kind: 'goals', id: g.id, archived: true });
        else if (k === 'del') await del(`/goals/${g.id}`);
        else return;
      }
      await changed();
      onClose();
    } catch (e) { fail(e); }
  };
  return (
    <Sheet title={g.name} onClose={onClose} wide>
      <div class="mct-kpis">
        <div><strong>{g.streak}</strong><span>Serie</span></div>
        <div><strong>{g.rate7} %</strong><span>Erfolg 7 Tage</span></div>
        <div><strong>{g.rate30} %</strong><span>Erfolg 30 Tage</span></div>
        <div><strong>{g.total}</strong><span>gesamt</span></div>
      </div>
      <p class="mct-muted">{FREQ(g)} · seit {shortDate(g.start_date)} · längste Serie {g.best_streak}{g.reminder_time ? ` · Erinnerung ${g.reminder_time} Uhr` : ''}</p>
      <p><strong>Stufe {g.level}</strong>{g.next_level_at && <span class="mct-muted"> · nächste Stufe bei {g.next_level_at} Abschlüssen</span>}</p>
      <div class="mct-meter" role="img" aria-label={`Fortschritt zur nächsten Stufe: ${g.total} von ${g.next_level_at ?? g.total}`}><span style={{ width: `${g.next_level_at ? Math.min(100, (g.total / g.next_level_at) * 100) : 100}%` }} /></div>
      <div class="mct-yearbar">
        <button type="button" class="mct-iconbtn" aria-label="Vorheriger Monat" onClick={() => setMonth(addMonths(month, -1))}><Icon name="chevron-left" /></button>
        <strong aria-live="polite">{monthLabel(month)}</strong>
        <button type="button" class="mct-iconbtn" aria-label="Nächster Monat" disabled={month >= boot.today.slice(0, 7)} onClick={() => setMonth(addMonths(month, 1))}><Icon name="chevron-right" /></button>
      </div>
      <div class="mct-minical" role="group" aria-label={`Abgehakte Tage im ${monthLabel(month)}`}>
        {Array.from({ length: lead }, (_, i) => <span key={'e' + i} />)}
        {Array.from({ length: daysInMonth(month) }, (_, i) => {
          const d = addDays(`${month}-01`, i);
          return <button type="button" key={d} disabled={d > boot.today} class={checks.has(d) ? 'on' : ''} aria-pressed={checks.has(d)} aria-label={`${dayLabel(d)}${checks.has(d) ? ', abgehakt' : ''}`} onClick={() => toggle(d)}>{i + 1}</button>;
        })}
      </div>
      {months.length > 0 && <><h3>Abschlüsse je Monat</h3><div class="mct-monthbars">{months.map(([m, n]) => <div key={m}><strong>{n}</strong><span>{MONTHS[Number(m.slice(5)) - 1].slice(0, 3)}</span></div>)}</div></>}
      {g.note && <p>{g.note}</p>}
      <div class="mct-btnrow wrap">
        <button type="button" class="mct-btn ghost" onClick={onEdit}><Icon name="pencil" size={18} />Bearbeiten</button>
        <button type="button" class="mct-btn ghost" onClick={() => act('archive')}><Icon name={g.archived ? 'archive-restore' : 'archive'} size={18} />{g.archived ? 'Wiederherstellen' : 'Archivieren'}</button>
        <button type="button" class="mct-btn ghost danger" onClick={() => act('delete')}><Icon name="trash" size={18} />Löschen</button>
      </div>
    </Sheet>
  );
}

export function GoalsScreen({ route }: { route: Route }): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<Goal | null | 'new'>(route.query.get('neu') ? 'new' : null);
  const [detail, setDetail] = useState<number | null>(null);
  const [archive, setArchive] = useState(false);
  const active = boot.goals.filter((g) => !g.archived);
  const archived = boot.goals.filter((g) => g.archived);
  const toggle = async (g: Goal) => { try { await post(`/goals/${g.id}/check`, { date: boot.today, done: !g.done_today }); await changed(); } catch (e) { fail(e); } };
  const shown = boot.goals.find((g) => g.id === detail);
  return (
    <Page title="Ziele" back="/mehr" actions={<button type="button" class="mct-iconbtn" aria-label="Neues Ziel" onClick={() => setEdit('new')}><Icon name="plus" /></button>}>
      {active.length === 0 && <Empty icon="target" title="Noch kein Ziel" text="Ein Ziel kann täglich, mehrmals pro Woche oder an festen Wochentagen fällig sein."><button type="button" class="mct-btn primary" onClick={() => setEdit('new')}>Ziel anlegen</button></Empty>}
      {active.length > 0 && (
        <SortList items={active} id={(g) => String(g.id)} label={(g) => g.name} onChange={(items) => post('/reorder', { kind: 'goals', ids: items.map((g) => g.id) }).then(changed, fail)}
          render={(g) => (
            <div class="mct-goal">
              <button type="button" class={'mct-check' + (g.done_today ? ' on' : '')} aria-pressed={g.done_today} aria-label={`${g.name} für heute abhaken`} onClick={() => toggle(g)}><Icon name={g.done_today ? 'check' : 'target'} /></button>
              <button type="button" class="mct-goal-main" onClick={() => setDetail(g.id)}>
                <strong>{g.name}</strong>
                <small>{FREQ(g)} · Serie {g.streak} · {g.rate30} % in 30 Tagen</small>
              </button>
            </div>
          )} />
      )}
      {archived.length > 0 && (
        <>
          <button type="button" class="mct-btn ghost small" aria-expanded={archive} onClick={() => setArchive(!archive)}><Icon name="archive" size={16} />Archiv ({archived.length})</button>
          {archive && <div class="mct-rows">{archived.map((g) => <Row key={g.id} icon="archive" label={g.name} hint={`${g.total} Abschlüsse`} onClick={() => setDetail(g.id)} />)}</div>}
        </>
      )}
      {edit && <GoalSheet goal={edit === 'new' ? null : edit} onClose={() => { setEdit(null); if (route.query.get('neu')) go('/mehr/ziele'); }} />}
      {shown && !edit && <GoalDetail goal={shown} onClose={() => setDetail(null)} onEdit={() => setEdit(shown)} />}
    </Page>
  );
}

// ---- Wichtige Tage --------------------------------------------------------

export function MilestoneSheet({ ms, onClose }: { ms: Milestone | null; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [m, setM] = useState({ name: ms?.name || '', date: ms?.date || boot.today, category: ms?.category || '', note: ms?.note || '', yearly: ms?.yearly ?? true,
    remind_same_day: ms?.remind_same_day ?? true, remind_days_before: ms?.remind_days_before ?? 0, asset_id: ms?.asset_id ?? null });
  const [busy, setBusy] = useState(false);
  const cats = [...new Set(boot.milestones.map((x) => x.category).filter(Boolean))];
  const save = async () => {
    setBusy(true);
    try { await post(ms ? `/milestones/${ms.id}` : '/milestones', m); await changed(); toast('Wichtiger Tag gespeichert.'); onClose(); } catch (e) { fail(e); }
    setBusy(false);
  };
  const upload = async (files: FileList | null) => {
    if (!files?.[0]) return;
    const fd = new FormData();
    fd.append('file', files[0]);
    try { const a = await post<{ id: number }>('/assets', fd); setM({ ...m, asset_id: a.id }); } catch (e) { fail(e); }
  };
  return (
    <Sheet title={ms ? 'Wichtigen Tag bearbeiten' : 'Neuer wichtiger Tag'} onClose={onClose} footer={<button type="button" class="mct-btn primary" disabled={busy || !m.name.trim()} onClick={save}>Speichern</button>}>
      <Field label="Name"><input type="text" value={m.name} maxLength={150} onInput={(e) => setM({ ...m, name: (e.target as HTMLInputElement).value })} /></Field>
      <Field label="Datum"><input type="date" value={m.date} onInput={(e) => setM({ ...m, date: (e.target as HTMLInputElement).value })} /></Field>
      <Field label="Kategorie (optional)"><input type="text" value={m.category} list="mct-cats" maxLength={60} onInput={(e) => setM({ ...m, category: (e.target as HTMLInputElement).value })} /></Field>
      <datalist id="mct-cats">{['Privat', 'Beruf', 'Familie', 'Gesundheit', ...cats].filter((v, i, a) => a.indexOf(v) === i).map((c) => <option key={c} value={c} />)}</datalist>
      <Field label="Notiz (optional)"><textarea rows={3} value={m.note} onInput={(e) => setM({ ...m, note: (e.target as HTMLTextAreaElement).value })} /></Field>
      <Toggle label="Jedes Jahr wiederholen" checked={m.yearly} onChange={(v) => setM({ ...m, yearly: v })} />
      {feature('reminders') && <>
        <Toggle label="Am Tag selbst erinnern" checked={m.remind_same_day} onChange={(v) => setM({ ...m, remind_same_day: v })} />
        <Field label="Zusätzlich vorher erinnern (Tage, 0 = aus)"><input type="number" min={0} max={60} value={m.remind_days_before} onInput={(e) => setM({ ...m, remind_days_before: Number((e.target as HTMLInputElement).value) })} /></Field>
      </>}
      {feature('photos') && (
        <div class="mct-btnrow wrap">
          {m.asset_id && <img class="mct-ms-photo" src={assetUrl(m.asset_id, 's')} alt="Foto zu diesem Tag" />}
          <label class="mct-btn ghost"><Icon name="image-plus" size={18} />{m.asset_id ? 'Foto ersetzen' : 'Foto hinzufügen'}<input class="mct-sr" type="file" accept="image/*" onChange={(e) => upload((e.target as HTMLInputElement).files)} /></label>
          {m.asset_id && <button type="button" class="mct-btn ghost" onClick={() => setM({ ...m, asset_id: null })}>Foto entfernen</button>}
        </div>
      )}
    </Sheet>
  );
}

export function MilestonesScreen({ route }: { route: Route }): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<Milestone | null | 'new'>(route.query.get('neu') ? 'new' : null);
  const remove = async (m: Milestone) => {
    if (!(await confirmDelete('Wichtigen Tag löschen?', `„${m.name}“ wird endgültig gelöscht.`))) return;
    try { await del(`/milestones/${m.id}`); await changed(); } catch (e) { fail(e); }
  };
  return (
    <Page title="Wichtige Tage" back="/mehr" actions={<button type="button" class="mct-iconbtn" aria-label="Neuer wichtiger Tag" onClick={() => setEdit('new')}><Icon name="plus" /></button>}>
      {boot.milestones.length === 0 && <Empty icon="flag" title="Noch kein wichtiger Tag" text="Halte Tage fest, die dir etwas bedeuten: Examen, Umzug, Jahrestage."><button type="button" class="mct-btn primary" onClick={() => setEdit('new')}>Tag anlegen</button></Empty>}
      <div class="mct-feed">
        {boot.milestones.map((m) => (
          <article class="mct-ms" key={m.id}>
            {m.asset_id && <img src={assetUrl(m.asset_id, 's')} alt="" />}
            <div class="mct-ms-body">
              <h2>{m.name}</h2>
              <p class="mct-muted">{dayLabelYear(m.date)}{m.category && ` · ${m.category}`}</p>
              <p>{m.days_since >= 0 ? <>seit <strong>{plural(m.days_since, 'Tag', 'Tagen')}</strong></> : <>in <strong>{plural(-m.days_since, 'Tag', 'Tagen')}</strong></>}
                {m.yearly && m.days_until !== null && m.days_since >= 0 && <> · {m.days_until === 0 ? <strong>Jahrestag ist heute</strong> : <>nächster Jahrestag in <strong>{plural(m.days_until, 'Tag', 'Tagen')}</strong></>}</>}</p>
              {m.note && <p>{m.note}</p>}
            </div>
            <div class="mct-ms-actions">
              <button type="button" class="mct-iconbtn" aria-label={`${m.name} bearbeiten`} onClick={() => setEdit(m)}><Icon name="pencil" /></button>
              <button type="button" class="mct-iconbtn" aria-label={`${m.name} löschen`} onClick={() => remove(m)}><Icon name="trash" /></button>
            </div>
          </article>
        ))}
      </div>
      {edit && <MilestoneSheet ms={edit === 'new' ? null : edit} onClose={() => { setEdit(null); if (route.query.get('neu')) go('/mehr/wichtige-tage'); }} />}
    </Page>
  );
}

// ---- Berichte, Rückblick, Fotos, Erfolge ----------------------------------

export function ReportsScreen({ route }: { route: Route }): JSX.Element {
  const boot = useStore().boot!;
  const l = lookup(boot);
  const [type, setType] = useState<'week' | 'month'>((route.query.get('type') as 'week' | 'month') || 'week');
  const [date, setDate] = useState(route.query.get('date') || addDays(boot.today, -7));
  const { data, error, loading, reload } = useLoad(() => get<Report>('/report', { type, date }), [type, date]);
  const colors = levelColors(boot.settings.color_scheme, boot.settings.custom_colors);
  const shift = (n: number) => setDate(type === 'week' ? addDays(date, 7 * n) : `${addMonths(date.slice(0, 7), n)}-15`);
  const delta = (v: number | null, digits = 0) => v === null ? null : <small class={v > 0 ? 'mct-good' : v < 0 ? 'mct-bad' : 'mct-muted'}>{signed(v, digits)}</small>;
  const notify = async (v: boolean) => { try { await post('/settings', { report_notify: v }); await changed(); } catch (e) { fail(e); } };
  return (
    <Page title="Berichte" back="/mehr">
      <Segmented label="Art des Berichts" value={type} onChange={setType} options={[{ value: 'week', label: 'Woche' }, { value: 'month', label: 'Monat' }]} />
      {!data ? <Loading error={loading ? null : error} reload={reload} /> : (
        <>
          <div class="mct-yearbar">
            <button type="button" class="mct-iconbtn" aria-label="Früher" onClick={() => shift(-1)}><Icon name="chevron-left" /></button>
            <strong aria-live="polite">{data.type === 'month' ? monthLabel(data.from.slice(0, 7)) : `${shortDate(data.from)} bis ${shortDate(data.to)}`}</strong>
            <button type="button" class="mct-iconbtn" aria-label="Später" disabled={data.to >= boot.today} onClick={() => shift(1)}><Icon name="chevron-right" /></button>
          </div>
          {!data.enough ? <Empty icon="file-text" title="Kein Eintrag in diesem Zeitraum" text="Sobald du Einträge hast, entsteht hier dein Bericht." /> : (
            <div class="mct-grid">
              <Card title="Überblick">
                <div class="mct-kpis">
                  <div><strong>{data.current.entries}</strong><span>Einträge</span>{delta(data.change.entries)}</div>
                  <div><strong>{data.current.activities}</strong><span>Aktivitäten</span>{delta(data.change.activities)}</div>
                  <div><strong>{num(data.current.avg)}</strong><span>Stimmung</span>{delta(data.change.avg, 1)}</div>
                  <div><strong>{data.current.words}</strong><span>Wörter</span>{delta(data.change.words)}</div>
                </div>
                <p class="mct-muted small">Veränderung {data.type === 'week' ? 'zur Vorwoche' : 'zum Vormonat'}.</p>
              </Card>
              <Card title="Verlauf">
                <LineChart title="Stimmungsverlauf im Bericht" desc={`${data.days.length} Tage mit Eintrag`} yMin={1} yMax={5} xMax={data.type === 'week' ? 7 : daysInMonth(data.from.slice(0, 7))}
                  points={data.days.map((d) => ({ x: Math.round((parseDate(d.date).getTime() - parseDate(data.from).getTime()) / 86400000) + 1, y: d.avg }))}
                  colorAt={(y) => colors[Math.max(0, Math.min(4, Math.round(5 - y)))]} yTicks={[1, 3, 5].map((v) => ({ v, label: ['', '−−', '', 'o', '', '++'][v] }))}
                  xTicks={data.type === 'week' ? Array.from({ length: 7 }, (_, i) => ({ v: i + 1, label: WD_SHORT[parseDate(addDays(data.from, i)).getDay()] })) : [1, 8, 15, 22, 29].map((v) => ({ v, label: String(v) }))} />
              </Card>
              {data.top_tags.length > 0 && <Card title="Häufigste Aktivitäten"><div class="mct-chips">{data.top_tags.map((x) => <Chip key={x.tag_id} icon={l.tags.get(x.tag_id)?.icon} label={`${l.tags.get(x.tag_id)?.name || 'Gelöscht'} ${x.n}×`} />)}</div></Card>}
              {data.goals.length > 0 && <Card title="Ziele">{data.goals.map((g) => <div class="mct-goalrow" key={g.goal_id}><span>{boot.goals.find((x) => x.id === g.goal_id)?.name || 'Ziel'}</span><strong>{g.n}×</strong></div>)}</Card>}
              {data.photos.length > 0 && <Card title="Fotos"><div class="mct-gallery">{data.photos.map((id) => <img key={id} src={assetUrl(id, 's')} alt="Foto aus dem Berichtszeitraum" loading="lazy" />)}</div></Card>}
            </div>
          )}
          {feature('reminders') && <Card><Toggle label="Benachrichtigung zum Wochenbericht" hint="Kommt zum Wochenanfang, wenn Erinnerungen im Browser erlaubt sind." checked={boot.settings.report_notify} onChange={notify} /></Card>}
        </>
      )}
    </Page>
  );
}

export function LookbackScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [date, setDate] = useState(boot.today);
  const { data, error, loading, reload } = useLoad(() => get<{ date: string; years: { year: number; years_ago: number; entries: Entry[] }[] }>('/lookback', { date }), [date]);
  return (
    <Page title="Rückblick" back="/mehr">
      <div class="mct-yearbar">
        <button type="button" class="mct-iconbtn" aria-label="Tag davor" onClick={() => setDate(addDays(date, -1))}><Icon name="chevron-left" /></button>
        <strong aria-live="polite">{dayLabel(date)}</strong>
        <button type="button" class="mct-iconbtn" aria-label="Tag danach" disabled={date >= boot.today} onClick={() => setDate(addDays(date, 1))}><Icon name="chevron-right" /></button>
      </div>
      {!data ? <Loading error={loading ? null : error} reload={reload} /> : data.years.length === 0
        ? <Empty icon="rotate-ccw" title="Nichts aus früheren Jahren" text="An diesem Tag gibt es noch keine älteren Einträge." />
        : data.years.map((y) => (
          <section key={y.year}>
            <h2 class="mct-section">Vor {y.years_ago === 1 ? 'einem Jahr' : `${y.years_ago} Jahren`} · {y.year}</h2>
            <div class="mct-feed">{y.entries.map((e) => <EntryCard key={e.id} e={e} l={lookup(boot)} showYear />)}</div>
          </section>
        ))}
    </Page>
  );
}

export function PhotosScreen(): JSX.Element {
  const [page, setPage] = useState(1);
  const [open, setOpen] = useState<number | null>(null);
  const { data, error, loading, reload } = useLoad(() => get<{ total: number; pages: number; photos: { id: number; entry_id: number; date: string }[] }>('/photos', { page }), [page]);
  const groups = new Map<string, { id: number; date: string }[]>();
  (data?.photos || []).forEach((p) => groups.set(p.date.slice(0, 7), [...(groups.get(p.date.slice(0, 7)) || []), p]));
  const ids = (data?.photos || []).map((p) => p.id);
  return (
    <Page title="Fotos" back="/mehr">
      {!data ? <Loading error={loading ? null : error} reload={reload} /> : data.total === 0
        ? <Empty icon="image" title="Noch keine Fotos" text="Fotos hängst du beim Schreiben eines Eintrags an." />
        : <>
          <p class="mct-count">{plural(data.total, 'Foto', 'Fotos')}</p>
          {[...groups.entries()].map(([m, list]) => (
            <section key={m}>
              <h2 class="mct-section">{monthLabel(m)}</h2>
              <div class="mct-gallery">{list.map((p) => <button type="button" key={p.id} onClick={() => setOpen(ids.indexOf(p.id))} aria-label={`Foto vom ${dayLabel(p.date)} vergrößern`}><img src={assetUrl(p.id, 's')} alt="" loading="lazy" /></button>)}</div>
            </section>
          ))}
          {data.pages > 1 && <nav class="mct-btnrow center" aria-label="Seiten">
            <button type="button" class="mct-btn ghost" disabled={page <= 1} onClick={() => setPage(page - 1)}>Neuere</button><span>Seite {page} von {data.pages}</span>
            <button type="button" class="mct-btn ghost" disabled={page >= data.pages} onClick={() => setPage(page + 1)}>Ältere</button></nav>}
        </>}
      {open !== null && <Lightbox ids={ids} start={open} onClose={() => setOpen(null)} />}
    </Page>
  );
}

const BADGE_ICON: Record<string, string> = { entries: 'notebook-pen', streak: 'flame', goals: 'target', years: 'calendar', photos: 'image' };
export function AchievementsScreen(): JSX.Element {
  const { data, error, loading, reload } = useLoad(() => get<{ achievements: Achievement[]; reached: number; total: number }>('/achievements'), []);
  const groups = [...new Set((data?.achievements || []).map((a) => a.group))];
  return (
    <Page title="Erfolge" back="/mehr">
      {!data ? <Loading error={loading ? null : error} reload={reload} /> : (
        <>
          <p class="mct-count">{data.reached} von {data.total} erreicht</p>
          {groups.map((g) => {
            const list = data.achievements.filter((a) => a.group === g);
            return (
              <Card key={g} title={list[0].group_label}>
                <ul class="mct-badges">
                  {list.map((a) => (
                    <li key={a.key} class={a.reached ? 'on' : ''}>
                      <span class="mct-badge"><Icon name={BADGE_ICON[g] || 'award'} size={24} /><b>{a.target >= 1000 ? `${a.target / 1000}k` : a.target}</b></span>
                      <span><strong>{a.name}</strong><small>{a.reached ? 'Erreicht' : `${a.value} von ${a.target}`}</small></span>
                    </li>
                  ))}
                </ul>
              </Card>
            );
          })}
        </>
      )}
    </Page>
  );
}

// ---- Erinnerungen ---------------------------------------------------------

async function enablePush(): Promise<string> {
  const key = state.boot?.push_key;
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !key) return 'Dieser Browser unterstützt keine Benachrichtigungen. Du siehst Erinnerungen als Hinweis beim Öffnen.';
  if ((await Notification.requestPermission()) !== 'granted') return 'Benachrichtigungen sind im Browser nicht erlaubt. Du siehst Erinnerungen als Hinweis beim Öffnen.';
  const reg = await navigator.serviceWorker.register(cfg.sw, { scope: '/' });
  await navigator.serviceWorker.ready;
  const raw = atob(key.replace(/-/g, '+').replace(/_/g, '/'));
  const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: Uint8Array.from(raw, (c) => c.charCodeAt(0)) });
  await post('/push', sub.toJSON());
  return 'Benachrichtigungen sind auf diesem Gerät eingeschaltet.';
}

export function RemindersScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<Reminder | 'new' | null>(null);
  const [push, setPush] = useState<string>(typeof Notification !== 'undefined' && Notification.permission === 'granted' ? 'Benachrichtigungen sind im Browser erlaubt.' : '');
  const toggle = async (r: Reminder) => { try { await post(`/reminders/${r.id}`, { active: !r.active }); await changed(); } catch (e) { fail(e); } };
  return (
    <Page title="Erinnerungen" back="/mehr" actions={<button type="button" class="mct-iconbtn" aria-label="Neue Erinnerung" onClick={() => setEdit('new')}><Icon name="plus" /></button>}>
      <Card>
        <p>Erinnerungen kommen als Benachrichtigung, wenn dein Browser das erlaubt. Sonst zeigt dir die App beim Öffnen einen Hinweis. Verlässlich pünktlich werden sie erst mit der Android-App.</p>
        <button type="button" class="mct-btn ghost" onClick={() => enablePush().then(setPush, () => setPush('Benachrichtigungen ließen sich nicht einschalten.'))}><Icon name="bell" size={18} />Benachrichtigungen auf diesem Gerät einschalten</button>
        {push && <p role="status" class="mct-muted">{push}</p>}
      </Card>
      {boot.setup.reminders.length === 0 && <Empty icon="bell" title="Keine Erinnerung" text="Lege beliebig viele Uhrzeiten mit eigenem Text an."><button type="button" class="mct-btn primary" onClick={() => setEdit('new')}>Erinnerung anlegen</button></Empty>}
      <div class="mct-rows card">
        {boot.setup.reminders.map((r) => (
          <div class="mct-row" key={r.id}>
            <button type="button" class="mct-row-main" onClick={() => setEdit(r)}>
              <span class="mct-row-icon"><Icon name="bell" /></span><span class="mct-row-text"><strong>{r.time} Uhr</strong><small>{r.text || 'Zeit für deinen Eintrag.'}</small></span>
              <span class="mct-sr">Erinnerung bearbeiten</span>
            </button>
            <input type="checkbox" role="switch" checked={r.active} aria-label={`Erinnerung ${r.time} Uhr aktiv`} onChange={() => toggle(r)} />
          </div>
        ))}
      </div>
      {edit && <ReminderSheet r={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Page>
  );
}

function ReminderSheet({ r, onClose }: { r: Reminder | null; onClose: () => void }): JSX.Element {
  const [time, setTime] = useState(r?.time || '20:00');
  const [text, setText] = useState(r?.text || '');
  const save = async () => { try { await post(r ? `/reminders/${r.id}` : '/reminders', { time, text }); await changed(); onClose(); } catch (e) { fail(e); } };
  const remove = async () => { try { await del(`/reminders/${r!.id}`); await changed(); onClose(); } catch (e) { fail(e); } };
  return (
    <Sheet title={r ? 'Erinnerung bearbeiten' : 'Neue Erinnerung'} onClose={onClose}
      footer={<>{r && <button type="button" class="mct-btn ghost danger" onClick={remove}>Löschen</button>}<button type="button" class="mct-btn primary" onClick={save}>Speichern</button></>}>
      <Field label="Uhrzeit"><input type="time" value={time} onInput={(e) => setTime((e.target as HTMLInputElement).value)} /></Field>
      <Field label="Eigener Text (optional)"><input type="text" value={text} maxLength={255} placeholder="Zeit für deinen Eintrag." onInput={(e) => setText((e.target as HTMLInputElement).value)} /></Field>
    </Sheet>
  );
}

// ---- Aussehen, Sicherheit, Einstellungen ----------------------------------

const save = async (patch: Partial<Settings>) => { try { await post('/settings', patch); await changed(); } catch (e) { fail(e); } };

export function AppearanceScreen(): JSX.Element {
  const boot = useStore().boot!;
  const s = boot.settings;
  const custom = s.custom_colors.length === 5 ? s.custom_colors : SCHEMES.klar.colors;
  const names = ['Sehr gut', 'Gut', 'Mittel', 'Schlecht', 'Sehr schlecht'];
  return (
    <Page title="Aussehen" back="/mehr">
      <Card title="Farbmodus">
        <Segmented label="Farbmodus" value={s.color_mode} onChange={(v) => save({ color_mode: v })} options={[{ value: 'system', label: 'Wie das Gerät' }, { value: 'light', label: 'Hell' }, { value: 'dark', label: 'Dunkel' }]} />
      </Card>
      <Card title="Farbschema der fünf Stufen">
        <div class="mct-schemes" role="radiogroup" aria-label="Farbschema">
          {[...Object.entries(SCHEMES).map(([k, v]) => ({ k, label: v.label, colors: levelColors(k, []) })), { k: 'eigen', label: 'Eigenes Schema', colors: custom }].map((sc) => (
            <button type="button" key={sc.k} role="radio" aria-checked={s.color_scheme === sc.k} class={s.color_scheme === sc.k ? 'on' : ''}
              onClick={() => save(sc.k === 'eigen' ? { color_scheme: 'eigen', custom_colors: custom } : { color_scheme: sc.k })}>
              <span>{sc.label}</span>
              <span class="mct-swatches">{sc.colors.map((_c, i) => <MoodIcon key={i} mood={{ id: 0, level: i + 1, name: '', icon: 'm' + (i + 1), position: 0, archived: false }} size={26} colors={sc.colors} />)}</span>
            </button>
          ))}
        </div>
        {s.color_scheme === 'eigen' && (
          <div class="mct-customcolors">
            {custom.map((c, i) => (
              <label key={i}><span>{names[i]}</span><input type="color" value={c} onChange={(e) => { const next = custom.slice(); next[i] = (e.target as HTMLInputElement).value; save({ custom_colors: next }); }} /></label>
            ))}
            <p class="mct-muted small">Jede Stufe bleibt zusätzlich an Symbol und Beschriftung erkennbar. Achte bei eigenen Farben auf genug Kontrast zum Hintergrund.</p>
          </div>
        )}
      </Card>
      <Card title="Schrift">
        <Segmented label="Schriftart" value={s.font} onChange={(v) => save({ font: v })} options={[{ value: 'roboto', label: 'Roboto' }, { value: 'system', label: 'Geräteschrift' }, { value: 'serif', label: 'Serifen' }]} />
      </Card>
    </Page>
  );
}

export function SecurityScreen(): JSX.Element {
  useStore();
  const [a, setA] = useState('');
  const [b, setB] = useState('');
  const [, tick] = useState(0);
  const on = hasPin();
  const submit = async () => {
    if (!/^\d{4,8}$/.test(a)) { toast('Die PIN braucht 4 bis 8 Ziffern.', 'error'); return; }
    if (a !== b) { toast('Die beiden Eingaben stimmen nicht überein.', 'error'); return; }
    await setPin(a);
    setA(''); setB(''); tick((n) => n + 1);
    toast('PIN-Sperre ist eingeschaltet.');
  };
  return (
    <Page title="Sicherheit" back="/mehr">
      <Card title="PIN-Sperre">
        <p>Die PIN sperrt die Oberfläche auf diesem Gerät. Sie ersetzt nicht die Anmeldung an deinem MediClever-Konto und gilt nur in diesem Browser.</p>
        {on ? (
          <button type="button" class="mct-btn ghost danger" onClick={() => { clearPin(); tick((n) => n + 1); toast('PIN-Sperre ist ausgeschaltet.'); }}>PIN-Sperre ausschalten</button>
        ) : (
          <form onSubmit={(e) => { e.preventDefault(); submit(); }}>
            <Field label="Neue PIN (4 bis 8 Ziffern)"><input type="password" inputMode="numeric" autoComplete="new-password" value={a} maxLength={8} onInput={(e) => setA((e.target as HTMLInputElement).value)} /></Field>
            <Field label="PIN wiederholen"><input type="password" inputMode="numeric" autoComplete="new-password" value={b} maxLength={8} onInput={(e) => setB((e.target as HTMLInputElement).value)} /></Field>
            <button type="submit" class="mct-btn primary">PIN-Sperre einschalten</button>
          </form>
        )}
        <p class="mct-muted small">Vergessene PIN: Melde dich ab und wieder an oder lösche die Websitedaten dieses Browsers. Deine Einträge bleiben erhalten.</p>
      </Card>
    </Page>
  );
}

export function SettingsScreen(): JSX.Element {
  const s = useStore().boot!.settings;
  useEffect(() => {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (tz && tz !== s.timezone) post('/settings', { timezone: tz }).then(changed, () => undefined);
     
  }, []);
  return (
    <Page title="Einstellungen" back="/mehr">
      <Card title="Woche">
        <span class="mct-field-label">Wochenanfang</span>
        <Segmented label="Wochenanfang" value={s.week_start} onChange={(v) => save({ week_start: v })} options={[{ value: 1, label: 'Montag' }, { value: 0, label: 'Sonntag' }, { value: 6, label: 'Samstag' }]} />
      </Card>
      <Card title="Einträge nach Mitternacht">
        <p class="mct-muted">Nach einer Spätschicht gehört der Eintrag oft noch zum Vortag.</p>
        <Segmented label="Einträge nach Mitternacht" value={s.midnight_rule} onChange={(v) => save({ midnight_rule: v })} options={[{ value: 'ask', label: 'Fragen' }, { value: 'prev', label: 'Vortag' }, { value: 'current', label: 'Aktueller Tag' }]} />
        <Field label="Gilt bis (Uhrzeit, volle Stunde)"><input type="number" min={1} max={8} value={s.midnight_until} onChange={(e) => save({ midnight_until: Number((e.target as HTMLInputElement).value) })} /></Field>
      </Card>
      <Card title="Startseite">
        <Toggle label="Satz des Tages" hint="Ein kurzer, ruhiger Satz über den Einträgen." checked={s.quotes} onChange={(v) => save({ quotes: v })} />
        {feature('lookback') && <Toggle label="Rückblick" hint="Hinweis auf Einträge vom selben Tag früherer Jahre." checked={s.lookback} onChange={(v) => save({ lookback: v })} />}
      </Card>
      <p class="mct-muted small">Zeitzone: {s.timezone}. Sie wird von diesem Gerät übernommen.</p>
    </Page>
  );
}

// ---- KI-Zugriff und Konto -------------------------------------------------

export function AiScreen(): JSX.Element {
  const boot = useStore().boot!;
  const { data, reload } = useLoad(() => get<Grant[]>('/grants'), []);
  const on = !!boot.settings.ai_consent_at;
  const setConsent = async (v: boolean) => {
    if (!v && !(await confirmDelete('KI-Einwilligung widerrufen?', 'Alle verbundenen Assistenten verlieren sofort den Zugriff.', 'Widerrufen'))) return;
    try { await post('/consent/ai', { consent: v }); await changed(); reload(); } catch (e) { fail(e); }
  };
  const revoke = async (g: Grant) => {
    if (!(await confirmDelete('Freigabe widerrufen?', `„${g.client}“ verliert sofort den Zugriff.`, 'Widerrufen'))) return;
    try { await del(`/grants/${g.id}`); toast('Freigabe widerrufen.'); reload(); } catch (e) { fail(e); }
  };
  const SC: Record<string, string> = { lesen: 'Lesen', schreiben: 'Schreiben', loeschen: 'Löschen' };
  return (
    <Page title="KI-Zugriff" back="/mehr">
      <Card title="Einwilligung">
        <p>Du kannst einen KI-Assistenten mit deinem Tagebuch verbinden. Er legt dann in deinem Namen Einträge an und liest sie.</p>
        <div class="mct-hint warn"><strong>Wichtig:</strong> Einträge, die der Assistent liest, gehen an den Anbieter des verbundenen Assistenten und werden dort nach dessen Regeln verarbeitet. Das können Gesundheitsdaten sein.</div>
        <Toggle label="Ich willige in den KI-Zugriff ein" hint="Standardmäßig aus. Jederzeit widerrufbar." checked={on} onChange={setConsent} />
      </Card>
      <Card title="So verbindest du einen Assistenten">
        <ol class="mct-steps-list">
          <li>Öffne in deinem Assistenten die Einstellungen für eigene Konnektoren.</li>
          <li>Füge diese Adresse hinzu:</li>
        </ol>
        <div class="mct-copy"><code>{boot.mcp_url}</code>
          <button type="button" class="mct-btn small ghost" onClick={() => navigator.clipboard.writeText(boot.mcp_url).then(() => toast('Adresse kopiert.'), () => toast('Kopieren nicht möglich.', 'error'))}><Icon name="copy" size={16} />Kopieren</button></div>
        <p class="mct-muted small">Beim Verbinden wählst du die Rechte: Lesen, Schreiben, Löschen. Löschen ist standardmäßig gesperrt. Fotos und Sprachnotizen sind für Assistenten nicht zugänglich.</p>
      </Card>
      <Card title="Verbundene Assistenten">
        {!data ? <Loading /> : data.length === 0 ? <p class="mct-muted">Noch kein Assistent verbunden.</p> : (
          <ul class="mct-grants">
            {data.map((g) => (
              <li key={g.id} class={g.revoked ? 'off' : ''}>
                <div><strong>{g.client}</strong><small>Rechte: {g.scopes.map((x) => SC[x] || x).join(', ')}</small>
                  <small>Freigegeben am {shortDate(g.granted_at.slice(0, 10))} · {g.last_used_at ? `zuletzt genutzt am ${shortDate(g.last_used_at.slice(0, 10))}` : 'noch nicht genutzt'}</small></div>
                {g.revoked ? <span class="mct-muted">widerrufen</span> : <button type="button" class="mct-btn small ghost danger" onClick={() => revoke(g)}>Widerrufen</button>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </Page>
  );
}

export function AccountScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [word, setWord] = useState('');
  const [open, setOpen] = useState<'wipe' | 'delete' | null>(null);
  const consent = async (v: boolean) => {
    if (!v && !(await confirmDelete('Einwilligung widerrufen?', 'Ohne Einwilligung kannst du keine neuen Einträge speichern. Deine vorhandenen Einträge bleiben, bis du sie löschst.', 'Widerrufen'))) return;
    try { await post('/consent', { consent: v, adult: true }); await changed(); } catch (e) { fail(e); }
  };
  const run = async () => {
    try {
      if (open === 'wipe') { await post('/account/wipe', { confirm: word }); toast('Dein Tagebuch ist leer.'); }
      else { await del('/account', { confirm: word }); toast('Alle Tagebuchdaten wurden gelöscht.'); }
      setOpen(null); setWord('');
      await changed();
      go('/eintraege');
    } catch (e) { fail(e); }
  };
  const need = open === 'wipe' ? 'LEEREN' : 'LÖSCHEN';
  return (
    <Page title="MediClever-Konto" back="/mehr">
      <Card title={boot.user.name}>
        <p>Das Tagebuch gehört zu deinem MediClever-Konto. Jede Einrichtung und jeder Eintrag gilt nur für dieses Konto.</p>
        <div class="mct-btnrow wrap"><a class="mct-btn ghost" href={cfg.account}>Konto verwalten</a><a class="mct-btn ghost" href={cfg.logout}><Icon name="log-out" size={18} />Abmelden</a></div>
      </Card>
      <Card title="Einwilligung">
        <p>Stimmung, Schlaf, Schmerzen, Krankheitstage und Beziehungsleben sind besonders geschützte Angaben. Wir speichern sie nur mit deiner ausdrücklichen Einwilligung und nur für dein Tagebuch.</p>
        <Toggle label="Ich willige in die Verarbeitung meiner Tagebuchdaten ein" checked={!!boot.settings.consent_at} onChange={consent} />
        <p class="mct-muted small">Bitte trag keine Patienten- und Einsatzdaten ein. Nutzung ab 18 Jahren.</p>
      </Card>
      <Card title="Daten löschen">
        <p>Beides lässt sich nicht rückgängig machen. Sichere deine Daten vorher unter „Daten“.</p>
        <div class="mct-btnrow wrap">
          <button type="button" class="mct-btn ghost danger" onClick={() => setOpen('wipe')}>Tagebuch leeren</button>
          <button type="button" class="mct-btn ghost danger" onClick={() => setOpen('delete')}>Alle Tagebuchdaten löschen</button>
        </div>
      </Card>
      {open && (
        <Sheet title={open === 'wipe' ? 'Tagebuch leeren' : 'Alle Tagebuchdaten löschen'} onClose={() => { setOpen(null); setWord(''); }}
          footer={<button type="button" class="mct-btn danger" disabled={word !== need} onClick={run}>Endgültig {open === 'wipe' ? 'leeren' : 'löschen'}</button>}>
          <p>{open === 'wipe'
            ? 'Alle Einträge, Fotos, Ziele, wichtigen Tage und deine Einrichtung werden gelöscht. Einstellungen und Einwilligung bleiben.'
            : 'Alle Einträge, Fotos, Ziele, wichtigen Tage, die Einrichtung, KI-Freigaben, das Protokoll, Einstellungen und Einwilligungen werden gelöscht. Dein MediClever-Konto bleibt bestehen.'}</p>
          <Field label={`Zur Bestätigung tippe ${need}`}><input type="text" value={word} autoComplete="off" onInput={(e) => setWord((e.target as HTMLInputElement).value.toUpperCase())} /></Field>
        </Sheet>
      )}
    </Page>
  );
}
