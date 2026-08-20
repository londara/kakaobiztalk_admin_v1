package com.webcash.iris.biztalk.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 등록이 금지된 특수·긴급번호 목록. / The list of barred special and emergency numbers.
 *
 * <p>화면 12 는 운영자에게 {@code "112, 114, 1335 과 같은 특수번호는 등록 불가능합니다"} 라고
 * 고지했고, 레거시는 그 규칙을 <b>어느 계층에도</b> 구현하지 않았다 — 클라이언트 검증에도,
 * {@code isValidDpNo()} 에도, 서비스 계약에도, 쿼리에도 없었다(D-S12). 길이 규칙이 대신해
 * 주지도 못한다: {@code 112} 는 8자리 미만이라 우연히 걸리지만 {@code 1335} 는 통과한다.</p>
 * <p>Screen 12 told the operator that special numbers cannot be registered, and the legacy
 * implemented that rule in <b>no layer at all</b> — not the client validation, not
 * {@code isValidDpNo()}, not the service contract, not the query (D-S12). The length rule does not
 * cover it either: {@code 112} is caught incidentally for being under 8 digits, {@code 1335} is
 * not.</p>
 *
 * <h2>왜 코드가 아니라 데이터인가 / why this is data and not code</h2>
 * <p>목록의 권위는 KISA / KAIT 에 있고 이 팀에 없다(AMB-S06, CONST-BIZ-D03). 자주 바뀌지는
 * 않지만 바뀔 때 릴리스를 기다려야 한다면, 그 사이 화면은 고지한 규칙을 지키지 못한다 —
 * D-S12 를 절차로 재현하는 셈이다. 그래서 배포 자산으로 읽어 들인다
 * ([ADR-SND-021](../../../../../../docs/design/adr/ADR-SND-021-barred-number-list.md)).</p>
 * <p>Authority for the list sits with KISA / KAIT, not with this team (AMB-S06, CONST-BIZ-D03). It
 * changes rarely, but if a correction had to wait for a release the screen would meanwhile fail to
 * keep a rule it states — D-S12 reproduced as a process. It is therefore loaded as a deployment
 * asset (ADR-SND-021).</p>
 *
 * <h2>비어 있으면 기동하지 않는다 / an empty list fails startup</h2>
 * <p>목록을 데이터로 옮기면 <b>목록이 사라질 수 있는</b> 새 실패 방식이 생긴다. 빈 집합으로
 * 조용히 기동하면 규칙이 없는 상태가 정상처럼 보이며, 그것이 정확히 D-S12 다. 그래서 파일이
 * 없거나 비었거나 숫자가 아닌 줄을 담고 있으면 <b>예외를 던진다</b> — 이 클래스에서 요란한
 * 실패는 결함이 아니라 설계다.</p>
 * <p>Moving the list into data creates a new failure mode: <b>the list can go missing</b>. Booting
 * quietly with an empty set would make ruleless operation look normal, and that is D-S12 exactly.
 * A missing, empty or malformed file therefore <b>throws</b>: loud failure here is the design, not
 * a defect.</p>
 *
 * <h2>설정으로 지울 수 없는 값 / values configuration cannot remove</h2>
 * <p>{@link #MANDATORY} 는 PM 결정(AMB-S06)이 이름을 든 네 값이며 <b>언제나</b> 합쳐진다.
 * ADR-SND-021 은 이것을 시험으로 보장하기로 했으나, 구조로 보장할 수 있으면 그편이 낫다 —
 * 설정 편집 한 번으로 {@code 119} 가 발신번호가 될 수 있는 상태를 시험이 사후에 잡는 것보다,
 * 애초에 표현 불가능한 것이 낫다.</p>
 * <p>{@link #MANDATORY} holds the four values PM ruling AMB-S06 names, and is <b>always</b> unioned
 * in. ADR-SND-021 proposed guaranteeing this by test; guaranteeing it structurally is better — a
 * configuration edit that makes {@code 119} registrable is better made unrepresentable than caught
 * afterwards.</p>
 *
 * // source: biztalk_admin_12_view.jsp — infoList01 (stated to the user, implemented nowhere)
 * // req: FR-SNDC-006, FR-SNDC-013, CONST-BIZ-D03, ADR-SND-021, AMB-S06
 */
@Component
public final class BarredNumbers {

    /**
     * 기본 위치. / The default location.
     *
     * <p>{@code *.properties} / {@code *.yml} 이 아니라 전용 텍스트 자산이다. 항목마다 왜
     * 금지되는지를 주석으로 남길 수 있고, 시크릿을 담는 설정 파일 부류와 섞이지 않는다.</p>
     * <p>A dedicated text asset rather than a {@code *.properties} / {@code *.yml} file: each entry
     * can carry a comment saying why it is barred, and it stays out of the class of configuration
     * files that hold secrets.</p>
     */
    static final String DEFAULT_LOCATION = "classpath:senderno/barred-numbers.txt";

    /**
     * 설정이 제거할 수 없는 값. / Values configuration cannot remove.
     *
     * <p>PM 결정 AMB-S06 이 이름을 든 네 값이다. 배포 자산에서 지워도 차단은 유지된다.</p>
     * <p>The four values named by PM ruling AMB-S06. Deleting them from the deployment asset does
     * not un-bar them.</p>
     */
    // req: FR-SNDC-006, CONST-BIZ-D03, AMB-S06
    public static final Set<String> MANDATORY = Set.of("112", "114", "119", "1335");

    private final Set<String> values;
    private final String origin;

    /**
     * 배포 자산에서 목록을 읽어 생성한다. / Loads the list from the deployment asset.
     *
     * @param loader   자원 로더 / the resource loader
     * @param location 자원 위치 — 배포 설정으로 대체 가능 / the location, overridable by deployment
     * @throws IllegalStateException 자원이 없거나 비었거나 형식이 아닐 때 / when it is missing, empty or malformed
     */
    // req: CONST-BIZ-D03, ADR-SND-021
    public BarredNumbers(ResourceLoader loader,
                         @Value("${biztalk.senderno.barred-numbers:" + DEFAULT_LOCATION + "}")
                         String location) {
        this(read(loader, location), location);
    }

    private BarredNumbers(String text, String origin) {
        this.values = parse(text, origin);
        this.origin = origin;
    }

    /**
     * 기본 자산으로 만든 목록을 반환한다. / Returns the list built from the bundled asset.
     *
     * <p>스프링 컨텍스트 없이 쓰는 경로(단위 시험, 배치)를 위한 것이다. <b>같은 파일</b>을
     * 읽으므로 시험이 검증하는 값과 배포 기본값이 갈라질 수 없다.</p>
     * <p>For callers without a Spring context (unit tests, batch). It reads the <b>same file</b>, so
     * what a test verifies and what ships as the default cannot diverge.</p>
     *
     * @return 기본 목록 / the bundled list
     */
    // req: CONST-BIZ-D03
    public static BarredNumbers bundled() {
        return new BarredNumbers(new DefaultResourceLoader(), DEFAULT_LOCATION);
    }

    /**
     * 주어진 본문으로 목록을 만든다 — 시험용. / Builds a list from the given text, for tests.
     *
     * @param text   파일 본문 / the file body
     * @param origin 오류 메시지에 쓸 출처 / the origin named in failure messages
     * @return 목록 / the list
     */
    // req: CONST-BIZ-D03
    public static BarredNumbers of(String text, String origin) {
        return new BarredNumbers(text, origin);
    }

    /**
     * 번호가 금지 목록에 있는지 판정한다. / Whether the number is barred.
     *
     * @param number 발신번호 / the sender number
     * @return 금지되면 true / true when barred
     */
    // req: FR-SNDC-006
    public boolean contains(String number) {
        return number != null && values.contains(number);
    }

    /**
     * 금지 번호 집합을 반환한다. / Returns the barred set.
     *
     * @return 변경 불가 집합 / an unmodifiable set
     */
    // req: FR-SNDC-013
    public Set<String> values() {
        return values;
    }

    /**
     * 목록의 출처를 반환한다. / Returns where the list came from.
     *
     * <p>기동 로그에 <b>값이 아니라 출처와 건수</b>를 남기기 위한 것이다. 어떤 목록으로
     * 기동했는지는 운영 질문이며, 목록 자체를 로그에 쏟을 이유는 없다.</p>
     * <p>So that a boot log can record <b>the origin and the count rather than the values</b>: which
     * list an instance started with is an operational question; dumping the list is not needed to
     * answer it.</p>
     *
     * @return 출처 / the origin
     */
    // req: CONST-BIZ-D03
    public String origin() {
        return origin;
    }

    /**
     * 자원을 읽는다. / Reads the resource.
     *
     * @param loader   자원 로더 / the resource loader
     * @param location 위치 / the location
     * @return 본문 / the body
     */
    // req: CONST-BIZ-D03
    private static String read(ResourceLoader loader, String location) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "barred sender-number list not found at '" + location + "'. "
                            + "The rule stated on the registration screen (FR-SNDC-006) cannot be "
                            + "enforced without it; refusing to start (CONST-BIZ-D03).");
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "barred sender-number list at '" + location + "' could not be read", e);
        }
    }

    /**
     * 본문을 집합으로 해석한다. / Parses the body into a set.
     *
     * <p>숫자가 아닌 항목은 <b>무시하지 않고 거절</b>한다. 무시하면 오타 하나가 조용히 한
     * 번호의 차단을 해제하며, 그 상태는 정상 기동과 구분되지 않는다.</p>
     * <p>A non-numeric entry is <b>rejected rather than skipped</b>: skipping would let one typo
     * quietly un-bar a number, in a state indistinguishable from a healthy boot.</p>
     *
     * @param text   본문 / the body
     * @param origin 출처 / the origin, for failure messages
     * @return 금지 번호 집합 / the barred set
     */
    // req: FR-SNDC-006, CONST-BIZ-D03
    private static Set<String> parse(String text, String origin) {
        Set<String> parsed = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                int comment = line.indexOf('#');
                String value = (comment >= 0 ? line.substring(0, comment) : line).trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (!value.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    throw new IllegalStateException(
                            "barred sender-number list '" + origin + "' line " + lineNumber
                                    + " is not a number. Entries are digits only; a malformed line "
                                    + "would silently un-bar a number (CONST-BIZ-D03).");
                }
                parsed.add(value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("barred sender-number list '" + origin
                    + "' could not be parsed", e);
        }

        if (parsed.isEmpty()) {
            throw new IllegalStateException(
                    "barred sender-number list '" + origin + "' is empty. An empty list would "
                            + "silently disable FR-SNDC-006, which is D-S12 itself; refusing to "
                            + "start (CONST-BIZ-D03).");
        }

        // MANDATORY 를 마지막에 합친다 — 파일에서 지워도 차단은 남는다(AMB-S06).
        // MANDATORY is unioned in last: removing a value from the file does not un-bar it.
        parsed.addAll(MANDATORY);
        return Collections.unmodifiableSet(parsed);
    }
}
