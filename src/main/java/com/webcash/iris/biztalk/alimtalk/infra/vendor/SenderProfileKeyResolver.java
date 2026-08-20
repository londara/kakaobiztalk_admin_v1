package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import com.webcash.iris.biztalk.alimtalk.domain.ProfileKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 이용기관별 카카오 발신프로필키 해결. / Resolves the Kakao sender profile key per institution.
 *
 * <h2>대체하는 것 / what this replaces</h2>
 * <p>{@code biztalk_admin_50_s001_act.jsp} 는 프로파일키를 소스에 <b>하드코딩</b>했다 — 두 곳에,
 * "우선 임시로 넣어둔다" 는 주석과 함께 — 그리고 매 발송마다 요청 전체를 로그에 직렬화했다
 * (D-A24, D-A30). 게다가 화면 61 은 운영자에게 그 키를 <b>직접 입력</b>하게 했으므로, 발송
 * 권한 그 자체가 사람들 사이에서 복사·붙여넣기로 돌아다녔다.</p>
 * <p>{@code biztalk_admin_50_s001_act.jsp} <b>hardcoded</b> the profile key in two places, commented
 * "putting it in temporarily", and serialised the whole request into the log on every send (D-A24,
 * D-A30). Screen 61 additionally had operators <b>type it in</b>, so the authority to send circulated
 * among people as a copy-paste string.</p>
 *
 * <h2>세 가지 성질 / three properties</h2>
 * <ol>
 *   <li><b>소스에 없다</b> — 값은 환경에서 주입된다({@code IRIS_ATK_PROFILE_KEY_*}). 체크인된
 *       기본값을 두지 않는 이유는 {@code application.yml} 머리말이 스스로 적어 둔 그대로다:
 *       체크인된 기본 비밀번호가 레거시를 {@code ota_config.xml} 로 데려갔다.
 *       <br><b>Not in source</b>: values are injected from the environment. No checked-in default, for the
 *       reason {@code application.yml}'s own header states — a checked-in default is how the legacy
 *       ended up with credentials in a config file.</li>
 *   <li><b>조용히 대체하지 않는다</b> — 알 수 없는 이용기관은 예외다. "아무 키나 써서 일단
 *       보낸다" 는 동작은 다른 기관을 사칭해 발송하는 것과 같다(T-A2).
 *       <br><b>No silent fallback</b>: an unknown institution throws. "Send with whatever key we have"
 *       is impersonation of another institution (T-A2).</li>
 *   <li><b>유출된 키를 거부한다</b> — 아래 참조.
 *       <br><b>Refuses the leaked key</b> — see below.</li>
 * </ol>
 *
 * <h2>유출된 키를 해시로 거부하는 이유 / why the leaked key is refused by hash</h2>
 * <p>레거시 키는 저장소에 평문으로 커밋되어 있었고 로그에도 남았으므로 <b>유출된 것으로
 * 간주</b>한다(RISK-A03). 회전(rotation)은 벤더 측 운영 작업이며 우리가 코드로 닫을 수 없다.
 * 우리가 닫을 수 있는 것은 하나다 — <b>회전한 뒤 실수로 옛 키를 다시 설정하는 것</b>.</p>
 * <p>The legacy key was committed in cleartext and written to logs, so it is treated as <b>compromised</b>
 * (RISK-A03). Rotation is a vendor-side operational task that no code of ours can close. What code
 * <i>can</i> close is the one adjacent mistake: <b>reconfiguring the old key after rotating away from
 * it</b>.</p>
 * <p>비교는 <b>SHA-256 해시</b>로 한다. 평문을 상수로 두면 이 클래스가 D-A24 를 그대로 재현하게
 * 된다 — 자격증명 유출을 막는 코드가 자격증명을 담는 셈이다. 해시는 커밋해도 안전하다.</p>
 * <p>The comparison uses a <b>SHA-256 hash</b>. Embedding the plaintext would make this class reproduce
 * D-A24 — code that prevents credential leakage would itself carry the credential. A hash is safe to
 * commit.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — imoIn.put("sender_key", "…"); biztalk_admin_61_view.jsp — sender_key input
 * // req: FR-ATS-003, FR-AZ-A05, NFR-SEC-CRED-A01, ADR-ATK-024, RISK-A03
 */
