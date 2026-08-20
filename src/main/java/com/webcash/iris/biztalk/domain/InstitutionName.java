package com.webcash.iris.biztalk.domain;

/**
 * 기관코드와 기관명의 짝. / A pairing of institution code and name.
 *
 * <p>대량 집계 데이터베이스에는 기관 마스터가 없다(AMB-R04). 레거시는 그 사실을 Java
 * {@code HashMap} 으로 우회했고, 조회에 실패한 코드는 {@code fin.get()} 이 null 을 돌려주어
 * <b>빈 기관명</b>으로 화면에 나갔다 — 조회가 실패했다는 사실은 어디에도 남지 않았다
 * (D-R12). 같은 우회를 하되, 실패는 값이 아니라 <b>표시</b>로 드러낸다.</p>
 * <p>The bulk database holds no institution master (AMB-R04). The legacy worked around that with
 * a Java {@code HashMap} whose misses returned null and reached the screen as a <b>blank
 * name</b>, with nothing recording that a lookup had failed (D-R12). The same workaround is used
 * here, but a miss surfaces as an explicit marker rather than as an absent value.</p>
 *
 * @param code 기관코드 / the institution code
 * @param name 기관명 / the institution name
 *
 * // source: biztalk_admin_20_l001_act.jsp — fin.put(a.getString("IS_CD"), a.getString("IS_NM"))
 * // req: FR-RPT-012
 */
public record InstitutionName(String code, String name) {
}
