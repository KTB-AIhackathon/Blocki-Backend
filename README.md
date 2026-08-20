# Blocki - Backend

> **"자동화의 캘린더 커스텀, 유동적인 워크플로우 생성"**
> AI 해커톤 **Blocki** 서비스의 백엔드 API 레포지토리입니다.

---

## 🛠 Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 4.1.0, Spring Security
- **Build Tool**: Gradle Wrapper (Gradle 9.5.1로 고정, `gradle/wrapper/gradle-wrapper.properties` 참고)
- **Database**: PostgreSQL / Spring Data JPA
- **Auth**: JWT(access token, HS256), Bearer 인증

---

## 📋 Prerequisites

팀원 간 동일한 개발 환경 유지를 위해 아래를 준비하세요.

- **JDK**: Java 17
- **Docker**: 로컬 PostgreSQL 실행용 (Docker Desktop 등)
- **IDE**: IntelliJ IDEA (권장)
- **Git**

### Gradle 버전 맞추기

팀원마다 Gradle 버전을 따로 설치해서 맞출 필요가 없습니다. 이 저장소는 **Gradle Wrapper**를 사용하며,
`gradle/wrapper/gradle-wrapper.properties`에 버전(`9.5.1`)이 고정되어 있고 이 파일은 git에 커밋되어
GitHub에서도 그대로 보입니다. 아래처럼 시스템에 설치된 `gradle` 대신 항상 `./gradlew`(Windows는
`gradlew.bat`)를 사용하면 자동으로 같은 버전이 다운로드·사용됩니다.

```bash
./gradlew build
```

---

## 🚀 Getting Started

### 1. Repository Clone

```bash
git clone https://github.com/KTB-AIhackathon/Blocki-Backend.git
cd Blocki-Backend
```

### 2. 로컬 PostgreSQL 실행

`docker-compose.yml`이 `application.yaml`의 기본 접속 정보와 동일하게 맞춰져 있어서, 아래 명령만 실행하면
별도 환경변수 설정 없이 바로 백엔드를 띄울 수 있습니다.

```bash
docker compose up -d
```

### 3. 환경변수 설정 (선택)

기본값은 `src/main/resources/application.yaml`에 들어 있고, 아래 값을 환경변수로 오버라이드할 수 있습니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/blocki` | `docker compose up -d`로 띄운 DB 기준 |
| `DB_USERNAME` | `blocki` | |
| `DB_PASSWORD` | `blocki` | |
| `JWT_SECRET` | (개발용 기본값, 배포 시 반드시 교체) | access token 서명 키, HS256 기준 최소 32바이트 |
| `AI_BASE_URL` | 없음 | Blocki-AI 워커 주소. `AI_INTERNAL_KEY`와 **둘 다** 있어야 문서 생성이 켜진다 |
| `AI_INTERNAL_KEY` | 없음 | 워커의 `INTERNAL_API_KEY`와 같은 값 |
| `AI_TIMEOUT_SECONDS` | `180` | 저장소를 훑고 LLM을 호출하는 시간. 짧으면 워커가 멀쩡해도 job이 읽기 타임아웃으로 죽는다 |

로컬에서 기본값과 다른 값을 쓰고 싶다면 `.env.example`을 `.env`로 복사해 값을 채우세요
(`.env`는 `.gitignore`에 등록되어 있어 커밋되지 않습니다). JWT 비밀키는 아래처럼 생성할 수 있습니다.

```bash
openssl rand -base64 64
```

배포 환경(AWS EC2/RDS)의 실제 값은 GitHub Actions Secrets 또는 서버 환경변수로만 주입하고, 어떤 경우에도
git에 커밋하지 않습니다.

전체 스택(프론트·AI·이 서버·로컬 스텁)은 워크스페이스 루트에서 띄운다.

```bash
./up.sh
python3 e2e/run_stack.py
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

### 5. API 확인

```bash
curl -X POST http://localhost:8080/api/v1/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{"password":"example-password","name":"김블로","email":"blocki@example.com"}'
```

API 상세 스펙은 `docs/api-specification.md`, 패키지 구조는 `docs/backend-directory-structure.md`를 참고하세요.

## 문서 생성 (Blocki-AI 연동)

`AI_BASE_URL`과 `AI_INTERNAL_KEY`가 모두 있으면 `DocumentGenerationWorker`가 큐를 돌며
워커의 `POST /internal/jobs`를 호출합니다.

- **GitHub** — 연결돼 있으면 복호화한 access token을 `X-GitHub-Pat` 헤더로 넘깁니다.
  요청에 저장소 목록을 싣지 않으므로 워커가 사용자의 저장소를 직접 찾습니다.
- **Notion** — 연결돼 있으면 `X-Notion-Token` 헤더와 함께, 글이 들어갈 페이지를
  `notion.parent_id`로 지정합니다.

### Notion 대시보드

워커는 "Developer TIL Dashboard" 한 페이지와 그 자손에만 씁니다. 그 페이지를 찾거나
만드는 일은 `NotionDashboardResolver`가 job 직전에 워커의
`POST /internal/notion/dashboard`를 호출해 처리하고, 받은 `page_id`를
`integrations.notion_dashboard_page_id`에 남깁니다. OAuth 콜백 시점이 아니라 필요할 때
해결하므로, 콜백 당시 워커가 죽어 있었다고 해서 그 계정이 영영 발행 불가가 되지 않습니다.

이 id는 OAuth의 `workspace_id`가 아닙니다. 워크스페이스는 페이지가 아니고, 워커는
대시보드가 아닌 부모를 거부합니다. 연결을 해제해도 이 값은 지우지 않습니다 —
사용자의 페이지는 그대로 두고, 재연결 시 같은 곳을 다시 찾게 하기 위해서입니다.

Notion은 전 구간에서 선택 사항입니다. 미연결·워커 장애·대시보드 해결 실패 어느 경우에도
문서 생성과 버전 저장은 그대로 성공합니다.
