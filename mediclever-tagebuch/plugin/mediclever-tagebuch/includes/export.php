<?php
namespace MCT;

/** Konto: Tagebuch leeren und vollständig löschen. */
final class Account
{
    const DATA_TABLES = ['entry_tags', 'entry_scale_values', 'entry_assets', 'entries', 'assets', 'goal_checks', 'goals', 'milestones',
        'scale_points', 'scales', 'tags', 'groups', 'moods', 'templates', 'reminders', 'import_map'];

    /** Löscht alle Tagebuchdaten samt Dateien. Einwilligung und Einstellungen bleiben auf Wunsch erhalten. */
    public static function wipe_diary(int $uid, bool $keep_settings = true): void
    {
        foreach (self::DATA_TABLES as $t) {
            Db::delete($t, $uid, []);
        }
        Assets::purge_user($uid);
        if (!$keep_settings) {
            delete_user_meta($uid, Settings::KEY);
        }
        Audit::log($uid, 'diary_wipe');
    }

    /** Vollständige Löschung: Daten, Fotos, Freigaben, Protokoll, Einstellungen. Das MediClever-Konto selbst bleibt bestehen. */
    public static function delete_all(int $uid): void
    {
        self::wipe_diary($uid, false);
        foreach (['oauth_tokens', 'mcp_grants', 'push_subs', 'audit_log'] as $t) {
            Db::delete($t, $uid, []);
        }
        delete_user_meta($uid, Settings::KEY);
    }
}

/** Löscht auf Wunsch auch das MediClever-Konto selbst. Nie ohne die Tagebuchdaten, nie für Verwalter der Seite. */
final class AccountRemoval
{
    public static function run(int $uid): void
    {
        if (user_can($uid, 'manage_options') || user_can($uid, 'edit_others_posts') || !apply_filters('mct_allow_account_delete', true, $uid)) {
            throw new Fail('account_protected', 'Dieses Konto lässt sich hier nicht löschen. Bitte wende dich an MediClever.', 403);
        }
        Account::delete_all($uid);
        require_once ABSPATH . 'wp-admin/includes/user.php';
        if (!wp_delete_user($uid)) {
            throw new Fail('account_delete', 'Das Konto ließ sich nicht löschen.', 500);
        }
    }
}

