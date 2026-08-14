package com.webcash.iris;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
@SpringBootApplication
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
