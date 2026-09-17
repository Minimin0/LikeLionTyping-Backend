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

### `GET /participants/{id}/play-state`

서버 기준 참가 가능 상태를 반환합니다. 인증 없음. 전화번호, 결제 이력, Admin 전용 정보는 반환하지 않습니다.

Response: `{ "availablePassCount": 0, "activeGame": null }`

진행 중인 경기가 있으면 문장 없이 최소 식별자만 반환합니다.

Response: `{ "availablePassCount": 0, "activeGame": { "gameSessionId": 1, "categoryId": 1 } }`

`availablePassCount`는 AVAILABLE 이용권 수입니다. 0장이면 새 게임 생성은 차단되지만, 이미 이용권이 소비된 `IN_PROGRESS` 경기는 0장이어도 계속할 수 있습니다.

### `GET /categories`

`code` 오름차순으로 카테고리를 반환합니다. 인증 없음.

Response: `[{ "id": 1, "code": "CH01", "name": "..." }]`

### `POST /game-sessions`

이용권을 소비하고 게임을 시작합니다. 인증 없음.

Request: `{ "participantId": 1, "categoryId": 1 }`

Response: `{ "gameSessionId": 1, "category": { "id": 1, "code": "CH01", "name": "..." }, "sentences": [{ "sequence": 1, "content": "..." }], "resumedExisting": false, "passConsumed": true, "availablePassCount": 0 }`

새 세션이면 `resumedExisting=false`, `passConsumed=true`, `availablePassCount`는 소비 후 서버 기준 AVAILABLE 이용권 수입니다. 같은 참가자의 같은 카테고리 시작 재요청은 기존 진행 세션을 반환하며 `resumedExisting=true`, `passConsumed=false`, `availablePassCount`는 현재 서버 기준 AVAILABLE 이용권 수입니다. 0 remaining passes does not invalidate a game whose pass was already consumed. 0 remaining passes prevents creation of a NEW game. 문장이 정확히 5개가 아니면 `409 SENTENCE_CONTENT_INVALID`, 이용권이 없으면 `409 NO_AVAILABLE_PASS`입니다. 다른 카테고리 진행 중이면 `409 ACTIVE_GAME_EXISTS`입니다. 이용권 선택 순서는 FREE, 생성일이 오래된 PAID입니다.

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

### `GET /admin/participants?query={phoneOrNickname}`

전화번호 또는 닉네임으로 참가자, 이용권, 게임 이력을 조회합니다. 전화번호는 하이픈과 공백을 제거해 조회하며, 닉네임은 부분 검색입니다. 닉네임은 중복될 수 있으므로 배열을 반환합니다. 운영 목적상 이 응답에만 정규화된 전화번호가 포함됩니다.

Response: `[{ "id": 1, "nickname": "lion", "phone": "01012345678", "passes": [], "gameSessions": [], "payments": [], "summary": { ... } }]`

기존 프론트 호환을 위해 `GET /admin/participants?phone={phone}`도 같은 상세 구조의 단일 참가자 응답으로 유지합니다.

### `GET /admin/dashboard`

운영 대시보드 지표를 반환합니다. 결제 합계는 `payment_records` 원장 합계입니다.

### `POST /admin/participants/{id}/passes`

결제를 현장에서 확인한 뒤 `{ "quantity": 2 }`처럼 수량을 전달해 PAID 이용권을 누적 발급합니다. 서버가 `amountKrw = quantity * 500`으로 계산하고, 한 트랜잭션에서 결제 원장과 PAID 이용권을 함께 생성합니다.

### `POST /admin/game-sessions/{id}/invalidate`

Request: `{ "reason": "키보드 오류", "restorePass": true }`. 게임을 INVALIDATED로 바꾸고, 요청한 경우 다른 정상 경기에 연결되지 않은 소비 이용권을 같은 트랜잭션에서 AVAILABLE로 복구합니다. `restorePass: false`면 기록만 무효화하고 이용권은 복구하지 않습니다.
