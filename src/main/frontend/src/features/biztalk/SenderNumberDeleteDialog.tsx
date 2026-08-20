import { useEffect, useMemo, useRef, useState } from 'react';
import { SenderNumberWriteError, type SenderNumberRow } from '../../api/senderNumberApi';
import { useSenderNumberDelete } from './queries';

/**
 * 발신번호 제거 팝업. / The sender-number delete popup.
 *
 * req: FR-SNDD-002, FR-SNDD-004, FR-SNDD-006, FR-SNDD-007, FR-SNDD-009, FR-SND-007,
 *      FR-SND-012, NFR-USE-D01, NFR-USE-D02
 * source: biztalk_admin_13_view.jsp (폼 레이아웃), biztalk_admin_10.js (btn_delete)
 *
 * <h2>이 화면이 지키는 등식 / the equality this screen keeps</h2>
 * <p><b>열거된 집합 = 지워지는 집합</b>(FR-SNDD-009). 목록이 서버 페이징이므로 화면에 보이는 행과
 * 선택된 행은 <b>다를 수 있다</b> — 1페이지에서 두 건을 고르고 3페이지로 넘어간 뒤 삭제를 누르는
 * 일이 가능하다. 그 두 건은 지금 화면에 없지만 지워져야 하고, 지워질 것이므로 <b>여기서 반드시
 * 열거되어야 한다.</b></p>
 * <p><b>The enumerated set equals the deleted set</b> (FR-SNDD-009). The list is server-paginated, so
 * the rows on screen and the rows selected <b>can differ</b>: an operator may select two rows on page
 * 1, move to page 3 and press 삭제. Those two are not on screen but will be deleted — so they must be
 * enumerated here.</p>
 *
 * <p>레거시에서는 이 구분이 존재할 수 없었다. 그리드가 전체 결과를 브라우저에 들고 있었으므로
 * (D-S14) 선택된 행은 언제나 화면에 있었다. 그 결함을 고치면서(서버 페이징) 이 가능성이
 * 생겼다 — <b>D-S1 의 계열이 우리 수정을 통해 다른 문으로 들어온 것</b>이다: 운영자가 보지 못한
 * 것에 조작이 걸린다.</p>
 * <p>The legacy could not have this distinction: its grid held the whole result set in the browser
 * (D-S14), so a selected row was always on screen. Fixing that defect with server-side paging created
 * the possibility — <b>D-S1's family entering through our own fix</b>: an operation applied to
 * something the operator never saw.</p>
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>보내는 값이 ref 다</b> — 레거시는 그리드가 가진 값을 콤마로 이어 하나의
 *       {@code DP_NO} 로 보냈고, 목록이 그 값을 마스킹하기 시작한 뒤로는 아무 행도 지우지 못한 채
 *       성공을 보고했다(D-S1)</li>
 *   <li><b>선택 건수를 함께 보여 준다</b> — 몇 건을 지우려는지가 확인의 핵심이다</li>
 *   <li><b>인증번호 칸이 없다</b> — 소유 인증은 구현하지 않는다(AMB-S01). 레거시 JSP 에는
 *       주석 처리된 칸과, 존재하지 않는 요소에 붙은 '인증번호전송' 핸들러가 남아 있었다(D-S4, D-S20)</li>
 *   <li><b>사유는 원래도 필수였다</b>(FR-SNDD-006). 달라진 것은 서버가 실제로 강제한다는 점이다</li>
 * </ul>
 */

/** 입력 편의를 위한 상한 — 규칙은 서버가 갖는다. / A typing affordance; the rule is the server's. */
const REASON_MAX = 100;

/** 포커스를 가둘 대상 선택자. / Selector for the elements focus is kept within. */
const FOCUSABLE =
  'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]';

interface Props {
  /**
   * 삭제 대상 — 목록에서 선택된 <b>모든</b> 행. 지금 화면에 보이지 않는 행도 포함한다.
   * The targets: <b>every</b> row selected in the list, including rows not currently displayed.
   */
  targets: SenderNumberRow[];
  /** 팝업을 닫을 때 / called when the popup closes */
  onClose: () => void;
  /** 삭제에 성공했을 때 / called after a successful deletion */
  onDeleted?: (affected: number) => void;
}

/**
 * 발신번호 제거 팝업 컴포넌트. / The delete popup component.
 *
 * @param props 대상과 콜백 / the targets and the callbacks
 * @returns 팝업 / the popup
 */
