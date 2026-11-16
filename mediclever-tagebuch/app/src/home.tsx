import type { JSX } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import { get, post, del, assetUrl } from './api';
import { state, useStore, useLoad, setMonth, go, changed, fail, toast, lookup, feature, type Lookup } from './store';
import { Icon } from './icons';
import { Card, Empty, Loading, Sheet, Chip, MoodIcon, confirmDelete, Field } from './ui';
import { addDays, addMonths, color, dayLabel, dayLabelYear, diffDays, duration, monthLabel, MONTHS, num, plural, quoteFor } from './format';
import type { Entry, Goal, Home, Milestone, Scale, ScaleValue } from './types';

/** Kopfzeile der Hauptbereiche: Monat vor und zurück, Monatsauswahl, Suche. */
export function MonthBar({ title }: { title: string }): JSX.Element {
  const s = useStore();
  const [pick, setPick] = useState(false);
  const counts = new Map((s.boot?.months || []).map((m) => [m.month, Number(m.n)]));
  const [year, setYear] = useState(Number(s.month.slice(0, 4)));
  const thisMonth = s.boot!.today.slice(0, 7);
  return (
    <header class="mct-top">
      <h1 class="mct-sr">{title}</h1>
      <div class="mct-monthbar">
        <button type="button" class="mct-iconbtn" aria-label="Vorheriger Monat" onClick={() => setMonth(addMonths(s.month, -1))}><Icon name="chevron-left" /></button>
        <button type="button" class="mct-month" aria-haspopup="dialog" onClick={() => { setYear(Number(s.month.slice(0, 4))); setPick(true); }}>
          {monthLabel(s.month)}<Icon name="chevron-down" size={18} />
        </button>
        <button type="button" class="mct-iconbtn" aria-label="Nächster Monat" disabled={s.month >= thisMonth} onClick={() => setMonth(addMonths(s.month, 1))}><Icon name="chevron-right" /></button>
      </div>
      <div class="mct-top-actions">
        {feature('search') && <a class="mct-iconbtn" href="#/suche" aria-label="Suche"><Icon name="search" /></a>}
      </div>
      {pick && (
        <Sheet title="Monat wählen" onClose={() => setPick(false)}>
          <div class="mct-yearbar">
            <button type="button" class="mct-iconbtn" aria-label="Vorheriges Jahr" onClick={() => setYear(year - 1)}><Icon name="chevron-left" /></button>
            <strong aria-live="polite">{year}</strong>
            <button type="button" class="mct-iconbtn" aria-label="Nächstes Jahr" disabled={year >= Number(thisMonth.slice(0, 4))} onClick={() => setYear(year + 1)}><Icon name="chevron-right" /></button>
          </div>
          <div class="mct-monthgrid">
            {MONTHS.map((name, i) => {
              const m = `${year}-${String(i + 1).padStart(2, '0')}`;
              const n = counts.get(m) || 0;
              return (
                <button type="button" key={m} disabled={m > thisMonth} class={m === s.month ? 'on' : ''} aria-pressed={m === s.month} onClick={() => { setMonth(m); setPick(false); }}>
                  <span>{name}</span><small>{n > 0 ? plural(n, 'Eintrag', 'Einträge') : 'leer'}</small>
                </button>
              );
            })}
          </div>
        </Sheet>
      )}
    </header>
  );
}

export function scaleText(v: ScaleValue, scale: Scale | undefined): string {
  if (!scale) return '';
  if (v.point_id !== null) return scale.points.find((p) => p.id === v.point_id)?.label || '';
  if (v.minutes !== null) return duration(v.minutes);
  return v.num !== null ? num(v.num, Number.isInteger(v.num) ? 0 : 1) : '';
}

export function Lightbox({ ids, start, onClose }: { ids: number[]; start: number; onClose: () => void }): JSX.Element {
  const [i, setI] = useState(start);
  return (
    <Sheet title={`Foto ${i + 1} von ${ids.length}`} onClose={onClose} wide>
      <img class="mct-lightbox" src={assetUrl(ids[i], 'm')} alt={`Foto ${i + 1} von ${ids.length} zu diesem Eintrag`} />
      {ids.length > 1 && (
        <div class="mct-btnrow center">
          <button type="button" class="mct-btn ghost" disabled={i === 0} onClick={() => setI(i - 1)}>Zurück</button>
          <button type="button" class="mct-btn ghost" disabled={i === ids.length - 1} onClick={() => setI(i + 1)}>Weiter</button>
        </div>
      )}
    </Sheet>
  );
}

