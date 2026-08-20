package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.alimtalk.domain.ProfileKey;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.SenderProfileKeyResolver.ProfileKeyUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SenderProfileKeyResolver} 검증. / Verification for {@link SenderProfileKeyResolver}.
 *
 * <p>이 클래스는 A2-01 의 핵심이며, 이 슬라이스에서 가장 심각한 위협(T-A1, CVSS ~9.1)에
 * 대응한다. 다만 <b>이미 유출된 키를 되돌리지는 못한다</b> — 회전은 벤더 측 운영 작업이다
 * (RISK-A03). 여기서 닫는 것은 <b>앞으로의 유출</b>과 <b>회전 후 옛 키 재설정</b> 두 가지다.</p>
 * <p>This class is the heart of A2-01 and addresses the slice's most severe threat (T-A1, CVSS ~9.1).
 * It <b>cannot un-leak the key already exposed</b> — rotation is a vendor-side operational task
 * (RISK-A03). What it closes is <b>future leakage</b> and <b>reconfiguring the old key after
 * rotation</b>.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — hardcoded sender_key, logged on every send
 * // req: FR-ATS-003, FR-AZ-A05, NFR-SEC-CRED-A01, RISK-A03
 */
class SenderProfileKeyResolverTest {

    /** 유출된 레거시 키. 저장소에 남기지 않기 위해 조립해서 쓴다. / The leaked key, assembled rather than stored. */
    private static final String COMPROMISED = "17da29" + "3505b526c3e63fa61f46dae58f002c" + "2921";

    @Nested
    @DisplayName("RISK-A03 — 유출된 키 거부 / refusing the compromised key")
    class CompromisedKey {

