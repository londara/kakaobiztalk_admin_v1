-- =============================================================================
-- IRIS BizTalk Portal — 발신번호 아카이브 및 전역 유일 제약 DDL
-- Sender-number archive table and global uniqueness constraint
--
-- req: FR-SNDD-001, FR-SNDD-003, FR-SNDD-008, FR-SNDC-004, CONST-DATA-D04,
--      CONST-BIZ-D01, ADR-SND-017, ADR-SND-018, RISK-S02, RISK-S07
-- sprint: S2a, task S2a-03
--
-- ⚠ 자동 적용 금지 / NOT AUTO-APPLIED
--   V1·V2 와 같다 — 이 스크립트는 기동 시 실행되지 않는다. 대상 DB 는 운영 중인 레거시
--   IRIS_ADMIN·AOA_ADMIN·KAKAOTALK 과 공유되므로 DBA 검토와 롤백 스크립트를 거쳐야 한다.
--   Like V1 and V2, this is not executed at startup: the database is shared with three running
--   legacy applications, so every change requires DBA review and a rollback script.
--
-- ⛔ G1 미결재 / G1 NOT YET APPROVED (2026-08-20)
--   CONFLICT-S01 (논리 삭제 vs CONST-DATA-01 "스키마 변경 없음") 은 아직 PM 결재 전이다.
--   설계는 그 범위를 <b>테이블 하나와 인덱스 하나, 추가만</b>으로 좁혔다(ADR-SND-017).
--   스크립트를 <b>작성</b>하는 것은 되돌릴 수 있고 <b>적용</b>하는 것은 되돌리기 어렵다 —
--   그래서 작성해 두고 적용은 결재 뒤로 둔다.
--   CONFLICT-S01 (logical delete vs "no schema change in scope") awaits PM approval. Design
--   narrowed it to <b>one new table and one index, additive only</b>. Writing the script is
--   reversible; applying it is not, so it is written now and applied after approval.
--
-- 선행 조건 / PREREQUISITES — 순서대로 / in order
--   1. G1 결재 (CONFLICT-S01) / G1 approval
--   2. S1-03 — BIZ_DB 와 BIZTALK_DB 가 같은 물리 DB 임을 확인 (RISK-S01)
--        BIZ_DB and BIZTALK_DB confirmed to be the same physical database
--   3. S2a-01 — 기관 간 중복 해소 완료. 남아 있으면 §3 이 실패한다 (RISK-S02)
--        cross-institution duplicates resolved; §3 fails outright if any remain
--   4. §1 의 결정성 검사 통과 (S1-01, RISK-S07)
--        the determinism check in §1 passes
-- =============================================================================


-- -----------------------------------------------------------------------------
-- §1. 선행 검사 — ENCRYPT 는 결정적인가 / PRECHECK: is ENCRYPT deterministic?
--
-- 스파이크 S1-01 이 답해야 했던 질문이며 아직 답이 없다. 여기서 스크립트 자신이 묻는다.
--
-- 왜 스크립트 안에서 묻는가: §3 의 유일 인덱스는 <b>결정적일 때만</b> 유일성을 보장한다.
-- 비결정적이면 같은 번호가 매번 다른 암호문이 되어 인덱스는 충돌을 보지 못하고, 제약은
-- <b>있는데 아무것도 막지 못하는</b> 상태가 된다 — 통제가 없는 것보다 나쁘다. 있다고 믿게
-- 되기 때문이다. 그 상태는 D-S1 과 같은 부류이므로, 여기서 요란하게 실패시킨다.
--
-- The question spike S1-01 was to answer, still unanswered — so the script asks it itself.
-- The unique index in §3 only guarantees uniqueness if ENCRYPT is deterministic. If it is not,
-- the same number encrypts differently each time, the index never sees a collision, and the
-- constraint <b>exists while preventing nothing</b> — worse than no control, because it is
-- believed. That is D-S1's family, so this fails loudly instead.
--
-- 비결정적이면 ADR-SND-018 의 blind-index 분기로 전환해야 한다 — 컬럼 추가, HMAC 키
-- (ADR-007), 전 행 backfill, 그리고 새 위협 T-I7. 그 작업은 이 스크립트에 없다.
-- If it is non-deterministic, switch to ADR-SND-018's blind-index branch: a new column, an HMAC
-- key (ADR-007), a backfill of every row, and a new threat (T-I7). That work is not in this file.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF ENCRYPT('01012345678') IS DISTINCT FROM ENCRYPT('01012345678') THEN
        RAISE EXCEPTION
            'ENCRYPT() is not deterministic: a unique index on DP_NO would enforce nothing. '
            'Stop and switch to the blind-index branch (ADR-SND-018, RISK-S07). '
            'Spike S1-01 has its answer.';
    END IF;
END
$$;


