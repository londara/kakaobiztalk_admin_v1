-- =============================================================================
-- 문자내역 쿼리 계획 검토 스크립트. / Query-plan review for 문자내역.
--
-- req: NFR-PERF-04, NFR-OPS-AUDIT-02
-- source: IDO.KKB_MSG_L002.xml, IDO.KKB_MSG_S001.xml
--
-- 목적 / purpose:
--   NFR-PERF-04 는 "31일 전체 기간 조회가 타임아웃 없이 완료된다"를 요구한다. 이는 코드로
--   닫을 수 없다 — 실제 데이터 분포와 인덱스에 의존하기 때문이다. 이 스크립트는 DBA 가
--   운영 데이터에 대해 실행하여 판정 근거를 만들기 위한 것이다.
--
--   NFR-PERF-04 requires a 31-day search to complete without timing out. Code cannot close it:
--   the answer depends on real data distribution and indexes. This script exists so a DBA can
--   produce the evidence against production data.
--
-- 실행되지 않았다 / NOT EXECUTED — PostgreSQL 이 이 환경에 없다.
--
-- 실행 방법 / how to run:
--   psql -d BIZTALK_DB -v inst="'IS001'" -f qa/sql/explain-message-history.sql > plan.txt
-- =============================================================================

\timing on

-- -----------------------------------------------------------------------------
-- 0. 사전 확인 — AMB-M01 / AMB-M02
--
-- 이 두 항목은 레거시 소스에서 확인할 수 없었고, 구현의 전제로 남아 있다. 아래 두 쿼리가
-- 그 전제를 검증한다. 실패하면 성능이 아니라 정확성 문제이므로 먼저 확인해야 한다.
--
-- Neither could be confirmed from the legacy source and both remain assumptions. These queries
-- verify them; a failure here is a correctness problem, not a performance one.
-- -----------------------------------------------------------------------------

-- AMB-M01: 사용자를 이용기관에 연결하는 컬럼이 실제로 존재하는가.
--          테넌트 격리 전체가 이 컬럼에 의존한다.
--          Does the column linking a user to an 이용기관 exist? All tenant isolation rests on it.
\echo '=== AMB-M01: user -> institution mapping column ==='
SELECT column_name, data_type, is_nullable
  FROM information_schema.columns
 WHERE table_name IN ('TB_USER', 'TB_MEMBER', 'TB_OPERATOR')
   AND (column_name LIKE '%IS%CD%' OR column_name LIKE '%INST%' OR column_name = 'ID')
 ORDER BY table_name, column_name;

-- AMB-M02: 상세 조회가 참조하는 11개 추가 컬럼이 4개 테이블 모두에 존재하는가.
--          레거시는 SELECT * 를 사용했으므로 컬럼 목록을 소스에서 도출할 수 없었다.
--          존재하지 않으면 상세 쿼리는 실행 시점에 실패한다.
\echo '=== AMB-M02: the 11 detail columns, per table ==='
WITH expected(col) AS (
  VALUES ('PROFILE_KEY'), ('AD_FLAG'), ('TEMPLATE_CODE'), ('IMAGE_PATH'),
         ('IMAGE_URL'), ('WIDE_IMAGE_FLAG'), ('BUTTON_JSON'), ('FAILED_TYPE'),
         ('FAILED_SUBJECT'), ('FAILED_IMAGE'), ('FAILED_MESSAGE')
),
targets(tbl) AS (
  VALUES ('KKB_MSG'), ('KKB_MSG_LOG'), ('KKF_MSG'), ('KKF_MSG_LOG')
)
SELECT t.tbl, e.col,
       CASE WHEN c.column_name IS NULL THEN 'MISSING' ELSE 'present' END AS status
  FROM targets t
 CROSS JOIN expected e
  LEFT JOIN information_schema.columns c
         ON upper(c.table_name) = t.tbl AND upper(c.column_name) = e.col
 ORDER BY t.tbl, e.col;

-- -----------------------------------------------------------------------------
-- 1. 인덱스 현황 / existing indexes
--
-- 조회는 REQDATE 범위 + ID(이용기관) 로 필터하고 REQDATE DESC, MSGKEY DESC 로 정렬한다.
-- (ID, REQDATE) 복합 인덱스가 없으면 8개 테이블 전부가 순차 스캔된다.
-- The search filters on a REQDATE range plus ID and sorts by REQDATE DESC, MSGKEY DESC. Without
-- a composite (ID, REQDATE) index all eight tables are sequentially scanned.
-- -----------------------------------------------------------------------------
\echo '=== existing indexes on the eight source tables ==='
SELECT tablename, indexname, indexdef
  FROM pg_indexes
 WHERE upper(tablename) IN ('KKB_MSG', 'KKB_MSG_LOG', 'KKF_MSG', 'KKF_MSG_LOG',
                            'SMS_MSG', 'SMS_MSG_LOG', 'MMS_MSG', 'MMS_MSG_LOG')
 ORDER BY tablename, indexname;

\echo '=== row counts and table sizes ==='
SELECT relname,
       n_live_tup AS approx_rows,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
  FROM pg_stat_user_tables
 WHERE upper(relname) LIKE '%MSG%'
 ORDER BY n_live_tup DESC;

