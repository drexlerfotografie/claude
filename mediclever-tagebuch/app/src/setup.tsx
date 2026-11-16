import type { JSX } from 'preact';
import { useState } from 'preact/hooks';
import { get, post, del, ApiError } from './api';
import { state, useStore, changed, fail, toast, feature } from './store';
import { Icon } from './icons';
import { Card, ColorPicker, Empty, Field, IconPicker, MoodIcon, Page, Row, Segmented, Sheet, SortList, ask } from './ui';
import { color, LEVEL_NAMES, plural } from './format';
import type { Group, Mood, Scale, Tag, Template } from './types';
import { RichText } from './editor';

export function SetupScreen(): JSX.Element {
  const b = useStore().boot!;
  const n = (list: { archived?: boolean }[]) => list.filter((x) => !x.archived).length;
  return (
    <Page title="Einrichtung" back="/mehr">
      <Card>
        <p>Du baust dir dein Tagebuch selbst zusammen. Fest vorgegeben sind nur die fünf Stufen der Stimmung, damit Auswertungen vergleichbar bleiben.</p>
        <div class="mct-rows">
          <Row icon="m2" label="Stimmungen" hint={plural(n(b.setup.moods), 'Stimmung', 'Stimmungen')} to="/mehr/stimmungen" />
          <Row icon="tags" label="Aktivitäten und Gruppen" hint={`${plural(n(b.setup.tags), 'Aktivität', 'Aktivitäten')} in ${plural(n(b.setup.groups), 'Gruppe', 'Gruppen')}`} to="/mehr/aktivitaeten" />
          {feature('scales') && <Row icon="sliders-horizontal" label="Skalen" hint={plural(n(b.setup.scales), 'Skala', 'Skalen')} to="/mehr/skalen" />}
          <Row icon="grip-vertical" label="Reihenfolge" hint="Gruppen und Skalen gemeinsam sortieren" to="/mehr/reihenfolge" />
          {feature('templates') && <Row icon="notebook-pen" label="Schreibvorlagen" hint={plural(b.setup.templates.length, 'Vorlage', 'Vorlagen')} to="/mehr/vorlagen" />}
        </div>
      </Card>
    </Page>
  );
}

/** Löschen fragt nach, nennt die Zahl der betroffenen Einträge und bietet Archivieren als Alternative an. */
async function removeWithCount(kind: 'tags' | 'scales' | 'groups', id: number, name: string, archived: boolean): Promise<void> {
  const { count } = await get<{ count: number }>('/usage', { kind, id });
  const what = kind === 'tags' ? 'Die Aktivität' : kind === 'scales' ? 'Die Skala' : 'Die Gruppe';
  const text = count > 0
    ? `${what} „${name}“ steckt in ${plural(count, 'Eintrag', 'Einträgen')}. Beim Löschen verschwindet sie auch dort. Im Archiv bleibt sie in alten Einträgen und Auswertungen sichtbar und fehlt nur beim Erfassen.`
    : `${what} „${name}“ wird in keinem Eintrag verwendet.`;
  const k = await ask({ title: `„${name}“ löschen?`, text, buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' },
    ...(archived ? [] : [{ key: 'arch', label: 'Archivieren', kind: 'primary' as const }]), { key: 'del', label: count > 0 ? `Löschen (${count})` : 'Löschen', kind: 'danger' }] });
  if (k === 'arch') await post('/archive', { kind, id, archived: true });
  else if (k === 'del') await del(`/${kind}/${id}`);
  else return;
  await changed();
  toast(k === 'arch' ? 'Archiviert.' : 'Gelöscht.');
}

const confirmUnused = (name: string): Promise<boolean> => ask({ title: `„${name}“ löschen?`, text: 'Diese Stimmung wird in keinem Eintrag verwendet.',
  buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, { key: 'yes', label: 'Löschen', kind: 'danger' }] }).then((k) => k === 'yes');

const archive = (kind: string, id: number, archived: boolean) => post('/archive', { kind, id, archived }).then(changed).catch(fail);

// ---- Stimmungen -----------------------------------------------------------