export function SenderNumberDeleteDialog({ targets, onClose, onDeleted }: Props) {
  const remove = useSenderNumberDelete();
  const [reason, setReason] = useState('');

  const dialogRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);

  useEffect(() => {
    openerRef.current = document.activeElement;
    dialogRef.current?.focus();
    return () => {
      (openerRef.current as HTMLElement | null)?.focus?.();
    };
  }, []);

  // 사유 칸으로 포커스를 옮긴다 — 운영자가 여기서 해야 하는 일은 하나뿐이다.
  // Focus moves to the reason field: there is exactly one thing to do here.
  useEffect(() => {
    dialogRef.current?.querySelector<HTMLElement>('#senderno-delete-reason')?.focus();
  }, []);

  const violations = useMemo(() => {
    const map = new Map<string, string>();
    if (remove.error instanceof SenderNumberWriteError) {
      for (const violation of remove.error.violations) {
        map.set(violation.field, violation.message);
      }
    }
    return map;
  }, [remove.error]);

  function submit(event: React.FormEvent) {
    event.preventDefault();
    remove.mutate(
      // 표시되는 번호가 아니라 ref 를 보낸다. 이 한 줄이 D-S1 의 재발을 막는다.
      // Refs are sent, never the displayed numbers. This line is what keeps D-S1 from returning.
      { refs: targets.map((row) => row.ref), reason },
      {
        onSuccess: (result) => {
          onDeleted?.(result.affected);
          onClose();
        },
      },
    );
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const focusable = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [],
    );
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    }
  }

  const deleting = remove.isPending;

  // 대상 기관. 선택은 기관을 바꿀 때 비워지므로(FR-SNDD-011) 보통 한 곳이지만, 값을
  // 가정하지 않고 실제로 확인한다.
  // The target institutions. The selection is cleared when the institution changes
  // (FR-SNDD-011) so this is normally one, but the value is checked rather than assumed.
  const institutions = Array.from(new Set(targets.map((row) => row.institutionName ?? '')));

  const generalError =
    remove.error && !(remove.error instanceof SenderNumberWriteError)
      ? remove.error.message
      : (violations.get('refs') ?? null);

  return (
    <div className="lg-modal-backdrop">
      <div
        className="lg-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="senderno-delete-heading"
        tabIndex={-1}
        ref={dialogRef}
        onKeyDown={onKeyDown}
      >
        <div className="lg-modal-header">
          <h2 id="senderno-delete-heading">발신번호 제거</h2>
        </div>

        {/*
          삭제가 무엇을 뜻하는지 적는다. 레거시 화면에는 이 문장이 없었고, 실제로는 아무 일도
          일어나지 않았으므로(D-S1) 경고할 것도 없었다. 이제는 실제로 지워지므로 결과를 말한다 —
          발송 능력이 사라진다는 것이 운영자가 알아야 하는 결과다(위협 T-D4).
          What deletion means is spelled out. The legacy screen said nothing, and since nothing
          actually happened (D-S1) there was nothing to warn about. It now takes effect, so the
          consequence is stated: the ability to send from these numbers goes away (threat T-D4).
        */}
        <ul className="lg-info">
          <li>
            제거하면 해당 발신번호로는 <strong>즉시 발송할 수 없습니다.</strong>
          </li>
          <li>제거된 발신번호는 이력과 함께 보관되며, 필요하면 다시 등록할 수 있습니다.</li>
        </ul>

        {generalError && (
          <p role="alert" className="field-error visible">
            {generalError}
          </p>
        )}

        <form className="lg-modal-form" onSubmit={submit} aria-label="발신번호 제거">
          <div className="lg-form-grid">
            <span className="lg-form-label" id="senderno-delete-institution-label">
              이용기관
            </span>
            <span
              className="lg-form-readonly"
              aria-labelledby="senderno-delete-institution-label"
            >
              {institutions.join(', ')}
            </span>

            {/*
              열거가 이 화면의 요점이다(FR-SNDD-007, FR-SNDD-009). 건수만 보여 주면 다른 페이지에서
              고른 행을 운영자가 확인할 방법이 없고, 그때 삭제는 <b>보지 못한 것에</b> 걸린다.
              그래서 <b>줄이지 않고</b> 전부 적는다 — 100건이면 100줄이며, 스크롤이 요약보다 낫다.
              The enumeration is the point of this screen (FR-SNDD-007, FR-SNDD-009). A count alone
              would leave rows chosen on another page unverifiable, and the delete would then apply to
              <b>something unseen</b>. Every one is listed, <b>never abbreviated</b>: 100 targets mean
              100 lines, and scrolling beats summarising.
            */}
            <span className="lg-form-label" id="senderno-delete-targets-label">
              삭제번호
            </span>
            <div className="lg-form-readonly" aria-labelledby="senderno-delete-targets-label">
              <p className="field-help" data-testid="senderno-delete-count">
                선택 {targets.length}건
              </p>
              <ul className="lg-target-list">
                {targets.map((row) => (
                  <li key={row.ref}>{row.number}</li>
                ))}
              </ul>
            </div>

            <label htmlFor="senderno-delete-reason">
              사유 <span className="lg-required" aria-hidden="true">*</span>
            </label>
            <textarea
              id="senderno-delete-reason"
              className="lg-form-wide"
              rows={3}
              value={reason}
              maxLength={REASON_MAX}
              required
              aria-required="true"
              aria-invalid={violations.has('reason') || undefined}
              aria-describedby={
                violations.has('reason') ? 'senderno-delete-reason-error' : undefined
              }
              onChange={(e) => setReason(e.target.value)}
            />
            {violations.has('reason') && (
              <p id="senderno-delete-reason-error" className="lg-field-error" role="alert">
                {violations.get('reason')}
              </p>
            )}
          </div>

          <div className="lg-modal-actions">
            <button
              type="submit"
              className="lg-btn lg-btn-primary"
              disabled={deleting || targets.length === 0}
            >
              {deleting ? '삭제 중…' : '삭제'}
            </button>
            <button type="button" className="lg-btn" onClick={onClose} disabled={deleting}>
              닫기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