public class SenderProfileKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(SenderProfileKeyResolver.class);

    /**
     * 유출된 레거시 프로파일키의 SHA-256. / SHA-256 of the leaked legacy profile key.
     *
     * <p>평문이 아니라 해시를 두는 것이 요점이다(위 참조). 회전 후 이 값이 다시 설정되면
     * 기동을 거부한다.</p>
     * <p>A hash rather than the plaintext, which is the point (above). If this value is reconfigured
     * after rotation, startup is refused.</p>
     *
     * // req: NFR-SEC-CRED-A01, RISK-A03
     */
    static final String COMPROMISED_KEY_SHA256 =
            "4a08351d28d48644628b115e4f5b34d80ea40b32f7424cf8e7cbd87a16316c34";

    private final Map<String, ProfileKey> byInstitution;
    private final ProfileKey shared;

    /**
     * 해결기를 생성한다. / Creates the resolver.
     *
     * <p>{@code shared} 는 A1-04(스파이크)가 "모든 이용기관이 하나의 키를 공유한다" 로 답할
     * 경우를 위한 것이다. 레거시 소스는 키 하나를 모든 발송에 썼는데, 그것이 <b>올바른 공유
     * 모델</b>인지 <b>한 기관의 키를 전부에 잘못 쓴 것</b>인지 소스로는 구분할 수 없다 — 두
     * 경우의 시정 조치가 다르다. 그래서 공유 키는 지원하되 기동 시 경고를 남긴다.</p>
     * <p>{@code shared} exists for the case where spike A1-04 answers "all institutions share one key".
     * The legacy used a single key for every send, and source cannot distinguish a <b>correct shared
     * model</b> from <b>one institution's key wrongly used for all</b> — the remediation differs. The
     * shared key is therefore supported but warned about at startup.</p>
     *
     * @param byInstitution 이용기관코드 → 키 / institution code to key, may be empty
     * @param shared        공유 키, 없으면 {@code null} / the shared key, or {@code null}
     * @throws IllegalStateException 유출된 키가 설정되면 / when the compromised key is configured
     *
     * // req: FR-ATS-003, NFR-SEC-CRED-A01, RISK-A03
     */
    public SenderProfileKeyResolver(Map<String, String> byInstitution, String shared) {
        Map<String, String> source = byInstitution == null ? Map.of() : byInstitution;
        source.forEach((institution, key) -> rejectIfCompromised(key, "institution " + institution));
        this.byInstitution = source.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        e -> e.getKey().toUpperCase(Locale.ROOT),
                        e -> ProfileKey.of(e.getValue())));

        if (shared != null && !shared.isBlank()) {
            rejectIfCompromised(shared, "the shared key");
            this.shared = ProfileKey.of(shared);
            log.warn("AlimTalk is configured with a SHARED sender profile key for all institutions. "
                    + "Source could not establish whether that is correct or whether one institution's key "
                    + "was being used for all (spike A1-04). Resolve it before production.");
        } else {
            this.shared = null;
        }

        if (this.byInstitution.isEmpty() && this.shared == null) {
            // 기동을 막지 않는 이유: Sprint A1 화면(검증·수신번호 확인)은 키가 없어도 동작하며,
            // 발송은 아직 배선되어 있지 않다. 키가 필요한 순간에 resolve() 가 거부한다.
            // Startup is not blocked because the Sprint A1 screens (validation, recipient preview) work
            // without a key and sending is not wired yet. resolve() refuses at the moment one is needed.
            log.warn("No AlimTalk sender profile key is configured. Validation screens work without one; "
                    + "any send will be refused until IRIS_ATK_PROFILE_KEY_<IS_CD> is provided.");
        } else {
            log.info("AlimTalk profile keys configured for {} institution(s){}.",
                    this.byInstitution.size(), this.shared != null ? " plus a shared key" : "");
        }
    }

    /**
     * 이용기관의 프로파일키를 돌려준다. / Returns an institution's profile key.
     *
     * <p>기관별 키가 우선이고, 없으면 공유 키, 그것도 없으면 <b>예외</b>다. 마지막 단계가
     * 중요하다 — 조용히 아무 키나 쓰면 다른 기관을 사칭해 고객에게 메시지를 보내게 된다(T-A2).</p>
     * <p>Per-institution first, then the shared key, then <b>an exception</b>. That last step is the
     * important one: silently using any available key would send a customer a message impersonating
     * another institution (T-A2).</p>
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @return 프로파일키 / the profile key
     * @throws ProfileKeyUnavailableException 설정된 키가 없으면 / when no key is configured
     *
     * // req: FR-ATS-003, FR-AZ-A05
     */
    public ProfileKey resolve(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new ProfileKeyUnavailableException("institution code must not be blank");
        }
        ProfileKey key = byInstitution.get(institutionCode.toUpperCase(Locale.ROOT));
        if (key != null) {
            return key;
        }
        if (shared != null) {
            return shared;
        }
        // 예외 메시지에 키를 담지 않는다 — 예외 메시지도 로그로 흘러간다(D-A30).
        // The message carries no key material: exception text reaches logs too (D-A30).
        throw new ProfileKeyUnavailableException(
                "no AlimTalk sender profile key configured for institution " + institutionCode);
    }

    /**
     * 이용기관에 키가 설정되어 있는지 확인한다. / Reports whether a key is configured for an institution.
     *
     * <p>발송 전 점검에 쓴다. 발송을 시도했다가 실패하는 것보다, 보낼 수 없다는 사실을 미리
     * 알리는 편이 낫다(NFR-USE-A04).</p>
     * <p>Used for a pre-send check: telling the operator a send is impossible beats attempting it and
     * failing (NFR-USE-A04).</p>
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @return 설정되어 있으면 {@code true} / {@code true} when configured
     *
     * // req: FR-ATS-003, NFR-USE-A04
     */
    public boolean isConfiguredFor(String institutionCode) {
        return institutionCode != null
                && !institutionCode.isBlank()
                && (shared != null || byInstitution.containsKey(institutionCode.toUpperCase(Locale.ROOT)));
    }

    /**
     * 유출된 키가 설정되었으면 거부한다. / Refuses a configured value that is the compromised key.
     *
     * @param candidate 설정된 값 / the configured value
     * @param where     어디에 설정되었는지 / where it was configured
     * @throws IllegalStateException 유출된 키이면 / when it is the compromised key
     *
     * // req: NFR-SEC-CRED-A01, RISK-A03
     */
    private static void rejectIfCompromised(String candidate, String where) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        if (sha256(candidate).equals(COMPROMISED_KEY_SHA256)) {
            throw new IllegalStateException(
                    "The AlimTalk sender profile key configured for " + where + " is the key that was "
                            + "committed in cleartext in biztalk_admin_50_s001_act.jsp and written to "
                            + "application logs on every send (D-A24, D-A30). It must be treated as "
                            + "compromised and rotated at the vendor (RISK-A03), not reconfigured.");
        }
    }

    /**
     * SHA-256 16진 문자열. / SHA-256 as a lower-case hex string.
     *
     * @param value 입력 / the input
     * @return 해시 / the hash
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    /**
     * 프로파일키가 없어 발송할 수 없을 때. / Raised when no profile key permits a send.
     *
     * // req: FR-ATS-003
     */
    public static class ProfileKeyUnavailableException extends RuntimeException {

        /**
         * 예외를 생성한다. / Creates the exception.
         *
         * @param message 키 값을 담지 않는 설명 / a description carrying no key material
         */
        public ProfileKeyUnavailableException(String message) {
            super(message);
        }
    }
}
