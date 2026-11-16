import { useEffect, useState, useCallback, useRef } from 'preact/hooks';
import { get, ApiError } from './api';
import type { Boot, Mood, Tag, Scale, Group } from './types';

interface Toast { id: number; text: string; kind: 'ok' | 'error' }
interface State { boot: Boot | null; month: string; toasts: Toast[]; version: number; error: string | null }

export const state: State = { boot: null, month: '', toasts: [], version: 0, error: null };
const listeners = new Set<() => void>();

export function emit(): void {
  listeners.forEach((l) => l());
}

export function useStore(): State {
  const [, force] = useState(0);
  useEffect(() => {
    const l = () => force((n) => n + 1);
    listeners.add(l);
    return () => { listeners.delete(l); };
  }, []);
  return state;
}

export async function loadBoot(): Promise<void> {
  try {
    state.boot = await get<Boot>('/bootstrap');
    if (!state.month) state.month = state.boot.today.slice(0, 7);
    state.error = null;
  } catch (e) {
    state.error = e instanceof ApiError ? e.message : 'Das Tagebuch lässt sich gerade nicht laden.';
  }
  emit();
}

/** Nach einer Änderung: Grunddaten neu laden und alle Ansichten auffrischen. */
export async function changed(): Promise<void> {
  await loadBoot();
  state.version++;
  emit();
}

let toastId = 0;
export function toast(text: string, kind: 'ok' | 'error' = 'ok'): void {
  const t = { id: ++toastId, text, kind };
  state.toasts = [...state.toasts, t];
  emit();
  setTimeout(() => {
    state.toasts = state.toasts.filter((x) => x.id !== t.id);
    emit();
  }, kind === 'error' ? 6000 : 3000);
}

export function fail(e: unknown): void {
  toast(e instanceof Error ? e.message : 'Das hat nicht geklappt.', 'error');
}

export function setMonth(m: string): void {
  state.month = m;
  emit();
}

// ---- Routing über den Hash ------------------------------------------------

export interface Route { path: string; parts: string[]; query: URLSearchParams }

function parse(): Route {
  const raw = location.hash.replace(/^#/, '') || '/eintraege';
  const [path, q = ''] = raw.split('?');
  return { path, parts: path.split('/').filter(Boolean), query: new URLSearchParams(q) };
}

export function useRoute(): Route {
  const [route, setRoute] = useState(parse());
  useEffect(() => {
    const on = () => { setRoute(parse()); window.scrollTo(0, 0); };
    window.addEventListener('hashchange', on);
    // Falls sich die Adresse zwischen dem ersten Zeichnen und dem Anmelden dieses Lauschers geändert hat.
    if (parse().path !== route.path) on();
    return () => window.removeEventListener('hashchange', on);
  }, []);
  return route;
}

export function go(path: string): void {
  location.hash = path;
}

export function back(fallback = '/mehr'): void {
  if (history.length > 1 && document.referrer !== '' || history.state !== null) history.back();
  else go(fallback);
}

// ---- Daten laden ----------------------------------------------------------

export interface Loaded<T> { data: T | null; error: string | null; loading: boolean; reload: () => void }

export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]): Loaded<T> {
  const [s, set] = useState<{ data: T | null; error: string | null; loading: boolean }>({ data: null, error: null, loading: true });
  const run = useRef(0);
  const { version } = useStore();
  const load = useCallback(() => {
    const id = ++run.current;
    set((p) => ({ ...p, loading: true, error: null }));
    fn().then(
      (data) => { if (id === run.current) set({ data, error: null, loading: false }); },
      (e) => { if (id === run.current) set({ data: null, error: e instanceof Error ? e.message : 'Fehler beim Laden.', loading: false }); },
    );
     
  }, deps);
  useEffect(load, [load, version]);
  return { ...s, reload: load };
}

// ---- Nachschlagen ---------------------------------------------------------

export interface Lookup { moods: Map<number, Mood>; tags: Map<number, Tag>; scales: Map<number, Scale>; groups: Map<number, Group> }

export function lookup(boot: Boot): Lookup {
  return {
    moods: new Map(boot.setup.moods.map((m) => [m.id, m])),
    tags: new Map(boot.setup.tags.map((t) => [t.id, t])),
    scales: new Map(boot.setup.scales.map((s) => [s.id, s])),
    groups: new Map(boot.setup.groups.map((g) => [g.id, g])),
  };
}

export const feature = (key: string): boolean => state.boot?.features[key] !== false;
