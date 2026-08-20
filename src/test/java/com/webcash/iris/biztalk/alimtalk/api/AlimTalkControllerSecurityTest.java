package com.webcash.iris.biztalk.alimtalk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * {@link AlimTalkController} 인가 표면 검증. / Authorization-surface verification for {@link AlimTalkController}.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / why this is not an integration test</h2>
 * <p>본래 {@code @WebMvcTest} 로 미인증·비운영자 호출을 실제로 거절하는지 확인해야 한다
 * (TEST-PLAN-ALIMTALK §4). 그 검증은 여전히 A1-19 의 미완 부분이다. 이 테스트는
 * <b>대체물이며 동등물이 아니다</b> — 요청이 실제로 403 을 받는지는 증명하지 못한다.</p>
 * <p>This should be a {@code @WebMvcTest} asserting that anonymous and non-operator calls are actually
 * rejected (TEST-PLAN-ALIMTALK §4); that remains A1-19's unfinished part. This test is a
 * <b>substitute, not an equivalent</b>: it cannot prove a request receives 403.</p>
 *
 * <p>그럼에도 값이 있는 이유는, 이 슬라이스가 <b>정확히 이 자리에서</b> 결함을 하나 찾았기
 * 때문이다. D-A37 — 저장소 어디에도 {@code @EnableMethodSecurity} 가 없었고, Spring Boot 3 에서
 * 메서드 보안은 기본으로 꺼져 있다. 여섯 컨트롤러의 {@code @PreAuthorize} 가 전부 무력했고,
 * 설계 문서들이 "심층 방어"라 부른 층은 존재하지 않았다. 통합 테스트가 아니라 <b>이 반사 검사</b>
 * 가 그것을 찾았다.</p>
 * <p>It earns its place because this slice found a defect <b>at exactly this spot</b>: D-A37 — no
 * {@code @EnableMethodSecurity} anywhere, and method security is off by default in Spring Boot 3. Every
 * {@code @PreAuthorize} on six controllers was inert, and the layer the design documents call "defence in
 * depth" did not exist. A reflection check, not an integration test, is what found it.</p>
 *
 * // source: WSVC.biztalk_admin_61.xml (login=Y only); biztalk_admin_10.js — browser-side alert('권한 없음')
 * // req: FR-AZ-A01, FR-AZ-A03, FR-AZ-A05, NFR-SEC-AUTHZ-A01
 */
class AlimTalkControllerSecurityTest {

    /** 프로파일키로 읽힐 수 있는 이름들. / Names that could carry a profile key. */
    private static final List<String> CREDENTIAL_NAMES =
            List.of("senderkey", "sender_key", "profilekey", "profile_key", "apikey", "api_key");

    private static List<Method> endpoints() {
        return Arrays.stream(AlimTalkController.class.getDeclaredMethods())
                .filter(m -> Arrays.stream(m.getAnnotations())
                        .anyMatch(a -> a.annotationType().isAnnotationPresent(RequestMapping.class)
                                || a.annotationType().equals(RequestMapping.class)))
                .toList();
    }

    @Nested
    @DisplayName("D-A37 — 메서드 보안이 실제로 켜져 있는가 / is method security actually on")
    class MethodSecurityEnabled {

