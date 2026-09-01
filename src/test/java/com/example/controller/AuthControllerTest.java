package com.example.controller;

import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 API 통합 테스트. CLAUDE.md 14장이 규정한 통합 테스트 1~3번을 구현한다.
 *
 * <p>세 개의 {@code @Nested} 클래스가 각각 14장의 1번·2번·3번 시나리오에 대응한다.
 * <b>시나리오는 셋이지만 {@code @Test} 메서드는 그보다 많다.</b> "테스트 3건"은 시나리오 묶음을
 * 세는 말이지 메서드 수가 아니다.
 *
 * <h2>⚠️ Boot 4 는 테스트 애노테이션 패키지가 재편됐다</h2>
 *
 * <p>{@code @AutoConfigureMockMvc} 는 {@code org.springframework.boot.webmvc.test.autoconfigure}
 * 에 있다. Boot 3 의 {@code org.springframework.boot.test.autoconfigure.web.servlet} 이 아니다
 * (spring-boot-webmvc-test-4.1.1.jar 를 직접 열어 확인했다). 인터넷 예제를 그대로 옮기면
 * 컴파일에 실패한다. 반면 {@code @SpringBootTest} 는
 * {@code org.springframework.boot.test.context} 로 기존 경로 그대로다. 둘이 갈린다는 점이 함정이다.
 *
 * <h2>⚠️ {@code @Transactional} 이 필요한 이유</h2>
 *
 * <p>{@code ddl-auto: create-drop} 은 <b>JVM 실행 단위</b>로 동작한다. 즉 클래스 안 테스트끼리는
 * 스키마가 초기화되지 않아 앞 테스트가 만든 계정이 뒤 테스트의 이메일 중복 검사에 걸린다.
 * 메서드마다 롤백해 간섭을 끊는다.
 *
 * <h2>ObjectMapper 는 주입받는다</h2>
 *
 * <p>Boot 4 의 직렬화 엔진은 Jackson 3({@code tools.jackson})이다. 그런데 springdoc 과
 * jjwt-jackson 이 Jackson 2({@code com.fasterxml.jackson})를 compile scope 로 함께 끌고 오므로,
 * 잘못된 쪽을 import 해도 <b>컴파일은 통과한다.</b> 프로덕션 코드
 * ({@code JwtAuthenticationEntryPoint})와 같은 타입을 쓰기 위해 컨텍스트의 빈을 주입받는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("인증 API 통합 테스트")
class AuthControllerTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String ME_URL = "/api/v1/auth/me";

    private static final String EMAIL = "tester@example.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "테스터";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // ── 시나리오 1 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 1 — 회원가입")
    class Signup {

        @Test
        @DisplayName("회원가입에 성공하면 200과 함께 토큰을 발급한다")
        void 회원가입_성공() throws Exception {
            mockMvc.perform(signupRequest(EMAIL, PASSWORD, NICKNAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        /**
         * DoD 1. CSRF 가 켜져 있으면 토큰 없는 POST 가 403 으로 막힌다.
         *
         * <p>위 테스트가 200 을 받는다는 것 자체가 이미 근거이지만, 실패했을 때
         * "403 이었다"는 사실이 이름만으로 드러나도록 별도 메서드로 둔다.
         */
        @Test
        @DisplayName("CSRF 토큰 없이 POST 해도 403이 아니다 (csrf.disable 확인)")
        void CSRF_비활성화() throws Exception {
            mockMvc.perform(signupRequest("csrf@example.com", PASSWORD, "씨에스알에프"))
                    .andExpect(status().isOk());
        }

        /** DoD 2. STATELESS 가 아니면 Security 가 JSESSIONID 를 발급한다. */
        @Test
        @DisplayName("응답에 JSESSIONID 쿠키가 없다 (STATELESS 확인)")
        void 세션_미사용() throws Exception {
            MockHttpServletResponse response =
                    mockMvc.perform(signupRequest("session@example.com", PASSWORD, "세션"))
                            .andExpect(status().isOk())
                            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                            .andReturn()
                            .getResponse();

            assertThat(response.getCookie("JSESSIONID"))
                    .as("STATELESS 가 아니면 세션 쿠키가 발급된다")
                    .isNull();
        }

        /** DoD 3. */
        @Test
        @DisplayName("이미 사용 중인 이메일로 가입하면 409 EMAIL_DUPLICATED 를 반환한다")
        void 이메일_중복() throws Exception {
            mockMvc.perform(signupRequest(EMAIL, PASSWORD, NICKNAME))
                    .andExpect(status().isOk());

            mockMvc.perform(signupRequest(EMAIL, "otherPassword", "다른사람"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
        }

        /**
         * DoD 4. 한글 25자는 UTF-8 로 75바이트라 BCrypt 한계(72바이트)를 넘는다.
         *
         * <p>{@code @Size(max=64)} 처럼 <b>문자 수</b>로 검증하면 이 입력이 통과해
         * {@code BCryptPasswordEncoder} 가 {@code IllegalArgumentException} 을 던지고
         * <b>500</b> 이 나간다. 400 이어야 한다는 것이 이 테스트의 요지다.
         */
        @Test
        @DisplayName("한글 25자(75바이트) 비밀번호는 500이 아니라 400 INVALID_INPUT 이다")
        void 비밀번호_바이트_초과() throws Exception {
            String koreanPassword = "가".repeat(25);

            assertThat(koreanPassword).hasSize(25);
            assertThat(koreanPassword.getBytes(StandardCharsets.UTF_8))
                    .as("한글 25자가 75바이트여야 이 테스트가 의미를 갖는다")
                    .hasSize(75);

            mockMvc.perform(signupRequest("korean@example.com", koreanPassword, "한글비번"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        /** DoD 10. 평문 저장은 물론이고 다른 방식으로 해싱돼도 잡아낸다. */
        @Test
        @DisplayName("비밀번호는 BCrypt 해시로 저장된다")
        void 비밀번호_해시_저장() throws Exception {
            mockMvc.perform(signupRequest(EMAIL, PASSWORD, NICKNAME))
                    .andExpect(status().isOk());

            User saved = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();

            assertThat(saved.getPassword())
                    .as("평문이 그대로 저장되면 안 된다")
                    .isNotEqualTo(PASSWORD);
            assertThat(saved.getPassword())
                    .as("BCrypt 해시는 $2a$ 또는 $2b$ 로 시작한다")
                    .startsWith("$2");
        }
    }

    // ── 시나리오 2 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 2 — 로그인")
    class Login {

        @Test
        @DisplayName("로그인에 성공하면 200과 함께 유효한 JWT를 발급한다")
        void 로그인_성공() throws Exception {
            signup(EMAIL, PASSWORD, NICKNAME);

            String body = mockMvc.perform(loginRequest(EMAIL, PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 형식만 확인한다. 서명이 실제로 유효한지는 아래 /me 호출이 통과하는 것으로 증명된다.
            assertThat(extractToken(body).split("\\."))
                    .as("JWT 는 헤더.페이로드.서명 세 부분이다")
                    .hasSize(3);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401을 반환한다")
        void 비밀번호_오류() throws Exception {
            signup(EMAIL, PASSWORD, NICKNAME);

            mockMvc.perform(loginRequest(EMAIL, "wrongPassword"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        /**
         * DoD 7. 계정 열거(account enumeration) 방어.
         *
         * <p>미가입 이메일과 비밀번호 오류의 응답이 조금이라도 다르면, 공격자가 응답 차이만으로
         * "이 이메일이 가입되어 있는가"를 판별할 수 있다. 상태 코드·코드·메시지를 전부 대조한다.
         */
        @Test
        @DisplayName("미가입 이메일과 비밀번호 오류의 401 응답이 완전히 동일하다")
        void 실패_응답_구분_불가() throws Exception {
            signup(EMAIL, PASSWORD, NICKNAME);

            MockHttpServletResponse 비밀번호오류 = mockMvc.perform(loginRequest(EMAIL, "wrongPassword"))
                    .andExpect(status().isUnauthorized())
                    .andReturn()
                    .getResponse();

            MockHttpServletResponse 미가입이메일 = mockMvc.perform(loginRequest("nobody@example.com", PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andReturn()
                    .getResponse();

            JsonNode 오류본문 = objectMapper.readTree(비밀번호오류.getContentAsString());
            JsonNode 미가입본문 = objectMapper.readTree(미가입이메일.getContentAsString());

            assertThat(미가입이메일.getStatus())
                    .as("상태 코드가 갈리면 그것만으로 계정 존재 여부가 드러난다")
                    .isEqualTo(비밀번호오류.getStatus());

            assertThat(미가입본문.get("error").get("code").asString())
                    .as("error.code 가 갈리면 안 된다")
                    .isEqualTo(오류본문.get("error").get("code").asString());

            assertThat(미가입본문.get("error").get("message").asString())
                    .as("error.message 가 갈리면 안 된다")
                    .isEqualTo(오류본문.get("error").get("message").asString());
        }
    }

    // ── 시나리오 3 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 3 — 보호된 엔드포인트")
    class ProtectedEndpoint {

        /**
         * DoD 8·14. 이 요청은 컨트롤러에 도달하지 못하고 필터 단계에서 거부된다.
         * 따라서 {@code GlobalExceptionHandler} 가 아니라
         * {@code JwtAuthenticationEntryPoint} 가 본문을 쓴다. 본문이 비어 있거나 Security
         * 기본 포맷이면 EntryPoint 가 등록되지 않은 것이다.
         */
        @Test
        @DisplayName("토큰 없이 호출하면 401이고 응답이 ApiResponse 포맷이다")
        void 토큰_없음() throws Exception {
            mockMvc.perform(get(ME_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.message").isNotEmpty());
        }

        @Test
        @DisplayName("위조된 토큰으로 호출하면 401이다")
        void 위조_토큰() throws Exception {
            mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        /** DoD 9. */
        @Test
        @DisplayName("유효한 토큰으로 호출하면 200과 함께 nickname·email 을 반환한다")
        void 유효한_토큰() throws Exception {
            String token = signup(EMAIL, PASSWORD, NICKNAME);

            mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.email").value(EMAIL))
                    .andExpect(jsonPath("$.data.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }

    // ── 공통 헬퍼 ───────────────────────────────────────────────────────

    private RequestBuilder signupRequest(String email, String password, String nickname) {
        return post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(email, password, nickname)));
    }

    private RequestBuilder loginRequest(String email, String password) {
        return post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password)));
    }

    /** 가입시키고 발급된 토큰을 돌려준다. */
    private String signup(String email, String password, String nickname) throws Exception {
        return extractToken(mockMvc.perform(signupRequest(email, password, nickname))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String extractToken(String responseBody) {
        return objectMapper.readTree(responseBody).get("data").get("token").asString();
    }
}
