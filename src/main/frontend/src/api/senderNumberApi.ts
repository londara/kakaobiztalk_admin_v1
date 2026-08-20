/**
 * 발신번호 관리 API 클라이언트. / Sender-number admin API client.
 *
 * req: FR-SND-001, FR-SND-003, FR-SND-005, FR-SND-006, FR-SND-007, FR-AZ-D01
 * source: biztalk_admin_10.js — jex.createAjaxUtil('biztalk_admin_10_l001')
 *
 * <h2>`ref` 를 쓰고 `number` 를 쓰지 않는 이유 / why `ref` and never `number`</h2>
 * 행에 대한 후속 동작(상세·수정·삭제)은 반드시 `ref` 를 보낸다. 표시되는 `number` 를
 * 식별자로 쓰면 D-S1 이 재발한다 — 레거시 그리드는 목록이 준 값을 그대로 삭제 요청에
 * 실었고, 2025-10 에 그 값이 마스킹되기 시작하자 삭제가 아무 행도 지우지 못한 채
 * "정상적으로 처리되었습니다" 를 보고했다.
 *
 * Any follow-up action on a row sends `ref`, never the displayed `number`. Using the displayed
 * value as an identifier is what caused D-S1: the legacy grid passed whatever the list gave it
 * into the delete request, and when that value became masked in 2025-10 the delete matched
 * nothing while still reporting success.
 *
 * `ref` 는 서버가 발급하는 불투명 토큰이며 <b>인가 수단이 아니다</b>. 조작된 토큰으로 다른
 * 기관의 번호를 지목해도 서버가 세션 권한으로 범위를 다시 판정한다(FR-AZ-D03).
 * `ref` is a server-issued opaque token and <b>not an authorization mechanism</b>: a tampered
 * token is still rejected by the server's own scope check (FR-AZ-D03).
 */

import { AuthApiError } from './authApi';
import { csrfHeader } from './csrf';

/** 조회 조건. / Search criteria. */
export interface SenderNumberQuery {
  /** 이용기관 코드 / the institution code */
  institution?: string;
  /** 페이지 번호 (0부터) / zero-based page */
  page?: number;
  /** 페이지 크기 / page size */
  size?: number;
}

/** 결과 1행 — 레거시 그리드 7컬럼. / One row, the legacy grid's seven columns. */
export interface SenderNumberRow {
  /**
   * 행 식별자 — 후속 동작에 이 값을 쓴다. 표시하지 않는다.
   * Row identifier; used for follow-up actions and never displayed.
   */
  ref: string;
  institutionName: string | null;
  /**
   * 발신번호 — 전체 표시. PM 결정 AMB-S04 에 따라 마스킹하지 않으며, 노출 통제는
   * 서버의 조회 감사로 한다(FR-SND-011).
   * The sender number, in full: ruling AMB-S04 removed masking and replaced it with
   * server-side read auditing (FR-SND-011).
   */
  number: string;
  /** 마스킹된 등록자명 / registering operator, masked */
  registeredBy: string | null;
  /** YYYYMMDDHH24MISS */
  registeredAt: string | null;
  /** 마스킹된 수정자명 / last editor, masked */
  updatedBy: string | null;
  /** YYYYMMDDHH24MISS */
  updatedAt: string | null;
  description: string | null;
}

