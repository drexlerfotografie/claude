<?php
final class SetupEntriesTest extends Base
{
    public function test_startpaket_ist_nur_ein_vorschlag_und_aenderbar(): void
    {
        $u = $this->user();
        MCT\Setup::apply_pack($u, 'schichtdienst');
        $s = MCT\Setup::all($u);
        $this->assertCount(5, $s['moods']);
        $this->assertGreaterThan(10, count($s['tags']));
        $this->assertNotEmpty($s['scales']);
        $renamed = MCT\Setup::tag_save($u, ['name' => 'Bereitschaft'], $s['tags'][0]['id']);
        $this->assertSame('Bereitschaft', $renamed['name']);
        $this->assertTrue(MCT\Settings::get($u)['onboarded']);
    }

    public function test_archiviertes_bleibt_in_alten_eintraegen_sichtbar(): void
    {
        $u = $this->user();
        $tag = MCT\Setup::tag_save($u, ['name' => 'Yoga']);
        $e = $this->entry($u, '2026-04-01', 2, ['tag_ids' => [$tag['id']]]);
        MCT\Setup::archive($u, 'tags', $tag['id'], true);
        $this->assertTrue(MCT\Setup::all($u)['tags'][0]['archived']);
        $this->assertSame([$tag['id']], MCT\Entries::get($u, $e['id'])['tags']);
        $this->assertSame($tag['id'], MCT\Stats::month($u, '2026-04')['tag_counts'][0]['tag_id']);
    }

    public function test_loeschen_nennt_die_zahl_betroffener_eintraege(): void
    {
        $u = $this->user();
        $extra = MCT\Setup::mood_save($u, ['name' => 'Euphorisch', 'level' => 1]);
        $this->entry($u, '2026-04-01', 1, ['mood_id' => $extra['id']]);
        $this->entry($u, '2026-04-02', 1, ['mood_id' => $extra['id']]);
        $this->assertSame(2, MCT\Setup::usage($u, 'moods', $extra['id']));
        try {
            MCT\Setup::mood_delete($u, $extra['id']);
            $this->fail('Löschen ohne Ersatz muss scheitern');
        } catch (MCT\Fail $f) {
            $this->assertSame('in_use', $f->slug);
            $this->assertSame(2, $f->data['count']);
        }
        MCT\Setup::mood_delete($u, $extra['id'], $this->mood($u, 1));
        $this->assertSame(2, MCT\Stats::month($u, '2026-04')['levels'][1]);
    }

    public function test_jede_stufe_behaelt_eine_aktive_stimmung(): void
    {
        $u = $this->user();
        $this->assertFail('last_mood', fn() => MCT\Setup::archive($u, 'moods', $this->mood($u, 3), true));
        $this->assertFail('last_mood', fn() => MCT\Setup::mood_delete($u, $this->mood($u, 3)));
    }

    public function test_aktivitaeten_zusammenfuehren_und_loeschen(): void
    {
        $u = $this->user();
        $a = MCT\Setup::tag_save($u, ['name' => 'Joggen']);
        $b = MCT\Setup::tag_save($u, ['name' => 'Laufen']);
        $e1 = $this->entry($u, '2026-04-01', 2, ['tag_ids' => [$a['id']]]);
        $e2 = $this->entry($u, '2026-04-02', 2, ['tag_ids' => [$a['id'], $b['id']]]);
        $goal = MCT\Goals::save($u, ['name' => 'Laufziel', 'tag_id' => $a['id']]);
        MCT\Setup::tag_merge($u, $a['id'], $b['id']);
        $this->assertSame([$b['id']], MCT\Entries::get($u, $e1['id'])['tags']);
        $this->assertSame([$b['id']], MCT\Entries::get($u, $e2['id'])['tags']);
        $this->assertSame($b['id'], MCT\Goals::overview($u)[0]['tag_id']);
        MCT\Setup::tag_delete($u, $b['id']);
        $this->assertSame([], MCT\Entries::get($u, $e1['id'])['tags']);
        $this->assertNull(MCT\Goals::overview($u)[0]['tag_id']);
        $this->assertSame($goal['id'], MCT\Goals::overview($u)[0]['id']);
    }

