import type { ComponentChildren, JSX } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { Icon, ICON_META, MOOD_ICONS } from './icons';
import { state, emit, useStore, go } from './store';
import { PALETTE, color as resolveColor, levelColors } from './format';
import type { Mood } from './types';

// ---- Dialoge --------------------------------------------------------------

interface SheetProps { title: string; onClose: () => void; children: ComponentChildren; wide?: boolean; footer?: ComponentChildren }

/** Modaler Dialog auf Basis von <dialog>: Fokus bleibt im Dialog, Escape schließt. */
export function Sheet({ title, onClose, children, wide, footer }: SheetProps): JSX.Element {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
    return () => { if (d?.open) d.close(); };
  }, []);
  return (
    <dialog ref={ref} class={'mct-sheet' + (wide ? ' wide' : '')} aria-label={title}
      onCancel={(e) => { e.preventDefault(); onClose(); }}
      onClick={(e) => { if (e.target === ref.current) onClose(); }}>
      <div class="mct-sheet-box">
        <header class="mct-sheet-head">
          <h2>{title}</h2>
          <button type="button" class="mct-iconbtn" aria-label="Schließen" onClick={onClose}><Icon name="x" /></button>
        </header>
        <div class="mct-sheet-body">{children}</div>
        {footer && <footer class="mct-sheet-foot">{footer}</footer>}
      </div>
    </dialog>
  );
}

interface AskOptions { title: string; text?: ComponentChildren; buttons: { key: string; label: string; kind?: 'primary' | 'danger' | 'ghost' }[] }
let asking: (AskOptions & { resolve: (k: string | null) => void }) | null = null;

/** Rückfrage mit mehreren Antworten. Liefert den Schlüssel der gewählten Antwort oder null. */
export function ask(opts: AskOptions): Promise<string | null> {
  return new Promise((resolve) => { asking = { ...opts, resolve }; emit(); });
}
export const confirmDelete = (title: string, text: ComponentChildren, label = 'Löschen'): Promise<boolean> =>
  ask({ title, text, buttons: [{ key: 'no', label: 'Abbrechen', kind: 'ghost' }, { key: 'yes', label, kind: 'danger' }] }).then((k) => k === 'yes');

export function DialogHost(): JSX.Element | null {
  useStore();
  if (!asking) return null;
  const a = asking;
  const done = (k: string | null) => { asking = null; a.resolve(k); emit(); };
  return (
    <Sheet title={a.title} onClose={() => done(null)}
      footer={a.buttons.map((b) => <button type="button" key={b.key} class={'mct-btn ' + (b.kind || 'primary')} onClick={() => done(b.key)}>{b.label}</button>)}>
      {typeof a.text === 'string' ? <p>{a.text}</p> : a.text}
    </Sheet>
  );
}

export function Toasts(): JSX.Element {
  const s = useStore();
  return (
    <div class="mct-toasts" role="status" aria-live="polite">
      {s.toasts.map((t) => <div key={t.id} class={'mct-toast ' + t.kind}>{t.text}</div>)}
    </div>
  );
}

// ---- Bausteine ------------------------------------------------------------

export function Page({ title, back, actions, children }: { title: string; back?: string; actions?: ComponentChildren; children: ComponentChildren }): JSX.Element {
  useEffect(() => { document.getElementById('mct-main')?.focus({ preventScroll: true }); }, [title]);
  return (
    <>
      <header class="mct-top">
        {back && <button type="button" class="mct-iconbtn" aria-label="Zurück" onClick={() => go(back)}><Icon name="chevron-left" /></button>}
        <h1>{title}</h1>
        <div class="mct-top-actions">{actions}</div>
      </header>
      <div class="mct-content">{children}</div>
    </>
  );
}

export function Card({ title, children, action, class: cls }: { title?: string; children: ComponentChildren; action?: ComponentChildren; class?: string }): JSX.Element {
  return (
    <section class={'mct-card ' + (cls || '')}>
      {(title || action) && <div class="mct-card-head">{title && <h2>{title}</h2>}{action}</div>}
      {children}
    </section>
  );
}