/** Eintragskarte: Stimmung, Wochentag und Datum, Uhrzeit, Aktivitäten, Skalenwerte, Notiz, Fotos, erreichte Ziele. */
export function EntryCard({ e, l, goals, showYear }: { e: Entry; l: Lookup; goals?: Goal[]; showYear?: boolean }): JSX.Element {
  const mood = l.moods.get(e.mood_id);
  const [menu, setMenu] = useState(false);
  const [photo, setPhoto] = useState<number | null>(null);
  const photos = e.assets.filter((a) => a.type === 'photo').map((a) => a.id);
  const audio = e.assets.filter((a) => a.type === 'audio');
  const done = (goals || []).filter((g) => g.month_checks?.includes(e.date));
  const fav = async () => {
    try { await post(`/entries/${e.id}`, { favorite: !e.favorite }); await changed(); } catch (err) { fail(err); }
  };
  const remove = async () => {
    setMenu(false);
    if (!(await confirmDelete('Eintrag löschen?', `Der Eintrag vom ${dayLabel(e.date)} wird samt Fotos endgültig gelöscht.`))) return;
    try { await del(`/entries/${e.id}`); toast('Eintrag gelöscht.'); await changed(); } catch (err) { fail(err); }
  };
  return (
    <article class="mct-entry" aria-label={`Eintrag vom ${dayLabel(e.date)}, ${e.time} Uhr, Stimmung ${mood?.name || ''}`}>
      <div class="mct-entry-head">
        <MoodIcon mood={mood} size={40} />
        <div class="mct-entry-meta">
          <span class="mct-entry-day">{showYear ? dayLabelYear(e.date) : dayLabel(e.date)}</span>
          <span class="mct-entry-mood"><strong>{mood?.name}</strong> · {e.time} Uhr</span>
        </div>
        {feature('favorites') && (
          <button type="button" class={'mct-iconbtn' + (e.favorite ? ' fav' : '')} aria-pressed={e.favorite} aria-label="Als Favorit markieren" onClick={fav}>
            <Icon name="star" />
          </button>
        )}
        <button type="button" class="mct-iconbtn" aria-label="Eintrag bearbeiten oder löschen" aria-haspopup="dialog" onClick={() => setMenu(true)}><Icon name="ellipsis" /></button>
      </div>
      {e.tags.length > 0 && (
        <div class="mct-chips">
          {e.tags.map((id) => { const t = l.tags.get(id); return t ? <Chip key={id} icon={t.icon} label={t.name} color={color(t.color || l.groups.get(t.group_id || 0)?.color)} /> : null; })}
        </div>
      )}
      {e.scales.length > 0 && (
        <div class="mct-tiles">
          {e.scales.map((v) => { const sc = l.scales.get(v.scale_id); return sc ? <div class="mct-tile" key={v.scale_id}><Icon name={sc.icon} size={18} color={color(sc.color)} /><span><small>{sc.name}</small>{scaleText(v, sc)}</span></div> : null; })}
        </div>
      )}
      {e.title && <h3 class="mct-entry-title">{e.title}</h3>}
      {/* Notizen sind serverseitig durch eine Positivliste erlaubter Elemente gelaufen. */}
      {e.note && <div class="mct-note" dangerouslySetInnerHTML={{ __html: e.note }} />}
      {photos.length > 0 && (
        <div class={'mct-photos n' + Math.min(photos.length, 4)}>
          {photos.map((id, i) => <button type="button" key={id} onClick={() => setPhoto(i)} aria-label={`Foto ${i + 1} von ${photos.length} vergrößern`}><img src={assetUrl(id, 's')} alt="" loading="lazy" /></button>)}
        </div>
      )}
      {audio.map((a) => <audio key={a.id} controls preload="none" src={assetUrl(a.id, 'full')} aria-label="Sprachnotiz" />)}
      {done.length > 0 && <div class="mct-chips">{done.map((g) => <Chip key={g.id} icon="circle-check" label={g.name} color="var(--ok)" />)}</div>}
      {photo !== null && <Lightbox ids={photos} start={photo} onClose={() => setPhoto(null)} />}
      {menu && (
        <Sheet title="Eintrag" onClose={() => setMenu(false)}>
          <div class="mct-rows">
            <button type="button" class="mct-row" onClick={() => go(`/eintrag/${e.id}`)}><span class="mct-row-icon"><Icon name="pencil" /></span><span class="mct-row-text">Bearbeiten</span></button>
            <button type="button" class="mct-row danger" onClick={remove}><span class="mct-row-icon"><Icon name="trash" /></span><span class="mct-row-text">Löschen</span></button>
          </div>
        </Sheet>
      )}
    </article>
  );
}

