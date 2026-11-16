import type { ComponentChildren, JSX } from 'preact';
import { useRef, useState } from 'preact/hooks';
import { get } from './api';
import { useStore, useLoad, lookup, feature, fail, type Lookup } from './store';
import { Icon } from './icons';
import { Card, Chip, Empty, Loading, MoodIcon, Segmented, Sheet } from './ui';
import { MonthBar, TagPickSheet } from './home';
import { Bars, LineChart, Ring, shareChart, theme } from './charts';
import { color, daysInMonth, duration, LEVEL_NAMES, levelColors, MONTHS, num, parseDate, plural, shortDate, signed, WD_SHORT, addDays, dayLabel } from './format';
import type { CalendarData, Scale, ScaleStat, Stats } from './types';

function StatCard({ title, children, share = true }: { title: string; children: ComponentChildren; share?: boolean }): JSX.Element {
  const ref = useRef<HTMLDivElement>(null);
  return (
    <div ref={ref}>
      <Card title={title} action={share ? (
        <button type="button" class="mct-iconbtn" aria-label={`„${title}“ als Bild teilen`} onClick={() => { if (ref.current) shareChart(ref.current, title).catch(fail); }}><Icon name="share-2" size={20} /></button>
      ) : undefined}>{children}</Card>
    </div>
  );
}

const scoreColor = (colors: string[]) => (y: number) => colors[Math.max(0, Math.min(4, Math.round(5 - y)))];
const AXIS_LEGEND = 'Achse: ++ sehr gut · + gut · o mittel · − schlecht · −− sehr schlecht';
const moodTicks = [1, 2, 3, 4, 5].map((v) => ({ v, label: ['', '−−', '−', 'o', '+', '++'][v] }));

function ScaleCard({ st, scale, month, year }: { st: ScaleStat; scale: Scale; month: string; year: boolean }): JSX.Element {
  const vals = st.series.map((p) => p.value);
  const isTime = scale.type === 'time';
  const yMax = scale.type === 'text' ? scale.points.length : Math.max(...vals, scale.type === 'num' ? scale.max ?? 10 : 60);
  const yMin = scale.type === 'text' ? 1 : 0;
  const fmt = (v: number | null) => (v === null ? '–' : isTime ? duration(v) : scale.type === 'text' ? `${num(v)} von ${scale.points.length}` : num(v));
  const good = st.change === null || st.change === 0 ? null : (st.change > 0) === (scale.trend === 'up');
  const xMax = year ? 12 : daysInMonth(month);
  const pts = year ? monthly(st.series) : st.series.map((p) => ({ x: Number(p.date.slice(8)), y: p.value }));
  return (
    <StatCard title={scale.name}>
      <LineChart points={pts} xMax={xMax} yMin={yMin} yMax={yMax || 1} stroke={color(scale.color)} title={scale.name}
        desc={`Durchschnitt ${fmt(st.avg)} an ${st.n} Tagen`} bars={scale.chart_mode === 'sum'}
        yTicks={scale.type === 'text' ? scale.points.map((p) => ({ v: p.position, label: String(p.position) })) : [yMin, yMax / 2, yMax].map((v) => ({ v, label: isTime ? String(Math.round(v / 60)) + ' h' : String(Math.round(v)) }))}
        xTicks={year ? [1, 4, 7, 10].map((v) => ({ v, label: MONTHS[v - 1].slice(0, 3) })) : [1, 8, 15, 22, 29].filter((v) => v <= xMax).map((v) => ({ v, label: String(v) }))} />
      <p data-share><strong>{scale.chart_mode === 'sum' ? 'Tagessumme' : 'Tagesdurchschnitt'}: {fmt(st.avg)}</strong> an {plural(st.n, 'Tag', 'Tagen')}</p>
      {st.change !== null && (
        <p data-share class={good === null ? '' : good ? 'mct-good' : 'mct-bad'}>
          {isTime ? signed(Math.round(st.change)) + ' Min.' : signed(st.change, 1)} zum {year ? 'Vorjahr' : 'Vormonat'}
          {good !== null && (good ? ' · in die gewünschte Richtung' : ' · gegen die gewünschte Richtung')}
        </p>
      )}
      {scale.type === 'text' && <p class="mct-muted small">{scale.points.map((p) => `${p.position} = ${p.label}`).join(' · ')}</p>}
    </StatCard>
  );
}

