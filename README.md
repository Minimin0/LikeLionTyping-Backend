# LikeLionTyping Backend

Java 21 + Spring Boot + MySQL 기반 멋쟁이 타자처럼 Backend입니다.

## Links

- Project Hub: https://github.com/Minimin0/LikeLionTyping
- Frontend: https://github.com/Minimin0/LikeLionTyping-Frontend

## Technology

- Java 21
- Spring Boot 3.x
- Gradle
- MySQL
- Spring Data JPA
- Spring Security
- Flyway

## Run

```bash
./gradlew bootRun
```

## Build

```bash
./gradlew test
./gradlew build
```

## Environment

`.env.example`을 참고해 로컬 환경변수를 설정합니다.

```text
DB_URL=jdbc:mysql://localhost:3306/likelion_typing
DB_USERNAME=likelion
DB_PASSWORD=change-me
```

Secret은 Git에 commit하지 않습니다.

## Structure

```text
src/main/java/com/likelion/typing/
├── participant/
├── category/
├── game/
├── pass/
├── ranking/
├── admin/
├── security/
└── common/
    ├── config/
    ├── exception/
    └── response/
```

## Responsibilities

- 참가자 관리
- 전화번호 기반 최초 무료 참여 판정
- PlayPass 관리
- GameSession 관리
- 게임 기록 저장
- 개인 최고 기록 판정
- 카테고리별 랭킹
- Admin 인증
- 유료 재도전 권한 발급
- 경기 무효 처리
- 오류 발생 시 이용권 복구

## Branch

`main`은 운영 기준 브랜치, `develop`은 통합 개발 브랜치입니다.
기능 개발은 `feat/*`, 버그 수정은 `fix/*`, 설정/문서 작업은 `chore/*`에서 진행합니다.
