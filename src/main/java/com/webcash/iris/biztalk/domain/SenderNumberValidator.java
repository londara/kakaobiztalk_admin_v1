package com.webcash.iris.biztalk.domain;

/**
 * 발신번호 형식 검증. / Sender-number format validation.
 *
 * <p><b>이 클래스는 레거시에서 서버 측에 존재하지 않던 규칙을 구현한다.</b> 등록 화면
 * {@code biztalk_admin_12_view.jsp} 는 사용자에게 세 가지 규칙을 고지하지만, 그중 실제로
 * 구현된 것은 길이 규칙뿐이었다.</p>
 * <p><b>This class implements rules the legacy never enforced server-side.</b> The registration
 * screen states three rules to the user; only the length rule was ever implemented.</p>
 *
 * <table>
 *   <caption>레거시 상태 / legacy state</caption>
 *   <tr><th>화면 고지 / stated to the user</th><th>레거시 구현 / legacy implementation</th></tr>
 *   <tr><td>8~11자리, 030/050 은 12자리까지</td><td>{@code isValidDpNo()} — 구현됨 / implemented</td></tr>
 *   <tr><td>15xx·16xx 는 8자리만</td><td>{@code isValidDpNo()} — 구현됨 / implemented</td></tr>
 *   <tr><td>112·114·1335 등 특수번호 불가</td><td><b>어디에도 없음 / nowhere</b> (D-S12)</td></tr>
 *   <tr><td>숫자만 / digits only</td><td><b>서버 검사 없음 / no server check</b> (D-S13)</td></tr>
 * </table>
 *
 * <p>숫자 검사가 없다는 사실은 클라이언트 검증이 무력했다는 점과 겹쳐 심각해진다.
 * {@code biztalk_admin_12.js} 는 존재하지 않는 요소({@code #ATK}, {@code #BRNO},
 * {@code #IS_ENGNM})를 검사했고, {@code $(없는요소).val()} 은 {@code undefined} 를 반환하며
 * {@code undefined == ""} 는 거짓이므로 <b>모든 검사가 통과</b>했다. 선언된
 * {@code validate[custom[number]]} 규칙도 {@code validationEngine} 이 초기화만 되고 호출되지
 * 않아 실행된 적이 없다(D-S11). 결과적으로 {@code abcdefgh} 는 등록 가능했다.</p>
 * <p>The missing digit check compounds with vacuous client validation: the page checked elements
 * that do not exist, and {@code undefined == ""} is false, so every check passed. The declared
 * {@code number} rule never ran because {@code validationEngine} was initialised but never
 * invoked (D-S11). The net effect is that {@code abcdefgh} was registrable.</p>
 *
 * <p>금지 번호 목록은 이 클래스가 갖지 않는다 — {@link BarredNumbers} 가 배포 자산에서 읽어
 * 오며, 그래야 규제 목록이 릴리스 없이 갱신된다(CONST-BIZ-D03, ADR-SND-021).</p>
 * <p>The barred list does not live here: {@link BarredNumbers} loads it from a deployment asset so
 * that a regulatory list can be corrected without a release (CONST-BIZ-D03, ADR-SND-021).</p>
 *
 * // source: biztalk_admin_12_c001_act.jsp — isValidDpNo(); biztalk_admin_12_view.jsp — infoList01
 * // req: FR-SNDC-005, FR-SNDC-006, FR-SNDC-010, FR-SNDC-013, CONST-BIZ-D03
 */
public final class SenderNumberValidator {

    private SenderNumberValidator() {
    }

