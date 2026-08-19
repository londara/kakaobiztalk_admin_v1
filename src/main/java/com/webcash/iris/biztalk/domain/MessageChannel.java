package com.webcash.iris.biztalk.domain;

/**
 * 집계 채널. / An aggregated message channel.
 *
 * <p>컬럼 접두어는 {@code KKB_APITR_SMTN} 의 것을 그대로 쓴다. 매퍼의
 * {@code columnPrefix} 와 일치해야 하므로 <b>이름을 바꾸면 매핑이 조용히 깨진다</b>.</p>
 * <p>The prefixes are the table's own. They must match the mapper's {@code columnPrefix},
 * so <b>renaming one silently breaks the mapping</b>.</p>
 *
 * <h2>친구톡 이미지의 두 채널 / the two friend-talk image channels</h2>
 * <p>{@code FTIMG_*} 는 <b>와이드를 포함한</b> 전체 이미지 건수다. 일반 이미지는 저장된
 * 값이 아니라 {@code FTIMG_* − FTIMGWI_*} 로 계산한다(CONST-BIZ-R01). 이 파생을 잃으면
 * 일반과 와이드가 이중 계상된다.</p>
 * <p>{@code FTIMG_*} counts <b>all</b> images including wide ones. The normal-image figure is
 * derived as {@code FTIMG_* − FTIMGWI_*} rather than stored (CONST-BIZ-R01); losing the
 * derivation double-counts normal and wide.</p>
 *
 * // source: IDO.KKB_APITR_SMTN_L001, IDO.KKB_APITR_SMTN_L002
 * // req: FR-RPT-009, CONST-BIZ-R01
 */
public enum MessageChannel {

    /** 알림톡 / notification talk. */
    ALIMTALK("AT", "알림톡"),

    /** 친구톡 텍스트 / friend talk, text. */
    FRIEND_TEXT("FTTXT", "친구톡(txt)"),

    /** 친구톡 일반 이미지 — 파생값 / friend talk, normal image (derived). */
    FRIEND_IMAGE("FTIMG", "친구톡(img-일반)"),

    /** 친구톡 와이드 이미지 / friend talk, wide image. */
    FRIEND_WIDE_IMAGE("FTIMGWI", "친구톡(img-와이드)"),

    /** SMS. */
    SMS("SMS", "sms"),

    /** LMS. */
    LMS("LMS", "lms"),

    /** MMS. */
    MMS("MMS", "mms");

    private final String columnPrefix;
    private final String label;

    MessageChannel(String columnPrefix, String label) {
        this.columnPrefix = columnPrefix;
        this.label = label;
    }

    /**
     * 테이블 컬럼 접두어를 반환한다. / Returns the table's column prefix.
     *
     * @return 접두어 / the prefix
     */
    // source: KKB_APITR_SMTN column naming
    public String columnPrefix() {
        return columnPrefix;
    }

    /**
     * 화면 표시명을 반환한다. / Returns the display label.
     *
     * @return 표시명 / the label
     */
    // req: FR-RPT-009
    public String label() {
        return label;
    }
}