function MoodSheet({ mood, level, onClose }: { mood: Mood | null; level: number; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [name, setName] = useState(mood?.name || '');
  const [icon, setIcon] = useState(mood?.icon || 'm' + level);
  const [lv, setLv] = useState(mood?.level || level);
  const save = async () => { try { await post(mood ? `/moods/${mood.id}` : '/moods', { name, icon, level: lv }); await changed(); onClose(); } catch (e) { fail(e); } };
  const [replace, setReplace] = useState<{ count: number; id: number } | null>(null);
  const others = boot.setup.moods.filter((m) => m.id !== mood?.id && !m.archived);
  const remove = async () => {
    if (!mood) return;
    try {
      const { count } = await get<{ count: number }>('/usage', { kind: 'moods', id: mood.id });
      if (count > 0) {
        // Einträge werden nie mitgelöscht: Der Nutzer wählt die Ersatz-Stimmung selbst und bestätigt sie.
        setReplace({ count, id: (others.find((m) => m.level === mood.level) || others[0])?.id ?? 0 });
        return;
      }
      if (!(await confirmUnused(mood.name))) return;
      await del(`/moods/${mood.id}`);
      await changed();
      onClose();
    } catch (e) { fail(e); }
  };
  const removeWith = async (archiveInstead: boolean) => {
    if (!mood || !replace) return;
    try {
      if (archiveInstead) await post('/archive', { kind: 'moods', id: mood.id, archived: true });
      else await del(`/moods/${mood.id}`, { replace_with: replace.id });
      await changed();
      toast(archiveInstead ? 'Archiviert.' : 'Gelöscht. Die Einträge haben jetzt die Ersatz-Stimmung.');
      onClose();
    } catch (e) { fail(e); }
  };
  if (mood && replace) {
    const target = others.find((m) => m.id === replace.id);
    return (
      <Sheet title={`„${mood.name}“ löschen?`} onClose={() => setReplace(null)}
        footer={<><button type="button" class="mct-btn ghost" onClick={() => setReplace(null)}>Abbrechen</button>
          {!mood.archived && <button type="button" class="mct-btn ghost" onClick={() => removeWith(true)}>Archivieren</button>}
          <button type="button" class="mct-btn danger" disabled={!target} onClick={() => removeWith(false)}>Löschen und ersetzen ({replace.count})</button></>}>
        <p>Diese Stimmung steckt in {plural(replace.count, 'Eintrag', 'Einträgen')}. Die Einträge bleiben erhalten und bekommen eine Ersatz-Stimmung. Im Archiv bliebe alles, wie es ist.</p>
        <Field label="Ersatz-Stimmung für diese Einträge">
          <select value={replace.id} onChange={(e) => setReplace({ ...replace, id: Number((e.target as HTMLSelectElement).value) })}>
            {others.map((m) => <option key={m.id} value={m.id}>{m.name} (Stufe {m.level} · {LEVEL_NAMES[m.level]})</option>)}
          </select>
        </Field>
        {target && target.level !== mood.level && <div class="mct-hint warn">Die Ersatz-Stimmung liegt auf einer anderen Stufe. Das verändert deine Auswertungen rückwirkend.</div>}
      </Sheet>
    );
  }
  return (
    <Sheet title={mood ? 'Stimmung bearbeiten' : 'Neue Stimmung'} onClose={onClose}
      footer={<>{mood && <button type="button" class="mct-btn ghost danger" onClick={remove}>Löschen</button>}
        {mood && <button type="button" class="mct-btn ghost" onClick={() => archive('moods', mood.id, !mood.archived).then(onClose)}>{mood.archived ? 'Wiederherstellen' : 'Archivieren'}</button>}
        <button type="button" class="mct-btn primary" disabled={!name.trim()} onClick={save}>Speichern</button></>}>
      <Field label="Name"><input type="text" value={name} maxLength={100} onInput={(e) => setName((e.target as HTMLInputElement).value)} /></Field>
      <Field label="Stufe">
        <select value={lv} onChange={(e) => setLv(Number((e.target as HTMLSelectElement).value))}>{[1, 2, 3, 4, 5].map((l) => <option key={l} value={l}>{l} · {LEVEL_NAMES[l]}</option>)}</select>
      </Field>
      <span class="mct-field-label">Symbol</span>
      <IconPicker value={icon} onChange={setIcon} moods />
    </Sheet>
  );
}

export function MoodsScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<{ mood: Mood | null; level: number } | null>(null);
  return (
    <Page title="Stimmungen" back="/mehr/einrichtung">
      <p class="mct-muted">Fünf Stufen sind fest. Pro Stufe kannst du mehrere eigene Stimmungen anlegen, zum Beispiel „Stolz“ und „Erleichtert“ auf der besten Stufe.</p>
      {[1, 2, 3, 4, 5].map((level) => {
        const list = boot.setup.moods.filter((m) => m.level === level);
        return (
          <Card key={level} title={`Stufe ${level} · ${LEVEL_NAMES[level]}`} action={<button type="button" class="mct-iconbtn" aria-label={`Neue Stimmung auf Stufe ${level}`} onClick={() => setEdit({ mood: null, level })}><Icon name="plus" /></button>}>
            <SortList items={list.filter((m) => !m.archived)} id={(m) => String(m.id)} label={(m) => m.name}
              onChange={(items) => post('/reorder', { kind: 'moods', ids: items.map((m) => m.id) }).then(changed, fail)}
              render={(m) => <button type="button" class="mct-sort-main" onClick={() => setEdit({ mood: m, level })}><MoodIcon mood={m} size={30} /><span>{m.name}</span><Icon name="pencil" size={18} class="mct-muted" /></button>} />
            {list.filter((m) => m.archived).map((m) => (
              <button type="button" class="mct-row archived" key={m.id} onClick={() => setEdit({ mood: m, level })}><span class="mct-row-icon"><Icon name="archive" /></span><span class="mct-row-text">{m.name}<small>archiviert</small></span></button>
            ))}
          </Card>
        );
      })}
      {edit && <MoodSheet mood={edit.mood} level={edit.level} onClose={() => setEdit(null)} />}
    </Page>
  );
}

