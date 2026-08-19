package com.webcash.iris.biztalk.config;

import com.webcash.iris.biztalk.domain.BizTalkApiRegistry;
import com.webcash.iris.biztalk.domain.TalkChannel;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 톡전송 내역 슬라이스의 설정 결선. / Configuration wiring for the 톡전송 내역 slice.
 *
 * <h2>왜 허용 목록이 설정인가 / why the allow-list is configuration</h2>
 * <p>PM 결정 SCOPE-T01 이 화면을 BizTalk API 로 좁히면서, 레거시에 없던 것을 만들어야 했다 —
 * <b>어느 코드가 BizTalk 인지에 대한 정의</b>. 그런데 권위 있는 목록은 코드가 아니라
 * <b>데이터</b>다: 소스 전체를 훑어도 리터럴은 다섯 개뿐인데 {@code ADV_COM_GET_STATUS} 는
 * 운영에 존재하고 어떤 소스 파일에도 없다. 코드에 열거하면 증명 가능하게 불완전하고, API 가
 * 하나 등록될 때마다 릴리스가 필요하다(ADR-TLK-024).</p>
 * <p>PM ruling SCOPE-T01 narrowed the screen to BizTalk APIs, which required inventing something the
 * legacy never had — <b>a definition of which codes are BizTalk</b>. But the authoritative list is
 * <b>data</b>, not code: a full source scan yields five literals while {@code ADV_COM_GET_STATUS}
 * exists in production and in no source file. Enumerating in code is provably incomplete and would
 * need a release per registered API (ADR-TLK-024).</p>
 *
 * <h2>설정 키 / configuration keys</h2>
 * <pre>
 * biztalk:
 *   talk-history:
 *     api-services:
 *       - code: ADV_KKO_AT_SEND
 *         channel: AT          # AT | FT | (생략 = 상세 없음 / omitted = no detail)
 *         label: 알림톡 발송
 * </pre>
 * <p>설정이 없으면 {@link BizTalkApiRegistry#DEFAULT_ENTRIES} 다섯 개가 쓰인다. 설정 파일은
 * SEC-001 대상이므로 이 슬라이스는 <b>키를 문서화하고 기본값을 코드에 둔다</b> — 운영 반영은
 * 설정 파일을 다룰 수 있는 담당자의 작업이다.</p>
 * <p>Absent configuration falls back to {@link BizTalkApiRegistry#DEFAULT_ENTRIES}. Configuration files
 * are within SEC-001's scope, so this slice <b>documents the keys and keeps the defaults in code</b>;
 * applying them in an environment is the work of someone who may edit those files.</p>
 *
 * // req: FR-TLK-002, ADR-TLK-024
 */
@Configuration
@EnableConfigurationProperties(TalkHistoryConfig.TalkHistoryProperties.class)
public class TalkHistoryConfig {

    private static final Logger log = LoggerFactory.getLogger(TalkHistoryConfig.class);

    /**
     * BizTalk API 레지스트리 빈을 만든다. / Builds the BizTalk API registry bean.
     *
     * <p>설정 항목이 없으면 기본값을 쓰고 그 사실을 <b>INFO 로 남긴다</b>. 조용히 기본값을
     * 쓰면 "설정을 했다고 생각했는데 안 된 상태"와 "일부러 기본값을 쓰는 상태"를 구분할 수
     * 없다.</p>
     * <p>With no configured entries the defaults are used and that is <b>logged at INFO</b>: falling
     * back silently makes "I thought I configured it" indistinguishable from "the defaults are
     * intended".</p>
     *
     * @param properties 설정 / the properties
     * @return 레지스트리 / the registry
     */
    // req: FR-TLK-002
    @Bean
    public BizTalkApiRegistry bizTalkApiRegistry(TalkHistoryProperties properties) {
        List<BizTalkApiRegistry.Entry> entries = new ArrayList<>();
        for (ApiServiceProperty property : properties.getApiServices()) {
            entries.add(property.toEntry());
        }
        if (entries.isEmpty()) {
            log.info("biztalk.talk-history.api-services 설정이 없어 기본 {}건을 사용합니다 "
                            + "(ADR-TLK-024). / No biztalk.talk-history.api-services configured; "
                            + "using the {} defaults (ADR-TLK-024).",
                    BizTalkApiRegistry.DEFAULT_ENTRIES.size(),
                    BizTalkApiRegistry.DEFAULT_ENTRIES.size());
        }
        return new BizTalkApiRegistry(entries);
    }

    /**
     * 톡전송 내역 설정. / 톡전송 내역 properties.
     *
     * // req: FR-TLK-002
     */
    @ConfigurationProperties(prefix = "biztalk.talk-history")
    public static class TalkHistoryProperties {

        private List<ApiServiceProperty> apiServices = new ArrayList<>();

        /**
         * BizTalk 로 분류되는 API 서비스 목록을 반환한다.
         * Returns the API services classified as BizTalk.
         *
         * @return 목록 / the list
         */
        public List<ApiServiceProperty> getApiServices() {
            return apiServices;
        }

        /**
         * BizTalk 로 분류되는 API 서비스 목록을 설정한다.
         * Sets the API services classified as BizTalk.
         *
         * @param apiServices 목록 / the list
         */
        public void setApiServices(List<ApiServiceProperty> apiServices) {
            this.apiServices = (apiServices == null) ? new ArrayList<>() : apiServices;
        }
    }

    /**
     * API 서비스 설정 항목 하나. / One configured API service.
     *
     * // req: FR-TLK-002, FR-TLK-013
     */
    public static class ApiServiceProperty {

        private String code;
        private String channel;
        private String label;

        /**
         * {@code API_SVC_CD} 값을 반환한다. / Returns the {@code API_SVC_CD} value.
         *
         * @return 코드 / the code
         */
        public String getCode() {
            return code;
        }

        /**
         * {@code API_SVC_CD} 값을 설정한다. / Sets the {@code API_SVC_CD} value.
         *
         * @param code 코드 / the code
         */
        public void setCode(String code) {
            this.code = code;
        }

        /**
         * 채널 코드를 반환한다. / Returns the channel code.
         *
         * @return {@code AT}, {@code FT} 또는 null / {@code AT}, {@code FT} or null
         */
        public String getChannel() {
            return channel;
        }

        /**
         * 채널 코드를 설정한다. / Sets the channel code.
         *
         * @param channel 채널 코드 / the channel code
         */
        public void setChannel(String channel) {
            this.channel = channel;
        }

        /**
         * 표시명을 반환한다. / Returns the display label.
         *
         * @return 표시명 / the label
         */
        public String getLabel() {
            return label;
        }

        /**
         * 표시명을 설정한다. / Sets the display label.
         *
         * @param label 표시명 / the label
         */
        public void setLabel(String label) {
            this.label = label;
        }

        /**
         * 레지스트리 항목으로 변환한다. / Converts into a registry entry.
         *
         * <p>채널을 생략하면 <b>상세 조회를 지원하지 않는 서비스</b>다 — 범위에는 있으나
         * 메시지가 없는 호출(예: 상태 조회)이 그렇다. 알 수 없는 채널 값은 <b>거부</b>한다:
         * 오타를 조용히 "상세 없음"으로 처리하면 상세 링크가 사라진 이유를 아무도 알 수
         * 없다.</p>
         * <p>An omitted channel means <b>a service with no message detail</b> — a status-polling call is
         * in scope but has no messages. An unknown channel value is <b>refused</b>: silently treating a
         * typo as "no detail" would make a vanished detail link inexplicable.</p>
         *
         * @return 항목 / the entry
         * @throws IllegalArgumentException 코드가 없거나 채널 값이 알 수 없을 때 / when the code is
         *         missing or the channel value is unknown
         */
        // req: FR-TLK-002, FR-TLK-013
        public BizTalkApiRegistry.Entry toEntry() {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException(
                        "biztalk.talk-history.api-services[].code 는 필수입니다. / "
                                + "biztalk.talk-history.api-services[].code is required.");
            }
            TalkChannel resolved = null;
            if (channel != null && !channel.isBlank()) {
                String normalised = channel.trim().toUpperCase();
                resolved = switch (normalised) {
                    case "AT" -> TalkChannel.ALIMTALK;
                    case "FT" -> TalkChannel.FRIENDTALK;
                    default -> throw new IllegalArgumentException(
                            "알 수 없는 채널 값입니다: '" + channel + "' (AT 또는 FT). / "
                                    + "Unknown channel value: '" + channel + "' (AT or FT).");
                };
            }
            String effectiveLabel = (label == null || label.isBlank()) ? code : label;
            return new BizTalkApiRegistry.Entry(code.trim(), resolved, effectiveLabel);
        }
    }
}
