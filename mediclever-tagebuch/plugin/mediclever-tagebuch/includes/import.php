<?php
namespace MCT;

/**
 * Import eines Backups (ZIP mit Base64-JSON, vom Browser entpackt und in Paketen geschickt) und einer CSV-Datei.
 * Jede Quell-ID landet in import_map: Ein zweiter Lauf derselben Datei erzeugt keine Doppelten.
 * Fehlermeldungen und Bericht enthalten nur Zählwerte und IDs, nie Inhalte.
 */
final class Import
{
    /** Zuordnung häufiger Aktivitätsnamen auf eigene Symbole. Unbekanntes bekommt ein neutrales Symbol. */
    const ICON_WORDS = [
        'arbeit' => 'briefcase', 'work' => 'briefcase', 'dienst' => 'briefcase', 'schicht' => 'clock', 'nacht' => 'moon', 'schlaf' => 'bed',
        'sport' => 'dumbbell', 'training' => 'dumbbell', 'gym' => 'dumbbell', 'lauf' => 'footprints', 'jogg' => 'footprints', 'spazier' => 'footprints',
        'wander' => 'mountain', 'rad' => 'bike', 'fahrrad' => 'bike', 'schwimm' => 'waves', 'yoga' => 'leaf', 'meditat' => 'leaf', 'entspann' => 'leaf',
        'famil' => 'users', 'freund' => 'handshake', 'partner' => 'heart', 'date' => 'heart', 'liebe' => 'heart', 'sex' => 'heart', 'kolleg' => 'user-check',
        'lesen' => 'book', 'buch' => 'book', 'lern' => 'book-open', 'studi' => 'graduation-cap', 'schule' => 'graduation-cap', 'fortbild' => 'graduation-cap',
        'musik' => 'music', 'film' => 'film', 'kino' => 'film', 'serie' => 'tv', 'tv' => 'tv', 'fernseh' => 'tv', 'spiel' => 'gamepad-2', 'gaming' => 'gamepad-2', 'zock' => 'gamepad-2',
        'koch' => 'chef-hat', 'essen' => 'utensils', 'restaurant' => 'utensils', 'fast food' => 'pizza', 'pizza' => 'pizza', 'kaffee' => 'coffee', 'alkohol' => 'wine', 'bier' => 'beer',
        'wein' => 'wine', 'wasser' => 'glass-water', 'trink' => 'glass-water', 'einkauf' => 'shopping-cart', 'shopping' => 'shopping-bag', 'putz' => 'spray-can', 'haushalt' => 'home',
        'aufräum' => 'home', 'wäsche' => 'shirt', 'garten' => 'flower-2', 'hund' => 'dog', 'katze' => 'cat', 'tier' => 'paw-print', 'reise' => 'plane', 'urlaub' => 'palmtree',
        'auto' => 'car', 'fahr' => 'car', 'bahn' => 'train-front', 'krank' => 'thermometer', 'arzt' => 'stethoscope', 'medika' => 'pill', 'tablet' => 'pill', 'schmerz' => 'zap',
        'kopf' => 'brain', 'migr' => 'brain', 'period' => 'droplet', 'dusch' => 'shower-head', 'bad' => 'bath', 'foto' => 'camera', 'mal' => 'palette', 'kreativ' => 'palette',
        'schreib' => 'pen-line', 'party' => 'party-popper', 'feier' => 'party-popper', 'konzert' => 'mic', 'geburtstag' => 'cake', 'geld' => 'wallet', 'sonne' => 'sun',
        'regen' => 'cloud-rain', 'wetter' => 'cloud-sun', 'schnee' => 'snowflake', 'frei' => 'coffee', 'pause' => 'coffee', 'telefon' => 'phone', 'handy' => 'smartphone',
        'social' => 'smartphone', 'computer' => 'laptop', 'program' => 'code', 'ehrenamt' => 'heart-handshake', 'feuerwehr' => 'flame', 'rettung' => 'siren', 'einsatz' => 'siren',
        'natur' => 'trees', 'draußen' => 'trees', 'wald' => 'trees', 'stress' => 'activity', 'müde' => 'battery-low', 'energie' => 'battery', 'kirche' => 'church', 'tanz' => 'music-2',
    ];

    public static function icon_for(string $name): string
    {
        $n = function_exists('mb_strtolower') ? mb_strtolower($name) : strtolower($name);
        foreach (self::ICON_WORDS as $word => $icon) {
            if (str_contains($n, $word)) {
                return $icon;
            }
        }
        return 'circle';
    }

    /** Farbnummer des Vorbilds auf die eigene Palette (c1 bis c12). */
    public static function color_for($n): string
    {
        if (is_string($n) && preg_match('/^c\d{1,2}$/', $n)) {
            return $n;
        }
        return 'c' . ((max(0, (int) $n - 1) % 12) + 1);
    }

    private static function pick(array $a, array $keys, $default = null)
    {
        foreach ($keys as $k) {
            if (array_key_exists($k, $a) && $a[$k] !== null) {
                return $a[$k];
            }
        }
        return $default;
    }

    private static function run_key(string $key): string
    {
        $key = preg_replace('/[^a-zA-Z0-9]/', '', $key);
        if (strlen($key) < 8) {
            throw new Fail('bad_run', 'Der Importlauf ist nicht eindeutig gekennzeichnet.');
        }
        return substr($key, 0, 64);
    }

    private static function mapped(int $uid, string $run, string $kind, $src): ?int
    {
        $v = Db::val('SELECT new_id FROM ' . Db::t('import_map') . ' WHERE user_id = %d AND run_key = %s AND kind = %s AND source_id = %s', [$uid, $run, $kind, (string) $src]);
        return $v === null ? null : (int) $v;
    }

    private static function remember(int $uid, string $run, string $kind, $src, int $new): void
    {
        Db::run('INSERT IGNORE INTO ' . Db::t('import_map') . ' (user_id, run_key, kind, source_id, new_id) VALUES (%d, %s, %s, %s, %d)', [$uid, $run, $kind, (string) $src, $new]);
    }

    /** Alle Zuordnungen eines Laufs für eine Art als [Quell-ID => neue ID]. */
    private static function map_all(int $uid, string $run, string $kind): array
    {
        $out = [];
        foreach (Db::rows('SELECT source_id, new_id FROM ' . Db::t('import_map') . ' WHERE user_id = %d AND run_key = %s AND kind = %s', [$uid, $run, $kind]) as $r) {
            $out[$r['source_id']] = (int) $r['new_id'];
        }
        return $out;
    }

