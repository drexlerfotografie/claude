import { readFileSync } from 'node:fs';
import { test, expect, login, open, seed, rest } from './helpers';

const expected = JSON.parse(readFileSync('tests/fixtures/sample.expected.json', 'utf8')) as Record<string, unknown>;
type Counts = Record<string, unknown>;

function sameCounts(got: Counts): void {
  for (const [k, v] of Object.entries(expected)) expect(got[k], `Zählwert ${k}`).toEqual(v);
}

test.describe('Import, Sicherung und Wiederherstellung über die Oberfläche', () => {
  test.beforeAll(() => seed('ben', true));
  test.beforeEach(async ({ page }) => { await login(page, 'ben'); });

  test('Backup-Datei: Vorschau, Import in leeres Tagebuch, Bericht, alle Zählwerte', async ({ page }) => {
    await open(page);
    await page.getByLabel('Ich bin mindestens 18 Jahre alt.').check();
    await page.getByLabel(/Ich willige ein, dass MediClever/).check();
    await page.getByRole('button', { name: 'Einwilligen und weiter' }).click();
    await page.getByRole('button', { name: 'Stattdessen eine Sicherung importieren' }).click();
    await expect(page.getByRole('heading', { name: 'Import und Wiederherstellen' })).toBeVisible();

    // Der Browser öffnet die Datei lokal und zeigt die Zählwerte.
    await page.getByLabel('Datei wählen').setInputFiles('tests/fixtures/sample.daylio');
    const preview = page.locator('.mct-card', { hasText: 'Vorschau' });
    await expect(preview.getByRole('row', { name: /Einträge/ }).first()).toContainText(String(expected.entries));
    await expect(preview.getByRole('row', { name: /Gruppen \/ Aktivitäten/ })).toContainText('3 / 13 (davon 2 archiviert)');
    await expect(preview.getByRole('row', { name: /Fotos/ })).toContainText('16 (16 in der Datei gefunden)');
    await expect(preview.getByRole('radio', { name: 'In ein leeres Tagebuch' })).toHaveAttribute('aria-checked', 'true');
    await preview.getByRole('button', { name: 'Import starten' }).click();

    const report = page.locator('.mct-card', { hasText: 'Importbericht' });
    await expect(report).toBeVisible({ timeout: 60_000 });
    await expect(report.getByRole('row', { name: /Einträge/ })).toContainText(`${expected.entries}`);
    await expect(report.getByRole('row', { name: /Fotos/ })).toContainText('16');
    await expect(report.getByRole('status')).toContainText('0 Fehler');
    sameCounts((await rest<Counts>(page, 'GET', '/counts')).data);

    // Importierte Einträge samt Fotos sind in der App zu sehen.
    await report.getByRole('link', { name: 'Zu den Einträgen' }).click();
    await expect(page.locator('.mct-entry').first()).toBeVisible();
    await open(page, '/mehr/fotos');
    await expect(page.locator('.mct-count')).toContainText('14 Fotos');
    await open(page, '/mehr/ziele');
    await expect(page.locator('.mct-goal', { hasText: 'Bewegung' })).toContainText(/Serie \d+/);
  });

  test('zweiter Lauf derselben Datei erzeugt keine Doppelten', async ({ page }) => {
    await open(page, '/mehr/daten');
    await page.getByLabel('Datei wählen').setInputFiles('tests/fixtures/sample.daylio');
    await page.getByRole('radio', { name: 'Zusammenführen' }).click();
    await page.getByRole('button', { name: 'Import starten' }).click();
    const report = page.locator('.mct-card', { hasText: 'Importbericht' });
    await expect(report).toBeVisible({ timeout: 60_000 });
    const row = report.getByRole('row', { name: /Einträge/ });
    await expect(row.getByRole('cell').nth(0)).toHaveText('0');
    await expect(row.getByRole('cell').nth(1)).toHaveText(String(expected.entries));
    sameCounts((await rest<Counts>(page, 'GET', '/counts')).data);
  });

  test('Import in ein gefülltes Tagebuch fragt vor dem Löschen nach', async ({ page }) => {
    await open(page, '/mehr/daten');
    // Eine andere Datei (eigene Sicherung) wäre ein neuer Lauf: Hier genügt der Hinweis-Dialog mit derselben Datei im Modus „leer“ nach einem zusätzlichen Eintrag.
    const before = (await rest<Counts>(page, 'GET', '/counts')).data;
    const [dl] = await Promise.all([page.waitForEvent('download'), page.getByRole('link', { name: /Vollständige Sicherung/ }).click()]);
    const path = await dl.path();
    expect(dl.suggestedFilename()).toMatch(/\.mctb$/);

    // Wiederherstellen aus der eigenen Sicherung in ein leeres Tagebuch.
    await page.getByLabel('Datei wählen').setInputFiles({ name: 'sicherung.mctb', mimeType: 'application/zip', buffer: readFileSync(path) });
    await expect(page.locator('.mct-card', { hasText: 'Vorschau' })).toContainText('MediClever-Sicherung');
    await page.getByRole('button', { name: 'Import starten' }).click();
    const ask = page.getByRole('dialog', { name: 'Vorhandene Einträge löschen?' });
    await expect(ask).toContainText(`${expected.entries} Einträge`);
    await ask.getByRole('button', { name: 'Löschen und importieren' }).click();
    await expect(page.locator('.mct-card', { hasText: 'Importbericht' })).toBeVisible({ timeout: 60_000 });
    const after = (await rest<Counts>(page, 'GET', '/counts')).data;
    expect(after).toEqual(before);
  });

  test('CSV-Datei: Fehlendes wird erst nach Rückfrage angelegt', async ({ page }) => {
    await open(page, '/mehr/daten');
    const before = (await rest<{ entries: number; tags: number }>(page, 'GET', '/counts')).data;
    await page.getByLabel('Datei wählen').setInputFiles('tests/fixtures/sample.csv');
    const card = page.locator('.mct-card', { hasText: 'CSV-Vorschau' });
    await expect(card).toContainText('3 Zeilen');
    await expect(card).toContainText('Fehlende Aktivitäten: Sauna');
    await expect(card).toContainText('Fehlende Skalen: Energie');
    await card.getByLabel('Entspannt').selectOption('2');
    await card.getByRole('button', { name: 'Fehlendes anlegen und importieren' }).click();
    await expect(page.locator('.mct-card', { hasText: 'Importbericht' }).getByRole('status')).toContainText('3 übernommen');
    const after = (await rest<{ entries: number; tags: number }>(page, 'GET', '/counts')).data;
    expect(after.entries).toBe(before.entries + 3);
    expect(after.tags).toBe(before.tags + 1);
  });

  test('beschädigte Datei zeigt eine verständliche Meldung', async ({ page }) => {
    await open(page, '/mehr/daten');
    await page.getByLabel('Datei wählen').setInputFiles({ name: 'kaputt.daylio', mimeType: 'application/zip', buffer: Buffer.from('das ist kein Archiv') });
    await expect(page.getByRole('alert')).toContainText('kein lesbares Archiv');
  });
});