    /**
     * 발신번호가 등록 가능한 형식인지 검증한다.
     * Validates that a sender number is registrable.
     *
     * <p>검증 순서는 의도적이다. 숫자 여부를 먼저 확인해야 이후의 접두어·길이 판정이
     * 의미를 갖는다.</p>
     * <p>The order is deliberate: the digit check must precede the prefix and length rules for
     * those rules to mean anything.</p>
     *
     * <p>금지 번호 목록을 <b>인수로 받는다.</b> 상수로 두면 목록을 배포 자산으로 옮긴 의미가
     * 사라지고(CONST-BIZ-D03), 검증기가 어떤 목록으로 판정했는지도 호출부에서 보이지 않는다.
     * 이 메서드는 순수 함수로 남고, 목록을 고르는 일은 {@link BarredNumbers} 가 한다.</p>
     * <p>The barred list arrives as an <b>argument</b>. Holding it as a constant would undo the
     * point of moving it into a deployment asset (CONST-BIZ-D03) and would hide which list a
     * verdict was reached against. This method stays a pure function; choosing the list is
     * {@link BarredNumbers}' job.</p>
     *
     * @param number 검증할 발신번호 / the sender number to validate
     * @param barred 금지 번호 목록 / the barred-number list
     * @return 검증 결과 / the validation outcome
     */
    // source: biztalk_admin_12_c001_act.jsp — isValidDpNo()
    // req: FR-SNDC-005, FR-SNDC-006, FR-SNDC-010, CONST-BIZ-D03
    public static Result validate(String number, BarredNumbers barred) {
        if (number == null || number.isBlank()) {
            return Result.REQUIRED;
        }
        String value = number.trim();

        // D-S13 — 레거시에는 이 검사가 없었다. 길이만 맞으면 문자열이 통과했다.
        // D-S13 — absent from the legacy: any string of the right length passed.
        //
        // Character.isDigit() 를 쓰지 않는다. 그 메서드는 전각 숫자(U+FF10~U+FF19)와 여러
        // 유니코드 십진 숫자에 true 를 반환하므로, 겉보기에 같은 번호가 ASCII 와 다른
        // 바이트열로 저장된다. 그렇게 저장된 값은 decrypt(DP_NO) = :DP_NO 비교에서 ASCII
        // 입력과 결코 일치하지 않는다 — 발송 경로의 검증까지 포함해서다. 즉 D-S1 과 같은
        // 계열의 "저장은 되는데 조회는 안 되는" 상태를 새로 만들어 낸다.
        //
        // Character.isDigit() is deliberately not used: it returns true for full-width digits
        // (U+FF10–U+FF19) and other Unicode decimal digits, so a visually identical number would
        // be stored as a different byte sequence and could never match an ASCII input under
        // decrypt(DP_NO) = :DP_NO — including in the send path's own check. That would recreate
        // the D-S1 class of defect: storable but unmatchable.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return Result.NOT_NUMERIC;
            }
        }

        // D-S12 — 화면은 고지했고 코드는 구현하지 않았다. 길이 규칙이 이를 대신하지 못한다:
        // 112 는 8자리 미만이라 우연히 걸리지만 1335 는 그렇지 않다.
        // D-S12 — stated to users, implemented nowhere. The length rule does not cover it:
        // 112 is caught incidentally by the minimum length, but 1335 is not.
        if (barred.contains(value)) {
            return Result.BARRED;
        }

        if (value.length() < 8) {
            return Result.TOO_SHORT;
        }

        // 15xx·16xx 대표번호는 정확히 8자리여야 한다.
        // 15xx/16xx representative numbers must be exactly 8 digits.
        String twoDigitPrefix = value.substring(0, 2);
        if ("15".equals(twoDigitPrefix) || "16".equals(twoDigitPrefix)) {
            return value.length() == 8 ? Result.VALID : Result.BAD_REPRESENTATIVE_LENGTH;
        }

        // 030·050 은 12자리까지, 그 외는 11자리까지.
        // 030/050 allow up to 12 digits; everything else up to 11.
        String threeDigitPrefix = value.substring(0, 3);
        int maxLength = ("030".equals(threeDigitPrefix) || "050".equals(threeDigitPrefix)) ? 12 : 11;

        return value.length() <= maxLength ? Result.VALID : Result.TOO_LONG;
    }

    /**
     * 검증 결과. / The validation outcome.
     *
     * <p>불리언이 아니라 열거형인 이유는 NFR-USE-D02 다. 레거시는 어떤 규칙을 어겼는지와
     * 무관하게 {@code "등록중 오류 발생."} 한 문장만 보여 주었다.</p>
     * <p>An enum rather than a boolean because of NFR-USE-D02: the legacy showed the single
     * sentence {@code "등록중 오류 발생."} regardless of which rule was broken.</p>
     */
    // req: NFR-USE-D02
    public enum Result {
        /** 통과 / accepted. */
        VALID("발신번호가 유효합니다."),
        /** 미입력 / not supplied. */
        REQUIRED("발신번호를 입력해 주세요."),
        /** 숫자가 아닌 문자 포함 / contains a non-digit. */
        NOT_NUMERIC("발신번호는 숫자만 입력할 수 있습니다."),
        /** 특수·긴급번호 / a special or emergency number. */
        BARRED("특수번호 및 긴급번호는 발신번호로 등록할 수 없습니다."),
        /** 8자리 미만 / shorter than 8 digits. */
        TOO_SHORT("발신번호는 8자리 이상이어야 합니다."),
        /** 허용 길이 초과 / longer than the permitted maximum. */
        TOO_LONG("발신번호 길이가 허용 범위를 초과했습니다. (030·050 은 12자리, 그 외는 11자리)"),
        /** 15xx·16xx 인데 8자리가 아님 / a 15xx/16xx number that is not exactly 8 digits. */
        BAD_REPRESENTATIVE_LENGTH("15xx·16xx 대표번호는 8자리만 등록할 수 있습니다.");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        /**
         * 사용자에게 보여 줄 메시지를 반환한다. / Returns the message to show the user.
         *
         * @return 메시지 / the message
         */
        // req: NFR-USE-D02
        public String message() {
            return message;
        }

        /**
         * 통과 여부를 반환한다. / Whether validation passed.
         *
         * @return 통과면 true / true when accepted
         */
        // req: FR-SNDC-003
        public boolean accepted() {
            return this == VALID;
        }
    }
}
