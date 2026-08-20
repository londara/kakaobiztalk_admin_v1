#!/usr/bin/env bash
# =============================================================================
# 알림톡 슬라이스 테스트를 Maven 없이 실행한다.
# Runs the AlimTalk slice test suite without Maven.
#
# req: SPRINT-A1-RETRO A1-R15, TEST-PLAN-ALIMTALK §1.1, RISK-A12
#
# 왜 이 스크립트가 존재하는가 / why this exists:
#   이 환경에는 Maven 이 없다(RISK-A12). Sprint A1 의 세 차례 반복에서 매번 클래스패스를
#   손으로 다시 조립했고, 그때마다 NoClassDefFoundError 를 하나씩 만나며 필요한 jar 를
#   찾아냈다 — 같은 탐색을 세 번 했다. 그 결과를 여기에 고정한다.
#
#   Maven is unavailable here (RISK-A12). Across three Sprint A1 iterations the classpath was
#   reassembled by hand each time, discovering the required jars one NoClassDefFoundError at a
#   time — the same search, three times over. This pins the result.
#
# 한계 / limitations:
#   - Spring Boot 컨텍스트를 띄우지 않는다. MockMvc 는 standalone 이므로 필터 체인
#     (인증·CSRF·403)은 검증되지 않는다 — A1-19 가 부분 완료로 남은 이유다.
#     No Boot context: MockMvc runs standalone, so the filter chain (authn, CSRF, 403) is not
#     exercised. That is why A1-19 remains partial.
#   - 커버리지는 측정하지 않는다. JaCoCo 는 별도로 에이전트를 붙여 실행한다.
#     Coverage is not measured here; JaCoCo is attached separately.
#   - Maven 설치 후에는 `mvn verify` 가 정본이고 이 스크립트는 불필요해진다.
#     Once Maven is installed, `mvn verify` is authoritative and this becomes redundant.
#
# 사용 / usage:  bash qa/run-alimtalk-tests.sh
# =============================================================================
set -euo pipefail

# javac 는 Windows 바이너리라 argfile 안의 `/d/...` 형식을 이해하지 못한다. argfile 은
# 셸의 경로 변환을 거치지 않으므로 여기서 직접 변환한다.
# javac is a Windows binary and does not understand `/d/...` inside an argfile; argfiles bypass the
# shell's path translation, so convert here.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cygpath -w "$ROOT" 2>/dev/null || echo "$ROOT")"
M2="${M2_REPO:-$HOME/.m2/repository}"
WORK="${TMPDIR:-/tmp}/atk-tests-$$"
mkdir -p "$WORK/out"
trap 'rm -rf "$WORK"' EXIT

# ---------------------------------------------------------------------------
# 버전을 고정한다 / pinned versions.
# 범위 지정(예: 최신 jar 자동 선택)을 쓰지 않는 이유: 로컬 저장소에 여러 버전이 있을 때
# 조용히 다른 조합으로 테스트가 통과하면, 통과 사실 자체의 의미가 사라진다.
# No globbing for "newest jar": when the local repository holds several versions, a silent change
# of combination makes a passing run meaningless.
# ---------------------------------------------------------------------------
jars=(
  com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar
  com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar
  com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar
  org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar
  org/junit/jupiter/junit-jupiter-engine/5.10.2/junit-jupiter-engine-5.10.2.jar
  org/junit/jupiter/junit-jupiter-params/5.10.2/junit-jupiter-params-5.10.2.jar
  org/junit/platform/junit-platform-commons/1.10.2/junit-platform-commons-1.10.2.jar
  org/junit/platform/junit-platform-engine/1.10.2/junit-platform-engine-1.10.2.jar
  org/junit/platform/junit-platform-launcher/1.10.3/junit-platform-launcher-1.10.3.jar
  org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar
  org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar
  org/assertj/assertj-core/3.24.2/assertj-core-3.24.2.jar
  org/hamcrest/hamcrest/2.2/hamcrest-2.2.jar
  # MockMvc 가 끌고 오는 것들 / what MockMvc drags in
  com/jayway/jsonpath/json-path/2.9.0/json-path-2.9.0.jar
  net/minidev/json-smart/2.6.0/json-smart-2.6.0.jar
  net/minidev/accessors-smart/2.6.0/accessors-smart-2.6.0.jar
  io/micrometer/micrometer-commons/1.16.6/micrometer-commons-1.16.6.jar
  io/micrometer/micrometer-observation/1.16.6/micrometer-observation-1.16.6.jar
  org/slf4j/slf4j-api/2.0.18/slf4j-api-2.0.18.jar
  org/springframework/spring-aop/6.1.13/spring-aop-6.1.13.jar
  org/springframework/spring-beans/6.1.13/spring-beans-6.1.13.jar
  org/springframework/spring-context/6.1.13/spring-context-6.1.13.jar
  org/springframework/spring-core/6.1.13/spring-core-6.1.13.jar
  org/springframework/spring-jcl/6.2.19/spring-jcl-6.2.19.jar
  org/springframework/spring-test/6.1.13/spring-test-6.1.13.jar
  # @Transactional (AlimTalkSendService) / the transaction annotation
  org/springframework/spring-tx/6.1.13/spring-tx-6.1.13.jar
  org/springframework/spring-web/6.1.13/spring-web-6.1.13.jar
  org/springframework/spring-webmvc/6.1.13/spring-webmvc-6.1.13.jar
  org/springframework/boot/spring-boot/3.3.4/spring-boot-3.3.4.jar
  # @ConditionalOnProperty (AlimTalkDispatchConfig) / the conditional annotation
  org/springframework/boot/spring-boot-autoconfigure/3.3.4/spring-boot-autoconfigure-3.3.4.jar
  org/springframework/security/spring-security-config/6.3.3/spring-security-config-6.3.3.jar
  org/springframework/security/spring-security-core/6.3.3/spring-security-core-6.3.3.jar
  org/springframework/security/spring-security-web/6.3.3/spring-security-web-6.3.3.jar
  org/apache/tomcat/embed/tomcat-embed-core/11.0.22/tomcat-embed-core-11.0.22.jar
  org/mybatis/mybatis/3.5.14/mybatis-3.5.14.jar
  aopalliance/aopalliance/1.0/aopalliance-1.0.jar
  jakarta/xml/bind/jakarta.xml.bind-api/4.0.5/jakarta.xml.bind-api-4.0.5.jar
)