function GoalsToday({ goals, today }: { goals: Goal[]; today: string }): JSX.Element | null {
  const due = goals.filter((g) => g.due_today);
  if (!due.length) return null;
  const toggle = async (g: Goal) => {
    try { await post(`/goals/${g.id}/check`, { date: today, done: !g.done_today }); await changed(); } catch (e) { fail(e); }
  };
  return (
    <section class="mct-today" aria-label="Ziele für heute">
      <h2>Heute</h2>
      <div class="mct-goalchips">
        {due.map((g) => (
          <button type="button" key={g.id} class={g.done_today ? 'on' : ''} aria-pressed={g.done_today} onClick={() => toggle(g)}>
            <Icon name={g.done_today ? 'circle-check' : 'target'} size={18} />{g.name}{g.streak > 1 && <small>Serie {g.streak}</small>}
          </button>
        ))}
      </div>
    </section>
  );
}

function milestonesIn(ms: Milestone[], month: string): { m: Milestone; date: string; years: number }[] {
  const out: { m: Milestone; date: string; years: number }[] = [];
  for (const m of ms) {
    const md = m.date.slice(5);
    if (m.date.slice(0, 7) === month) out.push({ m, date: m.date, years: 0 });
    else if (m.yearly && md.slice(0, 2) === month.slice(5) && m.date < month) out.push({ m, date: `${month}-${md.slice(3)}`, years: Number(month.slice(0, 4)) - Number(m.date.slice(0, 4)) });
  }
  return out;
}

