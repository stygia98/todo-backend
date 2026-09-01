package com.example.controller;

import com.example.domain.Todo;
import com.example.domain.TodoRepository;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.SignupRequest;
import com.example.dto.TodoCreateRequest;
import com.example.dto.TodoUpdateRequest;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Todo API 통합 테스트. CLAUDE.md 14장이 규정한 통합 테스트 4~7번을 구현하고,
 * ROADMAP Phase 4 DoD 판정에 필요한 추가 케이스를 함께 넣는다.
 *
 * <p>14장의 4~7번은 <b>시나리오 묶음</b>이며 {@code @Test} 메서드 수는 그보다 많다.
 * {@code AuthControllerTest} 와 같은 이유다.
 *
 * <p>애노테이션 조합·경로는 {@code AuthControllerTest} 를 그대로 따른다.
 * {@code @AutoConfigureMockMvc} 는 {@code org.springframework.boot.webmvc.test.autoconfigure} 다.
 *
 * <p>⚠️ 이 클래스의 각 테스트는 <b>새 계정으로 가입</b>해 자신만의 Todo 를 만든다.
 * 이 DB 에는 태스크 19 의 시드(계정 {@code seed@example.com} + Todo 10,100건)가 이미 들어 있지만,
 * 모든 조회가 {@code TodoSpecifications.ownedBy(userId)} 로 걸러지므로 서로 섞이지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Todo API 통합 테스트")
class TodoControllerTest {

    private static final String TODOS_URL = "/api/v1/todos";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ── 통합 테스트 4 — 생성 후 목록 조회 ────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 4 — 생성 후 목록 조회")
    class ListAfterCreate {

