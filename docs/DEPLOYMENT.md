# Production Deployment Runbook

## Architecture And Boundary

```text
Internet :443
  -> AWS Security Group
  -> Nginx HTTPS
  -> 127.0.0.1:8080 Spring Boot
  -> 127.0.0.1:3306 MySQL 8
```

Nginx만 public entry point입니다. Spring Boot와 MySQL은 Internet에 직접 노출하지 않습니다. 이 문서는 단일 EC2 MVP를 기준으로 하며 AWS 리소스, DNS, 인증서를 자동 생성하지 않습니다.

Security Group baseline:

| Port | Source | Purpose |
| ---: | --- | --- |
| 22/TCP | `<ADMIN_IP>/32` | 제한된 관리자 SSH |
| 80/TCP | `0.0.0.0/0` | HTTPS redirect와 ACME challenge |
| 443/TCP | `0.0.0.0/0` | public HTTPS |
| 8080/TCP | no public rule | loopback Spring Boot |
| 3306/TCP | no public rule | loopback MySQL |

IPv6를 사용할 때만 동일 목적의 IPv6 규칙을 별도로 검토합니다. SSH에 `0.0.0.0/0`을 사용하지 않습니다.

## Server Baseline

기존 지원 Linux가 있으면 유지합니다. 새 서버라면 정확한 AMI ID를 고정하지 않고 지원 중인 Ubuntu 24.04 LTS 계열을 사용합니다. 최소 용량은 instance type과 행사 부하를 확정한 뒤 결정합니다.

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless nginx mysql-server curl unzip apache2-utils openssl certbot
java -version
nginx -v
mysql --version
```

애플리케이션을 root로 실행하지 않습니다.

```bash
sudo useradd --system --home /opt/likelion-typing --shell /usr/sbin/nologin likelion-typing
sudo install -d -o root -g likelion-typing -m 0750 /opt/likelion-typing/releases
sudo install -d -o root -g likelion-typing -m 0750 /etc/likelion-typing
```

## MySQL 8

`/etc/mysql/mysql.conf.d/mysqld.cnf`에서 외부 수신을 차단하고 재시작합니다.

```ini
[mysqld]
bind-address = 127.0.0.1
character-set-server = utf8mb4
collation-server = utf8mb4_0900_ai_ci
```

```bash
sudo systemctl restart mysql
sudo ss -ltnp | grep ':3306'
```

root가 아닌 application 전용 사용자를 만들고 권한을 application database로 제한합니다. 아래 placeholder는 실행 전에 교체합니다.

```sql
CREATE DATABASE likelion_typing
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER '<APP_DB_USER>'@'127.0.0.1' IDENTIFIED BY '<STRONG_DB_PASSWORD>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON likelion_typing.* TO '<APP_DB_USER>'@'127.0.0.1';
```

현재 Flyway migration과 schema history에 필요한 DDL/DML만 허용합니다. 새 migration이 추가될 때 필요한 권한을 다시 검토하며 `GRANT ALL ON *.*` 또는 `WITH GRANT OPTION`을 사용하지 않습니다.

## Environment And Secrets

`deploy/backend.env.example`을 `/etc/likelion-typing/backend.env`로 설치하고 placeholder를 `sudoedit`로 교체합니다.

| Variable | Required | Secret | Default |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | yes | no | none |
| `SPRING_DATASOURCE_USERNAME` | yes | yes | none |
| `SPRING_DATASOURCE_PASSWORD` | yes | yes | none |
| `ADMIN_PASSWORD_HASH` | yes | yes | none |
| `ADMIN_TOKEN_SECRET` | yes | yes | none; at least 32 characters |
| `ALLOWED_ORIGINS` | yes | no | local development only |
| `ADMIN_TOKEN_TTL_SECONDS` | no | no | `28800` |
| `SERVER_ADDRESS` | production | no | set to `127.0.0.1` |
| `SERVER_PORT` | no | no | `8080` |
| `JAVA_OPTS` | no | no | percentage-based example provided |

Generate values in a private administrator shell. Do not put plaintext passwords in a command argument, repository, chat, or shell history.

```bash
read -rsp 'Admin password: ' ADMIN_PASSWORD_PLAINTEXT; echo
htpasswd -bnBC 12 '' "$ADMIN_PASSWORD_PLAINTEXT" | tr -d ':\n'; echo
unset ADMIN_PASSWORD_PLAINTEXT
openssl rand -base64 48
```

Only the BCrypt output is stored as `ADMIN_PASSWORD_HASH`. Store the random output as `ADMIN_TOKEN_SECRET` and protect the environment file.

```bash
sudo chown root:likelion-typing /etc/likelion-typing/backend.env
sudo chmod 600 /etc/likelion-typing/backend.env
```

`ALLOWED_ORIGINS` must be the exact production Frontend HTTPS origin. Do not use `*`.

## Build And Artifact Integrity

Build a reviewed, explicit commit. Do not deploy with blind `git pull && restart`.

```bash
git fetch origin
git checkout <VERIFIED_COMMIT_SHA>
./gradlew clean test build
BOOT_JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)"
sha256sum "$BOOT_JAR" | tee artifact.sha256
git rev-parse HEAD
```

Record the full Git SHA and SHA-256 in the release ticket. Upload the JAR, checksum text, and the reviewed deployment scripts through the team's approved transfer path. Verify the expected checksum came from the build machine, not the target server.

## Systemd

```bash
sudo install -o root -g root -m 0644 deploy/systemd-example.service /etc/systemd/system/likelion-typing.service
sudo systemd-analyze verify /etc/systemd/system/likelion-typing.service
sudo systemctl daemon-reload
sudo systemctl enable likelion-typing
```

The unit runs as `likelion-typing`, reads the root-owned mode-600 environment file, logs to journald, restarts only on failure, and applies filesystem/kernel/capability protections that do not block Java networking or temporary files. `JAVA_OPTS` uses RAM percentages because the EC2 instance size is not fixed.

## Nginx And HTTPS

Replace only the example domain and matching certificate paths in `deploy/nginx-example.conf`. Keep `/api/` proxying to `127.0.0.1:8080` and preserve forwarded headers.

DNS must point to the EC2 public endpoint before certificate issuance. With port 80 temporarily available, obtain the certificate without creating DNS records automatically:

```bash
sudo systemctl stop nginx
sudo certbot certonly --standalone -d <BACKEND_DOMAIN>
sudo systemctl start nginx
sudo install -o root -g root -m 0644 deploy/nginx-example.conf /etc/nginx/conf.d/likelion-typing.conf
sudo nginx -t
sudo systemctl reload nginx
sudo certbot renew --dry-run
```

Certificate issuance is blocked until the domain and DNS ownership are confirmed. Enable HSTS only with the final HTTPS configuration, as in the example. The custom access format logs `$uri` without query strings, preventing Admin search phone values from entering access logs. Nginx access/error logs use the OS `logrotate` policy; verify `/etc/logrotate.d/nginx`. Journald retention uses the host's `/etc/systemd/journald.conf` limits. Do not add request logging that records phone, Authorization, passwords, or datasource values.

## Preflight And Deploy

Before first deployment, provide the uploaded candidate JAR and its build-machine checksum. Preflight prints only whether required secret names are set.

```bash
sudo ARTIFACT_PATH=/tmp/app.jar EXPECTED_SHA256=<BUILD_SHA256> \
  scripts/production-preflight.sh
