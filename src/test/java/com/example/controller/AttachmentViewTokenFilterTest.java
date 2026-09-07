package com.example.controller;

import com.example.config.AttachmentTokenProvider;
import com.example.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 뷰 토큰이 {@code Authorization: Bearer}로 들어왔을 때 {@link com.example.config.JwtAuthenticationFilter}가
 * 인증을 거부하는지 확인하는 통합 테스트다(양방향 권한 상승 방지 중 첫 번째, Phase 12).
 *
 * <p>{@code AttachmentController}(/raw)는 이후 태스크의 산출물이라 아직 존재하지 않는다.
 * 대신 <b>기존에 이미 보호돼 있는 아무 엔드포인트</b>(여기서는 {@code /api/v1/auth/me})에
 * 뷰 토큰을 실어 보내는 것만으로 필터의 {@code purpose} 검사를 검증할 수 있다 — 이 필터는
 * 모든 보호된 경로에 공통으로 적용되기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("뷰 토큰 Authorization 헤더 오용 방지 (권한 상승 방지 1/2)")
class AttachmentViewTokenFilterTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String ME_URL = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttachmentTokenProvider attachmentTokenProvider;

    @Test
    @DisplayName("뷰 토큰을 Authorization: Bearer 로 보내면 401이다 (purpose 불일치)")
    void viewTokenAsBearerIsRejected() throws Exception {
        Long userId = signupAndGetUserId("view-token@example.com", "password123", "테스터");

        String viewToken = attachmentTokenProvider.createViewToken(1L, userId);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + viewToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private Long signupAndGetUserId(String email, String password, String nickname) throws Exception {
        String body = mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, password, nickname))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // sub 클레임과 동일한 id 를 얻기 위해 발급된 액세스 토큰을 그대로 디코드한다.
        String token = objectMapper.readTree(body).get("data").get("token").asString();
        String payload = token.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload));
        return Long.valueOf(objectMapper.readTree(json).get("sub").asString());
    }
}
