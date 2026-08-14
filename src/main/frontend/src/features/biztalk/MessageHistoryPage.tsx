import { useState } from 'react';
import {
  CriteriaError,
  MessageHistoryPage as Page,
  MessageHistoryRow,
  searchMessageHistory,
} from '../../api/messageHistoryApi';
import { MessageDetailPanel } from './MessageDetailPanel';

/**
 * 문자내역 조회 화면. / 문자내역 search screen.
 *
 * req: FR-MSG-002, FR-MSG-004, FR-MSG-005, FR-MSG-007, FR-MSG-014, NFR-USE-01
 * source: biztalk_admin_40_view.jsp (검색 폼), biztalk_admin_40.js (그리드 12컬럼)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>발신/수신 라벨이 실제 컬럼과 일치한다</b> — 레거시는 반대였다(D3)</li>
 *   <li><b>수신번호·결과코드 검색이 실제로 동작한다</b> — 레거시는 전송했으나 서버가
 *       무시했다(D4)</li>
 *   <li><b>이용기관 드롭다운이 운영자에게만 표시된다</b> — 레거시는 모든 사용자에게 전체
 *       고객사 명단을 채워 보여줬다(TM-011)</li>
 *   <li><b>서버 페이징</b> — 레거시는 전량을 받아 클라이언트에서 페이징했다(D7)</li>
 * </ul>
 */

interface Props {
  /** 운영자 여부 — 이용기관 선택 가능 여부를 결정한다 / whether the user is an operator */
  operator: boolean;
}

/** 기본 조회 기간(일). 레거시는 당일이 기본이었다. / Default window; the legacy defaulted to today. */
const DEFAULT_DAYS = 1;

