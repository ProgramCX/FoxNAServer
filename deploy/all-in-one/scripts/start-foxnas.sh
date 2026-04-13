#!/usr/bin/env bash
set -euo pipefail

wait_for_port() {
  local host="$1"
  local port="$2"
  local name="$3"
  local retries=60

  while ! nc -z "$host" "$port" >/dev/null 2>&1; do
    retries=$((retries - 1))
    if [ "$retries" -le 0 ]; then
      echo "[foxnas] ${name} not ready: ${host}:${port}" >&2
      exit 1
    fi
    sleep 1
  done
}

wait_for_port 127.0.0.1 3306 "mysql"
wait_for_port 127.0.0.1 6379 "redis"
wait_for_port 127.0.0.1 5672 "rabbitmq"
wait_for_port "${MONGODB_HOST:-mongodb}" "${MONGODB_PORT:-27017}" "mongodb"

exec java \
  -Dspring.datasource.url="${SPRING_DATASOURCE_URL:-}" \
  -Dspring.datasource.username="${SPRING_DATASOURCE_USERNAME:-}" \
  -Dspring.datasource.password="${SPRING_DATASOURCE_PASSWORD:-}" \
  -Dspring.data.mongodb.host="${MONGODB_HOST:-mongodb}" \
  -Dspring.data.mongodb.port="${MONGODB_PORT:-27017}" \
  -Dspring.data.mongodb.database="${MONGODB_DATABASE:-foxnas_logs}" \
  -Dspring.mail.from="${APP_SPRING_MAIL_FROM:-}" \
  -Dspring.mail.username="${APP_SPRING_MAIL_USERNAME:-${SPRING_MAIL_USERNAME:-}}" \
  -Dspring.mail.password="${APP_SPRING_MAIL_PASSWORD:-${SPRING_MAIL_PASSWORD:-}}" \
  -Dspring.mail.host="${APP_SPRING_MAIL_HOST:-smtp.qq.com}" \
  -Dspring.mail.port="${APP_SPRING_MAIL_PORT:-465}" \
  -jar /opt/foxnas/app.jar \
  --spring.config.location=file:/opt/foxnas/config/config.properties
