<?php
// Erzeugt von scripts/wp.sh – nur für die lokale Entwicklung.
define('DB_NAME', getenv('MCT_DB_NAME') ?: 'mct_dev');
define('DB_USER', 'root');
define('DB_PASSWORD', '');
define('DB_HOST', 'localhost:__SOCK__');
define('DB_CHARSET', 'utf8mb4');
define('DB_COLLATE', '');
$table_prefix = 'wp_';
foreach (['AUTH_KEY','SECURE_AUTH_KEY','LOGGED_IN_KEY','NONCE_KEY','AUTH_SALT','SECURE_AUTH_SALT','LOGGED_IN_SALT','NONCE_SALT'] as $k) {
    define($k, hash('sha256', 'mct-dev-' . $k));
}
// Die Adresse folgt der Anfrage, damit die Seite auch über eine Vorschau-Weiterleitung erreichbar ist. Nur lokal sinnvoll.
$mct_host = $_SERVER['HTTP_X_FORWARDED_HOST'] ?? $_SERVER['HTTP_HOST'] ?? '';
$mct_host = preg_match('/^[a-zA-Z0-9.\-]+(:\d+)?$/', $mct_host) ? $mct_host : '127.0.0.1:__PORT__';
$mct_https = ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https' || !preg_match('/^(127\.0\.0\.1|localhost)(:|$)/', $mct_host);
if ($mct_https) { $_SERVER['HTTPS'] = 'on'; }
define('WP_HOME', ($mct_https ? 'https://' : 'http://') . $mct_host);
define('WP_SITEURL', WP_HOME);
define('WP_CONTENT_DIR', '__CONTENT__');
define('WP_CONTENT_URL', WP_HOME . '/wp-content');
define('WP_DEBUG', true);
define('WP_DEBUG_LOG', true);
define('WP_DEBUG_DISPLAY', false);
define('DISABLE_WP_CRON', true);
define('AUTOMATIC_UPDATER_DISABLED', true);
define('WP_HTTP_BLOCK_EXTERNAL', true);
define('WP_ENVIRONMENT_TYPE', 'local');
if (!defined('ABSPATH')) { define('ABSPATH', __DIR__ . '/'); }
require_once ABSPATH . 'wp-settings.php';
