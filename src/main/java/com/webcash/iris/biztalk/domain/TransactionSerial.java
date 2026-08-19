package com.webcash.iris.biztalk.domain;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 거래일련번호 — 하나의 정규화 규칙. / The transaction serial: one normalisation rule.
 *
 * <h2>레거시가 같은 식별자를 세 가지로 다룬 방식 / three rules for one identifier</h2>
 * <table>
 *   <caption>레거시 정규화 경로 / the legacy's normalisation paths</caption>
 *   <tr><th>경로 / path</th><th>규칙 / rule</th></tr>
 *   <tr><td>목록 검색 / list search</td>
 *       <td>{@code padStart(20,'0')} 후 {@code IS_TUNO = :IS_TUNO} 정확 일치</td></tr>
 *   <tr><td>알림톡 상세 / 알림톡 detail</td>
 *       <td>{@code stripStart(v,"0")} 후 {@code SERIALNUM = LPAD(:SERIALNUM,10,'0')}</td></tr>
 *   <tr><td>친구톡 상세 / 친구톡 detail</td>
 *       <td>{@code stripStart(v,"0")} 후 {@code SERIALNUM = :SERIALNUM} 원값</td></tr>
 * </table>
 *
 * <p>알림톡 규칙은 단지 다른 것이 아니라 <b>손실이 있다</b>. PostgreSQL 의
 * {@code lpad(string, length, fill)} 은 입력이 목표 폭보다 길면 <b>거부하지 않고
 * 잘라낸다</b>. 20자리 거래고유번호 {@code 00000026081900142813} 은 앞의 0 이 제거되어
 * {@code 26081900142813}(14자리)이 되고, {@code lpad(…,10,'0')} 이 그것을
 * {@code 2608190014} 로 잘라낸다 — <b>다른 거래의 메시지</b>에 일치하거나 아무것도
 * 일치하지 않는다(D-T9). CONST-BIZ-T01 관점에서 이것은 표시 결함이 아니라 <b>교차 기관
 * 노출 경로</b>다.</p>
 * <p>The 알림톡 rule is not merely different, it is <b>lossy</b>: PostgreSQL's
 * {@code lpad(string, length, fill)} <b>truncates rather than refusing</b> when the input exceeds
 * the target width. A 20-character serial {@code 00000026081900142813} loses its leading zeros to
 * {@code 26081900142813} (14 characters), and {@code lpad(…,10,'0')} cuts that to
 * {@code 2608190014} — matching <b>another transaction's messages</b>, or none (D-T9). Under
 * CONST-BIZ-T01 that is a cross-institution disclosure path, not a display bug.</p>
 *
 * <h2>왜 자바에서 패딩하는가 / why padding happens in Java</h2>
 * <p>잘림을 없애기 위해서다. 정규화가 질의 전에 끝나면 {@code lpad} 가 술어에 있을 이유가
 * 없고, 절단될 값도 없다.</p>
 * <p>To remove the truncation. With normalisation finished before the query there is no reason for
 * {@code lpad} to be in a predicate, and nothing to truncate.</p>
 *
 * <p><b>정정 2026-08-19.</b> 이 문단은 원래 "술어 안의 {@code LPAD} 는 잘림 결함이면서 동시에
 * 인덱스를 쓸 수 없게 만든다(sargability)"고 적었다. <b>뒤의 절반은 거짓이다.</b> sargability 는
 * 함수가 <b>컬럼</b>에 적용될 때 깨지는데({@code LPAD(IS_TUNO,…) = :x}), 레거시는 함수를
 * <b>파라미터</b>에 적용했다({@code SERIALNUM = LPAD(:SERIALNUM,10,'0')}) — PostgreSQL 이 계획
 * 시점에 상수로 접어 넣으므로 인덱스를 그대로 쓴다.
 * {@code TalkHistoryLoadTest#lpadOnAParameterDoesNotPreventIndexUse} 가 세 형태의 실행 계획을
 * 나란히 출력한다. 잘림은 실재하고 {@code LpadTruncationTest} 가 실행으로 증명하므로 결정은
 * 바뀌지 않는다 — 근거 하나가 측정으로 반박되었다는 사실만 남긴다.</p>
 * <p><b>Correction 2026-08-19.</b> This paragraph originally claimed {@code LPAD} in a predicate was
 * "both the truncation bug and a sargability problem". <b>The second half was false.</b> Sargability
 * breaks when a function is applied to the <b>column</b> ({@code LPAD(IS_TUNO,…) = :x}); the legacy
 * applied it to the <b>parameter</b>, which PostgreSQL folds to a constant at plan time, so the index
 * is used normally. {@code TalkHistoryLoadTest#lpadOnAParameterDoesNotPreventIndexUse} prints all
 * three plans. The truncation is real and {@code LpadTruncationTest} proves it by execution, so the
 * decision stands — only the fact that one justification was refuted by measurement is recorded.</p>
 *
 * <h2>폭이 설정값인 이유 / why the widths are configuration</h2>
 * <p>이 환경에서는 운영급 데이터에 접근할 수 없다(RISK-T13 — Docker 금지). 작업 T1-01b 가
 * DBA 를 통해 실측하기 전까지 폭은 <b>관측된 20자리와 레거시의 10자리 목표</b>에서
 * 기본값을 취한다. 결정적으로, 입력이 설정 폭을 넘으면 <b>잘라내지 않고 WARN 으로
 * 알린다</b> — 스스로 알리는 추측은 조용히 잘라내는 추측과 같은 결함이 아니다.</p>
 * <p>Production-like data is not reachable from this environment (RISK-T13 — Docker is prohibited).
 * Until task T1-01b measures them via a DBA, the widths default from <b>the observed 20 characters
 * and the legacy's 10-character target</b>. Crucially, an input exceeding the configured width is
 * <b>reported at WARN rather than truncated</b>: a guess that announces itself is not the same
 * defect as a guess that truncates.</p>
 *
 * // source: biztalk_admin_30.js — getDat(): isTuno.padStart(20,'0')
 * // source: biztalk_admin_32_l001_act.jsp — StringUtils.stripStart(serialNum, "0")
 * // source: IDO.KKB_AT_MSG_L001 — SERIALNUM = LPAD(:SERIALNUM,10,'0')
 * // source: IDO.KKB_FT_MSG_L001 — SERIALNUM = :SERIALNUM
 * // req: FR-TLK-009, FR-TLKD-009, ADR-TLK-025, AMB-T04
 */
public final class TransactionSerial {

    private static final Logger log = LoggerFactory.getLogger(TransactionSerial.class);

    /**
     * {@code FT_APITR_HSTR.IS_TUNO} 의 저장 폭. 화면 캡처에서 관측된 20자리.
     * The stored width of {@code FT_APITR_HSTR.IS_TUNO}; 20, as observed in the screenshot.
     *
     * <p>T1-01b 가 실측으로 확정한다. / Confirmed by measurement in T1-01b.</p>
     */
    public static final int DEFAULT_TRANSACTION_WIDTH = 20;

    /**
     * {@code KKO_MSG.SERIALNUM} 의 저장 폭. 레거시 {@code LPAD(…,10,'0')} 이 암시하는 10자리.
     * The stored width of {@code KKO_MSG.SERIALNUM}; 10, as implied by the legacy's
     * {@code LPAD(…,10,'0')}.
     *
     * <p>T1-01b 가 실측으로 확정한다. 이 값이 실제보다 작으면 잘림이 아니라 WARN 이 난다.
     * Confirmed by measurement in T1-01b. If it is smaller than reality the result is a WARN,
     * not a truncation.</p>
     */
    public static final int DEFAULT_MESSAGE_WIDTH = 10;

    private final String digits;
    private final int transactionWidth;
    private final int messageWidth;

    private TransactionSerial(String digits, int transactionWidth, int messageWidth) {
        this.digits = digits;
        this.transactionWidth = transactionWidth;
        this.messageWidth = messageWidth;
    }

    /**
     * 기본 폭으로 거래일련번호를 해석한다. / Parses a serial with the default widths.
     *
     * @param raw 사용자 입력 또는 행에서 온 값 / a user- or row-supplied value
     * @return 해석된 일련번호, 값이 없으면 empty / the parsed serial, empty when absent
     * @throws InvalidSerialException 숫자가 아닌 값 / when the value is not numeric
     */
    // req: FR-TLK-009
    public static Optional<TransactionSerial> parse(String raw) {
        return parse(raw, DEFAULT_TRANSACTION_WIDTH, DEFAULT_MESSAGE_WIDTH);
    }

    /**
     * 지정한 폭으로 거래일련번호를 해석한다. / Parses a serial with explicit widths.
     *
     * <p>빈 값은 오류가 아니라 <b>조건 없음</b>이다 — 거래일련번호는 선택 조건이므로
     * {@code Optional.empty()} 를 돌려준다. 예외로 만들면 검색 조건을 비운 정상 요청이
     * 실패한다.</p>
     * <p>A blank value is not an error but <b>the absence of a predicate</b>: the serial is an
     * optional filter, so {@code Optional.empty()} is returned. Throwing would fail a legitimate
     * request that simply left the field empty.</p>
     *
     * @param raw              입력 값 / the input value
     * @param transactionWidth {@code IS_TUNO} 저장 폭 / the {@code IS_TUNO} stored width
     * @param messageWidth     {@code SERIALNUM} 저장 폭 / the {@code SERIALNUM} stored width
     * @return 해석된 일련번호, 값이 없으면 empty / the parsed serial, empty when absent
     * @throws InvalidSerialException 숫자가 아닌 값 / when the value is not numeric
     */
    // req: FR-TLK-009, FR-TLKD-009
    public static Optional<TransactionSerial> parse(String raw, int transactionWidth, int messageWidth) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (!trimmed.chars().allMatch(Character::isDigit)) {
            throw new InvalidSerialException(trimmed);
        }
        // 선행 0 을 제거해 표준형으로 삼는다. 레거시는 경로마다 0 을 붙이거나 떼거나 했고,
        // 그 비대칭이 D-T25 다. 표준형을 하나 정하고 렌더링에서만 폭을 맞춘다.
        // Leading zeros are stripped to form the canonical value. The legacy added or removed them
        // per path, and that asymmetry is D-T25: one canonical form, width applied at render time.
        String digits = trimmed.replaceFirst("^0+", "");
        if (digits.isEmpty()) {
            digits = "0";
        }
        return Optional.of(new TransactionSerial(digits, transactionWidth, messageWidth));
    }

    /**
     * {@code FT_APITR_HSTR.IS_TUNO} 에 바인딩할 형태를 반환한다.
     * Returns the form to bind against {@code FT_APITR_HSTR.IS_TUNO}.
     *
     * @return 0 으로 채운 거래일련번호 / the zero-padded transaction serial
     */
    // req: FR-TLK-009
    public String transactionForm() {
        return pad(transactionWidth, "IS_TUNO");
    }

    /**
     * {@code KKO_MSG.SERIALNUM} / {@code KKF_MSG.SERIALNUM} 에 바인딩할 형태를 반환한다.
     * Returns the form to bind against {@code SERIALNUM}.
     *
     * @return 0 으로 채운 메시지 일련번호 / the zero-padded message serial
     */
    // req: FR-TLKD-009
    public String messageForm() {
        return pad(messageWidth, "SERIALNUM");
    }

    /**
     * 정규화된 숫자열을 반환한다. / Returns the canonical digit string.
     *
     * @return 선행 0 을 제거한 값 / the value with leading zeros removed
     */
    // req: FR-TLK-009
    public String canonical() {
        return digits;
    }

    /**
     * 지정한 폭으로 0 을 채운다. 폭을 넘으면 <b>잘라내지 않고</b> WARN 을 남긴다.
     * Left-pads to the given width; an over-width value is <b>not truncated</b> but logged at WARN.
     *
     * <p>레거시의 {@code lpad(…,10,'0')} 은 여기서 잘라냈고, 그 결과 다른 거래의 메시지에
     * 일치했다(D-T9). 폭 설정이 틀렸을 가능성이 남아 있는 동안(T1-01b 미완) 잘라내는 것은
     * <b>조용히 틀린 답</b>을 주는 것이고, 넘겨보내는 것은 <b>일치하지 않는</b> 답을 준다.
     * 후자는 0건으로 드러나고, 전자는 드러나지 않는다.</p>
     * <p>The legacy's {@code lpad(…,10,'0')} truncated here and consequently matched another
     * transaction's messages (D-T9). While the configured width may still be wrong (T1-01b
     * outstanding), truncating gives a <b>quietly wrong</b> answer and passing the value through
     * gives a <b>non-matching</b> one. The latter shows up as zero rows; the former does not.</p>
     */
    private String pad(int width, String column) {
        if (digits.length() > width) {
            // ⚠ 조용히 잘라내지 않는다. 설정 폭이 실제보다 작다는 신호다 — T1-01b 의 입력.
            // Never truncate silently: this is the signal that the configured width is too small,
            // and it is an input to T1-01b.
            log.warn("거래일련번호 길이({})가 {}의 설정 폭({})을 초과했습니다. 잘라내지 않고 "
                            + "원값으로 조회합니다 — 설정 폭 확인이 필요합니다(T1-01b, D-T9). / "
                            + "Serial length {} exceeds the configured width {} for {}; querying "
                            + "with the untruncated value. The configured width needs checking "
                            + "(T1-01b, D-T9).",
                    digits.length(), column, width, digits.length(), width, column);
            return digits;
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionSerial that)) {
            return false;
        }
        return digits.equals(that.digits)
                && transactionWidth == that.transactionWidth
                && messageWidth == that.messageWidth;
    }

    @Override
    public int hashCode() {
        return digits.hashCode() * 31 + transactionWidth * 7 + messageWidth;
    }

    /**
     * 감사·로그용 표현. 일련번호는 PII 가 아니므로 그대로 남긴다.
     * Representation for audit and logging; a serial is not PII, so it is shown in full.
     *
     * @return 정규화된 값 / the canonical value
     */
    @Override
    public String toString() {
        return digits;
    }

    /**
     * 숫자가 아닌 거래일련번호가 들어올 때 던진다.
     * Thrown when a non-numeric transaction serial is supplied.
     *
     * <p>비검사 예외다. 레거시는 어떤 문자열이든 그대로 바인딩했고, 숫자 컬럼 비교에서
     * 무슨 일이 일어나는지는 DB 에 달려 있었다.</p>
     * <p>Unchecked. The legacy bound any string verbatim and left the outcome of comparing it to a
     * numeric column to the database.</p>
     *
     * // req: FR-TLK-009, FR-TLK-014
     */
    public static class InvalidSerialException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param value 문제가 된 값 / the offending value
         */
        public InvalidSerialException(String value) {
            super("거래일련번호는 숫자여야 합니다: '" + value + "' / "
                    + "The transaction serial must be numeric: '" + value + "'");
        }
    }
}
