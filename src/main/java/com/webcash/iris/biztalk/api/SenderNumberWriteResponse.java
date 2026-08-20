package com.webcash.iris.biztalk.api;

/**
 * 쓰기 결과. / The outcome of a write.
 *
 * <p><b>{@code affected} 가 응답에 있는 것이 D-S1 에 대한 답이다.</b> 레거시는 삭제가 0건을
 * 지웠을 때도 {@code "정상적으로 처리되었습니다"} 만 돌려주었고, 화면은 몇 건이 실제로 바뀌었는지
 * 알 방법이 없었다. 이제 0건은 애초에 응답이 되지 못한다 — 서버가 예외로 거절하므로
 * (FR-SNDD-002) 이 값은 <b>언제나 1 이상</b>이다. 그럼에도 담는 이유는 운영자가 "3건 선택했는데
 * 2건이 지워졌다" 를 응답만으로 확인할 수 있어야 하기 때문이다.</p>
 * <p><b>Carrying {@code affected} is the answer to D-S1.</b> The legacy returned only a success
 * sentence even when the delete removed nothing, and the screen had no way to know how many rows had
 * changed. Zero can no longer reach this response at all — the server refuses it (FR-SNDD-002) — so
 * this value is <b>always at least 1</b>. It is carried anyway so that "I selected three and two were
 * deleted" is answerable from the response alone.</p>
 *
 * <p>{@code ref} 는 등록에서만 채워진다. 발신번호 자체를 응답에 담지 않는 이유는 요청자가 이미
 * 그 값을 알기 때문이며, 담지 않으면 응답 본문이 로그·프록시에 남을 때 노출면이 하나 줄어든다.</p>
 * <p>{@code ref} is populated for registration only. The number itself is not echoed: the caller
 * already has it, and omitting it removes one exposure surface from logs and proxies.</p>
 *
 * @param affected 실제로 바뀐 행 수 / rows actually changed
 * @param ref      등록된 행의 식별자 — 삭제 응답에서는 {@code null} / the new row's identifier; {@code null} on delete
 *
 * // source: biztalk_admin_10_d001_act.jsp — "정상적으로 처리되었습니다" regardless of row count
 * // req: FR-SNDD-002, NFR-OPS-D02, FR-SND-007
 */
public record SenderNumberWriteResponse(int affected, String ref) {

    /**
     * 등록 결과를 만든다. / Builds a registration outcome.
     *
     * @param ref 등록된 행의 식별자 토큰 / the new row's identifier token
     * @return 응답 / the response
     */
    // req: FR-SNDC-001, FR-SND-007
    public static SenderNumberWriteResponse registered(String ref) {
        return new SenderNumberWriteResponse(1, ref);
    }

    /**
     * 삭제 결과를 만든다. / Builds a deletion outcome.
     *
     * @param affected 지워진 행 수 / rows deleted
     * @return 응답 / the response
     */
    // req: FR-SNDD-002, NFR-OPS-D02
    public static SenderNumberWriteResponse deleted(int affected) {
        return new SenderNumberWriteResponse(affected, null);
    }
}
