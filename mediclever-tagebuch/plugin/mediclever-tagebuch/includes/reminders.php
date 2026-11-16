<?php
namespace MCT;

/**
 * Erinnerungen. Web-Push, wo der Browser es erlaubt (RFC 8291/8292), sonst ein Hinweis beim Öffnen der App.
 * WP-Cron hängt von Seitenaufrufen ab: zuverlässige Benachrichtigungen kommen erst mit der nativen App.
 */
final class Reminders
{
    const HOOK = 'mct_reminders_tick';
    const SPKI_PREFIX = '3059301306072a8648ce3d020106082a8648ce3d030107034200';

    public static function init(): void
    {
        add_filter('cron_schedules', static function ($s) {
            $s['mct_5min'] = ['interval' => 300, 'display' => 'Alle 5 Minuten (MediClever Tagebuch)'];
            return $s;
        });
        add_action(self::HOOK, [self::class, 'tick']);
        if (!wp_next_scheduled(self::HOOK) && !wp_installing()) {
            wp_schedule_event(time() + 60, 'mct_5min', self::HOOK);
        }
    }

    public static function unschedule(): void
    {
        wp_clear_scheduled_hook(self::HOOK);
    }

    public static function b64(string $bin): string
    {
        return rtrim(strtr(base64_encode($bin), '+/', '-_'), '=');
    }

    public static function unb64(string $s): string
    {
        return (string) base64_decode(strtr($s, '-_', '+/') . str_repeat('=', (4 - strlen($s) % 4) % 4));
    }

    /** Schlüsselpaar des Servers (VAPID). Wird beim ersten Bedarf erzeugt. */
    private static function keys(): ?array
    {
        $k = get_option('mct_vapid');
        if (is_array($k) && !empty($k['private'])) {
            return $k;
        }
        if (!function_exists('openssl_pkey_new')) {
            return null;
        }
        $res = @openssl_pkey_new(['curve_name' => 'prime256v1', 'private_key_type' => OPENSSL_KEYTYPE_EC]);
        if (!$res) {
            return null;
        }
        $d = openssl_pkey_get_details($res);
        openssl_pkey_export($res, $pem);
        $k = ['private' => $pem, 'public' => self::b64("\x04" . str_pad($d['ec']['x'], 32, "\0", STR_PAD_LEFT) . str_pad($d['ec']['y'], 32, "\0", STR_PAD_LEFT))];
        update_option('mct_vapid', $k, false);
        return $k;
    }

    public static function public_key(): ?string
    {
        return Features::enabled('reminders') ? (self::keys()['public'] ?? null) : null;
    }

    public static function host_allowed(string $endpoint): bool
    {
        $p = wp_parse_url($endpoint);
        if (!$p || ($p['scheme'] ?? '') !== 'https') {
            return false;
        }
        $host = strtolower($p['host'] ?? '');
        foreach ((array) apply_filters('mct_push_hosts', ['fcm.googleapis.com', '.push.services.mozilla.com', '.notify.windows.com', '.push.apple.com']) as $h) {
            if ($host === ltrim($h, '.') || ($h[0] === '.' && str_ends_with($host, $h))) {
                return true;
            }
        }
        return false;
    }

    public static function subscribe(int $uid, array $sub): void
    {
        $endpoint = (string) ($sub['endpoint'] ?? '');
        $p256 = (string) ($sub['keys']['p256dh'] ?? '');
        $auth = (string) ($sub['keys']['auth'] ?? '');
        if (!self::host_allowed($endpoint) || strlen(self::unb64($p256)) !== 65 || strlen(self::unb64($auth)) !== 16) {
            throw new Fail('bad_sub', 'Dieser Browser liefert keine gültige Push-Anmeldung.');
        }
        Db::run('DELETE FROM ' . Db::t('push_subs') . ' WHERE endpoint_hash = %s', [hash('sha256', $endpoint)]);
        Db::insert('push_subs', $uid, ['endpoint' => $endpoint, 'endpoint_hash' => hash('sha256', $endpoint), 'p256dh' => $p256, 'auth' => $auth, 'created_at' => Db::now()]);
    }