CP=""
missing=0
for j in "${jars[@]}"; do
  [[ "$j" == \#* ]] && continue
  if [ ! -f "$M2/$j" ]; then
    echo "MISSING: $M2/$j" >&2
    missing=$((missing + 1))
    continue
  fi
  CP="$CP;$(cygpath -w "$M2/$j" 2>/dev/null || echo "$M2/$j")"
done
if [ "$missing" -gt 0 ]; then
  echo "==> $missing jar(s) absent from the local repository. Cannot run." >&2
  exit 2
fi
CP="${CP#;}"

# ---------------------------------------------------------------------------
# 컴파일 / compile.
# alimtalk 패키지 전체 + 이 슬라이스가 실제로 참조하는 두 개의 외부 클래스만 넣는다.
# TenantContext 는 TemplateRegistry 가 테넌트 범위를 얻는 단일 지점이고, SecurityConfig 는
# AlimTalkControllerSecurityTest 가 @EnableMethodSecurity 를 리플렉션으로 확인하기 때문에
# 필요하다 (D-A37). 둘을 빼면 테스트가 "통과"하는 것이 아니라 조용히 깨진다.
# The alimtalk package plus exactly the two outside classes this slice touches. TenantContext is
# where TemplateRegistry obtains its scope; SecurityConfig is read reflectively by
# AlimTalkControllerSecurityTest to confirm @EnableMethodSecurity (D-A37). Omit either and the
# suite does not pass — it breaks quietly.
# ---------------------------------------------------------------------------
{
  find "$ROOT/src/main/java/com/webcash/iris/biztalk/alimtalk" -name '*.java'
  find "$ROOT/src/test/java/com/webcash/iris/biztalk/alimtalk" -name '*.java'
  echo "$ROOT/src/main/java/com/webcash/iris/common/tenant/TenantContext.java"
  echo "$ROOT/src/main/java/com/webcash/iris/auth/config/SecurityConfig.java"
} > "$WORK/srcs.txt"

echo "==> compiling $(wc -l < "$WORK/srcs.txt" | tr -d ' ') source files"
javac -nowarn -encoding UTF-8 -d "$WORK/out" -cp "$CP" "@$WORK/srcs.txt"

# JUnit Platform 런처 / the launcher.
cat > "$WORK/RunTests.java" <<'JAVA'
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/** JUnit 5 콘솔 런처 대체 / a stand-in for the JUnit 5 console launcher. */
public class RunTests {
  public static void main(String[] a) {
    LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request()
        .selectors(java.util.Arrays.stream(a).map(DiscoverySelectors::selectClass).toList()).build();
    Launcher launcher = LauncherFactory.create();
    SummaryGeneratingListener l = new SummaryGeneratingListener();
    launcher.execute(req, l);
    var s = l.getSummary();
    s.printFailuresTo(new java.io.PrintWriter(System.out), 12);
    System.out.printf("%nTESTS found=%d passed=%d failed=%d skipped=%d%n",
        s.getTestsFoundCount(), s.getTestsSucceededCount(), s.getTestsFailedCount(),
        s.getTestsSkippedCount());
    if (s.getTestsFailedCount() > 0) System.exit(1);
  }
}
JAVA
javac -nowarn -d "$WORK/out" -cp "$CP" "$WORK/RunTests.java"

OUT="$(cygpath -w "$WORK/out" 2>/dev/null || echo "$WORK/out")"
RES="$(cygpath -w "$ROOT/src/test/resources" 2>/dev/null || echo "$ROOT/src/test/resources")"

classes=$(cd "$WORK/out" && find com/webcash/iris/biztalk/alimtalk -name '*Test.class' ! -name '*$*' \
  | sed 's#[/\\]#.#g; s#\.class$##' | tr '\n' ' ')
echo "==> running $(echo "$classes" | wc -w) test classes"

# 테스트가 0건 발견되는 것은 성공이 아니라 실패다 — 클래스 선택이 조용히 빗나간 경우다.
# Discovering zero tests is a failure, not a success: it means class selection missed silently.
if [ -z "${classes// /}" ]; then
  echo "==> no test classes discovered" >&2
  exit 3
fi

# ---------------------------------------------------------------------------
# 커버리지 / coverage.
# `--coverage` 를 주면 JaCoCo 에이전트를 붙이고 클래스별 표를 낸다. 합계만 보면 구멍이
# 보이지 않는다 — 반복 3에서 85.5 % 합계가 컨트롤러의 0 % 를 가리고 있었다.
# `--coverage` attaches the JaCoCo agent and prints a per-class table. An aggregate hides holes: in
# iteration 3 an 85.5 % total concealed the controller at 0 %.
# ---------------------------------------------------------------------------
if [ "${1:-}" = "--coverage" ]; then
  JACOCO_AGENT="$M2/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar"
  JACOCO_CORE="$M2/org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar"
  ASM_CP=""
  for a in asm/9.7/asm-9.7 asm-commons/9.7/asm-commons-9.7 asm-tree/9.7/asm-tree-9.7; do
    ASM_CP="$ASM_CP;$(cygpath -w "$M2/org/ow2/asm/$a.jar" 2>/dev/null || echo "$M2/org/ow2/asm/$a.jar")"
  done
  for j in "$JACOCO_AGENT" "$JACOCO_CORE"; do
    [ -f "$j" ] || { echo "==> JaCoCo absent: $j" >&2; exit 2; }
  done

  EXEC="$WORK/jacoco.exec"
  # java 는 Windows 바이너리이므로 -javaagent 경로도 Windows 형식이어야 한다.
  # java is a Windows binary, so the -javaagent path must be in Windows form too.
  AGENT_W="$(cygpath -w "$JACOCO_AGENT" 2>/dev/null || echo "$JACOCO_AGENT")"
  EXEC_W="$(cygpath -w "$EXEC" 2>/dev/null || echo "$EXEC")"

  # 대상 패키지만 계측한다 / instrument only the subject package.
  java "-javaagent:$AGENT_W=destfile=$EXEC_W,includes=com.webcash.iris.biztalk.alimtalk.*" \
       -cp "$OUT;$CP;$RES" RunTests $classes

  # 프로덕션 클래스만 분석 대상으로 둔다 / analyse production classes only.
  mkdir -p "$WORK/prod"
  (cd "$WORK/out" && find com/webcash/iris/biztalk/alimtalk -name '*.class' ! -name '*Test.class' \
     ! -name '*Test$*.class' -exec cp --parents {} "$WORK/prod" \;)

  CORE_W="$(cygpath -w "$JACOCO_CORE" 2>/dev/null || echo "$JACOCO_CORE")"
  javac -nowarn -encoding UTF-8 -d "$WORK/out" -cp "$CORE_W$ASM_CP" "$ROOT/qa/CoverageReport.java"

  echo
  java -cp "$OUT;$CORE_W$ASM_CP" CoverageReport "$EXEC_W" \
    "$(cygpath -w "$WORK/prod" 2>/dev/null || echo "$WORK/prod")" \
    "${MIN_LINE:-90}" "${MIN_BRANCH:-85}"
  exit $?
fi

java -cp "$OUT;$CP;$RES" RunTests $classes
