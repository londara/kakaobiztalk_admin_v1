package com.webcash.iris.biztalk.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 문자내역 조회 조건. / 문자내역 search criteria.
 *
 * <p>이 클래스가 <b>레거시 결함 4건</b>을 담당한다:</p>
 * <table>
 *   <caption>담당 결함 / defects handled here</caption>
 *   <tr><th>결함</th><th>레거시 동작</th><th>이 클래스</th></tr>
 *   <tr><td>D8</td><td>시각만 비교하여 다일 범위를 거절</td><td>날짜+시각 전체 비교</td></tr>
 *   <tr><td>D13</td><td>조회 기간 상한 없음</td><td>31일 상한</td></tr>
 *   <tr><td>D2</td><td>메시지키 검색이 항상 0건</td><td>숫자 검증 후 숫자 비교</td></tr>
 *   <tr><td>D3</td><td>발신/수신번호 라벨이 반대</td><td>필드명으로 의미 고정</td></tr>
 * </table>
 *
 * // source: biztalk_admin_40.js — getDat(), sTime/eTime comparison
 * // source: IDO.KKB_MSG_L002.xml — to_char(MSGKEY,'9'), CASE WHEN filters
 * // req: FR-MSG-002, FR-MSG-008, FR-MSG-009, FR-MSG-012, FR-MSG-013, FR-MSG-015
 */
public final class MessageHistoryCriteria {

    /** 최대 조회 기간. PM 이 G1 에서 승인. / Maximum search window, approved at G1. */
    // req: FR-MSG-013 (AMB-06 resolved at G1)
    public static final Duration MAX_WINDOW = Duration.ofDays(31);

    /** 기본 페이지 크기. / Default page size. */
    // req: NFR-PERF-02
    public static final int DEFAULT_PAGE_SIZE = 50;
    /** 최대 페이지 크기. / Maximum page size. */
    public static final int MAX_PAGE_SIZE = 500;

    private final LocalDateTime from;
    private final LocalDateTime to;
    private final String institutionCode;
    private final Long messageKey;
    private final String senderNumber;
    private final String recipientNumber;
    private final String statusCode;
    private final MessageType messageType;
    private final TableType tableType;
    private final String resultCode;
    private final int page;
    private final int size;

    private MessageHistoryCriteria(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.institutionCode = builder.institutionCode;
        this.messageKey = builder.messageKey;
        this.senderNumber = builder.senderNumber;
        this.recipientNumber = builder.recipientNumber;
        this.statusCode = builder.statusCode;
        this.messageType = builder.messageType;
        this.tableType = builder.tableType;
        this.resultCode = builder.resultCode;
        this.page = builder.page;
        this.size = builder.size;
    }

    /** @return 조회 시작 일시 / the window start */
    public LocalDateTime from() {
        return from;
    }

    /** @return 조회 종료 일시 / the window end */
    public LocalDateTime to() {
        return to;
    }

    /** @return 적용할 이용기관 코드. null 이면 전체 / the 이용기관 code, null for unrestricted */
    public String institutionCode() {
        return institutionCode;
    }

    /** @return 메시지키. null 이면 미지정 / the message key, null when unset */
    public Long messageKey() {
        return messageKey;
    }

    /** @return 발신번호 부분 문자열 / the sender-number fragment */
    public String senderNumber() {
        return senderNumber;
    }

    /** @return 수신번호 부분 문자열 / the recipient-number fragment */
    public String recipientNumber() {
        return recipientNumber;
    }

    /** @return 상태 코드 / the status code */
    public String statusCode() {
        return statusCode;
    }

    /** @return 메시지 유형 / the message type */
    public MessageType messageType() {
        return messageType;
    }

    /** @return 문자타입 / the table type */
    public TableType tableType() {
        return tableType;
    }

    /** @return 결과 코드 / the result code */
    public String resultCode() {
        return resultCode;
    }

    /** @return 0부터 시작하는 페이지 번호 / the zero-based page number */
    public int page() {
        return page;
    }

    /** @return 페이지 크기 / the page size */
    public int size() {
        return size;
    }

    /** @return SQL OFFSET 값 / the SQL offset */
    // req: FR-MSG-007
    public int offset() {
        return page * size;
    }

    /**
     * 빌더를 생성한다. / Creates a builder.
     *
     * @return 빌더 / a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 조회 조건 빌더. / Criteria builder.
     *
     * <p>{@link #build()} 에서 검증한다. 부분적으로 유효한 조건 객체가 만들어져 조회
     * 계층까지 흘러가지 않도록, 불변 객체 생성 시점에 한 번만 검증한다.</p>
     * <p>Validation happens in {@link #build()} so that a partially-valid criteria object can
     * never reach the query layer.</p>
     */
    public static final class Builder {
        private LocalDateTime from;
        private LocalDateTime to;
        private String institutionCode;
        private Long messageKey;
        private String senderNumber;
        private String recipientNumber;
        private String statusCode;
        private MessageType messageType;
        private TableType tableType;
        private String resultCode;
        private int page = 0;
        private int size = DEFAULT_PAGE_SIZE;

        /** @param value 시작 일시 / the window start @return this */
        public Builder from(LocalDateTime value) {
            this.from = value;
            return this;
        }

        /** @param value 종료 일시 / the window end @return this */
        public Builder to(LocalDateTime value) {
            this.to = value;
            return this;
        }

        /** @param value 이용기관 코드 / the 이용기관 code @return this */
        public Builder institutionCode(String value) {
            this.institutionCode = blankToNull(value);
            return this;
        }

