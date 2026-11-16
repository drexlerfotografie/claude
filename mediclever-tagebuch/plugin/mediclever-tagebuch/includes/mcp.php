<?php
namespace MCT;

/**
 * MCP-Endpunkt (Streamable HTTP ohne Sitzungszustand). Greift auf dieselbe Logik zu wie die REST-Schnittstelle.
 * Antworten sind knapp und seitenweise; kein Tool gibt das ganze Tagebuch auf einmal aus.
 */
final class Mcp
{
    const PROTOCOLS = ['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05'];
    const PER_MINUTE = 60;

    public static function init(): void
    {
        add_action('rest_api_init', static function () {
            register_rest_route(MCT_REST_NS, '/mcp', [
                ['methods' => 'POST', 'permission_callback' => '__return_true', 'callback' => [self::class, 'handle']],
                ['methods' => 'GET, DELETE', 'permission_callback' => '__return_true', 'callback' => static function () {
                    $r = new \WP_REST_Response(['jsonrpc' => '2.0', 'error' => ['code' => -32000, 'message' => 'Dieser Endpunkt nimmt nur POST an.'], 'id' => null], 405);
                    $r->header('Allow', 'POST');
                    return $r;
                }],
            ]);
        });
    }

    public static function tools(): array
    {
        $date = ['type' => 'string', 'description' => 'Datum als JJJJ-MM-TT. Ohne Angabe gilt heute in der Zeitzone des Nutzers.'];
        $t = [];
        $add = function (string $name, string $title, string $desc, array $props, array $required, bool $read, bool $destructive = false, bool $idempotent = false) use (&$t) {
            $t[$name] = ['name' => $name, 'title' => $title, 'description' => $desc,
                'inputSchema' => ['type' => 'object', 'properties' => $props ?: new \stdClass(), 'required' => $required, 'additionalProperties' => false],
                'annotations' => ['title' => $title, 'readOnlyHint' => $read, 'destructiveHint' => $destructive, 'idempotentHint' => $read || $idempotent, 'openWorldHint' => false]];
        };
        $entry_props = [
            'datum' => $date,
            'uhrzeit' => ['type' => 'string', 'description' => 'Uhrzeit als HH:MM. Ohne Angabe die aktuelle Uhrzeit.'],
            'stimmung' => ['type' => 'string', 'description' => 'Name einer Stimmung des Nutzers, siehe einrichtung_lesen.'],
            'aktivitaeten' => ['type' => 'array', 'items' => ['type' => 'string'], 'description' => 'Namen von Aktivitäten des Nutzers.'],
            'skalen' => ['type' => 'array', 'description' => 'Skalenwerte. Bei Textskalen die Bezeichnung des Punkts, bei Zeitskalen H:MM, bei Zahlenskalen eine Zahl.',
                'items' => ['type' => 'object', 'properties' => ['name' => ['type' => 'string'], 'wert' => ['type' => 'string']], 'required' => ['name', 'wert']]],
            'titel' => ['type' => 'string'],
            'notiz' => ['type' => 'string', 'description' => 'Notiz als einfacher Text.'],
        ];
        $add('einrichtung_lesen', 'Einrichtung lesen', 'Liefert Stimmungen, Gruppen, Aktivitäten, Skalen und Ziele des Nutzers mit ihren Namen. Vor dem Anlegen eines Eintrags aufrufen.', [], [], true);
        $add('eintrag_anlegen', 'Eintrag anlegen', 'Legt einen Tagebuch-Eintrag an. Unbekannte Namen werden nicht angelegt; stattdessen kommt eine Liste möglicher Treffer zurück.',
            $entry_props + ['wiederholungsschluessel' => ['type' => 'string', 'description' => 'Frei gewählter Schlüssel. Ein erneuter Aufruf mit demselben Schlüssel erzeugt keinen zweiten Eintrag.']],
            ['stimmung'], false, false, true);
        $add('eintrag_aendern', 'Eintrag ändern', 'Ändert einzelne Felder eines Eintrags. Nicht genannte Felder bleiben unverändert. Eine Aktivitätenliste ersetzt die bisherige.',
            ['id' => ['type' => 'integer']] + $entry_props, ['id'], false, false, true);
        $add('eintrag_loeschen', 'Eintrag löschen', 'Löscht einen Eintrag endgültig. Nur möglich, wenn der Nutzer das Löschen eigens freigegeben hat.',
            ['id' => ['type' => 'integer']], ['id'], false, true, true);
        $add('tag_lesen', 'Tag lesen', 'Liefert alle Einträge eines Datums.', ['datum' => $date], [], true);
        $add('eintraege_suchen', 'Einträge suchen', 'Sucht Einträge nach Zeitraum, Text, Stimmung und Aktivität. Liefert zehn Einträge pro Seite.',
            ['von' => $date, 'bis' => $date, 'text' => ['type' => 'string'], 'stimmung' => ['type' => 'string'], 'aktivitaet' => ['type' => 'string'],
                'seite' => ['type' => 'integer', 'minimum' => 1]], [], true);
        $add('auswertung_lesen', 'Auswertung lesen', 'Kennzahlen für einen Zeitraum: Durchschnitt (5 = beste Stufe), Zähler je Stufe, häufigste Aktivitäten, Serien, Skalen.',
            ['von' => $date, 'bis' => $date, 'monat' => ['type' => 'string', 'description' => 'Alternativ ein Monat als JJJJ-MM.']], [], true);
        $add('ziel_abhaken', 'Ziel abhaken', 'Hakt ein Ziel für ein Datum ab oder nimmt den Haken zurück.',
            ['ziel' => ['type' => 'string', 'description' => 'Name des Ziels.'], 'datum' => $date, 'erledigt' => ['type' => 'boolean', 'description' => 'false nimmt den Haken zurück. Standard: true.']],
            ['ziel'], false, false, true);
        $add('wichtige_tage_lesen', 'Wichtige Tage lesen', 'Liefert die wichtigen Tage mit dem nächsten Jahrestag.', [], [], true);
        $add('wichtigen_tag_anlegen', 'Wichtigen Tag anlegen', 'Legt einen neuen wichtigen Tag an.',
            ['name' => ['type' => 'string'], 'datum' => $date, 'kategorie' => ['type' => 'string'], 'notiz' => ['type' => 'string'],
                'jaehrlich' => ['type' => 'boolean', 'description' => 'Wiederholt sich jedes Jahr. Standard: true.']], ['name', 'datum'], false);
        return $t;
    }

