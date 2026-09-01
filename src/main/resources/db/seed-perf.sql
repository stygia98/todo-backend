-- ============================================================================
-- 성능 측정 전용 시드 — 같은 계정에 Todo 10,000건
-- ============================================================================
--
-- 용도: ROADMAP Phase 4 DoD의 "키워드 검색 포함 목록 조회, 워밍업 후 3회 측정
-- 중앙값 500ms 이내"를 실측하기 위한 전용 데이터다. seed-dev.sql(100건)로는
-- 인덱스가 없어도 1ms 미만이라 항상 통과해 지표가 무의미하다. CLAUDE.md 4장이
-- 지목한 유일한 성능 위험 — LOWER(title) LIKE '%키워드%' 의 앞쪽 와일드카드가
-- 인덱스를 타지 못하는 문제 — 는 데이터가 이 정도는 있어야 드러난다.
--
-- ⚠️ 기능 확인용이 아니다. seed-dev.sql 과 이 파일을 둘 다 실행해도 무방하지만
--    (같은 계정에 합산된다), 성능 측정 시에는 이 파일만 적재한 상태에서 재는
--    편이 "10,000건 기준"이라는 DoD 문구와 정확히 일치한다.
--
-- ⚠️ local 프로파일에서 수동 실행하는 용도다. 운영에는 적용하지 않는다.
--
-- ⚠️ completed 와 priority 를 명시한다 — DB DEFAULT 절이 없다(seed-dev.sql 상단 설명 참조).
--
-- ⚠️ 이 스크립트는 멱등하지 않다. 다시 실행하면 10,000건이 추가로 쌓인다.
--    측정을 반복하려면 먼저 todos 만 비운다(계정은 남긴다):
--      "...\psql" -U postgres -d todolist_db -c "DELETE FROM todos WHERE user_id = (SELECT id FROM users WHERE email='seed@example.com');"
--
-- 실행 (Git Bash):
--   "C:\Program Files\PostgreSQL\17\bin\psql" -U postgres -d todolist_db -f src/main/resources/db/seed-perf.sql
--
-- seed-dev.sql 을 먼저 실행하지 않았다면 계정부터 만든다(seed-dev.sql 과 동일한 계정/해시).
-- ============================================================================

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

-- ── Todo 10,000건 ────────────────────────────────────────────────────────
-- 제목에 검색 키워드 5종을 균등하게 순환시켜 섞는다. 키워드가 한쪽에
-- 몰리면 LIKE 매칭 건수가 왜곡되어 측정 의미가 사라진다(각 키워드 약 2,000건).
-- 100건짜리를 복사해 붙이지 않고 generate_series 로 10,000건을 한 번에 만든다.
INSERT INTO todos (
    user_id, title, content, completed, priority, due_date,
    created_at, updated_at
)
SELECT
    (SELECT id FROM users WHERE email = 'seed@example.com'),
    CASE (s.i % 5)
        WHEN 0 THEN '보고서 작성 성능테스트 #' || s.i
        WHEN 1 THEN '회의 준비 성능테스트 #' || s.i
        WHEN 2 THEN '디자인 검토 성능테스트 #' || s.i
        WHEN 3 THEN '배포 점검 성능테스트 #' || s.i
        ELSE '테스트 케이스 정리 성능테스트 #' || s.i
    END,
    NULL,                                              -- 성능 측정에 본문은 불필요
    (s.i % 2 = 0),
    CASE (s.i % 3)
        WHEN 0 THEN 'HIGH'
        WHEN 1 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE
        WHEN s.i % 7 = 0 THEN NULL
        ELSE (current_date + ((s.i % 60) - 30))
    END,
    timezone('UTC', now()) - (s.i || ' seconds')::interval,
    timezone('UTC', now()) - (s.i || ' seconds')::interval
FROM generate_series(1, 10000) AS s(i);
