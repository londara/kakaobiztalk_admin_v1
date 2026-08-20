package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.SenderNumberMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

/**
 * {@link SenderNumberWriteService} 검증. / Verification for {@link SenderNumberWriteService}.
 *
 * <p>DB 없이 실행된다 — 매퍼는 대역이다. 실제 SQL 은 {@code SenderNumberMapperIntegrationTest} 가
 * embedded PostgreSQL 로 검증한다. 여기서 검증하는 것은 <b>순서·검증·결과값 확인·감사</b>이며,
 * 그 넷이 이 슬라이스의 결함이 살던 곳이다 — D-S1(0건을 성공으로), D-S5(이력에 목록 전체),
 * D-S6(트랜잭션 없음), D-S7(직전 결과 검사), D-S9(기관별 중복검사).</p>
 * <p>Runs without a database; the mapper is a double, and the real SQL is verified by
 * {@code SenderNumberMapperIntegrationTest} against embedded PostgreSQL. What is verified here is
 * <b>ordering, validation, row-count checking and auditing</b> — the four places this slice's defects
 * lived.</p>
 *
 * // req: FR-SNDC-001…014, FR-SNDD-001…011, FR-SNDH-001…003, FR-AZ-D01…D05
 */
class SenderNumberWriteServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);
    private static final String IP = "10.1.2.3";
    private static final String CODE = "K0ABCD";
    private static final String NUMBER = "0212345678";
    private static final String REASON = "고객사 요청 / customer request";

    private SenderNumberMapper mapper;
    private AuditService audit;
    private SenderNumberWriteService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SenderNumberMapper.class);
        audit = Mockito.mock(AuditService.class);
        service = new SenderNumberWriteService(mapper, BarredNumbers.bundled(), audit, FIXED);
        given(mapper.insertLedger(any())).willReturn(1);
        given(mapper.insertHistory(any())).willReturn(1);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static void bindOperator() {
        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    private static void bindClientUser(String ownCode) {
        TenantContext.set(new TenantContext.TenantPrincipal("user@example.com", ownCode, false));
    }

    private static SenderNumberRegistration registration(String number) {
        return new SenderNumberRegistration(number, "대표번호", REASON);
    }

    private static SenderNumberRef ref(String code, String number) {
        return new SenderNumberRef(code, number);
    }

    // =========================================================================
    // 등록 / registration
    // =========================================================================

    @Nested
    @DisplayName("등록 / register")
    class Register {

        @Test
        @DisplayName("정상 등록은 원장과 이력을 한 번씩 쓴다 / writes the ledger and the history once each")
            // req: FR-SNDC-001, FR-SNDC-008, FR-SNDH-001
        void writesLedgerAndHistory() {
            bindOperator();

            SenderNumberRef result = service.register(CODE, registration(NUMBER), IP);

            assertThat(result).isEqualTo(ref(CODE, NUMBER));
            verify(mapper).insertLedger(new SenderNumberMapper.LedgerInsert(
                    CODE, NUMBER, "대표번호", "op@example.com"));
            verify(mapper).insertHistory(new SenderNumberMapper.HistoryInsert(
                    CODE, NUMBER, "C", REASON, "op@example.com"));
        }

        @Test
        @DisplayName("D-S9 회귀 — 다른 기관이 가진 번호를 거절한다 / rejects a number held by another institution")
            // req: FR-SNDC-004, CONST-BIZ-D01
        void rejectsGlobalDuplicate() {
            bindOperator();
            // countAnywhere 에는 기관 술어가 없다. 레거시는 KKB_DPNO_LDGR_L001 을 재사용해
            // IS_CD 술어를 함께 걸었으므로 요청 기관 자신의 번호만 보았다(D-S9).
            // countAnywhere carries no institution predicate; the legacy reused L001 with its IS_CD
            // predicate and so saw only the requesting institution's rows (D-S9).
            given(mapper.countAnywhere(NUMBER)).willReturn(1);

            assertThatThrownBy(() -> service.register(CODE, registration(NUMBER), IP))
                    .isInstanceOf(SenderNumberDuplicateException.class);

            verify(mapper, never()).insertLedger(any());
            verify(mapper, never()).insertHistory(any());
        }

        @Test
        @DisplayName("중복 판정은 기관을 가리지 않고 조회한다 / the duplicate lookup is institution-blind")
            // req: FR-SNDC-004, D-S9
        void duplicateLookupIsInstitutionBlind() {
            bindOperator();
            service.register(CODE, registration(NUMBER), IP);
            // 기관 코드를 인수로 받는 조회를 <b>쓰지 않는다</b>는 것이 요점이다.
            // The point is that no institution-scoped lookup is used for this decision.
            verify(mapper).countAnywhere(NUMBER);
            verify(mapper, never()).findOne(anyString(), anyString());
        }

        @Test
        @DisplayName("D-S13 회귀 — 숫자가 아니면 거절한다 / rejects a non-numeric number")
            // req: FR-SNDC-005
        void rejectsNonNumeric() {
            bindOperator();
            assertThatThrownBy(() -> service.register(CODE, registration("abcdefgh"), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("숫자");
            verify(mapper, never()).insertLedger(any());
        }

        @Test
        @DisplayName("D-S12 회귀 — 특수번호를 거절한다 / rejects a special number")
            // req: FR-SNDC-006
        void rejectsBarredNumber() {
            bindOperator();
            for (String barred : new String[]{"112", "114", "119", "1335"}) {
                assertThatThrownBy(() -> service.register(CODE, registration(barred), IP))
                        .as("%s must be refused", barred)
                        .isInstanceOf(SenderNumberValidationException.class);
            }
            verify(mapper, never()).insertLedger(any());
        }

        @Test
        @DisplayName("AMB-S10 — 사유가 없으면 거절한다 / refuses a registration with no reason")
            // req: FR-SNDC-011
        void refusesRegistrationWithoutReason() {
            bindOperator();
            // 레거시와의 <b>의도된 차이</b>다. 레거시 화면에도 칸은 있었으나 클라이언트 검증이
            // 존재하지 않는 요소를 검사했으므로(D-S11) 빈 값이 그대로 저장되었다. QA 는 이 거절을
            // parity 결함으로 보고하지 않아야 한다.
            // A <b>deliberate difference</b>: the legacy screen had the field but its client
            // validation tested non-existent elements (D-S11) so empty values were stored. QA must
            // not file this rejection as a parity defect.
            assertThatThrownBy(() -> service.register(
                    CODE, new SenderNumberRegistration(NUMBER, "대표번호", "  "), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("사유");
            verify(mapper, never()).insertLedger(any());
        }

        @Test
        @DisplayName("D-S15 회귀 — 과다 길이를 거절한다 / refuses over-length text")
            // req: FR-SNDC-007
        void refusesOverLengthText() {
            bindOperator();
            assertThatThrownBy(() -> service.register(
                    CODE, new SenderNumberRegistration(NUMBER, "가".repeat(201), REASON), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("설명");
            assertThatThrownBy(() -> service.register(
                    CODE, new SenderNumberRegistration(NUMBER, "대표번호", "가".repeat(101)), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("사유");
        }

        @Test
        @DisplayName("D-S7 회귀 — 이력 쓰기 실패는 등록을 실패시킨다 / a failed history write fails the registration")
            // req: FR-SNDC-008, NFR-OPS-D02
        void historyFailureFailsTheRegistration() {
            bindOperator();
            // 레거시는 이력 insert 뒤에 <b>직전</b> 문장의 결과 객체를 검사했으므로(D-S7) 이력
            // 실패가 조용히 삼켜지고 원장만 바뀐 상태로 커밋되었다.
            // The legacy inspected the *previous* statement's result after the history insert (D-S7),
            // so a history failure was swallowed and the ledger-only state committed.
            given(mapper.insertHistory(any())).willReturn(0);

            assertThatThrownBy(() -> service.register(CODE, registration(NUMBER), IP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("history");
        }

        @Test
        @DisplayName("행위자는 세션에서 온다 / the actor comes from the session")
            // req: FR-SNDC-009
        void actorComesFromTheSession() {
            bindOperator();
            service.register(CODE, registration(NUMBER), IP);

            ArgumentCaptor<SenderNumberMapper.LedgerInsert> captor =
                    ArgumentCaptor.forClass(SenderNumberMapper.LedgerInsert.class);
            verify(mapper).insertLedger(captor.capture());
            assertThat(captor.getValue().actorId()).isEqualTo("op@example.com");
        }

        @Test
        @DisplayName("D-S1 계열 — 표시용 값을 입력으로 받지 않는다 / a display value is refused as input")
            // req: FR-SND-007, FR-SNDC-005
        void refusesDisplayFormattedInput() {
            bindOperator();
            assertThatThrownBy(() -> service.register(CODE, registration("01********8"), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("표시용");
            assertThatThrownBy(() -> service.register(CODE, registration("0212345678,15881234"), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("표시용");
        }

        @Test
        @DisplayName("감사 기록에 발신번호를 담지 않는다 / no sender number in the audit record")
            // req: ADR-SND-019, NFR-OPS-AUDIT-D01
        void auditCarriesNoNumber() {
            bindOperator();
            service.register(CODE, registration(NUMBER), IP);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit).record(captor.capture());
            AuditEvent event = captor.getValue();

            assertThat(event.action()).isEqualTo(AuditEvent.ACTION_SENDER_NUMBER_REGISTER);
            assertThat(event.targetAccount()).isEqualTo(CODE);
            assertThat(event.actor()).isEqualTo("op@example.com");
            // 감사 저장소는 보존 기간이 길고 접근 모델이 다르다. 번호를 담으면 노출을 줄이려
            // 만든 통제가 2차 PII 저장소가 된다(T-I4).
            // The audit store has longer retention and a different access model; carrying the number
            // would turn a control meant to reduce exposure into a second PII repository (T-I4).
            assertThat(event.detail()).doesNotContain(NUMBER);
        }
    }

    // =========================================================================
    // 삭제 / deletion
    // =========================================================================

    @Nested
    @DisplayName("삭제 / delete")
    class Delete {

        @BeforeEach
        void archiveAndDeleteSucceed() {
            given(mapper.archive(anyString(), anyString(), anyString(), anyString())).willReturn(1);
            given(mapper.deleteLive(anyString(), anyString())).willReturn(1);
        }

        @Test
        @DisplayName("아카이브 → 삭제 → 이력 순으로 실행한다 / runs archive, then delete, then history")
            // req: FR-SNDD-001, ADR-SND-017
        void runsArchiveBeforeDelete() {
            bindOperator();

            service.delete(new SenderNumberDeletion(List.of(ref(CODE, NUMBER)), REASON), IP);

            // 순서가 의미를 갖는다. 원장 행이 사라진 뒤에는 복사할 원본이 없고,
            // INSERT ... SELECT 는 0건을 넣고 조용히 성공한다 — D-S1 의 실패 방식이다.
            // The order carries meaning: once the ledger row is gone there is nothing to copy and
            // the INSERT … SELECT would insert zero rows and succeed quietly — D-S1's failure mode.
            var order = Mockito.inOrder(mapper);
            order.verify(mapper).archive(CODE, NUMBER, "op@example.com", REASON);
            order.verify(mapper).deleteLive(CODE, NUMBER);
            order.verify(mapper).insertHistory(any());
        }

        @Test
        @DisplayName("D-S1 회귀 — 아카이브가 0건이면 실패한다 / fails when the archive copies nothing")
            // req: FR-SNDD-002, NFR-OPS-D02
        void failsWhenNothingToArchive() {
            bindOperator();
            given(mapper.archive(anyString(), anyString(), anyString(), anyString())).willReturn(0);

            assertThatThrownBy(() -> service.delete(
                    new SenderNumberDeletion(List.of(ref(CODE, NUMBER)), REASON), IP))
                    .isInstanceOf(SenderNumberNotLiveException.class);

            // 이것이 D-S1 의 핵심이다: 아무것도 하지 않았다면 이력도 쓰지 않는다. 레거시는
            // 0건을 지운 뒤에도 이력을 쓰고 성공을 보고했다.
            // This is D-S1's core: having done nothing, write no history either. The legacy wrote the
            // history row and reported success after deleting nothing.
            verify(mapper, never()).insertHistory(any());
            verify(mapper, never()).deleteLive(anyString(), anyString());
        }

        @Test
        @DisplayName("D-S1 회귀 — 삭제가 0건이면 실패한다 / fails when the delete removes nothing")
            // req: FR-SNDD-002, NFR-OPS-D02
        void failsWhenDeleteRemovesNothing() {
            bindOperator();
            given(mapper.deleteLive(anyString(), anyString())).willReturn(0);

            assertThatThrownBy(() -> service.delete(
                    new SenderNumberDeletion(List.of(ref(CODE, NUMBER)), REASON), IP))
                    .isInstanceOf(SenderNumberNotLiveException.class);

            verify(mapper, never()).insertHistory(any());
        }

        @Test
        @DisplayName("D-S5 회귀 — 3건을 지우면 이력 3건이 각각 한 번호를 담는다 / three deletions, three single-number history rows")
            // req: FR-SNDD-004, FR-SNDH-003
        void writesOneHistoryRowPerNumber() {
            bindOperator();
            List<SenderNumberRef> refs = List.of(
                    ref(CODE, "0212345678"), ref(CODE, "0312345678"), ref(CODE, "15881234"));

            int affected = service.delete(new SenderNumberDeletion(refs, REASON), IP);

            assertThat(affected).isEqualTo(3);
            ArgumentCaptor<SenderNumberMapper.HistoryInsert> captor =
                    ArgumentCaptor.forClass(SenderNumberMapper.HistoryInsert.class);
            verify(mapper, Mockito.times(3)).insertHistory(captor.capture());

            List<String> written = captor.getAllValues().stream()
                    .map(SenderNumberMapper.HistoryInsert::number)
                    .toList();
            assertThat(written).containsExactly("0212345678", "0312345678", "15881234");
            // 레거시는 putAll(input) 로 이력을 만들었고 그 DP_NO 는 콤마로 이어붙인 목록이었다 —
            // 3건을 지우면 목록 전체를 한 "번호" 로 암호화한 행이 3개 생겼다(D-S5).
            // The legacy built history with putAll(input), whose DP_NO was the comma-joined list:
            // three deletions produced three rows each encrypting the whole list as one "number".
            assertThat(written).noneMatch(number -> number.contains(","));
        }

        @Test
        @DisplayName("사유가 없으면 거절한다 / refuses a deletion with no reason")
            // req: FR-SNDD-006
        void refusesDeletionWithoutReason() {
            bindOperator();
            assertThatThrownBy(() -> service.delete(
                    new SenderNumberDeletion(List.of(ref(CODE, NUMBER)), null), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("사유");
            verify(mapper, never()).archive(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("빈 선택은 조용한 0건 성공이 되지 않는다 / an empty selection is not a quiet zero-row success")
            // req: FR-SNDD-002, FR-SNDD-010
        void refusesEmptySelection() {
            bindOperator();
            assertThatThrownBy(() -> service.delete(new SenderNumberDeletion(List.of(), REASON), IP))
                    .isInstanceOf(SenderNumberValidationException.class);
        }

        @Test
        @DisplayName("같은 행을 두 번 담아도 한 번만 지운다 / a repeated target is deleted once")
            // req: FR-SNDD-009
        void deduplicatesTargets() {
            bindOperator();
            // 두 번째 통과에서 살아 있는 행을 찾지 못해 전체가 실패하면, 요청의 오류를 데이터의
            // 오류로 보고하는 것이 된다.
            // Failing the whole request on the second pass for want of a live row would report a
            // request error as a data error.
            int affected = service.delete(new SenderNumberDeletion(
                    List.of(ref(CODE, NUMBER), ref(CODE, NUMBER)), REASON), IP);

            assertThat(affected).isEqualTo(1);
            verify(mapper, Mockito.times(1)).deleteLive(CODE, NUMBER);
        }

        @Test
        @DisplayName("100건을 넘으면 거절한다 / refuses more than the batch cap")
            // req: FR-SNDD-005, NFR-PERF-D03
        void refusesOverSizedBatch() {
            bindOperator();
            List<SenderNumberRef> refs = new java.util.ArrayList<>();
            for (int i = 0; i < 101; i++) {
                refs.add(ref(CODE, String.format("021234%04d", i)));
            }
            assertThatThrownBy(() -> service.delete(new SenderNumberDeletion(refs, REASON), IP))
                    .isInstanceOf(SenderNumberValidationException.class);
        }

        @Test
        @DisplayName("감사 기록은 건수를 담고 번호는 담지 않는다 / the audit record carries the count, not the numbers")
            // req: FR-AZ-D05, ADR-SND-019
        void auditCarriesCountNotNumbers() {
            bindOperator();
            service.delete(new SenderNumberDeletion(
                    List.of(ref(CODE, "0212345678"), ref(CODE, "15881234")), REASON), IP);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit).record(captor.capture());
            AuditEvent event = captor.getValue();

            assertThat(event.action()).isEqualTo(AuditEvent.ACTION_SENDER_NUMBER_DELETE);
            assertThat(event.detail()).contains("2");
            assertThat(event.detail()).doesNotContain("0212345678");
            assertThat(event.detail()).doesNotContain("15881234");
        }
    }

    // =========================================================================
    // 인가 / authorization
    // =========================================================================

    @Nested
    @DisplayName("D-S2 / D-S3 회귀 — 인가와 테넌트 범위 / authorization and tenant scope")
    class Authorization {

        @Test
        @DisplayName("운영자가 아니면 등록도 삭제도 거절한다 / a non-operator may neither register nor delete")
            // req: FR-AZ-D01, FR-AZ-D02, FR-AZ-D04
        void refusesNonOperator() {
            bindClientUser(CODE);

            assertThatThrownBy(() -> service.register(CODE, registration(NUMBER), IP))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.delete(
                    new SenderNumberDeletion(List.of(ref(CODE, NUMBER)), REASON), IP))
                    .isInstanceOf(AccessDeniedException.class);

            // 삭제가 등록보다 <b>덜</b> 보호되지 않는다는 것이 FR-AZ-D04 다. 레거시에서는 등록에만
            // 브라우저 측 검사가 있었고 삭제에는 아무 검사도 없었다(D-S2).
            // FR-AZ-D04 is that delete is not <b>less</b> guarded than register. In the legacy only
            // register had a browser-side check; delete had none at all (D-S2).
            verify(mapper, never()).insertLedger(any());
            verify(mapper, never()).archive(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("세션이 없으면 닫힌 쪽으로 실패한다 / fails closed with no session bound")
            // req: FR-AZ-D01, NFR-SEC-TENANT-D01
        void refusesUnboundSession() {
            // 바인딩이 없으면 TenantContext.require() 가 던진다. 요점은 예외의 <b>종류</b>가 아니라
            // 쓰기가 시작되지 않는다는 것이다 — 레거시는 세션 없이도 서비스에 도달할 수 있었고
            // 인가는 login=Y 하나였다(D-S2).
            // With nothing bound, TenantContext.require() throws. The point is not the exception's
            // <b>type</b> but that no write begins: the legacy could reach the service with no
            // session and its only gate was login=Y (D-S2).
            assertThatThrownBy(() -> service.register(CODE, registration(NUMBER), IP))
                    .isInstanceOf(IllegalStateException.class);
            verify(mapper, never()).insertLedger(any());
            verify(mapper, never()).countAnywhere(anyString());
        }

        @Test
        @DisplayName("이용기관을 고르지 않은 등록은 거절한다 / refuses a registration with no institution")
            // req: FR-SNDC-012
        void refusesRegistrationWithNoInstitution() {
            bindOperator();
            assertThatThrownBy(() -> service.register(null, registration(NUMBER), IP))
                    .isInstanceOf(SenderNumberValidationException.class)
                    .hasMessageContaining("이용기관");
        }
    }
}
