<?php
// Tests laufen gegen das lokale WordPress, aber in einer eigenen Datenbank (mct_test).
$root = dirname(__DIR__, 2);
putenv('MCT_DB_NAME=mct_test');
$core = $root . '/.wp/core';
if (!is_file($core . '/wp-load.php')) {
    fwrite(STDERR, "Lokales WordPress fehlt. Bitte zuerst scripts/wp.sh start ausführen.\n");
    exit(1);
}
passthru('MCT_DB_NAME=mct_test php ' . escapeshellarg($root . '/scripts/wp-install.php') . ' ' . escapeshellarg($core) . ' > /dev/null', $code);
if ($code !== 0) {
    fwrite(STDERR, "Die Testdatenbank ist nicht erreichbar. Läuft MariaDB (scripts/wp.sh start)?\n");
    exit(1);
}
$_SERVER['HTTP_HOST'] = '127.0.0.1:8888';
$_SERVER['REQUEST_URI'] = '/';
$_SERVER['REQUEST_METHOD'] = 'GET';
$_SERVER['REMOTE_ADDR'] = '127.0.0.1';
define('MCT_STORAGE_DIR', $root . '/.wp/test-data');
require $core . '/wp-load.php';
require_once __DIR__ . '/Base.php';
MCT\Schema::install();