    public function test_gruppen_und_skalen_teilen_eine_reihenfolge(): void
    {
        $u = $this->user();
        $g1 = MCT\Setup::group_save($u, ['name' => 'Eins']);
        $s1 = MCT\Setup::scale_save($u, ['name' => 'Schlaf', 'type' => 'time']);
        $g2 = MCT\Setup::group_save($u, ['name' => 'Zwei']);
        $this->assertSame([1, 2, 3], [$g1['position'], $s1['position'], $g2['position']]);
        MCT\Setup::reorder_shared($u, [['kind' => 'scale', 'id' => $s1['id']], ['kind' => 'group', 'id' => $g2['id']], ['kind' => 'group', 'id' => $g1['id']]]);
        $all = MCT\Setup::all($u);
        $this->assertSame(1, $all['scales'][0]['position']);
        $this->assertSame('Zwei', $all['groups'][0]['name']);
    }

    public function test_textskala_braucht_benannte_punkte(): void
    {
        $u = $this->user();
        $this->assertFail('bad_points', fn() => MCT\Setup::scale_save($u, ['name' => 'X', 'type' => 'text', 'points' => ['nur einer']]));
        $s = MCT\Setup::scale_save($u, ['name' => 'Energie', 'type' => 'text', 'points' => ['Leer', 'Okay', 'Voll'], 'trend' => 'up']);
        $this->assertSame(['Leer', 'Okay', 'Voll'], array_column($s['points'], 'label'));
        $e = $this->entry($u, '2026-04-01', 2, ['scales' => [['scale_id' => $s['id'], 'point_id' => $s['points'][2]['id']]]]);
        $this->assertSame($s['points'][2]['id'], $e['scales'][0]['point_id']);
    }

    public function test_einwilligung_vor_dem_ersten_eintrag(): void
    {
        $u = $this->user(false);
        $this->assertFail('consent_required', fn() => $this->entry($u, '2026-04-01', 2));
        $res = $this->rest($u, 'POST', '/consent', ['consent' => true, 'adult' => false]);
        $this->assertSame(400, $res->get_status());
        $this->rest($u, 'POST', '/consent', ['consent' => true, 'adult' => true]);
        $this->assertSame(200, $this->rest($u, 'POST', '/entries', ['date' => '2026-04-01', 'mood_id' => $this->mood($u, 2)])->get_status());
        $this->rest($u, 'POST', '/consent', ['consent' => false]);
        $this->assertSame(403, $this->rest($u, 'POST', '/entries', ['date' => '2026-04-02', 'mood_id' => $this->mood($u, 2)])->get_status());
    }

    public function test_notiz_laeuft_durch_die_positivliste(): void
    {
        $u = $this->user();
        $e = $this->entry($u, '2026-04-01', 2, ['note' => '<b>fett</b><script>alert(1)</script><img src=x onerror=alert(2)><a href="javascript:alert(3)">x</a><ul><li onclick="x()">Punkt</li></ul>',
            'title' => '<i>Titel</i> <script>x</script>']);
        $this->assertStringContainsString('<b>fett</b>', $e['note']);
        $this->assertStringContainsString('<li>Punkt</li>', $e['note']);
        foreach (['<script', 'onerror', 'javascript:', 'onclick', '<img', '<a '] as $bad) {
            $this->assertStringNotContainsString($bad, $e['note']);
        }
        $this->assertStringNotContainsString('<', $e['title']);
    }

    public function test_mehrere_eintraege_pro_tag_und_zeitzone(): void
    {
        $u = $this->user();
        $a = MCT\Entries::save($u, ['date' => '2026-07-10', 'time' => '08:30', 'mood_id' => $this->mood($u, 2)]);
        $b = MCT\Entries::save($u, ['date' => '2026-07-10', 'time' => '23:45', 'mood_id' => $this->mood($u, 4), 'tz_offset' => 180]);
        $this->assertSame(120, $a['tz_offset']);                 // Europe/Berlin im Sommer
        $this->assertSame('2026-07-10 06:30:00', $a['ts_utc']);
        $this->assertSame('2026-07-10 20:45:00', $b['ts_utc']);
        $this->assertSame('2026-07-10', $b['date']);
        $day = MCT\Entries::day($u, '2026-07-10');
        $this->assertCount(2, $day);
        $month = MCT\Entries::month($u, '2026-07');
        $this->assertSame($b['id'], $month[0]['id'], 'neueste zuerst');
    }

