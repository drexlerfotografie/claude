<?php
namespace MCT;

/** Fotos und Sprachnotizen. Ablage außerhalb des öffentlichen Upload-Pfads, Auslieferung nur angemeldet. */
final class Assets
{
    const SIZES = ['s' => 360, 'm' => 1280];
    const MAX_BYTES = 30 * 1024 * 1024;
    const AUDIO = ['audio/webm' => 'webm', 'video/webm' => 'webm', 'audio/ogg' => 'ogg', 'audio/mp4' => 'm4a', 'video/mp4' => 'm4a',
        'audio/mpeg' => 'mp3', 'audio/x-m4a' => 'm4a', 'audio/wav' => 'wav', 'audio/x-wav' => 'wav', 'application/ogg' => 'ogg'];

    public static function base_dir(): string
    {
        $dir = defined('MCT_STORAGE_DIR') ? MCT_STORAGE_DIR : WP_CONTENT_DIR . '/mct-data';
        $dir = rtrim((string) apply_filters('mct_storage_dir', $dir), '/');
        if (!is_dir($dir)) {
            wp_mkdir_p($dir);
            // Falls der Ordner doch im Dokumentenstamm liegt: Zugriff per Webserver sperren.
            @file_put_contents($dir . '/.htaccess', "Require all denied\nDeny from all\n");
            @file_put_contents($dir . '/index.php', "<?php // Stille.\n");
        }
        return $dir;
    }

    /** Speichert eine Datei aus rohen Bytes. Der Typ wird am Inhalt erkannt, nie am Namen. */
    public static function store(int $uid, string $bytes, ?string $taken_at = null, ?string $checksum = null): array
    {
        Features::need('photos', $uid);
        if ($bytes === '' || strlen($bytes) > self::MAX_BYTES) {
            throw new Fail('bad_file', 'Die Datei ist leer oder größer als 30 MB.', 413);
        }
        $mime = (new \finfo(FILEINFO_MIME_TYPE))->buffer($bytes) ?: '';
        $rel = sprintf('%d/%s/%s', $uid, gmdate('Y/m'), bin2hex(random_bytes(12)));
        $dir = self::base_dir() . '/' . dirname($rel);
        wp_mkdir_p($dir);
        $base = self::base_dir() . '/' . $rel;

        if (in_array($mime, ['image/jpeg', 'image/png', 'image/webp', 'image/gif'], true)) {
            $img = @imagecreatefromstring($bytes);
            if (!$img) {
                throw new Fail('bad_file', 'Das Bild lässt sich nicht lesen.', 415);
            }
            // Ausrichtung übernehmen, dann neu kodieren: Aufnahmeort und Gerätedaten fallen dabei weg.
            if ($mime === 'image/jpeg' && function_exists('exif_read_data')) {
                $exif = @exif_read_data('data://image/jpeg;base64,' . base64_encode($bytes));
                $o = (int) ($exif['Orientation'] ?? 1);
                if ($taken_at === null && !empty($exif['DateTimeOriginal']) && preg_match('/^\d{4}:\d{2}:\d{2} \d{2}:\d{2}:\d{2}$/', $exif['DateTimeOriginal'])) {
                    $taken_at = preg_replace('/^(\d{4}):(\d{2}):(\d{2})/', '$1-$2-$3', $exif['DateTimeOriginal']);
                }
                $deg = [3 => 180, 6 => -90, 8 => 90][$o] ?? 0;
                if ($deg) {
                    $img = imagerotate($img, $deg, 0);
                }
            }
            self::write_jpeg($img, $base . '.jpg', 3000, 88);
            foreach (self::SIZES as $key => $px) {
                self::write_jpeg($img, $base . "_$key.jpg", $px, 80);
            }
            imagedestroy($img);
            $type = 'photo';
            $path = $rel . '.jpg';
            $mime = 'image/jpeg';
        } elseif (isset(self::AUDIO[$mime])) {
            Features::need('voice', $uid);
            $path = $rel . '.' . self::AUDIO[$mime];
            file_put_contents(self::base_dir() . '/' . $path, $bytes);
            $type = 'audio';
            $mime = str_replace('video/', 'audio/', $mime);
        } else {
            throw new Fail('bad_file', 'Erlaubt sind Fotos (JPEG, PNG, WebP) und Sprachnotizen.', 415);
        }
        $id = Db::insert('assets', $uid, ['checksum' => $checksum ?: hash('sha256', $bytes), 'type' => $type, 'mime' => $mime,
            'taken_at' => $taken_at, 'path' => $path, 'size' => filesize(self::base_dir() . '/' . $path), 'created_at' => Db::now()]);
        Audit::log($uid, 'asset_create', $id);
        return ['id' => $id, 'type' => $type];
    }

