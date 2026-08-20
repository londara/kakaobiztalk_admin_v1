package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.biztalk.infra.db.TalkMessageMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link TalkDetailService} 검증 — 계층 규칙과 채널 라우팅.
 * Verification for {@link TalkDetailService}: the hierarchy rule and channel routing.
 *
 * <p>레거시가 이 두 단계에서 계층을 어긴 방식이 서로 달랐다. 화면 32 는 기관을 <b>브라우저가
 * 보낸 숨은 입력</b>에서 받았고(D-T2), 화면 31 은 <b>기관 조건 없이</b> 메시지 키만으로
 * 조회했다(D-T5). 두 결함 모두 "하위 단계가 상위를 거치지 않는다"는 하나의 성질 위반이다.</p>
 * <p>The legacy broke the hierarchy differently at each level: screen 32 took the institution from a
 * <b>browser-supplied hidden input</b> (D-T2), and screen 31 queried by message key <b>with no institution
 * predicate</b> (D-T5). Both are violations of one property — a lower level reached without passing through the
 * one above.</p>
 *
 * // req: FR-AZ-T03, FR-AZ-T04, FR-AZ-T05, FR-TLKD-004, FR-TLKD-005, FR-TLKM-006, CONST-BIZ-T01
 */
class TalkDetailServiceTest {

    private TalkMessageMapper mapper;
    private AuditService audit;
    private TalkDetailService service;

    private static TalkMessageMapper.TalkMessageRowRecord row(String rslt, String msgRslt) {
        return new TalkMessageMapper.TalkMessageRowRecord(
                "0000000042", "1001", "K00011", "3",
                rslt, rslt == null ? null : "수신거부",
                msgRslt, msgRslt == null ? null : "번호오류",
                "021***5678", "010***5432",
                "20260819", "112504", "112505", "112507", "QUE");
    }