export function Empty({ icon = 'notebook-pen', title, text, children }: { icon?: string; title: string; text?: string; children?: ComponentChildren }): JSX.Element {
  return (
    <div class="mct-empty">
      <div class="mct-empty-icon"><Icon name={icon} size={30} /></div>
      <h2>{title}</h2>
      {text && <p>{text}</p>}
      {children}
    </div>
  );
}

export function Loading({ error, reload }: { error?: string | null; reload?: () => void }): JSX.Element {
  if (error) {
    return <div class="mct-empty" role="alert"><div class="mct-empty-icon"><Icon name="alert" size={30} /></div><h2>Das hat nicht geklappt</h2><p>{error}</p>
      {reload && <button type="button" class="mct-btn primary" onClick={reload}>Noch einmal versuchen</button>}</div>;
  }
  return <div class="mct-loading" role="status"><span class="mct-spinner" aria-hidden="true" />Lädt …</div>;
}

export function Toggle({ label, hint, checked, onChange }: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void }): JSX.Element {
  return (
    <label class="mct-toggle">
      <span><span class="mct-toggle-label">{label}</span>{hint && <small>{hint}</small>}</span>
      <input type="checkbox" role="switch" checked={checked} onChange={(e) => onChange((e.target as HTMLInputElement).checked)} />
    </label>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ComponentChildren }): JSX.Element {
  return <label class="mct-field"><span class="mct-field-label">{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}

export function Segmented<T extends string | number>({ label, value, options, onChange }: { label: string; value: T; options: { value: T; label: string }[]; onChange: (v: T) => void }): JSX.Element {
  return (
    <div class="mct-seg" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button type="button" key={String(o.value)} role="radio" aria-checked={o.value === value} class={o.value === value ? 'on' : ''} onClick={() => onChange(o.value)}>{o.label}</button>
      ))}
    </div>
  );
}

export function Row({ icon, label, hint, to, onClick, right, iconColor }: { icon?: string; label: string; hint?: string; to?: string; onClick?: () => void; right?: ComponentChildren; iconColor?: string }): JSX.Element {
  const inner = <>{icon && <span class="mct-row-icon" style={iconColor ? { color: iconColor } : undefined}><Icon name={icon} /></span>}
    <span class="mct-row-text"><span>{label}</span>{hint && <small>{hint}</small>}</span>{right ?? <Icon name="chevron-right" size={18} class="mct-muted" />}</>;
  return to ? <a class="mct-row" href={'#' + to}>{inner}</a> : <button type="button" class="mct-row" onClick={onClick}>{inner}</button>;
}

export function MoodIcon({ mood, size = 28, colors }: { mood: Mood | undefined; size?: number; colors?: string[] }): JSX.Element {
  const s = state.boot?.settings;
  const c = colors || levelColors(s?.color_scheme || 'klar', s?.custom_colors || []);
  return <Icon name={mood?.icon || 'm3'} size={size} color={c[(mood?.level || 3) - 1]} />;
}

// ---- Auswahl von Symbol und Farbe -----------------------------------------

export function IconPicker({ value, onChange, moods }: { value: string; onChange: (v: string) => void; moods?: boolean }): JSX.Element {
  const [q, setQ] = useState('');
  const needle = q.trim().toLowerCase();
  const items = moods ? MOOD_ICONS.map((key) => ({ key, label: key, cat: 'Stimmung' })) : ICON_META.filter((i) => i.cat !== 'Bedienung');
  const hits = needle ? items.filter((i) => (i.label + ' ' + i.cat).toLowerCase().includes(needle)) : items;
  const cats = [...new Set(hits.map((i) => i.cat))];
  return (
    <div class="mct-picker">
      {!moods && <Field label="Symbol suchen"><input type="search" value={q} placeholder="zum Beispiel Sport, Dienst, Essen" onInput={(e) => setQ((e.target as HTMLInputElement).value)} /></Field>}
      <div class="mct-picker-scroll">
        {cats.map((cat) => (
          <div key={cat}>
            {!moods && <h3>{cat}</h3>}
            <div class="mct-picker-grid" role="radiogroup" aria-label={'Symbol: ' + cat}>
              {hits.filter((i) => i.cat === cat).map((i) => (
                <button type="button" key={i.key} role="radio" aria-checked={value === i.key} aria-label={moods ? 'Symbol ' + i.key : i.label.split(' ')[0]} title={moods ? undefined : i.label.split(' ')[0]}
                  class={value === i.key ? 'on' : ''} onClick={() => onChange(i.key)}><Icon name={i.key} size={24} /></button>
              ))}
            </div>
          </div>
        ))}
        {hits.length === 0 && <p class="mct-muted">Kein Symbol gefunden.</p>}
      </div>
    </div>
  );
}

