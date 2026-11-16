import { execFileSync } from 'node:child_process';
import { expect, test as base, type Page } from '@playwright/test';

export const APP = '/tagebuch/';

/** Setzt ein Testkonto zurück: mit Beispieldaten oder leer. Alle Inhalte sind erfunden. */
export function seed(user: string, empty = false): void {
  execFileSync('php', ['scripts/seed.php', user, ...(empty ? ['leer'] : [])], { stdio: 'pipe' });
}

export async function login(page: Page, user: string): Promise<void> {
  // Direkt zur Tagebuch-Seite weiterleiten: Die WordPress-Profilseite gehört nicht zur App.
  await page.goto('/wp-login.php?redirect_to=' + encodeURIComponent(APP));
  await page.fill('#user_login', user);
  await page.fill('#user_pass', `${user}-passwort`);
  await page.click('#wp-submit');
  await page.waitForURL((u) => !u.pathname.includes('wp-login'));
}

export async function open(page: Page, route = '/eintraege'): Promise<void> {
  await page.goto(`${APP}#${route}`);
  await page.locator('.mct').first().waitFor();
  await expect(page.locator('.mct-loading')).toHaveCount(0);
}

/** REST-Aufruf als angemeldeter Nutzer, mit dem Nonce der Seite. */
export async function rest<T>(page: Page, method: string, path: string, body?: unknown): Promise<{ status: number; data: T }> {
  return page.evaluate(async ({ method, path, body }) => {
    const cfg = JSON.parse((document.getElementById('mct-app') as HTMLElement).dataset.config || '{}');
    const res = await fetch(cfg.rest + path, { method, headers: { 'X-WP-Nonce': cfg.nonce, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    return { status: res.status, data: await res.json().catch(() => null) };
  }, { method, path, body }) as Promise<{ status: number; data: T }>;
}

/** Wie rest(), aber ohne Spuren in der Browser-Konsole. Für Aufrufe, die absichtlich abgelehnt werden. */
export async function restQuiet(page: Page, method: 'GET' | 'POST' | 'DELETE', path: string, body?: unknown): Promise<number> {
  const cfg = JSON.parse((await page.locator('#mct-app').getAttribute('data-config')) || '{}');
  const res = await page.request.fetch(cfg.rest + path, { method, headers: { 'X-WP-Nonce': cfg.nonce }, data: body });
  return res.status();
}

/** Jeder Test prüft nebenbei: keine Fehler in der Browser-Konsole, keine Anfragen an fremde Server. */
export const test = base.extend<{ guard: void }>({
  guard: [async ({ page }, use) => {
    const errors: string[] = [];
    const external: string[] = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    page.on('pageerror', (e) => errors.push('Fehler im Skript: ' + e.message + ' @ ' + page.url() + ' ' + (e.stack || '').split('\n').slice(1, 4).join(' | ')));
    page.on('request', (r) => { const u = new URL(r.url()); if (!['127.0.0.1', 'localhost'].includes(u.hostname) && !['data:', 'blob:', 'about:'].includes(u.protocol)) external.push(r.url()); });
    await use();
    const allowed = (process.env.MCT_EXPECTED_ERRORS || '').split('|').filter(Boolean);
    expect(errors.filter((e) => !allowed.some((a) => e.includes(a))), 'Browser-Konsole bleibt ohne Fehler').toEqual([]);
    expect(external, 'keine Anfragen an fremde Server').toEqual([]);
  }, { auto: true }],
});
export { expect };
