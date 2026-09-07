package com.example.controller;

import com.example.config.AttachmentTokenProvider;
import com.example.domain.Attachment;
import com.example.domain.AttachmentRepository;
import com.example.domain.AttachmentStatus;
import com.example.domain.Todo;
import com.example.domain.TodoRepository;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.SignupRequest;
import com.example.dto.TodoCreateRequest;
import com.example.dto.TodoUpdateRequest;
import com.example.storage.LocalStorageService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 첨부 이미지 API 통합 테스트. appendFileImage.md 9절 "C. 업로드·링크 흐름"·"D. 검증" 묶음을
 * 구현한다. XSS 정화(A)와 권한 상승 방지(B)는 각각 {@code HtmlSanitizerTest}와
 * {@code AttachmentTokenProviderTest}/{@code AttachmentViewTokenFilterTest}가 담당한다.
 *
 * <p>애노테이션 조합·헬퍼 패턴은 {@code TodoControllerTest}를 그대로 따른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("첨부 이미지 API 통합 테스트")
class AttachmentControllerTest {

    private static final String ATTACHMENTS_URL = "/api/v1/attachments";
    private static final String TODOS_URL = "/api/v1/todos";
    private static final String PASSWORD = "password123";

    /** PNG 시그니처(8바이트) + 임의 데이터. 매직바이트 검증에는 앞 12바이트만 쓰인다. */
    private static final byte[] VALID_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8
    };

    private static final byte[] NOT_ACTUALLY_A_PNG = "이것은 이미지가 아니라 텍스트 파일입니다.".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * ⚠️ {@code application-test.yml}의 {@code jwt.secret} 리터럴 값을 그대로 하드코딩하지
     * 않는다. 그 파일의 값은 "테스트 재현성을 위한 고정값"이 의도이지만, {@code jwt.secret}이
     * {@code application.yml}에서 {@code ${JWT_SECRET}}로도 선언돼 있어 <b>OS 환경변수
     * {@code JWT_SECRET}이 실제로 설정된 상태로 테스트를 실행하면(예: 로컬 개발 시
     * {@code .env}를 source한 셸) Spring Boot의 프로퍼티 소스 우선순위상 OS 환경변수가
     * 프로파일별 yml보다 먼저 적용되어 그 리터럴 값이 조용히 무시된다.</b> 실제로 이 세션에서
     * {@code JWT_SECRET}을 source한 채 하드코딩된 리터럴로 토큰을 만들었더니 서명 불일치로
     * 전부 401이 나(의도한 시나리오가 아니라 우연히 같은 결과가 나온 것) 뒤늦게 발견했다.
     * 이 필드로 <b>실제 이 컨텍스트가 쓰는 값</b>을 읽어야 어떤 환경에서 실행해도 안전하다.
     */
    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String jwtSecret;

    @Nested
    @DisplayName("업로드·링크 흐름")
    class UploadAndLinkFlow {

        @Test
        @DisplayName("presign → PUT → complete → Todo 저장 순서로 LINKED 전환되고 todo_id가 채워진다")
        void fullFlowTransitionsToLinked() throws Exception {
            String token = signup("flow@example.com", "플로우");

            Long attachmentId = presign(token, "photo.png", "image/png", 16L);
            assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getStatus())
                    .isEqualTo(AttachmentStatus.TEMP);

            upload(token, attachmentId, VALID_PNG, "image/png");
            String viewUrl = complete(token, attachmentId);
            // complete 단계까지는 아직 어떤 Todo 에도 연결되지 않는다.
            assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getStatus())
                    .isEqualTo(AttachmentStatus.TEMP);

            Long todoId = createTodo(token,
                    "<p>사진</p><img src=\"blob:x\" data-attachment-id=\"" + attachmentId + "\">");

            Attachment linked = attachmentRepository.findById(attachmentId).orElseThrow();
            assertThat(linked.getStatus()).isEqualTo(AttachmentStatus.LINKED);
            assertThat(linked.getTodo().getId()).isEqualTo(todoId);
            assertThat(linked.getFileSize()).isEqualTo(VALID_PNG.length);

            // viewUrl 이 실제로 원본 바이트를 서빙하는지 왕복 확인한다.
            String rawPath = viewUrl.substring(viewUrl.indexOf("/api/v1/attachments"));
            mockMvc.perform(get(rawPath))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(VALID_PNG));
        }

        @Test
        @DisplayName("본문에서 이미지를 지우고 저장하면 해당 첨부에 deleted_at이 기록된다")
        void removingImageFromBodySoftDeletesAttachment() throws Exception {
            String token = signup("unlink@example.com", "언링크");
            Long attachmentId = presign(token, "photo.png", "image/png", 16L);
            upload(token, attachmentId, VALID_PNG, "image/png");
            complete(token, attachmentId);
            Long todoId = createTodo(token,
                    "<img src=\"blob:x\" data-attachment-id=\"" + attachmentId + "\">");
            assertThat(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId)).isPresent();

            mockMvc.perform(put(TODOS_URL + "/" + todoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(
                                    new TodoUpdateRequest("제목", "<p>이미지 없음</p>", null, null))))
                    .andExpect(status().isOk());

            assertThat(attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId)).isEmpty();
            // 물리 삭제가 아니므로 행 자체는 남아 있다.
            assertThat(attachmentRepository.findById(attachmentId)).isPresent();
        }

        @Test
        @DisplayName("타인 소유 첨부 id를 본문에 넣어 저장하면 400 INVALID_INPUT이다")
        void otherUsersAttachmentIdIsRejected() throws Exception {
            String ownerToken = signup("owner-att@example.com", "소유자");
            Long attachmentId = presign(ownerToken, "photo.png", "image/png", 16L);
            upload(ownerToken, attachmentId, VALID_PNG, "image/png");
            complete(ownerToken, attachmentId);

            String strangerToken = signup("stranger-att@example.com", "타인");

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                            .content(objectMapper.writeValueAsString(new TodoCreateRequest(
                                    "제목", "<img src=\"x\" data-attachment-id=\"" + attachmentId + "\">",
                                    null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("존재하지 않는 첨부 id로 저장해도 같은 400 INVALID_INPUT이다 (응답이 구분되지 않음)")
        void nonExistentAttachmentIdGetsSameErrorAsOtherOwner() throws Exception {
            String token = signup("nonexistent-att@example.com", "없는첨부");

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(new TodoCreateRequest(
                                    "제목", "<img src=\"x\" data-attachment-id=\"999999999\">", null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("이미 다른 Todo에 LINKED된 id로 저장하면 400 INVALID_INPUT이다")
        void attachmentAlreadyLinkedToAnotherTodoIsRejected() throws Exception {
            String token = signup("relink@example.com", "재연결");
            Long attachmentId = presign(token, "photo.png", "image/png", 16L);
            upload(token, attachmentId, VALID_PNG, "image/png");
            complete(token, attachmentId);
            createTodo(token, "<img src=\"x\" data-attachment-id=\"" + attachmentId + "\">");

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(new TodoCreateRequest(
                                    "복사한 본문", "<img src=\"x\" data-attachment-id=\"" + attachmentId + "\">",
                                    null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }

    /**
     * appendFileImage.md 9절 "B. 권한 상승 방지" 4종 중 실제 {@code /raw} HTTP 엔드포인트로
     * 검증해야 하는 세 가지(6·7·8번)를 담는다. 5번(뷰 토큰을 Authorization: Bearer로 오용)은
     * {@code AttachmentViewTokenFilterTest}가, 판별 로직 자체의 단위 검증은
     * {@code AttachmentTokenProviderTest}가 담당한다 — 여기서는 실제 컨트롤러 응답 코드를 본다.
     */
    @Nested
    @DisplayName("raw 엔드포인트 권한 상승 방지 (양방향 + 만료)")
    class RawEndpointAuthorization {

        @Test
        @DisplayName("액세스 토큰을 raw의 ?token= 으로 보내면 401이다")
        void accessTokenAsRawQueryParamIsRejected() throws Exception {
            String token = signup("access-as-view@example.com", "액세스토큰오용");
            Long attachmentId = presign(token, "photo.png", "image/png", 16L);
            upload(token, attachmentId, VALID_PNG, "image/png");
            complete(token, attachmentId);

            // token 자체가 로그인 시 발급된 액세스 토큰(purpose=access)이다.
            mockMvc.perform(get(ATTACHMENTS_URL + "/" + attachmentId + "/raw?token=" + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("다른 첨부의 aid를 가진 뷰 토큰으로 접근하면 401이다")
        void viewTokenWithMismatchedAidIsRejected() throws Exception {
            String token = signup("mismatched-aid@example.com", "AID불일치");
            Long attachmentA = presign(token, "a.png", "image/png", 16L);
            upload(token, attachmentA, VALID_PNG, "image/png");
            String viewUrlForA = complete(token, attachmentA);
            String tokenForA = viewUrlForA.substring(viewUrlForA.indexOf("token=") + "token=".length());

            Long attachmentB = presign(token, "b.png", "image/png", 16L);
            upload(token, attachmentB, VALID_PNG, "image/png");
            complete(token, attachmentB);

            // A 의 뷰 토큰(aid=A)으로 B 의 raw 를 요청한다 — 경로변수와 aid 가 어긋난다.
            mockMvc.perform(get(ATTACHMENTS_URL + "/" + attachmentB + "/raw?token=" + tokenForA))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("aid는 일치하지만 그 첨부가 존재하지 않으면 404다")
        void validTokenForNonExistentAttachmentIs404() throws Exception {
            AttachmentTokenProvider tokenProvider = new AttachmentTokenProvider(jwtSecret, 86_400_000L);
            String forgedButWellFormedToken = tokenProvider.createViewToken(999_999_999L, 1L);

            mockMvc.perform(get(ATTACHMENTS_URL + "/999999999/raw?token=" + forgedButWellFormedToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("만료된 뷰 토큰으로 접근하면 401이다")
        void expiredViewTokenIsRejected() throws Exception {
            String token = signup("expired-view@example.com", "만료토큰");
            Long attachmentId = presign(token, "photo.png", "image/png", 16L);
            upload(token, attachmentId, VALID_PNG, "image/png");
            complete(token, attachmentId);

            // 발급 즉시 만료되도록 음수 만료시간을 준, 같은 시크릿의 별도 프로바이더 인스턴스.
            AttachmentTokenProvider expiredTokenProvider = new AttachmentTokenProvider(jwtSecret, -1_000L);
            String expiredToken = expiredTokenProvider.createViewToken(attachmentId, 1L);

            mockMvc.perform(get(ATTACHMENTS_URL + "/" + attachmentId + "/raw?token=" + expiredToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("업로드 검증")
    class UploadValidation {

        @Test
        @DisplayName("5MB를 초과하는 fileSize로 presign 요청 시 400 FILE_TOO_LARGE다")
        void oversizedFileIsRejectedAtPresign() throws Exception {
            String token = signup("toolarge@example.com", "용량초과");

            mockMvc.perform(presignRequest(token, "big.png", "image/png", 6_000_000L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
        }

        @Test
        @DisplayName("허용되지 않은 contentType으로 presign 요청 시 400 UNSUPPORTED_FILE_TYPE이다")
        void unsupportedContentTypeIsRejectedAtPresign() throws Exception {
            String token = signup("badtype@example.com", "형식오류");

            mockMvc.perform(presignRequest(token, "a.svg", "image/svg+xml", 100L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
        }

        @Test
        @DisplayName("매직바이트가 선언된 형식과 다르면 complete가 400 + 파일 삭제로 처리한다")
        void magicByteMismatchIsRejectedAndFileDeleted() throws Exception {
            String token = signup("magicbyte@example.com", "매직바이트");
            Long attachmentId = presign(token, "fake.png", "image/png", (long) NOT_ACTUALLY_A_PNG.length);
            upload(token, attachmentId, NOT_ACTUALLY_A_PNG, "image/png");

            mockMvc.perform(post(ATTACHMENTS_URL + "/" + attachmentId + "/complete")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));

            String storageKey = attachmentRepository.findById(attachmentId).orElseThrow().getStorageKey();
            assertThatThrownBy(() -> localStorageService.read(storageKey))
                    .as("검증에 실패한 파일은 즉시 삭제되어야 한다")
                    .isInstanceOf(java.io.UncheckedIOException.class);
        }
    }

    @Nested
    @DisplayName("목록 조회 N+1 방지")
    class NPlusOnePrevention {

        @Test
        @DisplayName("Todo 목록에 첨부를 채워도 항목 수와 무관하게 첨부 조회 쿼리 수가 일정하다")
        void attachmentBatchQueryDoesNotScaleWithItemCount() throws Exception {
            String token = signup("attachment-nplus1@example.com", "첨부N플러스원");

            for (int i = 0; i < 3; i++) {
                Long attachmentId = presign(token, "p" + i + ".png", "image/png", 16L);
                upload(token, attachmentId, VALID_PNG, "image/png");
                complete(token, attachmentId);
                createTodo(token, "<img src=\"x\" data-attachment-id=\"" + attachmentId + "\">");
            }

            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Statistics statistics = sessionFactory.getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3));

            long queriesForThree = statistics.getPrepareStatementCount();

            for (int i = 3; i < 6; i++) {
                Long attachmentId = presign(token, "p" + i + ".png", "image/png", 16L);
                upload(token, attachmentId, VALID_PNG, "image/png");
                complete(token, attachmentId);
                createTodo(token, "<img src=\"x\" data-attachment-id=\"" + attachmentId + "\">");
            }
            statistics.clear();

            mockMvc.perform(get(TODOS_URL + "?size=10")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(6));

            long queriesForSix = statistics.getPrepareStatementCount();

            assertThat(queriesForSix)
                    .as("Todo 와 첨부가 3건에서 6건으로 늘어도 쿼리 수는 그대로여야 한다. 3건일 때 %d건", queriesForThree)
                    .isEqualTo(queriesForThree);
        }
    }

    // ── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private String signup(String email, String nickname) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("token").asString();
    }

    private org.springframework.test.web.servlet.RequestBuilder presignRequest(
            String token, String filename, String contentType, long fileSize) {
        String json = """
                {"filename":"%s","contentType":"%s","fileSize":%d}
                """.formatted(filename, contentType, fileSize);
        return post(ATTACHMENTS_URL + "/presign")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(json);
    }

    private Long presign(String token, String filename, String contentType, long fileSize) throws Exception {
        String body = mockMvc.perform(presignRequest(token, filename, contentType, fileSize))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        return data.get("attachmentId").asLong();
    }

    private void upload(String token, Long attachmentId, byte[] content, String contentType) throws Exception {
        mockMvc.perform(put(ATTACHMENTS_URL + "/" + attachmentId + "/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(contentType)
                        .content(content))
                .andExpect(status().isOk());
    }

    /** @return complete 응답의 viewUrl */
    private String complete(String token, Long attachmentId) throws Exception {
        String body = mockMvc.perform(post(ATTACHMENTS_URL + "/" + attachmentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("viewUrl").asString();
    }

    private Long createTodo(String token, String content) throws Exception {
        String body = mockMvc.perform(post(TODOS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new TodoCreateRequest("제목", content, null, null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }
}
