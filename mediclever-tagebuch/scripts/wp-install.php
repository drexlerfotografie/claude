<?php
// Installiert WordPress lokal und legt Testkonten sowie die Tagebuch-Seite an. Idempotent.
$core = $argv[1] ?? '';
if (!is_file($core . '/wp-load.php')) { fwrite(STDERR, "WordPress nicht gefunden\n"); exit(1); }
define('WP_INSTALLING', true);
$_SERVER['HTTP_HOST'] = '127.0.0.1';
$_SERVER['REQUEST_URI'] = '/';
require $core . '/wp-load.php';
require_once ABSPATH . 'wp-admin/includes/upgrade.php';
require_once ABSPATH . 'wp-admin/includes/plugin.php';
if (!is_blog_installed()) {
    wp_install('MediClever lokal', 'admin', 'admin@example.test', false, '', 'admin');
}
update_option('permalink_structure', '/%postname%/');
update_option('timezone_string', 'Europe/Berlin');
update_option('WPLANG', 'de_DE');
switch_theme('mct-dev');
foreach (['anna' => 'Anna Beispiel', 'ben' => 'Ben Muster', 'leer' => 'Lea Leer'] as $login => $name) {
    if (!username_exists($login)) {
        $id = wp_create_user($login, $login . '-passwort', $login . '@example.test');
        wp_update_user(['ID' => $id, 'display_name' => $name, 'role' => 'subscriber']);
    }
}
$active = (array) get_option('active_plugins', []);
if (!in_array('mediclever-tagebuch/mediclever-tagebuch.php', $active, true)) {
    $r = activate_plugin('mediclever-tagebuch/mediclever-tagebuch.php');
    if (is_wp_error($r)) { fwrite(STDERR, $r->get_error_message() . "\n"); exit(1); }
}
if (!get_page_by_path('tagebuch')) {
    wp_insert_post(['post_type' => 'page', 'post_status' => 'publish', 'post_title' => 'Tagebuch',
        'post_name' => 'tagebuch', 'post_content' => '[mediclever_tagebuch]']);
}
global $wp_rewrite;
$wp_rewrite->init();
flush_rewrite_rules(false);
echo "WordPress " . get_bloginfo('version') . " bereit, PHP " . PHP_VERSION . "\n";
