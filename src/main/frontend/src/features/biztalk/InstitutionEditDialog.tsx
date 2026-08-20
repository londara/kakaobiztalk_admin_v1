import { useEffect, useMemo, useRef, useState } from 'react';
import {
  InstitutionValidationError,
  type InstitutionRow,
  type InstitutionUpdate,
} from '../../api/institutionApi';
import { useAuthKeyRotation, useInstitutionDetail, useInstitutionUpdate } from './queries';

/**
 * 이용기관 수정 팝업. / The 이용기관 edit popup.
 *
 * req: FR-INSTC-001, FR-INSTC-002, FR-INSTC-003, FR-INSTC-010, FR-INSTC-011, FR-INSTC-014,
 *      FR-INSTC-015, FR-ATK-002, FR-ATK-005, FR-AZ-I01
 * source: biztalk_admin_01_view.jsp (폼 레이아웃), biztalk_admin_01.js (loadData, fn_save,
 *         btn_generate_code)
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>인증키가 마스킹되어 표시된다</b> — 레거시는 상세조회가 반환한 평문을 입력칸에
 *       넣었고, 그 서비스는 로그인만 요구했다(D-I20)</li>
 *   <li><b>키 재발급이 서버에서 이루어지고 확인 즉시 확정된다</b> — 레거시는 브라우저의
 *       {@code Math.random()} 으로 만들어 저장 시 기록했다(D-I4, AMB-I13)</li>
 *   <li><b>이용기관코드는 읽기 전용이며 중복검사 버튼이 없다</b> — 코드는 불변이므로
 *       (FR-INSTC-002) 그 버튼은 항상 아무 일도 하지 않는다. 레거시는 버튼을
 *       {@code disabled} 로 두었지만, 동작 없는 버튼을 그리지 않는 것이 이 화면들의
 *       규칙이다(D-I13 에서 배운 것)</li>
 *   <li><b>검증 결과가 해당 칸 옆에 표시된다</b> — 레거시는 {@code alert()} 을 연달아
 *       띄웠고 규칙은 전부 브라우저에만 있었다(D-I19)</li>
 *   <li><b>사용여부에 '삭제'가 없다</b> — 논리 삭제는 자기 자신의 조작이다(FR-INSTC-015)</li>
 * </ul>
 * <ul>
 *   <li>The 인증키 is shown masked; the legacy put the plaintext from a login-only service into a
 *       field (D-I20)</li>
 *   <li>Rotation happens on the server and commits on confirmation; the legacy used browser
 *       {@code Math.random()} and persisted at 저장 (D-I4, AMB-I13)</li>
 *   <li>기관코드 is read-only and there is no 중복검사 button: the code is immutable
 *       (FR-INSTC-002), so that button could never do anything</li>
 *   <li>Validation messages sit beside their field instead of a chain of {@code alert()} calls,
 *       and the rules are the server's (D-I19)</li>
 *   <li>사용여부 offers no 삭제 — logical delete is its own operation (FR-INSTC-015)</li>
 * </ul>
 *
 * <h2>왜 검증 규칙을 여기에 복제하지 않는가 / why the rules are not duplicated here</h2>
 * <p>길이와 형식 규칙은 서버에만 있다. 화면에 같은 규칙을 한 벌 더 두면 두 벌이 갈라지고,
 * 갈라졌을 때 <b>느슨한 쪽이 이긴다</b> — 그것이 레거시가 겪은 일이다(D-I19: 화면은 6자,
 * 계약은 16자). {@code maxLength} 는 입력 편의이며 규칙이 아니고, 판정은 저장 요청의
 * 응답이 한다.</p>
 * <p>Length and format rules live on the server only. A second copy on the screen would drift, and
 * when it drifts <b>the looser side wins</b> — which is what the legacy had (the form said 6
 * characters, the contract said 16). {@code maxLength} here is a typing affordance, not a rule; the
 * verdict comes from the save response.</p>
 */

/** 사용여부 선택지 — 'D' 는 없다. / Status options; 'D' is absent. */
// req: FR-INSTC-015, ADR-INST-014
const STATUS_OPTIONS: ReadonlyArray<{ value: 'Y' | 'N'; label: string }> = [
  { value: 'Y', label: '사용' },
  { value: 'N', label: '미사용' },
];

/** 입력 편의를 위한 상한 — 규칙은 서버가 갖는다. / Typing affordances; the rules are the server's. */
const MAX = { name: 100, englishName: 100, businessNumber: 10, description: 650 } as const;

/** 포커스를 가둘 대상 선택자. / Selector for the elements focus is kept within. */
const FOCUSABLE =
  'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]';

