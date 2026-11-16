<?php
namespace MCT;

/** REST-Schnittstelle mc-tagebuch/v1. Anmeldung über das WordPress-Konto mit Nonce; jede Abfrage ist auf den angemeldeten Nutzer begrenzt. */
final class Rest
{
    public static function init(): void
    {
        add_action('rest_api_init', [self::class, 'routes']);
    }

    /** Nur angemeldete Nutzer mit gültigem Nonce (Cookie-Anmeldung). Token der KI-Freigabe gelten hier nicht. */
    public static function allowed(): bool
    {
        return is_user_logged_in() && current_user_can('read');
    }

    private static function route(string $method, string $path, callable $fn, ?string $feature = null): void
    {
        register_rest_route(MCT_REST_NS, $path, [
            'methods' => $method,
            'permission_callback' => [self::class, 'allowed'],
            'callback' => static function (\WP_REST_Request $req) use ($fn, $feature) {
                $uid = get_current_user_id();
                Audit::$source = 'app';
                try {
                    if ($feature) {
                        Features::need($feature, $uid);
                    }
                    $out = $fn($uid, $req);
                    $res = rest_ensure_response($out ?? ['ok' => true]);
                    $res->header('Cache-Control', 'no-store, private');
                    return $res;
                } catch (Fail $e) {
                    return new \WP_Error('mct_' . $e->slug, $e->getMessage(), array_merge(['status' => $e->status], $e->data));
                } catch (\Throwable $e) {
                    // Keine Inhalte in Logs oder Fehlermeldungen: nur Ort des Fehlers.
                    error_log('MCT Fehler in ' . basename($e->getFile()) . ':' . $e->getLine() . ' (' . get_class($e) . ')');
                    return new \WP_Error('mct_error', 'Das hat nicht geklappt. Bitte versuch es noch einmal.', ['status' => 500]);
                }
            },
        ]);
    }

    private static function body(\WP_REST_Request $r): array
    {
        $j = $r->get_json_params();
        return is_array($j) ? $j : (array) $r->get_body_params();
    }

    private static function ids($v): array
    {
        return array_values(array_filter(array_map('intval', is_array($v) ? $v : explode(',', (string) $v))));
    }

    public static function bootstrap(int $uid): array
    {
        $user = get_userdata($uid);
        return [
            'user' => ['name' => $user->display_name],
            'settings' => Settings::get($uid),
            'features' => Features::all($uid),
            'setup' => Setup::all($uid),
            'months' => Entries::months($uid),
            'today' => Settings::today($uid),
            'goals' => Features::enabled('goals', $uid) ? Goals::overview($uid) : [],
            'milestones' => Features::enabled('milestones', $uid) ? Milestones::all($uid) : [],
            'packs' => array_map(fn($k, $p) => ['key' => $k, 'label' => $p['label'], 'groups' => array_map(fn($g) => ['name' => $g[0], 'tags' => array_column($g[2], 0)], $p['groups']),
                'scales' => array_column($p['scales'], 1)], array_keys(Setup::PACKS), Setup::PACKS),
            'push_key' => Reminders::public_key(),
            'mcp_url' => rest_url(MCT_REST_NS . '/mcp'),
            'version' => MCT_VERSION,
        ];
    }

    private static function send_file(string $mime, string $name, string $body = '', ?string $file = null, bool $inline = false): void
    {
        nocache_headers();
        header('Content-Type: ' . $mime);
        header('X-Content-Type-Options: nosniff');
        header('Content-Disposition: ' . ($inline ? 'inline' : 'attachment') . '; filename="' . $name . '"');
        header('Cache-Control: private, max-age=' . ($inline ? '86400' : '0'));
        header_remove('Pragma');
        header_remove('Expires');
        if ($file) {
            header('Content-Length: ' . filesize($file));
            readfile($file);
        } else {
            header('Content-Length: ' . strlen($body));
            echo $body; // phpcs:ignore
        }
        exit;
    }

