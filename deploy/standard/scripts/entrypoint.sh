#!/usr/bin/env bash
set -euo pipefail

export MYSQL_DATABASE="${MYSQL_DATABASE:-db_foxnas}"
export MYSQL_USER="${MYSQL_USER:-foxnas}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
export JWT_SECRET="${JWT_SECRET:-}"
export OAUTH_BASE_URL="${OAUTH_BASE_URL:-http://localhost}"
export OAUTH_FRONTEND_BASE_URL="${OAUTH_FRONTEND_BASE_URL:-http://localhost}"
export APP_SERVER_PORT="${APP_SERVER_PORT:-8080}"
export BROADCAST_PORT="${BROADCAST_PORT:-25522}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:3306/${MYSQL_DATABASE}?useSSL=false&serverTimezone=GMT%2B8}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${MYSQL_USER}}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${MYSQL_PASSWORD}}"
export MONGODB_HOST="${MONGODB_HOST:-mongodb}"
export MONGODB_PORT="${MONGODB_PORT:-27017}"
export MONGODB_DATABASE="${MONGODB_DATABASE:-foxnas_logs}"
export JWT_EXPIRATION="${JWT_EXPIRATION:-86400000}"
export APP_SPRING_MAIL_HOST="${APP_SPRING_MAIL_HOST:-smtp.qq.com}"
export APP_SPRING_MAIL_PORT="${APP_SPRING_MAIL_PORT:-465}"
export APP_SPRING_MAIL_FROM="${APP_SPRING_MAIL_FROM:-}"
export APP_SPRING_MAIL_USERNAME="${APP_SPRING_MAIL_USERNAME:-${SPRING_MAIL_USERNAME:-}}"
export APP_SPRING_MAIL_PASSWORD="${APP_SPRING_MAIL_PASSWORD:-${SPRING_MAIL_PASSWORD:-}}"
export APP_SPRING_MAIL_DEFAULT_ENCODING="${APP_SPRING_MAIL_DEFAULT_ENCODING:-UTF-8}"
export APP_SPRING_MAIL_SMTP_AUTH="${APP_SPRING_MAIL_SMTP_AUTH:-true}"
export APP_SPRING_MAIL_SMTP_STARTTLS_ENABLE="${APP_SPRING_MAIL_SMTP_STARTTLS_ENABLE:-true}"
export APP_SPRING_MAIL_SMTP_STARTTLS_REQUIRED="${APP_SPRING_MAIL_SMTP_STARTTLS_REQUIRED:-true}"
export APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_CLASS="${APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_CLASS:-javax.net.ssl.SSLSocketFactory}"
export APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_PORT="${APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_PORT:-465}"
export APP_SPRING_MAIL_SMTP_CONNECTION_TIMEOUT="${APP_SPRING_MAIL_SMTP_CONNECTION_TIMEOUT:-5000}"
export APP_SPRING_MAIL_SMTP_TIMEOUT="${APP_SPRING_MAIL_SMTP_TIMEOUT:-5000}"
export APP_SPRING_MAIL_SMTP_WRITE_TIMEOUT="${APP_SPRING_MAIL_SMTP_WRITE_TIMEOUT:-5000}"
export APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_FALLBACK="${APP_SPRING_MAIL_SMTP_SOCKET_FACTORY_FALLBACK:-false}"
export APP_MULTIPART_MAX_FILE_SIZE="${APP_MULTIPART_MAX_FILE_SIZE:-10240MB}"
export APP_MULTIPART_MAX_REQUEST_SIZE="${APP_MULTIPART_MAX_REQUEST_SIZE:-10240MB}"
export APP_LOG_MAX_FILE_SIZE="${APP_LOG_MAX_FILE_SIZE:-10MB}"
export APP_LOG_MAX_HISTORY="${APP_LOG_MAX_HISTORY:-30}"
export GITHUB_CLIENT_ID="${GITHUB_CLIENT_ID:-}"
export GITHUB_CLIENT_SECRET="${GITHUB_CLIENT_SECRET:-}"
export QQ_CLIENT_ID="${QQ_CLIENT_ID:-}"
export QQ_CLIENT_SECRET="${QQ_CLIENT_SECRET:-}"
export MICROSOFT_CLIENT_ID="${MICROSOFT_CLIENT_ID:-}"
export MICROSOFT_CLIENT_SECRET="${MICROSOFT_CLIENT_SECRET:-}"
export MYSQL_SCHEMA_FILE="${MYSQL_SCHEMA_FILE:-/opt/foxnas/sql/db_foxnas_schema.sql}"

if [ -z "${MYSQL_PASSWORD}" ]; then
  echo "[entrypoint] ERROR: 请设置 MYSQL_PASSWORD" >&2
  exit 1
fi

if [ -z "${MYSQL_ROOT_PASSWORD}" ]; then
  echo "[entrypoint] ERROR: 请设置 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

if [ -z "${JWT_SECRET}" ]; then
  JWT_SECRET="$(openssl rand -hex 32)"
  export JWT_SECRET
  echo "[entrypoint] JWT_SECRET 未设置，已自动生成临时密钥"
elif [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "[entrypoint] ERROR: JWT_SECRET 长度不足，至少32位" >&2
  exit 1
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

  if [ ! -f "${MYSQL_SCHEMA_FILE}" ]; then
    echo "[entrypoint] ERROR: MySQL 初始化文件不存在: ${MYSQL_SCHEMA_FILE}" >&2
    exit 1
  fi

  echo "[entrypoint] Importing MySQL schema: ${MYSQL_SCHEMA_FILE}"
  mysql --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${MYSQL_SCHEMA_FILE}"

  mysqladmin --socket=/tmp/mysql.sock -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  wait "$pid" || true
fi

if [ ! -f /opt/foxnas/config/config.properties ] || grep -Eq '\$\{spring\.[^}]+\}' /opt/foxnas/config/config.properties; then
  echo "[entrypoint] Rendering config.properties from template..."
  envsubst < /opt/foxnas/config/config.properties.template > /opt/foxnas/config/config.properties
fi

exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf

