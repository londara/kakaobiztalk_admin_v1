/**
 * 톡전송 상세·내보내기 API 클라이언트 — 스프린트 T2.
 * 톡전송 detail and export API client — Sprint T2.
 *
 * req: FR-AZ-T04, FR-TLKD-001, FR-TLKD-002, FR-TLKD-005, FR-TLKD-007, FR-TLKM-001, FR-TLKM-007,
 *      FR-TLKX-001, FR-TLKX-003, FR-TLKX-004, FR-TLKX-006
 * source: biztalk_admin_32.js, biztalk_admin_31.js, biztalk_admin_30.js — fn_makeExcel()
 *
 * `BASE`, `readError`, `toParams` 를 목록 모듈에서 가져온다. 복제하면 한쪽만 고쳐지는 상태가
 * 생기고, 그것이 이 슬라이스가 서른네 건 중 열한 건으로 만난 결함 종류다.
 *
 * `BASE`, `readError` and `toParams` are imported from the list module. Duplicating them would create a state
 * where only one gets fixed — the defect class this slice met in eleven of thirty-four.
 */

import {
  BASE,
  TalkHistoryApiError,
  readError,
  toParams,
  type TalkHistoryQuery,
} from './talkHistoryApi';

/** 결과 구분. / The result classification. */
export type TalkOutcome = 'SUCCESS' | 'FAILURE' | 'PENDING';

/** 거래 상세내역 조회 조건. / The transaction-detail criteria. */
export interface TalkMessageQuery {
  /** 거래일자 YYYYMMDD / the transaction date */
  transactionDate: string;
  /** 거래고유번호 / the transaction serial */
  serial: string;
  /** 수신번호 부분 일치 / the recipient substring */
  recipient?: string;
  /** 상태 코드 / the status code */
  status?: string;
  /** 톡결과 구분 / the talk-result filter */
  talkResult?: TalkOutcome;
  /** 문자결과 구분 / the SMS-result filter */
  smsResult?: TalkOutcome;
  /** 페이지 번호 / the page number */
  page?: number;
  /** 페이지 크기 / the page size */
  size?: number;
}

/**
 * 거래 상세내역 1행. / One transaction-detail row.
 *
 * `senderMasked` / `recipientMasked` 라는 이름은 의도다. 레거시 질의는 `decrypt()` 만 적용하고
 * `masking()` 을 적용하지 않아 평문 번호를 브라우저로 보냈다(D-T6). 필드 이름이 마스킹을
 * 주장하면, 마스킹되지 않은 값을 담는 변경은 이름과 모순되어 리뷰에서 보인다.
 *
 * The names `senderMasked` / `recipientMasked` are deliberate: the legacy queries applied `decrypt()` without
 * `masking()` and sent plaintext numbers to the browser (D-T6). A field name that claims masking makes a change
 * shipping an unmasked value contradict its own name, which a review can see.
 */
export interface TalkMessageRow {
  /** 채널 코드 — 서버가 레지스트리에서 결정 / the channel code, from the server's registry */
  channelCode: string;
  /** 채널 표시명 / the channel label */
  channelLabel: string;
  /** 거래번호 / the transaction serial */
  transactionNo: string;
  /** 메시지키 / the message key */
  messageKey: string;
  /** 이용기관 / the institution code */
  institutionCode: string;
  /** 상태 원값 / the raw status code */
  statusCode: string;
  /** 상태 표시 / the status display */
  statusDisplay: string;
  /** 톡결과 표시 / the talk-result display */
  talkResult: string;
  /** 톡결과 구분 / the talk-result classification */
  talkOutcome: TalkOutcome;
  /** 문자결과 표시 / the SMS-result display */
  smsResult: string;
  /** 문자결과 구분 / the SMS-result classification */
  smsOutcome: TalkOutcome;
  /** 발송번호 — 마스킹됨 / the sender number, masked */
  senderMasked: string;
  /** 수신번호 — 마스킹됨 / the recipient number, masked */
  recipientMasked: string;
  /** 요청일자 / the request date */
  requestDate: string;
  /** 요청시간 / the request time */
  requestTime: string;
  /** 발송시간 / the dispatch time */
  sentTime: string;
  /** 응답시간 / the receipt time */
  reportTime: string;
  /** 활성/보관 / live or archive */
  tableType: string;
  /** 메시지 상세 가능 여부 / whether message detail can be opened */
  detailAvailable: boolean;
}

