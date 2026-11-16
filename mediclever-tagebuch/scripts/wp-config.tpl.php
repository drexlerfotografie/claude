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
define('WP_HOME', 'http://127.0.0.1:__PORT__');
define('WP_SITEURL', 'http://127.0.0.1:__PORT__');
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
