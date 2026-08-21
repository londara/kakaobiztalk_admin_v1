package com.webcash.iris.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link CidrMatcher} 단위 테스트. / Unit tests for {@link CidrMatcher}.
 *
 * <h2>이 시험이 존재하는 이유 / why this exists</h2>
 * <p>이 클래스는 <b>접근 통제</b>다. 매칭이 한 방향으로 틀리면 허용 대상이 차단되고(가용성),
 * 반대로 틀리면 <b>차단 대상이 통과한다</b>(인증 우회). 레거시는 같은 판정을 SQL 안에 두어
 * 리뷰가 불가능했고, 그 결과 조회 결과를 버리는 결함 L5 가 발견될 때까지 남아 있었다.
 * 커버리지 측정 결과 이 클래스는 <b>41행 전부 미검증</b>이었다 — 통제가 있으나 그 통제가
 * 옳다는 증거가 없는 상태였다.</p>
 * <p>This class is <b>access control</b>. An error one way blocks permitted users; an error the
 * other way <b>lets blocked ones through</b>. The legacy buried the same decision in SQL where it
 * could not be reviewed, which is how defect L5 survived. Coverage showed all 41 lines untested —
 * a control present with no evidence that it is correct.</p>
 *
 * <p><b>DNS 를 타는 입력은 쓰지 않는다.</b> {@code toLong} 은 {@link java.net.InetAddress
 * #getByName} 을 호출하므로 리터럴이 아닌 문자열은 이름 해석을 시도한다. 그런 입력을 시험에
 * 넣으면 결과가 실행 환경의 DNS 에 좌우된다 — 아래는 전부 리터럴 또는 즉시 거절되는 값이다.</p>
 * <p><b>No DNS-triggering input is used.</b> {@code toLong} calls {@code InetAddress.getByName},
 * so a non-literal string attempts name resolution and the outcome would depend on the runner's
 * DNS. Every input below is a literal or is rejected before resolution.</p>
 *
 * // source: apm_0001_01_view.jsp — IDO A_USER_IP_AUTHED_R001 (결함 L5)
 * // req: FR-LOGIN-024, NFR-SEC-AUTH-L03
 */
class CidrMatcherTest {

    @Nested
    @DisplayName("빈 허용목록 / empty allowlist")
    class Empty {

        @Test
        @DisplayName("목록이 null 이면 비어 있다 / a null list yields an empty matcher")
        // req: FR-LOGIN-024
        void nullListIsEmpty() {
            // 설정 키가 아예 없는 경우다. 예외가 아니라 "허용목록 미사용" 으로 읽혀야 한다.
            // The configuration key is absent entirely; that must read as "not in use", not an error.
            assertThat(new CidrMatcher(null).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("빈 목록은 비어 있다 / an empty list yields an empty matcher")
        // req: FR-LOGIN-024
        void emptyListIsEmpty() {
            assertThat(new CidrMatcher(List.of()).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("공백 항목은 건너뛴다 / blank entries are skipped")
        // req: FR-LOGIN-024
        void blankEntriesAreSkipped() {
            // YAML 목록에서 흔한 실수다. 공백 한 줄이 파싱 실패로 기동을 막으면 안 되지만,
            // 그것이 대역으로 등록되어도 안 된다.
            // A common YAML slip: a blank line must neither break startup nor become a range.
            CidrMatcher matcher = new CidrMatcher(Arrays.asList("  ", "", null));
            assertThat(matcher.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("비어 있어도 matches 는 false 다 / matches is false even when empty")
        // req: FR-LOGIN-024
        void emptyMatcherMatchesNothing() {
            // 중요: isEmpty()==true 는 "허용목록 미사용" 이라는 <b>호출자</b>의 판단 재료이고,
            // matches() 자체는 아무 대역도 없으므로 false 를 준다. 이 두 값을 혼동해
            // matches() 만 보고 차단하면 설정 누락이 전면 차단이 된다 — 그 분기는
            // IpAllowlistPolicy 가 담당한다.
            // isEmpty() is input for the *caller's* "allowlist not in use" decision, while
            // matches() reports false because no range exists. Conflating the two — blocking on
            // matches() alone — would turn a missing config into a total outage. That branch
            // belongs to IpAllowlistPolicy.
            assertThat(new CidrMatcher(List.of()).matches("10.0.0.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("대역 매칭 / range matching")
    class Matching {

        @Test
        @DisplayName("/8 대역 안과 밖을 구분한다 / distinguishes inside and outside a /8")
        // req: FR-LOGIN-024
        void matchesWithinSlashEight() {
            CidrMatcher matcher = new CidrMatcher(List.of("10.0.0.0/8"));

            assertThat(matcher.matches("10.0.0.1")).isTrue();
            assertThat(matcher.matches("10.255.255.254")).isTrue();
            assertThat(matcher.matches("11.0.0.1")).isFalse();
            assertThat(matcher.matches("9.255.255.255")).isFalse();
        }

        @Test
        @DisplayName("/32 는 정확히 한 주소만 허용한다 / a /32 permits exactly one address")
        // req: FR-LOGIN-024
        void slashThirtyTwoIsExact() {
            CidrMatcher matcher = new CidrMatcher(List.of("192.168.1.7/32"));

            assertThat(matcher.matches("192.168.1.7")).isTrue();
            assertThat(matcher.matches("192.168.1.6")).isFalse();
            assertThat(matcher.matches("192.168.1.8")).isFalse();
        }

        @Test
        @DisplayName("prefix 없는 항목은 /32 로 본다 / an entry without a prefix means /32")
        // req: FR-LOGIN-024
        void bareAddressIsSlashThirtyTwo() {
            CidrMatcher matcher = new CidrMatcher(List.of("203.0.113.5"));

            assertThat(matcher.matches("203.0.113.5")).isTrue();
            assertThat(matcher.matches("203.0.113.6")).isFalse();
        }

        @Test
        @DisplayName("/0 은 모든 주소를 허용한다 / a /0 permits every address")
        // req: FR-LOGIN-024
        void slashZeroMatchesEverything() {
            // 코드가 prefix 0 을 특별 분기하는 이유를 고정한다: `-1L << 32` 는 원하는 마스크를
            // 주지 않는다. 이 시험이 실패하면 /0 이 조용히 <b>아무것도</b> 허용하지 않게 된다.
            //
            // ⚠ 운영 관점에서 /0 은 허용목록을 무력화하는 설정이다. 여기서는 <b>동작</b>을
            //   고정할 뿐이며 그것이 바람직한 설정이라는 뜻은 아니다.
            //
            // Pins why prefix 0 is special-cased: `-1L << 32` does not yield the intended mask, and
            // without the branch a /0 would silently permit *nothing*. Note this fixes the
            // behaviour only — operationally a /0 defeats the allowlist and is not a recommendation.
            CidrMatcher matcher = new CidrMatcher(List.of("10.0.0.0/0"));

            assertThat(matcher.matches("8.8.8.8")).isTrue();
            assertThat(matcher.matches("192.168.0.1")).isTrue();
        }

        @Test
        @DisplayName("호스트 비트가 있어도 대역으로 정규화한다 / host bits are normalised away")
        // req: FR-LOGIN-024
        void hostBitsAreNormalised() {
            // 운영자는 "10.1.2.3/8" 처럼 대표 주소를 적곤 한다. 네트워크 주소로 정규화하지
            // 않으면 그 항목은 아무것도 매칭하지 못하고, 조용히 대역 하나가 빠진다.
            // Operators often write a representative address such as 10.1.2.3/8. Without
            // normalising to the network address the entry would match nothing and a range would
            // quietly go missing.
            CidrMatcher matcher = new CidrMatcher(List.of("10.1.2.3/8"));

            assertThat(matcher.matches("10.9.9.9")).isTrue();
            assertThat(matcher.matches("10.1.2.3")).isTrue();
        }

        @Test
        @DisplayName("여러 대역 중 하나라도 맞으면 허용한다 / any matching range permits")
        // req: FR-LOGIN-024
        void anyRangeMatches() {
            CidrMatcher matcher =
                    new CidrMatcher(List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.1.0/24"));

            assertThat(matcher.matches("10.1.1.1")).isTrue();
            assertThat(matcher.matches("172.16.5.5")).isTrue();
            assertThat(matcher.matches("192.168.1.200")).isTrue();
            assertThat(matcher.matches("192.168.2.1")).isFalse();
            assertThat(matcher.matches("172.32.0.1")).isFalse();
        }

        @Test
        @DisplayName("/24 경계를 정확히 판정한다 / the /24 boundary is exact")
        // req: FR-LOGIN-024
        void slashTwentyFourBoundary() {
            // off-by-one 은 접근 통제에서 곧바로 우회 또는 오차단이 된다.
            // An off-by-one here is directly either a bypass or a wrongful block.
            CidrMatcher matcher = new CidrMatcher(List.of("192.168.1.0/24"));

            assertThat(matcher.matches("192.168.1.0")).isTrue();
            assertThat(matcher.matches("192.168.1.255")).isTrue();
            assertThat(matcher.matches("192.168.0.255")).isFalse();
            assertThat(matcher.matches("192.168.2.0")).isFalse();
        }

        @Test
        @DisplayName("최상위 비트가 켜진 주소도 올바르게 다룬다 / addresses above 127 are handled")
        // req: FR-LOGIN-024
        void highBitAddressesAreUnsigned() {
            // 부호 확장 결함을 잡는다. `b & 0xFF` 를 빼면 200.x 같은 주소가 음수가 되어
            // 매칭이 무너진다 — 공인 IP 대역이 대부분 여기에 해당한다.
            // Catches sign-extension: without `b & 0xFF`, an address such as 200.x becomes
            // negative and matching collapses — which is most public address space.
            CidrMatcher matcher = new CidrMatcher(List.of("200.100.50.0/24"));

            assertThat(matcher.matches("200.100.50.1")).isTrue();
            assertThat(matcher.matches("200.100.51.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("거절되는 입력 / rejected input")
    class Rejected {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("빈 주소는 허용하지 않는다 / a blank address is not permitted")
        // req: FR-LOGIN-024
        void blankAddressIsNotPermitted(String ip) {
            assertThat(new CidrMatcher(List.of("10.0.0.0/8")).matches(ip)).isFalse();
        }

        @Test
        @DisplayName("null 주소는 허용하지 않는다 / a null address is not permitted")
        // req: FR-LOGIN-024
        void nullAddressIsNotPermitted() {
            // 헤더가 없을 때 호출자가 null 을 넘길 수 있다. 예외가 아니라 거절이어야 한다 —
            // 접근 통제에서 예외는 상위에서 잘못 처리되면 통과로 바뀔 수 있다.
            // A caller may pass null when the header is absent. It must be a refusal rather than an
            // exception: in access control, an exception mishandled upstream can become a pass.
            assertThat(new CidrMatcher(List.of("10.0.0.0/8")).matches(null)).isFalse();
        }

        @Test
        @DisplayName("IPv6 주소는 허용하지 않는다 / an IPv6 address is not permitted")
        // req: FR-LOGIN-024
        void ipv6AddressIsNotPermitted() {
            // 리터럴이므로 DNS 를 타지 않는다. 16바이트로 파싱되어 명시적으로 거절된다 —
            // IPv4 마스크로 IPv6 를 매칭하는 것보다 안전하다.
            // A literal, so no DNS. It parses to 16 bytes and is refused explicitly, which is safer
            // than matching IPv6 against an IPv4 mask.
            CidrMatcher matcher = new CidrMatcher(List.of("10.0.0.0/8"));

            assertThat(matcher.matches("::1")).isFalse();
            assertThat(matcher.matches("2001:db8::1")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc", "10.0.0.0/"})
        @DisplayName("잘못된 prefix 는 기동을 실패시킨다 / a malformed prefix fails startup")
        // req: FR-LOGIN-024
        void malformedPrefixFailsFast(String cidr) {
            // 조용히 건너뛰지 않는 것이 이 클래스의 설계 의도다. 무시하면 운영자가 의도한
            // 대역이 빠진 채 동작하고, 누군가 차단될 때까지 드러나지 않는다.
            // Not skipping silently is the design intent: ignoring the entry leaves the system
            // running without a range the operator meant to add, invisible until someone is blocked.
            assertThatThrownBy(() -> new CidrMatcher(List.of(cidr)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(cidr);
        }

        @Test
        @DisplayName("IPv6 CIDR 항목은 두 경로 모두 기동을 실패시킨다 / an IPv6 CIDR fails startup by either route")
        // req: FR-LOGIN-024
        void ipv6CidrEntryFailsFast() {
            // IPv6 CIDR 은 prefix 값에 따라 <b>서로 다른 지점</b>에서 거절된다. 두 경로를 모두
            // 고정하지 않으면 한쪽만 막히도록 리팩터링되어도 시험이 통과한다.
            //   · prefix > 32 (`/128` 등) → parsePrefix 가 먼저 거절
            //   · prefix ≤ 32 (`/24` 등) → 통과 후 toLong 이 16바이트를 보고 거절
            // 중요한 것은 <b>메시지가 아니라 거절 자체</b>이므로 타입으로 단정하고, 어느
            // 경로였는지는 메시지로 구분해 기록한다.
            //
            // An IPv6 CIDR is refused at *different points* depending on the prefix: above 32 the
            // prefix check rejects first, at or below 32 it passes and toLong rejects on seeing 16
            // bytes. Pinning only one route would let a refactor that closes just that route pass.
            // What matters is the refusal, not the wording, so the type is asserted and the route
            // is merely recorded.
            assertThatThrownBy(() -> new CidrMatcher(List.of("::1/128")))
                    .as("prefix above 32 is caught by the prefix check")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prefix must be 0-32");

            assertThatThrownBy(() -> new CidrMatcher(List.of("::1/24")))
                    .as("a prefix within 0-32 reaches the address parser, which rejects IPv6")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only IPv4");

            assertThatThrownBy(() -> new CidrMatcher(List.of("2001:db8::/32")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only IPv4");
        }
    }
}