        @Test
        @DisplayName("@EnableMethodSecurity 가 설정에 있다 / the configuration enables method security")
        // req: NFR-SEC-AUTHZ-A01, FR-AZ-A01
        void methodSecurityIsEnabled() throws Exception {
            // 이 단언이 D-A37 을 찾았다. Spring Boot 3 에서 @EnableMethodSecurity 가 없으면
            // @PreAuthorize 는 아무것도 하지 않는다 — 애노테이션은 남아 있고 보호는 사라진다.
            // 화면 10 의 레거시가 브라우저에서 alert('권한 없음') 을 띄우고 서버는 아무도 막지
            // 않았던 것(D-S2)과 같은 모양이며, 이번에는 우리 코드에서 일어났다.
            // This assertion found D-A37. Without @EnableMethodSecurity in Spring Boot 3, @PreAuthorize
            // does nothing — the annotation remains and the protection does not. The same shape as the
            // legacy's browser-side alert('권한 없음') while the server refused nobody (D-S2), this time
            // in our own code.
            Class<?> config = Class.forName("com.webcash.iris.auth.config.SecurityConfig");

            assertThat(config.isAnnotationPresent(EnableMethodSecurity.class))
                    .as("@EnableMethodSecurity 없으면 모든 @PreAuthorize 가 무력하다 "
                            + "/ without it every @PreAuthorize is inert")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("FR-AZ-A01/A03 — 인가 선언 / authorization declarations")
    class Authorization {

        @Test
        @DisplayName("컨트롤러가 운영자 역할을 요구한다 / the controller requires the operator role")
        // req: FR-AZ-A01, FR-AZ-A03
        void controllerRequiresOperatorRole() {
            PreAuthorize preAuthorize = AlimTalkController.class.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize)
                    .as("레거시 화면 61 은 login=Y 하나뿐이었고, 그것으로 충분했던 이유는 "
                            + "actUseYn=N 이라 아무 일도 할 수 없었기 때문이다 "
                            + "/ legacy screen 61 carried login=Y alone, adequate only because it was inert")
                    .isNotNull();
            assertThat(preAuthorize.value()).contains("OPERATOR");
        }

        @Test
        @DisplayName("모든 엔드포인트가 인가 아래 있다 / every endpoint is under an authorization rule")
        // req: FR-AZ-A01, NFR-SEC-AUTHZ-A01
        void everyEndpointIsCovered() {
            // 새 엔드포인트를 추가하면서 인가를 잊는 것이 이 검사가 막는 회귀다. 클래스 수준
            // 애노테이션이 모든 메서드를 덮으므로, 메서드마다 반복하지 않아도 된다.
            // The regression this prevents is adding an endpoint and forgetting authorization. The
            // class-level annotation covers every method, so per-method repetition is unnecessary.
            assertThat(endpoints()).as("엔드포인트가 하나도 없다 / no endpoints found").isNotEmpty();

            boolean classLevel = AlimTalkController.class.isAnnotationPresent(PreAuthorize.class);
            assertThat(endpoints()).allSatisfy(method ->
                    assertThat(classLevel || method.isAnnotationPresent(PreAuthorize.class))
                            .as("%s 이 인가 밖에 있다 / %s is outside any authorization rule",
                                    method.getName(), method.getName())
                            .isTrue());
        }

        @Test
        @DisplayName("경로가 /api/admin 아래에 있다 / the path sits under /api/admin")
        // req: FR-AZ-A01, NFR-SEC-AUTHZ-A01
        void pathSitsUnderApiAdmin() {
            // SecurityConfig 의 URL 규칙(/api/admin/** → hasRole("OPERATOR"))이 두 번째 방어선이다.
            // D-A37 이 켜지기 전까지는 사실 <b>유일한</b> 방어선이었다.
            // The URL rule (/api/admin/** → hasRole("OPERATOR")) is the second barrier — and until
            // D-A37 was fixed it was in fact the <b>only</b> one.
            RequestMapping mapping = AlimTalkController.class.getAnnotation(RequestMapping.class);

            assertThat(mapping).isNotNull();
            assertThat(mapping.value()[0]).startsWith("/api/admin/");
        }
    }

    @Nested
    @DisplayName("T-A24/FR-AZ-A05 — 자격증명은 요청으로 들어올 수 없다 / the credential cannot arrive in a request")
    class CredentialCannotBeSupplied {

