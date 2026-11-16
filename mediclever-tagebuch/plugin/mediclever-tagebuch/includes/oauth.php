<?php
namespace MCT;

/**
 * OAuth 2.1 für den MCP-Endpunkt: Autorisierungscode mit PKCE (S256), dynamische Client-Registrierung,
 * kurzlebige Zugriffstoken mit Erneuerung. Token werden nur als Hash gespeichert.
 */
final class OAuth
{
    const SCOPES = ['lesen' => 'Einträge und Auswertungen lesen', 'schreiben' => 'Einträge anlegen und ändern, Ziele abhaken', 'loeschen' => 'Einträge löschen'];
    const ACCESS_TTL = 3600;
    const REFRESH_TTL = 60 * 86400;
    const CALLBACKS = ['https://claude.ai/api/mcp/auth_callback', 'https://claude.com/api/mcp/auth_callback'];

    public static function init(): void
    {
        add_action('init', [self::class, 'early_routes'], 1);
        add_action('rest_api_init', [self::class, 'routes']);
        add_filter('rest_allowed_cors_headers', static fn($h) => array_merge($h, ['Mcp-Protocol-Version', 'Mcp-Session-Id', 'Last-Event-ID']));
        add_filter('rest_exposed_cors_headers', static fn($h) => array_merge($h, ['WWW-Authenticate', 'Mcp-Session-Id']));
    }

    public static function issuer(): string
    {
        return untrailingslashit(home_url());
    }

    public static function resource(): string
    {
        return rest_url(MCT_REST_NS . '/mcp');
    }

    public static function resource_metadata_url(): string
    {
        return self::issuer() . '/.well-known/oauth-protected-resource';
    }

    private static function json_exit(array $data, int $status = 200): void
    {
        status_header($status);
        nocache_headers();
        header('Content-Type: application/json; charset=utf-8');
        header('Access-Control-Allow-Origin: *');
        echo wp_json_encode($data);
        exit;
    }

