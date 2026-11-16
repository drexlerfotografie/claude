<?php
/**
 * Plugin Name:       MediClever Tagebuch
 * Description:       Stimmungs- und Aktivitäten-Tagebuch, das jeder Nutzer selbst einrichtet. Mit REST-Schnittstelle und MCP-Endpunkt.
 * Version:           0.1.0
 * Requires at least: 6.5
 * Requires PHP:      8.1
 * Author:            MediClever
 * License:           GPL-2.0-or-later
 * Text Domain:       mediclever-tagebuch
 */

if (!defined('ABSPATH')) {
    exit;
}

define('MCT_VERSION', '0.1.0');
define('MCT_FILE', __FILE__);
define('MCT_DIR', plugin_dir_path(__FILE__));
define('MCT_URL', plugin_dir_url(__FILE__));
define('MCT_REST_NS', 'mc-tagebuch/v1');

foreach (['util', 'schema', 'setup', 'entries', 'assets', 'goals', 'stats', 'import', 'export', 'reminders', 'rest', 'oauth', 'mcp', 'shortcode'] as $mct_file) {
    require_once MCT_DIR . 'includes/' . $mct_file . '.php';
}

register_activation_hook(__FILE__, ['MCT\\Schema', 'install']);
register_deactivation_hook(__FILE__, ['MCT\\Reminders', 'unschedule']);

add_action('plugins_loaded', static function () {
    MCT\Schema::maybe_upgrade();
    MCT\Rest::init();
    MCT\OAuth::init();
    MCT\Mcp::init();
    MCT\Reminders::init();
    MCT\Shortcode::init();
});
