package com.webcash.iris.biztalk.alimtalk.infra.vendor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.webcash.iris.biztalk.alimtalk.domain.ProfileKey;
import com.webcash.iris.biztalk.alimtalk.domain.RecipientNumber;
import java.io.IOException;

/**
 * 벤더에 보낼 payload 만 직렬화한다 — 가려지지 않은 값으로.
 * Serialises payloads for the vendor, and only those, with values unmasked.
 *
 * <h2>이 클래스가 존재해야 하는 이유 / why this class must exist</h2>
 * <p>{@link RecipientNumber} 와 {@link ProfileKey} 는 {@code @JsonValue} 로 <b>가려진</b> 값을
 * 돌려준다. 그것이 기본값인 것은 의도다 — 실수로 노출되는 경로를 없애려면 노출이 기본값이
 * 아니어야 한다. 그런데 벤더는 <b>실제 값</b>을 받아야 하므로 예외가 정확히 한 곳 필요하다.</p>
 * <p>{@link RecipientNumber} and {@link ProfileKey} serialise <b>masked</b> via {@code @JsonValue}, and
 * that default is deliberate: removing accidental-exposure paths requires that exposure not be the
 * default. But the vendor must receive the <b>real</b> values, so exactly one exception is needed.</p>
 *
 * <p>그 예외를 <b>여기</b>에 두는 것이 요점이다. CI 규칙
 * {@code Confine the raw PII accessor to the vendor boundary} 는 {@code exposeForVendorCall()} 이
 * {@code infra/vendor/} 밖에 나타나면 빌드를 깨뜨린다. 즉 이 클래스가 없으면 다른 곳에서
 * 마스킹을 우회할 방법도 없고, 이 클래스가 있어도 우회는 <b>이 디렉터리 안에서만</b> 가능하다.
 * 통제가 문서가 아니라 빌드에 있다.</p>
 * <p>Placing that exception <b>here</b> is the point. The CI rule
 * {@code Confine the raw PII accessor to the vendor boundary} breaks the build if
 * {@code exposeForVendorCall()} appears outside {@code infra/vendor/}. So masking cannot be bypassed
 * elsewhere, and even with this class the bypass is possible <b>only inside this directory</b>. The
 * control lives in the build, not in a document.</p>
 *
 * <p>⚠ 이 매퍼의 출력은 <b>화면·응답·로그에 보내면 안 된다.</b> 수신번호와 발신프로필키가 평문으로
 * 들어 있다. 아웃박스의 {@code PAYLOAD} 컬럼과 벤더 HTTP 본문, 두 곳에만 쓴다. 레거시는
 * {@code data_log=true} 로 이 내용을 매 발송마다 로그에 남겼다(D-A30).</p>
 * <p>⚠ Output from this mapper <b>must not reach a screen, a response, or a log</b>: it carries the
 * recipient number and the sender profile key in clear. Its only destinations are the outbox
 * {@code PAYLOAD} column and the vendor HTTP body. The legacy wrote exactly this to the log on every
 * send via {@code data_log=true} (D-A30).</p>
 *
 * // source: jex.iris_admin.xml:393 — data_log=true on COOCON_ALERT (D-A30)
 * // req: FR-ATS-004, FR-AZ-A05, NFR-SEC-PII-A01, NFR-SEC-CRED-A01
 */
public final class VendorPayloadMapper {

    private final ObjectMapper mapper;

    /**
     * 매퍼를 만든다. / Creates the mapper.
     *
     * // req: FR-ATS-004
     */
    public VendorPayloadMapper() {
        SimpleModule unmasked = new SimpleModule("alimtalk-vendor-unmasked");
        unmasked.addSerializer(RecipientNumber.class, new RawRecipientSerializer());
        unmasked.addSerializer(ProfileKey.class, new RawProfileKeySerializer());

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(unmasked);
    }

    /**
     * payload 를 벤더용 JSON 으로 만든다. / Renders a payload as JSON for the vendor.
     *
     * <p>{@code writerWithDefaultPrettyPrinter} 를 쓰지 않는다 — 미리보기와 달리 사람이 읽을 것이
     * 아니고, 들여쓰기는 그저 바이트를 늘린다.</p>
     * <p>No pretty printer: unlike the preview this is not read by a person, and indentation only adds
     * bytes.</p>
     *
     * @param payload 계약 타입 / a contract type
     * @return JSON
     * @throws com.fasterxml.jackson.core.JsonProcessingException 직렬화 실패 / on failure
     *
     * // req: FR-ATS-004, CONST-DATA-A01
     */
    public String render(Object payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return mapper.writeValueAsString(payload);
    }

    /**
     * 수신번호를 가리지 않고 쓴다. / Writes a recipient number unmasked.
     *
     * // req: FR-ATS-004, NFR-SEC-PII-A01
     */
    private static final class RawRecipientSerializer extends JsonSerializer<RecipientNumber> {
        /**
         * 값을 쓴다. / Writes the value.
         *
         * @param value       수신번호 / the recipient number
         * @param gen         생성기 / the generator
         * @param serializers 제공자 / the provider
         * @throws IOException 쓰기 실패 / on write failure
         *
         * // req: FR-ATS-004
         */
        @Override
        public void serialize(RecipientNumber value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.exposeForVendorCall());
        }
    }

    /**
     * 발신프로필키를 가리지 않고 쓴다. / Writes a sender profile key unmasked.
     *
     * // req: FR-ATS-004, NFR-SEC-CRED-A01
     */
    private static final class RawProfileKeySerializer extends JsonSerializer<ProfileKey> {
        /**
         * 값을 쓴다. / Writes the value.
         *
         * @param value       발신프로필키 / the profile key
         * @param gen         생성기 / the generator
         * @param serializers 제공자 / the provider
         * @throws IOException 쓰기 실패 / on write failure
         *
         * // req: FR-ATS-004
         */
        @Override
        public void serialize(ProfileKey value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.exposeForVendorCall());
        }
    }
}
