<?php
namespace MCT;

/** Bindet die Web-App per Shortcode [mediclever_tagebuch] in eine Seite ein. Keine externen Quellen, keine Tracker. */
final class Shortcode
{
    public static function init(): void
    {
        add_shortcode('mediclever_tagebuch', [self::class, 'render']);
        add_action('init', [self::class, 'service_worker'], 2);
    }

    /** Liefert den Service Worker mit Geltungsbereich der ganzen Seite aus. */
    public static function service_worker(): void
    {
        if (isset($_GET['mct_sw'])) {
            $file = MCT_DIR . 'build/sw.js';
            header('Content-Type: text/javascript; charset=utf-8');
            header('Service-Worker-Allowed: /');
            header('Cache-Control: no-cache');
            if (is_file($file)) {
                readfile($file);
            }
            exit;
        }
    }

    public static function render(): string
    {
        if (!is_user_logged_in()) {
            return '<div class="mct-login" style="font-family:Roboto,Arial,sans-serif;max-width:480px;margin:48px auto;padding:24px;text-align:center">'
                . '<h2 style="color:#11202C">MediClever Tagebuch</h2><p>Das Tagebuch gehört zu deinem MediClever-Konto. Bitte melde dich an.</p>'
                . '<p><a href="' . esc_url(wp_login_url(get_permalink())) . '" style="display:inline-block;min-height:44px;line-height:44px;padding:0 24px;border-radius:999px;background:#B9303F;color:#fff;font-weight:700;text-decoration:none">Anmelden</a></p></div>';
        }
        $manifest_file = MCT_DIR . 'build/.vite/manifest.json';
        if (!is_file($manifest_file)) {
            return '<p>Die Tagebuch-App ist noch nicht gebaut (<code>npm run build</code>).</p>';
        }
        $manifest = json_decode((string) file_get_contents($manifest_file), true);
        $entry = $manifest['index.html'] ?? $manifest['src/main.tsx'] ?? reset($manifest);
        $ver = MCT_VERSION . '-' . filemtime($manifest_file);
        foreach ((array) ($entry['css'] ?? []) as $i => $css) {
            wp_enqueue_style('mct-app-' . $i, MCT_URL . 'build/' . $css, [], $ver);
        }
        wp_enqueue_script_module('mct-app', MCT_URL . 'build/' . $entry['file'], [], $ver);
        $config = ['rest' => esc_url_raw(rest_url(MCT_REST_NS)), 'nonce' => wp_create_nonce('wp_rest'), 'assets' => MCT_URL . 'build/',
            'sw' => add_query_arg('mct_sw', '1', home_url('/')), 'logout' => wp_logout_url(home_url('/')), 'account' => admin_url('profile.php')];
        return '<div id="mct-app" data-config="' . esc_attr(wp_json_encode($config)) . '"><noscript>Das Tagebuch braucht JavaScript.</noscript></div>';
    }
}
