<?php
namespace MCT;

/** Fehler mit HTTP-Status, den REST und MCP einheitlich ausgeben. */
class Fail extends \Exception
{
    public string $slug;
    public int $status;
    public array $data;

    public function __construct(string $slug, string $message, int $status = 400, array $data = [])
    {
        parent::__construct($message);
        $this->slug = $slug;
        $this->status = $status;
        $this->data = $data;
    }
}

/** Dünne Schicht über $wpdb. Alle Hilfen verlangen die Nutzer-ID. */
final class Db
{
    public static function t(string $name): string
    {
        global $wpdb;
        return $wpdb->prefix . 'mct_' . $name;
    }

    public static function rows(string $sql, array $args = []): array
    {
        global $wpdb;
        $q = $args ? $wpdb->prepare($sql, $args) : $sql; // phpcs:ignore
        return $wpdb->get_results($q, ARRAY_A) ?: []; // phpcs:ignore
    }

    public static function row(string $sql, array $args = []): ?array
    {
        $r = self::rows($sql, $args);
        return $r[0] ?? null;
    }

    public static function val(string $sql, array $args = [])
    {
        global $wpdb;
        $q = $args ? $wpdb->prepare($sql, $args) : $sql; // phpcs:ignore
        return $wpdb->get_var($q); // phpcs:ignore
    }

    public static function col(string $sql, array $args = []): array
    {
        global $wpdb;
        $q = $args ? $wpdb->prepare($sql, $args) : $sql; // phpcs:ignore
        return $wpdb->get_col($q) ?: []; // phpcs:ignore
    }

    public static function run(string $sql, array $args = []): int
    {
        global $wpdb;
        $q = $args ? $wpdb->prepare($sql, $args) : $sql; // phpcs:ignore
        $r = $wpdb->query($q); // phpcs:ignore
        if ($r === false) {
            throw new Fail('db', 'Die Datenbank hat die Änderung abgelehnt.', 500);
        }
        return (int) $r;
    }

    public static function insert(string $table, int $uid, array $data): int
    {
        global $wpdb;
        $data['user_id'] = $uid;
        if ($wpdb->insert(self::t($table), $data) === false) {
            throw new Fail('db', 'Speichern fehlgeschlagen.', 500);
        }
        return (int) $wpdb->insert_id;
    }

    public static function update(string $table, int $uid, int $id, array $data): void
    {
        global $wpdb;
        if (!$data) {
            return;
        }
        if ($wpdb->update(self::t($table), $data, ['id' => $id, 'user_id' => $uid]) === false) {
            throw new Fail('db', 'Speichern fehlgeschlagen.', 500);
        }
    }

    public static function delete(string $table, int $uid, array $where): int
    {
        global $wpdb;
        $where['user_id'] = $uid;
        return (int) $wpdb->delete(self::t($table), $where);
    }

    /** Liest einen Datensatz des Nutzers oder wirft 404. Fremde IDs sehen aus wie nicht vorhanden. */
    public static function own(string $table, int $uid, int $id): array
    {
        $r = self::row('SELECT * FROM ' . self::t($table) . ' WHERE id = %d AND user_id = %d', [$id, $uid]);
        if (!$r) {
            throw new Fail('not_found', 'Nicht gefunden.', 404);
        }
        return $r;
    }

    public static function in(array $ids): string
    {
        $ids = array_values(array_unique(array_map('intval', $ids)));
        return $ids ? implode(',', $ids) : '0';
    }

    public static function now(): string
    {
        return gmdate('Y-m-d H:i:s');
    }
}

/** Funktionsschalter: Jede Funktion lässt sich später einzeln sperren (kostenlos/Premium). */
final class Features
{
    const KEYS = ['stats', 'calendar', 'goals', 'milestones', 'reports', 'lookback', 'photos', 'voice', 'scales',
        'templates', 'import', 'export', 'backup', 'mcp', 'reminders', 'achievements', 'pin', 'appearance', 'search', 'favorites'];

    public static function enabled(string $key, int $uid = 0): bool
    {
        $opt = (array) get_option('mct_features', []);
        $on = array_key_exists($key, $opt) ? (bool) $opt[$key] : true;
        return (bool) apply_filters('mct_feature_enabled', $on, $key, $uid);
    }

    public static function all(int $uid): array
    {
        $out = [];
        foreach (self::KEYS as $k) {
            $out[$k] = self::enabled($k, $uid);
        }
        return $out;
    }

    public static function need(string $key, int $uid): void
    {
        if (!self::enabled($key, $uid)) {
            throw new Fail('feature_off', 'Diese Funktion ist für dein Konto nicht freigeschaltet.', 403, ['feature' => $key]);
        }
    }
}

/** Protokoll ohne Inhalte: wer, wann, welche Aktion, welche ID. */
final class Audit
{
    public static string $source = 'app';

    public static function log(int $uid, string $action, ?int $object_id = null): void
    {
        global $wpdb;
        $wpdb->insert(Db::t('audit_log'), ['user_id' => $uid, 'ts' => Db::now(), 'source' => substr(self::$source, 0, 40),
            'action' => substr($action, 0, 60), 'object_id' => $object_id]);
    }
}

/** Einstellungen pro Nutzer als Nutzer-Metadaten. */
final class Settings
{
    const KEY = 'mct_settings';

    const DEFAULTS = [
        'week_start' => 1,            // 1 Montag, 0 Sonntag, 6 Samstag
        'midnight_rule' => 'ask',     // ask | prev | current
        'midnight_until' => 4,        // Uhrzeit, bis zu der die Regel greift
        'quotes' => true,
        'lookback' => true,
        'report_notify' => true,
        'color_scheme' => 'klar',
        'custom_colors' => [],
        'font' => 'roboto',
        'color_mode' => 'system',     // system | light | dark
        'timezone' => 'Europe/Berlin',
        'consent_at' => null,
        'ai_consent_at' => null,
        'adult' => false,
        'onboarded' => false,
        'mcp_allow_delete' => false,
    ];

    public static function get(int $uid): array
    {
        $s = get_user_meta($uid, self::KEY, true);
        return array_merge(self::DEFAULTS, is_array($s) ? $s : []);
    }

    public static function update(int $uid, array $in): array
    {
        $s = self::get($uid);
        foreach ($in as $k => $v) {
            switch ($k) {
                case 'week_start':
                    $s[$k] = in_array((int) $v, [0, 1, 6], true) ? (int) $v : 1;
                    break;
                case 'midnight_rule':
                    $s[$k] = in_array($v, ['ask', 'prev', 'current'], true) ? $v : 'ask';
                    break;
                case 'midnight_until':
                    $s[$k] = max(0, min(8, (int) $v));
                    break;
                case 'quotes': case 'lookback': case 'report_notify': case 'onboarded': case 'mcp_allow_delete':
                    $s[$k] = (bool) $v;
                    break;
                case 'color_scheme': case 'font':
                    $s[$k] = sanitize_key((string) $v);
                    break;
                case 'color_mode':
                    $s[$k] = in_array($v, ['system', 'light', 'dark'], true) ? $v : 'system';
                    break;
                case 'custom_colors':
                    $s[$k] = array_slice(array_values(array_filter(array_map('sanitize_hex_color', (array) $v))), 0, 5);
                    break;
                case 'timezone':
                    $s[$k] = in_array($v, timezone_identifiers_list(), true) ? $v : $s[$k];
                    break;
            }
        }
        update_user_meta($uid, self::KEY, $s);
        return $s;
    }

    public static function set_raw(int $uid, string $key, $value): void
    {
        $s = self::get($uid);
        $s[$key] = $value;
        update_user_meta($uid, self::KEY, $s);
    }

    public static function tz(int $uid): \DateTimeZone
    {
        try {
            return new \DateTimeZone(self::get($uid)['timezone']);
        } catch (\Exception $e) {
            return new \DateTimeZone('Europe/Berlin');
        }
    }

    /** „Heute“ in der Zeitzone des Nutzers, mit Mitternachtsregel (Vortag bis zur eingestellten Uhrzeit). */
    public static function today(int $uid, bool $apply_midnight = false): string
    {
        $now = new \DateTimeImmutable('now', self::tz($uid));
        $s = self::get($uid);
        if ($apply_midnight && $s['midnight_rule'] === 'prev' && (int) $now->format('G') < (int) $s['midnight_until']) {
            $now = $now->modify('-1 day');
        }
        return $now->format('Y-m-d');
    }
}

/** Kleine Helfer für Eingaben. */
final class In
{
    const NOTE_TAGS = ['b' => [], 'strong' => [], 'i' => [], 'em' => [], 'u' => [], 's' => [], 'br' => [], 'p' => [],
        'div' => [], 'ul' => [], 'ol' => [], 'li' => []];

    /** Notizen laufen durch eine Positivliste erlaubter HTML-Elemente. */
    public static function note($html): string
    {
        $html = (string) $html;
        $html = preg_replace('#<(script|style|iframe|object|embed)\b.*?</\1>#is', '', $html);
        $clean = wp_kses($html, self::NOTE_TAGS);
        return trim(str_replace("\xC2\xA0", ' ', $clean));
    }

    public static function text($v, int $max = 150): string
    {
        $v = trim(preg_replace('/\s+/u', ' ', wp_strip_all_tags((string) $v)));
        return function_exists('mb_substr') ? mb_substr($v, 0, $max) : substr($v, 0, $max);
    }

    public static function date($v): string
    {
        $v = (string) $v;
        if (!preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $v, $m) || !checkdate((int) $m[2], (int) $m[3], (int) $m[1])) {
            throw new Fail('bad_date', 'Das Datum ist ungültig. Erwartet wird JJJJ-MM-TT.');
        }
        return $v;
    }

    public static function time($v): string
    {
        $v = (string) $v;
        if (!preg_match('/^([01]?\d|2[0-3]):([0-5]\d)$/', $v, $m)) {
            throw new Fail('bad_time', 'Die Uhrzeit ist ungültig. Erwartet wird HH:MM.');
        }
        return sprintf('%02d:%02d', $m[1], $m[2]);
    }

    public static function month($v): string
    {
        $v = (string) $v;
        if (!preg_match('/^\d{4}-(0[1-9]|1[0-2])$/', $v)) {
            throw new Fail('bad_month', 'Der Monat ist ungültig. Erwartet wird JJJJ-MM.');
        }
        return $v;
    }

    /** Erster und letzter Tag eines Monats. */
    public static function month_range(string $month): array
    {
        $month = self::month($month);
        return ["$month-01", date('Y-m-t', strtotime("$month-01"))];
    }

    public static function color($v): string
    {
        $v = (string) $v;
        return preg_match('/^(#[0-9a-fA-F]{6}|c\d{1,2})$/', $v) ? $v : '';
    }

    /** Normalisiert Namen für den Abgleich: klein, ohne Rand-Leerzeichen, Umlaute vereinheitlicht. */
    public static function norm(string $s): string
    {
        $s = function_exists('mb_strtolower') ? mb_strtolower(trim($s)) : strtolower(trim($s));
        $s = strtr($s, ['ä' => 'ae', 'ö' => 'oe', 'ü' => 'ue', 'ß' => 'ss']);
        return preg_replace('/[^a-z0-9]+/', ' ', remove_accents($s));
    }
}
