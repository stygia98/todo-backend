package com.example.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link MaxByteLength} 의 검증 로직.
 *
 * <p>인코딩을 {@link StandardCharsets#UTF_8} 로 <b>명시</b>한다.
 * {@code String.getBytes()} 를 인자 없이 호출하면 플랫폼 기본 인코딩을 쓰는데,
 * 개발 환경(Windows)과 운영 환경(Linux)의 기본값이 달라 <b>같은 입력이 다르게 판정된다.</b>
 * DB 와 HTTP 본문이 모두 UTF-8 이므로 기준을 UTF-8 로 고정한다.
 */
public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

    private int max;

    @Override
    public void initialize(MaxByteLength constraintAnnotation) {
        this.max = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 은 통과시킨다. "값이 있어야 한다"는 @NotBlank 의 책임이고,
        // 여기서 함께 막으면 필드 하나에 두 개의 실패 사유가 겹쳐 메시지가 어느 쪽인지 흐려진다.
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
