/**
 * 카카오 알림톡 API 클라이언트. / Kakao AlimTalk API client.
 *
 * req: FR-ATT-001, FR-ATT-002, FR-ATT-003, FR-ATV-001, FR-ATV-003, FR-ATC-012, FR-ATS-007
 * source: biztalk_admin_61.js — generateBtn / validateTemplateStrict / receiver-number handling
 *
 * <h2>이 클라이언트에 `senderKey` 가 없는 이유 / why there is no `senderKey` here</h2>
 * 레거시 화면 61 은 운영자에게 발신프로필키를 <b>직접 입력</b>하게 했다. 그 키는 기관을
 * 대신해 발송할 권한 그 자체이므로, 화면에 입력란이 있다는 것은 자격증명이 사람들 사이에서
 * 복사·붙여넣기로 돌아다닌다는 뜻이다(D-A24). 새 설계에서는 서버가 이용기관으로부터
 * 해결하며, 요청 타입에 필드가 <b>존재하지 않는다</b> — 잊을 수 있는 규칙이 아니라 타입이
 * 막는다(FR-AZ-A05, T-A24).
 *
 * Legacy screen 61 asked the operator to <b>type in</b> the sender profile key. That key *is* the
 * authority to send on the institution's behalf, so an input box for it means the credential
 * circulates among people as a copy-paste string (D-A24). Here the server resolves it from the
 * institution and the field <b>does not exist</b> on the request type — the type prevents it rather
 * than a rule someone must remember (FR-AZ-A05, T-A24).
 *
 * <h2>수신번호가 마스킹되어 돌아오는 이유 / why recipients come back masked</h2>
 * 서버는 `RecipientNumber` 래퍼로 직렬화하므로 응답의 번호는 `010****5678` 형태다
 * (NFR-SEC-PII-A01). 화면은 마스킹을 <b>수행하지 않는다</b> — 마스킹을 클라이언트에 맡기면
 * 평문이 이미 네트워크를 건너온 뒤다.
 *
 * The server serialises through the `RecipientNumber` wrapper, so numbers arrive as `010****5678`
 * (NFR-SEC-PII-A01). The screen does <b>not</b> perform the masking: leaving it to the client means
 * the clear value has already crossed the network.
 */

import { AuthApiError } from './authApi';

/** 템플릿 선택 목록의 한 항목. / One entry in the template selection list. */
export interface TemplateSummary {
  /** 템플릿코드 / the template code */
  templateCode: string;
  /** 강조표기제목의 기본값 / the default emphasis title */
  templateTitle: string | null;
}

/** 템플릿 불일치 지점. / A divergence between template and content. */
export interface Divergence {
  /** 내용에서의 문자 위치, 개행·공백 1자 / character offset; newline and space count as one */
  position: number;
  /** 어긋난 템플릿 조각 / the template fragment that did not match */
  templatePart: string;
  /** 사람이 읽는 이유 / a human-readable reason */
  reason: string;
}

/** 등록 템플릿 기준 검증 결과. / Validation outcome against a registered template. */
export interface RegisteredValidateResponse {
  /** 템플릿이 이 기관에 등록되어 있는지 / whether the template is registered to this institution */
  registered: boolean;
  /** 발송을 허용할 수 있는지 / whether a send may proceed */
  permitsSend: boolean;
  /** 변수별로 읽어낸 값 / the value read per variable */
  variableValues: Record<string, string>;
  /** 불일치 지점 — 첫 건만이 아니다 / the divergences, not merely the first */
  divergences: Divergence[];
}

/** 수신번호 미리보기 결과. / Recipient preview outcome. */
export interface RecipientPreviewResponse {
  /** 발송 대상 건수 / how many will be sent to */
  validCount: number;
  /** 제거된 중복 건수 / duplicates removed */
  duplicatesRemoved: number;
  /** 형식 불일치로 제외된 원문 값 / raw values excluded as malformed */
  excluded: string[];
  /** 마스킹된 수신번호 — 서버가 마스킹한다 / masked numbers, masked by the server */
  maskedRecipients: string[];
  /** 운영자 확인이 필요한지 / whether confirmation is required */
  requiresConfirmation: boolean;
  /** 발급된 거래고유번호, 대상이 없으면 null / the issued transaction id, null when nobody to send to */
  tranId: string | null;
}

