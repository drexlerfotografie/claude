<?php
namespace MCT;

/**
 * Auswertungen. Alles rechnet der Server, damit App und MCP dieselben Zahlen liefern.
 * Stimmungswert: 5 = beste Stufe, 1 = schlechteste (also 6 minus Stufe).
 */
final class Stats
{
    const MIN_ENTRIES = 3;
    const WEEKDAYS = [1 => 'Montag', 2 => 'Dienstag', 3 => 'Mittwoch', 4 => 'Donnerstag', 5 => 'Freitag', 6 => 'Samstag', 7 => 'Sonntag'];

    public static function load(int $uid, string $from, string $to): array
    {
        $rows = Db::rows('SELECT e.id, e.local_date, e.local_time, e.mood_id, m.level, e.note FROM ' . Db::t('entries') . ' e LEFT JOIN ' . Db::t('moods')
            . ' m ON m.id = e.mood_id WHERE e.user_id = %d AND e.local_date BETWEEN %s AND %s ORDER BY e.local_date, e.local_time, e.id', [$uid, $from, $to]);
        if (!$rows) {
            return [];
        }
        $tags = [];
        foreach (Db::rows('SELECT et.entry_id, et.tag_id FROM ' . Db::t('entry_tags') . ' et JOIN ' . Db::t('entries')
            . ' e ON e.id = et.entry_id WHERE et.user_id = %d AND e.local_date BETWEEN %s AND %s', [$uid, $from, $to]) as $t) {
            $tags[(int) $t['entry_id']][] = (int) $t['tag_id'];
        }
        $out = [];
        foreach ($rows as $r) {
            $text = trim(wp_strip_all_tags(str_replace(['<br>', '</p>', '</li>', '</div>'], ' ', (string) $r['note'])));
            $out[] = ['id' => (int) $r['id'], 'date' => $r['local_date'], 'time' => $r['local_time'], 'mood_id' => (int) $r['mood_id'],
                'level' => (int) ($r['level'] ?: 3), 'score' => 6 - (int) ($r['level'] ?: 3), 'tags' => $tags[(int) $r['id']] ?? [],
                'words' => $text === '' ? 0 : count(preg_split('/\s+/u', $text))];
        }
        return $out;
    }

    private static function avg(array $v): ?float
    {
        return $v ? round(array_sum($v) / count($v), 2) : null;
    }

    /** Tageswerte: Durchschnitt der Einträge eines Tages. */
    public static function days(array $entries): array
    {
        $by = [];
        foreach ($entries as $e) {
            $by[$e['date']]['scores'][] = $e['score'];
            $by[$e['date']]['tags'] = array_values(array_unique(array_merge($by[$e['date']]['tags'] ?? [], $e['tags'])));
            $by[$e['date']]['moods'][] = $e['mood_id'];
        }
        $out = [];
        foreach ($by as $date => $d) {
            $out[] = ['date' => $date, 'avg' => self::avg($d['scores']), 'n' => count($d['scores']), 'tags' => $d['tags'], 'moods' => array_values(array_unique($d['moods']))];
        }
        return $out;
    }

    /** Tage in Folge: laufende und längste Reihe über alle Einträge. */
    public static function streaks(int $uid): array
    {
        $dates = Db::col('SELECT DISTINCT local_date FROM ' . Db::t('entries') . ' WHERE user_id = %d ORDER BY local_date', [$uid]);
        $best = $run = 0;
        $prev = null;
        foreach ($dates as $d) {
            $run = ($prev !== null && strtotime($d) - strtotime($prev) === 86400) ? $run + 1 : 1;
            $best = max($best, $run);
            $prev = $d;
        }
        $today = Settings::today($uid);
        $set = array_flip($dates);
        $cur = 0;
        $c = strtotime($today);
        if (!isset($set[$today])) {
            $c = strtotime('-1 day', $c);
        }
        while (isset($set[date('Y-m-d', $c)])) {
            $cur++;
            $c = strtotime('-1 day', $c);
        }
        $last5 = [];
        for ($i = 4; $i >= 0; $i--) {
            $d = date('Y-m-d', strtotime("-$i days", strtotime($today)));
            $last5[] = ['date' => $d, 'done' => isset($set[$d])];
        }
        return ['current' => $cur, 'longest' => $best, 'days_total' => count($dates), 'last5' => $last5];
    }

