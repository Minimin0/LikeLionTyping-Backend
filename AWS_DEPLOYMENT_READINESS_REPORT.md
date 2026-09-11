# AWS Deployment Readiness Report

검증일: 2026-09-11 (Asia/Seoul)

## 1. Repository State

- Repository: `Minimin0/LikeLionTyping-Backend`
- Branch: `chore/aws-deployment-readiness`
- HEAD: PR의 `headRefOid`를 authoritative revision으로 사용
- Base: `origin/develop` `e632e9294dbc8a88778074fe9e9eeda55c620e78`
- Working tree: deployment-readiness 파일만 변경, commit 후 clean
- Backend 기능/API/Flyway migration 변경: 없음

## 2. Current Deployment Architecture

```text
Internet HTTPS :443
  -> AWS Security Group
  -> Nginx
  -> 127.0.0.1:8080 Spring Boot
  -> 127.0.0.1:3306 MySQL 8
```

단일 EC2 MVP 구조입니다. Frontend hosting 방식은 강제하지 않으며 확정된 HTTPS origin만 `ALLOWED_ORIGINS`로 받습니다.

## 3. AWS CLI Status

- Installed: NO
- Authenticated: NO
- Region configured: NO
- Credential/access key value 출력: 없음
- AWS read/write operation: 실행하지 않음

AWS CLI 부재는 repository readiness 작업을 막지 않았으며 실제 provisioning은 수행하지 않았습니다.

## 4. Server Requirements

- OS: 기존 지원 Linux 유지, 신규면 지원 중인 Ubuntu 24.04 LTS 계열 권장
- Runtime: Java 21
- Proxy: Nginx
- Database: MySQL 8, `utf8mb4`, loopback bind
- Utilities: `curl`, `unzip`, `openssl`, `apache2-utils`, `certbot`, `sha256sum`, `ss`
- Application user: non-login `likelion-typing`
- Paths: `/opt/likelion-typing`, `/etc/likelion-typing/backend.env`
- Disk/memory: instance type 확정 후 결정; preflight 기본 free-space floor 1024MB, JVM RAM percentage 방식

## 5. Network Security

| Port | Exposure |
| ---: | --- |
| 22 | `<ADMIN_IP>/32` only |
| 80 | Public, HTTPS redirect/ACME |
| 443 | Public HTTPS |
| 8080 | No public rule; `127.0.0.1` only |
| 3306 | No public rule; `127.0.0.1` only |

Nginx access log는 `$uri`만 기록하고 query string을 제외하므로 Admin phone 검색값을 저장하지 않습니다.

## 6. Environment Variables

| Variable | Required | Default | Secret |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | YES | none | NO |
| `SPRING_DATASOURCE_USERNAME` | YES | none | YES |
| `SPRING_DATASOURCE_PASSWORD` | YES | none | YES |
| `ADMIN_PASSWORD_HASH` | YES | none | YES |
| `ADMIN_TOKEN_SECRET` | YES | none, 32+ chars | YES |
| `ALLOWED_ORIGINS` | YES | local development value | NO |
| `ADMIN_TOKEN_TTL_SECONDS` | NO | `28800` | NO |
| `SERVER_ADDRESS` | production | `127.0.0.1` in example | NO |
| `SERVER_PORT` | NO | `8080` | NO |
| `JAVA_OPTS` | NO | configurable percentage example | NO |

Preflight는 값을 출력하지 않고 `SET` 여부, placeholder 제거, BCrypt prefix, token length, HTTPS CORS, MySQL JDBC, loopback bind를 확인합니다.

## 7. Systemd

- Status: production candidate 작성 완료
- User/group: `likelion-typing`
- Artifact: `/opt/likelion-typing/current/app.jar`
- Lifecycle: restart-on-failure, 5-second delay, bounded start/stop timeout
- Logging: journald
- Hardening: `NoNewPrivileges`, private tmp/devices, protected system/home/kernel controls, empty capabilities, restrictive umask
- `bash -n`: PASS
- `systemd-analyze verify`: BLOCKED on current macOS; Linux target preflight에서 필수 실행

## 8. Nginx