function todayIso(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.toISOString().slice(0, 10)}T00:00`;
}

/**
 * 문자내역 조회 화면 컴포넌트. / The 문자내역 screen component.
 */
export function MessageHistoryPage({ operator }: Props) {
  const [from, setFrom] = useState(todayIso(-DEFAULT_DAYS));
  const [to, setTo] = useState(todayIso(1));
  const [institutionCode, setInstitutionCode] = useState('');
  const [messageKey, setMessageKey] = useState('');
  const [senderNumber, setSenderNumber] = useState('');
  const [recipientNumber, setRecipientNumber] = useState('');
  const [status, setStatus] = useState('');
  const [messageType, setMessageType] = useState('');
  const [tableType, setTableType] = useState('');
  const [resultCode, setResultCode] = useState('');

  const [result, setResult] = useState<Page | null>(null);
  const [violations, setViolations] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<MessageHistoryRow | null>(null);

  async function runSearch(page = 0) {
    setViolations([]);
    setError(null);
    setLoading(true);
    try {
      const parsedKey = messageKey.trim() === '' ? undefined : Number(messageKey);
      const data = await searchMessageHistory({
        from,
        to,
        // 운영자가 아니면 이용기관을 보내지 않는다. 서버는 어차피 무시하지만,
        // 보내지 않는 것이 의도를 분명히 하고 감사 기록에 불필요한 시도를 남기지 않는다.
        // Not sent unless operator: the server ignores it anyway, but omitting it states intent
        // and avoids logging a pointless override attempt.
        institutionCode: operator && institutionCode ? institutionCode : undefined,
        messageKey: parsedKey,
        senderNumber: senderNumber || undefined,
        recipientNumber: recipientNumber || undefined,
        status: status || undefined,
        messageType: messageType || undefined,
        tableType: tableType || undefined,
        resultCode: resultCode || undefined,
        page,
      });
      setResult(data);
    } catch (e) {
      if (e instanceof CriteriaError) {
        // 위반 목록 전체를 보여준다. 레거시는 alert 하나로 첫 번째만 알렸고, 그 판정조차
        // 잘못되어 정상 범위를 거절했다(D8).
        // Every violation is shown; the legacy showed one alert, and its check was itself wrong.
        setViolations(e.violations);
      } else {
        setError('조회 중 오류가 발생했습니다. 잠시 후 다시 시도하세요.');
      }
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page-wrap">
      <h1>문자내역</h1>

      <form
        className="search-panel"
        onSubmit={(e) => {
          e.preventDefault();
          void runSearch(0);
        }}
        noValidate
      >
        <fieldset>
          <legend>조회 조건</legend>

          <div role="alert" aria-live="assertive" className={error ? 'field-error visible' : 'field-error'}>
            {error}
          </div>
          {violations.length > 0 && (
            <ul role="alert" aria-live="assertive" className="violations">
              {violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          )}

          <div className="search-grid">
            {/*
              이용기관 선택은 운영자에게만 렌더링한다. 레거시는 모든 사용자에게
              전체 고객사 명단을 드롭다운으로 제공했다(TM-011, FR-TEN-004).
              Rendered for operators only; the legacy showed every customer to everyone.
            */}
            {operator && (
              <div className="field">
                <label htmlFor="mh-institution">이용기관</label>
                <input
                  id="mh-institution"
                  type="text"
                  value={institutionCode}
                  onChange={(e) => setInstitutionCode(e.target.value)}
                  placeholder="전체"
                />
              </div>
            )}

            <div className="field">
              <label htmlFor="mh-from">요청일시 (시작)</label>
              <input
                id="mh-from"
                type="datetime-local"
                required
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-to">요청일시 (종료)</label>
              <input
                id="mh-to"
                type="datetime-local"
                required
                value={to}
                onChange={(e) => setTo(e.target.value)}
                aria-describedby="mh-range-help"
              />
              <p id="mh-range-help" className="field-help">
                조회 기간은 최대 31일까지 가능합니다.
              </p>
            </div>

            <div className="field">
              <label htmlFor="mh-msgkey">메시지키</label>
              <input
                id="mh-msgkey"
                type="text"
                inputMode="numeric"
                value={messageKey}
                onChange={(e) => setMessageKey(e.target.value.replace(/\D/g, ''))}
              />
            </div>

            {/*
              라벨과 컬럼이 일치한다. 레거시: 발신번호→PHONE(수신 컬럼), 수신번호→CALLBACK(발신 컬럼).
              Labels match columns. Legacy: 발신번호→PHONE (recipient), 수신번호→CALLBACK (sender).
            */}
            <div className="field">
              <label htmlFor="mh-sender">발송번호</label>
              <input
                id="mh-sender"
                type="text"
                value={senderNumber}
                onChange={(e) => setSenderNumber(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-recipient">수신번호</label>
              <input
                id="mh-recipient"
                type="text"
                value={recipientNumber}
                onChange={(e) => setRecipientNumber(e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="mh-status">상태</label>
              <select id="mh-status" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="">전체</option>
                <option value="1">미전송</option>
                <option value="2">전송완료</option>
                <option value="3">톡결과수신</option>
                <option value="4">문자결과수신</option>
                <option value="6">큐입력</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-type">유형</label>
              <select
                id="mh-type"
                value={messageType}
                onChange={(e) => setMessageType(e.target.value)}
              >
                <option value="">전체</option>
                <option value="AT">알림톡</option>
                <option value="FT">친구톡</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-table">문자타입</label>
              <select id="mh-table" value={tableType} onChange={(e) => setTableType(e.target.value)}>
                <option value="">전체</option>
                <option value="SMS">SMS</option>
                <option value="MMS">MMS</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="mh-result">결과코드</label>
              <input
                id="mh-result"
                type="text"
                value={resultCode}
                onChange={(e) => setResultCode(e.target.value)}
              />
            </div>
          </div>

          <button type="submit" className="primary" disabled={loading}>
            {loading ? '조회 중…' : '조회'}
          </button>
        </fieldset>
      </form>

      {result && (
        <section aria-live="polite">
          <p className="result-summary">
            총 <strong>{result.totalCount.toLocaleString()}</strong>건 · {result.page + 1} /{' '}
            {Math.max(result.totalPages, 1)} 페이지
          </p>

          {result.rows.length === 0 ? (
            /*
              빈 결과와 오류를 구분한다. 레거시는 둘 다 같은 빈 그리드로 표시했다.
              An empty result is distinguished from an error; the legacy showed both identically.
            */
            <p className="empty-state">조회 결과가 없습니다.</p>
          ) : (
            <>
              <div className="table-scroll">
                <table>
                  <caption className="sr-only">문자내역 조회 결과</caption>
                  <thead>
                    <tr>
                      <th scope="col">유형</th>
                      <th scope="col">테이블</th>
                      <th scope="col">메시지키</th>
                      <th scope="col">이용기관</th>
                      <th scope="col">상태</th>
                      <th scope="col">톡결과</th>
                      <th scope="col">발송번호</th>
                      <th scope="col">수신번호</th>
                      <th scope="col">요청일자</th>
                      <th scope="col">요청시간</th>
                      <th scope="col">발송시간</th>
                      <th scope="col">응답시간</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.rows.map((row) => (
                      <tr key={`${row.messageType}-${row.tableType}-${row.messageKey}-${row.requestDate}`}>
                        <td>{row.messageTypeLabel}</td>
                        <td>{row.tableType}</td>
                        <td>
                          {/*
                            레거시는 <a onclick> 이었다. button 을 쓰면 키보드로 접근 가능하고
                            스크린리더가 조작 가능한 요소로 인식한다(WCAG 2.1.1).
                            The legacy used an anchor with onclick; a button is keyboard-reachable
                            and announced as actionable.
                          */}
                          <button
                            type="button"
                            className="link-button"
                            onClick={() => setSelected(row)}
                          >
                            {row.messageKey}
                          </button>
                        </td>
                        <td>{row.institutionCode}</td>
                        <td>{row.statusLabel}</td>
                        <td>{row.resultCode}</td>
                        <td>{row.senderNumber}</td>
                        <td>{row.recipientNumber}</td>
                        <td>{row.requestDate?.slice(0, 8)}</td>
                        <td>{row.requestTime}</td>
                        <td>{row.sentTime}</td>
                        <td>{row.reportTime}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <nav className="paging" aria-label="페이지 이동">
                <button
                  type="button"
                  disabled={result.page === 0 || loading}
                  onClick={() => void runSearch(result.page - 1)}
                >
                  이전
                </button>
                <button
                  type="button"
                  disabled={result.page + 1 >= result.totalPages || loading}
                  onClick={() => void runSearch(result.page + 1)}
                >
                  다음
                </button>
              </nav>
            </>
          )}
        </section>
      )}

      {selected && (
        <MessageDetailPanel row={selected} onClose={() => setSelected(null)} />
      )}
    </main>
  );
}
