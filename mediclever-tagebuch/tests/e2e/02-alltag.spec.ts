import { test, expect, login, open, seed, rest, restQuiet } from './helpers';

test.describe('Alltag mit Beispieldaten', () => {
  test.beforeAll(() => seed('anna'));
  test.beforeEach(async ({ page }) => { await login(page, 'anna'); });

  test('Einträge: Monatsliste, Monatswechsel, Bearbeiten, Favorit, Löschen', async ({ page }) => {
    await open(page);
    await expect(page.getByRole('navigation', { name: 'Hauptbereiche' }).getByRole('link')).toHaveText(['Einträge', 'Statistiken', 'Kalender', 'Mehr']);
    await expect(page.getByRole('button', { name: 'Neu anlegen' })).toBeVisible();
    await expect(page.locator('.mct-entry').first()).toContainText('16. November');
    await expect(page.getByRole('complementary', { name: 'Satz des Tages' })).toBeVisible();
    await expect(page.getByLabel('Ziele für heute')).toContainText('2 Liter trinken');
    await expect(page.getByRole('link', { name: /Vor 1 Jahr/ })).toBeVisible();
    await expect(page.locator('.mct-gap').first()).toContainText(/Tage? fehl/);

    // Monat zurück und per Monatsauswahl wieder vor.
    await page.getByRole('button', { name: 'Vorheriger Monat' }).click();
    await expect(page.getByRole('button', { name: /Oktober 2026/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Monatsbericht Oktober 2026/ })).toBeVisible();
    await page.getByRole('button', { name: 'Heute anzeigen' }).click();
    await page.getByRole('button', { name: /November 2026/ }).click();
    await page.getByRole('button', { name: /September/ }).click();
    await expect(page.locator('.mct-entry').first()).toContainText('September');
    await page.getByRole('button', { name: 'Heute anzeigen' }).click();
    await expect(page.locator('.mct-entry').first()).toContainText('November');

    // Bearbeiten
    const first = page.locator('.mct-entry').first();
    await first.getByRole('button', { name: 'Eintrag bearbeiten oder löschen' }).click();
    await page.getByRole('button', { name: 'Bearbeiten', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Eintrag bearbeiten' })).toBeVisible();
    await page.getByLabel('Schnelle Notiz').fill('Geänderte Notiz');
    await page.getByRole('button', { name: 'Speichern' }).first().click();
    await expect(page.locator('.mct-entry').first()).toContainText('Geänderte Notiz');

    // Favorit
    const star = page.locator('.mct-entry').first().getByRole('button', { name: 'Als Favorit markieren' });
    await star.click();
    await expect(star).toHaveAttribute('aria-pressed', 'true');
    await open(page, '/mehr/favoriten');
    await expect(page.locator('.mct-entry', { hasText: 'Geänderte Notiz' })).toBeVisible();

    // Löschen mit Rückfrage
    await open(page);
    const before = (await rest<{ entries: number }>(page, 'GET', '/counts')).data.entries;
    await page.locator('.mct-entry').first().getByRole('button', { name: 'Eintrag bearbeiten oder löschen' }).click();
    await page.getByRole('dialog', { name: 'Eintrag', exact: true }).getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByRole('dialog', { name: 'Eintrag löschen?' })).toBeVisible();
    await page.getByRole('dialog', { name: 'Eintrag löschen?' }).getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByText('Eintrag gelöscht.')).toBeVisible();
    expect((await rest<{ entries: number }>(page, 'GET', '/counts')).data.entries).toBe(before - 1);
  });

  test('Plus-Schnellwahl: Heute, Gestern, anderer Tag, neues Ziel, neuer wichtiger Tag', async ({ page }) => {
    await open(page);
    await page.getByRole('button', { name: 'Neu anlegen' }).click();
    const sheet = page.getByRole('dialog', { name: 'Neu anlegen' });
    await expect(sheet.getByRole('button')).toContainText(['Heute', 'Gestern', 'Anderer Tag', 'Neues Ziel', 'Neuer wichtiger Tag']);
    await sheet.getByRole('button', { name: 'Gestern' }).click();
    await expect(page.locator('.mct-daylabel')).toContainText('15. November');
    await page.getByRole('button', { name: 'Abbrechen' }).click();

    await page.getByRole('button', { name: 'Neu anlegen' }).click();
    await page.getByRole('button', { name: 'Neuer wichtiger Tag' }).click();
    await page.getByLabel('Name').fill('Erster Dienst');
    await page.getByLabel('Datum').fill('2020-09-01');
    await page.getByLabel('Kategorie (optional)').fill('Beruf');
    await page.getByRole('button', { name: 'Speichern' }).click();
    const ms = page.locator('.mct-ms', { hasText: 'Erster Dienst' });
    await expect(ms).toContainText(/seit [\d.]+ Tagen/);
    await expect(ms).toContainText(/nächster Jahrestag in \d+ Tagen/);

    await page.getByRole('button', { name: 'Neu anlegen' }).click();
    await page.getByRole('button', { name: 'Neues Ziel' }).click();
    await page.getByLabel('Name').fill('Dehnen');
    await page.getByRole('radio', { name: 'Wochentage' }).click();
    await page.getByRole('button', { name: 'Dienstag' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.locator('.mct-goal', { hasText: 'Dehnen' })).toContainText('Mo, Di, Mi, Fr');
  });

  test('Ziele: abhaken, Serie, Erfolgsraten, Kalender, Archiv', async ({ page }) => {
    await open(page, '/mehr/ziele');
    const goal = page.locator('.mct-goal', { hasText: '2 Liter trinken' });
    const streak = Number(/Serie (\d+)/.exec(await goal.innerText())![1]);
    expect(streak).toBeGreaterThanOrEqual(6);
    await goal.getByRole('button', { name: '2 Liter trinken für heute abhaken' }).click();
    await expect(goal).toContainText(`Serie ${streak + 1}`);
    await goal.getByRole('button', { name: /2 Liter trinken.*Serie/ }).click();
    const detail = page.getByRole('dialog', { name: '2 Liter trinken' });
    await expect(detail).toContainText('Erfolg 7 Tage');
    await expect(detail).toContainText('100 %');
    await expect(detail).toContainText('Stufe');
    await expect(detail).toContainText('Erinnerung 18:00 Uhr');
    await expect(detail.getByRole('button', { name: /16\. November, abgehakt/ })).toBeVisible();
    await detail.getByRole('button', { name: 'Archivieren' }).click();
    await expect(page.getByRole('button', { name: /Archiv \(1\)/ })).toBeVisible();
    await expect(page.locator('.mct-goal', { hasText: '2 Liter trinken' })).toHaveCount(0);
  });

  test('Statistiken: alle Auswertungen des Monats und die Jahresansicht', async ({ page }) => {
    await open(page, '/statistik');
    for (const title of ['Tage in Folge', 'Auf einen Blick', 'Stimmungsverlauf', 'Stimmungszähler', 'Aktivitätenzähler', 'Verteilung nach Gruppen', 'Stimmung je Wochentag',
      'Einfluss auf die Stimmung', 'Stimmungsstabilität', 'Oft zusammen', 'Längste Serie guter Tage', 'Schlafqualität', 'Schlafdauer', 'Ziele']) {
      await expect(page.getByRole('heading', { name: title, exact: true }), title).toBeVisible();
    }
    await expect(page.getByRole('img', { name: /Stimmungsverlauf\. Durchschnitt/ })).toBeVisible();
    // Aktivität überlagern
    await page.getByRole('button', { name: 'Aktivität einblenden' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Sport' }).click();
    await expect(page.getByText(/Hinterlegte Tage: Sport/)).toBeVisible();
    await page.getByRole('radio', { name: 'Balken' }).click();
    // Zahlen stimmen mit dem Server überein
    const stats = await rest<{ count: number; avg: number }>(page, 'GET', '/stats?month=2026-11');
    await expect(page.locator('.mct-card', { hasText: 'Stimmungszähler' }).getByRole('img')).toHaveAttribute('aria-label', new RegExp('Stimmungszähler'));
    await expect(page.getByText(new RegExp(`${stats.data.count} Einträge`)).first()).toBeVisible();
    // Jahresansicht
    await page.getByRole('radio', { name: 'Jahr 2026' }).click();
    await expect(page.getByText(/Punkte (über|unter) dem Vorjahr|gleichauf/).or(page.getByText('Dein bester Wochentag')).first()).toBeVisible();
    // Leerzustand
    await page.getByRole('radio', { name: 'Monat' }).click();
    await page.getByRole('button', { name: /November 2026/ }).click();
    await page.getByRole('button', { name: 'Vorheriges Jahr' }).click();
    await page.getByRole('button', { name: /Januar/ }).click();
    await expect(page.getByRole('heading', { name: 'Zu wenig Daten' })).toBeVisible();
  });

  test('Kalender: Stimmung je Tag, Nachtragen, Filter mit Zähler, kombinierbar', async ({ page }) => {
    await open(page, '/kalender');
    await expect(page.getByRole('grid', { name: 'Kalender' })).toBeVisible();
    await expect(page.getByRole('columnheader').first()).toHaveText('Mo');
    await expect(page.getByRole('gridcell', { name: /kein Eintrag\. Nachtragen/ }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Nach Aktivität filtern' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Frei' }).click();
    const status = page.locator('.mct-count');
    await expect(status).toContainText(/\d+ von \d+ Einträgen/);
    const one = Number((await status.locator('strong').textContent()) || '0');
    await page.getByRole('button', { name: 'Aktivität hinzu' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Draußen' }).click();
    await expect(async () => { expect(Number((await status.locator('strong').textContent()) || '0')).toBeLessThanOrEqual(one); }).toPass();
    const server = await rest<{ matches: number }>(page, 'GET', `/calendar?month=2026-11&tags=${(await rest<{ setup: { tags: { id: number; name: string }[] } }>(page, 'GET', '/bootstrap')).data.setup.tags.filter((t) => ['Frei', 'Draußen'].includes(t.name)).map((t) => t.id).join(',')}`);
    await expect(status.locator('strong')).toHaveText(String(server.data.matches));
    // Tag öffnen
    await page.getByRole('button', { name: 'Draußen entfernen' }).click();
    await page.getByRole('button', { name: 'Frei entfernen' }).click();
    await page.getByRole('gridcell', { name: /14\. November: / }).click();
    await expect(page.getByRole('heading', { name: 'Samstag, 14. November 2026' })).toBeVisible();
    await expect(page.locator('.mct-entry')).not.toHaveCount(0);
    // Wochenanfang einstellbar
    await open(page, '/mehr/einstellungen');
    await page.getByRole('radio', { name: 'Sonntag' }).click();
    await open(page, '/kalender');
    await expect(page.getByRole('columnheader').first()).toHaveText('So');
    await open(page, '/mehr/einstellungen');
    await page.getByRole('radio', { name: 'Montag' }).click();
  });

  test('Suche nach Text, Stimmung und Aktivität', async ({ page }) => {
    await open(page);
    await page.getByRole('link', { name: 'Suche' }).click();
    await page.getByLabel('Text in Notiz oder Titel').fill('Beatmung');
    await expect(page.locator('.mct-count')).toContainText(/\d+ Treffer/);
    await expect(page.locator('.mct-entry').first()).toContainText('Beatmung');
    await page.getByLabel('Text in Notiz oder Titel').fill('');
    await page.getByRole('button', { name: 'Stimmung Stolz' }).click();
    await expect(page.locator('.mct-entry').first()).toContainText('Stolz');
    await page.getByRole('button', { name: 'Aktivität', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Sport' }).click();
    await expect(page.locator('.mct-count')).toContainText(/Treffer/);
  });

  test('Archivieren, Löschen mit Anzahl, Zusammenführen und Reihenfolge', async ({ page }) => {
    await open(page, '/mehr/aktivitaeten');
    // Löschen nennt die Zahl der betroffenen Einträge und bietet Archivieren an.
    await page.getByRole('button', { name: 'Kopfschmerzen', exact: true }).click();
    await page.getByRole('button', { name: 'Löschen' }).click();
    const ask = page.getByRole('dialog', { name: '„Kopfschmerzen“ löschen?' });
    await expect(ask).toContainText(/steckt in \d+ Einträgen/);
    await ask.getByRole('button', { name: 'Archivieren' }).click();
    await expect(page.getByRole('button', { name: /Archiv \(1\)/ })).toBeVisible();
    // Archiviertes fehlt beim Erfassen, bleibt aber in alten Einträgen sichtbar.
    await open(page, '/eintrag/neu?date=2026-11-12&fix=1');
    await page.getByRole('radio', { name: 'Mittel', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Familie' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Kopfschmerzen', exact: true })).toHaveCount(0);
    await open(page, '/suche');
    await page.getByRole('button', { name: 'Aktivität', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: /Kopfschmerzen/ }).click();
    await expect(page.locator('.mct-entry').first()).toContainText('Kopfschmerzen');

    // Zusammenführen
    await open(page, '/mehr/aktivitaeten');
    const counts = (await rest<{ tags: number }>(page, 'GET', '/counts')).data.tags;
    await page.getByRole('button', { name: 'Mittagsschlaf', exact: true }).click();
    await page.getByRole('button', { name: /zusammenführen/ }).click();
    await page.getByRole('dialog', { name: 'Womit zusammenführen?' }).getByRole('button', { name: 'Ausgeschlafen' }).click();
    await page.getByRole('dialog', { name: 'Aktivitäten zusammenführen?' }).getByRole('button', { name: 'Zusammenführen' }).click();
    await expect(page.getByText('Zusammengeführt.')).toBeVisible();
    expect((await rest<{ tags: number }>(page, 'GET', '/counts')).data.tags).toBe(counts - 1);

    // Reihenfolge von Gruppen und Skalen per Tastatur
    await open(page, '/mehr/reihenfolge');
    const rows = page.locator('.mct-sort li');
    const second = await rows.nth(1).innerText();
    await rows.nth(1).getByRole('button').focus();
    await page.keyboard.press('ArrowUp');
    await expect(rows.first()).toContainText(second.split('\n')[0]);
    await page.reload();
    await expect(page.locator('.mct-sort li').first()).toContainText(second.split('\n')[0]);
  });

  test('Stimmungen: mehrere pro Stufe, Löschen mit Ersatz', async ({ page }) => {
    await open(page, '/mehr/stimmungen');
    await page.getByRole('button', { name: 'Neue Stimmung auf Stufe 2' }).click();
    await page.getByLabel('Name').fill('Zufrieden');
    await page.getByRole('radio', { name: 'Symbol leaf' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('button', { name: 'Zufrieden', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Stolz', exact: true }).click();
    await page.getByRole('button', { name: 'Löschen' }).click();
    const before = (await rest<{ entries: number }>(page, 'GET', '/counts')).data.entries;
    const ask = page.getByRole('dialog', { name: '„Stolz“ löschen?' });
    await expect(ask).toContainText(/steckt in \d+ Einträgen/);
    await expect(ask).toContainText('Die Einträge bleiben erhalten');
    // Die Ersatz-Stimmung wählt und bestätigt der Nutzer selbst; vorgeschlagen wird eine derselben Stufe.
    const select = ask.getByLabel('Ersatz-Stimmung für diese Einträge');
    await expect(select.locator('option:checked')).toContainText('Sehr gut');
    await select.selectOption({ label: 'Gut (Stufe 2 · Gut)' });
    await expect(ask).toContainText('anderen Stufe');
    await select.selectOption({ label: 'Sehr gut (Stufe 1 · Sehr gut)' });
    await ask.getByRole('button', { name: /Löschen und ersetzen \(\d+\)/ }).click();
    await expect(page.getByRole('button', { name: 'Stolz', exact: true })).toHaveCount(0);
    expect((await rest<{ entries: number }>(page, 'GET', '/counts')).data.entries, 'kein Eintrag geht verloren').toBe(before);
  });

  test('Berichte, Rückblick, Fotos, Erfolge', async ({ page }) => {
    await open(page, '/mehr/berichte');
    await expect(page.getByRole('heading', { name: 'Überblick' })).toBeVisible();
    for (const k of ['Einträge', 'Aktivitäten', 'Stimmung', 'Wörter']) await expect(page.locator('.mct-kpis')).toContainText(k);
    await page.getByRole('radio', { name: 'Monat' }).click();
    await expect(page.locator('.mct-yearbar strong')).toContainText('2026');
    await open(page, '/mehr/rueckblick');
    await expect(page.getByRole('heading', { name: /Vor 1 Jahr · 2025/ })).toBeVisible();
    await expect(page.locator('.mct-entry').first()).toContainText('Schöner Abend am See.');
    await open(page, '/mehr/fotos');
    await expect(page.locator('.mct-gallery img').first()).toBeVisible();
    await page.locator('.mct-gallery button').first().click();
    await expect(page.getByRole('dialog', { name: /Foto 1 von/ })).toBeVisible();
    await page.getByRole('button', { name: 'Schließen' }).click();
    await open(page, '/mehr/erfolge');
    await expect(page.locator('.mct-count')).toContainText(/\d+ von \d+ erreicht/);
    await expect(page.locator('.mct-badges li.on').first()).toBeVisible();
  });

  test('Aussehen: Dunkel, Farbschema, Schrift; Erinnerungen; Vorlagen', async ({ page }) => {
    await open(page, '/mehr/aussehen');
    await page.getByRole('radio', { name: 'Dunkel' }).click();
    await expect(page.locator('.mct')).toHaveAttribute('data-mode', 'dark');
    await page.getByRole('radio', { name: /Ruhig/ }).click();
    await page.getByRole('radio', { name: /Eigenes Schema/ }).click();
    await expect(page.locator('.mct-customcolors input[type=color]')).toHaveCount(5);
    await page.getByRole('radio', { name: 'Serifen' }).click();
    await expect(page.locator('.mct')).toHaveAttribute('data-font', 'serif');
    await page.reload();
    await expect(page.locator('.mct')).toHaveAttribute('data-mode', 'dark');
    await page.getByRole('radio', { name: 'Wie das Gerät' }).click();
    await page.getByRole('radio', { name: /Klar/ }).click();
    await page.getByRole('radio', { name: 'Roboto' }).click();

    await open(page, '/mehr/erinnerungen');
    await page.getByRole('button', { name: 'Neue Erinnerung' }).click();
    await page.getByLabel('Uhrzeit').fill('06:45');
    await page.getByLabel('Eigener Text (optional)').fill('Guten Morgen');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByRole('button', { name: /06:45 Uhr.*Erinnerung bearbeiten/ })).toContainText('Guten Morgen');
    await page.getByRole('switch', { name: 'Erinnerung 06:45 Uhr aktiv' }).click();

    await open(page, '/mehr/vorlagen');
    await expect(page.locator('.mct-sort li')).toHaveCount(3);
    await page.getByRole('button', { name: 'Neue Vorlage' }).click();
    await page.getByLabel('Titel').fill('Nachtdienst-Notiz');
    await page.getByRole('textbox', { name: 'Notiz', exact: true }).fill('Wie war die Nacht?');
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.locator('.mct-sort li')).toHaveCount(4);
  });

  test('PIN-Sperre sperrt die Oberfläche auf dem Gerät', async ({ page }) => {
    await open(page, '/mehr/sicherheit');
    await page.getByLabel('Neue PIN (4 bis 8 Ziffern)').fill('2468');
    await page.getByLabel('PIN wiederholen').fill('2468');
    await page.getByRole('button', { name: 'PIN-Sperre einschalten' }).click();
    await expect(page.getByText('PIN-Sperre ist eingeschaltet.')).toBeVisible();
    await page.evaluate(() => sessionStorage.clear());
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Gesperrt' })).toBeVisible();
    await expect(page.locator('.mct-entry')).toHaveCount(0);
    await page.getByLabel('PIN eingeben').fill('1111');
    await page.getByRole('button', { name: 'Entsperren' }).click();
    await expect(page.getByRole('alert')).toContainText('Die PIN stimmt nicht.');
    await page.getByLabel('PIN eingeben').fill('2468');
    await page.getByRole('button', { name: 'Entsperren' }).click();
    await expect(page.getByRole('heading', { name: 'Sicherheit' })).toBeVisible();
    await page.getByRole('button', { name: 'PIN-Sperre ausschalten' }).click();
  });

  test('Einträge nach Mitternacht folgen der Einstellung', async ({ page }) => {
    await page.clock.install({ time: new Date('2026-11-17T01:30:00+01:00') });
    await open(page, '/mehr/einstellungen');
    await page.getByRole('radio', { name: 'Fragen' }).click();
    await open(page);
    await page.getByRole('button', { name: 'Neu anlegen' }).click();
    await page.getByRole('button', { name: 'Heute' }).click();
    const ask = page.getByRole('dialog', { name: 'Für welchen Tag?' });
    await expect(ask).toBeVisible();
    await ask.getByRole('button', { name: 'Gestern' }).click();
    await expect(page.locator('.mct-daylabel')).toContainText('15. November');
  });

  test('Export: CSV, PDF und Sicherung lassen sich herunterladen', async ({ page }) => {
    await open(page, '/mehr/daten');
    for (const [name, file] of [['Einträge als CSV', 'tagebuch.csv'], ['Einträge als PDF', 'tagebuch.pdf'], ['Sicherung ohne Fotos', /\.mctb$/], ['Fotos exportieren', 'tagebuch-fotos.zip']] as const) {
      const [dl] = await Promise.all([page.waitForEvent('download'), page.getByRole('link', { name: new RegExp(name) }).click()]);
      expect(dl.suggestedFilename()).toMatch(file);
      expect(await dl.failure()).toBeNull();
    }
  });

  test('Konto: Einwilligung widerrufen sperrt neue Einträge, Tagebuch leeren', async ({ page }) => {
    await open(page, '/mehr/konto');
    await page.getByRole('switch', { name: /Ich willige in die Verarbeitung/ }).click();
    await page.getByRole('dialog', { name: 'Einwilligung widerrufen?' }).getByRole('button', { name: 'Widerrufen' }).click();
    await expect(page.getByRole('switch', { name: /Ich willige in die Verarbeitung/ })).not.toBeChecked();
    expect(await restQuiet(page, 'POST', '/entries', { date: '2026-11-01', mood_id: 1 })).toBe(403);
    await page.getByRole('switch', { name: /Ich willige in die Verarbeitung/ }).click();
    await expect(page.getByRole('switch', { name: /Ich willige in die Verarbeitung/ })).toBeChecked();

    await page.getByRole('button', { name: 'Tagebuch leeren' }).click();
    const sheet = page.getByRole('dialog', { name: 'Tagebuch leeren' });
    await expect(sheet.getByRole('button', { name: 'Endgültig leeren' })).toBeDisabled();
    await sheet.getByLabel('Zur Bestätigung tippe LEEREN').fill('leeren');
    await sheet.getByRole('button', { name: 'Endgültig leeren' }).click();
    await expect(page.getByRole('heading', { name: 'Noch kein Eintrag in diesem Monat' })).toBeVisible();

    // Daten oder Konto löschen: nichts ist vorausgewählt, beides muss ausdrücklich angehakt werden.
    await open(page, '/mehr/konto');
    await page.getByRole('button', { name: 'Daten oder Konto löschen' }).click();
    const del = page.getByRole('dialog', { name: 'Daten oder Konto löschen' });
    const data = del.getByRole('checkbox', { name: /Alle Tagebuchdaten löschen/ });
    const account = del.getByRole('checkbox', { name: /Auch mein MediClever-Konto löschen/ });
    await expect(data).not.toBeChecked();
    await expect(account).not.toBeChecked();
    await del.getByLabel('Zur Bestätigung tippe LÖSCHEN').fill('löschen');
    await expect(del.getByRole('button', { name: 'Endgültig löschen' })).toBeDisabled();
    await account.check();
    await expect(data, 'das Konto geht nur zusammen mit den Daten').toBeChecked();
    await account.uncheck();
    await expect(del.getByRole('button', { name: 'Endgültig löschen' })).toBeEnabled();
    await del.getByRole('button', { name: 'Schließen' }).click();
    expect((await rest<{ entries: number; assets: number }>(page, 'GET', '/counts')).data).toMatchObject({ entries: 0, assets: 0, tags: 0 });
  });
});
