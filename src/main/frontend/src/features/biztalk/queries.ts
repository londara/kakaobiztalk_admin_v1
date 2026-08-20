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

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import {
  fetchInstitution,
  rotateAuthKey,
  searchInstitutions,
  updateInstitution,
  type InstitutionPage,
  type InstitutionQuery,
  type InstitutionRow,
  type InstitutionUpdate,
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
import {
  deleteSenderNumbers,
  fetchSenderNumberContext,
  listSenderNumbers,
  registerSenderNumber,
  type SenderNumberContext,
  type SenderNumberPage,
  type SenderNumberRegistration,
  type SenderNumberWriteResult,
} from '../../api/senderNumberApi';

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
  /** 이용기관 상세 — 수정 팝업이 여는 값 / institution detail, opened by the edit popup */
  institutionDetail: (code: string) => ['institutions', 'detail', code] as const,
  /** 발신번호 목록 / sender-number list */
  senderNumbers: (institution: string, page: number, size: number) =>
    ['sender-numbers', { institution, page, size }] as const,
  /** 등록 화면이 여는 이용기관 문맥 / the institution context the register form opens with */
  senderNumberContext: (institution: string) =>
    ['sender-numbers', 'context', institution] as const,
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

/**
 * 이용기관 한 건을 조회한다 — 수정 팝업. / Reads one institution, for the edit popup.
 *
 * req: FR-INSTC-001, FR-INSTC-010
 *
 * <p>목록 행을 그대로 폼에 채우지 않고 다시 조회한다. 목록은 페이지 캐시에 남아 있으므로
 * 다른 사람이 그 사이 고친 값을 모른다 — 팝업을 열 때 조회하면 <b>지금</b>의 값을 고치게 된다.
 * 레거시도 같은 선택을 했다({@code loadData()} 가 {@code _l002} 를 호출한다).</p>
 * <p>Re-read rather than filling the form from the list row: the list sits in a page cache and does
 * not know about someone else's edit since, so reading on open means editing what is there
 * <b>now</b>. The legacy made the same choice — its {@code loadData()} called the detail service.</p>
 *
 * @param code 기관코드, 팝업이 닫혀 있으면 null / the code, or null while the popup is closed
 * @returns 조회 결과 / the query result
 */
export function useInstitutionDetail(code: string | null): UseQueryResult<InstitutionRow> {
  return useQuery({
    // 비활성 상태에서도 키는 필요하다. 조회가 실행되지 않으므로 값은 쓰이지 않는다.
    // A key is required even while disabled; the query does not run, so the value is unused.
    queryKey: biztalkKeys.institutionDetail(code ?? ''),
    queryFn: () => fetchInstitution(code as string),
    enabled: code !== null,
    // 팝업을 열 때마다 서버에 묻는다. 자격증명과 사용여부를 다루는 화면에서 캐시된 값을
    // 고치기 시작하면, 마지막 쓰기가 이기는 편집을 하게 된다(TM-I019).
    // Asks the server each time the popup opens: on a screen that governs a credential and a
    // status, editing a cached copy is how a last-write-wins edit happens (TM-I019).
    staleTime: 0,
  });
}

/**
 * 이용기관 수정을 저장한다. / Saves an 이용기관 edit.
 *
 * req: FR-INSTC-004, FR-INSTC-008
 *
 * <p>성공하면 <b>목록과 상세를 모두 무효화</b>한다. 이것이 FR-INSTC-008 이 이 시스템에서
 * 뜻하는 전부다 — 레거시가 갱신한 캐시는 IRIS_ADMIN 프로세스 안에 있어 포털이 닿을 수 없고,
 * 포털은 서버 측 기관 캐시를 두지 않는다(PM 결정 AMB-I11). 레거시 런타임 캐시의 지연은
 * RISK-I02·RISK-I13 으로 추적한다.</p>
 * <p>On success it invalidates <b>both the list and the detail</b>, which is all FR-INSTC-008 can
 * mean in this system: the cache the legacy refreshed lives inside the IRIS_ADMIN process, out of
 * reach, and the portal keeps no server-side institution cache (PM ruling AMB-I11). The legacy
 * runtime's staleness is tracked as RISK-I02 / RISK-I13.</p>
 *
 * <p>실패는 <b>무효화하지 않는다.</b> 저장되지 않은 변경을 반영하려 목록을 다시 불러오면
 * 사용자가 방금 무엇을 했는지와 화면이 보여주는 것이 어긋난다.</p>
 * <p>A failure invalidates nothing: refetching the list for a change that was not stored would
 * leave the screen disagreeing with what the user just did.</p>
 *
 * @returns 저장 mutation / the save mutation
 */
export function useInstitutionUpdate(): UseMutationResult<
  InstitutionRow,
  Error,
  { code: string; body: InstitutionUpdate }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, body }) => updateInstitution(code, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['institutions'] });
    },
  });
}

/**
 * 인증키를 재발급한다. / Rotates the 인증키.
 *
 * req: FR-ATK-001, FR-ATK-005, FR-INSTC-011
 *
 * <p>조회가 아니라 <b>동작</b>이므로 mutation 이다. 그리고 캐시하지 않는다 — 같은 기관에
 * 두 번 요청하면 두 개의 다른 키가 발급되어야 하며, 두 번째에 캐시된 값을 주면 화면이
 * 저장되지 않은 키를 보여주게 된다.</p>
 * <p>A mutation rather than a query, and never cached: two requests must issue two different keys,
 * and serving a cached one the second time would show a key that is not what is stored.</p>
 *
 * <p>반환된 평문은 <b>여기서 캐시하지 않는다</b>. 화면이 한 번 보여주고 잊는다 —
 * 쿼리 캐시에 자격증명을 남기면 그것이 또 하나의 노출 경로가 된다(FR-ATK-004).</p>
 * <p>The returned plaintext is <b>not cached here</b>. The screen shows it once and forgets it:
 * leaving a credential in the query cache would be one more exposure path (FR-ATK-004).</p>
 *
 * @returns 재발급 mutation / the rotation mutation
 */
