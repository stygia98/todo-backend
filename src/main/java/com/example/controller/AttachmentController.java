package com.example.controller;

import com.example.config.AttachmentTokenProvider;
import com.example.domain.Attachment;
import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.AttachmentPresignRequest;
import com.example.dto.AttachmentPresignResponse;
import com.example.dto.AttachmentViewResponse;
import com.example.exception.BusinessException;
import com.example.service.AttachmentService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 첨부 이미지 API. CLAUDE.md 5장의 첨부 6종만 제공한다.
 *
 * <p>⚠️ <b>{@code /raw}만 예외다.</b> 나머지 다섯은 {@link TodoController}와 동일하게
 * {@code ApiResponse} 봉투를 쓰고 {@code @AuthenticationPrincipal User user}로 인증 주체를
 * 받는다. {@code /raw}는 {@code Authorization} 헤더가 아니라 쿼리의 뷰 토큰으로 인가하므로
 * (이 프로젝트에서 컨트롤러가 인가를 직접 수행하는 유일한 경로), 실패 시에도 JSON이 아니라
 * 상태코드만(401/404) 반환한다.
 *
 * <p>⚠️ {@code PUT /{id}/upload}는 "로컬 전용"이지만 presigned URL이 아니라 일반 인증
 * 엔드포인트다. S3 presigned URL은 서명 자체가 인가를 대신하지만, 로컬 업로드는 백엔드가
 * 직접 파일을 받으므로 다른 API와 동일하게 {@code Authorization: Bearer}로 인가한다.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentTokenProvider attachmentTokenProvider;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<AttachmentPresignResponse>> presign(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AttachmentPresignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(attachmentService.presign(user, request)));
    }

    /** 로컬 전용. 요청 본문을 그대로 스트리밍해 저장한다(멀티파트가 아니라 raw 바이트). */
    @PutMapping("/{id}/upload")
    public ResponseEntity<ApiResponse<Void>> upload(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            attachmentService.upload(id, user.getId(), request.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 요청 본문을 읽지 못했습니다.", e);
        }
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<AttachmentViewResponse>> complete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(attachmentService.complete(id, user.getId())));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<ApiResponse<AttachmentViewResponse>> url(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(attachmentService.viewUrl(id, user.getId())));
    }

    /**
     * 로컬 전용, {@code ApiResponse} 봉투 예외. 뷰 토큰의 {@code purpose}·{@code aid}가
     * 이 경로변수와 일치할 때만 통과시킨다(양방향 권한 상승 방지 중 두 번째).
     *
     * <ul>
     *   <li>토큰이 없거나 형식이 잘못됨·서명 불일치·만료됨·purpose가 attachment-view가 아님·
     *       aid가 이 id와 다름 → 401 (본문 없음)
     *   <li>토큰은 유효하나 해당 id의 첨부가 없거나 소프트 삭제됨 → 404 (본문 없음)
     * </ul>
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id, @RequestParam String token) {
        Optional<Claims> claims = attachmentTokenProvider.parseViewClaims(token);
        if (claims.isEmpty() || !attachmentTokenProvider.isValidViewToken(claims.get(), id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Attachment attachment;
        try {
            attachment = attachmentService.findExisting(id);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] content = attachmentService.loadRaw(attachment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                // MIME 스니핑으로 이미지로 위장한 HTML/스크립트가 실행되는 것을 막는다.
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(content);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        attachmentService.delete(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
