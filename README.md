# todo-backend

개인용 Todo List 서비스의 백엔드 API 서버다. Spring Boot 기반 REST API로 회원가입/로그인(이메일 + 구글 OAuth2)과 Todo CRUD, 검색·필터·페이지네이션을 제공한다.

전체 스펙의 정본은 문서 저장소([`todo-project`](https://github.com/stygia98/todo-project))의 `CLAUDE.md`다. 이 저장소를 단독으로 클론했다면 해당 문서를 함께 참고한다.

## 기술 스택

| 항목 | 버전/선택 |
|---|---|
| 프레임워크 | Spring Boot 4.1.1 |
| JDK | 21 |
| 빌드 | Maven (`mvnw` 래퍼) |
| ORM | Spring Data JPA / Hibernate |
| 보안 | Spring Security + JWT (jjwt 0.12.6) |
| HTML 정화 | Jsoup 1.23.2 |
| API 문서 | SpringDoc OpenAPI 3.1.0 (Swagger UI) |
| DB | PostgreSQL |

## 실행 (Windows)

개발 환경은 Windows다. 셸에 따라 명령이 다르다.

| 목적 | PowerShell / cmd | Git Bash |
|---|---|---|
| 기동 | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |
| 테스트 | `.\mvnw.cmd test` | `./mvnw test` |
| 클린 빌드 | `.\mvnw.cmd clean package` | `./mvnw clean package` |

- 기동 주소: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

### DB 생성 (최초 1회)

```bash
"C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres todolist_db
"C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres todolist_test
```

### 환경변수 공급 (필수)

Spring Boot는 `.env` 파일을 자동으로 읽지 않는다. `.env.example`을 복사해 `.env`를 만든 뒤, 실행 전 셸에서 값을 주입한다.

```bash
cp .env.example .env   # 값을 채운다
set -a; source .env; set +a
./mvnw spring-boot:run
```

필요한 키(`.env.example` 참조): `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`·`JWT_SECRET`(raw UTF-8 32자 이상)·`JWT_EXPIRATION`·`GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`FRONTEND_URL`·`CORS_ALLOWED_ORIGINS`.

테스트 실행에도 `DB_PASSWORD`가 필요하다(`DB_PASSWORD='...' ./mvnw test`). 테스트는 `todolist_test` DB를 쓰며 `ddl-auto: create-drop`이라 매 실행마다 스키마가 새로 생성·제거된다.

## 주요 API

Base path: `/api/v1`. 전체 목록과 요청/응답 스키마는 Swagger UI에서 확인한다.

| 영역 | 엔드포인트 |
|---|---|
| 인증 | `POST /auth/signup`, `POST /auth/login`, `GET /auth/me`, `GET /oauth2/authorization/google` |
| Todo | `GET/POST /todos`, `GET/PUT/DELETE /todos/{id}`, `PATCH /todos/{id}/toggle` |

모든 응답은 `{ success, data, error }` 공통 포맷을 따른다.

## 상세 문서

- 기술 규칙 정본: [`todo-project/CLAUDE.md`](https://github.com/stygia98/todo-project/blob/main/CLAUDE.md)
- 개발 로드맵·완료 판정: [`todo-project/docs/ROADMAP.md`](https://github.com/stygia98/todo-project/blob/main/docs/ROADMAP.md)