    /** Stabilität 0–100: 100 minus die mittlere Tagesschwankung, bezogen auf die größte mögliche Schwankung (4). */
    public static function stability(array $days): ?int
    {
        if (count($days) < 2) {
            return null;
        }
        $diffs = [];
        for ($i = 1; $i < count($days); $i++) {
            $diffs[] = abs($days[$i]['avg'] - $days[$i - 1]['avg']);
        }
        return (int) round(100 * (1 - (array_sum($diffs) / count($diffs)) / 4));
    }

    /** Einfluss einer Aktivität: Durchschnitt der Einträge mit ihr minus Durchschnitt der Einträge ohne sie. */
    public static function influence(array $entries, int $min = 2): array
    {
        $with = [];
        foreach ($entries as $e) {
            foreach ($e['tags'] as $t) {
                $with[$t][] = $e['score'];
            }
        }
        $total = array_sum(array_column($entries, 'score'));
        $n = count($entries);
        $out = [];
        foreach ($with as $tag => $scores) {
            $k = count($scores);
            if ($k < $min || $k === $n) {
                continue;
            }
            $delta = array_sum($scores) / $k - ($total - array_sum($scores)) / ($n - $k);
            $out[] = ['tag_id' => $tag, 'delta' => round($delta, 2), 'n' => $k, 'avg' => round(array_sum($scores) / $k, 2)];
        }
        usort($out, fn($a, $b) => $b['delta'] <=> $a['delta']);
        return $out;
    }

    /** Längste Serie guter Tage (Tagesdurchschnitt ab 4) mit den Aktivitäten dieser Tage. */
    public static function good_streak(array $days): array
    {
        $best = ['length' => 0, 'from' => null, 'to' => null, 'tags' => []];
        $run = [];
        $flush = function () use (&$run, &$best) {
            if (count($run) > $best['length']) {
                $counts = [];
                foreach ($run as $d) {
                    foreach ($d['tags'] as $t) {
                        $counts[$t] = ($counts[$t] ?? 0) + 1;
                    }
                }
                arsort($counts);
                $best = ['length' => count($run), 'from' => $run[0]['date'], 'to' => end($run)['date'], 'tags' => array_slice(array_keys($counts), 0, 6)];
            }
            $run = [];
        };
        $prev = null;
        foreach ($days as $d) {
            $consecutive = $prev !== null && strtotime($d['date']) - strtotime($prev) === 86400;
            if ($d['avg'] >= 4 && ($consecutive || !$run)) {
                $run[] = $d;
            } else {
                $flush();
                if ($d['avg'] >= 4) {
                    $run[] = $d;
                }
            }
            $prev = $d['date'];
        }
        $flush();
        return $best;
    }

    /** Skalenwerte je Tag, zusammengefasst nach der Anzeige der Skala (Durchschnitt oder Summe). */
    public static function scale_series(int $uid, string $from, string $to): array
    {
        $rows = Db::rows('SELECT v.scale_id, e.local_date, v.num_val, v.minutes, p.position FROM ' . Db::t('entry_scale_values') . ' v JOIN ' . Db::t('entries')
            . ' e ON e.id = v.entry_id LEFT JOIN ' . Db::t('scale_points') . ' p ON p.id = v.point_id WHERE v.user_id = %d AND e.local_date BETWEEN %s AND %s ORDER BY e.local_date',
            [$uid, $from, $to]);
        $modes = [];
        foreach (Db::rows('SELECT id, chart_mode FROM ' . Db::t('scales') . ' WHERE user_id = %d', [$uid]) as $s) {
            $modes[(int) $s['id']] = $s['chart_mode'];
        }
        $by = [];
        foreach ($rows as $r) {
            $val = $r['position'] !== null ? (float) $r['position'] : ($r['minutes'] !== null ? (float) $r['minutes'] : ($r['num_val'] !== null ? (float) $r['num_val'] : null));
            if ($val !== null) {
                $by[(int) $r['scale_id']][$r['local_date']][] = $val;
            }
        }
        $out = [];
        foreach ($by as $sid => $dates) {
            $series = [];
            foreach ($dates as $date => $vals) {
                $series[] = ['date' => $date, 'value' => round(($modes[$sid] ?? 'avg') === 'sum' ? array_sum($vals) : array_sum($vals) / count($vals), 2)];
            }
            $out[$sid] = ['scale_id' => $sid, 'series' => $series, 'avg' => self::avg(array_column($series, 'value')), 'n' => count($series)];
        }
        return $out;
    }