    public function test_suche_nach_text_stimmung_und_aktivitaet(): void
    {
        $u = $this->user();
        $t = MCT\Setup::tag_save($u, ['name' => 'Kino']);
        $this->entry($u, '2026-04-01', 1, ['note' => 'Schöner Abend', 'tag_ids' => [$t['id']]]);
        $this->entry($u, '2026-04-02', 4, ['note' => 'Langer Dienst']);
        $this->entry($u, '2026-04-03', 4, ['title' => 'Abendschicht']);
        $this->assertSame(2, MCT\Entries::search($u, ['text' => 'abend'])['total']);
        $this->assertSame(1, MCT\Entries::search($u, ['tag_ids' => [$t['id']]])['total']);
        $this->assertSame(2, MCT\Entries::search($u, ['mood_ids' => [$this->mood($u, 4)]])['total']);
        $this->assertSame(1, MCT\Entries::search($u, ['text' => 'abend', 'mood_ids' => [$this->mood($u, 4)]])['total']);
        $this->assertSame(0, MCT\Entries::search($u, ['text' => "%' OR 1=1 --"])['total']);
    }

    public function test_loeschen_entfernt_abhaengige_zeilen_und_dateien(): void
    {
        $u = $this->user();
        $im = imagecreatetruecolor(20, 20);
        ob_start();
        imagejpeg($im);
        $asset = MCT\Assets::store($u, ob_get_clean());
        $file = MCT\Assets::file($u, $asset['id'])['file'];
        $t = MCT\Setup::tag_save($u, ['name' => 'X']);
        $e = $this->entry($u, '2026-04-01', 2, ['tag_ids' => [$t['id']], 'asset_ids' => [$asset['id']]]);
        MCT\Entries::delete($u, $e['id']);
        $this->assertFileDoesNotExist($file);
        foreach (['entry_tags', 'entry_assets', 'assets', 'entries'] as $table) {
            $this->assertSame(0, (int) MCT\Db::val('SELECT COUNT(*) FROM ' . MCT\Db::t($table) . ' WHERE user_id = %d', [$u]), $table);
        }
    }

    public function test_funktionsschalter_sperrt_eine_funktion(): void
    {
        $u = $this->user();
        $off = fn($on, $key) => $key === 'stats' ? false : $on;
        add_filter('mct_feature_enabled', $off, 10, 2);
        $res = $this->rest($u, 'GET', '/stats', ['month' => '2026-04']);
        remove_filter('mct_feature_enabled', $off, 10);
        $this->assertSame(403, $res->get_status());
        $this->assertSame(200, $this->rest($u, 'GET', '/stats', ['month' => '2026-04'])->get_status());
    }

    public function test_konto_loeschen_entfernt_alles(): void
    {
        $u = $this->user();
        MCT\Setup::apply_pack($u, 'allgemein');
        $this->entry($u, '2026-04-01', 2);
        MCT\Goals::save($u, ['name' => 'Z']);
        $this->assertSame(400, $this->rest($u, 'DELETE', '/account', ['confirm' => 'nein'])->get_status());
        $this->assertSame(200, $this->rest($u, 'DELETE', '/account', ['confirm' => 'LÖSCHEN'])->get_status());
        foreach (MCT\Schema::TABLES as $t) {
            if ($t === 'oauth_clients') {
                continue;
            }
            $this->assertSame(0, (int) MCT\Db::val('SELECT COUNT(*) FROM ' . MCT\Db::t($t) . ' WHERE user_id = %d', [$u]), $t);
        }
        $this->assertSame('', get_user_meta($u, MCT\Settings::KEY, true));
    }
}
