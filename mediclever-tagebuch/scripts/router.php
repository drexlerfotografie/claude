<?php
// Router für den eingebauten PHP-Server: liefert Dateien aus, alles andere geht an WordPress.
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$file = $_SERVER['DOCUMENT_ROOT'] . $path;
if ($path !== '/' && is_file($file) && !str_ends_with($path, '.php')) {
    return false;
}
if (str_starts_with($path, '/wp-content/')) {
    // WP_CONTENT_DIR liegt außerhalb des Dokumentenstamms.
    $content = dirname($_SERVER['DOCUMENT_ROOT']) . '/content' . substr($path, strlen('/wp-content'));
    $real = realpath($content);
    $base = realpath(dirname($_SERVER['DOCUMENT_ROOT']) . '/content');
    // Der private Datenordner wird wie auf dem Live-System (.htaccess) nie ausgeliefert.
    if ($real && is_file($real) && !str_ends_with($real, '.php') && !str_contains($real, '/mct-data/')) {
        $allowed = str_starts_with($real, $base) || str_contains($real, '/plugin/mediclever-tagebuch/');
        if ($allowed) {
            $types = ['js' => 'text/javascript', 'css' => 'text/css', 'svg' => 'image/svg+xml', 'png' => 'image/png',
                'woff2' => 'font/woff2', 'json' => 'application/json', 'jpg' => 'image/jpeg', 'webmanifest' => 'application/manifest+json'];
            $ext = strtolower(pathinfo($real, PATHINFO_EXTENSION));
            header('Content-Type: ' . ($types[$ext] ?? 'application/octet-stream'));
            if (basename($real) === 'sw.js') { header('Service-Worker-Allowed: /'); }
            readfile($real);
            return true;
        }
    }
    http_response_code(404);
    echo 'Nicht gefunden';
    return true;
}
if (is_file($file) && str_ends_with($path, '.php')) {
    $_SERVER['SCRIPT_NAME'] = $path;
    $_SERVER['SCRIPT_FILENAME'] = $file;
    chdir(dirname($file));
    require $file;
    return true;
}
if (is_dir($file) && is_file(rtrim($file, '/') . '/index.php') && $path !== '/') {
    $_SERVER['SCRIPT_NAME'] = rtrim($path, '/') . '/index.php';
    chdir($file);
    require rtrim($file, '/') . '/index.php';
    return true;
}
$_SERVER['SCRIPT_NAME'] = '/index.php';
require $_SERVER['DOCUMENT_ROOT'] . '/index.php';
