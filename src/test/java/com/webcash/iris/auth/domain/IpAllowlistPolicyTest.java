package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link IpAllowlistPolicy} 단위 테스트. / Unit tests for {@link IpAllowlistPolicy}.
 *
 * <h2>이 시험이 존재하는 이유 / why this exists</h2>
 * <p>레거시 결함 L5 의 정확한 형태를 기억해야 한다: {@code apm_0001_01_view.jsp} 는 매 화면
 * 렌더마다 허용목록을 <b>조회했고</b>, 그 결과에 따른 리다이렉트는 <b>주석 처리되어</b> 있었다.
 * 조회는 돌고 판정은 버려졌으므로, 호출부만 읽으면 IP 제한이 동작하는 것처럼 보였다 —
 * 실제로 차단된 사람은 아무도 없었다.</p>
 * <p>Legacy defect L5's exact shape matters: the JSP <b>queried</b> the allowlist on every render
 * and the resulting redirect was <b>commented out</b>. The lookup ran and the verdict was
 * discarded, so reading the call site suggested IP restriction worked while nobody was ever
 * blocked.</p>
 *
 * <p><b>따라서 이 시험의 핵심은 "거절이 실제로 일어나는가" 다.</b> 판정 로직만 검증하고
 * 예외 전파를 검증하지 않으면 L5 와 정확히 같은 결함을 재현할 수 있다 — 계산은 맞는데
 * 아무도 막지 않는 상태. 커버리지 측정 결과 이 클래스는 <b>18행 전부 미검증</b>이었다.</p>
 * <p><b>So the load-bearing assertion is that refusal actually happens.</b> Verifying the decision
 * without verifying that it propagates would permit exactly L5 again: a correct computation that
 * blocks no one. Coverage showed all 18 lines untested.</p>
 *
 * // source: apm_0001_01_view.jsp — commented-out redirect (defect L5)
 * // req: FR-LOGIN-024, AMB-L03, NFR-OPS-AUDIT-L01
 */
class IpAllowlistPolicyTest {

    private static final String EMAIL = "operator@webcash.co.kr";
    private static final List<String> RANGES = List.of("10.0.0.0/8", "192.168.1.0/24");

    private AuditService audit;

    @BeforeEach
    void setUp() {
        audit = mock(AuditService.class);
    }

    private IpAllowlistPolicy policy(boolean enabled, boolean operatorsOnly, List<String> cidrs) {
        return new IpAllowlistPolicy(enabled, operatorsOnly, cidrs, audit);
    }

    @Nested
    @DisplayName("비활성 상태 / disabled")
    class Disabled {

