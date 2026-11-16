<?php
namespace MCT;

/** Einträge: anlegen, ändern, löschen, lesen, suchen. */
final class Entries
{
    /** Vor dem ersten Eintrag braucht es die ausdrückliche Einwilligung. */
    public static function need_consent(int $uid): void
    {
        if (empty(Settings::get($uid)['consent_at'])) {
            throw new Fail('consent_required', 'Bevor du Einträge speicherst, brauchen wir deine Einwilligung.', 403);
        }
    }

    public static function save(int $uid, array $in, ?int $id = null): array
    {
        self::need_consent($uid);
        $old = $id ? Db::own('entries', $uid, $id) : null;
        $data = [];

        if (!$old || array_key_exists('mood_id', $in)) {
            $mood = Db::row('SELECT id FROM ' . Db::t('moods') . ' WHERE id = %d AND user_id = %d', [(int) ($in['mood_id'] ?? 0), $uid]);
            if (!$mood) {
                throw new Fail('mood_required', 'Bitte wähle eine Stimmung.');
            }
            $data['mood_id'] = (int) $mood['id'];
        }
        $date = array_key_exists('date', $in) ? In::date($in['date']) : ($old['local_date'] ?? Settings::today($uid, true));
        $time = array_key_exists('time', $in) ? In::time($in['time']) : ($old['local_time'] ?? (new \DateTimeImmutable('now', Settings::tz($uid)))->format('H:i'));
        if (array_key_exists('tz_offset', $in) && $in['tz_offset'] !== null) {
            $offset = max(-840, min(840, (int) $in['tz_offset']));
        } elseif ($old && !array_key_exists('date', $in) && !array_key_exists('time', $in)) {
            $offset = (int) $old['tz_offset'];
        } else {
            $offset = (int) round((new \DateTimeImmutable("$date $time", Settings::tz($uid)))->getOffset() / 60);
        }
        $data['local_date'] = $date;
        $data['local_time'] = $time;
        $data['tz_offset'] = $offset;
        $data['ts_utc'] = gmdate('Y-m-d H:i:s', strtotime("$date $time:00 UTC") - $offset * 60);
        if (array_key_exists('ts_utc', $in) && $in['ts_utc']) {
            $data['ts_utc'] = gmdate('Y-m-d H:i:s', (int) strtotime($in['ts_utc'] . ' UTC'));
        }
        if (array_key_exists('title', $in)) {
            $data['title'] = In::text($in['title'], 255);
        }
        if (array_key_exists('note', $in)) {
            $data['note'] = In::note($in['note']);
        }
        if (array_key_exists('favorite', $in)) {
            $data['favorite'] = $in['favorite'] ? 1 : 0;
        }
        $data['updated_at'] = Db::now();

        if (!$old) {
            $idem = isset($in['idem_key']) ? substr(sanitize_text_field((string) $in['idem_key']), 0, 80) : null;
            if ($idem) {
                $dup = Db::val('SELECT id FROM ' . Db::t('entries') . ' WHERE user_id = %d AND idem_key = %s', [$uid, $idem]);
                if ($dup) {
                    $out = self::get($uid, (int) $dup);
                    $out['wiederholt'] = true;
                    return $out;
                }
                $data['idem_key'] = $idem;
            }
            $data['source'] = in_array($in['source'] ?? 'app', ['app', 'import', 'mcp'], true) ? ($in['source'] ?? 'app') : 'app';
            $data['created_at'] = Db::now();
            $data['title'] = $data['title'] ?? '';
            $data['note'] = $data['note'] ?? '';
            $id = Db::insert('entries', $uid, $data);
            Audit::log($uid, 'entry_create', $id);
        } else {
            Db::update('entries', $uid, $id, $data);
            Audit::log($uid, 'entry_update', $id);
        }

        if (array_key_exists('tag_ids', $in)) {
            self::set_tags($uid, $id, (array) $in['tag_ids']);
        }
        if (array_key_exists('scales', $in)) {
            self::set_scales($uid, $id, (array) $in['scales'], !empty($in['scales_merge']));
        }
        if (array_key_exists('asset_ids', $in)) {
            self::set_assets($uid, $id, (array) $in['asset_ids']);
        }
        Goals::auto_check($uid, $id, $date);
        return self::get($uid, $id);
    }

    private static function set_tags(int $uid, int $id, array $tag_ids): void
    {
        $own = array_map('intval', Db::col('SELECT id FROM ' . Db::t('tags') . ' WHERE user_id = %d AND id IN (' . Db::in($tag_ids) . ')', [$uid]));
        Db::delete('entry_tags', $uid, ['entry_id' => $id]);
        foreach ($own as $tid) {
            Db::run('INSERT IGNORE INTO ' . Db::t('entry_tags') . ' (entry_id, tag_id, user_id) VALUES (%d, %d, %d)', [$id, $tid, $uid]);
        }
    }

