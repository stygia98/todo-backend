-- ============================================================================
-- 개발용 시드 — 테스트 계정 1개 + Todo 100건
-- ============================================================================
--
-- 용도: 페이지네이션·완료 필터·우선순위·검색을 화면에서 눈으로 확인한다.
-- 성능 측정에는 쓰지 않는다 — 100건은 인덱스가 없어도 1ms 미만이라 언제나
-- 통과해 지표가 무의미하다. 성능 DoD 측정은 seed-perf.sql(10,000건)을 쓴다.
--
-- ⚠️ local 프로파일에서 수동 실행하는 용도다. 애플리케이션이 자동 실행하지
--    않으며(spring.sql.init 미설정), 운영에는 적용하지 않는다.
--
-- ⚠️ completed 와 priority 를 모든 INSERT 에 명시적으로 채운다. 두 컬럼에는
--    DB DEFAULT 절이 생성되지 않는다(Phase 2 에서 확인). 기본값(false, MEDIUM)은
--    Todo 생성자에만 있고 JPA 를 거칠 때만 적용되는데, 이 스크립트는 JPA 를
--    우회한 직접 INSERT 라 누락하면 NOT NULL 위반으로 실패한다.
--
-- ⚠️ 이 스크립트는 멱등하지 않다. 다시 실행하면 계정은 email UNIQUE 제약으로
--    막히지만(ON CONFLICT DO NOTHING), todos 는 매번 100건씩 추가된다.
--    깨끗하게 다시 채우려면 먼저 비운다.
--
-- 실행 (Git Bash, PostgreSQL bin 이 PATH 에 없으면 절대 경로를 쓴다):
--   "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d todolist_db -f src/main/resources/db/seed-dev.sql
--
-- 다시 깨끗하게 채우려면 먼저:
--   "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d todolist_db -c "TRUNCATE TABLE todos, users RESTART IDENTITY CASCADE;"
-- ============================================================================

-- ── 계정 1개 ─────────────────────────────────────────────────────────────
-- password 는 BCrypt 해시다. 평문 "seedPassword123" 을 애플리케이션 회원가입
-- API로 실제 가입시켜 얻은 해시를 그대로 옮겼다(2026-09-01, BCryptPasswordEncoder).
-- 로그인: seed@example.com / seedPassword123
INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES (
    'seed@example.com',
    '$2a$10$98DpyA0UrRE3ldWaitgx6uk7vnS.4toLPvWdxCu781TX/KXTSJ7qW',
    '시드 사용자',
    'LOCAL',
    timezone('UTC', now()),
    timezone('UTC', now())
)
ON CONFLICT (email) DO NOTHING;

-- ── Todo 100건 ───────────────────────────────────────────────────────────
-- 우선순위(HIGH/MEDIUM/LOW), 완료 여부, 마감일(과거/미래/없음)을 골고루 섞어
-- 목록 화면에서 필터·정렬 결과가 눈으로 갈리게 한다. created_at 도 행마다
-- 달라야 정렬(createdAt desc)이 의미를 가지므로 순번만큼 과거로 흩뿌린다.
INSERT INTO todos (
    user_id, title, content, completed, priority, due_date,
    created_at, updated_at
)
SELECT
    (SELECT id FROM users WHERE email = 'seed@example.com'),
    CASE (s.i % 5)
        WHEN 0 THEN '보고서 작성 #' || s.i
        WHEN 1 THEN '회의 준비 #' || s.i
        WHEN 2 THEN '디자인 검토 #' || s.i
        WHEN 3 THEN '배포 점검 #' || s.i
        ELSE '테스트 케이스 정리 #' || s.i
    END,
    '<p>시드 데이터 항목 ' || s.i || '번.</p>',
    (s.i % 2 = 0),                                    -- 절반은 완료
    CASE (s.i % 3)
        WHEN 0 THEN 'HIGH'
        WHEN 1 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE
        WHEN s.i % 7 = 0 THEN NULL                     -- 일부는 마감일 없음
        ELSE (current_date + ((s.i % 60) - 30))         -- 과거~미래 섞어서
    END,
    timezone('UTC', now()) - (s.i || ' minutes')::interval,
    timezone('UTC', now()) - (s.i || ' minutes')::interval
FROM generate_series(1, 100) AS s(i);
