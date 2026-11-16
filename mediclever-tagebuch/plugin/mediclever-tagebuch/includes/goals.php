<?php
namespace MCT;

/** Ziele mit Serien und Erfolgsraten sowie wichtige Tage. */
final class Goals
{
    const LEVELS = [1, 7, 14, 30, 60, 100, 200, 365, 500, 1000];

    public static function out(array $r): array
    {
        return ['id' => (int) $r['id'], 'name' => $r['name'], 'tag_id' => $r['tag_id'] === null ? null : (int) $r['tag_id'],
            'freq_type' => $r['freq_type'], 'freq_count' => (int) $r['freq_count'],
            'freq_days' => $r['freq_days'] === '' ? [] : array_map('intval', explode(',', $r['freq_days'])),
            'reminder_time' => $r['reminder_time'], 'reminder_text' => $r['reminder_text'], 'start_date' => $r['start_date'],
            'end_date' => $r['end_date'], 'note' => (string) $r['note'], 'position' => (int) $r['position'], 'archived' => $r['archived_at'] !== null];
    }

    public static function save(int $uid, array $in, ?int $id = null): array
    {
        Features::need('goals', $uid);
        $data = [];
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = In::text($in['name'] ?? '', 150);
            if ($data['name'] === '') {
                throw new Fail('name_required', 'Bitte gib dem Ziel einen Namen.');
            }
        }
        if (array_key_exists('tag_id', $in)) {
            $tid = $in['tag_id'] ? (int) $in['tag_id'] : null;
            if ($tid) {
                Db::own('tags', $uid, $tid);
            }
            $data['tag_id'] = $tid;
        }
        if (array_key_exists('freq_type', $in)) {
            $data['freq_type'] = in_array($in['freq_type'], ['daily', 'weekly', 'days'], true) ? $in['freq_type'] : 'daily';
        }
        if (array_key_exists('freq_count', $in)) {
            $data['freq_count'] = max(1, min(7, (int) $in['freq_count']));
        }
        if (array_key_exists('freq_days', $in)) {
            $days = array_values(array_unique(array_filter(array_map('intval', (array) $in['freq_days']), fn($d) => $d >= 1 && $d <= 7)));
            sort($days);
            $data['freq_days'] = implode(',', $days);
        }
        if (($data['freq_type'] ?? '') === 'days' && ($data['freq_days'] ?? 'x') === '') {
            throw new Fail('days_required', 'Bitte wähle mindestens einen Wochentag.');
        }
        if (array_key_exists('reminder_time', $in)) {
            $data['reminder_time'] = $in['reminder_time'] ? In::time($in['reminder_time']) : null;
        }
        if (array_key_exists('reminder_text', $in)) {
            $data['reminder_text'] = In::text($in['reminder_text'], 255);
        }
        if (array_key_exists('start_date', $in) && $in['start_date']) {
            $data['start_date'] = In::date($in['start_date']);
        }
        if (array_key_exists('end_date', $in)) {
            $data['end_date'] = $in['end_date'] ? In::date($in['end_date']) : null;
        }
        if (array_key_exists('note', $in)) {
            $data['note'] = In::text($in['note'], 1000);
        }
        if ($id === null) {
            $data['start_date'] = $data['start_date'] ?? Settings::today($uid);
            $data['position'] = 1 + (int) Db::val('SELECT COALESCE(MAX(position),0) FROM ' . Db::t('goals') . ' WHERE user_id = %d', [$uid]);
            $id = Db::insert('goals', $uid, $data);
            Audit::log($uid, 'goal_create', $id);
        } else {
            Db::own('goals', $uid, $id);
            Db::update('goals', $uid, $id, $data);
        }
        return self::out(Db::own('goals', $uid, $id));
    }

    public static function delete(int $uid, int $id): void
    {
        Db::own('goals', $uid, $id);
        Db::delete('goal_checks', $uid, ['goal_id' => $id]);
        Db::delete('goals', $uid, ['id' => $id]);
        Audit::log($uid, 'goal_delete', $id);
    }

    public static function check(int $uid, int $goal_id, string $date, bool $done): void
    {
        Features::need('goals', $uid);
        Db::own('goals', $uid, $goal_id);
        $date = In::date($date);
        if ($done) {
            Db::run('INSERT IGNORE INTO ' . Db::t('goal_checks') . ' (user_id, goal_id, check_date) VALUES (%d, %d, %s)', [$uid, $goal_id, $date]);
        } else {
            Db::delete('goal_checks', $uid, ['goal_id' => $goal_id, 'check_date' => $date]);
        }
        Audit::log($uid, $done ? 'goal_check' : 'goal_uncheck', $goal_id);
    }

    /** Ziele mit verknüpfter Aktivität gelten als erreicht, sobald ein Eintrag des Tages die Aktivität enthält. */
    public static function auto_check(int $uid, int $entry_id, string $date): void
    {
        $goals = Db::rows('SELECT g.id FROM ' . Db::t('goals') . ' g JOIN ' . Db::t('entry_tags')
            . ' et ON et.tag_id = g.tag_id AND et.entry_id = %d WHERE g.user_id = %d AND g.archived_at IS NULL AND g.start_date <= %s AND (g.end_date IS NULL OR g.end_date >= %s)',
            [$entry_id, $uid, $date, $date]);
        foreach ($goals as $g) {
            Db::run('INSERT IGNORE INTO ' . Db::t('goal_checks') . ' (user_id, goal_id, check_date) VALUES (%d, %d, %s)', [$uid, (int) $g['id'], $date]);
        }
    }

    /** Ist das Ziel an diesem Datum fällig? ISO-Wochentag 1 = Montag. */
    public static function due(array $goal, string $date): bool
    {
        if ($date < $goal['start_date'] || ($goal['end_date'] && $date > $goal['end_date'])) {
            return false;
        }
        if ($goal['freq_type'] === 'days') {
            return in_array((int) date('N', strtotime($date)), $goal['freq_days'], true);
        }
        return true;
    }

    /** Alle Ziele mit Kennzahlen. $month liefert zusätzlich die abgehakten Tage des Monats. */
    public static function overview(int $uid, ?string $month = null, bool $with_archived = true): array
    {
        $today = Settings::today($uid);
        $rows = Db::rows('SELECT * FROM ' . Db::t('goals') . ' WHERE user_id = %d' . ($with_archived ? '' : ' AND archived_at IS NULL') . ' ORDER BY position, id', [$uid]);
        $checks = [];
        foreach (Db::rows('SELECT goal_id, check_date FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d ORDER BY check_date', [$uid]) as $c) {
            $checks[(int) $c['goal_id']][] = $c['check_date'];
        }
        $out = [];
        foreach ($rows as $r) {
            $g = self::out($r);
            $dates = $checks[$g['id']] ?? [];
            $g = array_merge($g, self::metrics($g, $dates, $today));
            if ($month) {
                $g['month_checks'] = array_values(array_filter($dates, fn($d) => str_starts_with($d, $month)));
            }
            $out[] = $g;
        }
        return $out;
    }

    public static function metrics(array $g, array $dates, string $today): array
    {
        $set = array_flip($dates);
        $total = count($dates);
        $level = 0;
        $next = null;
        foreach (self::LEVELS as $i => $n) {
            if ($total >= $n) {
                $level = $i + 1;
            } elseif ($next === null) {
                $next = $n;
            }
        }
        $per_month = [];
        foreach ($dates as $d) {
            $m = substr($d, 0, 7);
            $per_month[$m] = ($per_month[$m] ?? 0) + 1;
        }
        return ['total' => $total, 'streak' => self::streak($g, $set, $today), 'best_streak' => self::best_streak($g, $dates),
            'rate7' => self::rate($g, $set, $today, 7), 'rate30' => self::rate($g, $set, $today, 30),
            'done_today' => isset($set[$today]), 'due_today' => self::due($g, $today) && !$g['archived'],
            'level' => $level, 'next_level_at' => $next, 'per_month' => $per_month];
    }

    private static function week_key(string $date): string
    {
        return date('o-W', strtotime($date));
    }

    /** Laufende Serie. Der heutige Tag (bzw. die laufende Woche) bricht die Serie nicht, solange er noch offen ist. */
    public static function streak(array $g, array $set, string $today): int
    {
        if ($g['freq_type'] === 'weekly') {
            $weeks = [];
            foreach (array_keys($set) as $d) {
                $k = self::week_key($d);
                $weeks[$k] = ($weeks[$k] ?? 0) + 1;
            }
            $n = 0;
            $cursor = strtotime($today);
            if (($weeks[self::week_key($today)] ?? 0) >= $g['freq_count']) {
                $n++;
            }
            for ($i = 0; $i < 600; $i++) {
                $cursor = strtotime('-7 days', $cursor);
                if (($weeks[date('o-W', $cursor)] ?? 0) >= $g['freq_count']) {
                    $n++;
                } else {
                    break;
                }
            }
            return $n;
        }
        $n = 0;
        $cursor = strtotime($today);
        for ($i = 0; $i < 4000; $i++) {
            $d = date('Y-m-d', $cursor);
            if ($d < $g['start_date'] && !isset($set[$d])) {
                break;
            }
            if (isset($set[$d])) {
                $n++;
            } elseif (self::due($g, $d) && $d !== $today) {
                break;
            }
            $cursor = strtotime('-1 day', $cursor);
        }
        return $n;
    }

    private static function best_streak(array $g, array $dates): int
    {
        if (!$dates) {
            return 0;
        }
        if ($g['freq_type'] === 'weekly') {
            $weeks = [];
            foreach ($dates as $d) {
                $monday = date('Y-m-d', strtotime('monday this week', strtotime($d)));
                $weeks[$monday] = ($weeks[$monday] ?? 0) + 1;
            }
            ksort($weeks);
            $best = $run = 0;
            $prev = null;
            foreach ($weeks as $monday => $n) {
                if ($n < $g['freq_count']) {
                    $run = 0;
                    $prev = null;
                    continue;
                }
                $run = ($prev !== null && strtotime($monday) - strtotime($prev) <= 8 * 86400) ? $run + 1 : 1;
                $best = max($best, $run);
                $prev = $monday;
            }
            return $best;
        }
        $best = $run = 0;
        $set = array_flip($dates);
        $cursor = strtotime($dates[0]);
        $end = strtotime(end($dates));
        while ($cursor <= $end) {
            $d = date('Y-m-d', $cursor);
            if (isset($set[$d])) {
                $best = max($best, ++$run);
            } elseif ($g['freq_type'] !== 'days' || in_array((int) date('N', $cursor), $g['freq_days'], true)) {
                $run = 0;
            }
            $cursor = strtotime('+1 day', $cursor);
        }
        return $best;
    }

    /** Erfolgsrate der letzten $days Tage in Prozent. */
    public static function rate(array $g, array $set, string $today, int $days): int
    {
        $done = $due = 0;
        for ($i = 0; $i < $days; $i++) {
            $d = date('Y-m-d', strtotime("-$i days", strtotime($today)));
            if ($d < $g['start_date']) {
                break;
            }
            if (isset($set[$d])) {
                $done++;
            }
            if (self::due($g, $d)) {
                $due++;
            }
        }
        if ($g['freq_type'] === 'weekly') {
            $due = (int) ceil($due / 7 * $g['freq_count']);
        }
        return $due > 0 ? (int) min(100, round($done / $due * 100)) : 0;
    }
}