-- -----------------------------------------------------------------------------
-- §2. 아카이브 테이블 / the archive table
--
-- PM 결정 AMB-S02 는 삭제를 논리 삭제로 정했고, ADR-SND-017 은 그것을 상태 컬럼이 아니라
-- <b>행 이동</b>으로 구현한다. 이유는 하나다: KAKAOTALK 은 발송 권한을 KKB_DPNO_LDGR 의 행
-- 존재만으로 판단하며 (IDO.KKB_DPNO_LDGR_L001) 어떤 상태 컬럼도 읽지 않는다. 상태 컬럼을
-- 두면 "삭제된" 번호가 계속 발송 가능하다 — 이용기관 슬라이스의 D-I1 을 의도적으로 다시
-- 만드는 셈이다. 행이 사라지면 레거시는 <b>수정 없이</b> 그 번호를 거부한다.
--
-- Ruling AMB-S02 made deletion logical; ADR-SND-017 implements it as a <b>row move</b> rather
-- than a status column, because KAKAOTALK grants sending rights by row presence alone and reads
-- no status. A status column would keep "deleted" numbers sendable — D-I1 rebuilt on purpose.
-- With the row gone, the legacy rejects the number with no change on its side.
--
-- LIKE 를 쓰는 이유 / why LIKE:
--   컬럼 타입을 손으로 적으면 원장과 <b>다른</b> 타입을 적을 수 있고, 그 차이는 복원할 때
--   비로소 드러난다. 특히 DP_NO·RGSR_NM 은 ENCRYPT() 의 반환 타입이며 이 저장소는 그
--   정의를 갖고 있지 않다 (ADR-005 §4.3 미해결). LIKE 는 데이터베이스가 아는 정의를 그대로
--   복제하므로 추측이 개입하지 않는다.
--   Hand-written column types could differ from the ledger's, and the difference would surface
--   only on restore. DP_NO and RGSR_NM in particular are ENCRYPT()'s return type, whose
--   definition this repository does not have (ADR-005 §4.3, unresolved). LIKE copies the
--   definition the database already holds, so no guess is involved.
--
--   INCLUDING 절을 쓰지 않는다 — 원장의 제약·인덱스는 "발송 가능한 번호" 에 대한 규칙이며
--   아카이브에는 해당하지 않는다. 특히 §3 의 유일 인덱스가 복제되면 같은 번호를 두 번
--   삭제할 수 없게 된다 (FR-SNDD-008 이 그것을 허용한다).
--   No INCLUDING clause: the ledger's constraints are rules about *sendable* numbers and do not
--   apply here. Copying §3's unique index in particular would make it impossible to delete the
--   same number twice, which FR-SNDD-008 explicitly permits.
-- -----------------------------------------------------------------------------
CREATE TABLE KKB_DPNO_ARCV (LIKE KKB_DPNO_LDGR);

-- 삭제 메타데이터 / deletion metadata.
--
--   DEL_DT   삭제 시각 — 레거시가 RGDT 에 쓰는 것과 같은 YYYYMMDDHH24MISS 문자열이다.
--            애플리케이션 시계를 쓰지 않는다: 프로그램의 Clock 은 UTC 이고 이 컬럼군은
--            벽시계 문자열이므로 섞으면 9시간 차이가 조용히 끼어든다 (ADR-INST-017).
--            The same YYYYMMDDHH24MISS wall-clock string the legacy writes into RGDT. The
--            application Clock is UTC and this column family is wall-clock text; mixing them
--            silently interleaves two epochs nine hours apart (ADR-INST-017).
--   DEL_ID   삭제한 운영자 — ENCRYPT() 를 통과한다. 원장의 RGSR_ID 가 평문 이메일이었던
--            것이 D-S16 이고, 새로 만드는 컬럼이 같은 결함을 반복할 이유는 없다
--            (AMB-S09 결정 B).
--            The deleting operator, passed through ENCRYPT(). RGSR_ID holding a plaintext email
--            is D-S16; a new column has no reason to repeat it (AMB-S09 ruling B).
--   REASON   사유 — 필수다 (FR-SNDD-006). 이력에도 남지만 (KKB_DPNO_HIS.REASON) 아카이브
--            행만 보고도 왜 지워졌는지 알 수 있어야 한다.
--            The reason, mandatory (FR-SNDD-006). It is also in the history, but an archive row
--            should be readable on its own.
--
-- 컬럼 타입은 KKB_DPNO_HIS 의 대응 컬럼과 맞춘다 / types match the corresponding
-- KKB_DPNO_HIS columns, which is where REASON and the RGDT string format already exist.
ALTER TABLE KKB_DPNO_ARCV
    ADD COLUMN DEL_DT VARCHAR(14) NOT NULL,
    ADD COLUMN DEL_ID BYTEA,
    ADD COLUMN REASON VARCHAR(100);

