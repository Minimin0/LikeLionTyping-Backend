#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/opt/likelion-typing}"
ENV_FILE="${ENV_FILE:-/etc/likelion-typing/backend.env}"
SERVICE_FILE="${SERVICE_FILE:-/etc/systemd/system/likelion-typing.service}"
MIN_FREE_MB="${MIN_FREE_MB:-1024}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
ARTIFACT_PATH="${ARTIFACT_PATH:-$APP_ROOT/current/app.jar}"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "PASS: $*"; }
value_of() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1; }

for command in java nginx mysqladmin curl sha256sum systemctl systemd-analyze ss; do
    command -v "$command" >/dev/null 2>&1 || fail "$command is not installed"
done

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ "$java_major" == "21" ]] || fail "Java 21 is required"
pass "Java 21"

[[ -f "$ENV_FILE" ]] || fail "$ENV_FILE is missing"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail "$ENV_FILE must have mode 600"
[[ "$(stat -c '%U' "$ENV_FILE")" == "root" ]] || fail "$ENV_FILE must be owned by root"
for name in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD ADMIN_PASSWORD_HASH ADMIN_TOKEN_SECRET ALLOWED_ORIGINS SERVER_ADDRESS SERVER_PORT; do
    grep -Eq "^${name}=.+" "$ENV_FILE" || fail "$name is missing"
    [[ "$(value_of "$name")" != *'<'*'>'* ]] || fail "$name still contains a placeholder"
    echo "$name: SET"
done

datasource_url="$(value_of SPRING_DATASOURCE_URL)"
admin_hash="$(value_of ADMIN_PASSWORD_HASH)"
token_secret="$(value_of ADMIN_TOKEN_SECRET)"
allowed_origins="$(value_of ALLOWED_ORIGINS)"
[[ "$datasource_url" == jdbc:mysql://* ]] || fail "datasource URL must use MySQL JDBC"
[[ "$admin_hash" =~ ^\$2[aby]\$ ]] || fail "ADMIN_PASSWORD_HASH must be BCrypt"
(( ${#token_secret} >= 32 )) || fail "ADMIN_TOKEN_SECRET must be at least 32 characters"
IFS=',' read -ra origin_list <<<"$allowed_origins"
for origin in "${origin_list[@]}"; do
    origin="${origin//[[:space:]]/}"
    [[ "$origin" == https://* && "$origin" != *'*'* ]] || fail "ALLOWED_ORIGINS must use explicit HTTPS origins"
done
[[ "$(value_of SERVER_ADDRESS)" == "127.0.0.1" ]] || fail "SERVER_ADDRESS must be 127.0.0.1"
[[ "$(value_of SERVER_PORT)" == "8080" ]] || fail "SERVER_PORT must be 8080"

[[ -f "$SERVICE_FILE" ]] || fail "$SERVICE_FILE is missing"
systemd-analyze verify "$SERVICE_FILE"
pass "systemd unit syntax"

nginx -t
pass "Nginx configuration"

mysqladmin ping --host="$MYSQL_HOST" --port="$MYSQL_PORT" --silent >/dev/null || fail "MySQL is not reachable"
pass "MySQL reachable at configured host and port"

[[ -f "$ARTIFACT_PATH" ]] || fail "$ARTIFACT_PATH is missing"
if [[ -n "${EXPECTED_SHA256:-}" ]]; then
    [[ "$(sha256sum "$ARTIFACT_PATH" | awk '{print $1}')" == "$EXPECTED_SHA256" ]] || fail "artifact checksum mismatch"
elif [[ "$ARTIFACT_PATH" == "$APP_ROOT/current/app.jar" && -f "$APP_ROOT/current/app.jar.sha256" ]]; then
    (cd "$APP_ROOT/current" && sha256sum --check --status app.jar.sha256) || fail "artifact checksum mismatch"
else
    fail "set EXPECTED_SHA256 for the candidate artifact"
fi
pass "artifact checksum"

free_mb="$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')"
(( free_mb >= MIN_FREE_MB )) || fail "less than ${MIN_FREE_MB}MB free on application filesystem"
pass "disk space ${free_mb}MB free"

if ss -ltn '( sport = :8080 )' | grep -q ':8080'; then
    echo "INFO: port 8080 is in use"
else
    echo "INFO: port 8080 is available"
fi

pass "production preflight complete"