function monthly(series: { date: string; value: number }[]): { x: number; y: number }[] {
  const by = new Map<number, number[]>();
  series.forEach((p) => { const m = Number(p.date.slice(5, 7)); by.set(m, [...(by.get(m) || []), p.value]); });
  return [...by.entries()].map(([x, v]) => ({ x, y: v.reduce((a, b) => a + b, 0) / v.length }));
}

export function StatsScreen(): JSX.Element {
  const s = useStore();
  const boot = s.boot!;
  const [period, setPeriod] = useState<'month' | 'year'>('month');
  const [bars, setBars] = useState(false);
  const [overlay, setOverlay] = useState<number | null>(null);
  const [pick, setPick] = useState(false);
  const [allTags, setAllTags] = useState(false);
  const year = s.month.slice(0, 4);
  const { data, error, loading, reload } = useLoad(() => get<Stats>('/stats', period === 'year' ? { year } : { month: s.month }), [period, s.month]);
  const l: Lookup = lookup(boot);
  const colors = levelColors(boot.settings.color_scheme, boot.settings.custom_colors);
  const t = theme();
  if (!data) return <><MonthBar title="Statistiken" /><div class="mct-content"><Loading error={loading ? null : error} reload={reload} /></div></>;
  const isYear = data.period === 'year';
  const xMax = isYear ? 12 : daysInMonth(s.month);
  const moodPts = isYear ? data.months.map((m) => ({ x: Number(m.month.slice(5)), y: m.avg })) : data.days.map((d) => ({ x: Number(d.date.slice(8)), y: d.avg }));
  const marks = overlay && !isYear ? data.days.filter((d) => d.tags.includes(overlay)).map((d) => Number(d.date.slice(8))) : [];
  const maxTag = data.tag_counts[0]?.n || 1;
  const tagName = (id: number) => l.tags.get(id)?.name || 'Gelöscht';
  const tagColor = (id: number) => { const tg = l.tags.get(id); return color(tg?.color || l.groups.get(tg?.group_id || 0)?.color); };
  const wd = [1, 2, 3, 4, 5, 6, 7].map((d) => data.weekday_avg.find((w) => w.weekday === d)!);
  const pos = data.influence.filter((i) => i.delta > 0).slice(0, 4);
  const neg = data.influence.filter((i) => i.delta < 0).slice(-4).reverse();

  return (
    <>
      <MonthBar title="Statistiken" />
      <div class="mct-content">
        <Segmented label="Zeitraum" value={period} onChange={setPeriod} options={[{ value: 'month', label: 'Monat' }, { value: 'year', label: `Jahr ${year}` }]} />
        <div class="mct-grid">
          <Card title="Tage in Folge">
            <div class="mct-big"><strong>{data.streaks.current}</strong><span>{data.streaks.current === 1 ? 'Tag' : 'Tage'} in Folge</span></div>
            <div class="mct-last5" aria-label="Die letzten fünf Tage">
              {data.streaks.last5.map((d) => <span key={d.date} class={d.done ? 'on' : ''} title={dayLabel(d.date)}><Icon name={d.done ? 'check' : 'minus'} size={16} label={`${dayLabel(d.date)}: ${d.done ? 'Eintrag vorhanden' : 'kein Eintrag'}`} /><small>{WD_SHORT[parseDate(d.date).getDay()]}</small></span>)}
            </div>
            <p class="mct-muted">Längste Serie: {plural(data.streaks.longest, 'Tag', 'Tage')}</p>
          </Card>
          {!data.enough ? (
            <Card class="span2">
              <Empty icon="chart-column" title="Zu wenig Daten" text={`Für Auswertungen braucht es mindestens ${data.min_entries} Einträge im Zeitraum. Bisher: ${data.count}.`}>
                <a class="mct-btn primary" href={`#/eintrag/neu?date=${boot.today}`}><Icon name="plus" size={18} />Eintrag schreiben</a>
              </Empty>
            </Card>
          ) : (
            <>
              {data.hints.length > 0 && (
                <Card title="Auf einen Blick">
                  <ul class="mct-hints">{data.hints.map((h) => <li key={h}>{h}</li>)}</ul>
                </Card>
              )}
              <StatCard title="Stimmungsverlauf">
                <LineChart points={moodPts} xMax={xMax} yMin={1} yMax={5} yTicks={moodTicks} colorAt={scoreColor(colors)} stroke={t.muted} marks={marks} bars={bars} title="Stimmungsverlauf"
                  desc={`Durchschnitt ${num(data.avg)} von 5 bei ${data.count} Einträgen`}
                  xTicks={isYear ? [1, 4, 7, 10].map((v) => ({ v, label: MONTHS[v - 1].slice(0, 3) })) : [1, 8, 15, 22, 29].filter((v) => v <= xMax).map((v) => ({ v, label: String(v) }))} />
                <p data-share><strong>Durchschnitt: {num(data.avg)} von 5</strong> · {plural(data.count, 'Eintrag', 'Einträge')}</p>
                <p class="mct-muted small">{AXIS_LEGEND}</p>
                <div class="mct-btnrow wrap">
                  <Segmented label="Darstellung" value={bars ? 'b' : 'l'} onChange={(v) => setBars(v === 'b')} options={[{ value: 'l', label: 'Linie' }, { value: 'b', label: 'Balken' }]} />
                  {!isYear && (overlay
                    ? <Chip icon={l.tags.get(overlay)?.icon} label={tagName(overlay)} onRemove={() => setOverlay(null)} />
                    : <button type="button" class="mct-btn small ghost" onClick={() => setPick(true)}><Icon name="plus" size={16} />Aktivität einblenden</button>)}
                </div>
                {overlay && !isYear && <p class="mct-muted small">Hinterlegte Tage: {tagName(overlay)} ({marks.length})</p>}
              </StatCard>
              <StatCard title="Stimmungszähler">
                <Ring half title="Stimmungszähler" center={String(data.count)} sub="Einträge" parts={[1, 2, 3, 4, 5].map((lv) => ({ value: data.levels[lv] || 0, color: colors[lv - 1], label: LEVEL_NAMES[lv] }))} />
                <div class="mct-legend">
                  {[1, 2, 3, 4, 5].map((lv) => <span key={lv} data-share><MoodIcon mood={{ id: 0, level: lv, name: '', icon: 'm' + lv, position: 0, archived: false }} size={22} colors={colors} />{LEVEL_NAMES[lv]}: <strong>{data.levels[lv] || 0}</strong></span>)}
                </div>
              </StatCard>
              <StatCard title="Aktivitätenzähler">
                {data.tag_counts.length === 0 ? <p class="mct-muted">Keine Aktivitäten im Zeitraum.</p> : (
                  <>
                    <Bars title="Aktivitäten nach Häufigkeit" max={maxTag} rows={data.tag_counts.slice(0, 6).map((x) => ({ label: tagName(x.tag_id), value: x.n, text: String(x.n), color: tagColor(x.tag_id) }))} />
                    {data.tag_counts.slice(0, 6).map((x, i) => <span key={x.tag_id} data-share hidden>{i + 1}. {tagName(x.tag_id)}: {x.n}</span>)}
                    {data.tag_counts.length > 6 && <button type="button" class="mct-btn small ghost" onClick={() => setAllTags(true)}>Alle anzeigen ({data.tag_counts.length})</button>}
                  </>
                )}
              </StatCard>
              <StatCard title="Verteilung nach Gruppen">
                <Ring title="Verteilung nach Gruppen" center={String(data.group_counts.reduce((a, g) => a + g.n, 0))} sub="Aktivitäten"
                  parts={data.group_counts.map((g) => ({ value: g.n, color: color(l.groups.get(g.group_id)?.color), label: l.groups.get(g.group_id)?.name || 'Ohne Gruppe' }))} />
                <div class="mct-legend">
                  {data.group_counts.map((g) => <span key={g.group_id} data-share><span class="mct-dot" style={{ background: color(l.groups.get(g.group_id)?.color) }} />{l.groups.get(g.group_id)?.name || 'Ohne Gruppe'}: <strong>{g.n}</strong></span>)}
                </div>
              </StatCard>
              <StatCard title="Stimmung je Wochentag">
                <LineChart bars points={wd.map((w, i) => ({ x: i + 1, y: w.avg }))} xMax={7} yMin={1} yMax={5} yTicks={moodTicks} colorAt={scoreColor(colors)} title="Durchschnittliche Stimmung je Wochentag"
                  desc={wd.filter((w) => w.avg !== null).map((w) => `${WD_SHORT[w.weekday % 7]} ${num(w.avg)}`).join(', ')}
                  xTicks={wd.map((w, i) => ({ v: i + 1, label: WD_SHORT[w.weekday % 7] }))} />
                {wd.filter((w) => w.avg !== null).map((w) => <span key={w.weekday} data-share hidden>{WD_SHORT[w.weekday % 7]}: {num(w.avg)}</span>)}
                <p class="mct-muted small">{AXIS_LEGEND}</p>
              </StatCard>
              <StatCard title="Einfluss auf die Stimmung">
                {pos.length + neg.length === 0 ? <p class="mct-muted">Dafür braucht es Aktivitäten, die an mehreren Tagen vorkommen.</p> : (
                  <div class="mct-influence">
                    {[...pos, ...neg].map((i) => (
                      <div key={i.tag_id} data-share class={i.delta > 0 ? 'up' : 'down'}>
                        <Icon name={i.delta > 0 ? 'arrow-up' : 'arrow-down'} size={18} /><span>{tagName(i.tag_id)}</span><strong>{signed(i.delta, 1)}</strong><small>{i.n}×</small>
                      </div>
                    ))}
                  </div>
                )}
                <p class="mct-muted small">Unterschied zum Durchschnitt der Einträge ohne die Aktivität. Ein Zusammenhang, keine Ursache.</p>
              </StatCard>
              <StatCard title="Stimmungsstabilität">
                <div class="mct-big"><strong data-share>{data.stability ?? '–'}</strong><span>von 100</span></div>
                <div class="mct-meter" role="img" aria-label={`Stabilität ${data.stability ?? 0} von 100`}><span style={{ width: `${data.stability ?? 0}%` }} /></div>
                <p class="mct-muted small">100 heißt: von Tag zu Tag gleichbleibend. {data.prev_stability !== null && data.stability !== null && `${isYear ? 'Vorjahr' : 'Vormonat'}: ${data.prev_stability}.`}</p>
              </StatCard>
              <StatCard title="Oft zusammen" share={false}>
                {data.often_together.length === 0 ? <p class="mct-muted">Keine Aktivitäten im Zeitraum.</p> : data.often_together.map((o) => (
                  <div class="mct-together" key={o.level}>
                    <MoodIcon mood={{ id: 0, level: o.level, name: '', icon: 'm' + o.level, position: 0, archived: false }} size={26} colors={colors} />
                    <div><small>{LEVEL_NAMES[o.level]}</small><div class="mct-chips">{o.tags.map((x) => <Chip key={x.tag_id} icon={l.tags.get(x.tag_id)?.icon} label={`${tagName(x.tag_id)} ${x.n}×`} color={tagColor(x.tag_id)} />)}</div></div>
                  </div>
                ))}
              </StatCard>
              <StatCard title="Längste Serie guter Tage" share={false}>
                <div class="mct-big"><strong>{data.good_streak.length}</strong><span>{data.good_streak.length === 1 ? 'Tag' : 'Tage'}</span></div>
                {data.good_streak.from && <p class="mct-muted">{shortDate(data.good_streak.from)} bis {shortDate(data.good_streak.to!)}</p>}
                {data.good_streak.tags.length > 0 && <><p class="small">Häufig an diesen Tagen:</p><div class="mct-chips">{data.good_streak.tags.map((id) => <Chip key={id} icon={l.tags.get(id)?.icon} label={tagName(id)} color={tagColor(id)} />)}</div></>}
              </StatCard>
              {feature('scales') && data.scales.map((st) => { const sc = l.scales.get(st.scale_id); return sc ? <ScaleCard key={st.scale_id} st={st} scale={sc} month={s.month} year={isYear} /> : null; })}
              {feature('goals') && data.goals.length > 0 && (
                <Card title="Ziele" action={<a class="mct-link" href="#/mehr/ziele">Alle Ziele</a>}>
                  {data.goals.map((g) => (
                    <div class="mct-goalrow" key={g.id}>
                      <span>{g.name}</span><span class="mct-muted">{plural(g.month_checks?.length || 0, 'Abschluss', 'Abschlüsse')}</span><strong>Serie {g.streak}</strong>
                    </div>
                  ))}
                </Card>
              )}
            </>
          )}
        </div>
      </div>
      {pick && <TagPickSheet exclude={[]} onPick={(id) => { setOverlay(id); setPick(false); }} onClose={() => setPick(false)} />}
      {allTags && (
        <Sheet title="Alle Aktivitäten" onClose={() => setAllTags(false)}>
          <Bars title="Alle Aktivitäten nach Häufigkeit" max={maxTag} rows={data.tag_counts.map((x) => ({ label: tagName(x.tag_id), value: x.n, text: String(x.n), color: tagColor(x.tag_id) }))} />
        </Sheet>
      )}
    </>
  );
}

