package com.webcash.iris.biztalk.domain;

import java.util.List;

/**
 * 문자상세내역 조회 키. / The key identifying one message for detail lookup.
 *
 * <p>레거시 상세조회는 {@code REQDATE} + {@code STATUS} + {@code MSGKEY} 세 값으로
 * 한 건을 식별하고, {@code MSG_TYPE} × {@code TABLE_TYPE} 으로 조회할 테이블을 결정했다.
 * 즉 <b>키가 아니라 키 + 라우팅 정보</b>가 함께 필요하다.</p>
 * <p>The legacy identified one record by {@code REQDATE} + {@code STATUS} + {@code MSGKEY} and
 * chose the table from {@code MSG_TYPE} × {@code TABLE_TYPE} — so what is needed is a key
 * <b>plus</b> routing information.</p>
 *
 * <p><b>레거시 결함 D-routing 대응.</b> 레거시는 {@code MSG_TYPE} 이 {@code "AT"} 가
 * 아니면 무조건 친구톡 테이블을 조회했다({@code else} 분기). 여기서는 열거형으로 받으므로
 * 미인식 값은 이 객체를 만들 수조차 없다.</p>
 * <p><b>Fixes the routing defect.</b> The legacy sent anything that was not {@code "AT"} to the
 * 친구톡 tables via a bare {@code else}. Taking enums here means an unrecognised value cannot
 * even construct this object.</p>
 *
 * @param messageType 메시지 유형 / the message type
 * @param tableType   문자타입 / the table type
 * @param messageKey  메시지키 / the message key
 * @param requestDate 요청일시 {@code YYYYMMDDHH24MISS} / the request timestamp
 * @param statusCode  상태 코드 / the status code
 *
 * // source: biztalk_admin_40.js — fn_getDetail() passes MSGKEY / ID / TABLE_TYPE / MSGTYPE / REQDATE / STATUS
 * // source: biztalk_admin_41_l001_act.jsp — 4-way idoName selection
 * // req: FR-MSGD-002, FR-MSGD-003, CONST-DATA-03
 */
public record MessageDetailKey(
        MessageType messageType,
        TableType tableType,
        Long messageKey,
        String requestDate,
        String statusCode
) {

    /** 요청일시 문자열 길이 {@code YYYYMMDDHH24MISS}. / Expected length of the timestamp string. */
    private static final int REQUEST_DATE_LENGTH = 14;

    /**
     * 값을 검증하여 키를 생성한다. / Validates and creates the key.
     *
     * <p>레거시는 {@code msgType.isEmpty() || tableType.isEmpty()} 만 확인하고
     * {@code JexWebBIZException("Input Parameter Error")} 를 던졌다. {@code MSGKEY} 는
     * SQL 에서 {@code CAST(:MSGKEY AS INTEGER)} 로 변환했으므로 숫자가 아닌 값은
     * <b>DB 레벨 오류</b>가 되었다 — 애플리케이션이 거절하는 편이 낫다(CONST-DATA-03).</p>
     * <p>The legacy checked only for empty type values, and cast {@code MSGKEY} in SQL, so a
     * non-numeric value became a database-level error. Rejecting it in the application is
     * better (CONST-DATA-03).</p>
     *
     * @param messageType 메시지 유형 코드 / the message type code
     * @param tableType   문자타입 코드 / the table type code
     * @param messageKey  메시지키 / the message key
     * @param requestDate 요청일시 / the request timestamp
     * @param statusCode  상태 코드 / the status code
     * @return 검증된 키 / the validated key
     * @throws MessageHistoryCriteria.CriteriaException 검증 실패 시 / when validation fails
     */
    // source: biztalk_admin_41_l001_act.jsp — `if(msgType.isEmpty() || tableType.isEmpty())`
    // req: FR-MSGD-003, CONST-DATA-03
    public static MessageDetailKey of(String messageType,
                                      String tableType,
                                      Long messageKey,
                                      String requestDate,
                                      String statusCode) {
        List<String> violations = new java.util.ArrayList<>();

        MessageType type = MessageType.fromCode(messageType).orElse(null);
        if (type == null) {
            violations.add("유형 코드가 올바르지 않습니다.");
        }
        TableType table = TableType.fromCode(tableType).orElse(null);
        if (table == null) {
            violations.add("문자타입 코드가 올바르지 않습니다.");
        }
        if (messageKey == null) {
            violations.add("메시지키는 필수이며 숫자여야 합니다.");
        }
        if (requestDate == null || requestDate.length() != REQUEST_DATE_LENGTH
                || !requestDate.chars().allMatch(Character::isDigit)) {
            // 레거시 SQL 은 `to_char(REQDATE,'YYYYMMDDHH24MISS') = :REQDATE` 로 정확히
            // 일치시켰다. 형식이 다르면 조용히 0건이 되므로 형식을 먼저 검증한다.
            // The legacy matched the formatted timestamp exactly, so a malformed value silently
            // returned nothing; the format is validated first.
            violations.add("요청일시 형식이 올바르지 않습니다(YYYYMMDDHHMMSS).");
        }
        if (statusCode == null || statusCode.isBlank()) {
            violations.add("상태 코드는 필수입니다.");
        }

        if (!violations.isEmpty()) {
            throw new MessageHistoryCriteria.CriteriaException(violations);
        }
        return new MessageDetailKey(type, table, messageKey, requestDate, statusCode);
    }

    /**
     * 조회할 테이블 이름을 반환한다. / Returns the table to query.
     *
     * <p>{@code KKO_SMS_MSG}, {@code KKO_MMS_MSG}, {@code KKF_SMS_MSG},
     * {@code KKF_MMS_MSG} 중 하나. 아카이브 테이블은 SQL 에서 {@code _LOG} 를 붙여
     * UNION 한다.</p>
     * <p>One of the four live tables; the SQL unions the {@code _LOG} archive.</p>
     *
     * @return 테이블 이름 / the table name
     */
    // source: biztalk_admin_41_l001_act.jsp — idoName selection
    // req: FR-MSGD-002
    public String tableName() {
        return messageType.tablePrefix() + "_" + tableType.code() + "_MSG";
    }
}
