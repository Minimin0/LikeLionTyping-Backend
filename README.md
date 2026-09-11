# 멋쟁이 타자처럼 Backend

축제 현장의 참가자 식별, 이용권 소비, 게임 기록, 랭킹, 운영자 복구를 담당하는 API입니다.

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

## Database

Participant, Category, Sentence, PlayPass, GameSession 데이터는 MySQL에 영속화됩니다. 운영 카테고리와 문장은 [docs/CONTENT_REQUIRED.md](docs/CONTENT_REQUIRED.md)를 따라 배포 전에 확정해야 합니다.

## Flyway

`V1__create_core_tables.sql`이 테이블, 외래 키, 유니크 제약과 조회 인덱스를 생성합니다. Hibernate는 항상 `ddl-auto=validate`를 사용합니다.

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

## Admin

`POST /api/admin/login`에서 받은 Bearer 토큰으로 `/api/admin/**`를 호출합니다. 전화번호는 관리자 참가자 조회에서만 반환됩니다.

## Production Notes

Nginx에서 HTTPS를 종료하고 Spring Boot와 MySQL은 loopback에서만 수신합니다. 재현 가능한 설치, release, 검증, rollback, 장애 대응 절차는 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)에 있습니다.

운영 후보 파일:

- `deploy/backend.env.example`
- `deploy/systemd-example.service`
- `deploy/nginx-example.conf`
- `scripts/production-preflight.sh`
- `scripts/deploy-backend.sh`
- `scripts/verify-deployment.sh`

`scripts/smoke-test.sh`는 데이터를 생성하는 명시적 write test입니다. 일반 배포 확인에는 read-only `verify-deployment.sh`를 사용합니다.
