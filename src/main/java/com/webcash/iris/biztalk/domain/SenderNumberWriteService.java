package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.SenderNumberMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.logging.CorrelationId;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발신번호 쓰기 서비스 — 등록과 삭제. / Sender-number write service: register and delete.
 *
 * <p>{@link SenderNumberService} 와 분리된 이유는 이용기관 슬라이스와 같다 — 조회와 쓰기가 한
 * 클래스에 있으면 {@code @Transactional(readOnly)} 의 의미가 메서드마다 달라지고, 그 차이는
 * 읽는 사람이 매번 확인해야 하는 것이 된다. 이 슬라이스에서는 이유가 하나 더 있다: 쓰기 경로의
 * 결함이 <b>결과값 확인 누락</b>이었으므로(D-S1, D-S7), 확인해야 할 반환값이 있는 코드를 한 곳에
 * 모아 두는 편이 검토에 유리하다.</p>
 * <p>Separated from {@link SenderNumberService} for the reason the institution slice gives: mixing
 * reads and writes makes the meaning of {@code @Transactional(readOnly)} vary per method, and that
 * difference must then be re-checked by every reader. This slice adds a second reason — the write
 * path's defects were <b>unchecked return values</b> (D-S1, D-S7), so keeping the code that has
 * return values to check in one place helps review.</p>
 *
 * <h2>이 클래스가 고치는 것 / what this class fixes</h2>
 * <table>
 *   <caption>결함과 대응 / defects and their fixes</caption>
 *   <tr><th>결함</th><th>레거시</th><th>여기</th></tr>
 *   <tr><td>D-S1</td><td>마스킹된 표시값으로 삭제 → 0건 → 성공 보고</td>
 *       <td>{@link SenderNumberRef} 로 조회 → 0건이면 예외</td></tr>
 *   <tr><td>D-S5</td><td>이력 {@code DP_NO} 에 콤마로 이어붙인 목록</td>
 *       <td>반복문의 그 번호 하나로 이력 1건</td></tr>
 *   <tr><td>D-S6</td><td>트랜잭션 없음 — 중간 실패 시 일부만 삭제</td>
 *       <td>{@code @Transactional} 한 경계 안에 전부</td></tr>
 *   <tr><td>D-S7</td><td>이력 insert 뒤 <b>직전</b> 결과를 검사</td>
 *       <td>각 문장의 반환값을 그 자리에서 검사</td></tr>
 *   <tr><td>D-S9</td><td>중복검사에 {@code IS_CD} 술어가 있었다</td>
 *       <td>{@link SenderNumberMapper#countAnywhere} — 기관 술어 없음</td></tr>
 *   <tr><td>D-S11</td><td>클라이언트 검증이 없는 요소를 검사 → 전부 통과</td>
 *       <td>서버에서 전부 검증</td></tr>
 * </table>
 *
 * <h2>순서가 의미를 갖는 곳 / where ordering carries meaning</h2>
 * <p>삭제는 <b>아카이브 → 삭제 → 이력</b> 순이다. 아카이브가 먼저인 이유는 원장 행이 사라진
 * 뒤에는 복사할 원본이 없기 때문이다 — {@code INSERT ... SELECT} 가 0건을 넣고 조용히 성공한다.
 * 세 문장이 모두 한 트랜잭션 안에 있으므로 어느 하나가 실패하면 전부 되돌아간다.</p>
 * <p>Deletion runs <b>archive → delete → history</b>. The archive comes first because once the
 * ledger row is gone there is nothing left to copy — the {@code INSERT … SELECT} would insert zero
 * rows and succeed quietly. All three statements share one transaction, so any failure rolls back
 * all of them.</p>
 *
 * // source: biztalk_admin_12_c001_act.jsp, biztalk_admin_10_d001_act.jsp
 * // req: FR-SNDC-001…014, FR-SNDD-001…011, FR-SNDH-001…003, FR-AZ-D01…D05
 */
@Service
public class SenderNumberWriteService {

    private final SenderNumberMapper mapper;
    private final BarredNumbers barred;
    private final AuditService audit;
    private final Clock clock;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 발신번호 매퍼 / the sender-number mapper
     * @param barred 금지 번호 목록 / the barred-number list
     * @param audit  감사 서비스 / the audit service
     * @param clock  시각 공급자 — 감사 기록용 / the clock, for audit records
     */
    public SenderNumberWriteService(SenderNumberMapper mapper,
                                    BarredNumbers barred,
                                    AuditService audit,
                                    Clock clock) {
        this.mapper = mapper;
        this.barred = barred;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 발신번호를 등록한다. / Registers a sender number.
     *
     * <p>대상 이용기관은 <b>요청이 아니라 세션</b>이 결정한다(FR-SNDC-012, FR-AZ-D03). 레거시
     * 팝업은 부모 창의 {@code IS_CD} 를 받아 그대로 insert 에 넣었고, 그것은 조회 경로에만
     * 기록된 D-S3 의 쓰기 경로 쌍둥이다 — 인증된 사용자라면 누구나 임의 기관에 번호를 등록할 수
     * 있었다.</p>
     * <p>The target institution is decided by <b>the session, not the request</b> (FR-SNDC-012,
     * FR-AZ-D03). The legacy popup took the opener's {@code IS_CD} straight into the insert — the
     * write-path twin of D-S3, letting any authenticated user register a number against any
     * institution.</p>
     *
     * @param requestedInstitutionCode 요청에 담긴 이용기관 코드 / the requested institution code
     * @param registration             등록 내용 / the registration
     * @param sourceIp                 출처 IP / the source address
     * @return 등록된 발신번호 / the registered sender number
     */
    // source: biztalk_admin_12_c001_act.jsp — isValidDpNo(), KKB_DPNO_LDGR_C001, KKB_DPNO_HIS_C001
    // req: FR-SNDC-001, FR-SNDC-003, FR-SNDC-004, FR-SNDC-005, FR-SNDC-006, FR-SNDC-007,
    //      FR-SNDC-008, FR-SNDC-009, FR-SNDC-010, FR-SNDC-011, FR-SNDC-012, FR-AZ-D05
    @Transactional
    public SenderNumberRef register(String requestedInstitutionCode,
                                    SenderNumberRegistration registration,
                                    String sourceIp) {

        TenantContext.TenantPrincipal principal = requireOperator();
        String institutionCode = requireScopedInstitution(principal, requestedInstitutionCode);

        String number = validateNumber(registration.number());
        String description = validateOptionalText(registration.description(), "description", "설명",
                SenderNumberLimits.DESCRIPTION_MAX);
        String reason = validateReason(registration.reason());

        /*
          중복검사는 <b>기관을 가리지 않는다</b>(FR-SNDC-004, PM 결정 AMB-S03). 레거시는
          KKB_DPNO_LDGR_L001 을 재사용했고 그 조건에 IS_CD 가 함께 있어 요청 기관 자신의 번호만
          보았으므로, 같은 번호를 여러 기관이 나란히 가질 수 있었다(D-S9).

          살아 있는 행만 세므로 아카이브된 번호는 중복이 아니다 — FR-SNDD-008(삭제한 번호의
          재등록)이 특별 취급 없이 성립한다.

          The duplicate check is institution-blind (FR-SNDC-004, ruling AMB-S03). The legacy reused
          L001, whose predicate included IS_CD, so it saw only the requesting institution's rows and
          the same number could be held by several (D-S9). Only live rows are counted, so an archived
          number is not a duplicate and FR-SNDD-008 falls out with no special case.
        */
        if (mapper.countAnywhere(number) > 0) {
            throw new SenderNumberDuplicateException();
        }

        // 반환값을 확인한다. 레거시는 이력 insert 뒤에 <b>직전</b> 문장의 결과를 검사했으므로
        // (D-S7) 실패가 조용히 삼켜졌다. 0건 삽입은 SQL 오류가 아니다.
        // The row count is checked. The legacy tested the *previous* statement's result after the
        // history insert (D-S7), so failures were swallowed. A zero-row insert is not a SQL error.
        int inserted = mapper.insertLedger(new SenderNumberMapper.LedgerInsert(
                institutionCode, number, description, principal.email()));
        if (inserted != 1) {
            throw new IllegalStateException(
                    "sender-number insert affected " + inserted + " rows, expected 1");
        }

        writeHistory(institutionCode, number, SenderNumberAction.CREATE, reason, principal.email());

        record(principal, institutionCode, AuditEvent.ACTION_SENDER_NUMBER_REGISTER,
                "1건 등록 / 1 number registered; 사유 " + reason.length() + "자", sourceIp);

        return new SenderNumberRef(institutionCode, number);
    }

    /**
     * 선택한 발신번호를 삭제한다. / Deletes the selected sender numbers.
     *
     * <p><b>이 메서드가 이 스프린트의 이유다.</b> 레거시 삭제는 아무것도 지우지 않으면서 성공을
     * 보고했다 — 2025년 10월부터일 가능성이 높다(D-S1, 명세 §6.4). 그러므로 여기서 가장 중요한
     * 문장은 무언가를 하는 문장이 아니라 {@code rows == 0} 일 때 <b>예외를 던지는</b> 문장이다.</p>
     * <p><b>This method is why the sprint exists.</b> The legacy delete removed nothing and reported
     * success, probably since October 2025 (D-S1, specification §6.4). The most important line here is
     * therefore not one that does something but the one that <b>throws</b> when {@code rows == 0}.</p>
     *
     * <p>확인 화면이 열거한 집합과 여기서 지워지는 집합은 같다(FR-SNDD-009). 그것이 성립하는
     * 이유는 요청이 "몇 건" 이 아니라 <b>어느 것</b>을 담고 있고, 서버가 그 각각을 다시 판정하기
     * 때문이다 — 조작된 목록도 세션 범위에서 걸러진다(위협 T-T8).</p>
     * <p>The set the confirmation enumerated and the set deleted here are the same (FR-SNDD-009),
     * because the request carries <b>which</b> rather than how many and the server re-decides each one
     * — a crafted list is filtered by session scope (threat T-T8).</p>
     *
     * @param deletion 삭제 내용 / the deletion
     * @param sourceIp 출처 IP / the source address
     * @return 삭제된 건수 / the number of rows deleted
     */
    // source: biztalk_admin_10_d001_act.jsp — the loop, its putAll(input) history and its unchecked result
    // req: FR-SNDD-001, FR-SNDD-002, FR-SNDD-004, FR-SNDD-005, FR-SNDD-006, FR-SNDD-009,
    //      FR-SNDH-003, FR-AZ-D04, FR-AZ-D05, NFR-OPS-D01, NFR-OPS-D02
    @Transactional
    public int delete(SenderNumberDeletion deletion, String sourceIp) {

        TenantContext.TenantPrincipal principal = requireOperator();
        String reason = validateReason(deletion.reason());
        Set<SenderNumberRef> targets = validateTargets(deletion.refs());

        for (SenderNumberRef ref : targets) {
            // 각 대상의 기관을 <b>따로</b> 판정한다. 목록에서 온 값이라도 세션 권한으로 다시
            // 판정해야 한다 — ref 는 식별자이고 인가 수단이 아니다(T-T8, FR-AZ-D03).
            // Each target's institution is decided separately: a value that came from the list is
            // still re-decided against session entitlements — a ref is an identifier, not a
            // capability (T-T8, FR-AZ-D03).
            String institutionCode = requireScopedInstitution(principal, ref.institutionCode());

            /*
              아카이브가 먼저다. 원장 행이 사라진 뒤에는 복사할 원본이 없고, INSERT ... SELECT
              는 0건을 넣고 조용히 성공한다 — 그것이 정확히 D-S1 의 실패 방식이므로 순서를
              반대로 두면 결함을 다른 자리에 다시 만드는 셈이다(ADR-SND-017).

              The archive comes first: once the ledger row is gone there is nothing to copy and the
              INSERT … SELECT inserts zero rows and succeeds quietly — D-S1's failure mode exactly,
              so reversing the order would rebuild the defect elsewhere (ADR-SND-017).
            */
            int archived = mapper.archive(institutionCode, ref.number(),
                    principal.email(), reason);
            if (archived == 0) {
                // 살아 있는 행이 없다. 레거시는 이 자리에서 아무 일도 하지 않고 성공을
                // 보고했다(D-S1). 예외이므로 트랜잭션 전체가 되돌아간다 — 다중 삭제에서
                // 하나가 사라져 있으면 나머지도 지우지 않는다(FR-SNDD-005).
                // No live row. The legacy did nothing here and reported success (D-S1). Throwing
                // rolls back the whole transaction: if one of a multi-delete has vanished, none of
                // the others is deleted either (FR-SNDD-005).
                throw new SenderNumberNotLiveException();
            }

            int deleted = mapper.deleteLive(institutionCode, ref.number());
            if (deleted == 0) {
                throw new SenderNumberNotLiveException();
            }
            if (deleted > 1) {
                // 하나를 지목했는데 여럿이 지워졌다. 전역 유일 제약(FR-SNDC-004)이 있는
                // 스키마에서는 일어날 수 없으므로, 일어났다면 제약이 없거나 유효하지 않다.
                // One row was named and several were removed. Impossible under the global
                // uniqueness constraint (FR-SNDC-004), so if it happens the constraint is absent
                // or ineffective.
                throw new IllegalStateException(
                        "delete affected " + deleted + " rows for one reference; "
                                + "the uniqueness constraint (FR-SNDC-004) is not in force");
            }

            /*
              이력은 <b>이 반복의 번호 하나</b>로 만든다. 레거시는 각 번호를 개별로 지우면서
              이력은 putAll(input) 로 만들었고, 그 input 의 DP_NO 는 클라이언트가 보낸 콤마
              목록이었다 — 3건을 지우면 목록 전체를 한 "번호" 로 암호화한 행이 3개 생겼다(D-S5).
              The history row is built from <b>this iteration's single number</b>. The legacy deleted
              each number individually but built history with putAll(input), whose DP_NO was still
              the client's comma-joined list: three deletions wrote three rows each encrypting the
              whole list as one "number" (D-S5).
            */
            writeHistory(institutionCode, ref.number(), SenderNumberAction.DELETE, reason,
                    principal.email());
        }

        // 감사 기록은 <b>건수</b>를 담고 번호는 담지 않는다(ADR-SND-019). 한 건을 지운 것과
        // 기관의 번호 전부를 지운 것은 같은 사건이 아니다(T-D4).
        // The audit record carries the <b>count</b> and no numbers (ADR-SND-019): deleting one and
        // deleting all of an institution's numbers are not the same event (T-D4).
        record(principal, scopeOf(targets), AuditEvent.ACTION_SENDER_NUMBER_DELETE,
                targets.size() + "건 삭제 / " + targets.size() + " numbers deleted", sourceIp);

        return targets.size();
    }

    /**
     * 이력을 한 건 쓰고 결과를 확인한다. / Writes one history record and checks the result.
     *
     * <p>이력 실패는 업무 실패다(FR-SNDC-008). 레거시는 이력 insert 뒤에 <b>직전</b> 문장의
     * 결과 객체를 검사했으므로({@code idoOut1}) 이력 쓰기 실패가 조용히 삼켜졌고, 원장만 바뀐
     * 상태로 커밋되었다 — 등록과 삭제 두 경로에 같은 복사-붙여넣기 결함이 있었다(D-S7).</p>
     * <p>A history failure is a business failure (FR-SNDC-008). The legacy inspected the
     * <b>previous</b> statement's result object after the history insert, so a failed history write
     * was swallowed and the ledger-only state committed — the same copy-paste defect in both the
     * register and delete paths (D-S7).</p>
     *
     * @param institutionCode 이용기관 코드 / the institution code
     * @param number          발신번호 한 건 / exactly one sender number
     * @param action          행위 코드 / the action code
     * @param reason          사유 / the reason
     * @param actorId         행위자 / the acting principal
     */
    // source: biztalk_admin_12_c001_act.jsp / _10_d001_act.jsp — if (idoOut1.getErrCode() ...)
    // req: FR-SNDC-008, FR-SNDD-004, FR-SNDH-001, FR-SNDH-003, NFR-OPS-D02
    private void writeHistory(String institutionCode,
                              String number,
                              SenderNumberAction action,
                              String reason,
                              String actorId) {
        int rows = mapper.insertHistory(new SenderNumberMapper.HistoryInsert(
                institutionCode, number, action.code(), reason, actorId));
        if (rows != 1) {
            throw new IllegalStateException(
                    "history insert affected " + rows + " rows, expected 1; "
                            + "the operation is rolled back rather than committed without a record");
        }
    }

    /**
     * 운영자임을 확인한다. / Confirms the operator role.
     *
     * <p>경로 규칙과 컨트롤러의 {@code @PreAuthorize} 에 이은 <b>세 번째</b> 검사다. 레거시의
     * 결함이 정확히 이 지점이었다 — 등록·수정은 브라우저에서 매니저 여부를 물어
     * {@code alert('권한 없음')} 을 띄웠고, <b>삭제에는 그 검사조차 없었다</b>(D-S2).</p>
     * <p>A <b>third</b> check after the routing rule and the controller annotation. The legacy defect
     * was exactly here: register and edit asked the browser whether the user was a manager and raised
     * an alert, and <b>delete had no check at all</b> (D-S2).</p>
     *
     * @return 인증된 운영자 / the authenticated operator
     */
    // source: biztalk_admin_10.js — btn_register/btn_update call 00_l003; btn_delete calls nothing
    // req: FR-AZ-D01, FR-AZ-D02, FR-AZ-D04, NFR-SEC-AUTHZ-D01
    private static TenantContext.TenantPrincipal requireOperator() {
        TenantContext.TenantPrincipal principal = TenantContext.require();
        if (!principal.operator()) {
            throw new AccessDeniedException("발신번호 관리는 운영자 전용입니다.");
        }
        return principal;
    }

    /**
     * 대상 이용기관을 세션 권한으로 판정한다. / Decides the target institution from session rights.
     *
     * <p>{@code effectiveInstitutionCode} 는 운영자에게는 요청값을, 이용기관 담당자에게는 자기
     * 기관을 돌려준다. 쓰기 경로에서는 <b>비어 있는 결과를 허용하지 않는다</b> — 조회는 기관
     * 미선택이 빈 목록으로 끝나지만, 쓰기는 대상이 없으면 무엇에 쓰는지 모르는 것이다.</p>
     * <p>{@code effectiveInstitutionCode} returns the requested value for an operator and the user's
     * own institution for a client-company user. On the write path an <b>empty result is not
     * allowed</b>: an unselected institution ends a search with an empty list, but a write with no
     * target does not know what it is writing to.</p>
     *
     * @param principal   행위자 / the acting principal
     * @param requested   요청에 담긴 코드 / the requested code
     * @return 확정된 이용기관 코드 / the resolved institution code
     */
    // source: biztalk_admin_12_c001_act.jsp — idoIn1.putAll(input) carrying the opener's IS_CD
    // req: FR-AZ-D03, FR-SNDC-012, NFR-SEC-TENANT-D01
    private String requireScopedInstitution(TenantContext.TenantPrincipal principal,
                                            String requested) {
        String scoped = principal.effectiveInstitutionCode(requested);
        if (scoped == null || scoped.isBlank()) {
            throw new SenderNumberValidationException("institution", "이용기관을 선택해 주세요.");
        }
        if (requested != null && !requested.isBlank() && !requested.equals(scoped)) {
            // 범위를 벗어난 기관을 지목한 시도. 데이터는 새지 않지만 시도는 남긴다 — 쓰기
            // 경로에서의 열거 시도는 조회에서의 그것보다 더 값진 증적이다.
            // An attempt to name an institution outside scope. Nothing leaks, but the attempt is
            // recorded: probing on a write path is more valuable evidence than on a read path.
            audit.record(new AuditEvent(
                    Instant.now(clock), principal.email(), null,
                    AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT, AuditEvent.Outcome.DENIED,
                    "발신번호 쓰기 요청이 범위를 벗어난 이용기관을 지목했다", sourceIpUnavailable(),
                    CorrelationId.current()));
            throw new AccessDeniedException("해당 이용기관에 대한 권한이 없습니다.");
        }
        return scoped;
    }

    /**
     * 감사 기록의 출처 IP 자리 표시. / Placeholder for the audit record's source address.
     *
     * <p>범위 위반은 검증 도중 발견되며 그 시점에 이 메서드는 요청 객체를 갖고 있지 않다.
     * 값을 꾸며내지 않고 {@code null} 을 남긴다 — 감사 기록에 <b>틀린</b> 출처가 남는 것은
     * 출처가 없는 것보다 나쁘다.</p>
     * <p>A scope violation is found during validation, where this method has no request object.
     * Rather than invent a value it records {@code null}: a <b>wrong</b> source address in an audit
     * record is worse than an absent one.</p>
     *
     * @return {@code null}
     */
    // req: NFR-OPS-AUDIT-D01
    private static String sourceIpUnavailable() {
        return null;
    }

    /**
     * 발신번호를 검증한다. / Validates the sender number.
     *
     * @param number 입력값 / the input
     * @return 정규화된 발신번호 / the normalised number
     */
    // source: biztalk_admin_12_c001_act.jsp — isValidDpNo(); biztalk_admin_12.js (vacuous checks)
    // req: FR-SNDC-003, FR-SNDC-005, FR-SNDC-006, FR-SNDC-010, FR-SNDC-013, NFR-USE-D02
    private String validateNumber(String number) {
        // 표시용 문자열이 식별자·입력값 자리에 들어왔는지 먼저 본다. 마스킹된 값이나 콤마
        // 목록은 형식 검증을 통과하지 못하지만, 그 경우의 메시지를 "숫자만" 으로 두면 무엇이
        // 잘못되었는지 오해하게 된다 — D-S1 의 흔적을 정확히 말해 주는 편이 낫다.
        // A display-formatted value in an input position is checked first. A masked value or a
        // comma list would fail the format rules anyway, but reporting "digits only" would
        // mis-describe it; naming D-S1's fingerprint is more useful.
        if (SenderNumberRef.looksLikeDisplayValue(number)) {
            throw new SenderNumberValidationException("number",
                    "발신번호에 표시용 문자(*, ,)가 포함되어 있습니다. 목록을 다시 조회하세요.");
        }
        SenderNumberValidator.Result result = SenderNumberValidator.validate(number, barred);
        if (!result.accepted()) {
            throw new SenderNumberValidationException("number", result.message());
        }
        return number.trim();
    }

    /**
     * 사유를 검증한다 — 등록과 삭제 모두 필수. / Validates the reason; mandatory for both paths.
     *
     * <p>등록의 사유는 PM 결정 AMB-S10 으로 필수가 되었다(FR-SNDC-011). 레거시 화면에도 칸은
     * 있었으나 클라이언트 검증이 존재하지 않는 요소를 검사했으므로(D-S11) 빈 값이 저장되었다.
     * 소유 인증이 없는 상태에서(RESIDUAL-S01) 사유는 운영자가 그 번호를 주장한 유일한 근거
     * 기록이다. 삭제의 사유는 처음부터 필수였다(FR-SNDD-006).</p>
     * <p>The registration reason became mandatory by PM ruling AMB-S10 (FR-SNDC-011): the legacy
     * screen had the field but its client validation tested non-existent elements (D-S11), so empty
     * values were stored. With no ownership verification (RESIDUAL-S01) the reason is the only record
     * of the operator's basis for claiming the number. The deletion reason was always mandatory
     * (FR-SNDD-006).</p>
     *
     * @param reason 입력값 / the input
     * @return 정규화된 사유 / the normalised reason
     */
    // source: biztalk_admin_12_view.jsp / _13_view.jsp — REASON, maxlength=100, unenforced
    // req: FR-SNDC-011, FR-SNDD-006, FR-SNDC-007, NFR-USE-D02
    private static String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SenderNumberValidationException("reason", "사유를 입력해 주세요.");
        }
        String value = reason.trim();
        if (value.length() > SenderNumberLimits.REASON_MAX) {
            throw new SenderNumberValidationException("reason",
                    "사유는 " + SenderNumberLimits.REASON_MAX + "자를 넘을 수 없습니다.");
        }
        return value;
    }

    /**
     * 선택 입력 문자열의 길이를 검증한다. / Validates an optional text field's length.
     *
     * @param value     입력값 / the input
     * @param field     계약상의 필드 이름 / the contract field name
     * @param label     화면의 한국어 이름 / the Korean label
     * @param maxLength 최대 길이 / the maximum length
     * @return 정규화된 값 — 빈 값은 {@code null} / the normalised value; blank becomes {@code null}
     */
    // source: WSVC.biztalk_admin_12_c001 — no length declared for DSCP (D-S15)
    // req: FR-SNDC-007, NFR-USE-D02
    private static String validateOptionalText(String value,
                                               String field,
                                               String label,
                                               int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new SenderNumberValidationException(field,
                    label + "은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }

    /**
     * 삭제 대상 집합을 검증한다. / Validates the set of delete targets.
     *
     * <p>중복을 제거한다. 같은 행을 두 번 담은 요청은 두 번째에 살아 있는 행을 찾지 못해 전체가
     * 실패하는데, 그것은 <b>요청의 오류를 데이터의 오류로 보고</b>하는 것이다. 집합으로 만들면
     * 확인 화면이 열거한 <b>서로 다른</b> 번호들과 정확히 대응한다(FR-SNDD-009).</p>
     * <p>Duplicates are removed. A request naming the same row twice would fail on the second pass
     * for want of a live row, which <b>reports a request error as a data error</b>. As a set it
     * corresponds exactly to the <b>distinct</b> numbers the confirmation enumerated
     * (FR-SNDD-009).</p>
     *
     * @param refs 요청에 담긴 대상 / the requested targets
     * @return 중복 없는 대상 집합 / the distinct targets
     */
    // req: FR-SNDD-005, FR-SNDD-009, NFR-PERF-D03
    private static Set<SenderNumberRef> validateTargets(List<SenderNumberRef> refs) {
        if (refs == null || refs.isEmpty()) {
            // 화면은 선택이 없으면 버튼을 비활성으로 둔다(FR-SNDD-010). 여기까지 왔다면
            // 화면을 거치지 않은 요청이며, 그때도 조용히 0건 성공이 되어서는 안 된다.
            // The screen disables the control with no selection (FR-SNDD-010). Reaching here means
            // the request bypassed the screen — and even then it must not become a quiet zero-row
            // success.
            throw new SenderNumberValidationException("refs", "삭제할 발신번호를 선택해 주세요.");
        }
        Set<SenderNumberRef> distinct = new LinkedHashSet<>(refs);
        if (distinct.size() > SenderNumberLimits.DELETE_BATCH_MAX) {
            throw new SenderNumberValidationException("refs",
                    "한 번에 삭제할 수 있는 발신번호는 "
                            + SenderNumberLimits.DELETE_BATCH_MAX + "건까지입니다.");
        }
        return distinct;
    }

    /**
     * 감사 기록에 담을 기관 범위를 만든다. / Builds the institution scope for the audit record.
     *
     * <p>운영자는 여러 기관의 번호를 한 번에 선택할 수 없다 — 화면이 기관을 바꿀 때 선택을
     * 비우기 때문이다(FR-SNDD-011). 그래도 <b>가정하지 않고</b> 실제 값을 본다: 요청이 화면을
     * 거치지 않았을 수도 있고, 그 경우 감사 기록이 사실과 달라지면 안 된다.</p>
     * <p>An operator cannot select numbers from several institutions at once, because the screen
     * clears the selection when the institution changes (FR-SNDD-011). The actual values are still
     * inspected rather than <b>assumed</b>: a request may not have come through the screen, and the
     * audit record must not then disagree with what happened.</p>
     *
     * @param targets 대상 집합 / the targets
     * @return 단일 기관이면 그 코드, 여러 기관이면 열거 / the code, or an enumeration
     */
    // req: FR-AZ-D05, NFR-OPS-AUDIT-D01
    private static String scopeOf(Set<SenderNumberRef> targets) {
        Set<String> codes = new LinkedHashSet<>();
        for (SenderNumberRef ref : targets) {
            codes.add(ref.institutionCode());
        }
        return codes.size() == 1 ? codes.iterator().next() : String.join(",", codes);
    }

    /**
     * 상태 변경 사건을 기록한다. / Records a state-changing event.
     *
     * <p>{@code AuditService.record} 는 {@code REQUIRES_NEW} 이므로 업무 트랜잭션이 롤백되어도
     * 기록은 남는다 — 시도되었다는 증적이 실패와 함께 사라져서는 안 된다(위협 T-R3).</p>
     * <p>{@code AuditService.record} runs {@code REQUIRES_NEW}, so the record survives a rollback:
     * evidence that something was attempted must not vanish with the attempt (threat T-R3).</p>
     *
     * @param principal       행위자 / the acting principal
     * @param institutionCode 대상 이용기관 / the target institution
     * @param action          행위 코드 / the action code
     * @param detail          비민감 부가정보 — 발신번호는 담지 않는다 / non-sensitive detail; never a number
     * @param sourceIp        출처 IP / the source address
     */
    // req: FR-AZ-D05, NFR-OPS-AUDIT-D01, ADR-SND-019
    private void record(TenantContext.TenantPrincipal principal,
                        String institutionCode,
                        String action,
                        String detail,
                        String sourceIp) {
        audit.record(new AuditEvent(
                Instant.now(clock),
                principal.email(),
                institutionCode,
                action,
                AuditEvent.Outcome.OK,
                detail,
                sourceIp,
                CorrelationId.current()));
    }
}
