<?php
namespace MCT;

/** Einrichtung: Stimmungen, Gruppen, Aktivitäten, Skalen, Schreibvorlagen, Erinnerungen. */
final class Setup
{
    const MOOD_DEFAULTS = [1 => 'Sehr gut', 2 => 'Gut', 3 => 'Mittel', 4 => 'Schlecht', 5 => 'Sehr schlecht'];

    public static function all(int $uid): array
    {
        $points = [];
        foreach (Db::rows('SELECT id, scale_id, position, label FROM ' . Db::t('scale_points') . ' WHERE user_id = %d ORDER BY position, id', [$uid]) as $p) {
            $points[(int) $p['scale_id']][] = ['id' => (int) $p['id'], 'position' => (int) $p['position'], 'label' => $p['label']];
        }
        $scales = [];
        foreach (Db::rows('SELECT * FROM ' . Db::t('scales') . ' WHERE user_id = %d ORDER BY position, id', [$uid]) as $s) {
            $scales[] = self::scale_out($s, $points[(int) $s['id']] ?? []);
        }
        return [
            'moods' => array_map([self::class, 'mood_out'], Db::rows('SELECT * FROM ' . Db::t('moods') . ' WHERE user_id = %d ORDER BY level, position, id', [$uid])),
            'groups' => array_map([self::class, 'group_out'], Db::rows('SELECT * FROM ' . Db::t('groups') . ' WHERE user_id = %d ORDER BY position, id', [$uid])),
            'tags' => array_map([self::class, 'tag_out'], Db::rows('SELECT * FROM ' . Db::t('tags') . ' WHERE user_id = %d ORDER BY position, id', [$uid])),
            'scales' => $scales,
            'templates' => array_map([self::class, 'template_out'], Db::rows('SELECT * FROM ' . Db::t('templates') . ' WHERE user_id = %d ORDER BY position, id', [$uid])),
            'reminders' => array_map([self::class, 'reminder_out'], Db::rows('SELECT * FROM ' . Db::t('reminders') . ' WHERE user_id = %d ORDER BY time, id', [$uid])),
        ];
    }

    public static function mood_out(array $r): array
    {
        return ['id' => (int) $r['id'], 'level' => (int) $r['level'], 'name' => $r['name'], 'icon' => $r['icon'],
            'position' => (int) $r['position'], 'archived' => $r['archived_at'] !== null];
    }

    public static function group_out(array $r): array
    {
        return ['id' => (int) $r['id'], 'name' => $r['name'], 'color' => $r['color'], 'position' => (int) $r['position'],
            'collapsed' => (bool) $r['collapsed'], 'archived' => $r['archived_at'] !== null];
    }

    public static function tag_out(array $r): array
    {
        return ['id' => (int) $r['id'], 'group_id' => $r['group_id'] === null ? null : (int) $r['group_id'], 'name' => $r['name'],
            'icon' => $r['icon'], 'color' => $r['color'], 'position' => (int) $r['position'], 'archived' => $r['archived_at'] !== null];
    }

    public static function scale_out(array $r, array $points): array
    {
        return ['id' => (int) $r['id'], 'type' => $r['type'], 'name' => $r['name'], 'color' => $r['color'], 'icon' => $r['icon'],
            'position' => (int) $r['position'], 'chart_mode' => $r['chart_mode'], 'trend' => $r['trend'],
            'min' => $r['min_val'] === null ? null : (float) $r['min_val'], 'max' => $r['max_val'] === null ? null : (float) $r['max_val'],
            'archived' => $r['archived_at'] !== null, 'points' => $points];
    }

    public static function template_out(array $r): array
    {
        return ['id' => (int) $r['id'], 'title' => $r['title'], 'body' => (string) $r['body'], 'position' => (int) $r['position']];
    }

    public static function reminder_out(array $r): array
    {
        return ['id' => (int) $r['id'], 'time' => $r['time'], 'text' => $r['text'], 'active' => (bool) $r['active']];
    }

