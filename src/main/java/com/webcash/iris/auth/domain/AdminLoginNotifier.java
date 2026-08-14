package com.webcash.iris.auth.domain;

import com.webcash.iris.common.audit.AuditEvent;
import com.webcash.iris.common.audit.AuditService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 알림. / Administrator login notification.
 *
 * <h2>레거시 결함 L1 대응 / Fixes legacy defect L1</h2>
 * <p>레거시 {@code apc_login_proc_act.jsp} 는 운영자 로그인 시 다른 관리자에게 카카오
 * 알림톡을 보냈는데, 그 과정에서 다음 값들이 <b>소스에 하드코딩</b>되어 있었다:</p>
 * <ul>
 *   <li>카카오 {@code sender_key} — 실제 자격증명</li>
 *   <li>{@code sender_number}, {@code template_code}</li>
 *   <li>수신자 <b>개인 휴대폰번호 3건</b></li>
 * </ul>
 * <p>이는 자격증명 노출이면서 동시에 개인정보 노출이다. 운영 중인 시스템의 소스에 실제
 * 개인 연락처가 들어 있었다는 뜻이며, 담당자가 바뀌면 소스를 수정해야 했다.</p>
 * <p>The legacy notified other administrators on operator login with the Kakao
 * {@code sender_key}, sender number, template code and <b>three personal mobile numbers</b>
 * hardcoded in source — a credential exposure and a personal-data exposure at once, and one
 * requiring a code change whenever staff changed.</p>
 *
 * <h2>이 구현의 범위 / Scope of this implementation</h2>
 * <p>수신자와 채널 설정은 <b>전부 설정에서</b> 온다(CONST-SEC-L02). 다만 카카오 알림톡
 * 발송 자체는 <b>로그인 모듈의 범위가 아니다</b> — 프로바이더 연동은 문자내역·발송
 * 모듈에 속한다. 따라서 기본 구현은 {@link NotificationChannel} 포트를 통해 위임하고,
 * 연동이 없는 동안에는 감사 기록만 남긴다. 존재하지 않는 프로바이더 연동을 발명하는
 * 것보다 경계를 명확히 두는 편이 낫다.</p>
 * <p>Recipients and channel settings come entirely from configuration. Kakao dispatch
 * itself, however, is <b>outside the login module</b>: provider integration belongs to the
 * messaging module. The default therefore delegates through a {@link NotificationChannel}
 * port and, absent an integration, records an audit event only — a clear boundary beats
 * inventing a provider integration that does not exist here.</p>
 *
 * // source: apc_login_proc_act.jsp — hardcoded sender_key / receiver_number
 * // req: FR-LOGIN-021, CONST-SEC-L02, NFR-SEC-SECRET-L01
 */