/** 거래 상세내역 한 페이지. / One page of transaction detail. */
export interface TalkMessagePage {
  rows: TalkMessageRow[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
}

/**
 * 메시지 상세. / A message detail.
 *
 * 모든 문자열 필드가 값 또는 `(값 없음)` 표식을 담는다 — 빈 문자열은 오지 않는다. 레거시는
 * 값이 없는 것과 조회가 실패한 것을 같은 빈 칸으로 그렸고, D-T18 때문에 발신·수신번호는 항상
 * 비어 있었으므로 운영자는 그 두 칸이 왜 비었는지 알 방법이 없었다.
 *
 * Every string field carries a value or the `(값 없음)` marker — never an empty string. The legacy drew "no
 * value" and "the lookup failed" as one empty cell, and because of D-T18 the numbers were always blank, so an
 * operator had no way to tell why.
 */
export interface TalkMessageDetail {
  messageKey: string;
  institutionCode: string;
  channelCode: string;
  channelLabel: string;
  statusDisplay: string;
  profileKey: string;
  adFlag: string;
  talkResult: string;
  smsResult: string;
  templateCode: string;
  senderMasked: string;
  recipientMasked: string;
  requestedAt: string;
  sentAt: string;
  carrierRepliedAt: string;
  reportedAt: string;
  message: string;
  imagePath: string;
  imageUrl: string;
  wideImageFlag: string;
  buttonJson: string;
  failedType: string;
  failedSubject: string;
  failedImage: string;
  failedMessage: string;
  hasAttachment: boolean;
  hasFailback: boolean;
}

/**
 * 거래에 속한 메시지를 조회한다. / Reads the messages under a transaction.
 *
 * @param query 조회 조건 / the criteria
 * @returns 한 페이지 / one page
 * @throws TalkHistoryApiError 요청 실패 / on failure
 *
 * req: FR-TLKD-001, FR-TLKD-002, FR-TLKD-005, FR-TLKD-007
 */
export async function fetchTalkMessages(query: TalkMessageQuery): Promise<TalkMessagePage> {
  const params = new URLSearchParams();
  const optional: Array<[string, string | number | undefined]> = [
    ['recipient', query.recipient],
    ['status', query.status],
    ['talkResult', query.talkResult],
    ['smsResult', query.smsResult],
    ['page', query.page],
    ['size', query.size],
  ];
  for (const [key, value] of optional) {
    if (value !== undefined && value !== null && `${value}` !== '') {
      params.set(key, `${value}`);
    }
  }

  const path =
    `${BASE}/${encodeURIComponent(query.transactionDate)}` +
    `/${encodeURIComponent(query.serial)}/messages?${params.toString()}`;

  const response = await fetch(path, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new TalkHistoryApiError(await readError(response), response.status);
  }
  return (await response.json()) as TalkMessagePage;
}

/**
 * 메시지 한 건의 상세를 조회한다. / Reads one message's detail.
 *
 * 거래 키를 함께 보낸다. 레거시는 메시지 키만으로 조회할 수 있었고 기관 조건이 없었으므로,
 * 메시지 키만 알면 다른 기관의 메시지 본문·템플릿코드·전화번호를 읽었다(D-T5).
 *
 * The transaction key is sent alongside. The legacy allowed a lookup by message key alone with no institution
 * predicate, so a message key was enough to read another institution's body, template code and numbers (D-T5).
 *
 * @param transactionDate 거래일자 / the transaction date
 * @param serial 거래고유번호 / the transaction serial
 * @param messageKey 메시지키 / the message key
 * @param tableType 활성/보관 / live or archive
 * @returns 상세 / the detail
 * @throws TalkHistoryApiError 요청 실패 / on failure
 *
 * req: FR-AZ-T04, FR-TLKM-001, FR-TLKM-007
 */
export async function fetchTalkMessageDetail(
  transactionDate: string,
  serial: string,
  messageKey: string,
  tableType: string,
): Promise<TalkMessageDetail> {
  const path =
    `${BASE}/${encodeURIComponent(transactionDate)}/${encodeURIComponent(serial)}` +
    `/messages/${encodeURIComponent(messageKey)}?tableType=${encodeURIComponent(tableType)}`;

  const response = await fetch(path, {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new TalkHistoryApiError(await readError(response), response.status);
  }
  return (await response.json()) as TalkMessageDetail;
}

/** 파일명 헤더에서 UTF-8 파일명을 뽑는 정규식. / Extracts the UTF-8 filename from the header. */
const FILENAME_STAR = new RegExp("filename\\*=UTF-8''([^;]+)");

/**
 * 거래내역을 파일로 내보낸다. / Exports the transaction history as a file.
 *
 * `fetch` 로 받아 blob 으로 다룬다. 레거시는 숨은 iframe 을 대상으로 폼을 제출했는데, 그 이름의
 * 프레임이 어떤 뷰에도 없었고 `fintech.common.submit` 이 만들어 주지도 않았다 — 실패하면
 * 사용자에게 아무 표시도 없었다(D-T23). `fetch` 는 오류를 상태 코드와 본문으로 돌려주므로 화면이
 * 그것을 보여줄 수 있다.
 *
 * Received via `fetch` and handled as a blob. The legacy submitted a form to a hidden iframe whose name no view
 * declared and which `fintech.common.submit` did not create, so a failure showed the user nothing (D-T23).
 * `fetch` returns errors as a status and a body the screen can display.
 *
 * 조건 파라미터가 목록 조회와 **같은 타입**이다. 하나라도 다르면 화면에 걸린 조건이 파일에
 * 반영되지 않을 수 있고, 그것이 D-T1 의 시작이었다.
 *
 * The criteria are the **same type** as the list query's. Any difference would let a filter set on the screen
 * miss the file, which is where D-T1 began.
 *
 * @param query 조회 조건 — 목록과 같은 타입 / the criteria, the same type the list uses
 * @returns 파일 blob, 서버가 알린 행 수, 파일명 / the blob, the row count the server reported, and the filename
 * @throws TalkHistoryApiError 요청 실패 / on failure
 *
 * req: FR-TLKX-001, FR-TLKX-003, FR-TLKX-004, FR-TLKX-006
 */
export async function exportTalkHistory(
  query: TalkHistoryQuery,
): Promise<{ blob: Blob; rows: number; filename: string }> {
  // 페이지와 크기는 보내지 않는다 — 내보내기는 전체를 대상으로 하며, 상한은 서버가 강제한다.
  // Page and size are not sent: the export covers the whole result and the server enforces the ceiling.
  const params = toParams({ ...query, page: undefined, size: undefined });

  const response = await fetch(`${BASE}/export?${params.toString()}`, {
    method: 'GET',
    credentials: 'same-origin',
  });
  if (!response.ok) {
    // 상한 초과 같은 거부는 사용자가 고칠 수 있으므로 서버 문구를 그대로 올린다.
    // A refusal such as an exceeded ceiling is fixable by the user, so the server's message is surfaced.
    throw new TalkHistoryApiError(await readError(response), response.status);
  }

  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = FILENAME_STAR.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : '톡전송내역.xlsx';

  return {
    blob: await response.blob(),
    rows: Number(response.headers.get('X-Talk-Export-Rows') ?? '0'),
    filename,
  };
}
