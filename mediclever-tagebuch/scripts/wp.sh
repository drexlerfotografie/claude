#!/usr/bin/env bash
# Lokales WordPress ohne Docker: MariaDB + PHP-Server.
# Befehle: setup | start | stop | reset | status
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WP="$ROOT/.wp"
PORT="${MCT_PORT:-8888}"
DBPORT="${MCT_DB_PORT:-3317}"
SOCK="$WP/mysql.sock"
MYSQL=(mariadb --protocol=socket --socket="$SOCK" -uroot)

db_running() { [ -S "$SOCK" ] && mariadb-admin --socket="$SOCK" -uroot ping >/dev/null 2>&1; }
web_running() { curl -s -o /dev/null "http://127.0.0.1:$PORT/wp-login.php"; }

start_db() {
  db_running && return 0
  mkdir -p "$WP/mysql"
  if [ ! -d "$WP/mysql/mysql" ]; then
    mariadb-install-db --no-defaults --datadir="$WP/mysql" --user="$(id -un)" --auth-root-authentication-method=normal >/dev/null
  fi
  nohup mariadbd --no-defaults --datadir="$WP/mysql" --socket="$SOCK" --port="$DBPORT" \
    --bind-address=127.0.0.1 --user="$(id -un)" --pid-file="$WP/mysql.pid" \
    --character-set-server=utf8mb4 --skip-log-bin >"$WP/mysql.log" 2>&1 &
  for _ in $(seq 1 60); do db_running && return 0; sleep 0.5; done
  echo "MariaDB startet nicht, siehe $WP/mysql.log" >&2; exit 1
}

start_web() {
  web_running && return 0
  PHP_CLI_SERVER_WORKERS=6 nohup php -d upload_max_filesize=32M -d post_max_size=40M -d memory_limit=512M \
    -S "127.0.0.1:$PORT" -t "$WP/core" "$ROOT/scripts/router.php" >"$WP/php.log" 2>&1 &
  echo $! >"$WP/php.pid"
  for _ in $(seq 1 40); do web_running && return 0; sleep 0.25; done
  echo "PHP-Server startet nicht, siehe $WP/php.log" >&2; exit 1
}

stop_all() {
  [ -f "$WP/php.pid" ] && kill "$(cat "$WP/php.pid")" 2>/dev/null || true
  pkill -f "php .*-S 127.0.0.1:$PORT" 2>/dev/null || true
  db_running && mariadb-admin --socket="$SOCK" -uroot shutdown >/dev/null 2>&1 || true
  rm -f "$WP/php.pid"
}

setup() {
  [ -d "$ROOT/vendor/johnpbloch/wordpress-core" ] || (cd "$ROOT" && composer install --no-interaction)
  mkdir -p "$WP/content/plugins" "$WP/content/themes/mct-dev" "$WP/content/uploads"
  if [ ! -f "$WP/core/wp-load.php" ]; then
    mkdir -p "$WP/core" && cp -r "$ROOT/vendor/johnpbloch/wordpress-core/." "$WP/core/"
  fi
  ln -sfn "$ROOT/plugin/mediclever-tagebuch" "$WP/content/plugins/mediclever-tagebuch"
  cp "$ROOT/scripts/dev-theme/"* "$WP/content/themes/mct-dev/"
  sed -e "s|__SOCK__|$SOCK|g" -e "s|__PORT__|$PORT|g" -e "s|__CONTENT__|$WP/content|g" \
    "$ROOT/scripts/wp-config.tpl.php" >"$WP/core/wp-config.php"
  start_db
  "${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS mct_dev CHARACTER SET utf8mb4; CREATE DATABASE IF NOT EXISTS mct_test CHARACTER SET utf8mb4;"
  php "$ROOT/scripts/wp-install.php" "$WP/core"
}

reset() {
  start_db
  "${MYSQL[@]}" -e "DROP DATABASE IF EXISTS mct_dev; CREATE DATABASE mct_dev CHARACTER SET utf8mb4;"
  rm -rf "$WP/content/mct-data"
  php "$ROOT/scripts/wp-install.php" "$WP/core"
}

case "${1:-}" in
  setup) setup ;;
  start) [ -f "$WP/core/wp-config.php" ] || setup; start_db; start_web; echo "http://127.0.0.1:$PORT/tagebuch/" ;;
  stop) stop_all ;;
  reset) reset ;;
  status) db_running && echo "DB läuft" || echo "DB aus"; web_running && echo "Web läuft" || echo "Web aus" ;;
  *) echo "Aufruf: scripts/wp.sh setup|start|stop|reset|status"; exit 2 ;;
esac