export function HomeScreen(): JSX.Element {
  const s = useStore();
  const boot = s.boot!;
  const month = s.month;
  const { data, error, loading, reload } = useLoad(() => get<Home>('/home', { month }), [month]);
  const [quoteOff, setQuoteOff] = useState(false);
  const [notices, setNotices] = useState<{ title: string; text: string }[]>([]);
  const l = lookup(boot);
  const isNow = month === boot.today.slice(0, 7);

  useEffect(() => {
    if (!feature('reminders')) return;
    get<{ notices: { title: string; text: string }[] }>('/push/due').then((r) => setNotices(r.notices), () => undefined);
  }, [s.version]);

  if (!data) return <><MonthBar title="Einträge" /><div class="mct-content"><Loading error={loading ? null : error} reload={reload} /></div></>;

  const items: JSX.Element[] = [];
  const marks = milestonesIn(data.milestones, month);
  const lastDay = isNow ? boot.today : `${month}-${String(new Date(Number(month.slice(0, 4)), Number(month.slice(5)), 0).getDate()).padStart(2, '0')}`;
  let cursor = addDays(lastDay, 1);
  const gap = (from: string, to: string) => {
    const missing = diffDays(from, to) - 1;
    if (missing > 0) {
      const d = addDays(from, -1);
      items.push(
        <div class="mct-gap" key={'gap' + from}>
          <span>{missing === 1 ? '1 Tag fehlt' : `${missing} Tage fehlen`}</span>
          <a class="mct-btn small ghost" href={`#/eintrag/neu?date=${d}`} aria-label={`Eintrag für ${dayLabel(d)} nachtragen`}><Icon name="plus" size={16} />Nachtragen</a>
        </div>,
      );
    }
  };
  const pushMarks = (after: string, before: string) => {
    marks.filter((x) => x.date > after && x.date <= before).forEach((x) => items.push(
      <a class="mct-mark" key={'ms' + x.m.id + x.date} href="#/mehr/wichtige-tage">
        <Icon name="flag" size={20} /><span><strong>{x.m.name}</strong><small>{dayLabel(x.date)}{x.years > 0 ? ` · ${x.years}. Jahrestag` : ''}</small></span>
      </a>,
    ));
  };
  let prevDate = '';
  for (const e of data.entries) {
    if (e.date !== prevDate) {
      pushMarks(addDays(e.date, -1), addDays(cursor, -1));
      gap(cursor, e.date);
      cursor = e.date;
      prevDate = e.date;
    }
    items.push(<EntryCard key={e.id} e={e} l={l} goals={data.goals} />);
  }
  pushMarks(addDays(`${month}-01`, -1), addDays(cursor, -1));
  if (data.entries.length) gap(cursor, addDays(`${month}-01`, -1));

  return (
    <>
      <MonthBar title="Einträge" />
      <div class="mct-content">
        {notices.map((n, i) => (
          <div class="mct-notice" key={i} role="status"><Icon name="bell" size={20} /><span><strong>{n.title}</strong> {n.text}</span>
            <a class="mct-btn small primary" href={`#/eintrag/neu?date=${boot.today}`}>Eintragen</a></div>
        ))}
        {isNow && boot.settings.quotes && !quoteOff && (
          <aside class="mct-quote" aria-label="Satz des Tages">
            <p>{quoteFor(boot.today)}</p>
            <button type="button" class="mct-iconbtn" aria-label="Satz des Tages ausblenden" onClick={() => setQuoteOff(true)}><Icon name="x" size={18} /></button>
          </aside>
        )}
        {isNow && feature('goals') && <GoalsToday goals={data.goals} today={boot.today} />}
        {isNow && data.lookback && (
          <a class="mct-mark" href="#/mehr/rueckblick"><Icon name="rotate-ccw" size={20} />
            <span><strong>Vor {data.lookback.years_ago === 1 ? '1 Jahr' : `${data.lookback.years_ago} Jahren`}</strong>
              <small>{plural(data.lookback.count, 'Eintrag', 'Einträge')} von diesem Tag{data.lookback.years > 1 ? ' · weitere aus früheren Jahren' : ''}</small></span></a>
        )}
        {!isNow && <button type="button" class="mct-btn ghost block" onClick={() => setMonth(boot.today.slice(0, 7))}><Icon name="calendar" size={18} />Heute anzeigen</button>}
        {!isNow && feature('reports') && data.entries.length > 0 && (
          <a class="mct-mark" href={`#/mehr/berichte?type=month&date=${month}-15`}><Icon name="file-text" size={20} />
            <span><strong>Monatsbericht {monthLabel(month)}</strong><small>{plural(data.entries.length, 'Eintrag', 'Einträge')} im Überblick</small></span></a>
        )}
        {data.entries.length === 0 ? (
          <Empty title={isNow ? 'Noch kein Eintrag in diesem Monat' : `Kein Eintrag im ${monthLabel(month)}`}
            text={isNow ? 'Ein Eintrag dauert etwa eine Minute: Stimmung wählen, antippen, was los war, fertig.' : 'Du kannst Tage jederzeit nachtragen.'}>
            <a class="mct-btn primary" href={`#/eintrag/neu?date=${isNow ? boot.today : month + '-01'}`}><Icon name="plus" size={18} />{isNow ? 'Ersten Eintrag schreiben' : 'Tag nachtragen'}</a>
          </Empty>
        ) : <div class="mct-feed">{items}</div>}
      </div>
    </>
  );
}

