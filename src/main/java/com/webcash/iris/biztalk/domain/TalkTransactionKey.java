package com.webcash.iris.biztalk.domain;

/**
 * 거래 상세를 지목하는 키 — 서버가 검증한 형태.
 * The key identifying a transaction for detail lookup, in its server-validated form.
 *
 * <h2>이 타입이 존재하는 이유 / why this type exists</h2>
 * <p>레거시 화면 32 는 이용기관 코드를 <b>브라우저가 보낸 숨은 입력</b>에서 가져왔다:</p>
 * <pre>&lt;input type="hidden" id="FINTECH_ISCD" value="&lt;%=fintechIscd%&gt;"/&gt;</pre>
 * <p>그 값을 고치면 조회 대상 기관이 바뀐다(D-T2). 이 타입은 <b>기관 코드를 담지 않는다</b> —
 * 서비스가 거래일자와 거래번호로 원장을 다시 읽어 기관을 도출하므로, 요청이 기관을 지목할
 * 방법 자체가 없다(FR-AZ-T03).</p>
 * <p>Legacy screen 32 took the institution code from a <b>hidden input the browser supplied</b>, so
 * changing it changed which institution was queried (D-T2). This type <b>carries no institution code</b>:
 * the service re-reads the ledger by date and serial to derive it, so a request has no way to name one
 * (FR-AZ-T03).</p>
 *
 * @param transactionDate 거래일자 {@code TRDD} / the transaction date
 * @param serial          거래고유번호 / the transaction serial
 *
 * // source: biztalk_admin_32_view.jsp — hidden FINTECH_ISCD; biztalk_admin_32.js — getDat()
 * // req: FR-AZ-T03, FR-TLKD-001, CONST-BIZ-T01
 */
public record TalkTransactionKey(String transactionDate, TransactionSerial serial) {

    /**
     * 키를 만든다. / Creates the key.
     *
     * @param rawDate   거래일자 {@code YYYYMMDD} / the transaction date
     * @param rawSerial 거래고유번호 / the transaction serial
     * @return 검증된 키 / the validated key
     * @throws IllegalArgumentException 일자나 일련번호가 없을 때 / when either is absent
     */
    // req: FR-TLKD-001, FR-TLKD-009
    public static TalkTransactionKey of(String rawDate, String rawSerial) {
        if (rawDate == null || rawDate.isBlank()) {
            throw new IllegalArgumentException(
                    "거래일자는 필수입니다. / The transaction date is required.");
        }
        TransactionSerial serial = TransactionSerial.parse(rawSerial).orElseThrow(
                () -> new IllegalArgumentException(
                        "거래고유번호는 필수입니다. / The transaction serial is required."));
        return new TalkTransactionKey(rawDate.trim(), serial);
    }

    /**
     * 원장({@code FT_APITR_HSTR.IS_TUNO})에 바인딩할 형태를 반환한다.
     * Returns the form to bind against the ledger's {@code IS_TUNO}.
     *
     * <p>매퍼가 {@code #{k.serial.transactionForm}} 으로 {@link TransactionSerial} 안까지
     * 들어가지 않도록 여기서 한 단계 내보낸다. MyBatis 의 프로퍼티 해석은 레코드의 접근자는
     * 알아보지만 일반 클래스에는 JavaBean 게터를 요구하고, {@link TransactionSerial} 은
     * {@code equals}/{@code hashCode} 를 직접 정의하려고 레코드가 아닌 최종 클래스로 만들었다.
     * 접근자 이름을 바꾸는 대신 <b>매퍼가 레코드 하나만 보게</b> 하는 편이 낫다 — 도메인 타입이
     * 프레임워크의 명명 규칙에 맞춰 흔들리지 않는다.</p>
     * <p>Exposed here so the mapper need not reach into {@link TransactionSerial} via
     * {@code #{k.serial.transactionForm}}. MyBatis resolves record accessors but requires JavaBean getters on
     * an ordinary class, and {@link TransactionSerial} is a final class rather than a record so it can define
     * its own {@code equals}/{@code hashCode}. Rather than rename the accessor, <b>the mapper sees one
     * record</b> — which keeps the domain type from bending to a framework's naming convention.</p>
     *
     * @return 저장 폭에 맞춘 거래고유번호 / the serial at the ledger's stored width
     */
    // req: FR-TLKD-009, FR-AZ-T03
    public String serialForLedger() {
        return serial.transactionForm();
    }

    /**
     * 감사 기록에 담을 설명을 반환한다. / Returns a description for the audit record.
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-T05
    public String describe() {
        return transactionDate + "/" + serial.canonical();
    }
}