    private static function set_scales(int $uid, int $id, array $values, bool $merge): void
    {
        if (!$merge) {
            Db::delete('entry_scale_values', $uid, ['entry_id' => $id]);
        }
        foreach ($values as $v) {
            $sid = (int) ($v['scale_id'] ?? 0);
            $scale = Db::row('SELECT * FROM ' . Db::t('scales') . ' WHERE id = %d AND user_id = %d', [$sid, $uid]);
            if (!$scale) {
                continue;
            }
            $row = ['point_id' => null, 'num_val' => null, 'minutes' => null];
            if ($scale['type'] === 'text') {
                $pid = (int) ($v['point_id'] ?? 0);
                if (!Db::val('SELECT id FROM ' . Db::t('scale_points') . ' WHERE id = %d AND scale_id = %d AND user_id = %d', [$pid, $sid, $uid])) {
                    continue;
                }
                $row['point_id'] = $pid;
            } elseif ($scale['type'] === 'time') {
                if (!isset($v['minutes']) || $v['minutes'] === '' || $v['minutes'] === null) {
                    continue;
                }
                $row['minutes'] = max(0, min(100000, (int) $v['minutes']));
            } else {
                if (!isset($v['num']) || $v['num'] === '' || $v['num'] === null || !is_numeric($v['num'])) {
                    continue;
                }
                $num = (float) $v['num'];
                if ($scale['min_val'] !== null) {
                    $num = max((float) $scale['min_val'], $num);
                }
                if ($scale['max_val'] !== null) {
                    $num = min((float) $scale['max_val'], $num);
                }
                $row['num_val'] = $num;
            }
            Db::delete('entry_scale_values', $uid, ['entry_id' => $id, 'scale_id' => $sid]);
            global $wpdb;
            $wpdb->insert(Db::t('entry_scale_values'), array_merge($row, ['entry_id' => $id, 'scale_id' => $sid, 'user_id' => $uid]));
        }
    }

    private static function set_assets(int $uid, int $id, array $asset_ids): void
    {
        $own = array_map('intval', Db::col('SELECT id FROM ' . Db::t('assets') . ' WHERE user_id = %d AND id IN (' . Db::in($asset_ids) . ')', [$uid]));
        $before = array_map('intval', Db::col('SELECT asset_id FROM ' . Db::t('entry_assets') . ' WHERE user_id = %d AND entry_id = %d', [$uid, $id]));
        Db::delete('entry_assets', $uid, ['entry_id' => $id]);
        $pos = 0;
        foreach ($asset_ids as $aid) {
            $aid = (int) $aid;
            if (in_array($aid, $own, true)) {
                Db::run('INSERT IGNORE INTO ' . Db::t('entry_assets') . ' (entry_id, asset_id, user_id, position) VALUES (%d, %d, %d, %d)', [$id, $aid, $uid, ++$pos]);
            }
        }
        foreach (array_diff($before, array_map('intval', $asset_ids)) as $gone) {
            Assets::delete_if_orphan($uid, $gone);
        }
    }

    public static function delete(int $uid, int $id): void
    {
        Db::own('entries', $uid, $id);
        $assets = array_map('intval', Db::col('SELECT asset_id FROM ' . Db::t('entry_assets') . ' WHERE user_id = %d AND entry_id = %d', [$uid, $id]));
        Db::delete('entry_tags', $uid, ['entry_id' => $id]);
        Db::delete('entry_scale_values', $uid, ['entry_id' => $id]);
        Db::delete('entry_assets', $uid, ['entry_id' => $id]);
        Db::delete('entries', $uid, ['id' => $id]);
        foreach ($assets as $aid) {
            Assets::delete_if_orphan($uid, $aid);
        }
        Audit::log($uid, 'entry_delete', $id);
    }

    public static function get(int $uid, int $id): array
    {
        return self::hydrate($uid, [Db::own('entries', $uid, $id)])[0];
    }