// ---- Aktivitäten und Gruppen ----------------------------------------------

function GroupSheet({ group, onClose }: { group: Group | null; onClose: () => void }): JSX.Element {
  const [name, setName] = useState(group?.name || '');
  const [c, setC] = useState(group?.color || 'c1');
  const [collapsed, setCollapsed] = useState(group?.collapsed || false);
  const save = async () => { try { await post(group ? `/groups/${group.id}` : '/groups', { name, color: c, collapsed }); await changed(); onClose(); } catch (e) { fail(e); } };
  return (
    <Sheet title={group ? 'Gruppe bearbeiten' : 'Neue Gruppe'} onClose={onClose}
      footer={<>{group && <button type="button" class="mct-btn ghost danger" onClick={() => removeWithCount('groups', group.id, group.name, group.archived).then(onClose, fail)}>Löschen</button>}
        {group && <button type="button" class="mct-btn ghost" onClick={() => archive('groups', group.id, !group.archived).then(onClose)}>{group.archived ? 'Wiederherstellen' : 'Archivieren'}</button>}
        <button type="button" class="mct-btn primary" disabled={!name.trim()} onClick={save}>Speichern</button></>}>
      <Field label="Name" hint="Emojis sind erlaubt."><input type="text" value={name} maxLength={100} onInput={(e) => setName((e.target as HTMLInputElement).value)} /></Field>
      <span class="mct-field-label">Farbe</span>
      <ColorPicker value={c} onChange={setC} />
      <Segmented label="Beim Erfassen" value={collapsed ? 'zu' : 'auf'} onChange={(v) => setCollapsed(v === 'zu')} options={[{ value: 'auf', label: 'Offen' }, { value: 'zu', label: 'Eingeklappt' }]} />
      {group && <p class="mct-muted small">Beim Löschen einer Gruppe bleiben ihre Aktivitäten erhalten und stehen danach ohne Gruppe da.</p>}
    </Sheet>
  );
}

