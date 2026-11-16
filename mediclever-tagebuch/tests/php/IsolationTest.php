<?php
/** Strikte Trennung nach Nutzer: Fremde IDs werden gezielt angefragt und müssen wie „nicht vorhanden“ aussehen. */
final class IsolationTest extends Base
{
    public function test_fremde_ids_ueber_rest_sind_nicht_erreichbar(): void
    {
        $a = $this->user();
        $b = $this->user();
        MCT\Setup::apply_pack($a, 'allgemein');
        $setup = MCT\Setup::all($a);
        $entry = $this->entry($a, '2026-05-01', 2, ['note' => 'geheim', 'tag_ids' => [$setup['tags'][0]['id']]]);
        $goal = MCT\Goals::save($a, ['name' => 'Ziel A']);
        $ms = MCT\Milestones::save($a, ['name' => 'Tag A', 'date' => '2020-01-01']);
        $asset = MCT\Assets::store($a, $this->jpeg());
        $tpl = MCT\Setup::template_save($a, ['title' => 'Vorlage A', 'body' => 'x']);
        $rem = MCT\Setup::reminder_save($a, ['time' => '08:00', 'text' => 'x']);

        $attempts = [
            ['GET', "/entries/{$entry['id']}", []], ['POST', "/entries/{$entry['id']}", ['note' => 'überschrieben']], ['DELETE', "/entries/{$entry['id']}", []],
            ['POST', "/moods/{$setup['moods'][0]['id']}", ['name' => 'x']], ['DELETE', "/moods/{$setup['moods'][0]['id']}", []],
            ['POST', "/groups/{$setup['groups'][0]['id']}", ['name' => 'x']], ['DELETE', "/groups/{$setup['groups'][0]['id']}", []],
            ['POST', "/tags/{$setup['tags'][0]['id']}", ['name' => 'x']], ['DELETE', "/tags/{$setup['tags'][0]['id']}", []],
            ['POST', "/tags/{$setup['tags'][0]['id']}/merge", ['into' => $setup['tags'][1]['id']]],
            ['POST', "/scales/{$setup['scales'][0]['id']}", ['name' => 'x']], ['DELETE', "/scales/{$setup['scales'][0]['id']}", []],
            ['POST', "/goals/{$goal['id']}", ['name' => 'x']], ['DELETE', "/goals/{$goal['id']}", []], ['POST', "/goals/{$goal['id']}/check", ['date' => '2026-05-01']],
            ['POST', "/milestones/{$ms['id']}", ['name' => 'x']], ['DELETE', "/milestones/{$ms['id']}", []],
            ['GET', "/assets/{$asset['id']}", []], ['DELETE', "/assets/{$asset['id']}", []],
            ['POST', "/templates/{$tpl['id']}", ['title' => 'x']], ['DELETE', "/templates/{$tpl['id']}", []],
            ['POST', "/reminders/{$rem['id']}", ['time' => '09:00']], ['DELETE', "/reminders/{$rem['id']}", []],
            ['POST', '/archive', ['kind' => 'tags', 'id' => $setup['tags'][0]['id'], 'archived' => true]],
        ];
        foreach ($attempts as [$method, $path, $params]) {
            $res = $this->rest($b, $method, $path, $params);
            $this->assertSame(404, $res->get_status(), "$method $path muss für fremde Nutzer 404 liefern");
        }
        // Nichts wurde verändert.
        $this->assertSame('geheim', MCT\Entries::get($a, $entry['id'])['note']);
        $this->assertCount(count($setup['tags']), MCT\Setup::all($a)['tags']);
        $this->assertSame('Ziel A', MCT\Goals::overview($a)[0]['name']);
    }

    public function test_listen_zeigen_nur_eigene_daten(): void
    {
        $a = $this->user();
        $b = $this->user();
        $this->entry($a, '2026-05-01', 1, ['note' => 'von A']);
        $this->entry($b, '2026-05-01', 5, ['note' => 'von B']);
        $home = $this->rest($b, 'GET', '/home', ['month' => '2026-05'])->get_data();
        $this->assertCount(1, $home['entries']);
        $this->assertSame('von B', $home['entries'][0]['note']);
        $search = $this->rest($b, 'GET', '/entries', ['text' => 'von'])->get_data();
        $this->assertSame(1, $search['total']);
        $stats = $this->rest($b, 'GET', '/stats', ['month' => '2026-05'])->get_data();
        $this->assertSame(1, $stats['count']);
        $this->assertSame(1, $stats['levels'][5]);
    }

    public function test_fremde_verweise_in_eigenen_eintraegen_werden_verworfen(): void
    {
        $a = $this->user();
        $b = $this->user();
        MCT\Setup::apply_pack($a, 'allgemein');
        $foreign_tag = MCT\Setup::all($a)['tags'][0]['id'];
        $foreign_asset = MCT\Assets::store($a, $this->jpeg())['id'];
        $e = $this->entry($b, '2026-05-02', 3, ['tag_ids' => [$foreign_tag], 'asset_ids' => [$foreign_asset]]);
        $this->assertSame([], $e['tags']);
        $this->assertSame([], $e['assets']);
        $this->assertFail('mood_required', fn() => MCT\Entries::save($b, ['date' => '2026-05-02', 'mood_id' => $this->mood($a, 1)]));
    }

    public function test_ohne_anmeldung_gibt_es_nichts(): void
    {
        wp_set_current_user(0);
        $req = new WP_REST_Request('GET', '/' . MCT_REST_NS . '/bootstrap');
        $this->assertSame(401, rest_get_server()->dispatch($req)->get_status());
    }

    public function test_fotos_liegen_nicht_im_oeffentlichen_upload_pfad(): void
    {
        $a = $this->user();
        $asset = MCT\Assets::store($a, $this->jpeg());
        $file = MCT\Assets::file($a, $asset['id'], 's')['file'];
        $this->assertFileExists($file);
        $this->assertStringNotContainsString(wp_get_upload_dir()['basedir'], $file);
        $this->assertFail('not_found', fn() => MCT\Assets::file($this->user(), $asset['id']));
    }

    public function test_aufnahmeort_und_geraetedaten_werden_entfernt(): void
    {
        $a = $this->user();
        // JPEG mit EXIF-Block (APP1) samt erfundener Geräteangabe.
        $jpeg = $this->jpeg();
        $exif = "Exif\0\0II*\0\x08\0\0\0\x01\0\x0f\x01\x02\0\x0c\0\0\0\x1a\0\0\0\0\0\0\0GeraetXYZ99\0";
        $with = substr($jpeg, 0, 2) . "\xFF\xE1" . pack('n', strlen($exif) + 2) . $exif . substr($jpeg, 2);
        $this->assertStringContainsString('GeraetXYZ99', $with);
        $asset = MCT\Assets::store($a, $with);
        foreach (['full', 's', 'm'] as $size) {
            $this->assertStringNotContainsString('GeraetXYZ99', file_get_contents(MCT\Assets::file($a, $asset['id'], $size)['file']));
        }
    }

    private function jpeg(): string
    {
        $im = imagecreatetruecolor(40, 30);
        ob_start();
        imagejpeg($im);
        return ob_get_clean();
    }
}
