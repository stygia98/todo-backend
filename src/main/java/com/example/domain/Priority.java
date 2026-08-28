package com.example.domain;

/**
 * 할 일의 우선순위.
 *
 * <p>DB에는 {@code VARCHAR(10)} 으로 저장된다. 반드시 {@code @Enumerated(EnumType.STRING)} 으로 매핑한다.
 * 기본값인 ORDINAL 로 두면 아래 상수의 <b>선언 순서를 바꾸는 순간 기존 데이터의 의미가 조용히 바뀐다.</b>
 *
 * <p>화면 표시색은 CLAUDE.md 8장에 정의되어 있다 (HIGH 빨강 · MEDIUM 주황 · LOW 초록).
 */
public enum Priority {

    HIGH,

    /** 기본값. 생성 시 지정하지 않으면 이 값이 된다. */
    MEDIUM,

    LOW
}
