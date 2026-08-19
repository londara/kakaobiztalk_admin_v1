package com.webcash.iris.biztalk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.webcash.iris.auth.config.SecurityConfig;
import com.webcash.iris.biztalk.domain.BizTalkApiRegistry;
import com.webcash.iris.biztalk.domain.TalkExportService;
import com.webcash.iris.biztalk.domain.TalkHistoryCriteria;
import com.webcash.iris.biztalk.domain.TalkHistoryService;
import com.webcash.iris.biztalk.domain.TalkPeriodPolicy;
import com.webcash.iris.common.audit.AuditService;
import com.webcash.iris.common.tenant.PrincipalScope;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 내보내기 응답 헤더 검증 — 회고 액션 B2.
 * Verification of the export's emitted headers: retrospective action B2.
 *
 * <h2>이 시험이 왜 뒤늦게 왔는가 / why this test arrived late</h2>
 * <p>스프린트 T2 는 FR-TLKX-003(헤더 안전)과 FR-TLKX-004(콘텐츠 타입 하나)를 추적표에
 * {@code PARTIAL} 로 남겼다. 성질은 <b>구조적으로</b> 성립했다 — 파일명이 서버 상수와 검증된
 * 일자로만 조립되고, {@code ContentDisposition} 이 인코딩을 맡고, 타입은 한 번만 설정된다.
 * 그러나 <b>실제로 전선에 나가는 바이트를 단언하는 시험이 없었다</b>. 구조적 논증과 관측된
 * 사실은 다른 것이고, 이 슬라이스의 결함 서른네 건 중 상당수가 정확히 그 차이에서 나왔다.</p>
 * <p>Sprint T2 left FR-TLKX-003 (header safety) and FR-TLKX-004 (one content type) as {@code PARTIAL} in the
 * trace matrix. The properties held <b>structurally</b> — the filename is composed only from a server constant
 * and validated dates, {@code ContentDisposition} does the encoding, and the type is set once. But <b>no test
 * asserted the bytes that actually go on the wire</b>. A structural argument and an observed fact are different
 * things, and a good share of this slice's thirty-four defects came from exactly that gap.</p>
 *
 * <h2>레거시가 이 헤더로 한 것 / what the legacy did with these headers</h2>
 * <p>{@code startDt}/{@code endDt} 를 검증 없이 파일명에 이어 붙이고 그 파일명을
 * {@code Content-Disposition} 에 넣었다. 비-IE 분기는 바이트를 다시 해석했을 뿐이고
 * ({@code getBytes("UTF-8"), "ISO-8859-1"}) — 인코딩도, CR/LF 거부도 없었으므로 응답 분할이
 * 가능했다(D-T4). 콘텐츠 타입은 <b>네 번</b> 설정되었고 그중 어느 것도 실제로 만들어지는
 * {@code .xlsx} 의 타입이 아니었다(D-T34).</p>
 * <p>It concatenated unvalidated dates into the filename and wrote that into {@code Content-Disposition}. The
 * non-IE branch merely reinterpreted the bytes — no encoding, no CR/LF rejection — so response splitting was
 * reachable (D-T4). The content type was set <b>four times</b> and none of them was the type of the
 * {@code .xlsx} actually produced (D-T34).</p>
 *
 * // source: biztalk_admin_30_spreadsheet_view.jsp — FILE_NM, zipFileName, four setContentType calls
 * // req: FR-TLKX-003, FR-TLKX-004, FR-TLKX-007, NFR-SEC-HDR-T01, NFR-COMPAT-T01
 */
@WebMvcTest(controllers = TalkExportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"iris.auth.require-https=false"})
class TalkExportControllerTest {

    private static final String EXPORT = "/api/admin/biztalk/talk-history/export";

    @Autowired private MockMvc mvc;

    @MockBean private TalkHistoryService historyService;
    @MockBean private TalkExportService exportService;
    @MockBean private AuditService audit;
    @MockBean private Clock clock;

