#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || { echo "usage: EXPECTED_SHA256=<sha256> $0 <jar> <git-commit>" >&2; exit 2; }
: "${EXPECTED_SHA256:?set checksum produced on the build machine}"

artifact="$1"
release_id="$2"
app_root="${APP_ROOT:-/opt/likelion-typing}"
service="${SERVICE_NAME:-likelion-typing}"
local_health="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/api/health}"

[[ -f "$artifact" ]] || { echo "artifact not found: $artifact" >&2; exit 1; }
[[ "$release_id" =~ ^[0-9a-f]{40}$ ]] || { echo "git-commit must be a full 40-character SHA" >&2; exit 1; }
actual_sha="$(sha256sum "$artifact" | awk '{print $1}')"
[[ "$actual_sha" == "$EXPECTED_SHA256" ]] || { echo "artifact checksum mismatch" >&2; exit 1; }

release_dir="$app_root/releases/$release_id"
install -d -o root -g likelion-typing -m 0750 "$app_root/releases" "$release_dir"
if [[ -f "$release_dir/app.jar" ]]; then
    [[ "$(sha256sum "$release_dir/app.jar" | awk '{print $1}')" == "$EXPECTED_SHA256" ]] || {
        echo "release directory exists with a different artifact" >&2
        exit 1
    }
else
    install -o root -g likelion-typing -m 0440 "$artifact" "$release_dir/app.jar"
    printf '%s  app.jar\n' "$EXPECTED_SHA256" >"$release_dir/app.jar.sha256"
    chown root:likelion-typing "$release_dir/app.jar.sha256"
    chmod 0440 "$release_dir/app.jar.sha256"
fi

previous="$(readlink "$app_root/current" 2>/dev/null || true)"
ln -sfn "releases/$release_id" "$app_root/current.next"
mv -Tf "$app_root/current.next" "$app_root/current"
[[ -z "$previous" ]] || ln -sfn "$previous" "$app_root/previous"

systemctl restart "$service"
systemctl is-active --quiet "$service"
for _ in {1..30}; do
    health="$(curl --fail --silent --show-error --max-time 3 "$local_health" 2>/dev/null || true)"
    if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$health"; then
        echo "deployed commit $release_id"
        exit 0
    fi
    sleep 2
done

echo "deployment health check failed; inspect journalctl -u $service" >&2
exit 1