sudo EXPECTED_SHA256=<BUILD_SHA256> \
  scripts/deploy-backend.sh /tmp/app.jar <VERIFIED_COMMIT_SHA>
```

Releases are immutable directories at `/opt/likelion-typing/releases/<commit>/`. `current` changes atomically to the new release and `previous` points to the prior release. Re-deploying the same commit is allowed only when its checksum is identical.

Watch startup before exposing traffic:

```bash
sudo journalctl -u likelion-typing -n 200 --no-pager
sudo journalctl -u likelion-typing -f
```

Confirm Flyway success, then Hibernate `ddl-auto=validate`, then Tomcat startup. A started process alone is not deployment success.

## Health And Smoke

Read-only verification is the default:

```bash
sudo PUBLIC_BASE_URL=https://<BACKEND_DOMAIN>/api scripts/verify-deployment.sh
curl -fsS http://127.0.0.1:8080/api/health
curl -fsS https://<BACKEND_DOMAIN>/api/health
```

`scripts/smoke-test.sh` creates a Participant and GameSession. Run it only in an isolated test DB, or with an explicitly approved production test identity:

```bash
ALLOW_WRITE_SMOKE_TEST=1 PHONE=<APPROVED_TEST_PHONE> CATEGORY_ID=<ID> \
  BASE_URL=https://<BACKEND_DOMAIN>/api scripts/smoke-test.sh
