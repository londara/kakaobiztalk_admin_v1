package com.webcash.iris.biztalk.alimtalk.api;

import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkBatchRequest;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkSendService;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkButton;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkLimits;
import com.webcash.iris.biztalk.alimtalk.domain.AlimTalkRequest;
import com.webcash.iris.biztalk.alimtalk.domain.FailbackData;
import com.webcash.iris.biztalk.alimtalk.domain.ProfileKey;
import com.webcash.iris.biztalk.alimtalk.domain.RecipientNumber;
import com.webcash.iris.biztalk.alimtalk.domain.RecipientParser;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateMatchResult;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateMatcher;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateRegistry;
import com.webcash.iris.biztalk.alimtalk.domain.TemplateSummary;
import com.webcash.iris.biztalk.alimtalk.domain.TranIdGenerator;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.SenderProfileKeyResolver;
import com.webcash.iris.biztalk.alimtalk.infra.vendor.VendorPayloadMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 알림톡 엔드포인트 — 운영자 전용. / Kakao AlimTalk endpoints, operators only.
 *
 * <h2>레거시에는 서버가 없었다 / the legacy had no server side</h2>
 * <p>{@code WSVC.biztalk_admin_61} 은 {@code actUseYn=N} 이고 {@code biztalk_admin_61_act} 클래스는
 * 어떤 계층에도 존재하지 않는다. 화면 61 은 JSON 을 읽기 전용 textarea 에 쓰고 운영자가 손으로
 * 복사했다 — 즉 <b>검증할 서버가 없었고, 그래서 검증도 없었다.</b> 계약 위반 세 건(D-A1·D-A2·
 * D-A3)이 1년 넘게 살아남은 구조적 이유가 이것이다.</p>
 * <p>{@code WSVC.biztalk_admin_61} declares {@code actUseYn=N} and no {@code biztalk_admin_61_act} class
 * exists in any layer. Screen 61 wrote JSON into a read-only textarea for an operator to copy by hand —
 * there was <b>no server to validate anything, and so nothing was validated.</b> That is the structural
 * reason three contract defects survived over a year.</p>
 *
 * <p>이 컨트롤러가 그 서버다. 레거시 화면이 {@code login=Y} 하나만 걸고도 안전했던 이유는
 * 아무 일도 할 수 없었기 때문이며(D-A2 참조), 발송 능력이 생기는 순간 그 보호는 사라진다.
 * 따라서 인가는 물려받지 않고 새로 도출한다(FR-AZ-A03).</p>
 * <p>This controller is that server. The legacy screen was safe carrying only {@code login=Y} because it
 * could do nothing; the moment it can send, that protection is gone. Authorization is therefore derived
 * afresh rather than inherited (FR-AZ-A03).</p>
 *
 * <h2>Sprint A1 범위 / Sprint A1 scope</h2>
 * <p>여기 있는 두 엔드포인트는 <b>어떤 것도 쓰지 않고 아무것도 보내지 않는다</b> — 순수 검증이다.
 * 발송({@code POST /send}, {@code /batch})은 outbox·디스패처·벤더 클라이언트·자격증명 해결을
 * 필요로 하므로 Sprint A2 에 속한다(A2-04…A2-07). 발송 경로를 A1 에 넣지 않은 이유는 일정이
 * 아니라 <b>보안</b>이다: A2-01 이전에 발송을 배선하면 <b>유출된 키로</b> 발송하게 된다(T-A1).</p>
 * <p>Both endpoints here <b>write nothing and send nothing</b> — they are pure validation. Sending
 * ({@code POST /send}, {@code /batch}) needs the outbox, the dispatcher, the vendor client and credential
 * resolution, so it belongs to Sprint A2 (A2-04…A2-07). Sending is absent from A1 for a <b>security</b>
 * reason rather than a scheduling one: wiring it before A2-01 would mean sending <b>with the leaked
 * key</b> (T-A1).</p>
 *
 * <p>{@code /api/admin/**} 아래 두어 {@code SecurityConfig} 의 운영자 규칙을 받게 하고, 형제 슬라이스
 * 관례대로 컨트롤러 수준 {@code @PreAuthorize} 를 이중으로 둔다.</p>
 * <p>Placed under {@code /api/admin/**} for the operator routing rule, with a controller-level
 * {@code @PreAuthorize} as defence in depth, following the sibling slices' convention.</p>
 *
 * // source: WSVC.biztalk_admin_61.xml (actUseYn=N); biztalk_admin_61.js; biztalk_admin_61_view.jsp
 * // req: FR-AZ-A01, FR-AZ-A03, FR-ATC-012, FR-ATV-007, FR-ATS-005, FR-ATS-008
 */
@RestController
@RequestMapping("/api/admin/alimtalk")
@PreAuthorize("hasRole('OPERATOR')")
public class AlimTalkController {

    /**
     * 미리보기 직렬화기. / The preview serialiser.
     *
     * <p>{@link ProfileKey} 와 {@code RecipientNumber} 가 가려진 값으로 직렬화되므로, 이
     * 미리보기에는 자격증명도 평문 수신번호도 나타나지 않는다(FR-AZ-A05, NFR-SEC-PII-A01).</p>
     * <p>{@link ProfileKey} and {@code RecipientNumber} serialise redacted, so neither a credential nor a
     * clear recipient number appears in this preview.</p>
     *
     * // req: FR-AZ-A05, NFR-SEC-PII-A01
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper PREVIEW =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final TranIdGenerator tranIds;
    private final TemplateRegistry templates;
    private final SenderProfileKeyResolver profileKeys;
    private final AlimTalkSendService sends;
    private final VendorPayloadMapper vendorPayloads;
    private final boolean dispatchEnabled;

    /**
     * 컨트롤러를 생성한다. / Creates the controller.
     *
     * @param tranIds         거래고유번호 생성기 / the transaction-id generator
     * @param templates       템플릿 레지스트리 / the template registry
     * @param profileKeys     발신프로필키 해결기 / the profile key resolver
     * @param sends           발송 접수 / send acceptance
     * @param vendorPayloads  벤더용 직렬화기 — 미리보기와 달리 가려지지 않은 값을 쓴다
     *                        / the vendor serialiser, which unlike the preview writes unmasked values
     * @param dispatchEnabled 발송 경로가 배선되었는지 / whether the dispatch path is wired
     */
    public AlimTalkController(
            TranIdGenerator tranIds,
            TemplateRegistry templates,
            SenderProfileKeyResolver profileKeys,
            AlimTalkSendService sends,
            VendorPayloadMapper vendorPayloads,
            @org.springframework.beans.factory.annotation.Value(
                    "${iris.alimtalk.dispatch.enabled:false}") boolean dispatchEnabled) {
        this.tranIds = tranIds;
        this.templates = templates;
        this.profileKeys = profileKeys;
        this.sends = sends;
        this.vendorPayloads = vendorPayloads;
        this.dispatchEnabled = dispatchEnabled;
    }

    /**
     * 이용기관의 템플릿 목록을 조회한다. / Lists an institution's templates.
     *
     * <p>레거시가 자유 입력으로 두었던 {@code template_code} 를 선택 목록으로 바꾼다(D-A15).
     * 범위는 세션에서 나온다 — {@code institution} 파라미터는 <b>요구</b>일 뿐이고
     * {@link TemplateRegistry} 가 세션 권한으로 다시 판정한다(FR-AZ-A02, FR-ATT-003).</p>
     * <p>Replaces the free-text {@code template_code} with a selection list (D-A15). The scope comes from
     * the session: the {@code institution} parameter is a <b>request</b>, re-adjudicated against the
     * session's entitlement by {@link TemplateRegistry} (FR-AZ-A02, FR-ATT-003).</p>
     *
     * @param institution 요청된 이용기관코드 / the requested institution code
     * @return 템플릿 요약 목록 / template summaries
     *
     * // source: IDO.KKB_MSG_TMPL_L001; biztalk_admin_61_view.jsp — free-text template_code input
     * // req: FR-ATT-001, FR-ATT-002, FR-ATT-003, FR-AZ-A02
     */
    @GetMapping("/templates")
    public ResponseEntity<List<TemplateSummary>> templates(
            @RequestParam("institution") String institution) {
        return ResponseEntity.ok(templates.list(institution));
    }

    /**
     * 등록된 템플릿에 대해 메시지를 검증한다. / Validates a message against its registered template.
     *
     * <p>{@link #validate} 의 수동 형태와 달리, 본문을 <b>레지스트리에서</b> 가져온다 —
     * {@code KKB_MSG_TMPL.TEMPLATE_MSG} 는 화면이 이미 수집하는 두 값으로 키가 잡혀 있으므로
     * 운영자가 손으로 붙여넣을 이유가 없다(D-A16). 미등록 코드는 내용 불일치와 <b>구분해서</b>
     * 보고된다(FR-ATV-003).</p>
     * <p>Unlike the manual {@link #validate}, the body comes <b>from the registry</b>: since
     * {@code KKB_MSG_TMPL.TEMPLATE_MSG} is keyed by the two values the screen already collects, there is no
     * reason for an operator to paste it (D-A16). An unregistered code is reported <b>distinctly</b> from a
     * content mismatch (FR-ATV-003).</p>
     *
     * @param request 이용기관, 템플릿코드, 검증할 본문 / institution, template code and the body to check
     * @return 검증 결과 / the validation outcome
     *
     * // req: FR-ATV-001, FR-ATV-002, FR-ATV-003, FR-ATT-004, FR-AZ-A02
     */
    @PostMapping("/templates/validate")
    public ResponseEntity<RegisteredValidateResponse> validateAgainstRegistry(
            @RequestBody RegisteredValidateRequest request) {
        TemplateRegistry.Outcome outcome =
                templates.validate(request.institutionCode(), request.templateCode(), request.content());
        return ResponseEntity.ok(new RegisteredValidateResponse(
                outcome.registered(),
                outcome.permitsSend(),
                outcome.match().variableValues(),
                outcome.match().divergences()));
    }

    /**
     * 메시지 내용이 템플릿에 부합하는지 검증한다. / Validates message content against a template.
     *
     * <p>수동 검증 탭에 대응한다(FR-ATV-007). 등록된 템플릿을 대상으로 하는 자동 검증은
     * {@code KKB_MSG_TMPL} 조회가 필요하므로 A1-11/A1-12 에 속하며, <b>같은</b>
     * {@link TemplateMatcher} 를 쓴다 — 수동 결과와 자동 결과가 어긋날 수 없어야 한다.</p>
     * <p>Serves the manual validation tab (FR-ATV-007). Automatic validation against a registered
     * template needs a {@code KKB_MSG_TMPL} read and belongs to A1-11/A1-12; it uses the <b>same</b>
     * {@link TemplateMatcher}, so a manual verdict and an automatic one cannot disagree.</p>
     *
     * <p>{@code POST} 인 이유: 템플릿 본문과 메시지 본문은 URL 에 담기에 길고, 메시지에는 고객
     * 데이터가 들어갈 수 있다 — 질의 문자열은 로그에 남는다.</p>
     * <p>{@code POST} because template and message bodies are too long for a URL and the message may carry
     * customer data: query strings end up in logs.</p>
     *
     * @param request 템플릿 본문과 검증할 내용 / the template body and the content to check
     * @return 일치 여부와 불일치 지점 / conformance and any divergences
     *
     * // req: FR-ATV-002, FR-ATV-004, FR-ATV-006, FR-ATV-007, FR-ATV-008
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
        TemplateMatcher matcher;
        try {
            matcher = TemplateMatcher.compile(request.template());
        } catch (IllegalArgumentException e) {
            // 컴파일 거절은 템플릿 자체의 문제다 — ${...} 사용, 인접 변수, 변수 수 초과.
            // 내용 불일치와 구분해서 알려준다(FR-ATV-003 과 같은 원칙).
            // A compile rejection is a problem with the template itself — ${...}, adjacent variables, or too
            // many variables. Reported distinctly from a content mismatch (the FR-ATV-003 principle).
            return ResponseEntity.badRequest().body(ValidateResponse.templateRejected(e.getMessage()));
        }
        TemplateMatchResult result = matcher.match(request.content());
        return ResponseEntity.ok(ValidateResponse.of(result));
    }

    /**
     * 수신번호 입력을 해석해 발송 전 확인 정보를 돌려준다. / Parses recipient input for a pre-despatch check.
     *
     * <p><b>이 엔드포인트가 존재하는 이유는 D-A26 이다.</b> 레거시는 형식이 맞지 않는 수신번호를
     * 발송과 이력 기록이 <b>모두 끝난 뒤</b> 예외로 던졌다 — 운영자는 "전송 실패"를 보았으나
     * 메시지는 이미 나가 있었다. 2500건 분기에서는 예외가 루프 <b>안</b>에 있어 첫 1000건만
     * 전달되고 나머지가 조용히 버려졌다.</p>
     * <p><b>This endpoint exists because of D-A26.</b> The legacy threw on malformed recipients <b>after</b>
     * both the despatch and the history write — the operator saw "send failed" while the messages had gone
     * out. In the 2500-recipient branch the throw sat <b>inside</b> the loop, so the first 1000 were
     * delivered and the rest silently abandoned.</p>
     *
     * <p>그래서 제외될 번호는 <b>예외가 아니라 응답</b>으로 돌아온다. 운영자가 발송 전에
     * "유효한 N건만 보낼지" 결정할 수 있다(FR-ATS-007). 응답의 수신번호는 마스킹된다.</p>
     * <p>So excluded numbers come back <b>as a response, not an exception</b>, letting the operator decide
     * before sending whether to proceed with the valid subset (FR-ATS-007). Numbers in the response are
     * masked.</p>
     *
     * @param request 원문 수신번호 입력과 이용기관코드 / the raw recipient input and institution code
     * @return 유효 건수, 중복 제거 건수, 제외된 값, 발급된 거래고유번호
     *         / valid count, duplicates removed, excluded values and an issued transaction id
     *
     * // req: FR-ATC-012, FR-ATS-005, FR-ATS-006, FR-ATS-007, FR-ATS-008, NFR-SEC-PII-A01
     */
    @PostMapping("/recipients/preview")
    public ResponseEntity<RecipientPreviewResponse> previewRecipients(
            @RequestBody RecipientPreviewRequest request) {
        RecipientParser.Result parsed = RecipientParser.parse(request.recipients());

        // 유효 수신자가 없으면 거래고유번호를 발급하지 않는다 — 발급 자체가 순번을 소비하므로,
        // 보낼 수 없는 요청에 번호를 낭비하지 않는다(D-A31, RISK-A04).
        // No transaction id is issued when there is nobody to send to: issuing consumes a sequence value,
        // and a request that cannot be sent should not spend one (D-A31, RISK-A04).
        String tranId = parsed.hasRecipients() ? tranIds.next(request.institutionCode()) : null;

        return ResponseEntity.ok(new RecipientPreviewResponse(
                parsed.count(),
                parsed.duplicates(),
                parsed.rejected(),
                parsed.accepted().stream().map(RecipientNumber::toString).toList(),
                parsed.requiresConfirmation(),
                tranId));
    }

    /**
     * 작성 폼으로부터 계약 적합 payload 를 만든다. / Composes a contract-conforming payload from the form.
     *
     * <p>레거시 "JSON 생성" 버튼에 대응한다. 다른 점은 <b>어디서 조립하는가</b>다 — 레거시는
     * 브라우저에서 객체를 손으로 쌓았고, 그래서 {@code failback} 을 잘못 쓰고(D-A1) 계약에 없는
     * 다섯 필드를 넣었으며(D-A2) {@code order} 를 빠뜨렸다(D-A3). 아무도 그 결과를 계약과
     * 대조하지 않았으므로 1년 넘게 드러나지 않았다.</p>
     * <p>Mirrors the legacy "JSON 생성" button. What differs is <b>where assembly happens</b>: the legacy
     * built the object by hand in the browser, which is how it emitted {@code failback} instead of
     * {@code failback_data} (D-A1), five fields the contract does not declare (D-A2), and no
     * {@code order} (D-A3). Nothing compared the result to the contract, so it survived over a year.</p>
     *
     * <p>여기서는 서버가 {@code AlimTalkRequest} 로 조립하므로 {@code ContractConformanceTest} 가
     * 지키는 바로 그 타입을 지나간다. 화면이 payload 모양을 알 필요가 없어진다.</p>
     * <p>Here the server assembles an {@code AlimTalkRequest}, so the payload passes through the very type
     * {@code ContractConformanceTest} guards. The screen no longer needs to know the payload's shape.</p>
     *
     * <p>미리보기의 {@code sender_key} 는 <b>가려진 채</b> 나온다. {@link ProfileKey} 가 그렇게
     * 직렬화되기 때문이며, 미리보기에 자격증명을 담을 이유가 없다(FR-AZ-A05).</p>
     * <p>The preview's {@code sender_key} appears <b>redacted</b>, because {@link ProfileKey} serialises that
     * way — a preview has no reason to carry a credential.</p>
     *
     * @param request 작성 폼 값 / the form values
     * @return payload JSON 과 검증 결과 / the payload JSON and validation findings
     *
     * // source: biztalk_admin_61.js — generateBtn click handler
     * // req: FR-ATC-001, FR-ATC-002, FR-ATC-005, FR-ATC-009, FR-ATC-012, FR-AZ-A05
     */
    @PostMapping("/compose")
    public ResponseEntity<ComposeResponse> compose(@RequestBody AlimTalkComposeRequest request) {
        List<String> problems = new java.util.ArrayList<>();
        AlimTalkRequest payload = buildSinglePayload(request, problems);

        String json;
        try {
            json = PREVIEW.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.internalServerError()
                    .body(new ComposeResponse(null, List.of("payload 를 직렬화할 수 없습니다.")));
        }
        return ResponseEntity.ok(new ComposeResponse(json, List.copyOf(problems)));
    }

    /**
     * 단건 폼을 계약 타입으로 옮기고 문제를 모은다. / Maps the single-send form to the contract type.
     *
     * <p>{@link #compose} 와 {@link #send} 가 <b>같은</b> 이 메서드를 쓴다. 두 경로가 각자 payload 를
     * 만들면 언젠가 어긋나고, 어긋나면 화면에서 검증을 통과한 것이 발송 시 거절된다 — 또는 더
     * 나쁘게, 검증되지 않은 것이 발송된다. 레거시가 정확히 그랬다: 화면 61 이 만드는 payload 와
     * 화면 50 이 보내는 payload 가 서로 달랐고, 그래서 화면 61 의 검증은 발송을 지키지 못했다.</p>
     * <p>{@link #compose} and {@link #send} share this method. Two paths building their own payloads
     * eventually diverge, and then what passed validation on screen is rejected at send — or worse, what
     * was never validated is sent. That is precisely what the legacy did: screen 61 composed one payload
     * and screen 50 sent a different one, so screen 61's validation guarded nothing.</p>
     *
     * @param request  작성 폼 / the form
     * @param problems 문제를 모을 목록 / the list to collect problems into
     * @return 계약 payload / the contract payload
     *
     * // source: biztalk_admin_61.js — generateBtn; biztalk_admin_50_s001_act.jsp — the send path
     * // req: FR-ATC-001, FR-ATC-002, FR-ATC-005, FR-ATC-009, FR-ATC-012, FR-AZ-A05
     */
    private AlimTalkRequest buildSinglePayload(
            AlimTalkComposeRequest request, List<String> problems) {

        RecipientParser.Result recipients = RecipientParser.parse(request.recipients());
        if (!recipients.hasRecipients()) {
            problems.add("수신번호가 없습니다.");
        }
        recipients.rejected().forEach(v -> problems.add("수신번호 형식 오류: " + v));

        checkLength(problems, "이용기관코드", request.isCd(), AlimTalkLimits.CONTRACT_IS_CD);
        checkLength(problems, "거래고유번호", request.tranId(), AlimTalkLimits.CONTRACT_TRAN_ID);
        checkLength(problems, "발신번호", request.senderNumber(), AlimTalkLimits.CONTRACT_SENDER_NUMBER);
        checkLength(problems, "예약발송시간", request.reqdate(), AlimTalkLimits.CONTRACT_REQDATE);
        checkLength(problems, "템플릿코드", request.templateCode(), AlimTalkLimits.CONTRACT_TEMPLATE_CODE);
        checkLength(problems, "강조표기 제목", request.templateTitle(), AlimTalkLimits.TEMPLATE_TITLE);
        checkLength(problems, "메시지", request.msg(), AlimTalkLimits.MSG);

        List<AlimTalkButton> buttons = new java.util.ArrayList<>();
        for (AlimTalkComposeRequest.ButtonInput input : nullSafe(request.buttons())) {
            AlimTalkButton button = new AlimTalkButton(
                    blankToNull(input.name()),
                    input.type() == null || input.type().isBlank()
                            ? null : AlimTalkButton.ButtonType.valueOf(input.type()),
                    blankToNull(input.urlPc()),
                    blankToNull(input.urlMobile()),
                    blankToNull(input.schemeAndroid()),
                    blankToNull(input.schemeIos()));
            checkLength(problems, "버튼명", button.name(), AlimTalkLimits.BUTTON_NAME);
            if (!button.isComplete()) {
                // 레거시는 이름이 빈 버튼을 조용히 버렸다 — 운영자는 화면에 설정된 버튼을 보고
                // 있었지만 payload 에는 없었다(D-A9).
                // The legacy silently dropped a button with a blank name: the operator saw one in the form
                // and none in the payload.
                problems.add("버튼이 완전하지 않습니다: " + (button.name() == null ? "(이름 없음)" : button.name()));
            }
            buttons.add(button);
        }

        FailbackData failback = null;
        AlimTalkComposeRequest.FailbackInput fb = request.failback();
        if (fb != null && fb.type() != null && !fb.type().isBlank()) {
            failback = new FailbackData(
                    FailbackData.FailbackType.valueOf(fb.type()),
                    blankToNull(fb.subject()),
                    blankToNull(fb.msg()),
                    blankToNull(fb.imgId()));
            checkLength(problems, "대체 전송 제목", failback.subject(), AlimTalkLimits.CONTRACT_FAILBACK_SUBJECT);
            if (!failback.isValid()) {
                problems.add("대체 전송 항목이 유효하지 않습니다 (본문 필수, 제목은 LMS·MMS, 이미지는 MMS).");
            }
        }

        AlimTalkRequest payload = new AlimTalkRequest(
                blankToNull(request.isCd()),
                blankToNull(request.tranId()),
                blankToNull(request.senderNumber()),
                recipients.accepted().isEmpty() ? null : recipients.accepted(),
                blankToNull(request.reqdate()),
                blankToNull(request.msg()),
                profileKeys.isConfiguredFor(request.isCd())
                        ? profileKeys.resolve(request.isCd())
                        : null,
                blankToNull(request.templateCode()),
                blankToNull(request.templateTitle()),
                buttons.isEmpty() ? null : buttons,
                failback);

        if (!profileKeys.isConfiguredFor(request.isCd())) {
            problems.add("이 이용기관의 발신프로필키가 설정되어 있지 않습니다 (A2-01).");
        }

        return payload;
    }

    /**
     * 다건 작성 폼으로부터 계약 적합 payload 를 만든다. / Composes a conforming batch payload.
     *
     * <p>레거시 다건 탭은 {@code msg_data} 배열을 만들면서 계약이 항목마다 요구하는
     * {@code order} 를 <b>넣지 않았다</b>(D-A3). 그 결함이 드러나지 않은 이유는
     * {@code ADV_KKO_AT_SEND_M} 을 호출하는 코드가 저장소에 <b>하나도 없었기</b> 때문이다
     * (D-A33) — 호출자가 없으니 검증이 없었고, 검증이 없으니 빠진 필드가 보이지 않았다.
     * 두 결함이 서로를 가렸다.</p>
     * <p>The legacy batch tab built a {@code msg_data} array <b>without</b> the {@code order} the contract
     * requires on every item (D-A3). It stayed invisible because <b>no code in the repository</b> ever
     * called {@code ADV_KKO_AT_SEND_M} (D-A33): no caller meant no validation, and no validation meant the
     * missing field never surfaced. Each defect hid the other.</p>
     *
     * <p>순번은 <b>여기서</b> 부여한다(FR-ATC-004). 클라이언트가 보낸 값을 쓰면 브라우저가 매긴
     * 번호를 서버가 믿는 셈이 되고, 그것은 배열 위치에 의존하던 원래 문제와 형태만 다르다.</p>
     * <p>The order is assigned <b>here</b> (FR-ATC-004). Using a client-supplied value would mean trusting
     * a number the browser chose — the original array-position problem in another shape.</p>
     *
     * @param request 다건 작성 폼 값 / the batch form values
     * @return payload JSON 과 검증 결과 / the payload JSON and validation findings
     *
     * // source: biztalk_admin_61.js — multi-panel msgData assembly; IMO.ADV_KKO_AT_SEND_M rule_Sub_1
     * // req: FR-ATC-001, FR-ATC-004, FR-ATC-005, FR-ATC-007, FR-AZ-A05
     */
    @PostMapping("/compose/batch")
    public ResponseEntity<ComposeResponse> composeBatch(
            @RequestBody AlimTalkBatchComposeRequest request) {
        List<String> problems = new java.util.ArrayList<>();

        checkLength(problems, "이용기관코드", request.isCd(), AlimTalkLimits.CONTRACT_IS_CD);
        checkLength(problems, "거래고유번호", request.tranId(), AlimTalkLimits.CONTRACT_TRAN_ID);

        List<AlimTalkBatchComposeRequest.ItemInput> inputs = nullSafe(request.items());
        if (inputs.isEmpty()) {
            problems.add("메시지 데이터가 없습니다.");
        }

        ProfileKey key = profileKeys.isConfiguredFor(request.isCd())
                ? profileKeys.resolve(request.isCd())
                : null;
        if (key == null) {
            problems.add("이 이용기관의 발신프로필키가 설정되어 있지 않습니다 (A2-01).");
        }

        List<AlimTalkBatchRequest.MsgDataItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            AlimTalkBatchComposeRequest.ItemInput input = inputs.get(i);
            String label = "메시지 " + (i + 1);

            RecipientParser.Result parsed = RecipientParser.parse(input.recipient());
            RecipientNumber recipient = parsed.accepted().isEmpty() ? null : parsed.accepted().get(0);
            if (recipient == null) {
                problems.add(label + ": 수신번호가 유효하지 않습니다.");
            }
            if (parsed.count() > 1) {
                // 계약은 msg_data 항목마다 수신번호 하나를 선언한다. 여러 개를 넣으면 어느
                // 것이 이 메시지의 대상인지 알 수 없다.
                // The contract declares one recipient per msg_data item; several leaves it unclear which
                // this message is for.
                problems.add(label + ": 항목마다 수신번호는 하나여야 합니다.");
            }

            checkLength(problems, label + " 발신번호", input.senderNumber(), AlimTalkLimits.CONTRACT_SENDER_NUMBER);
            checkLength(problems, label + " 예약발송시간", input.reqdate(), AlimTalkLimits.CONTRACT_REQDATE);
            checkLength(problems, label + " 템플릿코드", input.templateCode(), AlimTalkLimits.CONTRACT_TEMPLATE_CODE);
            checkLength(problems, label + " 강조표기 제목", input.templateTitle(), AlimTalkLimits.TEMPLATE_TITLE);
            checkLength(problems, label + " 메시지", input.msg(), AlimTalkLimits.MSG);

            List<AlimTalkButton> buttons = new java.util.ArrayList<>();
            for (AlimTalkComposeRequest.ButtonInput b : nullSafe(input.buttons())) {
                AlimTalkButton button = toButton(b);
                if (!button.isComplete()) {
                    problems.add(label + ": 버튼이 완전하지 않습니다.");
                }
                buttons.add(button);
            }

            FailbackData failback = toFailback(input.failback());
            if (failback != null && !failback.isValid()) {
                problems.add(label + ": 대체 전송 항목이 유효하지 않습니다.");
            }

            items.add(new AlimTalkBatchRequest.MsgDataItem(
                    // 순번은 1부터, 시스템이 부여한다 — 이것이 D-A3 의 수정이다.
                    // One-based and system-assigned: this is the D-A3 fix.
                    String.valueOf(i + 1),
                    recipient,
                    blankToNull(input.senderNumber()),
                    blankToNull(input.reqdate()),
                    blankToNull(input.msg()),
                    key,
                    blankToNull(input.templateCode()),
                    blankToNull(input.templateTitle()),
                    failback,
                    buttons.isEmpty() ? null : buttons));
        }

        AlimTalkBatchRequest payload = new AlimTalkBatchRequest(
                blankToNull(request.isCd()),
                blankToNull(request.tranId()),
                items.isEmpty() ? null : items);

        String json;
        try {
            json = PREVIEW.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.internalServerError()
                    .body(new ComposeResponse(null, List.of("payload 를 직렬화할 수 없습니다.")));
        }
        return ResponseEntity.ok(new ComposeResponse(json, List.copyOf(problems)));
    }

    /**
     * 버튼 입력을 계약 타입으로 옮긴다. / Maps a button input to the contract type.
     *
     * @param input 버튼 입력 / the button input
     * @return 계약 버튼 / the contract button
     *
     * // req: FR-ATC-002, FR-ATC-009
     */
    private static AlimTalkButton toButton(AlimTalkComposeRequest.ButtonInput input) {
        return new AlimTalkButton(
                blankToNull(input.name()),
                input.type() == null || input.type().isBlank()
                        ? null : AlimTalkButton.ButtonType.valueOf(input.type()),
                blankToNull(input.urlPc()),
                blankToNull(input.urlMobile()),
                blankToNull(input.schemeAndroid()),
                blankToNull(input.schemeIos()));
    }

    /**
     * 대체 전송 입력을 계약 타입으로 옮긴다. / Maps a fallback input to the contract type.
     *
     * @param input 대체 전송 입력 / the fallback input
     * @return 계약 대체 전송, 유형이 없으면 {@code null} / the contract fallback, or {@code null}
     *
     * // req: FR-ATC-002
     */
    private static FailbackData toFailback(AlimTalkComposeRequest.FailbackInput input) {
        if (input == null || input.type() == null || input.type().isBlank()) {
            return null;
        }
        return new FailbackData(
                FailbackData.FailbackType.valueOf(input.type()),
                blankToNull(input.subject()),
                blankToNull(input.msg()),
                blankToNull(input.imgId()));
    }

    /**
     * 길이를 확인하고 위반을 모은다. / Checks a length and collects the violation.
     *
     * @param problems 위반 목록 / the collected problems
     * @param label    화면 표시 이름 / the on-screen label
     * @param value    값 / the value
     * @param bound    유효 한계 / the effective bound
     *
     * // req: FR-ATC-005, NFR-USE-A03
     */
    private static void checkLength(List<String> problems, String label, String value, int bound) {
        if (!AlimTalkLimits.within(value, bound)) {
            // 레거시는 열두 개 길이 제약을 어디에도 적지 않았다(D-A7). 어긋난 규칙을 이름으로
            // 말한다 — "등록중 오류 발생" 은 운영자가 고칠 수 없다(NFR-USE-A03).
            // The legacy wrote none of the twelve length constraints anywhere (D-A7). The violated rule is
            // named: a generic error message is not something an operator can act on.
            problems.add(label + " 이(가) 최대 " + bound + "자를 넘습니다.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static <T> List<T> nullSafe(List<T> value) {
        return value == null ? List.of() : value;
    }

    /**
     * 작성 결과. / A composition result.
     *
     * @param payload  계약 적합 payload JSON / the contract-conforming payload JSON
     * @param problems 발견된 문제 / the problems found
     *
     * // req: FR-ATC-001, NFR-USE-A03
     */
    public record ComposeResponse(String payload, List<String> problems) {
    }

    /**
     * 발송 준비 상태를 돌려준다. / Reports how ready sending is for an institution.
     *
     * <p>레거시 화면 61 은 "JSON 생성" 버튼이 실제로 무엇을 하는지 화면에서 알 수 없었다 —
     * 운영자는 payload 를 손으로 복사해 어딘가에 붙여넣어야 했고, 그 payload 가 유효한지도
     * 알 수 없었다. 여기서는 <b>지금 보낼 수 있는지</b>와 <b>보낼 수 없다면 무엇이 남았는지</b>를
     * 화면이 말한다.</p>
     * <p>Legacy screen 61 gave no way to tell what "JSON 생성" actually achieved: the operator copied a
     * payload by hand and had no way to know it was valid. Here the screen states <b>whether a send is
     * possible now</b> and <b>what remains if it is not</b>.</p>
     *
     * <p>자격증명 여부만 확인하고 <b>값은 어떤 형태로도 응답에 담지 않는다</b>(FR-AZ-A05).
     * "설정되어 있다/없다" 는 운영자가 알아야 할 사실이고, 키 자체는 아니다.</p>
     * <p>Only the presence of a credential is reported; <b>no form of the value enters the response</b>
     * (FR-AZ-A05). Whether one is configured is something an operator needs to know; the key is not.</p>
     *
     * @param institution 요청된 이용기관코드 / the requested institution code
     * @return 준비 상태 / the readiness report
     *
     * // source: biztalk_admin_61_view.jsp — generateBtn / outputJson (no feedback on validity)
     * // req: FR-ATS-003, FR-AZ-A05, NFR-USE-A04
     */
    @GetMapping("/send-readiness")
    public ResponseEntity<SendReadinessResponse> sendReadiness(
            @RequestParam("institution") String institution) {
        boolean credential = profileKeys.isConfiguredFor(institution);

        List<String> blockers = new java.util.ArrayList<>();
        if (!credential) {
            blockers.add("이 이용기관의 발신프로필키가 설정되어 있지 않습니다 (A2-01).");
        }
        // 배선 여부를 <b>설정에서 읽어</b> 보고한다. 고정 문구를 두면 상태가 바뀌어도 문구가
        // 그대로 남아 거짓이 된다 — 레거시 화면 61 이 "JSON 생성" 이 무엇을 했는지 끝내 말하지
        // 않은 것과 같은 침묵이다.
        // Reported from configuration rather than a fixed string: a fixed string would survive a change
        // of state and become false — the same silence as legacy screen 61 never saying what
        // "JSON 생성" did.
        if (!dispatchEnabled) {
            blockers.add("발송 경로가 배선되지 않았습니다 — iris.alimtalk.dispatch.enabled 가 꺼져 있습니다.");
        }

        return ResponseEntity.ok(
                new SendReadinessResponse(credential, dispatchEnabled, List.copyOf(blockers)));
    }

    /**
     * 단건 발송을 접수한다. / Accepts a single send.
     *
     * <h2>"발송" 이 아니라 "접수" 인 이유 / why this accepts rather than sends</h2>
     * <p>이 메서드는 벤더를 호출하지 않는다. 아웃박스에 행을 쓰고 끝난다. 레거시는 운영자의
     * 요청 처리 안에서 벤더를 직접 호출했고(biztalk_admin_50_s001_act.jsp:133), 그래서 벤더가
     * 죽으면 발송이 <b>사라졌다</b> — 다시 시도할 기록이 없었기 때문이다.</p>
     * <p>This does not call the vendor: it writes an outbox row and returns. The legacy called the vendor
     * inside the operator's request, so a dead vendor made the send <b>vanish</b> — no record remained
     * from which to retry.</p>
     *
     * <p>응답은 <b>202 Accepted</b> 다. 200 을 돌려주면 화면이 "발송 완료" 라고 쓰게 되고, 그것은
     * 아직 사실이 아니다(NFR-OPS-A02).</p>
     * <p>The response is <b>202 Accepted</b>: a 200 would have the screen say "sent", which is not yet
     * true (NFR-OPS-A02).</p>
     *
     * <p>발송이 배선되지 않았으면 <b>409</b> 로 거절하고 아무것도 쓰지 않는다. 아웃박스에만 쌓아
     * 두면 나중에 배선한 순간 <b>오래된 메시지가 한꺼번에 나간다</b> — 그것이 예약 발송으로
     * 의도된 것인지 아닌지 알 수 없다.</p>
     * <p>With dispatch unwired it refuses with <b>409</b> and writes nothing. Merely accumulating rows
     * would mean that the moment dispatch is wired, <b>stale messages all go out at once</b>, with no way
     * to tell which of them were meant as reservations.</p>
     *
     * @param request 발송 요청 / the send request
     * @return 접수 결과 또는 거절 / the acceptance, or a refusal
     *
     * // source: biztalk_admin_50_s001_act.jsp:114-137
     * // req: FR-ATS-001, FR-ATS-002, FR-ATS-003, NFR-OPS-A02
     */
    @PostMapping("/send")
    public ResponseEntity<SendResponse> send(@RequestBody AlimTalkComposeRequest request) {
        if (!dispatchEnabled) {
            return ResponseEntity.status(409).body(new SendResponse(null, 0, false,
                    List.of("발송 경로가 배선되지 않았습니다. 접수하지 않았습니다 —"
                            + " iris.alimtalk.dispatch.enabled 를 확인하세요.")));
        }

        // 접수 전에 계약 적합성을 확인한다. 레거시는 브라우저에서만 검사했고(D-A5) 서버는
        // 무엇이든 받아 벤더에 넘겼다.
        // Conformance is checked before acceptance; the legacy checked only in the browser (D-A5) and the
        // server passed anything on to the vendor.
        ResponseEntity<ComposeResponse> composed = compose(request);
        ComposeResponse body = composed.getBody();
        if (body == null || body.payload() == null || !body.problems().isEmpty()) {
            List<String> problems = body == null ? List.of("payload 를 만들 수 없습니다.") : body.problems();
            return ResponseEntity.badRequest().body(new SendResponse(null, 0, false, problems));
        }

        // 벤더로 갈 payload 는 <b>가려지지 않은</b> 값을 담아야 한다. compose 의 결과는 미리보기용
        // 이라 마스킹되어 있으므로 다시 만든다 — 같은 계약 타입, 다른 직렬화기.
        // The payload bound for the vendor must carry <b>unmasked</b> values; compose's output is a masked
        // preview, so it is rendered again — same contract type, different serialiser.
        String vendorPayload;
        try {
            // 문제 목록은 이미 위에서 비어 있음을 확인했다 — 여기서는 payload 객체만 필요하다.
            // The problem list was already confirmed empty above; only the payload object is needed here.
            vendorPayload = vendorPayloads.render(
                    buildSinglePayload(request, new java.util.ArrayList<>()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return ResponseEntity.internalServerError()
                    .body(new SendResponse(null, 0, false, List.of("payload 를 직렬화할 수 없습니다.")));
        }

        AlimTalkSendService.Acceptance acceptance =
                sends.accept(request.isCd(), request.tranId(), List.of(vendorPayload), parseReqdate(request.reqdate()));

        if (acceptance.duplicate()) {
            // FR-ATS-009 — 다시 보내지 않고 원래 결과를 돌려준다. 409 를 쓰는 이유: 200 이면
            // 화면이 새로 접수된 것처럼 표시하고, 400 이면 운영자가 자기 입력이 잘못됐다고
            // 생각한다. 둘 다 사실이 아니다 — 요청은 올바르고, 이미 처리되었을 뿐이다.
            // Returns the original outcome instead of sending again. A 200 would look like a fresh
            // acceptance and a 400 would suggest the operator's input was wrong; neither is true — the
            // request is valid and simply already handled.
            return ResponseEntity.status(409).body(new SendResponse(
                    acceptance.tranId(),
                    acceptance.acceptedCount(),
                    acceptance.isScheduled(),
                    List.of("이 거래고유번호는 이미 접수되었습니다. 다시 보내지 않았습니다. 현재 상태: "
                            + acceptance.statuses())));
        }

        return ResponseEntity.accepted().body(new SendResponse(
                acceptance.tranId(), acceptance.acceptedCount(), acceptance.isScheduled(), List.of()));
    }

    /**
     * 접수된 발송의 결말을 조회한다. / Reports how an accepted send ended.
     *
     * <p>FR-ATS-002 는 벤더 결과를 운영자에게 제시하도록 요구한다. 접수와 발송이 분리되어 있어
     * 접수 응답에는 그 결과가 없으므로, 이 엔드포인트가 그 요구를 이어받는다.</p>
     * <p>FR-ATS-002 requires the vendor outcome to be presented to the operator; with acceptance separated
     * from despatch the acceptance response cannot carry it, so this endpoint takes up that
     * requirement.</p>
     *
     * <p>⚠ 문자 그대로의 충족은 아니다 — 두 번째 요청이 필요하다. ADR-ATK-023 수정 2 참조.</p>
     * <p>⚠ Not a literal satisfaction: a second request is needed. See ADR-ATK-023 amendment 2.</p>
     *
     * <p>payload 는 <b>돌려주지 않는다</b>. 그 안에는 수신번호와 발신프로필키가 평문으로 있다 —
     * 상태를 알려주는 것과 자격증명을 넘겨주는 것은 다른 일이다.</p>
     * <p>The payload is <b>not</b> returned: it holds the recipient and the profile key in clear, and
     * reporting a status is not the same as handing over credentials.</p>
     *
     * @param institution 이용기관코드 / institution code
     * @param tranId      거래고유번호 / transaction id
     * @return 항목별 상태 / the per-item statuses
     *
     * // req: FR-ATS-002, FR-ATS-009, FR-AZ-A02, NFR-SEC-PII-A01
     */
    @GetMapping("/send-status")
    public ResponseEntity<SendStatusResponse> sendStatus(
            @RequestParam("institution") String institution,
            @RequestParam("tranId") String tranId) {

        List<com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry> entries =
                sends.outcomeOf(institution, tranId);

        if (entries.isEmpty()) {
            // 없는 것과 실패한 것을 구분한다. 404 로 "접수 기록이 없다" 를 말해야 운영자가
            // 다시 보내도 되는지 판단할 수 있다.
            // Absent is distinguished from failed: a 404 saying "no acceptance on record" is what lets the
            // operator decide whether it is safe to send again.
            return ResponseEntity.status(404).body(new SendStatusResponse(tranId, List.of()));
        }

        List<SendStatusItem> items = entries.stream()
                .map(e -> new SendStatusItem(
                        e.msgOrder(), e.status().name(), e.attempts(), e.lastError()))
                .toList();
        return ResponseEntity.ok(new SendStatusResponse(tranId, items));
    }

    /**
     * 발송 결말. / A send outcome.
     *
     * @param tranId 거래고유번호 / the transaction id
     * @param items  항목별 상태 / the per-item statuses
     *
     * // req: FR-ATS-002
     */
    public record SendStatusResponse(String tranId, List<SendStatusItem> items) {
    }

    /**
     * 항목 하나의 상태. / One item's status.
     *
     * @param order     순번 / the order
     * @param status    상태 / the status
     * @param attempts  시도 횟수 / attempts made
     * @param lastError 마지막 오류 요약 / the last error summary
     *
     * // req: FR-ATS-002, NFR-OPS-A02
     */
    public record SendStatusItem(int order, String status, int attempts, String lastError) {
    }

    /**
     * {@code yyyyMMddHHmmss} 를 파싱한다. / Parses {@code yyyyMMddHHmmss}.
     *
     * <p>형식이 아니면 {@code null} 을 돌려 즉시 발송으로 둔다 — 이 시점에는 이미
     * {@link #compose} 가 길이를 검사했다. 여기서 예외를 던지면 접수 직전에 실패하고, 운영자는
     * 검증을 통과한 뒤 이유 없이 거절당한다.</p>
     * <p>A non-conforming value yields {@code null}, meaning immediate: {@link #compose} has already
     * checked the length by this point. Throwing here would fail immediately before acceptance, leaving
     * the operator rejected without reason after passing validation.</p>
     *
     * @param reqdate 예약 문자열 / the scheduled-time string
     * @return 파싱된 시각 또는 {@code null} / the parsed time, or {@code null}
     *
     * // req: FR-ATS-006, FR-ATC-007
     */
    private static java.time.LocalDateTime parseReqdate(String reqdate) {
        if (reqdate == null || reqdate.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(
                    reqdate.trim(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 접수 결과. / The result of an acceptance.
     *
     * @param tranId        거래고유번호 / the transaction id
     * @param acceptedCount 접수된 건수 / the number accepted
     * @param scheduled     예약 발송인지 / whether it is scheduled
     * @param problems      거절 사유 / reasons for refusal
     *
     * // req: FR-ATS-002, NFR-OPS-A02
     */
    public record SendResponse(
            String tranId, int acceptedCount, boolean scheduled, List<String> problems) {
    }

    /**
     * 발송 준비 상태. / A send-readiness report.
     *
     * @param credentialConfigured 이 기관의 프로파일키가 설정되어 있는지 / whether a profile key is configured
     * @param dispatchWired        발송 경로가 배선되었는지 / whether the dispatch path is wired
     * @param blockers             남은 항목 / what remains
     *
     * // req: FR-ATS-003, NFR-USE-A04
     */
    public record SendReadinessResponse(
            boolean credentialConfigured,
            boolean dispatchWired,
            List<String> blockers) {
    }

    /**
     * 템플릿 검증 요청. / A template validation request.
     *
     * @param template 템플릿 본문 / the template body
     * @param content  검증할 메시지 내용 / the message content to check
     *
     * // req: FR-ATV-007
     */
    public record ValidateRequest(String template, String content) {
    }

    /**
     * 등록 템플릿 기준 검증 요청. / A validation request against a registered template.
     *
     * <p>본문을 담지 <b>않는다</b> — 그것이 D-A16 의 요점이다. 레지스트리가 갖고 있는 값을
     * 클라이언트가 함께 보내면, 둘이 어긋날 때 어느 쪽이 권위인지 알 수 없게 된다.</p>
     * <p>It carries <b>no</b> template body — that is the point of D-A16. Letting the client send a value
     * the registry already holds leaves no way to say which is authoritative when they disagree.</p>
     *
     * @param institutionCode 요청된 이용기관코드 / the requested institution code
     * @param templateCode    템플릿코드 / the template code
     * @param content         검증할 메시지 내용 / the message content to check
     *
     * // req: FR-ATV-001, FR-ATT-004
     */
    public record RegisteredValidateRequest(String institutionCode, String templateCode, String content) {
    }

    /**
     * 등록 템플릿 기준 검증 응답. / A validation response against a registered template.
     *
     * @param registered     템플릿이 등록되어 있으면 {@code true} / {@code true} when registered
     * @param permitsSend    발송을 허용할 수 있으면 {@code true} / {@code true} when a send may proceed
     * @param variableValues 변수별로 읽어낸 값 / the value read per variable
     * @param divergences    불일치 지점 / the divergences
     *
     * // req: FR-ATV-002, FR-ATV-003, FR-ATV-006
     */
    public record RegisteredValidateResponse(
            boolean registered,
            boolean permitsSend,
            Map<String, String> variableValues,
            List<TemplateMatchResult.Divergence> divergences) {
    }

    /**
     * 템플릿 검증 응답. / A template validation response.
     *
     * @param conformant     부합하면 {@code true} / {@code true} when conformant
     * @param variableValues 변수별로 읽어낸 값 / the value read per variable
     * @param divergences    불일치 지점 / the divergences
     * @param templateError  템플릿 자체가 거절된 이유 / why the template itself was rejected
     *
     * // req: FR-ATV-002, FR-ATV-006, NFR-USE-A03
     */
    public record ValidateResponse(
            boolean conformant,
            java.util.Map<String, String> variableValues,
            List<TemplateMatchResult.Divergence> divergences,
            String templateError) {

        /**
         * 일치 결과를 응답으로 바꾼다. / Maps a match result to a response.
         *
         * @param result 일치 결과 / the match result
         * @return 응답 / the response
         */
        static ValidateResponse of(TemplateMatchResult result) {
            return new ValidateResponse(
                    result.matched(), result.variableValues(), result.divergences(), null);
        }

        /**
         * 템플릿 자체가 거절된 응답. / A response for a rejected template.
         *
         * @param reason 거절 이유 / the reason
         * @return 응답 / the response
         */
        static ValidateResponse templateRejected(String reason) {
            return new ValidateResponse(false, java.util.Map.of(), List.of(), reason);
        }
    }

    /**
     * 수신번호 미리보기 요청. / A recipient preview request.
     *
     * <p><b>{@code sender_key} 필드가 없다.</b> 있어서는 안 되기 때문이다 — 프로파일키는 서버가
     * 이용기관으로부터 해결하며, 클라이언트가 채울 수 있는 필드로 두면 FR-AZ-A05 의 "선택 불가"가
     * 성립하지 않는다(T-A24). 레거시 화면 61 은 운영자에게 이 키를 직접 입력하게 했다.</p>
     * <p><b>There is no {@code sender_key} field</b>, because there must not be: the profile key is resolved
     * server-side from the institution, and a client-populatable field would defeat FR-AZ-A05's "never
     * selectable" (T-A24). Legacy screen 61 asked operators to type it in.</p>
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @param recipients      원문 수신번호 입력 / the raw recipient input
     *
     * // req: FR-AZ-A05, FR-ATC-012
     */
    public record RecipientPreviewRequest(String institutionCode, String recipients) {
    }

    /**
     * 수신번호 미리보기 응답. / A recipient preview response.
     *
     * @param validCount           발송 대상 건수 / how many will be sent to
     * @param duplicatesRemoved    제거된 중복 건수 / duplicates removed
     * @param excluded             형식 불일치로 제외된 원문 값 / raw values excluded as malformed
     * @param maskedRecipients     마스킹된 수신번호 / the masked recipient numbers
     * @param requiresConfirmation 운영자 확인이 필요한지 / whether confirmation is required
     * @param tranId               발급된 거래고유번호, 대상이 없으면 {@code null}
     *                             / the issued transaction id, {@code null} when there is nobody to send to
     *
     * // req: FR-ATC-012, FR-ATS-007, NFR-SEC-PII-A01, NFR-USE-A04
     */
    public record RecipientPreviewResponse(
            int validCount,
            int duplicatesRemoved,
            List<String> excluded,
            List<String> maskedRecipients,
            boolean requiresConfirmation,
            String tranId) {
    }
}