        /** @param value 메시지키 / the message key @return this */
        public Builder messageKey(Long value) {
            this.messageKey = value;
            return this;
        }

        /** @param value 발신번호 / the sender number @return this */
        public Builder senderNumber(String value) {
            this.senderNumber = blankToNull(value);
            return this;
        }

        /** @param value 수신번호 / the recipient number @return this */
        public Builder recipientNumber(String value) {
            this.recipientNumber = blankToNull(value);
            return this;
        }

        /** @param value 상태 코드 / the status code @return this */
        public Builder statusCode(String value) {
            this.statusCode = blankToNull(value);
            return this;
        }

        /** @param value 메시지 유형 / the message type @return this */
        public Builder messageType(MessageType value) {
            this.messageType = value;
            return this;
        }

        /** @param value 문자타입 / the table type @return this */
        public Builder tableType(TableType value) {
            this.tableType = value;
            return this;
        }

        /** @param value 결과 코드 / the result code @return this */
        public Builder resultCode(String value) {
            this.resultCode = blankToNull(value);
            return this;
        }

        /** @param value 페이지 번호 / the page number @return this */
        public Builder page(int value) {
            this.page = Math.max(0, value);
            return this;
        }

        /**
         * 페이지 크기를 설정한다. 범위를 벗어나면 보정한다.
         * Sets the page size, clamping out-of-range values.
         *
         * <p>예외를 던지지 않고 보정하는 이유: 페이지 크기는 사용자가 의도적으로 조작할
         * 값이 아니라 클라이언트가 보내는 값이며, 상한만 지켜지면 시스템은 안전하다.
         * 다만 <b>상한 초과는 반드시 잘라낸다</b> — 10,000 건 요청이 통과하면 페이징의
         * 의미가 사라진다(FR-MSG-007).</p>
         * <p>Clamped rather than rejected: page size is sent by the client, not chosen by the
         * user, and the system is safe as long as the ceiling holds. The ceiling is enforced
         * absolutely — letting a 10,000-row request through would defeat pagination.</p>
         *
         * @param value 페이지 크기 / the page size
         * @return this
         */
        // req: FR-MSG-007, NFR-PERF-02
        public Builder size(int value) {
            if (value <= 0) {
                this.size = DEFAULT_PAGE_SIZE;
            } else {
                this.size = Math.min(value, MAX_PAGE_SIZE);
            }
            return this;
        }

        /**
         * 조건을 검증하고 생성한다. / Validates and builds.
         *
         * @return 조회 조건 / the criteria
         * @throws CriteriaException 검증 실패 시 / when validation fails
         */
        // req: FR-MSG-012, FR-MSG-013
        public MessageHistoryCriteria build() {
            List<String> violations = new ArrayList<>();

            if (from == null || to == null) {
                // source: WSVC.biztalk_admin_40_l001.xml — START_DT / END_DT are mandatory
                violations.add("요청일시 범위는 필수입니다.");
                throw new CriteriaException(violations);
            }

            // D8 대응: 날짜와 시각을 함께 비교한다.
            //
            // 레거시는 시각(HHmmss)만 초로 환산해 비교했다:
            //   if (Number(sTime) > Number(eTime)) alert("시작시간이 종료시간보다 클 수 없습니다.")
            // 그 결과 2026-01-01 18:00 ~ 2026-01-05 09:00 같은 <b>정상 범위가 거절</b>되었다.
            //
            // D8: the legacy compared only the time-of-day in seconds, so a legitimate range
            // such as 2026-01-01 18:00 ~ 2026-01-05 09:00 was refused.
            if (!from.isBefore(to)) {
                violations.add("시작일시가 종료일시보다 이후일 수 없습니다.");
            }

            // D13/FR-MSG-013 대응: 기간 상한.
            // 레거시에는 상한이 없었고, 서버 페이징도 주석 처리되어 있었다(D7).
            // 두 결함이 겹치면 넓은 범위 조회 하나가 8개 테이블을 전량 스캔한다.
            //
            // The legacy had no cap, and server paging was commented out (D7); together, one
            // wide query full-scanned eight tables.
            if (from.plus(MAX_WINDOW).isBefore(to)) {
                violations.add("조회 기간은 최대 " + MAX_WINDOW.toDays() + "일까지 가능합니다.");
            }

            if (!violations.isEmpty()) {
                throw new CriteriaException(violations);
            }
            return new MessageHistoryCriteria(this);
        }

        private static String blankToNull(String value) {
            // 빈 문자열을 null 로 정규화한다. 레거시 SQL 은
            // `CASE WHEN :X = '' THEN 1=1 ELSE X = :X END` 로 빈 값을 "조건 없음"으로
            // 취급했으므로(FR-MSG-015), 그 의미를 도메인 경계에서 한 번만 표현한다.
            // Normalised here because the legacy SQL treated an empty value as "no filter"
            // (FR-MSG-015); the meaning is expressed once, at the domain boundary.
            return (value == null || value.isBlank()) ? null : value.trim();
        }
    }

    /**
     * 조회 조건 검증 실패. / Raised when criteria validation fails.
     */
    public static class CriteriaException extends RuntimeException {

        private final List<String> violations;

        /**
         * 위반 목록으로 예외를 생성한다. / Creates the exception from violations.
         *
         * @param violations 위반 사유 / the violations
         */
        public CriteriaException(List<String> violations) {
            super("Invalid search criteria: " + violations.size() + " violation(s)");
            this.violations = List.copyOf(violations);
        }

        /**
         * 위반 사유 목록을 반환한다. / Returns the violations.
         *
         * @return 사유 목록 / the violations
         */
        public List<String> violations() {
            return violations;
        }
    }
}