    public static function diary_empty(int $uid): bool
    {
        return !(int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid])
            && !(int) Db::val('SELECT COUNT(*) FROM ' . Db::t('tags') . ' WHERE user_id = %d', [$uid]);
    }

    /** Start eines Laufs. mode „new“ leert das Tagebuch vorher (nur mit Bestätigung), „merge“ führt zusammen. */
    public static function start(int $uid, string $run, string $mode, bool $confirm_wipe): array
    {
        Features::need('import', $uid);
        Entries::need_consent($uid);
        $run = self::run_key($run);
        $resumed = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('import_map') . ' WHERE user_id = %d AND run_key = %s', [$uid, $run]) > 0;
        if ($mode === 'new' && !$resumed) {
            $entries = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid]);
            if ($entries > 0 && !$confirm_wipe) {
                throw new Fail('not_empty', sprintf('Dein Tagebuch enthält bereits %d Einträge. Sie werden beim Import in ein leeres Tagebuch gelöscht.', $entries), 409, ['count' => $entries]);
            }
            Account::wipe_diary($uid, true);
        }
        Audit::log($uid, 'import_start');
        return ['run_key' => $run, 'resumed' => $resumed, 'status' => self::status($uid, $run)];
    }

    public static function status(int $uid, string $run): array
    {
        $run = self::run_key($run);
        $counts = [];
        foreach (Db::rows('SELECT kind, COUNT(*) AS n FROM ' . Db::t('import_map') . ' WHERE user_id = %d AND run_key = %s GROUP BY kind', [$uid, $run]) as $r) {
            $counts[$r['kind']] = (int) $r['n'];
        }
        return ['counts' => $counts, 'assets_done' => array_keys(self::map_all($uid, $run, 'asset'))];
    }

    private static function result(): array
    {
        return ['created' => 0, 'skipped' => 0, 'errors' => 0, 'warnings' => []];
    }

    private static function add(array &$total, string $kind, array $r): void
    {
        $total[$kind] = $r;
    }

    /**
     * Einrichtung aus dem Backup: Stimmungen, Gruppen, Aktivitäten, Skalen, Ziele, wichtige Tage, Vorlagen, Erinnerungen, Einstellungen.
     * $json ist das entpackte Backup ohne die großen Listen (dayEntries, goalEntries, assets).
     */
    public static function setup(int $uid, string $run, array $json): array
    {
        Features::need('import', $uid);
        $run = self::run_key($run);
        $report = [];
        $existing = Setup::all($uid);

        // Stimmungen: Stufe 1 ist die beste.
        $r = self::result();
        foreach ((array) ($json['customMoods'] ?? []) as $m) {
            $src = self::pick($m, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'mood', $src) !== null) { $r['skipped']++; continue; }
            $level = max(1, min(5, (int) self::pick($m, ['mood_group_id', 'level', 'moodGroupId'], 3)));
            $name = In::text((string) self::pick($m, ['custom_name', 'name', 'customName'], ''), 100) ?: Setup::MOOD_DEFAULTS[$level];
            $found = null;
            foreach ($existing['moods'] as $x) {
                if ((int) $x['level'] === $level && In::norm($x['name']) === In::norm($name)) { $found = $x['id']; break; }
            }
            if ($found) {
                $r['skipped']++;
            } else {
                $micon = self::pick($m, ['mct_icon']);
                $found = Db::insert('moods', $uid, ['level' => $level, 'name' => $name, 'icon' => is_string($micon) && $micon !== '' ? sanitize_key($micon) : 'm' . $level,
                    'position' => (int) self::pick($m, ['mood_group_order', 'order', 'position'], 0),
                    'archived_at' => (int) self::pick($m, ['state'], 0) === 1 ? Db::now() : null]);
                $existing['moods'][] = ['id' => $found, 'level' => $level, 'name' => $name];
                $r['created']++;
            }
            self::remember($uid, $run, 'mood', $src, $found);
        }
        // Unbenutzte Vorgaben weichen den importierten Stimmungen derselben Stufe.
        if ($r['created'] > 0) {
            $imported = array_values(self::map_all($uid, $run, 'mood'));
            foreach (Db::rows('SELECT id, level, name, icon FROM ' . Db::t('moods') . ' WHERE user_id = %d', [$uid]) as $m) {
                $level = (int) $m['level'];
                if (in_array((int) $m['id'], $imported, true) || $m['name'] !== Setup::MOOD_DEFAULTS[$level] || $m['icon'] !== 'm' . $level) {
                    continue;
                }
                $others = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('moods') . ' WHERE user_id = %d AND level = %d AND id <> %d AND archived_at IS NULL', [$uid, $level, (int) $m['id']]);
                if ($others > 0 && Setup::usage($uid, 'moods', (int) $m['id']) === 0) {
                    Db::delete('moods', $uid, ['id' => (int) $m['id']]);
                }
            }
            $existing['moods'] = Setup::all($uid)['moods'];
        }
        self::add($report, 'moods', $r);

        // Gruppen
        $r = self::result();
        foreach ((array) ($json['tag_groups'] ?? []) as $g) {
            $src = self::pick($g, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'group', $src) !== null) { $r['skipped']++; continue; }
            $name = In::text((string) self::pick($g, ['name'], ''), 100) ?: 'Gruppe';
            $found = null;
            foreach ($existing['groups'] as $x) {
                if (In::norm($x['name']) === In::norm($name)) { $found = $x['id']; break; }
            }
            if ($found) {
                $r['skipped']++;
            } else {
                $expanded = self::pick($g, ['is_expanded', 'isExpanded', 'expanded'], true);
                $found = Db::insert('groups', $uid, ['name' => $name, 'color' => self::color_for(self::pick($g, ['color', 'color_id', 'id_color', 'colorId'], 1)),
                    'position' => (int) self::pick($g, ['order', 'position'], 0), 'collapsed' => $expanded ? 0 : 1,
                    'archived_at' => (int) self::pick($g, ['state'], 0) === 1 ? Db::now() : null]);
                $existing['groups'][] = ['id' => $found, 'name' => $name];
                $r['created']++;
            }
            self::remember($uid, $run, 'group', $src, $found);
        }
        self::add($report, 'groups', $r);
        $group_map = self::map_all($uid, $run, 'group');

        // Aktivitäten
        $r = self::result();
        foreach ((array) ($json['tags'] ?? []) as $t) {
            $src = self::pick($t, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'tag', $src) !== null) { $r['skipped']++; continue; }
            $name = In::text((string) self::pick($t, ['name'], ''), 100) ?: 'Aktivität';
            $archived = (int) self::pick($t, ['state'], 0) === 1;
            $found = null;
            foreach ($existing['tags'] as $x) {
                if (In::norm($x['name']) === In::norm($name) && !empty($x['archived']) === $archived) { $found = $x['id']; break; }
            }
            if ($found) {
                $r['skipped']++;
            } else {
                $gsrc = self::pick($t, ['id_tag_group', 'tag_group_id', 'group_id', 'idTagGroup']);
                $icon = self::pick($t, ['mct_icon']);
                $found = Db::insert('tags', $uid, ['name' => $name, 'group_id' => $gsrc !== null ? ($group_map[(string) $gsrc] ?? null) : null,
                    'icon' => is_string($icon) && $icon !== '' ? sanitize_key($icon) : self::icon_for($name),
                    'color' => self::pick($t, ['color', 'color_id', 'id_color']) !== null ? self::color_for(self::pick($t, ['color', 'color_id', 'id_color'])) : '',
                    'position' => (int) self::pick($t, ['order', 'position'], 0), 'archived_at' => $archived ? Db::now() : null]);
                $existing['tags'][] = ['id' => $found, 'name' => $name, 'archived' => $archived];
                $r['created']++;
            }
            self::remember($uid, $run, 'tag', $src, $found);
        }
        self::add($report, 'tags', $r);
        $tag_map = self::map_all($uid, $run, 'tag');

        // Skalen samt Punkten. Punkte können in der Skala stecken oder als eigene Liste vorliegen.
        $r = self::result();
        $loose_points = [];
        foreach (['scale_values', 'scaleValues', 'text_scale_values', 'textScaleValues'] as $k) {
            foreach ((array) ($json[$k] ?? []) as $p) {
                $sid = self::pick($p, ['id_scale', 'scale_id', 'scaleId']);
                if ($sid !== null) {
                    $loose_points[(string) $sid][] = $p;
                }
            }
        }
        foreach ((array) ($json['scales'] ?? []) as $s) {
            $src = self::pick($s, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'scale', $src) !== null) { $r['skipped']++; continue; }
            $name = In::text((string) self::pick($s, ['name'], ''), 100) ?: 'Skala';
            $points = self::pick($s, ['values', 'text_values', 'textValues', 'points', 'scale_values', 'items'], $loose_points[(string) $src] ?? []);
            $points = is_array($points) ? $points : [];
            $rawtype = self::pick($s, ['type', 'scale_type', 'id_type', 'value_type'], null);
            $t = is_string($rawtype) ? strtolower($rawtype) : $rawtype;
            if ($points) {
                $type = 'text';
            } elseif (in_array($t, ['time', 'duration', 'zeit'], true) || self::pick($s, ['is_time', 'isTime'], false)) {
                $type = 'time';
            } elseif (in_array($t, ['num', 'number', 'numeric', 'zahl'], true)) {
                $type = 'num';
            } else {
                // Laut Projektbeschreibung kennt das Backup nur Text- und Zeitskalen: ohne Punkte also Zeit.
                $type = 'time';
            }
            $found = null;
            foreach ($existing['scales'] as $x) {
                if (In::norm($x['name']) === In::norm($name) && $x['type'] === $type) { $found = $x['id']; break; }
            }
            $trend = self::pick($s, ['trend', 'trend_type', 'trendType'], 'up');
            $trend = in_array($trend, ['down', 'lower_is_better', 'negative', 1, '1', -1, '-1'], true) ? 'down' : 'up';
            $chart = self::pick($s, ['chart_mode', 'chart_type', 'chart_display', 'chartType', 'display', 'aggregation'], 'avg');
            $chart = in_array($chart, ['sum', 'daily_sum', 'total', 1, '1'], true) ? 'sum' : 'avg';
            if ($found) {
                $r['skipped']++;
            } else {
                $icon = self::pick($s, ['mct_icon']);
                $found = Db::insert('scales', $uid, ['type' => $type, 'name' => $name, 'icon' => is_string($icon) && $icon !== '' ? sanitize_key($icon) : ($type === 'time' ? 'clock' : 'gauge'),
                    'color' => self::color_for(self::pick($s, ['color', 'color_id', 'id_color'], 2)), 'position' => (int) self::pick($s, ['order', 'position'], 0),
                    'trend' => $trend, 'chart_mode' => $chart,
                    'min_val' => $type === 'text' ? null : (float) self::pick($s, ['min', 'min_value'], 0),
                    'max_val' => $type === 'text' ? null : (float) self::pick($s, ['max', 'max_value'], $type === 'time' ? 720 : 10),
                    'archived_at' => (int) self::pick($s, ['state'], 0) === 1 ? Db::now() : null]);
                $r['created']++;
            }
            self::remember($uid, $run, 'scale', $src, $found);
            usort($points, fn($a, $b) => (int) self::pick($a, ['order', 'position', 'value'], 0) <=> (int) self::pick($b, ['order', 'position', 'value'], 0));
            $have = Db::rows('SELECT id, label FROM ' . Db::t('scale_points') . ' WHERE user_id = %d AND scale_id = %d', [$uid, $found]);
            foreach ($points as $i => $p) {
                $psrc = self::pick($p, ['id']);
                if ($psrc === null || self::mapped($uid, $run, 'point', $psrc) !== null) { continue; }
                $label = In::text((string) self::pick($p, ['name', 'label', 'text'], ''), 100) ?: ('Punkt ' . ($i + 1));
                $pid = null;
                foreach ($have as $h) {
                    if (In::norm($h['label']) === In::norm($label)) { $pid = (int) $h['id']; break; }
                }
                $pid = $pid ?: Db::insert('scale_points', $uid, ['scale_id' => $found, 'label' => $label, 'position' => $i + 1]);
                self::remember($uid, $run, 'point', $psrc, $pid);
            }
        }
        self::add($report, 'scales', $r);

        // Ziele
        $r = self::result();
        foreach ((array) ($json['goals'] ?? []) as $g) {
            $src = self::pick($g, ['id', 'goal_id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'goal', $src) !== null) { $r['skipped']++; continue; }
            $tsrc = self::pick($g, ['id_tag', 'tag_id', 'idTag']);
            $tag_id = $tsrc !== null && (int) $tsrc > 0 ? ($tag_map[(string) $tsrc] ?? null) : null;
            $name = In::text((string) self::pick($g, ['name'], ''), 150);
            if ($name === '' && $tag_id) {
                $name = (string) Db::val('SELECT name FROM ' . Db::t('tags') . ' WHERE id = %d AND user_id = %d', [$tag_id, $uid]);
            }
            [$ftype, $fcount, $fdays, $warn] = self::goal_frequency($g);
            if ($warn) {
                $r['warnings'][] = 'Ziel mit unbekannter Wiederholung als „täglich“ übernommen (Quell-ID ' . (int) $src . ').';
            }
            $created = self::pick($g, ['created_at', 'createdAt', 'start_date']);
            $start = is_numeric($created) ? gmdate('Y-m-d', (int) ($created / 1000)) : (is_string($created) && preg_match('/^\d{4}-\d{2}-\d{2}/', $created) ? substr($created, 0, 10) : Settings::today($uid));
            $rem_on = (bool) self::pick($g, ['reminder_enabled', 'reminderEnabled'], false);
            $id = Db::insert('goals', $uid, ['name' => $name ?: 'Ziel', 'tag_id' => $tag_id, 'freq_type' => $ftype, 'freq_count' => $fcount, 'freq_days' => $fdays,
                'reminder_time' => $rem_on ? sprintf('%02d:%02d', (int) self::pick($g, ['reminder_hour'], 20) % 24, (int) self::pick($g, ['reminder_minute'], 0) % 60) : null,
                'reminder_text' => In::text((string) self::pick($g, ['reminder_text', 'reminderText'], ''), 255),
                'start_date' => $start, 'end_date' => preg_match('/^\d{4}-\d{2}-\d{2}$/', (string) self::pick($g, ['mct_end_date'], '')) ? self::pick($g, ['mct_end_date']) : null, 'note' => In::text((string) self::pick($g, ['note'], ''), 1000),
                'position' => (int) self::pick($g, ['order', 'position'], 0),
                'archived_at' => in_array((int) self::pick($g, ['state'], 0), [1, 2], true) ? Db::now() : null]);
            self::remember($uid, $run, 'goal', $src, $id);
            $r['created']++;
        }
        self::add($report, 'goals', $r);

        // Wichtige Tage: Der Monat zählt hier ab 1.
        $r = self::result();
        foreach ((array) ($json['milestones'] ?? []) as $m) {
            $src = self::pick($m, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (self::mapped($uid, $run, 'milestone', $src) !== null) { $r['skipped']++; continue; }
            $y = (int) self::pick($m, ['year'], 0);
            $mo = (int) self::pick($m, ['month'], 0);
            $d = (int) self::pick($m, ['day'], 0);
            $iso = self::pick($m, ['date']);
            if (is_string($iso) && preg_match('/^(\d{4})-(\d{2})-(\d{2})/', $iso, $mm)) {
                [$y, $mo, $d] = [(int) $mm[1], (int) $mm[2], (int) $mm[3]];
            }
            if (!checkdate($mo, $d, $y)) { $r['errors']++; continue; }
            $cat = self::pick($m, ['category', 'category_name', 'categoryName'], '');
            $rem = (array) self::pick($m, ['reminders'], []);
            $before = 0;
            $same = false;
            foreach ($rem as $x) {
                $days = is_array($x) ? (int) self::pick($x, ['days_before', 'daysBefore', 'offset_days'], 0) : (int) $x;
                if ($days === 0) { $same = true; } else { $before = max($before, $days); }
            }
            $id = Db::insert('milestones', $uid, ['name' => In::text((string) self::pick($m, ['name'], ''), 150) ?: 'Wichtiger Tag',
                'm_date' => sprintf('%04d-%02d-%02d', $y, $mo, $d), 'category' => is_string($cat) ? In::text($cat, 60) : '',
                'note' => In::text((string) self::pick($m, ['note'], ''), 2000),
                'yearly' => self::pick($m, ['is_yearly', 'yearly', 'isAnniversary', 'is_anniversary', 'repeat_yearly'], true) ? 1 : 0,
                'remind_same_day' => $same ? 1 : 0, 'remind_days_before' => min(60, $before)]);
            self::remember($uid, $run, 'milestone', $src, $id);
            $asset_refs = (array) self::pick($m, ['assets', 'asset_ids'], []);
            $single = self::pick($m, ['id_asset', 'asset_id', 'photo_id']);
            if ($single !== null) {
                $asset_refs[] = $single;
            }
            foreach ($asset_refs as $aref) {
                $aid = is_array($aref) ? self::pick($aref, ['id']) : $aref;
                if ($aid !== null) {
                    self::remember($uid, $run, 'alink', $aid . '>m>0', $id);
                }
            }
            $r['created']++;
        }
        self::add($report, 'milestones', $r);

        // Schreibvorlagen (Text als HTML, durch die Positivliste)
        $r = self::result();
        foreach ((array) ($json['writingTemplates'] ?? []) as $i => $t) {
            $src = self::pick($t, ['id'], 't' . $i);
            if (self::mapped($uid, $run, 'template', $src) !== null) { $r['skipped']++; continue; }
            $title = In::text((string) self::pick($t, ['title', 'name'], ''), 150) ?: 'Vorlage';
            $dup = Db::val('SELECT id FROM ' . Db::t('templates') . ' WHERE user_id = %d AND title = %s', [$uid, $title]);
            if ($dup) {
                $r['skipped']++;
                self::remember($uid, $run, 'template', $src, (int) $dup);
                continue;
            }
            $id = Db::insert('templates', $uid, ['title' => $title, 'body' => In::note((string) self::pick($t, ['body', 'text'], '')),
                'position' => (int) self::pick($t, ['order', 'position'], $i)]);
            self::remember($uid, $run, 'template', $src, $id);
            $r['created']++;
        }
        self::add($report, 'templates', $r);

        // Erinnerungen
        $r = self::result();
        foreach ((array) ($json['reminders'] ?? []) as $i => $t) {
            $src = self::pick($t, ['id'], 'r' . $i);
            if (self::mapped($uid, $run, 'reminder', $src) !== null) { $r['skipped']++; continue; }
            $time = sprintf('%02d:%02d', (int) self::pick($t, ['hour'], 20) % 24, (int) self::pick($t, ['minute'], 0) % 60);
            $custom = self::pick($t, ['customTextEnabled', 'custom_text_enabled'], true) ? (string) self::pick($t, ['customText', 'custom_text', 'text'], '') : '';
            $id = Db::insert('reminders', $uid, ['time' => $time, 'text' => In::text($custom, 255), 'active' => (int) self::pick($t, ['state'], 0) === 0 ? 1 : 0]);
            self::remember($uid, $run, 'reminder', $src, $id);
            $r['created']++;
        }
        self::add($report, 'reminders', $r);

        // Einstellungen: nur, was es hier gibt.
        $prefs = [];
        foreach ((array) ($json['prefs'] ?? []) as $k => $p) {
            if (is_array($p)) {
                $prefs[strtoupper((string) self::pick($p, ['key', 'pref_name', 'name'], ''))] = self::pick($p, ['value']);
            } else {
                $prefs[strtoupper((string) $k)] = $p;
            }
        }
        $set = [];
        foreach ($prefs as $k => $v) {
            if (str_contains($k, 'FIRST_DAY_OF_WEEK') || str_contains($k, 'WEEK_START')) {
                // Kalenderzählung des Vorbilds: 1 Sonntag, 2 Montag, 7 Samstag.
                $set['week_start'] = [1 => 0, 2 => 1, 7 => 6][(int) $v] ?? 1;
            }
            if (str_contains($k, 'MIDNIGHT') || str_contains($k, 'PAST_MIDNIGHT')) {
                $set['midnight_rule'] = [0 => 'ask', 1 => 'prev', 2 => 'current'][(int) $v] ?? 'ask';
            }
        }
        if ($set) {
            Settings::update($uid, $set);
        }
        $report['prefs'] = ['created' => count($set), 'skipped' => count($prefs) - count($set), 'errors' => 0, 'warnings' => []];
        Setup::ensure_moods($uid);
        Settings::set_raw($uid, 'onboarded', true);
        return $report;
    }

    /** Wiederholung eines Ziels. Unbekannte Werte werden als täglich übernommen und gemeldet. */
    public static function goal_frequency(array $g): array
    {
        $own = self::pick($g, ['mct_freq_type']);
        if ($own) {
            return [(string) $own, (int) self::pick($g, ['mct_freq_count'], 1), (string) self::pick($g, ['mct_freq_days'], ''), false];
        }
        $type = self::pick($g, ['repeat_type', 'repeatType']);
        $value = (int) self::pick($g, ['repeat_value', 'repeatValue'], 0);
        $t = is_string($type) ? strtolower($type) : (int) $type;
        if ($t === 'weekly' || $t === 2) {
            return ['weekly', max(1, min(7, $value ?: 1)), '', false];
        }
        if ($t === 'daily' || $t === 1 || $t === 0) {
            // Bitmaske der Wochentage, sofern nicht alle sieben gesetzt sind (Bit 0 = Montag).
            if ($value > 0 && $value < 127) {
                $days = [];
                for ($i = 0; $i < 7; $i++) {
                    if ($value & (1 << $i)) { $days[] = $i + 1; }
                }
                return ['days', 1, implode(',', $days), false];
            }
            return ['daily', 1, '', false];
        }
        return ['daily', 1, '', true];
    }

    /** Ermittelt die Einheit der Zeitwerte: Minuten, Sekunden oder Millisekunden. */
    public static function time_divisor(float $max): int
    {
        if ($max > 6000000) {
            return 60000;
        }
        return $max > 6000 ? 60 : 1;
    }

    /** Einträge in Paketen. Monat zählt ab 0; maßgeblich ist datetime (ms, UTC) mit timeZoneOffset. */
    public static function entries(int $uid, string $run, array $items, int $time_divisor = 1): array
    {
        Features::need('import', $uid);
        Entries::need_consent($uid);
        $run = self::run_key($run);
        $r = self::result();
        $mood_map = self::map_all($uid, $run, 'mood');
        $tag_map = self::map_all($uid, $run, 'tag');
        $scale_map = self::map_all($uid, $run, 'scale');
        $point_map = self::map_all($uid, $run, 'point');
        $done = self::map_all($uid, $run, 'entry');
        $fallback = (int) Db::val('SELECT id FROM ' . Db::t('moods') . ' WHERE user_id = %d AND level = 3 ORDER BY id LIMIT 1', [$uid]);
        $scale_types = [];
        foreach (Db::rows('SELECT id, type FROM ' . Db::t('scales') . ' WHERE user_id = %d', [$uid]) as $s) {
            $scale_types[(int) $s['id']] = $s['type'];
        }
        global $wpdb;
        foreach ($items as $e) {
            $src = self::pick((array) $e, ['id']);
            if ($src === null) { $r['errors']++; continue; }
            if (isset($done[(string) $src])) { $r['skipped']++; continue; }
            $ms = self::pick($e, ['datetime', 'dateTime']);
            $off_ms = (int) self::pick($e, ['timeZoneOffset', 'time_zone_offset'], 0);
            if (is_numeric($ms)) {
                $utc = (int) floor($ms / 1000);
                $local = $utc + (int) round($off_ms / 1000);
                $date = gmdate('Y-m-d', $local);
                $time = gmdate('H:i', $local);
            } else {
                $y = (int) self::pick($e, ['year'], 0);
                $mo = (int) self::pick($e, ['month'], -1) + 1;
                $d = (int) self::pick($e, ['day'], 0);
                if (!checkdate($mo, $d, $y)) { $r['errors']++; continue; }
                $date = sprintf('%04d-%02d-%02d', $y, $mo, $d);
                $time = sprintf('%02d:%02d', (int) self::pick($e, ['hour'], 0) % 24, (int) self::pick($e, ['minute'], 0) % 60);
                $utc = strtotime("$date $time:00 UTC") - (int) round($off_ms / 1000);
            }
            $mood = $mood_map[(string) self::pick($e, ['mood', 'mood_id'], '')] ?? null;
            if (!$mood) {
                $mood = $fallback;
                $r['warnings'][] = 'Eintrag mit unbekannter Stimmung der mittleren Stufe zugeordnet (Quell-ID ' . (int) $src . ').';
            }
            $id = Db::insert('entries', $uid, ['ts_utc' => gmdate('Y-m-d H:i:s', $utc), 'tz_offset' => (int) round($off_ms / 60000), 'local_date' => $date,
                'local_time' => $time, 'mood_id' => $mood, 'title' => In::text((string) self::pick($e, ['note_title', 'title'], ''), 255),
                'note' => In::note((string) self::pick($e, ['note'], '')),
                'favorite' => self::pick($e, ['isFavorite', 'is_favorite', 'favorite'], false) ? 1 : 0,
                'source' => 'import', 'created_at' => Db::now(), 'updated_at' => Db::now()]);
            self::remember($uid, $run, 'entry', $src, $id);
            foreach (array_unique(array_map('strval', (array) self::pick($e, ['tags'], []))) as $t) {
                if (isset($tag_map[$t])) {
                    Db::run('INSERT IGNORE INTO ' . Db::t('entry_tags') . ' (entry_id, tag_id, user_id) VALUES (%d, %d, %d)', [$id, $tag_map[$t], $uid]);
                }
            }
            foreach (array_values((array) self::pick($e, ['assets'], [])) as $pos => $a) {
                $aid = is_array($a) ? self::pick($a, ['id']) : $a;
                if ($aid !== null) {
                    self::remember($uid, $run, 'alink', $aid . '>e>' . $pos . '>' . $id, $id);
                }
            }
            // Skalenwerte: Verweis auf einen Textpunkt oder Zeitwert.
            $values = [];
            foreach ((array) $e as $k => $v) {
                if (is_array($v) && $v && is_array(reset($v)) && (array_key_exists('id_text_scale_value', reset($v)) || array_key_exists('time_value', reset($v))
                    || array_key_exists('id_scale', reset($v)) || array_key_exists('scale_id', reset($v)))) {
                    $values = $v;
                    break;
                }
            }
            foreach ($values as $v) {
                $sid = $scale_map[(string) self::pick($v, ['id_scale', 'scale_id', 'scaleId'], '')] ?? null;
                $psrc = self::pick($v, ['id_text_scale_value', 'text_value_id', 'idTextScaleValue']);
                $tv = self::pick($v, ['time_value', 'timeValue']);
                $nv = self::pick($v, ['number_value', 'num_value', 'value']);
                $row = ['entry_id' => $id, 'user_id' => $uid, 'point_id' => null, 'num_val' => null, 'minutes' => null];
                if ($psrc !== null && (int) $psrc > 0 && isset($point_map[(string) $psrc])) {
                    $row['point_id'] = $point_map[(string) $psrc];
                    $sid = $sid ?: (int) Db::val('SELECT scale_id FROM ' . Db::t('scale_points') . ' WHERE id = %d AND user_id = %d', [$row['point_id'], $uid]);
                } elseif ($sid && $tv !== null && ($scale_types[$sid] ?? '') === 'time') {
                    $row['minutes'] = (int) round((float) $tv / max(1, $time_divisor));
                } elseif ($sid && is_numeric($nv) && ($scale_types[$sid] ?? '') === 'num') {
                    $row['num_val'] = (float) $nv;
                } else {
                    continue;
                }
                if ($sid) {
                    $row['scale_id'] = $sid;
                    $wpdb->replace(Db::t('entry_scale_values'), $row);
                }
            }
            $r['created']++;
        }
        return $r;
    }

    /** Abgehakte Ziel-Tage. $month_base: 0 oder 1, je nachdem, ab wo der Monat in der Quelle zählt. */
    public static function goal_checks(int $uid, string $run, array $items, int $month_base = 1): array
    {
        Features::need('import', $uid);
        $run = self::run_key($run);
        $r = self::result();
        $goal_map = self::map_all($uid, $run, 'goal');
        $done = self::map_all($uid, $run, 'check');
        foreach ($items as $i => $c) {
            $src = self::pick((array) $c, ['id'], null);
            $gid = $goal_map[(string) self::pick($c, ['goalId', 'goal_id', 'id_goal'], '')] ?? null;
            $y = (int) self::pick($c, ['year'], 0);
            $d = (int) self::pick($c, ['day'], 0);
            $mo = self::pick($c, ['month']);
            if ($mo !== null && $y > 0) {
                $mo = (int) $mo + ($month_base === 0 ? 1 : 0);
                $date = checkdate($mo, $d, $y) ? sprintf('%04d-%02d-%02d', $y, $mo, $d) : null;
            } else {
                $ms = self::pick($c, ['createdAt', 'created_at', 'datetime']);
                $date = is_numeric($ms) ? gmdate('Y-m-d', (int) floor($ms / 1000) + (int) round((int) self::pick($c, ['timeZoneOffset', 'createdAtOffset'], 0) / 1000)) : null;
            }
            $src = $src ?? ($gid . ':' . $date);
            if (!$gid || !$date) { $r['errors']++; continue; }
            if (isset($done[(string) $src])) { $r['skipped']++; continue; }
            Db::run('INSERT IGNORE INTO ' . Db::t('goal_checks') . ' (user_id, goal_id, check_date) VALUES (%d, %d, %s)', [$uid, $gid, $date]);
            $new = (int) Db::val('SELECT id FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d AND goal_id = %d AND check_date = %s', [$uid, $gid, $date]);
            self::remember($uid, $run, 'check', $src, $new);
            $r['created']++;
        }
        return $r;
    }

    /** Ein Foto des Backups. Verknüpft es mit den Einträgen und wichtigen Tagen, die darauf verweisen. */
    public static function asset(int $uid, string $run, string $source_id, string $bytes, ?string $taken_at, string $checksum): array
    {
        Features::need('import', $uid);
        $run = self::run_key($run);
        $source_id = preg_replace('/[^a-zA-Z0-9_-]/', '', $source_id);
        if (self::mapped($uid, $run, 'asset', $source_id) !== null) {
            return ['skipped' => true];
        }
        $asset = Assets::store($uid, $bytes, $taken_at, preg_replace('/[^a-zA-Z0-9]/', '', $checksum) ?: null);
        self::remember($uid, $run, 'asset', $source_id, $asset['id']);
        global $wpdb;
        $links = Db::rows('SELECT source_id, new_id FROM ' . Db::t('import_map') . ' WHERE user_id = %d AND run_key = %s AND kind = %s AND source_id LIKE %s',
            [$uid, $run, 'alink', $wpdb->esc_like($source_id . '>') . '%']);
        $linked = 0;
        foreach ($links as $l) {
            $parts = explode('>', $l['source_id']);
            if (($parts[1] ?? '') === 'm') {
                Db::update('milestones', $uid, (int) $l['new_id'], ['asset_id' => $asset['id']]);
            } else {
                Db::run('INSERT IGNORE INTO ' . Db::t('entry_assets') . ' (entry_id, asset_id, user_id, position) VALUES (%d, %d, %d, %d)',
                    [(int) $l['new_id'], $asset['id'], $uid, (int) ($parts[2] ?? 0) + 1]);
            }
            $linked++;
        }
        return ['skipped' => false, 'id' => $asset['id'], 'linked' => $linked];
    }

    // ---- CSV ---------------------------------------------------------------

    /** Zerlegt eine Skalenangabe: „Name: Bezeichnung 2/5“ oder „Name: 6:00“. */
    public static function parse_scale(string $s): ?array
    {
        if (!preg_match('/^(.*?):\s*(.+)$/u', trim($s), $m)) {
            return null;
        }
        $name = trim($m[1]);
        $val = trim($m[2]);
        if (preg_match('/^(\d{1,3}):(\d{2})$/', $val, $t)) {
            return ['name' => $name, 'type' => 'time', 'minutes' => (int) $t[1] * 60 + (int) $t[2]];
        }
        if (preg_match('/^(.*?)\s*(\d+)\/(\d+)$/u', $val, $t)) {
            return ['name' => $name, 'type' => 'text', 'label' => trim($t[1]), 'pos' => (int) $t[2], 'of' => (int) $t[3]];
        }
        if (is_numeric(str_replace(',', '.', $val))) {
            return ['name' => $name, 'type' => 'num', 'num' => (float) str_replace(',', '.', $val)];
        }
        return null;
    }

    public static function parse_time(string $t): ?string
    {
        $t = trim($t);
        if (preg_match('/^(\d{1,2})[:.](\d{2})\s*([AaPp])\.?\s*[Mm]\.?$/', $t, $m)) {
            $h = (int) $m[1] % 12 + (strtolower($m[3]) === 'p' ? 12 : 0);
            return sprintf('%02d:%02d', $h, $m[2]);
        }
        if (preg_match('/^(\d{1,2})[:.](\d{2})/', $t, $m) && (int) $m[1] < 24) {
            return sprintf('%02d:%02d', $m[1], $m[2]);
        }
        return null;
    }

    private static function csv_split(string $s, string $sep): array
    {
        return array_values(array_filter(array_map('trim', explode($sep, $s)), fn($x) => $x !== ''));
    }

    /** Prüft CSV-Zeilen: Welche Stimmungen, Aktivitäten und Skalen fehlen? Angelegt wird nichts. */
    public static function csv_check(int $uid, array $rows): array
    {
        Features::need('import', $uid);
        $setup = Setup::all($uid);
        $have = ['moods' => [], 'tags' => [], 'scales' => []];
        foreach ($have as $k => $_) {
            foreach ($setup[$k] as $x) {
                $have[$k][In::norm($x['name'])] = true;
            }
        }
        $missing = ['moods' => [], 'tags' => [], 'scales' => []];
        $bad = 0;
        foreach ($rows as $row) {
            $date = substr((string) ($row['full_date'] ?? ''), 0, 10);
            if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date) || self::parse_time((string) ($row['time'] ?? '')) === null) {
                $bad++;
                continue;
            }
            $mood = trim((string) ($row['mood'] ?? ''));
            if ($mood !== '' && !isset($have['moods'][In::norm($mood)])) {
                $missing['moods'][$mood] = true;
            }
            foreach (self::csv_split((string) ($row['activities'] ?? ''), '|') as $a) {
                if (!isset($have['tags'][In::norm($a)])) {
                    $missing['tags'][$a] = true;
                }
            }
            foreach (self::csv_split((string) ($row['scales'] ?? ''), '|') as $s) {
                $p = self::parse_scale($s);
                if ($p && !isset($have['scales'][In::norm($p['name'])])) {
                    $missing['scales'][$p['name']] = true;
                }
            }
        }
        return ['rows' => count($rows), 'invalid' => $bad, 'missing' => array_map('array_keys', $missing)];
    }

    /**
     * Importiert CSV-Zeilen. $opts: create_missing (bool), mood_levels [Name => Stufe], offset (Zeilennummer des ersten Elements).
     * Fehlendes wird nur nach Rückfrage (create_missing) angelegt.
     */
    public static function csv(int $uid, string $run, array $rows, array $opts = []): array
    {
        Features::need('import', $uid);
        Entries::need_consent($uid);
        $run = self::run_key($run);
        $r = self::result();
        $create = !empty($opts['create_missing']);
        $levels = (array) ($opts['mood_levels'] ?? []);
        $offset = (int) ($opts['offset'] ?? 0);
        $setup = Setup::all($uid);
        $idx = ['moods' => [], 'tags' => [], 'scales' => []];
        foreach ($idx as $k => $_) {
            foreach ($setup[$k] as $x) {
                $idx[$k][In::norm($x['name'])] = $x;
            }
        }
        global $wpdb;
        foreach ($rows as $i => $row) {
            $src = 'csv' . ($offset + $i);
            if (self::mapped($uid, $run, 'entry', $src) !== null) { $r['skipped']++; continue; }
            $date = substr((string) ($row['full_date'] ?? ''), 0, 10);
            $time = self::parse_time((string) ($row['time'] ?? ''));
            if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date) || $time === null) { $r['errors']++; continue; }
            $mood_name = trim((string) ($row['mood'] ?? ''));
            $mood = $idx['moods'][In::norm($mood_name)] ?? null;
            if (!$mood && $create && $mood_name !== '') {
                $mood = Setup::mood_save($uid, ['name' => $mood_name, 'level' => max(1, min(5, (int) ($levels[$mood_name] ?? 3)))]);
                $idx['moods'][In::norm($mood_name)] = $mood;
            }
            if (!$mood) { $r['errors']++; continue; }
            $tag_ids = [];
            foreach (self::csv_split((string) ($row['activities'] ?? ''), '|') as $a) {
                $t = $idx['tags'][In::norm($a)] ?? null;
                if (!$t && $create) {
                    $t = Setup::tag_save($uid, ['name' => $a, 'icon' => self::icon_for($a)]);
                    $idx['tags'][In::norm($a)] = $t;
                }
                if ($t) {
                    $tag_ids[] = $t['id'];
                }
            }
            $offset_min = (int) round((new \DateTimeImmutable("$date $time", Settings::tz($uid)))->getOffset() / 60);
            $id = Db::insert('entries', $uid, ['ts_utc' => gmdate('Y-m-d H:i:s', strtotime("$date $time:00 UTC") - $offset_min * 60), 'tz_offset' => $offset_min,
                'local_date' => $date, 'local_time' => $time, 'mood_id' => $mood['id'], 'title' => In::text((string) ($row['note_title'] ?? ''), 255),
                'note' => In::note((string) ($row['note'] ?? '')), 'favorite' => 0, 'source' => 'import', 'created_at' => Db::now(), 'updated_at' => Db::now()]);
            self::remember($uid, $run, 'entry', $src, $id);
            foreach (array_unique($tag_ids) as $tid) {
                Db::run('INSERT IGNORE INTO ' . Db::t('entry_tags') . ' (entry_id, tag_id, user_id) VALUES (%d, %d, %d)', [$id, $tid, $uid]);
            }
            foreach (self::csv_split((string) ($row['scales'] ?? ''), '|') as $s) {
                $p = self::parse_scale($s);
                if (!$p) { continue; }
                $scale = $idx['scales'][In::norm($p['name'])] ?? null;
                if (!$scale && $create) {
                    $in = ['name' => $p['name'], 'type' => $p['type']];
                    if ($p['type'] === 'text') {
                        $in['points'] = array_map(fn($n) => $n === $p['pos'] && $p['label'] !== '' ? $p['label'] : "Punkt $n", range(1, max(2, min(10, $p['of']))));
                    }
                    $scale = Setup::scale_save($uid, $in);
                    $idx['scales'][In::norm($p['name'])] = $scale;
                }
                if (!$scale || $scale['type'] !== $p['type']) { continue; }
                $val = ['entry_id' => $id, 'scale_id' => $scale['id'], 'user_id' => $uid, 'point_id' => null, 'num_val' => null, 'minutes' => null];
                if ($p['type'] === 'text') {
                    $point = null;
                    foreach ($scale['points'] as $pt) {
                        if (In::norm($pt['label']) === In::norm($p['label']) || (!$point && $pt['position'] === $p['pos'])) { $point = $pt; }
                    }
                    if (!$point) { continue; }
                    if ($p['label'] !== '' && str_starts_with($point['label'], 'Punkt ') && $create) {
                        Db::update('scale_points', $uid, $point['id'], ['label' => In::text($p['label'], 100)]);
                        $idx['scales'][In::norm($p['name'])] = Setup::scale_get($uid, $scale['id']);
                    }
                    $val['point_id'] = $point['id'];
                } elseif ($p['type'] === 'time') {
                    $val['minutes'] = $p['minutes'];
                } else {
                    $val['num_val'] = $p['num'];
                }
                $wpdb->replace(Db::t('entry_scale_values'), $val);
            }
            $r['created']++;
        }
        return $r;
    }

    /** Zählwerte des Tagebuchs. Grundlage für Importbericht und Abnahme. */
    public static function counts(int $uid): array
    {
        $c = fn(string $sql) => (int) Db::val($sql, [$uid]);
        $levels = [1 => 0, 2 => 0, 3 => 0, 4 => 0, 5 => 0];
        foreach (Db::rows('SELECT m.level, COUNT(*) AS n FROM ' . Db::t('entries') . ' e JOIN ' . Db::t('moods') . ' m ON m.id = e.mood_id WHERE e.user_id = %d GROUP BY m.level', [$uid]) as $r) {
            $levels[(int) $r['level']] = (int) $r['n'];
        }
        return [
            'entries' => $c('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d'),
            'levels' => $levels,
            'with_note' => $c('SELECT COUNT(*) FROM ' . Db::t('entries') . " WHERE user_id = %d AND note IS NOT NULL AND note <> ''"),
            'with_title' => $c('SELECT COUNT(*) FROM ' . Db::t('entries') . " WHERE user_id = %d AND title <> ''"),
            'favorites' => $c('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d AND favorite = 1'),
            'assets' => $c('SELECT COUNT(*) FROM ' . Db::t('assets') . ' WHERE user_id = %d'),
            'asset_links' => $c('SELECT COUNT(*) FROM ' . Db::t('entry_assets') . ' WHERE user_id = %d'),
            'entries_with_photo' => $c('SELECT COUNT(DISTINCT entry_id) FROM ' . Db::t('entry_assets') . ' WHERE user_id = %d'),
            'moods' => $c('SELECT COUNT(*) FROM ' . Db::t('moods') . ' WHERE user_id = %d'),
            'tags' => $c('SELECT COUNT(*) FROM ' . Db::t('tags') . ' WHERE user_id = %d'),
            'tags_archived' => $c('SELECT COUNT(*) FROM ' . Db::t('tags') . ' WHERE user_id = %d AND archived_at IS NOT NULL'),
            'groups' => $c('SELECT COUNT(*) FROM ' . Db::t('groups') . ' WHERE user_id = %d'),
            'scales' => $c('SELECT COUNT(*) FROM ' . Db::t('scales') . ' WHERE user_id = %d'),
            'goals' => $c('SELECT COUNT(*) FROM ' . Db::t('goals') . ' WHERE user_id = %d'),
            'goals_archived' => $c('SELECT COUNT(*) FROM ' . Db::t('goals') . ' WHERE user_id = %d AND archived_at IS NOT NULL'),
            'goal_checks' => $c('SELECT COUNT(*) FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d'),
            'milestones' => $c('SELECT COUNT(*) FROM ' . Db::t('milestones') . ' WHERE user_id = %d'),
            'templates' => $c('SELECT COUNT(*) FROM ' . Db::t('templates') . ' WHERE user_id = %d'),
            'reminders' => $c('SELECT COUNT(*) FROM ' . Db::t('reminders') . ' WHERE user_id = %d'),
            'first_entry' => Db::val('SELECT MIN(local_date) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid]),
            'last_entry' => Db::val('SELECT MAX(local_date) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid]),
        ];
    }
}
