package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.logging.CorrelationId;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용기관 쓰기 서비스 — 수정과 인증키 재발급. / 이용기관 write service: edit and key rotation.
 *
 * <p>이 클래스에 있는 모든 것이 데이터를 바꾼다. 그것이 {@link InstitutionService} 와 분리된
 * 이유다 — 조회와 쓰기가 한 클래스에 있으면 트랜잭션 성격({@code readOnly})과 감사 의무가
 * 메서드마다 달라지고, 그 차이는 읽는 사람이 매번 확인해야 하는 것이 된다.</p>
 * <p>Everything here mutates, which is why it is separate from {@link InstitutionService}: mixing
 * reads and writes in one class makes the transaction character and the audit obligation vary per
 * method, and that difference then has to be re-checked by every reader.</p>
 *
 * <h2>레거시가 이 자리에서 한 일 / what the legacy did here</h2>
 * <p>{@code biztalk_admin_01_c001_act.jsp} 는 요청을 그대로 IDO 에 넘기고
 * ({@code idoInBiztalkIs.putAll(input)}) 세션에서 가져온 행위자 네 필드만 덧붙인 뒤
 * <b>UPSERT</b> 를 실행했다. 검증은 없었고(D-I19), 인가는 {@code login=Y} 하나였으며(D-I2),
 * 캐시 갱신 실패는 {@code catch(Throwable)} 안에서 삼켜졌다(D-I17).</p>
 * <p>The legacy action passed the request straight into the IDO, added four session-derived actor
 * fields and ran an <b>upsert</b>. No validation (D-I19), authorization was a login check alone
 * (D-I2), and a cache-refresh failure was swallowed inside {@code catch(Throwable)} (D-I17).</p>
 *
 * <h2>캐시 갱신이 없는 이유 / why there is no cache refresh</h2>
 * <p>레거시가 갱신한 {@code FINInstitution} 캐시는 IRIS_ADMIN <b>프로세스 안</b>에 있다. 별개
 * 프로세스인 포털은 그것에 닿을 수 없고, 포털 자신은 서버 측 기관 캐시를 두지 않는다. PM
 * 결정(AMB-I11)으로 FR-INSTC-008 은 "포털이 소유한 뷰의 무효화" 로 다시 쓰였고 그 일은
 * 클라이언트 쿼리 캐시가 한다. 레거시 캐시의 지연은 RISK-I02·RISK-I13 으로 추적한다 —
 * 우리가 소유하지 않은 시스템으로 호출을 내보내는 대신(ADR-INST-016) 못 하는 일을 기록해
 * 둔다.</p>
 * <p>The cache the legacy refreshed lives <b>inside</b> the IRIS_ADMIN process. A separate process
 * cannot reach it, and the portal keeps no server-side institution cache of its own. PM ruling
 * AMB-I11 rewrote FR-INSTC-008 as invalidation of the view this system owns, which the client
 * query cache performs. The legacy cache's staleness is tracked as RISK-I02 / RISK-I13 rather than
 * papered over with a call into a system we do not own (ADR-INST-016).</p>
 *
 * // source: biztalk_admin_01_c001_act.jsp, biztalk_admin_01.js — fn_save()
 * // req: FR-INSTC-002…016, FR-ATK-001, FR-ATK-005, FR-AZ-I01, FR-AZ-I02, FR-AZ-I04
 */
@Service
public class InstitutionWriteService {

