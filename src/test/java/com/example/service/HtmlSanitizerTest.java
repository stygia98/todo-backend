package com.example.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HtmlSanitizer} 단위 테스트.
 *
 * <p>Phase 4의 XSS 정화 검증은 {@code TodoControllerTest}의 7번 묶음(통합 테스트)이 담당했다.
 * 이 클래스는 Phase 12에서 추가된 {@code img} 허용 규칙만 별도로, 더 빠르게(Spring 컨텍스트
 * 없이) 검증한다.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @ParameterizedTest(name = "img src=\"{0}\" 는 제거되고 태그는 남는다")
    @ValueSource(strings = {
            "http://evil.example.com/steal.png",
            "javascript:alert(document.cookie)",
            "data:image/png;base64,aGVsbG8=",
            "blob:http://localhost:3000/uuid"
    })
    @DisplayName("어떤 스킴의 img src 든 전부 제거된다")
    void imgSrcIsAlwaysRemoved(String src) {
        String cleaned = sanitizer.clean("<p>본문</p><img src=\"" + src + "\" data-attachment-id=\"1\">");

        assertThat(cleaned).contains("<img").doesNotContain("src=");
    }

    @Test
    @DisplayName("data-attachment-id 와 alt 는 보존된다")
    void dataAttachmentIdAndAltArePreserved() {
        String cleaned = sanitizer.clean(
                "<img src=\"http://x/y.png\" data-attachment-id=\"42\" alt=\"고양이 사진\">");

        assertThat(cleaned).contains("data-attachment-id=\"42\"").contains("alt=\"고양이 사진\"");
    }

    @Test
    @DisplayName("img 를 추가해도 기존 허용 태그 집합은 그대로 유지된다")
    void existingAllowedTagsStillWork() {
        String cleaned = sanitizer.clean(
                "<p>문단</p><strong>굵게</strong><em>기울임</em><ul><li>목록</li></ul>"
                        + "<a href=\"https://example.com\">링크</a><code>코드</code><pre>포맷</pre>");

        assertThat(cleaned)
                .contains("<p>문단</p>")
                .contains("<strong>굵게</strong>")
                .contains("<em>기울임</em>")
                .contains("<li>목록</li>")
                .contains("<code>코드</code>")
                .contains("<pre>포맷</pre>")
                // 기존 규칙(rel/target 강제 주입)도 img 추가와 무관하게 그대로 동작해야 한다.
                .contains("rel=\"noopener noreferrer\"")
                .contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("허용되지 않은 태그(script)는 여전히 제거된다")
    void disallowedTagsAreStillRemoved() {
        String cleaned = sanitizer.clean("<p>본문</p><script>alert(1)</script>");

        assertThat(cleaned).doesNotContain("<script").contains("<p>본문</p>");
    }

    @Test
    @DisplayName("입력이 비어 있으면 null 을 반환한다")
    void blankInputReturnsNull() {
        assertThat(sanitizer.clean(null)).isNull();
        assertThat(sanitizer.clean("   ")).isNull();
    }
}