    public static function unsubscribe(int $uid, string $endpoint): void
    {
        Db::delete('push_subs', $uid, ['endpoint_hash' => hash('sha256', $endpoint)]);
    }

    private static function der_to_raw(string $der): string
    {
        $pos = 2 + ((ord($der[1]) & 0x80) ? (ord($der[1]) & 0x7f) : 0);
        $out = '';
        for ($i = 0; $i < 2; $i++) {
            $len = ord($der[$pos + 1]);
            $out .= str_pad(ltrim(substr($der, $pos + 2, $len), "\0"), 32, "\0", STR_PAD_LEFT);
            $pos += 2 + $len;
        }
        return $out;
    }

    public static function vapid_header(string $endpoint): ?string
    {
        $k = self::keys();
        if (!$k) {
            return null;
        }
        $p = wp_parse_url($endpoint);
        $head = self::b64('{"typ":"JWT","alg":"ES256"}');
        $claims = self::b64(wp_json_encode(['aud' => $p['scheme'] . '://' . $p['host'], 'exp' => time() + 12 * 3600, 'sub' => 'mailto:' . get_option('admin_email')]));
        if (!openssl_sign("$head.$claims", $sig, $k['private'], OPENSSL_ALGO_SHA256)) {
            return null;
        }
        return 'vapid t=' . "$head.$claims." . self::b64(self::der_to_raw($sig)) . ', k=' . $k['public'];
    }

    /** Verschlüsselt eine Nachricht nach RFC 8291 (aes128gcm). */
    public static function encrypt(string $payload, string $ua_public, string $auth_secret): ?string
    {
        $local = openssl_pkey_new(['curve_name' => 'prime256v1', 'private_key_type' => OPENSSL_KEYTYPE_EC]);
        if (!$local) {
            return null;
        }
        $d = openssl_pkey_get_details($local);
        $as_public = "\x04" . str_pad($d['ec']['x'], 32, "\0", STR_PAD_LEFT) . str_pad($d['ec']['y'], 32, "\0", STR_PAD_LEFT);
        $pem = "-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode(hex2bin(self::SPKI_PREFIX) . $ua_public), 64) . "-----END PUBLIC KEY-----\n";
        $peer = openssl_pkey_get_public($pem);
        $secret = $peer ? openssl_pkey_derive($peer, $local, 32) : false;
        if ($secret === false) {
            return null;
        }
        $ikm = hash_hkdf('sha256', $secret, 32, "WebPush: info\0" . $ua_public . $as_public, $auth_secret);
        $salt = random_bytes(16);
        $cek = hash_hkdf('sha256', $ikm, 16, "Content-Encoding: aes128gcm\0", $salt);
        $nonce = hash_hkdf('sha256', $ikm, 12, "Content-Encoding: nonce\0", $salt);
        $cipher = openssl_encrypt($payload . "\x02", 'aes-128-gcm', $cek, OPENSSL_RAW_DATA, $nonce, $tag);
        return $salt . pack('N', 4096) . chr(65) . $as_public . $cipher . $tag;
    }

    public static function send(int $uid, string $title, string $text): int
    {
        $sent = 0;
        foreach (Db::rows('SELECT * FROM ' . Db::t('push_subs') . ' WHERE user_id = %d', [$uid]) as $s) {
            $vapid = self::vapid_header($s['endpoint']);
            $body = self::encrypt((string) wp_json_encode(['title' => $title, 'body' => $text]), self::unb64($s['p256dh']), self::unb64($s['auth']));
            if (!$vapid || !$body || !self::host_allowed($s['endpoint'])) {
                continue;
            }
            $res = wp_safe_remote_post($s['endpoint'], ['timeout' => 8, 'body' => $body, 'headers' => ['Authorization' => $vapid, 'TTL' => '3600',
                'Content-Encoding' => 'aes128gcm', 'Content-Type' => 'application/octet-stream', 'Urgency' => 'normal']]);
            $code = is_wp_error($res) ? 0 : (int) wp_remote_retrieve_response_code($res);
            if ($code === 404 || $code === 410) {
                Db::run('DELETE FROM ' . Db::t('push_subs') . ' WHERE id = %d', [(int) $s['id']]);
            } elseif ($code >= 200 && $code < 300) {
                $sent++;
            }
        }
        return $sent;
    }

