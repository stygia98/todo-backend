package com.example.domain;

/**
 * 첨부 파일의 연결 상태.
 *
 * <p>DB에는 {@code VARCHAR(20)}으로 저장된다. 반드시 {@code @Enumerated(EnumType.STRING)}으로 매핑한다.
 *
 * <p>에디터에서는 할 일을 저장하기 <b>전에</b> 이미지가 먼저 업로드된다. 업로드 시점에는
 * {@code TEMP}이고 {@code todo_id}는 null이며, 할 일 저장/수정 시 본문에 실제로 남아 있는
 * 첨부만 {@code LINKED}로 전환하며 {@code todo_id}를 채운다.
 */
public enum AttachmentStatus {

    /** 업로드는 됐으나 아직 어떤 Todo에도 연결되지 않음. 24시간 경과 시 고아 정리 배치 대상. */
    TEMP,

    /** Todo 본문에 실제로 남아 저장된 상태. */
    LINKED
}
