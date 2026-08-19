/**
 * 톡전송 내역 API 클라이언트. / 톡전송 내역 API client.
 *
 * req: FR-TLK-001, FR-TLK-003, FR-TLK-004, FR-TLK-005, FR-TLK-012, FR-TLK-013, FR-TLK-015,
 *      FR-AZ-T01, FR-AZ-T02
 * source: biztalk_admin_30.js — jex.createAjaxUtil('biztalk_admin_30_l001') / ('..._30_l003')
 *
 * <h2>필터 선택지를 별도 요청으로 받는 이유 / why the filter options are their own request</h2>
 * 레거시 `onload` 는 `_me.getDat()` 를 먼저 부르고 `fn_fintechSvcSel()` 을 나중에 불렀다 —
 * 즉 API 선택기가 채워지기 전에 조회가 나갔다(D-T28). 선택지를 명시적인 요청으로 두면 화면이
 * 그 완료를 기다릴 수 있고, 기다린다는 사실이 코드에 드러난다.
 *
 * The legacy `onload` called `_me.getDat()` first and `fn_fintechSvcSel()` afterwards, so the query
 * left before the API selector was populated (D-T28). Making the options an explicit request lets the
 * screen await them, and makes the waiting visible in the code.
 *
 * <h2>상태 라벨을 서버에서 받는 이유 / why status labels come from the server</h2>
 * 레거시는 필터 라디오를 코드 테이블에서 생성하고 그리드 라벨을 자바스크립트에 하드코딩했다.
 * 코드 테이블에 값이 하나 추가되면 <b>같은 릴리스에서</b> 필터는 가능해지고 컬럼은
 * `알수없음` 이 되었다(D-T29). 여기에는 라벨 표가 없다 — 서버가 한 출처에서 만든다.
 *
 * The legacy generated the filter radios from a code table and hardcoded the grid labels in
 * JavaScript, so adding one code made it filterable and rendered it as `알수없음` <b>in the same
 * release</b> (D-T29). There is no label table here: the server derives them from one source.
 *
 * <h2>`detailAvailable` 을 계산하지 않는 이유 / why `detailAvailable` is not computed here</h2>
 * 레거시 그리드가 `API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1` 로 링크를 걸었고 서버는 네 개의
 * 정확한 코드만 처리했다. 두 규칙이 어긋나 `ADV_KKO_AT_SEND2` 행은 링크는 걸리고 팝업은 비었다
 * (D-T13). 이 클라이언트는 서버가 보낸 값을 그대로 쓴다 — 브라우저는 의견을 갖지 않는다.
 *
 * The legacy grid linked on `API_SVC_CD.indexOf("KKO") != -1 && PRSU == 1` while the server handled
 * four exact codes; they disagreed, so an `ADV_KKO_AT_SEND2` row was linked and its popup empty
 * (D-T13). This client uses the server's value verbatim — the browser holds no opinion.
 */

/**
 * 톡전송 내역 요청 실패. / A failed 톡전송 내역 request.
 *
 * req: FR-TLK-014
 */
export class TalkHistoryApiError extends Error {
  /** HTTP 상태 / the HTTP status */
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'TalkHistoryApiError';
    this.status = status;
  }
}

/** 조회 조건. / The query criteria. */
export interface TalkHistoryQuery {
  /** 이용기관 코드. 비우면 전체 — 운영자 전용 화면이다 / the institution code; blank means all */
  institution?: string;
  /** 시작일자 YYYYMMDD / the start date */
  from: string;
  /** 종료일자 YYYYMMDD. 비우면 하루 조회 / the end date; blank means one day */
  to?: string;
  /** 시작시각 HHMM 또는 HHMMSS / the start time */
  fromTime?: string;
  /** 종료시각 HHMM 또는 HHMMSS / the end time */
  toTime?: string;
  /** 거래일련번호 / the transaction serial */
  serial?: string;
  /** 상태 코드 PRSU / the status code */
  status?: string;
  /** API 서비스 코드 / the API service code */
  apiService?: string;
  /** 0부터 시작하는 페이지 번호 / the zero-based page number */
  page?: number;
  /** 페이지 크기 / the page size */
  size?: number;
}

/** 거래내역 1행. / One transaction-history row. */
export interface TalkHistoryRow {
  /** 일자 YYYYMMDD / the transaction date */
  transactionDate: string;
  /** 기관코드 / the institution code */
  institutionCode: string;
  /**
   * 표시용 기관명. 미해석이면 서버가 코드와 표식을 함께 보낸다.
   * The display name; when unresolved the server sends the code plus a marker.
   *
   * 레거시는 행마다 상관 서브쿼리로 조회하고 일치하지 않으면 빈 칸을 그렸다 — 조회가 실패한
   * 사실이 어디에도 남지 않았다(D-T26).
   * The legacy resolved it with a per-row correlated subquery and drew a blank when unmatched,
   * recording nothing about the failed lookup (D-T26).
   */
  institutionName: string;
  /** 거래고유번호 / the transaction serial */
  transactionNo: string;
  /** API 서비스 코드 / the API service code */
  apiServiceCode: string;
  /** 상태 원값 / the raw status code */
  statusCode: string;
  /** 상태 표시 라벨 / the status display label */
  statusLabel: string;
  /** 응답코드 / the response code */
  responseCode: string | null;
  /** 등록시각 YYYYMMDDHHMMSS / when recorded */
  registeredAt: string;
  /** 완료시각 YYYYMMDDHHMMSS / when last changed */
  completedAt: string | null;
  /**
   * 상세 조회 가능 여부 — 서버가 계산한다.
   * Whether detail is available; the server computes it.
   *
   * 처리중·오류 행도 포함된다. 레거시는 `PRSU == 1` 인 행만 링크해, 실패를 조사하는 운영자가
   * 가장 필요로 하는 행에 링크가 없었다(FR-TLK-013).
   * 처리중 and 오류 rows are included. The legacy linked only `PRSU == 1`, so the rows an operator
   * investigating a failure most needs had no link (FR-TLK-013).
   */
  detailAvailable: boolean;
}

