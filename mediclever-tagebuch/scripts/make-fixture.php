<?php
/**
 * Erzeugt tests/fixtures/sample.daylio: eine künstliche Sicherung im selben Aufbau wie das echte Backup
 * (ZIP mit Base64-JSON und Fotos). Alle Inhalte sind erfunden. Deckt die bekannten Stolperstellen ab.
 */
mt_srand(20261116);
$out = __DIR__ . '/../tests/fixtures';
@mkdir($out, 0777, true);

$moods = [];
$names = [1 => ['Richtig gut', 'Stolz'], 2 => ['Gut'], 3 => ['Geht so'], 4 => ['Mies'], 5 => ['Ganz unten']];
$mid = 1;
foreach ($names as $level => $list) {
    foreach ($list as $i => $n) {
        $moods[] = ['id' => $mid++, 'custom_name' => $n, 'mood_group_id' => $level, 'mood_group_order' => $i, 'icon_id' => $level, 'predefined_name_id' => -1, 'state' => 0, 'createdAt' => 1500000000000];
    }
}
$groups = [
    ['id' => 1, 'name' => 'Dienst', 'is_expanded' => true, 'order' => 1, 'color' => 3],
    ['id' => 2, 'name' => 'Freizeit ', 'is_expanded' => false, 'order' => 2, 'color' => 7],
    ['id' => 3, 'name' => '🏡 Zuhause', 'is_expanded' => true, 'order' => 4, 'color' => 14],
];
$tagdefs = [[1, 'Frühdienst'], [1, 'Nachtdienst'], [1, 'Fortbildung '], [2, 'Sport'], [2, 'Lesen'], [2, 'Freunde'], [2, 'Kino'], [3, 'Kochen'], [3, 'Putzen'],
    [3, 'Garten'], [0, 'Ohne Gruppe'], [2, 'Altes Hobby'], [1, 'Alte Wache']];
$tags = [];
foreach ($tagdefs as $i => [$g, $n]) {
    $tags[] = ['id' => 100 + $i, 'name' => $n, 'createdAt' => 1500000000000, 'icon' => 40 + $i, 'order' => $i, 'state' => $i >= 11 ? 1 : 0, 'id_tag_group' => $g];
}
$scales = [
    ['id' => 1, 'name' => 'Schlafqualität', 'order' => 3, 'state' => 0, 'trend' => 'up', 'chart_type' => 'avg', 'color' => 5,
        'values' => [['id' => 11, 'name' => 'Schlecht', 'order' => 1], ['id' => 12, 'name' => 'Mäßig', 'order' => 2], ['id' => 13, 'name' => 'Gut', 'order' => 3], ['id' => 14, 'name' => 'Sehr gut', 'order' => 4]]],
    ['id' => 2, 'name' => 'Stress', 'order' => 5, 'state' => 0, 'trend' => 'down', 'chart_type' => 'avg', 'color' => 2,
        'values' => [['id' => 21, 'name' => 'Kaum', 'order' => 1], ['id' => 22, 'name' => 'Etwas', 'order' => 2], ['id' => 23, 'name' => 'Viel', 'order' => 3], ['id' => 24, 'name' => 'Sehr viel', 'order' => 4], ['id' => 25, 'name' => 'Zu viel', 'order' => 5]]],
    ['id' => 3, 'name' => 'Schlafdauer', 'order' => 6, 'state' => 0, 'trend' => 'up', 'chart_type' => 'avg', 'color' => 9, 'type' => 'time'],
];
$goals = [
    ['id' => 1, 'goal_id' => 1, 'name' => 'Bewegung', 'id_tag' => 103, 'created_at' => 1730000000000, 'reminder_enabled' => true, 'reminder_hour' => 18, 'reminder_minute' => 30, 'state' => 0, 'repeat_type' => 1, 'repeat_value' => 127, 'order' => 1, 'note' => ''],
    ['id' => 2, 'goal_id' => 2, 'name' => 'Lesen', 'id_tag' => 104, 'created_at' => 1730000000000, 'reminder_enabled' => false, 'state' => 0, 'repeat_type' => 2, 'repeat_value' => 3, 'order' => 2, 'note' => ''],
    ['id' => 3, 'goal_id' => 3, 'name' => 'Altes Ziel', 'id_tag' => 0, 'created_at' => 1700000000000, 'reminder_enabled' => false, 'state' => 1, 'repeat_type' => 1, 'repeat_value' => 127, 'order' => 3, 'note' => ''],
];

