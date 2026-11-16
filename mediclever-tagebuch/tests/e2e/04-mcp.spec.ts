import { createHash, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { test, expect, login, open, seed } from './helpers';

const BASE = 'http://127.0.0.1:8888';
const CALLBACK = 'http://127.0.0.1:53999/callback';
const b64 = (b: Buffer) => b.toString('base64url');

interface Tokens { access_token: string; refresh_token: string; expires_in: number; scope: string }

test.describe('KI-Zugriff über MCP', () => {
  test.beforeAll(() => seed('anna'));

  test('Verbinden mit OAuth, Eintrag per MCP anlegen, in der App sehen, Widerruf sperrt sofort', async ({ page }) => {
    // 1. Der Endpunkt verlangt eine Anmeldung und verweist auf seine Metadaten.
    const unauth = await fetch(`${BASE}/wp-json/mc-tagebuch/v1/mcp`, { method: 'POST', body: '{}' });
    expect(unauth.status).toBe(401);
    const metaUrl = /resource_metadata="([^"]+)"/.exec(unauth.headers.get('www-authenticate') || '')![1];
    const resource = await (await fetch(metaUrl)).json();
    expect(resource.resource).toBe(`${BASE}/wp-json/mc-tagebuch/v1/mcp`);
    const as = await (await fetch(`${resource.authorization_servers[0]}/.well-known/oauth-authorization-server`)).json();
    expect(as.code_challenge_methods_supported).toEqual(['S256']);

    // 2. Dynamische Client-Registrierung.
    const reg = await (await fetch(as.registration_endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ client_name: 'Testassistent', redirect_uris: [CALLBACK], grant_types: ['authorization_code', 'refresh_token'], token_endpoint_auth_method: 'none' }) })).json();
    expect(reg.client_id).toMatch(/^mct_/);

    // 3. Freigabeseite: ohne Anmeldung geht es zur Anmeldung, danach wählt der Nutzer die Rechte.
    const verifier = b64(randomBytes(32));
    const url = new URL(as.authorization_endpoint);
    for (const [k, v] of Object.entries({ response_type: 'code', client_id: reg.client_id, redirect_uri: CALLBACK, code_challenge: b64(createHash('sha256').update(verifier).digest()),
      code_challenge_method: 'S256', state: 'xyz123', scope: 'lesen schreiben loeschen' })) url.searchParams.set(k, v);
    await page.goto(url.toString());
    await expect(page).toHaveURL(/wp-login\.php/);
    await page.fill('#user_login', 'anna');
    await page.fill('#user_pass', 'anna-passwort');
    await page.click('#wp-submit');
    await expect(page.getByRole('heading', { name: 'Zugriff für „Testassistent“ freigeben?' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: /Schreiben/ })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: /Löschen/ }), 'Löschen ist standardmäßig gesperrt').not.toBeChecked();
    await expect(page.getByText('gehen an den Anbieter des verbundenen Assistenten')).toBeVisible();
    await page.getByRole('checkbox', { name: /Ich willige ein/ }).check();
    // Lokaler Empfänger für die Rückruf-Adresse, wie ihn ein Kommandozeilen-Client öffnet.
    let callback = '';
    const server = createServer((req, res) => { callback = `http://127.0.0.1:53999${req.url}`; res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); res.end('<p>verbunden</p>'); });
    await new Promise<void>((r) => server.listen(53999, '127.0.0.1', r));
    await page.getByRole('button', { name: 'Zugriff freigeben' }).click();
    await expect(page.getByText('verbunden')).toBeVisible();
    server.close();
    const cb = new URL(callback);
    expect(cb.searchParams.get('state')).toBe('xyz123');

    // 4. Code gegen Token tauschen (PKCE).
    const tokenRes = await fetch(as.token_endpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'authorization_code', code: cb.searchParams.get('code')!, code_verifier: verifier, client_id: reg.client_id, redirect_uri: CALLBACK }) });
    expect(tokenRes.status).toBe(200);
    const tokens = await tokenRes.json() as Tokens;
    expect(tokens.scope).toBe('lesen schreiben');
    expect(tokens.expires_in).toBe(3600);

    // 5. Mit dem offiziellen MCP-SDK verbinden und Tools aufrufen.
    const transport = new StreamableHTTPClientTransport(new URL(resource.resource), { requestInit: { headers: { Authorization: `Bearer ${tokens.access_token}` } } });
    const client = new Client({ name: 'e2e', version: '1.0.0' });
    await client.connect(transport);
    expect(client.getServerVersion()?.name).toBe('mediclever-tagebuch');
    const tools = (await client.listTools()).tools;
    expect(tools.map((t) => t.name)).toEqual(['einrichtung_lesen', 'eintrag_anlegen', 'eintrag_aendern', 'eintrag_loeschen', 'tag_lesen', 'eintraege_suchen', 'auswertung_lesen',
      'ziel_abhaken', 'wichtige_tage_lesen', 'wichtigen_tag_anlegen']);
    for (const t of tools) {
      expect(t.title, t.name).toBeTruthy();
      expect(typeof t.annotations?.readOnlyHint, t.name).toBe('boolean');
      expect(typeof t.annotations?.destructiveHint, t.name).toBe('boolean');
    }
    const parse = (r: Awaited<ReturnType<typeof client.callTool>>) => JSON.parse((r.content as { text: string }[])[0].text);
    const setup = parse(await client.callTool({ name: 'einrichtung_lesen', arguments: {} }));
    expect(setup.stimmungen.length).toBeGreaterThanOrEqual(5);
    const created = await client.callTool({ name: 'eintrag_anlegen', arguments: { datum: '2026-11-16', uhrzeit: '21:40', stimmung: 'Gut', aktivitaeten: ['Sport', 'Freunde'],
      skalen: [{ name: 'Schlafdauer', wert: '7:45' }], titel: 'Per Assistent angelegt', notiz: 'Diktiert nach dem Training.', wiederholungsschluessel: 'e2e-1' } });
    expect(created.isError).toBeFalsy();
    expect(parse(created).neu).toBe(true);
    expect(parse(await client.callTool({ name: 'eintrag_anlegen', arguments: { stimmung: 'Gut', wiederholungsschluessel: 'e2e-1' } })).neu).toBe(false);
    const unknown = await client.callTool({ name: 'eintrag_anlegen', arguments: { stimmung: 'Gut', aktivitaeten: ['Unterwasserrugby'] } });
    expect(unknown.isError).toBe(true);
    const del = await client.callTool({ name: 'eintrag_loeschen', arguments: { id: parse(created).eintrag.id } });
    expect(del.isError, 'ohne Lösch-Freigabe bleibt Löschen gesperrt').toBe(true);

    // 6. Der Eintrag erscheint in der App.
    await open(page);
    const card = page.locator('.mct-entry', { hasText: 'Per Assistent angelegt' });
    await expect(card).toContainText('Gut · 21:40 Uhr');
    await expect(card).toContainText('Sport');
    await expect(card).toContainText('7:45 Std.');
    await expect(card).toContainText('Diktiert nach dem Training.');

    // 7. Einstellungen zeigen den verbundenen Client mit letzter Nutzung; Widerruf sperrt sofort.
    await open(page, '/mehr/ki');
    await expect(page.getByRole('switch', { name: 'Ich willige in den KI-Zugriff ein' })).toBeChecked();
    const grant = page.locator('.mct-grants li', { hasText: 'Testassistent' });
    await expect(grant).toContainText('Rechte: Lesen, Schreiben');
    await expect(grant).toContainText('zuletzt genutzt am');
    await grant.getByRole('button', { name: 'Widerrufen' }).click();
    await page.getByRole('dialog', { name: 'Freigabe widerrufen?' }).getByRole('button', { name: 'Widerrufen' }).click();
    await expect(grant).toContainText('widerrufen');
    const after = await fetch(resource.resource, { method: 'POST', headers: { Authorization: `Bearer ${tokens.access_token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }) });
    expect(after.status, 'nach dem Widerruf ist der Zugriff sofort gesperrt').toBe(401);
    const refresh = await fetch(as.token_endpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: tokens.refresh_token, client_id: reg.client_id }) });
    expect(refresh.status).toBe(400);
    await client.close().catch(() => undefined);
  });

  test('Freigabeseite lehnt fremde Rückruf-Adressen und fehlendes PKCE ab', async ({ page }) => {
    await login(page, 'anna');
    const res = await page.request.get(`${BASE}/?mct_oauth=authorize&response_type=code&client_id=unbekannt&redirect_uri=${encodeURIComponent('https://boese.example/cb')}`);
    expect(res.status()).toBe(400);
    expect(await res.text()).toContain('Freigabe nicht möglich');
  });
});
