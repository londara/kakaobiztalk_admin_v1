/**
 * 이용기관 관리 API 클라이언트. / 이용기관 admin API client.
 *
 * req: FR-INST-001, FR-INST-002, FR-INST-003, FR-INST-006, FR-ATK-002, FR-AZ-I01
 * source: biztalk_admin_00.js — jex.createAjaxUtil('biztalk_admin_00_l001')
 *
 * <b>인증키는 서버에서 마스킹되어 도착한다.</b> 레거시는 전 기관의 인증키를 평문으로
 * 목록에 실어 보냈고 화면은 그대로 컬럼에 렌더링했다(D-I5). 클라이언트에는 마스킹된
 * 값만 존재하므로 화면이 실수로 전체 값을 노출할 방법이 없다.
 *
 * The 인증키 arrives already masked. The legacy shipped every institution's key in plaintext
 * and the screen rendered it into a column (D-I5). Only the masked value exists on the client,
 * so no screen can expose the full one by accident.
 */

import { AuthApiError } from './authApi';
import { csrfHeader } from './csrf';

/** 상태 필터. / Status filter. */
export type InstitutionStatusFilter = 'ALL' | 'Y' | 'N';

/** 조회 조건. / Search criteria. */
export interface InstitutionQuery {
  /** 기관명 검색어 / name fragment */
  name?: string;
  /** 상태 필터 / status filter */
  status?: InstitutionStatusFilter;
  /** 페이지 번호 (0부터) / zero-based page */
  page?: number;
  /** 페이지 크기 / page size */
  size?: number;
}

/** 결과 1행 — 레거시 그리드 8컬럼. / One row, the legacy grid's eight columns. */
export interface InstitutionRow {
  code: string;
  name: string | null;
  englishName: string | null;
  businessNumber: string | null;
  /** 마스킹된 인증키 — 평문은 클라이언트에 오지 않는다 / masked; the plaintext never reaches the client */
  authKeyMasked: string | null;
  status: string;
  statusLabel: string;
  description: string | null;
  /** YYYYMMDDHH24MISS */
  registeredAt: string | null;
  /** YYYYMMDDHH24MISS */
  lastModifiedAt: string | null;
}