async function post<T>(path: string, body: unknown, fallbackMessage: string): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '알림톡 발송 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? fallbackMessage),
    });
  }
  return payload as T;
}

/**
 * 이용기관의 템플릿 목록을 조회한다. / Lists an institution's templates.
 *
 * req: FR-ATT-001, FR-ATT-003
 *
 * 이용기관을 고르지 않았으면 요청하지 않는다 — 발신번호 화면과 같은 판단(D-S19 계열).
 * No request when no institution is chosen, the same judgement as the sender-number screen.
 *
 * 레거시는 `template_code` 를 자유 입력으로 두었다(D-A15). `KKB_MSG_TMPL` 이 정확히
 * (기관, 코드) 로 키가 잡힌 레지스트리이므로 선택 목록을 만들 재료가 이미 있었고, 등록되지
 * 않은 코드로 발송하면 벤더가 거절하므로 자유 입력은 실패를 나중으로 미루는 선택이었다.
 * The legacy left `template_code` as free text (D-A15) though the registry is keyed by exactly
 * (institution, code); an unregistered code is rejected by the vendor, so free text merely deferred
 * the failure.
 */
export async function listTemplates(institution: string): Promise<TemplateSummary[]> {
  if (!institution || institution.trim() === '') {
    return [];
  }
  const params = new URLSearchParams({ institution });
  const response = await fetch(`/api/admin/alimtalk/templates?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });
  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '알림톡 템플릿 조회 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? '템플릿을 조회할 수 없습니다.'),
    });
  }
  return payload as TemplateSummary[];
}

/**
 * 등록된 템플릿에 대해 메시지를 검증한다. / Validates a message against its registered template.
 *
 * req: FR-ATV-001, FR-ATV-002, FR-ATV-003
 *
 * 템플릿 본문을 <b>보내지 않는다</b>. 레거시 검증 탭은 운영자에게 본문을 손으로 붙여넣게
 * 했지만 `KKB_MSG_TMPL.TEMPLATE_MSG` 가 이미 그 값을 갖고 있었다(D-A16). 클라이언트가 본문을
 * 함께 보내면 둘이 어긋날 때 어느 쪽이 권위인지 알 수 없게 된다.
 *
 * The template body is <b>not sent</b>. The legacy tab had the operator paste it, though
 * `KKB_MSG_TMPL.TEMPLATE_MSG` already held it (D-A16). Sending a copy would leave no way to say
 * which is authoritative when the two disagree.
 */
export async function validateAgainstRegistry(request: {
  institutionCode: string;
  templateCode: string;
  content: string;
}): Promise<RegisteredValidateResponse> {
  return post<RegisteredValidateResponse>(
    '/api/admin/alimtalk/templates/validate',
    request,
    '템플릿을 검증할 수 없습니다.',
  );
}

/**
 * 수신번호를 해석해 발송 전 확인 정보를 얻는다. / Parses recipients for a pre-despatch check.
 *
 * req: FR-ATC-012, FR-ATS-005, FR-ATS-006, FR-ATS-007
 *
 * 이 호출이 존재하는 이유는 D-A26 이다. 레거시는 형식이 맞지 않는 수신번호를 발송과 이력
 * 기록이 <b>모두 끝난 뒤</b> 예외로 던졌으므로, 운영자는 "전송 실패" 를 보았지만 메시지는 이미
 * 나가 있었다. 2500건 분기에서는 예외가 루프 안에 있어 첫 1000건만 전달되고 나머지가 조용히
 * 버려졌다. 이제 제외 대상이 <b>발송 전에</b> 값으로 돌아온다.
 *
 * This call exists because of D-A26: the legacy threw on malformed recipients <b>after</b> both the
 * despatch and the history write, so the operator saw "send failed" while the messages had gone out.
 * In the 2500-recipient branch the throw sat inside the loop, delivering the first 1000 and silently
 * abandoning the rest. Exclusions now come back as data, <b>before</b> sending.
 */
export async function previewRecipients(request: {
  institutionCode: string;
  recipients: string;
}): Promise<RecipientPreviewResponse> {
  return post<RecipientPreviewResponse>(
    '/api/admin/alimtalk/recipients/preview',
    request,
    '수신번호를 확인할 수 없습니다.',
  );
}

/** 발송 준비 상태. / A send-readiness report. */
export interface SendReadinessResponse {
  /** 이 기관의 발신프로필키가 설정되어 있는지 / whether a profile key is configured */
  credentialConfigured: boolean;
  /** 발송 경로가 배선되었는지 / whether the dispatch path is wired */
  dispatchWired: boolean;
  /** 남은 항목 / what remains before a send is possible */
  blockers: string[];
}

/**
 * 발송 준비 상태를 조회한다. / Reads how ready sending is.
 *
 * req: FR-ATS-003, FR-AZ-A05, NFR-USE-A04
 *
 * 화면이 "다음 스프린트에 제공됩니다" 라는 고정 문구를 두는 대신 서버가 실제 상태를
 * 말하게 한다. 고정 문구는 상태가 바뀌어도 그대로 남아 거짓이 되고, 레거시 화면 61 이
 * "JSON 생성" 이 무엇을 했는지 알려주지 않았던 것과 같은 종류의 침묵이다.
 *
 * The screen asks the server for the actual state instead of carrying a fixed "coming next sprint"
 * note. A fixed note survives the state changing and becomes false — the same kind of silence as
 * legacy screen 61, which never told the operator what "JSON 생성" had achieved.
 *
 * 응답에는 키가 어떤 형태로도 담기지 않는다. "설정되어 있다" 는 운영자가 알아야 할 사실이고,
 * 키 자체는 아니다(FR-AZ-A05).
 * No form of the key appears in the response: whether one is configured is something an operator
 * needs to know; the key is not.
 */
export async function readSendReadiness(institution: string): Promise<SendReadinessResponse | null> {
  if (!institution || institution.trim() === '') {
    return null;
  }
  const params = new URLSearchParams({ institution });
  const response = await fetch(`/api/admin/alimtalk/send-readiness?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });
  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '알림톡 발송 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? '발송 준비 상태를 확인할 수 없습니다.'),
    });
  }
  return payload as SendReadinessResponse;
}

