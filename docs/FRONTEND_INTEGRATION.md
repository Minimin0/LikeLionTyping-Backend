# Frontend Integration

개발 기본 URL은 `http://localhost:8080/api`이며 운영에서는 현재 HTTPS origin의 `/api`를 사용합니다.

1. `/participants/identify` 결과의 `participantId`와 `availablePassCount`를 저장합니다.
2. `/categories`에서 서버가 반환한 ID를 사용합니다. 코드·문장 내용을 프론트에 하드코딩하지 않습니다.
3. `/game-sessions` 응답의 5개 문장을 순서대로 표시하고 `gameSessionId`를 보존합니다.
4. 종료 시 브라우저에서 측정한 양의 정수 밀리초를 `/game-sessions/{id}/complete`에 전송합니다.
5. 완료 응답을 잃으면 재완료하지 말고 먼저 `GET /game-sessions/{id}`로 저장 상태를 확인합니다.
6. `/rankings?categoryId=...`의 `rank`를 그대로 표시합니다. 동률을 프론트에서 재계산하지 않습니다.

관리자 화면은 `/admin/login` 토큰을 메모리에 보관하고 각 관리자 요청에 `Authorization: Bearer ...`를 붙입니다. 토큰 만료 또는 401/403이면 다시 로그인합니다. 참가자 검색, PAID 발급, 무효화/복구 버튼은 요청 중 비활성화하되 서버의 멱등·잠금 방어를 최종 기준으로 봅니다.

오류는 HTTP 상태와 `code`로 분기합니다. 특히 `NICKNAME_MISMATCH`, `NO_AVAILABLE_PASS`, `ACTIVE_GAME_EXISTS`, `INVALID_GAME_STATE`, `SENTENCE_CONTENT_INVALID`를 사용자에게 상황별로 안내하고, 서버의 `message`에 UI 문구를 의존하지 않습니다.
