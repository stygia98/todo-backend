package com.example.controller;

import com.example.domain.Todo;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.PageResponse;
import com.example.dto.TodoCreateRequest;
import com.example.dto.TodoResponse;
import com.example.dto.TodoToggleRequest;
import com.example.dto.TodoUpdateRequest;
import com.example.service.TodoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.config.OpenApiConfig.SECURITY_SCHEME_NAME;

/**
 * Todo API. CLAUDE.md 5장의 여섯 엔드포인트만 제공한다.
 *
 * <p>⚠️ <b>이 컨트롤러는 DTO 변환과 인증 주체 전달만 한다.</b> 정화·소유권 검증·정렬 화이트리스트는
 * 전부 {@link TodoService} 의 책임이다. 컨트롤러가 {@link Todo} 를 직접 다루는 경우는
 * {@code TodoResponse.from} 호출 시점뿐이다.
 *
 * <p>클래스 전체에 {@link SecurityRequirement} 를 붙인다. {@code AuthController} 와 달리
 * 여섯 엔드포인트가 전부 보호 대상이라 메서드마다 반복하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
@SecurityRequirement(name = SECURITY_SCHEME_NAME)
public class TodoController {

    private final TodoService todoService;

    /**
     * 목록. {@code completed} 를 지정하지 않으면 전체, {@code keyword} 를 지정하지 않으면
     * 검색 없이 조회한다. 정렬 값이 화이트리스트 밖이어도 여기서는 걸러지지 않는다 —
     * {@code TodoService} 가 서비스 진입부에서 대체한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TodoResponse>>> list(
            @AuthenticationPrincipal User user,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String keyword) {
        Page<Todo> page = todoService.list(user.getId(), completed, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page, TodoResponse::from)));
    }

    /** 생성. 상태 코드는 201 이 아니라 200 이다 — Phase 3 의 signup 과 같은 판단을 유지한다. */
    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TodoCreateRequest request) {
        Todo todo = todoService.create(user, request);
        return ResponseEntity.ok(ApiResponse.ok(TodoResponse.from(todo)));
    }

    /** 단건 조회. 존재하지 않거나 타인 소유이면 {@code TodoService} 가 404 를 던진다. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TodoResponse>> get(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        Todo todo = todoService.get(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(TodoResponse.from(todo)));
    }

    /**
     * 전체 교체(PUT). {@code completed} 는 여기서 바꾸지 않는다 — {@link TodoUpdateRequest} 에
     * 그 필드가 애초에 없다. 완료 상태는 오직 {@link #toggle} 로만 바뀐다.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TodoResponse>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody TodoUpdateRequest request) {
        Todo todo = todoService.update(id, user.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok(TodoResponse.from(todo)));
    }

    /**
     * 완료 상태 토글. 바디로 받은 목표 상태를 그대로 반영한다 — 서버가 현재 값을 뒤집지 않는다.
     * 같은 값으로 두 번 호출해도 결과가 같다(멱등).
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<TodoResponse>> toggle(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody TodoToggleRequest request) {
        Todo todo = todoService.toggle(id, user.getId(), request.completed());
        return ResponseEntity.ok(ApiResponse.ok(TodoResponse.from(todo)));
    }

    /** Soft Delete. 돌려줄 값이 없으므로 {@link ApiResponse#ok()} 무인자 팩토리를 쓴다. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        todoService.delete(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
