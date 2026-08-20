import { useEffect, useMemo, useRef, useState } from 'react';
import {
  SenderNumberWriteError,
  type SenderNumberRegistration,
} from '../../api/senderNumberApi';
import { useSenderNumberContext, useSenderNumberRegister } from './queries';

/**
 * 발신번호 등록 팝업. / The sender-number register popup.
 *
 * req: FR-SNDC-001, FR-SNDC-002, FR-SNDC-003, FR-SNDC-011, FR-SNDC-012, FR-SNDC-013,
 *      FR-SNDC-014, FR-SND-012, NFR-USE-D02
 * source: biztalk_admin_12_view.jsp (폼 레이아웃·고지문), biztalk_admin_12.js (loadData, btn_save)
 *
 * <h2>레거시 로직을 기준으로 한다 / the legacy logic is the baseline</h2>
 * <p>PM 지시(2026-08-20)에 따라 화면 12 의 <b>필드 구성·순서·고지문</b>을 그대로 옮긴다. 담는
 * 그릇만 바뀐다 — {@code window.open} 팝업이 아니라 같은 문서 안의 모달이다
 * (ADR-SND-020). 레거시 팝업이 실제로 보장한 것은 세 가지였고 모달이 그 셋을 그대로 갖는다:
 * 목록이 고른 이용기관 없이는 열리지 않는다, 그 기관은 폼에서 읽기 전용이다, 완료되면 뒤의
 * 목록이 다시 조회된다.</p>
 * <p>Per the PM directive the field set, order and stated rules of screen 12 are carried across
 * unchanged; only the container differs — a modal in the same document rather than a
 * {@code window.open} popup (ADR-SND-020). What the legacy popup actually guaranteed was three
 * things, and the modal keeps all three: it cannot open without the institution the list selected,
 * that institution is read-only on the form, and completing it re-queries the list behind.</p>
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>고지된 세 규칙이 실제로 적용된다</b> — 레거시는 특수번호 규칙을 어느 계층에도
 *       구현하지 않았고(D-S12) 숫자 검사도 서버에 없었다(D-S13). 화면이 말하는 것과 서버가
 *       하는 것이 같아야 한다는 요구가 FR-SNDC-013 이다</li>
 *   <li><b>사유가 필수다</b> — PM 결정 AMB-S10. 레거시에도 칸은 있었으나 클라이언트 검증이
 *       존재하지 않는 요소를 검사했으므로 빈 값이 저장되었다(D-S11)</li>
 *   <li><b>거절되어도 폼이 닫히지 않고 입력이 남는다</b> — 레거시는 규칙과 무관하게
 *       {@code jex.alert('등록중 오류 발생.')} 한 문장만 보여 주었다(FR-SNDC-014)</li>
 *   <li><b>이용기관 문맥이 코드와 이름만 온다</b> — 레거시는 이름을 채우려고 상세조회를
 *       불러 평문 인증키까지 브라우저로 가져왔다(D-S18)</li>
 *   <li><b>인증번호 칸이 없다</b> — 소유 인증은 구현하지 않는다(AMB-S01). 레거시 JSP 에는
 *       주석 처리된 칸과 '인증번호전송' 버튼이 남아 있었고, 계약에는 {@code AUTH_NO} 가
 *       선언되어 있었다 — 있는 통제로 오해되는 상태다(D-S4)</li>
 * </ul>
 *
 * <h2>왜 검증 규칙을 여기에 복제하지 않는가 / why the rules are not duplicated here</h2>
 * <p>자릿수·접두어·특수번호·길이 판정은 서버에만 있다. 화면에 같은 규칙을 한 벌 더 두면 두 벌이
 * 갈라지고, 갈라졌을 때 <b>느슨한 쪽이 이긴다</b>. 레거시가 그 극단이었다 — 규칙이 브라우저에만
 * 있었고 그 검사조차 존재하지 않는 요소를 보았으므로 <b>아무 규칙도 없었다</b>(D-S11).
 * {@code maxLength} 는 입력 편의이며 규칙이 아니고, 판정은 등록 요청의 응답이 한다.</p>
 * <p>Digits, prefix, barred numbers and lengths live on the server only. A second copy here would
 * drift, and when it drifts <b>the looser side wins</b> — the legacy was the extreme case: the rules
 * were in the browser and even those tested non-existent elements, so there were <b>no rules</b>
 * (D-S11). {@code maxLength} is a typing affordance, not a rule; the verdict comes from the response.</p>
 */

