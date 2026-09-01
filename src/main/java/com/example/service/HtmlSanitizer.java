package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tiptap 이 생성한 본문 HTML을 저장 전에 정화한다(CLAUDE.md 6장 XSS 방어의 저장 측).
 *
 * <p>허용 태그는 Tiptap 툴바·Tiptap 확장·이 정화기·프론트 DOMPurify 네 곳이 반드시 같은 집합을
 * 가리켜야 한다. 한 곳을 바꾸면 나머지 세 곳도 함께 바꾼다. 이 클래스는 그중 Jsoup 쪽이다.
 *
 * <p>정규식으로 구현하지 않는다. HTML 파싱은 정규식으로 안전하게 할 수 없다.
 */
@Component
public class HtmlSanitizer {

    /**
     * {@code a} 태그에 {@code rel="noopener noreferrer"}·{@code target="_blank"} 를 강제 주입한다.
     *
     * <p>{@code addAttributes} 로 허용하는 것과 {@code addEnforcedAttribute} 로 값을 강제하는 것은
     * 별개다. 전자만 하면 사용자가 넣은 값이 통과만 될 뿐 tabnabbing 방지 값이 채워지지 않는다.
     * 정화 후 재파싱해 속성을 수동 주입하는 방식은 쓰지 않는다 — {@code addEnforcedAttribute} 로
     * 정화 한 번에 끝나며, 재파싱은 정화 보장을 흐리고 {@link Document.OutputSettings} 를
     * 다시 챙겨야 해 {@code prettyPrint(false)} 를 놓치기 쉽다(jsoup 1.23.2 jar 로 확인).
     */
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "em", "h2", "h3", "ul", "ol", "li",
                    "a", "code", "pre", "blockquote")
            .addAttributes("a", "href", "rel", "target")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addEnforcedAttribute("a", "target", "_blank");

    /**
     * 기본 pretty-print 는 블록 요소를 재포맷해 {@code pre} 블록의 공백과 줄바꿈을 망가뜨린다.
     * 반드시 끈다.
     */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    /**
     * 본문을 정화한다.
     *
     * @param html 원본 HTML. {@code null}이거나 공백뿐이면 {@code null}을 반환한다 — 본문은 선택 항목이다.
     * @return 정화된 HTML, 또는 입력이 비어 있으면 {@code null}
     */
    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS);
    }
}