    private static function next_pos(string $table, int $uid, string $extra = ''): int
    {
        return 1 + (int) Db::val('SELECT COALESCE(MAX(position),0) FROM ' . Db::t($table) . ' WHERE user_id = %d' . $extra, [$uid]);
    }

    private static function name_required(array $in, int $max = 100): string
    {
        $name = In::text($in['name'] ?? '', $max);
        if ($name === '') {
            throw new Fail('name_required', 'Bitte gib einen Namen ein.');
        }
        return $name;
    }

    // ---- Stimmungen -------------------------------------------------------

    public static function mood_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = self::name_required($in);
        }
        if ($id === null || array_key_exists('level', $in)) {
            $level = (int) ($in['level'] ?? 0);
            if ($level < 1 || $level > 5) {
                throw new Fail('bad_level', 'Die Stufe muss zwischen 1 und 5 liegen.');
            }
            $data['level'] = $level;
        }
        if (array_key_exists('icon', $in)) {
            $data['icon'] = sanitize_key($in['icon']);
        }
        if ($id === null) {
            $data['icon'] = $data['icon'] ?? ('m' . $data['level']);
            $data['position'] = self::next_pos('moods', $uid);
            $id = Db::insert('moods', $uid, $data);
            Audit::log($uid, 'mood_create', $id);
        } else {
            Db::own('moods', $uid, $id);
            Db::update('moods', $uid, $id, $data);
        }
        return self::mood_out(Db::own('moods', $uid, $id));
    }

    public static function usage(int $uid, string $kind, int $id): int
    {
        switch ($kind) {
            case 'moods':
                return (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d AND mood_id = %d', [$uid, $id]);
            case 'tags':
                return (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entry_tags') . ' WHERE user_id = %d AND tag_id = %d', [$uid, $id]);
            case 'scales':
                return (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entry_scale_values') . ' WHERE user_id = %d AND scale_id = %d', [$uid, $id]);
            case 'groups':
                return (int) Db::val('SELECT COUNT(DISTINCT et.entry_id) FROM ' . Db::t('entry_tags') . ' et JOIN ' . Db::t('tags')
                    . ' t ON t.id = et.tag_id WHERE et.user_id = %d AND t.group_id = %d', [$uid, $id]);
        }
        return 0;
    }

    public static function archive(int $uid, string $kind, int $id, bool $archived): void
    {
        if (!in_array($kind, ['moods', 'groups', 'tags', 'scales', 'goals'], true)) {
            throw new Fail('bad_kind', 'Unbekannter Baustein.');
        }
        Db::own($kind, $uid, $id);
        if ($kind === 'moods' && $archived) {
            $row = Db::own('moods', $uid, $id);
            $left = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('moods') . ' WHERE user_id = %d AND level = %d AND archived_at IS NULL AND id <> %d',
                [$uid, $row['level'], $id]);
            if ($left === 0) {
                throw new Fail('last_mood', 'Jede der fünf Stufen braucht mindestens eine aktive Stimmung.', 409);
            }
        }
        Db::update($kind, $uid, $id, ['archived_at' => $archived ? Db::now() : null]);
        Audit::log($uid, ($archived ? 'archive_' : 'restore_') . $kind, $id);
    }

    public static function mood_delete(int $uid, int $id, ?int $replace_with = null): void
    {
        $row = Db::own('moods', $uid, $id);
        $left = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('moods') . ' WHERE user_id = %d AND level = %d AND id <> %d AND archived_at IS NULL', [$uid, $row['level'], $id]);
        if ($left === 0 && $row['archived_at'] === null) {
            throw new Fail('last_mood', 'Jede der fünf Stufen braucht mindestens eine aktive Stimmung.', 409);
        }
        $used = self::usage($uid, 'moods', $id);
        if ($used > 0) {
            if (!$replace_with || $replace_with === $id) {
                throw new Fail('in_use', sprintf('Diese Stimmung steckt in %d Einträgen. Wähle eine Ersatz-Stimmung oder archiviere sie.', $used), 409, ['count' => $used]);
            }
            Db::own('moods', $uid, $replace_with);
            Db::run('UPDATE ' . Db::t('entries') . ' SET mood_id = %d WHERE user_id = %d AND mood_id = %d', [$replace_with, $uid, $id]);
        }
        Db::delete('moods', $uid, ['id' => $id]);
        Audit::log($uid, 'mood_delete', $id);
    }

    // ---- Gruppen ----------------------------------------------------------

    public static function group_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = self::name_required($in);
        }
        if (array_key_exists('color', $in)) {
            $data['color'] = In::color($in['color']);
        }
        if (array_key_exists('collapsed', $in)) {
            $data['collapsed'] = $in['collapsed'] ? 1 : 0;
        }
        if ($id === null) {
            $data['color'] = $data['color'] ?? 'c1';
            $data['position'] = self::shared_next_pos($uid);
            $id = Db::insert('groups', $uid, $data);
            Audit::log($uid, 'group_create', $id);
        } else {
            Db::own('groups', $uid, $id);
            Db::update('groups', $uid, $id, $data);
        }
        return self::group_out(Db::own('groups', $uid, $id));
    }

    /** Gruppen und Skalen teilen sich eine gemeinsame Reihenfolge. */
    private static function shared_next_pos(int $uid): int
    {
        return max(self::next_pos('groups', $uid), self::next_pos('scales', $uid));
    }

    public static function group_delete(int $uid, int $id): void
    {
        Db::own('groups', $uid, $id);
        Db::run('UPDATE ' . Db::t('tags') . ' SET group_id = NULL WHERE user_id = %d AND group_id = %d', [$uid, $id]);
        Db::delete('groups', $uid, ['id' => $id]);
        Audit::log($uid, 'group_delete', $id);
    }

    // ---- Aktivitäten ------------------------------------------------------

    public static function tag_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = self::name_required($in);
        }
        if (array_key_exists('group_id', $in)) {
            $gid = $in['group_id'] ? (int) $in['group_id'] : null;
            if ($gid) {
                Db::own('groups', $uid, $gid);
            }
            $data['group_id'] = $gid;
        }
        if (array_key_exists('icon', $in)) {
            $data['icon'] = sanitize_key($in['icon']);
        }
        if (array_key_exists('color', $in)) {
            $data['color'] = In::color($in['color']);
        }
        if ($id === null) {
            $data['icon'] = ($data['icon'] ?? '') ?: 'circle';
            $data['position'] = self::next_pos('tags', $uid);
            $id = Db::insert('tags', $uid, $data);
            Audit::log($uid, 'tag_create', $id);
        } else {
            Db::own('tags', $uid, $id);
            Db::update('tags', $uid, $id, $data);
        }
        return self::tag_out(Db::own('tags', $uid, $id));
    }

    public static function tag_delete(int $uid, int $id): void
    {
        Db::own('tags', $uid, $id);
        Db::delete('entry_tags', $uid, ['tag_id' => $id]);
        Db::run('UPDATE ' . Db::t('goals') . ' SET tag_id = NULL WHERE user_id = %d AND tag_id = %d', [$uid, $id]);
        Db::delete('tags', $uid, ['id' => $id]);
        Audit::log($uid, 'tag_delete', $id);
    }

    /** Ersetzen: führt Aktivität $from in $into zusammen. */
    public static function tag_merge(int $uid, int $from, int $into): void
    {
        if ($from === $into) {
            throw new Fail('same_tag', 'Bitte wähle eine andere Aktivität als Ziel.');
        }
        Db::own('tags', $uid, $from);
        Db::own('tags', $uid, $into);
        Db::run('INSERT IGNORE INTO ' . Db::t('entry_tags') . ' (entry_id, tag_id, user_id) SELECT entry_id, %d, user_id FROM '
            . Db::t('entry_tags') . ' WHERE user_id = %d AND tag_id = %d', [$into, $uid, $from]);
        Db::delete('entry_tags', $uid, ['tag_id' => $from]);
        Db::run('UPDATE ' . Db::t('goals') . ' SET tag_id = %d WHERE user_id = %d AND tag_id = %d', [$into, $uid, $from]);
        Db::delete('tags', $uid, ['id' => $from]);
        Audit::log($uid, 'tag_merge', $into);
    }

    // ---- Skalen -----------------------------------------------------------

    public static function scale_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        $existing = $id ? Db::own('scales', $uid, $id) : null;
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = self::name_required($in);
        }
        if ($id === null) {
            $type = (string) ($in['type'] ?? '');
            if (!in_array($type, ['num', 'text', 'time'], true)) {
                throw new Fail('bad_type', 'Der Skalentyp muss Zahl, Text oder Zeit sein.');
            }
            $data['type'] = $type;
        }
        $type = $existing['type'] ?? $data['type'];
        foreach (['color' => fn($v) => In::color($v), 'icon' => fn($v) => sanitize_key($v)] as $k => $fn) {
            if (array_key_exists($k, $in)) {
                $data[$k] = $fn($in[$k]);
            }
        }
        if (array_key_exists('chart_mode', $in)) {
            $data['chart_mode'] = $in['chart_mode'] === 'sum' ? 'sum' : 'avg';
        }
        if (array_key_exists('trend', $in)) {
            $data['trend'] = $in['trend'] === 'down' ? 'down' : 'up';
        }
        if ($type !== 'text') {
            foreach (['min' => 'min_val', 'max' => 'max_val'] as $k => $col) {
                if (array_key_exists($k, $in)) {
                    $data[$col] = $in[$k] === null || $in[$k] === '' ? null : (float) $in[$k];
                }
            }
            if ($id === null && $type === 'num') {
                $data['min_val'] = $data['min_val'] ?? 0;
                $data['max_val'] = $data['max_val'] ?? 10;
            }
            $min = $data['min_val'] ?? ($existing['min_val'] ?? null);
            $max = $data['max_val'] ?? ($existing['max_val'] ?? null);
            if ($min !== null && $max !== null && (float) $max <= (float) $min) {
                throw new Fail('bad_range', 'Der höchste Wert muss über dem niedrigsten liegen.');
            }
        }
        if ($id === null) {
            $data['color'] = ($data['color'] ?? '') ?: 'c2';
            $data['icon'] = ($data['icon'] ?? '') ?: 'gauge';
            $data['position'] = self::shared_next_pos($uid);
            $id = Db::insert('scales', $uid, $data);
            Audit::log($uid, 'scale_create', $id);
        } else {
            Db::update('scales', $uid, $id, $data);
        }
        if ($type === 'text' && array_key_exists('points', $in)) {
            self::scale_points_save($uid, $id, (array) $in['points']);
        }
        return self::scale_get($uid, $id);
    }

    private static function scale_points_save(int $uid, int $scale_id, array $points): void
    {
        $labels = [];
        foreach ($points as $p) {
            $label = In::text(is_array($p) ? ($p['label'] ?? '') : $p, 100);
            if ($label !== '') {
                $labels[] = ['id' => is_array($p) && !empty($p['id']) ? (int) $p['id'] : 0, 'label' => $label];
            }
        }
        if (count($labels) < 2 || count($labels) > 10) {
            throw new Fail('bad_points', 'Eine Textskala braucht zwischen 2 und 10 benannte Punkte.');
        }
        $existing = array_map('intval', Db::col('SELECT id FROM ' . Db::t('scale_points') . ' WHERE user_id = %d AND scale_id = %d', [$uid, $scale_id]));
        $keep = [];
        foreach ($labels as $i => $p) {
            if ($p['id'] && in_array($p['id'], $existing, true)) {
                Db::update('scale_points', $uid, $p['id'], ['label' => $p['label'], 'position' => $i + 1]);
                $keep[] = $p['id'];
            } else {
                $keep[] = Db::insert('scale_points', $uid, ['scale_id' => $scale_id, 'label' => $p['label'], 'position' => $i + 1]);
            }
        }
        foreach (array_diff($existing, $keep) as $gone) {
            Db::delete('entry_scale_values', $uid, ['scale_id' => $scale_id, 'point_id' => $gone]);
            Db::delete('scale_points', $uid, ['id' => $gone]);
        }
    }

    public static function scale_get(int $uid, int $id): array
    {
        $row = Db::own('scales', $uid, $id);
        $points = array_map(fn($p) => ['id' => (int) $p['id'], 'position' => (int) $p['position'], 'label' => $p['label']],
            Db::rows('SELECT id, position, label FROM ' . Db::t('scale_points') . ' WHERE user_id = %d AND scale_id = %d ORDER BY position, id', [$uid, $id]));
        return self::scale_out($row, $points);
    }

    public static function scale_delete(int $uid, int $id): void
    {
        Db::own('scales', $uid, $id);
        Db::delete('entry_scale_values', $uid, ['scale_id' => $id]);
        Db::delete('scale_points', $uid, ['scale_id' => $id]);
        Db::delete('scales', $uid, ['id' => $id]);
        Audit::log($uid, 'scale_delete', $id);
    }

    // ---- Reihenfolge ------------------------------------------------------

    /** Setzt die Reihenfolge innerhalb einer Tabelle. Bei Aktivitäten optional mit Gruppenwechsel. */
    public static function reorder(int $uid, string $kind, array $ids, $group_id = false): void
    {
        if (!in_array($kind, ['moods', 'tags', 'templates', 'goals'], true)) {
            throw new Fail('bad_kind', 'Unbekannter Baustein.');
        }
        $own = array_map('intval', Db::col('SELECT id FROM ' . Db::t($kind) . ' WHERE user_id = %d', [$uid]));
        $pos = 0;
        foreach ($ids as $id) {
            $id = (int) $id;
            if (!in_array($id, $own, true)) {
                continue;
            }
            $data = ['position' => ++$pos];
            if ($kind === 'tags' && $group_id !== false) {
                if ($group_id) {
                    Db::own('groups', $uid, (int) $group_id);
                }
                $data['group_id'] = $group_id ? (int) $group_id : null;
            }
            Db::update($kind, $uid, $id, $data);
        }
    }

    /** Gemeinsame Reihenfolge von Gruppen und Skalen: [{kind: group|scale, id}]. */
    public static function reorder_shared(int $uid, array $items): void
    {
        $pos = 0;
        foreach ($items as $it) {
            $kind = ($it['kind'] ?? '') === 'scale' ? 'scales' : 'groups';
            $id = (int) ($it['id'] ?? 0);
            if (Db::val('SELECT id FROM ' . Db::t($kind) . ' WHERE id = %d AND user_id = %d', [$id, $uid])) {
                Db::update($kind, $uid, $id, ['position' => ++$pos]);
            }
        }
    }

    // ---- Schreibvorlagen und Erinnerungen ---------------------------------

    const DEFAULT_TEMPLATES = [
        ['Tagesrückblick', '<p><b>Was lief heute gut?</b></p><p><br></p><p><b>Was war anstrengend?</b></p><p><br></p><p><b>Was nehme ich mir für morgen vor?</b></p>'],
        ['Dankbarkeit', '<p>Drei Dinge, für die ich heute dankbar bin:</p><ol><li></li><li></li><li></li></ol>'],
        ['Nach dem Dienst', '<p><b>Wie war die Schicht?</b></p><p><br></p><p><b>Was beschäftigt mich noch?</b></p><p><br></p><p><b>Was tut mir jetzt gut?</b></p>'],
    ];

    public static function template_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        if ($id === null || array_key_exists('title', $in)) {
            $data['title'] = In::text($in['title'] ?? '', 150);
            if ($data['title'] === '') {
                throw new Fail('name_required', 'Bitte gib einen Titel ein.');
            }
        }
        if (array_key_exists('body', $in)) {
            $data['body'] = In::note($in['body']);
        }
        if ($id === null) {
            $data['position'] = self::next_pos('templates', $uid);
            $id = Db::insert('templates', $uid, $data);
        } else {
            Db::own('templates', $uid, $id);
            Db::update('templates', $uid, $id, $data);
        }
        return self::template_out(Db::own('templates', $uid, $id));
    }

    public static function template_delete(int $uid, int $id): void
    {
        Db::own('templates', $uid, $id);
        Db::delete('templates', $uid, ['id' => $id]);
    }

    public static function templates_reset(int $uid): void
    {
        Db::delete('templates', $uid, []);
        foreach (self::DEFAULT_TEMPLATES as $i => $t) {
            Db::insert('templates', $uid, ['title' => $t[0], 'body' => $t[1], 'position' => $i + 1]);
        }
    }

    public static function reminder_save(int $uid, array $in, ?int $id = null): array
    {
        $data = [];
        if ($id === null || array_key_exists('time', $in)) {
            $data['time'] = In::time($in['time'] ?? '');
        }
        if (array_key_exists('text', $in)) {
            $data['text'] = In::text($in['text'], 255);
        }
        if (array_key_exists('active', $in)) {
            $data['active'] = $in['active'] ? 1 : 0;
        }
        if ($id === null) {
            $id = Db::insert('reminders', $uid, $data);
        } else {
            Db::own('reminders', $uid, $id);
            Db::update('reminders', $uid, $id, $data);
        }
        return self::reminder_out(Db::own('reminders', $uid, $id));
    }

    public static function reminder_delete(int $uid, int $id): void
    {
        Db::own('reminders', $uid, $id);
        Db::delete('reminders', $uid, ['id' => $id]);
    }

    // ---- Erster Start -----------------------------------------------------

    const PACKS = [
        'allgemein' => [
            'label' => 'Allgemein',
            'groups' => [
                ['Alltag', 'c1', [['Arbeit', 'briefcase'], ['Haushalt', 'home'], ['Einkaufen', 'shopping-cart'], ['Lernen', 'book-open']]],
                ['Menschen', 'c4', [['Familie', 'users'], ['Freunde', 'handshake'], ['Partnerschaft', 'heart'], ['Allein', 'user']]],
                ['Freizeit', 'c6', [['Sport', 'dumbbell'], ['Spazieren', 'footprints'], ['Lesen', 'book'], ['Musik', 'music'], ['Serien', 'tv'], ['Spielen', 'gamepad-2']]],
                ['Gesundheit', 'c8', [['Gut gegessen', 'utensils'], ['Viel getrunken', 'glass-water'], ['Krank', 'thermometer'], ['Entspannung', 'leaf']]],
            ],
            'scales' => [
                ['text', 'Schlafqualität', 'moon', 'c9', 'up', ['Schlecht', 'Mäßig', 'Gut', 'Sehr gut']],
                ['time', 'Schlafdauer', 'bed', 'c10', 'up', []],
                ['text', 'Stress', 'activity', 'c3', 'down', ['Kaum', 'Etwas', 'Deutlich', 'Sehr hoch']],
            ],
        ],
        'schichtdienst' => [
            'label' => 'Schichtdienst',
            'groups' => [
                ['Dienst', 'c1', [['Frühdienst', 'sunrise'], ['Spätdienst', 'sunset'], ['Nachtdienst', 'moon'], ['24-Stunden-Dienst', 'clock'], ['Frei', 'coffee'], ['Fortbildung', 'graduation-cap'], ['Überstunden', 'timer']]],
                ['Erholung', 'c6', [['Ausgeschlafen', 'bed'], ['Mittagsschlaf', 'cloud-moon'], ['Sport', 'dumbbell'], ['Draußen', 'trees'], ['Entspannung', 'leaf']]],
                ['Menschen', 'c4', [['Familie', 'users'], ['Freunde', 'handshake'], ['Kollegen', 'user-check'], ['Allein', 'user']]],
                ['Gesundheit', 'c8', [['Gut gegessen', 'utensils'], ['Viel getrunken', 'glass-water'], ['Kopfschmerzen', 'brain'], ['Krank', 'thermometer']]],
            ],
            'scales' => [
                ['text', 'Schlafqualität', 'moon', 'c9', 'up', ['Schlecht', 'Mäßig', 'Gut', 'Sehr gut']],
                ['time', 'Schlafdauer', 'bed', 'c10', 'up', []],
                ['text', 'Belastung im Dienst', 'activity', 'c3', 'down', ['Ruhig', 'Normal', 'Fordernd', 'Sehr belastend']],
                ['text', 'Energie', 'battery', 'c7', 'up', ['Leer', 'Niedrig', 'Okay', 'Voll']],
            ],
        ],
        'leer' => ['label' => 'Leer starten', 'groups' => [], 'scales' => []],
    ];

    /** Legt die fünf Stufen an, falls sie fehlen. Das Einzige, was fest vorgegeben ist. */
    public static function ensure_moods(int $uid): void
    {
        $have = array_map('intval', Db::col('SELECT DISTINCT level FROM ' . Db::t('moods') . ' WHERE user_id = %d', [$uid]));
        foreach (self::MOOD_DEFAULTS as $level => $name) {
            if (!in_array($level, $have, true)) {
                Db::insert('moods', $uid, ['level' => $level, 'name' => $name, 'icon' => 'm' . $level, 'position' => $level]);
            }
        }
    }

    public static function apply_pack(int $uid, string $pack): void
    {
        if (!isset(self::PACKS[$pack])) {
            throw new Fail('bad_pack', 'Dieses Startpaket gibt es nicht.');
        }
        self::ensure_moods($uid);
        $has = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('tags') . ' WHERE user_id = %d', [$uid])
            + (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('scales') . ' WHERE user_id = %d', [$uid]);
        if ($has === 0) {
            $pos = 0;
            $tpos = 0;
            foreach (self::PACKS[$pack]['groups'] as $g) {
                $gid = Db::insert('groups', $uid, ['name' => $g[0], 'color' => $g[1], 'position' => ++$pos]);
                foreach ($g[2] as $t) {
                    Db::insert('tags', $uid, ['group_id' => $gid, 'name' => $t[0], 'icon' => $t[1], 'color' => '', 'position' => ++$tpos]);
                }
            }
            foreach (self::PACKS[$pack]['scales'] as $s) {
                $sid = Db::insert('scales', $uid, ['type' => $s[0], 'name' => $s[1], 'icon' => $s[2], 'color' => $s[3], 'trend' => $s[4],
                    'chart_mode' => 'avg', 'position' => ++$pos, 'min_val' => $s[0] === 'time' ? 0 : null, 'max_val' => $s[0] === 'time' ? 720 : null]);
                foreach ($s[5] as $i => $label) {
                    Db::insert('scale_points', $uid, ['scale_id' => $sid, 'label' => $label, 'position' => $i + 1]);
                }
            }
        }
        if (!(int) Db::val('SELECT COUNT(*) FROM ' . Db::t('templates') . ' WHERE user_id = %d', [$uid])) {
            self::templates_reset($uid);
        }
        Settings::set_raw($uid, 'onboarded', true);
        Audit::log($uid, 'pack_' . $pack);
    }

    /** Unscharfe Zuordnung eines Namens. Liefert [Treffer-ID|null, Kandidaten]. Angelegt wird nichts. */
    public static function match(array $items, string $needle): array
    {
        $n = In::norm($needle);
        $exact = [];
        $near = [];
        foreach ($items as $it) {
            $name = In::norm($it['name']);
            if ($name === $n) {
                $exact[] = $it;
                continue;
            }
            $dist = levenshtein(substr($name, 0, 60), substr($n, 0, 60));
            $limit = max(2, (int) floor(strlen($n) / 3));
            if ($n !== '' && (str_contains($name, $n) || str_contains($n, $name) || $dist <= $limit)) {
                $near[] = ['item' => $it, 'dist' => $dist];
            }
        }
        $active = array_values(array_filter($exact, fn($i) => empty($i['archived'])));
        if (count($active) === 1) {
            return [$active[0], []];
        }
        if (count($exact) === 1) {
            return [$exact[0], []];
        }
        usort($near, fn($a, $b) => $a['dist'] <=> $b['dist']);
        $cands = array_merge($exact, array_column($near, 'item'));
        if (count($exact) === 0 && count($near) === 1 && $near[0]['dist'] <= 1) {
            return [$near[0]['item'], []];
        }
        return [null, array_slice(array_map(fn($i) => $i['name'], $cands), 0, 8)];
    }
}
