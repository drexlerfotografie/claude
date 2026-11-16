<?php
/** Jede Auswertung gegen eine von Hand nachgerechnete Stichprobe. */
final class StatsGoalsTest extends Base
{
    private function sample(int $u): array
    {
        $a = MCT\Setup::tag_save($u, ['name' => 'A'])['id'];
        $b = MCT\Setup::tag_save($u, ['name' => 'B'])['id'];
        $scale = MCT\Setup::scale_save($u, ['name' => 'Schlaf', 'type' => 'text', 'points' => ['1', '2', '3', '4']]);
        foreach (['2026-02-10', '2026-02-11', '2026-02-12'] as $d) {
            $this->entry($u, $d, 3);
        }
        $this->entry($u, '2026-03-02', 1, ['tag_ids' => [$a], 'scales' => [['scale_id' => $scale['id'], 'point_id' => $scale['points'][3]['id']]], 'note' => 'eins zwei drei']);
        $this->entry($u, '2026-03-03', 2, ['tag_ids' => [$a, $b], 'scales' => [['scale_id' => $scale['id'], 'point_id' => $scale['points'][1]['id']]]]);
        $this->entry($u, '2026-03-04', 2);
        $this->entry($u, '2026-03-05', 5, ['tag_ids' => [$b]]);
        $this->entry($u, '2026-03-09', 3, ['time' => '08:00']);
        $this->entry($u, '2026-03-09', 1, ['time' => '20:00', 'tag_ids' => [$a]]);
        $this->entry($u, '2026-03-10', 4, ['tag_ids' => [$b]]);
        return ['a' => $a, 'b' => $b, 'scale' => $scale['id']];
    }

    public function test_monatsauswertung_stimmt_mit_der_handrechnung(): void
    {
        $u = $this->user();
        $x = $this->sample($u);
        $s = MCT\Stats::month($u, '2026-03');
        $this->assertSame(7, $s['count']);
        $this->assertSame(3.43, $s['avg']);                       // (5+4+4+1+3+5+2)/7
        $this->assertSame(3.0, $s['prev_avg']);
        $this->assertSame([1 => 2, 2 => 2, 3 => 1, 4 => 1, 5 => 1], $s['levels']);
        $this->assertSame([5.0, 4.0, 4.0, 1.0, 4.0, 2.0], array_column($s['days'], 'avg'));
        $this->assertSame(55, $s['stability']);                   // Schwankungen 1,0,3,3,2 → 1,8 → 100·(1−1,8/4)
        $wd = array_column($s['weekday_avg'], null, 'weekday');
        $this->assertSame(4.33, $wd[1]['avg']);
        $this->assertSame(3.0, $wd[2]['avg']);
        $this->assertNull($wd[5]['avg']);
        $inf = array_column($s['influence'], null, 'tag_id');
        $this->assertSame(2.17, $inf[$x['a']]['delta']);          // 4,67 − 2,5
        $this->assertSame(-1.92, $inf[$x['b']]['delta']);         // 2,33 − 4,25
        $this->assertSame(['length' => 3, 'from' => '2026-03-02', 'to' => '2026-03-04'], array_intersect_key($s['good_streak'], ['length' => 1, 'from' => 1, 'to' => 1]));
        $this->assertSame(4, $s['streaks']['longest']);
        $this->assertSame(3, array_sum(array_column($s['tag_counts'], 'n', 'tag_id')) / 2);
        $this->assertSame(3.0, $s['scales'][0]['avg']);           // Punkte 4 und 2
        $this->assertSame(3, $s['words']);
        $this->assertContains('Dein bester Wochentag war der Montag.', $s['hints']);
        $this->assertContains('Deine Stimmung lag im Schnitt 0,4 Punkte über dem Vormonat.', $s['hints']);
        $level1 = array_column($s['often_together'], null, 'level')[1];
        $this->assertSame([['tag_id' => $x['a'], 'n' => 2]], $level1['tags']);
    }

    public function test_wochentags_hinweis_widerspricht_dem_diagramm_nicht(): void
    {
        $u = $this->user();
        // Dienstag hat den höchsten Schnitt, aber nur einen Eintrag: kein Hinweis auf einen „besten Wochentag“.
        $this->entry($u, '2026-03-03', 1);
        $this->entry($u, '2026-03-06', 2);
        $this->entry($u, '2026-03-13', 3);
        $hints = implode(' ', MCT\Stats::month($u, '2026-03')['hints']);
        $this->assertStringNotContainsString('bester Wochentag', $hints);
        // Ein zweiter guter Dienstag macht ihn zum belegten besten Tag.
        $this->entry($u, '2026-03-10', 1);
        $this->assertContains('Dein bester Wochentag war der Dienstag.', MCT\Stats::month($u, '2026-03')['hints']);
    }

