/**
 * 거래 상세내역 패널 — 화면 32. / The transaction-detail panel: screen 32.
 *
 * req: FR-TLKD-001, FR-TLKD-002, FR-TLKD-003, FR-TLKD-005, FR-TLKD-006, FR-TLKD-007, FR-TLKD-008
 * source: biztalk_admin_32_view.jsp, biztalk_admin_32.js
 *
 * <h2>레거시 팝업이 아니라 패널인 이유 / why a panel rather than a popup</h2>
 * 레거시는 `ap.openPop` 으로 별 창을 띄우고 `frm0` 을 제출해 값을 넘겼다. 그 형태가 D-T2 의
 * 경로였다 — 이용기관 코드가 폼의 숨은 입력에 담겨 브라우저를 거쳤으므로 고칠 수 있었다. 여기서는
 * 거래 키만 경로에 담아 요청하고 기관은 서버가 원장에서 도출하므로, 넘길 것이 없다.
 *
 * The legacy opened a separate window with `ap.openPop` and passed values by submitting `frm0`. That shape was
 * D-T2's path: the institution code travelled through the browser in a hidden input and so could be edited.
 * Here only the transaction key goes in the URL and the server derives the institution from the ledger, so
 * there is nothing to pass.
 *
 * <h2>미수신 선택지가 있는 이유 / why 미수신 is an option</h2>
 * 레거시 톡결과 필터는 성공과 실패뿐이었고 실패는 `AND RSLT != '0'` 이었다. SQL 에서
 * `NULL != '0'` 은 UNKNOWN 이므로 결과가 아직 오지 않은 행은 **어느 선택지에도** 나타나지
 * 않았다(D-T22). 세 선택지가 전체를 남김 없이 나눈다.
 *
 * The legacy talk-result filter offered only success and failure, and failure was `AND RSLT != '0'`. Since
 * `NULL != '0'` is UNKNOWN in SQL, a row with no result yet appeared under **neither** option (D-T22). The
 * three options now partition the whole set.
 */

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchTalkMessages,
  type TalkMessageRow,
  type TalkOutcome,
} from '../../api/talkDetailApi';

/** 화면 32 의 14개 컬럼. / Screen 32's fourteen columns. */
const COLUMNS = [
  '유형',
  '거래번호',
  '메시지키',
  '이용기관',
  '상태',
  '톡결과',
  '문자결과',
  '발송번호',
  '수신번호',
  '요청일자',
  '요청시간',
  '발송시간',
  '응답시간',
  '테이블',
] as const;

/** 결과 구분 선택지 — 세 값이 전체를 나눈다. / The three options that partition the set. */
const OUTCOMES: ReadonlyArray<{ value: TalkOutcome; label: string }> = [
  { value: 'SUCCESS', label: '성공' },
  { value: 'FAILURE', label: '실패' },
  { value: 'PENDING', label: '미수신' },
];