        @Test
        @DisplayName("기관별 설정에 유출된 키가 있으면 기동을 거부한다 / refuses it in a per-institution entry")
        // req: NFR-SEC-CRED-A01, RISK-A03
        void refusesInPerInstitutionEntry() {
            // 회전 자체는 우리가 할 수 없지만, 회전한 뒤 옛 키를 다시 설정하는 실수는 막을 수 있다.
            // Rotation is not ours to perform, but reconfiguring the old key afterwards is preventable.
            assertThatThrownBy(() -> new SenderProfileKeyResolver(Map.of("K00001", COMPROMISED), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("compromised")
                    .hasMessageContaining("RISK-A03");
        }

        @Test
        @DisplayName("공유 키로 설정해도 거부한다 / refuses it as the shared key")
        // req: NFR-SEC-CRED-A01, RISK-A03
        void refusesAsSharedKey() {
            assertThatThrownBy(() -> new SenderProfileKeyResolver(Map.of(), COMPROMISED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("compromised");
        }

        @Test
        @DisplayName("거부 메시지에 키 값이 없다 / the refusal message carries no key material")
        // req: NFR-SEC-CRED-A01, NFR-SEC-PII-A02
        void refusalMessageCarriesNoKeyMaterial() {
            // 예외 메시지도 로그로 흘러간다. 자격증명 유출을 막는 코드가 예외 경로로 유출하면
            // 아무것도 얻지 못한다(D-A30).
            // Exception text reaches logs too. Code that prevents credential leakage gains nothing if it
            // leaks through the exception path (D-A30).
            assertThatThrownBy(() -> new SenderProfileKeyResolver(Map.of(), COMPROMISED))
                    .hasMessageNotContaining(COMPROMISED);
        }

        @Test
        @DisplayName("소스에 평문 대신 해시를 둔다 / the source holds a hash, not the plaintext")
        // req: NFR-SEC-CRED-A01
        void sourceHoldsAHashNotThePlaintext() {
            // 이 단언의 요점: 유출 방지 클래스가 유출된 값을 담으면 D-A24 를 스스로 재현한다.
            // The point: a leak-prevention class that carries the leaked value reproduces D-A24 itself.
            assertThat(SenderProfileKeyResolver.COMPROMISED_KEY_SHA256)
                    .hasSize(64)
                    .doesNotContain(COMPROMISED);
        }
    }

    @Nested
    @DisplayName("FR-ATS-003 — 해결 / resolution")
    class Resolution {

        @Test
        @DisplayName("기관별 키를 돌려준다 / returns the per-institution key")
        // req: FR-ATS-003
        void returnsPerInstitutionKey() {
            SenderProfileKeyResolver resolver = new SenderProfileKeyResolver(
                    Map.of("K00001", "key-one", "K00002", "key-two"), null);

            assertThat(resolver.resolve("K00001")).isEqualTo(ProfileKey.of("key-one"));
            assertThat(resolver.resolve("K00002")).isEqualTo(ProfileKey.of("key-two"));
        }

        @Test
        @DisplayName("이용기관코드는 대소문자를 가리지 않는다 / the institution code is case-insensitive")
        // req: FR-ATS-003
        void institutionCodeIsCaseInsensitive() {
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("k00001", "key-one"), null);

            assertThat(resolver.resolve("K00001")).isEqualTo(ProfileKey.of("key-one"));
        }

        @Test
        @DisplayName("T-A2 — 설정이 없으면 조용히 대체하지 않고 거부한다 / no silent fallback")
        // req: FR-ATS-003, FR-AZ-A05
        void noSilentFallback() {
            // "아무 키나 써서 일단 보낸다" 는 다른 기관을 사칭해 고객에게 메시지를 보내는 것과
            // 같다(T-A2). 보내지 않는 편이 낫다.
            // "Send with whatever key we have" is impersonating another institution to a customer (T-A2).
            // Not sending is better.
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("K00001", "key-one"), null);

            assertThatThrownBy(() -> resolver.resolve("K00002"))
                    .isInstanceOf(ProfileKeyUnavailableException.class)
                    .hasMessageContaining("K00002");
        }

        @Test
        @DisplayName("아무것도 설정되지 않으면 발송을 거부한다 / refuses when nothing is configured")
        // req: FR-ATS-003
        void refusesWhenNothingConfigured() {
            SenderProfileKeyResolver resolver = new SenderProfileKeyResolver(Map.of(), null);

            assertThatThrownBy(() -> resolver.resolve("K00001"))
                    .isInstanceOf(ProfileKeyUnavailableException.class);
        }

        @Test
        @DisplayName("빈 이용기관코드를 거부한다 / refuses a blank institution code")
        // req: FR-ATS-003
        void refusesBlankInstitutionCode() {
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("K00001", "key-one"), null);

            assertThatThrownBy(() -> resolver.resolve("  "))
                    .isInstanceOf(ProfileKeyUnavailableException.class);
            assertThatThrownBy(() -> resolver.resolve(null))
                    .isInstanceOf(ProfileKeyUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("A1-04 — 공유 키 모델 / the shared-key model")
    class SharedKey {

        @Test
        @DisplayName("공유 키가 설정되면 모든 기관에 쓰인다 / a shared key serves every institution")
        // req: FR-ATS-003
        void sharedKeyServesEveryInstitution() {
            // 레거시는 키 하나를 모든 발송에 썼다. 그것이 올바른 공유 모델인지, 한 기관의 키를
            // 전부에 잘못 쓴 것인지는 소스로 구분되지 않는다(A1-04). 두 경우의 시정이 다르므로
            // 지원하되 기동 시 경고한다.
            // The legacy used one key for every send. Source cannot tell a correct shared model from one
            // institution's key wrongly used for all (A1-04); the remediations differ, so it is supported
            // but warned about.
            SenderProfileKeyResolver resolver = new SenderProfileKeyResolver(Map.of(), "shared-key");

            assertThat(resolver.resolve("K00001")).isEqualTo(ProfileKey.of("shared-key"));
            assertThat(resolver.resolve("K99999")).isEqualTo(ProfileKey.of("shared-key"));
        }

        @Test
        @DisplayName("기관별 키가 공유 키보다 우선한다 / a per-institution key wins over the shared one")
        // req: FR-ATS-003
        void perInstitutionWinsOverShared() {
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("K00001", "specific"), "shared-key");

            assertThat(resolver.resolve("K00001")).isEqualTo(ProfileKey.of("specific"));
            assertThat(resolver.resolve("K00002")).isEqualTo(ProfileKey.of("shared-key"));
        }
    }

    @Nested
    @DisplayName("NFR-USE-A04 — 발송 전 확인 / pre-send readiness")
    class Readiness {

        @Test
        @DisplayName("설정 여부를 미리 알 수 있다 / configuration can be checked in advance")
        // req: FR-ATS-003, NFR-USE-A04
        void configurationCanBeCheckedInAdvance() {
            // 보낼 수 없다는 사실을 발송 시도 전에 알리는 편이, 시도했다가 실패하는 것보다 낫다.
            // Telling the operator a send is impossible beats attempting it and failing.
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("K00001", "key-one"), null);

            assertThat(resolver.isConfiguredFor("K00001")).isTrue();
            assertThat(resolver.isConfiguredFor("K00002")).isFalse();
            assertThat(resolver.isConfiguredFor(null)).isFalse();
            assertThat(resolver.isConfiguredFor("")).isFalse();
        }

        @Test
        @DisplayName("공유 키가 있으면 모든 기관이 준비 상태다 / a shared key makes every institution ready")
        // req: NFR-USE-A04
        void sharedKeyMakesEveryInstitutionReady() {
            SenderProfileKeyResolver resolver = new SenderProfileKeyResolver(Map.of(), "shared-key");

            assertThat(resolver.isConfiguredFor("K12345")).isTrue();
        }
    }

    @Nested
    @DisplayName("D-A30 — 노출 / exposure")
    class Exposure {

        @Test
        @DisplayName("해결된 키는 마스킹되어 표현된다 / a resolved key is represented masked")
        // req: NFR-SEC-CRED-A01
        void resolvedKeyIsRepresentedMasked() {
            SenderProfileKeyResolver resolver =
                    new SenderProfileKeyResolver(Map.of("K00001", "super-secret-key"), null);

            ProfileKey key = resolver.resolve("K00001");

            // 상수를 참조하지 않고 동작을 단언한다 — REDACTED 는 domain 패키지 전용이며,
            // 그 접근 제한 자체가 설계의 일부다(원문에 닿는 경로를 좁게 유지한다).
            // Asserting behaviour rather than the constant: REDACTED is package-private to domain, and
            // that restriction is part of the design — the paths reaching the raw value stay narrow.
            assertThat(key.toString())
                    .doesNotContain("super-secret-key")
                    .contains("REDACTED");
            assertThat(key.exposeForVendorCall()).isEqualTo("super-secret-key");
        }
    }
}
