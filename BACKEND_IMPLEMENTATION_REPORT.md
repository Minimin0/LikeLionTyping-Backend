# Backend Implementation Report

## 1. Repository State

- Repository: `Minimin0/LikeLionTyping-Backend`
- Branch: `feat/backend-core`
- Implementation commit: `b088fd1`
- Java: 21
- Spring Boot: 3.5.5
- Build tool: Gradle Wrapper 8.14.3

## 2. Implemented Architecture

기능 경계는 `participant`, `category`, `pass`, `game`, `ranking`, `admin`, `security`, `common`으로 나눴습니다. Controller는 HTTP/validation, Service는 트랜잭션과 상태 전이, Repository는 잠금과 조회, DTO는 외부 계약을 담당합니다.

## 3. Database

`participants`, `categories`, `sentences`, `play_passes`, `game_sessions` 테이블을 Flyway V1에서 생성합니다. phone, category code, category/sequence, 참가자별 FREE 소유자에 유니크 제약을 두고 pass·game·ranking 경로에 복합 인덱스를 추가했습니다. `elapsed_ms`는 BIGINT이며 `ddl-auto=validate`입니다.

## 4. API

| Endpoint | Status |
| --- | --- |
| `POST /api/participants/identify` | DONE |
| `GET /api/categories` | DONE; production content required |
| `POST /api/game-sessions` | DONE |
| `POST /api/game-sessions/{id}/complete` | DONE |
| `GET /api/game-sessions/{id}` | DONE; additive recovery API |
| `GET /api/rankings` | DONE |
| `POST /api/admin/login` | DONE |
| `GET /api/admin/participants` | DONE |
| `POST /api/admin/participants/{id}/passes` | DONE |
| `POST /api/admin/game-sessions/{id}/invalidate` | DONE |

## 5. Security

관리자 원문 비밀번호는 저장하지 않고 환경변수 BCrypt 해시로 검증합니다. 로그인은 만료 시각을 HMAC-SHA256으로 서명한 Bearer 토큰을 발급합니다. `/api/admin/**`만 보호하며 login과 public API는 공개합니다. CORS origin은 환경변수로 제한합니다. public DTO에는 phone 필드가 없습니다.

## 6. Transaction & Concurrency

- Identify: participant와 FREE를 한 트랜잭션에서 만들며 phone/FREE 유니크 제약과 중복 race 재조회로 방어합니다.
- Start: participant 행을 잠그고 기존 IN_PROGRESS를 검사한 뒤 FREE 우선, 오래된 PAID 순으로 잠가 소비합니다.
- Complete: GameSession 행 잠금 후 IN_PROGRESS만 COMPLETED로 전이합니다.
- Paid issue: participant 행 잠금 후 미사용 PAID가 있으면 그대로 반환합니다.
- Invalidate: participant와 GameSession을 잠그고 INVALIDATED 전이와 선택적 pass 복구를 한 트랜잭션에서 수행합니다.

INVALIDATED 이력은 보존하고 복구한 PlayPass는 새 게임에 재사용합니다. GameSession의 play_pass_id에 UNIQUE를 두지 않았으며, 같은 pass가 다른 non-invalidated 게임에 연결돼 있으면 복구를 거부합니다.

## 7. Test Results

- Command: `./gradlew clean test`
- Result: PASS
- Total: 8, Success: 8, Failure: 0, Skipped: 0
- Coverage: identify/free, normalization, nickname mismatch, concurrent identify/start/complete/paid issue, five sentences, pass depletion, personal best, tie ranking, invalidation/restore, recovery, admin auth, public phone exclusion
- MySQL Testcontainers: BLOCKED because Docker is unavailable on this host; H2 MySQL mode Flyway/JPA integration passed.

## 8. Release Gate

| Gate | Result |
| --- | --- |
| FREE once / no duplicate / nickname mismatch | PASS |
| PAID issue and double-click defense | PASS |
| pass consumption / duplicate session / duplicate complete | PASS |
| elapsedMs / personal best / category ranking / tie | PASS |
| public phone exclusion | PASS |
| invalidation / ranking exclusion / atomic restore | PASS |
| unauthenticated admin blocked | PASS |
| Flyway / restart-safe schema / ddl validate | PASS in H2; MySQL verification BLOCKED |
| no committed secrets | PASS |
| production categories and 15 sentences | BLOCKED: team input required |

## 9. Spec Ambiguities / Engineering Decisions

- Category names and 15 sentences were absent, so no production seed was invented. `docs/CONTENT_REQUIRED.md` defines the input gate.
- Restored passes may be reused only after all prior sessions using that pass are INVALIDATED.
- Admin auth uses a short-lived signed Bearer token without adding a JWT dependency.
- Simultaneous PAID issue returns one existing AVAILABLE PAID; another paid purchase requires consuming it first.
- `GET /api/game-sessions/{id}` was added for completion-response recovery.
- Same-category duplicate start returns the existing IN_PROGRESS session; another category returns `ACTIVE_GAME_EXISTS`.

## 10. Remaining Risks

- P0: production category names and 15 sentences are required before game traffic.
- P0: production DB/admin secrets and HTTPS certificate are required before deployment.
- P1: run the integration suite against MySQL 8 with Testcontainers or a test DB.
- P2: token revocation is not supported; short TTL and secret rotation are sufficient for the single-admin MVP.

## 11. Frontend Integration Guide

Use server IDs and sentences, store `gameSessionId`, submit integer milliseconds, recover uncertain completion through GET, display server-provided rank, and send the admin Bearer token only to admin endpoints. See `docs/FRONTEND_INTEGRATION.md`.

## 12. Deployment Readiness

EC2 systemd and Nginx HTTPS examples are present. DB, admin hash, token secret and allowed origin are environment-only. Flyway runs before Hibernate validation. Real AWS resources, DNS, certificate, MySQL and production content remain external deployment inputs.

## 13. Git Status

Implementation was committed on `feat/backend-core`; documentation is committed separately. Final status and commit hashes are reported at handoff.