- Status: production candidate 작성 완료
- HTTP 80: HTTPS redirect
- HTTPS 443: TLS 1.2/1.3, HSTS, restricted request body
- Proxy: `/api/` to `127.0.0.1:8080`, forwarded host/IP/protocol headers, bounded timeouts
- Privacy: query string excluded from access log
- TLS issuance: BLOCKED until domain/DNS ownership is confirmed
- `nginx -t`: BLOCKED because Nginx is not installed on current macOS; target preflight에서 필수 실행

## 9. MySQL

- MySQL 8 and `utf8mb4_0900_ai_ci`
- `bind-address=127.0.0.1`; no public 3306 rule
- Dedicated `user@127.0.0.1`; root application login prohibited
- Privileges limited to current Flyway DDL and application DML on `likelion_typing.*`
- Startup authority: Flyway migration, then Hibernate `ddl-auto=validate`
- Application restart does not initialize or delete data
- Backup required before a release with a new migration

## 10. Health / Smoke

- Local read-only health: `GET http://127.0.0.1:8080/api/health`
- Public read-only health: `GET https://<BACKEND_DOMAIN>/api/health`
- `verify-deployment.sh`: requires active systemd service and exact `status=UP`
- `deploy-backend.sh`: polls local health for up to 60 seconds
- `smoke-test.sh`: write test, blocked unless `ALLOW_WRITE_SMOKE_TEST=1`
- Write guard test: PASS

## 11. Deployment Flow

1. Checkout an explicit reviewed commit.
2. Run `./gradlew clean test build`.
3. Generate and record SHA-256 on the build machine.
4. Upload the JAR through the approved transfer path.
5. Run production preflight without printing secrets.
6. Install immutable `/releases/<commit>/app.jar`.
7. Atomically switch `current`; record `previous`.
8. Restart systemd and require localhost health UP.
9. Verify Flyway/JPA/Tomcat logs and public HTTPS health.

Validated local build artifact SHA-256: `e13cfcd7efc237c0d051dcd25d97ef332d43f0ac7169fe4d58c47a9dd5b99ba6`. Production must regenerate it from the final reviewed commit.

## 12. Rollback

- Application: atomically switch `current` to the recorded `previous` artifact, restart, run health verification.
- Database: never edit applied migrations or Flyway history. Assess compatibility first; use a reviewed forward migration or a verified pre-release backup during an approved outage.
- Backup/restore rehearsal remains required before production.

## 13. Test Results

- Command: `./gradlew clean test build`
- Result: PASS
- Tests: 11
- Failures: 0
- Errors: 0
- Skipped: 0
- `bash -n scripts/*.sh`: PASS
- API Contract/Core source regression: none; source files unchanged

## 14. Secret Scan

- Result: PASS
- AWS access key: none
- DB/admin plaintext password: none
- Admin token secret: none
- Private key/certificate key: none
- Examples contain placeholders only

## 15. Remaining P0

The repository has no remaining P0. Actual production launch is blocked by external inputs:

- Confirmed Category 3 and Sentence 15 migration
- AWS region and EC2 target
- Restricted administrator access source
- Backend domain, DNS ownership, and TLS certificate
- Frontend production HTTPS origin
- Production DB/admin secret values stored outside Git

## 16. Remaining P1

- Run `systemd-analyze verify` and `nginx -t` on the selected Linux host.
- Rehearse MySQL backup and restore.
- Select EC2 size/storage after expected concurrency and retention are confirmed.
- Confirm journald and Nginx rotation/retention on the selected OS image.

## 17. Required User Inputs

- AWS region
- Existing versus new EC2 target
- EC2 instance size and encrypted storage size
- SSH/admin access method and `<ADMIN_IP>/32`
- Backend domain/subdomain and DNS owner
- Frontend production origin
- Same-host MySQL confirmation or existing managed DB topology
- TLS issuance method
- Secret storage/transfer method
- Final production content PR

## 18. Recommendation

For the event MVP, use one supported Ubuntu LTS EC2 with encrypted persistent storage, Nginx public on 80/443, Spring Boot and MySQL bound to loopback, systemd/journald, reviewed Flyway migrations, and external verified backups. This is the fewest moving parts consistent with the current architecture. Move MySQL to RDS only when availability, independent scaling, or managed backup requirements justify the added cost and networking.

## 19. AWS Provisioning Readiness

READY

Repository artifacts and runbook are ready for a Linux-host provisioning rehearsal. No AWS resource, DNS record, certificate, production secret, or production traffic was created or changed.

## 20. Exact Next Action

Confirm the AWS region for the production environment.
