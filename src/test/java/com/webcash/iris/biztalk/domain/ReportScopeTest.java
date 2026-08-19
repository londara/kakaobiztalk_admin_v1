package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.common.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ReportScope} 검증. / Verification for {@link ReportScope}.
 *
 * <p>이 클래스가 지키는 것은 CONFLICT-R01 의 PM 결정이다 — 전체 조회는 <b>운영자의 권한</b>
 * 이지 요청 파라미터가 아니며, 이용기관 주체는 무엇을 보내든 자기 기관으로 좁혀진다.</p>
 * <p>These tests hold the CONFLICT-R01 ruling: 전체 is <b>an operator's permission</b> rather than
 * a request parameter, and a tenant principal is narrowed to their own institution whatever they
 * send.</p>
 *
 * // req: FR-AZ-R03, FR-AZ-R04, CONFLICT-R01
 */
class ReportScopeTest {

    private static TenantContext.TenantPrincipal operator() {
        return new TenantContext.TenantPrincipal("op@example.com", null, true);
    }

    private static TenantContext.TenantPrincipal tenant(String institution) {
        return new TenantContext.TenantPrincipal("user@client.example", institution, false);
    }

    @Nested
    @DisplayName("운영자 / operator")
    class Operator {

        @Test
        @DisplayName("기관을 비우면 전체 조회가 된다")
        void blankMeansAllInstitutions() {
            ReportScope scope = ReportScope.resolve(operator(), null);

            assertThat(scope.allInstitutions()).isTrue();
            assertThat(scope.institutionCode()).isNull();
            assertThat(scope.describe()).isEqualTo("ALL");
        }

        @Test
        @DisplayName("공백 문자열도 전체 조회로 본다")
        void whitespaceIsTreatedAsBlank() {
            assertThat(ReportScope.resolve(operator(), "   ").allInstitutions()).isTrue();
        }

        @Test
        @DisplayName("특정 기관을 지목하면 그 기관으로 좁혀진다")
        void namedInstitutionNarrowsTheScope() {
            ReportScope scope = ReportScope.resolve(operator(), "K0001");

            assertThat(scope.allInstitutions()).isFalse();
            assertThat(scope.institutionCode()).isEqualTo("K0001");
            assertThat(scope.overrideAttempted()).isFalse();
        }
    }

    @Nested
    @DisplayName("이용기관 주체 / tenant principal")
    class Tenant {

        /**
         * D-R2 회귀: 레거시는 요청 본문의 IS_CD 를 그대로 쿼리에 넣었다.
         * D-R2 regression: the legacy put the body's IS_CD straight into the query.
         */
        // req: FR-AZ-R03, T-R04
        @Test
        @DisplayName("다른 기관을 지목해도 자기 기관으로 좁혀진다")
        void foreignInstitutionIsIgnored() {
            ReportScope scope = ReportScope.resolve(tenant("K0001"), "K9999");

            assertThat(scope.institutionCode()).isEqualTo("K0001");
            assertThat(scope.allInstitutions()).isFalse();
            assertThat(scope.overrideAttempted()).isTrue();
        }

        /**
         * D-R2 회귀에서 가장 중요한 한 건. 레거시 SQL 의
         * {@code AND (:IS_CD = '' OR IS_CD = :IS_CD)} 때문에 <b>파라미터를 비우는 것</b>이
         * 곧 전 기관 조회였다. 인증조차 없는 서비스(D-R1)와 합쳐져 T-R10 이 되었다.
         * The single most important D-R2 case: the legacy's
         * {@code AND (:IS_CD = '' OR IS_CD = :IS_CD)} made <b>omitting the parameter</b> a
         * query for every institution — which, on an unauthenticated service (D-R1), is T-R10.
         */
        // req: FR-AZ-R03, T-R10
        @Test
        @DisplayName("기관을 비워 보내도 전체 조회가 되지 않는다")
        void blankDoesNotBecomeAllInstitutions() {
            ReportScope scope = ReportScope.resolve(tenant("K0001"), null);

            assertThat(scope.allInstitutions()).isFalse();
            assertThat(scope.institutionCode()).isEqualTo("K0001");
        }

        @Test
        @DisplayName("자기 기관을 지목한 것은 우회 시도가 아니다")
        void namingOwnInstitutionIsNotAnOverride() {
            assertThat(ReportScope.resolve(tenant("K0001"), "K0001").overrideAttempted()).isFalse();
        }

        /**
         * 소속을 알 수 없는 비운영자는 <b>거부</b>한다. null 을 돌려주면 매퍼가 기관 조건을
         * 만들지 않아 전 기관이 노출된다 — 격리 통제가 조용히 정반대로 뒤집힌다.
         * A non-operator with no institution is <b>refused</b>: returning null would suppress the
         * mapper's predicate and expose every institution, inverting the isolation control.
         */
        // req: FR-AZ-R03, NFR-SEC-TENANT-R01
        @Test
        @DisplayName("소속을 알 수 없으면 조회를 거부한다")
        void unknownInstitutionFailsClosed() {
            assertThatThrownBy(() -> ReportScope.resolve(tenant(null), "K0001"))
                    .isInstanceOf(TenantContext.TenantScopeUnavailableException.class);

            assertThatThrownBy(() -> ReportScope.resolve(tenant("  "), null))
                    .isInstanceOf(TenantContext.TenantScopeUnavailableException.class);
        }
    }
}
