/**
 * BizTalk 화면의 서버 상태 훅. / Server-state hooks for the BizTalk screens.
 *
 * req: FR-INST-001, FR-INST-003, FR-MSG-002, FR-MSG-017, FR-MSGD-001, FR-SND-001
 *
 * <p>API 클라이언트({@code src/api/**})는 그대로 두고 그 위에 캐시 계층만 얹는다. 요청을
 * 만드는 방법 — POST 를 쓰는 이유, CSRF 헤더, 마스킹된 필드 — 은 전부 API 계층의 결정이며
 * 화면이나 캐시가 바꿀 일이 아니다.</p>
 * <p>The API clients are left as they are and only a cache layer sits on top. How a request is
 * made — why it is a POST, the CSRF header, which fields arrive masked — remains the API layer's
 * decision, not the screen's and not the cache's.</p>
 *
 * <h2>쿼리 키에 조회 조건 전체를 넣는 이유 / why the whole criteria object is in the key</h2>
 * <p>키는 "이 데이터가 무엇의 답인가" 를 적는 자리다. 조건 하나라도 빠지면 서로 다른 조회가
 * 같은 캐시 항목을 공유하게 되고, 기관 A 의 결과가 기관 B 의 화면에 나타난다 — 테넌트
 * 격리(FR-TEN-004)를 화면 단에서 무너뜨리는 가장 쉬운 방법이다.</p>
 * <p>The key states what the cached data is an answer to. Omitting a single criterion lets two
 * different searches share one cache entry, so institution A's rows can surface on institution
 * B's screen — the easiest way to break tenant isolation on the client.</p>
 */

import { useMutation, useQuery, type UseMutationResult, type UseQueryResult } from '@tanstack/react-query';
import {
  searchInstitutions,
  type InstitutionPage,
  type InstitutionQuery,
} from '../../api/institutionApi';
import {
  exportMessageHistory,
  fetchMessageDetail,
  searchMessageHistory,
  type MessageDetail,
  type MessageHistoryPage,
  type MessageHistoryQuery,
  type MessageHistoryRow,
} from '../../api/messageHistoryApi';
import { listSenderNumbers, type SenderNumberPage } from '../../api/senderNumberApi';

/**
 * 쿼리 키 모음. / The query keys.
 *
 * <p>키를 한 곳에 모아 두면 무효화 대상이 눈에 보인다. 문자열을 호출부에서 직접 적으면
 * 오타 하나가 "무효화되지 않는 캐시" 로 조용히 나타난다.</p>
 * <p>Collected in one place so that what an invalidation covers is visible; a typo in an inline
 * key string shows up only as a cache that quietly never invalidates.</p>
 */
export const biztalkKeys = {
  /** 이용기관 조회 / institution search */
  institutionSearch: (query: InstitutionQuery) => ['institutions', 'search', query] as const,
  /** 발신번호 목록 / sender-number list */
  senderNumbers: (institution: string, page: number, size: number) =>
    ['sender-numbers', { institution, page, size }] as const,
  /** 문자내역 조회 / message-history search */
  messageHistory: (query: MessageHistoryQuery) => ['message-history', 'search', query] as const,
  /** 문자상세내역 / message detail */
  messageDetail: (row: MessageHistoryRow) =>
    [
      'message-history',
      'detail',
      {
        messageType: row.messageType,
        tableType: row.tableType,
        messageKey: row.messageKey,
        // 그리드가 표시용으로 잘라낸 8자리가 아니라 원본 14자리가 행을 식별한다.
        // The raw 14-digit value identifies the row, not the 8 digits the grid displays.
        requestDate: row.requestDate,
        status: row.status,
      },
    ] as const,
} as const;

/**
 * 이용기관을 조회한다. / Searches institutions.
 *
 * req: FR-INST-001, FR-INST-003
 *
 * @param query 조회 조건 / the criteria
 * @returns 조회 결과 / the query result
 */
export function useInstitutionSearch(query: InstitutionQuery): UseQueryResult<InstitutionPage> {
  return useQuery({
    queryKey: biztalkKeys.institutionSearch(query),
    queryFn: () => searchInstitutions(query),
  });
}

/** 이용기관 선택 상자에 채울 최대 건수. / Cap on the institutions offered in the picker. */
const INSTITUTION_OPTION_LIMIT = 200;