    /** Ergänzt Aktivitäten, Skalenwerte und Dateien zu einer Liste von Eintragszeilen. */
    public static function hydrate(int $uid, array $rows, bool $full = true): array
    {
        if (!$rows) {
            return [];
        }
        $ids = Db::in(array_column($rows, 'id'));
        $levels = [];
        foreach (Db::rows('SELECT id, level FROM ' . Db::t('moods') . ' WHERE user_id = %d', [$uid]) as $m) {
            $levels[(int) $m['id']] = (int) $m['level'];
        }
        $tags = $scales = $assets = [];
        foreach (Db::rows('SELECT et.entry_id, et.tag_id FROM ' . Db::t('entry_tags') . ' et JOIN ' . Db::t('tags')
            . " t ON t.id = et.tag_id WHERE et.user_id = %d AND et.entry_id IN ($ids) ORDER BY t.position, t.id", [$uid]) as $r) {
            $tags[(int) $r['entry_id']][] = (int) $r['tag_id'];
        }
        foreach (Db::rows('SELECT * FROM ' . Db::t('entry_scale_values') . " WHERE user_id = %d AND entry_id IN ($ids)", [$uid]) as $r) {
            $scales[(int) $r['entry_id']][] = ['scale_id' => (int) $r['scale_id'], 'point_id' => $r['point_id'] === null ? null : (int) $r['point_id'],
                'num' => $r['num_val'] === null ? null : (float) $r['num_val'], 'minutes' => $r['minutes'] === null ? null : (int) $r['minutes']];
        }
        foreach (Db::rows('SELECT ea.entry_id, a.id, a.type FROM ' . Db::t('entry_assets') . ' ea JOIN ' . Db::t('assets')
            . " a ON a.id = ea.asset_id WHERE ea.user_id = %d AND ea.entry_id IN ($ids) ORDER BY ea.position", [$uid]) as $r) {
            $assets[(int) $r['entry_id']][] = ['id' => (int) $r['id'], 'type' => $r['type']];
        }
        $out = [];
        foreach ($rows as $r) {
            $id = (int) $r['id'];
            $e = ['id' => $id, 'date' => $r['local_date'], 'time' => $r['local_time'], 'ts_utc' => $r['ts_utc'], 'tz_offset' => (int) $r['tz_offset'],
                'mood_id' => (int) $r['mood_id'], 'level' => $levels[(int) $r['mood_id']] ?? 3, 'title' => $r['title'],
                'favorite' => (bool) $r['favorite'], 'source' => $r['source'], 'tags' => $tags[$id] ?? [], 'scales' => $scales[$id] ?? [],
                'assets' => $assets[$id] ?? []];
            $e['note'] = $full ? (string) $r['note'] : wp_trim_words(wp_strip_all_tags((string) $r['note']), 30, ' …');
            $out[] = $e;
        }
        return $out;
    }

    public static function month(int $uid, string $month): array
    {
        $rows = Db::rows('SELECT * FROM ' . Db::t('entries') . ' WHERE user_id = %d AND local_date BETWEEN %s AND %s ORDER BY local_date DESC, local_time DESC, id DESC',
            array_merge([$uid], In::month_range($month)));
        return self::hydrate($uid, $rows);
    }

    public static function day(int $uid, string $date): array
    {
        return self::hydrate($uid, Db::rows('SELECT * FROM ' . Db::t('entries') . ' WHERE user_id = %d AND local_date = %s ORDER BY local_time, id', [$uid, $date]));
    }

    /** Suche nach Zeitraum, Text, Stimmung und Aktivität, seitenweise. */
    public static function search(int $uid, array $a): array
    {
        $where = ['e.user_id = %d'];
        $args = [$uid];
        if (!empty($a['from'])) {
            $where[] = 'e.local_date >= %s';
            $args[] = In::date($a['from']);
        }
        if (!empty($a['to'])) {
            $where[] = 'e.local_date <= %s';
            $args[] = In::date($a['to']);
        }
        if (isset($a['text']) && trim((string) $a['text']) !== '') {
            global $wpdb;
            $like = '%' . $wpdb->esc_like(trim((string) $a['text'])) . '%';
            $where[] = '(e.note LIKE %s OR e.title LIKE %s)';
            $args[] = $like;
            $args[] = $like;
        }
        if (!empty($a['mood_ids'])) {
            $where[] = 'e.mood_id IN (' . Db::in((array) $a['mood_ids']) . ')';
        }
        if (!empty($a['levels'])) {
            $where[] = 'e.mood_id IN (SELECT id FROM ' . Db::t('moods') . ' WHERE user_id = ' . (int) $uid . ' AND level IN (' . Db::in((array) $a['levels']) . '))';
        }
        foreach ((array) ($a['tag_ids'] ?? []) as $tid) {
            $where[] = 'EXISTS (SELECT 1 FROM ' . Db::t('entry_tags') . ' x WHERE x.entry_id = e.id AND x.tag_id = ' . (int) $tid . ' AND x.user_id = ' . (int) $uid . ')';
        }
        if (!empty($a['favorite'])) {
            $where[] = 'e.favorite = 1';
        }
        if (!empty($a['has_photo'])) {
            $where[] = 'EXISTS (SELECT 1 FROM ' . Db::t('entry_assets') . ' y WHERE y.entry_id = e.id AND y.user_id = ' . (int) $uid . ')';
        }
        $per = max(1, min(50, (int) ($a['per_page'] ?? 20)));
        $page = max(1, (int) ($a['page'] ?? 1));
        $w = implode(' AND ', $where);
        $total = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . " e WHERE $w", $args);
        $rows = Db::rows('SELECT e.* FROM ' . Db::t('entries') . " e WHERE $w ORDER BY e.local_date DESC, e.local_time DESC, e.id DESC LIMIT %d OFFSET %d",
            array_merge($args, [$per, ($page - 1) * $per]));
        return ['total' => $total, 'page' => $page, 'pages' => (int) ceil($total / $per), 'entries' => self::hydrate($uid, $rows, !empty($a['full']))];
    }

    /** Monate mit Einträgen, für die Monatsauswahl. */
    public static function months(int $uid): array
    {
        return Db::rows("SELECT DATE_FORMAT(local_date, '%%Y-%%m') AS month, COUNT(*) AS n FROM " . Db::t('entries')
            . ' WHERE user_id = %d GROUP BY month ORDER BY month DESC', [$uid]);
    }
}