    public function test_erfolge_zaehlen_fotos_wie_die_galerie(): void
    {
        $u = $this->user();
        $im = imagecreatetruecolor(20, 20);
        ob_start();
        imagejpeg($im);
        $bytes = ob_get_clean();
        $at_entry = MCT\Assets::store($u, $bytes)['id'];
        $at_milestone = MCT\Assets::store($u, $bytes)['id'];
        $this->entry($u, '2026-03-03', 2, ['asset_ids' => [$at_entry]]);
        MCT\Milestones::save($u, ['name' => 'Tag', 'date' => '2020-01-01', 'asset_id' => $at_milestone]);
        $photos = array_column(MCT\Stats::achievements($u)['achievements'], null, 'key')['photos-50'];
        $this->assertSame(1, $photos['value']);
        $this->assertSame(1, MCT\Assets::gallery($u)['total']);
    }

    public function test_leerzustand_bei_zu_wenig_daten(): void
    {
        $u = $this->user();
        $this->entry($u, '2026-03-02', 1);
        $s = MCT\Stats::month($u, '2026-03');
        $this->assertFalse($s['enough']);
        $this->assertSame([], $s['hints']);
        $this->assertNull($s['stability']);
        $empty = MCT\Stats::month($u, '2020-01');
        $this->assertSame(0, $empty['count']);
        $this->assertNull($empty['avg']);
    }

    public function test_jahresansicht_fasst_monate_zusammen(): void
    {
        $u = $this->user();
        $this->sample($u);
        $y = MCT\Stats::year($u, 2026);
        $this->assertSame(10, $y['count']);
        $this->assertSame(['2026-02', '2026-03'], array_column($y['months'], 'month'));
        $this->assertSame(3.0, $y['months'][0]['avg']);
    }

    public function test_kalender_mit_filter_und_zaehler(): void
    {
        $u = $this->user();
        $x = $this->sample($u);
        $c = MCT\Stats::calendar($u, '2026-03');
        $days = array_column($c['days'], null, 'date');
        $this->assertSame(2, $days['2026-03-09']['level']);      // Durchschnitt aus Stufe 3 und 1
        $this->assertSame(2, $days['2026-03-09']['n']);
        $this->assertSame(7, $c['matches']);
        $f = MCT\Stats::calendar($u, '2026-03', [], [$x['a']]);
        $this->assertSame(3, $f['matches']);
        $this->assertTrue(array_column($f['days'], null, 'date')['2026-03-09']['match']);
        $this->assertFalse(array_column($f['days'], null, 'date')['2026-03-04']['match']);
        $both = MCT\Stats::calendar($u, '2026-03', [$this->mood($u, 2)], [$x['a'], $x['b']]);
        $this->assertSame(1, $both['matches']);                  // „hinzu“ kombiniert: alle Bedingungen müssen zutreffen
    }

    public function test_bericht_mit_veraenderung_zur_vorperiode(): void
    {
        $u = $this->user();
        $this->sample($u);
        $r = MCT\Stats::report($u, 'week', '2026-03-10');
        $this->assertSame(['2026-03-09', '2026-03-15'], [$r['from'], $r['to']]);
        $this->assertSame(3, $r['current']['entries']);
        $this->assertSame(4, $r['previous']['entries']);
        $this->assertSame(-1, $r['change']['entries']);
        $this->assertSame(2, $r['current']['activities']);
        $m = MCT\Stats::report($u, 'month', '2026-03-10');
        $this->assertSame(7, $m['current']['entries']);
        $this->assertSame(0.43, $m['change']['avg']);
    }

    public function test_rueckblick_auf_fruehere_jahre(): void
    {
        $u = $this->user();
        $this->entry($u, '2024-03-10', 2, ['note' => 'vor zwei Jahren']);
        $this->entry($u, '2025-03-10', 3);
        $this->entry($u, '2025-03-11', 3);
        $l = MCT\Stats::lookback($u, '2026-03-10');
        $this->assertSame([2025, 2024], array_column($l['years'], 'year'));
        $this->assertSame(2, $l['years'][1]['years_ago']);
    }

