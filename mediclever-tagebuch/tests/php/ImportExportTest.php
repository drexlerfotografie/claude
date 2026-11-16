<?php
final class ImportExportTest extends Base
{
    private function expected(): array
    {
        return json_decode(file_get_contents(dirname(__DIR__) . '/fixtures/sample.expected.json'), true);
    }

    private function assertCounts(array $exp, array $got): void
    {
        foreach ($exp as $k => $v) {
            $this->assertEquals($v, $got[$k], "Zählwert „{$k}“");
        }
    }

    public function test_import_der_testdatei_erfuellt_alle_zaehlwerte(): void
    {
        $u = $this->user();
        $report = $this->importFixture($u);
        $this->assertSame(0, $report['entries']['errors']);
        $this->assertCounts($this->expected(), MCT\Import::counts($u));
    }

    public function test_zweiter_lauf_erzeugt_keine_doppelten(): void
    {
        $u = $this->user();
        $this->importFixture($u);
        $before = MCT\Import::counts($u);
        $second = $this->importFixture($u);
        $this->assertSame(0, $second['entries']['created']);
        $this->assertSame($before['entries'], $second['entries']['skipped']);
        $this->assertSame(0, $second['assets']['created']);
        $this->assertSame(0, $second['checks']['created']);
        $this->assertSame($before, MCT\Import::counts($u));
    }

    public function test_import_ist_unterbrechbar_und_fortsetzbar(): void
    {
        $u = $this->user();
        $zip = new ZipArchive();
        $zip->open(dirname(__DIR__) . '/fixtures/sample.daylio');
        $json = json_decode(base64_decode($zip->getFromName('backup.daylio')), true);
        $run = 'unterbrochen01';
        MCT\Import::setup($u, $run, array_diff_key($json, ['dayEntries' => 1, 'goalEntries' => 1, 'assets' => 1]));
        MCT\Import::entries($u, $run, array_slice($json['dayEntries'], 0, 100));
        $status = MCT\Import::status($u, $run);
        $this->assertSame(100, $status['counts']['entry']);
        // Fortsetzen: derselbe Lauf, alles noch einmal schicken.
        $this->importFixture($u, $run);
        $this->assertCounts($this->expected(), MCT\Import::counts($u));
    }

