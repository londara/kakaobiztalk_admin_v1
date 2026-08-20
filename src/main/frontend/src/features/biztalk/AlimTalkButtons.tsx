import type { ButtonInput } from '../../api/alimTalkApi';

/**
 * 버튼 편집기 — 단건·다건 양쪽에서 쓴다. / The button editor, shared by single and batch.
 *
 * req: FR-ATC-002, FR-ATC-005, FR-ATC-009
 * source: biztalk_admin_61.js — addButton / updateButtonDetailSection
 *
 * 레거시는 이 UI 를 <b>두 번</b> 만들었다 — `addButton` (단건) 과 `addMsgData` 안의
 * `add-button` (다건) 이 거의 같은 innerHTML 문자열을 각각 들고 있었고, 그래서 두 곳이
 * 어긋났다: 단건의 첫 버튼만 `value="웹링크"` 로 채워지고 다건은 그렇지 않다. 한 곳에서
 * 만들면 그런 어긋남이 생기지 않는다.
 *
 * The legacy built this UI <b>twice</b> — `addButton` for single send and an `add-button` handler inside
 * `addMsgData` for batch, each carrying a near-identical innerHTML string. They drifted: only the
 * single-send first button is pre-filled with `value="웹링크"`. Built once, they cannot drift.
 *
 * 버튼 유형에 따라 나타나는 항목은 레거시와 같다 — `WL` 은 URL 둘, `AL` 은 스킴 둘,
 * `DS`·`BK`·`MD` 는 추가 항목이 없다.
 * The fields shown per type follow the legacy: two URLs for `WL`, two schemes for `AL`, nothing
 * further for `DS`, `BK` and `MD`.
 */
export function AlimTalkButtons({
  idPrefix,
  buttons,
  onChange,
}: {
  /** id 충돌을 막는 접두어 / prefix keeping ids unique across items */
  idPrefix: string;
  /** 현재 버튼 목록 / the current buttons */
  buttons: ButtonInput[];
  /** 변경된 목록을 돌려준다 / receives the updated list */
  onChange: (next: ButtonInput[]) => void;
}) {
  const patch = (index: number, changes: Partial<ButtonInput>) =>
    onChange(buttons.map((b, i) => (i === index ? { ...b, ...changes } : b)));

  return (
    <div className="lg-stack">
      <label>버튼</label>

      {buttons.map((button, index) => (
        <div className="lg-item-card" key={index}>
          <div className="lg-stack">
            <label htmlFor={`${idPrefix}-btn-name-${index}`}>버튼명</label>
            <input
              id={`${idPrefix}-btn-name-${index}`}
              type="text"
              maxLength={14}
              placeholder="버튼명"
              value={button.name}
              onChange={(e) => patch(index, { name: e.target.value })}
            />
          </div>

          <div className="lg-stack">
            <label htmlFor={`${idPrefix}-btn-type-${index}`}>버튼타입</label>
            <select
              id={`${idPrefix}-btn-type-${index}`}
              value={button.type}
              onChange={(e) => patch(index, { type: e.target.value })}
            >
              <option value="WL">웹링크 (WL)</option>
              <option value="AL">앱링크 (AL)</option>
              <option value="DS">배송조회 (DS)</option>
              <option value="BK">봇키워드 (BK)</option>
              <option value="MD">메시지전달 (MD)</option>
            </select>
          </div>

          {button.type === 'WL' ? (
            <>
              <div className="lg-stack">
                <label htmlFor={`${idPrefix}-btn-mobile-${index}`}>모바일 URL</label>
                <input
                  id={`${idPrefix}-btn-mobile-${index}`}
                  type="text"
                  maxLength={240}
                  placeholder="모바일 웹 URL"
                  value={button.urlMobile}
                  onChange={(e) => patch(index, { urlMobile: e.target.value })}
                />
              </div>
              <div className="lg-stack">
                <label htmlFor={`${idPrefix}-btn-pc-${index}`}>PC URL</label>
                <input
                  id={`${idPrefix}-btn-pc-${index}`}
                  type="text"
                  maxLength={240}
                  placeholder="PC 웹 URL"
                  value={button.urlPc}
                  onChange={(e) => patch(index, { urlPc: e.target.value })}
                />
              </div>
            </>
          ) : null}

          {button.type === 'AL' ? (
            <>
              <div className="lg-stack">
                <label htmlFor={`${idPrefix}-btn-ios-${index}`}>iOS 스킴</label>
                <input
                  id={`${idPrefix}-btn-ios-${index}`}
                  type="text"
                  maxLength={240}
                  placeholder="iOS 앱 스킴"
                  value={button.schemeIos}
                  onChange={(e) => patch(index, { schemeIos: e.target.value })}
                />
              </div>
              <div className="lg-stack">
                <label htmlFor={`${idPrefix}-btn-android-${index}`}>Android 스킴</label>
                <input
                  id={`${idPrefix}-btn-android-${index}`}
                  type="text"
                  maxLength={240}
                  placeholder="Android 앱 스킴"
                  value={button.schemeAndroid}
                  onChange={(e) => patch(index, { schemeAndroid: e.target.value })}
                />
              </div>
            </>
          ) : null}

          <button
            type="button"
            className="lg-btn lg-btn-warn"
            onClick={() => onChange(buttons.filter((_, i) => i !== index))}
          >
            삭제
          </button>
        </div>
      ))}

      <button
        type="button"
        className="lg-btn"
        onClick={() =>
          onChange([
            ...buttons,
            { name: '', type: 'WL', urlMobile: '', urlPc: '', schemeIos: '', schemeAndroid: '' },
          ])
        }
      >
        버튼 추가
      </button>
    </div>
  );
}
