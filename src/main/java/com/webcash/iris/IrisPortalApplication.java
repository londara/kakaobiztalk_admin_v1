package com.webcash.iris;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * IRIS BizTalk Portal 애플리케이션 진입점.
 * Entry point for the IRIS BizTalk Portal, the replacement for the legacy
 * {@code IRIS_ADMIN} login and biztalk modules.
 *
 * <p>레거시 Jex 프레임워크는 사용하지 않는다. Jex 런타임이 제공하던 인증 게이팅,
 * 서비스 시간대 제한, 사용량 상한, 감사 로그는 {@code com.webcash.iris.common}
 * 패키지에서 명시적 컴포넌트로 재구현된다.</p>
 * <p>The Jex framework is discarded. The behaviours its runtime supplied —
 * per-service auth gating, service time windows, usage caps and audit logging —
 * are rebuilt as explicit components under {@code com.webcash.iris.common},
 * because discarding the runtime would otherwise remove them silently.</p>
 *
 * @see <a href="../../../../../docs/design/architecture-overview-LOGIN.md">architecture-overview-LOGIN.md</a>
 */
/*
 * UserDetailsServiceAutoConfiguration 을 제외한다.
 *
 * 이 자동설정은 UserDetailsService 빈이 없을 때 임의 비밀번호를 가진 인메모리 사용자
 * (inMemoryUserDetailsManager)를 만들고 "Using generated security password: ..." 를
 * 로그에 남긴다. 그러나 이 시스템의 로그인은 Spring 의 AuthenticationManager 를 전혀
 * 사용하지 않는다 — AuthenticationController 가 AuthenticationService 를 통해
 * A_USER_LDGR 을 직접 검증한다(레거시 apc_login_proc_act.jsp 와 동일한 방식). 따라서
 * 생성된 기본 사용자는 <b>사용되지 않는 잔여물</b>이며, 존재 자체가 혼란과 불필요한
 * 계정을 만든다. 레거시에는 이런 개념이 없었다.
 *
 * Excludes UserDetailsServiceAutoConfiguration: it creates an in-memory user with a random
 * password (and logs "Using generated security password: ...") when no UserDetailsService bean
 * exists. But login here never uses Spring's AuthenticationManager — the controller verifies
 * A_USER_LDGR directly through AuthenticationService, as the legacy did. The generated user is
 * therefore an unused artifact; the legacy had no such concept.
 *
 * req: FR-LOGIN-001, CONST-TECH-L01
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class IrisPortalApplication {

    /**
     * 애플리케이션을 시작한다. / Starts the application.
     *
     * @param args 커맨드라인 인자 / command line arguments
     */
    // req: CONST-TECH-L01
    public static void main(String[] args) {
        SpringApplication.run(IrisPortalApplication.class, args);
    }
}
