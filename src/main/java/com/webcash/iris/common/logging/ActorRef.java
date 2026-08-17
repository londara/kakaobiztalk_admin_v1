package com.webcash.iris.common.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 로그에 쓰는 가명 행위자 참조. / A pseudonymous actor reference for logs.
 *
 * <p><b>애플리케이션 로그에는 이메일을 쓰지 않는다.</b> 이 규칙은 임의의 신중함이 아니라
 * 이 프로젝트의 명시적 요구사항이다 — 레거시는 중복 로그인이 발생할 때마다 이메일·세션
 * 식별자·IP 를 debug 레벨로 기록했고, NFR-SEC-LOG-L01 은 정확히 그 실패를 막기 위해
 * 존재한다.</p>
 * <p><b>Email never appears in application logs.</b> Not a general precaution but this project's
 * explicit requirement: the legacy logged email, session id and IP at debug level on every
 * duplicate login, and NFR-SEC-LOG-L01 exists to prevent exactly that.</p>
 *
 * <h2>그러면 조사할 때 누구인지 어떻게 아는가 / then how is an investigation to know who</h2>
 * <p>실제 신원은 <b>DB 감사 기록</b>({@code AuditService}) 이 보관한다. 감사 저장소는 접근
 * 통제와 보존 기간이 따로 있고, 신원을 담는 것이 그 저장소의 목적이다. 애플리케이션 로그는
 * 목적이 다르다 — 무슨 일이 일어났는지를 보는 것이지 누구인지를 보는 것이 아니다.</p>
 * <p>The real identity lives in the <b>database audit record</b>, a store with its own access
 * control and retention whose purpose is to hold it. Application logs serve a different purpose:
 * seeing what happened, not who did it.</p>
 *
 * <p>가명값은 <b>안정적</b>이므로 한 사람의 여러 요청을 로그만으로 이어 볼 수 있고, 상관
 * 식별자와 함께 쓰면 감사 기록으로 건너갈 수 있다. 되돌릴 수 없는 것은 아니다 — 이메일
 * 주소 공간은 사전 공격이 가능할 만큼 좁으므로 <b>익명화가 아니라 노출 축소</b>로 이해해야
 * 한다. 로그를 읽을 수 있는 사람이 특정인을 지목하려면 별도의 권한이 필요하도록 만드는 것이
 * 목적이다.</p>
 * <p>The pseudonym is <b>stable</b>, so one person's requests can be followed through the log and
 * joined to the audit record via the correlation id. It is not irreversible — the space of email
 * addresses is small enough to attack by dictionary — so treat it as <b>exposure reduction, not
 * anonymisation</b>. The point is that reading the log alone does not name anyone.</p>
 *
 * // source: application.yml — "The legacy logged email, session id and IP at debug level"
 * // req: NFR-SEC-LOG-L01, NFR-SEC-LOG-D01, NFR-SEC-PII-D01
 */
public final class ActorRef {

    /** 인증되지 않은 요청의 행위자 표기. / The actor marker for an unauthenticated request. */
    public static final String ANONYMOUS = "anon";

    private ActorRef() {
    }

    /**
     * 이메일을 로그용 가명 참조로 바꾼다. / Converts an email into a pseudonymous log reference.
     *
     * @param email 행위자 이메일, null 가능 / the actor's email; may be null
     * @return 가명 참조 / the pseudonymous reference
     */
    // req: NFR-SEC-LOG-L01
    public static String of(String email) {
        if (email == null || email.isBlank()) {
            return ANONYMOUS;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            // 8 hex 문자면 로그를 읽는 사람이 눈으로 구분하기에 충분하고, 사고 조사에서
            // 실제 신원이 필요하면 감사 기록으로 간다.
            // Eight hex characters is enough to tell actors apart by eye; an investigation that
            // needs the real identity goes to the audit record.
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 제공한다. 그럼에도 여기서 이메일을 반환하지는 않는다 —
            // 예외 상황이 PII 노출 경로가 되어서는 안 된다.
            // SHA-256 is required of every JVM. Even so, this does not fall back to returning the
            // email: an exceptional path must not become a PII disclosure path.
            return ANONYMOUS;
        }
    }
}