    private static function write_jpeg($img, string $file, int $max, int $quality): void
    {
        $w = imagesx($img);
        $h = imagesy($img);
        $scale = min(1, $max / max($w, $h));
        $nw = max(1, (int) round($w * $scale));
        $nh = max(1, (int) round($h * $scale));
        $dst = imagecreatetruecolor($nw, $nh);
        imagefill($dst, 0, 0, imagecolorallocate($dst, 255, 255, 255));
        imagecopyresampled($dst, $img, 0, 0, 0, 0, $nw, $nh, $w, $h);
        imagejpeg($dst, $file, $quality);
        imagedestroy($dst);
    }

    /** Pfad einer Datei des Nutzers, gegen Ausbruch aus dem Datenordner gesichert. */
    public static function file(int $uid, int $id, string $size = 'full'): array
    {
        $a = Db::own('assets', $uid, $id);
        $path = $a['path'];
        if ($a['type'] === 'photo' && isset(self::SIZES[$size])) {
            $path = preg_replace('/\.jpg$/', "_$size.jpg", $path);
        }
        $full = realpath(self::base_dir() . '/' . $path);
        $base = realpath(self::base_dir());
        if (!$full || !$base || !str_starts_with($full, $base . '/' . $uid . '/')) {
            throw new Fail('not_found', 'Nicht gefunden.', 404);
        }
        return ['file' => $full, 'mime' => $a['mime'], 'type' => $a['type']];
    }

    public static function delete(int $uid, int $id): void
    {
        $a = Db::own('assets', $uid, $id);
        $base = self::base_dir() . '/' . $a['path'];
        foreach ([$base, preg_replace('/\.jpg$/', '_s.jpg', $base), preg_replace('/\.jpg$/', '_m.jpg', $base)] as $f) {
            if (is_file($f) && str_contains($f, '/' . $uid . '/')) {
                @unlink($f);
            }
        }
        Db::delete('entry_assets', $uid, ['asset_id' => $id]);
        Db::run('UPDATE ' . Db::t('milestones') . ' SET asset_id = NULL WHERE user_id = %d AND asset_id = %d', [$uid, $id]);
        Db::delete('assets', $uid, ['id' => $id]);
    }

    public static function delete_if_orphan(int $uid, int $id): void
    {
        $used = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entry_assets') . ' WHERE user_id = %d AND asset_id = %d', [$uid, $id])
            + (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('milestones') . ' WHERE user_id = %d AND asset_id = %d', [$uid, $id]);
        if ($used === 0 && Db::val('SELECT id FROM ' . Db::t('assets') . ' WHERE id = %d AND user_id = %d', [$id, $uid])) {
            self::delete($uid, $id);
        }
    }

    /** Galerie aller Fotos, nach Monat gruppiert. */
    public static function gallery(int $uid, int $page = 1, int $per = 60): array
    {
        $total = (int) Db::val('SELECT COUNT(*) FROM ' . Db::t('entry_assets') . ' ea JOIN ' . Db::t('assets')
            . " a ON a.id = ea.asset_id WHERE ea.user_id = %d AND a.type = 'photo'", [$uid]);
        $rows = Db::rows('SELECT a.id, e.id AS entry_id, e.local_date FROM ' . Db::t('entry_assets') . ' ea JOIN ' . Db::t('assets')
            . ' a ON a.id = ea.asset_id JOIN ' . Db::t('entries') . " e ON e.id = ea.entry_id WHERE ea.user_id = %d AND a.type = 'photo'"
            . ' ORDER BY e.local_date DESC, e.local_time DESC, ea.position LIMIT %d OFFSET %d', [$uid, $per, (max(1, $page) - 1) * $per]);
        return ['total' => $total, 'page' => $page, 'pages' => (int) ceil($total / $per),
            'photos' => array_map(fn($r) => ['id' => (int) $r['id'], 'entry_id' => (int) $r['entry_id'], 'date' => $r['local_date']], $rows)];
    }

    /** Löscht alle Dateien eines Nutzers (Kontolöschung). */
    public static function purge_user(int $uid): void
    {
        $dir = self::base_dir() . '/' . $uid;
        if (!is_dir($dir)) {
            return;
        }
        $it = new \RecursiveIteratorIterator(new \RecursiveDirectoryIterator($dir, \FilesystemIterator::SKIP_DOTS), \RecursiveIteratorIterator::CHILD_FIRST);
        foreach ($it as $f) {
            $f->isDir() ? @rmdir($f->getPathname()) : @unlink($f->getPathname());
        }
        @rmdir($dir);
    }
}