/** 입력 편의를 위한 상한 — 규칙은 서버가 갖는다. / Typing affordances; the rules are the server's. */
const MAX = { number: 20, description: 200, reason: 100 } as const;

/**
 * 레거시 화면이 운영자에게 고지한 세 규칙. / The three rules the legacy screen stated.
 *
 * <p>문구를 그대로 옮긴다(PM 지시). 그리고 <b>셋 다 서버가 실제로 적용한다</b> — 레거시는 두
 * 번째 규칙을 어디에도 구현하지 않았다(D-S12). 화면이 말하는 규칙과 서버가 적용하는 규칙이
 * 일치해야 한다는 것이 FR-SNDC-013 이며, 여기 문구를 고치려면 서버도 함께 고쳐야 한다.</p>
 * <p>Carried across verbatim (PM directive), and <b>all three are actually enforced</b>: the legacy
 * implemented the second in no layer at all (D-S12). FR-SNDC-013 requires the stated rules and the
 * enforced rules to be the same set, so changing this text means changing the server too.</p>
 */
// source: biztalk_admin_12_view.jsp — ul.infoList01
// req: FR-SNDC-006, FR-SNDC-010, FR-SNDC-013
const STATED_RULES: readonly string[] = [
  '8~11자리 번호여야 합니다.(030, 050 시작하는 경우 12자리까지 가능)',
  '112,114,1335 와 같은 특수번호는 등록 불가능합니다.',
  '15xx, 16xx 같은 대표번호 서비스인 경우 전체 번호 수가 8자리인 경우에만 등록 가능합니다.',
];

/** 포커스를 가둘 대상 선택자. / Selector for the elements focus is kept within. */
const FOCUSABLE =
  'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]';

/** 빈 폼. / An empty form. */
const EMPTY: SenderNumberRegistration = { number: '', description: '', reason: '' };

interface Props {
  /** 등록 대상 이용기관 — 목록이 고른 값 / the institution the list selected */
  institution: string;
  /** 팝업을 닫을 때 / called when the popup closes */
  onClose: () => void;
  /** 등록에 성공했을 때 / called after a successful registration */
  onRegistered?: () => void;
}

/**
 * 발신번호 등록 팝업 컴포넌트. / The register popup component.
 *
 * @param props 이용기관과 콜백 / the institution and the callbacks
 * @returns 팝업 / the popup
 */