export function CalendarScreen(): JSX.Element {
  const s = useStore();
  const boot = s.boot!;
  const l = lookup(boot);
  const [moods, setMoods] = useState<number[]>([]);
  const [tags, setTags] = useState<number[]>([]);
  const [add, setAdd] = useState<'tag' | 'mood' | null>(null);
  const { data, error, loading, reload } = useLoad(() => get<CalendarData>('/calendar', { month: s.month, moods: moods.join(','), tags: tags.join(',') }), [s.month, moods.join(','), tags.join(',')]);
  const colors = levelColors(boot.settings.color_scheme, boot.settings.custom_colors);
  const ws = boot.settings.week_start;
  const first = parseDate(`${s.month}-01`);
  const lead = (first.getDay() - ws + 7) % 7;
  const n = daysInMonth(s.month);
  const by = new Map((data?.days || []).map((d) => [d.date, d]));
  const cells: (string | null)[] = [...Array(lead).fill(null), ...Array.from({ length: n }, (_, i) => addDays(`${s.month}-01`, i))];
  while (cells.length % 7) cells.push(null);
  const filtered = moods.length + tags.length > 0;
  return (
    <>
      <MonthBar title="Kalender" />
      <div class="mct-content">
        <Card>
          <div class="mct-chips" role="group" aria-label="Filter">
            {moods.map((id) => <Chip key={'m' + id} icon={l.moods.get(id)?.icon} label={l.moods.get(id)?.name || ''} color={colors[(l.moods.get(id)?.level || 3) - 1]} onRemove={() => setMoods(moods.filter((x) => x !== id))} />)}
            {tags.map((id) => <Chip key={'t' + id} icon={l.tags.get(id)?.icon} label={l.tags.get(id)?.name || ''} onRemove={() => setTags(tags.filter((x) => x !== id))} />)}
            <button type="button" class="mct-btn small ghost" onClick={() => setAdd('mood')}><Icon name="filter" size={16} />{filtered ? 'Stimmung hinzu' : 'Nach Stimmung filtern'}</button>
            <button type="button" class="mct-btn small ghost" onClick={() => setAdd('tag')}><Icon name="plus" size={16} />{filtered ? 'Aktivität hinzu' : 'Nach Aktivität filtern'}</button>
          </div>
          {filtered && data && <p class="mct-count" role="status"><strong>{data.matches}</strong> von {plural(data.count, 'Eintrag', 'Einträgen')} {data.matches === 1 ? 'passt' : 'passen'} zum Filter.</p>}
        </Card>
        {!data ? <Loading error={loading ? null : error} reload={reload} /> : (
          <>
            <div class="mct-cal" role="grid" aria-label="Kalender">
              <div class="mct-cal-row head" role="row">
                {Array.from({ length: 7 }, (_, i) => <span key={i} role="columnheader" aria-label={['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'][(ws + i) % 7]}>{WD_SHORT[(ws + i) % 7]}</span>)}
              </div>
              {Array.from({ length: cells.length / 7 }, (_, r) => (
                <div class="mct-cal-row" role="row" key={r}>
                  {cells.slice(r * 7, r * 7 + 7).map((d, i) => {
                    if (!d) return <span key={i} role="gridcell" class="mct-cal-empty" />;
                    const day = by.get(d);
                    const future = d > boot.today;
                    const num2 = Number(d.slice(8));
                    if (!day) {
                      return future
                        ? <span key={i} role="gridcell" class="mct-cal-day future"><span>{num2}</span></span>
                        : <a key={i} role="gridcell" class="mct-cal-day open" href={`#/eintrag/neu?date=${d}&fix=1`} aria-label={`${dayLabel(d)}: kein Eintrag. Nachtragen`}><span>{num2}</span><Icon name="plus" size={16} /></a>;
                    }
                    const dim = filtered && !day.match;
                    return (
                      <a key={i} role="gridcell" class={'mct-cal-day has' + (dim ? ' dim' : '') + (d === boot.today ? ' today' : '')} href={`#/tag/${d}`}
                        aria-label={`${dayLabel(d)}: ${LEVEL_NAMES[day.level]}, ${plural(day.n, 'Eintrag', 'Einträge')}${filtered ? (day.match ? ', passt zum Filter' : ', passt nicht zum Filter') : ''}`}>
                        <span>{num2}</span>
                        <Icon name={'m' + day.level} size={24} color={dim ? 'var(--line-strong)' : colors[day.level - 1]} />
                      </a>
                    );
                  })}
                </div>
              ))}
            </div>
            {data.hints.length > 0 && <Card title="Auf einen Blick"><ul class="mct-hints">{data.hints.map((h) => <li key={h}>{h}</li>)}</ul></Card>}
            <Card title={filtered ? 'Stimmungszähler (gefiltert)' : 'Stimmungszähler'}>
              <Ring half title="Stimmungszähler" center={String(data.matches)} sub="Einträge" parts={[1, 2, 3, 4, 5].map((lv) => ({ value: data.levels[lv] || 0, color: colors[lv - 1], label: LEVEL_NAMES[lv] }))} />
              <div class="mct-legend">{[1, 2, 3, 4, 5].map((lv) => <span key={lv}><Icon name={'m' + lv} size={22} color={colors[lv - 1]} />{LEVEL_NAMES[lv]}: <strong>{data.levels[lv] || 0}</strong></span>)}</div>
            </Card>
          </>
        )}
      </div>
      {add === 'tag' && <TagPickSheet exclude={tags} onPick={(id) => { setTags([...tags, id]); setAdd(null); }} onClose={() => setAdd(null)} />}
      {add === 'mood' && (
        <Sheet title="Stimmung wählen" onClose={() => setAdd(null)}>
          <div class="mct-rows">
            {boot.setup.moods.filter((m) => !moods.includes(m.id)).map((m) => (
              <button type="button" class="mct-row" key={m.id} onClick={() => { setMoods([...moods, m.id]); setAdd(null); }}>
                <span class="mct-row-icon"><MoodIcon mood={m} size={26} /></span><span class="mct-row-text">{m.name}{m.archived && <small>archiviert</small>}</span>
              </button>
            ))}
          </div>
        </Sheet>
      )}
    </>
  );
}
