import { mkdirSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { test, expect, login, open, seed } from './helpers';
import type { Page } from '@playwright/test';

/** Alle Bildschirme mit künstlichen Beispieldaten: Screenshots in vier Varianten, Prüfung auf Barrierefreiheit, Überlauf und Tippflächen. */
const SCREENS: [string, string, ((p: Page) => Promise<void>)?][] = [
  ['eintraege', '/eintraege'],
  ['eintrag-schritt-1', '/eintrag/neu?date=2026-11-13&fix=1'],
  ['eintrag-schritt-2', '/eintrag/neu?date=2026-11-13&fix=1', async (p) => { await p.getByRole('radio', { name: 'Gut', exact: true }).click(); await p.getByRole('button', { name: 'Sport' }).click(); }],
  ['plus-schnellwahl', '/eintraege', async (p) => { await p.getByRole('button', { name: 'Neu anlegen' }).click(); }],
  ['monatsauswahl', '/eintraege', async (p) => { await p.getByRole('button', { name: /November 2026/ }).click(); }],
  ['suche', '/suche', async (p) => { await p.getByLabel('Text in Notiz oder Titel').fill('Dienst'); await p.locator('.mct-entry').first().waitFor(); }],
  ['statistik', '/statistik'],
  ['kalender', '/kalender'],
  ['tag', '/tag/2026-11-14'],
  ['mehr', '/mehr'],
  ['ziele', '/mehr/ziele'],
  ['ziel-detail', '/mehr/ziele', async (p) => { await p.locator('.mct-goal-main').first().click(); }],
  ['berichte', '/mehr/berichte'],
  ['rueckblick', '/mehr/rueckblick'],
  ['wichtige-tage', '/mehr/wichtige-tage'],
  ['favoriten', '/mehr/favoriten'],
  ['fotos', '/mehr/fotos'],
  ['erfolge', '/mehr/erfolge'],
  ['erinnerungen', '/mehr/erinnerungen'],
  ['einrichtung', '/mehr/einrichtung'],
  ['stimmungen', '/mehr/stimmungen'],
  ['aktivitaeten', '/mehr/aktivitaeten'],
  ['aktivitaet-bearbeiten', '/mehr/aktivitaeten', async (p) => { await p.getByRole('button', { name: 'Sport', exact: true }).click(); }],
  ['skalen', '/mehr/skalen'],
  ['skala-bearbeiten', '/mehr/skalen', async (p) => { await p.getByRole('button', { name: /Schlafqualität/ }).click(); }],
  ['reihenfolge', '/mehr/reihenfolge'],
  ['vorlagen', '/mehr/vorlagen'],
  ['aussehen', '/mehr/aussehen'],
  ['sicherheit', '/mehr/sicherheit'],
  ['einstellungen', '/mehr/einstellungen'],
  ['daten', '/mehr/daten'],
  ['ki-zugriff', '/mehr/ki'],
  ['konto', '/mehr/konto'],
];

const VARIANTS = [
  { name: 'mobil-hell', width: 412, height: 915, scheme: 'light' as const },
  { name: 'mobil-dunkel', width: 412, height: 915, scheme: 'dark' as const },
  { name: 'desktop-hell', width: 1280, height: 800, scheme: 'light' as const },
  { name: 'desktop-dunkel', width: 1280, height: 800, scheme: 'dark' as const },
];

/** Tippflächen: alles Bedienbare misst mindestens 44 px, Textlinks im Fließtext ausgenommen. */
async function smallTargets(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const out: string[] = [];
    const scope = document.querySelector('dialog[open]') || document.querySelector('.mct');
    scope?.querySelectorAll<HTMLElement>('button, a, select, input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range]):not([type=color])').forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0 || el.closest('.mct-sr') || el.classList.contains('mct-sr') || el.classList.contains('mct-skip')) return;
      if (el.closest('.mct-note')) return;
      if (r.width < 43.5 || r.height < 43.5) out.push(`${el.tagName.toLowerCase()}.${el.className} „${(el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 30)}“ ${Math.round(r.width)}×${Math.round(r.height)}`);
    });
    return out;
  });
}

test.describe('Bildschirme @screenshot', () => {
  test.beforeAll(() => seed('anna'));

  for (const v of VARIANTS) {
    test(`alle Bildschirme: ${v.name}`, async ({ browser }) => {
      test.setTimeout(240_000);
      mkdirSync(`screenshots/${v.name}`, { recursive: true });
      const context = await browser.newContext({ viewport: { width: v.width, height: v.height }, colorScheme: v.scheme, locale: 'de-DE', timezoneId: 'Europe/Berlin', deviceScaleFactor: 1 });
      const page = await context.newPage();
      const errors: string[] = [];
      page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
      page.on('pageerror', (e) => errors.push(e.message));
      await login(page, 'anna');
      const problems: string[] = [];
      for (const [name, route, prepare] of SCREENS) {
        await open(page, '/mehr/konto');
        await open(page, route);
        if (prepare) await prepare(page);
        await page.waitForTimeout(250);
        await page.screenshot({ path: `screenshots/${v.name}/${name}.png`, fullPage: true });
        const axe = await new AxeBuilder({ page: page as never }).include('#mct-app').analyze();
        for (const x of axe.violations.filter((a) => a.impact === 'serious' || a.impact === 'critical')) {
          problems.push(`${name}: axe ${x.id} (${x.impact}) – ${x.nodes.slice(0, 2).map((n) => n.target.join(' ') + ' ' + (n.any[0]?.message || '')).join(' ; ')}`);
        }
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
        if (overflow > 1) problems.push(`${name}: Überlauf um ${overflow} px`);
        for (const t of await smallTargets(page)) problems.push(`${name}: Tippfläche zu klein – ${t}`);
      }
      expect(problems, 'keine schweren Fehler bei Barrierefreiheit, Überlauf und Tippflächen').toEqual([]);
      expect(errors, 'Browser-Konsole bleibt ohne Fehler').toEqual([]);
      await context.close();
    });
  }

  test('schmales Gerät (360 px): kein Überlauf, kein abgeschnittener Text in der Navigation', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 360, height: 740 }, locale: 'de-DE', timezoneId: 'Europe/Berlin' });
    const page = await context.newPage();
    await login(page, 'anna');
    const problems: string[] = [];
    for (const [name, route, prepare] of SCREENS) {
      await open(page, '/mehr/konto');
      await open(page, route);
      if (prepare) await prepare(page);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      if (overflow > 1) problems.push(`${name}: Überlauf um ${overflow} px`);
    }
    expect(problems).toEqual([]);
    await context.close();
  });

  test('Tastaturbedienung: Navigation, Dialog schließt mit Escape, Fokus kehrt zurück', async ({ page }) => {
    await login(page, 'anna');
    await open(page);
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Zum Inhalt springen' })).toBeFocused();
    const plus = page.getByRole('button', { name: 'Neu anlegen' });
    await plus.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Neu anlegen' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(plus).toBeFocused();
    await page.getByRole('link', { name: 'Statistiken' }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: 'Stimmungsverlauf' })).toBeVisible();
  });
});
