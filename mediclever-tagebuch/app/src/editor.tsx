import type { JSX } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { get, post, del, assetUrl, ApiError } from './api';
import { state, useStore, go, changed, fail, toast, feature, type Route } from './store';
import { Icon } from './icons';
import { Field, IconPicker, Loading, MoodIcon, Sheet, ask } from './ui';
import { addDays, color, dayLabel, duration, LEVEL_NAMES, nowTime } from './format';
import type { Entry, Goal, Group, Scale, ScaleValue, Tag } from './types';

interface Draft {
  date: string; time: string; mood_id: number | null; tag_ids: number[]; scales: Record<number, ScaleValue>; title: string; note: string;
  assets: { id: number; type: 'photo' | 'audio' }[];
}

const isPlain = (html: string): boolean => !/<(?!br\s*\/?>)[a-z]/i.test(html);
const toPlain = (html: string): string => html.replace(/<br\s*\/?>/gi, ' ').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
const escape = (s: string): string => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Notizfeld mit Formatierung (fett, Listen). Der Server lässt nur eine Positivliste von Elementen durch. */
export function RichText({ value, onChange }: { value: string; onChange: (html: string) => void }): JSX.Element {
  const ref = useRef<HTMLDivElement>(null);
  // Der Inhalt wird nur von außen gesetzt (erstes Öffnen, Vorlage), nie während des Tippens:
  // Sonst träfe ein verspäteter älterer Stand ein und Schreibmarke wie Text gingen verloren.
  useEffect(() => {
    const el = ref.current;
    if (el && document.activeElement !== el && el.innerHTML !== value) el.innerHTML = value;
  }, [value]);
  const emit = () => onChange(ref.current?.innerHTML || '');
  const cmd = (c: string) => { if (document.activeElement !== ref.current) ref.current?.focus(); document.execCommand(c); emit(); };
  const keep = (e: MouseEvent) => e.preventDefault();
  return (
    <div class="mct-rich">
      <div class="mct-rich-bar" role="toolbar" aria-label="Formatierung">
        <button type="button" onMouseDown={keep} onClick={() => cmd('bold')} aria-label="Fett"><strong>F</strong></button>
        <button type="button" onMouseDown={keep} onClick={() => cmd('insertUnorderedList')} aria-label="Liste"><Icon name="list" size={20} /></button>
        <button type="button" onMouseDown={keep} onClick={() => cmd('insertOrderedList')} aria-label="Nummerierte Liste"><Icon name="list-ordered" size={20} /></button>
      </div>
      <div ref={ref} class="mct-rich-text" contentEditable role="textbox" aria-multiline="true" aria-label="Notiz" tabIndex={0} onInput={emit} />
    </div>
  );
}

