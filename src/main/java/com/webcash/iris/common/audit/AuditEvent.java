package com.webcash.iris.common.audit;

import java.time.Instant;

/**
 * 감사 기록 1건. / A single audit record.
 *
 * <p>Jex 런타임의 {@code mntLogYn=Y} 동작을 대체한다. 이 동작은 레거시 biztalk·auth
 * 소스 어디에도 구현되어 있지 않고 <b>런타임이 제공</b>했다 — Jex 를 버리면 아무것도
 * 컴파일 실패하지 않은 채 조용히 사라진다(RISK-002).</p>
 * <p>Replaces the Jex runtime's {@code mntLogYn=Y} behaviour. Nothing in the legacy
 * source performs it — the <b>runtime</b> did. Discarding Jex removes it silently,
 * with no compile error and no failing test (RISK-002).</p>
 *
 * <p><b>민감값 금지.</b> 비밀번호·OTP 코드·OTP 비밀키·세션 식별자는 어떤 필드에도
 * 담지 않는다. 검색 조건에 전화번호가 포함되는 경우 해시하여 저장한다 — 감사 기록이
 * 2차 PII 저장소가 되지 않게 하기 위한 것이다.</p>
 * <p><b>No sensitive values.</b> Never carries a password, OTP code, OTP secret or
 * session id. Where search criteria include a phone number it is hashed, so the
 * audit store cannot become a secondary PII repository.</p>
 *
 * @param occurredAt    발생 시각(UTC) / when it happened, UTC
 * @param actor         행위자 식별자(이메일) / the acting principal, by email
 * @param targetAccount 대상 계정 (운영자 조작 시) / target account for operator actions, may be null
 * @param action        행위 코드 / the action code
 * @param outcome       결과 / the outcome
 * @param detail        비민감 부가정보 / non-sensitive supplementary detail
 * @param sourceIp      신뢰 가능한 출처 IP / trusted source address
 * @param correlationId 요청 상관 식별자 / request correlation id
 *
 * // source: WSVC.apc_login_proc.xml — mntLogYn=Y
 * // req: NFR-OPS-AUDIT-L01, NFR-OPS-AUDIT-L02, ADR-006
 */
public record AuditEvent(
        Instant occurredAt,
        String actor,
        String targetAccount,
        String action,
        Outcome outcome,
        String detail,
        String sourceIp,
        String correlationId
) {

    /** 감사 결과 구분. / Audit outcome classification. */
    public enum Outcome {
        /** 정상 처리 / the action succeeded. */
        OK,
        /** 거부됨 — 인증·인가 실패 포함 / denied, including authentication and authorization failures. */
        DENIED,
        /** 처리 중 오류 / an error occurred. */
        ERROR
    }

    /** 로그인 시도 / login attempt. */
    public static final String ACTION_LOGIN = "auth.login";
    /** 계정 잠금 발생 / account became locked. */
    public static final String ACTION_LOCKOUT = "auth.lockout";
    /** 비밀번호 변경 / password changed. */
    public static final String ACTION_PASSWORD_CHANGE = "auth.password.change";
    /** 운영자에 의한 비밀번호 초기화 / password reset by an operator. */
    public static final String ACTION_PASSWORD_RESET = "auth.password.reset";
    /** 로그아웃 / logout. */
    public static final String ACTION_LOGOUT = "auth.logout";
    /** OTP 등록 / OTP registered. */
    public static final String ACTION_OTP_REGISTER = "auth.otp.register";
    /** 운영자에 의한 OTP 초기화 / OTP reset by an operator. */
    public static final String ACTION_OTP_RESET = "auth.otp.reset";
    /** 관리자 로그인 알림 / administrator login notification. */
    public static final String ACTION_ADMIN_NOTIFICATION = "auth.admin.notification";
    /**
     * 클라이언트가 이용기관 식별자를 보낸 시도 / an attempt to supply a tenant identifier.
     *
     * <p>레거시는 클라이언트가 보낸 {@code ID} 를 조회 조건에 그대로 사용했다. 신규
     * 시스템에서는 무시되지만, 그 시도를 기록해 두면 탐색 행위를 사후에 식별할 수 있다.</p>
     * <p>The legacy used a client-supplied {@code ID} directly. It is ignored now, but recording
     * the attempt makes probing identifiable after the fact.</p>
     */
    // source: IDO.KKB_MSG_L002 — CASE WHEN :ID = '' THEN 1=1 ELSE ID = :ID END
    // req: TM-004, NFR-SEC-TENANT
    public static final String ACTION_TENANT_OVERRIDE_ATTEMPT = "biztalk.tenant.override.attempt";
    /** 문자내역 조회 / 문자내역 search. */
    public static final String ACTION_MESSAGE_HISTORY_SEARCH = "biztalk.message-history.search";
    /** 문자상세내역 열람 / message detail view. */
    public static final String ACTION_MESSAGE_DETAIL_VIEW = "biztalk.message-detail.view";
    /**
     * 문자내역 내보내기 / 문자내역 export.
     *
     * <p>조회와 <b>별개의 액션</b>으로 기록한다. 화면에서 한 페이지를 보는 것과 수천 건을
     * 파일로 반출하는 것은 감사 관점에서 같은 사건이 아니다 — 대량 반출은 유출의 주된
     * 경로이며, 조회와 구분되지 않으면 사후에 찾아낼 수 없다.</p>
     * <p>Recorded as a <b>distinct action</b> from a search: viewing one page and extracting
     * thousands of rows to a file are not the same event for audit purposes. Bulk extraction is a
     * primary exfiltration path, and if it is indistinguishable from browsing it cannot be found
     * afterwards.</p>
     *
     * // req: FR-MSG-017, NFR-OPS-AUDIT, CONST-LEGAL-02
     */
    public static final String ACTION_MESSAGE_HISTORY_EXPORT = "biztalk.message-history.export";
}
