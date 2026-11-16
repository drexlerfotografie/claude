<?php
/**
 * Legt künstliche Beispieldaten für ein Testkonto an (Standard: anna). Alle Inhalte sind erfunden.
 * Aufruf: php scripts/seed.php [login] [leer]
 */
$root = dirname(__DIR__);
$_SERVER['HTTP_HOST'] = '127.0.0.1:8888';
$_SERVER['REQUEST_URI'] = '/';
require $root . '/.wp/core/wp-load.php';

$login = $argv[1] ?? 'anna';
$user = get_user_by('login', $login);
if (!$user) { fwrite(STDERR, "Konto fehlt: $login\n"); exit(1); }
$u = (int) $user->ID;
MCT\Account::delete_all($u);
if (($argv[2] ?? '') === 'leer') { echo "Konto $login ist leer.\n"; exit(0); }

MCT\Settings::set_raw($u, 'consent_at', MCT\Db::now());
MCT\Settings::set_raw($u, 'adult', true);
MCT\Setup::apply_pack($u, 'schichtdienst');
MCT\Setup::mood_save($u, ['name' => 'Stolz', 'level' => 1, 'icon' => 'sparkles']);
MCT\Setup::mood_save($u, ['name' => 'Erschöpft', 'level' => 4, 'icon' => 'battery-low']);
$setup = MCT\Setup::all($u);
$moods = [];
foreach ($setup['moods'] as $m) { $moods[$m['level']][] = $m['id']; }
$tags = array_column($setup['tags'], 'id', 'name');
$scales = array_column($setup['scales'], null, 'name');
mt_srand(4711);
$today = MCT\Settings::today($u);
$notes = ['Ruhige Schicht, zwei Einsätze.', 'Fortbildung zu Beatmung, viel mitgenommen.', '<b>Guter Tag.</b> Nach dem Dienst noch eine Runde gelaufen.', 'Wenig geschlafen, trotzdem durchgehalten.',
    '<ul><li>Einkauf erledigt</li><li>Wäsche</li><li>Früh ins Bett</li></ul>', 'Abends mit Freunden gekocht.', 'Langer Dienst. Kopf voll.', ''];