    const SCOPE = ['einrichtung_lesen' => 'lesen', 'tag_lesen' => 'lesen', 'eintraege_suchen' => 'lesen', 'auswertung_lesen' => 'lesen', 'wichtige_tage_lesen' => 'lesen',
        'eintrag_anlegen' => 'schreiben', 'eintrag_aendern' => 'schreiben', 'ziel_abhaken' => 'schreiben', 'wichtigen_tag_anlegen' => 'schreiben', 'eintrag_loeschen' => 'loeschen'];

    public static function handle(\WP_REST_Request $req): \WP_REST_Response
    {
        $auth = OAuth::authenticate();
        if (!$auth) {
            $r = new \WP_REST_Response(['jsonrpc' => '2.0', 'error' => ['code' => -32001, 'message' => 'Anmeldung erforderlich.'], 'id' => null], 401);
            $r->header('WWW-Authenticate', 'Bearer resource_metadata="' . OAuth::resource_metadata_url() . '"');
            return $r;
        }
        if (!OAuth::throttle('mcp|' . $auth['grant_id'], self::PER_MINUTE, 60)) {
            $r = new \WP_REST_Response(['jsonrpc' => '2.0', 'error' => ['code' => -32002, 'message' => 'Zu viele Aufrufe pro Minute. Bitte kurz warten.'], 'id' => null], 429);
            $r->header('Retry-After', '60');
            return $r;
        }
        $body = json_decode($req->get_body(), true);
        if (!is_array($body)) {
            return new \WP_REST_Response(['jsonrpc' => '2.0', 'error' => ['code' => -32700, 'message' => 'Ungültiges JSON.'], 'id' => null], 400);
        }
        Audit::$source = 'mcp:' . $auth['grant_id'];
        $batch = array_is_list($body);
        $out = [];
        foreach ($batch ? $body : [$body] as $msg) {
            $res = is_array($msg) ? self::dispatch($msg, $auth) : ['jsonrpc' => '2.0', 'error' => ['code' => -32600, 'message' => 'Ungültige Anfrage.'], 'id' => null];
            if ($res !== null) {
                $out[] = $res;
            }
        }
        if (!$out) {
            return new \WP_REST_Response(null, 202);
        }
        $r = new \WP_REST_Response($batch ? $out : $out[0], 200);
        $r->header('Cache-Control', 'no-store');
        return $r;
    }

