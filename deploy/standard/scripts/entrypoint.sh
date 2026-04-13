#!/usr/bin/env bash
set -euo pipefail

export MYSQL_DATABASE="${MYSQL_DATABASE:-db_foxnas}"
export MYSQL_USER="${MYSQL_USER:-foxnas}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-mysql}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-mysql}"
export MONGODB_HOST="${MONGODB_HOST:-mongodb}"
export MONGODB_PORT="${MONGODB_PORT:-27017}"
export MONGODB_DATABASE="${MONGODB_DATABASE:-foxnas_logs}"
export MYSQL_SCHEMA_FILE="${MYSQL_SCHEMA_FILE:-/opt/foxnas/sql/db_foxnas_schema.sql}"

sanitize_sql_file() {
  local source_file="$1"
  local target_file="$2"

  if LC_ALL=C grep -q $'\x00' "$source_file"; then
    echo "[entrypoint] Detected NUL bytes in schema file, sanitizing before import..."
    tr -d '\000' < "$source_file" > "$target_file"
  else
    cp "$source_file" "$target_file"
  fi
}

if [ -z "${MYSQL_PASSWORD}" ]; then
  echo "[entrypoint] ERROR: 请设置 MYSQL_PASSWORD" >&2
  exit 1
fi

if [ -z "${MYSQL_ROOT_PASSWORD}" ]; then
  echo "[entrypoint] ERROR: 请设置 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

if [ ! -f /opt/foxnas/config.properties ]; then
  echo "[entrypoint] ERROR: 缺少配置文件 /opt/foxnas/config.properties，请挂载本地 config.properties" >&2
  exit 1
fi

mkdir -p /data/files /data/redis /var/lib/mysql /var/lib/rabbitmq /var/log/foxnas
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

  if [ ! -f "${MYSQL_SCHEMA_FILE}" ]; then
    echo "[entrypoint] ERROR: MySQL 初始化文件不存在: ${MYSQL_SCHEMA_FILE}" >&2
    exit 1
  fi

  schema_import_file="/tmp/db_foxnas_schema.sql"
  sanitize_sql_file "${MYSQL_SCHEMA_FILE}" "${schema_import_file}"

  echo "[entrypoint] Importing MySQL schema: ${MYSQL_SCHEMA_FILE}"
  mysql --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${schema_import_file}"

  mysqladmin --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  wait "$pid" || true
else
  echo "[entrypoint] MySQL data directory exists, checking required tables..."
  mysqld --user=mysql --datadir=/var/lib/mysql --skip-networking --socket=/tmp/mysql.sock &
  pid="$!"

  until mysqladmin --socket=/tmp/mysql.sock ping --silent; do
    sleep 1
  done

  table_exists="$(mysql --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" -Nse "SELECT 1 FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='tb_ddns_task' LIMIT 1;")"
  if [ "${table_exists:-}" != "1" ]; then
    if [ ! -f "${MYSQL_SCHEMA_FILE}" ]; then
      echo "[entrypoint] ERROR: MySQL 初始化文件不存在: ${MYSQL_SCHEMA_FILE}" >&2
      exit 1
    fi

    schema_import_file="/tmp/db_foxnas_schema.sql"
    sanitize_sql_file "${MYSQL_SCHEMA_FILE}" "${schema_import_file}"

    echo "[entrypoint] Table tb_ddns_task missing, importing schema: ${MYSQL_SCHEMA_FILE}"
    mysql --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${schema_import_file}"
  fi

  mysqladmin --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  wait "$pid" || true
fi

exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf

