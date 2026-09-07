package com.example.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AttachmentHtmlReader} 단위 테스트.
 *
 * <p>입력은 항상 {@link HtmlSanitizer#clean(String)}을 거친 정본 HTML을 가정한다
 * (클래스 자체의 불변식 — 클래스 javadoc 참조).
 */
class AttachmentHtmlReaderTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();
    private final AttachmentHtmlReader reader = new AttachmentHtmlReader();

    @Test
    @DisplayName("본문에 있는 모든 첨부 id를 수집한다")
    void extractsAllAttachmentIds() {
        String sanitized = sanitizer.clean(
                "<p>사진 두 장</p>"
                        + "<img src=\"http://x/a.png\" data-attachment-id=\"1\">"
                        + "<img src=\"http://x/b.png\" data-attachment-id=\"2\">");

        assertThat(reader.extractAttachmentIds(sanitized)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("같은 id가 두 번 나와도 중복 없이 하나로 모인다")
    void deduplicatesRepeatedIds() {
        String sanitized = sanitizer.clean(
                "<img src=\"http://x/a.png\" data-attachment-id=\"1\">"
                        + "<img src=\"http://x/a.png\" data-attachment-id=\"1\">");

        assertThat(reader.extractAttachmentIds(sanitized)).containsExactly(1L);
    }

    @Test
    @DisplayName("이미지가 없으면 빈 집합을 반환한다")
    void returnsEmptySetWhenNoImages() {
        String sanitized = sanitizer.clean("<p>이미지 없는 본문</p>");

        assertThat(reader.extractAttachmentIds(sanitized)).isEmpty();
    }

    @Test
    @DisplayName("입력이 비어 있으면 빈 집합을 반환한다")
    void returnsEmptySetForBlankInput() {
        assertThat(reader.extractAttachmentIds(null)).isEmpty();
        assertThat(reader.extractAttachmentIds("")).isEmpty();
        assertThat(reader.extractAttachmentIds("   ")).isEmpty();
    }

    @Test
    @DisplayName("data-attachment-id 가 숫자가 아니면 조용히 걸러낸다")
    void ignoresNonNumericAttachmentId() {
        // 정화된 HTML 이라는 전제를 어기는 손상된 입력에도 예외 없이 방어적으로 동작해야 한다.
        String malformed = "<img data-attachment-id=\"not-a-number\">"
                + "<img data-attachment-id=\"7\">";

        assertThat(reader.extractAttachmentIds(malformed)).containsExactly(7L);
    }
}