$jpeg = function (int $seed) {
    $im = imagecreatetruecolor(640, 480);
    imagefill($im, 0, 0, imagecolorallocate($im, ($seed * 53) % 200 + 30, ($seed * 97) % 200 + 30, ($seed * 31) % 200 + 30));
    for ($i = 0; $i < 6; $i++) {
        imagefilledellipse($im, mt_rand(0, 640), mt_rand(0, 480), mt_rand(40, 200), mt_rand(40, 200), imagecolorallocate($im, mt_rand(0, 255), mt_rand(0, 255), mt_rand(0, 255)));
    }
    ob_start();
    imagejpeg($im, null, 70);
    imagedestroy($im);
    return ob_get_clean();
};

$notes = ['Ruhiger Tag.', 'Viel los, aber gut geschafft.', '<b>Wichtig:</b> früher ins Bett.', 'Erste Zeile<br>Zweite Zeile', '<ul><li>Einkauf</li><li>Wäsche</li></ul>',
    'Text&nbsp;mit geschütztem Leerzeichen', 'Langer Spaziergang am Abend, danach gekocht.', '<script>alert(1)</script>Harmloser Rest'];
$offsets = [3600000, 7200000, 10800000];
$entries = [];
$assets = [];
$files = [];
$eid = 1;
$aid = 1;
$levelcount = [1 => 0, 2 => 0, 3 => 0, 4 => 0, 5 => 0];
$stats = ['with_note' => 0, 'with_title' => 0, 'favorites' => 0, 'asset_links' => 0];
$start = strtotime('2025-10-01 00:00:00 UTC');
$dates = [];
for ($d = 0; $d < 412; $d++) {
    $n = [0, 1, 1, 1, 1, 2, 1, 0, 1, 3][mt_rand(0, 9)];
    if ($d === 411) { $n = 1; }
    for ($k = 0; $k < $n; $k++) {
        $day = $start + $d * 86400;
        $off = $offsets[($d < 26 || ($d > 180 && $d < 390)) ? 1 : 0];
        if ($eid % 37 === 0) { $off = $offsets[2]; }
        $h = [7, 12, 18, 22, 23][mt_rand(0, 4)];
        $mi = mt_rand(0, 59);
        if ($eid === 5) { $h = 23; $mi = 58; }
        $local = $day + $h * 3600 + $mi * 60;
        $utc_ms = ($local * 1000) - $off;
        $mood = $moods[[0, 1, 2, 2, 2, 3, 3, 3, 4, 5][mt_rand(0, 9)]];
        $levelcount[$mood['mood_group_id']]++;
        $etags = [];
        foreach ($tags as $t) {
            if (mt_rand(0, 9) < 2 && ($t['state'] === 0 || $d < 60)) { $etags[] = $t['id']; }
        }
        if (mt_rand(0, 4) === 0) { $etags[] = 103; }
        $etags = array_values(array_unique($etags));
        $note = mt_rand(0, 9) < 3 ? $notes[mt_rand(0, count($notes) - 1)] : '';
        $title = ($eid === 9) ? 'Ein Titel' : '';
        $fav = mt_rand(0, 30) === 0;
        $ea = [];
        if (mt_rand(0, 11) === 0 && $aid <= 14) {
            $cnt = $aid === 1 ? 3 : 1;
            for ($c = 0; $c < $cnt; $c++) {
                $sum = md5('foto' . $aid);
                $assets[] = ['id' => $aid, 'checksum' => $sum, 'type' => 1, 'createdAt' => $utc_ms, 'createdAtOffset' => $off, 'android_metadata' => '{}'];
                $files['/assets/photos/' . gmdate('Y/n', $local) . '/' . $sum] = $jpeg($aid);
                $ea[] = $aid++;
            }
        }
        $sv = [];
        if (mt_rand(0, 2) > 0) { $sv[] = ['id_scale' => 1, 'id_text_scale_value' => 10 + mt_rand(1, 4), 'time_value' => 0]; }
        if (mt_rand(0, 3) === 0) { $sv[] = ['id_scale' => 2, 'id_text_scale_value' => 20 + mt_rand(1, 5), 'time_value' => 0]; }
        if (mt_rand(0, 2) === 0) { $sv[] = ['id_scale' => 3, 'id_text_scale_value' => 0, 'time_value' => mt_rand(20, 38) * 15]; }
        $entries[] = ['id' => $eid, 'minute' => $mi, 'hour' => $h, 'day' => (int) gmdate('j', $local), 'month' => (int) gmdate('n', $local) - 1, 'year' => (int) gmdate('Y', $local),
            'datetime' => $utc_ms, 'timeZoneOffset' => $off, 'mood' => $mood['id'], 'note' => $note, 'note_title' => $title, 'tags' => $etags, 'assets' => $ea,
            'isFavorite' => $fav, 'scale_values' => $sv];
        if ($note !== '') { $stats['with_note']++; }
        if ($title !== '') { $stats['with_title']++; }
        if ($fav) { $stats['favorites']++; }
        $stats['asset_links'] += count($ea);
        $dates[] = gmdate('Y-m-d', $local);
        $eid++;
    }
}
// Zwei Fotos ohne Eintrag: eines gehört zu einem wichtigen Tag, eines ist verwaist.
foreach ([$aid, $aid + 1] as $x) {
    $sum = md5('foto' . $x);
    $assets[] = ['id' => $x, 'checksum' => $sum, 'type' => 1, 'createdAt' => 1760000000000, 'createdAtOffset' => 7200000, 'android_metadata' => '{}'];
    $files['/assets/photos/2025/10/' . $sum] = $jpeg($x);
}
$milestones = [
    ['id' => 1, 'name' => 'Examen', 'day' => 14, 'month' => 3, 'year' => 2019, 'category' => 'Beruf', 'note' => 'Geschafft', 'is_yearly' => true, 'id_asset' => $aid, 'reminders' => [['days_before' => 0], ['days_before' => 7]]],
    ['id' => 2, 'name' => 'Umzug', 'day' => 1, 'month' => 1, 'year' => 2022, 'category' => 'Privat', 'note' => '', 'is_yearly' => true, 'reminders' => []],
    ['id' => 3, 'name' => 'Schalttag', 'day' => 29, 'month' => 2, 'year' => 2024, 'category' => '', 'note' => '', 'is_yearly' => true, 'reminders' => []],
];
$goalEntries = [];
$gid = 1;
for ($d = 300; $d < 412; $d++) {
    $ts = $start + $d * 86400;
    if (mt_rand(0, 2) > 0) {
        $goalEntries[] = ['id' => $gid++, 'goalId' => 1, 'year' => (int) gmdate('Y', $ts), 'month' => (int) gmdate('n', $ts), 'day' => (int) gmdate('j', $ts), 'hour' => 20, 'minute' => 0, 'second' => 0];
    }
    if (mt_rand(0, 3) === 0) {
        $goalEntries[] = ['id' => $gid++, 'goalId' => 2, 'year' => (int) gmdate('Y', $ts), 'month' => (int) gmdate('n', $ts), 'day' => (int) gmdate('j', $ts), 'hour' => 21, 'minute' => 0, 'second' => 0];
    }
}
$json = ['version' => 15, 'isReminderOn' => true, 'customMoods' => $moods, 'tags' => $tags, 'tag_groups' => $groups, 'scales' => $scales, 'dayEntries' => array_reverse($entries),
    'achievements' => [['id' => 1, 'name' => 'x']], 'daysInRowLongestChain' => 5, 'goals' => $goals, 'goalEntries' => $goalEntries,
    'goalSuccessWeeks' => [['goal_id' => 1, 'year' => 2026, 'week' => 40]], 'milestones' => $milestones, 'assets' => $assets,
    'writingTemplates' => [['id' => 1, 'order' => 1, 'title' => 'Abendrunde', 'body' => '<b>Gut war:</b><br><br><b>Schwer war:</b>'], ['id' => 2, 'order' => 2, 'title' => 'Dienst', 'body' => 'Wie lief es?']],
    'reminders' => [['id' => 1, 'hour' => 20, 'minute' => 0, 'state' => 0, 'customTextEnabled' => true, 'customText' => 'Wie war dein Tag?'], ['id' => 2, 'hour' => 8, 'minute' => 15, 'state' => 1, 'customTextEnabled' => false, 'customText' => '']],
    'prefs' => [['key' => 'FIRST_DAY_OF_WEEK', 'pref_name' => 'default', 'value' => 2], ['key' => 'COLOR_MODE', 'pref_name' => 'default', 'value' => 'dark']],
    'metadata' => ['number_of_entries' => count($entries), 'created_at' => 1794787200000, 'is_auto_backup' => false, 'platform' => 'android', 'android_version' => 36,
        'number_of_photos' => count($assets), 'photos_size' => array_sum(array_map('strlen', $files))]];

