package com.webcash.iris.biztalk.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 어떤 API 거래가 BizTalk 인지, 그리고 그것이 어느 채널인지 정하는 유일한 곳.
 * The single place deciding which API transactions are BizTalk and which channel each belongs to.
 *
 * <h2>레거시가 이 결정을 세 곳에서 따로 한 방식 / three places, three answers</h2>
 * <ol>
 *   <li><b>범위</b>: 아무 곳에서도 하지 않았다. {@code IDO.KKB_APITR_HSTR_L001} 에는 채널
 *       술어가 없어 화면은 <b>모든</b> 핀테크 API 거래를 보여주었다 — 운영 화면 캡처의
 *       {@code ADV_COM_GET_STATUS} 가 그 증거다.</li>
 *   <li><b>상세 링크</b>: 그리드가 {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1} 로
 *       판단했다.</li>
 *   <li><b>상세 서비스</b>: 액션이 네 개의 정확한 코드만 처리했고
 *       {@code ADV_KKO_AT_SEND2} 는 빠져 있었다. 링크는 걸리고 서비스는 못 하므로 팝업이
 *       <b>빈 그리드로</b> 열렸다 — 오류도 메시지도 없이(D-T13).</li>
 * </ol>
 * <ol>
 *   <li><b>Scope</b>: nowhere. {@code IDO.KKB_APITR_HSTR_L001} carries no channel predicate, so the
 *       screen showed <b>every</b> fintech API transaction — {@code ADV_COM_GET_STATUS} in the
 *       production screenshot is the evidence.</li>
 *   <li><b>The detail link</b>: the grid decided with
 *       {@code API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1}.</li>
 *   <li><b>The detail service</b>: the action handled four exact codes and omitted
 *       {@code ADV_KKO_AT_SEND2}. Linked but unservable, so the popup opened on an <b>empty
 *       grid</b> — no error, no message (D-T13).</li>
 * </ol>
 *
 * <h2>왜 코드가 아니라 설정인가 / why configuration rather than code</h2>
 * <p>권위 있는 목록은 <b>데이터</b>다. 소스 전체를 훑어도 리터럴은 다섯 개뿐인데,
 * {@code ADV_COM_GET_STATUS} 는 운영에 존재하고 어떤 소스 파일에도 없다. 코드에 열거하면
 * 증명 가능하게 불완전하고, API 가 하나 등록될 때마다 릴리스가 필요하다
 * (ADR-TLK-024).</p>
 * <p>The authoritative list is <b>data</b>. A full source scan yields five literals, yet
 * {@code ADV_COM_GET_STATUS} exists in production and in no source file. Enumerating in code is
 * provably incomplete and would need a release per registered API (ADR-TLK-024).</p>
 *
 * <p><b>설정 키</b>: {@code biztalk.talk-history.api-services[].code} /
 * {@code .channel} ({@code AT}|{@code FT}|생략) / {@code .label}. 설정이 없으면 아래 기본값
 * 다섯 개가 쓰인다 — 설정 파일은 SEC-001 대상이므로 이 슬라이스는 <b>키를 문서화하고
 * 기본값을 코드에 둔다</b>.</p>
 * <p><b>Configuration keys</b>: {@code biztalk.talk-history.api-services[].code} /
 * {@code .channel} ({@code AT}|{@code FT}|omitted) / {@code .label}. Absent configuration falls
 * back to the five defaults below — configuration files are within SEC-001's scope, so this slice
 * <b>documents the keys and keeps defaults in code</b>.</p>
 *
 * <h2>구현 시점 수정 / an implementation-time amendment</h2>
 * <p>ADR-TLK-024 와 ADR-TLK-026 은 레지스트리를 <b>둘</b>로 두고 그 사이의 포함 관계를
 * 시동 시 검사하도록 했다. 구현하면서 그 검사가 불필요해졌다: 항목 하나가 코드와 채널을
 * <b>함께</b> 담으므로 "채널은 있는데 범위에는 없는 코드"가 <b>표현 자체로 불가능</b>하다.
 * 검사로 막던 상태를 타입으로 막는다. 두 ADR 에 수정 노트를 남겼다.</p>
 * <p>ADR-TLK-024 and ADR-TLK-026 specified <b>two</b> registries with a startup containment check
 * between them. Implementation made that check unnecessary: one entry carries the code and the
 * channel <b>together</b>, so "a code with a channel but no scope entry" is <b>unrepresentable</b>.
 * A state the check guarded against is now excluded by the type. Both ADRs carry an amendment
 * note.</p>
 *
 * // source: IDO.KKB_APITR_HSTR_L001 — no channel predicate
 * // source: IDO.KKB_OPENAPI_INFO_L002 — SELECT … FROM FT_OPENAPI_INFO WHERE 1=1
 * // source: biztalk_admin_32_l001_act.jsp — four exact API_SVC_CD branches
 * // req: FR-TLK-002, FR-TLK-012, FR-TLK-013, FR-TLKD-005, FR-TLKM-006, ADR-TLK-024, ADR-TLK-026
 */
