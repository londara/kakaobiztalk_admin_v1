import { useCallback, useEffect, useState } from 'react';
import {
  formatTimestamp,
  InstitutionPage as Page,
  InstitutionQuery,
  InstitutionRow,
  InstitutionStatusFilter,
  searchInstitutions,
} from '../../api/institutionApi';

/**
 * 이용기관 관리 화면. / 이용기관 management screen.
 *
 * req: FR-INST-001, FR-INST-002, FR-INST-003, FR-INST-006, FR-INST-007, FR-INST-008,
 *      FR-ATK-002, FR-AZ-I03
 * source: biztalk_admin_00_view.jsp (검색 폼·그리드), biztalk_admin_00.js (drawGrid 8컬럼)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>인증키가 마스킹된다</b> — 레거시는 전 기관의 키를 평문 컬럼으로 노출했다(D-I5)</li>
 *   <li><b>서버 페이징</b> — 레거시는 전량을 받아 클라이언트에서 잘랐다(D-I10)</li>
 *   <li><b>등록일시·수정일시에 시각이 표시된다</b> — 레거시는 날짜만 보여 D-I9 을 가렸다</li>
 *   <li><b>매핑되지 않는 상태값이 원문으로 표시된다</b> — 레거시는 전부 '미사용' 으로
 *       뭉개 데이터 이상을 감췄다(FR-INST-006)</li>
 *   <li><b>담당자관리 탭이 없다</b> — 레거시에서도 주석 처리되어 동작하지 않았고,
 *       범위에서 제외되었다(AMB-I02, D-I13)</li>
 * </ul>
 *
 * <h2>동작하지 않는 버튼을 두지 않는다 / no buttons that do nothing</h2>
 * <p>레거시 화면에는 담당자 '추가'·'삭제' 버튼이 마크업에 존재했지만 이벤트 핸들러가
 * 아예 없었다(D-I13). 이 화면은 Sprint I1 범위인 <b>조회</b>만 제공하며, 등록·수정·중지·
 * 삭제 버튼은 해당 기능이 구현되는 Sprint I2 에서 함께 추가한다. 눌러도 아무 일도
 * 일어나지 않는 버튼을 먼저 두는 것은 같은 결함을 되풀이하는 것이다.</p>
 * <p>The legacy markup carried 담당자 추가/삭제 buttons with no event handlers at all (D-I13).
 * This screen offers only the <b>search</b> that Sprint I1 covers; the 등록/수정/중지/삭제
 * buttons arrive in Sprint I2 alongside the operations they invoke. Shipping a button that does
 * nothing when pressed would repeat exactly that defect.</p>
 */

/** 상태 라디오 선택지 — 레거시와 동일한 3분류. / Status radio options, the legacy's three. */
const STATUS_OPTIONS: ReadonlyArray<{ value: InstitutionStatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'Y', label: '사용' },
  { value: 'N', label: '사용 안함' },
];

/** 그리드 컬럼 정의. / Grid column definitions. */
const COLUMNS: ReadonlyArray<{ key: string; label: string }> = [
  { key: 'code', label: '기관코드' },
  { key: 'name', label: '기관명' },
  { key: 'englishName', label: '영문명' },
  { key: 'statusLabel', label: '사용여부' },
  { key: 'authKeyMasked', label: '인증키' },
  { key: 'registeredAt', label: '등록일시' },
  { key: 'lastModifiedAt', label: '수정일시' },
  { key: 'description', label: '설명' },
];

interface Props {
  /**
   * 이용기관을 선택했을 때의 콜백 — Sprint I2 의 수정 화면이 소비한다.
   * Called when an institution is chosen; Sprint I2's edit screen consumes it.
   */
  onSelect?: (row: InstitutionRow) => void;
}

/**
 * 이용기관 관리 화면 컴포넌트. / The 이용기관 management screen component.
 */