$pick = fn(array $a) => $a[mt_rand(0, count($a) - 1)];
$jpeg = function (int $seed) {
    $im = imagecreatetruecolor(900, 675);
    for ($y = 0; $y < 675; $y += 5) {
        imagefilledrectangle($im, 0, $y, 900, $y + 5, imagecolorallocate($im, 30 + ($seed * 37) % 60 + intdiv($y, 6), 80 + ($seed * 53) % 80 + intdiv($y, 9), 140 + ($seed * 29) % 60));
    }
    imagefilledellipse($im, 620 - $seed * 20, 180, 150, 150, imagecolorallocate($im, 250, 220, 150));
    imagefilledpolygon($im, [0, 675, 300, 380 + $seed * 5, 520, 520, 760, 400, 900, 560, 900, 675], imagecolorallocate($im, 40, 70, 60));
    ob_start();
    imagejpeg($im, null, 82);
    return ob_get_clean();
};
$photo = 0;
for ($d = 200; $d >= 0; $d--) {
    $date = date('Y-m-d', strtotime("-$d days", strtotime($today)));
    if ($d > 0 && mt_rand(0, 9) < 2 && $d !== 365) { continue; }
    $n = mt_rand(0, 6) === 0 ? 2 : 1;
    for ($k = 0; $k < $n; $k++) {
        $level = $pick([1, 2, 2, 2, 3, 3, 3, 4, 4, 5]);
        $shift = $pick(['Frühdienst', 'Spätdienst', 'Nachtdienst', 'Frei', 'Frei']);
        $t = [$tags[$shift]];
        foreach (['Sport', 'Familie', 'Freunde', 'Draußen', 'Gut gegessen', 'Ausgeschlafen', 'Kopfschmerzen', 'Entspannung'] as $name) {
            $bias = in_array($name, ['Sport', 'Draußen', 'Ausgeschlafen'], true) ? ($level <= 2 ? 5 : 1) : ($name === 'Kopfschmerzen' ? ($level >= 4 ? 5 : 0) : 2);
            if (mt_rand(0, 9) < $bias) { $t[] = $tags[$name]; }
        }
        $sv = [['scale_id' => $scales['Schlafqualität']['id'], 'point_id' => $scales['Schlafqualität']['points'][max(0, min(3, 4 - $level + mt_rand(-1, 1)))]['id']],
            ['scale_id' => $scales['Schlafdauer']['id'], 'minutes' => 15 * mt_rand(20, 34)]];
        if (mt_rand(0, 1)) { $sv[] = ['scale_id' => $scales['Belastung im Dienst']['id'], 'point_id' => $scales['Belastung im Dienst']['points'][mt_rand(0, 3)]['id']]; }
        $assets = [];
        if ($photo < 9 && ($d === 0 || $d % 23 === 0) && $k === 0) {
            $assets[] = MCT\Assets::store($u, $jpeg($photo++))['id'];
            if ($d === 0) { $assets[] = MCT\Assets::store($u, $jpeg($photo++))['id']; }
        }
        MCT\Entries::save($u, ['date' => $date, 'time' => sprintf('%02d:%02d', $k ? 21 : $pick([7, 13, 18, 20]), mt_rand(0, 59)), 'mood_id' => $pick($moods[$level]), 'tag_ids' => $t,
            'scales' => $sv, 'note' => $d === 0 ? 'Ruhiger Tag. Nach dem Frühdienst spazieren gewesen.' : $pick($notes), 'title' => $d === 46 ? 'Prüfung bestanden' : '',
            'favorite' => $d === 46 || $d === 3, 'asset_ids' => $assets]);
    }
}
// Einträge vom selben Tag in früheren Jahren für den Rückblick.
foreach ([1, 2] as $y) {
    MCT\Entries::save($u, ['date' => date('Y-m-d', strtotime("-$y years", strtotime($today))), 'time' => '19:30', 'mood_id' => $moods[2][0], 'tag_ids' => [$tags['Freunde']], 'note' => 'Schöner Abend am See.']);
}
$g1 = MCT\Goals::save($u, ['name' => 'Bewegung', 'tag_id' => $tags['Sport'], 'freq_type' => 'weekly', 'freq_count' => 3, 'start_date' => date('Y-m-d', strtotime('-120 days', strtotime($today)))]);
$g2 = MCT\Goals::save($u, ['name' => '2 Liter trinken', 'freq_type' => 'daily', 'reminder_time' => '18:00', 'start_date' => date('Y-m-d', strtotime('-60 days', strtotime($today)))]);
MCT\Goals::save($u, ['name' => 'Lernen', 'freq_type' => 'days', 'freq_days' => [1, 3, 5], 'start_date' => date('Y-m-d', strtotime('-30 days', strtotime($today)))]);
for ($d = 60; $d >= 1; $d--) {
    $date = date('Y-m-d', strtotime("-$d days", strtotime($today)));
    if ($d <= 6 || mt_rand(0, 9) < 7) { MCT\Goals::check($u, $g2['id'], $date, true); }
}
foreach (MCT\Db::col('SELECT e.local_date FROM ' . MCT\Db::t('entries') . ' e JOIN ' . MCT\Db::t('entry_tags') . ' t ON t.entry_id = e.id WHERE e.user_id = %d AND t.tag_id = %d', [$u, $tags['Sport']]) as $date) {
    MCT\Goals::check($u, $g1['id'], $date, true);
}
$in5 = date('m-d', strtotime('+5 days', strtotime($today)));
MCT\Milestones::save($u, ['name' => 'Examen bestanden', 'date' => '2021-' . $in5, 'category' => 'Beruf', 'note' => 'Drei Jahre Ausbildung geschafft.', 'yearly' => true, 'remind_same_day' => true,
    'asset_id' => MCT\Assets::store($u, $jpeg(11))['id']]);
MCT\Milestones::save($u, ['name' => 'Umzug', 'date' => '2023-02-01', 'category' => 'Privat', 'yearly' => true]);
MCT\Setup::reminder_save($u, ['time' => '20:00', 'text' => 'Wie war dein Tag?']);
MCT\Setup::reminder_save($u, ['time' => '07:30', 'text' => '', 'active' => false]);
MCT\Settings::set_raw($u, 'onboarded', true);
$c = MCT\Import::counts($u);
echo "Beispieldaten für $login: {$c['entries']} Einträge, {$c['assets']} Fotos, {$c['goal_checks']} Ziel-Haken\n";
