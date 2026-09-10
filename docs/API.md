# API v1

Base URL: `/api`. JSON 요청은 `Content-Type: application/json`을 사용합니다.

오류 응답은 `{ "code", "message", "timestamp", "path" }` 형식입니다. 주요 코드는 `VALIDATION_ERROR`, `NICKNAME_MISMATCH`, `NO_AVAILABLE_PASS`, `INVALID_GAME_STATE`, `ACTIVE_GAME_EXISTS`, `PARTICIPANT_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `GAME_SESSION_NOT_FOUND`, `SENTENCE_CONTENT_INVALID`, `ADMIN_UNAUTHORIZED`입니다.

## Public API

### `GET /health`

서버 프로세스 상태를 반환합니다. 인증 없음. 응답: `{ "status": "UP" }`.

### `POST /participants/identify`

참가자를 생성하거나 전화번호로 식별합니다. 인증 없음.

Request: `{ "nickname": "lion", "phone": "010-1234-5678" }`

Response: `{ "participantId": 1, "nickname": "lion", "isNewParticipant": true, "availablePassCount": 1 }`

신규 참가자와 FREE 이용권은 한 트랜잭션에서 생성됩니다. 같은 전화번호와 다른 닉네임은 `409 NICKNAME_MISMATCH`입니다.

### `GET /categories`

`code` 오름차순으로 카테고리를 반환합니다. 인증 없음.

Response: `[{ "id": 1, "code": "CH01", "name": "..." }]`

### `POST /game-sessions`

이용권을 소비하고 게임을 시작합니다. 인증 없음.

Request: `{ "participantId": 1, "categoryId": 1 }`

Response: `{ "gameSessionId": 1, "category": { "id": 1, "code": "CH01", "name": "..." }, "sentences": [{ "sequence": 1, "content": "..." }] }`

문장이 정확히 5개가 아니면 `409 SENTENCE_CONTENT_INVALID`, 이용권이 없으면 `409 NO_AVAILABLE_PASS`입니다. 같은 참가자의 같은 카테고리 시작 재요청은 기존 진행 세션을 반환하며, 다른 카테고리 진행 중이면 `409 ACTIVE_GAME_EXISTS`입니다. 이용권 선택 순서는 FREE, 생성일이 오래된 PAID입니다.

### `POST /game-sessions/{id}/complete`

진행 중인 게임을 완료합니다. 인증 없음.

Request: `{ "elapsedMs": 43821 }`

Response: `{ "gameSessionId": 1, "status": "COMPLETED", "elapsedMs": 43821, "personalBestMs": 43821, "personalBest": true, "rank": 1 }`

완료 재요청 또는 무효 경기는 `409 INVALID_GAME_STATE`입니다.

### `GET /game-sessions/{id}`

응답 유실 시 상태를 재조회하는 additive recovery API입니다. 인증 없음. 완료 전에는 `personalBestMs`와 `rank`가 `null`입니다. 전화번호는 반환하지 않습니다.

### `GET /rankings?categoryId={id}`

카테고리별 참가자 최고 기록을 반환합니다. 인증 없음.

Response: `[{ "rank": 1, "nickname": "lion", "elapsedMs": 43821 }]`

COMPLETED만 포함하며 동률은 같은 공식 순위를 갖습니다. 전화번호는 반환하지 않습니다.

## Admin API

### `POST /admin/login`

인증 없음. Request: `{ "password": "..." }`. Response: `{ "token": "...", "expiresAt": "..." }`. 실패는 `401 ADMIN_UNAUTHORIZED`입니다.

아래 API는 `Authorization: Bearer <token>`이 필요합니다.

### `GET /admin/participants?phone={phone}`

전화번호로 참가자, 이용권, 게임 이력을 조회합니다. 운영 목적상 이 응답에만 정규화된 전화번호가 포함됩니다.

### `POST /admin/participants/{id}/passes`

결제를 현장에서 확인한 뒤 PAID 이용권을 발급합니다. 미사용 PAID가 이미 있으면 같은 이용권을 반환합니다.

### `POST /admin/game-sessions/{id}/invalidate`

Request: `{ "restorePass": true }`. 게임을 INVALIDATED로 바꾸고, 요청한 경우 다른 정상 경기에 연결되지 않은 소비 이용권을 같은 트랜잭션에서 AVAILABLE로 복구합니다.