export function InstitutionPage({ onSelect }: Props) {
  const [name, setName] = useState('');
  const [status, setStatus] = useState<InstitutionStatusFilter>('ALL');

  const [result, setResult] = useState<Page | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const run = useCallback(
    async (query: InstitutionQuery) => {
      setLoading(true);
      setError(null);
      try {
        setResult(await searchInstitutions(query));
      } catch (e) {
        // 실패했을 때 이전 결과를 남겨두면 조회에 성공한 것처럼 보인다.
        // Leaving stale rows on screen after a failure would look like a successful search.
        setResult(null);
        setError(e instanceof Error ? e.message : '이용기관을 조회할 수 없습니다.');
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  // 진입 시 1회 조회한다. 레거시도 화면을 열면 목록을 채웠다.
  // One search on entry, as the legacy did when the screen opened.
  useEffect(() => {
    void run({ status: 'ALL', page: 0 });
  }, [run]);

  function submit(event: React.FormEvent) {
    event.preventDefault();
    // 조건이 바뀌면 첫 페이지로 돌아간다 — 3페이지에서 조건을 바꾸면 결과가 3페이지보다
    // 짧아 빈 화면이 나올 수 있다.
    // A criteria change returns to page one: changing filters while on page three can leave the
    // user past the end of a shorter result set.
    void run({ name, status, page: 0 });
  }

  function goToPage(page: number) {
    void run({ name, status, page });
  }

  return (
    <section aria-labelledby="institution-heading">
      <h1 id="institution-heading">서비스 관리</h1>
      <p>이용기관 관리</p>

      <form onSubmit={submit} aria-label="이용기관 조회">
        <div>
          <label htmlFor="institution-name">검색</label>
          <input
            id="institution-name"
            type="text"
            value={name}
            placeholder="이용기관 검색"
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        <fieldset>
          <legend>상태</legend>
          {STATUS_OPTIONS.map((option) => (
            <label key={option.value} htmlFor={`status-${option.value}`}>
              <input
                id={`status-${option.value}`}
                type="radio"
                name="status"
                value={option.value}
                checked={status === option.value}
                onChange={() => setStatus(option.value)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>

        <button type="submit" disabled={loading}>
          조회
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      {loading && <p role="status">조회 중입니다.</p>}

      {result && !loading && (
        <>
          <table>
            <caption>이용기관 목록</caption>
            <thead>
              <tr>
                {COLUMNS.map((column) => (
                  <th key={column.key} scope="col">
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row) => (
                <tr key={row.code}>
                  <td>
                    {/*
                      레거시는 이 링크를 문자열 연결로 만들어 DB 값을 인라인 onclick 에
                      그대로 넣었다(D-I12). React 는 값을 텍스트로 렌더링하므로 같은 구멍이
                      생기지 않는다.
                      The legacy built this link by string concatenation, dropping a DB value
                      into an inline onclick (D-I12). React renders values as text, so the same
                      hole cannot open.
                    */}
                    <button type="button" onClick={() => onSelect?.(row)}>
                      {row.code}
                    </button>
                  </td>
                  <td>{row.name}</td>
                  <td>{row.englishName}</td>
                  <td>{row.statusLabel}</td>
                  <td>{row.authKeyMasked}</td>
                  <td>{formatTimestamp(row.registeredAt)}</td>
                  <td>{formatTimestamp(row.lastModifiedAt)}</td>
                  <td>{row.description}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {result.rows.length === 0 && <p>조회 결과가 없습니다.</p>}

          {result.totalPages > 1 && (
            <nav aria-label="페이지">
              <button type="button" onClick={() => goToPage(result.page - 1)} disabled={result.page === 0}>
                이전
              </button>
              <span>
                {result.page + 1} / {result.totalPages}
              </span>
              <button
                type="button"
                onClick={() => goToPage(result.page + 1)}
                disabled={result.page + 1 >= result.totalPages}
              >
                다음
              </button>
            </nav>
          )}

          <p>총 {result.totalCount} 건</p>
        </>
      )}
    </section>
  );
}
