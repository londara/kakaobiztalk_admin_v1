package com.webcash.iris.biztalk.alimtalk.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 템플릿 본문과 메시지 내용의 일치 검증. / Matches a message body against its registered template.
 *
 * <h2>레거시는 <b>유효한 내용을 거절</b>했다 / the legacy <b>rejected valid content</b></h2>
 * <p>{@code validateTemplateStrict()} 은 각 {@code #{…}} 변수 뒤에서 다음 고정 문자의
 * <b>첫 출현 위치</b>까지 커서를 전진시켰다:</p>
 * <p>{@code validateTemplateStrict()} advanced the content cursor to the <b>first occurrence</b> of
 * the next literal character after each variable:</p>
 * <pre>{@code while (idx < content.length && content[idx] !== nextFixedChar) idx++;}</pre>
 *
 * <p>치환된 값 자체가 그 문자를 포함하면 스캔이 값 <b>안에서</b> 멈추고 뒤따르는 비교가
 * 실패한다. 템플릿 {@code #{name}님 안녕} 과 내용 {@code 김님철수님 안녕} 은 공백에서
 * 불일치로 보고된다(D-A6). 한국어에서 {@code 님}·{@code 이}·{@code 가}·{@code 은}·{@code 는}
 * 은 이름 안에도 흔하고 변수 바로 뒤 고정 문자로도 흔하므로, 이것은 <b>예외적 경우가 아니라
 * 일상적 경우</b>다.</p>
 * <p>When the substituted value contains that character the scan halts <b>inside</b> the value and
 * the following comparison fails: template {@code #{name}님 안녕} with content {@code 김님철수님 안녕}
 * is reported as a mismatch at the space (D-A6). In Korean, particles such as {@code 님} are common
 * both inside names and as the literal immediately after a variable, so this is an <b>ordinary case,
 * not a corner one</b>.</p>
 *
 * <h2>정규식으로 위임한다 / delegating to a matcher</h2>
 * <p>PM 은 AMB-A00b 로 "고친다"를 결정했다. 손으로 쓴 스캐너에 역추적을 추가하는 것은 사실상
 * 정규식 엔진을 다시 쓰는 일이므로, 템플릿을 패턴으로 컴파일해 JDK 매처에 맡긴다.</p>
 * <p>PM ruled "correct it" (AMB-A00b). Adding backtracking to a hand-written scanner is writing a
 * regex engine, so the template is compiled to a pattern and matching is delegated to the JDK.</p>
 * <pre>
 *   LITERAL  → Pattern.quote(text)      고정 부분은 그대로 / literal, escaped
 *   VARIABLE → (?&lt;name&gt;.+?)            최소 일치 + 역추적 / lazy, backtracks
 * </pre>
 * <p>{@code (?<name>.+?)} 뒤에 인용된 고정 문자가 오면 매처가 <b>역추적</b>한다:
 * {@code 김} 으로 시작해 실패하면 {@code 김님철수} 까지 늘려 맞춘다. 레거시의 단방향 스캔은
 * 역추적할 수 없었고, 그것이 결함의 전부다.</p>
 * <p>A lazy group followed by a quoted literal <b>backtracks</b>: it tries {@code 김}, fails, and
 * extends until {@code 김님철수} matches. The legacy's single forward scan could not backtrack, and
 * that is the entire defect.</p>
 *
 * <p>{@code +?} 대신 {@code *?} 를 쓰지 않는 것도 규칙이다 — 빈 치환은 불일치다(FR-ATV-005).
 * 레거시는 {@code #{a}b} 와 {@code b} 를 일치로 보았다.</p>
 * <p>{@code +?} rather than {@code *?} is a rule, not a detail: an empty substitution is a mismatch
 * (FR-ATV-005). The legacy matched {@code #{a}b} against {@code b}.</p>
 *
 * <h2>{@link Pattern#quote} 이 보안 장치인 이유 / why {@link Pattern#quote} is a control</h2>
 * <p>템플릿 본문은 운영자가 작성한 텍스트이며 {@code .}·{@code (}·{@code [}·{@code *}·{@code ?}
 * 를 포함할 수 있다. 인용하지 않으면 그 문자들이 패턴의 의미를 조용히 바꾼다 — 검증이
 * 서버로 옮겨 오면서 새로 생긴 주입 표면이다(T-A7). 구조적으로 닫는다.</p>
 * <p>Template bodies are operator-authored and may contain regex metacharacters. Unquoted, those
 * would silently change the pattern's meaning — an injection surface created by moving validation
 * server-side (T-A7). It is closed by construction.</p>
 *
 * // source: biztalk_admin_61.js:1041-1150 — validateTemplateStrict()
 * // req: FR-ATV-004, FR-ATV-005, FR-ATV-006, FR-ATV-008
 */
public final class TemplateMatcher {

    /**
     * 카카오 변수 구문 — {@code #{…}} 만 허용한다. / Kakao variable syntax: {@code #{…}} only.
     *
     * <p>레거시 패턴은 {@code /(\#\{[^}]+\}|\$\{[^}]+\})/g} 로 {@code ${…}} 도 받아들였다.
     * 카카오는 {@code #{…}} 만 쓰므로, {@code ${…}} 로 작성된 템플릿은 <b>로컬에서 통과하고
     * 벤더에서 거절</b>된다 — 통과시키는 쪽이 더 위험하다(FR-ATV-008).</p>
     * <p>The legacy pattern also accepted {@code ${…}}. Only {@code #{…}} is Kakao's, so a template
     * written with {@code ${…}} would <b>pass locally and be rejected by the vendor</b> — accepting
     * it is the more dangerous behaviour (FR-ATV-008).</p>
     */
    private static final Pattern VARIABLE = Pattern.compile("#\\{([^}]+)}");

    /** 거절되는 레거시 구문. / The legacy syntax that is now rejected. */
    private static final Pattern LEGACY_DOLLAR_VARIABLE = Pattern.compile("\\$\\{[^}]+}");

    /**
     * 템플릿당 허용 변수 수 상한. / Maximum variables per template.
     *
     * <p>역추적 폭발(T-A20, RISK-A10)에 대한 상한 중 하나. 실제 알림톡 템플릿은 변수를 이보다
     * 훨씬 적게 쓴다.</p>
     * <p>One of the bounds against catastrophic backtracking (T-A20, RISK-A10). Real AlimTalk
     * templates use far fewer.</p>
     */
    static final int MAX_VARIABLES = 30;

    private final List<Token> tokens;
    private final Pattern pattern;

    private TemplateMatcher(List<Token> tokens, Pattern pattern) {
        this.tokens = tokens;
        this.pattern = pattern;
    }

    /**
     * 템플릿 본문을 컴파일한다. / Compiles a template body.
     *
     * <p>컴파일 결과는 캐시할 수 있도록 불변이다 — 같은 템플릿이 발송마다 다시 컴파일되지
     * 않아야 한다(NFR-PERF-A01).</p>
     * <p>The result is immutable so it can be cached: the same template must not be recompiled on
     * every send (NFR-PERF-A01).</p>
     *
     * @param templateBody {@code KKB_MSG_TMPL.TEMPLATE_MSG} 값 / the registered template body
     * @return 컴파일된 매처 / the compiled matcher
     * @throws IllegalArgumentException 본문이 {@code null} 이거나, {@code ${…}} 를 쓰거나,
     *         변수가 인접하거나, 변수 수가 상한을 넘으면
     *         / when null, using {@code ${…}}, containing adjacent variables, or exceeding the cap
     *
     * // req: FR-ATV-004, FR-ATV-008
     */
    public static TemplateMatcher compile(String templateBody) {
        if (templateBody == null) {
            throw new IllegalArgumentException("template body must not be null");
        }
        Matcher legacy = LEGACY_DOLLAR_VARIABLE.matcher(templateBody);
        if (legacy.find()) {
            // 조용히 받아들이지 않는다 — 어떤 구문을 써야 하는지 말해 준다.
            // Not silently accepted: the message names the syntax to use.
            throw new IllegalArgumentException(
                    "template uses ${...}; Kakao AlimTalk variables must be written #{...}");
        }

        List<Token> tokens = tokenize(templateBody);
        long variableCount = tokens.stream().filter(t -> t.variable).count();
        if (variableCount > MAX_VARIABLES) {
            throw new IllegalArgumentException(
                    "template declares " + variableCount + " variables, exceeding the cap of " + MAX_VARIABLES);
        }
        rejectAdjacentVariables(tokens);

        StringBuilder regex = new StringBuilder();
        for (Token token : tokens) {
            if (token.variable) {
                // 명명 그룹으로 두면 불일치 보고에서 "무엇으로 읽었는지"를 말할 수 있다.
                // A named group lets the divergence report say what was read for each variable.
                regex.append("(?<").append(token.groupName).append(">.+?)");
            } else {
                regex.append(Pattern.quote(token.text));
            }
        }
        return new TemplateMatcher(tokens, Pattern.compile(regex.toString(), Pattern.DOTALL));
    }

    /**
     * 메시지 내용이 템플릿에 부합하는지 검증한다. / Validates message content against the template.
     *
     * @param content 발송할 메시지 본문 / the message body to send
     * @return 일치 결과 / the match result
     *
     * // req: FR-ATV-002, FR-ATV-004, FR-ATV-006
     */
    public TemplateMatchResult match(String content) {
        if (content == null) {
            return TemplateMatchResult.mismatch(
                    List.of(new TemplateMatchResult.Divergence(0, "(none)", "content is null")));
        }
        Matcher matcher = pattern.matcher(content);
        if (matcher.matches()) {
            Map<String, String> values = new LinkedHashMap<>();
            for (Token token : tokens) {
                if (token.variable) {
                    values.put(token.text, matcher.group(token.groupName));
                }
            }
            return TemplateMatchResult.match(values);
        }
        return TemplateMatchResult.mismatch(locateDivergences(content));
    }

    /**
     * 모든 불일치 지점을 찾는다. / Locates every divergence.
     *
     * <p>레거시는 네 개의 조기 반환 경로 모두에서 <b>첫 오류만</b> 돌려주었으므로, 운영자는
     * 시도마다 오류 하나씩 고쳐야 했다(FR-ATV-006). 전체 패턴이 실패한 뒤 토큰 접두어를
     * 점진적으로 맞춰 보며 어디서부터 어긋나는지 찾는다 — 단일 {@link Pattern} 으로는 표현할 수
     * 없어 토큰 단위 제어가 필요한 부분이다(ADR-ATK-022 대안 D).</p>
     * <p>The legacy returned <b>only the first</b> error from all four of its exit paths, so an
     * operator fixed one per attempt (FR-ATV-006). After a whole-pattern failure, prefixes are matched
     * incrementally to find where conformance stops — the part that needs token-level control a single
     * {@link Pattern} cannot express (ADR-ATK-022 option D).</p>
     *
     * @param content 메시지 본문 / the message body
     * @return 불일치 목록 / the divergences
     *
     * // req: FR-ATV-006, NFR-USE-A03
     */
    private List<TemplateMatchResult.Divergence> locateDivergences(String content) {
        List<TemplateMatchResult.Divergence> found = new ArrayList<>();
        StringBuilder prefix = new StringBuilder();
        for (Token token : tokens) {
            String candidate = prefix + (token.variable
                    ? "(?<" + token.groupName + ">.+?)"
                    : Pattern.quote(token.text));
            Matcher matcher = Pattern.compile(candidate + ".*", Pattern.DOTALL).matcher(content);
            if (!matcher.matches()) {
                found.add(new TemplateMatchResult.Divergence(
                        matchedPrefixLength(prefix.toString(), content),
                        token.variable ? "#{" + token.text + "}" : token.text,
                        token.variable
                                ? "변수에 대응하는 값이 없다 / no value present for the variable"
                                : "고정 문구가 일치하지 않는다 / the literal text does not match"));
                // 이 지점 이후는 위치가 신뢰할 수 없으므로 더 진행하지 않는다.
                // Positions past this point are unreliable, so we stop rather than guess.
                break;
            }
            prefix.append(token.variable
                    ? "(?<" + token.groupName + ">.+?)"
                    : Pattern.quote(token.text));
        }
        if (found.isEmpty()) {
            // 접두어는 모두 맞았으나 전체는 실패했다 — 내용이 더 길다.
            // Every prefix matched but the whole did not: the content carries extra text.
            found.add(new TemplateMatchResult.Divergence(
                    matchedPrefixLength(prefix.toString(), content),
                    "(end of template)",
                    "템플릿 이후에 여분의 내용이 있다 / extra content follows the template"));
        }
        return found;
    }

    /**
     * 접두어가 소비한 문자 수를 구한다. / Measures how many characters a prefix consumed.
     *
     * @param prefixRegex 접두어 정규식 / the prefix regex
     * @param content     메시지 본문 / the message body
     * @return 소비된 문자 수 / the consumed character count
     */
    private static int matchedPrefixLength(String prefixRegex, String content) {
        if (prefixRegex.isEmpty()) {
            return 0;
        }
        Matcher matcher = Pattern.compile(prefixRegex, Pattern.DOTALL).matcher(content);
        return matcher.lookingAt() ? matcher.end() : 0;
    }

    /**
     * 인접 변수를 거절한다. / Rejects adjacent variables.
     *
     * <p>{@code #{a}#{b}} 는 사이에 고정 문구가 없어 <b>정의상 모호</b>하다 — 어떤 매처도 저자의
     * 의도를 복원할 수 없다. 동시에 역추적 폭발의 병리적 사례이기도 하므로, 거절하는 데 드는
     * 비용이 없고 얻는 것이 둘이다(T-A20).</p>
     * <p>{@code #{a}#{b}} has no intervening literal and is <b>ambiguous by definition</b> — no matcher
     * can recover the author's intent. It is also the pathological backtracking case, so rejecting it
     * costs nothing and gains two things (T-A20).</p>
     *
     * @param tokens 토큰 목록 / the token list
     * @throws IllegalArgumentException 인접 변수가 있으면 / when adjacent variables are present
     *
     * // req: FR-ATV-004
     */
    private static void rejectAdjacentVariables(List<Token> tokens) {
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i).variable && tokens.get(i - 1).variable) {
                throw new IllegalArgumentException(
                        "template has adjacent variables #{" + tokens.get(i - 1).text
                                + "}#{" + tokens.get(i).text
                                + "} with no literal between them, which cannot be matched unambiguously");
            }
        }
    }

    /**
     * 템플릿을 고정·변수 토큰으로 분해한다. / Splits a template into literal and variable tokens.
     *
     * @param body 템플릿 본문 / the template body
     * @return 토큰 목록 / the token list
     *
     * // req: FR-ATV-004
     */
    private static List<Token> tokenize(String body) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = VARIABLE.matcher(body);
        int cursor = 0;
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                tokens.add(Token.literal(body.substring(cursor, matcher.start())));
            }
            tokens.add(Token.variable(matcher.group(1).trim(), index++));
            cursor = matcher.end();
        }
        if (cursor < body.length()) {
            tokens.add(Token.literal(body.substring(cursor)));
        }
        return tokens;
    }

    /**
     * 템플릿 토큰. / A template token.
     *
     * // req: FR-ATV-004
     */
    private static final class Token {
        private final String text;
        private final boolean variable;
        private final String groupName;

        private Token(String text, boolean variable, String groupName) {
            this.text = text;
            this.variable = variable;
            this.groupName = groupName;
        }

        static Token literal(String text) {
            return new Token(text, false, null);
        }

        static Token variable(String name, int index) {
            // 그룹명은 영숫자만 허용되므로 변수명을 그대로 쓰지 않는다 — 한글 변수명이 흔하다.
            // Group names allow only alphanumerics, so the variable name is not reused directly:
            // Korean variable names are common.
            return new Token(name, true, "v" + index);
        }
    }
}