/** Suche nach Text, Stimmung und Aktivität. */
export function SearchScreen({ favorites }: { favorites?: boolean }): JSX.Element {
  const s = useStore();
  const boot = s.boot!;
  const l = lookup(boot);
  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  const [moods, setMoods] = useState<number[]>([]);
  const [tags, setTags] = useState<number[]>([]);
  const [page, setPage] = useState(1);
  const [pickTag, setPickTag] = useState(false);
  useEffect(() => { const t = setTimeout(() => { setQ(text); setPage(1); }, 300); return () => clearTimeout(t); }, [text]);
  const active = favorites || q.trim() !== '' || moods.length > 0 || tags.length > 0;
  const { data, error, loading, reload } = useLoad(
    () => (active ? get<{ total: number; page: number; pages: number; entries: Entry[] }>('/entries', { text: q, moods: moods.join(','), tags: tags.join(','), favorite: favorites, page }) : Promise.resolve(null)),
    [q, moods.join(','), tags.join(','), page, favorites, active],
  );
  const toggle = (list: number[], id: number, set: (v: number[]) => void) => { set(list.includes(id) ? list.filter((x) => x !== id) : [...list, id]); setPage(1); };
  return (
    <>
      <header class="mct-top">
        <a class="mct-iconbtn" href={favorites ? '#/mehr' : '#/eintraege'} aria-label="Zurück"><Icon name="chevron-left" /></a>
        <h1>{favorites ? 'Favoriten' : 'Suche'}</h1>
      </header>
      <div class="mct-content">
        {!favorites && (
          <Card>
            <Field label="Text in Notiz oder Titel"><input type="search" value={text} placeholder="Suchbegriff" onInput={(e) => setText((e.target as HTMLInputElement).value)} /></Field>
            <div class="mct-filterrow" role="group" aria-label="Nach Stimmung filtern">
              {boot.setup.moods.map((m) => (
                <button type="button" key={m.id} class={'mct-moodbtn small' + (moods.includes(m.id) ? ' on' : '')} aria-pressed={moods.includes(m.id)} aria-label={'Stimmung ' + m.name} title={m.name}
                  onClick={() => toggle(moods, m.id, setMoods)}><MoodIcon mood={m} size={26} /></button>
              ))}
            </div>
            <div class="mct-chips">
              {tags.map((id) => <Chip key={id} icon={l.tags.get(id)?.icon} label={l.tags.get(id)?.name || ''} onRemove={() => toggle(tags, id, setTags)} />)}
              <button type="button" class="mct-btn small ghost" onClick={() => setPickTag(true)}><Icon name="plus" size={16} />Aktivität</button>
            </div>
          </Card>
        )}
        {!active && <Empty icon="search" title="Wonach suchst du?" text="Gib einen Begriff ein oder wähle Stimmung und Aktivität." />}
        {active && !data && <Loading error={loading ? null : error} reload={reload} />}
        {active && data && (
          <>
            <p class="mct-count" role="status">{data.total === 0 ? 'Keine Treffer.' : plural(data.total, 'Treffer', 'Treffer')}</p>
            {data.total === 0 && favorites && <Empty icon="star" title="Noch keine Favoriten" text="Markiere einen Eintrag mit dem Stern, dann findest du ihn hier wieder." />}
            <div class="mct-feed">{data.entries.map((e) => <EntryCard key={e.id} e={e} l={l} showYear />)}</div>
            {data.pages > 1 && (
              <nav class="mct-btnrow center" aria-label="Seiten">
                <button type="button" class="mct-btn ghost" disabled={page <= 1} onClick={() => setPage(page - 1)}>Zurück</button>
                <span>Seite {data.page} von {data.pages}</span>
                <button type="button" class="mct-btn ghost" disabled={page >= data.pages} onClick={() => setPage(page + 1)}>Weiter</button>
              </nav>
            )}
          </>
        )}
      </div>
      {pickTag && <TagPickSheet exclude={tags} onPick={(id) => { toggle(tags, id, setTags); setPickTag(false); }} onClose={() => setPickTag(false)} />}
    </>
  );
}

export function TagPickSheet({ exclude, onPick, onClose }: { exclude: number[]; onPick: (id: number) => void; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [q, setQ] = useState('');
  const hits = boot.setup.tags.filter((t) => !exclude.includes(t.id) && t.name.toLowerCase().includes(q.toLowerCase()));
  return (
    <Sheet title="Aktivität wählen" onClose={onClose}>
      <Field label="Aktivität suchen"><input type="search" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} /></Field>
      <div class="mct-rows scroll">
        {hits.map((t) => <button type="button" class="mct-row" key={t.id} onClick={() => onPick(t.id)}><span class="mct-row-icon"><Icon name={t.icon} /></span>
          <span class="mct-row-text">{t.name}{t.archived && <small>archiviert</small>}</span></button>)}
        {hits.length === 0 && <p class="mct-muted">Keine Aktivität gefunden.</p>}
      </div>
    </Sheet>
  );
}

