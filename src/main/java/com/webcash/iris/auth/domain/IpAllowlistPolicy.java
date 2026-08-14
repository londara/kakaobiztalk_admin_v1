package com.webcash.iris.auth.domain;

import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 접속 IP 허용목록 정책. / Source-address allowlist policy.
 *
 * <h2>레거시 결함 L5 대응 / Fixes legacy defect L5</h2>
 * <p>레거시 {@code apm_0001_01_view.jsp} 는 로그인 화면을 그릴 때마다
 * {@code A_USER_IP_AUTHED_R001} 로 접속 IP 를 조회했으나, 결과에 따른
 * {@code response.sendRedirect("/error.jsp")} 가 <b>주석 처리되어 있었다.</b> 즉
 * 조회는 매번 수행되고 결과는 버려졌다 — 호출부만 읽으면 IP 제한이 동작하는 것처럼
 * 보이지만 실제로는 아무도 차단되지 않았다.</p>
 * <p>The legacy queried the allowlist on every render of the login screen but had the
 * resulting {@code response.sendRedirect("/error.jsp")} <b>commented out</b>: the lookup
 * ran and the answer was discarded. Reading the call site alone suggested IP restriction
 * worked; in fact nobody was ever blocked.</p>
 *
 * <h2>범위 — AMB-L03 / Scope — AMB-L03</h2>
 * <p>PM 결정 대기 중이며 <b>운영자 전용</b>을 가정한다. 외부 이용기관 담당자는 고정 IP 를
 * 갖지 않으므로 허용목록을 적용할 수 없다. 이 가정이 틀리면 설정 하나로 바뀐다.</p>
 * <p>Pending the PM ruling, this assumes <b>operators only</b>: external client-company
 * users do not have stable addresses, so an allowlist cannot apply to them. If the
 * assumption is wrong, one configuration flag changes it.</p>
 *
 * <p><b>기본값은 비활성이다.</b> 허용목록을 켠 채 대역을 비워두면 운영자 전원이 잠긴다.
 * 명시적으로 활성화하고 대역을 채워야 동작한다.</p>
 * <p><b>Disabled by default:</b> enabling it with an empty range list would lock out every
 * operator. It requires explicit enablement together with configured ranges.</p>
 *
 * // source: apm_0001_01_view.jsp — commented-out redirect (defect L5)
 * // req: FR-LOGIN-024, AMB-L03
 */
@Component
public class IpAllowlistPolicy {

    private final boolean enabled;
    private final boolean operatorsOnly;
    private final CidrMatcher matcher;
    private final AuditService audit;

    /**
     * 정책을 생성한다. / Creates the policy.
     *
     * @param enabled       허용목록 사용 여부 / whether the allowlist is in use
     * @param operatorsOnly 운영자에게만 적용할지 여부 / whether it applies to operators only
     * @param cidrs         허용 대역 목록 / the allowed ranges
     * @param audit         감사 서비스 / the audit service
     */
    // req: FR-LOGIN-024
    public IpAllowlistPolicy(
            @Value("${iris.auth.ip-allowlist.enabled:false}") boolean enabled,
            @Value("${iris.auth.ip-allowlist.applies-to-operators-only:true}") boolean operatorsOnly,
            @Value("${iris.auth.ip-allowlist.cidrs:}") List<String> cidrs,
            AuditService audit) {
        this.enabled = enabled;
        this.operatorsOnly = operatorsOnly;
        this.matcher = new CidrMatcher(cidrs);
        this.audit = audit;

        if (enabled && matcher.isEmpty()) {
            // 활성화했으나 대역이 없다 — 전원 차단 상태다. 기동 시점에 실패시킨다.
            // Enabled with no ranges means everyone is blocked. Fail at startup.
            throw new IllegalStateException(
                    "iris.auth.ip-allowlist.enabled=true but no CIDR range is configured. "
                            + "This would deny every login. Configure "
                            + "iris.auth.ip-allowlist.cidrs or disable the allowlist.");
        }
    }

    /**
     * 접속을 허용할지 판정한다. 거부 시 예외를 던진다.
     * Decides whether the source is permitted, raising when it is not.
     *
     * <p>운영자 여부는 인증 <b>전</b>에는 알 수 없다. 따라서 로그인 시점에는 이메일만으로
     * 판단할 수 없고, 이 메서드는 인증 성공 후 역할이 확정된 뒤 호출되어야 한다 —
     * 그렇지 않으면 운영자 전용 규칙을 적용할 대상을 구분할 수 없다.</p>
     * <p>Whether the caller is an operator is unknown <b>before</b> authentication, so this
     * must be invoked after the role is resolved; otherwise there is no way to tell whom the
     * operators-only rule applies to.</p>
     *
     * @param email    계정 이메일 / the account email
     * @param operator 운영자 여부 / whether the principal is an operator
     * @param sourceIp 신뢰 가능한 출처 IP / the trusted source address
     * @throws AuthenticationException 허용되지 않은 출처일 때 / when the source is not allowed
     */
    // req: FR-LOGIN-024, NFR-OPS-AUDIT-L01
    public void assertAllowed(String email, boolean operator, String sourceIp) {
        if (!enabled) {
            return;
        }
        if (operatorsOnly && !operator) {
            // 이용기관 담당자에게는 적용하지 않는다 (AMB-L03 가정).
            // Not applied to client-company users (AMB-L03 assumption).
            return;
        }
        if (matcher.matches(sourceIp)) {
            return;
        }
        audit.recordAuth(email, AuditEvent.ACTION_LOGIN, AuditEvent.Outcome.DENIED,
                "source-not-allowlisted", sourceIp, null);
        throw new AuthenticationException(AuthFailureReason.IP_NOT_ALLOWED);
    }
}