    /** Fällige Erinnerungen eines Nutzers zwischen zwei Zeitpunkten (UTC-Sekunden), in seiner Zeitzone. */
    public static function due(int $uid, int $from, int $to): array
    {
        $tz = Settings::tz($uid);
        $a = (new \DateTimeImmutable('@' . $from))->setTimezone($tz);
        $b = (new \DateTimeImmutable('@' . $to))->setTimezone($tz);
        $today = $b->format('Y-m-d');
        $in = fn(string $time) => $a->format('Y-m-d') === $today ? ($time > $a->format('H:i') && $time <= $b->format('H:i')) : $time <= $b->format('H:i');
        $out = [];
        if (!Features::enabled('reminders', $uid)) {
            return $out;
        }
        foreach (Db::rows('SELECT time, text FROM ' . Db::t('reminders') . ' WHERE user_id = %d AND active = 1', [$uid]) as $r) {
            if ($in($r['time'])) {
                $out[] = ['title' => 'MediClever Tagebuch', 'text' => $r['text'] !== '' ? $r['text'] : 'Zeit für deinen Eintrag.'];
            }
        }
        foreach (Goals::overview($uid, null, false) as $g) {
            if ($g['reminder_time'] && $in($g['reminder_time']) && $g['due_today'] && !$g['done_today']) {
                $out[] = ['title' => 'Ziel: ' . $g['name'], 'text' => $g['reminder_text'] !== '' ? $g['reminder_text'] : 'Heute noch offen.'];
            }
        }
        if ($in('09:00')) {
            foreach (Milestones::all($uid) as $m) {
                if ($m['days_until'] === 0 && $m['remind_same_day']) {
                    $out[] = ['title' => 'Wichtiger Tag', 'text' => $m['name'] . ' ist heute.'];
                } elseif ($m['remind_days_before'] > 0 && $m['days_until'] === $m['remind_days_before']) {
                    $out[] = ['title' => 'Wichtiger Tag', 'text' => sprintf('%s ist in %d Tagen.', $m['name'], $m['days_until'])];
                }
            }
            $s = Settings::get($uid);
            if ($s['report_notify'] && Features::enabled('reports', $uid) && (int) $b->format('w') === (int) $s['week_start']) {
                $out[] = ['title' => 'Dein Wochenbericht', 'text' => 'Der Bericht zur letzten Woche ist fertig.'];
            }
        }
        return $out;
    }

    public static function tick(): void
    {
        $now = time();
        $last = (int) get_option('mct_last_tick', $now - 300);
        $last = max($last, $now - 3600);
        update_option('mct_last_tick', $now, false);
        foreach (array_map('intval', Db::col('SELECT DISTINCT user_id FROM ' . Db::t('push_subs'))) as $uid) {
            foreach (self::due($uid, $last, $now) as $n) {
                self::send($uid, $n['title'], $n['text']);
            }
        }
    }

    /** Hinweis beim Öffnen: Erinnerungen, deren Uhrzeit heute schon vorbei ist, solange heute noch kein Eintrag existiert. */
    public static function due_texts(int $uid): array
    {
        $today = Settings::today($uid);
        if ((int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d AND local_date = %s', [$uid, $today]) > 0) {
            return ['notices' => []];
        }
        $start = (new \DateTimeImmutable($today . ' 00:00', Settings::tz($uid)))->getTimestamp();
        return ['notices' => self::due($uid, $start, time())];
    }
}