    /** Vollständige Auswertung eines Zeitraums. */
    public static function period(int $uid, string $from, string $to, string $prev_from, string $prev_to): array
    {
        $entries = self::load($uid, $from, $to);
        $prev = self::load($uid, $prev_from, $prev_to);
        $days = self::days($entries);
        $n = count($entries);

        $levels = [1 => 0, 2 => 0, 3 => 0, 4 => 0, 5 => 0];
        $moods = $tagc = $weekday = $together = [];
        foreach ($entries as $e) {
            $levels[$e['level']]++;
            $moods[$e['mood_id']] = ($moods[$e['mood_id']] ?? 0) + 1;
            $weekday[(int) date('N', strtotime($e['date']))][] = $e['score'];
            foreach ($e['tags'] as $t) {
                $tagc[$t] = ($tagc[$t] ?? 0) + 1;
                $together[$e['level']][$t] = ($together[$e['level']][$t] ?? 0) + 1;
            }
        }
        arsort($tagc);
        $tag_counts = [];
        foreach ($tagc as $t => $c) {
            $tag_counts[] = ['tag_id' => $t, 'n' => $c];
        }
        $groups = [];
        if ($tagc) {
            foreach (Db::rows('SELECT id, group_id FROM ' . Db::t('tags') . ' WHERE user_id = %d AND id IN (' . Db::in(array_keys($tagc)) . ')', [$uid]) as $t) {
                $gid = $t['group_id'] === null ? 0 : (int) $t['group_id'];
                $groups[$gid] = ($groups[$gid] ?? 0) + $tagc[(int) $t['id']];
            }
        }
        arsort($groups);
        $weekday_avg = [];
        for ($d = 1; $d <= 7; $d++) {
            $weekday_avg[] = ['weekday' => $d, 'avg' => self::avg($weekday[$d] ?? []), 'n' => count($weekday[$d] ?? [])];
        }
        $often = [];
        foreach ($together as $level => $counts) {
            arsort($counts);
            $often[] = ['level' => $level, 'tags' => array_map(fn($t, $c) => ['tag_id' => $t, 'n' => $c], array_slice(array_keys($counts), 0, 5), array_slice($counts, 0, 5))];
        }
        usort($often, fn($a, $b) => $a['level'] <=> $b['level']);

        $avg = self::avg(array_column($entries, 'score'));
        $prev_avg = self::avg(array_column($prev, 'score'));
        $scales = self::scale_series($uid, $from, $to);
        $prev_scales = self::scale_series($uid, $prev_from, $prev_to);
        foreach ($scales as $sid => &$s) {
            $s['prev_avg'] = $prev_scales[$sid]['avg'] ?? null;
            $s['change'] = $s['prev_avg'] === null || $s['avg'] === null ? null : round($s['avg'] - $s['prev_avg'], 2);
        }
        unset($s);

        $months = [];
        if (substr($from, 0, 7) !== substr($to, 0, 7)) {
            $bym = [];
            foreach ($entries as $e) {
                $bym[substr($e['date'], 0, 7)][] = $e['score'];
            }
            foreach ($bym as $m => $sc) {
                $months[] = ['month' => $m, 'avg' => self::avg($sc), 'n' => count($sc)];
            }
        }

        $out = [
            'from' => $from, 'to' => $to, 'count' => $n, 'enough' => $n >= self::MIN_ENTRIES, 'min_entries' => self::MIN_ENTRIES,
            'avg' => $avg, 'prev_avg' => $prev_avg, 'prev_count' => count($prev),
            'days' => $days, 'months' => $months,
            'levels' => $levels, 'moods' => array_map(fn($id, $c) => ['mood_id' => $id, 'n' => $c], array_keys($moods), $moods),
            'tag_counts' => $tag_counts,
            'group_counts' => array_map(fn($g, $c) => ['group_id' => $g, 'n' => $c], array_keys($groups), $groups),
            'weekday_avg' => $weekday_avg,
            'influence' => self::influence($entries),
            'stability' => self::stability($days),
            'prev_stability' => self::stability(self::days($prev)),
            'often_together' => $often,
            'good_streak' => self::good_streak($days),
            'scales' => array_values($scales),
            'streaks' => self::streaks($uid),
            'words' => array_sum(array_column($entries, 'words')),
        ];
        $out['hints'] = self::hints($out);
        return $out;
    }