public final class BizTalkApiRegistry {

    /**
     * 소스 스캔으로 확인된 기본 API 서비스 집합.
     * The default API-service set, as confirmed by a source scan.
     *
     * <p>{@code ADV_KKO_AT_SEND2} 가 포함되어 있다 — 레거시 상세 액션이 <b>빠뜨린</b>
     * 코드이며, 그 누락이 D-T13 이다.</p>
     * <p>Includes {@code ADV_KKO_AT_SEND2}, the code the legacy detail action <b>omitted</b> —
     * that omission is D-T13.</p>
     */
    // source: grep ADV_KKO over IRIS_ADMIN + IRIS_ADMIN_ETC
    // req: FR-TLK-002
    public static final List<Entry> DEFAULT_ENTRIES = List.of(
            new Entry("ADV_KKO_AT_SEND", TalkChannel.ALIMTALK, "알림톡 발송"),
            new Entry("ADV_KKO_AT_SEND2", TalkChannel.ALIMTALK, "알림톡 발송 (2)"),
            new Entry("ADV_KKO_AT_SEND_M", TalkChannel.ALIMTALK, "알림톡 대량발송"),
            new Entry("ADV_KKO_FT_SEND", TalkChannel.FRIENDTALK, "친구톡 발송"),
            new Entry("ADV_KKO_FT_SEND_M", TalkChannel.FRIENDTALK, "친구톡 대량발송"));

    private final Map<String, Entry> byCode;

