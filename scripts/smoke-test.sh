#!/usr/bin/env bash
set -euo pipefail

: "${PHONE:?set a fresh test PHONE}"
: "${CATEGORY_ID:?set CATEGORY_ID with five production sentences}"

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
NICKNAME="${NICKNAME:-smoke-test}"
ELAPSED_MS="${ELAPSED_MS:-43821}"

curl -fsS "$BASE_URL/health"
participant="$(curl -fsS -H 'Content-Type: application/json' -d "{\"nickname\":\"$NICKNAME\",\"phone\":\"$PHONE\"}" "$BASE_URL/participants/identify")"
participant_id="$(sed -E 's/.*"participantId":([0-9]+).*/\1/' <<<"$participant")"
curl -fsS "$BASE_URL/categories"
game="$(curl -fsS -H 'Content-Type: application/json' -d "{\"participantId\":$participant_id,\"categoryId\":$CATEGORY_ID}" "$BASE_URL/game-sessions")"
game_id="$(sed -E 's/.*"gameSessionId":([0-9]+).*/\1/' <<<"$game")"
curl -fsS -H 'Content-Type: application/json' -d "{\"elapsedMs\":$ELAPSED_MS}" "$BASE_URL/game-sessions/$game_id/complete"
curl -fsS "$BASE_URL/game-sessions/$game_id"
curl -fsS "$BASE_URL/rankings?categoryId=$CATEGORY_ID"
