# Deployment

## AWS EC2

1. Java 21과 MySQL 8을 준비하고 애플리케이션 전용 DB 사용자를 만듭니다.
2. `./gradlew clean build` 산출물 `build/libs/typing-0.0.1-SNAPSHOT.jar`를 `/opt/likelion-typing/`에 배치합니다.
3. `/etc/likelion-typing/backend.env`에 README의 환경변수를 설정하고 권한을 `600`으로 제한합니다.
4. `deploy/systemd-example.service`를 설치해 Spring Boot를 `127.0.0.1:8080`에서 실행합니다.
5. `deploy/nginx-example.conf`의 도메인과 인증서 경로를 교체한 뒤 HTTPS를 활성화합니다.
6. `/api/health`, Flyway 로그, smoke test를 확인한 후 트래픽을 연결합니다.

## Secrets

관리자 비밀번호는 BCrypt 해시만 `ADMIN_PASSWORD_HASH`로 주입합니다. `ADMIN_TOKEN_SECRET`은 32자 이상의 암호학적 난수로 생성합니다. 환경 파일, AWS credential, DB 비밀번호, 원문 관리자 비밀번호는 Git에 넣지 않습니다.

## Migration And Rollback

애플리케이션 시작 시 Flyway가 먼저 실행되고 Hibernate가 스키마를 검증합니다. 운영 콘텐츠 migration을 포함한 DB 백업을 배포 전에 만들고, migration 실패 시 애플리케이션을 열지 않은 채 원인을 수정한 새 migration을 배포합니다. 적용된 migration 파일을 수정하지 않습니다.