    private static function dispatch(array $msg, array $auth): ?array
    {
        $id = $msg['id'] ?? null;
        $method = (string) ($msg['method'] ?? '');
        $params = (array) ($msg['params'] ?? []);
        if (!array_key_exists('id', $msg)) {
            return null; // Benachrichtigung, z. B. notifications/initialized
        }
        $ok = fn($result) => ['jsonrpc' => '2.0', 'id' => $id, 'result' => $result];
        switch ($method) {
            case 'initialize':
                $v = (string) ($params['protocolVersion'] ?? '');
                return $ok(['protocolVersion' => in_array($v, self::PROTOCOLS, true) ? $v : self::PROTOCOLS[1], 'capabilities' => ['tools' => ['listChanged' => false]],
                    'serverInfo' => ['name' => 'mediclever-tagebuch', 'title' => 'MediClever Tagebuch', 'version' => MCT_VERSION],
                    'instructions' => 'Stimmungs- und Aktivitäten-Tagebuch eines Nutzers. Rufe zuerst einrichtung_lesen auf und verwende nur die dort genannten Namen. '
                        . 'Lege nichts stillschweigend an: Bei unbekannten Namen frag den Nutzer. Fotos und Sprachnotizen sind nicht zugänglich.']);
            case 'ping':
                return $ok(new \stdClass());
            case 'tools/list':
                return $ok(['tools' => array_values(self::tools())]);
            case 'tools/call':
                $name = (string) ($params['name'] ?? '');
                $args = (array) ($params['arguments'] ?? []);
                if (!isset(self::SCOPE[$name])) {
                    return ['jsonrpc' => '2.0', 'id' => $id, 'error' => ['code' => -32602, 'message' => 'Unbekanntes Tool: ' . sanitize_key($name)]];
                }
                Audit::log($auth['uid'], 'mcp_' . $name);
                try {
                    if (!in_array(self::SCOPE[$name], $auth['scopes'], true)) {
                        throw new Fail('scope', sprintf('Dafür fehlt die Freigabe „%s“. Der Nutzer kann sie beim Verbinden des Assistenten erteilen.', self::SCOPE[$name]), 403);
                    }
                    $data = self::call($name, $args, $auth['uid']);
                    return $ok(['content' => [['type' => 'text', 'text' => wp_json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)]], 'structuredContent' => $data, 'isError' => false]);
                } catch (Fail $e) {
                    $data = array_merge(['fehler' => $e->getMessage()], $e->data);
                    return $ok(['content' => [['type' => 'text', 'text' => wp_json_encode($data, JSON_UNESCAPED_UNICODE)]], 'isError' => true]);
                } catch (\Throwable $e) {
                    error_log('MCT MCP-Fehler in ' . basename($e->getFile()) . ':' . $e->getLine());
                    return $ok(['content' => [['type' => 'text', 'text' => 'Das hat nicht geklappt.']], 'isError' => true]);
                }
            default:
                return ['jsonrpc' => '2.0', 'id' => $id, 'error' => ['code' => -32601, 'message' => 'Methode nicht bekannt.']];
        }
    }

    private static function resolve(array $items, string $name, string $what): array
    {
        [$hit, $cands] = Setup::match($items, $name);
        if (!$hit) {
            throw new Fail('unknown_name', sprintf('%s „%s“ ist nicht bekannt. Angelegt wurde nichts.', $what, In::text($name, 60)), 404, ['moegliche_treffer' => $cands]);
        }
        return $hit;
    }

    /** Übersetzt Tool-Argumente in die Eingabe von Entries::save. */
    private static function entry_input(int $uid, array $a, bool $create): array
    {
        $setup = Setup::all($uid);
        $active = fn($list) => array_values(array_filter($list, fn($x) => !$x['archived']));
        $in = [];
        if (isset($a['stimmung'])) {
            $in['mood_id'] = self::resolve($active($setup['moods']), (string) $a['stimmung'], 'Die Stimmung')['id'];
        }
        if (isset($a['aktivitaeten'])) {
            $in['tag_ids'] = array_map(fn($n) => self::resolve($active($setup['tags']), (string) $n, 'Die Aktivität')['id'], (array) $a['aktivitaeten']);
        }
        if (isset($a['skalen'])) {
            $in['scales'] = [];
            $in['scales_merge'] = !$create;
            foreach ((array) $a['skalen'] as $s) {
                $scale = self::resolve($active($setup['scales']), (string) ($s['name'] ?? ''), 'Die Skala');
                $wert = trim((string) ($s['wert'] ?? ''));
                if ($scale['type'] === 'text') {
                    $pts = array_map(fn($p) => ['id' => $p['id'], 'name' => $p['label']], $scale['points']);
                    $point = ctype_digit($wert) && isset($pts[(int) $wert - 1]) ? $pts[(int) $wert - 1] : self::resolve($pts, $wert, 'Der Skalenpunkt');
                    $in['scales'][] = ['scale_id' => $scale['id'], 'point_id' => $point['id']];
                } elseif ($scale['type'] === 'time') {
                    if (preg_match('/^(\d{1,3}):(\d{2})$/', $wert, $m)) {
                        $min = (int) $m[1] * 60 + (int) $m[2];
                    } elseif (is_numeric($wert)) {
                        $min = (int) $wert;
                    } else {
                        throw new Fail('bad_value', 'Zeitwerte bitte als H:MM oder in Minuten angeben.');
                    }
                    $in['scales'][] = ['scale_id' => $scale['id'], 'minutes' => $min];
                } else {
                    if (!is_numeric(str_replace(',', '.', $wert))) {
                        throw new Fail('bad_value', 'Für diese Skala wird eine Zahl erwartet.');
                    }
                    $in['scales'][] = ['scale_id' => $scale['id'], 'num' => (float) str_replace(',', '.', $wert)];
                }
            }
        }
        if (isset($a['datum'])) {
            $in['date'] = In::date($a['datum']);
        } elseif ($create) {
            $in['date'] = Settings::today($uid, true);
        }
        if (isset($a['uhrzeit'])) {
            $in['time'] = In::time($a['uhrzeit']);
        }
        if (isset($a['titel'])) {
            $in['title'] = (string) $a['titel'];
        }
        if (isset($a['notiz'])) {
            $in['note'] = str_replace(["\r\n", "\n"], '<br>', esc_html((string) $a['notiz']));
        }
        return $in;
    }

    private static function entry_out(array $e, array $l): array
    {
        $out = ['id' => $e['id'], 'datum' => $e['date'], 'uhrzeit' => $e['time'], 'stimmung' => $l['moods'][$e['mood_id']] ?? '', 'stufe' => $e['level'],
            'aktivitaeten' => array_values(array_filter(array_map(fn($t) => $l['tags'][$t] ?? null, $e['tags'])))];
        $scales = array_values(array_filter(array_map(fn($v) => Export::scale_text($v, $l['full']), $e['scales'])));
        if ($scales) { $out['skalen'] = $scales; }
        if ($e['title'] !== '') { $out['titel'] = $e['title']; }
        $note = trim(html_entity_decode(wp_strip_all_tags(preg_replace('#<(br|/p|/div|/li)[^>]*>#i', "\n", $e['note'])), ENT_QUOTES, 'UTF-8'));
        if ($note !== '') { $out['notiz'] = $note; }
        if ($e['favorite']) { $out['favorit'] = true; }
        if ($e['assets']) { $out['dateien'] = count($e['assets']); }
        return $out;
    }

    private static function lookup(int $uid): array
    {
        $s = Setup::all($uid);
        $l = ['moods' => [], 'tags' => [], 'full' => ['moods' => [], 'tags' => [], 'scales' => [], 'points' => []], 'setup' => $s];
        foreach ($s['moods'] as $m) { $l['moods'][$m['id']] = $m['name']; }
        foreach ($s['tags'] as $t) { $l['tags'][$t['id']] = $t['name']; }
        foreach ($s['scales'] as $sc) {
            $l['full']['scales'][$sc['id']] = $sc;
            foreach ($sc['points'] as $p) { $l['full']['points'][$p['id']] = $p + ['of' => count($sc['points'])]; }
        }
        return $l;
    }

    public static function call(string $name, array $a, int $uid): array
    {
        switch ($name) {
            case 'einrichtung_lesen':
                $s = Setup::all($uid);
                $on = fn($list) => array_values(array_filter($list, fn($x) => empty($x['archived'])));
                $groups = [];
                foreach ($on($s['groups']) as $g) {
                    $groups[] = ['gruppe' => $g['name'], 'aktivitaeten' => array_values(array_map(fn($t) => $t['name'], array_filter($on($s['tags']), fn($t) => $t['group_id'] === $g['id'])))];
                }
                $loose = array_values(array_map(fn($t) => $t['name'], array_filter($on($s['tags']), fn($t) => $t['group_id'] === null)));
                if ($loose) {
                    $groups[] = ['gruppe' => 'Ohne Gruppe', 'aktivitaeten' => $loose];
                }
                return ['stimmungen' => array_map(fn($m) => ['name' => $m['name'], 'stufe' => $m['level']], $on($s['moods'])), 'hinweis_stufen' => 'Stufe 1 ist die beste, Stufe 5 die schlechteste.',
                    'gruppen' => $groups,
                    'skalen' => array_map(fn($x) => ['name' => $x['name'], 'typ' => ['text' => 'Text', 'time' => 'Zeit', 'num' => 'Zahl'][$x['type']]] + ($x['type'] === 'text' ? ['punkte' => array_column($x['points'], 'label')] : []), $on($s['scales'])),
                    'ziele' => array_map(fn($g) => ['name' => $g['name'], 'heute_erledigt' => $g['done_today'], 'serie' => $g['streak']], Goals::overview($uid, null, false)),
                    'heute' => Settings::today($uid, true)];
            case 'eintrag_anlegen':
                $in = self::entry_input($uid, $a, true);
                $in['source'] = 'mcp';
                if (!empty($a['wiederholungsschluessel'])) {
                    $in['idem_key'] = 'mcp:' . (string) $a['wiederholungsschluessel'];
                }
                $e = Entries::save($uid, $in);
                return ['eintrag' => self::entry_out($e, self::lookup($uid)), 'neu' => empty($e['wiederholt'])];
            case 'eintrag_aendern':
                $e = Entries::save($uid, self::entry_input($uid, $a, false), (int) ($a['id'] ?? 0));
                return ['eintrag' => self::entry_out($e, self::lookup($uid))];
            case 'eintrag_loeschen':
                Entries::delete($uid, (int) ($a['id'] ?? 0));
                return ['geloescht' => (int) $a['id']];
            case 'tag_lesen':
                $date = isset($a['datum']) ? In::date($a['datum']) : Settings::today($uid, true);
                $l = self::lookup($uid);
                return ['datum' => $date, 'eintraege' => array_map(fn($e) => self::entry_out($e, $l), Entries::day($uid, $date))];
            case 'eintraege_suchen':
                $l = self::lookup($uid);
                $q = ['from' => $a['von'] ?? null, 'to' => $a['bis'] ?? null, 'text' => $a['text'] ?? null, 'page' => (int) ($a['seite'] ?? 1), 'per_page' => 10, 'full' => true];
                if (!empty($a['stimmung'])) {
                    $q['mood_ids'] = [self::resolve($l['setup']['moods'], (string) $a['stimmung'], 'Die Stimmung')['id']];
                }
                if (!empty($a['aktivitaet'])) {
                    $q['tag_ids'] = [self::resolve($l['setup']['tags'], (string) $a['aktivitaet'], 'Die Aktivität')['id']];
                }
                $r = Entries::search($uid, $q);
                return ['treffer' => $r['total'], 'seite' => $r['page'], 'seiten' => $r['pages'], 'eintraege' => array_map(function ($e) use ($l) {
                    $o = self::entry_out($e, $l);
                    if (isset($o['notiz']) && mb_strlen($o['notiz']) > 600) {
                        $o['notiz'] = mb_substr($o['notiz'], 0, 600) . ' …';
                    }
                    return $o;
                }, $r['entries'])];
            case 'auswertung_lesen':
                if (!empty($a['monat'])) {
                    [$from, $to] = In::month_range((string) $a['monat']);
                } else {
                    $to = isset($a['bis']) ? In::date($a['bis']) : Settings::today($uid);
                    $from = isset($a['von']) ? In::date($a['von']) : date('Y-m-d', strtotime('-29 days', strtotime($to)));
                }
                if ($from > $to || (strtotime($to) - strtotime($from)) > 400 * 86400) {
                    throw new Fail('bad_range', 'Der Zeitraum darf höchstens 400 Tage umfassen.');
                }
                $s = Stats::summary($uid, $from, $to);
                $l = self::lookup($uid);
                $tags = [];
                foreach ($s['tag_counts'] as $tid => $n) {
                    $tags[] = ['aktivitaet' => $l['tags'][$tid] ?? '?', 'anzahl' => $n];
                }
                return ['von' => $from, 'bis' => $to, 'eintraege' => $s['count'], 'tage_mit_eintrag' => $s['days_with_entries'],
                    'durchschnitt' => $s['avg'], 'hinweis' => 'Durchschnitt: 5 = beste Stufe, 1 = schlechteste.',
                    'zaehler_je_stufe' => $s['levels'], 'haeufigste_aktivitaeten' => $tags, 'stabilitaet_0_bis_100' => $s['stability'],
                    'laengste_serie_guter_tage' => $s['good_streak']['length'], 'tage_in_folge_aktuell' => $s['streaks']['current'], 'tage_in_folge_laengste' => $s['streaks']['longest'],
                    'skalen' => array_map(fn($x) => ['name' => $l['full']['scales'][$x['scale_id']]['name'] ?? '?', 'durchschnitt' => $x['avg'], 'tage' => $x['n']], $s['scales'])];
            case 'ziel_abhaken':
                $goals = Goals::overview($uid, null, false);
                $g = self::resolve($goals, (string) ($a['ziel'] ?? ''), 'Das Ziel');
                $date = isset($a['datum']) ? In::date($a['datum']) : Settings::today($uid, true);
                $done = !isset($a['erledigt']) || $a['erledigt'];
                Goals::check($uid, $g['id'], $date, (bool) $done);
                foreach (Goals::overview($uid, null, false) as $x) {
                    if ($x['id'] === $g['id']) {
                        return ['ziel' => $x['name'], 'datum' => $date, 'erledigt' => (bool) $done, 'serie' => $x['streak']];
                    }
                }
                return ['ziel' => $g['name'], 'datum' => $date, 'erledigt' => (bool) $done];
            case 'wichtige_tage_lesen':
                return ['wichtige_tage' => array_map(fn($m) => ['name' => $m['name'], 'datum' => $m['date'], 'kategorie' => $m['category'], 'seit_tagen' => $m['days_since'],
                    'naechster_jahrestag' => $m['next_date'], 'in_tagen' => $m['days_until']], Milestones::all($uid))];
            case 'wichtigen_tag_anlegen':
                $m = Milestones::save($uid, ['name' => $a['name'] ?? '', 'date' => $a['datum'] ?? '', 'category' => $a['kategorie'] ?? '', 'note' => $a['notiz'] ?? '',
                    'yearly' => !isset($a['jaehrlich']) || $a['jaehrlich']]);
                return ['name' => $m['name'], 'datum' => $m['date'], 'naechster_jahrestag' => $m['next_date'], 'in_tagen' => $m['days_until']];
        }
        throw new Fail('unknown_tool', 'Unbekanntes Tool.');
    }
}
