package com.webcash.iris.biztalk.domain;

import com.webcash.iris.biztalk.infra.db.TalkMessageMapper;
import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 상세내역과 메시지 상세 조회 서비스 — 화면 32 와 31.
 * The transaction-detail and message-detail service: screens 32 and 31.
 *
 * <h2>이 서비스가 지키는 계층 규칙 / the hierarchy rule this service holds</h2>
 * <p>CONST-BIZ-T01: 거래 → 메시지 → 메시지 본문은 <b>엄격한 계층</b>이며, 하위 단계는 상위를
 * 통해서만 도달한다. 레거시는 두 단계 모두 그것을 어겼다 — 화면 32 는 기관을 브라우저가 보낸
 * 숨은 입력에서 받았고(D-T2), 화면 31 은 기관 조건 없이 메시지 키만으로 조회했다(D-T5).</p>
 * <p>CONST-BIZ-T01: 거래 → 메시지 → message body is a <b>strict hierarchy</b>, and a lower level is
 * reachable only through the one above. The legacy broke it at both levels — screen 32 took the institution
 * from a browser-supplied hidden input (D-T2), and screen 31 queried by message key with no institution
 * predicate at all (D-T5).</p>
 *
 * <p>여기서는 <b>모든 조회가 원장에서 시작</b>한다. 기관은 {@code FT_APITR_HSTR} 를 다시 읽어
 * 도출하고, 채널은 그 행의 API 서비스 코드로 레지스트리에 묻는다 — 요청이 둘 중 어느 것도
 * 지목할 방법이 없다.</p>
 * <p>Here <b>every lookup starts at the ledger</b>: the institution is derived by re-reading
 * {@code FT_APITR_HSTR}, and the channel comes from asking the registry about that row's API service code.
 * A request has no way to name either.</p>
 *
 * // source: biztalk_admin_32_l001_act.jsp, biztalk_admin_31_l001_act.jsp
 * // req: FR-AZ-T03, FR-AZ-T04, FR-AZ-T05, FR-TLKD-001…009, FR-TLKM-001…007
 */
@Service
public class TalkDetailService {

    private final TalkMessageMapper mapper;
    private final BizTalkApiRegistry registry;
    private final AuditService audit;

    /**
     * 서비스를 생성한다. / Creates the service.
     *
     * @param mapper   메시지 매퍼 / the message mapper
     * @param registry BizTalk API 레지스트리 / the BizTalk API registry
     * @param audit    감사 서비스 / the audit service
     */
    public TalkDetailService(TalkMessageMapper mapper,
                            BizTalkApiRegistry registry,
                            AuditService audit) {
        this.mapper = mapper;
        this.registry = registry;
        this.audit = audit;
    }