-- -----------------------------------------------------------------------------
-- 2. 통상 조회 (1일) — NFR-PERF-01
--
-- ANALYZE 를 쓰므로 쿼리가 실제로 실행된다. 읽기 전용이지만 운영 DB 에서는 부하 시간대를
-- 피해야 한다.
-- ANALYZE actually executes the query. It is read-only, but avoid peak hours in production.
-- -----------------------------------------------------------------------------
\echo '=== NFR-PERF-01: one-day window ==='
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT ID, MSGKEY, STATUS, RSLT,
       masking(CALLBACK) AS CALLBACK, masking(PHONE) AS PHONE,
       REQDATE, REQTIME, SENTTIME, REPORTTIME, MSG_TYPE, TABLE_TYPE
  FROM (
        SELECT ID, MSGKEY, STATUS, RSLT, decrypt(CALLBACK) AS CALLBACK,
               decrypt(PHONE) AS PHONE,
               to_char(REQDATE, 'YYYYMMDDHH24MISS') AS REQDATE,
               to_char(REQDATE, 'HH24MISS') AS REQTIME,
               to_char(SENTDATE, 'HH24MISS') AS SENTTIME,
               to_char(REPORTDATE, 'HH24MISS') AS REPORTTIME,
               'AT' AS MSG_TYPE, 'K' AS TABLE_TYPE
          FROM KKB_MSG
         WHERE REQDATE >= now() - interval '1 day'
           AND REQDATE <  now()
           AND ID = :inst
       ) A
 ORDER BY REQDATE DESC, MSGKEY DESC
 LIMIT 50 OFFSET 0;

-- -----------------------------------------------------------------------------
-- 3. COUNT(*) — 페이징의 실제 비용 / the real cost of paging
--
-- LIMIT 이 있는 목록 쿼리는 정렬을 조기 종료할 수 있지만, COUNT(*) 는 조건에 맞는 모든
-- 행을 세어야 한다. 8-way UNION 에서 이것이 지배적 비용일 가능성이 높다 — 확인 대상이다.
-- The list query can stop early thanks to LIMIT, but COUNT(*) must count every matching row.
-- Across an 8-way union that is likely the dominant cost — which is what needs confirming.
-- -----------------------------------------------------------------------------
\echo '=== count(*) over the union — likely the dominant cost ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
  FROM (
        SELECT MSGKEY FROM KKB_MSG
         WHERE REQDATE >= now() - interval '31 days' AND REQDATE < now() AND ID = :inst
        UNION ALL
        SELECT MSGKEY FROM KKB_MSG_LOG
         WHERE REQDATE >= now() - interval '31 days' AND REQDATE < now() AND ID = :inst
       ) A;

-- -----------------------------------------------------------------------------
-- 4. 31일 상한 조회 — NFR-PERF-04
--
-- MessageHistoryCriteria.MAX_WINDOW = 31일이므로 이것이 최악의 정상 요청이다.
-- This is the worst legitimate request, since MAX_WINDOW is 31 days.
-- -----------------------------------------------------------------------------
\echo '=== NFR-PERF-04: 31-day window, the criteria cap ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
  FROM KKB_MSG
 WHERE REQDATE >= now() - interval '31 days'
   AND REQDATE <  now()
   AND ID = :inst;

-- -----------------------------------------------------------------------------
-- 5. decrypt() 비용 분리 / isolating the decrypt() cost
--
-- 레거시는 페이징 없이(D7) 조건에 맞는 모든 행에 decrypt() 를 두 번씩 적용했다. 신규
-- 구현은 LIMIT 을 적용하지만, 서브쿼리 안에서 decrypt() 가 호출되므로 플래너가 이를
-- LIMIT 이후로 밀어낼 수 있는지 확인해야 한다 — 밀어내지 못하면 D7 수정의 효과가 반감된다.
--
-- The legacy applied decrypt() twice to every matching row. The new query applies a LIMIT, but
-- decrypt() sits inside the subquery, so it matters whether the planner can defer it past the
-- LIMIT; if not, the benefit of fixing D7 is halved.
-- -----------------------------------------------------------------------------
\echo '=== decrypt() cost: is it applied before or after LIMIT? ==='
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT decrypt(CALLBACK), decrypt(PHONE)
  FROM KKB_MSG
 WHERE REQDATE >= now() - interval '1 day' AND ID = :inst
 ORDER BY REQDATE DESC
 LIMIT 50;

\echo '=== same query without decrypt, for comparison ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT CALLBACK, PHONE
  FROM KKB_MSG
 WHERE REQDATE >= now() - interval '1 day' AND ID = :inst
 ORDER BY REQDATE DESC
 LIMIT 50;

-- -----------------------------------------------------------------------------
-- 6. 권고 인덱스 — 적용하지 말 것 / recommended indexes — DO NOT APPLY
--
-- 아래는 주석 상태로 남긴다. 인덱스 추가는 운영 테이블에 락과 디스크를 요구하므로 DBA 의
-- 판단과 결재를 거쳐야 한다(harness §7 파괴적 작업).
-- Left commented: adding an index takes locks and disk on production tables and requires the
-- DBA's judgement and sign-off.
--
-- CREATE INDEX CONCURRENTLY ix_kkb_msg_id_reqdate ON KKB_MSG (ID, REQDATE DESC);
-- CREATE INDEX CONCURRENTLY ix_kkb_msg_log_id_reqdate ON KKB_MSG_LOG (ID, REQDATE DESC);
-- CREATE INDEX CONCURRENTLY ix_kkf_msg_id_reqdate ON KKF_MSG (ID, REQDATE DESC);
-- CREATE INDEX CONCURRENTLY ix_kkf_msg_log_id_reqdate ON KKF_MSG_LOG (ID, REQDATE DESC);
--
-- 상세 조회용 — (MSGKEY, REQDATE) 로 단건을 찾는다.
-- CREATE INDEX CONCURRENTLY ix_kkb_msg_key ON KKB_MSG (MSGKEY, REQDATE);
-- -----------------------------------------------------------------------------

\echo '=== done. Record the output against NFR-PERF-04 in requirements-trace-biztalk.csv ==='