        @Test
        @DisplayName("생성한 Todo가 목록의 data.content와 페이지네이션 필드에 나타난다")
        void createdTodoAppearsInListWithPaginationFields() throws Exception {
            String token = signup("list4-a@example.com", "리스트생성");
            createTodo(token, "첫 번째 할 일", null, null, null);

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].title").value("첫 번째 할 일"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.first").value(true))
                    .andExpect(jsonPath("$.data.last").value(true));
        }

        @Test
        @DisplayName("completed를 지정하지 않으면 완료·미완료 전체가 조회된다")
        void completedUnspecifiedReturnsAll() throws Exception {
            String token = signup("list4-b@example.com", "전체조회");
            String doneId = createTodo(token, "완료됨", null, null, null);
            createTodo(token, "미완료", null, null, null);
            toggle(token, doneId, true);

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @DisplayName("completed=true/false 를 지정하면 필터가 적용된다")
        void completedFilterIsApplied() throws Exception {
            String token = signup("list4-c@example.com", "필터조회");
            String doneId = createTodo(token, "완료됨", null, null, null);
            createTodo(token, "미완료", null, null, null);
            toggle(token, doneId, true);

            mockMvc.perform(get(TODOS_URL + "?completed=true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("완료됨"));

            mockMvc.perform(get(TODOS_URL + "?completed=false").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("미완료"));
        }

        @Test
        @DisplayName("대소문자를 섞은 키워드로도 검색된다")
        void keywordSearchIsCaseInsensitive() throws Exception {
            String token = signup("list4-d@example.com", "검색조회");
            createTodo(token, "Spring Boot 학습", null, null, null);
            createTodo(token, "무관한 항목", null, null, null);

            mockMvc.perform(get(TODOS_URL + "?keyword=SPRING boot").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("Spring Boot 학습"));
        }

        /** DoD 6. 없는 프로퍼티를 그대로 Pageable 에 넘기면 500이 난다 — 서비스가 걸러야 한다. */
        @Test
        @DisplayName("잘못된 정렬 값(sort=foo,desc)에도 500이 아니라 200으로 응답한다")
        void invalidSortDoesNotCause500() throws Exception {
            String token = signup("list4-e@example.com", "정렬조회");
            createTodo(token, "정렬 테스트", null, null, null);

            mockMvc.perform(get(TODOS_URL + "?sort=foo,desc").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        /** DoD — 날짜가 배열이 아니라 ISO 문자열로 직렬화된다. */
        @Test
        @DisplayName("createdAt은 Z로 끝나는 ISO-8601 문자열, dueDate는 yyyy-MM-dd로 직렬화된다")
        void datesAreSerializedAsIsoStrings() throws Exception {
            String token = signup("list4-f@example.com", "날짜조회");
            createTodo(token, "날짜 확인", null, null, "2026-12-31");

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].createdAt")
                            .value(org.hamcrest.Matchers.matchesPattern(
                                    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")))
                    .andExpect(jsonPath("$.data.content[0].dueDate").value("2026-12-31"));
        }
    }

    // ── 통합 테스트 5 — Soft Delete ─────────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 5 — Soft Delete")
    class SoftDelete {

        @Test
        @DisplayName("삭제 후 목록에서 제외되고 deleted_at이 기록된다 (물리 삭제 아님)")
        void deletedTodoIsExcludedButRowRemains() throws Exception {
            String token = signup("delete5@example.com", "삭제테스트");
            String id = createTodo(token, "삭제 대상", null, null, null);

            mockMvc.perform(delete(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(jsonPath("$.data.totalElements").value(0));

            Todo raw = todoRepository.findById(Long.valueOf(id)).orElseThrow();
            assertThat(raw.getDeletedAt()).as("물리 삭제가 아니므로 행 자체는 남아 있고 deleted_at만 채워진다").isNotNull();
        }

        @Test
        @DisplayName("삭제된 Todo를 단건 조회하면 404 TODO_NOT_FOUND다")
        void deletedTodoReturns404OnGet() throws Exception {
            String token = signup("delete5b@example.com", "삭제후조회");
            String id = createTodo(token, "삭제 후 조회", null, null, null);

            mockMvc.perform(delete(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        }
    }

    // ── 통합 테스트 6 — 타 사용자 접근 404 ────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 6 — 타 사용자의 Todo 접근 시 404 (소유권 검증)")
    class OwnershipViolation {

        @Test
        @DisplayName("타인의 Todo를 조회하면 403이 아니라 404를 반환한다")
        void getOthersTodoReturns404() throws Exception {
            String ownerToken = signup("owner6@example.com", "주인");
            String strangerToken = signup("stranger6@example.com", "타인");
            String id = createTodo(ownerToken, "내 것", null, null, null);

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        }

        @Test
        @DisplayName("타인의 Todo를 수정(PUT)하면 404를 반환한다")
        void updateOthersTodoReturns404() throws Exception {
            String ownerToken = signup("owner6b@example.com", "주인2");
            String strangerToken = signup("stranger6b@example.com", "타인2");
            String id = createTodo(ownerToken, "내 것2", null, null, null);

            mockMvc.perform(put(TODOS_URL + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                            .content(objectMapper.writeValueAsString(
                                    new TodoUpdateRequest("탈취 시도", null, null, null))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        }

        @Test
        @DisplayName("타인의 Todo를 삭제하면 404를 반환하고 실제로 삭제되지 않는다")
        void deleteOthersTodoReturns404AndDoesNotDelete() throws Exception {
            String ownerToken = signup("owner6c@example.com", "주인3");
            String strangerToken = signup("stranger6c@example.com", "타인3");
            String id = createTodo(ownerToken, "내 것3", null, null, null);

            mockMvc.perform(delete(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                    .andExpect(status().isNotFound());

            assertThat(todoRepository.findByIdAndDeletedAtIsNull(Long.valueOf(id)))
                    .as("소유권 불일치로 거부된 요청이 실제로 삭제를 일으키면 안 된다")
                    .isPresent();
        }
    }

    // ── 통합 테스트 7 — XSS 정화 ─────────────────────────────────────────────

    @Nested
    @DisplayName("통합 테스트 7 — script 포함 본문 저장 시 태그가 제거된다")
    class XssSanitization {

        @Test
        @DisplayName("script 태그는 제거되고 허용 태그는 보존된다")
        void scriptTagIsRemoved() throws Exception {
            String token = signup("xss7@example.com", "엑스에스에스");
            String id = createTodo(token, "XSS 확인",
                    "<p>안전</p><script>alert(1)</script>", null, null);

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("<script>"))))
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("<p>안전</p>")));
        }

        /** rel/target 강제 주입. Safelist.addEnforcedAttribute 로 구현했다(HtmlSanitizer 참조). */
        @Test
        @DisplayName("a 태그에 rel=\"noopener noreferrer\"와 target=\"_blank\"가 강제 주입된다")
        void anchorGetsEnforcedRelAndTarget() throws Exception {
            String token = signup("xss7b@example.com", "링크확인");
            String id = createTodo(token, "링크 포함",
                    "<p><a href=\"https://example.com\">링크</a></p>", null, null);

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("rel=\"noopener noreferrer\"")))
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("target=\"_blank\"")));
        }

        /**
         * prettyPrint(false) 검증. 이걸 놓쳐도 script 제거 테스트는 그대로 통과하므로
         * 반드시 별도로 확인해야 발견된다(태스크 노트 참조).
         */
        @Test
        @DisplayName("pre 블록의 줄바꿈이 정화 후에도 보존된다 (prettyPrint(false) 검증)")
        void preBlockLineBreaksArePreserved() throws Exception {
            String token = signup("xss7c@example.com", "코드블록");
            String id = createTodo(token, "코드 포함", "<pre>line1\nline2</pre>", null, null);

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("line1\nline2")));
        }

        /** javascript: 스킴은 href 로 통과하지 못한다. */
        @Test
        @DisplayName("javascript: 스킴 링크는 href가 제거된다")
        void javascriptSchemeIsStripped() throws Exception {
            String token = signup("xss7d@example.com", "스킴확인");
            String id = createTodo(token, "위험한 링크",
                    "<a href=\"javascript:alert(1)\">클릭</a>", null, null);

            mockMvc.perform(get(TODOS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("javascript:"))));
        }
    }

    // ── DoD — PUT과 toggle의 역할 분리 ────────────────────────────────────────

    @Nested
    @DisplayName("DoD — PUT과 toggle의 역할 분리")
    class PutAndToggleSeparation {

        @Test
        @DisplayName("PUT 저장은 완료 상태를 덮어쓰지 않는다")
        void putDoesNotOverwriteCompleted() throws Exception {
            String token = signup("put-toggle@example.com", "퍼트토글");
            String id = createTodo(token, "원래 제목", null, null, null);
            toggle(token, id, true);

            mockMvc.perform(put(TODOS_URL + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(
                                    new TodoUpdateRequest("바뀐 제목", null, null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                    .andExpect(jsonPath("$.data.completed").value(true));
        }

        @Test
        @DisplayName("toggle을 같은 값으로 두 번 호출해도 결과가 동일하다 (멱등)")
        void toggleIsIdempotent() throws Exception {
            String token = signup("toggle-idem@example.com", "토글멱등");
            String id = createTodo(token, "토글 대상", null, null, null);

            toggle(token, id, true).andExpect(jsonPath("$.data.completed").value(true));
            toggle(token, id, true).andExpect(jsonPath("$.data.completed").value(true));
        }
    }

    // ── DoD — 입력 검증 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("DoD — 입력 검증")
    class Validation {

        @Test
        @DisplayName("제목 미입력 시 400 INVALID_INPUT과 필드 메시지를 반환한다")
        void blankTitleReturns400() throws Exception {
            String token = signup("valid-blank@example.com", "빈제목");

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(
                                    new TodoCreateRequest("", null, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("title")));
        }

        @Test
        @DisplayName("제목 200자 초과 시 400을 반환한다")
        void titleOver200CharsReturns400() throws Exception {
            String token = signup("valid-long-title@example.com", "긴제목");
            String longTitle = "가".repeat(201);

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(
                                    new TodoCreateRequest(longTitle, null, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("본문 50,000자 초과 시 400을 반환한다")
        void contentOver50000CharsReturns400() throws Exception {
            String token = signup("valid-long-content@example.com", "긴본문");
            String longContent = "a".repeat(50_001);

            mockMvc.perform(post(TODOS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .content(objectMapper.writeValueAsString(
                                    new TodoCreateRequest("제목", longContent, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }

    // ── DoD — N+1 방지 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("DoD — 목록 조회 시 N+1이 발생하지 않는다")
    class NPlusOnePrevention {

        /**
         * 원리적 논증(Todo.user 가 LAZY, TodoResponse 에 user 없음)이 아니라 실측으로 확인한다.
         * Hibernate Statistics 로 쿼리 실행 횟수를 세되, <b>절대값이 아니라 증가분</b>을 비교한다.
         * 절대값은 {@code JwtAuthenticationFilter} 의 사용자 조회 1건이 매 요청 섞여 들어와
         * 매직 넘버가 되기 쉽다. 대신 "항목 수가 늘어도 쿼리 수는 늘지 않는다"를 직접 증명한다.
         */
        @Test
        @DisplayName("Todo 항목 수가 3개→6개로 늘어도 목록 조회의 쿼리 실행 횟수는 늘지 않는다")
        void queryCountDoesNotScaleWithItemCount() throws Exception {
            String token = signup("nplus1@example.com", "N플러스원");
            User user = userRepository.findByEmailAndDeletedAtIsNull("nplus1@example.com").orElseThrow();

            for (int i = 0; i < 3; i++) {
                todoRepository.save(Todo.create(user, "항목" + i, null, null, null));
            }
            todoRepository.flush();

            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Statistics statistics = sessionFactory.getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3));

            long queriesForThree = statistics.getPrepareStatementCount();

            for (int i = 0; i < 3; i++) {
                todoRepository.save(Todo.create(user, "추가항목" + i, null, null, null));
            }
            todoRepository.flush();
            statistics.clear();

            mockMvc.perform(get(TODOS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(6));

            long queriesForSix = statistics.getPrepareStatementCount();

            assertThat(queriesForSix)
                    .as("항목이 3개에서 6개로 늘어도 쿼리 수는 그대로여야 한다. "
                            + "N+1이면 항목 수에 비례해 늘어난다. 3개일 때 %d건", queriesForThree)
                    .isEqualTo(queriesForThree);
        }
    }

    // ── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private String signup(String email, String nickname) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("token").asString();
    }

    private String createTodo(String token, String title, String content, String priority, String dueDate)
            throws Exception {
        com.example.domain.Priority priorityEnum = priority == null ? null : com.example.domain.Priority.valueOf(priority);
        java.time.LocalDate due = dueDate == null ? null : java.time.LocalDate.parse(dueDate);

        String body = mockMvc.perform(post(TODOS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new TodoCreateRequest(title, content, priorityEnum, due))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private org.springframework.test.web.servlet.ResultActions toggle(String token, String id, boolean completed)
            throws Exception {
        return mockMvc.perform(patch(TODOS_URL + "/" + id + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("{\"completed\":" + completed + "}"));
    }
}