function TagSheet({ tag, groupId, onClose }: { tag: Tag | null; groupId: number | null; onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [name, setName] = useState(tag?.name || '');
  const [icon, setIcon] = useState(tag?.icon || 'circle');
  const [c, setC] = useState(tag?.color || '');
  const [gid, setGid] = useState<number | null>(tag ? tag.group_id : groupId);
  const [merge, setMerge] = useState(false);
  const save = async () => { try { await post(tag ? `/tags/${tag.id}` : '/tags', { name, icon, color: c, group_id: gid }); await changed(); onClose(); } catch (e) { fail(e); } };
  const doMerge = async (into: Tag) => {
    if (!tag) return;
    const k = await ask({ title: 'Aktivitäten zusammenführen?', text: `Alle Einträge mit „${tag.name}“ bekommen stattdessen „${into.name}“. „${tag.name}“ wird danach entfernt. Das lässt sich nicht rückgängig machen.`,
      buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, { key: 'yes', label: 'Zusammenführen', kind: 'primary' }] });
    if (k !== 'yes') return;
    try { await post(`/tags/${tag.id}/merge`, { into: into.id }); await changed(); toast('Zusammengeführt.'); onClose(); } catch (e) { fail(e); }
  };
  return (
    <Sheet title={tag ? 'Aktivität bearbeiten' : 'Neue Aktivität'} onClose={onClose} wide
      footer={<>{tag && <button type="button" class="mct-btn ghost danger" onClick={() => removeWithCount('tags', tag.id, tag.name, tag.archived).then(onClose, fail)}>Löschen</button>}
        {tag && <button type="button" class="mct-btn ghost" onClick={() => archive('tags', tag.id, !tag.archived).then(onClose)}>{tag.archived ? 'Wiederherstellen' : 'Archivieren'}</button>}
        <button type="button" class="mct-btn primary" disabled={!name.trim()} onClick={save}>Speichern</button></>}>
      <Field label="Name"><input type="text" value={name} maxLength={100} onInput={(e) => setName((e.target as HTMLInputElement).value)} /></Field>
      <Field label="Gruppe">
        <select value={gid ?? ''} onChange={(e) => setGid(Number((e.target as HTMLSelectElement).value) || null)}>
          <option value="">Ohne Gruppe</option>
          {boot.setup.groups.map((g) => <option key={g.id} value={g.id}>{g.name}{g.archived ? ' (archiviert)' : ''}</option>)}
        </select>
      </Field>
      <span class="mct-field-label">Farbe</span>
      <ColorPicker value={c} onChange={setC} allowNone />
      <IconPicker value={icon} onChange={setIcon} />
      {tag && <button type="button" class="mct-btn ghost small" onClick={() => setMerge(true)}><Icon name="merge" size={16} />Durch andere Aktivität ersetzen (zusammenführen)</button>}
      {merge && tag && (
        <Sheet title="Womit zusammenführen?" onClose={() => setMerge(false)}>
          <div class="mct-rows scroll">{boot.setup.tags.filter((t) => t.id !== tag.id).map((t) => (
            <button type="button" class="mct-row" key={t.id} onClick={() => { setMerge(false); doMerge(t); }}><span class="mct-row-icon"><Icon name={t.icon} /></span><span class="mct-row-text">{t.name}</span></button>
          ))}</div>
        </Sheet>
      )}
    </Sheet>
  );
}

