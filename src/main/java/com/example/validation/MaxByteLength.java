package com.example.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열의 <b>UTF-8 인코딩 바이트 길이</b> 상한을 검증한다.
 *
 * <h2>왜 {@code @Size(max=...)} 로 대체할 수 없는가</h2>
 *
 * <p>{@code @Size} 는 <b>문자 수</b>를 센다. 그런데 BCrypt 의 한계는 <b>72바이트</b>이고,
 * UTF-8 에서 한글 1자는 3바이트다. 즉 <b>한글 25자 = 75바이트</b>로 이미 한계를 넘는데,
 * {@code @Size(max=64)} 는 이 입력을 <b>통과시킨다.</b>
 *
 * <p>그리고 최신 {@code BCryptPasswordEncoder} 는 72바이트 초과분을 조용히 버리지 않고
 * {@code IllegalArgumentException} 을 <b>던진다</b>(CVE-2025-22228 대응).
 * 결국 검증을 통과한 요청이 인코딩 단계에서 터져 <b>500</b> 이 나간다.
 * 한국어 사용자 대상 서비스에서 한글 비밀번호는 충분히 현실적인 입력이다.
 *
 * <p>따라서 최소 길이는 문자 수로({@code @Size(min=6)}), 최대 길이는 바이트로(이 애노테이션)
 * 검증한다. 두 애노테이션은 대체 관계가 아니라 <b>역할이 다르다.</b>
 *
 * <p>CLAUDE.md 4장 「비밀번호 상한은 문자 수가 아니라 바이트다」 참조.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxByteLengthValidator.class)
public @interface MaxByteLength {

    /** 허용할 최대 바이트 수. 비밀번호는 BCrypt 한계인 72를 쓴다. */
    int value();

    String message() default "입력값이 너무 깁니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
