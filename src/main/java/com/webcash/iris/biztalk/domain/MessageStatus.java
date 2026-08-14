package com.webcash.iris.biztalk.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 발송 상태 코드. / Send status code.
 *
 * <p>레거시 그리드 렌더러는 {@code 1/2/3/4/6} 만 라벨로 변환하고 <b>그 외 값에는 아무것도
 * 반환하지 않아 빈 칸으로 표시</b>했다. {@code 5} 는 어떤 분기에도 없다 — 폐기된 상태인지
 * 렌더러의 누락인지 소스만으로는 알 수 없다(AMB-05, 도메인 담당자 확인 대기).</p>
 * <p>The legacy grid renderer mapped only {@code 1/2/3/4/6} and returned nothing for anything
 * else, showing a blank cell. {@code 5} appears in no branch — whether a retired state or a
 * gap in the renderer cannot be told from source alone (AMB-05, awaiting the domain owner).</p>
 *
 * <p>PM 결정(AMB-01)에 따라 미인식 값은 <b>원값을 그대로 표시</b>한다. 빈 칸은 데이터가
 * 없는 것처럼 보이게 하여 조사 자체를 막는다.</p>
 * <p>Per PM decision AMB-01 an unmapped value is <b>displayed verbatim</b>: a blank cell makes
 * the data look absent and prevents the question being asked at all.</p>
 *
 * // source: biztalk_admin_40.js — STATUS renderer, values 1/2/3/4/6
 * // source: biztalk_admin_40_view.jsp — select options 미전송/전송완료/톡결과수신/문자결과수신/큐입력
 * // req: FR-MSG-005
 */
public enum MessageStatus {

    /** 미전송 / not yet sent. */
    NOT_SENT("1", "미전송"),
    /** 전송완료 / sent. */
    SENT("2", "전송완료"),
    /** 톡결과수신 / Kakao delivery receipt received. */
    TALK_RESULT_RECEIVED("3", "톡결과수신"),
    /** 문자결과수신 / SMS delivery receipt received. */
    SMS_RESULT_RECEIVED("4", "문자결과수신"),
    /** 큐입력 / queued. */
    QUEUED("6", "큐입력");

    private final String code;
    private final String label;

    MessageStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * DB 컬럼값을 반환한다. / Returns the database column value.
     *
     * @return 상태 코드 / the status code
     */
    public String code() {
        return code;
    }

    /**
     * 화면 표시 라벨을 반환한다. / Returns the display label.
     *
     * @return 라벨 / the label
     */
    public String label() {
        return label;
    }

    /**
     * 코드를 열거형으로 변환한다. / Resolves a code.
     *
     * @param code 상태 코드 / the status code
     * @return 해당 상태 또는 empty / the matching status, or empty
     */
    public static Optional<MessageStatus> fromCode(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
    }

    /**
     * 표시용 라벨을 반환한다. 미인식 코드는 원값을 그대로 반환한다.
     * Returns a display label, falling back to the raw code when unrecognised.
     *
     * <p>레거시는 이 경우 빈 문자열을 반환했다(결함이라기보다 누락). 원값을 보여주면
     * 최소한 "5 라는 값이 존재한다"는 사실이 사용자와 담당자에게 드러난다.</p>
     * <p>The legacy returned an empty string here. Showing the raw value at least surfaces the
     * fact that a value such as {@code 5} exists.</p>
     *
     * @param code 상태 코드 / the status code
     * @return 라벨 또는 원값 / the label, or the raw code
     */
    // source: biztalk_admin_40.js — renderer returns undefined for unmapped values
    // req: FR-MSG-005, AMB-05
    public static String labelOrRaw(String code) {
        return fromCode(code).map(MessageStatus::label).orElse(code == null ? "" : code);
    }
}