/** 직접 입력 비교 결과. / Manual comparison outcome. */
export interface ValidateResponse {
  /** 부합하면 true / true when conformant */
  conformant: boolean;
  /** 변수별로 읽어낸 값 / the value read per variable */
  variableValues: Record<string, string>;
  /** 불일치 지점 / the divergences */
  divergences: Divergence[];
  /** 템플릿 자체가 거절된 이유 / why the template itself was rejected */
  templateError: string | null;
}

/**
 * 직접 입력한 템플릿과 내용을 비교한다. / Compares a hand-entered template and content.
 *
 * req: FR-ATV-004, FR-ATV-006, FR-ATV-007, FR-ATV-008
 *
 * 레거시 검증 탭에 대응하지만 두 가지가 다르다. 첫째, 판정기가 서버에 하나뿐이므로 이
 * 결과와 등록 템플릿 검증 결과가 어긋날 수 없다(FR-ATV-007). 둘째, 레거시가 거절하던
 * 입력이 이제 통과한다 — `#{name}님 안녕` 과 `김님철수님 안녕` 은 일치한다(D-A6).
 *
 * Mirrors the legacy validation tab with two differences: there is one matcher on the server, so
 * this verdict and the registry-based verdict cannot disagree (FR-ATV-007); and inputs the legacy
 * rejected now pass — `#{name}님 안녕` matches `김님철수님 안녕` (D-A6).
 *
 * 템플릿 자체의 문제(${...} 사용, 인접 변수, 변수 수 초과)는 400 으로 오며 `templateError`
 * 에 담긴다 — 내용 불일치와 구분해서 보고해야 하기 때문이다.
 * A problem with the template itself arrives as 400 carrying `templateError`, reported distinctly
 * from a content mismatch.
 */
