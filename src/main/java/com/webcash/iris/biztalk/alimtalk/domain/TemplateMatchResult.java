package com.webcash.iris.biztalk.alimtalk.domain;

import java.util.List;
import java.util.Map;

/**
 * 템플릿 일치 검증 결과. / The outcome of matching a message against its template.
 *
 * <p>불일치는 <b>목록</b>으로 돌려준다. 레거시는 네 개의 조기 반환 경로 모두에서 첫 오류만
 * 돌려주었으므로 운영자가 시도마다 하나씩 고쳐야 했다(D-A6, FR-ATV-006).</p>
 * <p>Divergences are returned as a <b>list</b>. The legacy returned only the first error from all four
 * of its exit paths, so an operator fixed one per attempt (D-A6, FR-ATV-006).</p>
 *
 * <p>일치할 때는 변수별로 <b>무엇으로 읽었는지</b>를 함께 돌려준다. 레거시가 준 것은 문자
 * 위치 하나였고("위치: 7"), 그것으로는 운영자가 무엇을 고쳐야 할지 알 수 없었다(NFR-USE-A03).</p>
 * <p>On a match it also returns <b>what was read</b> for each variable. The legacy gave a single
 * character offset, which did not tell an operator what to change (NFR-USE-A03).</p>
 *
 * @param matched          부합하면 {@code true} / {@code true} when conformant
 * @param variableValues   변수명 → 읽어낸 값 / variable name to the value read
 * @param divergences      불일치 지점 / the divergences
 *
 * // source: biztalk_admin_61.js — errors.push({type,position}); resultDiv.textContent
 * // req: FR-ATV-002, FR-ATV-006, NFR-USE-A03
 */
public record TemplateMatchResult(
        boolean matched,
        Map<String, String> variableValues,
        List<Divergence> divergences) {

    /**
     * 부합 결과를 만든다. / Creates a conformant result.
     *
     * @param variableValues 변수별로 읽어낸 값 / the values read per variable
     * @return 부합 결과 / a conformant result
     *
     * // req: FR-ATV-002
     */
    public static TemplateMatchResult match(Map<String, String> variableValues) {
        return new TemplateMatchResult(true, Map.copyOf(variableValues), List.of());
    }

    /**
     * 불일치 결과를 만든다. / Creates a non-conformant result.
     *
     * @param divergences 불일치 지점 / the divergences
     * @return 불일치 결과 / a non-conformant result
     *
     * // req: FR-ATV-002, FR-ATV-006
     */
    public static TemplateMatchResult mismatch(List<Divergence> divergences) {
        return new TemplateMatchResult(false, Map.of(), List.copyOf(divergences));
    }

    /**
     * 첫 불일치 지점. / The first divergence.
     *
     * <p>운영자 화면에 한 줄로 보여줄 때 쓴다. 목록 전체는 {@link #divergences()} 로 얻는다 —
     * 첫 항목만 <b>가지고 있는</b> 것과 첫 항목만 <b>보여주는</b> 것은 다르다.</p>
     * <p>For a one-line operator message. The full list is available from {@link #divergences()}:
     * <b>having</b> only the first is not the same as <b>showing</b> only the first.</p>
     *
     * @return 첫 불일치, 부합이면 {@code null} / the first divergence, or {@code null} when conformant
     *
     * // req: NFR-USE-A03
     */
    public Divergence firstDivergence() {
        return divergences.isEmpty() ? null : divergences.get(0);
    }

    /**
     * 불일치 지점 하나. / A single divergence.
     *
     * @param position    내용에서의 문자 위치, 개행·공백 1자 / character offset in the content, newline and space count as one
     * @param templatePart 어긋난 템플릿 조각 / the template fragment that did not match
     * @param reason      사람이 읽는 이유 / a human-readable reason
     *
     * // req: FR-ATV-006, NFR-USE-A03
     */
    public record Divergence(int position, String templatePart, String reason) {
    }
}
