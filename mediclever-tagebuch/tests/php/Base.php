<?php
use PHPUnit\Framework\TestCase;

abstract class Base extends TestCase
{
    protected array $users = [];

    protected function user(bool $consent = true): int
    {
        $login = 't' . bin2hex(random_bytes(6));
        $id = wp_create_user($login, wp_generate_password(), $login . '@example.test');
        $this->users[] = $id;
        if ($consent) {
            MCT\Settings::set_raw($id, 'consent_at', MCT\Db::now());
        }
        MCT\Setup::ensure_moods($id);
        return $id;
    }

    protected function tearDown(): void
    {
        require_once ABSPATH . 'wp-admin/includes/user.php';
        foreach ($this->users as $id) {
            MCT\Account::delete_all($id);
            wp_delete_user($id);
        }
        $this->users = [];
        wp_set_current_user(0);
        unset($_SERVER['HTTP_AUTHORIZATION']);
    }

    protected function mood(int $uid, int $level): int
    {
        return (int) MCT\Db::val('SELECT id FROM ' . MCT\Db::t('moods') . ' WHERE user_id = %d AND level = %d ORDER BY id LIMIT 1', [$uid, $level]);
    }

    protected function entry(int $uid, string $date, int $level, array $extra = []): array
    {
        return MCT\Entries::save($uid, array_merge(['date' => $date, 'time' => '12:00', 'mood_id' => $this->mood($uid, $level)], $extra));
    }

    /** REST-Aufruf als bestimmter Nutzer. */
    protected function rest(int $uid, string $method, string $path, array $params = []): WP_REST_Response
    {
        wp_set_current_user($uid);
        $req = new WP_REST_Request($method, '/' . MCT_REST_NS . $path);
        if ($method === 'GET' || $method === 'DELETE') {
            $req->set_query_params($params);
        } else {
            $req->set_header('Content-Type', 'application/json');
            $req->set_body(wp_json_encode($params));
        }
        return rest_get_server()->dispatch($req);
    }

    protected function assertFail(string $slug, callable $fn): void
    {
        try {
            $fn();
        } catch (MCT\Fail $e) {
            $this->assertSame($slug, $e->slug);
            return;
        }
        $this->fail("Erwarteter Fehler „{$slug}“ blieb aus.");
    }

    /** Importiert die künstliche Testdatei so, wie es der Browser tut: entpacken, in Paketen schicken. */
    protected function importFixture(int $uid, string $run = 'fixturerun0001', bool $with_photos = true): array
    {
        $zip = new ZipArchive();
        $zip->open(dirname(__DIR__) . '/fixtures/sample.daylio');
        $json = json_decode(base64_decode($zip->getFromName('backup.daylio')), true);
        $report = ['setup' => MCT\Import::setup($uid, $run, array_diff_key($json, ['dayEntries' => 1, 'goalEntries' => 1, 'assets' => 1]))];
        $report['entries'] = ['created' => 0, 'skipped' => 0, 'errors' => 0];
        foreach (array_chunk($json['dayEntries'], 100) as $chunk) {
            $r = MCT\Import::entries($uid, $run, $chunk);
            foreach (['created', 'skipped', 'errors'] as $k) {
                $report['entries'][$k] += $r[$k];
            }
        }
        $report['checks'] = MCT\Import::goal_checks($uid, $run, $json['goalEntries'], 1);
        $report['assets'] = ['created' => 0, 'skipped' => 0];
        if ($with_photos) {
            $names = [];
            for ($i = 0; $i < $zip->numFiles; $i++) {
                $names[basename($zip->getNameIndex($i))] = $i;
            }
            foreach ($json['assets'] as $a) {
                $r = MCT\Import::asset($uid, $run, (string) $a['id'], $zip->getFromIndex($names[$a['checksum']]), null, $a['checksum']);
                $report['assets'][$r['skipped'] ? 'skipped' : 'created']++;
            }
        }
        $zip->close();
        return $report;
    }
}
