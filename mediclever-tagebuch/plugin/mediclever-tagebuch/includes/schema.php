<?php
namespace MCT;

/** Datenbanktabellen. Jede Tabelle trägt user_id; kein Zugriff ohne sie. */
final class Schema
{
    const DB_VERSION = '7';

    const TABLES = ['moods', 'groups', 'tags', 'scales', 'scale_points', 'entries', 'entry_tags', 'entry_scale_values',
        'assets', 'entry_assets', 'goals', 'goal_checks', 'milestones', 'templates', 'reminders', 'import_map',
        'mcp_grants', 'audit_log', 'oauth_clients', 'oauth_tokens', 'push_subs'];

    public static function maybe_upgrade(): void
    {
        if (get_option('mct_db_version') !== self::DB_VERSION) {
            self::install();
        }
    }

    public static function install(): void
    {
        global $wpdb;
        require_once ABSPATH . 'wp-admin/includes/upgrade.php';
        $c = $wpdb->get_charset_collate();
        $p = $wpdb->prefix . 'mct_';
        $sql = [];
        $sql[] = "CREATE TABLE {$p}moods (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            level tinyint(3) unsigned NOT NULL,
            name varchar(100) NOT NULL,
            icon varchar(40) NOT NULL DEFAULT '',
            position int(11) NOT NULL DEFAULT 0,
            archived_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_level (user_id,level)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}groups (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            name varchar(100) NOT NULL,
            color varchar(20) NOT NULL DEFAULT '',
            position int(11) NOT NULL DEFAULT 0,
            collapsed tinyint(1) NOT NULL DEFAULT 0,
            archived_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}tags (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            group_id bigint(20) unsigned DEFAULT NULL,
            name varchar(100) NOT NULL,
            icon varchar(40) NOT NULL DEFAULT '',
            color varchar(20) NOT NULL DEFAULT '',
            position int(11) NOT NULL DEFAULT 0,
            archived_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}scales (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            type varchar(10) NOT NULL,
            name varchar(100) NOT NULL,
            color varchar(20) NOT NULL DEFAULT '',
            icon varchar(40) NOT NULL DEFAULT '',
            position int(11) NOT NULL DEFAULT 0,
            chart_mode varchar(10) NOT NULL DEFAULT 'avg',
            trend varchar(10) NOT NULL DEFAULT 'up',
            min_val double DEFAULT NULL,
            max_val double DEFAULT NULL,
            archived_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}scale_points (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            scale_id bigint(20) unsigned NOT NULL,
            position int(11) NOT NULL DEFAULT 0,
            label varchar(100) NOT NULL,
            PRIMARY KEY  (id),
            KEY user_scale (user_id,scale_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}entries (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            ts_utc datetime NOT NULL,
            tz_offset int(11) NOT NULL DEFAULT 0,
            local_date date NOT NULL,
            local_time char(5) NOT NULL DEFAULT '00:00',
            mood_id bigint(20) unsigned NOT NULL,
            title varchar(255) NOT NULL DEFAULT '',
            note longtext,
            favorite tinyint(1) NOT NULL DEFAULT 0,
            source varchar(10) NOT NULL DEFAULT 'app',
            idem_key varchar(80) DEFAULT NULL,
            created_at datetime NOT NULL,
            updated_at datetime NOT NULL,
            PRIMARY KEY  (id),
            KEY user_date (user_id,local_date),
            KEY user_fav (user_id,favorite),
            KEY user_idem (user_id,idem_key)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}entry_tags (
            entry_id bigint(20) unsigned NOT NULL,
            tag_id bigint(20) unsigned NOT NULL,
            user_id bigint(20) unsigned NOT NULL,
            PRIMARY KEY  (entry_id,tag_id),
            KEY user_tag (user_id,tag_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}entry_scale_values (
            entry_id bigint(20) unsigned NOT NULL,
            scale_id bigint(20) unsigned NOT NULL,
            user_id bigint(20) unsigned NOT NULL,
            point_id bigint(20) unsigned DEFAULT NULL,
            num_val double DEFAULT NULL,
            minutes int(11) DEFAULT NULL,
            PRIMARY KEY  (entry_id,scale_id),
            KEY user_scale (user_id,scale_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}assets (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            checksum varchar(64) NOT NULL,
            type varchar(10) NOT NULL DEFAULT 'photo',
            mime varchar(50) NOT NULL DEFAULT '',
            taken_at datetime DEFAULT NULL,
            path varchar(255) NOT NULL,
            size bigint(20) unsigned NOT NULL DEFAULT 0,
            created_at datetime NOT NULL,
            PRIMARY KEY  (id),
            KEY user_checksum (user_id,checksum)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}entry_assets (
            entry_id bigint(20) unsigned NOT NULL,
            asset_id bigint(20) unsigned NOT NULL,
            user_id bigint(20) unsigned NOT NULL,
            position int(11) NOT NULL DEFAULT 0,
            PRIMARY KEY  (entry_id,asset_id),
            KEY user_asset (user_id,asset_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}goals (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            name varchar(150) NOT NULL,
            tag_id bigint(20) unsigned DEFAULT NULL,
            freq_type varchar(10) NOT NULL DEFAULT 'daily',
            freq_count tinyint(3) unsigned NOT NULL DEFAULT 1,
            freq_days varchar(20) NOT NULL DEFAULT '',
            reminder_time char(5) DEFAULT NULL,
            reminder_text varchar(255) NOT NULL DEFAULT '',
            start_date date NOT NULL,
            end_date date DEFAULT NULL,
            note text,
            position int(11) NOT NULL DEFAULT 0,
            archived_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}goal_checks (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            goal_id bigint(20) unsigned NOT NULL,
            check_date date NOT NULL,
            PRIMARY KEY  (id),
            UNIQUE KEY goal_date (goal_id,check_date),
            KEY user_date (user_id,check_date)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}milestones (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            name varchar(150) NOT NULL,
            m_date date NOT NULL,
            category varchar(60) NOT NULL DEFAULT '',
            note text,
            asset_id bigint(20) unsigned DEFAULT NULL,
            yearly tinyint(1) NOT NULL DEFAULT 1,
            remind_same_day tinyint(1) NOT NULL DEFAULT 0,
            remind_days_before int(11) NOT NULL DEFAULT 0,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}templates (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            title varchar(150) NOT NULL,
            body longtext,
            position int(11) NOT NULL DEFAULT 0,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}reminders (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            time char(5) NOT NULL,
            text varchar(255) NOT NULL DEFAULT '',
            active tinyint(1) NOT NULL DEFAULT 1,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}import_map (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            run_key varchar(64) NOT NULL,
            kind varchar(20) NOT NULL,
            source_id varchar(64) NOT NULL,
            new_id bigint(20) unsigned NOT NULL,
            PRIMARY KEY  (id),
            UNIQUE KEY src (user_id,run_key,kind,source_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}mcp_grants (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            client_id varchar(64) NOT NULL,
            client_name varchar(150) NOT NULL DEFAULT '',
            scopes varchar(100) NOT NULL DEFAULT '',
            granted_at datetime NOT NULL,
            last_used_at datetime DEFAULT NULL,
            revoked_at datetime DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_id (user_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}audit_log (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            ts datetime NOT NULL,
            source varchar(40) NOT NULL,
            action varchar(60) NOT NULL,
            object_id bigint(20) unsigned DEFAULT NULL,
            PRIMARY KEY  (id),
            KEY user_ts (user_id,ts)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}oauth_clients (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL DEFAULT 0,
            client_id varchar(64) NOT NULL,
            name varchar(150) NOT NULL DEFAULT '',
            redirect_uris text,
            created_at datetime NOT NULL,
            PRIMARY KEY  (id),
            UNIQUE KEY client_id (client_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}oauth_tokens (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            grant_id bigint(20) unsigned NOT NULL,
            access_hash char(64) NOT NULL,
            refresh_hash char(64) NOT NULL,
            access_expires datetime NOT NULL,
            refresh_expires datetime NOT NULL,
            PRIMARY KEY  (id),
            KEY access_hash (access_hash),
            KEY refresh_hash (refresh_hash),
            KEY grant_id (grant_id)
        ) $c;";
        $sql[] = "CREATE TABLE {$p}push_subs (
            id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
            user_id bigint(20) unsigned NOT NULL,
            endpoint text NOT NULL,
            endpoint_hash char(64) NOT NULL,
            p256dh varchar(120) NOT NULL DEFAULT '',
            auth varchar(60) NOT NULL DEFAULT '',
            created_at datetime NOT NULL,
            PRIMARY KEY  (id),
            UNIQUE KEY endpoint_hash (endpoint_hash),
            KEY user_id (user_id)
        ) $c;";
        foreach ($sql as $statement) {
            dbDelta($statement);
        }
        update_option('mct_db_version', self::DB_VERSION);
    }

    /** Entfernt alle Tabellen (nur für Tests und Deinstallation). */
    public static function drop_all(): void
    {
        global $wpdb;
        foreach (self::TABLES as $t) {
            $wpdb->query('DROP TABLE IF EXISTS ' . Db::t($t)); // phpcs:ignore
        }
        delete_option('mct_db_version');
    }
}
