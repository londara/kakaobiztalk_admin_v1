import { useCallback, useEffect, useState } from 'react';
import { formatTimestamp, InstitutionRow, searchInstitutions } from '../../api/institutionApi';
import {
  listSenderNumbers,
  SenderNumberPage as Page,
  SenderNumberRow,
} from '../../api/senderNumberApi';

/**
 * 이용기관 정보 관리 — 발신번호 화면. / 이용기관 정보 관리 — the sender-number screen.
 *
 * req: FR-SND-001, FR-SND-002, FR-SND-003, FR-SND-005, FR-SND-006, FR-SND-008,
 *      FR-SND-009, FR-SND-010, FR-AZ-D01
 * source: biztalk_admin_10_view.jsp (이용기관 선택 + 그리드), biztalk_admin_10.js (drawGrid 7컬럼)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>발신번호가 전체로 표시된다</b> — 레거시는 목록에서만 이름용 마스킹 함수를
 *       적용하고 상세에서는 원본을 보여 주었다. 그 불일치가 삭제를 망가뜨렸다(D-S1, AMB-S04)</li>
 *   <li><b>서버 페이징과 고정 정렬</b> — 레거시는 LIMIT 도 ORDER BY 도 없이 전량을
 *       받아 브라우저에서 잘랐고, 페이징 위젯은 숨겨져 있었다(D-S14)</li>
 *   <li><b>기관을 고르기 전에는 조회하지 않는다</b> — 레거시는 페이지 로드마다 빈
 *       IS_CD 로 한 번 조회했다(D-S19)</li>
 *   <li><b>행 식별은 ref 로 한다</b> — 표시값을 식별자로 쓰는 구조가 D-S1 의 원인이었다</li>
 *   <li><b>수수료 탭이 없다</b> — 레거시 JSP 에서 주석 처리되어 있었고 서비스 계약도
 *       쿼리도 존재하지 않는다(§2.7)</li>
 * </ul>
 *
 * <h2>동작하지 않는 버튼을 두지 않는다 / no buttons that do nothing</h2>
 * <p>레거시 화면에는 '등록'·'삭제' 버튼이 있었고 JS 에는 '수정' 핸들러까지 있었지만,
 * <b>수정 버튼 자체가 마크업에 없어</b> 상세·수정 화면은 도달 불가능한 죽은 코드였다(D-S8).
 * 이 화면은 Sprint S1 범위인 <b>조회</b>만 제공한다. 등록·수정·삭제는 해당 동작이 구현되는
 * Sprint S2 에서 함께 추가한다.</p>
 * <p>The legacy had 등록/삭제 buttons and even a 수정 handler in JS — but no 수정 button in the
 * markup, so the detail screen was unreachable dead code (D-S8). This screen offers only the
 * <b>list</b> that Sprint S1 covers; 등록/수정/삭제 arrive in Sprint S2 with the operations they
 * invoke.</p>
 */

/** 그리드 컬럼 정의 — 레거시 7컬럼. / Grid columns, the legacy's seven. */
const COLUMNS: ReadonlyArray<{ key: keyof SenderNumberRow; label: string }> = [
  { key: 'institutionName', label: '기관명' },
  { key: 'number', label: '발신번호' },
  { key: 'registeredBy', label: '등록자' },
  { key: 'registeredAt', label: '등록일자' },
  { key: 'updatedBy', label: '수정자' },
  { key: 'updatedAt', label: '수정일자' },
  { key: 'description', label: '설명' },
];

const PAGE_SIZE = 20;

const EMPTY_PAGE: Page = { rows: [], totalCount: 0, page: 0, size: PAGE_SIZE, totalPages: 0 };

interface Props {
  /**
   * 행을 선택했을 때의 콜백 — Sprint S2 의 상세 화면이 소비한다. `ref` 를 넘긴다.
   * Called when a row is chosen; Sprint S2's detail screen consumes it. Passes `ref`.
   */
  onSelect?: (row: SenderNumberRow) => void;
}

