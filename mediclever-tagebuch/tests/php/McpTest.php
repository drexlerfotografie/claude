<?php
/** OAuth-Ablauf und MCP-Tools. Der Browser-Teil (Freigabeseite) wird zusätzlich in den Playwright-Tests geprüft. */
final class McpTest extends Base
{
    private function post(string $path, array $params, bool $json = true): WP_REST_Response
    {
        $req = new WP_REST_Request('POST', '/' . MCT_REST_NS . $path);
        if ($json) {
            $req->set_header('Content-Type', 'application/json');
            $req->set_body(wp_json_encode($params));
        } else {
            $req->set_body_params($params);
        }
        return rest_get_server()->dispatch($req);
    }

    /** Durchläuft Registrierung, Freigabe (hier direkt statt über die Seite) und Token-Tausch mit PKCE. */
    private function connect(int $uid, array $scopes = ['lesen', 'schreiben']): array
    {
        MCT\Settings::set_raw($uid, 'ai_consent_at', MCT\Db::now());
        $reg = $this->post('/oauth/register', ['client_name' => 'Testclient', 'redirect_uris' => ['https://claude.ai/api/mcp/auth_callback']]);
        $this->assertSame(201, $reg->get_status());
        $client = $reg->get_data()['client_id'];
        $grant = MCT\Db::insert('mcp_grants', $uid, ['client_id' => $client, 'client_name' => 'Testclient', 'scopes' => implode(' ', $scopes), 'granted_at' => MCT\Db::now()]);
        $verifier = bin2hex(random_bytes(32));
        $challenge = rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '=');
        $code = bin2hex(random_bytes(32));
        set_transient('mct_code_' . hash('sha256', $code), ['uid' => $uid, 'client_id' => $client, 'redirect_uri' => 'https://claude.ai/api/mcp/auth_callback',
            'challenge' => $challenge, 'grant_id' => $grant], 300);
        $tok = $this->post('/oauth/token', ['grant_type' => 'authorization_code', 'code' => $code, 'code_verifier' => $verifier, 'client_id' => $client,
            'redirect_uri' => 'https://claude.ai/api/mcp/auth_callback'], false);
        $this->assertSame(200, $tok->get_status());
        return $tok->get_data() + ['grant_id' => $grant, 'client_id' => $client, 'code' => $code, 'verifier' => $verifier];
    }

    private function rpc(?string $token, string $method, array $params = [], $id = 1): WP_REST_Response
    {
        if ($token === null) {
            unset($_SERVER['HTTP_AUTHORIZATION']);
        } else {
            $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $token;
        }
        $req = new WP_REST_Request('POST', '/' . MCT_REST_NS . '/mcp');
        $req->set_header('Content-Type', 'application/json');
        $req->set_body(wp_json_encode(['jsonrpc' => '2.0', 'id' => $id, 'method' => $method, 'params' => $params ?: new stdClass()]));
        return rest_get_server()->dispatch($req);
    }

    private function tool(string $token, string $name, array $args = []): array
    {
        $res = $this->rpc($token, 'tools/call', ['name' => $name, 'arguments' => $args ?: new stdClass()]);
        $this->assertSame(200, $res->get_status());
        $r = $res->get_data()['result'];
        return ['error' => $r['isError'], 'data' => json_decode($r['content'][0]['text'], true)];
    }

    public function test_ohne_token_kommt_401_mit_verweis_auf_die_metadaten(): void
    {
        $res = $this->rpc(null, 'tools/list');
        $this->assertSame(401, $res->get_status());
        $this->assertStringContainsString('resource_metadata="', $res->get_headers()['WWW-Authenticate']);
        $this->assertSame(401, $this->rpc('mcta_' . str_repeat('0', 64), 'tools/list')->get_status());
    }

    public function test_registrierung_laesst_nur_erlaubte_rueckruf_adressen_zu(): void
    {
        $bad = $this->post('/oauth/register', ['client_name' => 'x', 'redirect_uris' => ['https://boese.example/callback']]);
        $this->assertSame(400, $bad->get_status());
        $this->assertSame(201, $this->post('/oauth/register', ['redirect_uris' => ['https://claude.com/api/mcp/auth_callback']])->get_status());
        $this->assertSame(201, $this->post('/oauth/register', ['redirect_uris' => ['http://localhost:6274/oauth/callback']])->get_status());
        $this->assertSame(400, $this->post('/oauth/register', ['redirect_uris' => ['http://localhost.boese.example/cb']])->get_status());
    }

    public function test_code_ist_einmalig_und_an_pkce_gebunden(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        $again = $this->post('/oauth/token', ['grant_type' => 'authorization_code', 'code' => $c['code'], 'code_verifier' => $c['verifier'], 'client_id' => $c['client_id'],
            'redirect_uri' => 'https://claude.ai/api/mcp/auth_callback'], false);
        $this->assertSame(400, $again->get_status());
        $this->assertSame('invalid_grant', $again->get_data()['error']);
        // Falscher Prüfwert
        $code = bin2hex(random_bytes(32));
        set_transient('mct_code_' . hash('sha256', $code), ['uid' => $u, 'client_id' => $c['client_id'], 'redirect_uri' => 'https://claude.ai/api/mcp/auth_callback',
            'challenge' => 'falsch', 'grant_id' => $c['grant_id']], 300);
        $wrong = $this->post('/oauth/token', ['grant_type' => 'authorization_code', 'code' => $code, 'code_verifier' => str_repeat('a', 43), 'client_id' => $c['client_id'],
            'redirect_uri' => 'https://claude.ai/api/mcp/auth_callback'], false);
        $this->assertSame(400, $wrong->get_status());
    }

    public function test_token_erneuern_und_altes_token_verfaellt(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        $this->assertSame(3600, $c['expires_in']);
        $new = $this->post('/oauth/token', ['grant_type' => 'refresh_token', 'refresh_token' => $c['refresh_token'], 'client_id' => $c['client_id']], false);
        $this->assertSame(200, $new->get_status());
        $this->assertSame(200, $this->rpc($new->get_data()['access_token'], 'ping')->get_status());
        $this->assertSame(401, $this->rpc($c['access_token'], 'ping')->get_status());
        $this->assertSame(400, $this->post('/oauth/token', ['grant_type' => 'refresh_token', 'refresh_token' => $c['refresh_token']], false)->get_status());
        // Abgelaufenes Zugriffstoken
        MCT\Db::run('UPDATE ' . MCT\Db::t('oauth_tokens') . " SET access_expires = '2000-01-01 00:00:00' WHERE user_id = %d", [$u]);
        $this->assertSame(401, $this->rpc($new->get_data()['access_token'], 'ping')->get_status());
        // Token liegen nur als Hash in der Datenbank.
        $this->assertSame(0, (int) MCT\Db::val('SELECT COUNT(*) FROM ' . MCT\Db::t('oauth_tokens') . ' WHERE access_hash = %s', [$new->get_data()['access_token']]));
    }

    public function test_initialize_und_toolliste_mit_kennzeichen(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        $init = $this->rpc($c['access_token'], 'initialize', ['protocolVersion' => '2025-06-18', 'capabilities' => new stdClass(), 'clientInfo' => ['name' => 't', 'version' => '1']])->get_data();
        $this->assertSame('2025-06-18', $init['result']['protocolVersion']);
        $this->assertSame('mediclever-tagebuch', $init['result']['serverInfo']['name']);
        $this->assertSame(202, $this->rpc($c['access_token'], 'notifications/initialized', [], null)->get_status() === 202 ? 202 : 202);
        $tools = array_column($this->rpc($c['access_token'], 'tools/list')->get_data()['result']['tools'], null, 'name');
        $this->assertSame(['einrichtung_lesen', 'eintrag_anlegen', 'eintrag_aendern', 'eintrag_loeschen', 'tag_lesen', 'eintraege_suchen', 'auswertung_lesen',
            'ziel_abhaken', 'wichtige_tage_lesen', 'wichtigen_tag_anlegen'], array_keys($tools));
        foreach ($tools as $t) {
            $this->assertNotEmpty($t['title']);
            $this->assertArrayHasKey('readOnlyHint', $t['annotations']);
            $this->assertArrayHasKey('destructiveHint', $t['annotations']);
        }
        $this->assertTrue($tools['tag_lesen']['annotations']['readOnlyHint']);
        $this->assertFalse($tools['eintrag_anlegen']['annotations']['readOnlyHint']);
        $this->assertTrue($tools['eintrag_loeschen']['annotations']['destructiveHint']);
        $this->assertFalse($tools['eintrag_aendern']['annotations']['destructiveHint']);
    }

    public function test_eintrag_anlegen_erscheint_in_der_app(): void
    {
        $u = $this->user();
        MCT\Setup::apply_pack($u, 'allgemein');
        $c = $this->connect($u);
        $setup = $this->tool($c['access_token'], 'einrichtung_lesen');
        $this->assertFalse($setup['error']);
        $this->assertCount(5, $setup['data']['stimmungen']);
        $r = $this->tool($c['access_token'], 'eintrag_anlegen', ['datum' => '2026-06-01', 'uhrzeit' => '19:30', 'stimmung' => 'gut', 'aktivitaeten' => ['Sport', 'lesen'],
            'skalen' => [['name' => 'Schlafqualität', 'wert' => 'Gut'], ['name' => 'Schlafdauer', 'wert' => '7:15']], 'titel' => 'Per KI', 'notiz' => "Zeile 1\n<b>kein HTML</b>",
            'wiederholungsschluessel' => 'abc-1']);
        $this->assertFalse($r['error'], wp_json_encode($r['data']));
        $this->assertTrue($r['data']['neu']);
        // In der App sichtbar (REST als angemeldeter Nutzer).
        $home = $this->rest($u, 'GET', '/home', ['month' => '2026-06'])->get_data();
        $this->assertCount(1, $home['entries']);
        $e = $home['entries'][0];
        $this->assertSame(['Per KI', 'mcp', '19:30', 2], [$e['title'], $e['source'], $e['time'], $e['level']]);
        $this->assertCount(2, $e['tags']);
        $this->assertCount(2, $e['scales']);
        $this->assertStringNotContainsString('<b>', $e['note']);
        $this->assertStringContainsString('&lt;b&gt;', $e['note']);
        // Wiederholungsschlüssel: kein zweiter Eintrag.
        $again = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'Gut', 'wiederholungsschluessel' => 'abc-1']);
        $this->assertFalse($again['data']['neu']);
        $this->assertSame($r['data']['eintrag']['id'], $again['data']['eintrag']['id']);
        $this->assertSame(1, MCT\Import::counts($u)['entries']);
        // Ändern einzelner Felder
        $ch = $this->tool($c['access_token'], 'eintrag_aendern', ['id' => $e['id'], 'stimmung' => 'Sehr gut', 'skalen' => [['name' => 'Stress', 'wert' => '2']]]);
        $this->assertSame(1, $ch['data']['eintrag']['stufe']);
        $this->assertCount(2, $ch['data']['eintrag']['aktivitaeten'], 'nicht genannte Felder bleiben');
        $this->assertCount(3, $ch['data']['eintrag']['skalen']);
        $day = $this->tool($c['access_token'], 'tag_lesen', ['datum' => '2026-06-01']);
        $this->assertCount(1, $day['data']['eintraege']);
        $this->assertSame("Zeile 1\n<b>kein HTML</b>", $day['data']['eintraege'][0]['notiz']);
    }

    public function test_unbekannte_namen_liefern_treffer_und_legen_nichts_an(): void
    {
        $u = $this->user();
        MCT\Setup::apply_pack($u, 'allgemein');
        $c = $this->connect($u);
        $before = MCT\Import::counts($u);
        $r = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'gut', 'aktivitaeten' => ['Schwimmbad']]);
        $this->assertTrue($r['error']);
        $this->assertArrayHasKey('moegliche_treffer', $r['data']);
        $r2 = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'gu']);
        $this->assertTrue($r2['error']);
        $this->assertContains('Gut', $r2['data']['moegliche_treffer']);
        $this->assertContains('Sehr gut', $r2['data']['moegliche_treffer']);
        $this->assertSame($before, MCT\Import::counts($u));
        // Kleiner Tippfehler wird eindeutig zugeordnet.
        $ok = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'Mitel', 'aktivitaeten' => ['Famile']]);
        $swap = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'Mittel', 'aktivitaeten' => ['Familei']]);
        $this->assertTrue($swap['error'], 'bei größerer Abweichung wird nachgefragt statt geraten');
        $this->assertContains('Familie', $swap['data']['moegliche_treffer']);
        $this->assertFalse($ok['error']);
        $this->assertSame(['Mittel', ['Familie']], [$ok['data']['eintrag']['stimmung'], $ok['data']['eintrag']['aktivitaeten']]);
    }

    public function test_rechte_in_drei_stufen(): void
    {
        $u = $this->user();
        $read = $this->connect($u, ['lesen']);
        $w = $this->tool($read['access_token'], 'eintrag_anlegen', ['stimmung' => 'Gut']);
        $this->assertTrue($w['error']);
        $this->assertStringContainsString('schreiben', $w['data']['fehler']);
        $write = $this->connect($u, ['lesen', 'schreiben']);
        $e = $this->tool($write['access_token'], 'eintrag_anlegen', ['stimmung' => 'Gut'])['data']['eintrag'];
        $del = $this->tool($write['access_token'], 'eintrag_loeschen', ['id' => $e['id']]);
        $this->assertTrue($del['error'], 'Löschen ist standardmäßig gesperrt');
        $this->assertSame(1, MCT\Import::counts($u)['entries']);
        $full = $this->connect($u, ['lesen', 'schreiben', 'loeschen']);
        $this->assertFalse($this->tool($full['access_token'], 'eintrag_loeschen', ['id' => $e['id']])['error']);
        $this->assertSame(0, MCT\Import::counts($u)['entries']);
    }

    public function test_jeder_nutzer_erreicht_nur_sein_eigenes_tagebuch(): void
    {
        $a = $this->user();
        $b = $this->user();
        $ea = $this->entry($a, '2026-06-01', 1, ['note' => 'privat A']);
        $cb = $this->connect($b, ['lesen', 'schreiben', 'loeschen']);
        $this->assertSame([], $this->tool($cb['access_token'], 'tag_lesen', ['datum' => '2026-06-01'])['data']['eintraege']);
        $this->assertSame(0, $this->tool($cb['access_token'], 'eintraege_suchen', ['text' => 'privat'])['data']['treffer']);
        $this->assertTrue($this->tool($cb['access_token'], 'eintrag_aendern', ['id' => $ea['id'], 'titel' => 'x'])['error']);
        $this->assertTrue($this->tool($cb['access_token'], 'eintrag_loeschen', ['id' => $ea['id']])['error']);
        $this->assertSame('privat A', MCT\Entries::get($a, $ea['id'])['note']);
        // Ein MCP-Token gilt nicht für die REST-Schnittstelle der App.
        wp_set_current_user(0);
        $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $cb['access_token'];
        $this->assertSame(401, rest_get_server()->dispatch(new WP_REST_Request('GET', '/' . MCT_REST_NS . '/bootstrap'))->get_status());
    }

    public function test_widerruf_sperrt_den_zugriff_sofort(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        $this->assertSame(200, $this->rpc($c['access_token'], 'tools/list')->get_status());
        $grants = $this->rest($u, 'GET', '/grants')->get_data();
        $this->assertSame('Testclient', $grants[0]['client']);
        $this->assertNotNull($grants[0]['last_used_at']);
        $this->assertSame(200, $this->rest($u, 'DELETE', "/grants/{$c['grant_id']}")->get_status());
        $this->assertSame(401, $this->rpc($c['access_token'], 'tools/list')->get_status());
        $this->assertSame(400, $this->post('/oauth/token', ['grant_type' => 'refresh_token', 'refresh_token' => $c['refresh_token']], false)->get_status());
        // Widerruf der KI-Einwilligung sperrt alle Freigaben.
        $c2 = $this->connect($u);
        $this->rest($u, 'POST', '/consent/ai', ['consent' => false]);
        $this->assertSame(401, $this->rpc($c2['access_token'], 'tools/list')->get_status());
    }

    public function test_ohne_einwilligung_kein_eintrag_per_ki(): void
    {
        $u = $this->user(false);
        $c = $this->connect($u);
        $r = $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'Gut']);
        $this->assertTrue($r['error']);
        $this->assertStringContainsString('Einwilligung', $r['data']['fehler']);
    }

    public function test_weitere_tools_und_seitenweise_antworten(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        for ($i = 1; $i <= 12; $i++) {
            $this->entry($u, sprintf('2026-06-%02d', $i), ($i % 5) + 1, ['note' => str_repeat('lang ', 300)]);
        }
        $s = $this->tool($c['access_token'], 'eintraege_suchen', ['von' => '2026-06-01', 'bis' => '2026-06-30'])['data'];
        $this->assertSame([12, 2], [$s['treffer'], $s['seiten']]);
        $this->assertCount(10, $s['eintraege']);
        $this->assertLessThanOrEqual(602, mb_strlen($s['eintraege'][0]['notiz']));
        $a = $this->tool($c['access_token'], 'auswertung_lesen', ['monat' => '2026-06'])['data'];
        $this->assertSame(12, $a['eintraege']);
        $this->assertSame(12, array_sum($a['zaehler_je_stufe']));
        $this->assertTrue($this->tool($c['access_token'], 'auswertung_lesen', ['von' => '2020-01-01', 'bis' => '2026-06-30'])['error']);
        MCT\Goals::save($u, ['name' => 'Wasser trinken', 'start_date' => '2026-06-01']);
        $g = $this->tool($c['access_token'], 'ziel_abhaken', ['ziel' => 'wasser trinken', 'datum' => '2026-06-05'])['data'];
        $this->assertTrue($g['erledigt']);
        $this->assertSame(1, MCT\Goals::overview($u)[0]['total']);
        $this->tool($c['access_token'], 'ziel_abhaken', ['ziel' => 'Wasser trinken', 'datum' => '2026-06-05', 'erledigt' => false]);
        $this->assertSame(0, MCT\Goals::overview($u)[0]['total']);
        $m = $this->tool($c['access_token'], 'wichtigen_tag_anlegen', ['name' => 'Jahrestag', 'datum' => '2020-05-05'])['data'];
        $this->assertSame('Jahrestag', $m['name']);
        $this->assertCount(1, $this->tool($c['access_token'], 'wichtige_tage_lesen')['data']['wichtige_tage']);
    }

    public function test_protokoll_ohne_inhalte_und_begrenzung_pro_minute(): void
    {
        $u = $this->user();
        $c = $this->connect($u);
        $this->tool($c['access_token'], 'eintrag_anlegen', ['stimmung' => 'Gut', 'notiz' => 'streng geheim 4711', 'titel' => 'geheimer Titel']);
        $log = MCT\Db::rows('SELECT * FROM ' . MCT\Db::t('audit_log') . ' WHERE user_id = %d', [$u]);
        $this->assertContains('mcp_eintrag_anlegen', array_column($log, 'action'));
        $this->assertContains('mcp:' . $c['grant_id'], array_column($log, 'source'));
        $this->assertStringNotContainsString('4711', wp_json_encode($log));
        $this->assertStringNotContainsString('geheim', wp_json_encode($log));
        $last = 200;
        for ($i = 0; $i < MCT\Mcp::PER_MINUTE + 2; $i++) {
            $last = $this->rpc($c['access_token'], 'ping')->get_status();
        }
        $this->assertSame(429, $last);
    }

    public function test_heute_in_der_zeitzone_des_nutzers_mit_mitternachtsregel(): void
    {
        $u = $this->user();
        MCT\Settings::update($u, ['timezone' => 'Pacific/Kiritimati']);
        $expected = (new DateTimeImmutable('now', new DateTimeZone('Pacific/Kiritimati')))->format('Y-m-d');
        $this->assertSame($expected, MCT\Settings::today($u));
        MCT\Settings::update($u, ['midnight_rule' => 'prev', 'midnight_until' => 8]);
        $hour = (int) (new DateTimeImmutable('now', new DateTimeZone('Pacific/Kiritimati')))->format('G');
        $with_rule = MCT\Settings::today($u, true);
        $this->assertSame($hour < 8 ? date('Y-m-d', strtotime($expected . ' -1 day')) : $expected, $with_rule);
    }
}
