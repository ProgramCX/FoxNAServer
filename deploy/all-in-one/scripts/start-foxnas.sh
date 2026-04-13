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

exec java -jar /opt/foxnas/app.jar --spring.config.location=file:/opt/foxnas/config/config.properties