    public function test_serien_und_erfolgsraten_der_ziele(): void
    {
        $base = ['id' => 1, 'start_date' => '2026-03-01', 'end_date' => null, 'archived' => false, 'freq_count' => 1, 'freq_days' => []];
        $daily = $base + ['freq_type' => 'daily'];
        $checks = ['2026-03-06', '2026-03-07', '2026-03-08', '2026-03-09', '2026-03-10'];
        $this->assertSame(5, MCT\Goals::metrics($daily, $checks, '2026-03-10')['streak']);
        $this->assertSame(5, MCT\Goals::metrics($daily, $checks, '2026-03-11')['streak'], 'der offene heutige Tag bricht die Serie nicht');
        $this->assertSame(0, MCT\Goals::metrics($daily, $checks, '2026-03-12')['streak']);
        $this->assertSame(71, MCT\Goals::metrics($daily, $checks, '2026-03-10')['rate7']);
        $this->assertSame(50, MCT\Goals::metrics($daily, $checks, '2026-03-10')['rate30']);   // 5 von 10 Tagen seit Start
        $this->assertSame(5, MCT\Goals::metrics($daily, $checks, '2026-03-20')['best_streak']);

        $weekly = array_merge($base, ['freq_type' => 'weekly', 'freq_count' => 3]);
        $w = ['2026-03-02', '2026-03-03', '2026-03-04', '2026-03-09', '2026-03-10', '2026-03-12'];
        $this->assertSame(2, MCT\Goals::metrics($weekly, $w, '2026-03-12')['streak']);
        $this->assertSame(1, MCT\Goals::metrics($weekly, array_slice($w, 0, 5), '2026-03-12')['streak'], 'laufende Woche noch offen');

        $days = array_merge($base, ['freq_type' => 'days', 'freq_days' => [1, 3, 5]]);
        $d = ['2026-03-02', '2026-03-04', '2026-03-06', '2026-03-09'];
        $this->assertSame(4, MCT\Goals::metrics($days, $d, '2026-03-10')['streak']);
        $this->assertSame(0, MCT\Goals::metrics($days, $d, '2026-03-12')['streak'], 'Mittwoch verpasst');
        $this->assertSame(2, MCT\Goals::metrics($daily, $checks, '2026-03-10')['level'] + 1);
    }

    public function test_ziel_mit_aktivitaet_wird_durch_eintrag_abgehakt(): void
    {
        $u = $this->user();
        $tag = MCT\Setup::tag_save($u, ['name' => 'Sport']);
        $goal = MCT\Goals::save($u, ['name' => 'Bewegung', 'tag_id' => $tag['id'], 'start_date' => '2026-03-01']);
        $this->entry($u, '2026-03-05', 2, ['tag_ids' => [$tag['id']]]);
        $this->entry($u, '2026-03-06', 2);
        $o = MCT\Goals::overview($u, '2026-03')[0];
        $this->assertSame(['2026-03-05'], $o['month_checks']);
        MCT\Goals::check($u, $goal['id'], '2026-03-06', true);
        MCT\Goals::check($u, $goal['id'], '2026-03-06', true);
        $this->assertSame(2, MCT\Goals::overview($u)[0]['total']);
        MCT\Goals::check($u, $goal['id'], '2026-03-06', false);
        $this->assertSame(1, MCT\Goals::overview($u)[0]['total']);
    }

    public function test_wichtige_tage_mit_naechstem_jahrestag(): void
    {
        $r = ['id' => 1, 'name' => 'Examen', 'm_date' => '2019-03-14', 'category' => '', 'note' => '', 'asset_id' => null, 'yearly' => 1, 'remind_same_day' => 0, 'remind_days_before' => 0];
        $m = MCT\Milestones::out($r, '2026-03-10');
        $this->assertSame(['2026-03-14', 4, 7, 2553], [$m['next_date'], $m['days_until'], $m['next_years'], $m['days_since']]);
        $this->assertSame(0, MCT\Milestones::out($r, '2026-03-14')['days_until']);
        $this->assertSame('2027-03-14', MCT\Milestones::out($r, '2026-03-15')['next_date']);
        $leap = MCT\Milestones::out(array_merge($r, ['m_date' => '2024-02-29']), '2026-03-01');
        $this->assertSame('2027-02-28', $leap['next_date']);
        $once = MCT\Milestones::out(array_merge($r, ['m_date' => '2026-12-24', 'yearly' => 0]), '2026-03-10');
        $this->assertSame(289, $once['days_until']);
    }

    public function test_erfolge_werden_aus_den_eintraegen_berechnet(): void
    {
        $u = $this->user();
        for ($i = 1; $i <= 10; $i++) {
            $this->entry($u, sprintf('2026-03-%02d', $i), 2);
        }
        $a = array_column(MCT\Stats::achievements($u)['achievements'], null, 'key');
        $this->assertTrue($a['entries-10']['reached']);
        $this->assertFalse($a['entries-50']['reached']);
        $this->assertSame(10, $a['entries-50']['value']);
        $this->assertTrue($a['streak-7']['reached']);
        $this->assertFalse($a['streak-30']['reached']);
    }
}