$zipfile = $out . '/sample.daylio';
@unlink($zipfile);
$zip = new ZipArchive();
$zip->open($zipfile, ZipArchive::CREATE);
$zip->addFromString('backup.daylio', base64_encode(json_encode($json, JSON_UNESCAPED_UNICODE)));
foreach ($files as $name => $bytes) {
    $zip->addFromString($name, $bytes);
}
$zip->close();

sort($dates);
$expected = ['entries' => count($entries), 'levels' => $levelcount, 'with_note' => $stats['with_note'], 'with_title' => $stats['with_title'], 'favorites' => $stats['favorites'],
    'assets' => count($assets), 'asset_links' => $stats['asset_links'], 'moods' => count($moods), 'tags' => count($tags), 'tags_archived' => 2, 'groups' => count($groups),
    'scales' => count($scales), 'goals' => count($goals), 'goals_archived' => 1, 'goal_checks' => count($goalEntries), 'milestones' => count($milestones),
    'templates' => 2, 'reminders' => 2, 'first_entry' => $dates[0], 'last_entry' => end($dates)];
file_put_contents($out . '/sample.expected.json', json_encode($expected, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) . "\n");

// CSV in den Spalten des Exports, mit erfundenen Zeilen.
$csv = fopen($out . '/sample.csv', 'w');
fputcsv($csv, ['full_date', 'date', 'weekday', 'time', 'mood', 'activities', 'scales', 'note_title', 'note'], ',', '"', '');
fputcsv($csv, ['2026-03-02', '2. März', 'Montag', '20:15', 'Gut', 'Sport | Lesen', 'Schlafqualität: Gut 3/4 | Schlafdauer: 7:30', '', 'Kurze Notiz, mit Komma'], ',', '"', '');
fputcsv($csv, ['2026-03-01', '1. März', 'Sonntag', '9:05 PM', 'Entspannt', 'Sauna | Sport', 'Energie: Hoch 4/5', 'Titel', "Zeile eins\nZeile zwei"], ',', '"', '');
fputcsv($csv, ['2026-02-28', '28. Februar', 'Samstag', '08:00', 'Geht so', '', '', '', ''], ',', '"', '');
fclose($csv);
echo 'Testdatei erzeugt: ' . count($entries) . ' Einträge, ' . count($assets) . ' Fotos, ' . round(filesize($zipfile) / 1024) . " kB\n";
