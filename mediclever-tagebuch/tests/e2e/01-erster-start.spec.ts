import { test, expect, login, open, seed, rest } from './helpers';

test.describe('Erster Start und erster Eintrag', () => {
  test.beforeAll(() => seed('leer', true));

  test('neues Konto: Einwilligung, Startpaket, vollständiger Eintrag mit Notiz und Foto in zwei Schritten', async ({ page }) => {
    const started = Date.now();
    await login(page, 'leer');
    await open(page);

    // Schritt 1: Einwilligung. Ohne beide Haken geht es nicht weiter.
    await expect(page.getByRole('heading', { name: 'Bevor es losgeht' })).toBeVisible();
    const agree = page.getByRole('button', { name: 'Einwilligen und weiter' });
    await expect(agree).toBeDisabled();
    await page.getByLabel('Ich bin mindestens 18 Jahre alt.').check();
    await page.getByLabel(/Ich willige ein, dass MediClever/).check();
    await agree.click();

    // Schritt 2: Startpaket als Vorschlag.
    await expect(page.getByRole('heading', { name: 'Womit möchtest du starten?' })).toBeVisible();
    await page.getByRole('radio', { name: /Schichtdienst/ }).click();
    await expect(page.locator('.mct-packpreview')).toContainText('Nachtdienst');
    await page.getByRole('button', { name: 'Mit diesem Paket starten' }).click();

    // Leerzustand der Einträge.
    await expect(page.getByRole('heading', { name: 'Noch kein Eintrag in diesem Monat' })).toBeVisible();
    await page.getByRole('link', { name: 'Ersten Eintrag schreiben' }).click();

    // Eintrag Schritt 1: Datum, Uhrzeit, Stimmung.
    await expect(page.getByText('Schritt 1 von 2')).toBeVisible();
    await expect(page.getByLabel('Datum')).toBeVisible();
    await expect(page.getByLabel('Uhrzeit')).toBeVisible();
    await page.getByRole('radio', { name: 'Gut', exact: true }).click();

    // Eintrag Schritt 2: Aktivitäten, Skala, Notiz, Foto.
    await expect(page.getByText('Schritt 2 von 2')).toBeVisible();
    await page.getByRole('button', { name: 'Frühdienst' }).click();
    await page.getByRole('button', { name: 'Sport' }).click();
    await page.getByRole('radiogroup', { name: 'Schlafqualität' }).getByRole('radio', { name: 'Gut', exact: true }).click();
    await page.getByRole('button', { name: 'Schlafdauer: 15 Minuten mehr' }).click();
    await page.getByLabel('Schnelle Notiz').fill('Erster Eintrag im Test');
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mP8z8BQz0AEYBxVSF+FABJADveWkH6oAAAAAElFTkSuQmCC', 'base64');
    await page.getByLabel('Aus Galerie').setInputFiles({ name: 'bild.png', mimeType: 'image/png', buffer: png });
    await expect(page.getByAltText('Angehängtes Foto')).toBeVisible();
    await page.getByRole('button', { name: 'Speichern' }).last().click();

    // Der Eintrag steht in der Liste, mit allem, was erfasst wurde.
    const card = page.locator('.mct-entry').first();
    await expect(card).toContainText('Gut');
    await expect(card).toContainText('Frühdienst');
    await expect(card).toContainText('Sport');
    await expect(card).toContainText('Schlafqualität');
    await expect(card).toContainText('7:00 Std.');
    await expect(card).toContainText('Erster Eintrag im Test');
    await expect(card.locator('.mct-photos img')).toHaveCount(1);
    expect(Date.now() - started, 'Einrichtung und erster Eintrag dauern deutlich unter 3 Minuten').toBeLessThan(180_000);

    const counts = await rest<{ entries: number; assets: number }>(page, 'GET', '/counts');
    expect(counts.data.entries).toBe(1);
    expect(counts.data.assets).toBe(1);
  });

  test('ganze Notiz mit Titel, Formatierung und Schreibvorlage', async ({ page }) => {
    await login(page, 'leer');
    await open(page, '/eintrag/neu?date=2026-11-10&fix=1');
    await page.getByRole('radio', { name: 'Mittel', exact: true }).click();
    await page.getByRole('button', { name: /Ganze Notiz/ }).click();
    await page.getByLabel('Titel (optional)').fill('Mein Titel');
    await page.getByRole('textbox', { name: 'Notiz', exact: true }).click();
    await page.keyboard.type('Normaler Text ');
    await page.getByRole('button', { name: 'Fett' }).click();
    await page.keyboard.type('wichtig');
    await page.getByRole('button', { name: 'Schreibvorlage einfügen' }).click();
    await page.getByRole('button', { name: 'Dankbarkeit' }).click();
    await page.getByRole('button', { name: 'Übernehmen' }).click();
    await page.getByRole('button', { name: 'Speichern' }).last().click();
    const card = page.locator('.mct-entry', { hasText: 'Mein Titel' });
    await expect(card.locator('.mct-note b, .mct-note strong')).toContainText('wichtig');
    await expect(card.locator('.mct-note ol li')).toHaveCount(3);
  });

  test('Einrichtung ändern: Gruppe, Aktivität und Skala anlegen, direkt beim Erfassen ergänzen', async ({ page }) => {
    await login(page, 'leer');
    await open(page, '/mehr/aktivitaeten');
    await page.getByRole('button', { name: 'Gruppe', exact: true }).click();
    await page.getByLabel('Name').fill('🎯 Projekte');
    await page.getByRole('radio', { name: 'Blau' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('heading', { name: '🎯 Projekte' })).toBeVisible();
    await page.getByRole('button', { name: 'Neue Aktivität in 🎯 Projekte' }).click();
    await page.getByLabel('Name').fill('Werkstatt');
    await page.getByLabel('Symbol suchen').fill('werkzeug');
    await page.getByRole('radio', { name: 'Reparieren' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('button', { name: 'Werkstatt', exact: true })).toBeVisible();

    await open(page, '/mehr/skalen');
    await page.getByRole('button', { name: 'Neue Skala' }).click();
    await page.getByLabel('Name').fill('Schmerz');
    await page.getByRole('radio', { name: 'Zahl' }).click();
    await page.getByRole('radio', { name: 'Höher ist schlecht' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('button', { name: /Schmerz/ })).toContainText('höher ist schlecht');

    // Beim Erfassen: Plus in der Gruppe legt direkt eine Aktivität an.
    await open(page, '/eintrag/neu?date=2026-11-09&fix=1');
    await page.getByRole('radio', { name: 'Gut', exact: true }).click();
    await page.getByRole('button', { name: 'Neue Aktivität in 🎯 Projekte anlegen' }).click();
    await page.getByLabel('Name').fill('Garage');
    await page.getByRole('button', { name: 'Anlegen', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Garage' })).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('slider', { name: 'Schmerz' }).fill('7');
    await page.getByRole('button', { name: 'Speichern' }).last().click();
    const card = page.locator('.mct-entry', { hasText: 'Garage' });
    await expect(card).toContainText('Schmerz');
    await expect(card).toContainText('7');
  });
});