interface Props {
  /** 수정할 이용기관 코드 / the institution code to edit */
  code: string;
  /** 팝업을 닫을 때 / called when the popup closes */
  onClose: () => void;
  /** 저장에 성공했을 때 — 목록 화면이 알림을 표시한다 / called after a successful save */
  onSaved?: (row: InstitutionRow) => void;
}

/** 폼 상태를 조회 결과에서 만든다. / Builds the form state from the loaded row. */
function toForm(row: InstitutionRow): InstitutionUpdate {
  return {
    name: row.name ?? '',
    englishName: row.englishName ?? '',
    businessNumber: row.businessNumber ?? '',
    // 저장된 값이 'Y'/'N' 이 아닐 수도 있다(FR-INST-006 이 그런 값을 원문으로 보여주는
    // 이유). 선택 상자에는 표현할 수 없으므로 '미사용' 으로 열되, 그 사실을 감추지 않기
    // 위해 원본 라벨을 안내문에 남긴다.
    // A stored value may be neither 'Y' nor 'N' — the reason FR-INST-006 shows such values
    // verbatim. A select cannot represent it, so the form opens on 미사용 and the note keeps the
    // original visible rather than hiding it.
    status: row.status === 'Y' ? 'Y' : 'N',
    description: row.description ?? '',
  };
}

/**
 * 이용기관 수정 팝업 컴포넌트. / The edit popup component.
 *
 * @param props 코드와 닫기 콜백 / the code and the close callback
 * @returns 팝업 / the popup
 */
