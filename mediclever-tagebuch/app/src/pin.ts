// PIN-Sperre: sperrt die Oberfläche auf diesem Gerät. Gespeichert wird nur ein gesalzener Hash im Browser.
const KEY = 'mct_pin';

async function hash(pin: string, salt: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(salt + ':' + pin));
  return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
}

function read(): { salt: string; hash: string } | null {
  try { return JSON.parse(localStorage.getItem(KEY) || 'null'); } catch { return null; }
}

export const hasPin = (): boolean => read() !== null;

export async function setPin(pin: string): Promise<void> {
  const salt = Array.from(crypto.getRandomValues(new Uint8Array(12)), (b) => b.toString(16)).join('');
  localStorage.setItem(KEY, JSON.stringify({ salt, hash: await hash(pin, salt) }));
  sessionStorage.setItem(KEY + '_open', '1');
}

export function clearPin(): void {
  localStorage.removeItem(KEY);
}

export async function checkPin(pin: string): Promise<boolean> {
  const p = read();
  if (!p) return true;
  const ok = (await hash(pin, p.salt)) === p.hash;
  if (ok) sessionStorage.setItem(KEY + '_open', '1');
  return ok;
}

export const isLocked = (): boolean => hasPin() && sessionStorage.getItem(KEY + '_open') !== '1';
export const lock = (): void => sessionStorage.removeItem(KEY + '_open');