final class Milestones
{
    public static function out(array $r, string $today): array
    {
        $since = (int) round((strtotime($today) - strtotime($r['m_date'])) / 86400);
        $next = null;
        $until = null;
        $years = null;
        if ($r['yearly']) {
            [$y, $m, $d] = array_map('intval', explode('-', $r['m_date']));
            $ty = (int) substr($today, 0, 4);
            foreach ([$ty, $ty + 1] as $cand) {
                $dd = ($m === 2 && $d === 29 && !checkdate(2, 29, $cand)) ? 28 : $d;
                $date = sprintf('%04d-%02d-%02d', $cand, $m, $dd);
                if ($date >= $today && $date > $r['m_date']) {
                    $next = $date;
                    $years = $cand - $y;
                    break;
                }
            }
            if ($next) {
                $until = (int) round((strtotime($next) - strtotime($today)) / 86400);
            }
        } elseif ($r['m_date'] >= $today) {
            $next = $r['m_date'];
            $until = -$since;
        }
        return ['id' => (int) $r['id'], 'name' => $r['name'], 'date' => $r['m_date'], 'category' => $r['category'], 'note' => (string) $r['note'],
            'asset_id' => $r['asset_id'] === null ? null : (int) $r['asset_id'], 'yearly' => (bool) $r['yearly'],
            'remind_same_day' => (bool) $r['remind_same_day'], 'remind_days_before' => (int) $r['remind_days_before'],
            'days_since' => $since, 'next_date' => $next, 'days_until' => $until, 'next_years' => $years];
    }