    public static function routes(): void
    {
        $id = '(?P<id>\d+)';
        self::route('GET', '/bootstrap', fn($u) => self::bootstrap($u));

        // Einwilligungen
        self::route('POST', '/consent', function ($u, $r) {
            $b = self::body($r);
            if (!empty($b['consent'])) {
                if (empty($b['adult'])) {
                    throw new Fail('adult_required', 'Das Tagebuch ist für Nutzer ab 18 Jahren.');
                }
                Settings::set_raw($u, 'consent_at', Db::now());
                Settings::set_raw($u, 'adult', true);
                Audit::log($u, 'consent_given');
            } else {
                Settings::set_raw($u, 'consent_at', null);
                Audit::log($u, 'consent_revoked');
            }
            return Settings::get($u);
        });
        self::route('POST', '/consent/ai', function ($u, $r) {
            $on = !empty(self::body($r)['consent']);
            Settings::set_raw($u, 'ai_consent_at', $on ? Db::now() : null);
            if (!$on) {
                OAuth::revoke_all($u);
            }
            Audit::log($u, $on ? 'ai_consent_given' : 'ai_consent_revoked');
            return Settings::get($u);
        }, 'mcp');
        self::route('POST', '/settings', fn($u, $r) => Settings::update($u, self::body($r)));
        self::route('POST', '/onboarding', function ($u, $r) {
            Setup::apply_pack($u, (string) (self::body($r)['pack'] ?? 'leer'));
            return self::bootstrap($u);
        });

        // Einrichtung
        self::route('POST', '/moods', fn($u, $r) => Setup::mood_save($u, self::body($r)));
        self::route('POST', "/moods/$id", fn($u, $r) => Setup::mood_save($u, self::body($r), (int) $r['id']));
        self::route('DELETE', "/moods/$id", function ($u, $r) {
            Setup::mood_delete($u, (int) $r['id'], $r['replace_with'] ? (int) $r['replace_with'] : null);
        });
        self::route('POST', '/groups', fn($u, $r) => Setup::group_save($u, self::body($r)));
        self::route('POST', "/groups/$id", fn($u, $r) => Setup::group_save($u, self::body($r), (int) $r['id']));
        self::route('DELETE', "/groups/$id", function ($u, $r) { Setup::group_delete($u, (int) $r['id']); });
        self::route('POST', '/tags', fn($u, $r) => Setup::tag_save($u, self::body($r)));
        self::route('POST', "/tags/$id", fn($u, $r) => Setup::tag_save($u, self::body($r), (int) $r['id']));
        self::route('DELETE', "/tags/$id", function ($u, $r) { Setup::tag_delete($u, (int) $r['id']); });
        self::route('POST', "/tags/$id/merge", function ($u, $r) { Setup::tag_merge($u, (int) $r['id'], (int) (self::body($r)['into'] ?? 0)); });
        self::route('POST', '/scales', fn($u, $r) => Setup::scale_save($u, self::body($r)), 'scales');
        self::route('POST', "/scales/$id", fn($u, $r) => Setup::scale_save($u, self::body($r), (int) $r['id']), 'scales');
        self::route('DELETE', "/scales/$id", function ($u, $r) { Setup::scale_delete($u, (int) $r['id']); }, 'scales');
        self::route('POST', '/archive', function ($u, $r) {
            $b = self::body($r);
            Setup::archive($u, (string) ($b['kind'] ?? ''), (int) ($b['id'] ?? 0), !empty($b['archived']));
        });
        self::route('GET', '/usage', fn($u, $r) => ['count' => Setup::usage($u, (string) $r['kind'], (int) $r['id'])]);
        self::route('POST', '/reorder', function ($u, $r) {
            $b = self::body($r);
            Setup::reorder($u, (string) ($b['kind'] ?? ''), (array) ($b['ids'] ?? []), array_key_exists('group_id', $b) ? $b['group_id'] : false);
        });
        self::route('POST', '/reorder-shared', function ($u, $r) { Setup::reorder_shared($u, (array) (self::body($r)['items'] ?? [])); });
        self::route('GET', '/setup', fn($u) => Setup::all($u));
        self::route('POST', '/templates', fn($u, $r) => Setup::template_save($u, self::body($r)), 'templates');
        self::route('POST', "/templates/$id", fn($u, $r) => Setup::template_save($u, self::body($r), (int) $r['id']), 'templates');
        self::route('DELETE', "/templates/$id", function ($u, $r) { Setup::template_delete($u, (int) $r['id']); }, 'templates');
        self::route('POST', '/templates/reset', function ($u) { Setup::templates_reset($u); return Setup::all($u)['templates']; }, 'templates');
        self::route('POST', '/reminders', fn($u, $r) => Setup::reminder_save($u, self::body($r)), 'reminders');
        self::route('POST', "/reminders/$id", fn($u, $r) => Setup::reminder_save($u, self::body($r), (int) $r['id']), 'reminders');
        self::route('DELETE', "/reminders/$id", function ($u, $r) { Setup::reminder_delete($u, (int) $r['id']); }, 'reminders');

        // Einträge
        self::route('GET', '/home', function ($u, $r) {
            $month = In::month((string) $r['month']);
            $today = Settings::today($u);
            $out = ['month' => $month, 'today' => $today, 'entries' => Entries::month($u, $month), 'months' => Entries::months($u),
                'goals' => Features::enabled('goals', $u) ? Goals::overview($u, $month, false) : [],
                'milestones' => Features::enabled('milestones', $u) ? Milestones::all($u) : [], 'lookback' => null, 'streak' => Stats::streaks($u)];
            if (Features::enabled('lookback', $u) && Settings::get($u)['lookback']) {
                $lb = Stats::lookback($u, $today);
                $out['lookback'] = $lb['years'] ? ['years_ago' => $lb['years'][0]['years_ago'], 'count' => count($lb['years'][0]['entries']), 'years' => count($lb['years'])] : null;
            }
            return $out;
        });
        self::route('GET', '/entries', function ($u, $r) {
            if ($r['date']) {
                return ['entries' => Entries::day($u, In::date($r['date']))];
            }
            Features::need('search', $u);
            return Entries::search($u, ['from' => $r['from'], 'to' => $r['to'], 'text' => $r['text'], 'mood_ids' => self::ids($r['moods']),
                'tag_ids' => self::ids($r['tags']), 'favorite' => $r['favorite'], 'has_photo' => $r['has_photo'], 'page' => $r['page'], 'full' => true]);
        });
        self::route('POST', '/entries', fn($u, $r) => Entries::save($u, array_merge(self::body($r), ['source' => 'app'])));
        self::route('GET', "/entries/$id", fn($u, $r) => Entries::get($u, (int) $r['id']));
        self::route('POST', "/entries/$id", fn($u, $r) => Entries::save($u, self::body($r), (int) $r['id']));
        self::route('DELETE', "/entries/$id", function ($u, $r) { Entries::delete($u, (int) $r['id']); });

        // Dateien
        self::route('POST', '/assets', function ($u, $r) {
            $f = $r->get_file_params()['file'] ?? null;
            if (!$f || !empty($f['error']) || !is_uploaded_file($f['tmp_name'])) {
                throw new Fail('bad_file', 'Die Datei ist nicht angekommen. Vielleicht ist sie zu groß.', 400);
            }
            return Assets::store($u, (string) file_get_contents($f['tmp_name']));
        }, 'photos');
        self::route('GET', "/assets/$id", function ($u, $r) {
            $f = Assets::file($u, (int) $r['id'], (string) ($r['size'] ?: 'full'));
            self::send_file($f['mime'], 'datei-' . (int) $r['id'], '', $f['file'], true);
        });
        self::route('DELETE', "/assets/$id", function ($u, $r) { Assets::delete($u, (int) $r['id']); });
        self::route('GET', '/photos', fn($u, $r) => Assets::gallery($u, max(1, (int) $r['page'])), 'photos');

        // Ziele und wichtige Tage
        self::route('GET', '/goals', fn($u, $r) => Goals::overview($u, $r['month'] ? In::month($r['month']) : null), 'goals');
        self::route('POST', '/goals', fn($u, $r) => Goals::save($u, self::body($r)), 'goals');
        self::route('POST', "/goals/$id", fn($u, $r) => Goals::save($u, self::body($r), (int) $r['id']), 'goals');
        self::route('DELETE', "/goals/$id", function ($u, $r) { Goals::delete($u, (int) $r['id']); }, 'goals');
        self::route('POST', "/goals/$id/check", function ($u, $r) {
            $b = self::body($r);
            Goals::check($u, (int) $r['id'], (string) ($b['date'] ?? Settings::today($u)), !isset($b['done']) || $b['done']);
            return Goals::overview($u, substr((string) ($b['date'] ?? Settings::today($u)), 0, 7), false);
        }, 'goals');
        self::route('GET', '/milestones', fn($u) => Milestones::all($u), 'milestones');
        self::route('POST', '/milestones', fn($u, $r) => Milestones::save($u, self::body($r)), 'milestones');
        self::route('POST', "/milestones/$id", fn($u, $r) => Milestones::save($u, self::body($r), (int) $r['id']), 'milestones');
        self::route('DELETE', "/milestones/$id", function ($u, $r) { Milestones::delete($u, (int) $r['id']); }, 'milestones');

        // Auswertungen
        self::route('GET', '/stats', fn($u, $r) => $r['year'] ? Stats::year($u, (int) $r['year']) : Stats::month($u, In::month((string) $r['month'])), 'stats');
        self::route('GET', '/calendar', fn($u, $r) => Stats::calendar($u, In::month((string) $r['month']), self::ids($r['moods']), self::ids($r['tags'])), 'calendar');
        self::route('GET', '/report', fn($u, $r) => Stats::report($u, (string) ($r['type'] ?: 'week'), (string) ($r['date'] ?: Settings::today($u))), 'reports');
        self::route('GET', '/lookback', fn($u, $r) => Stats::lookback($u, (string) ($r['date'] ?: Settings::today($u))), 'lookback');
        self::route('GET', '/achievements', fn($u) => Stats::achievements($u), 'achievements');
        self::route('GET', '/counts', fn($u) => Import::counts($u));

        // Import
        self::route('POST', '/import/start', function ($u, $r) {
            $b = self::body($r);
            return Import::start($u, (string) ($b['run_key'] ?? ''), ($b['mode'] ?? 'merge') === 'new' ? 'new' : 'merge', !empty($b['confirm_wipe']));
        }, 'import');
        self::route('GET', '/import/status', fn($u, $r) => Import::status($u, (string) $r['run_key']), 'import');
        self::route('POST', '/import/setup', fn($u, $r) => Import::setup($u, (string) (self::body($r)['run_key'] ?? ''), (array) (self::body($r)['data'] ?? [])), 'import');
        self::route('POST', '/import/entries', function ($u, $r) {
            $b = self::body($r);
            return Import::entries($u, (string) ($b['run_key'] ?? ''), (array) ($b['items'] ?? []), max(1, (int) ($b['time_divisor'] ?? 1)));
        }, 'import');
        self::route('POST', '/import/checks', function ($u, $r) {
            $b = self::body($r);
            return Import::goal_checks($u, (string) ($b['run_key'] ?? ''), (array) ($b['items'] ?? []), (int) ($b['month_base'] ?? 1));
        }, 'import');
        self::route('POST', '/import/asset', function ($u, $r) {
            $f = $r->get_file_params()['file'] ?? null;
            if (!$f || !empty($f['error']) || !is_uploaded_file($f['tmp_name'])) {
                throw new Fail('bad_file', 'Die Datei ist nicht angekommen.', 400);
            }
            $taken = (string) $r['taken_at'];
            return Import::asset($u, (string) $r['run_key'], (string) $r['source_id'], (string) file_get_contents($f['tmp_name']),
                preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $taken) ? $taken : null, (string) $r['checksum']);
        }, 'import');
        self::route('POST', '/import/csv-check', fn($u, $r) => Import::csv_check($u, (array) (self::body($r)['rows'] ?? [])), 'import');
        self::route('POST', '/import/csv', function ($u, $r) {
            $b = self::body($r);
            return Import::csv($u, (string) ($b['run_key'] ?? ''), (array) ($b['rows'] ?? []), (array) ($b['options'] ?? []));
        }, 'import');