    public function test_stolperstellen(): void
    {
        $u = $this->user();
        $this->importFixture($u);
        $setup = MCT\Setup::all($u);
        // Monat bei Einträgen zählt ab 0; maßgeblich ist datetime mit timeZoneOffset.
        $zip = new ZipArchive();
        $zip->open(dirname(__DIR__) . '/fixtures/sample.daylio');
        $json = json_decode(base64_decode($zip->getFromName('backup.daylio')), true);
        foreach (array_slice($json['dayEntries'], 0, 40) as $src) {
            $id = (int) MCT\Db::val('SELECT new_id FROM ' . MCT\Db::t('import_map') . " WHERE user_id = %d AND kind = 'entry' AND source_id = %s", [$u, (string) $src['id']]);
            $e = MCT\Entries::get($u, $id);
            $this->assertSame(sprintf('%04d-%02d-%02d', $src['year'], $src['month'] + 1, $src['day']), $e['date']);
            $this->assertSame(sprintf('%02d:%02d', $src['hour'], $src['minute']), $e['time'], 'Ortszeit bleibt erhalten');
            $this->assertSame($src['timeZoneOffset'] / 60000, $e['tz_offset']);
            $this->assertSame(gmdate('Y-m-d H:i:s', intdiv($src['datetime'], 1000)), $e['ts_utc']);
        }
        // Drei Zeitzonen-Versätze kommen vor.
        $offsets = array_map('intval', MCT\Db::col('SELECT DISTINCT tz_offset FROM ' . MCT\Db::t('entries') . ' WHERE user_id = %d ORDER BY tz_offset', [$u]));
        $this->assertSame([60, 120, 180], $offsets);
        // Namen mit Leerzeichen am Ende sind bereinigt.
        $this->assertContains('Freizeit', array_column($setup['groups'], 'name'));
        $this->assertContains('Fortbildung', array_column($setup['tags'], 'name'));
        // Eingeklappt und Farbnummern auf die eigene Palette abgebildet.
        $groups = array_column($setup['groups'], null, 'name');
        $this->assertTrue($groups['Freizeit']['collapsed']);
        $this->assertSame('c2', $groups['🏡 Zuhause']['color']);   // Farbnummer 14 → c2
        // Symbole: eigene Zuordnung über den Namen, sonst neutral.
        $tags = array_column($setup['tags'], null, 'name');
        $this->assertSame('dumbbell', $tags['Sport']['icon']);
        $this->assertSame('circle', $tags['Ohne Gruppe']['icon']);
        $this->assertNull($tags['Ohne Gruppe']['group_id']);
        // Notizen laufen durch die Positivliste.
        $this->assertSame(0, (int) MCT\Db::val('SELECT COUNT(*) FROM ' . MCT\Db::t('entries') . " WHERE user_id = %d AND note LIKE '%%<script%%'", [$u]));
        $this->assertGreaterThan(0, (int) MCT\Db::val('SELECT COUNT(*) FROM ' . MCT\Db::t('entries') . " WHERE user_id = %d AND note LIKE '%%<li>%%'", [$u]));
        // Skalen: Textpunkte und Zeitwerte, Trend übernommen.
        $scales = array_column($setup['scales'], null, 'name');
        $this->assertSame(['text', 'text', 'time'], [$scales['Schlafqualität']['type'], $scales['Stress']['type'], $scales['Schlafdauer']['type']]);
        $this->assertCount(5, $scales['Stress']['points']);
        $this->assertSame('down', $scales['Stress']['trend']);
        $min = array_map('intval', MCT\Db::col('SELECT minutes FROM ' . MCT\Db::t('entry_scale_values') . ' WHERE user_id = %d AND scale_id = %d', [$u, $scales['Schlafdauer']['id']]));
        $this->assertGreaterThan(50, count($min));
        $this->assertGreaterThanOrEqual(300, min($min));
        $this->assertLessThanOrEqual(570, max($min));
        // Wichtige Tage: Monat zählt ab 1, Foto ist verknüpft.
        $ms = array_column(MCT\Milestones::all($u), null, 'name');
        $this->assertSame('2019-03-14', $ms['Examen']['date']);
        $this->assertNotNull($ms['Examen']['asset_id']);
        $this->assertTrue($ms['Examen']['remind_same_day']);
        $this->assertSame(7, $ms['Examen']['remind_days_before']);
        // Ziele: Wiederholung und Verknüpfung.
        $goals = array_column(MCT\Goals::overview($u), null, 'name');
        $this->assertSame('daily', $goals['Bewegung']['freq_type']);
        $this->assertSame('18:30', $goals['Bewegung']['reminder_time']);
        $this->assertSame(['weekly', 3], [$goals['Lesen']['freq_type'], $goals['Lesen']['freq_count']]);
        $this->assertTrue($goals['Altes Ziel']['archived']);
        $this->assertSame($tags['Sport']['id'], $goals['Bewegung']['tag_id']);
        // Erfolge werden nicht importiert, Einstellungen nur, was es hier gibt.
        $this->assertSame(1, MCT\Settings::get($u)['week_start']);
        // Bis zu drei Fotos an einem Eintrag, in der Reihenfolge der Quelle.
        $this->assertSame(3, (int) MCT\Db::val('SELECT MAX(n) FROM (SELECT COUNT(*) AS n FROM ' . MCT\Db::t('entry_assets') . ' WHERE user_id = %d GROUP BY entry_id) x', [$u]));
    }

