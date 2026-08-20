import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  composeBatchPayload,
  composePayload,
  listTemplates,
  previewRecipients,
  readSendReadiness,
  validateAgainstRegistry,
  validateTemplate,
  type ButtonInput,
  type ComposeResponse,
  type RecipientPreviewResponse,
  type RegisteredValidateResponse,
  type ValidateResponse,
} from '../../api/alimTalkApi';
import { AlimTalkButtons } from './AlimTalkButtons';
import { useInstitutionOptions } from './queries';

/**
 * 카카오 알림톡 템플릿. / Kakao AlimTalk template screen.
 *
 * req: FR-ATC-001, FR-ATC-002, FR-ATC-004, FR-ATC-005, FR-ATC-007, FR-ATC-009, FR-ATC-010,
 *      FR-ATC-012, FR-ATT-001, FR-ATT-002, FR-ATV-001, FR-ATV-003, FR-ATV-006, FR-ATV-007,
 *      FR-ATS-003, FR-ATS-007, FR-AZ-A05, NFR-SEC-PII-A01, NFR-USE-A01, NFR-USE-A03,
 *      NFR-COMPAT-A02
 * source: biztalk_admin_61_view.jsp, biztalk_admin_61.js
 *
 * <h2>레거시 배치를 따른다 / the legacy arrangement is followed</h2>
 * 라벨을 블록으로 두고 입력을 전체 폭으로 아래에 놓는 레거시 `.form-group` 배치, 필수 항목의
 * 빨간 별표, 필드명을 그대로 쓴 placeholder, 탭 셋, 하단의 색 구분된 `JSON 생성 / 초기화 /
 * 복사` 와 출력 textarea — 모두 레거시와 같다. 정의는 화면 안이 아니라 공유 `lg-*` 클래스에
 * 둔다(D-A22).
 *
 * The legacy `.form-group` arrangement — block label, full-width control beneath — the red required
 * markers, the field-name placeholders, the three tabs, and the colour-coded action row with its
 * output textarea all follow the legacy. The definitions live in the shared `lg-*` classes rather
 * than inside the screen (D-A22).
 *
 * <h2>다건 발송이 여기 있는 이유 / why batch composition is here</h2>
 * 이 탭을 한 번 "A2-10 에서 제공됩니다" 로 미뤄 두었는데, 그것은 <b>잘못된 판단</b>이었다.
 * 미뤄야 할 이유로 들었던 것은 항목별 {@code order} 인데, 그 필드는 이미 DTO 에 있고
 * `ContractConformanceTest` 가 항목마다 존재를 단언한다. 게다가 FR-ATC-004 는 순번을
 * <b>시스템이</b> 부여하도록 정하므로 벤더에게 물을 것이 없다. A2-10 에 속하는 것은 배치
 * <b>발송</b>이지 <b>조립</b>이 아니다.
 *
 * This tab was once deferred to "A2-10", which was <b>wrong</b>. The reason given was the per-item
 * {@code order} — but that field is already in the DTO and `ContractConformanceTest` asserts it on every
 * item, and FR-ATC-004 has the <b>system</b> assign it, so there is nothing to ask the vendor. What
 * belongs to A2-10 is batch <b>despatch</b>, not batch <b>composition</b>.
 */

type Tab = 'single' | 'multi' | 'validate';
type Emphasis = 'none' | 'emphasize' | 'itemlist' | 'image';
type Failback = '' | 'SMS' | 'LMS' | 'MMS';

/** 다건 메시지 데이터 한 건. / One batch message item. */
interface ItemState {
  recipient: string;
  senderNumber: string;
  reqdate: string;
  templateCode: string;
  msgType: 'AT' | 'FT' | 'AI';
  emphasis: Emphasis;
  templateTitle: string;
  msg: string;
  buttons: ButtonInput[];
  failbackType: Failback;
  failbackSubject: string;
  failbackMsg: string;
  failbackImgId: string;
}

const EMPTY_ITEM: ItemState = {
  recipient: '',
  senderNumber: '',
  reqdate: '',
  templateCode: '',
  msgType: 'AT',
  emphasis: 'none',
  templateTitle: '',
  msg: '',
  buttons: [],
  failbackType: '',
  failbackSubject: '',
  failbackMsg: '',
  failbackImgId: '',
};

interface FormState {
  isCd: string;
  tranId: string;
  /**
   * 수신번호 행 — 레거시와 같이 한 행에 한 번호. / recipient rows, one number per row as in the legacy.
   *
   * source: biztalk_admin_61_view.jsp #receiverNumberContainer — `.receiver-number` 반복 행
   * source: biztalk_admin_61.js:451 — `querySelectorAll('.receiver-number').map(trim).filter(≠'')`
   */
  recipients: string[];
  senderNumber: string;
  reqdate: string;
  templateCode: string;
  msgType: 'AT' | 'FT' | 'AI';
  emphasis: Emphasis;
  templateTitle: string;
  msg: string;
  buttons: ButtonInput[];
  failbackType: Failback;
  failbackSubject: string;
  failbackMsg: string;
  failbackImgId: string;
  multiIsCd: string;
  multiTranId: string;
  items: ItemState[];
  manualTemplate: string;
  manualContent: string;
}

