#!/usr/bin/env bash
set -euo pipefail

export MYSQL_DATABASE="${MYSQL_DATABASE:-db_foxnas}"
export MYSQL_USER="${MYSQL_USER:-foxnas}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-foxnas}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123456}"
export JWT_SECRET="${JWT_SECRET:-}"
export OAUTH_BASE_URL="${OAUTH_BASE_URL:-http://localhost}"
export OAUTH_FRONTEND_BASE_URL="${OAUTH_FRONTEND_BASE_URL:-http://localhost}"
export APP_SERVER_PORT="${APP_SERVER_PORT:-8848}"
export BROADCAST_PORT="${BROADCAST_PORT:-25522}"

if [ -z "${JWT_SECRET}" ]; then
  JWT_SECRET="$(openssl rand -hex 32)"
  export JWT_SECRET
fi

mkdir -p /opt/foxnas/config /data/files /data/redis /var/lib/mysql /var/lib/rabbitmq /var/log/foxnas
chown -R mysql:mysql /var/lib/mysql
chown -R redis:redis /data/redis
chown -R rabbitmq:rabbitmq /var/lib/rabbitmq || true

if [ ! -d /var/lib/mysql/mysql ]; then
  echo "[entrypoint] Initializing MySQL data directory..."
  mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql

  mysqld --user=mysql --datadir=/var/lib/mysql --skip-networking --socket=/tmp/mysql.sock &
  pid="$!"

  until mysqladmin --socket=/tmp/mysql.sock ping --silent; do
    sleep 1
  done

  mysql --socket=/tmp/mysql.sock -uroot <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
CREATE DATABASE IF NOT EXISTS ${MYSQL_DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON ${MYSQL_DATABASE}.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL

  mysqladmin --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  wait "$pid" || true
fi

if [ ! -f /opt/foxnas/config/config.properties ]; then
  echo "[entrypoint] Creating default config.properties..."
  envsubst < /opt/foxnas/config/config.properties.template > /opt/foxnas/config/config.properties
fi

exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf

