-- =============================================================================
-- IRIS BizTalk Portal — 카카오 알림톡 아웃박스 DDL
-- AlimTalk transactional outbox
--
-- req: ADR-ATK-023, ADR-ATK-026, FR-ATS-001, FR-ATS-005, FR-ATS-008, RISK-A06
--
-- ⚠ 자동 적용 금지 / NOT AUTO-APPLIED
--   V1 과 같다 — 이 스크립트는 기동 시 실행되지 않는다. 대상 DB 는 운영 중인 레거시
--   IRIS_ADMIN 과 공유되므로 DBA 검토와 롤백 스크립트를 거쳐야 한다.
--   Like V1, this is not executed at startup: the database is shared with the running legacy
--   system, so every change requires DBA review and a rollback script.
--
-- ✅ G1 결재 완료 (PM, 2026-08-19 — 명세 §6.4). 적용 조건 중 결재는 충족되었다.
--    G1 approved (PM, 2026-08-19, specification §6.4); the approval condition is met.
--   스크립트를 <b>작성</b>하는 것은 되돌릴 수 있고, <b>적용</b>하는 것은 되돌리기 어렵다.
--   Writing the script is reversible; applying it is not.
-- =============================================================================
--
-- 설계 원칙 — 공유 테이블을 건드리지 않는다 / DESIGN PRINCIPLE — NO SHARED TABLE IS TOUCHED
--
--   ADR-ATK-023 원안은 KKB_ADMIN_SEND_HIS 에 상태 컬럼을 추가하려 했다. 조사 결과
--   상태는 KKO_MSG_LOG 에 이미, 그리고 <b>메시지 단위</b>로 존재한다 — KKB_ADMIN_SEND_HIS 는
--   한 행이 발송 <b>행위</b> 하나이므로 거기 상태를 두면 단위가 어긋난다. 요구를 철회했고
--   (ADR-ATK-023 수정 1) 그 결과 RISK-A06 이 소멸했다.
--
--   The original decision added a status column to the shared KKB_ADMIN_SEND_HIS. Investigation
--   found the status already exists in KKO_MSG_LOG at the per-message grain, whereas
--   KKB_ADMIN_SEND_HIS holds one row per send *action*. The requirement was withdrawn
--   (ADR-ATK-023 amendment 1) and RISK-A06 retired with it.
--
--   근거 / evidence: mapping/analysis/ANALYSIS-A2-02-existing-schema.md
--
--   따라서 이 스크립트는 <b>새 테이블 하나만</b> 만든다. 발신번호 슬라이스가 CONFLICT-S01 에서
--   이미 받은 승인 범위와 같다.
--   This script therefore creates <b>one new table only</b> — the scope already granted to the
--   발신번호 slice under CONFLICT-S01.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- KKB_ATK_SEND_OUTBOX — 접수 확정까지만 사는 표
--                       lives only until acceptance is settled
--
-- 왜 이 표가 필요한가 / why this table is needed:
--   KKO_MSG_LOG 는 게이트웨이가 쓰며 <b>접수 이후</b>만 말한다. 아웃박스가 덮는 구간은 그
--   앞이다 — 보내기로 결정한 시점부터 접수가 확정되는 시점까지. 그 사이에 프로세스가 죽으면
--   어느 표에도 흔적이 없고, 재시도해도 되는지 알 수 없다. 레거시가 정확히 그랬다:
--   KKB_ADMIN_SEND_HIS 에 행을 넣고 나서 벤더를 호출했고, 둘을 감싸는 트랜잭션은 없었다
--   (biztalk_admin_50_s001_act.jsp:118-133).
--
--   KKO_MSG_LOG is written by the gateway and describes only what happened *after* acceptance.
--   This table covers the gap before it. The legacy inserted its history row and then called the
--   vendor with no transaction spanning the two.
-- -----------------------------------------------------------------------------
CREATE TABLE KKB_ATK_SEND_OUTBOX (
    -- 대리 키. tran_id 를 주 키로 쓰지 않는 이유: 다건 발송은 하나의 tran_id 아래 여러
    -- 메시지를 갖는다(계약 msg_data 배열). tran_id 는 배치 전체에 하나다.
    -- A surrogate key: a batch shares one tran_id across many messages, so tran_id alone
    -- cannot identify a row.
    OUTBOX_ID      BIGSERIAL     NOT NULL,

    -- 이용기관코드 — 모든 조회가 이것으로 한정된다 (FR-AZ-A02).
    IS_CD          VARCHAR(6)    NOT NULL,

    -- 거래고유번호. 계약 10자 (ADR-ATK-026). 벤더 멱등성의 키이기도 하다 — 그 전제가
    -- 확인되지 않았음은 RISK-A07 에 기록되어 있다.
    -- The contract's 10-character transaction id, also the vendor idempotency key; that the
    -- vendor honours it is unverified (RISK-A07).
    TRAN_ID        VARCHAR(10)   NOT NULL,

    -- 배치 내 순번. 단건은 1. 레거시는 이 값을 아예 내보내지 않아 수신자와 메시지의 대응이
    -- 배열 위치에만 의존했다 (D-A3).
    -- Position within the batch; 1 for a single send. The legacy emitted no order at all (D-A3).
    MSG_ORDER      INTEGER       NOT NULL,

    -- 벤더에 보낼 payload. 계약 적합성은 ContractConformanceTest 가 지킨다.
    --
    -- ⚠ 수신번호가 이 안에 <b>평문</b>으로 들어간다 — 벤더가 받아야 하는 값이므로 마스킹할 수
    --   없다. KKO_MSG_LOG 는 PHONE 을 암호화해 두었고(decrypt(PHONE) 로 읽는다), 이 표도 같은
    --   기준을 따라야 한다. 컬럼 암호화 방식은 A2-04 에서 결정한다 — harness §10 은 PII 컬럼에
    --   AES-256-GCM 을 요구한다. 그때까지 이 표는 <b>운영에 적용하지 않는다</b>.
    --
    --   The recipient number appears here in clear, because the vendor must receive it. KKO_MSG_LOG
    --   encrypts PHONE, and this table must meet the same bar; harness §10 requires AES-256-GCM for
    --   PII columns. Until that is decided in A2-04 this table is not applied in production.
    PAYLOAD        TEXT          NOT NULL,

    -- 상태. 'UNKNOWN' 이 있는 이유가 이 표의 존재 이유다 — 60초 read 타임아웃 뒤에는 전달
    -- 여부를 <b>모른다</b>. 모르는 것을 FAILED 로 적으면 재시도가 중복 발송이 된다.
    -- 'UNKNOWN' is why this table exists: after a 60-second read timeout delivery is genuinely
    -- unknown, and recording that as FAILED would turn a retry into a duplicate send.
    STATUS         VARCHAR(8)    NOT NULL,

    ATTEMPTS       INTEGER       NOT NULL DEFAULT 0,

    -- 예약 발송 시각. 계약 reqdate 는 항목마다 선언되는데 레거시 다건 화면은 수집하지
    -- 않았다 (D-A14).
    DUE_AT         TIMESTAMP     NULL,

    -- 클레임 만료. SELECT ... FOR UPDATE SKIP LOCKED 가 행을 잡되, 인스턴스가 죽으면 락이
    -- 풀리므로 시간 기반 만료도 함께 둔다.
    --
    -- ⚠ 이 값은 벤더 read 타임아웃(60초)보다 <b>길어야 한다</b>. 짧으면 아직 응답을 기다리는
    --   행을 다른 인스턴스가 다시 집어 중복 발송한다. 설계 문서에 이 제약이 없었다 —
    --   ANALYSIS-A2-05-vendor-transport.md §2③ 참조.
    --   This must EXCEED the vendor read timeout (60 s). Shorter, and another instance re-claims a
    --   row whose response is still pending, sending it twice. The design did not state this.
    CLAIMED_UNTIL  TIMESTAMP     NULL,

    LAST_ERROR     VARCHAR(1000) NULL,

    CREATED_AT     TIMESTAMP     NOT NULL DEFAULT now(),
    UPDATED_AT     TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT PK_KKB_ATK_SEND_OUTBOX PRIMARY KEY (OUTBOX_ID),

    -- 같은 (기관, 거래번호, 순번) 을 두 번 접수하지 않는다. 이것이 재시도 안전성의 DB 측
    -- 절반이다 — 나머지 절반은 벤더의 멱등성이고 그것은 우리가 강제할 수 없다 (RISK-A07).
    -- The database half of retry safety; the other half is vendor idempotency, which we cannot
    -- enforce (RISK-A07).
    CONSTRAINT UQ_KKB_ATK_SEND_OUTBOX UNIQUE (IS_CD, TRAN_ID, MSG_ORDER),

    -- 상태 문자열을 코드에서만 검사하지 않는다. 잘못된 상태가 들어가면 디스패처가 그 행을
    -- 영원히 무시하고, 그것은 조용한 미전달이다.
    -- The status is not validated in code alone: an unrecognised value would make the dispatcher
    -- ignore the row forever, which is a silent non-delivery.
    CONSTRAINT CK_KKB_ATK_SEND_OUTBOX_STATUS
        CHECK (STATUS IN ('PENDING', 'SENT', 'FAILED', 'UNKNOWN', 'DEAD'))
);