    public function test_einheit_der_zeitwerte_und_monatsbasis(): void
    {
        $this->assertSame(1, MCT\Import::time_divisor(570));
        $this->assertSame(60, MCT\Import::time_divisor(34200));
        $this->assertSame(60000, MCT\Import::time_divisor(34200000));
        $u = $this->user();
        $run = 'monatsbasis001';
        MCT\Import::setup($u, $run, ['goals' => [['id' => 7, 'name' => 'G', 'repeat_type' => 1, 'repeat_value' => 127, 'state' => 0]]]);
        MCT\Import::goal_checks($u, $run, [['id' => 1, 'goalId' => 7, 'year' => 2026, 'month' => 0, 'day' => 15]], 0);
        MCT\Import::goal_checks($u, $run, [['id' => 2, 'goalId' => 7, 'year' => 2026, 'month' => 3, 'day' => 15]], 1);
        $this->assertSame(['2026-01-15', '2026-03-15'], MCT\Db::col('SELECT check_date FROM ' . MCT\Db::t('goal_checks') . ' WHERE user_id = %d ORDER BY check_date', [$u]));
        $r = MCT\Import::setup($u, 'unbekannt00001', ['goals' => [['id' => 8, 'name' => 'H', 'repeat_type' => 9]]]);
        $this->assertCount(1, $r['goals']['warnings']);
    }

    public function test_import_in_leeres_tagebuch_fragt_vor_dem_leeren(): void
    {
        $u = $this->user();
        $this->entry($u, '2026-01-01', 2);
        try {
            MCT\Import::start($u, 'leerentest0001', 'new', false);
            $this->fail('Ohne Bestätigung darf nichts gelöscht werden');
        } catch (MCT\Fail $f) {
            $this->assertSame('not_empty', $f->slug);
            $this->assertSame(1, $f->data['count']);
        }
        $this->assertSame(1, MCT\Import::counts($u)['entries']);
        MCT\Import::start($u, 'leerentest0001', 'new', true);
        $this->assertSame(0, MCT\Import::counts($u)['entries']);
    }

    public function test_zusammenfuehren_nutzt_vorhandene_namen(): void
    {
        $u = $this->user();
        $sport = MCT\Setup::tag_save($u, ['name' => 'sport']);
        $this->entry($u, '2020-01-01', 2, ['tag_ids' => [$sport['id']]]);
        MCT\Import::start($u, 'fixturerun0001', 'merge', false);
        $this->importFixture($u, 'fixturerun0001', false);
        $c = MCT\Import::counts($u);
        $this->assertSame($this->expected()['entries'] + 1, $c['entries']);
        $this->assertSame($this->expected()['tags'], $c['tags'], 'vorhandene Aktivität wird wiederverwendet');
        $this->assertSame($this->expected()['moods'], $c['moods'], 'gleichnamige Stimmung wird wiederverwendet, unbenutzte Vorgaben weichen');
    }

