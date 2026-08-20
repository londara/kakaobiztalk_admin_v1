package com.webcash.iris.biztalk.domain;

/**
 * 발신번호 등록 명령. / The sender-number registration command.
 *
 * <p>레거시 화면 12 의 입력 세 칸을 그대로 옮긴다 — 발신번호, 설명, 사유. 이용기관은 <b>담지
 * 않는다</b>: 대상 기관은 목록 화면이 고른 값이며 세션 권한으로 재판정된다(FR-SNDC-012,
 * FR-AZ-D03). 레거시는 팝업이 열릴 때 부모 창의 {@code IS_CD} 를 받아 그대로 insert 에 넣었고,
 * 그것이 조회 경로에만 기록된 D-S3 의 쓰기 경로 쌍둥이다.</p>
 * <p>Carries screen 12's three input fields — 발신번호, 설명, 사유 — and <b>not the
 * institution</b>: the target is what the list screen selected and it is re-decided from session
 * entitlements (FR-SNDC-012, FR-AZ-D03). The legacy popup received the opener's {@code IS_CD} and
 * put it straight into the insert, which is the write-path twin of D-S3.</p>
 *
 * <p>{@code 사유} 는 필수다(PM 결정 AMB-S10, FR-SNDC-011). 레거시 화면에도 칸은 있었으나 클라이언트
 * 검증이 존재하지 않는 요소를 검사했으므로(D-S11) 빈 값이 그대로 저장되었다. 소유 인증이 없는
 * 상태에서(RESIDUAL-S01) 사유는 운영자가 그 번호를 주장한 <b>유일한 근거 기록</b>이다.</p>
 * <p>{@code 사유} is mandatory (PM ruling AMB-S10, FR-SNDC-011). The legacy screen had the field, but
 * its client validation tested elements that did not exist (D-S11), so an empty value was stored.
 * With no ownership verification (RESIDUAL-S01) the 사유 is the <b>only record of the operator's
 * basis</b> for claiming the number.</p>
 *
 * @param number      발신번호 / the sender number
 * @param description 설명 / the description
 * @param reason      사유 — 필수 / the reason, mandatory
 *
 * // source: biztalk_admin_12_view.jsp — DP_NO, DSCP, REASON; biztalk_admin_12.js btn_save
 * // req: FR-SNDC-001, FR-SNDC-011, FR-SNDC-012
 */
public record SenderNumberRegistration(String number, String description, String reason) {
}
