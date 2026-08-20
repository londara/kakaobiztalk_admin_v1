package com.webcash.iris.biztalk.domain;

/**
 * 거래 상세내역 조회 조건 — 검증을 통과한 형태.
 * 거래 상세내역 criteria, in their validated form.
 *
 * <h2>필터가 두 채널 모두에 적용되어야 하는 이유 / why the filters must apply to both channels</h2>
 * <p>레거시 {@code biztalk_admin_32_l001_act.jsp} 는 수신번호·상태·톡결과·문자결과를
 * {@code IDODynamic} 으로 조립해 {@code DYNAMIC_0} 으로 넘겼다. 알림톡 질의
 * ({@code KKB_AT_MSG_L001})에는 그것을 받을 {@code ??} 자리와 {@code <in>} 선언이 있었고,
 * <b>친구톡 질의({@code KKB_FT_MSG_L001})에는 둘 다 없었다</b> — 복사할 때 빠졌다. 그래서
 * 친구톡을 조회하면 네 개의 필터가 <b>조용히 무시</b>되었고, 사용자에게는 필터가 듣지 않는다는
 * 표시가 전혀 없었다(D-T8).</p>
 * <p>The legacy action assembled 수신번호/상태/톡결과/문자결과 into an {@code IDODynamic} passed as
 * {@code DYNAMIC_0}. The 알림톡 query had the {@code ??} placeholder and the {@code <in>} declaration to
 * receive it; <b>the 친구톡 query had neither</b> — both were lost in the copy. So for 친구톡 all four
 * filters were <b>silently ignored</b>, with nothing telling the user the filters were inert (D-T8).</p>
 *
 * <p>여기서는 채널별 분기가 <b>테이블 이름에만</b> 있고 술어는 공유된다. 한 채널에서만 필터가
 * 빠지는 상태를 만들려면 술어를 두 벌로 나눠야 하는데, 그럴 이유가 없다.</p>
 * <p>Here the per-channel branch is <b>only the table names</b> and the predicates are shared. Producing a
 * state where a filter applies to one channel and not the other would require two copies of the
 * predicate, and there is no reason to have them.</p>
 *
 * @param key             거래 키 — 기관은 담지 않는다 / the transaction key; carries no institution
 * @param channel         레지스트리가 결정한 채널 / the channel, decided by the registry
 * @param institutionCode 서버가 도출한 이용기관 / the institution, derived on the server
 * @param recipient       수신번호 부분 일치. 조건 없으면 null / recipient substring, null when unfiltered
 * @param statusCode      상태 코드. 조건 없으면 null / the status code, null when unfiltered
 * @param talkOutcome     톡결과 구분. 조건 없으면 null / the talk-result classification, null when unfiltered
 * @param smsOutcome      문자결과 구분. 조건 없으면 null / the SMS-result classification, null when unfiltered
 * @param page            0부터 시작하는 페이지 번호 / the zero-based page number
 * @param size            페이지 크기 / the page size
 *
 * // source: biztalk_admin_32_l001_act.jsp — IDODynamic DYNAMIC_0
 * // source: IDO.KKB_FT_MSG_L001 — no ?? placeholder, no DYNAMIC_0 in <in>
 * // req: FR-TLKD-002, FR-TLKD-003, FR-TLKD-006, FR-TLKD-007, FR-AZ-T03
 */
public record TalkMessageCriteria(
        TalkTransactionKey key,
        TalkChannel channel,
        String institutionCode,
        String recipient,
        String statusCode,
        TalkResult.Outcome talkOutcome,
        TalkResult.Outcome smsOutcome,
        int page,
        int size
) {

    /** 기본 페이지 크기. 레거시 팝업의 {@code LIST_CNT} 와 같다. / Default page size, as the legacy popup's. */
    public static final int DEFAULT_SIZE = 10;

    /** 최대 페이지 크기. / Maximum page size. */
    public static final int MAX_SIZE = 200;

    /**
     * 매퍼에 바인딩할 메시지 일련번호를 반환한다.
     * Returns the message serial to bind for the mapper.
     *
     * <p>패딩은 자바에서 끝난다. 레거시는 {@code SERIALNUM = LPAD(:SERIALNUM,10,'0')} 으로
     * 술어 안에서 채웠고, PostgreSQL 의 {@code lpad} 가 목표 폭보다 긴 입력을 <b>잘라내므로</b>
     * 20자리 거래고유번호가 10자리로 절단되어 다른 거래에 일치했다(D-T9).</p>
     * <p>Padding is finished in Java. The legacy padded inside the predicate with
     * {@code LPAD(:SERIALNUM,10,'0')}, and PostgreSQL's {@code lpad} <b>truncates</b> an over-width input,
     * so a 20-character serial was cut to ten and matched a different transaction (D-T9).</p>
     *
     * @return 저장 폭에 맞춘 일련번호 / the serial at its stored width
     */
    // req: FR-TLKD-009
    public String serialForMapper() {
        return key.serial().messageForm();
    }

    /**
     * 페이지 크기를 정규화한다. / Normalises a requested page size.
     *
     * @param requested 요청 크기 / the requested size
     * @return 유효 범위의 크기 / a size within range
     */
    // req: FR-TLKD-007
    public static int normaliseSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }

    /**
     * 행 오프셋을 반환한다. / Returns the row offset.
     *
     * @return 오프셋 / the offset
     */
    // req: FR-TLKD-007
    public int offset() {
        return page * size;
    }

    /**
     * 감사 기록에 담을 설명을 반환한다. / Returns a description for the audit record.
     *
     * <p>수신번호 검색어는 <b>해시하지 않고 담지 않는다</b>. 문자내역 슬라이스는 해시해서
     * 남겼으나, 그 화면은 수신번호가 <b>주된 검색 조건</b>이어서 무엇으로 찾았는지가 감사
     * 가치가 있었다. 여기서는 거래 키가 이미 무엇을 보았는지 특정하므로 부분 일치 문자열을
     * 남길 이유가 없다 — 감사 저장소를 2차 PII 저장소로 만들지 않는다(ADR-006).</p>
     * <p>The recipient search term is <b>omitted rather than hashed</b>. The 문자내역 slice hashed it because
     * there the recipient is the <b>primary</b> search key and what was searched for had audit value. Here
     * the transaction key already identifies what was viewed, so there is no reason to record a substring —
     * keeping the audit store from becoming a secondary PII repository (ADR-006).</p>
     *
     * @return 설명 / the description
     */
    // req: FR-AZ-T05, NFR-SEC-PII-T01
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("txn=").append(key.describe());
        sb.append(" channel=").append(channel.code());
        sb.append(" institution=").append(institutionCode);
        if (recipient != null) {
            // 검색어 자체는 담지 않고 <b>썼다는 사실</b>만 남긴다.
            // The term itself is not recorded — only that one was used.
            sb.append(" recipientFilter=yes");
        }
        if (statusCode != null) {
            sb.append(" status=").append(statusCode);
        }
        if (talkOutcome != null) {
            sb.append(" talk=").append(talkOutcome);
        }
        if (smsOutcome != null) {
            sb.append(" sms=").append(smsOutcome);
        }
        sb.append(" page=").append(page).append(" size=").append(size);
        return sb.toString();
    }
}