        @Test
        @DisplayName("비활성이면 어떤 출처도 통과한다 / any source passes when disabled")
        // req: FR-LOGIN-024
        void disabledAllowsEverything() {
            // 기본값이 비활성인 것은 의도다. 대역 없이 켜면 운영자 전원이 잠긴다.
            // Disabled by default is deliberate: enabling with no ranges locks out every operator.
            IpAllowlistPolicy p = policy(false, true, List.of());

            assertThatCode(() -> p.assertAllowed(EMAIL, true, "203.0.113.9"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비활성이면 감사 기록도 남기지 않는다 / no audit entry is written when disabled")
        // req: NFR-OPS-AUDIT-L01
        void disabledWritesNoAudit() {
            // 통제가 꺼져 있는데 거절 감사가 쌓이면 로그가 오해를 만든다.
            // A DENIED entry while the control is off would make the log misleading.
            policy(false, true, List.of()).assertAllowed(EMAIL, true, "203.0.113.9");

            verify(audit, never()).recordAuth(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("활성 상태 / enabled")
    class Enabled {

        @Test
        @DisplayName("허용 대역 내 운영자는 통과한다 / an operator inside a range passes")
        // req: FR-LOGIN-024
        void operatorInRangePasses() {
            IpAllowlistPolicy p = policy(true, true, RANGES);

            assertThatCode(() -> p.assertAllowed(EMAIL, true, "10.5.5.5"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> p.assertAllowed(EMAIL, true, "192.168.1.42"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("허용 대역 밖 운영자는 거절된다 — L5 회귀 방지 / an operator outside every range is refused (L5 regression)")
        // req: FR-LOGIN-024
        void operatorOutOfRangeIsRefused() {
            // <b>이 단정이 L5 의 재발을 막는다.</b> 판정이 옳아도 예외가 전파되지 않으면
            // 레거시와 동일한 상태다 — 조회는 돌고 아무도 막히지 않는다.
            // <b>This is what prevents L5 from returning.</b> A correct verdict that does not
            // propagate leaves the legacy behaviour: the lookup runs and nobody is blocked.
            IpAllowlistPolicy p = policy(true, true, RANGES);

            assertThatThrownBy(() -> p.assertAllowed(EMAIL, true, "203.0.113.9"))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.IP_NOT_ALLOWED);
        }

        @Test
        @DisplayName("거절은 감사에 기록된다 / a refusal is audited")
        // req: NFR-OPS-AUDIT-L01
        void refusalIsAudited() {
            // 차단되었다는 사실 자체가 침해 조사의 입력이다. 조용히 거절하면 대입 시도의
            // 출처를 사후에 알 수 없다.
            // The refusal itself is investigative input; refusing silently loses the origin of an
            // attempted intrusion.
            IpAllowlistPolicy p = policy(true, true, RANGES);

            assertThatThrownBy(() -> p.assertAllowed(EMAIL, true, "203.0.113.9"))
                    .isInstanceOf(AuthenticationException.class);

            verify(audit).recordAuth(eq(EMAIL), eq(AuditEvent.ACTION_LOGIN),
                    eq(AuditEvent.Outcome.DENIED), eq("source-not-allowlisted"),
                    eq("203.0.113.9"), eq(null));
        }

        @Test
        @DisplayName("통과 시에는 감사를 남기지 않는다 / a permitted source writes no audit entry")
        // req: NFR-OPS-AUDIT-L01
        void permittedSourceWritesNoAudit() {
            policy(true, true, RANGES).assertAllowed(EMAIL, true, "10.5.5.5");

            verify(audit, never()).recordAuth(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("판정할 수 없는 출처는 거절된다 / an unparseable source is refused")
        // req: FR-LOGIN-024
        void unparseableSourceIsRefused() {
            // null 출처는 CidrMatcher 가 false 로 처리하므로 거절된다. 접근 통제에서
            // "알 수 없음"은 "허용"이 아니어야 한다.
            // A null source yields false from CidrMatcher and is refused: in access control,
            // "unknown" must not mean "allowed".
            IpAllowlistPolicy p = policy(true, true, RANGES);

            assertThatThrownBy(() -> p.assertAllowed(EMAIL, true, null))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("적용 범위 — AMB-L03 / scope")
    class Scope {

        @Test
        @DisplayName("운영자 전용이면 이용기관 담당자는 면제된다 / client users are exempt when operators-only")
        // req: FR-LOGIN-024, AMB-L03
        void nonOperatorIsExemptWhenOperatorsOnly() {
            // AMB-L03 가정: 외부 이용기관 담당자는 고정 IP 를 갖지 않으므로 허용목록을 적용할
            // 수 없다. 이 가정이 PM 결정으로 뒤집히면 이 시험이 먼저 실패해 알려준다.
            // AMB-L03's assumption: external client users have no stable address, so the allowlist
            // cannot apply. If the PM rules otherwise, this test fails first and says so.
            IpAllowlistPolicy p = policy(true, true, RANGES);

            assertThatCode(() -> p.assertAllowed("tenant@client.example", false, "203.0.113.9"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("전체 적용이면 이용기관 담당자도 거절된다 / client users are refused when not operators-only")
        // req: FR-LOGIN-024, AMB-L03
        void nonOperatorIsRefusedWhenAppliesToAll() {
            // AMB-L03 이 "전체 적용"으로 결정될 경우의 동작을 미리 고정한다. 설정 한 개로
            // 전환된다는 주장이 사실인지 확인하는 것이기도 하다.
            // Pins the behaviour should AMB-L03 be ruled "applies to all", and checks the claim
            // that one flag switches it.
            IpAllowlistPolicy p = policy(true, false, RANGES);

            assertThatThrownBy(() -> p.assertAllowed("tenant@client.example", false, "203.0.113.9"))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AuthenticationException) e).reason())
                    .isEqualTo(AuthFailureReason.IP_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("설정 오류 / misconfiguration")
    class Misconfiguration {

        @Test
        @DisplayName("활성인데 대역이 없으면 기동을 실패시킨다 / enabled with no ranges fails startup")
        // req: FR-LOGIN-024
        void enabledWithoutRangesFailsStartup() {
            // 이 조합은 운영자 전원 차단을 뜻한다. 기동시켜 놓고 첫 로그인에서 드러나게 하는
            // 대신 즉시 실패시킨다 — 실패 시점이 이를수록 피해가 작다.
            // This combination means every operator is denied. Failing at startup rather than
            // surfacing on the first login keeps the damage small.
            assertThatThrownBy(() -> policy(true, true, List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no CIDR range is configured");
        }

        @Test
        @DisplayName("공백만 있는 대역 목록도 기동을 실패시킨다 / a blank-only range list fails startup")
        // req: FR-LOGIN-024
        void enabledWithBlankRangesFailsStartup() {
            // YAML 에서 흔한 형태다. CidrMatcher 가 공백을 건너뛰므로 결과는 빈 목록과 같고,
            // 따라서 같은 이유로 막아야 한다 — 그렇지 않으면 "설정했다고 생각한" 전면 차단이 된다.
            // A common YAML shape: CidrMatcher skips blanks, so the result equals an empty list and
            // must fail for the same reason — otherwise it is a total outage the operator believes
            // they configured away.
            assertThatThrownBy(() -> policy(true, true, List.of("  ", "")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("비활성이면 대역이 없어도 기동한다 / disabled with no ranges starts normally")
        // req: FR-LOGIN-024
        void disabledWithoutRangesStarts() {
            // 기본 설정이 바로 이것이다. 여기서 실패하면 허용목록을 쓰지 않는 모든 배포가
            // 기동하지 못한다.
            // This is the default configuration; failing here would stop every deployment that
            // does not use an allowlist.
            assertThat(policy(false, true, List.of())).isNotNull();
        }

        @Test
        @DisplayName("잘못된 CIDR 은 기동을 실패시킨다 / a malformed CIDR fails startup")
        // req: FR-LOGIN-024
        void malformedCidrFailsStartup() {
            assertThatThrownBy(() -> policy(true, true, List.of("10.0.0.0/33")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
