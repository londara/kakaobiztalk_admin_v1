package com.webcash.iris.auth.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 개발 환경 전용 OTP 우회 스위치. / A development-only OTP bypass switch.
 *
 * <p><b>이 클래스는 인증 통제를 의도적으로 약화시킨다.</b> 존재 이유는 로컬 개발에서 매
 * 로그인마다 OTP 앱을 여는 마찰을 없애는 것뿐이며, 그 외의 정당한 용도는 없다.
 * 설계 결정과 그 대가는 ADR-LOGIN-020 에 기록되어 있다.</p>
 * <p><b>This class deliberately weakens an authentication control.</b> It exists only to remove
 * the friction of opening an OTP app on every local login, and has no other legitimate use. The
 * decision and its cost are recorded in ADR-LOGIN-020.</p>
 *
 * <h2>왜 "기본값 false" 로는 부족한가 / why "defaults to false" is not enough</h2>
 * <p>이 코드베이스는 이미 그 교훈을 담고 있다 — {@code require-https} 는 기본값을
 * {@code true} 로 두어 <b>운영에서 잊어도 평문이 열리지 않게</b> 한다. 같은 원칙을 적용하면,
 * 꺼져 있는 것이 기본인 스위치로는 부족하다. {@code application-prod.yml} 에 한 줄이 잘못
 * 들어가는 순간 인터넷에 노출된 포털(CONST-SEC-01)이 단일 요소 인증으로 동작하고, 그 상태는
 * 아무것도 실패하지 않으므로 <b>조용하다</b>.</p>
 * <p>The codebase already carries this lesson: {@code require-https} defaults to {@code true} so
 * production cannot fail open by omission. By the same principle a merely off-by-default switch
 * is insufficient — one wrong line in {@code application-prod.yml} would put an internet-facing
 * portal (CONST-SEC-01) on single-factor authentication, and nothing would fail, so nothing would
 * announce it.</p>
 *
 * <p>따라서 이 스위치는 <b>기동을 거부</b>한다. {@code local} 프로필이 아닌 곳에서 켜져
 * 있으면 애플리케이션이 뜨지 않는다. 잘못된 설정은 배포 실패로 즉시 드러나며, 조용한
 * 취약점으로 남지 않는다.</p>
 * <p>So the switch <b>refuses to start</b>: enabled outside the {@code local} profile, the
 * application does not come up. A misconfiguration surfaces as a failed deployment instead of a
 * silent vulnerability.</p>
 *
 * // req: FR-LOGIN-010, NFR-SEC-AUTH-L01, CONST-SEC-01
 * // ADR: ADR-LOGIN-020
 */
@Component
public class OtpDevBypass {

    private static final Logger log = LoggerFactory.getLogger(OtpDevBypass.class);

    /** 이 스위치가 허용되는 유일한 프로필. / The only profile in which this switch is permitted. */
    static final String PERMITTED_PROFILE = "local";

    private final boolean enabled;
    private final List<String> activeProfiles;

    /**
     * 스위치를 생성한다. / Creates the switch.
     *
     * @param enabled     설정값 / the configured value
     * @param environment 활성 프로필 확인용 / used to inspect the active profiles
     */
    // req: NFR-SEC-AUTH-L01
    public OtpDevBypass(
            @Value("${iris.auth.otp.dev-bypass-enabled:false}") boolean enabled,
            Environment environment) {
        this.enabled = enabled;
        this.activeProfiles = Arrays.asList(environment.getActiveProfiles());
    }

    /**
     * 설정이 안전한지 기동 시점에 검증한다. / Validates the configuration at startup.
     *
     * <p>{@code local} 이 아닌 곳에서 켜져 있으면 예외를 던져 기동을 중단시킨다. 경고 로그로
     * 넘기지 않는 이유는, 경고는 무시되기 때문이다 — 그리고 이 경우 무시된 경고의 대가가
     * 단일 요소 인증이다.</p>
     * <p>Throws and halts startup when enabled outside {@code local}. Not a warning, because
     * warnings get ignored — and here the cost of an ignored warning is single-factor
     * authentication.</p>
     *
     * @throws IllegalStateException 운영 계열 프로필에서 켜져 있을 때 / when enabled outside local
     */
    // req: NFR-SEC-AUTH-L01, CONST-SEC-01
    @PostConstruct
    void verifyConfiguration() {
        if (!enabled) {
            return;
        }
        if (!activeProfiles.contains(PERMITTED_PROFILE)) {
            throw new IllegalStateException(
                    "iris.auth.otp.dev-bypass-enabled=true is only permitted under the '"
                            + PERMITTED_PROFILE + "' profile, but the active profiles are "
                            + activeProfiles + ". Refusing to start: enabling this outside local "
                            + "development would place the portal on single-factor authentication "
                            + "(FR-LOGIN-010, NFR-SEC-AUTH-L01).");
        }
        log.warn("╔══════════════════════════════════════════════════════════════════╗");
        log.warn("║  OTP DEV BYPASS IS ACTIVE — second factor is NOT verified.       ║");
        log.warn("║  Local development only. Never enable this anywhere else.        ║");
        log.warn("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * 우회가 활성 상태인지 반환한다. / Whether the bypass is active.
     *
     * <p>{@link #verifyConfiguration()} 가 기동 시점에 프로필을 이미 검증했으므로 여기서는
     * 설정값만 본다. 그럼에도 프로필을 <b>다시</b> 확인하는 이유는, 이 메서드가 인증 경로에서
     * 호출되기 때문이다 — 기동 검증이 어떤 이유로든 건너뛰어졌다면(테스트 배선, 수동 생성)
     * 여기서 닫는다.</p>
     * <p>{@link #verifyConfiguration()} has already validated the profile at startup, so the flag
     * alone would do. The profile is checked <b>again</b> anyway because this method sits on the
     * authentication path: if startup validation was skipped for any reason — a test wiring, a
     * hand-constructed instance — this closes it.</p>
     *
     * @return 우회가 활성이면 true / true when the bypass is active
     */
    // req: NFR-SEC-AUTH-L01
    public boolean isActive() {
        return enabled && activeProfiles.contains(PERMITTED_PROFILE);
    }
}