    /**
     * 거래에 속한 메시지를 조회한다. / Reads the messages under a transaction.
     *
     * @param request  요청 값 / the request values
     * @param sourceIp 감사용 출처 주소 / the source address, for audit
     * @return 한 페이지와 전체 건수 / one page plus the total count
     * @throws UnsupportedTransactionException 상세를 지원하지 않는 API 서비스 / when the API service has no detail
     * @throws TransactionNotFoundException    원장에 없는 거래 / when the transaction is not in the ledger
     */
    // req: FR-TLKD-001, FR-TLKD-002, FR-TLKD-005, FR-TLKD-007, FR-AZ-T03, FR-AZ-T05
    @Transactional(readOnly = true)
    public PagedResult<TalkMessageRow> messages(MessageQueryRequest request, String sourceIp) {

        TenantContext.TenantPrincipal principal = TenantContext.require();
        TalkTransactionKey key = TalkTransactionKey.of(request.transactionDate(), request.serial());

        // 기관과 채널은 원장에서 온다. 요청은 둘 중 어느 것도 담지 않는다(FR-AZ-T03).
        // The institution and the channel come from the ledger; the request carries neither (FR-AZ-T03).
        TalkMessageMapper.TransactionOwner owner = mapper.findTransactionOwner(key);
        if (owner == null) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_DETAIL_VIEW,
                    AuditEvent.Outcome.DENIED, "notFound " + key.describe(), sourceIp, null);
            throw new TransactionNotFoundException(key);
        }

        TalkChannel channel = registry.channelOf(owner.apiServiceCode())
                .orElseThrow(() -> {
                    // ⚠ 레거시는 여기서 아무것도 하지 않았다. IDO 핸들이 null 로 남고, 예외도
                    // 던지지 않고, REC1 없는 결과 도메인을 반환해 팝업이 빈 그리드로 열렸다 —
                    // 운영자는 "이 거래에 메시지가 없다"고 결론지었다(D-T13).
                    // The legacy did nothing here: the IDO handle stayed null, nothing threw, and a result
                    // domain with no REC1 opened the popup on an empty grid — so the operator concluded the
                    // transaction had no messages (D-T13).
                    audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_DETAIL_VIEW,
                            AuditEvent.Outcome.DENIED,
                            "unsupported " + key.describe() + " api=" + owner.apiServiceCode(),
                            sourceIp, null);
                    return new UnsupportedTransactionException(owner.apiServiceCode());
                });

        TalkMessageCriteria criteria = new TalkMessageCriteria(
                key,
                channel,
                owner.institutionCode(),
                blankToNull(request.recipient()),
                blankToNull(request.status()),
                parseOutcome(request.talkOutcome()),
                parseOutcome(request.smsOutcome()),
                Math.max(0, request.page()),
                TalkMessageCriteria.normaliseSize(request.size()));

        try {
            int total = mapper.countMessages(criteria);
            List<TalkMessageRow> rows = mapper.findMessages(criteria).stream()
                    .map(record -> toRow(record, channel))
                    .toList();

            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_DETAIL_VIEW,
                    AuditEvent.Outcome.OK,
                    criteria.describe() + " rows=" + rows.size() + " total=" + total,
                    sourceIp, null);

            return new PagedResult<>(rows, total, criteria.page(), criteria.size());

        } catch (RuntimeException e) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_DETAIL_VIEW,
                    AuditEvent.Outcome.ERROR, criteria.describe(), sourceIp, null);
            throw e;
        }
    }

    /**
     * 메시지 한 건의 상세를 조회한다. / Reads one message's detail.
     *
     * <p>기관은 <b>요청에서 받지 않고</b> 거래 원장에서 도출한다. 레거시 화면 31 의 질의는
     * {@code REQDATE} + {@code STATUS} + {@code MSGKEY} 만으로 키가 만들어져, 메시지 키만 알면
     * 다른 기관의 메시지 본문·템플릿코드·전화번호를 읽을 수 있었다(D-T5).</p>
     * <p>The institution is <b>not taken from the request</b> but derived from the transaction ledger. The
     * legacy screen-31 query was keyed on {@code REQDATE} + {@code STATUS} + {@code MSGKEY} alone, so a
     * message key was enough to read another institution's body, template code and phone numbers (D-T5).</p>
     *
     * @param request  요청 값 / the request values
     * @param sourceIp 감사용 출처 주소 / the source address, for audit
     * @return 상세 / the detail
     * @throws UnsupportedTransactionException 상세를 지원하지 않는 API 서비스 / when unsupported
     * @throws TransactionNotFoundException    원장에 없는 거래 / when the transaction is absent
     * @throws MessageNotFoundException        기관 범위 안에 없는 메시지 / when the message is not in scope
     */
    // req: FR-AZ-T04, FR-AZ-T05, FR-TLKM-001…007
    @Transactional(readOnly = true)
    public TalkMessageDetail detail(DetailQueryRequest request, String sourceIp) {

        TenantContext.TenantPrincipal principal = TenantContext.require();
        TalkTransactionKey txnKey =
                TalkTransactionKey.of(request.transactionDate(), request.serial());

        TalkMessageMapper.TransactionOwner owner = mapper.findTransactionOwner(txnKey);
        if (owner == null) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_MESSAGE_VIEW,
                    AuditEvent.Outcome.DENIED, "notFound " + txnKey.describe(), sourceIp, null);
            throw new TransactionNotFoundException(txnKey);
        }

        TalkChannel channel = registry.channelOf(owner.apiServiceCode())
                .orElseThrow(() -> new UnsupportedTransactionException(owner.apiServiceCode()));

        TalkMessageDetailKey key = TalkMessageDetailKey.of(
                owner.institutionCode(), request.messageKey(), channel, request.tableType());

        TalkMessageMapper.TalkMessageDetailRecord record = mapper.findDetail(key);
        if (record == null) {
            // 없는 것과 권한이 없는 것을 <b>같은 응답</b>으로 다룬다. 구분하면 응답만으로 어떤
            // 메시지 키가 존재하는지 추론할 수 있다 — 열거 창구가 된다(TM-T10).
            // Absent and not-permitted produce the <b>same response</b>: distinguishing them would let a
            // caller infer which message keys exist from responses alone — an enumeration oracle (TM-T10).
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_MESSAGE_VIEW,
                    AuditEvent.Outcome.DENIED, "notFound " + key.describe(), sourceIp, null);
            throw new MessageNotFoundException(request.messageKey());
        }

        audit.recordAuth(principal.email(), AuditEvent.ACTION_TALK_MESSAGE_VIEW,
                AuditEvent.Outcome.OK, key.describe(), sourceIp, null);

        return toDetail(record, channel);
    }

    private static TalkMessageRow toRow(TalkMessageMapper.TalkMessageRowRecord record,
                                        TalkChannel channel) {
        return new TalkMessageRow(
                // 채널은 레지스트리에서 온다. 행이 스스로 보고하는 유형은 읽지 않는다(D-T7).
                // The channel comes from the registry; the row's self-reported type is not read (D-T7).
                channel,
                record.transactionNo(),
                record.messageKey(),
                record.institutionCode(),
                record.statusCode(),
                TalkResult.ofTalk(record.talkResultCode(), record.talkResultText()),
                TalkResult.ofSms(record.smsResultCode(), record.smsResultText()),
                record.senderNumber(),
                record.recipientNumber(),
                record.requestDate(),
                record.requestTime(),
                record.sentTime(),
                record.reportTime(),
                record.tableType());
    }

    private static TalkMessageDetail toDetail(TalkMessageMapper.TalkMessageDetailRecord record,
                                              TalkChannel channel) {
        return new TalkMessageDetail(
                record.messageKey(),
                record.institutionCode(),
                channel,
                record.statusCode(),
                record.profileKey(),
                record.adFlag(),
                TalkResult.ofTalk(record.talkResultCode(), record.talkResultText()),
                TalkResult.ofSms(record.smsResultCode(), record.smsResultText()),
                record.templateCode(),
                record.senderNumber(),
                record.recipientNumber(),
                record.requestedAt(),
                record.sentAt(),
                record.carrierRepliedAt(),
                record.reportedAt(),
                record.message(),
                record.imagePath(),
                record.imageUrl(),
                record.wideImageFlag(),
                record.buttonJson(),
                record.failedType(),
                record.failedSubject(),
                record.failedImage(),
                record.failedMessage());
    }

    private static TalkResult.Outcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "SUCCESS" -> TalkResult.Outcome.SUCCESS;
            case "FAILURE" -> TalkResult.Outcome.FAILURE;
            case "PENDING" -> TalkResult.Outcome.PENDING;
            default -> throw new IllegalArgumentException(
                    "결과 구분은 SUCCESS, FAILURE 또는 PENDING 이어야 합니다: '" + raw + "' / "
                            + "The result filter must be SUCCESS, FAILURE or PENDING: '" + raw + "'");
        };
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * 화면 32 의 요청 값. / Screen 32's request values.
     *
     * <p>이용기관을 <b>담지 않는다</b> — 서버가 원장에서 도출한다(FR-AZ-T03).</p>
     * <p>Carries <b>no institution</b>: the server derives it from the ledger (FR-AZ-T03).</p>
     *
     * @param transactionDate 거래일자 / the transaction date
     * @param serial          거래고유번호 / the transaction serial
     * @param recipient       수신번호 부분 일치 / the recipient substring
     * @param status          상태 코드 / the status code
     * @param talkOutcome     톡결과 구분 / the talk-result classification
     * @param smsOutcome      문자결과 구분 / the SMS-result classification
     * @param page            페이지 번호 / the page number
     * @param size            페이지 크기 / the page size
     */
    // req: FR-TLKD-001, FR-AZ-T03
    public record MessageQueryRequest(
            String transactionDate,
            String serial,
            String recipient,
            String status,
            String talkOutcome,
            String smsOutcome,
            int page,
            Integer size
    ) {
    }

    /**
     * 화면 31 의 요청 값. / Screen 31's request values.
     *
     * <p>거래 키를 <b>함께</b> 받는다. 메시지 키만으로 조회할 수 있게 하면 D-T5 가 그대로
     * 돌아온다 — 계층은 요청의 형태로 강제된다(CONST-BIZ-T01).</p>
     * <p>The transaction key is required <b>alongside</b> the message key. Allowing a lookup by message key
     * alone would restore D-T5: the hierarchy is enforced by the shape of the request (CONST-BIZ-T01).</p>
     *
     * @param transactionDate 거래일자 / the transaction date
     * @param serial          거래고유번호 / the transaction serial
     * @param messageKey      메시지키 / the message key
     * @param tableType       {@code QUE}/{@code LOG} / live or archive
     */
    // req: FR-AZ-T04, CONST-BIZ-T01
    public record DetailQueryRequest(
            String transactionDate,
            String serial,
            String messageKey,
            String tableType
    ) {
    }

    /**
     * 상세 조회를 지원하지 않는 API 서비스일 때 던진다.
     * Thrown when the API service has no message detail.
     *
     * <p>빈 결과와 <b>구분되어야 한다</b>. 레거시는 이 경우 빈 그리드를 반환해 "메시지가 없다"와
     * 구분할 수 없었다(D-T13, FR-TLKD-005).</p>
     * <p>Must be <b>distinguishable from an empty result</b>: the legacy returned an empty grid here, which
     * was indistinguishable from "there are no messages" (D-T13, FR-TLKD-005).</p>
     */
    // req: FR-TLKD-005
    public static class UnsupportedTransactionException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param apiServiceCode 문제가 된 API 서비스 코드 / the API service code in question
         */
        public UnsupportedTransactionException(String apiServiceCode) {
            super("상세 조회를 지원하지 않는 거래입니다 (API: " + apiServiceCode + "). / "
                    + "This transaction has no message detail (API: " + apiServiceCode + ").");
        }
    }

    /** 원장에 없는 거래일 때 던진다. / Thrown when the transaction is not in the ledger. */
    // req: FR-AZ-T03
    public static class TransactionNotFoundException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param key 조회한 키 / the key looked up
         */
        public TransactionNotFoundException(TalkTransactionKey key) {
            super("거래를 찾을 수 없습니다: " + key.describe() + " / "
                    + "Transaction not found: " + key.describe());
        }
    }

    /**
     * 범위 안에 없는 메시지일 때 던진다. / Thrown when the message is not within scope.
     *
     * <p>"없음"과 "권한 없음"을 구분하지 않는다 — 구분하면 응답이 열거 창구가 된다(TM-T10).</p>
     * <p>Does not distinguish absent from not-permitted: distinguishing them would make the response an
     * enumeration oracle (TM-T10).</p>
     */
    // req: FR-AZ-T04
    public static class MessageNotFoundException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param messageKey 조회한 메시지키 / the message key looked up
         */
        public MessageNotFoundException(String messageKey) {
            super("메시지를 찾을 수 없습니다: " + messageKey + " / "
                    + "Message not found: " + messageKey);
        }
    }

    /**
     * 결과 구분 문자열을 파싱한 값을 반환한다 — 테스트 편의용.
     * Exposes the parsed outcome, for test convenience.
     *
     * @param raw 요청 값 / the request value
     * @return 파싱된 구분, 조건 없으면 empty / the parsed classification, empty when unfiltered
     */
    // req: FR-TLKD-006
    public static Optional<TalkResult.Outcome> outcomeOf(String raw) {
        return Optional.ofNullable(parseOutcome(raw));
    }
}
