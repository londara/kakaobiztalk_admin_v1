/**
 * 메시지 상세 패널 — 화면 31. / The message-detail panel: screen 31.
 *
 * req: FR-AZ-T04, FR-TLKM-001, FR-TLKM-002, FR-TLKM-003, FR-TLKM-005, FR-TLKM-007, FR-TLKM-008
 * source: biztalk_admin_31_view.jsp, biztalk_admin_31.js
 *
 * <h2>세 묶음을 모두 보이게 두는 이유 / why all three groups stay visible</h2>
 * 레거시는 탭으로 하나씩만 보였고 두 묶음을 `display:none` 으로 감췄다. 그런데 첫 화면에서
 * 감춰진 것이 **첨부와 FailBack** 이었고, 그 두 묶음의 필드는 D9 계열 결함으로 애초에 채워지지
 * 않았다 — 즉 감춰진 것이 비어 있었는지 확인할 방법이 없었다. 여기서는 세 묶음을 모두 그리고,
 * 비어 있으면 비어 있다고 적는다.
 *
 * The legacy used tabs and hid two groups with `display:none`. What it hid on first render were **첨부 and
 * FailBack**, whose fields were never populated in the first place — so there was no way to see that the hidden
 * groups were empty. Here all three are rendered, and an empty one says so.
 *
 * <h2>빈 칸이 없는 이유 / why there are no blank cells</h2>
 * 서버가 값이 없는 필드에 `(값 없음)` 표식을 담아 보낸다. 레거시 화면 31 은 값이 없는 것과 조회가
 * 실패한 것을 같은 빈 칸으로 그렸고, D-T18 때문에 발신·수신번호는 **항상** 비어 있었으므로,
 * 운영자는 그 두 칸이 왜 비었는지 알 방법이 없었다.
 *
 * The server ships a `(값 없음)` marker for an absent field. Legacy screen 31 drew "no value" and "the lookup
 * failed" as the same empty cell, and because of D-T18 the numbers were **always** blank, so an operator had no
 * way to tell why.
 */

import { useQuery } from '@tanstack/react-query';
import { fetchTalkMessageDetail, type TalkMessageDetail } from '../../api/talkDetailApi';

/** 속성. / The props. */
export interface TalkMessageDetailPanelProps {
  /** 거래일자 / the transaction date */
  transactionDate: string;
  /** 거래고유번호 / the transaction serial */
  serial: string;
  /** 메시지키 / the message key */
  messageKey: string;
  /** 활성/보관 / live or archive */
  tableType: string;
  /** 닫기 / close */
  onClose: () => void;
}

/** 한 줄 항목. / One labelled field. */
function Field({ label, value, testId }: { label: string; value: string; testId?: string }) {
  return (
    <>
      <th scope="row">{label}</th>
      <td className="lg-cell-text" data-testid={testId}>
        {value}
      </td>
    </>
  );
}