export function TagsScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [editTag, setEditTag] = useState<{ tag: Tag | null; groupId: number | null } | null>(null);
  const [editGroup, setEditGroup] = useState<Group | 'new' | null>(null);
  const [showArchive, setShowArchive] = useState(false);
  const groups: (Group | null)[] = [...boot.setup.groups.filter((g) => !g.archived), null];
  const archivedTags = boot.setup.tags.filter((t) => t.archived);
  const archivedGroups = boot.setup.groups.filter((g) => g.archived);
  return (
    <Page title="Aktivitäten und Gruppen" back="/mehr/einrichtung" actions={<button type="button" class="mct-btn small primary" onClick={() => setEditGroup('new')}><Icon name="plus" size={16} />Gruppe</button>}>
      {boot.setup.tags.length === 0 && boot.setup.groups.length === 0 && <Empty icon="tags" title="Noch keine Aktivitäten" text="Lege zuerst eine Gruppe an, zum Beispiel „Dienst“ oder „Freizeit“."><button type="button" class="mct-btn primary" onClick={() => setEditGroup('new')}>Gruppe anlegen</button></Empty>}
      {groups.map((g) => {
        const tags = boot.setup.tags.filter((t) => t.group_id === (g?.id ?? null) && !t.archived);
        if (!g && tags.length === 0) return null;
        return (
          <Card key={g?.id ?? 0} title={g?.name || 'Ohne Gruppe'} action={<div class="mct-btnrow">
            {g && <button type="button" class="mct-iconbtn" aria-label={`Gruppe ${g.name} bearbeiten`} onClick={() => setEditGroup(g)}><Icon name="pencil" /></button>}
            <button type="button" class="mct-iconbtn" aria-label={`Neue Aktivität in ${g?.name || 'Ohne Gruppe'}`} onClick={() => setEditTag({ tag: null, groupId: g?.id ?? null })}><Icon name="plus" /></button></div>}>
            {tags.length === 0 ? <p class="mct-muted">Noch keine Aktivität in dieser Gruppe.</p> : (
              <SortList items={tags} id={(t) => String(t.id)} label={(t) => t.name}
                onChange={(items) => post('/reorder', { kind: 'tags', ids: items.map((t) => t.id) }).then(changed, fail)}
                render={(t) => <button type="button" class="mct-sort-main" onClick={() => setEditTag({ tag: t, groupId: t.group_id })}>
                  <Icon name={t.icon} size={24} color={color(t.color || g?.color)} /><span>{t.name}</span><Icon name="pencil" size={18} class="mct-muted" /></button>} />
            )}
          </Card>
        );
      })}
      {archivedTags.length + archivedGroups.length > 0 && (
        <>
          <button type="button" class="mct-btn ghost small" aria-expanded={showArchive} onClick={() => setShowArchive(!showArchive)}><Icon name="archive" size={16} />Archiv ({archivedTags.length + archivedGroups.length})</button>
          {showArchive && (
            <div class="mct-rows card">
              {archivedGroups.map((g) => <Row key={'g' + g.id} icon="folder" label={g.name} hint="Gruppe, archiviert" onClick={() => setEditGroup(g)} />)}
              {archivedTags.map((t) => <Row key={t.id} icon={t.icon} label={t.name} hint="archiviert" onClick={() => setEditTag({ tag: t, groupId: t.group_id })} />)}
            </div>
          )}
        </>
      )}
      {editTag && <TagSheet tag={editTag.tag} groupId={editTag.groupId} onClose={() => setEditTag(null)} />}
      {editGroup && <GroupSheet group={editGroup === 'new' ? null : editGroup} onClose={() => setEditGroup(null)} />}
    </Page>
  );
}

// ---- Skalen ---------------------------------------------------------------

