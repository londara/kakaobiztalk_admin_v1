package com.webcash.iris.biztalk.alimtalk.infra.vendor;

/**
 * 벤더 발송 경계. / The vendor send boundary.
 *
 * <p>인터페이스로 두는 이유는 테스트 편의가 아니다. 벤더 계약의 <b>미확인 부분</b>을 한 구현
 * 클래스에 가두기 위한 것이다 — {@code RSMS} 마샬링 형태, {@code CSTM_RSMS} 응답 구조,
 * {@code (is_cd, tran_id)} 멱등성. 확정되면 바뀌는 곳이 한 곳이어야 하고, 디스패처와 아웃박스는
 * 그 확정을 기다릴 필요가 없어야 한다.</p>
 * <p>This is an interface not for testing convenience but to confine the <b>unverified</b> parts of
 * the vendor contract to a single implementation — the {@code RSMS} marshalling shape, the
 * {@code CSTM_RSMS} response structure, and idempotency on {@code (is_cd, tran_id)}. When those are
 * settled exactly one class changes, and the dispatcher and outbox need not wait for them.</p>
 *
 * // source: IMO.ADV_KKO_AT_SEND2, jex/impl/OAuthHTTPConnection.java
 * // req: FR-ATS-004, ADR-ATK-025, RISK-A02, RISK-A07
 */
public interface AlimTalkVendorClient {

    /**
     * 계약 적합 payload 를 벤더에 보낸다. / Sends a contract-conforming payload to the vendor.
     *
     * <p><b>예외를 던지지 않는다.</b> 모든 실패를 {@link VendorSendResult} 로 돌려준다. 예외로
     * 알리면 호출부가 {@code catch} 한 덩어리 안에서 "전달되지 않음" 과 "알 수 없음" 을 다시
     * 구분해야 하고, 그 구분은 스택 트레이스에서 복원하기 어렵다 — 레거시가 모든 실패를
     * {@code JexWebBIZException("오류 발생")} 하나로 만든 것이 정확히 그 결과였다.</p>
     * <p><b>Throws nothing.</b> Every failure returns as a {@link VendorSendResult}. Signalling by
     * exception would force the caller to re-derive "not delivered" from "unknown" inside one
     * {@code catch}, a distinction hard to recover from a stack trace — which is exactly what the
     * legacy's single {@code JexWebBIZException("오류 발생")} produced.</p>
     *
     * @param isCd    이용기관코드 — OAuth 토큰이 기관별이므로 필요하다
     *                / institution code; the OAuth token is per institution
     * @param tranId  거래고유번호 — 벤더 측 중복 제거 키로 기대된다(미확인, RISK-A07)
     *                / transaction id, expected to be the vendor's de-duplication key (unverified)
     * @param payload 보낼 JSON / the JSON to send
     * @return 결과 — 세 갈래 / the outcome, one of three
     *
     * // req: FR-ATS-004, RISK-A07
     */
    VendorSendResult send(String isCd, String tranId, String payload);
}