/**
 * 발신번호 관리 화면 컴포넌트. / The sender-number management screen component.
 */
export function SenderNumberPage({ onSelect }: Props) {
  const [institutions, setInstitutions] = useState<InstitutionRow[]>([]);
  const [institution, setInstitution] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page>(EMPTY_PAGE);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 이용기관 목록. 레거시는 USE_YN=ALL 로 전 기관을 받아 사용 여부를 구분 없이 나열했다.
  // 여기서는 상태를 함께 보여 준다(FR-SND-010).
  // The institution list. The legacy fetched every institution with USE_YN=ALL and listed them
  // without distinguishing status; here the status is shown alongside (FR-SND-010).
  useEffect(() => {
    let cancelled = false;
    searchInstitutions({ status: 'ALL', page: 0, size: 200 })
      .then((response) => {
        if (!cancelled) {
          setInstitutions(response.rows);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setInstitutions([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(
    async (targetInstitution: string, targetPage: number) => {
      // D-S19: 기관을 고르기 전에는 요청하지 않는다.
      // D-S19: no request before an institution is chosen.
      if (!targetInstitution) {
        setResult(EMPTY_PAGE);
        setError(null);
        return;
      }

      setLoading(true);
      setError(null);
      try {
        setResult(
          await listSenderNumbers({
            institution: targetInstitution,
            page: targetPage,
            size: PAGE_SIZE,
          }),
        );
      } catch (e) {
        setResult(EMPTY_PAGE);
        setError(e instanceof Error ? e.message : '발신번호를 조회할 수 없습니다.');
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void load(institution, page);
  }, [institution, page, load]);

  const onInstitutionChange = (value: string) => {
    // 기관이 바뀌면 페이지를 처음으로 되돌린다. 3페이지를 보다 기관을 바꿨을 때
    // 3페이지가 유지되면 결과가 없는 것처럼 보인다.
    // Changing institution resets to the first page: keeping page 3 after a switch would look
    // like an empty result.
    setPage(0);
    setInstitution(value);
  };

  return (
    <section aria-labelledby="senderno-heading">
      <h1 id="senderno-heading">이용기관 정보 관리</h1>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void load(institution, page);
        }}
      >
        <label htmlFor="senderno-institution">이용기관</label>
        <select
          id="senderno-institution"
          value={institution}
          onChange={(e) => onInstitutionChange(e.target.value)}
        >
          <option value="">이용기관을 선택하세요</option>
          {institutions.map((row) => (
            <option key={row.code} value={row.code}>
              {row.name ?? row.code}
              {row.statusLabel ? ` (${row.statusLabel})` : ''}
            </option>
          ))}
        </select>

        <button type="submit" disabled={loading || !institution}>
          조회
        </button>
      </form>

      {error ? <p role="alert">{error}</p> : null}

      {!institution ? (
        <p>이용기관을 선택하면 등록된 발신번호가 표시됩니다.</p>
      ) : (
        <>
          <table>
            <caption>발신번호 목록</caption>
            <thead>
              <tr>
                {COLUMNS.map((column) => (
                  <th key={String(column.key)} scope="col">
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row) => (
                <tr
                  key={row.ref}
                  onClick={onSelect ? () => onSelect(row) : undefined}
                >
                  {COLUMNS.map((column) => (
                    <td key={String(column.key)}>
                      {column.key === 'registeredAt' || column.key === 'updatedAt'
                        ? formatTimestamp(row[column.key] as string | null)
                        : ((row[column.key] as string | null) ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>

          {!loading && result.rows.length === 0 ? (
            <p>등록된 발신번호가 없습니다.</p>
          ) : null}

          <nav aria-label="페이지">
            <button type="button" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
              이전
            </button>
            <span>
              {result.totalPages === 0 ? 0 : page + 1} / {result.totalPages} (총 {result.totalCount}건)
            </span>
            <button
              type="button"
              disabled={loading || page + 1 >= result.totalPages}
              onClick={() => setPage(page + 1)}
            >
              다음
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