    private final InstitutionAdminMapper mapper;
    private final InstitutionService reader;
    private final AtkGenerator keys;
    private final AuditService audit;
    private final Clock clock;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper 이용기관 매퍼 / the institution mapper
     * @param reader 조회 서비스 — 마스킹된 표현의 유일한 출처 / the read service, sole source of the masked view
     * @param keys   인증키 발급기 / the key generator
     * @param audit  감사 서비스 / the audit service
     * @param clock  시각 공급자 — 감사 기록용 / the clock, for audit records
     */
    public InstitutionWriteService(InstitutionAdminMapper mapper,
                                   InstitutionService reader,
                                   AtkGenerator keys,
                                   AuditService audit,
                                   Clock clock) {
        this.mapper = mapper;
        this.reader = reader;
        this.keys = keys;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 이용기관을 수정한다. / Updates an institution.
     *
     * <p>수정 <b>전</b>의 값을 먼저 읽는다. 감사 기록에 before/after 를 남기려면 그것이
     * 필요하고(FR-AZ-I04), 대상이 존재하는지도 그 조회에서 판명된다. 갱신 행 수가 0이면 그
     * 사이에 삭제된 것이므로 예외다 — 0행을 성공으로 처리하면 "저장되었습니다" 라는 응답과
     * 아무 일도 일어나지 않은 데이터베이스가 공존한다.</p>
     * <p>The prior values are read first: the audit record needs before/after (FR-AZ-I04) and the
     * same read establishes that the target exists. Zero rows updated means it was deleted in
     * between and raises — treating zero as success would pair a "saved" message with a database
     * where nothing changed.</p>
     *
     * <p>저장 후 <b>다시 읽어</b> 반환한다. {@code LAST_AMDT} 는 데이터베이스가 쓰므로
     * (ADR-INST-017) 애플리케이션은 그 값을 알지 못한다 — 화면이 방금 저장한 행의 수정일시를
     * 보여주려면 저장된 것을 읽어야 한다. 요청 값을 그대로 되돌려주면 화면과 데이터베이스가
     * 어긋날 수 있고, 그 어긋남은 다음 조회에서야 드러난다.</p>
     * <p>The row is <b>re-read</b> after the write. {@code LAST_AMDT} is written by the database
     * (ADR-INST-017), so the application does not know its value; showing the just-saved row's
     * timestamp requires reading what was stored. Echoing the request back could leave the screen
     * disagreeing with the database, and the disagreement would surface only on the next search.</p>
     *
     * @param code     대상 기관코드 / the institution code
     * @param edit     수정 내용 / the requested changes
     * @param sourceIp 출처 IP / the source address
     * @return 수정된 이용기관 — 인증키는 마스킹된다 / the updated institution, key masked
     * @throws InstitutionValidationException 검증 실패 / on a validation failure
     * @throws InstitutionNotFoundException   대상이 없을 때 / when the target does not exist
     */
    // source: biztalk_admin_01_c001_act.jsp
    // req: FR-INSTC-002, FR-INSTC-003, FR-INSTC-006, FR-INSTC-007, FR-INSTC-012, FR-INSTC-013,
    //      FR-INSTC-014, FR-INSTC-015, FR-INSTC-016, FR-AZ-I04
    @Transactional
    public InstitutionRow update(String code, InstitutionEdit edit, String sourceIp) {
        TenantContext.TenantPrincipal principal = requireOperator();
        requireValidCode(code);
        validate(edit);

        InstitutionRow before = reader.findByCode(code);

        int rows = mapper.update(new InstitutionAdminMapper.InstitutionUpdate(
                code,
                edit.name(),
                edit.englishName(),
                edit.businessNumber(),
                edit.status(),
                edit.description(),
                principal.email()));

        if (rows == 0) {
            throw new InstitutionNotFoundException(code);
        }

        InstitutionRow after = reader.findByCode(code);

        record(principal, code, AuditEvent.ACTION_INSTITUTION_UPDATE, diff(before, edit), sourceIp);

        return after;
    }

    /**
     * 인증키를 재발급한다. / Rotates the 인증키.
     *
     * <p>확인한 시점에 <b>즉시 확정</b>된다 — 저장 버튼과 무관하다(FR-INSTC-011, PM 결정
     * AMB-I13). 레거시는 브라우저에서 만든 값을 폼에 담아 두었다가 저장할 때 기록했으므로
     * 닫기를 누르면 사라졌다. 즉시 확정하면 감사 기록과 실제 저장된 값이 어긋날 수 없다 —
     * 폐기된 재발급에 대한 감사 기록은 존재하지 않는 키를 가리키게 된다.</p>
     * <p>Committed <b>at once</b>, independent of the save button (FR-INSTC-011, PM ruling
     * AMB-I13). The legacy held a browser-generated value in the form until 저장, so 닫기 discarded
     * it. Committing immediately means the audit record cannot disagree with what is stored: a
     * record for a discarded rotation would name a key that never existed.</p>
     *
     * <p>새 키는 <b>한 번만</b> 반환된다. 서버는 그것을 다시 보여주지 않으며(FR-ATK-003 의
     * reveal 은 별개 조작이다) 로그에도 남기지 않는다(FR-ATK-004).</p>
     * <p>The new key is returned <b>once</b>. The server will not show it again — reveal
     * (FR-ATK-003) is a separate operation — and never logs it (FR-ATK-004).</p>
     *
     * @param code     대상 기관코드 / the institution code
     * @param sourceIp 출처 IP / the source address
     * @return 새 인증키 — 고객사에 전달하기 위한 일회성 평문 / the new key, disclosed once
     * @throws InstitutionNotFoundException 대상이 없을 때 / when the target does not exist
     */
    // source: biztalk_admin_01.js — btn_generate_code (Math.random, in the browser)
    // req: FR-ATK-001, FR-ATK-004, FR-ATK-005, FR-INSTC-011, FR-AZ-I04
    @Transactional
    public String rotateAuthKey(String code, String sourceIp) {
        TenantContext.TenantPrincipal principal = requireOperator();
        requireValidCode(code);

        // 존재 확인을 위한 조회다. 반환값의 인증키는 이미 마스킹되어 있으므로 이 경로에는
        // 평문 이전 키가 존재하지 않는다 — 감사 기록에 실수로 담길 값 자체가 없다.
        // A read to establish existence. Its key is already masked, so no plaintext previous key
        // exists on this path at all — there is nothing for an audit record to capture by mistake.
        reader.findByCode(code);

        String issued = keys.generate();
        int rows = mapper.rotateAuthKey(code, issued, principal.email());
        if (rows == 0) {
            throw new InstitutionNotFoundException(code);
        }

        // 감사 기록에는 사실만 남긴다. 키도, 마스킹된 조각도 담지 않는다 — 감사 저장소는
        // 보존 기간이 길고 접근 모델이 다르므로 자격증명의 2차 저장소가 되면 안 된다
        // (FR-ATK-004, ADR-INST-015).
        // The record carries the fact only — no key, not even a masked fragment. The audit store
        // has longer retention and a different access model, and must not become a secondary
        // credential repository (FR-ATK-004, ADR-INST-015).
        record(principal, code, AuditEvent.ACTION_INSTITUTION_KEY_ROTATE,
                "인증키 재발급 / key rotated", sourceIp);

        return issued;
    }

    /**
     * 운영자임을 확인하고 주체를 반환한다. / Confirms the operator role and returns the principal.
     *
     * <p>경로 규칙({@code /api/admin/**})과 컨트롤러의 {@code @PreAuthorize} 에 이어
     * <b>세 번째</b> 검사다. 과해 보이지만 레거시의 결함이 정확히 이 지점이었다 — 인가가
     * 라우팅과 화면에만 있어서 서비스를 직접 부르면 아무도 막지 않았다(D-I2). 서비스가 자기
     * 자신을 지키면 새 진입점이 생겨도 규칙이 함께 온다.</p>
     * <p>A <b>third</b> check after the routing rule and the controller's {@code @PreAuthorize}.
     * It looks redundant, but the legacy defect was exactly here: authorization existed in routing
     * and in the screen, so calling the service directly met no barrier (D-I2). A service that
     * guards itself carries the rule to every future entry point.</p>
     *
     * @return 인증된 운영자 / the authenticated operator
     */
    // req: FR-AZ-I01, FR-AZ-I02, NFR-SEC-AUTHZ-I01
    private static TenantContext.TenantPrincipal requireOperator() {
        TenantContext.TenantPrincipal principal = TenantContext.require();
        if (!principal.operator()) {
            // 사유를 구체적으로 남기지 않는다 — 구분해 주면 응답만으로 어떤 기관이 존재하는지
            // 추론할 수 있다(GlobalExceptionHandler 의 같은 판단).
            // The reason is not detailed: distinguishing cases would allow inference about which
            // institutions exist from responses alone (the same judgement GlobalExceptionHandler
            // makes).
            throw new AccessDeniedException("이용기관 관리는 운영자 전용입니다.");
        }
        return principal;
    }

    /**
     * 기관코드 형식을 검사한다. / Checks the 기관코드 format.
     *
     * <p>경로에서 온 값이라도 검사한다. 형식이 맞지 않는 코드는 데이터베이스에 닿기 전에
     * 걸러야 하며, 레거시에서 이 규칙은 브라우저에만 있었다(D-I19) — 그 때문에 그리드에
     * 스크립트 페이로드를 심는 경로가 열려 있었다(D-I12).</p>
     * <p>Checked even though it comes from the path: a malformed code should not reach the
     * database, and in the legacy this rule lived only in the browser (D-I19) — which is what left
     * a path open for planting a script payload in the grid (D-I12).</p>
     *
     * @param code 기관코드 / the institution code
     */
    // source: biztalk_admin_01.js — fn_save(): substring(0,2) != "K0"
    // req: FR-INSTC-003, FR-INSTC-014, D-I12, D-I19
    private static void requireValidCode(String code) {
        if (code == null || !code.matches(InstitutionLimits.CODE_REGEX)) {
            throw new InstitutionValidationException("code",
                    "이용기관코드 형식이 올바르지 않습니다. K0 으로 시작하는 6자여야 합니다.");
        }
    }

    /**
     * 수정 내용을 검증한다. / Validates the requested changes.
     *
     * <p>요청 레코드의 Bean Validation 과 <b>같은 상수</b>를 쓴다({@link InstitutionLimits}).
     * 두 벌의 규칙이 아니라 두 개의 진입점이며, 한쪽만 느슨해질 수 없다. HTTP 를 거치지 않는
     * 호출자에게는 이쪽이 유일한 방어선이다(FR-INSTC-003).</p>
     * <p>Uses the <b>same constants</b> as the request record's Bean Validation
     * ({@link InstitutionLimits}): two entry points, not two rule sets, so neither can drift
     * looser. For a caller that does not pass through HTTP this is the only barrier
     * (FR-INSTC-003).</p>
     *
     * @param edit 수정 내용 / the requested changes
     */
    // source: biztalk_admin_01.js — fn_save() alert chain
    // req: FR-INSTC-003, FR-INSTC-009, FR-INSTC-014, FR-INSTC-015, FR-INSTC-016, D-I19
    private static void validate(InstitutionEdit edit) {
        requireText(edit.name(), "name", "이용기관명", InstitutionLimits.NAME_MAX);
        requireText(edit.englishName(), "englishName", "이용기관영문명",
                InstitutionLimits.ENGLISH_NAME_MAX);

        // 사업자등록번호는 수정하지 않은 경우에도 검사한다(PM 결정 AMB-I12). 저장 요청은 행
        // 전체가 유효하다는 주장이므로, 손대지 않은 값이라도 규칙을 만족해야 한다. 의도된
        // 동작 변경이며 레거시는 이런 저장을 받아들였다(RISK-I14).
        // Validated even when unchanged (PM ruling AMB-I12): a save asserts the whole row is
        // valid, so an untouched value must satisfy the rule too. A deliberate behavioural change
        // — the legacy accepted such a save (RISK-I14).
        if (edit.businessNumber() == null
                || !edit.businessNumber().matches(InstitutionLimits.BUSINESS_NUMBER_REGEX)) {
            throw new InstitutionValidationException("businessNumber",
                    "사업자등록번호는 숫자 10자리여야 합니다.");
        }

        // 이 화면에서 도달 가능한 상태는 사용·미사용 둘뿐이다. 'D' 는 논리 삭제 표식이며
        // (ADR-INST-014), 수정 폼으로 그 값에 닿을 수 있다면 확인도, 의존 데이터 미리보기도,
        // 삭제 감사 기록도 없는 삭제가 된다(FR-INSTC-015, TM-I023).
        // Only 사용 and 미사용 are reachable here. 'D' is the logical-delete marker
        // (ADR-INST-014); reaching it through the edit form would be a delete with no
        // confirmation, no dependent-record preview and no deletion audit entry.
        InstitutionStatus status = InstitutionStatus.fromCode(edit.status());
        if (status != InstitutionStatus.ACTIVE && status != InstitutionStatus.SUSPENDED) {
            throw new InstitutionValidationException("status",
                    "사용 여부는 사용 또는 미사용이어야 합니다.");
        }

        if (edit.description() != null
                && edit.description().length() > InstitutionLimits.DESCRIPTION_MAX) {
            throw new InstitutionValidationException("description",
                    "설명은 " + InstitutionLimits.DESCRIPTION_MAX + "자를 넘을 수 없습니다.");
        }
    }

    /**
     * 필수 문자열을 검사한다. / Checks a required text field.
     *
     * @param value     값 / the value
     * @param field     계약상의 필드 이름 / the contract field name
     * @param label     화면에 쓰이는 한국어 이름 / the Korean label used on screen
     * @param maxLength 최대 길이 / the maximum length
     */
    // req: FR-INSTC-003, FR-INSTC-014
    private static void requireText(String value, String field, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InstitutionValidationException(field, label + "은(는) 필수입니다.");
        }
        if (value.length() > maxLength) {
            throw new InstitutionValidationException(field,
                    label + "은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
    }

    /**
     * 감사 기록에 담을 변경 요약을 만든다. / Builds the change summary for the audit record.
     *
     * <p>FR-AZ-I04 는 before/after 를 요구한다. <b>바뀐 필드만</b> 담는다 — 바뀌지 않은 값을
     * 함께 적으면 무엇이 실제로 변했는지 사후에 알아보기 어려워지고, 기록만 커진다.</p>
     * <p>FR-AZ-I04 requires before/after. Only <b>changed</b> fields are recorded: including
     * untouched values makes it harder to see afterwards what actually changed, and only grows the
     * row.</p>
     *
     * <p>설명은 <b>값을 담지 않고</b> 변경 사실만 남긴다. 최대 650자이며, 감사 저장소는 본문
     * 보관소가 아니다.</p>
     * <p>The description records only that it changed, <b>never its value</b>: it runs to 650
     * characters and the audit store is not a content repository.</p>
     *
     * @param before 수정 전 / the prior state
     * @param edit   수정 요청 / the requested changes
     * @return 비민감 변경 요약 / a non-sensitive change summary
     */
    // req: FR-AZ-I04, NFR-OPS-AUDIT-I01
    private static String diff(InstitutionRow before, InstitutionEdit edit) {
        List<String> changes = new ArrayList<>();
        appendChange(changes, "기관명", before.name(), edit.name());
        appendChange(changes, "영문명", before.englishName(), edit.englishName());
        appendChange(changes, "사업자등록번호", before.businessNumber(), edit.businessNumber());
        appendChange(changes, "사용여부", before.status(), edit.status());
        if (!Objects.equals(nullToEmpty(before.description()), nullToEmpty(edit.description()))) {
            changes.add("설명 변경");
        }
        return changes.isEmpty() ? "변경 없음 / no field changed" : String.join(", ", changes);
    }

    /**
     * 값이 바뀌었으면 요약에 추가한다. / Adds an entry when the value changed.
     *
     * @param changes 요약 목록 / the summary list
     * @param label   필드 라벨 / the field label
     * @param before  이전 값 / the prior value
     * @param after   새 값 / the new value
     */
    // req: FR-AZ-I04
    private static void appendChange(List<String> changes,
                                     String label,
                                     String before,
                                     String after) {
        if (!Objects.equals(nullToEmpty(before), nullToEmpty(after))) {
            changes.add(label + ": " + nullToEmpty(before) + " -> " + nullToEmpty(after));
        }
    }

    // req: FR-AZ-I04
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 상태 변경 사건을 기록한다. / Records a state-changing event.
     *
     * <p>{@code AuditService.record} 는 {@code REQUIRES_NEW} 이므로 업무 트랜잭션이 롤백되어도
     * 기록은 남는다. 시도되었다는 증적이 업무 실패와 함께 사라져서는 안 된다.</p>
     * <p>{@code AuditService.record} runs {@code REQUIRES_NEW}, so the record survives a rollback
     * of the business transaction: evidence that something was attempted must not vanish with the
     * attempt's failure.</p>
     *
     * @param principal 행위자 / the acting principal
     * @param code      대상 기관코드 / the target institution
     * @param action    행위 코드 / the action code
     * @param detail    비민감 부가정보 / non-sensitive detail
     * @param sourceIp  출처 IP / the source address
     */
    // req: FR-AZ-I04, NFR-OPS-AUDIT-I01
    private void record(TenantContext.TenantPrincipal principal,
                        String code,
                        String action,
                        String detail,
                        String sourceIp) {
        audit.record(new AuditEvent(
                Instant.now(clock),
                principal.email(),
                code,
                action,
                AuditEvent.Outcome.OK,
                detail,
                sourceIp,
                CorrelationId.current()));
    }
}