    public static function month(int $uid, string $month): array
    {
        [$from, $to] = In::month_range($month);
        [$pf, $pt] = In::month_range(date('Y-m', strtotime("$from -1 month")));
        $out = self::period($uid, $from, $to, $pf, $pt);
        $out['period'] = 'month';
        $out['goals'] = Features::enabled('goals', $uid) ? Goals::overview($uid, $month, false) : [];
        return $out;
    }

    public static function year(int $uid, int $year): array
    {
        if ($year < 1970 || $year > 2200) {
            throw new Fail('bad_year', 'Das Jahr ist ungültig.');
        }
        $out = self::period($uid, "$year-01-01", "$year-12-31", ($year - 1) . '-01-01', ($year - 1) . '-12-31');
        $out['period'] = 'year';
        $out['goals'] = [];
        return $out;
    }

    /** Hinweiszeilen in Klartext. Ruhig und sachlich, ohne Wertung. */
    public static function hints(array $s): array
    {
        $h = [];
        if (!$s['enough']) {
            return $h;
        }
        $wd = array_values(array_filter($s['weekday_avg'], fn($w) => $w['n'] >= 2));
        if (count($wd) >= 2) {
            usort($wd, fn($a, $b) => $b['avg'] <=> $a['avg']);
            if ($wd[0]['avg'] > end($wd)['avg']) {
                $h[] = sprintf('Dein bester Wochentag war der %s.', self::WEEKDAYS[$wd[0]['weekday']]);
            }
        }
        if ($s['prev_avg'] !== null && $s['avg'] !== null && $s['prev_count'] >= self::MIN_ENTRIES) {
            $d = round($s['avg'] - $s['prev_avg'], 1);
            $label = ($s['months'] ?? []) ? 'Vorjahr' : 'Vormonat';
            if (abs($d) < 0.1) {
                $h[] = "Deine Stimmung lag im Schnitt gleichauf mit dem $label.";
            } else {
                $h[] = sprintf('Deine Stimmung lag im Schnitt %s Punkte %s dem %s.', number_format(abs($d), 1, ',', ''), $d > 0 ? 'über' : 'unter', $label);
            }
        }
        if ($s['good_streak']['length'] >= 3) {
            $h[] = sprintf('Deine längste Serie guter Tage: %d Tage.', $s['good_streak']['length']);
        }
        return $h;
    }

    /** Kalender eines Monats, optional gefiltert nach Stimmungen und Aktivitäten (alle müssen zutreffen). */
    public static function calendar(int $uid, string $month, array $mood_ids = [], array $tag_ids = []): array
    {
        [$from, $to] = In::month_range($month);
        $entries = self::load($uid, $from, $to);
        $mood_ids = array_map('intval', $mood_ids);
        $tag_ids = array_map('intval', $tag_ids);
        $filtered = (bool) ($mood_ids || $tag_ids);
        $days = [];
        $matches = 0;
        $levels = [1 => 0, 2 => 0, 3 => 0, 4 => 0, 5 => 0];
        foreach ($entries as $e) {
            $hit = (!$mood_ids || in_array($e['mood_id'], $mood_ids, true)) && !array_diff($tag_ids, $e['tags']);
            $d = &$days[$e['date']];
            $d['scores'][] = $e['score'];
            $d['hit'] = ($d['hit'] ?? false) || $hit;
            unset($d);
            if ($hit) {
                $matches++;
                $levels[$e['level']]++;
            }
        }
        $out = [];
        foreach ($days as $date => $d) {
            $avg = array_sum($d['scores']) / count($d['scores']);
            $out[] = ['date' => $date, 'n' => count($d['scores']), 'level' => (int) max(1, min(5, round(6 - $avg))), 'match' => $d['hit']];
        }
        $s = ['enough' => count($entries) >= self::MIN_ENTRIES, 'weekday_avg' => [], 'avg' => null, 'prev_avg' => null, 'prev_count' => 0,
            'good_streak' => self::good_streak(self::days($entries)), 'months' => []];
        $wd = [];
        foreach ($entries as $e) {
            $wd[(int) date('N', strtotime($e['date']))][] = $e['score'];
        }
        foreach ($wd as $k => $v) {
            $s['weekday_avg'][] = ['weekday' => $k, 'avg' => self::avg($v), 'n' => count($v)];
        }
        return ['month' => $month, 'filtered' => $filtered, 'matches' => $matches, 'count' => count($entries), 'days' => $out, 'levels' => $levels,
            'hints' => self::hints($s)];
    }

    /** Wochen- oder Monatsbericht mit Veränderung zur Vorperiode. */
    public static function report(int $uid, string $type, string $date): array
    {
        Features::need('reports', $uid);
        $date = In::date($date);
        if ($type === 'week') {
            $ws = (int) Settings::get($uid)['week_start'];
            $dow = (int) date('w', strtotime($date));
            $from = date('Y-m-d', strtotime('-' . (($dow - $ws + 7) % 7) . ' days', strtotime($date)));
            $to = date('Y-m-d', strtotime('+6 days', strtotime($from)));
            $pf = date('Y-m-d', strtotime('-7 days', strtotime($from)));
            $pt = date('Y-m-d', strtotime('-1 day', strtotime($from)));
        } else {
            $type = 'month';
            [$from, $to] = In::month_range(substr($date, 0, 7));
            [$pf, $pt] = In::month_range(date('Y-m', strtotime("$from -1 month")));
        }
        $cur = self::load($uid, $from, $to);
        $prev = self::load($uid, $pf, $pt);
        $sum = function (array $e) {
            return ['entries' => count($e), 'activities' => array_sum(array_map(fn($x) => count($x['tags']), $e)),
                'avg' => self::avg(array_column($e, 'score')), 'words' => array_sum(array_column($e, 'words'))];
        };
        $a = $sum($cur);
        $b = $sum($prev);
        $tagc = [];
        foreach ($cur as $e) {
            foreach ($e['tags'] as $t) {
                $tagc[$t] = ($tagc[$t] ?? 0) + 1;
            }
        }
        arsort($tagc);
        $goals = [];
        foreach (Db::rows('SELECT goal_id, COUNT(*) AS n FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d AND check_date BETWEEN %s AND %s GROUP BY goal_id', [$uid, $from, $to]) as $g) {
            $goals[] = ['goal_id' => (int) $g['goal_id'], 'n' => (int) $g['n']];
        }
        $photos = array_map('intval', Db::col('SELECT a.id FROM ' . Db::t('entry_assets') . ' ea JOIN ' . Db::t('assets') . ' a ON a.id = ea.asset_id JOIN ' . Db::t('entries')
            . " e ON e.id = ea.entry_id WHERE ea.user_id = %d AND a.type = 'photo' AND e.local_date BETWEEN %s AND %s ORDER BY e.local_date LIMIT 12", [$uid, $from, $to]));
        return ['type' => $type, 'from' => $from, 'to' => $to, 'prev_from' => $pf, 'prev_to' => $pt, 'current' => $a, 'previous' => $b,
            'change' => ['entries' => $a['entries'] - $b['entries'], 'activities' => $a['activities'] - $b['activities'],
                'avg' => $a['avg'] === null || $b['avg'] === null ? null : round($a['avg'] - $b['avg'], 2), 'words' => $a['words'] - $b['words']],
            'days' => self::days($cur), 'top_tags' => array_map(fn($t, $c) => ['tag_id' => $t, 'n' => $c], array_slice(array_keys($tagc), 0, 5), array_slice($tagc, 0, 5)),
            'goals' => $goals, 'photos' => $photos, 'enough' => $a['entries'] > 0];
    }

