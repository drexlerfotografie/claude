<?php
// Lokale Entwicklung: keine Admin-Leiste, keine externen Anfragen (Avatare, Emoji-Skripte).
add_filter('show_admin_bar', '__return_false');
remove_action('wp_head', 'print_emoji_detection_script', 7);
remove_action('wp_print_styles', 'print_emoji_styles');
add_filter('pre_option_show_avatars', '__return_zero');

// Vorschau in einem eingebetteten Fenster: Anmeldung dort erlauben und Cookies dafür kennzeichnen.
remove_action('login_init', 'send_frame_options_header');
remove_action('admin_init', 'send_frame_options_header');
header_register_callback(static function () {
    if (empty($_SERVER['HTTPS'])) { return; }
    $cookies = [];
    foreach (headers_list() as $h) {
        if (stripos($h, 'Set-Cookie:') === 0) { $cookies[] = trim(substr($h, 11)); }
    }
    if ($cookies) {
        header_remove('Set-Cookie');
        foreach ($cookies as $c) { header('Set-Cookie: ' . $c . (stripos($c, 'samesite') === false ? '; SameSite=None; Secure' : ''), false); }
    }
});
// Vorführung: /?demo=anna meldet ein Testkonto ohne Passwort an. Gibt es nur in diesem Entwicklungs-Theme, nie im Plugin.
add_action('init', static function () {
    $demo = $_GET['demo'] ?? '';
    if (wp_get_environment_type() === 'local' && in_array($demo, ['anna', 'ben', 'leer'], true) && ($u = get_user_by('login', $demo))) {
        wp_set_current_user($u->ID);
        wp_set_auth_cookie($u->ID, true);
        wp_safe_redirect(home_url('/tagebuch/'));
        exit;
    }
});