function NoteSheet({ draft, set, onClose }: { draft: Draft; set: (d: Partial<Draft>) => void; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [templates, setTemplates] = useState(false);
  return (
    <Sheet title="Notiz" onClose={onClose} wide footer={<button type="button" class="mct-btn primary" onClick={onClose}>Übernehmen</button>}>
      <Field label="Titel (optional)"><input type="text" value={draft.title} maxLength={255} onInput={(e) => set({ title: (e.target as HTMLInputElement).value })} /></Field>
      <RichText value={draft.note} onChange={(note) => set({ note })} />
      {feature('templates') && boot.setup.templates.length > 0 && (
        <button type="button" class="mct-btn ghost small" onClick={() => setTemplates(true)}><Icon name="notebook-pen" size={16} />Schreibvorlage einfügen</button>
      )}
      {templates && (
        <Sheet title="Schreibvorlage" onClose={() => setTemplates(false)}>
          <div class="mct-rows">
            {boot.setup.templates.map((t) => (
              <button type="button" class="mct-row" key={t.id} onClick={() => { set({ note: draft.note + t.body }); setTemplates(false); }}>
                <span class="mct-row-icon"><Icon name="notebook-pen" /></span><span class="mct-row-text">{t.title}</span>
              </button>
            ))}
          </div>
        </Sheet>
      )}
    </Sheet>
  );
}

function ScaleInput({ scale, value, onChange }: { scale: Scale; value: ScaleValue | undefined; onChange: (v: ScaleValue | null) => void }): JSX.Element {
  const base: ScaleValue = { scale_id: scale.id, point_id: null, num: null, minutes: null };
  const head = <div class="mct-scale-head"><Icon name={scale.icon} size={20} color={color(scale.color)} /><span id={`sc${scale.id}`}>{scale.name}</span></div>;
  if (scale.type === 'text') {
    return (
      <div class="mct-scale">{head}
        <div class="mct-points" role="radiogroup" aria-labelledby={`sc${scale.id}`}>
          {scale.points.map((p) => (
            <button type="button" key={p.id} role="radio" aria-checked={value?.point_id === p.id} class={value?.point_id === p.id ? 'on' : ''}
              onClick={() => onChange(value?.point_id === p.id ? null : { ...base, point_id: p.id })}>{p.label}</button>
          ))}
        </div>
      </div>
    );
  }
  if (scale.type === 'time') {
    const min = scale.min ?? 0, max = scale.max ?? 720;
    const cur = value?.minutes;
    const step = (d: number) => onChange({ ...base, minutes: Math.max(min, Math.min(max, (cur ?? 420 - d) + d)) });
    return (
      <div class="mct-scale">{head}
        <div class="mct-stepper" role="group" aria-labelledby={`sc${scale.id}`}>
          <button type="button" class="mct-iconbtn" aria-label={`${scale.name}: 15 Minuten weniger`} onClick={() => step(-15)}><Icon name="minus" /></button>
          <output aria-live="polite">{cur === null || cur === undefined ? 'kein Wert' : duration(cur)}</output>
          <button type="button" class="mct-iconbtn" aria-label={`${scale.name}: 15 Minuten mehr`} onClick={() => step(15)}><Icon name="plus" /></button>
          {cur !== null && cur !== undefined && <button type="button" class="mct-btn small ghost" onClick={() => onChange(null)}>Leeren</button>}
        </div>
      </div>
    );
  }
  const min = scale.min ?? 0, max = scale.max ?? 10;
  return (
    <div class="mct-scale">{head}
      <div class="mct-range">
        <input type="range" min={min} max={max} step={1} value={value?.num ?? Math.round((min + max) / 2)} aria-labelledby={`sc${scale.id}`}
          aria-valuetext={value?.num === null || value?.num === undefined ? 'kein Wert' : String(value.num)}
          onInput={(e) => onChange({ ...base, num: Number((e.target as HTMLInputElement).value) })} />
        <output>{value?.num ?? '–'}</output>
        {value?.num !== null && value?.num !== undefined && <button type="button" class="mct-btn small ghost" onClick={() => onChange(null)}>Leeren</button>}
      </div>
    </div>
  );
}

function NewTagSheet({ group, onClose, onCreated }: { group: Group | null; onClose: () => void; onCreated: (t: Tag) => void }): JSX.Element {
  const [name, setName] = useState('');
  const [icon, setIcon] = useState('circle');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try { const t = await post<Tag>('/tags', { name, icon, group_id: group?.id ?? null }); await changed(); onCreated(t); } catch (e) { fail(e); }
    setBusy(false);
  };
  return (
    <Sheet title={group ? `Neue Aktivität in „${group.name}“` : 'Neue Aktivität'} onClose={onClose}
      footer={<button type="button" class="mct-btn primary" disabled={busy || !name.trim()} onClick={save}>Anlegen</button>}>
      <Field label="Name"><input type="text" value={name} maxLength={100} onInput={(e) => setName((e.target as HTMLInputElement).value)} /></Field>
      <IconPicker value={icon} onChange={setIcon} />
    </Sheet>
  );
}

