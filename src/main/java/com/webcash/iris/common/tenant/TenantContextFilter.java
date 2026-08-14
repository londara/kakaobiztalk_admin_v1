package com.webcash.iris.common.tenant;

import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션에서 테넌트 컨텍스트를 확립하는 필터. / Establishes the tenant context from the session.
 *
 * <p>PM 결정 AMB-02 에 따라 이용기관 식별자는 <b>서버가 세션에서 도출</b>한다. 클라이언트가
 * 보낸 값은 이용기관 담당자에게는 무시되고, 그 <b>시도 자체가 감사 기록된다</b> — 레거시는
 * 클라이언트가 보낸 {@code ID} 를 그대로 조회 조건에 넣었으므로(TM-004), 신규 시스템에서
 * 담당자가 이 파라미터를 보내는 것은 정상 사용이 아닐 가능성이 높다.</p>
 * <p>Per PM decision AMB-02 the identifier is derived server-side. A client-supplied value is
 * ignored for tenant users and <b>the attempt is audited</b>: since the legacy fed that value
 * straight into the query (TM-004), a tenant sending the parameter now is more likely probing
 * than using the system normally.</p>
 *
 * <p>이 필터는 인증 필터 <b>뒤에</b> 실행된다({@code @Order(20)}). 인증되지 않은 요청에는
 * 테넌트가 없고, 그 상태로 조회 계층에 도달하면 {@link TenantContext#require()} 가 예외를
 * 던져 실패를 닫는다.</p>
 * <p>Runs after authentication. An unauthenticated request has no tenant, and
 * {@link TenantContext#require()} then throws — failing closed rather than widening.</p>
 *
 * // req: FR-TEN-001, FR-TEN-002, NFR-SEC-TENANT, TM-004
 */
@Component
@Order(20)
public class TenantContextFilter extends OncePerRequestFilter {

    /** 로그인 시 세션에 저장되는 사용자 식별자. / The user id stored at login. */
    // source: apc_login_proc_act.jsp — newSession.setAttribute("userId", eml)
    public static final String SESSION_USER_ID = "userId";
    /** 로그인 시 세션에 저장되는 이용기관 코드. / The 이용기관 code stored at login. */
    public static final String SESSION_INSTITUTION = "institutionCode";
    /** 로그인 시 세션에 저장되는 운영자 여부. / The operator flag stored at login. */
    public static final String SESSION_OPERATOR = "operator";

    /** 클라이언트가 보낼 수 있는 이용기관 파라미터 이름. / The client-supplied parameter name. */
    private static final String SUPPLIED_INSTITUTION_PARAM = "institutionCode";

    private final AuditService audit;

    /**
     * 필터를 생성한다. / Creates the filter.
     *
     * @param audit 감사 서비스 / the audit service
     */
    public TenantContextFilter(AuditService audit) {
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            bindIfAuthenticated(request);
            auditSuppliedOverride(request);
            chain.doFilter(request, response);
        } finally {
            // 반드시 정리한다. 누락하면 스레드 재사용 시 이전 요청의 테넌트가 남아
            // 다음 사용자가 남의 데이터를 보게 된다 — 정확히 막으려는 사고다.
            // Always cleared: omitting this leaves the previous tenant on a reused thread, and
            // the next user reads someone else's data — the exact incident being prevented.
            TenantContext.clear();
        }
    }

    /**
     * 세션이 있으면 테넌트 컨텍스트를 확립한다. / Binds the context when a session exists.
     */
    // req: FR-TEN-001
    private void bindIfAuthenticated(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return;
        }
        TenantContext.set(new TenantContext.TenantPrincipal(
                userId.toString(),
                (String) session.getAttribute(SESSION_INSTITUTION),
                Boolean.TRUE.equals(session.getAttribute(SESSION_OPERATOR))));
    }

    /**
     * 이용기관 담당자가 이용기관 파라미터를 보낸 경우 감사 기록한다.
     * Audits a client-supplied 이용기관 parameter sent by a tenant user.
     *
     * <p>값을 사용하지는 않는다 — 무시는 {@link TenantContext.TenantPrincipal} 이 담당한다.
     * 여기서는 <b>탐지와 기록</b>만 한다.</p>
     * <p>The value is not used — ignoring it is the principal's job. This method only detects
     * and records.</p>
     */
    // req: NFR-OPS-AUDIT, TM-004
    private void auditSuppliedOverride(HttpServletRequest request) {
        String supplied = request.getParameter(SUPPLIED_INSTITUTION_PARAM);
        if (supplied == null || supplied.isBlank() || !TenantContext.isBound()) {
            return;
        }
        var principal = TenantContext.require();
        if (!principal.operator()) {
            audit.recordAuth(principal.email(), AuditEvent.ACTION_TENANT_OVERRIDE_ATTEMPT,
                    AuditEvent.Outcome.DENIED, "client-supplied institutionCode ignored",
                    request.getRemoteAddr(), null);
        }
    }
}
