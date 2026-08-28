@../CLAUDE.md

# todo-backend

> 전체 스펙의 정본은 위에서 임포트한 부모 `CLAUDE.md`다.
> 이 문서에는 **이 저장소에서만 필요한 규칙**만 적는다. 충돌하면 부모 문서를 따른다.
> 단독 클론 시에는 임포트가 해석되지 않으므로, 문서 저장소(`todo-project`)를 함께 확인한다.

## 실행

개발 환경은 **Windows**다. 셸에 따라 명령이 다르다.

| 목적 | PowerShell / cmd | Git Bash |
|---|---|---|
| 기동 | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |
| 테스트 | `.\mvnw.cmd test` | `./mvnw test` |
| 클린 빌드 | `.\mvnw.cmd clean package` | `./mvnw clean package` |

- 기동 주소: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- **`./mvnw`는 POSIX 셸 스크립트라 PowerShell에서 실행되지 않는다.** `mvnw.cmd`를 쓰거나 Git Bash를 쓴다.
- **`.gitattributes`의 `/mvnw text eol=lf`를 삭제하거나 덮어쓰지 않는다.** CRLF로 체크아웃되면 Git Bash에서 `bad interpreter` 오류가 난다.

### JAVA_HOME 은 21 을 가리켜야 한다

`mvnw`는 **`JAVA_HOME`을 우선 조회**한다. PATH의 `java`와 다른 JDK를 가리켜도 빌드는 `JAVA_HOME` 쪽으로 동작하므로, **빌드는 성공하는데 IDE나 다른 CLI만 실패하는** 혼란이 생긴다.

```bash
./mvnw -version    # 출력의 "Java version"이 21인지 확인한다
```

IDE(STS/Eclipse)는 PATH와 무관하게 자체 설정을 따른다. `Installed JREs`와 프로젝트 Build Path도 **21**인지 확인한다. `pom.xml`의 `<java.version>21</java.version>`과 어긋나면 IDE에서만 컴파일 오류가 뜬다.

### 로컬 실행 — 환경변수 공급 (필수)

⚠️ **Spring Boot는 `.env` 파일을 자동으로 읽지 않는다.** `.env`를 만들어 두는 것만으로는 기동에 실패한다. `application-local.yml`이 `${DB_PASSWORD}` 등을 참조하므로 **실제 환경변수로 주입**해야 한다.

**방법 1 — 셸에서 export (Git Bash, 기본 방식)**

```bash
export DB_USERNAME=postgres
export DB_PASSWORD='실제_비밀번호'
export JWT_SECRET='32자-이상의-임의-문자열-을-여기에-넣는다'
export FRONTEND_URL=http://localhost:3000
export CORS_ALLOWED_ORIGINS=http://localhost:3000
./mvnw spring-boot:run
```

`.env`를 만들어 두었다면 한 줄로 불러올 수 있다.

```bash
set -a; source .env; set +a; ./mvnw spring-boot:run
```

**방법 2 — IDE 실행 구성**

STS/Eclipse의 `Run Configurations → Environment` 탭에 같은 키를 등록한다. IDE로 디버깅할 때는 이 방식이 편하다.

> **`application-local.yml`에 비밀번호 기본값을 넣지 않는다.** 편해 보이지만 평문이 다시 커밋된다.

### 데이터베이스

```bash
# 최초 1회 (PostgreSQL bin이 PATH에 없으면 절대 경로를 쓴다)
"C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres todolist_db
"C:\Program Files\PostgreSQL\17\bin\createdb" -U postgres todolist_test
```

- **`-U postgres`를 빼면 Windows 사용자명으로 접속을 시도해 실패한다.**
- DB는 **`todolist_db` 데이터베이스 자체**를 쓴다. `postgres` DB에 `?currentSchema=todolist_db`로 붙지 않는다.
- 테스트는 `todolist_test`를 쓴다. **H2를 쓰지 않는다** — 검색에 `LOWER(...) LIKE` 등 PostgreSQL 문법을 쓰므로 동작이 갈린다.

## 버전 고정 (임의로 올리거나 내리지 않는다)

| 항목 | 버전 | 이유 |
|---|---|---|
| Spring Boot | **4.1.1** | `<parent>` |
| JDK | **21** | `<java.version>` |
| SpringDoc | **3.1.0** | Boot 마이너와 **1:1 대응**. 범위로 두면 관리 버전이 어긋난다. 2.8.x는 Boot 3 전용이라 기동에 실패한다 |
| Jsoup | **1.23.2** | HTML 정화 |
| jjwt | **0.12.6** × 3 | `jjwt-api` + `jjwt-impl`(runtime) + `jjwt-jackson`(runtime) |