export function useAuthKeyRotation(): UseMutationResult<string, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => rotateAuthKey(code),
    onSuccess: () => {
      // 재발급은 LAST_AMDT 도 바꾼다 — 목록의 수정일시가 낡는다.
      // Rotation also changes LAST_AMDT, so the list's 수정일시 goes stale.
      void queryClient.invalidateQueries({ queryKey: ['institutions'] });
    },
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
 * 등록 화면의 이용기관 문맥을 가져온다. / Loads the register form's institution context.
 *
 * req: FR-SNDC-002, FR-SNDC-012
 *
 * <p>목록 화면이 이미 기관명을 갖고 있으므로 그것을 넘겨도 될 것처럼 보이지만, 다시 조회한다 —
 * 이용기관 슬라이스가 수정 팝업에서 내린 것과 같은 판단이다. 목록은 페이지 캐시에 남아 있어
 * 그 사이 바뀐 기관명을 모르고, 등록 폼은 <b>지금</b>의 기관에 등록하는 화면이다. 레거시도 같은
 * 선택을 했다({@code loadData()}) — 다만 인증키까지 받아왔다(D-S18).</p>
 * <p>The list already holds the name and could pass it down, but it is re-read — the same judgement the
 * institution slice made for its edit popup. The list sits in a page cache and does not know about a
 * rename since, and the register form registers against the institution as it is <b>now</b>. The
 * legacy made the same choice, but received the 인증키 with it (D-S18).</p>
 *
 * @param institution 이용기관 코드, 팝업이 닫혀 있으면 null / the code, or null while the dialog is closed
 * @returns 조회 결과 / the query result
 */
export function useSenderNumberContext(
  institution: string | null,
): UseQueryResult<SenderNumberContext> {
  return useQuery({
    // 비활성 상태에서도 키는 필요하다. 조회가 실행되지 않으므로 값은 쓰이지 않는다.
    // A key is required even while disabled; the query does not run, so the value is unused.
    queryKey: biztalkKeys.senderNumberContext(institution ?? ''),
    queryFn: () => fetchSenderNumberContext(institution as string),
    enabled: institution !== null && institution !== '',
  });
}

/**
 * 발신번호를 등록한다. / Registers a sender number.
 *
 * req: FR-SNDC-001, FR-SND-012
 *
 * <p>성공하면 <b>발신번호 목록 전체를 무효화</b>한다 — 현재 페이지만이 아니다. 정렬이
 * 등록일시 내림차순이므로(FR-SND-004) 새 행은 1페이지 맨 위에 오고, 그러면 모든 페이지의 내용이
 * 한 칸씩 밀린다. 레거시는 {@code opener.getDat()} 로 부모 창을 다시 조회했고, 그것이 이
 * 시스템에서 뜻하는 바가 이 무효화다(FR-SND-012).</p>
 * <p>On success the <b>whole sender-number list is invalidated</b>, not just the current page: the
 * order is 등록일시 descending (FR-SND-004), so a new row lands at the top of page 1 and every page's
 * contents shift by one. The legacy re-queried the opener with {@code opener.getDat()}, and this
 * invalidation is what that means in this system (FR-SND-012).</p>
 *
 * <p>실패는 <b>무효화하지 않는다.</b> 저장되지 않은 변경을 반영하려 목록을 다시 불러오면
 * 사용자가 방금 무엇을 했는지와 화면이 보여주는 것이 어긋난다.</p>
 * <p>A failure invalidates nothing: refetching for a change that was not stored would leave the screen
 * disagreeing with what the user just did.</p>
 *
 * @returns 등록 mutation / the register mutation
 */
export function useSenderNumberRegister(): UseMutationResult<
  SenderNumberWriteResult,
  Error,
  { institution: string; body: SenderNumberRegistration }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ institution, body }) => registerSenderNumber(institution, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sender-numbers'] });
    },
  });
}

/**
 * 선택한 발신번호를 삭제한다. / Deletes the selected sender numbers.
 *
 * req: FR-SNDD-002, FR-SNDD-009, FR-SND-012
 *
 * <p>보내는 것은 <b>ref 목록</b>이며 표시되는 번호가 아니다. 표시값을 식별자로 쓴 것이 D-S1 의
 * 직접 원인이었다 — 목록이 마스킹을 시작한 순간 삭제가 아무 행도 지우지 못한 채 성공을 보고했다.</p>
 * <p>What is sent is a <b>list of refs</b>, never the displayed numbers: using a display value as an
 * identifier is the direct cause of D-S1 — the moment the list began masking, deletion stopped
 * matching anything while still reporting success.</p>
 *
 * <p>성공 시 목록을 무효화한다. 선택 해제는 화면이 한다 — 캐시가 아니라 화면의 상태이기
 * 때문이다(FR-SND-012).</p>
 * <p>On success the list is invalidated. Clearing the selection is the screen's job, because the
 * selection is screen state rather than cache (FR-SND-012).</p>
 *
 * @returns 삭제 mutation / the delete mutation
 */
export function useSenderNumberDelete(): UseMutationResult<
  SenderNumberWriteResult,
  Error,
  { refs: string[]; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refs, reason }) => deleteSenderNumbers(refs, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sender-numbers'] });
    },
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
