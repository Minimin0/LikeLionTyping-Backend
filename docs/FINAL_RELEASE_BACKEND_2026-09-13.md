# Backend Final Release Decisions — 2026-09-13

최종 통합/Release 목표: **2026-09-15**  
기준 브랜치: **develop**

> Backend는 이미 Production 배포와 주요 E2E가 완료된 상태다. 9/15까지는 신규 기능보다 현재 계약을 고정하고 Frontend 최종 통합을 지원하는 것이 목표다.

## 1. Backend Authority

Backend가 최종 결정한다.

- 최초 FREE 사용 가능 여부
- PlayPass 사용 가능 여부
- GameSession 공식 상태
- 공식 기록 인정 여부
- Personal Best
- Ranking
- PAID 이용권 유효성

Frontend는 서버가 반환한 상태를 표시한다.

## 2. 참가 / 이용권 정책

- 전화번호 1개당 닉네임 1개.
- 최초 참가자는 전체 서비스 기준 FREE 1회.
- FREE는 카테고리별 1회가 아니다.
- 재도전은 500원 결제 확인 후 Admin이 PAID 이용권을 발급한다.
- 이미 AVAILABLE PAID 이용권이 있으면 중복 발급하지 않고 기존 이용권을 반환한다.

## 3. 기록 / Ranking 정책

- 공식 기록 단위는 정수 millisecond `elapsedMs`.
- 재도전 GameSession은 각각 저장한다.
- 참가자별/카테고리별 `COMPLETED` GameSession의 최소 `elapsedMs`가 Personal Best다.
- Ranking도 참가자별/카테고리별 Personal Best만 사용한다.
- 느린 최신 기록이 기존 최고 기록을 덮어쓰지 않는다.
- 동률은 같은 rank를 사용한다.
- `INVALIDATED` session은 Ranking에 포함하지 않는다.

예:

```text
42.000초
38.500초
40.200초
→ 공식 Ranking 기록: 38.500초
```

## 4. Admin Release 범위

Admin API는 아래 범위로 Freeze한다.

1. `POST /api/admin/login`
2. `GET /api/admin/participants?query=...`
3. `POST /api/admin/participants/{id}/passes`
4. `POST /api/admin/game-sessions/{id}/invalidate`

Admin 역할:

- 관리자 인증
- 전화번호 또는 닉네임 기반 참가자 조회
- 결제 확인 후 PAID 이용권 발급
- 문제 GameSession 무효화
- 요청 시 해당 session이 소비한 이용권 복구

이번 Release에 추가하지 않는다.

- 닉네임/전화번호 수정
- 참가자 삭제
- 기록/순위 직접 수정
- 이용권 삭제/환불/취소
- standalone pass restore
- 등록 마감 토글
- 카테고리/문장 관리 API
- DB 초기화 API

## 5. Admin Security

- `/api/admin/login`만 공개.
- 나머지 `/api/admin/**`는 `ROLE_ADMIN` 필요.
- 관리자 비밀번호는 BCrypt hash로 검증.
- Admin token은 HMAC-SHA256 서명 + 만료시간 사용.
- Spring Security는 stateless.
- HTTPS 적용 전 공용 네트워크에서 Admin 브라우저 사용을 제한한다.

## 6. Production 콘텐츠 Freeze

Flyway V2가 초기 콘텐츠를 seed하고 V3가 아래 Production 콘텐츠로 갱신하며 Backend DB가 Source of Truth다.

### CH01 — 성결대 멋사

1. `안녕하세요 성결대학교 멋쟁이사자처럼입니다!`
2. `프론트엔드 백엔드 기획디자인 세 부서가 한 팀이 됩니다`
3. `상상을 코드로 아이디어를 현실로 만드는 개발동아리!`
4. `함께 고민하고 함께 성장합니다`
5. `저희의 아기사자가 되어주세요!`

### CH02 — 멋쟁이사자처럼

1. `전국 약 80개 대학이 함께하는 멋쟁이사자처럼`
2. `대표 활동은? 바로 해커톤입니다`
3. `제한된 시간 폭발하는 아이디어!`
4. `오늘의 버그가 내일의 실력이 됩니다`
5. `당신의 도전을 기다립니다 아기사자님!`

### CH03 — 페스티벌 라디오

1. `기다리던 동아리 페스티벌 오늘만큼은 마음껏 즐겨볼까요!`
2. `좋아하는 노래가 들려오면 친구와 함께 신나게 따라 불러보세요.`
3. `처음 듣는 노래도 이런 날 들으면 왠지 좋아지는 것 같아요!`
4. `신나는 음악과 웃음소리가 가득한 지금 이 순간을 제대로 즐겨봐요.`
5. `오늘 함께 들었던 노래와 추억은 오래 남을 거예요!`

띄어쓰기/문장부호까지 원문으로 취급한다.

## 7. API Contract Freeze

Frontend 최종 통합은 현재 API를 기준으로 한다.

Public:

- `POST /api/participants/identify`
- `GET /api/categories`
- `POST /api/game-sessions`
- `POST /api/game-sessions/{id}/complete`
- `GET /api/game-sessions/{id}`
- `GET /api/rankings?categoryId=...`

Admin:

- `POST /api/admin/login`
- `GET /api/admin/participants?query=...`
- `POST /api/admin/participants/{id}/passes`
- `POST /api/admin/game-sessions/{id}/invalidate`

9/15 Release 전 신규 API는 P0/P1 운영 문제를 해결하는 경우가 아니면 추가하지 않는다.

## 8. Production / Deployment 원칙

- Flyway + `ddl-auto=validate` 유지.
- Spring Boot는 loopback `127.0.0.1:8080`.
- MySQL은 loopback `127.0.0.1:3306`.
- Nginx가 `/api/*`를 Backend로 proxy.
- Frontend Production은 same-origin `/api` 사용.
- Domain/HTTPS 적용 후 `ALLOWED_ORIGINS`를 실제 HTTPS origin으로 고정.
- wildcard CORS 금지.

## 9. Test Data / 개인정보

- 행사 직전 smoke test 데이터는 즉석 SQL 삭제로 처리하지 않는다.
- 백업 → 대상 확인 → 정리 → 무결성 확인 → Ranking 확인 순서의 maintenance 절차로 정리한다.
- 전화번호 등 개인정보는 행사/상품 지급에 필요한 기간이 끝난 뒤 삭제 일정을 확정해 정리한다.

## 10. Freeze / 일정

- 09/13: Backend 정책/API 범위 확정
- 09/14: Frontend 최종 통합 지원, 필요한 회귀검증
- 09/15: 전체 E2E, 최종 Release
- 09/15 이후: 신규 기능 금지, P0/P1/보안/운영 QA만 처리
- 09/16~09/18: Domain/HTTPS/CORS/행사 PC/Admin 운영 리허설
- 09/19: 행사 운영

## 11. 최종 원칙

> Backend는 현재 Production 동작을 깨지 않는 것을 최우선으로 한다. 9/15 전에는 새로운 편의 기능보다 검증된 이용권·세션·기록·랭킹·Admin 계약을 고정한다.
