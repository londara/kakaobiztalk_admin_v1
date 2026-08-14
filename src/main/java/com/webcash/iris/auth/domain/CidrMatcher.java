package com.webcash.iris.auth.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * CIDR 표기 IP 대역 매칭. / Matches addresses against CIDR ranges.
 *
 * <p>레거시는 IP 허용 여부를 DB 조회({@code A_USER_IP_AUTHED_R001})로 판정했고 정확한
 * 매칭 규칙은 SQL 안에 있었다. 신규 구현은 설정 기반 CIDR 목록을 쓴다 — 접근 통제 규칙이
 * 배포 산출물에 명시되어 리뷰 가능해지고, 로그인 경로에서 DB 왕복이 사라진다.</p>
 * <p>The legacy decided allowlisting with a database lookup, its matching rule buried in
 * SQL. This implementation uses a configured CIDR list: the access rule becomes visible in
 * the deployment artifact and reviewable, and a database round trip leaves the login path.</p>
 *
 * <p><b>파싱 실패를 조용히 넘기지 않는다.</b> 잘못된 CIDR 항목을 무시하면 운영자가
 * 의도한 대역이 빠진 채로 시스템이 동작하고, 그 사실은 누군가 차단될 때까지 드러나지
 * 않는다. 기동 시점에 실패시킨다.</p>
 * <p><b>Parse failures are never swallowed.</b> Skipping a malformed entry would leave the
 * system running without a range the operator intended, invisible until someone is wrongly
 * blocked. It fails at startup instead.</p>
 *
 * // source: apm_0001_01_view.jsp — IDO A_USER_IP_AUTHED_R001 (결과가 버려졌음, 결함 L5)
 * // req: FR-LOGIN-024
 */
public final class CidrMatcher {

    private final List<Range> ranges = new ArrayList<>();

    /**
     * CIDR 목록으로 매처를 생성한다. / Creates a matcher from CIDR entries.
     *
     * @param cidrs CIDR 표기 목록 (예: {@code 10.0.0.0/8}) / CIDR entries such as {@code 10.0.0.0/8}
     * @throws IllegalArgumentException 형식이 잘못된 항목이 있을 때 / when an entry is malformed
     */
    // req: FR-LOGIN-024
    public CidrMatcher(List<String> cidrs) {
        if (cidrs == null) {
            return;
        }
        for (String entry : cidrs) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            ranges.add(parse(entry.trim()));
        }
    }

    /**
     * 설정된 대역이 없는지 반환한다. / Whether no range is configured.
     *
     * <p>빈 목록은 "허용목록 미사용"을 의미한다. 이 구분이 중요하다 — 빈 목록을
     * "아무도 허용하지 않음"으로 해석하면 설정 누락이 전면 차단으로 이어진다.</p>
     * <p>An empty list means "allowlist not in use". The distinction matters: reading it as
     * "permit nobody" would turn a missing configuration into a total outage.</p>
     *
     * @return 대역이 없으면 true / true when no range is configured
     */
    // req: FR-LOGIN-024
    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    /**
     * 주소가 허용 대역에 속하는지 반환한다. / Whether the address falls in an allowed range.
     *
     * @param ip 점 표기 IPv4 주소 / a dotted-quad IPv4 address
     * @return 포함 여부 / true when contained
     */
    // req: FR-LOGIN-024
    public boolean matches(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        long address;
        try {
            address = toLong(ip.trim());
        } catch (IllegalArgumentException e) {
            // 파싱할 수 없는 주소는 허용하지 않는다. IPv6 주소나 이상값이 통과하는 것보다
            // 차단되는 편이 안전하다 (§한계 참조).
            // An unparseable address is not allowed: better blocked than passed through.
            return false;
        }
        for (Range range : ranges) {
            if ((address & range.mask) == range.network) {
                return true;
            }
        }
        return false;
    }

    private static Range parse(String cidr) {
        int slash = cidr.indexOf('/');
        String addressPart = slash < 0 ? cidr : cidr.substring(0, slash);
        int prefix = slash < 0 ? 32 : parsePrefix(cidr.substring(slash + 1), cidr);

        long address = toLong(addressPart);
        // prefix 0 에서 -1L << 32 는 정의되지 않은 결과를 주므로 분기한다.
        // Branch for prefix 0: -1L << 32 does not give the intended mask.
        long mask = prefix == 0 ? 0L : ((0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL);
        return new Range(address & mask, mask);
    }

    private static int parsePrefix(String value, String cidr) {
        int prefix;
        try {
            prefix = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR prefix in: " + cidr, e);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be 0-32 in: " + cidr);
        }
        return prefix;
    }

    private static long toLong(String ipv4) {
        InetAddress parsed;
        try {
            parsed = InetAddress.getByName(ipv4);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Not a valid address: " + ipv4, e);
        }
        byte[] bytes = parsed.getAddress();
        if (bytes.length != 4) {
            // IPv6 는 지원하지 않는다. 지원하는 척하고 잘못 매칭하는 것보다 명시적으로
            // 거절하는 편이 낫다 — 허용목록은 접근 통제이므로 애매한 판정이 위험하다.
            // IPv6 is unsupported. Refusing explicitly beats pretending to support it and
            // matching incorrectly: an allowlist is access control, so ambiguity is a risk.
            throw new IllegalArgumentException("Only IPv4 is supported: " + ipv4);
        }
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    private record Range(long network, long mask) {
    }
}