    /**
     * 실제 조건 객체를 만든다. / Builds a real criteria object.
     *
     * <p>헤더의 파일명이 {@code window()} 에서 나오므로 가짜로 대체할 수 없다 — 그 경로가
     * 검증 대상이다.</p>
     * <p>The filename comes from {@code window()}, so it cannot be stubbed away: that path is what is under
     * test.</p>
     */
    private static TalkHistoryCriteria criteria(String from, String to) {
        return new TalkHistoryCriteria(
                TalkPeriodPolicy.validate(from, to, null, null),
                new PrincipalScope(null, true, false),
                null, null, null,
                BizTalkApiRegistry.withDefaults().codes(), 0, 100);
    }

    @BeforeEach
    void setUp() throws Exception {
        given(historyService.criteriaFor(any())).willReturn(criteria("20260801", "20260819"));
        given(exportService.export(any(), any(OutputStream.class))).willReturn(42);
    }

    @Nested
    @DisplayName("파일명 헤더 / the filename header")
    class FilenameHeader {

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("RFC 5987 filename* 형태로 UTF-8 인코딩된다 — FR-TLKX-003")
        void filenameIsRfc5987Encoded() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            String disposition = result.getResponse().getHeader("Content-Disposition");

            assertThat(disposition)
                    .as("첨부로 내려야 한다 / must be delivered as an attachment")
                    .startsWith("attachment;");
            assertThat(disposition)
                    .as("RFC 5987 filename* 이 있어야 한다 — 한글 파일명이 깨지지 않는 유일한 형태 / "
                            + "an RFC 5987 filename* is the only form that survives a Korean filename")
                    .contains("filename*=UTF-8''");
            // 레거시 비-IE 분기는 바이트를 ISO-8859-1 로 다시 해석했을 뿐이라 한글이 깨졌고,
            // IE 분기만 우연히 URLEncoder 를 호출해 안전했다.
            // The legacy's non-IE branch only reinterpreted bytes as ISO-8859-1, mangling Korean, while its IE
            // branch happened to call URLEncoder and so was safe by accident.
            assertThat(disposition).contains(".xlsx");
        }

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("파일명이 검증된 일자만 담는다 — 요청 값이 그대로 실리지 않는다")
        void filenameCarriesOnlyValidatedDates() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            String decoded = java.net.URLDecoder.decode(
                    result.getResponse().getHeader("Content-Disposition"), StandardCharsets.UTF_8);