@Component
public class AdminLoginNotifier {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginNotifier.class);

    private final boolean enabled;
    private final List<String> recipients;
    private final String siteLabel;
    private final NotificationChannel channel;
    private final AuditService audit;

    /**
     * 알림기를 생성한다. / Creates the notifier.
     *
     * @param enabled    알림 사용 여부 / whether notification is enabled
     * @param recipients 수신자 식별자 목록 (설정에서 주입) / recipient identifiers, injected from configuration
     * @param siteLabel  사이트 식별 문자열 / the site label
     * @param channel    발송 채널 / the dispatch channel
     * @param audit      감사 서비스 / the audit service
     */
    // req: FR-LOGIN-021, CONST-SEC-L02
    public AdminLoginNotifier(
            @Value("${iris.auth.admin-notification.enabled:false}") boolean enabled,
            @Value("${iris.auth.admin-notification.recipients:}") List<String> recipients,
            @Value("${iris.auth.admin-notification.site-label:IRIS_ADMIN}") String siteLabel,
            NotificationChannel channel,
            AuditService audit) {
        this.enabled = enabled;
        this.recipients = recipients == null ? List.of() : recipients;
        this.siteLabel = siteLabel;
        this.channel = channel;
        this.audit = audit;
    }

    /**
     * 운영자 로그인을 다른 관리자에게 알린다. / Notifies other administrators of an operator login.
     *
     * <p>이름을 마스킹하여 전달한다. 레거시도 {@code RegexNameMasking.maskName} 을 사용
     * 했으므로 이 부분은 동작이 보존된다.</p>
     * <p>The name is masked in transit; the legacy used {@code RegexNameMasking.maskName},
     * so this behaviour is preserved.</p>
     *
     * <p>알림 실패가 로그인을 실패시키지 <b>않는다.</b> 레거시는 알림 예외를 다시 던져
     * 로그인 전체를 실패시켰다 — 부가 기능의 장애가 인증을 막는 것은 가용성 측면에서
     * 잘못된 설계다.</p>
     * <p>A notification failure does <b>not</b> fail the login. The legacy rethrew the
     * exception and failed the whole login: letting an ancillary feature block authentication
     * is the wrong availability trade.</p>
     *
     * @param operatorEmail 로그인한 운영자 / the operator who logged in
     * @param maskedName    마스킹된 이름 / the masked display name
     * @param sourceIp      신뢰 가능한 출처 IP / the trusted source address
     */
    // source: apc_login_proc_act.jsp — adminFlag branch, ADV_KKO_AT_SEND
    // req: FR-LOGIN-021
    public void notifyOperatorLogin(String operatorEmail, String maskedName, String sourceIp) {
        if (!enabled || recipients.isEmpty()) {
            return;
        }
        String message = "▶ [" + maskedName + "] 관리자님이 [" + siteLabel + "] 사이트에 로그인했습니다.";
        try {
            channel.send(recipients, message);
            audit.recordAuth(operatorEmail, AuditEvent.ACTION_ADMIN_NOTIFICATION,
                    AuditEvent.Outcome.OK, "recipients=" + recipients.size(), sourceIp, null);
        } catch (RuntimeException e) {
            // 삼키지만 감사에는 남긴다. 조용히 사라지면 알림이 오지 않는 사실을 아무도
            // 모른다 — 통제가 있다고 믿는 상태가 없는 것보다 나쁘다.
            // Swallowed but audited: silent failure would leave nobody aware the notification
            // stopped arriving, and believing in an absent control is worse than having none.
            log.warn("Admin login notification failed; login proceeds", e);
            audit.recordAuth(operatorEmail, AuditEvent.ACTION_ADMIN_NOTIFICATION,
                    AuditEvent.Outcome.ERROR, "dispatch-failed", sourceIp, null);
        }
    }

    /**
     * 알림 발송 채널 포트. / The notification dispatch port.
     *
     * <p>카카오 알림톡 연동은 문자내역·발송 모듈의 책임이다. 로그인 모듈은 포트만
     * 정의하고, 그 모듈이 구현체를 제공한다.</p>
     * <p>Kakao dispatch belongs to the messaging module; the login module defines only the
     * port and that module supplies the implementation.</p>
     */
    public interface NotificationChannel {
        /**
         * 메시지를 발송한다. / Dispatches a message.
         *
         * @param recipients 수신자 식별자 / recipient identifiers
         * @param message    메시지 본문 / the message body
         */
        void send(List<String> recipients, String message);
    }

    /**
     * 연동이 없을 때의 기본 채널. / Default channel used when no integration exists.
     *
     * <p>발송하지 않고 감사 기록만 남긴다. 하드코딩된 프로바이더 자격증명을 만들어 넣지
     * 않기 위한 의도적 선택이다 — 그것이 정확히 결함 L1 이었다.</p>
     * <p>Records rather than dispatches. A deliberate choice not to invent hardcoded provider
     * credentials, which is exactly what defect L1 was.</p>
     */
    @Component
    public static class AuditOnlyChannel implements NotificationChannel {

        private static final Logger channelLog = LoggerFactory.getLogger(AuditOnlyChannel.class);

        /**
         * 발송 대신 기록한다. / Records instead of dispatching.
         *
         * @param recipients 수신자 식별자 / recipient identifiers
         * @param message    메시지 본문 / the message body
         */
        // req: FR-LOGIN-021
        @Override
        public void send(List<String> recipients, String message) {
            // 수신자 식별자는 로그에 남기지 않는다 — 휴대폰번호일 수 있다 (NFR-SEC-LOG-L01).
            // Recipient identifiers are not logged: they may be phone numbers.
            channelLog.info("Admin login notification prepared for {} recipient(s); "
                    + "no provider integration in the login module", recipients.size());
        }
    }
}