    /**
     * 레지스트리를 생성한다. / Creates the registry.
     *
     * @param entries 설정에서 온 항목. 비어 있으면 {@link #DEFAULT_ENTRIES} / entries from
     *                configuration; the defaults are used when empty
     * @throws IllegalArgumentException 코드가 중복될 때 / when a code is duplicated
     */
    // req: FR-TLK-002
    public BizTalkApiRegistry(List<Entry> entries) {
        List<Entry> effective = (entries == null || entries.isEmpty()) ? DEFAULT_ENTRIES : entries;
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : effective) {
            // 중복은 조용히 덮어쓰지 않고 거부한다. 같은 코드에 두 채널이 설정되면 어느
            // 쪽이 이겼는지가 설정 파일의 줄 순서에 달리게 되고, 그것은 D-T7 과 같은
            // 종류의 "어느 쪽이 맞는지 모르는" 상태다.
            // A duplicate is refused rather than silently overwritten: two channels for one code
            // would make the winner depend on line order in a config file — the same
            // "which one is right?" state as D-T7.
            if (map.putIfAbsent(entry.code(), entry) != null) {
                throw new IllegalArgumentException(
                        "BizTalk API 서비스 코드가 중복 설정되었습니다: " + entry.code()
                                + " / duplicate BizTalk API service code: " + entry.code());
            }
        }
        this.byCode = Collections.unmodifiableMap(map);
    }

    /**
     * 기본 항목으로 레지스트리를 생성한다. / Creates the registry with the default entries.
     *
     * @return 레지스트리 / the registry
     */
    // req: FR-TLK-002
    public static BizTalkApiRegistry withDefaults() {
        return new BizTalkApiRegistry(DEFAULT_ENTRIES);
    }

    /**
     * 조회 범위에 포함되는 API 서비스 코드를 반환한다.
     * Returns the API service codes within the query's scope.
     *
     * <p>매퍼가 {@code IN} 절에 바인딩한다. 목록이 비면 매퍼는 술어를 만들지 않는 대신
     * <b>아무 행도 반환하지 않아야</b> 한다 — 빈 범위는 "전체"가 아니라 "없음"이다.
     * 레거시가 빈 값을 "전체"로 읽은 것이 D-T2 의 절반이었다.</p>
     * <p>Bound into the mapper's {@code IN} clause. An empty set must yield <b>no rows</b> rather
     * than no predicate: an empty scope means "none", not "all". Reading a blank as "all" was half
     * of D-T2.</p>
     *
     * @return 코드 집합, 선언 순서 / the codes, in declaration order
     */
    // req: FR-TLK-002
    public Set<String> codes() {
        return byCode.keySet();
    }

    /**
     * 해당 코드가 조회 범위에 있는지 반환한다. / Whether the code is within scope.
     *
     * @param apiServiceCode {@code API_SVC_CD}
     * @return 포함 여부 / true when in scope
     */
    // req: FR-TLK-002
    public boolean contains(String apiServiceCode) {
        return apiServiceCode != null && byCode.containsKey(apiServiceCode);
    }

    /**
     * 해당 코드의 채널을 반환한다. / Returns the channel for the code.
     *
     * <p><b>상세 링크와 상세 조회가 같은 이 메서드를 읽는다.</b> 하나가 비어 있고 다른 하나가
     * 값을 갖는 상태는 존재할 수 없으므로, 링크가 걸린 행을 서비스가 못 하는 D-T13 이
     * 재현될 수 없다.</p>
     * <p><b>The detail link and the detail lookup read this same method.</b> A state where one is
     * empty and the other is not cannot exist, so D-T13 — a linked row the service cannot
     * serve — cannot recur.</p>
     *
     * @param apiServiceCode {@code API_SVC_CD}
     * @return 채널, 상세를 지원하지 않으면 empty / the channel, empty when detail is unsupported
     */
    // req: FR-TLK-013, FR-TLKD-005, FR-TLKM-006
    public Optional<TalkChannel> channelOf(String apiServiceCode) {
        if (apiServiceCode == null) {
            return Optional.empty();
        }
        Entry entry = byCode.get(apiServiceCode);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.channel());
    }

    /**
     * 해당 행에 상세 조회가 가능한지 반환한다. / Whether detail is available for the row.
     *
     * <p>처리 상태({@code PRSU})는 <b>보지 않는다.</b> 레거시는 {@code PRSU == 1} 인 행만
     * 링크했고, 그 결과 <b>처리중과 오류 행에 링크가 없었다</b> — 실패를 조사하는 운영자가
     * 가장 필요로 하는 행들이다(FR-TLK-013).</p>
     * <p>Processing status ({@code PRSU}) is <b>not consulted.</b> The legacy linked only
     * {@code PRSU == 1}, so <b>처리중 and 오류 rows had no link</b> — the rows an operator
     * investigating a failure most needs (FR-TLK-013).</p>
     *
     * @param apiServiceCode {@code API_SVC_CD}
     * @return 가능 여부 / true when the detail service can serve the row
     */
    // req: FR-TLK-013
    public boolean detailAvailable(String apiServiceCode) {
        return channelOf(apiServiceCode).isPresent();
    }

    /**
     * API 선택기에 제시할 항목을 반환한다. / Returns the options the API selector offers.
     *
     * <p>코드와 표시명 <b>두 필드만</b> 나간다. 레거시 {@code KKB_OPENAPI_INFO_L002} 는
     * 드롭다운 하나를 채우려고 API 당 21개 컬럼을 반환했고, 그중에는 API 를 등록·수정한
     * 운영자의 ID 와 이름({@code RGSR_ID}, {@code RGSR_NM}, {@code LSED_ID},
     * {@code LSED_NM})이 있었다(D-T27).</p>
     * <p>Exactly <b>two fields</b> leave the server. The legacy {@code KKB_OPENAPI_INFO_L002}
     * returned 21 columns per API to fill one dropdown, among them the ids and names of the
     * operators who registered and last edited each API (D-T27).</p>
     *
     * @return 코드와 표시명의 짝 / code-label pairs
     */
    // req: FR-TLK-012, CONST-SEC-T01
    public List<Option> options() {
        return byCode.values().stream().map(e -> new Option(e.code(), e.label())).toList();
    }

    /**
     * 레지스트리 항목 하나. / One registry entry.
     *
     * @param code    {@code FT_APITR_HSTR.API_SVC_CD} 값 / the {@code API_SVC_CD} value
     * @param channel 톡 채널. 상세를 지원하지 않는 서비스는 null / the talk channel; null when
     *                the service has no message detail
     * @param label   화면 표시명 / the display label
     */
    // req: FR-TLK-002, FR-TLK-013
    public record Entry(String code, TalkChannel channel, String label) {
    }

    /**
     * 선택기 항목 하나. / One selector option.
     *
     * @param code  {@code API_SVC_CD}
     * @param label 표시명 / the display label
     */
    // req: FR-TLK-012
    public record Option(String code, String label) {
    }
}
