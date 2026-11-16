// Zugriff auf die REST-Schnittstelle. Anmeldung über das WordPress-Konto mit Nonce.
export interface Config { rest: string; nonce: string; assets: string; sw: string; logout: string; account: string }

const el = document.getElementById('mct-app');
export const cfg: Config = JSON.parse(el?.dataset.config || '{"rest":"","nonce":"","assets":"","sw":"","logout":"","account":""}');

export class ApiError extends Error {
  code: string;
  status: number;
  data: Record<string, unknown>;
  constructor(message: string, code: string, status: number, data: Record<string, unknown>) {
    super(message);
    this.code = code;
    this.status = status;
    this.data = data;
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

function url(path: string, query?: Query): string {
  const u = new URL(cfg.rest + path, location.origin);
  for (const [k, v] of Object.entries(query || {})) {
    if (v !== undefined && v !== null && v !== '' && v !== false) u.searchParams.set(k, String(v === true ? 1 : v));
  }
  return u.toString();
}

async function handle<T>(res: Response): Promise<T> {
  const text = await res.text();
  let json: unknown = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* keine JSON-Antwort */ }
  if (!res.ok) {
    const j = (json || {}) as { message?: string; code?: string; data?: Record<string, unknown> };
    const msg = res.status === 403 && j.code === 'rest_cookie_invalid_nonce'
      ? 'Deine Anmeldung ist abgelaufen. Bitte lade die Seite neu.'
      : j.message || 'Das hat nicht geklappt. Bitte versuch es noch einmal.';
    throw new ApiError(msg, (j.code || 'error').replace(/^mct_/, ''), res.status, j.data || {});
  }
  return json as T;
}

export async function api<T>(method: 'GET' | 'POST' | 'DELETE', path: string, body?: unknown, query?: Query): Promise<T> {
  const headers: Record<string, string> = { 'X-WP-Nonce': cfg.nonce };
  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res: Response;
  try {
    res = await fetch(url(path, query), { method, headers, body: payload, credentials: 'same-origin' });
  } catch {
    throw new ApiError('Keine Verbindung. Bitte prüfe dein Netz.', 'offline', 0, {});
  }
  return handle<T>(res);
}

export const get = <T>(path: string, query?: Query) => api<T>('GET', path, undefined, query);
export const post = <T>(path: string, body?: unknown) => api<T>('POST', path, body ?? {});
export const del = <T>(path: string, query?: Query) => api<T>('DELETE', path, undefined, query);

/** Adresse einer Datei des Nutzers. Fotos sind nie öffentlich abrufbar, daher mit Nonce. */
export function assetUrl(id: number, size: 's' | 'm' | 'full' = 's'): string {
  return url(`/assets/${id}`, { size, _wpnonce: cfg.nonce });
}

export function downloadUrl(path: string, query?: Query): string {
  return url(path, { ...query, _wpnonce: cfg.nonce });
}
