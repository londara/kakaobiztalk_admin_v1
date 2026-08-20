package com.webcash.iris.biztalk.alimtalk.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 수신번호 입력 해석·검증·중복 제거. / Parses, validates and de-duplicates recipient input.
 *
 * <h2>레거시의 세 결함이 한곳에서 만난다 / three legacy defects meet here</h2>
 * <table>
 *   <caption>레거시 상태 / legacy state</caption>
 *   <tr><th>결함 / defect</th><th>레거시 / legacy</th></tr>
 *   <tr><td>D-A12</td><td>화면 61 은 수신번호를 <b>전혀</b> 검증하지 않았다 — 형식·중복·건수 모두</td></tr>
 *   <tr><td>D-A28</td><td>화면 50 의 {@code isPhoneNumber()} 는 {@code ^} 없이 {@code find()} 를 써서
 *       {@code abc01012345678} 을 통과시켰다</td></tr>
 *   <tr><td>D-A35</td><td>{@code RECEIVER_NUMBER.split(" ")} — 공백 한 칸만 구분자, trim 없음,
 *       빈 토큰 처리 없음</td></tr>
 * </table>
 *
 * <p>D-A28 과 D-A26 이 겹치면 결과가 나빠진다. 형식에 맞지 않는 번호는 <b>경고가 아니라
 * 예외</b>가 되었고, 그 예외는 발송과 이력 기록이 <b>모두 끝난 뒤</b> 던져졌다. 즉 운영자는
 * "전송 실패"를 보았지만 메시지는 이미 나가 있었다. 그래서 이 클래스는 무엇을 거절할지만이
 * 아니라 <b>언제 거절할지</b>가 중요하다 — 호출 시점은 어떤 쓰기보다 앞이다(FR-ATS-007).</p>
 * <p>D-A28 compounds with D-A26: a malformed number became an <b>exception rather than a warning</b>,
 * and that exception was thrown <b>after</b> both the despatch and the history write had succeeded. The
 * operator saw "send failed" while the messages had already gone out. So what matters here is not only
 * what is rejected but <b>when</b> — this runs before any write (FR-ATS-007).</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — isPhoneNumber(), RECEIVER_NUMBER.split(" ")
 * // req: FR-ATC-012, FR-ATS-005, FR-ATS-006
 */
public final class RecipientParser {

    /**
     * 휴대전화번호 — <b>양끝이 고정된</b> 패턴. / Mobile number, <b>fully anchored</b>.
     *
     * <p>레거시 정규식은 {@code (01[016789]{1})(\d{3,4})\d{4}$} 였고 {@code Matcher.find()} 로
     * 적용되었다. 끝에 {@code $} 는 있으나 <b>앞에 {@code ^} 가 없다</b> — {@code find()} 는 부분
     * 일치를 찾으므로 {@code abc01012345678} 이 통과했다(D-A28). 여기서는 {@code matches()} 를
     * 쓰고 패턴 자체도 완전하다.</p>
     * <p>The legacy pattern had a trailing {@code $} but <b>no leading {@code ^}</b>, and was applied
     * with {@code find()}, which seeks a substring — so {@code abc01012345678} passed (D-A28). Here the
     * pattern is complete and applied with {@code matches()}.</p>
     *
     * <p>휴대전화만 허용하는 것은 레거시와 같으나, 레거시가 <b>조용히 버린</b> 지역번호를
     * 여기서는 명시적으로 거절한다. 알림톡 자체는 휴대전화 전용이지만 SMS/LMS 대체 전송은
     * 그렇지 않으므로, 지역번호 수용 여부는 미해결이다(AMB-A09, 작업 가정 A).</p>
     * <p>Mobile-only matches the legacy, but landlines the legacy <b>silently discarded</b> are now
     * rejected explicitly. AlimTalk itself is mobile-only while the SMS/LMS fallback is not, so whether
     * landlines are in scope remains open (AMB-A09, working assumption A).</p>
     */
    private static final Pattern MOBILE = Pattern.compile("01[016789]\\d{7,8}");

    /**
     * 구분자 — 운영자가 실제로 쓰는 모든 것. / Delimiters operators actually use.
     *
     * <p>레거시는 공백 한 칸만 인정했으므로, 쉼표나 줄바꿈으로 붙여넣은 목록은 <b>한 개의
     * 잘못된 번호</b>로 해석되었다 — 건수가 조용히 달라졌다(D-A35).</p>
     * <p>The legacy recognised only a single space, so a list pasted with commas or newlines parsed as
     * <b>one malformed number</b> — the recipient count changed silently (D-A35).</p>
     */
    private static final Pattern DELIMITERS = Pattern.compile("[,;\\s]+");