function Recorder({ onDone }: { onDone: (blob: Blob) => void }): JSX.Element {
  const [rec, setRec] = useState<MediaRecorder | null>(null);
  const [secs, setSecs] = useState(0);
  useEffect(() => {
    if (!rec) return;
    const t = setInterval(() => setSecs((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [rec]);
  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const r = new MediaRecorder(stream);
      const chunks: Blob[] = [];
      r.ondataavailable = (e) => chunks.push(e.data);
      r.onstop = () => { stream.getTracks().forEach((t) => t.stop()); onDone(new Blob(chunks, { type: r.mimeType || 'audio/webm' })); };
      r.start();
      setSecs(0);
      setRec(r);
    } catch { toast('Das Mikrofon ist nicht freigegeben.', 'error'); }
  };
  if (rec) {
    return <button type="button" class="mct-btn danger" onClick={() => { rec.stop(); setRec(null); }}><Icon name="square" size={18} />Aufnahme beenden ({secs} s)</button>;
  }
  return <button type="button" class="mct-btn ghost" onClick={start}><Icon name="mic" size={18} />Sprachnotiz aufnehmen</button>;
}

export function EditorScreen({ route }: { route: Route }): JSX.Element {
  const s = useStore();
  const boot = s.boot!;
  const editId = route.parts[1] !== 'neu' ? Number(route.parts[1]) : null;
  const [draft, setDraft] = useState<Draft | null>(null);
  const [step, setStep] = useState(1);
  const [busy, setBusy] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [noteOpen, setNoteOpen] = useState(false);
  const [newTag, setNewTag] = useState<{ group: Group | null } | null>(null);
  const [collapsed, setCollapsed] = useState<Record<number, boolean>>(() => Object.fromEntries(boot.setup.groups.map((g) => [g.id, g.collapsed])));
  const [goals, setGoals] = useState<Goal[]>([]);
  const [uploading, setUploading] = useState(0);
  const set = (d: Partial<Draft>) => setDraft((p) => (p ? { ...p, ...d } : p));

  useEffect(() => {
    if (editId) {
      get<Entry>(`/entries/${editId}`).then((e) => {
        setDraft({ date: e.date, time: e.time, mood_id: e.mood_id, tag_ids: e.tags, scales: Object.fromEntries(e.scales.map((v) => [v.scale_id, v])), title: e.title, note: e.note, assets: e.assets });
        setStep(2);
      }, (e) => setLoadError(e instanceof Error ? e.message : 'Fehler'));
      return;
    }
    const fresh = (date: string) => setDraft({ date, time: nowTime(), mood_id: null, tag_ids: [], scales: {}, title: '', note: '', assets: [] });
    const date = route.query.get('date') || boot.today;
    const hour = new Date().getHours();
    // Einträge nach Mitternacht folgen der Einstellung: fragen, Vortag oder aktueller Tag.
    if (!route.query.get('fix') && date === boot.today && hour < boot.settings.midnight_until) {
      if (boot.settings.midnight_rule === 'prev') { fresh(addDays(date, -1)); return; }
      if (boot.settings.midnight_rule === 'ask') {
        fresh(date);
        ask({ title: 'Für welchen Tag?', text: 'Es ist nach Mitternacht. Gehört der Eintrag noch zu gestern?',
          buttons: [{ key: 'today', label: 'Heute', kind: 'ghost' }, { key: 'prev', label: 'Gestern', kind: 'primary' }] })
          .then((k) => { if (k === 'prev') set({ date: addDays(date, -1) }); });
        return;
      }
    }
    fresh(date);
     
  }, [route.path]);

  const date = draft?.date;
  useEffect(() => {
    if (date && feature('goals')) get<Goal[]>('/goals', { month: date.slice(0, 7) }).then((g) => setGoals(g.filter((x) => !x.archived && x.start_date <= date)), () => undefined);
  }, [date, s.version]);

  if (loadError) return <div class="mct-content"><Loading error={loadError} /></div>;
  if (!draft) return <div class="mct-content"><Loading /></div>;

  const moods = boot.setup.moods.filter((m) => !m.archived || m.id === draft.mood_id);
  const tagsOf = (gid: number | null) => boot.setup.tags.filter((t) => t.group_id === gid && (!t.archived || draft.tag_ids.includes(t.id)));
  const blocks = [
    ...boot.setup.groups.filter((g) => !g.archived).map((g) => ({ kind: 'group' as const, pos: g.position, g })),
    ...(feature('scales') ? boot.setup.scales.filter((x) => !x.archived || draft.scales[x.id]).map((x) => ({ kind: 'scale' as const, pos: x.position, x })) : []),
  ].sort((a, b) => a.pos - b.pos);
  const loose = tagsOf(null);

  const save = async () => {
    if (!draft.mood_id) { setStep(1); toast('Bitte wähle zuerst eine Stimmung.', 'error'); return; }
    setBusy(true);
    try {
      const body = { date: draft.date, time: draft.time, mood_id: draft.mood_id, tag_ids: draft.tag_ids, scales: Object.values(draft.scales), title: draft.title,
        note: draft.note, asset_ids: draft.assets.map((a) => a.id), tz_offset: -new Date(`${draft.date}T${draft.time}`).getTimezoneOffset() };
      await post(editId ? `/entries/${editId}` : '/entries', body);
      state.month = draft.date.slice(0, 7);
      await changed();
      toast(editId ? 'Eintrag geändert.' : 'Eintrag gespeichert.');
      go('/eintraege');
    } catch (e) {
      if (e instanceof ApiError && e.code === 'consent_required') go('/mehr/konto');
      fail(e);
    }
    setBusy(false);
  };
  const upload = async (files: FileList | Blob[] | null) => {
    for (const f of Array.from(files || [])) {
      setUploading((n) => n + 1);
      const fd = new FormData();
      fd.append('file', f);
      try { const a = await post<{ id: number; type: 'photo' | 'audio' }>('/assets', fd); setDraft((p) => (p ? { ...p, assets: [...p.assets, a] } : p)); } catch (e) { fail(e); }
      setUploading((n) => n - 1);
    }
  };
  const removeAsset = async (id: number) => {
    set({ assets: draft.assets.filter((a) => a.id !== id) });
    if (!editId) del(`/assets/${id}`).catch(() => undefined);
  };
  const toggleGoal = async (g: Goal) => {
    const done = g.month_checks?.includes(draft.date);
    try { setGoals(await post<Goal[]>(`/goals/${g.id}/check`, { date: draft.date, done: !done })); } catch (e) { fail(e); }
  };
  const saveBtn = (extra = '') => <button type="button" class={'mct-btn primary ' + extra} disabled={busy || uploading > 0} onClick={save}><Icon name="check" size={18} />Speichern</button>;
  const plain = isPlain(draft.note);

  return (
    <>
      <header class="mct-top">
        <button type="button" class="mct-iconbtn" aria-label={step === 2 && !editId ? 'Zurück zur Stimmung' : 'Abbrechen'}
          onClick={() => (step === 2 && !editId ? setStep(1) : go('/eintraege'))}><Icon name={step === 2 && !editId ? 'chevron-left' : 'x'} /></button>
        <h1>{editId ? 'Eintrag bearbeiten' : 'Neuer Eintrag'}</h1>
        <div class="mct-top-actions">{step === 2 && saveBtn('small')}</div>
      </header>
      <div class="mct-content">
        <p class="mct-steps" aria-live="polite">Schritt {step} von 2 · {step === 1 ? 'Wie geht es dir?' : 'Was war los?'}</p>
        {step === 1 && (
          <>
            <div class="mct-when">
              <Field label="Datum"><input type="date" value={draft.date} max={boot.today} onInput={(e) => set({ date: (e.target as HTMLInputElement).value || draft.date })} /></Field>
              <Field label="Uhrzeit"><input type="time" value={draft.time} onInput={(e) => set({ time: (e.target as HTMLInputElement).value || draft.time })} /></Field>
            </div>
            <p class="mct-daylabel">{dayLabel(draft.date)}</p>
            <div class="mct-moods" role="radiogroup" aria-label="Stimmung">
              {[1, 2, 3, 4, 5].map((level) => (
                <div class="mct-moodcol" key={level}>
                  {moods.filter((m) => m.level === level).map((m) => (
                    <button type="button" key={m.id} role="radio" aria-checked={draft.mood_id === m.id} class={'mct-moodbtn' + (draft.mood_id === m.id ? ' on' : '')}
                      onClick={() => { set({ mood_id: m.id }); if (!editId) setStep(2); }}>
                      <MoodIcon mood={m} size={38} /><span>{m.name}</span>
                    </button>
                  ))}
                  {moods.filter((m) => m.level === level).length === 0 && <span class="mct-muted small">{LEVEL_NAMES[level]}</span>}
                </div>
              ))}
            </div>
            <div class="mct-btnrow">
              <a class="mct-link" href="#/mehr/stimmungen">Stimmungen bearbeiten</a>
              <button type="button" class="mct-btn primary" disabled={!draft.mood_id} onClick={() => setStep(2)}>Weiter<Icon name="chevron-right" size={18} /></button>
            </div>
          </>
        )}
        {step === 2 && (
          <>
            <button type="button" class="mct-picked" onClick={() => setStep(1)}>
              <MoodIcon mood={boot.setup.moods.find((m) => m.id === draft.mood_id)} size={34} />
              <span><strong>{boot.setup.moods.find((m) => m.id === draft.mood_id)?.name}</strong><small>{dayLabel(draft.date)} · {draft.time} Uhr</small></span>
              <span class="mct-sr">Stimmung, Datum und Uhrzeit ändern</span>
              <Icon name="pencil" size={18} class="mct-muted" />
            </button>
            {blocks.length === 0 && loose.length === 0 && (
              <div class="mct-hint">Du hast noch keine Aktivitäten. <a class="mct-link" href="#/mehr/aktivitaeten">Jetzt einrichten</a></div>
            )}
            {blocks.map((b) => b.kind === 'scale'
              ? <ScaleInput key={'s' + b.x.id} scale={b.x} value={draft.scales[b.x.id]} onChange={(v) => { const sc = { ...draft.scales }; if (v) sc[b.x.id] = v; else delete sc[b.x.id]; set({ scales: sc }); }} />
              : (
                <section class="mct-group" key={'g' + b.g.id}>
                  <div class="mct-group-head">
                    <button type="button" class="mct-group-toggle" aria-expanded={!collapsed[b.g.id]} onClick={() => setCollapsed({ ...collapsed, [b.g.id]: !collapsed[b.g.id] })}>
                      <span class="mct-dot" style={{ background: color(b.g.color) }} />{b.g.name}
                      <small>{tagsOf(b.g.id).filter((t) => draft.tag_ids.includes(t.id)).length || ''}</small>
                      <Icon name={collapsed[b.g.id] ? 'chevron-down' : 'chevron-up'} size={18} />
                    </button>
                    <button type="button" class="mct-iconbtn" aria-label={`Neue Aktivität in ${b.g.name} anlegen`} onClick={() => setNewTag({ group: b.g })}><Icon name="plus" /></button>
                  </div>
                  {!collapsed[b.g.id] && <TagGrid tags={tagsOf(b.g.id)} group={b.g} selected={draft.tag_ids} onToggle={(id) => set({ tag_ids: draft.tag_ids.includes(id) ? draft.tag_ids.filter((x) => x !== id) : [...draft.tag_ids, id] })} />}
                </section>
              ))}
            {loose.length > 0 && (
              <section class="mct-group">
                <div class="mct-group-head"><span class="mct-group-toggle">Ohne Gruppe</span>
                  <button type="button" class="mct-iconbtn" aria-label="Neue Aktivität ohne Gruppe anlegen" onClick={() => setNewTag({ group: null })}><Icon name="plus" /></button></div>
                <TagGrid tags={loose} group={null} selected={draft.tag_ids} onToggle={(id) => set({ tag_ids: draft.tag_ids.includes(id) ? draft.tag_ids.filter((x) => x !== id) : [...draft.tag_ids, id] })} />
              </section>
            )}
            {feature('goals') && goals.length > 0 && (
              <section class="mct-group">
                <div class="mct-group-head"><span class="mct-group-toggle">Ziele des Tages</span></div>
                <div class="mct-goalchips">
                  {goals.map((g) => { const on = !!g.month_checks?.includes(draft.date); return (
                    <button type="button" key={g.id} class={on ? 'on' : ''} aria-pressed={on} onClick={() => toggleGoal(g)}>
                      <Icon name={on ? 'circle-check' : 'target'} size={18} />{g.name}{g.streak > 0 && <small>Serie {g.streak}</small>}
                    </button>); })}
                </div>
              </section>
            )}
            <section class="mct-group">
              <div class="mct-group-head"><span class="mct-group-toggle">Notiz</span></div>
              {draft.title && <p class="mct-entry-title">{draft.title}</p>}
              {plain
                ? <Field label="Schnelle Notiz"><input type="text" value={toPlain(draft.note)} placeholder="Ein Satz genügt" onInput={(e) => set({ note: escape((e.target as HTMLInputElement).value) })} /></Field>
                : <div class="mct-note preview" dangerouslySetInnerHTML={{ __html: draft.note }} />}
              <button type="button" class="mct-btn ghost small" onClick={() => setNoteOpen(true)}><Icon name="notebook-pen" size={16} />Ganze Notiz mit Titel und Formatierung</button>
            </section>
            {feature('photos') && (
              <section class="mct-group">
                <div class="mct-group-head"><span class="mct-group-toggle">Fotos und Sprachnotiz</span></div>
                {draft.assets.length > 0 && (
                  <div class="mct-thumbs">
                    {draft.assets.map((a) => (
                      <div class="mct-thumb" key={a.id}>
                        {a.type === 'photo' ? <img src={assetUrl(a.id, 's')} alt="Angehängtes Foto" /> : <audio controls preload="none" src={assetUrl(a.id, 'full')} aria-label="Sprachnotiz" />}
                        <button type="button" class="mct-iconbtn" aria-label={a.type === 'photo' ? 'Foto entfernen' : 'Sprachnotiz entfernen'} onClick={() => removeAsset(a.id)}><Icon name="x" size={18} /></button>
                      </div>
                    ))}
                  </div>
                )}
                {uploading > 0 && <p role="status" class="mct-muted">Lädt hoch …</p>}
                <div class="mct-btnrow wrap">
                  <label class="mct-btn ghost"><Icon name="camera" size={18} />Foto aufnehmen
                    <input class="mct-sr" type="file" accept="image/*" capture="environment" onChange={(e) => { upload((e.target as HTMLInputElement).files); (e.target as HTMLInputElement).value = ''; }} /></label>
                  <label class="mct-btn ghost"><Icon name="image-plus" size={18} />Aus Galerie
                    <input class="mct-sr" type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={(e) => { upload((e.target as HTMLInputElement).files); (e.target as HTMLInputElement).value = ''; }} /></label>
                  {feature('voice') && typeof MediaRecorder !== 'undefined' && <Recorder onDone={(b) => upload([b])} />}
                </div>
              </section>
            )}
            <div class="mct-btnrow">
              <a class="mct-link" href="#/mehr/einrichtung">Einrichtung bearbeiten</a>
              {saveBtn()}
            </div>
          </>
        )}
      </div>
      {noteOpen && <NoteSheet draft={draft} set={set} onClose={() => setNoteOpen(false)} />}
      {newTag && <NewTagSheet group={newTag.group} onClose={() => setNewTag(null)} onCreated={(t) => { set({ tag_ids: [...draft.tag_ids, t.id] }); setNewTag(null); }} />}
    </>
  );
}

function TagGrid({ tags, group, selected, onToggle }: { tags: Tag[]; group: Group | null; selected: number[]; onToggle: (id: number) => void }): JSX.Element {
  if (!tags.length) return <p class="mct-muted small">Noch keine Aktivität in dieser Gruppe.</p>;
  return (
    <div class="mct-taggrid" role="group" aria-label={group ? `Aktivitäten in ${group.name}` : 'Aktivitäten ohne Gruppe'}>
      {tags.map((t) => {
        const on = selected.includes(t.id);
        const c = color(t.color || group?.color);
        return (
          <button type="button" key={t.id} aria-pressed={on} class={'mct-tagbtn' + (on ? ' on' : '')} style={{ '--tag': c }} onClick={() => onToggle(t.id)}>
            <span class="mct-tagicon"><Icon name={t.icon} size={24} /></span><span>{t.name}</span>
          </button>
        );
      })}
    </div>
  );
}
