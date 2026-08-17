-- =============================================================================
-- IRIS BizTalk Portal — authentication module DDL
--
-- req: CONST-DATA-L01, ADR-006, ADR-LOGIN-012, RISK-L04
--
-- ⚠ 자동 적용 금지 / NOT AUTO-APPLIED
--   이 스크립트는 애플리케이션 기동 시 실행되지 않는다. 대상 데이터베이스는 아직
--   운영 중인 레거시 IRIS_ADMIN 과 공유되므로, 모든 변경은 DBA 검토와 롤백
--   스크립트를 거쳐야 한다 (PROJECT-STRUCTURE.md §5 decision 2, RISK-008).
--
--   This script is not executed at application startup. The target database is
--   shared with the still-running legacy IRIS_ADMIN, so every change requires DBA
--   review and a rollback script before it is applied.
--
-- 설계 원칙 — 추가 전용 / DESIGN PRINCIPLE — ADDITIVE ONLY
--   기존 a_user_ldgr 컬럼(특히 PWD)은 변경하지 않고 신규 컬럼만 추가한다.
--   이유가 두 가지다:
--     1) 레거시가 같은 테이블을 계속 읽으며 정상 동작해야 한다 (공존 기간)
--     2) 구 해시가 남아 있어야 롤백이 가능하다 (RISK-L04 — Argon2id 로 상향된
--        비밀번호는 레거시가 검증할 수 없으므로, PWD 를 덮어쓰면 롤백이 단방향이 된다)
--
--   Existing a_user_ldgr columns — PWD above all — are left untouched and only new
--   columns are added, for two reasons: the legacy must keep working against the
--   same table during coexistence, and the old hash must survive for rollback.
--   Overwriting PWD would make rollback one-way (RISK-L04).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. a_user_ldgr — additive columns for the new credential scheme
--    req: FR-LOGIN-005, CONST-DATA-L01
-- -----------------------------------------------------------------------------
ALTER TABLE a_user_ldgr ADD COLUMN IF NOT EXISTS PWD_HASH   VARCHAR(255);
ALTER TABLE a_user_ldgr ADD COLUMN IF NOT EXISTS PWD_SCHEME VARCHAR(16);

COMMENT ON COLUMN a_user_ldgr.PWD_HASH   IS 'Argon2id password hash (new scheme). Legacy PWD column retained untouched.';
COMMENT ON COLUMN a_user_ldgr.PWD_SCHEME IS 'Credential scheme in force: NULL = legacy SHA-256, ARGON2ID = migrated.';

-- 마이그레이션 진행률 조회용 / for tracking migration progress (RISK-L09)
CREATE INDEX IF NOT EXISTS IX_USER_LDGR_PWD_SCHEME ON a_user_ldgr (PWD_SCHEME);