    public static function all(int $uid): array
    {
        $today = Settings::today($uid);
        $out = array_map(fn($r) => self::out($r, $today), Db::rows('SELECT * FROM ' . Db::t('milestones') . ' WHERE user_id = %d ORDER BY m_date', [$uid]));
        usort($out, fn($a, $b) => ($a['days_until'] ?? 99999) <=> ($b['days_until'] ?? 99999));
        return $out;
    }

    public static function save(int $uid, array $in, ?int $id = null): array
    {
        Features::need('milestones', $uid);
        $data = [];
        if ($id === null || array_key_exists('name', $in)) {
            $data['name'] = In::text($in['name'] ?? '', 150);
            if ($data['name'] === '') {
                throw new Fail('name_required', 'Bitte gib dem Tag einen Namen.');
            }
        }
        if ($id === null || array_key_exists('date', $in)) {
            $data['m_date'] = In::date($in['date'] ?? '');
        }
        if (array_key_exists('category', $in)) {
            $data['category'] = In::text($in['category'], 60);
        }
        if (array_key_exists('note', $in)) {
            $data['note'] = In::text($in['note'], 2000);
        }
        if (array_key_exists('asset_id', $in)) {
            $aid = $in['asset_id'] ? (int) $in['asset_id'] : null;
            if ($aid) {
                Db::own('assets', $uid, $aid);
            }
            $data['asset_id'] = $aid;
        }
        foreach (['yearly', 'remind_same_day'] as $k) {
            if (array_key_exists($k, $in)) {
                $data[$k] = $in[$k] ? 1 : 0;
            }
        }
        if (array_key_exists('remind_days_before', $in)) {
            $data['remind_days_before'] = max(0, min(60, (int) $in['remind_days_before']));
        }
        if ($id === null) {
            $id = Db::insert('milestones', $uid, $data);
            Audit::log($uid, 'milestone_create', $id);
        } else {
            $old = Db::own('milestones', $uid, $id);
            Db::update('milestones', $uid, $id, $data);
            if (array_key_exists('asset_id', $data) && $old['asset_id'] && (int) $old['asset_id'] !== (int) $data['asset_id']) {
                Assets::delete_if_orphan($uid, (int) $old['asset_id']);
            }
        }
        return self::out(Db::own('milestones', $uid, $id), Settings::today($uid));
    }

    public static function delete(int $uid, int $id): void
    {
        $old = Db::own('milestones', $uid, $id);
        Db::delete('milestones', $uid, ['id' => $id]);
        if ($old['asset_id']) {
            Assets::delete_if_orphan($uid, (int) $old['asset_id']);
        }
        Audit::log($uid, 'milestone_delete', $id);
    }
}