    public function test_csv_import_fragt_bei_fehlendem_nach(): void
    {
        $u = $this->user();
        $this->importFixture($u, 'fixturerun0001', false);
        $fh = fopen(dirname(__DIR__) . '/fixtures/sample.csv', 'r');
        $head = fgetcsv($fh, null, ',', '"', '');
        $rows = [];
        while (($r = fgetcsv($fh, null, ',', '"', '')) !== false) {
            $rows[] = array_combine($head, $r);
        }
        $check = MCT\Import::csv_check($u, $rows);
        $this->assertSame(['moods' => ['Entspannt'], 'tags' => ['Sauna'], 'scales' => ['Energie']], $check['missing']);
        $before = MCT\Import::counts($u);
        $r = MCT\Import::csv($u, 'csvlauf0000001', $rows);
        $this->assertSame([2, 1], [$r['created'], $r['errors']], 'ohne Rückfrage wird nichts angelegt');
        $this->assertSame($before['tags'], MCT\Import::counts($u)['tags']);
        $r = MCT\Import::csv($u, 'csvlauf0000001', $rows, ['create_missing' => true, 'mood_levels' => ['Entspannt' => 2]]);
        $this->assertSame([1, 2], [$r['created'], $r['skipped']]);
        $e = array_values(array_filter(MCT\Entries::day($u, '2026-03-01'), fn($x) => $x['time'] === '21:05'));
        $this->assertCount(1, $e);
        $this->assertSame(2, $e[0]['level']);
        $this->assertCount(2, $e[0]['tags']);
        $this->assertSame('Titel', $e[0]['title']);
        $day = MCT\Entries::day($u, '2026-03-02');
        $mine = array_values(array_filter($day, fn($x) => $x['time'] === '20:15'));
        $this->assertCount(2, $mine[0]['scales']);
        $this->assertSame(450, array_column($mine[0]['scales'], 'minutes', 'scale_id')[array_column(MCT\Setup::all($u)['scales'], 'id', 'name')['Schlafdauer']]);
        // Export liefert dieselben Zeilen zurück.
        $csv = MCT\Export::csv($u);
        $this->assertStringContainsString('2026-03-02,"2. März",Montag,20:15,Gut,"Sport | Lesen","Schlafqualität: Gut 3/4 | Schlafdauer: 7:30"', $csv);
        $this->assertStringContainsString('2026-03-01,"1. März",Sonntag,21:05,Entspannt,"Sport | Sauna","Energie: Hoch 4/5",Titel', $csv);
    }

    public function test_sicherung_laesst_sich_vollstaendig_wiederherstellen(): void
    {
        $a = $this->user();
        $this->importFixture($a);
        $backup = json_decode(wp_json_encode(MCT\Export::backup($a)), true);
        $b = $this->user();
        MCT\Account::wipe_diary($b);
        $run = 'wiederherst001';
        MCT\Import::setup($b, $run, array_diff_key($backup, ['dayEntries' => 1, 'goalEntries' => 1, 'assets' => 1]));
        foreach (array_chunk($backup['dayEntries'], 200) as $chunk) {
            MCT\Import::entries($b, $run, $chunk);
        }
        MCT\Import::goal_checks($b, $run, $backup['goalEntries'], 1);
        foreach ($backup['assets'] as $x) {
            MCT\Import::asset($b, $run, (string) $x['id'], file_get_contents(MCT\Assets::file($a, $x['id'])['file']), $x['taken_at'], $x['checksum']);
        }
        $ca = MCT\Import::counts($a);
        $cb = MCT\Import::counts($b);
        $this->assertSame($ca, $cb);
        $this->assertSame(MCT\Export::csv($a), MCT\Export::csv($b), 'zeilengleicher CSV-Export nach der Wiederherstellung');
        $this->assertSame(array_column(MCT\Goals::overview($a), 'total', 'name'), array_column(MCT\Goals::overview($b), 'total', 'name'));
    }

    public function test_pdf_und_zip_export(): void
    {
        $u = $this->user();
        $this->entry($u, '2026-04-01', 2, ['note' => 'Größe: ß und (Klammern)', 'title' => 'Titel']);
        $pdf = MCT\Export::pdf($u);
        $this->assertStringStartsWith('%PDF-1.4', $pdf);
        $this->assertStringContainsString('%%EOF', $pdf);
        $this->assertStringContainsString('\\(Klammern\\)', $pdf);
        $zip = MCT\Export::zip($u, true, true);
        $z = new ZipArchive();
        $this->assertTrue($z->open($zip));
        $this->assertNotFalse($z->locateName('backup.json'));
        $z->close();
        unlink($zip);
    }

    public function test_monatsansicht_mit_grossem_bestand_antwortet_schnell(): void
    {
        $u = $this->user();
        $this->importFixture($u, 'fixturerun0001', false);
        $t = microtime(true);
        $res = $this->rest($u, 'GET', '/home', ['month' => '2026-10']);
        $ms = (microtime(true) - $t) * 1000;
        $this->assertSame(200, $res->get_status());
        $this->assertLessThan(300, $ms, 'Monatsansicht muss lokal unter 300 ms antworten');
    }
}