const COLOR_NAMES = ['Rot', 'Orange', 'Ocker', 'Oliv', 'Grün', 'Türkis', 'Blau', 'Kobalt', 'Violett', 'Purpur', 'Beere', 'Grau'];
export function ColorPicker({ value, onChange, allowNone }: { value: string; onChange: (v: string) => void; allowNone?: boolean }): JSX.Element {
  return (
    <div class="mct-colors" role="radiogroup" aria-label="Farbe">
      {allowNone && <button type="button" role="radio" aria-checked={value === ''} aria-label="Farbe der Gruppe" class={'none' + (value === '' ? ' on' : '')} onClick={() => onChange('')}>Gruppe</button>}
      {PALETTE.map((_, i) => {
        const key = 'c' + (i + 1);
        return <button type="button" key={key} role="radio" aria-checked={value === key} aria-label={COLOR_NAMES[i]} class={value === key ? 'on' : ''}
          style={{ background: resolveColor(key) }} onClick={() => onChange(key)}>{value === key && <Icon name="check" size={18} color="#fff" />}</button>;
      })}
    </div>
  );
}

// ---- Sortieren per Ziehen (und per Tasten) --------------------------------

interface SortProps<T> { items: T[]; id: (t: T) => string; render: (t: T) => ComponentChildren; onChange: (items: T[]) => void; label: (t: T) => string }

export function SortList<T>({ items, id, render, onChange, label }: SortProps<T>): JSX.Element {
  const [order, setOrder] = useState(items);
  const [drag, setDrag] = useState<string | null>(null);
  const list = useRef<HTMLUListElement>(null);
  const latest = useRef(order);
  useEffect(() => { setOrder(items); latest.current = items; }, [items]);

  const move = (from: number, to: number, commit: boolean) => {
    if (to < 0 || to >= latest.current.length || from === to) return;
    const next = latest.current.slice();
    next.splice(to, 0, next.splice(from, 1)[0]);
    latest.current = next;
    setOrder(next);
    if (commit) onChange(next);
  };
  const onPointerMove = (e: PointerEvent) => {
    if (!drag || !list.current) return;
    const rows = Array.from(list.current.children) as HTMLElement[];
    const from = latest.current.findIndex((t) => id(t) === drag);
    const to = rows.findIndex((r) => { const b = r.getBoundingClientRect(); return e.clientY >= b.top && e.clientY <= b.bottom; });
    if (to >= 0 && to !== from) move(from, to, false);
  };
  return (
    <ul class="mct-sort" ref={list}>
      {order.map((t, i) => (
        <li key={id(t)} class={drag === id(t) ? 'dragging' : ''}>
          <button type="button" class="mct-sort-handle" aria-label={`${label(t)} verschieben. Mit Pfeiltasten nach oben oder unten.`}
            onPointerDown={(e) => { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId); setDrag(id(t)); }}
            onPointerMove={onPointerMove}
            onPointerUp={() => { if (drag) { setDrag(null); onChange(latest.current); } }}
            onPointerCancel={() => setDrag(null)}
            onKeyDown={(e) => { if (e.key === 'ArrowUp') { e.preventDefault(); move(i, i - 1, true); } if (e.key === 'ArrowDown') { e.preventDefault(); move(i, i + 1, true); } }}>
            <Icon name="grip-vertical" />
          </button>
          <div class="mct-sort-body">{render(t)}</div>
        </li>
      ))}
    </ul>
  );
}

export function Chip({ icon, label, color, onRemove }: { icon?: string; label: string; color?: string; onRemove?: () => void }): JSX.Element {
  return (
    <span class="mct-chip">
      {icon && <Icon name={icon} size={16} color={color} />}{label}
      {onRemove && <button type="button" aria-label={label + ' entfernen'} onClick={onRemove}><Icon name="x" size={14} /></button>}
    </span>
  );
}