export function InstitutionEditDialog({ code, onClose, onSaved }: Props) {
  const detail = useInstitutionDetail(code);
  const save = useInstitutionUpdate();
  const rotation = useAuthKeyRotation();

  const [form, setForm] = useState<InstitutionUpdate | null>(null);
  const [loaded, setLoaded] = useState<InstitutionUpdate | null>(null);

  /*
    재발급된 평문 키. 이 값은 <b>화면에만</b> 존재한다 — 쿼리 캐시에도, localStorage 에도,
    저장 요청에도 담기지 않는다. 서버는 이 값을 다시 보여주지 않으므로(FR-ATK-003 의
    reveal 은 별개 조작이다) 운영자가 지금 고객사에 전달해야 한다(RISK-I15).

    The rotated plaintext key. It exists <b>on screen only</b>: not in the query cache, not in
    storage, and not in any save payload. The server will not show it again — reveal is a separate
    operation — so the operator has to pass it on now (RISK-I15).
  */
  const [issuedKey, setIssuedKey] = useState<string | null>(null);

  /** 진행 중인 확인 절차. / The confirmation currently in progress. */
  const [confirming, setConfirming] = useState<'rotate' | 'close' | null>(null);

  const dialogRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);
  const focusMoved = useRef(false);

  // 조회가 끝나면 폼을 채운다. 같은 코드로 다시 조회되어도(재발급 후 무효화) 이미 편집
  // 중인 값을 덮어쓰지 않는다 — 운영자가 입력하던 내용이 사라지는 것이 더 나쁘다.
  // The form is filled once the read completes. A refetch of the same code — after a rotation
  // invalidates it — does not overwrite what is being edited: losing typed input is worse.
  useEffect(() => {
    if (detail.data && form === null) {
      const initial = toForm(detail.data);
      setForm(initial);
      setLoaded(initial);
    }
  }, [detail.data, form]);

  /*
    열릴 때 포커스를 대화상자로 옮기고, 닫을 때 열었던 곳으로 되돌린다.

    <b>내용이 오기 전에</b> 옮기는 것이 중요하다. 조회가 끝날 때까지 기다리면 그 사이 Esc 가
    동작하지 않고(키 이벤트가 대화상자에 닿지 않는다) 스크린리더는 무엇이 열렸는지 듣지
    못한다. 그래서 컨테이너 자체가 {@code tabIndex=-1} 로 포커스를 받는다.

    되돌리지 않으면 키보드 사용자가 문서 처음으로 튕겨 나가 방금 누른 기관코드 링크를 다시
    찾아야 한다.

    Focus moves to the dialog on open and returns to the opener on close.

    Moving it <b>before the content arrives</b> matters: waiting for the read would leave Escape
    dead in the meantime — the key event would never reach the dialog — and a screen reader would
    not hear what opened. Hence the container itself takes focus via {@code tabIndex=-1}.

    Without the return, a keyboard user is thrown to the top of the document and has to find the
    기관코드 link again.
  */
  useEffect(() => {
    openerRef.current = document.activeElement;
    dialogRef.current?.focus();
    return () => {
      (openerRef.current as HTMLElement | null)?.focus?.();
    };
  }, []);

  // 폼이 처음 그려지면 첫 입력칸으로 옮긴다. 한 번만 한다 — 재발급 후 상세를 다시 읽을 때
  // 커서가 입력 중인 칸에서 튀어나가면 안 된다.
  // Once the form first renders, focus moves to its first field — once only, so that re-reading
  // the record after a rotation does not yank the cursor out of the field being typed in.
  useEffect(() => {
    if (form !== null && !focusMoved.current) {
      focusMoved.current = true;
      dialogRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    }
  }, [form]);

  const violations = useMemo(() => {
    const map = new Map<string, string>();
    if (save.error instanceof InstitutionValidationError) {
      for (const violation of save.error.violations) {
        map.set(violation.field, violation.message);
      }
    }
    return map;
  }, [save.error]);

  /** 저장되지 않은 변경이 있는지. / Whether there are unsaved changes. */
  const dirty =
    form !== null && loaded !== null && JSON.stringify(form) !== JSON.stringify(loaded);

  function field(name: keyof InstitutionUpdate, value: string) {
    setForm((previous) => (previous === null ? previous : { ...previous, [name]: value }));
  }

  /**
   * 닫기를 시도한다. / Attempts to close.
   *
   * 저장하지 않은 변경이 있으면 한 번 확인한다. 레거시는 곧바로 닫아 입력을 잃게 했다.
   * Confirms once when there are unsaved changes; the legacy closed immediately and lost them.
   */
  function attemptClose() {
    if (dirty) {
      setConfirming('close');
      return;
    }
    onClose();
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (form === null) {
      return;
    }
    save.mutate(
      { code, body: form },
      {
        onSuccess: (row) => {
          onSaved?.(row);
          onClose();
        },
      },
    );
  }

  function confirmRotation() {
    setConfirming(null);
    rotation.mutate(code, {
      onSuccess: (key) => setIssuedKey(key),
    });
  }

  /**
   * 팝업 안에서 키보드 이동을 처리한다. / Handles keyboard navigation inside the popup.
   *
   * Esc 로 닫고, Tab 은 팝업 안에서 순환한다. 순환시키지 않으면 포커스가 뒤의 목록 화면으로
   * 빠져나가 무엇을 조작하는지 알 수 없게 된다 — 모달이라고 말하면서 그렇게 동작하지 않는
   * 것은 스크린리더 사용자에게 특히 문제가 된다.
   * Escape closes and Tab cycles within the popup. Without cycling, focus escapes to the list
   * behind and it stops being clear what is being operated — saying "modal" while not behaving as
   * one is worst for screen-reader users.
   */
  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      attemptClose();
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

  const saving = save.isPending;
  const rotating = rotation.isPending;

  const generalError =
    save.error && !(save.error instanceof InstitutionValidationError)
      ? save.error.message
      : rotation.error
        ? rotation.error.message
        : detail.isError
          ? detail.error instanceof Error
            ? detail.error.message
            : '이용기관을 조회할 수 없습니다.'
          : null;

  return (
    /*
      배경을 눌러도 닫히지 않는다. 입력 중인 폼에서 배경 클릭 한 번으로 편집이 사라지는 것은
      되돌릴 수 없는 손실이며, 레거시 팝업도 그렇게 동작하지 않았다. 닫는 방법은 닫기 버튼과
      Esc 두 가지이고 둘 다 저장하지 않은 변경을 확인한다.

      A backdrop click does not close: losing a half-typed form to one stray click is an
      unrecoverable loss, and the legacy popup did not behave that way either. The two ways out are
      the 닫기 button and Escape, and both check for unsaved changes.
    */
    <div className="lg-modal-backdrop">
      <div
        className="lg-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="institution-edit-heading"
        aria-busy={detail.isLoading}
        // 조회가 끝나기 전에도 포커스를 받을 수 있어야 한다 — 그래야 Esc 가 곧바로 동작한다.
        // Focusable before the read finishes, so Escape works from the moment it opens.
        tabIndex={-1}
        ref={dialogRef}
        onKeyDown={onKeyDown}
      >
        <div className="lg-modal-header">
          <h2 id="institution-edit-heading">이용기관 수정</h2>
        </div>

        {detail.isLoading && (
          <p role="status" className="lg-loading">
            조회 중입니다.
          </p>
        )}

        {generalError && (
          <p role="alert" className="field-error visible">
            {generalError}
          </p>
        )}

        {/*
          재발급된 키는 한 번만 나온다. 안내문이 그 사실과 지금 해야 할 일을 함께 말한다 —
          "복사해 두라" 가 아니라 "고객사에 전달해야 한다" 가 실제 할 일이다.
          The rotated key appears once. The note says both that fact and what to do now: the task
          is not "copy this" but "give this to the customer".
        */}
        {issuedKey && (
          <div className="lg-modal-notice" role="alert">
            <p>
              <strong>새 인증키가 발급되었습니다.</strong> 이 값은 <strong>다시 표시되지
              않습니다.</strong> 이용기관에 전달하기 전까지 해당 기관의 발송이 실패합니다.
            </p>
            <label htmlFor="institution-issued-key">발급된 인증키</label>
            <input
              id="institution-issued-key"
              className="secret"
              type="text"
              value={issuedKey}
              readOnly
            />
          </div>
        )}

        {form !== null && (
          <form className="lg-modal-form" onSubmit={submit} aria-label="이용기관 수정">
            <div className="lg-form-grid">
              {/*
                이용기관코드는 읽기 전용이다(FR-INSTC-002). 입력 요소로 두면 비활성이어도
                "고칠 수 있는 값" 으로 읽히므로, 값으로만 표시하고 form 에 담지 않는다 —
                대상은 경로가 정한다.
                The code is read-only (FR-INSTC-002). An input, even disabled, reads as an
                editable value, so it is displayed as text and is not part of the payload: the
                path names the target.
              */}
              <span className="lg-form-label" id="institution-code-label">
                이용기관코드
              </span>
              {/*
                {@code <output>} 를 쓰지 않는다 — 그 요소는 암묵적으로 라이브 리전
                ({@code role="status"})이므로, 바뀌지 않는 값에 쓰면 스크린리더가 맥락 없이
                읽어 준다. 읽기 전용 표시는 평범한 텍스트가 정직한 마크업이다.
                Not an {@code <output>}: that element is an implicit live region
                ({@code role="status"}), so using it for a value that never changes makes a screen
                reader announce it out of context. Plain text is the honest markup for a read-only
                display.
              */}
              <span className="lg-form-readonly" aria-labelledby="institution-code-label">
                {code}
              </span>

              <label htmlFor="institution-name">
                이용기관명 <span className="lg-required" aria-hidden="true">*</span>
              </label>
              <input
                id="institution-name"
                type="text"
                value={form.name}
                maxLength={MAX.name}
                required
                aria-required="true"
                aria-invalid={violations.has('name') || undefined}
                aria-describedby={violations.has('name') ? 'institution-name-error' : undefined}
                onChange={(e) => field('name', e.target.value)}
              />
              {violations.has('name') && (
                <p id="institution-name-error" className="lg-field-error" role="alert">
                  {violations.get('name')}
                </p>
              )}

              <label htmlFor="institution-english-name">
                이용기관영문명 <span className="lg-required" aria-hidden="true">*</span>
              </label>
              <input
                id="institution-english-name"
                type="text"
                value={form.englishName}
                maxLength={MAX.englishName}
                required
                aria-required="true"
                aria-invalid={violations.has('englishName') || undefined}
                aria-describedby={
                  violations.has('englishName') ? 'institution-english-name-error' : undefined
                }
                onChange={(e) => field('englishName', e.target.value)}
              />
              {violations.has('englishName') && (
                <p id="institution-english-name-error" className="lg-field-error" role="alert">
                  {violations.get('englishName')}
                </p>
              )}

              <label htmlFor="institution-business-number">
                사업자등록번호 <span className="lg-required" aria-hidden="true">*</span>
              </label>
              <input
                id="institution-business-number"
                type="text"
                inputMode="numeric"
                value={form.businessNumber}
                maxLength={MAX.businessNumber}
                required
                aria-required="true"
                aria-invalid={violations.has('businessNumber') || undefined}
                aria-describedby={
                  violations.has('businessNumber')
                    ? 'institution-business-number-error'
                    : 'institution-business-number-help'
                }
                /*
                  레거시처럼 숫자만 남긴다. 레거시 정규식은 소수점을 허용했고
                  ({@code [^0-9.]}) 사업자등록번호에 소수점이 들어갈 자리는 없다 — 여기서는
                  숫자만 남긴다. 판정은 서버가 한다(FR-INSTC-016).
                  Non-digits are stripped as in the legacy, whose expression also allowed a decimal
                  point that a registration number has no place for. The verdict is the server's.
                */
                onChange={(e) => field('businessNumber', e.target.value.replace(/[^0-9]/g, ''))}
              />
              {violations.has('businessNumber') ? (
                <p id="institution-business-number-error" className="lg-field-error" role="alert">
                  {violations.get('businessNumber')}
                </p>
              ) : (
                <p id="institution-business-number-help" className="field-help">
                  숫자 10자리
                </p>
              )}

              {/*
                인증키. 마스킹된 값이며 편집 대상이 아니다(FR-INSTC-010). 옆의 버튼은
                레거시의 '키 생성' 자리에 있지만 <b>다른 조작</b>이다 — 서버가 만들고 확인
                즉시 확정되며, 그래서 이름도 '재발급' 이다(FR-INSTC-011).
                The 인증키: masked, and not editable (FR-INSTC-010). The button sits where the
                legacy's 키 생성 was but is a <b>different operation</b> — server-generated and
                committed on confirmation, which is why it is named 재발급 (FR-INSTC-011).
              */}
              <span className="lg-form-label" id="institution-key-label">
                인증키
              </span>
              <div className="lg-field-inline">
                <span className="lg-form-readonly secret" aria-labelledby="institution-key-label">
                  {detail.data?.authKeyMasked ?? ''}
                </span>
                <button
                  type="button"
                  className="lg-btn"
                  onClick={() => setConfirming('rotate')}
                  disabled={rotating || saving || confirming !== null}
                >
                  {rotating ? '재발급 중…' : '키 재발급'}
                </button>
              </div>

              <label htmlFor="institution-status">
                사용 여부 <span className="lg-required" aria-hidden="true">*</span>
              </label>
              <select
                id="institution-status"
                value={form.status}
                aria-invalid={violations.has('status') || undefined}
                onChange={(e) => field('status', e.target.value)}
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>

              <label htmlFor="institution-description">설명</label>
              <textarea
                id="institution-description"
                className="lg-form-wide"
                rows={8}
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
            </div>

            {/*
              확인 절차. {@code window.confirm} 을 쓰지 않는다 — 브라우저 대화상자는 스타일도
              위치도 제어할 수 없고, 무엇보다 <b>무엇을 확인하는지</b>를 팝업의 맥락 안에서
              보여줄 수 없다. 여기서는 결과를 문장으로 적는다.
              Confirmations do not use {@code window.confirm}: a browser dialog cannot be styled or
              placed, and above all cannot show <b>what is being confirmed</b> in the popup's own
              context. The consequence is spelled out instead.
            */}
            {confirming === 'rotate' && (
              <div className="lg-modal-confirm" role="alert">
                <p>
                  인증키를 재발급하면 <strong>즉시 적용</strong>되며, 현재 키로 연동 중인 발송은
                  새 키를 전달하기 전까지 실패합니다. 저장 버튼과 무관하게 바로 확정됩니다.
                </p>
                <div className="lg-modal-confirm-actions">
                  <button type="button" className="lg-btn lg-btn-primary" onClick={confirmRotation}>
                    재발급
                  </button>
                  <button type="button" className="lg-btn" onClick={() => setConfirming(null)}>
                    취소
                  </button>
                </div>
              </div>
            )}

            {confirming === 'close' && (
              <div className="lg-modal-confirm" role="alert">
                <p>저장하지 않은 변경이 있습니다. 닫으면 변경은 사라집니다.</p>
                <div className="lg-modal-confirm-actions">
                  <button type="button" className="lg-btn lg-btn-primary" onClick={onClose}>
                    닫기
                  </button>
                  <button type="button" className="lg-btn" onClick={() => setConfirming(null)}>
                    계속 편집
                  </button>
                </div>
              </div>
            )}

            <div className="lg-modal-actions">
              <button type="submit" className="lg-btn lg-btn-primary" disabled={saving}>
                {saving ? '저장 중…' : '저장'}
              </button>
              <button type="button" className="lg-btn" onClick={attemptClose} disabled={saving}>
                닫기
              </button>
            </div>
          </form>
        )}

        {/*
          폼이 없을 때도 닫는 길이 보여야 한다. 조회가 실패하면 채울 폼이 없고, 그 상태에서
          동작 줄까지 사라지면 <b>화면에 보이는 출구가 없는 대화상자</b>가 남는다 — Esc 는
          동작하지만 그것을 아는 사용자만 나올 수 있다. 배경 클릭으로도 닫지 않으므로
          (편집 내용 보호) 이 버튼이 유일한 시각적 출구다.
          A way out must be visible even without a form. A failed read leaves nothing to fill in,
          and if the action row disappears too what remains is a <b>dialog with no visible exit</b>
          — Escape works, but only for someone who knows that. A backdrop click does not close it
          either (edits are protected), so this button is the visible exit.
        */}
        {form === null && (
          <div className="lg-modal-actions">
            <button type="button" className="lg-btn" onClick={onClose}>
              닫기
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
