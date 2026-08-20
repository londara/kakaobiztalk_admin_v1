package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper;
import com.webcash.iris.biztalk.infra.db.InstitutionAdminMapper.InstitutionUpdate;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link InstitutionWriteService} 단위 테스트 — 결함 D-I2/D-I6/D-I19/D-I20 회귀.
 * Unit tests for {@link InstitutionWriteService} — D-I2, D-I6, D-I19, D-I20 regressions.
 *
 * <p>이 슬라이스에서 가장 위험한 경로를 다룬다. 레거시 저장 액션은 요청을 그대로 IDO 에
 * 넘기는 UPSERT 였고 검증도 인가도 없었다 — 그래서 이 테스트들의 대부분은 "무엇을 하는가"
 * 가 아니라 <b>"무엇을 거절하는가"</b> 를 단언한다.</p>
 * <p>This covers the slice's most dangerous path. The legacy save action was an upsert that passed
 * the request straight into the IDO with no validation and no authorization, which is why most of
 * these cases assert <b>what is refused</b> rather than what is done.</p>
 *
 * // source: biztalk_admin_01_c001_act.jsp, biztalk_admin_01.js — fn_save()
 * // req: FR-INSTC-002…016, FR-ATK-001, FR-ATK-004, FR-ATK-005, FR-AZ-I01, FR-AZ-I02, FR-AZ-I04
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstitutionWriteServiceTest {

    private static final String CODE = "K00001";
    private static final String OPERATOR = "op@webcash.co.kr";

    /**
     * 합성된 인증키 표본 — 레거시 키와 같은 모양(20자 영숫자)이되 실제 값이 아니다.
     * A synthetic sample key: the shape of a legacy key (20 alphanumerics), not a real value.
     *
     * <p>여기에는 운영 화면에서 관찰된 값이 그대로 들어 있었다. 마스킹을 시험하는 데 실제
     * 자격증명은 필요하지 않으며, 저장소에 두면 그것 자체가 노출 경로다 — gitleaks L1 훅도
     * 이 값을 시크릿으로 탐지했다(SI2a-01, NFR-SEC-CRED-I01).</p>
     * <p>This held a value observed on a live screen. Testing the mask needs no real credential, and
     * keeping one in the repository is itself an exposure path — the gitleaks L1 hook flagged it as a
     * secret (SI2a-01, NFR-SEC-CRED-I01).</p>
     */
    private static final String RAW_KEY = "SAMPLEsampleSAMPLE01";

    @Mock private InstitutionAdminMapper mapper;
    @Mock private InstitutionService reader;
    @Mock private AuditService audit;

    private AtkGenerator keys;
    private InstitutionWriteService service;

    @BeforeEach
    void setUp() {
        keys = new AtkGenerator();
        service = new InstitutionWriteService(mapper, reader, keys, audit,
                Clock.fixed(Instant.parse("2026-08-20T01:30:00Z"), ZoneOffset.UTC));
        TenantContext.set(new TenantContext.TenantPrincipal(OPERATOR, null, true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static InstitutionRow row(String name, String status, String description) {
        return new InstitutionRow(CODE, name, "COOCON_Business1", "1234567890",
                "****************LE01", status, InstitutionStatus.labelOf(status),
                description, "20210401120000", "20260721133000");
    }

    private static InstitutionEdit edit() {
        return new InstitutionEdit("쿠콘_마이데이터사업1본부", "COOCON_Business1",
                "1234567890", "Y", "설명");
    }

    private void existing(InstitutionRow before, InstitutionRow after) {
        when(reader.findByCode(CODE)).thenReturn(before, after);
        when(mapper.update(any())).thenReturn(1);
    }

    // ── 정상 경로 / the happy path ────────────────────────────────────────────

    @Test
    @DisplayName("세션의 신원을 최종수정자로 기록한다 / records the session identity as the modifier")
        // req: FR-INSTC-007, FR-INSTC-012, TC-I002-16, TC-I002-27
    void takesActorFromSession() {
        existing(row("이전", "Y", "설명"), row("새이름", "Y", "설명"));

        service.update(CODE, edit(), "10.0.0.1");

        ArgumentCaptor<InstitutionUpdate> command = ArgumentCaptor.forClass(InstitutionUpdate.class);
        verify(mapper).update(command.capture());

        // 레거시도 세션에서 가져왔으나 성명을 따로 넣었다. 포털의 신원은 이메일 하나이므로
        // LSED_ID 와 LSED_NM 에 같은 값이 들어간다 — 이전 편집자의 이름이 새 편집자의 ID
        // 옆에 남는 것보다 낫다(FR-INSTC-012).
        // The legacy also read the session but wrote a separate 성명. The portal's identity is the
        // email alone, so both columns receive it — better than leaving a previous editor's name
        // beside the new editor's id (FR-INSTC-012).
        assertThat(command.getValue().actorId()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("경로의 기관코드만 대상이 된다 / only the path's code identifies the target")
        // req: FR-INSTC-002, TC-I002-29
    void targetsThePathCode() {
        existing(row("이전", "Y", null), row("새이름", "Y", null));

        service.update(CODE, edit(), "10.0.0.1");

        ArgumentCaptor<InstitutionUpdate> command = ArgumentCaptor.forClass(InstitutionUpdate.class);
        verify(mapper).update(command.capture());
        assertThat(command.getValue().code()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("저장 후 다시 읽은 행을 반환한다 / returns the row as re-read after the write")
        // req: FR-INSTC-006, FR-INSTC-013, ADR-INST-017
    void returnsTheStoredRow() {
        InstitutionRow after = row("새이름", "Y", "설명");
        existing(row("이전", "Y", "설명"), after);

        // LAST_AMDT 는 데이터베이스가 쓴다(ADR-INST-017). 요청 값을 되돌려주면 화면이 실제
        // 저장된 수정일시를 알 수 없다.
        // LAST_AMDT is written by the database (ADR-INST-017); echoing the request would leave the
        // screen without the stored modification time.
        assertThat(service.update(CODE, edit(), "10.0.0.1")).isSameAs(after);
    }

    // ── 인가 / authorization ─────────────────────────────────────────────────

    @Test
    @DisplayName("운영자가 아니면 아무것도 쓰지 않는다 / a non-operator writes nothing")
        // req: FR-AZ-I01, FR-AZ-I02, TC-I002-12, D-I2
    void refusesNonOperator() {
        TenantContext.set(new TenantContext.TenantPrincipal("user@client.co.kr", "K00002", false));

        assertThatThrownBy(() -> service.update(CODE, edit(), "10.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);

        // 레거시는 서비스 수준 검사가 전혀 없어, 화면을 우회한 호출을 아무도 막지 않았다.
        // The legacy had no service-level check at all, so a call bypassing the screen met nothing.
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("운영자가 아니면 키를 재발급하지 못한다 / a non-operator cannot rotate a key")
        // req: FR-AZ-I01, FR-ATK-005
    void refusesNonOperatorRotation() {
        TenantContext.set(new TenantContext.TenantPrincipal("user@client.co.kr", "K00002", false));

        assertThatThrownBy(() -> service.rotateAuthKey(CODE, "10.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(mapper);
    }

    // ── 검증 / validation (D-I19) ────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            " | COOCON | 1234567890 | Y | name",
            "쿠콘 |  | 1234567890 | Y | englishName",
            "쿠콘 | COOCON | 12345 | Y | businessNumber",
            "쿠콘 | COOCON | 12345abcde | Y | businessNumber",
            "쿠콘 | COOCON | 1234567890 | D | status",
            "쿠콘 | COOCON | 1234567890 | X | status",
    })
    @DisplayName("규칙 위반은 필드 이름과 함께 거절된다 / a broken rule is refused with its field")
        // req: FR-INSTC-003, FR-INSTC-009, FR-INSTC-014, FR-INSTC-015, TC-I002-07…09, D-I19
    void refusesInvalidInput(String name, String englishName, String brno, String status,
                             String expectedField) {

        InstitutionEdit invalid = new InstitutionEdit(name, englishName, brno, status, null);

        assertThatThrownBy(() -> service.update(CODE, invalid, "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class)
                .extracting(e -> ((InstitutionValidationException) e).field())
                .isEqualTo(expectedField);

        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("논리 삭제 상태는 수정 폼으로 도달할 수 없다 / 'D' is unreachable from the edit form")
        // req: FR-INSTC-015, TC-I002-28, TM-I023
    void refusesDeletedStatus() {
        InstitutionEdit deleting = new InstitutionEdit("쿠콘", "COOCON", "1234567890", "D", null);

        // 'D' 를 받아들이면 확인도, 의존 데이터 미리보기도, 삭제 감사 기록도 없는 삭제가 된다.
        // Accepting 'D' would be a delete with no confirmation, no dependent-record preview and no
        // deletion audit entry.
        assertThatThrownBy(() -> service.update(CODE, deleting, "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class);
        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("설명 길이 상한을 넘으면 거절한다 / a description over the limit is refused")
        // req: FR-INSTC-014
    void refusesOverlongDescription() {
        InstitutionEdit tooLong = new InstitutionEdit("쿠콘", "COOCON", "1234567890", "Y",
                "가".repeat(InstitutionLimits.DESCRIPTION_MAX + 1));

        assertThatThrownBy(() -> service.update(CODE, tooLong, "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class)
                .extracting(e -> ((InstitutionValidationException) e).field())
                .isEqualTo("description");
    }

    @ParameterizedTest
    @CsvSource({"K0000", "K000012", "A00001", "k00001", "'      '"})
    @DisplayName("기관코드 형식이 아니면 거절한다 / a malformed 기관코드 is refused")
        // req: FR-INSTC-003, FR-INSTC-014, TC-I002-08, D-I12, D-I19
    void refusesMalformedCode(String code) {
        assertThatThrownBy(() -> service.update(code, edit(), "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class)
                .extracting(e -> ((InstitutionValidationException) e).field())
                .isEqualTo("code");

        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("수정하지 않은 사업자등록번호도 검사한다 / an untouched 사업자등록번호 is validated too")
        // req: FR-INSTC-016, TC-I002-23, AMB-I12, RISK-I14
    void validatesUnchangedBusinessNumber() {
        // PM 결정 AMB-I12. 의도된 동작 변경이며 레거시는 이 저장을 받아들였다 — 사용여부만
        // 바꾸려는 요청이라도 행 전체가 유효해야 한다.
        // PM ruling AMB-I12. A deliberate behavioural change: the legacy accepted this save. Even a
        // request that only changes 사용여부 requires the whole row to be valid.
        InstitutionEdit statusOnly = new InstitutionEdit("쿠콘", "COOCON", "123456789012", "N", null);

        assertThatThrownBy(() -> service.update(CODE, statusOnly, "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class)
                .extracting(e -> ((InstitutionValidationException) e).field())
                .isEqualTo("businessNumber");
    }

    // ── 대상 없음 / the missing target (D-I6) ────────────────────────────────

    @Test
    @DisplayName("갱신된 행이 없으면 등록으로 바뀌지 않는다 / zero rows does not become a create")
        // req: FR-INSTC-004, TC-I002-02, D-I6
    void doesNotInsertWhenTargetVanished() {
        when(reader.findByCode(CODE)).thenReturn(row("이전", "Y", null));
        when(mapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(CODE, edit(), "10.0.0.1"))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    @Test
    @DisplayName("없는 기관은 조회 단계에서 거절된다 / an absent institution is refused at the read")
        // req: FR-INSTC-004, ADR-INST-014, TC-I002-30
    void refusesAbsentInstitution() {
        when(reader.findByCode(CODE)).thenThrow(new InstitutionNotFoundException(CODE));

        assertThatThrownBy(() -> service.update(CODE, edit(), "10.0.0.1"))
                .isInstanceOf(InstitutionNotFoundException.class);

        verify(mapper, never()).update(any());
    }

    // ── 감사 / audit ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("바뀐 필드의 before/after 를 기록한다 / records before/after for changed fields")
        // req: FR-AZ-I04, NFR-OPS-AUDIT-I01, TC-I002-17
    void auditsBeforeAndAfter() {
        existing(row("이전이름", "Y", "설명"), row("새이름", "N", "설명"));

        service.update(CODE, new InstitutionEdit("새이름", "COOCON_Business1", "1234567890",
                "N", "설명"), "10.0.0.1");

        AuditEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditEvent.ACTION_INSTITUTION_UPDATE);
        assertThat(event.actor()).isEqualTo(OPERATOR);
        assertThat(event.targetAccount()).isEqualTo(CODE);
        assertThat(event.detail())
                .contains("기관명: 이전이름 -> 새이름")
                .contains("사용여부: Y -> N")
                // 바뀌지 않은 필드는 담지 않는다 — 무엇이 실제로 변했는지 가려진다.
                // Unchanged fields are omitted: including them obscures what actually changed.
                .doesNotContain("영문명")
                .doesNotContain("사업자등록번호");
    }

    @Test
    @DisplayName("설명은 변경 사실만 기록한다 / the description records only that it changed")
        // req: FR-AZ-I04, NFR-OPS-AUDIT-I01
    void auditsDescriptionWithoutItsValue() {
        existing(row("쿠콘", "Y", "이전 설명"), row("쿠콘", "Y", "새 설명"));

        service.update(CODE, new InstitutionEdit("쿠콘", "COOCON_Business1", "1234567890",
                "Y", "새 설명"), "10.0.0.1");

        // 최대 650자다. 감사 저장소는 본문 보관소가 아니다.
        // It runs to 650 characters; the audit store is not a content repository.
        assertThat(capturedEvent().detail())
                .isEqualTo("설명 변경")
                .doesNotContain("새 설명");
    }

    // ── 인증키 재발급 / key rotation ─────────────────────────────────────────

    @Test
    @DisplayName("서버가 만든 키를 저장하고 한 번 반환한다 / stores a server-made key and returns it once")
        // req: FR-ATK-001, FR-ATK-005, FR-INSTC-011, TC-I002-24, D-I4
    void rotatesWithAServerGeneratedKey() {
        when(reader.findByCode(CODE)).thenReturn(row("쿠콘", "Y", null));
        when(mapper.rotateAuthKey(anyString(), anyString(), anyString())).thenReturn(1);

        String issued = service.rotateAuthKey(CODE, "10.0.0.1");

        assertThat(AtkGenerator.isWellFormed(issued)).isTrue();
        verify(mapper).rotateAuthKey(eq(CODE), eq(issued), eq(OPERATOR));

        // 재발급은 필드 수정 경로를 지나지 않는다 — 확인 시점에 즉시 확정된다(AMB-I13).
        // Rotation does not travel the field-update path: it commits on confirmation (AMB-I13).
        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("감사 기록에 키가 담기지 않는다 / no key material reaches the audit record")
        // req: FR-ATK-004, NFR-SEC-LOG-I01, TC-I002-28
    void neverAuditsKeyMaterial() {
        when(reader.findByCode(CODE)).thenReturn(row("쿠콘", "Y", null));
        when(mapper.rotateAuthKey(anyString(), anyString(), anyString())).thenReturn(1);

        String issued = service.rotateAuthKey(CODE, "10.0.0.1");

        AuditEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditEvent.ACTION_INSTITUTION_KEY_ROTATE);
        assertThat(event.targetAccount()).isEqualTo(CODE);
        // 새 키도, 이전 키도, 마스킹된 조각도 담지 않는다.
        // Neither the new key, nor the old one, nor a masked fragment.
        assertThat(event.detail()).doesNotContain(issued).doesNotContain(RAW_KEY).doesNotContain("*");
        assertThat(event.toString()).doesNotContain(issued);
    }

    @Test
    @DisplayName("없는 기관의 키는 재발급되지 않는다 / no rotation for an absent institution")
        // req: FR-ATK-005, ADR-INST-014
    void refusesRotationForAbsentInstitution() {
        when(reader.findByCode(CODE)).thenThrow(new InstitutionNotFoundException(CODE));

        assertThatThrownBy(() -> service.rotateAuthKey(CODE, "10.0.0.1"))
                .isInstanceOf(InstitutionNotFoundException.class);

        verify(mapper, never()).rotateAuthKey(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("바뀐 것이 없으면 그 사실을 기록한다 / records that nothing changed")
        // req: FR-AZ-I04, NFR-OPS-AUDIT-I01
    void auditsNoChange() {
        InstitutionRow unchanged = row("쿠콘_마이데이터사업1본부", "Y", "설명");
        existing(unchanged, unchanged);

        service.update(CODE, edit(), "10.0.0.1");

        // "변경 없음" 도 기록한다. 저장 버튼을 눌렀다는 사실 자체가 감사 대상이며, 기록이
        // 없으면 사후에 "그 시각에 누가 이 화면을 열고 저장했는가" 를 답할 수 없다.
        // A no-op save is still recorded: pressing 저장 is itself the audited event, and without the
        // record there is no way to answer who opened and saved this screen at that time.
        assertThat(capturedEvent().detail()).isEqualTo("변경 없음 / no field changed");
    }

    @Test
    @DisplayName("설명은 비워 둘 수 있다 / the description may be empty")
        // req: FR-INSTC-001, FR-INSTC-014
    void acceptsNullDescription() {
        existing(row("쿠콘", "Y", "설명"), row("쿠콘", "Y", null));

        // 레거시 폼에서도 설명은 필수가 아니었다(별표가 없다). 필수로 만들면 기존 행 중
        // 설명이 빈 것은 저장할 수 없게 된다.
        // The legacy form did not mark 설명 required either. Making it required would leave existing
        // rows with an empty description unsavable.
        service.update(CODE, new InstitutionEdit("쿠콘", "COOCON", "1234567890", "N", null),
                "10.0.0.1");

        verify(mapper).update(any());
    }

    @Test
    @DisplayName("기관코드가 null 이면 거절한다 / a null 기관코드 is refused")
        // req: FR-INSTC-003, FR-INSTC-014
    void refusesNullCode() {
        assertThatThrownBy(() -> service.update(null, edit(), "10.0.0.1"))
                .isInstanceOf(InstitutionValidationException.class)
                .extracting(e -> ((InstitutionValidationException) e).field())
                .isEqualTo("code");
    }

    @Test
    @DisplayName("재발급 중 대상이 사라지면 예외다 / a target that vanishes mid-rotation raises")
        // req: FR-ATK-005, ADR-INST-014
    void refusesRotationWhenRowVanished() {
        when(reader.findByCode(CODE)).thenReturn(row("쿠콘", "Y", null));
        when(mapper.rotateAuthKey(anyString(), anyString(), anyString())).thenReturn(0);

        // 0행을 성공으로 처리하면 발급된 키가 화면에 표시되지만 저장되지는 않는다 —
        // 운영자는 고객사에 존재하지 않는 키를 전달하게 된다.
        // Treating zero rows as success would display an issued key that was never stored, and the
        // operator would hand a customer a key that does not exist.
        assertThatThrownBy(() -> service.rotateAuthKey(CODE, "10.0.0.1"))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    // ── 구조적 보장 / structural guarantees ──────────────────────────────────

    @Test
    @DisplayName("수정 명령에 인증키 필드가 없다 / the update command has no key field")
        // req: FR-INSTC-011, TM-I022, TC-I002-25
    void updateCommandCarriesNoKeyField() {
        /*
          이 테스트는 <b>타입의 형태</b>를 단언한다. 마스킹된 값이 자격증명으로 저장되는 사고는
          검사로 막는 것이 아니라 <b>담을 자리를 만들지 않아</b> 막는다. 누군가 편의를 위해
          이 레코드에 authKey 를 추가하면 그 순간 사고가 표현 가능해지고, 이 테스트가 그것을
          거절한다.

          This asserts the <b>shape of the type</b>. Storing a masked value as the credential is
          prevented by having nowhere to put it, not by a check. If someone adds authKey here for
          convenience the accident becomes representable again — and this test refuses it.
        */
        assertThat(Arrays.stream(InstitutionUpdate.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("authKey", "atk", "key")
                .containsExactly("code", "name", "englishName", "businessNumber",
                        "status", "description", "actorId");
    }

    @Test
    @DisplayName("수정 명령에 시각 필드가 없다 / the update command has no timestamp field")
        // req: FR-INSTC-013, ADR-INST-017
    void updateCommandCarriesNoTimestamp() {
        // LAST_AMDT 는 SQL 안에서 데이터베이스 시계로 만든다. 애플리케이션이 값을 넘길 수
        // 있으면 UTC Clock 이 쓰일 여지가 생기고, 그 순간 레거시가 같은 컬럼에 남긴 값보다
        // 9시간 뒤처진 행이 만들어진다.
        // LAST_AMDT is produced by the database clock inside the SQL. If the application could
        // supply it, the UTC clock would be available for use — and the row would land nine hours
        // behind the legacy's rows in the same column.
        assertThat(Arrays.stream(InstitutionUpdate.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("timestamp", "lastModifiedAt", "lastAmdt");
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        return captor.getValue();
    }
}