export function SenderNumberRegisterDialog({ institution, onClose, onRegistered }: Props) {
  const context = useSenderNumberContext(institution);
  const register = useSenderNumberRegister();

  const [form, setForm] = useState<SenderNumberRegistration>(EMPTY);

  const dialogRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);
  const focusMoved = useRef(false);

  /*
    열릴 때 포커스를 대화상자로 옮기고, 닫을 때 열었던 곳으로 되돌린다. 내용이 오기 전에
    옮기는 것이 중요하다 — 조회를 기다리면 그 사이 Esc 가 동작하지 않고 스크린리더는 무엇이
    열렸는지 듣지 못한다. 되돌리지 않으면 키보드 사용자가 문서 처음으로 튕겨 나가 방금 누른
    등록 버튼을 다시 찾아야 한다.

    Focus moves to the dialog on open and returns to the opener on close. Moving it before the
    content arrives matters: waiting for the read would leave Escape dead in the meantime and a
    screen reader would not hear what opened. Without the return, a keyboard user is thrown to the
    top of the document and has to find the 등록 button again.
  */
  useEffect(() => {
    openerRef.current = document.activeElement;
    dialogRef.current?.focus();
    return () => {
      (openerRef.current as HTMLElement | null)?.focus?.();
    };
  }, []);

  // 폼이 처음 그려지면 첫 입력칸으로 옮긴다. 한 번만 한다.
  // Once the form first renders, focus moves to its first field — once only.
  useEffect(() => {
    if (!focusMoved.current && !context.isLoading) {
      focusMoved.current = true;
      dialogRef.current?.querySelector<HTMLElement>('#senderno-number')?.focus();
    }
  }, [context.isLoading]);

  const violations = useMemo(() => {
    const map = new Map<string, string>();
    if (register.error instanceof SenderNumberWriteError) {
      for (const violation of register.error.violations) {
        map.set(violation.field, violation.message);
      }
    }
    return map;
  }, [register.error]);

  function field(name: keyof SenderNumberRegistration, value: string) {
    setForm((previous) => ({ ...previous, [name]: value }));
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    register.mutate(
      { institution, body: form },
      {
        onSuccess: () => {
          onRegistered?.();
          onClose();
        },
        /*
          실패 시에는 <b>아무것도 하지 않는다</b> — 닫지 않고, 입력을 비우지도 않는다.
          FR-SNDC-014 가 요구하는 동작이며, 11자리 번호를 다시 입력하게 만드는 것은
          사유가 한 자 길었다는 이유로 받아들일 만한 결과가 아니다.
          On failure <b>nothing happens</b>: the dialog does not close and the input is not cleared
          (FR-SNDC-014). Re-typing an 11-digit number because the reason was one character too long
          is not an acceptable outcome.
        */
      },
    );
  }

  /**
   * 팝업 안에서 키보드 이동을 처리한다. / Handles keyboard navigation inside the popup.
   *
   * Esc 로 닫고, Tab 은 팝업 안에서 순환한다. 순환시키지 않으면 포커스가 뒤의 목록으로 빠져나가
   * 무엇을 조작하는지 알 수 없게 된다 — 모달이라고 말하면서 그렇게 동작하지 않는 것은
   * 스크린리더 사용자에게 특히 문제가 된다.
   * Escape closes and Tab cycles within the popup. Without cycling, focus escapes to the list behind
   * and it stops being clear what is being operated — saying "modal" while not behaving as one is
   * worst for screen-reader users.
   */
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

  const saving = register.isPending;

  // 필드에 붙지 않는 실패만 상단에 보여 준다. 필드 오류는 해당 칸 옆에 있으므로 위에 또
  // 띄우면 같은 문장이 두 번 나온다.
  // Only failures with no field are shown at the top: a field error already sits beside its box, and
  // repeating it here would show the same sentence twice.
  const generalError =
    register.error && !(register.error instanceof SenderNumberWriteError)
      ? register.error.message
      : context.isError
        ? context.error instanceof Error
          ? context.error.message
          : '이용기관 정보를 조회할 수 없습니다.'
        : null;

  return (
    /*
      배경을 눌러도 닫히지 않는다. 입력 중인 폼에서 배경 클릭 한 번으로 편집이 사라지는 것은
      되돌릴 수 없는 손실이며, 레거시 팝업도 그렇게 동작하지 않았다. 닫는 방법은 닫기 버튼과
      Esc 두 가지다.
      A backdrop click does not close: losing a half-typed form to one stray click is an
      unrecoverable loss, and the legacy popup did not behave that way either.
    */
    <div className="lg-modal-backdrop">
      <div
        className="lg-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="senderno-register-heading"
        aria-busy={context.isLoading}
        tabIndex={-1}
        ref={dialogRef}
        onKeyDown={onKeyDown}
      >
        <div className="lg-modal-header">
          <h2 id="senderno-register-heading">발신번호 등록</h2>
        </div>

        {/*
          레거시 infoList01 — 문구를 그대로 옮긴다(PM 지시). 세 규칙 모두 서버가 적용한다
          (FR-SNDC-013): 레거시는 두 번째 규칙을 어디에도 구현하지 않았다(D-S12).
          The legacy infoList01, carried across verbatim. All three are enforced server-side
          (FR-SNDC-013); the legacy implemented the second in no layer (D-S12).
        */}
        <ul className="lg-info">
          {STATED_RULES.map((rule) => (
            <li key={rule}>{rule}</li>
          ))}
        </ul>

        {context.isLoading && (
          <p role="status" className="lg-loading">
            조회 중입니다.
          </p>
        )}

        {generalError && (
          <p role="alert" className="field-error visible">
            {generalError}
          </p>
        )}

        <form className="lg-modal-form" onSubmit={submit} aria-label="발신번호 등록">
          <div className="lg-form-grid">
            {/*
              이용기관코드·이용기관명은 읽기 전용이다(FR-SNDC-012). 레거시는 disabled 입력으로
              두었는데, 비활성 입력도 "고칠 수 있는 값" 으로 읽히므로 값으로만 표시한다.
              대상 기관은 폼이 아니라 질의 문자열이 정하고, 서버가 세션 권한으로 다시 판정한다 —
              레거시는 부모 창의 IS_CD 를 그대로 insert 에 넣었다.
              Read-only (FR-SNDC-012). The legacy used disabled inputs, but even a disabled input
              reads as an editable value, so these are plain text. The target comes from the query
              string and is re-decided by the server; the legacy put the opener's IS_CD straight into
              the insert.
            */}
            <span className="lg-form-label" id="senderno-institution-label">
              이용기관코드
            </span>
            <span className="lg-form-readonly" aria-labelledby="senderno-institution-label">
              {institution}
            </span>

            <span className="lg-form-label" id="senderno-institution-name-label">
              이용기관명
            </span>
            <span className="lg-form-readonly" aria-labelledby="senderno-institution-name-label">
              {context.data?.institutionName ?? ''}
            </span>

            <label htmlFor="senderno-number">
              발신번호 <span className="lg-required" aria-hidden="true">*</span>
            </label>
            <input
              id="senderno-number"
              type="text"
              inputMode="numeric"
              value={form.number}
              maxLength={MAX.number}
              required
              aria-required="true"
              aria-invalid={violations.has('number') || undefined}
              aria-describedby={violations.has('number') ? 'senderno-number-error' : undefined}
              /*
                레거시처럼 숫자만 남긴다. 판정은 서버가 한다(FR-SNDC-005) — 여기서 걸러 내는 것은
                입력 편의이고, 규칙이 아니다. 레거시의 같은 처리는 주석 처리되어 있었다.
                Non-digits are stripped as the legacy intended; the verdict is the server's
                (FR-SNDC-005). This is a typing affordance, not a rule — and the legacy's own version
                of it was commented out.
              */
              onChange={(e) => field('number', e.target.value.replace(/[^0-9]/g, ''))}
            />
            {violations.has('number') && (
              <p id="senderno-number-error" className="lg-field-error" role="alert">
                {violations.get('number')}
              </p>
            )}

            <label htmlFor="senderno-description">설명</label>
            <textarea
              id="senderno-description"
              className="lg-form-wide"
              rows={4}
              value={form.description}
              maxLength={MAX.description}
              aria-invalid={violations.has('description') || undefined}
              onChange={(e) => field('description', e.target.value)}
            />
            {violations.has('description') && (
              <p className="lg-field-error" role="alert">
                {violations.get('description')}
              </p>
            )}

            {/*
              사유는 필수다(PM 결정 AMB-S10, FR-SNDC-011). 레거시에도 칸은 있었으나 강제되지
              않았다 — 소유 인증이 없는 상태에서(RESIDUAL-S01) 사유는 운영자가 그 번호를 주장한
              유일한 근거 기록이다.
              Mandatory (PM ruling AMB-S10, FR-SNDC-011). The legacy had the field but never enforced
              it — and with no ownership verification (RESIDUAL-S01) the reason is the only record of
              the operator's basis for claiming the number.
            */}
            <label htmlFor="senderno-reason">
              사유 <span className="lg-required" aria-hidden="true">*</span>
            </label>
            <textarea
              id="senderno-reason"
              className="lg-form-wide"
              rows={3}
              value={form.reason}
              maxLength={MAX.reason}
              required
              aria-required="true"
              aria-invalid={violations.has('reason') || undefined}
              aria-describedby={violations.has('reason') ? 'senderno-reason-error' : undefined}
              onChange={(e) => field('reason', e.target.value)}
            />
            {violations.has('reason') && (
              <p id="senderno-reason-error" className="lg-field-error" role="alert">
                {violations.get('reason')}
              </p>
            )}
          </div>

          <div className="lg-modal-actions">
            <button type="submit" className="lg-btn lg-btn-primary" disabled={saving}>
              {saving ? '등록 중…' : '등록'}
            </button>
            <button type="button" className="lg-btn" onClick={onClose} disabled={saving}>
              닫기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
