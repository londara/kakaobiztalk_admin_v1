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
