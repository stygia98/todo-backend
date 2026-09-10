package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 배포 후 정상 기동 여부를 외부에서 확인하기 위한 헬스체크.
 *
 * <p>CLAUDE.md 5장 API 명세에는 없는 운영 보조 엔드포인트다. 인증 없이 접근 가능해야
 * 배포 직후 토큰 없이도 확인할 수 있으므로 {@code SecurityConfig.PUBLIC_PATHS}에 등록한다.
 * Actuator를 도입하지 않고, DB 연결만 {@link DataSource}로 직접 검사한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    /** DB 연결 유효성 검사 타임아웃(초). 헬스체크가 느린 DB 때문에 오래 걸리지 않도록 짧게 둔다. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.ok(new HealthResponse("UP", checkDatabase())));
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS) ? "UP" : "DOWN";
        } catch (SQLException e) {
            return "DOWN";
        }
    }
}
