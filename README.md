# 멋쟁이 타자처럼 Backend

축제 현장의 참가자 식별, 이용권 소비, 게임 기록, 랭킹, 운영자 복구를 담당하는 API입니다.

## 2026-09-15 Final Release

Backend는 현재 Production 기준 기능과 API를 Freeze하고 Frontend 최종 통합을 지원합니다.

- 기준선: `develop`
- 최종 통합/Release 목표: **2026-09-15**
- 신규 Backend API/기능: P0/P1 운영 문제 외에는 추가하지 않음
- 상세 기준: [docs/FINAL_RELEASE_BACKEND_2026-09-13.md](docs/FINAL_RELEASE_BACKEND_2026-09-13.md)

## Tech Stack

- Java 21, Spring Boot 3.5.5, Gradle 8.14.3
- Spring MVC, Data JPA, Security, Validation
- MySQL, Flyway

## Requirements

- JDK 21
- MySQL 8

## Local Development

MySQL에 `likelion_typing` 데이터베이스와 전용 사용자를 만든 뒤 `.env.example`의 변수를 셸 환경에 설정합니다. 실제 비밀번호와 키는 파일에 커밋하지 않습니다.

## Environment Variables

| Name | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `ADMIN_PASSWORD_HASH` | BCrypt admin password hash |
| `ADMIN_TOKEN_SECRET` | At least 32 random characters |
| `ADMIN_TOKEN_TTL_SECONDS` | Optional token lifetime; default 28800 |
| `ALLOWED_ORIGINS` | Comma-separated frontend origins |

## Database / Production Content

Participant, Category, Sentence, PlayPass, GameSession 데이터는 MySQL에 영속화됩니다.

Flyway V1이 core schema를 만들고, Flyway V2가 운영 카테고리 3개 / 문장 15개를 seed합니다. Backend Production DB가 콘텐츠 Source of Truth이며 Frontend가 운영 문장을 별도 결정하지 않습니다.

Hibernate는 항상 `ddl-auto=validate`를 사용합니다.

## 확정 정책

- 전화번호 1개당 닉네임 1개
- 최초 참가자는 전체 서비스 기준 FREE 1회
- 재도전은 500원 결제 확인 후 Admin이 PAID 이용권 발급
- 재도전 GameSession은 모두 저장
- Ranking에는 참가자별/카테고리별 `COMPLETED` 기록 중 최소 `elapsedMs`만 반영
- 느린 최신 기록이 기존 Personal Best를 덮어쓰지 않음
- 이용권 / 공식 기록 / PB / Ranking / GameSession 상태는 Backend Authority

## Running

```bash
./gradlew bootRun
```

## Tests

```bash
./gradlew clean test
./gradlew build
```

테스트는 H2 MySQL 호환 모드에서 Flyway와 JPA 스키마 검증을 포함하며 MySQL 8.0.46에서도 검증되었습니다. datasource 환경변수를 MySQL 테스트 DB로 덮어쓰면 같은 테스트를 재실행할 수 있습니다.

## API

[docs/API.md](docs/API.md)를 참고합니다. 빠른 현장 검증은 환경변수를 지정한 뒤 `scripts/smoke-test.sh`로 실행합니다.

Public API:

- `POST /api/participants/identify`
- `GET /api/categories`
- `POST /api/game-sessions`
- `POST /api/game-sessions/{id}/complete`
- `GET /api/game-sessions/{id}`
- `GET /api/rankings?categoryId=...`

## Admin

`POST /api/admin/login`에서 받은 Bearer 토큰으로 보호된 `/api/admin/**`를 호출합니다.

이번 Release의 Admin 범위는 다음으로 고정합니다.

- 전화번호 기반 참가자 조회
- PAID 이용권 발급
- GameSession 무효화 + 필요 시 이용권 복구

전화번호/닉네임 수정, 참가자 삭제, 환불/취소, 순위/기록 직접 수정, 등록 마감 토글은 이번 Release 범위가 아닙니다.

## Production Notes

Nginx에서 HTTPS를 종료하고 Spring Boot와 MySQL은 loopback에서만 수신합니다. Frontend Production은 same-origin `/api`를 사용합니다.

Domain/HTTPS 적용 후 `ALLOWED_ORIGINS`를 실제 HTTPS origin으로 고정하며 wildcard CORS는 사용하지 않습니다.

재현 가능한 설치, release, 검증, rollback, 장애 대응 절차는 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)에 있습니다.

운영 후보 파일:

- `deploy/backend.env.example`
- `deploy/systemd-example.service`
- `deploy/nginx-example.conf`
- `scripts/production-preflight.sh`
- `scripts/deploy-backend.sh`
- `scripts/verify-deployment.sh`

`scripts/smoke-test.sh`는 데이터를 생성하는 명시적 write test입니다. 일반 배포 확인에는 read-only `verify-deployment.sh`를 사용합니다.