```

## Backup And Persistence

Participant, PlayPass, GameSession, and ranking source data live in MySQL and survive application/EC2 restart. Never use `ddl-auto=create`; `ddl-auto=validate` and Flyway remain schema authority.

Before any release containing a new migration, take and verify a database backup:

```bash
umask 077
mysqldump --single-transaction --routines --triggers \
  -u <BACKUP_USER> -p likelion_typing > /var/backups/likelion-typing/pre-<RELEASE>.sql
test -s /var/backups/likelion-typing/pre-<RELEASE>.sql
```

Store backups outside the application release directory and rehearse restore before production. Backup credentials must not be in scripts or shell arguments.

## Rollback

Application rollback and database recovery are separate decisions. If no incompatible migration ran, switch atomically to the recorded previous artifact:

```bash
cd /opt/likelion-typing
test -L previous
sudo ln -sfn "$(readlink previous)" current.next
sudo mv -Tf current.next current
sudo systemctl restart likelion-typing
sudo PUBLIC_BASE_URL=https://<BACKEND_DOMAIN>/api /path/to/verify-deployment.sh
```

If a migration ran, stop and assess compatibility before changing the JAR. Never edit an applied migration, delete Flyway history, or improvise reverse SQL. Restore the verified pre-release backup only during an approved outage, or ship a reviewed forward migration.

## Operations

```bash
sudo systemctl status likelion-typing --no-pager
sudo systemctl start likelion-typing
sudo systemctl stop likelion-typing
sudo systemctl restart likelion-typing
sudo journalctl -u likelion-typing --since '30 minutes ago' --no-pager
sudo tail -n 200 /var/log/nginx/likelion-typing.error.log
sudo df -h
sudo ss -ltnp
```

## Common Failures

| Symptom | Check | Likely cause | Safe action |
| --- | --- | --- | --- |
| Application will not start | `journalctl -u likelion-typing -n 200` | env, Java, permissions | correct config; never print secret values |
| DB connection failure | `mysqladmin ping -h127.0.0.1`; MySQL log | service, bind, credentials | restore service/network; verify the dedicated user |
| Flyway failure | startup log and `flyway_schema_history` read-only query | checksum, privilege, invalid SQL | stop traffic; fix with a reviewed forward migration |
| Nginx 502 | localhost health, Nginx error log | app down or wrong upstream | restore Backend health before reloading Nginx |
| TLS issue | `certbot certificates`; `openssl s_client` | DNS, expiry, chain | fix DNS/renewal; do not bypass HTTPS |
| Port 8080 in use | `ss -ltnp '( sport = :8080 )'` | duplicate process | identify owner; stop only the confirmed stale service |
| Disk full | `df -h`; journal/Nginx sizes | log or backup growth | rotate/archive known files; do not delete DB files |
| Health failure | local then public health | app first, proxy second | isolate the failing layer; rollback if release-caused |
| Admin auth startup error | journal config error without values | invalid BCrypt or short token secret | replace environment value and restart |

Never paste environment files, Bearer tokens, phone numbers, or database passwords into incident logs or tickets.
