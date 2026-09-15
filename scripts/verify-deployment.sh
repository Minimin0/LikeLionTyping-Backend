#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-likelion-typing}"
LOCAL_BASE_URL="${LOCAL_BASE_URL:-http://127.0.0.1:8080/api}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-}"

systemctl is-active --quiet "$SERVICE_NAME"
local_health="$(curl --fail --silent --show-error --max-time 10 "$LOCAL_BASE_URL/health")"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$local_health"
echo "PASS: service active and localhost health UP"

if [[ -n "$PUBLIC_BASE_URL" ]]; then
    [[ "$PUBLIC_BASE_URL" == https://* ]] || { echo "PUBLIC_BASE_URL must use HTTPS" >&2; exit 1; }
    public_health="$(curl --fail --silent --show-error --max-time 10 "${PUBLIC_BASE_URL%/}/health")"
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$public_health"
    echo "PASS: public HTTPS health UP"
else
    echo "SKIP: public HTTPS health; PUBLIC_BASE_URL is not set"
fi