        // Export
        self::route('GET', '/export/csv', function ($u) { self::send_file('text/csv; charset=utf-8', 'tagebuch.csv', Export::csv($u)); }, 'export');
        self::route('GET', '/export/pdf', function ($u, $r) {
            self::send_file('application/pdf', 'tagebuch.pdf', Export::pdf($u, $r['from'] ? In::date($r['from']) : null, $r['to'] ? In::date($r['to']) : null));
        }, 'export');
        self::route('GET', '/export/backup', function ($u, $r) {
            $tmp = Export::zip($u, true, (bool) $r['photos']);
            register_shutdown_function(static fn() => @unlink($tmp));
            self::send_file('application/zip', 'mediclever-tagebuch-' . gmdate('Y-m-d') . '.mctb', '', $tmp);
        }, 'backup');
        self::route('GET', '/export/photos', function ($u) {
            $tmp = Export::zip($u, false, true);
            register_shutdown_function(static fn() => @unlink($tmp));
            self::send_file('application/zip', 'tagebuch-fotos.zip', '', $tmp);
        }, 'export');

        // Konto
        self::route('POST', '/account/wipe', function ($u, $r) {
            if ((self::body($r)['confirm'] ?? '') !== 'LEEREN') {
                throw new Fail('confirm', 'Bitte bestätige das Leeren mit dem Wort LEEREN.');
            }
            Account::wipe_diary($u, true);
            Setup::ensure_moods($u);
            return self::bootstrap($u);
        });
        self::route('DELETE', '/account', function ($u, $r) {
            if (($r['confirm'] ?? '') !== 'LÖSCHEN') {
                throw new Fail('confirm', 'Bitte bestätige die Löschung mit dem Wort LÖSCHEN.');
            }
            Account::delete_all($u);
        });

        // KI-Freigaben und Protokoll
        self::route('GET', '/grants', fn($u) => OAuth::grants($u), 'mcp');
        self::route('DELETE', "/grants/$id", function ($u, $r) { OAuth::revoke($u, (int) $r['id']); }, 'mcp');
        self::route('GET', '/audit', function ($u, $r) {
            $page = max(1, (int) $r['page']);
            return ['rows' => Db::rows('SELECT ts, source, action, object_id FROM ' . Db::t('audit_log') . ' WHERE user_id = %d ORDER BY id DESC LIMIT 50 OFFSET %d', [$u, ($page - 1) * 50])];
        });

        // Erinnerungen per Web-Push
        self::route('POST', '/push', function ($u, $r) { Reminders::subscribe($u, self::body($r)); }, 'reminders');
        self::route('DELETE', '/push', function ($u, $r) { Reminders::unsubscribe($u, (string) $r['endpoint']); }, 'reminders');
        self::route('GET', '/push/due', fn($u) => Reminders::due_texts($u), 'reminders');
    }
}