/** 한 페이지. / One page of results. */
export interface TalkHistoryPage {
  /** 이 페이지의 행 / the rows on this page */
  rows: TalkHistoryRow[];
  /**
   * 전체 건수.
   * The total matching count.
   *
   * 레거시는 페이지 정보를 서버에 넘기고도 건수를 되 읽지 않았고, 서비스 계약에도 그런 필드가
   * 없었다 — 그래서 페이저가 진짜 페이지 수를 알 수 없었다(D-T11).
   * The legacy passed page info to the server and never read the count back, and its contract
   * declared no such field, so the pager could not know the real page count (D-T11).
   */
  totalCount: number;
  /** 0부터 시작하는 페이지 번호 / the zero-based page number */
  page: number;
  /** 페이지 크기 / the page size */
  size: number;
  /** 전체 페이지 수 / the total page count */
  totalPages: number;
}

/** 선택기 항목. / A selector option. */
export interface TalkFilterOption {
  /** 코드 / the code */
  code: string;
  /** 표시명 / the display label */
  label: string;
}

/** 필터 선택지. / The filter options. */
export interface TalkFilterOptions {
  /**
   * API 선택기 항목 — BizTalk 로 분류된 서비스만.
   * The API selector's options: only services classified as BizTalk.
   *
   * 레거시 선택기는 등록된 모든 API 를 나열했고(`WHERE 1=1`), API 당 21개 컬럼을 받아
   * 그중 등록·수정 운영자의 ID 와 이름까지 브라우저로 보냈다(D-T27).
   * The legacy selector listed every registered API (`WHERE 1=1`) and received 21 columns per API,
   * shipping the ids and names of the operators who registered and edited it to the browser (D-T27).
   */
  apiServices: TalkFilterOption[];
  /** 상태 선택기 항목 / the status selector's options */
  statuses: TalkFilterOption[];
}

/**
 * 이 슬라이스의 API 접두사. / This slice's API prefix.
 *
 * T2 의 상세·내보내기 모듈이 같은 접두사와 같은 오류 읽기를 쓰도록 내보낸다 — 복제하면 한쪽만
 * 고쳐지는 상태가 생기고, 그것이 이 슬라이스가 서른네 건 중 열한 건으로 만난 결함 종류다.
 * Exported so T2's detail and export modules use the same prefix and the same error reading. Duplicating them
 * would create a state where only one gets fixed — the defect class this slice met in eleven of thirty-four.
 */
export const BASE = '/api/admin/biztalk/talk-history';

export async function readError(response: Response): Promise<string> {
  // 서버가 보낸 문구를 우선한다. 기간 상한과 시각 형식 위반은 사용자가 고칠 수 있는 것이므로
  // "요청이 실패했습니다" 로 덮어쓰면 고칠 방법을 숨긴다.
  // Prefer the server's message: a period-cap or time-format violation is something the user can fix,
  // and replacing it with "the request failed" hides how.
  try {
    const body = (await response.json()) as { message?: string };
    if (body?.message) {
      return body.message;
    }
  } catch {
    // 본문이 JSON 이 아니면 상태만 남는다. / A non-JSON body leaves only the status.
  }
  return `요청이 실패했습니다 (HTTP ${response.status}). / The request failed (HTTP ${response.status}).`;
}

export function toParams(query: TalkHistoryQuery): URLSearchParams {
  const params = new URLSearchParams();
  params.set('from', query.from);
  // 빈 문자열을 보내지 않는다. 서버는 "값 없음" 과 "빈 값" 을 구분하며, 레거시가 빈 값을
  // "전체" 로 읽은 것이 D-T2 의 절반이었다.
  // No empty strings are sent: the server distinguishes absent from blank, and reading a blank as
  // "all" was half of D-T2.
  const optional: Array<[string, string | number | undefined]> = [
    ['institution', query.institution],
    ['to', query.to],
    ['fromTime', query.fromTime],
    ['toTime', query.toTime],
    ['serial', query.serial],
    ['status', query.status],
    ['apiService', query.apiService],
    ['page', query.page],
    ['size', query.size],
  ];
  for (const [key, value] of optional) {
    if (value !== undefined && value !== null && `${value}` !== '') {
      params.set(key, `${value}`);
    }
  }
  return params;
}

/**
 * 톡전송 거래내역을 조회한다. / Queries the 톡전송 transaction history.
 *
 * @param query 조회 조건 / the criteria
 * @returns 한 페이지 / one page
 * @throws TalkHistoryApiError 요청 실패 / on failure
 *
 * req: FR-TLK-001, FR-TLK-005, FR-TLK-013
 */
export async function searchTalkHistory(query: TalkHistoryQuery): Promise<TalkHistoryPage> {
  const response = await fetch(`${BASE}?${toParams(query).toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new TalkHistoryApiError(await readError(response), response.status);
  }
  return (await response.json()) as TalkHistoryPage;
}

/**
 * 필터 선택지를 조회한다. / Fetches the filter options.
 *
 * @returns 선택지 / the options
 * @throws TalkHistoryApiError 요청 실패 / on failure
 *
 * req: FR-TLK-002, FR-TLK-004, FR-TLK-012, FR-TLK-015
 */
export async function fetchTalkFilterOptions(): Promise<TalkFilterOptions> {
  const response = await fetch(`${BASE}/filters`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new TalkHistoryApiError(await readError(response), response.status);
  }
  return (await response.json()) as TalkFilterOptions;
}