function ScaleSheet({ scale, onClose }: { scale: Scale | null; onClose: () => void }): JSX.Element {
  const [s, setS] = useState({ name: scale?.name || '', type: scale?.type || 'text', color: scale?.color || 'c7', icon: scale?.icon || 'gauge', chart_mode: scale?.chart_mode || 'avg',
    trend: scale?.trend || 'up', min: scale?.min ?? 0, max: scale?.max ?? 10 });
  const [points, setPoints] = useState<{ id?: number; label: string }[]>(scale?.points.length ? scale.points.map((p) => ({ id: p.id, label: p.label })) : [{ label: 'Niedrig' }, { label: 'Mittel' }, { label: 'Hoch' }]);
  const save = async () => {
    const body: Record<string, unknown> = { ...s };
    if (s.type === 'text') { body.points = points; delete body.min; delete body.max; }
    if (s.type === 'time') { body.min = 0; body.max = Math.max(60, Number(s.max) || 720); }
    try { await post(scale ? `/scales/${scale.id}` : '/scales', body); await changed(); onClose(); } catch (e) { fail(e); }
  };
  return (
    <Sheet title={scale ? 'Skala bearbeiten' : 'Neue Skala'} onClose={onClose} wide
      footer={<>{scale && <button type="button" class="mct-btn ghost danger" onClick={() => removeWithCount('scales', scale.id, scale.name, scale.archived).then(onClose, fail)}>Löschen</button>}
        {scale && <button type="button" class="mct-btn ghost" onClick={() => archive('scales', scale.id, !scale.archived).then(onClose)}>{scale.archived ? 'Wiederherstellen' : 'Archivieren'}</button>}
        <button type="button" class="mct-btn primary" disabled={!s.name.trim()} onClick={save}>Speichern</button></>}>
      <Field label="Name"><input type="text" value={s.name} maxLength={100} onInput={(e) => setS({ ...s, name: (e.target as HTMLInputElement).value })} /></Field>
      {!scale && <>
        <span class="mct-field-label">Typ</span>
        <Segmented label="Typ der Skala" value={s.type} onChange={(v) => setS({ ...s, type: v, max: v === 'time' ? 720 : 10, icon: v === 'time' ? 'clock' : 'gauge' })}
          options={[{ value: 'text', label: 'Text' }, { value: 'num', label: 'Zahl' }, { value: 'time', label: 'Zeitdauer' }]} />
      </>}
      {s.type === 'text' && (
        <fieldset class="mct-points-edit">
          <legend>Benannte Punkte (von niedrig nach hoch)</legend>
          {points.map((p, i) => (
            <div key={i}>
              <input type="text" value={p.label} aria-label={`Punkt ${i + 1}`} maxLength={100} onInput={(e) => setPoints(points.map((x, j) => (j === i ? { ...x, label: (e.target as HTMLInputElement).value } : x)))} />
              <button type="button" class="mct-iconbtn" aria-label={`Punkt ${i + 1} entfernen`} disabled={points.length <= 2} onClick={() => setPoints(points.filter((_, j) => j !== i))}><Icon name="x" /></button>
            </div>
          ))}
          <button type="button" class="mct-btn ghost small" disabled={points.length >= 10} onClick={() => setPoints([...points, { label: '' }])}><Icon name="plus" size={16} />Punkt</button>
        </fieldset>
      )}
      {s.type === 'num' && (
        <div class="mct-when">
          <Field label="Niedrigster Wert"><input type="number" value={s.min} onInput={(e) => setS({ ...s, min: Number((e.target as HTMLInputElement).value) })} /></Field>
          <Field label="Höchster Wert"><input type="number" value={s.max} onInput={(e) => setS({ ...s, max: Number((e.target as HTMLInputElement).value) })} /></Field>
        </div>
      )}
      {s.type === 'time' && <Field label="Höchste Dauer in Stunden"><input type="number" min={1} max={48} value={Math.round(Number(s.max) / 60)} onInput={(e) => setS({ ...s, max: Number((e.target as HTMLInputElement).value) * 60 })} /></Field>}
      <span class="mct-field-label">Anzeige im Diagramm</span>
      <Segmented label="Anzeige im Diagramm" value={s.chart_mode} onChange={(v) => setS({ ...s, chart_mode: v })} options={[{ value: 'avg', label: 'Tagesdurchschnitt' }, { value: 'sum', label: 'Tagessumme' }]} />
      <span class="mct-field-label">Trendrichtung</span>
      <Segmented label="Trendrichtung" value={s.trend} onChange={(v) => setS({ ...s, trend: v })} options={[{ value: 'up', label: 'Höher ist gut' }, { value: 'down', label: 'Höher ist schlecht' }]} />
      <span class="mct-field-label">Farbe</span>
      <ColorPicker value={s.color} onChange={(c) => setS({ ...s, color: c })} />
      <IconPicker value={s.icon} onChange={(icon) => setS({ ...s, icon })} />
    </Sheet>
  );
}

