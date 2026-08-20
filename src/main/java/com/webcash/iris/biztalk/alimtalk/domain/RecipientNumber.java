package com.webcash.iris.biztalk.alimtalk.domain;

import java.util.Objects;

/**
 * 수신 전화번호 — 로그에 남지 않는 개인정보. / A recipient phone number, unloggable by type.
 *
 * <p>{@link ProfileKey} 와 같은 장치를 개인정보에 적용한다. 레거시는 발송마다 요청 전체를
 * {@code debug} 로 직렬화했으므로 모든 수신번호가 애플리케이션 로그에 남았다(D-A30).
 * 마스킹 함수를 "쓰기로 약속"하는 대신 타입이 강제한다.</p>
 * <p>The same device as {@link ProfileKey}, applied to personal data. The legacy serialised the
 * whole request at {@code debug} on every send, so every recipient number landed in the
 * application log (D-A30). Rather than agreeing to call a masking function, the type enforces it.</p>
 *
 * <p>{@link #toString()} 은 마스킹된 형태를 돌려주므로 로그·예외 메시지·컬렉션 출력 어디서도
 * 평문이 나오지 않는다. 화면 표시용 마스킹도 같은 메서드를 쓴다(NFR-SEC-PII-A01).</p>
 * <p>{@link #toString()} returns the masked form, so no log line, exception message or collection
 * dump can print the number in clear. UI masking uses the same method (NFR-SEC-PII-A01).</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — logger.debug("[BIZTALK_50] " + imoIn.toJSONString())
 * // req: NFR-SEC-PII-A01, NFR-SEC-PII-A02, CONST-LEGAL-01
 */
public final class RecipientNumber {

    private final String value;

    private RecipientNumber(String value) {
        this.value = value;
    }

    /**
     * 이미 형식 검증을 통과한 번호를 감싼다. / Wraps a number that has already passed validation.
     *
     * <p>형식 검증은 {@link RecipientParser} 의 책임이다. 여기서 다시 검사하지 않는 것은
     * 의도적이다 — 검증 규칙이 두 곳에 있으면 둘이 어긋나고, 어긋난 쪽이 D-A28 이 된다.</p>
     * <p>Format validation belongs to {@link RecipientParser}. Not re-checking here is deliberate:
     * a rule in two places drifts, and the drifting copy becomes D-A28.</p>
     *
     * @param value 숫자만으로 이루어진 번호 / a digits-only number
     * @return 감싼 번호 / the wrapped number
     * @throws IllegalArgumentException 값이 비었으면 / when blank
     *
     * // req: FR-ATS-005
     */
    public static RecipientNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("recipient number must not be blank");
        }
        return new RecipientNumber(value);
    }

    /**
     * 벤더 호출을 위해 원문을 노출한다. / Exposes the raw number for the vendor call.
     *
     * @return 원문 번호 / the raw number
     *
     * // req: FR-ATC-006
     */
    public String exposeForVendorCall() {
        return value;
    }

    /**
     * 마스킹된 표현을 돌려준다. / Returns the masked representation.
     *
     * <p>가운데 자릿수를 가리고 앞 3자리와 뒤 4자리를 남긴다. 8자리 이하이면 뒤 4자리만
     * 남긴다 — 짧은 번호에서 앞자리까지 남기면 마스킹이 사실상 무의미해진다.</p>
     * <p>Hides the middle, keeping the first three and last four digits. For values of eight digits
     * or fewer only the last four are kept — retaining a prefix on a short number would make the
     * masking close to meaningless.</p>
     *
     * @return 마스킹된 번호 / the masked number
     *
     * // req: NFR-SEC-PII-A01, NFR-SEC-PII-A02
     */
    @Override
    public String toString() {
        int length = value.length();
        if (length <= 4) {
            return "*".repeat(length);
        }
        if (length <= 8) {
            return "*".repeat(length - 4) + value.substring(length - 4);
        }
        return value.substring(0, 3) + "*".repeat(length - 7) + value.substring(length - 4);
    }

    /**
     * Jackson 직렬화 값 — 마스킹된 형태. / The Jackson serialisation value, masked.
     *
     * <p>{@link ProfileKey#jsonValue()} 와 같은 이유로 기본값을 안전한 쪽에 둔다. 벤더로 나가는
     * 실제 번호는 {@code RsmsEnvelope} 가 {@link #exposeForVendorCall()} 로 꺼낸다.</p>
     * <p>The default is safe for the same reason as {@link ProfileKey#jsonValue()}. The real number
     * leaves through {@code RsmsEnvelope} calling {@link #exposeForVendorCall()}.</p>
     *
     * @return 마스킹된 번호 / the masked number
     *
     * // req: NFR-SEC-PII-A02
     */
    @com.fasterxml.jackson.annotation.JsonValue
    public String jsonValue() {
        return toString();
    }

    /**
     * 값 동등성 — 중복 제거에 사용된다. / Value equality, used for de-duplication.
     *
     * @param o 비교 대상 / the object to compare
     * @return 같은 번호이면 {@code true} / {@code true} when the same number
     *
     * // req: FR-ATC-012
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof RecipientNumber other && value.equals(other.value);
    }

    /**
     * 값 해시 — {@code LinkedHashSet} 중복 제거의 근거. / Value hash, the basis of set de-duplication.
     *
     * @return 해시 / the hash
     *
     * // req: FR-ATC-012
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