            // 서버 상수 + 검증을 통과한 일자. 요청 문자열이 아니라 <b>정규화된 일자</b>가 실린다.
            // A server constant plus dates that passed validation — the <b>normalised</b> dates, not the
            // request strings.
            assertThat(decoded)
                    .contains(TalkExportController.FILENAME_PREFIX)
                    .contains("20260801-20260819");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "20260801%0d%0aX-Injected:+1",
                "20260801%0aSet-Cookie:+a%3db",
                "20260801%0d%0a%0d%0a<html>"
        })
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("CR/LF 를 담은 요청은 헤더에 도달하지 못한다 — D-T4, NFR-SEC-HDR-T01")
        void crlfNeverReachesAHeader(String rawFrom) throws Exception {
            // ⚠ 이것이 D-T4 다. 레거시는 이 값을 파일명에 이어 붙이고 Content-Disposition 에
            // 넣었으므로 응답 분할이 가능했다. 여기서는 두 겹으로 막힌다: 일자가
            // TalkPeriodPolicy 를 통과해야 하고, 파일명은 그 <b>검증된</b> 일자로만 조립된다.
            //
            // This is D-T4. The legacy concatenated the value into the filename and wrote it into
            // Content-Disposition, making response splitting reachable. Two layers stop it here: the date must
            // pass TalkPeriodPolicy, and the filename is composed only from the <b>validated</b> date.
            given(historyService.criteriaFor(any()))
                    .willThrow(new com.webcash.iris.biztalk.domain.PeriodPolicy
                            .InvalidPeriodException("시작일자는 YYYYMMDD 8자리여야 합니다."));

            MvcResult result = mvc.perform(get(EXPORT).param("from", rawFrom))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // 거부된 요청은 파일 헤더를 아예 만들지 않는다.
            // A refused request never builds a file header at all.
            assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
            verify(exportService, never()).export(any(), any(OutputStream.class));
        }

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("어떤 헤더에도 CR/LF 가 없다")
        void noHeaderContainsCrlf() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            for (String name : result.getResponse().getHeaderNames()) {
                for (String value : result.getResponse().getHeaders(name)) {
                    assertThat(value)
                            .as("헤더 '%s' 에 개행이 있으면 응답 분할이다 / "
                                    + "a newline in header '%s' is response splitting", name, name)
                            .doesNotContain("\r")
                            .doesNotContain("\n");
                }
            }
        }
    }

    @Nested
    @DisplayName("콘텐츠 타입 / the content type")
    class ContentType {

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("정확히 한 번, xlsx 의 올바른 타입으로 설정된다 — D-T34, FR-TLKX-004")
        void exactlyOneCorrectContentType() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            // ⚠ 레거시는 이것을 네 번 설정했다: 페이지 지시자의
            // application/vnd.ms-excel; name='excel',text/html, 비-IE 분기의
            // application/vnd.ms-excel, application/download; UTF-8, application/octet-stream.
            // 만들어지는 파일은 .xlsx 이고 그중 어느 것도 그 타입이 아니었다.
            //
            // The legacy set this four times — a page directive, then application/vnd.ms-excel on the non-IE
            // branch only, then application/download; UTF-8, then application/octet-stream. The file produced
            // is .xlsx and none of the four was its type.
            assertThat(result.getResponse().getHeaders("Content-Type"))
                    .as("타입이 여러 번 설정되면 클라이언트가 무엇을 볼지 정해지지 않는다 / "
                            + "setting it more than once leaves what the client sees undefined")
                    .hasSize(1);

            assertThat(result.getResponse().getContentType())
                    .isEqualTo(TalkExportController.XLSX_MEDIA_TYPE);
        }

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("레거시가 쓴 잘못된 타입들이 나타나지 않는다")
        void legacyMediaTypesAreAbsent() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            String contentType = String.valueOf(result.getResponse().getContentType());

            assertThat(contentType)
                    .doesNotContain("vnd.ms-excel")
                    .doesNotContain("application/download")
                    .doesNotContain("octet-stream")
                    .doesNotContain("text/html");
        }
    }

    @Nested
    @DisplayName("행 수 헤더 / the row-count header")
    class RowCountHeader {

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("실제로 쓴 행 수를 헤더로 알린다 — FR-TLKX-007")
        void rowCountIsSurfaced() throws Exception {
            MvcResult result = mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isOk())
                    .andReturn();

            // 감사 기록에 남는 수와 대조할 수 있어야 한다. 레거시는 내보내기 기록이 아예 없었고
            // 파일이 몇 건인지 아무도 몰랐다.
            // It must be reconcilable against the audit record. The legacy recorded no export at all and nobody
            // knew how many rows a file held.
            assertThat(result.getResponse().getHeader("X-Talk-Export-Rows")).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("인가 / authorization")
    class Authorization {

        @Test
        @DisplayName("인증 없이 거부된다 — 파일을 만들지 않는다")
        void anonymousIsRefused() throws Exception {
            mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().is4xxClientError());
            verify(exportService, never()).export(any(), any(OutputStream.class));
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("이용기관 주체는 403 이다 — CONFLICT-T01")
        void tenantIsForbidden() throws Exception {
            // 내보내기는 이 슬라이스에서 가장 민감한 동작이다 — 레거시에서는 한 번의 클릭으로
            // 모든 기관의 평문 전화번호가 파일로 나갔다(D-T1, CVSS 8.6). 인가는 문을 만들 때
            // 함께 걸었고, 이 시험이 그것을 고정한다.
            // The export is the slice's most sensitive action: in the legacy one click produced a file holding
            // every institution's plaintext phone numbers (D-T1, CVSS 8.6). Authorization was fitted when the
            // door was built, and this test pins it.
            mvc.perform(get(EXPORT).param("from", "20260801"))
                    .andExpect(status().isForbidden());
            verify(exportService, never()).export(any(), any(OutputStream.class));
        }

        @Test
        @WithMockUser(roles = {"OPERATOR"})
        @DisplayName("요청일자가 없으면 400 이며 파일을 만들지 않는다")
        void missingFromIsRejected() throws Exception {
            mvc.perform(get(EXPORT)).andExpect(status().isBadRequest());
            verify(exportService, never()).export(any(), any(OutputStream.class));
        }
    }
}