/**
 * 이용기관 선택 상자의 선택지를 가져온다. / Loads the options for the institution picker.
 *
 * req: FR-SND-002, FR-SND-010
 *
 * <p>목록 조회와 <b>다른 키</b>를 쓴다. 선택지는 상태와 무관하게 전 기관을 담고, 목록은
 * 사용자가 고른 조건을 담는다 — 같은 키를 공유하면 한쪽의 조건이 다른 쪽 화면을 바꾼다.</p>
 * <p>A different key from the search: the picker holds every institution regardless of status,
 * while the search holds the user's criteria. Sharing one key would let either change the other.</p>
 *
 * @returns 선택지 조회 결과 / the query result
 */
export function useInstitutionOptions(): UseQueryResult<InstitutionPage> {
  const query: InstitutionQuery = { status: 'ALL', page: 0, size: INSTITUTION_OPTION_LIMIT };
  return useQuery({
    queryKey: biztalkKeys.institutionSearch(query),
    queryFn: () => searchInstitutions(query),
  });
}

/**
 * 이용기관의 발신번호를 조회한다. / Lists an institution's sender numbers.
 *
 * req: FR-SND-001, FR-SND-003
 *
 * <p>기관을 고르기 전에는 {@code enabled: false} 로 아예 조회를 만들지 않는다(D-S19).
 * API 계층도 같은 판단을 하지만, 쿼리 자체가 생기지 않아야 "조회 중" 상태도 나타나지 않는다.</p>
 * <p>Before an institution is chosen the query is not created at all (D-S19). The API client
 * makes the same judgement, but not creating the query also means no loading state appears.</p>
 *
 * @param institution 이용기관 코드 / the institution code
 * @param page        페이지 번호 / the zero-based page
 * @param size        페이지 크기 / the page size
 * @returns 조회 결과 / the query result
 */
export function useSenderNumbers(
  institution: string,
  page: number,
  size: number,
): UseQueryResult<SenderNumberPage> {
  return useQuery({
    queryKey: biztalkKeys.senderNumbers(institution, page, size),
    queryFn: () => listSenderNumbers({ institution, page, size }),
    enabled: institution !== '',
  });
}

/**
 * 문자내역을 조회한다. / Searches the message history.
 *
 * req: FR-MSG-002, FR-MSG-007
 *
 * <p>{@code null} 을 주면 조회하지 않는다. 이 화면은 진입만으로 조회하지 않는다 — 기본
 * 조건으로 한 번 조회해 두면 사용자가 요청하지 않은 개인정보 조회가 감사 로그에 남는다.</p>
 * <p>A {@code null} criteria means no search. The screen does not search on entry: running a
 * default search would put a PII read the user never asked for into the audit log.</p>
 *
 * @param query 확정된 조회 조건, 아직 조회하지 않았으면 null / the committed criteria, or null
 * @returns 조회 결과 / the query result
 */
export function useMessageHistorySearch(
  query: MessageHistoryQuery | null,
): UseQueryResult<MessageHistoryPage> {
  return useQuery({
    // 비활성 상태에서도 키는 있어야 한다. 조회 전에는 실행되지 않으므로 값은 쓰이지 않는다.
    // A key is required even while disabled; it is never used because the query does not run.
    queryKey: biztalkKeys.messageHistory(query ?? ({ from: '', to: '' } as MessageHistoryQuery)),
    queryFn: () => searchMessageHistory(query as MessageHistoryQuery),
    enabled: query !== null,
  });
}

/**
 * 문자상세내역을 조회한다. / Looks up a message detail record.
 *
 * req: FR-MSGD-001, FR-MSG-014
 *
 * @param row 선택된 목록 행 / the selected list row
 * @returns 조회 결과 / the query result
 */
export function useMessageDetail(row: MessageHistoryRow): UseQueryResult<MessageDetail> {
  return useQuery({
    queryKey: biztalkKeys.messageDetail(row),
    queryFn: () => fetchMessageDetail(row),
  });
}

/**
 * 조회 결과를 CSV 로 내려받는다. / Downloads the result as CSV.
 *
 * req: FR-MSG-017
 *
 * <p>조회가 아니라 <b>동작</b>이므로 mutation 이다. 결과는 파일이고 캐시할 것이 없다 —
 * 같은 조건으로 두 번 누르면 두 번 받아야 하며, 두 번째에 캐시된 파일을 주면 그 사이의
 * 변경이 빠진 파일을 받게 된다.</p>
 * <p>A mutation rather than a query: the result is a file and there is nothing to cache. Pressing
 * it twice must download twice — serving a cached file the second time would hand back one that
 * predates any change in between.</p>
 *
 * @returns 내보내기 mutation / the export mutation
 */
export function useMessageExport(): UseMutationResult<void, Error, MessageHistoryQuery> {
  return useMutation({
    mutationFn: (query: MessageHistoryQuery) => exportMessageHistory(query),
  });
}