export function TalkMessageDetailPanel({
  transactionDate,
  serial,
  messageKey,
  tableType,
  onClose,
}: TalkMessageDetailPanelProps) {
  const detail = useQuery({
    queryKey: ['talk-history', 'message-detail', transactionDate, serial, messageKey, tableType],
    // 거래 키를 함께 보낸다. 메시지 키만으로 조회할 수 있게 하면 D-T5 가 그대로 돌아온다.
    // The transaction key is sent alongside: a lookup by message key alone would restore D-T5.
    queryFn: () => fetchTalkMessageDetail(transactionDate, serial, messageKey, tableType),
  });

  const data: TalkMessageDetail | undefined = detail.data;

  return (
    <section className="lg-panel" aria-label="메시지 상세" data-testid="talk-msg-detail">
      <div className="lg-panel-header">
        {/* 레거시 팝업의 제목은 '기본 컨텐츠 관리' 였다 — 다른 화면에서 복사된 것이다(D-T34). */}
        {/* The legacy popup was titled '기본 컨텐츠 관리', copied from another screen (D-T34). */}
        <h2>메시지 상세 조회</h2>
        <button type="button" className="lg-btn" onClick={onClose} data-testid="talk-detail-close">
          닫기
        </button>
      </div>

      {detail.isError && (
        <p role="alert" className="field-error visible" data-testid="talk-detail-error">
          {(detail.error as Error).message}
        </p>
      )}

      {detail.isFetching && !data && <p data-testid="talk-detail-loading">조회 중입니다.</p>}

      {data && (
        <>
          <table className="lg-grid" data-testid="talk-detail-message">
            <caption>메시지정보</caption>
            <tbody>
              <tr>
                <Field label="이용기관" value={data.institutionCode} />
                <Field label="채널" value={data.channelLabel} testId="talk-detail-channel" />
              </tr>
              <tr>
                <Field label="메시지키" value={data.messageKey} />
                <Field label="상태" value={data.statusDisplay} />
              </tr>
              <tr>
                <Field label="프로필" value={data.profileKey} />
                <Field label="광고여부" value={data.adFlag} />
              </tr>
              <tr>
                {/*
                  코드가 항상 보인다. 레거시는 사전에 없는 코드일 때 NULL 전파로 이 칸을 비웠고,
                  그것이 운영자가 그 값을 가장 필요로 하는 경우였다(D-T20).
                  The code is always visible. The legacy blanked this via NULL propagation for codes absent
                  from the dictionary — the case an operator needs most (D-T20).
                */}
                <Field label="톡결과" value={data.talkResult} testId="talk-detail-talk-result" />
                <Field label="문자결과" value={data.smsResult} testId="talk-detail-sms-result" />
              </tr>
              <tr>
                <Field label="템플릿코드" value={data.templateCode} />
                <Field label="" value="" />
              </tr>
              <tr>
                {/*
                  레거시에서는 이 두 칸이 항상 비어 있었다 — 별칭 없는 decrypt() 두 개가 같은 출력
                  컬럼 이름으로 충돌했다(D-T18). 값은 마스킹되어 온다(D-T6).
                  These two were always blank in the legacy: two unaliased decrypt() calls collided on one
                  output column name (D-T18). The values arrive masked (D-T6).
                */}
                <Field label="발신번호" value={data.senderMasked} testId="talk-detail-sender" />
                <Field
                  label="수신자번호"
                  value={data.recipientMasked}
                  testId="talk-detail-recipient"
                />
              </tr>
              <tr>
                {/* 레거시는 네 시각 모두 다섯 자리 연도였다(D-T17). */}
                {/* The legacy used a five-digit year on all four timestamps (D-T17). */}
                <Field label="요청시간" value={data.requestedAt} testId="talk-detail-requested" />
                <Field label="송신시간" value={data.sentAt} />
              </tr>
              <tr>
                <Field label="통신사응답시간" value={data.carrierRepliedAt} />
                <Field label="결과수신시간" value={data.reportedAt} />
              </tr>
            </tbody>
          </table>

          <div className="lg-field-block">
            <label htmlFor="talk-detail-body">전송메시지</label>
            <textarea
              id="talk-detail-body"
              rows={10}
              value={data.message}
              readOnly
              data-testid="talk-detail-body"
            />
          </div>

          <table className="lg-grid" data-testid="talk-detail-attachment">
            <caption>
              첨부
              {/*
                비어 있다는 사실 자체가 정보다. 레거시는 이 묶음을 감춰서 비었는지조차 알 수
                없었다.
                Emptiness is itself informative. The legacy hid this group, so there was no way to know it
                was empty.
              */}
              {!data.hasAttachment && <span data-testid="talk-detail-no-attachment"> — 없음</span>}
            </caption>
            <tbody>
              <tr>
                <Field label="이미지경로" value={data.imagePath} />
                <Field label="이미지URL" value={data.imageUrl} />
              </tr>
              <tr>
                <Field label="와이드 이미지 여부" value={data.wideImageFlag} />
                <Field label="버튼JSON" value={data.buttonJson} />
              </tr>
            </tbody>
          </table>

          <table className="lg-grid" data-testid="talk-detail-failback">
            <caption>
              FailBack
              {!data.hasFailback && <span data-testid="talk-detail-no-failback"> — 없음</span>}
            </caption>
            <tbody>
              <tr>
                <Field label="문자전송타입" value={data.failedType} />
                <Field label="문자전송제목" value={data.failedSubject} />
              </tr>
              <tr>
                <Field label="문자내이미지주소" value={data.failedImage} />
                <Field label="문자내용" value={data.failedMessage} />
              </tr>
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