- **jjwt가 3개인 것은 정상이다.** `impl`·`jackson`의 `<scope>runtime</scope>`은 의도된 설정이며, "정리"하면 기동 시 `ClassNotFoundException`이 난다.
- jjwt는 **0.12 문법**이다. `Jwts.parser().verifyWith(key).build()` · `Jwts.builder().signWith(key)`. 인터넷 예제 다수인 0.11 문법(`setSigningKey`, `SignatureAlgorithm.HS256`)을 옮기면 컴파일에 실패한다.
- `pom.xml`은 **Boot 4의 기능별 분리 스타터명**을 쓴다. `spring-boot-starter-web`(X) → **`-webmvc`**(O), `spring-boot-starter-oauth2-client`(X) → **`-security-oauth2-client`**(O), `spring-boot-starter-test` 단일(X) → **`-webmvc-test` / `-security-test` / `-validation-test` / `-data-jpa-test` / `-security-oauth2-client-test`**(O).
- `maven-compiler-plugin`의 `default-compile`·`default-testCompile` 두 execution의 `annotationProcessorPaths`(Lombok)를 **삭제하거나 단순화하지 않는다.**

## 계층 규칙

- **컨트롤러는 엔티티를 반환하지 않는다.** 항상 DTO로 변환하며 DTO는 **`record`**를 쓴다.
- **엔티티에 `@Setter`를 붙이지 않는다.** 변경은 의미 있는 메서드로 한다 (`updateCompleted(boolean)`, `softDelete()`).
- **`@ManyToOne`은 반드시 `fetch = FetchType.LAZY`를 명시한다.** 기본값이 EAGER라 목록 조회에서 불필요한 쿼리가 늘어난다.
- **물리 삭제 금지.** `deleted_at`에 시각을 기록하고, **모든 조회에 `deleted_at IS NULL`을 포함**한다.
- 생성자 주입 + `@RequiredArgsConstructor`. Service에 `@Transactional`, 조회는 `readOnly = true`.
- 소유권 불일치는 **403이 아니라 404**로 응답한다(존재 여부 노출 방지).

## 자주 놓치는 것

- **`application.properties`와 `application.yml`을 동시에 두지 않는다.** `.properties`가 우선 적용되어 `.yml`이 조용히 무시된다.
  > ⚠️ 소스에서 지워도 **`target/classes/`에 이전 빌드의 복사본이 남는다.** Maven은 리소스를 복사할 뿐 낡은 파일을 지우지 않으므로, 설정을 바꿨는데 동작이 그대로면 **`./mvnw clean`**을 먼저 실행한다.
- **`SecurityFilterChain`에서 CSRF 비활성화와 STATELESS 세션을 빠뜨리지 않는다.** 빠뜨리면 `POST /api/v1/auth/signup`부터 403으로 막힌다. 쿠키가 아니라 `Authorization: Bearer` 헤더로 인증하므로 CSRF 토큰을 발급하는 경로 자체가 없다.
- **`authorizeRequests()`는 Spring Security 7에서 제거되었다.** 반드시 **`authorizeHttpRequests()`**를 쓴다. `WebSecurityConfigurerAdapter`도 쓰지 않는다.
- **`permitAll` 목록에서 Swagger 경로를 빼먹지 않는다.** 빼면 Phase 1의 DoD("Swagger UI 접속")가 조용히 회귀한다.
- **Jackson 날짜 설정을 넣지 않는다.** Boot 4는 Jackson 3(`tools.jackson`)을 쓰고 기본값이 이미 ISO-8601이라 무설정으로 충족된다.
  > ⚠️ 이 저장소에는 `com.fasterxml.jackson` 2.x가 springdoc·jjwt-jackson을 통해 **compile scope로 함께 들어와 있다.** 그래서 Boot 3 예제의 `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`는 **컴파일이 통과한다.** 대신 Boot 4의 Jackson 3 `ObjectMapper`에 아무 영향을 주지 못해 **조용히 무시된다.** 설정했는데 왜 안 되는지 찾게 되므로 애초에 참조하지 않는다.
- **`JWT_SECRET`은 raw UTF-8 32자 이상을 그대로 쓴다.** Base64로 디코드하지 않는다(32자 문자열이 24바이트가 되어 `WeakKeyException`). 알고리즘은 **HS256 고정**이며 HS512로 바꾸면 64바이트가 필요해진다.
- **`FRONTEND_URL`(단일 URL)과 `CORS_ALLOWED_ORIGINS`(쉼표 목록)를 하나로 합치지 않는다.** 합치면 OAuth2 리다이렉트 주소가 `https://a.com,https://b.com/oauth/callback?token=...`처럼 깨진다. 로컬은 값이 같아 정상 동작하고 **운영에서만 발현한다.**
- **타임존은 UTC로 고정한다.** `application.yml`의 `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`를 지우지 않는다. 로컬은 KST(+09:00), RDS는 UTC라 지우면 시각이 9시간 어긋난 채 배포 후에야 드러난다.
- **`@DataJpaTest`에는 `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")`를 함께 붙인다.** 기본값이 임베디드 DB로 교체를 시도한다.
- **`@EnableJpaAuditing`은 메인 애플리케이션 클래스에 붙인다.** `@Configuration` 클래스에 두면 `@DataJpaTest`가 로드하지 않아 `created_at`이 null이 된다.

## 주석·문서

모든 주석과 문서는 **한글**로 작성한다. 변수명·메서드명·클래스명은 영어(코드 표준 준수).