export async function validateTemplate(request: {
  template: string;
  content: string;
}): Promise<ValidateResponse> {
  const response = await fetch('/api/admin/alimtalk/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(request),
  });
  const payload = await response.json().catch(() => ({}));

  // 400 은 "템플릿이 잘못되었다" 는 <b>결과</b>이지 통신 실패가 아니다. 예외로 만들면
  // 화면이 그 이유를 보여줄 수 없다.
  // A 400 here is a <b>result</b> ("the template is malformed"), not a transport failure. Throwing
  // would leave the screen unable to show the reason.
  if (response.status === 400) {
    return payload as ValidateResponse;
  }
  if (!response.ok) {
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '알림톡 검증 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? '템플릿을 검증할 수 없습니다.'),
    });
  }
  return payload as ValidateResponse;
}

/** 버튼 입력. / A button input. */
export interface ButtonInput {
  name: string;
  type: string;
  urlMobile: string;
  urlPc: string;
  schemeIos: string;
  schemeAndroid: string;
}

/** 작성 결과. / A composition result. */
export interface ComposeResponse {
  /** 계약 적합 payload JSON / the contract-conforming payload JSON */
  payload: string | null;
  /** 발견된 문제 / the problems found */
  problems: string[];
}

/**
 * 작성 폼으로부터 payload 를 만든다. / Composes a payload from the form.
 *
 * req: FR-ATC-001, FR-ATC-002, FR-ATC-005, FR-ATC-009, FR-AZ-A05
 * source: biztalk_admin_61.js — generateBtn
 *
 * 레거시 "JSON 생성" 은 브라우저에서 객체를 손으로 쌓았고, 그래서 `failback` 을 잘못 쓰고
 * (D-A1) 계약에 없는 다섯 필드를 넣었으며(D-A2) `order` 를 빠뜨렸다(D-A3). 조립을 서버로
 * 옮기면 payload 가 `ContractConformanceTest` 가 지키는 타입을 지나가므로, 화면이 payload
 * 모양을 알 필요가 없어진다.
 *
 * The legacy built the object by hand in the browser, which is how it emitted the wrong fallback key,
 * five undeclared fields, and no order. Moving assembly to the server routes the payload through the
 * type the conformance test guards, so the screen no longer needs to know its shape.
 *
 * `senderKey` 를 보내지 않는다 — 이 요청 타입에 그 필드가 없다(FR-AZ-A05).
 * No `senderKey` is sent: the request type has no such field.
 */
export async function composePayload(request: {
  isCd: string;
  tranId: string;
  recipients: string;
  senderNumber: string;
  reqdate: string;
  templateCode: string;
  templateTitle: string;
  msg: string;
  buttons: ButtonInput[];
  failback: { type: string; subject: string; msg: string; imgId: string };
}): Promise<ComposeResponse> {
  return post<ComposeResponse>(
    '/api/admin/alimtalk/compose',
    request,
    'payload 를 만들 수 없습니다.',
  );
}

/** 다건 메시지 데이터 입력. / One batch message item input. */
export interface BatchItemInput {
  recipient: string;
  senderNumber: string;
  reqdate: string;
  templateCode: string;
  templateTitle: string;
  msg: string;
  buttons: ButtonInput[];
  failback: { type: string; subject: string; msg: string; imgId: string };
}

/**
 * 다건 작성 폼으로부터 payload 를 만든다. / Composes a batch payload from the form.
 *
 * req: FR-ATC-001, FR-ATC-004, FR-ATC-007
 * source: biztalk_admin_61.js — multi-panel msgData assembly
 *
 * 항목별 `order` 를 <b>보내지 않는다</b>. FR-ATC-004 는 순번을 시스템이 부여하도록 정하며,
 * 브라우저가 매긴 번호를 서버가 믿으면 레거시가 배열 위치에 의존하던 문제(D-A3)가 형태만
 * 바꿔 돌아온다.
 *
 * No per-item `order` is sent: FR-ATC-004 has the system assign it, and trusting a browser-chosen
 * number would return the legacy's array-position dependence (D-A3) in another shape.
 */
export async function composeBatchPayload(request: {
  isCd: string;
  tranId: string;
  items: BatchItemInput[];
}): Promise<ComposeResponse> {
  return post<ComposeResponse>(
    '/api/admin/alimtalk/compose/batch',
    request,
    '다건 payload 를 만들 수 없습니다.',
  );
}