    /** Rückblick: Einträge vom selben Tag früherer Jahre. */
    public static function lookback(int $uid, string $date): array
    {
        Features::need('lookback', $uid);
        $date = In::date($date);
        $rows = Db::rows("SELECT * FROM " . Db::t('entries') . " WHERE user_id = %d AND DATE_FORMAT(local_date, '%%m-%%d') = %s AND local_date < %s ORDER BY local_date DESC, local_time DESC",
            [$uid, substr($date, 5), $date]);
        $years = [];
        foreach (Entries::hydrate($uid, $rows) as $e) {
            $y = (int) substr($e['date'], 0, 4);
            $years[$y]['year'] = $y;
            $years[$y]['years_ago'] = (int) substr($date, 0, 4) - $y;
            $years[$y]['entries'][] = $e;
        }
        return ['date' => $date, 'years' => array_values($years)];
    }

    /** Erfolge: werden aus den Einträgen neu berechnet, nie importiert. */
    public static function achievements(int $uid): array
    {
        Features::need('achievements', $uid);
        $entries = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid]);
        $first = Db::val('SELECT MIN(local_date) FROM ' . Db::t('entries') . ' WHERE user_id = %d', [$uid]);
        $checks = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('goal_checks') . ' WHERE user_id = %d', [$uid]);
        $photos = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('assets') . " WHERE user_id = %d AND type = 'photo'", [$uid]);
        $streak = self::streaks($uid);
        $years = $first ? (int) floor((strtotime(Settings::today($uid)) - strtotime($first)) / (365.25 * 86400)) : 0;
        $defs = [
            ['entries', 'Einträge', $entries, [[1, 'Erster Schritt'], [10, 'Zehn Einträge'], [50, 'Fünfzig Einträge'], [100, 'Hundert Einträge'], [250, 'Gut dabei'], [500, 'Fünfhundert Einträge'], [1000, 'Tausend Einträge'], [1500, 'Langstrecke']]],
            ['streak', 'Tage in Folge', $streak['longest'], [[3, 'Drei Tage am Stück'], [7, 'Eine volle Woche'], [30, 'Ein Monat ohne Lücke'], [100, 'Hundert Tage'], [365, 'Ein ganzes Jahr']]],
            ['goals', 'Ziele abgehakt', $checks, [[1, 'Erstes Ziel erreicht'], [10, 'Zehnmal drangeblieben'], [100, 'Hundertmal drangeblieben'], [500, 'Fünfhundertmal drangeblieben'], [1000, 'Tausendmal drangeblieben']]],
            ['years', 'Jahre mit Tagebuch', $years, [[1, 'Ein Jahr Tagebuch'], [2, 'Zwei Jahre Tagebuch'], [3, 'Drei Jahre Tagebuch'], [5, 'Fünf Jahre Tagebuch'], [8, 'Acht Jahre Tagebuch'], [10, 'Zehn Jahre Tagebuch']]],
            ['photos', 'Fotos', $photos, [[1, 'Erstes Foto'], [50, 'Fünfzig Fotos'], [500, 'Fünfhundert Fotos'], [1500, 'Bilderbuch']]],
        ];
        $out = [];
        foreach ($defs as [$key, $label, $value, $steps]) {
            foreach ($steps as [$target, $name]) {
                $out[] = ['key' => "$key-$target", 'group' => $key, 'group_label' => $label, 'name' => $name, 'target' => $target,
                    'value' => min($value, $target), 'reached' => $value >= $target];
            }
        }
        return ['achievements' => $out, 'reached' => count(array_filter($out, fn($a) => $a['reached'])), 'total' => count($out)];
    }

    /** Kennzahlen für einen Zeitraum, knapp für MCP. */
    public static function summary(int $uid, string $from, string $to): array
    {
        $entries = self::load($uid, $from, $to);
        $days = self::days($entries);
        $levels = [1 => 0, 2 => 0, 3 => 0, 4 => 0, 5 => 0];
        $tagc = [];
        foreach ($entries as $e) {
            $levels[$e['level']]++;
            foreach ($e['tags'] as $t) {
                $tagc[$t] = ($tagc[$t] ?? 0) + 1;
            }
        }
        arsort($tagc);
        return ['from' => $from, 'to' => $to, 'count' => count($entries), 'days_with_entries' => count($days), 'avg' => self::avg(array_column($entries, 'score')),
            'levels' => $levels, 'tag_counts' => array_slice($tagc, 0, 15, true), 'stability' => self::stability($days),
            'good_streak' => self::good_streak($days), 'streaks' => self::streaks($uid), 'scales' => array_values(self::scale_series($uid, $from, $to))];
    }
}
