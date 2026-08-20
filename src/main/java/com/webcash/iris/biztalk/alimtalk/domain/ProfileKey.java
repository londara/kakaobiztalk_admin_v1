package com.webcash.iris.biztalk.alimtalk.domain;

import java.util.Objects;

/**
 * 카카오 발신프로필키 — 로그에 남지 않는 자격증명. / Kakao sender profile key, unloggable by type.
 *
 * <h2>왜 래퍼인가 / why a wrapper</h2>
 * <p>레거시는 이 값을 JSP 안에 하드코딩했고({@code sender_key = "17da29…2921"}, 두 곳,
 * "우선 임시로 넣어둔다"는 주석과 함께), 매 발송마다 요청 전체를 로그에 직렬화했다 —
 * 즉 자격증명이 소스 저장소와 로그 저장소에 동시에 남았다(D-A24, D-A30). 게다가 화면 61 은
 * 운영자에게 이 키를 <b>직접 입력</b>하게 했으므로, 키는 사람들 사이에서 복사·붙여넣기로
 * 돌아다녔다.</p>
 * <p>The legacy hardcoded this value in a JSP (twice, commented "putting it in temporarily"), and
 * serialised the whole request into the log on every send — so the credential sat in both the
 * source repository and the log store (D-A24, D-A30). Screen 61 additionally asked operators to
 * <b>type it in</b>, so it circulated among people as a copy-paste string.</p>
 *
 * <p>단순히 그 로그 한 줄을 지우는 것으로는 부족하다. <b>결함은 부주의한 로그 한 줄이었고,
 * 고칠 대상은 그 한 줄을 무해하게 만드는 것이다.</b> {@link #toString()} 이 값을 감추므로,
 * 누군가 나중에 {@code log.debug(request)} 를 다시 쓴다 해도 D-A30 은 재현되지 않는다.</p>
 * <p>Deleting that log line is not enough. <b>The defect was one careless log statement; the fix is
 * making that statement harmless rather than forbidding it.</b> Because {@link #toString()} hides
 * the value, a future {@code log.debug(request)} cannot reproduce D-A30.</p>
 *
 * <p>원문은 {@link #exposeForVendorCall()} 로만 얻을 수 있고, 그 이름은 호출부 코드 리뷰에서
 * 눈에 띄도록 의도적으로 길다.</p>
 * <p>The raw value is reachable only through {@link #exposeForVendorCall()}, deliberately named to
 * be conspicuous at the call site during review.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — imoIn.put("sender_key", "17da29…"); logger.debug(imoIn)
 * // req: FR-ATS-003, FR-AZ-A05, NFR-SEC-CRED-A01
 */
public final class ProfileKey {

    /** 로그·직렬화에 노출되는 대체 문자열. / The substitute shown to logs and serialisation. */
    static final String REDACTED = "ProfileKey[REDACTED]";

    private final String value;

    private ProfileKey(String value) {
        this.value = value;
    }

    /**
     * 프로파일키를 감싼다. / Wraps a profile key.
     *
     * @param value 원문 키 / the raw key, non-blank
     * @return 감싼 키 / the wrapped key
     * @throws IllegalArgumentException 값이 비었거나 계약 길이를 넘으면 / when blank or over the contract length
     *
     * // req: FR-ATS-003, FR-ATC-005
     */
    public static ProfileKey of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("profile key must not be blank");
        }
        if (value.length() > AlimTalkLimits.CONTRACT_SENDER_KEY) {
            // 예외 메시지에 값을 담지 않는다 — 예외 메시지도 로그로 흘러간다.
            // The message carries no value: exception text reaches logs too.
            throw new IllegalArgumentException(
                    "profile key exceeds contract length " + AlimTalkLimits.CONTRACT_SENDER_KEY);
        }
        return new ProfileKey(value);
    }

    /**
     * 벤더 호출을 위해 원문을 노출한다. / Exposes the raw key for the vendor call.
     *
     * <p>이 메서드의 호출부는 {@code CooconAlertClient} 하나여야 한다. 이름이 긴 이유는
     * 다른 곳에서 호출되면 리뷰에서 즉시 보이도록 하기 위함이다.</p>
     * <p>The only call site should be {@code CooconAlertClient}. The name is long so that any
     * other caller is immediately visible in review.</p>
     *
     * @return 원문 키 / the raw key
     *
     * // req: FR-ATS-003
     */
    public String exposeForVendorCall() {
        return value;
    }

    /**
     * 값을 감춘 표현을 돌려준다. / Returns a representation with the value hidden.
     *
     * <p>이것이 이 클래스의 존재 이유다. / This is the class's reason for existing.</p>
     *
     * @return 항상 {@value #REDACTED} / always {@value #REDACTED}
     *
     * // req: NFR-SEC-CRED-A01
     */
    @Override
    public String toString() {
        return REDACTED;
    }

    /**
     * Jackson 직렬화 값 — <b>의도적으로 가려진 값</b>. / The Jackson serialisation value, <b>deliberately redacted</b>.
     *
     * <p>이 선택은 처음 보면 잘못된 것처럼 보인다. 벤더에 보낼 payload 에는 원문 키가 있어야
     * 하는데, JSON 직렬화가 가려진 값을 내놓으면 payload 가 틀리지 않는가?</p>
     * <p>The choice looks wrong at first glance: the vendor payload needs the raw key, so how can
     * JSON serialisation emit a redacted one?</p>
     *
     * <p>답은 <b>Jackson 이 전송 경로가 아니라는 것</b>이다. 실제 payload 는
     * {@code RsmsEnvelope} 가 {@link #exposeForVendorCall()} 을 호출해 조립한다. Jackson 직렬화는
     * 진단·테스트·실수로 남긴 로그에만 쓰이며, 그 모든 경우에 원문이 나오지 않아야 한다.
     * 기본값을 안전한 쪽으로 두고 원문이 필요한 한 군데에서만 명시적으로 꺼내는 것이
     * D-A30 을 <b>구조적으로</b> 막는 방법이다.</p>
     * <p>The answer is that <b>Jackson is not the wire path</b>. The real payload is assembled by
     * {@code RsmsEnvelope} calling {@link #exposeForVendorCall()}. Jackson serialisation is used for
     * diagnostics, tests and logs left behind by accident — in all of which the raw value must not
     * appear. Making the default safe and requiring one explicit call where the raw value is needed
     * is what makes D-A30 <b>structurally</b> unreachable.</p>
     *
     * @return 가려진 문자열 / the redacted string
     *
     * // req: NFR-SEC-CRED-A01, NFR-SEC-PII-A02
     */
    @com.fasterxml.jackson.annotation.JsonValue
    public String jsonValue() {
        return REDACTED;
    }

    /**
     * 값 동등성. / Value equality.
     *
     * @param o 비교 대상 / the object to compare
     * @return 같은 키를 감싸면 {@code true} / {@code true} when wrapping the same key
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ProfileKey other && value.equals(other.value);
    }

    /**
     * 값 해시. / Value hash.
     *
     * @return 해시 / the hash
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