/** Export: CSV, PDF, eigenes Sicherungsformat, Fotos. */
final class Export
{
    const MONTHS = [1 => 'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

    private static function all_entries(int $uid, ?string $from = null, ?string $to = null): array
    {
        $rows = Db::rows('SELECT * FROM ' . Db::t('entries') . ' WHERE user_id = %d AND local_date BETWEEN %s AND %s ORDER BY local_date DESC, local_time DESC, id DESC',
            [$uid, $from ?: '1900-01-01', $to ?: '2999-12-31']);
        $out = [];
        foreach (array_chunk($rows, 500) as $chunk) {
            $out = array_merge($out, Entries::hydrate($uid, $chunk));
        }
        return $out;
    }

    private static function lookup(int $uid): array
    {
        $s = Setup::all($uid);
        $l = ['moods' => [], 'tags' => [], 'scales' => [], 'points' => []];
        foreach ($s['moods'] as $m) { $l['moods'][$m['id']] = $m; }
        foreach ($s['tags'] as $t) { $l['tags'][$t['id']] = $t; }
        foreach ($s['scales'] as $sc) {
            $l['scales'][$sc['id']] = $sc;
            foreach ($sc['points'] as $p) { $l['points'][$p['id']] = $p + ['of' => count($sc['points'])]; }
        }
        return $l;
    }

    public static function scale_text(array $v, array $l): ?string
    {
        $sc = $l['scales'][$v['scale_id']] ?? null;
        if (!$sc) {
            return null;
        }
        if ($v['point_id'] !== null && isset($l['points'][$v['point_id']])) {
            $p = $l['points'][$v['point_id']];
            return sprintf('%s: %s %d/%d', $sc['name'], $p['label'], $p['position'], $p['of']);
        }
        if ($v['minutes'] !== null) {
            return sprintf('%s: %d:%02d', $sc['name'], intdiv($v['minutes'], 60), $v['minutes'] % 60);
        }
        if ($v['num'] !== null) {
            return $sc['name'] . ': ' . rtrim(rtrim(number_format($v['num'], 2, '.', ''), '0'), '.');
        }
        return null;
    }

    /** CSV mit denselben Spalten wie die Importdatei. */
    public static function csv(int $uid): string
    {
        Features::need('export', $uid);
        $l = self::lookup($uid);
        $fh = fopen('php://temp', 'w+');
        fputcsv($fh, ['full_date', 'date', 'weekday', 'time', 'mood', 'activities', 'scales', 'note_title', 'note'], ',', '"', '');
        foreach (self::all_entries($uid) as $e) {
            $ts = strtotime($e['date']);
            $tags = array_map(fn($id) => $l['tags'][$id]['name'] ?? '', $e['tags']);
            $scales = array_filter(array_map(fn($v) => self::scale_text($v, $l), $e['scales']));
            fputcsv($fh, [$e['date'], (int) date('j', $ts) . '. ' . self::MONTHS[(int) date('n', $ts)], Stats::WEEKDAYS[(int) date('N', $ts)], $e['time'],
                $l['moods'][$e['mood_id']]['name'] ?? '', implode(' | ', $tags), implode(' | ', $scales), $e['title'], $e['note']], ',', '"', '');
        }
        rewind($fh);
        $csv = stream_get_contents($fh);
        fclose($fh);
        Audit::log($uid, 'export_csv');
        return "\xEF\xBB\xBF" . $csv;
    }

    /** Eigenes Sicherungsformat. Es nutzt dieselben Feldnamen, die der Import versteht. */
    public static function backup(int $uid): array
    {
        Features::need('backup', $uid);
        $s = Setup::all($uid);
        $ms = fn(string $utc) => strtotime($utc . ' UTC') * 1000;
        $out = ['mct_backup' => 1, 'version' => MCT_VERSION, 'created_at' => gmdate('c'), 'settings' => array_intersect_key(Settings::get($uid),
            array_flip(['week_start', 'midnight_rule', 'midnight_until', 'quotes', 'lookback', 'color_scheme', 'custom_colors', 'font', 'color_mode']))];
        $out['customMoods'] = array_map(fn($m) => ['id' => $m['id'], 'custom_name' => $m['name'], 'mood_group_id' => $m['level'], 'mood_group_order' => $m['position'],
            'state' => $m['archived'] ? 1 : 0, 'mct_icon' => $m['icon']], $s['moods']);
        $out['tag_groups'] = array_map(fn($g) => ['id' => $g['id'], 'name' => $g['name'], 'is_expanded' => !$g['collapsed'], 'order' => $g['position'],
            'color' => $g['color'], 'state' => $g['archived'] ? 1 : 0], $s['groups']);
        $out['tags'] = array_map(fn($t) => ['id' => $t['id'], 'name' => $t['name'], 'order' => $t['position'], 'state' => $t['archived'] ? 1 : 0,
            'id_tag_group' => $t['group_id'], 'color' => $t['color'] ?: null, 'mct_icon' => $t['icon']], $s['tags']);
        $out['scales'] = array_map(fn($x) => ['id' => $x['id'], 'name' => $x['name'], 'type' => $x['type'], 'order' => $x['position'], 'state' => $x['archived'] ? 1 : 0,
            'trend' => $x['trend'], 'chart_mode' => $x['chart_mode'], 'color' => $x['color'], 'mct_icon' => $x['icon'], 'min' => $x['min'], 'max' => $x['max'],
            'values' => array_map(fn($p) => ['id' => $p['id'], 'name' => $p['label'], 'order' => $p['position']], $x['points'])], $s['scales']);
        $out['goals'] = array_map(function ($r) {
            $g = Goals::out($r);
            return ['id' => $g['id'], 'name' => $g['name'], 'id_tag' => $g['tag_id'], 'state' => $g['archived'] ? 1 : 0, 'order' => $g['position'], 'note' => $g['note'],
                'created_at' => $g['start_date'], 'mct_end_date' => $g['end_date'], 'mct_freq_type' => $g['freq_type'], 'mct_freq_count' => $g['freq_count'],
                'mct_freq_days' => implode(',', $g['freq_days']), 'reminder_enabled' => $g['reminder_time'] !== null,
                'reminder_hour' => $g['reminder_time'] ? (int) substr($g['reminder_time'], 0, 2) : 0,
                'reminder_minute' => $g['reminder_time'] ? (int) substr($g['reminder_time'], 3, 2) : 0, 'reminder_text' => $g['reminder_text']];
        }, Db::rows('SELECT * FROM ' . Db::t('goals') . ' WHERE user_id = %d ORDER BY position, id', [$uid]));
        $out['goalEntries'] = array_map(fn($c) => ['id' => (int) $c['id'], 'goalId' => (int) $c['goal_id'], 'year' => (int) substr($c['check_date'], 0, 4),
            'month' => (int) substr($c['check_date'], 5, 2), 'day' => (int) substr($c['check_date'], 8, 2)],
            Db::rows('SELECT id, goal_id, check_date FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d ORDER BY check_date', [$uid]));
        $out['milestones'] = array_map(fn($m) => ['id' => (int) $m['id'], 'name' => $m['name'], 'date' => $m['m_date'], 'category' => $m['category'], 'note' => (string) $m['note'],
            'is_yearly' => (bool) $m['yearly'], 'asset_id' => $m['asset_id'] === null ? null : (int) $m['asset_id'],
            'reminders' => array_merge($m['remind_same_day'] ? [0] : [], $m['remind_days_before'] ? [(int) $m['remind_days_before']] : [])],
            Db::rows('SELECT * FROM ' . Db::t('milestones') . ' WHERE user_id = %d ORDER BY m_date', [$uid]));
        $out['writingTemplates'] = array_map(fn($t) => ['id' => $t['id'], 'title' => $t['title'], 'body' => $t['body'], 'order' => $t['position']], $s['templates']);
        $out['reminders'] = array_map(fn($r) => ['id' => $r['id'], 'hour' => (int) substr($r['time'], 0, 2), 'minute' => (int) substr($r['time'], 3, 2),
            'state' => $r['active'] ? 0 : 1, 'customTextEnabled' => true, 'customText' => $r['text']], $s['reminders']);
        $out['dayEntries'] = array_map(fn($e) => ['id' => $e['id'], 'datetime' => $ms($e['ts_utc']), 'timeZoneOffset' => $e['tz_offset'] * 60000, 'mood' => $e['mood_id'],
            'note' => $e['note'], 'note_title' => $e['title'], 'tags' => $e['tags'], 'assets' => array_column($e['assets'], 'id'), 'isFavorite' => $e['favorite'],
            'scales' => array_map(fn($v) => ['id_scale' => $v['scale_id'], 'id_text_scale_value' => $v['point_id'], 'time_value' => $v['minutes'], 'number_value' => $v['num']], $e['scales'])],
            array_reverse(self::all_entries($uid)));
        $out['assets'] = array_map(fn($a) => ['id' => (int) $a['id'], 'checksum' => 'a' . (int) $a['id'], 'type' => $a['type'], 'taken_at' => $a['taken_at']],
            Db::rows('SELECT id, type, taken_at FROM ' . Db::t('assets') . ' WHERE user_id = %d ORDER BY id', [$uid]));
        Audit::log($uid, 'export_backup');
        return $out;
    }

    /** ZIP mit Sicherung und/oder Fotos. Liefert den Pfad einer temporären Datei. */
    public static function zip(int $uid, bool $with_backup, bool $with_files): string
    {
        Features::need($with_backup ? 'backup' : 'export', $uid);
        if (!class_exists('ZipArchive')) {
            throw new Fail('no_zip', 'Auf diesem Server fehlt die ZIP-Erweiterung.', 500);
        }
        @set_time_limit(300);
        // wp_tempnam() gibt es nur im Adminbereich; über die REST-Schnittstelle ist es nicht geladen.
        $tmp = tempnam(get_temp_dir(), 'mct');
        $zip = new \ZipArchive();
        if ($tmp === false || $zip->open($tmp, \ZipArchive::OVERWRITE) !== true) {
            throw new Fail('no_zip', 'Die Datei ließ sich nicht erstellen.', 500);
        }
        if ($with_backup) {
            $zip->addFromString('backup.json', wp_json_encode(self::backup($uid)));
        }
        if ($with_files) {
            foreach (Db::rows('SELECT id, path, type FROM ' . Db::t('assets') . ' WHERE user_id = %d ORDER BY id', [$uid]) as $a) {
                $file = Assets::base_dir() . '/' . $a['path'];
                if (is_file($file)) {
                    $name = $with_backup ? 'assets/a' . (int) $a['id'] : 'fotos/' . (int) $a['id'] . '.' . pathinfo($file, PATHINFO_EXTENSION);
                    $zip->addFile($file, $name);
                    $zip->setCompressionName($name, \ZipArchive::CM_STORE);
                }
            }
        }
        $zip->close();
        Audit::log($uid, 'export_zip');
        return $tmp;
    }

    /** Schlichtes Text-PDF aller Einträge eines Zeitraums. Ohne Bibliothek, nur Standardschriften. */
    public static function pdf(int $uid, ?string $from = null, ?string $to = null): string
    {
        Features::need('export', $uid);
        $l = self::lookup($uid);
        $lines = [['b', 16, 'MediClever Tagebuch'], ['n', 9, 'Export vom ' . wp_date('d.m.Y')], ['n', 6, '']];
        foreach (self::all_entries($uid, $from, $to) as $e) {
            $ts = strtotime($e['date']);
            $lines[] = ['b', 11, sprintf('%s, %s · %s Uhr · %s', Stats::WEEKDAYS[(int) date('N', $ts)], date('d.m.Y', $ts), $e['time'], $l['moods'][$e['mood_id']]['name'] ?? '')];
            $tags = array_filter(array_map(fn($id) => $l['tags'][$id]['name'] ?? '', $e['tags']));
            if ($tags) {
                $lines[] = ['n', 9, 'Aktivitäten: ' . implode(', ', $tags)];
            }
            $scales = array_filter(array_map(fn($v) => self::scale_text($v, $l), $e['scales']));
            if ($scales) {
                $lines[] = ['n', 9, implode(' · ', $scales)];
            }
            if ($e['title'] !== '') {
                $lines[] = ['b', 10, $e['title']];
            }
            $text = trim(html_entity_decode(wp_strip_all_tags(preg_replace('#<(br|/p|/div|/li)[^>]*>#i', "\n", str_replace('<li>', '• ', $e['note']))), ENT_QUOTES, 'UTF-8'));
            foreach ($text === '' ? [] : preg_split('/\n+/', $text) as $para) {
                $lines[] = ['n', 10, $para];
            }
            $lines[] = ['n', 6, ''];
        }
        Audit::log($uid, 'export_pdf');
        return self::render_pdf($lines);
    }

    private static function render_pdf(array $lines): string
    {
        $enc = fn(string $s) => strtr((string) @iconv('UTF-8', 'Windows-1252//TRANSLIT//IGNORE', $s), ['\\' => '\\\\', '(' => '\\(', ')' => '\\)']);
        $pages = [];
        $cur = '';
        $y = 800;
        foreach ($lines as [$style, $size, $text]) {
            $max = (int) floor(500 / ($size * 0.5));
            $wrapped = $text === '' ? [''] : explode("\n", wordwrap($text, $max, "\n", true));
            foreach ($wrapped as $w) {
                if ($y < 50) {
                    $pages[] = $cur;
                    $cur = '';
                    $y = 800;
                }
                $cur .= sprintf("BT /%s %d Tf 48 %d Td (%s) Tj ET\n", $style === 'b' ? 'F2' : 'F1', $size, $y, $enc($w));
                $y -= (int) round($size * 1.45);
            }
        }
        $pages[] = $cur;
        $objs = [1 => '<< /Type /Catalog /Pages 2 0 R >>', 2 => '',
            3 => '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
            4 => '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>'];
        $kids = [];
        $n = 5;
        foreach ($pages as $content) {
            $objs[$n] = "<< /Length " . strlen($content) . " >>\nstream\n" . $content . "endstream";
            $objs[$n + 1] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $n 0 R >>";
            $kids[] = ($n + 1) . ' 0 R';
            $n += 2;
        }
        $objs[2] = '<< /Type /Pages /Kids [' . implode(' ', $kids) . '] /Count ' . count($kids) . ' >>';
        ksort($objs);
        $pdf = "%PDF-1.4\n%\xE2\xE3\xCF\xD3\n";
        $offsets = [];
        foreach ($objs as $i => $body) {
            $offsets[$i] = strlen($pdf);
            $pdf .= "$i 0 obj\n$body\nendobj\n";
        }
        $xref = strlen($pdf);
        $pdf .= "xref\n0 " . (count($objs) + 1) . "\n0000000000 65535 f \n";
        foreach ($offsets as $o) {
            $pdf .= sprintf("%010d 00000 n \n", $o);
        }
        return $pdf . "trailer\n<< /Size " . (count($objs) + 1) . " /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n";
    }
}