const TYPE_LABEL = { text: 'Textskala', num: 'Zahlenskala', time: 'Zeitdauer' };
export function ScalesScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<Scale | 'new' | null>(null);
  return (
    <Page title="Skalen" back="/mehr/einrichtung" actions={<button type="button" class="mct-iconbtn" aria-label="Neue Skala" onClick={() => setEdit('new')}><Icon name="plus" /></button>}>
      {boot.setup.scales.length === 0 && <Empty icon="sliders-horizontal" title="Noch keine Skala" text="Mit Skalen hältst du Werte fest, zum Beispiel Schlafdauer, Stress oder Schmerz."><button type="button" class="mct-btn primary" onClick={() => setEdit('new')}>Skala anlegen</button></Empty>}
      <div class="mct-rows card">
        {boot.setup.scales.map((x) => (
          <Row key={x.id} icon={x.icon} iconColor={color(x.color)} label={x.name} onClick={() => setEdit(x)}
            hint={`${TYPE_LABEL[x.type]}${x.type === 'text' ? ` · ${x.points.length} Punkte` : ''} · ${x.trend === 'up' ? 'höher ist gut' : 'höher ist schlecht'}${x.archived ? ' · archiviert' : ''}`} />
        ))}
      </div>
      {edit && <ScaleSheet scale={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Page>
  );
}

// ---- Reihenfolge ----------------------------------------------------------

export function OrderScreen(): JSX.Element {
  const boot = useStore().boot!;
  type Item = { kind: 'group' | 'scale'; id: number; name: string; pos: number; icon: string; c: string };
  const items: Item[] = [
    ...boot.setup.groups.filter((g) => !g.archived).map((g) => ({ kind: 'group' as const, id: g.id, name: g.name, pos: g.position, icon: 'folder', c: g.color })),
    ...boot.setup.scales.filter((x) => !x.archived).map((x) => ({ kind: 'scale' as const, id: x.id, name: x.name, pos: x.position, icon: x.icon, c: x.color })),
  ].sort((a, b) => a.pos - b.pos);
  return (
    <Page title="Reihenfolge" back="/mehr/einrichtung">
      <p class="mct-muted">So erscheinen Gruppen und Skalen beim Erfassen. Zieh am Griff oder nutze die Pfeiltasten.</p>
      {items.length === 0 ? <Empty icon="grip-vertical" title="Noch nichts zu sortieren" /> : (
        <Card>
          <SortList items={items} id={(i) => i.kind + i.id} label={(i) => i.name}
            onChange={(list) => post('/reorder-shared', { items: list.map((i) => ({ kind: i.kind, id: i.id })) }).then(changed, fail)}
            render={(i) => <div class="mct-sort-main static"><Icon name={i.icon} size={22} color={color(i.c)} /><span>{i.name}</span><small class="mct-muted">{i.kind === 'group' ? 'Gruppe' : 'Skala'}</small></div>} />
        </Card>
      )}
    </Page>
  );
}

// ---- Schreibvorlagen ------------------------------------------------------

function TemplateSheet({ t, onClose }: { t: Template | null; onClose: () => void }): JSX.Element {
  const [title, setTitle] = useState(t?.title || '');
  const [body, setBody] = useState(t?.body || '');
  const save = async () => { try { await post(t ? `/templates/${t.id}` : '/templates', { title, body }); await changed(); onClose(); } catch (e) { fail(e); } };
  return (
    <Sheet title={t ? 'Vorlage bearbeiten' : 'Neue Vorlage'} onClose={onClose} wide
      footer={<>{t && <button type="button" class="mct-btn ghost danger" onClick={() => del(`/templates/${t.id}`).then(changed).then(onClose, fail)}>Löschen</button>}
        <button type="button" class="mct-btn primary" disabled={!title.trim()} onClick={save}>Speichern</button></>}>
      <Field label="Titel"><input type="text" value={title} maxLength={150} onInput={(e) => setTitle((e.target as HTMLInputElement).value)} /></Field>
      <span class="mct-field-label">Text der Vorlage</span>
      <RichText value={body} onChange={setBody} />
    </Sheet>
  );
}

export function TemplatesScreen(): JSX.Element {
  const boot = useStore().boot!;
  const [edit, setEdit] = useState<Template | 'new' | null>(null);
  const reset = async () => {
    const k = await ask({ title: 'Standard wiederherstellen?', text: 'Deine eigenen Vorlagen werden durch die drei Standardvorlagen ersetzt.', buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, { key: 'yes', label: 'Wiederherstellen', kind: 'danger' }] });
    if (k !== 'yes') return;
    try { await post('/templates/reset'); await changed(); } catch (e) { if (e instanceof ApiError) fail(e); }
  };
  return (
    <Page title="Schreibvorlagen" back="/mehr/einrichtung" actions={<button type="button" class="mct-iconbtn" aria-label="Neue Vorlage" onClick={() => setEdit('new')}><Icon name="plus" /></button>}>
      {boot.setup.templates.length === 0 ? <Empty icon="notebook-pen" title="Keine Vorlage" text="Vorlagen geben dir beim Schreiben eine Struktur vor."><button type="button" class="mct-btn primary" onClick={() => setEdit('new')}>Vorlage anlegen</button></Empty> : (
        <Card>
          <SortList items={boot.setup.templates} id={(t) => String(t.id)} label={(t) => t.title}
            onChange={(items) => post('/reorder', { kind: 'templates', ids: items.map((t) => t.id) }).then(changed, fail)}
            render={(t) => <button type="button" class="mct-sort-main" onClick={() => setEdit(t)}><Icon name="notebook-pen" size={22} /><span>{t.title}</span><Icon name="pencil" size={18} class="mct-muted" /></button>} />
        </Card>
      )}
      <button type="button" class="mct-btn ghost small" onClick={reset}><Icon name="undo-2" size={16} />Standard wiederherstellen</button>
      {edit && <TemplateSheet t={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Page>
  );
}
