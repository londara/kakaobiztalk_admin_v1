package com.webcash.iris.auth.config;

import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TOTP 시각 공급자 설정. / TOTP time provider configuration.
 *
 * <p>{@link TimeProvider} 를 빈으로 분리하는 이유는 시간 오차 허용(±1 스텝)을
 * 테스트에서 실제로 검증하기 위함이다. 시각을 고정할 수 없으면 "이전 스텝의 코드가
 * 통과한다"는 성질을 확인할 방법이 없고, 그 성질이 바로 레거시 결함 L3 의 회귀
 * 방지 지점이다.</p>
 * <p>Exposing {@link TimeProvider} as a bean is what makes the ±1 step tolerance
 * testable. Without a pinnable clock there is no way to assert that a code from the
 * previous step is accepted — and that assertion is the regression guard for legacy
 * defect L3.</p>
 *
 * // req: FR-LOGIN-011, ADR-LOGIN-010
 */
@Configuration
public class TotpConfig {

    /**
     * 시스템 시각 공급자를 제공한다. / Provides the system time provider.
     *
     * @return 시각 공급자 / the time provider
     */
    @Bean
    public TimeProvider timeProvider() {
        return new SystemTimeProvider();
    }

    /**
     * OTP 비밀키 생성기를 제공한다. / Provides the OTP secret generator.
     *
     * <p><b>레거시 결함 L8 대응.</b> 레거시 {@code GoogleOTP.generate()} 는 30바이트
     * 버퍼를 만든 뒤 앞 <b>10바이트(80비트)</b> 만 사용했다. RFC 6238 은 HMAC-SHA1 에
     * 대해 160비트를 권고한다.</p>
     * <p><b>Fixes legacy defect L8.</b> The legacy allocated a 30-byte buffer and then
     * used only the first <b>10 bytes (80 bits)</b>. RFC 6238 recommends 160 bits for
     * HMAC-SHA1.</p>
     *
     * @return 160비트 비밀키 생성기 / a 160-bit secret generator
     */
    // source: com/common/irisadmin/util/GoogleOTP.java — Arrays.copyOf(buffer, 10)
    // req: FR-OTP-002, NFR-SEC-AUTH-L03
    @Bean
    public SecretGenerator secretGenerator() {
        return new DefaultSecretGenerator(20);
    }
}
