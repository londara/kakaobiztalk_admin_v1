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
