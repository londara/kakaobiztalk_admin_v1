/**
 * 이용기관 보고서 API 클라이언트. / Institution usage report API client.
 *
 * req: FR-RPT-001, FR-RPT-005, FR-RPT-007, FR-RPT-008, FR-RPT-009, FR-RPT-013, FR-RPTS-002,
 *      FR-RPTS-005, FR-AZ-R01
 * source: biztalk_admin_20.js — jex.createAjaxUtil('biztalk_admin_20_l001')
 *
 * <h2>응답을 파싱하지 않는다 / nothing here parses a response</h2>
 * 레거시 클라이언트는 `dat.REC2 === undefined` 로 분기한 뒤, 정의되어 있으면
 * `JSON.parse(dat.REC2)` 를 호출했다. 그 문자열은 서버가 Java `Arrays.toString()` 으로 만든
 * 것이었으므로 기관명에 따옴표나 역슬래시가 하나만 들어가도 화면이 통째로 깨졌다. 게다가
 * 어느 분기를 타는지는 서버의 환경 설정(`TSTCL_DV`)이 정했다 — 즉 <b>운영과 그 외 환경의
 * 응답 모양이 달랐다</b>(D-R5, D-R6).
 *
 * The legacy client branched on `dat.REC2 === undefined` and, when defined, called
 * `JSON.parse(dat.REC2)` on a string the server had produced with Java's `Arrays.toString()`.
 * One quote or backslash in an institution name broke the whole screen — and which branch ran was
 * decided by the server's environment setting (`TSTCL_DV`), so <b>production and every other
 * environment returned different shapes</b> (D-R5, D-R6).
 *
 * 여기에는 분기가 없고, 파싱해야 하는 필드도 없다.
 * There is no branch here and no field to parse.
 *
 * <h2>`seek` 를 쓰고 페이지 번호를 쓰지 않는 이유 / why `seek` and not a page number</h2>
 * 결과는 두 데이터베이스에 걸쳐 있어 offset 으로 자를 수 없다 — 어느 쪽도 상대 쪽에 몇 행이
 * 앞서는지 모른다. 대신 각 출처가 자기 인덱스로 독립 적용할 수 있는 이어보기 키를 쓴다
 * (ADR-RPT-021).
 * The result spans two databases and cannot be cut by offset, because neither side knows how many
 * rows precede it on the other. A seek key each source applies with its own index is used instead
 * (ADR-RPT-021).
 */

/**
 * 보고서 조회 실패. / A failed report request.
 *
 * `AuthApiError` 를 빌려 쓰지 않는다. 그쪽 생성자는 `{ message, code, violations }` 형태의
 * 인증 오류 본문을 받으므로, 문자열을 넘기면 `message` 가 `undefined` 가 되어 화면에
 * <b>빈 오류 문구</b>가 나온다 — 사용자에게는 "아무 일도 없었다" 로 보이고, 그것이 D-R16 이
 * 만들어 낸 바로 그 경험이다.
 * `AuthApiError` is not borrowed here: its constructor takes an auth error body shaped
 * `{ message, code, violations }`, so passing a string leaves `message` undefined and the screen
 * shows an <b>empty error</b> — which reads to the user as "nothing happened", precisely the
 * experience D-R16 produced.
 *
 * req: FR-RPT-014, FR-RPTX-011
 */
export class ReportApiError extends Error {
  /** HTTP 상태 / the HTTP status */
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ReportApiError';
    this.status = status;
  }
}

/** 발송 구분. / The send-source filter. */
export type SendSource = 'API' | 'BULK' | 'ALL';

/** 조회 조건. / The query criteria. */
export interface ReportQuery {
  /** 이용기관 코드. 운영자만 유효하며 비우면 전체 / the institution code; operators only, blank means all */
  institution?: string;
  /** 발송 구분 / the send-source filter */
  source?: SendSource;
  /** 시작일자 YYYYMMDD / the start date */
  from: string;
  /** 종료일자 YYYYMMDD / the end date */
  to: string;
  /** 이어보기 일자 / the seek date */
  seekDate?: string;
  /** 이어보기 기관코드 / the seek institution code */
  seekInstitution?: string;
  /** 페이지 크기 / the page size */
  size?: number;
}

/** 한 채널의 네 건수. / One channel's four counters. */
export interface Counters {
  /** 전체 / total */
  total: number;
  /** 성공 / success */
  success: number;
  /** 실패 / failed */
  failed: number;
  /**
   * 처리중 / in flight.
   *
   * 레거시는 이 값을 조회하고 계약에 선언까지 했으면서 화면과 엑셀 어디에도 싣지 않았다.
   * 그래서 전체 ≠ 성공 + 실패 였고, 숫자를 더해 본 사람에게 보고서는 틀려 보였다(D-R14).
   * The legacy queried this and declared it on the contract, then displayed it nowhere — so
   * 전체 ≠ 성공 + 실패, and to anyone who added the columns up the report looked wrong (D-R14).
   */
  inFlight: number;
}

/** 채널 컬럼 정의. / A channel column definition. */
export interface ReportColumn {
  /** 채널 식별자 / the channel key */
  key: string;
  /** 표시명 / the display label */
  label: string;
}