    private static TalkDetailService.MessageQueryRequest messageRequest() {
        return new TalkDetailService.MessageQueryRequest(
                "20260819", "42", null, null, null, null, 0, null);
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(TalkMessageMapper.class);
        audit = Mockito.mock(AuditService.class);
        service = new TalkDetailService(mapper, BizTalkApiRegistry.withDefaults(), audit);
        TenantContext.set(new TenantContext.TenantPrincipal("op@example.com", null, true));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("소유 기관 도출 / deriving the owner")
    class Ownership {

        @Test
        @DisplayName("기관은 원장에서 오고 요청에서 오지 않는다 — FR-AZ-T03")
        void institutionComesFromTheLedger() {
            // 요청 레코드에 기관 필드가 <b>없다</b>. 있으면 D-T2 가 재현될 여지가 남는다.
            // The request record has <b>no</b> institution field: having one would leave room for D-T2.
            assertThat(TalkDetailService.MessageQueryRequest.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .doesNotContain("institution", "institutionCode", "isCd");

            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.countMessages(any())).willReturn(1);
            given(mapper.findMessages(any())).willReturn(List.of(row("0", "0")));

            service.messages(messageRequest(), "127.0.0.1");

            ArgumentCaptor<TalkMessageCriteria> captor =
                    ArgumentCaptor.forClass(TalkMessageCriteria.class);
            verify(mapper).findMessages(captor.capture());
            assertThat(captor.getValue().institutionCode()).isEqualTo("K00011");
        }

        @Test
        @DisplayName("원장에 없는 거래는 거부되고 기록된다")
        void unknownTransactionIsRefusedAndAudited() {
            given(mapper.findTransactionOwner(any())).willReturn(null);

            assertThatThrownBy(() -> service.messages(messageRequest(), "127.0.0.1"))
                    .isInstanceOf(TalkDetailService.TransactionNotFoundException.class);

            verify(mapper, never()).findMessages(any());
            verify(audit).recordAuth(anyString(), eq(AuditEvent.ACTION_TALK_DETAIL_VIEW),
                    eq(AuditEvent.Outcome.DENIED), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("채널 라우팅 / channel routing")
    class ChannelRouting {

        @Test
        @DisplayName("채널은 API 서비스 코드가 정하고 행의 자기 보고는 읽지 않는다 — D-T7")
        void channelComesFromTheApiServiceCode() {
            // ⚠ 레거시는 채널을 메시지 행의 MSG_TYPE 에서 읽었고, 친구톡 질의가 그것을 'AT' 로
            // 잘못 채워 화면 32 의 유형이 틀리고 화면 31 이 KKO_MSG 를 조회했다. 여기서는 행
            // 레코드에 채널 필드가 아예 없다.
            // The legacy read the channel from the row's MSG_TYPE, which the 친구톡 query wrongly set to 'AT',
            // making screen 32's type wrong and screen 31 query KKO_MSG. Here the row record has no channel
            // field at all.
            assertThat(TalkMessageMapper.TalkMessageRowRecord.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .doesNotContain("channel", "msgType", "messageType");

            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_FT_SEND"));
            given(mapper.countMessages(any())).willReturn(1);
            given(mapper.findMessages(any())).willReturn(List.of(row("0", "0")));

            PagedResult<TalkMessageRow> result = service.messages(messageRequest(), "127.0.0.1");

            assertThat(result.rows()).singleElement()
                    .satisfies(r -> assertThat(r.channel()).isEqualTo(TalkChannel.FRIENDTALK));
        }

        @Test
        @DisplayName("ADV_KKO_AT_SEND2 도 라우팅된다 — 레거시가 빠뜨린 코드")
        void theCodeTheLegacyOmittedRoutes() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND2"));
            given(mapper.countMessages(any())).willReturn(0);
            given(mapper.findMessages(any())).willReturn(List.of());

            assertThat(service.messages(messageRequest(), "127.0.0.1")).isNotNull();
        }

        @Test
        @DisplayName("상세를 지원하지 않는 API 는 빈 결과와 구분되는 예외다 — D-T13")
        void unsupportedApiIsDistinguishableFromEmpty() {
            // ⚠ 레거시는 IDO 핸들을 null 로 두고 예외도 던지지 않고 REC1 없는 결과를 반환해
            // 팝업이 빈 그리드로 열렸다. 운영자는 "이 거래에 메시지가 없다"고 결론지었다.
            // The legacy left the IDO handle null, threw nothing, and returned a result with no REC1, so the
            // popup opened on an empty grid and the operator concluded there were no messages.
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_COM_GET_STATUS"));

            assertThatThrownBy(() -> service.messages(messageRequest(), "127.0.0.1"))
                    .isInstanceOf(TalkDetailService.UnsupportedTransactionException.class)
                    .hasMessageContaining("지원하지 않는");

            verify(mapper, never()).findMessages(any());
            verify(audit).recordAuth(anyString(), eq(AuditEvent.ACTION_TALK_DETAIL_VIEW),
                    eq(AuditEvent.Outcome.DENIED), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("결과 구분 / result classification")
    class ResultClassification {

        @Test
        @DisplayName("결과가 없는 행은 미수신으로 분류된다 — D-T22")
        void noResultMeansPending() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.countMessages(any())).willReturn(1);
            given(mapper.findMessages(any())).willReturn(List.of(row(null, null)));

            assertThat(service.messages(messageRequest(), "127.0.0.1").rows())
                    .singleElement()
                    .satisfies(r -> {
                        assertThat(r.talkResult().pending()).isTrue();
                        assertThat(r.fullyPending()).isTrue();
                    });
        }

        @Test
        @DisplayName("알 수 없는 결과 구분 값은 거부된다")
        void unknownOutcomeIsRefused() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));

            assertThatThrownBy(() -> service.messages(
                    new TalkDetailService.MessageQueryRequest(
                            "20260819", "42", null, null, "MAYBE", null, 0, null), "127.0.0.1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("세 구분 값이 모두 파싱된다")
        void allThreeOutcomesParse() {
            assertThat(TalkDetailService.outcomeOf("SUCCESS")).contains(TalkResult.Outcome.SUCCESS);
            assertThat(TalkDetailService.outcomeOf("failure")).contains(TalkResult.Outcome.FAILURE);
            assertThat(TalkDetailService.outcomeOf(" pending ")).contains(TalkResult.Outcome.PENDING);
            assertThat(TalkDetailService.outcomeOf(null)).isEmpty();
            assertThat(TalkDetailService.outcomeOf("  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("메시지 상세 / message detail")
    class Detail {

        private TalkDetailService.DetailQueryRequest detailRequest() {
            return new TalkDetailService.DetailQueryRequest("20260819", "42", "1001", "QUE");
        }

        private TalkMessageMapper.TalkMessageDetailRecord record() {
            return new TalkMessageMapper.TalkMessageDetailRecord(
                    "1001", "K00011", "PROF", "N", "3",
                    "9999", null, "0", "정상", "TPL_001",
                    "021***5678", "010***5432",
                    "2026-08-19 11:25:04", "2026-08-19 11:25:05",
                    "2026-08-19 11:25:06", "2026-08-19 11:25:07",
                    "안녕하세요", null, null, "N", null, null, null, null, null);
        }

        @Test
        @DisplayName("거래 키가 필수다 — 메시지 키만으로 조회할 수 없다 — D-T5")
        void transactionKeyIsRequired() {
            // 요청 레코드가 거래일자와 거래번호를 <b>함께</b> 요구한다. 메시지 키만으로 조회할
            // 수 있게 하면 D-T5 가 그대로 돌아온다.
            // The request record requires the date and serial <b>alongside</b> the message key. Allowing a lookup
            // by message key alone would restore D-T5.
            assertThat(TalkDetailService.DetailQueryRequest.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .contains("transactionDate", "serial", "messageKey");
        }

        @Test
        @DisplayName("기관은 원장에서 도출되어 키에 들어간다 — FR-AZ-T04")
        void institutionIsDerivedIntoTheKey() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.findDetail(any())).willReturn(record());

            service.detail(detailRequest(), "127.0.0.1");

            ArgumentCaptor<TalkMessageDetailKey> captor =
                    ArgumentCaptor.forClass(TalkMessageDetailKey.class);
            verify(mapper).findDetail(captor.capture());
            assertThat(captor.getValue().institutionCode()).isEqualTo("K00011");
            assertThat(captor.getValue().channel()).isEqualTo(TalkChannel.ALIMTALK);
        }

        @Test
        @DisplayName("없는 메시지는 기록되고 거부된다 — 없음과 권한 없음을 구분하지 않는다")
        void missingMessageIsRefusedWithoutDistinguishing() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.findDetail(any())).willReturn(null);

            assertThatThrownBy(() -> service.detail(detailRequest(), "127.0.0.1"))
                    .isInstanceOf(TalkDetailService.MessageNotFoundException.class);

            verify(audit).recordAuth(anyString(), eq(AuditEvent.ACTION_TALK_MESSAGE_VIEW),
                    eq(AuditEvent.Outcome.DENIED), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("사전에 없는 결과 코드도 표시된다 — D-T20")
        void unknownResultCodeIsStillDisplayed() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.findDetail(any())).willReturn(record());

            TalkMessageDetail detail = service.detail(detailRequest(), "127.0.0.1");

            assertThat(detail.talkResult().display())
                    .as("레거시는 NULL 전파로 이 칸을 비웠다 / the legacy blanked this via NULL propagation")
                    .contains("9999")
                    .contains(TalkResult.UNKNOWN_CODE_MARKER);
        }

        @Test
        @DisplayName("상세 열람이 기록된다 — 이 슬라이스에서 가장 민감한 열람이다")
        void detailViewIsAudited() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.findDetail(any())).willReturn(record());

            service.detail(detailRequest(), "10.0.0.9");

            verify(audit).recordAuth(eq("op@example.com"),
                    eq(AuditEvent.ACTION_TALK_MESSAGE_VIEW), eq(AuditEvent.Outcome.OK),
                    anyString(), eq("10.0.0.9"), any());
        }

        @Test
        @DisplayName("알 수 없는 테이블 구분은 거부된다")
        void unknownTableTypeIsRefused() {
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));

            assertThatThrownBy(() -> service.detail(
                    new TalkDetailService.DetailQueryRequest("20260819", "42", "1001", "WRONG"),
                    "127.0.0.1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("QUE");
        }
    }

    @Nested
    @DisplayName("감사 조건 설명 / audit description")
    class AuditDescription {

        @Test
        @DisplayName("수신번호 검색어를 감사 기록에 담지 않는다 — ADR-006")
        void recipientTermIsNotRecorded() {
            // 문자내역 슬라이스는 해시해서 남겼다. 그 화면은 수신번호가 <b>주된 검색 조건</b>이라
            // 무엇으로 찾았는지가 감사 가치가 있었다. 여기서는 거래 키가 이미 무엇을 보았는지
            // 특정하므로 부분 일치 문자열을 남길 이유가 없다.
            // The 문자내역 slice hashed it, because there the recipient is the <b>primary</b> search key and what
            // was searched for had audit value. Here the transaction key already identifies what was viewed, so
            // there is no reason to record a substring.
            given(mapper.findTransactionOwner(any())).willReturn(
                    new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
            given(mapper.countMessages(any())).willReturn(0);
            given(mapper.findMessages(any())).willReturn(List.of());

            service.messages(new TalkDetailService.MessageQueryRequest(
                    "20260819", "42", "01098765432", null, null, null, 0, null), "127.0.0.1");

            ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
            verify(audit).recordAuth(anyString(), anyString(), any(), detail.capture(),
                    anyString(), any());

            assertThat(detail.getValue())
                    .as("감사 저장소가 2차 PII 저장소가 되어서는 안 된다 / "
                            + "the audit store must not become a secondary PII repository")
                    .doesNotContain("01098765432")
                    .contains("recipientFilter=yes");
        }
    }
}