-- -----------------------------------------------------------------------------
-- 2. IRIS_USER_PWD_HISTORY — password reuse prevention
--    req: FR-PWD-004
--
--    해시만 저장한다. 평문은 어떤 형태로도 보관하지 않는다.
--    Hashes only. No plaintext in any form.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS IRIS_USER_PWD_HISTORY (
    ID        BIGSERIAL    PRIMARY KEY,
    EML       VARCHAR(50)  NOT NULL,
    PWD_HASH  VARCHAR(255) NOT NULL,
    CHNG_AT   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS IX_PWD_HISTORY_EML_AT ON IRIS_USER_PWD_HISTORY (EML, CHNG_AT DESC);

-- -----------------------------------------------------------------------------
-- 3. IRIS_USER_SESSION — shared session registry
--    req: FR-LOGIN-016, FR-LOGIN-017, ADR-LOGIN-012
--
--    EML 에 UNIQUE 제약을 둔다. "계정당 활성 세션 1개" 정책을 애플리케이션 규율이
--    아니라 데이터베이스 제약으로 보장하기 위한 것이다 — 동시 로그인 경합에서
--    두 행이 함께 남는 상태를 구조적으로 배제한다.
--    A UNIQUE constraint on EML makes "one active session per account" a database
--    guarantee rather than an application convention, structurally excluding the
--    race in which two rows survive a concurrent login.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS IRIS_USER_SESSION (
    EML         VARCHAR(50)  NOT NULL,
    SESSION_ID  VARCHAR(128) NOT NULL,
    SERVER_NAME VARCHAR(64)  NOT NULL,
    SOURCE_IP   VARCHAR(45)  NOT NULL,
    USER_AGENT  VARCHAR(512),
    LOGIN_AT    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT PK_IRIS_USER_SESSION PRIMARY KEY (SESSION_ID),
    CONSTRAINT UQ_IRIS_USER_SESSION_EML UNIQUE (EML)
);

CREATE INDEX IF NOT EXISTS IX_USER_SESSION_LOGIN_AT ON IRIS_USER_SESSION (LOGIN_AT);

-- -----------------------------------------------------------------------------
-- 4. IRIS_AUTH_AUDIT — append-only authentication audit trail
--    req: NFR-OPS-AUDIT-L01, NFR-OPS-AUDIT-L02, ADR-006, CONST-LEGAL-02
--
--    보존 5년 (PM 결정 2026-08-14, OI-02 종결).
--    Retention: 5 years, per the PM decision of 2026-08-14 closing OI-02.
--
--    애플리케이션 계정에는 INSERT 권한만 부여한다 (ADR-007). 추가 전용 성질이
--    애플리케이션 코드가 아니라 DB 권한으로 보장되어야 한다 — 애플리케이션이
--    침해되어도 감사 흔적을 지울 수 없어야 하기 때문이다 (TM-L013).
--    The application account is granted INSERT only (ADR-007): append-only must be
--    enforced by database privilege, not application code, so that a compromised
--    application still cannot erase its own trail.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS IRIS_AUTH_AUDIT (
    ID             BIGSERIAL    PRIMARY KEY,
    OCCURRED_AT    TIMESTAMPTZ  NOT NULL,
    ACTOR          VARCHAR(50)  NOT NULL,
    TARGET_ACCOUNT VARCHAR(50),
    ACTION         VARCHAR(64)  NOT NULL,
    OUTCOME        VARCHAR(16)  NOT NULL,
    DETAIL         VARCHAR(512),
    SOURCE_IP      VARCHAR(45),
    CORRELATION_ID VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS IX_AUTH_AUDIT_OCCURRED ON IRIS_AUTH_AUDIT (OCCURRED_AT DESC);
CREATE INDEX IF NOT EXISTS IX_AUTH_AUDIT_ACTOR    ON IRIS_AUTH_AUDIT (ACTOR, OCCURRED_AT DESC);
CREATE INDEX IF NOT EXISTS IX_AUTH_AUDIT_OUTCOME  ON IRIS_AUTH_AUDIT (OUTCOME, OCCURRED_AT DESC);

-- -----------------------------------------------------------------------------
-- 5. Least-privilege grants — ADR-007, T-L1-04
--    실제 롤 이름은 배포 환경에서 확정한다. 아래는 요구되는 권한의 정본 정의다.
--    Role names are fixed per environment; this is the authoritative statement of
--    the privileges the application account must and must not hold.
--
--    핵심: 업무 테이블에 UPDATE/DELETE 를 부여하지 않음으로써 ADR-002 의
--    "읽기 전용" 의도를 코드 규약이 아니라 DB 보장으로 만든다.
--    Crucially, withholding UPDATE/DELETE on business tables turns ADR-002's
--    read-only intent into a database guarantee rather than a code convention.
-- -----------------------------------------------------------------------------
-- GRANT SELECT                 ON a_user_ldgr              TO iris_portal_app;
-- GRANT UPDATE (PWD_HASH, PWD_SCHEME, LOGIN_ATTEMPT, OTP_FAIL_CNT,
--               LAST_LOGIN_DT, LAST_CHNG_PWD_DT, PWD_INIT_YN, OTP_KEY)
--                              ON a_user_ldgr              TO iris_portal_app;
-- GRANT SELECT                 ON USER_GRP_JNNG          TO iris_portal_app;
-- GRANT SELECT, INSERT         ON IRIS_USER_PWD_HISTORY  TO iris_portal_app;
-- GRANT SELECT, INSERT, DELETE ON IRIS_USER_SESSION      TO iris_portal_app;
-- GRANT INSERT                 ON IRIS_AUTH_AUDIT        TO iris_portal_app;
-- -- 명시적 금지 / explicitly withheld: UPDATE, DELETE on IRIS_AUTH_AUDIT
