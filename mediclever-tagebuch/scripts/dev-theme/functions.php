<?php
// Lokale Entwicklung: keine Admin-Leiste, keine externen Anfragen (Avatare, Emoji-Skripte).
add_filter('show_admin_bar', '__return_false');
remove_action('wp_head', 'print_emoji_detection_script', 7);
remove_action('wp_print_styles', 'print_emoji_styles');
add_filter('pre_option_show_avatars', '__return_zero');