-- 디스패처의 클레임 질의를 위한 인덱스 / for the dispatcher's claim query.
-- 부분 인덱스인 이유: SENT 와 DEAD 는 다시 조회되지 않으므로 인덱스에 담을 이유가 없고,
-- 발송량이 쌓이면 그 둘이 표의 대부분을 차지한다.
-- Partial, because SENT and DEAD are never re-queried and will come to dominate the table.
CREATE INDEX IX_KKB_ATK_SEND_OUTBOX_CLAIM
    ON KKB_ATK_SEND_OUTBOX (STATUS, DUE_AT, OUTBOX_ID)
    WHERE STATUS IN ('PENDING', 'FAILED', 'UNKNOWN');

-- 운영자 조회 — 기관별 최근 순 / operator lookup, per institution, newest first.
CREATE INDEX IX_KKB_ATK_SEND_OUTBOX_IS_CD
    ON KKB_ATK_SEND_OUTBOX (IS_CD, CREATED_AT DESC);

COMMENT ON TABLE KKB_ATK_SEND_OUTBOX IS
    '카카오 알림톡 발송 아웃박스 — 접수 확정까지의 구간만 보관한다. 접수 이후의 전달 상태는 KKO_MSG_LOG 가 갖는다.';

-- =============================================================================
-- 롤백 / ROLLBACK
--
--   DROP INDEX IF EXISTS IX_KKB_ATK_SEND_OUTBOX_IS_CD;
--   DROP INDEX IF EXISTS IX_KKB_ATK_SEND_OUTBOX_CLAIM;
--   DROP TABLE IF EXISTS KKB_ATK_SEND_OUTBOX;
--
--   되돌리기가 깨끗한 이유: 이 스크립트는 기존 객체를 하나도 변경하지 않는다. 레거시가 읽는
--   표는 손대지 않았으므로 롤백이 레거시에 영향을 주지 않는다.
--   The rollback is clean because nothing existing is altered: no table the legacy reads is
--   touched, so reverting cannot affect it.
--
--   ⚠ 단, 아웃박스에 미전송 행이 남은 상태로 DROP 하면 그 발송은 사라진다. 롤백 전에
--     STATUS IN ('PENDING','FAILED','UNKNOWN') 인 행이 0 인지 확인한다.
--     Dropping the table while unsent rows remain discards those sends. Verify that no row is in
--     PENDING, FAILED or UNKNOWN before rolling back.
-- =============================================================================