/** 페이지 결과. / A page of results. */
export interface SenderNumberPage {
  rows: SenderNumberRow[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
}

/**
 * 이용기관의 발신번호를 조회한다. / Lists an institution's sender numbers.
 *
 * req: FR-SND-001, FR-SND-002, FR-SND-003
 *
 * 이용기관을 고르지 않았으면 <b>요청하지 않는다</b>. 레거시는 `onload` 에서 목록 조회를
 * 먼저 부르고 그 뒤에 콤보를 채웠기 때문에 페이지 로드마다 빈 `IS_CD` 로 한 번씩
 * 조회했다(D-S19). 서버도 같은 판단을 하지만, 보내지 않는 쪽이 더 분명하다.
 *
 * No request is issued when no institution is chosen. The legacy called the list query in
 * `onload` before populating the combo, so every page load issued one query with a blank
 * `IS_CD` (D-S19). The server makes the same judgement, but not sending is clearer.
 */
export async function listSenderNumbers(query: SenderNumberQuery): Promise<SenderNumberPage> {
  if (!query.institution || query.institution.trim() === '') {
    return { rows: [], totalCount: 0, page: 0, size: 0, totalPages: 0 };
  }

  const params = new URLSearchParams();
  params.set('institution', query.institution);
  params.set('page', String(query.page ?? 0));
  if (query.size !== undefined) {
    params.set('size', String(query.size));
  }

  const response = await fetch(`/api/admin/sender-numbers?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    // 레거시의 '권한 없음' 은 브라우저 안의 alert 였고 서버는 아무도 막지 않았다(D-S2).
    // 이제 서버가 거부하고 화면은 그 결과를 보고할 뿐이다.
    // The legacy's 권한 없음 was a browser alert while the server refused nobody (D-S2).
    // Now the server refuses and the screen merely reports it.
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '발신번호 관리 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? '발신번호를 조회할 수 없습니다.'),
    });
  }

  return payload as SenderNumberPage;
}

/* ==========================================================================
   쓰기 경로 / the write path — Sprint S2a
   ========================================================================== */

/** 필드 단위 실패. / One field's failure. */
export interface SenderNumberViolation {
  /** 계약상의 필드 이름 / the contract field name */
  field: string;
  /** 운영자에게 보일 메시지 / the operator-facing message */
  message: string;
}

/**
 * 서버가 어느 칸을 왜 거절했는지 담는 오류.
 * An error carrying which field the server refused and why.
 *
 * req: FR-SNDC-003, FR-SNDC-014, NFR-USE-D02
 *
 * 레거시는 규칙과 무관하게 `등록중 오류 발생.` 한 문장을 띄웠고, 게다가 클라이언트 검증이
 * 존재하지 않는 요소를 검사했으므로 그 문장조차 거의 나오지 않았다(D-S11). 서버로 규칙을 옮기면서
 * 잃기 쉬운 것이 "어느 칸을 고쳐야 하는가" 이며, 이 타입이 그것을 화면까지 옮긴다.
 *
 * 400(검증)과 409(중복·대상없음)를 **같은 타입**으로 다룬다. 화면이 해야 할 일은 셋 다 같다 —
 * 해당 칸 옆에 문장을 보여 주고 폼을 열어 둔다(FR-SNDC-014). 상태 코드는 `code` 로 구분된다.
 *
 * The legacy showed one sentence regardless of the rule and, since its client validation tested
 * non-existent elements, rarely even that (D-S11). What is easily lost in moving rules server-side is
 * *which box to fix*; this type carries that to the screen. A 400 (validation) and both 409s
 * (duplicate, no live row) share this type because the screen's job is identical in all three: show
 * the sentence beside the field and keep the form open (FR-SNDC-014).
 */
export class SenderNumberWriteError extends Error {
  readonly violations: SenderNumberViolation[];
  /** 서버가 준 구분 코드 — `VALIDATION_FAILED` / `DUPLICATE` / `NOT_LIVE` */
  readonly code: string;

  constructor(code: string, violations: SenderNumberViolation[]) {
    super(violations[0]?.message ?? '요청을 처리할 수 없습니다.');
    this.name = 'SenderNumberWriteError';
    this.code = code;
    this.violations = violations;
  }
}

/**
 * 실패 응답을 오류로 바꾼다. / Turns a failure response into an error.
 *
 * @param response 응답 / the response
 * @param payload  본문 / the parsed body
 * @param fallback 기본 메시지 / the fallback message
 * @returns 던질 오류 / the error to throw
 */
function toWriteError(response: Response, payload: unknown, fallback: string): Error {
  const body = payload as { code?: string; message?: string; errors?: SenderNumberViolation[] };

  // `errors[]` 가 있으면 필드 단위 오류다 — 400 이든 409 든 같다.
  // An `errors[]` makes it a field-level failure, whether the status is 400 or 409.
  if (body.errors?.length) {
    return new SenderNumberWriteError(body.code ?? String(response.status), body.errors);
  }

  return new AuthApiError({
    code: body.code ?? String(response.status),
    message:
      response.status === 403
        ? '발신번호 관리 권한이 없습니다.'
        : (body.message ?? fallback),
  });
}

/** 등록 화면이 여는 이용기관 문맥. / The institution context the register form opens with. */
export interface SenderNumberContext {
  institution: string;
  institutionName: string | null;
}

/**
 * 등록 화면의 이용기관 문맥을 가져온다. / Loads the register form's institution context.
 *
 * req: FR-SNDC-002, FR-SNDC-012, NFR-SEC-PII-D02
 *
 * **기관코드와 기관명 둘만 온다.** 레거시 등록 팝업은 이름을 채우려고 이용기관 *상세조회*
 * (`biztalk_admin_01_l002`)를 호출했고, 그 서비스는 기관 레코드 전체를 **평문 인증키와 함께**
 * 반환했다(D-S18). 이름 하나가 필요한 화면이 살아 있는 자격증명을 브라우저 DOM 으로 끌어온 것이다.
 *
 * Only the code and the name arrive. The legacy popup called the institution *detail* service to fill
 * in a name, and that service returned the whole record **including the plaintext 인증키** (D-S18): a
 * screen needing one name pulled a live credential into the browser DOM.
 *
 * @param institution 이용기관 코드 / the institution code
 * @returns 이용기관 문맥 / the institution context
 */
export async function fetchSenderNumberContext(institution: string): Promise<SenderNumberContext> {
  const params = new URLSearchParams({ institution });
  const response = await fetch(`/api/admin/sender-numbers/context?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toWriteError(response, payload, '이용기관 정보를 조회할 수 없습니다.');
  }

  return payload as SenderNumberContext;
}

/** 등록 입력. / The registration input. */
export interface SenderNumberRegistration {
  /** 발신번호 / the sender number */
  number: string;
  /** 설명 / the description */
  description: string;
  /** 사유 — 필수(PM 결정 AMB-S10) / the reason, mandatory per PM ruling AMB-S10 */
  reason: string;
}

/*
  이 타입에 **없는** 것이 계약이다.

  `institution` 이 없다 — 대상 기관은 목록 화면이 고른 값이며 질의 문자열로 가고, 서버는 그것을
  신뢰하지 않고 세션 권한으로 다시 판정한다(FR-SNDC-012, FR-AZ-D03). `authNo`(인증번호)도 없다 —
  소유 인증은 구현하지 않으며(AMB-S01), 레거시처럼 선언만 남은 입력은 있는 통제로 오해된다(D-S4).

  The contract is what this type omits. No `institution`: it travels in the query string and the
  server re-decides it from session entitlements. No `authNo`: ownership verification is not
  implemented (AMB-S01), and a declared-but-dead input reads as a control that exists (D-S4).
*/

/** 쓰기 결과. / The outcome of a write. */
export interface SenderNumberWriteResult {
  /** 실제로 바뀐 행 수 — 언제나 1 이상 / rows actually changed; always at least 1 */
  affected: number;
  /** 등록된 행의 식별자 — 삭제에서는 null / the new row's identifier; null on delete */
  ref: string | null;
}

/**
 * 발신번호를 등록한다. / Registers a sender number.
 *
 * req: FR-SNDC-001, FR-SNDC-011, FR-SNDC-012, FR-SNDC-004
 *
 * 검증 규칙을 여기에 **복제하지 않는다**. 자릿수·접두어·특수번호·길이 판정은 전부 서버가 하며
 * (FR-SNDC-003) 화면은 그 응답을 표시한다. 레거시가 그 반대였다: 규칙은 브라우저에만 있었고,
 * 게다가 존재하지 않는 요소(`#ATK`, `#BRNO`, `#IS_ENGNM`)를 검사해 `undefined == ""` 가 거짓이므로
 * **모든 검사가 통과**했다(D-S11).
 *
 * No rule is duplicated here. Digits, prefix, barred numbers and lengths are all decided by the
 * server (FR-SNDC-003) and the screen displays the answer. The legacy was the reverse: the rules lived
 * only in the browser and tested elements that did not exist, so — `undefined == ""` being false —
 * every check passed (D-S11).
 *
 * @param institution  이용기관 코드 / the institution code
 * @param registration 등록 내용 / the registration
 * @returns 등록 결과 / the outcome
 */
export async function registerSenderNumber(
  institution: string,
  registration: SenderNumberRegistration,
): Promise<SenderNumberWriteResult> {
  const params = new URLSearchParams({ institution });
  const response = await fetch(`/api/admin/sender-numbers?${params.toString()}`, {
    method: 'POST',
    // CSRF 토큰을 헤더로 되돌려 보낸다(ADR-014). 상태를 바꾸는 요청에는 반드시 붙는다.
    // Echoes the CSRF token (ADR-014); every state-changing request carries it.
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    credentials: 'same-origin',
    body: JSON.stringify(registration),
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toWriteError(response, payload, '발신번호를 등록할 수 없습니다.');
  }

  return payload as SenderNumberWriteResult;
}

/**
 * 선택한 발신번호를 삭제한다. / Deletes the selected sender numbers.
 *
 * req: FR-SNDD-002, FR-SNDD-004, FR-SNDD-006, FR-SNDD-009, FR-SND-007
 *
 * **`refs` 를 보내고 `number` 를 보내지 않는다.** 레거시는 그리드가 가진 값을 콤마로 이어 하나의
 * `DP_NO` 로 보냈고, 2025-10 에 목록이 그 값을 마스킹하기 시작하자 `decrypt(DP_NO) = '01********8'`
 * 이 되어 **0건**이 지워졌다. 0건 삭제는 SQL 오류가 아니므로 예외도 없이 이력이 쓰이고
 * `정상적으로 처리되었습니다` 가 표시되었다(D-S1).
 *
 * 서버는 하나라도 살아 있는 행을 찾지 못하면 **409 로 거절하고 전체를 되돌린다**(FR-SNDD-002,
 * FR-SNDD-005). 즉 이 함수가 정상 반환하면 `affected` 는 언제나 1 이상이다 — 0건 성공은 표현
 * 불가능하다.
 *
 * Sends `refs`, never `number`. The legacy joined the grid's values with commas into one `DP_NO`, and
 * once the list began masking that value in 2025-10 the predicate matched nothing — which, a zero-row
 * `DELETE` not being an error, still wrote history and displayed success (D-S1). The server now
 * refuses with a 409 and rolls the whole request back if any target has no live row, so a normal
 * return always means `affected >= 1`: a zero-row success is unrepresentable.
 *
 * @param refs   삭제할 행 식별자 / the row identifiers to delete
 * @param reason 사유 — 필수 / the reason, mandatory
 * @returns 삭제 결과 / the outcome
 */
export async function deleteSenderNumbers(
  refs: string[],
  reason: string,
): Promise<SenderNumberWriteResult> {
  const response = await fetch('/api/admin/sender-numbers/delete', {
    // POST 인 이유: 본문이 필요하다. 대상 목록과 사유가 함께 와야 하고, 대상을 URL 에 넣으면
    // 발신번호가 주소창과 접근 로그에 남는다(NFR-SEC-LOG-D01 의 정신).
    // A POST because a body is required: the target set and the reason travel together, and putting
    // the targets in the URL would leave sender numbers in address bars and access logs.
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    credentials: 'same-origin',
    body: JSON.stringify({ refs, reason }),
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toWriteError(response, payload, '발신번호를 삭제할 수 없습니다.');
  }

  return payload as SenderNumberWriteResult;
}
