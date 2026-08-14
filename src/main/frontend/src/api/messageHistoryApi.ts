/**
 * 문자내역 API 클라이언트. / 문자내역 API client.
 *
 * req: FR-MSG-002, FR-MSG-004, FR-MSG-007, FR-MSGD-001
 * source: biztalk_admin_40.js — jex.createAjaxUtil('biztalk_admin_40_l001')
 *
 * <b>필드명이 실제 컬럼 의미를 반영한다.</b> 레거시 화면은 발신/수신 라벨이 컬럼과 반대로
 * 연결되어 있었고(D3), 서버는 PHONE 을 무시했으므로(D4) "수신번호"에 입력한 값이 실제로는
 * 발신번호를 필터링했다.
 *
 * Field names reflect the actual columns. In the legacy the sender/recipient labels were wired
 * inversely (D3) and the server ignored PHONE (D4), so a value typed into 수신번호 filtered the
 * sender column.
 */

import { AuthApiError } from './authApi';

/** 조회 조건. / Search criteria. */
export interface MessageHistoryQuery {
  /** 조회 시작 일시 (ISO local) / window start */
  from: string;
  /** 조회 종료 일시 (ISO local) / window end */
  to: string;
  /** 이용기관 코드 — 운영자만 유효 / 이용기관 code, operators only */
  institutionCode?: string;
  /** 메시지키 / message key */
  messageKey?: number;
  /** 발송번호 (컬럼 CALLBACK) / sender number, column CALLBACK */
  senderNumber?: string;
  /** 수신번호 (컬럼 PHONE) / recipient number, column PHONE */
  recipientNumber?: string;
  /** 상태 코드 / status code */
  status?: string;
  /** 유형 코드 AT/FT / type code */
  messageType?: string;
  /** 문자타입 SMS/MMS / table type */
  tableType?: string;
  /** 결과 코드 / result code */
  resultCode?: string;
  /** 페이지 번호 (0부터) / zero-based page */
  page?: number;
  /** 페이지 크기 / page size */
  size?: number;
}

/** 결과 1행 — 레거시 그리드 12컬럼. / One row, the legacy grid's twelve columns. */
export interface MessageHistoryRow {
  messageType: string;
  messageTypeLabel: string;
  tableType: string;
  messageKey: number;
  institutionCode: string;
  status: string;
  statusLabel: string;
  resultCode: string;
  senderNumber: string;
  recipientNumber: string;
  requestDate: string;
  requestTime: string;
  sentTime: string;
  reportTime: string;
}

/** 페이지 결과. / A page of results. */
export interface MessageHistoryPage {
  rows: MessageHistoryRow[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
}

/** 상세내역 19필드. / The 19 detail fields. */
export interface MessageDetail {
  resultCode: string | null;
  senderNumber: string | null;
  recipientNumber: string | null;
  requestDate: string | null;
  sentDate: string | null;
  resultDate: string | null;
  reportDate: string | null;
  message: string | null;
  profileKey: string | null;
  adFlag: string | null;
  templateCode: string | null;
  imagePath: string | null;
  imageUrl: string | null;
  wideImageFlag: string | null;
  buttonJson: string | null;
  failedType: string | null;
  failedSubject: string | null;
  failedImage: string | null;
  failedMessage: string | null;
}

/** 조회 조건 검증 실패 — 위반 목록을 보존한다. / Criteria validation failure. */
export class CriteriaError extends Error {
  readonly violations: string[];

  constructor(violations: string[]) {
    super(violations.join(' '));
    this.name = 'CriteriaError';
    this.violations = violations;
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    const violations = (payload as { violations?: string[] }).violations;
    if (violations && violations.length > 0) {
      throw new CriteriaError(violations);
    }
    throw new AuthApiError({
      code: (payload as { code?: string }).code ?? 'UNKNOWN',
      message: (payload as { message?: string }).message ?? '요청을 처리할 수 없습니다.',
    });
  }
  return payload as T;
}

/**
 * 문자내역을 조회한다. / Searches 문자내역.
 *
 * req: FR-MSG-002 — POST 를 쓰는 이유는 조회 조건에 전화번호가 포함될 수 있어서다.
 *      GET 이면 번호가 URL·접근 로그·브라우저 히스토리에 남는다(NFR-SEC-PII).
 */
export function searchMessageHistory(query: MessageHistoryQuery): Promise<MessageHistoryPage> {
  return post<MessageHistoryPage>('/api/message-history/search', query);
}

/**
 * 상세내역을 조회한다. / Looks up a detail record.
 *
 * req: FR-MSGD-001, FR-MSG-014
 * source: biztalk_admin_40.js — fn_getDetail() passes the raw 14-digit REQDATE
 */
export function fetchMessageDetail(row: MessageHistoryRow): Promise<MessageDetail> {
  return post<MessageDetail>('/api/message-history/detail', {
    messageType: row.messageType,
    tableType: row.tableType,
    messageKey: row.messageKey,
    // 그리드가 표시용으로 잘라낸 8자리가 아니라 원본 14자리를 보낸다.
    // The raw 14-digit value, not the 8-digit form the grid displays.
    requestDate: row.requestDate,
    status: row.status,
  });
}
