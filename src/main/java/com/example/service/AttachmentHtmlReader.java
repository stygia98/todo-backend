package com.example.service;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 정화된 정본(canonical) HTML에서 첨부 이미지 id 집합을 읽어낸다.
 *
 * <p>{@link HtmlSanitizer}에 메서드를 추가하지 않고 별도 컴포넌트로 둔다. 정화기의
 * 책임은 "정화"까지이고, "정화된 결과를 읽어 무엇을 할지"는 다른 관심사이기 때문이다.
 *
 * <h2>⚠️ 반드시 정화 <b>이후</b>의 HTML에서 수집해야 한다</h2>
 *
 * <p>{@code TodoService.create}/{@code update}는 {@code htmlSanitizer.clean()} 이후의
 * 결과에서 이 클래스를 호출해야 한다. 정화 전 원문에서 수집하면, 정화 과정에서 제거될
 * {@code img}의 첨부까지 {@code LINKED}로 승격되어 본문에는 없는데 영원히 정리되지
 * 않는 고아 레코드가 생긴다. 정화 후를 파싱하면 "DB의 본문"과 "LINKED 첨부 집합"이
 * 항상 1:1로 대응한다는 불변식이 성립하고, 이 불변식이 고아 정리 배치의 전제가 된다.
 *
 * <h2>⚠️ 읽기 전용 파싱이며 재직렬화하지 않는다</h2>
 *
 * <p>{@link HtmlSanitizer}의 {@code prettyPrint(false)} 주석이 경고하는 함정과 같은
 * 종류다 — 파싱한 문서를 다시 HTML 문자열로 만들어 저장하면 포맷팅이 흐트러질 수 있다.
 * 이 클래스는 id만 뽑아 쓰고 문서 자체는 버린다.
 */
@Component
public class AttachmentHtmlReader {

    /**
     * 정화된 HTML의 {@code img[data-attachment-id]}에서 id 집합을 뽑는다.
     *
     * @param sanitizedHtml {@link HtmlSanitizer#clean(String)}을 거친 정본 HTML
     * @return 첨부 id 집합. 입력이 비어 있거나 이미지가 없으면 빈 집합
     */
    public Set<Long> extractAttachmentIds(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return Set.of();
        }
        return Jsoup.parseBodyFragment(sanitizedHtml)
                .select("img[data-attachment-id]")
                .stream()
                .map(element -> element.attr("data-attachment-id"))
                .map(this::parseIdOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 속성값이 숫자가 아니면(위조되었거나 손상됐으면) 조용히 걸러낸다. 예외를 밖으로 흘리지 않는다. */
    private Long parseIdOrNull(String rawId) {
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
