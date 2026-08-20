package com.webcash.iris.biztalk.alimtalk.domain;

import java.time.LocalDateTime;

/**
 * 아웃박스 한 행. / One outbox row.
 *
 * <p>{@code KKB_ATK_SEND_OUTBOX} 의 행을 그대로 옮긴 값 객체다. 접수 확정까지만 사는 표이므로
 * 전달 상태·벤더 결과 코드·수신결과 시각은 담지 않는다 — 그것은 게이트웨이가
 * {@code KKO_MSG_LOG} 에 쓴다. 두 곳에 같은 사실을 두면 언젠가 어긋나고, 어긋난 뒤에는 어느
 * 쪽이 옳은지 알 수 없다.</p>
 * <p>A value object mirroring a {@code KKB_ATK_SEND_OUTBOX} row. Because the table lives only until
 * acceptance is settled, it carries no delivery status, vendor result code or report timestamp — the
 * gateway writes those to {@code KKO_MSG_LOG}. Holding the same fact in two places guarantees they
 * eventually disagree, and then neither can be trusted.</p>
 *
 * @param outboxId     대리 키 — 접수 전에는 {@code null} / surrogate key, {@code null} before insert
 * @param isCd         이용기관코드 / institution code
 * @param tranId       거래고유번호 — 배치 전체에 하나 / transaction id, one per batch
 * @param msgOrder     배치 내 순번, 1부터 / one-based position within the batch
 * @param payload      벤더에 보낼 계약 적합 JSON / the contract-conforming JSON to send
 * @param status       상태 / the state
 * @param attempts     시도 횟수 / attempts made
 * @param dueAt        예약 발송 시각 — 즉시 발송은 {@code null} / scheduled time, {@code null} for immediate
 * @param claimedUntil 클레임 만료 / claim expiry
 * @param lastError    마지막 오류 요약 / a summary of the last error
 * @param createdAt    생성 시각 / creation time
 * @param updatedAt    최종 변경 시각 / last update time
 *
 * // source: IMO.ADV_KKO_AT_SEND / _M — the contract this payload conforms to
 * // req: FR-ATS-001, FR-ATS-005, FR-ATC-004, ADR-ATK-023
 */
public record OutboxEntry(
        Long outboxId,
        String isCd,
        String tranId,
        int msgOrder,
        String payload,
        OutboxStatus status,
        int attempts,
        LocalDateTime dueAt,
        LocalDateTime claimedUntil,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * 접수용 신규 행을 만든다. / Creates a row for acceptance.
     *
     * <p>{@link OutboxStatus#PENDING} 으로 고정한다. 호출부가 상태를 고를 수 있게 하면 접수
     * 시점에 {@code SENT} 인 행을 만드는 것이 문법적으로 가능해지고, 그것은 보내지 않은
     * 메시지를 보냈다고 기록하는 일이다.</p>
     * <p>Fixed at {@link OutboxStatus#PENDING}. Letting the caller choose would make it syntactically
     * possible to create a row that is already {@code SENT} — recording a message as sent without
     * having sent it.</p>
     *
     * @param isCd     이용기관코드 / institution code
     * @param tranId   거래고유번호 / transaction id
     * @param msgOrder 순번 / order
     * @param payload  payload JSON
     * @param dueAt    예약 시각 또는 {@code null} / scheduled time, or {@code null}
     * @return 접수 대기 행 / a row awaiting despatch
     *
     * // req: FR-ATS-001, FR-ATC-004
     */
    public static OutboxEntry pending(
            String isCd, String tranId, int msgOrder, String payload, LocalDateTime dueAt) {
        return new OutboxEntry(
                null, isCd, tranId, msgOrder, payload, OutboxStatus.PENDING, 0, dueAt, null, null, null, null);
    }

    /**
     * 지금 보낼 수 있는가 — 예약 시각이 지났는지 본다.
     * Is this due now, by its scheduled time?
     *
     * <p>{@code dueAt} 이 {@code null} 이면 즉시 발송이다. 레거시는 예약 발송을 단건에서만
     * 지원하고 다건 계약이 항목마다 선언한 {@code reqdate} 를 화면이 수집하지 않았다(D-A14) —
     * 여기서는 두 경로가 같은 필드를 쓴다.</p>
     * <p>A {@code null} {@code dueAt} means immediate. The legacy supported reservation only for
     * single sends and never collected the per-item {@code reqdate} its batch contract declares
     * (D-A14); here both paths use the same field.</p>
     *
     * @param now 현재 시각 / the current time
     * @return 발송 시각이 되었으면 {@code true} / {@code true} when due
     *
     * // req: FR-ATS-006, FR-ATC-007
     */
    public boolean isDue(LocalDateTime now) {
        return dueAt == null || !dueAt.isAfter(now);
    }
}
