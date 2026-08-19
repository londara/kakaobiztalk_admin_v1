package com.webcash.iris.biztalk.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * API 거래 처리 상태 ({@code PRSU}). / API transaction processing status ({@code PRSU}).
 *
 * <h2>레거시가 두 곳에서 따로 정한 것 / what the legacy decided in two places</h2>
 * <p>필터 라디오 버튼은 {@code CodeUtil.makeRadio(request, "PRSU", …)} 로 <b>코드 테이블에서</b>
 * 생성되었고, 그리드 렌더러는 {@code 0/1/2/9} 를 <b>자바스크립트에 하드코딩</b>했다. 코드
 * 테이블에 값이 하나 추가되면 <b>같은 릴리스에서 필터는 가능해지고 컬럼은 읽을 수 없게</b>
 * 된다 — 렌더러가 그것을 {@code 알수없음} 으로 표시하기 때문이다(D-T29).</p>
 * <p>The filter radios were generated <b>from the code table</b> via
 * {@code CodeUtil.makeRadio(request, "PRSU", …)}, while the grid renderer <b>hardcoded</b>
 * {@code 0/1/2/9} in JavaScript. Add one value to the code table and, <b>in the same release</b>,
 * it becomes filterable and unreadable — the renderer shows it as {@code 알수없음} (D-T29).</p>
 *
 * <p>FR-TLK-004 는 두 집합이 <b>한 출처에서</b> 나오도록 요구한다. 이 열거형이 그 출처다:
 * {@link #filterOptions()} 가 필터의 선택지를, {@link #labelOf(String)} 가 컬럼의 라벨을
 * 만든다. 둘이 갈라지려면 이 파일을 고쳐야 하고, 이 파일을 고치면 둘이 함께 바뀐다.</p>
 * <p>FR-TLK-004 requires both sets to come from <b>one source</b>. This enum is that source:
 * {@link #filterOptions()} produces the filter's options and {@link #labelOf(String)} the column's
 * label. Diverging would require editing this file, and editing it changes both.</p>
 *
 * <p>미인식 코드는 <b>원값을 그대로</b> 표시한다 — {@link MessageStatus} 와 같은 규칙이고 같은
 * 이유다. {@code 알수없음} 은 "값이 있다"는 사실까지 숨겨서 조사 자체를 막는다.</p>
 * <p>An unrecognised code is displayed <b>verbatim</b> — the same rule as {@link MessageStatus},
 * for the same reason: {@code 알수없음} hides even the fact that a value exists, which prevents the
 * question being asked.</p>
 *
 * // source: biztalk_admin_30.js — PRSU renderer: '0'→처리중, '1'→처리완료, '2'→기처리, '9'→오류
 * // source: biztalk_admin_30_view.jsp — CodeUtil.makeRadio(request, "PRSU", "PRSU", "PRSU", "")
 * // req: FR-TLK-004, NFR-USE-T01
 */
public enum TalkStatus {

    /** 처리중 / in flight. */
    IN_PROGRESS("0", "처리중"),
    /** 처리완료 / completed. */
    COMPLETED("1", "처리완료"),
    /** 기처리 / already processed. */
    ALREADY_PROCESSED("2", "기처리"),
    /** 오류 / error. */
    ERROR("9", "오류");

    private final String code;
    private final String label;

    TalkStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * DB 컬럼값을 반환한다. / Returns the database column value.
     *
     * @return 상태 코드 / the status code
     */
    // req: FR-TLK-004
    public String code() {
        return code;
    }

    /**
     * 화면 표시 라벨을 반환한다. / Returns the display label.
     *
     * @return 라벨 / the label
     */
    // req: FR-TLK-004
    public String label() {
        return label;
    }

    /**
     * 코드를 열거형으로 변환한다. / Resolves a code.
     *
     * @param code 상태 코드 / the status code
     * @return 해당 상태 또는 empty / the matching status, or empty
     */
    // req: FR-TLK-004
    public static Optional<TalkStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
    }

    /**
     * 표시용 라벨을 반환한다. 미인식 코드는 원값을 그대로 반환한다.
     * Returns a display label, falling back to the raw code when unrecognised.
     *
     * @param code 상태 코드 / the status code
     * @return 라벨 또는 원값 / the label, or the raw code
     */
    // req: FR-TLK-004, NFR-USE-T01
    public static String labelOf(String code) {
        return fromCode(code).map(s -> s.label()).orElse(code == null ? "" : code);
    }

    /**
     * 필터가 제시할 선택지를 반환한다. / Returns the options the filter offers.
     *
     * <p><b>이 메서드가 존재하는 이유가 D-T29 다.</b> 필터의 선택지와 컬럼의 라벨이 같은
     * 열거형에서 나오므로, 하나를 추가하면 양쪽에 동시에 나타난다. 레거시에서는 필터는
     * 코드 테이블을, 컬럼은 하드코딩된 분기를 읽었다.</p>
     * <p><b>D-T29 is why this method exists.</b> The filter's options and the column's labels come
     * from one enum, so adding a value surfaces it in both at once. In the legacy the filter read a
     * code table and the column read a hardcoded branch.</p>
     *
     * <p>전체(빈 값)는 여기 담지 않는다 — 그것은 상태가 아니라 "조건 없음"이며, 상태 목록에
     * 섞으면 {@link #labelOf(String)} 가 빈 문자열에 라벨을 붙여야 한다.</p>
     * <p>전체 (a blank value) is not included: it is not a status but the absence of a predicate,
     * and mixing it in would make {@link #labelOf(String)} label the empty string.</p>
     *
     * @return 코드와 라벨의 짝, 선언 순서 / code-label pairs in declaration order
     */
    // req: FR-TLK-004
    public static List<FilterOption> filterOptions() {
        return Arrays.stream(values()).map(s -> new FilterOption(s.code, s.label)).toList();
    }

    /**
     * 필터 선택지 하나. / One filter option.
     *
     * @param code  상태 코드 / the status code
     * @param label 표시 라벨 / the display label
     */
    public record FilterOption(String code, String label) {
    }
}
