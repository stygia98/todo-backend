package com.example.dto;

/**
 * 헬스체크 응답.
 *
 * @param status 애플리케이션 상태. 이 응답이 나갔다는 것 자체가 기동 성공을 뜻하므로 항상 {@code "UP"}이다.
 * @param db     DB 커넥션 유효성 검사 결과({@code "UP"}/{@code "DOWN"})
 */
public record HealthResponse(String status, String db) {
}