/** 페이지 결과. / A page of results. */
export interface InstitutionPage {
  rows: InstitutionRow[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
}

/**
 * 이용기관을 조회한다. / Searches institutions.
 *
 * req: FR-INST-001, FR-INST-003
 *
 * 문자내역과 달리 `GET` 을 쓴다 — 조회 조건이 기관명과 상태뿐이고 개인정보를 담지 않으므로
 * URL 에 남아도 무방하다. 문자내역이 `POST` 인 이유(조건에 전화번호 포함)가 여기에는 없다.
 *
 * Unlike 문자내역 this uses `GET`: the criteria are only a name and a status, with no personal
 * data, so appearing in a URL is harmless. The reason 문자내역 uses `POST` — phone numbers in
 * the criteria — does not apply here.
 */
export async function searchInstitutions(query: InstitutionQuery): Promise<InstitutionPage> {
  const params = new URLSearchParams();
  if (query.name) {
    params.set('name', query.name);
  }
  if (query.status) {
    params.set('status', query.status);
  }
  params.set('page', String(query.page ?? 0));
  if (query.size !== undefined) {
    params.set('size', String(query.size));
  }

  const response = await fetch(`/api/admin/institutions/search?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    // 403 은 운영자 권한 없음을 뜻한다. 레거시의 '권한 없음' 은 브라우저 안의 alert 였고
    // 서버는 아무도 막지 않았다(D-I2) — 이제 서버가 거부하고 화면은 그 결과를 보여줄 뿐이다.
    // A 403 means the operator role is missing. The legacy's 권한 없음 was a browser alert while
    // the server refused nobody (D-I2); now the server refuses and the screen merely reports it.
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? String(response.status),
      message:
        response.status === 403
          ? '이용기관 관리 권한이 없습니다.'
          : ((payload as { message?: string }).message ?? '이용기관을 조회할 수 없습니다.'),
    });
  }

  return payload as InstitutionPage;
}

/** 수정 폼이 보내는 값. / What the edit form sends. */
export interface InstitutionUpdate {
  /** 기관명 / institution name */
  name: string;
  /** 영문명 / english name */
  englishName: string;
  /** 사업자등록번호 — 숫자 10자리 / business registration number, 10 digits */
  businessNumber: string;
  /** 사용여부 / status */
  status: 'Y' | 'N';
  /** 설명 / description */
  description: string;
}

/*
  이 타입에 **없는** 것이 계약이다.

  `code` 가 없다 — 대상은 경로가 정한다(FR-INSTC-002). `authKey` 가 없다 — 화면이 가진 인증키는
  마스킹된 값이므로(FR-INSTC-010), 그 문자열이 저장 요청에 실리면 별표가 그대로 자격증명이 되어
  고객사 연동이 즉시 끊긴다. 필드를 두지 않는 것이 그 사고를 표현 불가능하게 만드는 방법이다
  (TM-I022). 인증키 변경은 `rotateAuthKey` 만이 한다.

  The contract is what this type omits. No `code`: the path names the target (FR-INSTC-002). No
  `authKey`: the screen holds the key masked (FR-INSTC-010), so putting that string in a save
  request would make the asterisks the credential and cut off the customer at once. Omitting the
  field makes the accident unrepresentable (TM-I022); only `rotateAuthKey` changes a key.
*/

/** 필드 단위 검증 오류. / One field's validation failure. */
export interface FieldViolation {
  /** 계약상의 필드 이름 / the contract field name */
  field: string;
  /** 운영자에게 보일 메시지 / the operator-facing message */
  message: string;
}

/**
 * 서버 검증 실패 — 어느 칸이 잘못되었는지 담는다.
 * A server-side validation failure, carrying which field was wrong.
 *
 * req: FR-INSTC-003, D-I19
 *
 * 레거시의 모든 검증은 브라우저에 있었다(D-I19). 서버로 옮기면서 잃기 쉬운 것이 "어느 칸을
 * 고쳐야 하는가" 이며, 이 타입이 그것을 화면까지 옮긴다. 값은 담지 않는다 — 서버도 값을
 * 되돌려주지 않는다.
 *
 * Every legacy rule lived in the browser (D-I19). What is easily lost in moving them server-side is
 * *which box to fix*; this type carries that to the screen. It holds no values — the server does not
 * return them either.
 */
export class InstitutionValidationError extends Error {
  readonly violations: FieldViolation[];

  constructor(violations: FieldViolation[]) {
    super(violations[0]?.message ?? '입력값을 확인하세요.');
    this.name = 'InstitutionValidationError';
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
function toError(response: Response, payload: unknown, fallback: string): Error {
  const body = payload as { code?: string; message?: string; errors?: FieldViolation[] };

  // 검증 실패만 별도 타입이다. 나머지는 화면이 한 줄로 보여주는 것 외에 할 일이 없다.
  // Only a validation failure gets its own type; for the rest the screen can do nothing but show
  // one line.
  if (response.status === 400 && body.code === 'VALIDATION_FAILED' && body.errors?.length) {
    return new InstitutionValidationError(body.errors);
  }

  return new AuthApiError({
    code: body.code ?? String(response.status),
    message:
      response.status === 403
        ? '이용기관 관리 권한이 없습니다.'
        : response.status === 404
          ? '이용기관을 찾을 수 없습니다. 목록을 다시 조회하세요.'
          : (body.message ?? fallback),
  });
}

/**
 * 이용기관 한 건을 조회한다 — 수정 팝업이 여는 값.
 * Reads one institution, as opened by the edit popup.
 *
 * req: FR-INSTC-001, FR-INSTC-010, FR-ATK-002
 *
 * **인증키는 마스킹되어 도착한다.** 레거시 상세조회(`biztalk_admin_01_l002`)는 평문 인증키를
 * 반환했고 팝업은 그것을 입력칸에 넣었다 — 목록(D-I5)·중복검사(D-I3)에 이은 세 번째 노출
 * 경로이며 첫 분석에서 기록되지 않았다(D-I20). 평문이 클라이언트에 오지 않으므로 화면이
 * 실수로 노출할 방법이 없다.
 *
 * The 인증키 arrives masked. The legacy detail service returned it in plaintext and the popup put it
 * in a field — the third exposure path after the list (D-I5) and the duplicate check (D-I3), and
 * unrecorded by the first analysis (D-I20). The plaintext never reaches the client, so no screen can
 * expose it by accident.
 *
 * @param code 기관코드 / the institution code
 * @returns 이용기관 / the institution
 */
export async function fetchInstitution(code: string): Promise<InstitutionRow> {
  const response = await fetch(`/api/admin/institutions/${encodeURIComponent(code)}`, {
    method: 'GET',
    credentials: 'same-origin',
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toError(response, payload, '이용기관을 조회할 수 없습니다.');
  }

  return payload as InstitutionRow;
}

/**
 * 이용기관을 수정한다. / Updates an institution.
 *
 * req: FR-INSTC-002, FR-INSTC-003, FR-INSTC-004, FR-INSTC-011
 *
 * `PUT` 이며 등록으로 바뀌지 않는다. 레거시 서버는 등록과 수정을 하나의 UPSERT 로 처리해, 이미
 * 있는 기관코드로 등록을 호출하면 그 기관과 인증키까지 조용히 덮어썼다(D-I6). 대상이 없으면
 * 404 이며 새 행은 생기지 않는다.
 *
 * A `PUT` that cannot become a create. The legacy server served both from one upsert, so a create
 * with an existing code silently overwrote that institution and its credential (D-I6). A missing
 * target is a 404 and nothing is created.
 *
 * @param code 기관코드 / the institution code
 * @param body 수정 내용 / the changes
 * @returns 저장된 이용기관 — 서버가 다시 읽은 값 / the stored institution, as re-read by the server
 */
export async function updateInstitution(
  code: string,
  body: InstitutionUpdate,
): Promise<InstitutionRow> {
  const response = await fetch(`/api/admin/institutions/${encodeURIComponent(code)}`, {
    method: 'PUT',
    // CSRF 토큰을 헤더로 되돌려 보낸다(ADR-014). 상태를 바꾸는 요청에는 반드시 붙는다.
    // Echoes the CSRF token (ADR-014); every state-changing request carries it.
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toError(response, payload, '이용기관을 저장할 수 없습니다.');
  }

  return payload as InstitutionRow;
}

/**
 * 인증키를 재발급한다. / Rotates the 인증키.
 *
 * req: FR-ATK-001, FR-ATK-005, FR-INSTC-011
 *
 * 서버가 CSPRNG 로 만들고 **즉시 확정**한다 — 저장 버튼과 무관하다(PM 결정 AMB-I13). 레거시는
 * 브라우저에서 `Math.random()` 으로 만든 값을 폼에 담아 두었다가 저장 시 기록했으므로, 닫기를
 * 누르면 사라졌고 시도한 기록도 남지 않았다(D-I4).
 *
 * 반환되는 평문은 **한 번만** 주어진다. 저장하거나 다시 보여주지 않으며, 운영자가 고객사에
 * 전달하기 위한 값이다(FR-ATK-004).
 *
 * The server generates it with a CSPRNG and commits **at once**, independent of the save button (PM
 * ruling AMB-I13). The legacy generated it with `Math.random()` in the browser and held it in the
 * form until 저장, so 닫기 discarded it and left no record of the attempt (D-I4). The plaintext is
 * returned **once**, for the operator to pass to the customer; it is never stored or shown again.
 *
 * @param code 기관코드 / the institution code
 * @returns 새 인증키 / the newly issued key
 */
export async function rotateAuthKey(code: string): Promise<string> {
  const response = await fetch(
    `/api/admin/institutions/${encodeURIComponent(code)}/key/rotate`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...csrfHeader() },
      credentials: 'same-origin',
    },
  );

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw toError(response, payload, '인증키를 재발급할 수 없습니다.');
  }

  return (payload as { authKey: string }).authKey;
}

/**
 * `YYYYMMDDHH24MISS` 를 표시용 문자열로 바꾼다. / Formats a `YYYYMMDDHH24MISS` value.
 *
 * req: FR-INST-008
 *
 * 레거시 그리드는 `substring(0,8)` 로 날짜만 표시했다. 그 절단이 결함 D-I9 을 4년간
 * 가렸다 — 등록 SQL 의 `to_char(now(),'YYYYMMDD24MISS')` 는 `HH` 가 빠져 있어 시(時)
 * 자리에 리터럴 `24` 를 기록한다. 시각까지 표시하면 그런 값이 눈에 보인다.
 *
 * The legacy grid showed `substring(0,8)`, date only. That truncation hid defect D-I9 for four
 * years: the insert SQL's `to_char(now(),'YYYYMMDD24MISS')` omits `HH` and writes a literal `24`
 * where the hour belongs. Showing the time makes such values visible.
 *
 * 잘못된 값을 교정하지 않고 <b>그대로</b> 표시한다. 표시 단계에서 고치면 데이터의 문제가
 * 다시 숨는다.
 * Malformed values are shown <b>as they are</b>, not corrected: repairing them at render time
 * would hide the data problem again.
 */
export function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  if (value.length < 8) {
    return value;
  }
  const date = `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
  if (value.length < 14) {
    return date;
  }
  return `${date} ${value.slice(8, 10)}:${value.slice(10, 12)}:${value.slice(12, 14)}`;
}