COMMENT ON TABLE KKB_DPNO_ARCV IS
    'Deleted sender numbers (ADR-SND-017). A row here is the logical-delete representation: the '
    'ledger holds only sendable numbers because KAKAOTALK grants sending rights by row presence '
    'alone. Restoration is the reverse move and is a supported operation, not a DBA recovery.';

-- 복원과 감사가 이 테이블의 두 용도이며, 둘 다 기관 단위로 찾는다.
-- Restoration and audit are this table's two uses, and both look up by institution.
CREATE INDEX IX_KKB_DPNO_ARCV_01 ON KKB_DPNO_ARCV (IS_CD, DEL_DT DESC);


-- -----------------------------------------------------------------------------
-- §3. 전역 유일 제약 / the global uniqueness constraint
--
-- PM 결정 AMB-S03: 발신번호는 <b>모든 이용기관에 걸쳐</b> 유일하다 (FR-SNDC-004).
-- 레거시 중복검사는 KKB_DPNO_LDGR_L001 을 재사용했고 그 조건에 IS_CD 가 함께 있었으므로
-- 요청 기관 자신의 번호만 보았다 — 같은 번호를 여러 기관이 나란히 등록할 수 있었다 (D-S9).
--
-- 왜 애플리케이션 코드가 아니라 인덱스인가 / why an index and not application code:
--   AOA_ADMIN 이 <b>같은 테이블에 쓰는 두 번째 애플리케이션</b>이며 우리 코드를 거치지
--   않는다 (RISK-S05, 위협 T-T5). 애플리케이션에만 규칙을 두면 그 콘솔이 규칙을 우회한다.
--   데이터베이스 제약은 쓰는 주체를 가리지 않는다.
--   AOA_ADMIN is a <b>second writer on the same table</b> and does not pass through our code
--   (RISK-S05, threat T-T5). A rule in application code only is a rule that console bypasses;
--   a database constraint binds every writer.
--
-- 이 문장은 기관 간 중복이 하나라도 남아 있으면 <b>실패한다</b>. 그것이 S2a-01(중복 해소)이
-- 후속 작업이 아니라 선행 조건인 이유다 (RISK-S02).
-- This statement <b>fails outright</b> if any cross-institution duplicate remains, which is why
-- S2a-01 is a prerequisite rather than a follow-up (RISK-S02).
--
-- 형태는 §1 이 통과했을 때만 옳다: 결정적 ENCRYPT 에서 같은 번호는 같은 암호문이므로
-- DP_NO 자체가 유일 키가 된다. 함수 표현식 인덱스 (decrypt(DP_NO)) 를 쓰지 않는 이유는
-- decrypt 가 IMMUTABLE 이라고 보장할 수 없기 때문이다 — 정의를 갖고 있지 않다.
-- The form is only correct given §1: with a deterministic ENCRYPT the same number yields the
-- same ciphertext, so DP_NO itself is the unique key. An expression index on decrypt(DP_NO) is
-- avoided because decrypt cannot be assumed IMMUTABLE — its definition is not available.
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX UX_KKB_DPNO_LDGR_01 ON KKB_DPNO_LDGR (DP_NO);

COMMENT ON INDEX UX_KKB_DPNO_LDGR_01 IS
    'FR-SNDC-004 / CONST-BIZ-D01: a sender number is globally unique across institutions (PM '
    'ruling AMB-S03, fixing D-S9). Enforced in the database because AOA_ADMIN writes the same '
    'table without passing through the portal (RISK-S05, T-T5).';


-- =============================================================================
-- 롤백 / ROLLBACK
--
--   DROP INDEX  UX_KKB_DPNO_LDGR_01;
--   DROP TABLE  KKB_DPNO_ARCV;
--
-- 스키마 롤백은 완전하다 — KKB_DPNO_LDGR 은 변경되지 않으므로 기존 독자가 보는 것이
-- 달라지지 않는다. G1 이 승인하도록 요청받은 선례는 "이 프로그램은 테이블을 추가할 수
-- 있다" 이며 "공유 스키마를 변경할 수 있다" 가 아니다.
-- The schema rollback is complete: KKB_DPNO_LDGR is not altered, so no existing reader's view
-- changes. The precedent G1 is asked to set is "this programme may add tables", not "this
-- programme may alter shared schema".
--
-- ⚠ 되돌릴 수 없는 것은 §3 의 <b>선행 조건</b>이다. 유일 인덱스를 만들기 위해 해소한 기관 간
--   중복은 데이터 변경이며 인덱스를 지워도 복구되지 않는다. 그 해소는 이 스크립트가 아니라
--   전-상태를 기록한 감사 가능한 마이그레이션으로 수행한다 (S2a-01).
--   What is not reversible is §3's <b>precondition</b>: resolving cross-institution duplicates is
--   a data change and dropping the index does not undo it. That resolution is performed by an
--   auditable migration with a recorded before-state (S2a-01), not by this script.
-- =============================================================================
