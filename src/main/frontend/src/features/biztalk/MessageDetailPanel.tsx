import { useEffect, useRef, useState } from 'react';
import {
  MessageDetail,
  MessageHistoryRow,
  fetchMessageDetail,
} from '../../api/messageHistoryApi';

/**
 * 문자상세내역 패널. / Message detail panel.
 *
 * req: FR-MSGD-004, FR-MSGD-005, FR-MSGD-006, FR-MSGD-007, FR-MSGD-008
 * source: biztalk_admin_41_view.jsp, biztalk_admin_41.js
 *
 * <h2>레거시 대비 변경점 / changes from the legacy</h2>
 * <ul>
 *   <li><b>19개 필드를 모두 표시한다</b> — 레거시는 8개만 채워지고 11개는 빈 칸이었으며,
 *       그것을 표시할 탭 핸들러는 주석 처리되어 있었다(D9)</li>
 *   <li><b>날짜가 4자리 연도로 표시된다</b> — 레거시는 4개 IDO 중 3개가 5자리 연도를
 *       출력했다(D5)</li>
 *   <li><b>실패·이미지·버튼 섹션이 조건부로 표시된다</b> — 값이 있을 때만 렌더링한다</li>
 *   <li><b>팝업이 아니라 인라인 패널</b> — 레거시는 {@code ap.openPop()} 으로 별 창을
 *       열었다. 팝업 차단기와 스크린리더 모두에 불리하다</li>
 * </ul>
 */

interface Props {
  /** 선택된 목록 행 / the selected list row */
  row: MessageHistoryRow;
  /** 닫기 콜백 / close callback */
  onClose: () => void;
}

/**
 * 문자상세내역 패널 컴포넌트. / The detail panel component.
 */
export function MessageDetailPanel({ row, onClose }: Props) {
  const [detail, setDetail] = useState<MessageDetail | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(true);
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setNotFound(false);
    fetchMessageDetail(row)
      .then((d) => {
        if (active) {
          setDetail(d);
        }
      })
      .catch(() => {
        // 404 는 "없음" 또는 "다른 테넌트의 것"을 구분하지 않는다. 서버가 의도적으로
        // 구분하지 않으므로(TM-009) 화면도 구분하지 않는다.
        // A 404 covers both not-found and not-owned; the server declines to distinguish them
        // (TM-009), so neither does the screen.
        if (active) {
          setNotFound(true);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [row]);

  // 패널이 열리면 제목으로 포커스를 옮긴다. 인라인 패널은 시각적으로만 나타나므로
  // 키보드·스크린리더 사용자에게는 아무 일도 일어나지 않은 것처럼 보인다.
  // Focus moves to the heading on open: an inline panel appears only visually, so keyboard and
  // screen-reader users would otherwise see nothing happen.
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <section className="detail-panel" aria-labelledby="detail-heading">
      <h2 id="detail-heading" ref={headingRef} tabIndex={-1}>
        문자상세내역 — 메시지키 {row.messageKey}
      </h2>

      {loading && <p aria-live="polite">불러오는 중…</p>}

      {notFound && (
        // req: FR-MSGD-008 — 레거시는 빈 폼을 표시했다
        <p role="alert" className="field-error visible">
          해당 내역을 찾을 수 없습니다.
        </p>
      )}

      {detail && (
        <>
          <dl className="detail-grid">
            <Field label="유형" value={row.messageTypeLabel} />
            <Field label="테이블" value={row.tableType} />
            <Field label="상태" value={row.statusLabel} />
            <Field label="톡결과" value={detail.resultCode} />
            <Field label="발신번호" value={detail.senderNumber} />
            <Field label="수신번호" value={detail.recipientNumber} />
            <Field label="요청일시" value={detail.requestDate} />
            <Field label="발송일시" value={detail.sentDate} />
            <Field label="결과일시" value={detail.resultDate} />
            <Field label="응답일시" value={detail.reportDate} />
            {/* D9: 아래 3개는 레거시에서 항상 빈 칸이었다 / always blank in the legacy */}
            <Field label="템플릿 코드" value={detail.templateCode} />
            <Field label="발신 프로필" value={detail.profileKey} />
            <Field label="광고 여부" value={detail.adFlag} />
          </dl>

          <section>
            <h3>메시지 내용</h3>
            {/*
              req: FR-MSGD-007 — 읽기 전용.
              textarea + readOnly 를 쓰는 이유: 본문에 개행이 있고 길 수 있으며, 사용자가
              복사해야 한다. dangerouslySetInnerHTML 은 절대 쓰지 않는다 — 메시지 본문은
              외부에서 온 데이터이므로 XSS 경로가 된다(TM-L017).
              A readOnly textarea preserves newlines and allows copying. dangerouslySetInnerHTML
              is never used: the body is externally-sourced data and would be an XSS path.
            */}
            <textarea
              readOnly
              className="detail-message"
              aria-label="메시지 내용"
              value={detail.message ?? ''}
              rows={6}
            />
          </section>

          {/* req: FR-MSGD-006 — 값이 있을 때만 렌더링한다 */}
          {(detail.imagePath || detail.imageUrl) && (
            <section>
              <h3>이미지</h3>
              <dl className="detail-grid">
                <Field label="이미지 경로" value={detail.imagePath} />
                <Field label="이미지 URL" value={detail.imageUrl} />
                <Field label="와이드 이미지" value={detail.wideImageFlag} />
              </dl>
            </section>
          )}

          {detail.buttonJson && (
            <section>
              <h3>버튼</h3>
              <textarea
                readOnly
                className="detail-message"
                aria-label="버튼 정의"
                value={detail.buttonJson}
                rows={4}
              />
            </section>
          )}

          {(detail.failedType || detail.failedSubject || detail.failedMessage) && (
            <section>
              {/*
                실패 정보는 레거시에서 한 번도 표시되지 않았다(D9). 지원 담당자가 "왜
                실패했는가"에 답할 수 있는 유일한 정보다.
                Failure information was never displayed in the legacy, yet it is the only data
                that answers "why did this fail" for support staff.
              */}
              <h3>실패 정보</h3>
              <dl className="detail-grid">
                <Field label="실패 유형" value={detail.failedType} />
                <Field label="실패 제목" value={detail.failedSubject} />
                <Field label="실패 이미지" value={detail.failedImage} />
              </dl>
              <textarea
                readOnly
                className="detail-message"
                aria-label="실패 메시지"
                value={detail.failedMessage ?? ''}
                rows={3}
              />
            </section>
          )}
        </>
      )}

      <button type="button" className="primary" onClick={onClose}>
        닫기
      </button>
    </section>
  );
}

/**
 * 정의 목록 항목. / A definition-list entry.
 *
 * <p>값이 없으면 {@code -} 를 표시한다. 빈 칸으로 두면 "데이터가 없음"과 "필드가
 * 구현되지 않음"을 구분할 수 없다 — 레거시의 D9 가 정확히 그렇게 보였다.</p>
 * <p>An absent value renders as {@code -}: leaving it blank makes "no data" indistinguishable
 * from "field not implemented", which is exactly how D9 presented itself.</p>
 */
function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{value && value.trim() !== '' ? value : '-'}</dd>
    </>
  );
}
