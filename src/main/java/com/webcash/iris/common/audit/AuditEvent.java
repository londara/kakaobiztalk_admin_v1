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

    /**
     * 발신번호 목록 조회 / sender-number list read.
     *
     * <p>조회를 기록하는 이유가 다른 화면과 다르다. 발신번호는 PM 결정(AMB-S04)에 따라
     * 운영자 화면에 <b>마스킹 없이 전체가</b> 표시된다. 마스킹을 걷어낸 대신 조회 행위를
     * 남기는 것이 그 결정의 보상 통제이므로, 이 기록은 편의가 아니라 요구사항이다.</p>
     * <p>Recorded for a different reason than other screens: sender numbers are displayed to
     * operators <b>in full, unmasked</b>, per ruling AMB-S04. Auditing the read is the
     * compensating control that ruling depends on, so this record is a requirement rather than a
     * convenience.</p>
     *
     * <p>기관 코드와 건수만 담고 <b>번호 자체는 담지 않는다.</b> 담으면 감사 저장소가 더 긴
     * 보존 기간과 다른 접근 모델을 가진 2차 PII 저장소가 된다.</p>
     * <p>Carries the institution and a count, <b>never the numbers</b>: including them would make
     * the audit store a secondary PII repository with longer retention and different access.</p>
     *
     * // req: FR-SND-006, FR-SND-011, ADR-SND-019, NFR-OPS-AUDIT-D01
     */
    public static final String ACTION_SENDER_NUMBER_LIST = "biztalk.sender-number.list";

    /** 발신번호 상세 열람 / sender-number detail view. */
    // req: FR-SND-011, ADR-SND-019
    public static final String ACTION_SENDER_NUMBER_VIEW = "biztalk.sender-number.view";

    /**
     * 발신번호 등록 / sender number registered.
     *
     * <p>이 화면에서 등록은 데이터 입력이 아니라 <b>발송 권한 부여</b>다. {@code KAKAOTALK} 은
     * {@code KKB_DPNO_LDGR} 에 행이 있는지로 발신 가능 여부를 판단하므로, 행이 하나 생기는 것은
     * 그 번호로 메시지를 보낼 수 있게 되는 것과 같다. 소유 인증이 없는 상태에서(AMB-S01,
     * RESIDUAL-S01) 누가 어떤 번호를 <b>어떤 사유로</b> 주장했는지가 유일한 사후 추적 수단이다.</p>
     * <p>Registration here is not data entry but a <b>grant of sending capability</b>:
     * {@code KAKAOTALK} decides whether a send may proceed by whether a row exists, so creating one
     * makes that number usable as a caller ID. With ownership verification declined (AMB-S01,
     * RESIDUAL-S01), who claimed which number <b>and why</b> is the only trace available afterwards.</p>
     *
     * <p>기관 코드와 사유는 담고 <b>발신번호는 담지 않는다</b> — ADR-SND-019 는 조회에만 적용되는
     * 규칙이 아니다. 감사 저장소는 보존 기간이 길고 접근 모델이 다르므로, 쓰기 기록이라고 해서
     * 번호를 담아도 되는 것은 아니다.</p>
     * <p>Carries the institution and the reason, <b>never the number</b>: ADR-SND-019 is not a
     * read-only rule. The audit store has longer retention and a different access model, and a write
     * record is no more entitled to hold the number than a read record is.</p>
     *
     * // source: biztalk_admin_12_c001_act.jsp — mntLogYn=N, no application-level record at all
     * // req: FR-AZ-D05, NFR-OPS-AUDIT-D01, ADR-SND-019
     */
    public static final String ACTION_SENDER_NUMBER_REGISTER = "biztalk.sender-number.register";

    /**
     * 발신번호 삭제 / sender numbers deleted.
     *
     * <p>등록과 <b>별개의 액션</b>이다. 삭제는 해당 기관의 발송 능력을 즉시 없애며, 번호가
     * 하나뿐인 기관이라면 발송이 전면 중단된다(위협 T-D4). 등록과 구분되지 않으면 "이 기관은
     * 언제 발송할 수 없게 되었는가" 를 사후에 답할 수 없다.</p>
     * <p>A <b>distinct action</b> from registration: deleting removes sending capability at once, and
     * for an institution with a single number it stops sending entirely (threat T-D4). If the two are
     * indistinguishable, "when did this institution lose the ability to send" cannot be answered.</p>
     *
     * <p>영향받은 <b>건수</b>를 함께 남긴다. 한 건을 지운 것과 그 기관의 번호 전부를 지운 것은
     * 같은 사건이 아니다. 번호 자체는 담지 않는다.</p>
     * <p>Recorded with the <b>count</b> affected: deleting one number and deleting all of an
     * institution's numbers are not the same event. The numbers themselves are not carried.</p>
     *
     * // source: biztalk_admin_10_d001_act.jsp — reported success even when nothing was deleted (D-S1)
     * // req: FR-AZ-D05, FR-SNDD-004, NFR-OPS-AUDIT-D01, ADR-SND-019
     */
    public static final String ACTION_SENDER_NUMBER_DELETE = "biztalk.sender-number.delete";

    /**
     * 이용기관 보고서 조회 / institution usage report query.
     *
     * <p>이 화면에는 개인정보가 없다. 그럼에도 감사가 필수인 이유는 노출되는 것이
     * <b>고객사별 발송량</b>이기 때문이다 — 캠페인 시기, 고객 규모, 성장률을 추론할 수 있는
     * 상업적으로 민감한 자료다. 레거시에는 이 기록이 전혀 없었고, 조회 서비스는 인증조차
     * 요구하지 않았다(D-R1, D-R17).</p>
     * <p>This screen holds no personal data. Auditing is still mandatory because what it exposes
     * is <b>each customer's send volume</b> — enough to infer campaign timing, customer base and
     * growth. The legacy recorded none of it, and its query service required no session at all
     * (D-R1, D-R17).</p>
     *
     * <p>기록에는 행위자·범위·기간·건수만 남기고 <b>수치 자체는 남기지 않는다</b>(T-R15).</p>
     * <p>Actor, scope, period and counts only — <b>never the figures themselves</b> (T-R15).</p>
     *
     * // req: FR-AZ-R05, NFR-OPS-AUDIT-R01
     */
    public static final String ACTION_REPORT_QUERY = "biztalk.report.query";

    /**
     * 이용기관 보고서 내보내기 / institution usage report export.
     *
     * <p>실제로 쓴 행 수를 함께 기록한다 — 누군가 보고서를 열었다는 사실과, 고객사 발송량
     * 9만 행을 시스템 밖으로 가져갔다는 사실은 다른 사건이다(FR-RPTX-012).</p>
     * <p>Recorded with the row count actually written: someone opening the report and someone
     * taking 90,000 rows of customer volume off the system are different events (FR-RPTX-012).</p>
     *
     * // req: FR-RPTX-012, NFR-OPS-AUDIT-R01
     */
    public static final String ACTION_REPORT_EXPORT = "biztalk.report.export";

    /**
     * 톡전송 거래내역 조회 / 톡전송 transaction-history query.
     *
     * <p>레거시에는 이 기록이 없었다. {@code mntLogYn=Y} 가 Jex 서비스 모니터 행을 남겼을
     * 뿐이고 그것은 Jex 와 함께 사라진다(BR-002…005). 이 화면이 노출하는 것은 <b>전 기관의
     * 거래 내역</b>이며, 기관 술어가 아예 없었으므로(D-T2) 누가 무엇을 보았는지에 대한 기록
     * 없이 모든 고객사의 거래가 한 그리드에 있었다.</p>
     * <p>The legacy recorded none of this: {@code mntLogYn=Y} produced a Jex service-monitor row that
     * disappears with Jex (BR-002…005). What this screen exposes is <b>every institution's
     * transactions</b> — there was no institution predicate at all (D-T2) — so every customer's
     * activity sat in one grid with no record of who looked at it.</p>
     *
     * <p>행위자·범위·조건·건수만 남기고 <b>거래 내용은 남기지 않는다</b>.</p>
     * <p>Actor, scope, criteria and row count only — <b>never the transaction contents</b>.</p>
     *
     * // req: FR-AZ-T05, NFR-OPS-AUDIT-T01
     */
    public static final String ACTION_TALK_HISTORY_QUERY = "biztalk.talk-history.query";

    /**
     * 톡전송 거래 상세내역 열람 / 톡전송 transaction-detail view.
     *
     * // req: FR-AZ-T05, NFR-OPS-AUDIT-T01
     */
    public static final String ACTION_TALK_DETAIL_VIEW = "biztalk.talk-history.detail.view";

    /**
     * 톡전송 메시지 상세 열람 / talk message-detail view.
     *
     * <p>이 슬라이스에서 가장 민감한 열람이다 — 메시지 본문, 템플릿 코드, 수신자 번호가 함께
     * 나온다. 레거시 질의는 {@code REQDATE + STATUS + MSGKEY} 만으로 키가 만들어져 <b>메시지
     * 키만 알면 다른 기관의 메시지 본문을 읽을 수 있었다</b>(D-T5).</p>
     * <p>The most sensitive read in the slice — message body, template code and recipient number
     * together. The legacy query was keyed on {@code REQDATE + STATUS + MSGKEY} alone, so <b>a
     * message key was sufficient to read another institution's message body</b> (D-T5).</p>
     *
     * // req: FR-AZ-T04, FR-AZ-T05, NFR-OPS-AUDIT-T01
     */
    public static final String ACTION_TALK_MESSAGE_VIEW = "biztalk.talk-history.message.view";

    /**
     * 톡전송 거래내역 내보내기 / 톡전송 transaction-history export.
     *
     * <p>실제로 쓴 행 수를 함께 기록한다. 레거시 다운로드는 화면과 <b>다른 테이블</b>을 조회해
     * 모든 기관의 메시지를 평문 전화번호와 함께 반환했고(D-T1), 그 반출에 대한 기록은 어디에도
     * 없었다. 파일은 복사되고 전달되고 보관된다 — 조회 기록과 반출 기록이 구분되지 않으면
     * 사후에 찾아낼 수 없다.</p>
     * <p>Recorded with the row count actually written. The legacy download queried <b>different
     * tables</b> than the screen and returned every institution's messages with plaintext phone
     * numbers (D-T1), with no record of the extraction anywhere. Files are copied, forwarded and
     * kept: if extraction is indistinguishable from browsing it cannot be found afterwards.</p>
     *
     * // req: FR-TLKX-007, NFR-OPS-AUDIT-T01
     */
    public static final String ACTION_TALK_HISTORY_EXPORT = "biztalk.talk-history.export";

    /**
     * 이용기관 수정 / 이용기관 updated.
     *
     * <p>레거시에는 이 기록이 없었다. {@code mntLogYn=Y} 가 Jex 서비스 모니터 행을 남겼을
     * 뿐이고 그것은 Jex 와 함께 사라진다. 이 화면이 바꾸는 것은 <b>다른 시스템이 인가 판단에
     * 쓰는 제어 데이터</b>다 — 기관코드는 모든 슬라이스의 조회 조건이고, 사용여부는 발송
     * 가능 여부를 결정한다. 누가 무엇을 어떻게 바꿨는지가 남지 않으면 사고 후에 되짚을
     * 방법이 없다.</p>
     * <p>The legacy recorded none of this: {@code mntLogYn=Y} produced a Jex service-monitor row
     * that disappears with Jex. What this screen changes is <b>control data another system uses to
     * decide entitlement</b> — the institution code every slice filters on, and the status that
     * decides whether sending is permitted. Without a record of who changed what, an incident
     * cannot be reconstructed.</p>
     *
     * <p>기록에는 <b>바뀐 필드의 이전값과 새값</b>이 담긴다(FR-AZ-I04). 설명은 값 없이 변경
     * 사실만 남긴다 — 650자까지 가능하며 감사 저장소는 본문 보관소가 아니다.</p>
     * <p>Carries the <b>prior and new values of the changed fields</b> (FR-AZ-I04). The
     * description records only that it changed: it runs to 650 characters and the audit store is
     * not a content repository.</p>
     *
     * // source: biztalk_admin_01_c001_act.jsp — mntLogYn=Y, no application-level record
     * // req: FR-AZ-I04, NFR-OPS-AUDIT-I01, FR-INSTC-007
     */
    public static final String ACTION_INSTITUTION_UPDATE = "biztalk.institution.update";

    /**
     * 이용기관 인증키 재발급 / 이용기관 인증키 rotated.
     *
     * <p>수정과 <b>별개의 액션</b>으로 기록한다. 필드 하나를 고치는 것과 고객사의 운영
     * 자격증명을 교체하는 것은 감사 관점에서 같은 사건이 아니다 — 후자는 그 고객사의 연동을
     * 즉시 중단시키며, 새 키가 전달되기 전까지 발송은 실패한다. 두 사건이 구분되지 않으면
     * "언제 이 고객사의 키가 바뀌었는가" 를 사후에 답할 수 없다.</p>
     * <p>Recorded as a <b>distinct action</b> from an edit: changing one field and replacing a
     * customer's live credential are not the same event for audit purposes. The latter stops that
     * customer's integration until the new key is distributed. If the two are indistinguishable,
     * "when did this customer's key change" cannot be answered afterwards.</p>
     *
     * <p><b>키는 담지 않는다</b> — 새 키도, 이전 키도, 마스킹된 조각도. 감사 저장소는 보존
     * 기간이 길고 접근 모델이 달라, 자격증명을 담으면 그것이 곧 2차 노출 경로가 된다
     * (FR-ATK-004, ADR-INST-015).</p>
     * <p><b>No key material</b> — not the new key, not the old one, not a masked fragment. The
     * audit store has longer retention and a different access model; a credential written there
     * becomes a second exposure path (FR-ATK-004, ADR-INST-015).</p>
     *
     * // source: biztalk_admin_01.js — btn_generate_code, unrecorded browser-side generation
     * // req: FR-ATK-004, FR-ATK-005, FR-INSTC-011, FR-AZ-I04
     */
    public static final String ACTION_INSTITUTION_KEY_ROTATE = "biztalk.institution.key.rotate";
}