/** 보고서 1행. / One report row. */
export interface ReportRow {
  /** 발송 구분 표시명 / the source label */
  source: string;
  /** 일자 YYYYMMDD / the trade date */
  tradeDate: string;
  /** 기관코드 / the institution code */
  institutionCode: string;
  /**
   * 기관명. null 이면 해결되지 않은 것이며 화면은 코드와 미해결 표시를 그린다.
   * The institution name; null means unresolved and the screen draws the code plus a marker.
   * 레거시는 이 경우 빈칸을 그렸고 조회가 실패한 사실은 어디에도 남지 않았다(D-R12).
   * The legacy drew a blank cell and recorded nothing about the failed lookup (D-R12).
   */
  institutionName: string | null;
  /** 채널별 네 건수 / four counters per channel */
  counters: Record<string, Counters>;
  /** 총 건수 / the grand total */
  grandTotal: number;
  /**
   * 산술 항등식(전체 = 성공 + 실패 + 처리중) 성립 여부.
   * Whether the arithmetic identity holds.
   * 거짓이면 그 행은 사실로 표시되어서는 안 된다 — 집계를 만든 배치가 실패를 삼키고 성공을
   * 보고하기 때문이다(FR-RPT-010, D-R27).
   * A false value means the row must not be presented as fact, because the batch that produced
   * the aggregate swallows failures while reporting success (FR-RPT-010, D-R27).
   */
  reconciles: boolean;
}

/** 이어보기 키. / The seek key. */
export interface ReportSeek {
  tradeDate: string;
  institutionCode: string;
}

/**
 * 집계 기준일. / The aggregation watermark.
 *
 * 이 값이 화면에 보이는 이유는 D-R25 다. 화면의 기본 조회 범위는 오늘이었고 집계 배치의 기본
 * 실행은 4일 전 하루만 처리했으므로, 사용자가 화면을 열자마자 마주친 것은 "데이터 없음"이
 * 아니라 <b>미집계</b>였다.
 * This is on screen because of D-R25: the screen's default range was today while the batch's
 * default run covered a single day four days back, so what a user met on opening it was not
 * "no data" but <b>not yet aggregated</b>.
 */
export interface ReportWatermark {
  /** API 집계 기준일 ISO / the API watermark */
  apiAsOf: string | null;
  /** 대량 집계 기준일 ISO / the bulk watermark */
  bulkAsOf: string | null;
  /** 요청 구분에 적용되는 기준일 / the watermark that applies to the request */
  effectiveAsOf: string | null;
}

/** 한 페이지. / One page. */
export interface ReportPage {
  rows: ReportRow[];
  columns: ReportColumn[];
  /** 다음 페이지 요청에 그대로 실어 보낸다 / send this back for the next page */
  nextSeek: ReportSeek | null;
  /** 전체 건수. 상한 초과 시 null / the exact total, null above the probe ceiling */
  totalCount: number | null;
  hasMore: boolean;
  watermark: ReportWatermark;
  /**
   * 부분 결과 안내. 비어 있지 않으면 표시된 수치가 불완전하다.
   * Partial-result notes; a non-empty list means the figures shown are incomplete.
   * 조용한 부분 보고는 이 프로그램이 네 슬라이스 연속으로 만난 실패 방식이므로, 화면은 이
   * 안내를 반드시 눈에 띄게 보여준다(FR-RPTS-005).
   * Silent partial reporting is the failure mode this programme has met four times, so the screen
   * always shows these prominently (FR-RPTS-005).
   */
  incompleteNotes: string[];
}

const BASE = '/api/admin/reports/usage';

function toSearchParams(query: ReportQuery): string {
  const params = new URLSearchParams();
  params.set('from', query.from);
  params.set('to', query.to);
  if (query.institution) params.set('institution', query.institution);
  if (query.source) params.set('source', query.source);
  if (query.seekDate) params.set('seekDate', query.seekDate);
  if (query.seekInstitution) params.set('seekInstitution', query.seekInstitution);
  if (query.size != null) params.set('size', String(query.size));
  return params.toString();
}

async function readOrThrow<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }
  // 서버가 사용자에게 보여도 되는 설명을 담아 보낸다 — 기간 상한 초과나 형식 오류가
  // 여기에 해당한다. 담기지 않았다면 상태 코드만 알린다.
  // The server sends a user-safe explanation for things like an over-long period or a malformed
  // date; where it does not, only the status is reported.
  let message = `보고서를 조회하지 못했습니다 (HTTP ${response.status})`;
  try {
    const body = (await response.json()) as { message?: string; detail?: string };
    message = body.message ?? body.detail ?? message;
  } catch {
    // 본문이 JSON 이 아니면 상태 코드 메시지를 그대로 쓴다.
    // A non-JSON body leaves the status message in place.
  }
  throw new ReportApiError(message, response.status);
}

/**
 * 보고서 한 페이지를 조회한다. / Reads one page of the report.
 *
 * req: FR-RPT-001, FR-RPT-005, FR-RPT-007
 */
export async function fetchReport(query: ReportQuery): Promise<ReportPage> {
  const response = await fetch(`${BASE}?${toSearchParams(query)}`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  return readOrThrow<ReportPage>(response);
}

/**
 * 집계 기준일만 조회한다. / Reads the aggregation watermark alone.
 *
 * 화면은 조회 전에 이 값을 먼저 보여준다. 사용자가 기간을 고르기 전에 데이터가 어디까지
 * 있는지 알아야, 오늘을 조회하고 빈 화면을 마주하는 일이 없다(D-R25).
 * The screen shows this before any query: knowing how far the data reaches before choosing a
 * period is what stops a user querying today and meeting an empty grid (D-R25).
 *
 * req: FR-RPT-013, FR-RPT-015, NFR-USE-R01
 */
export async function fetchReportWatermark(source: SendSource = 'ALL'): Promise<ReportWatermark> {
  const response = await fetch(`${BASE}/watermark?source=${source}`, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  return readOrThrow<ReportWatermark>(response);
}
