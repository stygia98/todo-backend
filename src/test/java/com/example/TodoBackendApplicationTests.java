package com.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 애플리케이션 컨텍스트가 정상적으로 기동되는지 확인한다.
 *
 * <p>빈 설정 오류·순환 참조·설정 파일 문제를 가장 먼저 잡아내는 안전망이며,
 * Phase 3 이후 빈이 늘어날수록 가치가 커진다.
 *
 * <p>⚠️ {@code @ActiveProfiles("test")}가 없으면 공통 설정의 기본값인 local 프로파일로 떠서
 * 개발용 DB(todolist_db)에 접속을 시도한다. 테스트는 반드시 todolist_test 를 써야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TodoBackendApplicationTests {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로드된다")
    void contextLoads() {
    }

}