/** `YYYYMMDD` → `YYYY-MM-DD`. */
function formatDate(raw: string): string {
  if (!raw || raw.length < 8) {
    return raw ?? '';
  }
  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`;
}

/** `HHMMSS` → `HH:MM:SS`. */
function formatTime(raw: string): string {
  if (!raw || raw.length < 6) {
    return raw ?? '';
  }
  return `${raw.slice(0, 2)}:${raw.slice(2, 4)}:${raw.slice(4, 6)}`;
}

/** 패널 속성. / The panel's props. */
export interface TalkTransactionDetailPanelProps {
  /** 거래일자 / the transaction date */
  transactionDate: string;
  /** 거래고유번호 / the transaction serial */
  serial: string;
  /** 화면에 보이는 API 서비스 코드 — 문맥 표시용 / the API service code, for context */
  apiServiceCode: string;
  /** 화면에 보이는 기관명 — 문맥 표시용 / the institution name, for context */
  institutionName: string;
  /** 닫기 / close */
  onClose: () => void;
  /** 메시지 상세 열기 / open the message detail */
  onOpenMessage: (messageKey: string, tableType: string) => void;
}

export function TalkTransactionDetailPanel({
  transactionDate,
  serial,
  apiServiceCode,
  institutionName,
  onClose,
  onOpenMessage,
}: TalkTransactionDetailPanelProps) {
  const [recipient, setRecipient] = useState('');
  const [talkResult, setTalkResult] = useState<TalkOutcome | ''>('');
  const [smsResult, setSmsResult] = useState<TalkOutcome | ''>('');
  const [page, setPage] = useState(0);
  const [applied, setApplied] = useState(0);

  const messages = useQuery({
    queryKey: [
      'talk-history',
      'messages',
      transactionDate,
      serial,
      applied,
      recipient,
      talkResult,
      smsResult,
      page,
    ],
    queryFn: () =>
      fetchTalkMessages({
        transactionDate,
        serial,
        recipient: recipient || undefined,
        talkResult: talkResult || undefined,
        smsResult: smsResult || undefined,
        page,
      }),
  });

  const rows: TalkMessageRow[] = messages.data?.rows ?? [];
  const totalPages = messages.data?.totalPages ?? 0;

  function onSearch(event: React.FormEvent) {
    event.preventDefault();
    setPage(0);
    setApplied((n) => n + 1);
  }

  return (
    <section className="lg-panel" aria-label="거래 상세내역" data-testid="talk-txn-detail">
      <div className="lg-panel-header">
        <h2>거래 상세내역 조회</h2>
        <button type="button" className="lg-btn" onClick={onClose} data-testid="talk-txn-close">
          닫기
        </button>
      </div>

      <ul className="lg-info">
        <li>
          거래일자 {formatDate(transactionDate)} · 이용기관 {institutionName} · 거래번호 {serial} · API{' '}
          {apiServiceCode}
        </li>
      </ul>

      <form className="lg-search" onSubmit={onSearch}>
        <label>
          수신번호
          {/*
            11자리를 받는다. 레거시는 maxlength="10" 이어서 국내 휴대폰 번호를 온전히 입력할 수
            없었다(D-T21).
            Accepts eleven digits. The legacy set maxlength="10", so a Korean mobile number could not be
            typed in full (D-T21).
          */}
          <input
            type="text"
            inputMode="numeric"
            maxLength={11}
            value={recipient}
            onChange={(e) => setRecipient(e.target.value)}
            aria-label="수신번호"
            data-testid="talk-msg-recipient"
          />
        </label>

        <label>
          톡결과
          <select
            value={talkResult}
            onChange={(e) => setTalkResult(e.target.value as TalkOutcome | '')}
            aria-label="톡결과"
            data-testid="talk-msg-talk-result"
          >
            <option value="">전체</option>
            {OUTCOMES.map((outcome) => (
              <option key={outcome.value} value={outcome.value}>
                {outcome.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          문자결과
          <select
            value={smsResult}
            onChange={(e) => setSmsResult(e.target.value as TalkOutcome | '')}
            aria-label="문자결과"
            data-testid="talk-msg-sms-result"
          >
            <option value="">전체</option>
            {OUTCOMES.map((outcome) => (
              <option key={outcome.value} value={outcome.value}>
                {outcome.label}
              </option>
            ))}
          </select>
        </label>

        <span className="lg-search-actions">
          <button
            type="submit"
            className="lg-btn lg-btn-primary"
            disabled={messages.isFetching}
            data-testid="talk-msg-search"
          >
            조회
          </button>
        </span>
      </form>

      {messages.isError && (
        <p role="alert" className="field-error visible" data-testid="talk-msg-error">
          {(messages.error as Error).message}
        </p>
      )}

      <div className="lg-grid-wrap">
        <table className="lg-grid">
          <caption className="sr-only">거래 상세내역</caption>
          <thead>
            <tr>
              {COLUMNS.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td className="lg-empty" colSpan={COLUMNS.length}>
                  {messages.isFetching ? '조회 중입니다.' : '조회된 내용이 없습니다.'}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={`${row.messageKey}-${row.tableType}`}>
                  {/*
                    유형은 서버가 레지스트리에서 결정한 값이다. 레거시 친구톡 질의는 모든 행을
                    'AT' 로 보고했고, 화면 31 이 그 값으로 테이블을 골라 두 화면이 함께
                    틀렸다(D-T7).
                    The type is the server's registry decision. The legacy 친구톡 query reported every row as
                    'AT', and screen 31 chose its table from that value, so two screens were wrong
                    together (D-T7).
                  */}
                  <td>{row.channelLabel}</td>
                  <td>{row.transactionNo}</td>
                  <td>
                    <button
                      type="button"
                      className="lg-link"
                      onClick={() => onOpenMessage(row.messageKey, row.tableType)}
                      data-testid="talk-msg-detail-link"
                    >
                      {row.messageKey}
                    </button>
                  </td>
                  <td>{row.institutionCode}</td>
                  <td>{row.statusDisplay}</td>
                  <td>{row.talkResult}</td>
                  <td>{row.smsResult}</td>
                  {/*
                    마스킹된 값이다. 레거시는 decrypt() 만 적용해 평문 번호를 브라우저로
                    보냈다(D-T6).
                    Masked values. The legacy applied decrypt() alone and sent plaintext numbers to the
                    browser (D-T6).
                  */}
                  <td>{row.senderMasked}</td>
                  <td>{row.recipientMasked}</td>
                  <td>{formatDate(row.requestDate)}</td>
                  <td>{formatTime(row.requestTime)}</td>
                  <td>{formatTime(row.sentTime)}</td>
                  <td>{formatTime(row.reportTime)}</td>
                  <td>{row.tableType}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {messages.data && totalPages > 0 && (
        <nav className="lg-paging" aria-label="페이지 이동" data-testid="talk-msg-paging">
          <button
            type="button"
            className="lg-btn"
            disabled={page === 0 || messages.isFetching}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            이전
          </button>
          {/*
            서버가 보낸 총건수를 쓴다. 레거시 서비스는 TOT_CNT 를 올바르게 반환했고 클라이언트가
            그것을 읽지 않았다(D-T30) — 목록 화면은 반대로 계산조차 하지 않았다(D-T11).
            Uses the server's total. The legacy service returned TOT_CNT correctly and the client never read
            it (D-T30), while the list screen did not even compute it (D-T11).
          */}
          <span data-testid="talk-msg-page-indicator">
            {page + 1} / {totalPages} (총 {messages.data.totalCount}건)
          </span>
          <button
            type="button"
            className="lg-btn"
            disabled={page + 1 >= totalPages || messages.isFetching}
            onClick={() => setPage((current) => current + 1)}
          >
            다음
          </button>
        </nav>
      )}
    </section>
  );
}