    /** 번호에서 제거하는 표시용 문자. / Presentational characters stripped from a number. */
    private static final Pattern PRESENTATION = Pattern.compile("[-()]");

    private RecipientParser() {
    }

    /**
     * 수신번호 입력을 해석한다. / Parses recipient input.
     *
     * <p>순서가 규칙이다: 분리 → 표시문자 제거 → 형식 검증 → 중복 제거. 중복 제거를 검증
     * <b>뒤</b>에 두는 이유는 {@code 010-1234-5678} 과 {@code 01012345678} 이 같은 번호이며,
     * 정규화 전에는 서로 다르게 보이기 때문이다.</p>
     * <p>The order is a rule: split, strip presentation, validate, de-duplicate. De-duplication comes
     * <b>after</b> normalisation because {@code 010-1234-5678} and {@code 01012345678} are the same
     * number and look different before it.</p>
     *
     * @param raw 운영자가 입력한 문자열 / the operator's input
     * @return 해석 결과 / the parse result
     *
     * // req: FR-ATC-012, FR-ATS-005
     */
    public static Result parse(String raw) {
        Set<RecipientNumber> accepted = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        int duplicates = 0;

        if (raw == null || raw.isBlank()) {
            return new Result(List.of(), List.of(), 0);
        }

        for (String token : DELIMITERS.split(raw.trim())) {
            if (token.isBlank()) {
                // 구분자가 연속되면 빈 토큰이 나온다 — 오류가 아니라 입력 형식이다.
                // Consecutive delimiters yield empty tokens: input formatting, not an error.
                continue;
            }
            String normalised = PRESENTATION.matcher(token).replaceAll("");
            if (!MOBILE.matcher(normalised).matches()) {
                rejected.add(token);
                continue;
            }
            if (!accepted.add(RecipientNumber.of(normalised))) {
                duplicates++;
            }
        }
        return new Result(List.copyOf(accepted), List.copyOf(rejected), duplicates);
    }

    /**
     * 수신번호 해석 결과. / The result of parsing recipient input.
     *
     * <p>거절된 값을 <b>결과로</b> 돌려준다는 점이 D-A26 대응의 핵심이다. 예외를 던지면 호출부가
     * 그 시점에 어떤 상태였는지에 따라 결과가 달라지고, 레거시에서는 그 시점이 발송 이후였다.
     * 값으로 돌려주면 호출부가 <b>발송 전에</b> 운영자에게 물을 수 있다(FR-ATS-007).</p>
     * <p>Returning rejected values <b>as data</b> is the core of the D-A26 fix. Throwing makes the
     * outcome depend on the caller's state at that moment, and in the legacy that moment was after the
     * despatch. Returned as a value, the caller can ask the operator <b>before</b> sending (FR-ATS-007).</p>
     *
     * @param accepted   형식을 만족하고 중복이 제거된 번호 / valid, de-duplicated numbers
     * @param rejected   형식 불일치로 제외된 원문 토큰 / raw tokens excluded as malformed
     * @param duplicates 제거된 중복 건수 / how many duplicates were removed
     *
     * // req: FR-ATC-012, FR-ATS-006, FR-ATS-007
     */
    public record Result(List<RecipientNumber> accepted, List<String> rejected, int duplicates) {

        /**
         * 발송할 수 있는 수신자가 있는지 판정한다. / Reports whether there is anyone to send to.
         *
         * <p>레거시는 모든 번호가 형식 불일치일 때 빈 배열을 만들고 {@code size() <= 1000} 분기로
         * 들어가 <b>빈 수신자 목록으로 벤더를 호출</b>한 뒤 예외를 던졌다(D-A31).</p>
         * <p>When every number was malformed the legacy built an empty array, took the
         * {@code size() <= 1000} branch, <b>called the vendor with an empty recipient list</b>, and then
         * threw (D-A31).</p>
         *
         * @return 유효한 수신자가 하나 이상이면 {@code true} / {@code true} when at least one is valid
         *
         * // req: FR-ATS-006
         */
        public boolean hasRecipients() {
            return !accepted.isEmpty();
        }

        /**
         * 운영자 확인이 필요한지 판정한다. / Reports whether operator confirmation is required.
         *
         * @return 제외된 번호가 있으면 {@code true} / {@code true} when anything was excluded
         *
         * // req: FR-ATS-007
         */
        public boolean requiresConfirmation() {
            return !rejected.isEmpty();
        }

        /**
         * 발송 대상 건수. / The number of recipients to send to.
         *
         * @return 유효 건수 / the valid count
         *
         * // req: FR-ATC-012
         */
        public int count() {
            return accepted.size();
        }
    }
}