    /** Bekannte Adressen (.well-known) und die Freigabeseite. */
    public static function early_routes(): void
    {
        $path = (string) parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH);
        if (str_contains($path, '/.well-known/oauth-protected-resource')) {
            self::json_exit(['resource' => self::resource(), 'authorization_servers' => [self::issuer()], 'scopes_supported' => array_keys(self::SCOPES),
                'bearer_methods_supported' => ['header'], 'resource_name' => 'MediClever Tagebuch']);
        }
        if (str_contains($path, '/.well-known/oauth-authorization-server') || str_contains($path, '/.well-known/openid-configuration')) {
            self::json_exit(['issuer' => self::issuer(), 'authorization_endpoint' => add_query_arg('mct_oauth', 'authorize', home_url('/')),
                'token_endpoint' => rest_url(MCT_REST_NS . '/oauth/token'), 'registration_endpoint' => rest_url(MCT_REST_NS . '/oauth/register'),
                'revocation_endpoint' => rest_url(MCT_REST_NS . '/oauth/revoke'),
                'response_types_supported' => ['code'], 'grant_types_supported' => ['authorization_code', 'refresh_token'],
                'code_challenge_methods_supported' => ['S256'], 'token_endpoint_auth_methods_supported' => ['none'],
                'scopes_supported' => array_keys(self::SCOPES)]);
        }
        if (($_GET['mct_oauth'] ?? '') === 'authorize') {
            self::authorize();
        }
    }

    public static function routes(): void
    {
        foreach (['register' => 'register', 'token' => 'token', 'revoke' => 'revoke_endpoint'] as $path => $fn) {
            register_rest_route(MCT_REST_NS, '/oauth/' . $path, ['methods' => 'POST', 'permission_callback' => '__return_true', 'callback' => [self::class, $fn]]);
        }
    }

    private static function ip(): string
    {
        return (string) ($_SERVER['REMOTE_ADDR'] ?? '0');
    }

    /** Einfache Begrenzung über Transients. Liefert false, wenn das Limit überschritten ist. */
    public static function throttle(string $bucket, int $limit, int $window): bool
    {
        $key = 'mct_rl_' . md5($bucket . '|' . floor(time() / $window));
        $n = (int) get_transient($key);
        if ($n >= $limit) {
            return false;
        }
        set_transient($key, $n + 1, $window + 5);
        return true;
    }

    private static function error(string $code, string $desc, int $status = 400): \WP_REST_Response
    {
        $r = new \WP_REST_Response(['error' => $code, 'error_description' => $desc], $status);
        $r->header('Cache-Control', 'no-store');
        return $r;
    }

    public static function redirect_allowed(string $uri): bool
    {
        if (in_array($uri, (array) apply_filters('mct_oauth_allowed_redirects', self::CALLBACKS), true)) {
            return true;
        }
        $p = wp_parse_url($uri);
        // Lokale Rückruf-Adressen für Kommandozeilen-Clients (Claude Code, MCP Inspector) nach RFC 8252.
        return $p && ($p['scheme'] ?? '') === 'http' && in_array($p['host'] ?? '', ['localhost', '127.0.0.1', '[::1]'], true) && empty($p['user']);
    }

    public static function register(\WP_REST_Request $req): \WP_REST_Response
    {
        if (!Features::enabled('mcp')) {
            return self::error('access_denied', 'Der KI-Zugriff ist nicht verfügbar.', 403);
        }
        if (!self::throttle('reg|' . self::ip(), 20, 3600)) {
            return self::error('too_many_requests', 'Zu viele Registrierungen. Bitte später erneut versuchen.', 429);
        }
        $b = $req->get_json_params() ?: [];
        $uris = array_values(array_filter(array_map('strval', (array) ($b['redirect_uris'] ?? []))));
        if (!$uris) {
            return self::error('invalid_redirect_uri', 'Es fehlt eine Rückruf-Adresse.');
        }
        foreach ($uris as $u) {
            if (!self::redirect_allowed($u)) {
                return self::error('invalid_redirect_uri', 'Diese Rückruf-Adresse ist nicht zugelassen.');
            }
        }
        $client_id = 'mct_' . bin2hex(random_bytes(16));
        $name = In::text($b['client_name'] ?? 'KI-Assistent', 150) ?: 'KI-Assistent';
        global $wpdb;
        $wpdb->insert(Db::t('oauth_clients'), ['user_id' => 0, 'client_id' => $client_id, 'name' => $name, 'redirect_uris' => wp_json_encode($uris), 'created_at' => Db::now()]);
        $r = new \WP_REST_Response(['client_id' => $client_id, 'client_name' => $name, 'redirect_uris' => $uris, 'grant_types' => ['authorization_code', 'refresh_token'],
            'response_types' => ['code'], 'token_endpoint_auth_method' => 'none', 'client_id_issued_at' => time()], 201);
        $r->header('Cache-Control', 'no-store');
        return $r;
    }

    private static function client(string $client_id): ?array
    {
        global $wpdb;
        $c = $wpdb->get_row($wpdb->prepare('SELECT * FROM ' . Db::t('oauth_clients') . ' WHERE client_id = %s', $client_id), ARRAY_A);
        if ($c) {
            $c['redirect_uris'] = (array) json_decode((string) $c['redirect_uris'], true);
        }
        return $c ?: null;
    }

    private static function page(string $title, string $body): void
    {
        nocache_headers();
        header('Content-Type: text/html; charset=utf-8');
        header('X-Frame-Options: DENY');
        header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; form-action *");
        echo '<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>'
            . esc_html($title) . ' – MediClever Tagebuch</title><style>
            body{margin:0;font-family:Roboto,Arial,Helvetica,sans-serif;background:#F4F5F7;color:#2D323C;line-height:1.6}
            main{max-width:520px;margin:0 auto;padding:24px 16px 48px}
            .card{background:#fff;border:1px solid #DDE1E5;border-radius:18px;padding:24px}
            h1{font-size:1.5rem;line-height:1.2;color:#11202C;margin:0 0 12px}
            .kicker{font-size:.75rem;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:#B9303F;margin:0 0 8px}
            label{display:flex;gap:12px;align-items:flex-start;padding:12px 0;border-top:1px solid #DDE1E5;min-height:44px}
            input[type=checkbox]{width:22px;height:22px;margin-top:2px;accent-color:#B9303F;flex:none}
            .note{background:#FFF5E8;border:1px solid #F0BF76;border-radius:10px;padding:12px 14px;margin:16px 0}
            .row{display:flex;gap:12px;margin-top:20px;flex-wrap:wrap}
            button{min-height:48px;padding:0 22px;border-radius:999px;border:2px solid #B9303F;font:inherit;font-weight:700;cursor:pointer}
            .primary{background:#B9303F;color:#fff}.ghost{background:#fff;color:#B9303F}
            button:focus-visible,input:focus-visible{outline:3px solid #11202C;outline-offset:2px}
            small{color:#66717B}
            </style></head><body><main><div class="card">' . $body . '</div></main></body></html>'; // phpcs:ignore
        exit;
    }

    /** Freigabeseite: Der Nutzer wählt die Rechte und willigt in den KI-Zugriff ein. */
    public static function authorize(): void
    {
        $q = wp_unslash($_REQUEST);
        $client = self::client((string) ($q['client_id'] ?? ''));
        $redirect = (string) ($q['redirect_uri'] ?? '');
        if (!$client || !in_array($redirect, $client['redirect_uris'], true) || !self::redirect_allowed($redirect)) {
            status_header(400);
            self::page('Freigabe nicht möglich', '<h1>Freigabe nicht möglich</h1><p>Die Anfrage des Assistenten ist unvollständig oder die Rückruf-Adresse ist nicht zugelassen.</p>');
        }
        $state = (string) ($q['state'] ?? '');
        $back = static function (array $args) use ($redirect, $state) {
            if ($state !== '') {
                $args['state'] = $state;
            }
            wp_redirect(add_query_arg(array_map('rawurlencode', $args), $redirect)); // phpcs:ignore
            exit;
        };
        $challenge = (string) ($q['code_challenge'] ?? '');
        if (($q['response_type'] ?? '') !== 'code' || ($q['code_challenge_method'] ?? '') !== 'S256' || !preg_match('/^[A-Za-z0-9_-]{43,128}$/', $challenge)) {
            $back(['error' => 'invalid_request', 'error_description' => 'PKCE mit S256 ist erforderlich.']);
        }
        if (!is_user_logged_in()) {
            auth_redirect();
        }
        $uid = get_current_user_id();
        if (!Features::enabled('mcp', $uid)) {
            $back(['error' => 'access_denied']);
        }
        if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
            check_admin_referer('mct_oauth_' . $client['client_id']);
            if (($q['decision'] ?? '') !== 'allow') {
                $back(['error' => 'access_denied']);
            }
            if (empty($q['ai_consent'])) {
                self::page('Einwilligung fehlt', '<h1>Einwilligung fehlt</h1><p>Ohne deine Einwilligung kann kein Assistent auf dein Tagebuch zugreifen. Geh zurück und setze den Haken, oder schließe dieses Fenster.</p>');
            }
            $scopes = ['lesen'];
            foreach (['schreiben', 'loeschen'] as $s) {
                if (!empty($q['scope_' . $s])) {
                    $scopes[] = $s;
                }
            }
            if (empty(Settings::get($uid)['ai_consent_at'])) {
                Settings::set_raw($uid, 'ai_consent_at', Db::now());
                Audit::log($uid, 'ai_consent_given');
            }
            $grant = Db::insert('mcp_grants', $uid, ['client_id' => $client['client_id'], 'client_name' => $client['name'], 'scopes' => implode(' ', $scopes), 'granted_at' => Db::now()]);
            Audit::$source = 'app';
            Audit::log($uid, 'grant_create', $grant);
            $code = bin2hex(random_bytes(32));
            set_transient('mct_code_' . hash('sha256', $code), ['uid' => $uid, 'client_id' => $client['client_id'], 'redirect_uri' => $redirect,
                'challenge' => $challenge, 'grant_id' => $grant], 300);
            $back(['code' => $code]);
        }
        $fields = '';
        foreach (['response_type', 'client_id', 'redirect_uri', 'code_challenge', 'code_challenge_method', 'state', 'scope', 'resource'] as $k) {
            if (isset($q[$k])) {
                $fields .= '<input type="hidden" name="' . esc_attr($k) . '" value="' . esc_attr((string) $q[$k]) . '">';
            }
        }
        $host = (string) wp_parse_url($redirect, PHP_URL_HOST);
        $user = wp_get_current_user();
        self::page('KI-Zugriff freigeben', '<p class="kicker">MediClever Tagebuch</p><h1>Zugriff für „' . esc_html($client['name']) . '“ freigeben?</h1>'
            . '<p>Du bist angemeldet als <strong>' . esc_html($user->display_name) . '</strong>. Der Assistent meldet sich über <strong>' . esc_html($host) . '</strong> an und erhält nur Zugriff auf dein eigenes Tagebuch.</p>'
            . '<form method="post" action="' . esc_url(add_query_arg('mct_oauth', 'authorize', home_url('/'))) . '">' . $fields . wp_nonce_field('mct_oauth_' . $client['client_id'], '_wpnonce', true, false)
            . '<label><input type="checkbox" checked disabled><span><strong>Lesen</strong><br><small>Einträge, Einrichtung, Ziele, wichtige Tage und Auswertungen lesen. Fotos und Sprachnotizen bleiben außen vor.</small></span></label>'
            . '<label><input type="checkbox" name="scope_schreiben" value="1" checked><span><strong>Schreiben</strong><br><small>Einträge anlegen und ändern, Ziele abhaken, wichtige Tage anlegen.</small></span></label>'
            . '<label><input type="checkbox" name="scope_loeschen" value="1"><span><strong>Löschen</strong><br><small>Einträge löschen. Standardmäßig gesperrt.</small></span></label>'
            . '<div class="note"><label style="border:0;padding:0"><input type="checkbox" name="ai_consent" value="1" required><span><strong>Ich willige ein:</strong> Einträge, die der Assistent liest, gehen an den Anbieter des verbundenen Assistenten und werden dort nach dessen Regeln verarbeitet. Das können Gesundheitsdaten sein. Ich kann die Freigabe jederzeit in den Einstellungen widerrufen.</span></label></div>'
            . '<div class="row"><button class="primary" name="decision" value="allow">Zugriff freigeben</button><button class="ghost" name="decision" value="deny" formnovalidate>Ablehnen</button></div></form>');
    }

    private static function issue(int $uid, int $grant_id): array
    {
        $access = 'mcta_' . bin2hex(random_bytes(32));
        $refresh = 'mctr_' . bin2hex(random_bytes(32));
        Db::insert('oauth_tokens', $uid, ['grant_id' => $grant_id, 'access_hash' => hash('sha256', $access), 'refresh_hash' => hash('sha256', $refresh),
            'access_expires' => gmdate('Y-m-d H:i:s', time() + self::ACCESS_TTL), 'refresh_expires' => gmdate('Y-m-d H:i:s', time() + self::REFRESH_TTL)]);
        $scopes = (string) Db::val('SELECT scopes FROM ' . Db::t('mcp_grants') . ' WHERE id = %d AND user_id = %d', [$grant_id, $uid]);
        return ['access_token' => $access, 'token_type' => 'Bearer', 'expires_in' => self::ACCESS_TTL, 'refresh_token' => $refresh, 'scope' => $scopes];
    }

    public static function token(\WP_REST_Request $req): \WP_REST_Response
    {
        if (!self::throttle('tok|' . self::ip(), 120, 600)) {
            return self::error('too_many_requests', 'Zu viele Anfragen.', 429);
        }
        $p = array_merge((array) $req->get_body_params(), (array) ($req->get_json_params() ?: []));
        $type = (string) ($p['grant_type'] ?? '');
        if ($type === 'authorization_code') {
            $key = 'mct_code_' . hash('sha256', (string) ($p['code'] ?? ''));
            $c = get_transient($key);
            delete_transient($key);
            $verifier = (string) ($p['code_verifier'] ?? '');
            $ok = is_array($c) && hash_equals($c['client_id'], (string) ($p['client_id'] ?? '')) && hash_equals($c['redirect_uri'], (string) ($p['redirect_uri'] ?? ''))
                && preg_match('/^[A-Za-z0-9._~-]{43,128}$/', $verifier)
                && hash_equals($c['challenge'], rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '='));
            if (!$ok) {
                self::throttle('tokfail|' . self::ip(), 1000, 600);
                return self::error('invalid_grant', 'Der Autorisierungscode ist ungültig oder abgelaufen.');
            }
            if (Db::val('SELECT revoked_at FROM ' . Db::t('mcp_grants') . ' WHERE id = %d AND user_id = %d', [$c['grant_id'], $c['uid']]) !== null) {
                return self::error('invalid_grant', 'Die Freigabe wurde widerrufen.');
            }
            $out = self::issue((int) $c['uid'], (int) $c['grant_id']);
        } elseif ($type === 'refresh_token') {
            $hash = hash('sha256', (string) ($p['refresh_token'] ?? ''));
            $t = Db::row('SELECT t.*, g.revoked_at, g.client_id FROM ' . Db::t('oauth_tokens') . ' t JOIN ' . Db::t('mcp_grants') . ' g ON g.id = t.grant_id WHERE t.refresh_hash = %s', [$hash]);
            if (!$t || $t['revoked_at'] !== null || $t['refresh_expires'] < Db::now() || !hash_equals($t['client_id'], (string) ($p['client_id'] ?? $t['client_id']))) {
                return self::error('invalid_grant', 'Das Erneuerungstoken ist ungültig oder abgelaufen.');
            }
            Db::run('DELETE FROM ' . Db::t('oauth_tokens') . ' WHERE id = %d', [(int) $t['id']]);
            $out = self::issue((int) $t['user_id'], (int) $t['grant_id']);
        } else {
            return self::error('unsupported_grant_type', 'Unterstützt werden authorization_code und refresh_token.');
        }
        $r = new \WP_REST_Response($out, 200);
        $r->header('Cache-Control', 'no-store');
        $r->header('Pragma', 'no-cache');
        return $r;
    }

    public static function revoke_endpoint(\WP_REST_Request $req): \WP_REST_Response
    {
        $hash = hash('sha256', (string) $req['token']);
        Db::run('DELETE FROM ' . Db::t('oauth_tokens') . ' WHERE access_hash = %s OR refresh_hash = %s', [$hash, $hash]);
        return new \WP_REST_Response(null, 200);
    }

    public static function bearer(): string
    {
        $h = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
        if ($h === '' && function_exists('getallheaders')) {
            foreach ((array) getallheaders() as $k => $v) {
                if (strtolower($k) === 'authorization') {
                    $h = $v;
                }
            }
        }
        return preg_match('/^Bearer\s+(\S+)$/i', trim((string) $h), $m) ? $m[1] : '';
    }

    /** Prüft das Zugriffstoken. Liefert Nutzer, Freigabe und Rechte oder null. */
    public static function authenticate(): ?array
    {
        $token = self::bearer();
        if ($token === '') {
            return null;
        }
        if ((int) get_transient('mct_rl_' . md5('authfail|' . self::ip() . '|' . floor(time() / 600))) >= 30) {
            return null;
        }
        $t = Db::row('SELECT t.user_id, t.grant_id, g.scopes, g.client_name FROM ' . Db::t('oauth_tokens') . ' t JOIN ' . Db::t('mcp_grants')
            . ' g ON g.id = t.grant_id WHERE t.access_hash = %s AND t.access_expires > %s AND g.revoked_at IS NULL', [hash('sha256', $token), Db::now()]);
        if (!$t || empty(Settings::get((int) $t['user_id'])['ai_consent_at']) || !Features::enabled('mcp', (int) $t['user_id'])) {
            self::throttle('authfail|' . self::ip(), 30, 600);
            return null;
        }
        Db::run('UPDATE ' . Db::t('mcp_grants') . ' SET last_used_at = %s WHERE id = %d', [Db::now(), (int) $t['grant_id']]);
        return ['uid' => (int) $t['user_id'], 'grant_id' => (int) $t['grant_id'], 'scopes' => explode(' ', $t['scopes']), 'client' => $t['client_name']];
    }

    public static function grants(int $uid): array
    {
        return array_map(fn($g) => ['id' => (int) $g['id'], 'client' => $g['client_name'], 'scopes' => explode(' ', $g['scopes']), 'granted_at' => $g['granted_at'],
            'last_used_at' => $g['last_used_at'], 'revoked' => $g['revoked_at'] !== null],
            Db::rows('SELECT * FROM ' . Db::t('mcp_grants') . ' WHERE user_id = %d ORDER BY revoked_at IS NOT NULL, id DESC', [$uid]));
    }

    /** Widerruf sperrt den Zugriff sofort: Freigabe markieren, Token löschen. */
    public static function revoke(int $uid, int $grant_id): void
    {
        Db::own('mcp_grants', $uid, $grant_id);
        Db::update('mcp_grants', $uid, $grant_id, ['revoked_at' => Db::now()]);
        Db::delete('oauth_tokens', $uid, ['grant_id' => $grant_id]);
        Audit::log($uid, 'grant_revoke', $grant_id);
    }

    public static function revoke_all(int $uid): void
    {
        Db::run('UPDATE ' . Db::t('mcp_grants') . ' SET revoked_at = %s WHERE user_id = %d AND revoked_at IS NULL', [Db::now(), $uid]);
        Db::delete('oauth_tokens', $uid, []);
    }
}