        @Test
        @DisplayName("어떤 요청 타입에도 프로파일키 필드가 없다 / no request type exposes a profile-key field")
        // req: FR-AZ-A05, NFR-SEC-CRED-A01
        void noRequestTypeExposesAProfileKey() {
            // 레거시 화면 61 은 운영자에게 프로파일키를 직접 입력하게 했다(D-A24). 새 설계에서
            // 이것이 "잊을 수 있는 규칙" 이 아니라 타입의 성질이어야 한다는 것이 FR-AZ-A05 의
            // 요지다 — 필드가 없으면 클라이언트가 채울 수 없다.
            // Legacy screen 61 had the operator type the profile key in (D-A24). FR-AZ-A05's point is that
            // this must be a property of the type rather than a rule someone remembers: a field that does
            // not exist cannot be populated by a client.
            List<Class<?>> requestTypes = Arrays.stream(AlimTalkController.class.getDeclaredClasses())
                    .filter(Class::isRecord)
                    .filter(c -> c.getSimpleName().endsWith("Request"))
                    .toList();

            assertThat(requestTypes).as("검사할 요청 타입 / request types to check").isNotEmpty();
            assertThat(requestTypes).allSatisfy(type ->
                    assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                            .as("%s", type.getSimpleName())
                            .noneSatisfy(name -> assertThat(CREDENTIAL_NAMES)
                                    .contains(name.toLowerCase(Locale.ROOT))));
        }

        @Test
        @DisplayName("어떤 응답 타입도 프로파일키를 돌려주지 않는다 / no response type returns a profile key")
        // req: FR-AZ-A05, NFR-SEC-CRED-A01
        void noResponseTypeReturnsAProfileKey() {
            // 요청으로 들어올 수 없더라도 응답으로 나가면 같은 노출이다.
            // Being unable to arrive in a request is no help if it leaves in a response.
            List<Class<?>> responseTypes = Arrays.stream(AlimTalkController.class.getDeclaredClasses())
                    .filter(Class::isRecord)
                    .filter(c -> c.getSimpleName().endsWith("Response"))
                    .toList();

            assertThat(responseTypes).isNotEmpty();
            assertThat(responseTypes).allSatisfy(type ->
                    assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
                            .as("%s", type.getSimpleName())
                            .noneSatisfy(name -> assertThat(CREDENTIAL_NAMES)
                                    .contains(name.toLowerCase(Locale.ROOT))));
        }

        @Test
        @DisplayName("엔드포인트 파라미터로도 들어올 수 없다 / it cannot arrive as an endpoint parameter")
        // req: FR-AZ-A05
        void itCannotArriveAsAParameter() {
            assertThat(endpoints()).allSatisfy(method ->
                    assertThat(Arrays.stream(method.getParameters()).map(p -> p.getName().toLowerCase(Locale.ROOT)))
                            .as("%s", method.getName())
                            .noneSatisfy(name -> assertThat(CREDENTIAL_NAMES).contains(name)));
        }
    }

    @Nested
    @DisplayName("남은 공백 / the remaining gap")
    class RemainingGap {

        @Test
        @DisplayName("이 테스트가 덮지 못하는 것을 기록한다 / records what this test cannot cover")
        // req: NFR-SEC-AUTHZ-A01
        void recordsWhatIsNotCovered() {
            // 의도적으로 통과하는 문서화 테스트다. 반사 검사는 "선언이 있다"를 증명할 뿐이고,
            // "미인증 호출이 403 을 받는다"는 증명하지 못한다. A1-19 의 미완 부분이며,
            // Spring 테스트 컨텍스트가 필요하다. 이 사실을 로그에만 적어 두면 다음 사람이
            // 반사 검사를 통합 테스트로 오해할 수 있으므로 코드에도 남긴다.
            // A deliberately passing documentation test. Reflection proves a declaration exists; it does
            // not prove an anonymous call receives 403. That is A1-19's unfinished part and needs a Spring
            // test context. Recorded in code as well as in the log, so the next reader does not mistake a
            // reflection check for an integration test.
            assertThat(AlimTalkController.class.isAnnotationPresent(PreAuthorize.class)).isTrue();
        }
    }
}