const INITIAL: FormState = {
  isCd: '',
  tranId: '',
  recipients: [''],
  senderNumber: '',
  reqdate: '',
  templateCode: '',
  msgType: 'AT',
  emphasis: 'none',
  templateTitle: '',
  msg: '',
  buttons: [],
  failbackType: '',
  failbackSubject: '',
  failbackMsg: '',
  failbackImgId: '',
  multiIsCd: '',
  multiTranId: '',
  items: [],
  manualTemplate: '',
  manualContent: '',
};

/** 오류 메시지를 뽑아낸다. / Extracts a message from an unknown error. */
function messageOf(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    return String((error as { message: unknown }).message);
  }
  return '요청을 처리하지 못했습니다.';
}

/** 필수 표시. / The required marker. */
function Required() {
  return <span className="lg-required" aria-hidden="true">*</span>;
}

export function AlimTalkPage() {
  const [tab, setTab] = useState<Tab>('single');
  const [form, setForm] = useState<FormState>(INITIAL);

  /**
   * 행들을 서버가 받는 한 문자열로 합친다. / joins the rows into the single string the server parses.
   *
   * 레거시는 빈 행을 버리고 나머지를 배열로 만들었다(`filter(value => value !== '')`). 여기서는
   * 같은 규칙으로 버린 뒤 쉼표로 잇는다 — `RecipientParser` 가 구분자로 다시 나누므로 결과는
   * 같고, 한 행에 여러 번호를 <b>붙여넣는</b> 경우까지 함께 처리된다. 레거시는 그 입력을
   * 번호 하나로 취급해 형식 오류로 떨어뜨렸다.
   *
   * The legacy dropped empty rows and built an array (`filter(value => value !== '')`). We drop by the
   * same rule and join with a comma — `RecipientParser` splits on delimiters again, so the outcome is
   * identical, and a <b>paste</b> of several numbers into one row is handled too. The legacy treated
   * such an input as a single number and rejected it as malformed.
   *
   * source: biztalk_admin_61.js:451-453
   * req: FR-ATC-003, FR-ATC-010
   */
  const recipientsJoined = form.recipients
    .map((r) => r.trim())
    .filter((r) => r !== '')
    .join(',');
  const [composed, setComposed] = useState<ComposeResponse | null>(null);
  const [validation, setValidation] = useState<RegisteredValidateResponse | null>(null);
  const [manual, setManual] = useState<ValidateResponse | null>(null);
  const [preview, setPreview] = useState<RecipientPreviewResponse | null>(null);
  const [copied, setCopied] = useState(false);

  const institutions = useInstitutionOptions();
  const activeInstitution = tab === 'multi' ? form.multiIsCd : form.isCd;

  const readiness = useQuery({
    queryKey: ['alimtalk', 'send-readiness', activeInstitution],
    queryFn: () => readSendReadiness(activeInstitution),
    enabled: activeInstitution.trim() !== '',
  });

  const templates = useQuery({
    queryKey: ['alimtalk', 'templates', activeInstitution],
    queryFn: () => listTemplates(activeInstitution),
    enabled: activeInstitution.trim() !== '',
  });

  const compose = useMutation({
    mutationFn: () =>
      composePayload({
        isCd: form.isCd,
        tranId: form.tranId,
        recipients: recipientsJoined,
        senderNumber: form.senderNumber,
        reqdate: form.reqdate,
        templateCode: form.templateCode,
        templateTitle: form.emphasis === 'emphasize' ? form.templateTitle : '',
        msg: form.msg,
        buttons: form.buttons,
        failback: {
          type: form.failbackType,
          subject: form.failbackSubject,
          msg: form.failbackMsg,
          imgId: form.failbackImgId,
        },
      }),
    onSuccess: (r) => {
      setComposed(r);
      setCopied(false);
    },
  });

  const composeBatch = useMutation({
    mutationFn: () =>
      composeBatchPayload({
        isCd: form.multiIsCd,
        tranId: form.multiTranId,
        // 순번을 보내지 않는다 — 서버가 부여한다(FR-ATC-004). 브라우저가 매긴 번호를 서버가
        // 믿으면 배열 위치에 의존하던 D-A3 이 형태만 바꿔 돌아온다.
        // No order is sent: the server assigns it (FR-ATC-004). Trusting a browser-chosen number would
        // return D-A3's array-position dependence in another shape.
        items: form.items.map((item) => ({
          recipient: item.recipient,
          senderNumber: item.senderNumber,
          reqdate: item.reqdate,
          templateCode: item.templateCode,
          templateTitle: item.emphasis === 'emphasize' ? item.templateTitle : '',
          msg: item.msg,
          buttons: item.buttons,
          failback: {
            type: item.failbackType,
            subject: item.failbackSubject,
            msg: item.failbackMsg,
            imgId: item.failbackImgId,
          },
        })),
      }),
    onSuccess: (r) => {
      setComposed(r);
      setCopied(false);
    },
  });

  const validateRegistered = useMutation({
    mutationFn: () =>
      validateAgainstRegistry({
        institutionCode: form.isCd,
        templateCode: form.templateCode,
        content: form.msg,
      }),
    onSuccess: setValidation,
  });

  const validateManual = useMutation({
    mutationFn: () =>
      validateTemplate({ template: form.manualTemplate, content: form.manualContent }),
    onSuccess: setManual,
  });

  const checkRecipients = useMutation({
    mutationFn: () => previewRecipients({ institutionCode: form.isCd, recipients: recipientsJoined }),
    onSuccess: (result) => {
      setPreview(result);
      if (result.tranId && form.tranId.trim() === '') {
        setForm((c) => ({ ...c, tranId: result.tranId as string }));
      }
    },
  });

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((c) => ({ ...c, [key]: value }));
    setComposed(null);
  };

  const setItem = (index: number, patch: Partial<ItemState>) => {
    setForm((c) => ({
      ...c,
      items: c.items.map((it, i) => (i === index ? { ...it, ...patch } : it)),
    }));
    setComposed(null);
  };

  /**
   * 폼 전체를 초기값으로 되돌린다. / Restores the whole form.
   *
   * req: FR-ATC-010
   */
  const reset = () => {
    setForm(INITIAL);
    setComposed(null);
    setValidation(null);
    setManual(null);
    setPreview(null);
    setCopied(false);
  };

  /**
   * 출력 JSON 을 클립보드로 복사한다. / Copies the output JSON.
   *
   * req: FR-ATC-013
   */
  const copy = async () => {
    if (!composed?.payload) {
      return;
    }
    // 레거시는 폐기된 execCommand 를 쓰고 결과와 무관하게 성공을 알렸다(D-A20).
    try {
      await navigator.clipboard.writeText(composed.payload);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const selectedTemplate = templates.data?.find((t) => t.templateCode === form.templateCode);
  const emphasisUnavailable = form.emphasis === 'itemlist' || form.emphasis === 'image';

  const recipientsHint =
    form.isCd === ''
      ? '이용기관코드를 먼저 선택하세요.'
      : recipientsJoined === ''
        ? '수신번호를 입력하세요.'
        : '';
  const validateHint =
    form.templateCode === ''
      ? '템플릿코드를 먼저 선택하세요.'
      : form.msg.trim() === ''
        ? '메시지를 입력하세요.'
        : '';

  const institutionOptions = (institutions.data?.rows ?? []).map((row) => (
    <option key={row.code} value={row.code}>
      {row.code} · {row.name ?? ''}
    </option>
  ));

  return (
    <main className="page-wrap">
      <div className="lg-title">
        <h1>카카오 알림톡 템플릿</h1>
        <span className="lg-breadcrumb">
          BIZTALK <span aria-hidden="true">›</span> <strong>템플릿 샘플 검증</strong>
        </span>
      </div>

      <div className="lg-tabs">
        <button
          type="button"
          className={tab === 'single' ? 'lg-tab active' : 'lg-tab'}
          aria-pressed={tab === 'single'}
          onClick={() => setTab('single')}
        >
          단건 발송
        </button>
        <button
          type="button"
          className={tab === 'multi' ? 'lg-tab active' : 'lg-tab'}
          aria-pressed={tab === 'multi'}
          onClick={() => setTab('multi')}
        >
          다건 발송
        </button>
        <button
          type="button"
          className={tab === 'validate' ? 'lg-tab active' : 'lg-tab'}
          aria-pressed={tab === 'validate'}
          onClick={() => setTab('validate')}
        >
          검증
        </button>
      </div>

      {tab === 'single' ? (
        <section className="lg-form" aria-label="단건 발송">
          <div className="lg-stack">
            <label htmlFor="atk-is-cd">
              이용기관코드
              <Required />
            </label>
            <select
              id="atk-is-cd"
              value={form.isCd}
              onChange={(e) => {
                setForm((c) => ({ ...c, isCd: e.target.value, templateCode: '' }));
                setComposed(null);
                setValidation(null);
              }}
            >
              <option value="">is_cd</option>
              {institutionOptions}
            </select>
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-tran-id">
              거래고유번호
              <Required />
            </label>
            <input
              id="atk-tran-id"
              type="text"
              maxLength={10}
              placeholder="tran_id"
              value={form.tranId}
              onChange={(e) => set('tranId', e.target.value)}
            />
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-recipients">
              수신번호
              <Required />
            </label>
            {form.recipients.map((value, index) => (
              <div className="lg-row" key={index}>
                <input
                  id={index === 0 ? 'atk-recipients' : `atk-recipients-${index}`}
                  type="text"
                  placeholder="receiver_number"
                  aria-label={`수신번호 ${index + 1}`}
                  value={value}
                  onChange={(e) => {
                    const next = form.recipients.map((r, i) => (i === index ? e.target.value : r));
                    set('recipients', next);
                    setPreview(null);
                  }}
                />
                {/* 레거시는 행이 하나뿐일 때도 삭제 버튼을 보였고, 누르면 입력 자체가 사라져
                    복구할 방법이 없었다. 마지막 행에서는 감춘다.
                    The legacy showed 삭제 even on the only row; pressing it removed the input with no
                    way back. Hidden on the last remaining row. */}
                {form.recipients.length > 1 ? (
                  <button
                    type="button"
                    className="lg-btn lg-btn-warn"
                    aria-label={`수신번호 ${index + 1} 삭제`}
                    onClick={() => {
                      set(
                        'recipients',
                        form.recipients.filter((_, i) => i !== index),
                      );
                      setPreview(null);
                    }}
                  >
                    삭제
                  </button>
                ) : null}
              </div>
            ))}
            <button
              type="button"
              className="lg-btn"
              onClick={() => set('recipients', [...form.recipients, ''])}
            >
              수신번호 추가
            </button>
            {recipientsHint ? <span className="field-help">{recipientsHint}</span> : null}
            <button
              type="button"
              className="lg-btn"
              onClick={() => checkRecipients.mutate()}
              disabled={recipientsHint !== '' || checkRecipients.isPending}
            >
              {checkRecipients.isPending ? '확인 중…' : '수신번호 확인'}
            </button>
          </div>

          {checkRecipients.isError ? (
            <p role="alert" className="field-error visible" data-testid="recipients-error">
              {messageOf(checkRecipients.error)}
            </p>
          ) : null}

          {preview ? (
            <div data-testid="recipient-preview">
              <p className="lg-result-line">
                <strong>발송 대상 {preview.validCount}건</strong>
                {preview.duplicatesRemoved > 0 ? (
                  <span>중복 {preview.duplicatesRemoved}건 제거</span>
                ) : null}
              </p>
              {preview.requiresConfirmation ? (
                <>
                  <p role="alert" className="field-error visible">
                    {preview.excluded.length}건은 형식 오류로 제외됩니다.
                  </p>
                  <ul data-testid="excluded">
                    {preview.excluded.map((v) => (
                      <li key={v}>{v}</li>
                    ))}
                  </ul>
                </>
              ) : null}
              <ul data-testid="masked-recipients">
                {preview.maskedRecipients.map((v) => (
                  <li key={v}>{v}</li>
                ))}
              </ul>
              {preview.tranId ? (
                <p className="field-help" data-testid="tran-id">
                  거래고유번호 {preview.tranId} 를 제안했습니다. 필요하면 수정할 수 있습니다.
                </p>
              ) : null}
            </div>
          ) : null}

          <div className="lg-stack">
            <label htmlFor="atk-sender-number">
              발신번호
              <Required />
            </label>
            <input
              id="atk-sender-number"
              type="text"
              maxLength={24}
              placeholder="sender_number"
              value={form.senderNumber}
              onChange={(e) => set('senderNumber', e.target.value)}
            />
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-reqdate">예약발송시간</label>
            <input
              id="atk-reqdate"
              type="text"
              maxLength={14}
              placeholder="reqdate (yyyyMMddHHmmss)"
              value={form.reqdate}
              onChange={(e) => set('reqdate', e.target.value)}
            />
          </div>

          {/*
            레거시에는 여기에 발신프로필키(sender_key) 입력란이 있었다. 그 키는 기관을 대신해
            발송할 권한 자체이므로 화면에서 제거했다 — 서버가 해결한다(D-A24, FR-AZ-A05).
            The legacy had a sender_key input here; it is the authority to send as the institution, so
            it is gone from the screen and resolved server-side.
          */}

          <div className="lg-stack">
            <label htmlFor="atk-template-code">
              템플릿코드
              <Required />
            </label>
            <select
              id="atk-template-code"
              value={form.templateCode}
              disabled={form.isCd === ''}
              onChange={(e) => {
                set('templateCode', e.target.value);
                setValidation(null);
              }}
            >
              <option value="">template_code</option>
              {(templates.data ?? []).map((t) => (
                <option key={t.templateCode} value={t.templateCode}>
                  {t.templateCode}
                  {t.templateTitle ? ` · ${t.templateTitle}` : ''}
                </option>
              ))}
            </select>
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-msg-type">메시지타입</label>
            <select
              id="atk-msg-type"
              value={form.msgType}
              onChange={(e) => set('msgType', e.target.value as FormState['msgType'])}
            >
              <option value="AT">알림톡 (AT)</option>
              <option value="FT">친구톡 (FT)</option>
              <option value="AI">이미지형 (AI)</option>
            </select>
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-emphasis">강조 유형</label>
            <select
              id="atk-emphasis"
              value={form.emphasis}
              onChange={(e) => set('emphasis', e.target.value as Emphasis)}
            >
              <option value="none">없음</option>
              <option value="emphasize">강조표기형</option>
              <option value="itemlist">아이템리스트형</option>
              <option value="image">이미지형</option>
            </select>
          </div>

          {form.emphasis === 'emphasize' ? (
            <div className="lg-stack">
              <label htmlFor="atk-template-title">
                강조표기 제목
                <Required />
              </label>
              <input
                id="atk-template-title"
                type="text"
                maxLength={50}
                placeholder="template_title"
                value={form.templateTitle}
                onChange={(e) => set('templateTitle', e.target.value)}
              />
            </div>
          ) : null}

          {emphasisUnavailable ? (
            <p role="alert" className="field-error visible" data-testid="emphasis-unavailable">
              이 강조 유형의 필드(kko_header · highlight · items · summary)는 계약에 정의되어
              있지 않아 payload 에 포함되지 않습니다. 벤더 명세 확보 후 제공됩니다 (AMB-A05).
            </p>
          ) : null}

          <div className="lg-stack">
            <label htmlFor="atk-msg">
              메시지
              <Required />
            </label>
            <textarea
              id="atk-msg"
              rows={4}
              maxLength={1000}
              placeholder="msg"
              value={form.msg}
              onChange={(e) => {
                set('msg', e.target.value);
                setValidation(null);
              }}
            />
            {validateHint ? <span className="field-help">{validateHint}</span> : null}
            <button
              type="button"
              className="lg-btn"
              onClick={() => validateRegistered.mutate()}
              disabled={validateHint !== '' || validateRegistered.isPending}
            >
              {validateRegistered.isPending ? '검증 중…' : '템플릿 검증'}
            </button>
          </div>

          {validateRegistered.isError ? (
            <p role="alert" className="field-error visible" data-testid="validate-error">
              {messageOf(validateRegistered.error)}
            </p>
          ) : null}

          {selectedTemplate?.templateTitle ? (
            <p className="field-help" data-testid="emphasis-title">
              등록 제목: {selectedTemplate.templateTitle}
            </p>
          ) : null}

          {validation ? (
            <p
              data-testid="validation-result"
              className={`lg-verdict ${validation.permitsSend ? 'ok' : 'fail'}`}
            >
              {!validation.registered
                ? '실패: 이 이용기관에 등록되지 않은 템플릿입니다.'
                : validation.permitsSend
                  ? '성공: 템플릿과 입력이 일치합니다.'
                  : `실패: 템플릿과 일치하지 않습니다 — ${validation.divergences.length}건`}
              {validation.registered && !validation.permitsSend ? (
                <ul data-testid="divergences">
                  {validation.divergences.map((d, i) => (
                    <li key={`${d.position}-${i}`}>
                      {d.position}번째 문자 (개행·띄어쓰기 1자리로 계산): {d.reason}
                    </li>
                  ))}
                </ul>
              ) : null}
            </p>
          ) : null}

          <AlimTalkButtons
            idPrefix="atk"
            buttons={form.buttons}
            onChange={(next) => set('buttons', next)}
          />

          <div className="lg-stack">
            <label htmlFor="atk-failback-type">실패 시 대체 전송</label>
            <select
              id="atk-failback-type"
              value={form.failbackType}
              onChange={(e) => set('failbackType', e.target.value as Failback)}
            >
              <option value="">대체전송 없음</option>
              <option value="SMS">SMS</option>
              <option value="LMS">LMS</option>
              <option value="MMS">MMS</option>
            </select>
          </div>

          {form.failbackType !== '' && form.failbackType !== 'SMS' ? (
            <div className="lg-stack">
              <label htmlFor="atk-failback-subject">대체 전송 제목</label>
              <input
                id="atk-failback-subject"
                type="text"
                maxLength={50}
                placeholder="failback_subject"
                value={form.failbackSubject}
                onChange={(e) => set('failbackSubject', e.target.value)}
              />
            </div>
          ) : null}

          {form.failbackType !== '' ? (
            <div className="lg-stack">
              <label htmlFor="atk-failback-msg">대체 전송 메시지</label>
              <textarea
                id="atk-failback-msg"
                rows={3}
                placeholder="failback_msg"
                value={form.failbackMsg}
                onChange={(e) => set('failbackMsg', e.target.value)}
              />
            </div>
          ) : null}

          {form.failbackType === 'MMS' ? (
            <div className="lg-stack">
              <label htmlFor="atk-failback-img">대체 전송 이미지 ID</label>
              <input
                id="atk-failback-img"
                type="text"
                maxLength={256}
                placeholder="failback_img_id"
                value={form.failbackImgId}
                onChange={(e) => set('failbackImgId', e.target.value)}
              />
            </div>
          ) : null}
        </section>
      ) : null}

      {tab === 'multi' ? (
        <section className="lg-form" aria-label="다건 발송">
          <div className="lg-stack">
            <label htmlFor="atk-multi-is-cd">
              이용기관코드
              <Required />
            </label>
            <select
              id="atk-multi-is-cd"
              value={form.multiIsCd}
              onChange={(e) => set('multiIsCd', e.target.value)}
            >
              <option value="">is_cd</option>
              {institutionOptions}
            </select>
          </div>

          <div className="lg-stack">
            <label htmlFor="atk-multi-tran-id">
              거래고유번호
              <Required />
            </label>
            <input
              id="atk-multi-tran-id"
              type="text"
              maxLength={10}
              placeholder="tran_id"
              value={form.multiTranId}
              onChange={(e) => set('multiTranId', e.target.value)}
            />
          </div>

          <div className="lg-stack">
            <label>
              메시지 데이터
              <Required />
            </label>

            {form.items.map((item, index) => (
              <div className="lg-item-card" key={index} data-testid="msg-data-item">
                {/*
                  순번은 서버가 부여하지만 화면에도 보여준다 — 레거시는 순번 개념이 아예
                  없어 어느 수신자가 어느 메시지를 받는지 배열 위치에만 의존했다(D-A3).
                  The order is assigned by the server but shown here too: the legacy had no notion of it,
                  leaving recipient-to-message association to array position alone.
                */}
                <h3>메시지 {index + 1} (순번 {index + 1})</h3>

                <div className="lg-stack">
                  <label htmlFor={`atk-item-recipient-${index}`}>
                    수신번호
                    <Required />
                  </label>
                  <input
                    id={`atk-item-recipient-${index}`}
                    type="text"
                    placeholder="receiver_number"
                    value={item.recipient}
                    onChange={(e) => setItem(index, { recipient: e.target.value })}
                  />
                </div>

                <div className="lg-stack">
                  <label htmlFor={`atk-item-sender-${index}`}>
                    발신번호
                    <Required />
                  </label>
                  <input
                    id={`atk-item-sender-${index}`}
                    type="text"
                    maxLength={24}
                    placeholder="sender_number"
                    value={item.senderNumber}
                    onChange={(e) => setItem(index, { senderNumber: e.target.value })}
                  />
                </div>

                {/*
                  레거시 다건 폼에는 예약발송시간이 없었다. 계약(ADV_KKO_AT_SEND_M)은 항목마다
                  reqdate 를 선언하므로 다건 예약은 원래 가능했고, 화면이 수집하지 않았을
                  뿐이다 — 설계 결정이 아니라 누락이다(D-A14).
                  The legacy batch form had no reqdate. The contract declares one per item, so batch
                  reservation was always available and the screen simply never collected it (D-A14).
                */}
                <div className="lg-stack">
                  <label htmlFor={`atk-item-reqdate-${index}`}>예약발송시간</label>
                  <input
                    id={`atk-item-reqdate-${index}`}
                    type="text"
                    maxLength={14}
                    placeholder="reqdate (yyyyMMddHHmmss)"
                    value={item.reqdate}
                    onChange={(e) => setItem(index, { reqdate: e.target.value })}
                  />
                </div>

                <div className="lg-stack">
                  <label htmlFor={`atk-item-template-${index}`}>
                    템플릿코드
                    <Required />
                  </label>
                  <select
                    id={`atk-item-template-${index}`}
                    value={item.templateCode}
                    disabled={form.multiIsCd === ''}
                    onChange={(e) => setItem(index, { templateCode: e.target.value })}
                  >
                    <option value="">template_code</option>
                    {(templates.data ?? []).map((t) => (
                      <option key={t.templateCode} value={t.templateCode}>
                        {t.templateCode}
                        {t.templateTitle ? ` · ${t.templateTitle}` : ''}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="lg-stack">
                  <label htmlFor={`atk-item-msg-type-${index}`}>메시지타입</label>
                  <select
                    id={`atk-item-msg-type-${index}`}
                    value={item.msgType}
                    onChange={(e) =>
                      setItem(index, { msgType: e.target.value as ItemState['msgType'] })
                    }
                  >
                    <option value="AT">알림톡 (AT)</option>
                    <option value="FT">친구톡 (FT)</option>
                    <option value="AI">이미지형 (AI)</option>
                  </select>
                </div>

                <div className="lg-stack">
                  <label htmlFor={`atk-item-emphasis-${index}`}>강조 유형</label>
                  <select
                    id={`atk-item-emphasis-${index}`}
                    value={item.emphasis}
                    onChange={(e) => setItem(index, { emphasis: e.target.value as Emphasis })}
                  >
                    <option value="none">없음</option>
                    <option value="emphasize">강조표기형</option>
                    <option value="itemlist">아이템리스트형</option>
                  </select>
                </div>

                {item.emphasis === 'emphasize' ? (
                  <div className="lg-stack">
                    <label htmlFor={`atk-item-title-${index}`}>
                      강조표기 제목
                      <Required />
                    </label>
                    <input
                      id={`atk-item-title-${index}`}
                      type="text"
                      maxLength={50}
                      placeholder="template_title"
                      value={item.templateTitle}
                      onChange={(e) => setItem(index, { templateTitle: e.target.value })}
                    />
                  </div>
                ) : null}

                <div className="lg-stack">
                  <label htmlFor={`atk-item-msg-${index}`}>
                    메시지
                    <Required />
                  </label>
                  <textarea
                    id={`atk-item-msg-${index}`}
                    rows={4}
                    maxLength={1000}
                    placeholder="msg"
                    value={item.msg}
                    onChange={(e) => setItem(index, { msg: e.target.value })}
                  />
                </div>

                <AlimTalkButtons
                  idPrefix={`atk-item-${index}`}
                  buttons={item.buttons}
                  onChange={(next) => setItem(index, { buttons: next })}
                />

                <div className="lg-stack">
                  <label htmlFor={`atk-item-failback-${index}`}>실패 시 대체 전송</label>
                  <select
                    id={`atk-item-failback-${index}`}
                    value={item.failbackType}
                    onChange={(e) =>
                      setItem(index, { failbackType: e.target.value as Failback })
                    }
                  >
                    <option value="">대체전송 없음</option>
                    <option value="SMS">SMS</option>
                    <option value="LMS">LMS</option>
                    <option value="MMS">MMS</option>
                  </select>
                </div>

                {item.failbackType !== '' && item.failbackType !== 'SMS' ? (
                  <div className="lg-stack">
                    <label htmlFor={`atk-item-fb-subject-${index}`}>대체 전송 제목</label>
                    <input
                      id={`atk-item-fb-subject-${index}`}
                      type="text"
                      maxLength={50}
                      placeholder="failback_subject"
                      value={item.failbackSubject}
                      onChange={(e) => setItem(index, { failbackSubject: e.target.value })}
                    />
                  </div>
                ) : null}

                {item.failbackType !== '' ? (
                  <div className="lg-stack">
                    <label htmlFor={`atk-item-fb-msg-${index}`}>대체 전송 메시지</label>
                    <textarea
                      id={`atk-item-fb-msg-${index}`}
                      rows={3}
                      placeholder="failback_msg"
                      value={item.failbackMsg}
                      onChange={(e) => setItem(index, { failbackMsg: e.target.value })}
                    />
                  </div>
                ) : null}

                {item.failbackType === 'MMS' ? (
                  <div className="lg-stack">
                    <label htmlFor={`atk-item-fb-img-${index}`}>대체 전송 이미지 ID</label>
                    <input
                      id={`atk-item-fb-img-${index}`}
                      type="text"
                      maxLength={256}
                      placeholder="failback_img_id"
                      value={item.failbackImgId}
                      onChange={(e) => setItem(index, { failbackImgId: e.target.value })}
                    />
                  </div>
                ) : null}

                <button
                  type="button"
                  className="lg-btn lg-btn-warn"
                  onClick={() => {
                    setForm((c) => ({ ...c, items: c.items.filter((_, i) => i !== index) }));
                    setComposed(null);
                  }}
                >
                  삭제
                </button>
              </div>
            ))}

            <button
              type="button"
              className="lg-btn"
              onClick={() => {
                setForm((c) => ({ ...c, items: [...c.items, { ...EMPTY_ITEM }] }));
                setComposed(null);
              }}
            >
              메시지 데이터 추가
            </button>
          </div>

          {composeBatch.isError ? (
            <p role="alert" className="field-error visible" data-testid="batch-error">
              {messageOf(composeBatch.error)}
            </p>
          ) : null}
        </section>
      ) : null}

      {tab === 'validate' ? (
        <section className="lg-form" aria-label="검증">
          <h2>📝 템플릿 검증</h2>
          <p className="field-help">
            등록된 템플릿은 단건 탭에서 검증합니다. 여기서는 아직 등록되지 않은 템플릿을 직접
            비교합니다 — 검증 규칙은 동일합니다.
          </p>

          <div className="lg-compare">
            <div>
              <label htmlFor="atk-manual-template">Template 입력</label>
              <textarea
                id="atk-manual-template"
                value={form.manualTemplate}
                placeholder="템플릿을 입력하세요..."
                onChange={(e) => {
                  set('manualTemplate', e.target.value);
                  setManual(null);
                }}
              />
            </div>
            <div>
              <label htmlFor="atk-manual-content">Content 입력</label>
              <textarea
                id="atk-manual-content"
                value={form.manualContent}
                placeholder="내용을 입력하세요..."
                onChange={(e) => {
                  set('manualContent', e.target.value);
                  setManual(null);
                }}
              />
            </div>
          </div>

          <div className="lg-form-actions">
            <button
              type="button"
              className="lg-btn lg-btn-info"
              onClick={() => validateManual.mutate()}
              disabled={
                form.manualTemplate.trim() === '' ||
                form.manualContent.trim() === '' ||
                validateManual.isPending
              }
            >
              {validateManual.isPending ? '검증 중…' : '검증하기'}
            </button>
          </div>

          {validateManual.isError ? (
            <p role="alert" className="field-error visible" data-testid="manual-error">
              {messageOf(validateManual.error)}
            </p>
          ) : null}

          {manual ? (
            <p
              data-testid="manual-result"
              className={`lg-verdict ${manual.conformant ? 'ok' : 'fail'}`}
            >
              {manual.templateError
                ? `실패: ${manual.templateError}`
                : manual.conformant
                  ? '성공: 템플릿과 입력이 일치합니다.'
                  : '실패: 오류가 발생한 위치를 확인하세요.'}
              {!manual.conformant && !manual.templateError ? (
                <ul data-testid="manual-divergences">
                  {manual.divergences.map((d, i) => (
                    <li key={`${d.position}-${i}`}>
                      {d.position}번째 문자 (개행·띄어쓰기 1자리로 계산): {d.reason}
                    </li>
                  ))}
                </ul>
              ) : null}
            </p>
          ) : null}
        </section>
      ) : null}

      {/* 레거시 jsonButtons + outputJson — 검증 탭에서는 레거시도 숨겼다 */}
      {tab !== 'validate' ? (
        <>
          <div className="lg-form-actions" data-testid="json-buttons">
            {copied ? (
              <span className="field-help" data-testid="copied">
                클립보드에 복사되었습니다.
              </span>
            ) : null}
            <button
              type="button"
              className="lg-btn lg-btn-ok"
              onClick={() => (tab === 'multi' ? composeBatch.mutate() : compose.mutate())}
              disabled={compose.isPending || composeBatch.isPending}
            >
              {compose.isPending || composeBatch.isPending ? '생성 중…' : 'JSON 생성'}
            </button>
            <button type="button" className="lg-btn lg-btn-warn" onClick={reset}>
              초기화
            </button>
            <button
              type="button"
              className="lg-btn lg-btn-info"
              onClick={() => void copy()}
              disabled={!composed?.payload}
            >
              복사
            </button>
          </div>

          {compose.isError ? (
            <p role="alert" className="field-error visible" data-testid="compose-error">
              {messageOf(compose.error)}
            </p>
          ) : null}

          {composed?.problems && composed.problems.length > 0 ? (
            <ul className="field-error visible" data-testid="compose-problems" role="alert">
              {composed.problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          ) : null}

          <div className="lg-stack">
            <label htmlFor="atk-output">출력</label>
            <textarea id="atk-output" data-testid="output-json" readOnly rows={12} value={composed?.payload ?? ''} />
            {/*
              이 payload 는 계약 적합성 <b>표본</b>이지 발송용이 아니다. 수신번호와 발신프로필키가
              가려진 채 직렬화되므로(NFR-SEC-PII-A01, FR-AZ-A05) 그대로 보내면 벤더에서 실패한다.
              말하지 않으면 운영자는 알 수 없다 — 레거시의 "JSON 생성" 도 무엇을 만들었는지
              끝내 말하지 않았고, 그 침묵이 D-A20 이 눈에 띄지 않은 이유였다.

              This payload is a contract-conformance <b>sample</b>, not something to send: the recipient
              numbers and the sender profile key serialise masked (NFR-SEC-PII-A01, FR-AZ-A05), so sending
              it verbatim fails at the vendor. Unsaid, an operator cannot know — the legacy's "JSON 생성"
              never said what it produced either, and that silence is why D-A20 went unnoticed.
            */}
            {composed?.payload ? (
              <p className="field-help" data-testid="output-masking-note">
                수신번호와 발신프로필키는 가려진 채 출력됩니다. 이 JSON 은 계약 적합성 확인용
                표본이며 그대로 발송할 수 없습니다.
              </p>
            ) : null}
          </div>
        </>
      ) : null}

      {readiness.data ? (
        <section className="lg-form" data-testid="send-readiness" aria-live="polite">
          <h2>발송 준비 상태</h2>
          <p data-testid="credential-state">
            발신프로필키:{' '}
            {readiness.data.credentialConfigured
              ? '설정됨'
              : '미설정 — 이 기관으로는 발송할 수 없습니다'}
          </p>
          {readiness.data.blockers.length > 0 ? (
            <ul className="lg-info" data-testid="send-blockers">
              {readiness.data.blockers.map((b) => (
                <li key={b}>{b}</li>
              ))}
            </ul>
          ) : null}
        </section>
      ) : null}
    </main>
  );
}
