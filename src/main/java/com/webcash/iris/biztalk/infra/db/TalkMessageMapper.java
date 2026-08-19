package com.webcash.iris.biztalk.infra.db;

import com.webcash.iris.biztalk.domain.TalkMessageCriteria;
import com.webcash.iris.biztalk.domain.TalkMessageDetailKey;
import com.webcash.iris.biztalk.domain.TalkTransactionKey;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 톡 메시지 매퍼 — {@code KKO_MSG} / {@code KKF_MSG} 계열, {@code BIZTALK_DB}.
 * The talk-message mapper over the {@code KKO_MSG} / {@code KKF_MSG} family, on {@code BIZTALK_DB}.
 *
 * <h2>문자내역 슬라이스의 매퍼가 아닌 이유 / why this is not the 문자내역 slice's mapper</h2>
 * <p>두 슬라이스는 <b>겹치지 않는 테이블 집합</b>을 읽는다. 문자내역은
 * {@code KKO_SMS_MSG}/{@code KKO_MMS_MSG}/{@code KKF_SMS_MSG}/{@code KKF_MMS_MSG} 와 각 보관본
 * 여덟 개를, 이 슬라이스는 {@code KKO_MSG}/{@code KKF_MSG} 와 보관본 두 개를 읽는다 — 열두 개
 * 테이블, 교집합 없음. 상류 증거는 일간집계 배치 {@code IDO.KKB_APITR_SMTN_C001} 이 알림톡
 * 건수({@code AT_CNT})를 {@code KKO_MSG} + {@code KKO_MSG_LOG} 에서 세는 것이며, 컬럼 집합도
 * 그 해석과 일치한다: {@code TEMPLATE_CODE}, {@code PROFILE_KEY}, {@code BUTTON_JSON},
 * {@code FAILED_*} 는 톡 계열에만 있다(ADR-TLK-027, AMB-T06).</p>
 * <p>The two slices read <b>disjoint table sets</b>: 문자내역 reads eight SMS/MMS tables, this slice reads
 * four talk tables. Twelve tables, no overlap. The upstream evidence is that the daily aggregation batch
 * counts 알림톡 from {@code KKO_MSG} + {@code KKO_MSG_LOG}, and the column sets agree with that reading —
 * {@code TEMPLATE_CODE}, {@code PROFILE_KEY}, {@code BUTTON_JSON} and the {@code FAILED_*} group exist only
 * in the talk family (ADR-TLK-027, AMB-T06).</p>
 *
 * <p>공유되는 것은 <b>규약</b>이다: {@code masking(decrypt(…))} 를 최외곽 프로젝션에 두는 배치
 * (ADR-005), {@code TABLE_TYPE} 으로 활성·보관을 구분하는 형태, 쓰기 메서드를 선언하지 않는 것.</p>
 * <p>What is shared is the <b>convention</b>: {@code masking(decrypt(…))} at the outermost projection
 * (ADR-005), the {@code TABLE_TYPE} live/archive discriminator, and declaring no write method.</p>
 *
 * // source: IDO.KKB_AT_MSG_L001, IDO.KKB_FT_MSG_L001, IDO.KKO_MSG_L002, IDO.KKF_MSG_L002
 * // req: FR-AZ-T03, FR-AZ-T04, FR-TLKD-001…009, FR-TLKM-001…006, CONST-DATA-T01
 */
@Mapper
public interface TalkMessageMapper {

    /**
     * 거래의 소유 기관을 원장에서 읽는다. / Reads the transaction's owning institution from the ledger.
     *
     * <p><b>이 메서드가 FR-AZ-T03 이다.</b> 레거시 화면 32 는 이용기관 코드를 브라우저가 보낸
     * 숨은 입력에서 가져왔고, 그 값을 고치면 조회 대상 기관이 바뀌었다(D-T2). 여기서는 거래일자와
     * 거래번호로 {@code FT_APITR_HSTR} 를 다시 읽어 기관을 <b>서버가 도출</b>한다.</p>
     * <p><b>This method is FR-AZ-T03.</b> Legacy screen 32 took the institution from a hidden input the
     * browser supplied, so changing it changed which institution was queried (D-T2). Here the institution is
     * <b>derived on the server</b> by re-reading {@code FT_APITR_HSTR} for that date and serial.</p>
     *
     * @param key 거래 키 / the transaction key
     * @return 소유 기관과 API 서비스 코드. 없으면 null / the owner and API service code, null when absent
     */
    // req: FR-AZ-T03
    TransactionOwner findTransactionOwner(@Param("k") TalkTransactionKey key);

    /**
     * 거래에 속한 메시지 한 페이지를 조회한다. / Reads one page of messages under a transaction.
     *
     * @param criteria 검증된 조건 / the validated criteria
     * @return 이 페이지의 행 / the rows on this page
     */
    // req: FR-TLKD-001, FR-TLKD-002, FR-TLKD-007, FR-TLKD-008, FR-TLKD-009
    List<TalkMessageRowRecord> findMessages(@Param("c") TalkMessageCriteria criteria);

    /**
     * 조건에 맞는 메시지 전체 건수를 조회한다. / Counts all matching messages.
     *
     * <p>레거시는 이 값을 {@code TOT_CNT} 로 <b>올바르게 반환했고</b>
     * {@code biztalk_admin_32.js} 는 그것을 <b>읽지 않았다</b>(D-T30). 같은 슬라이스의 목록
     * 서비스는 반대로 계산조차 하지 않았다(D-T11) — 두 화면이 같은 문제를 반대 방향으로 틀렸다.</p>
     * <p>The legacy returned this <b>correctly</b> as {@code TOT_CNT} and the client <b>never read it</b>
     * (D-T30), while the same slice's list service did not even compute it (D-T11) — two screens getting one
     * problem wrong in opposite directions.</p>
     *
     * @param criteria 검증된 조건 / the validated criteria
     * @return 전체 건수 / the total count
     */
    // req: FR-TLKD-007
    int countMessages(@Param("c") TalkMessageCriteria criteria);

    /**
     * 메시지 한 건의 상세를 조회한다. / Reads one message's detail.
     *
     * @param key 기관을 포함한 키 / the institution-qualified key
     * @return 상세. 없으면 null / the detail, null when absent
     */
    // req: FR-AZ-T04, FR-TLKM-001…006
    TalkMessageDetailRecord findDetail(@Param("k") TalkMessageDetailKey key);

    /**
     * 거래의 소유 기관과 API 서비스 코드. / A transaction's owning institution and API service code.
     *
     * <p>API 서비스 코드를 함께 읽는 이유는 <b>채널을 레지스트리에서 결정</b>하기 위해서다.
     * 레거시는 채널을 메시지 행의 {@code MSG_TYPE} 에서 읽었고, 친구톡 질의가 그것을
     * {@code 'AT'} 로 잘못 채워 두 화면이 함께 틀렸다(D-T7).</p>
     * <p>The API service code is read alongside so the <b>channel comes from the registry</b>. The legacy read
     * the channel from the message row's {@code MSG_TYPE}, which the 친구톡 query wrongly set to {@code 'AT'},
     * making two screens wrong together (D-T7).</p>
     *
     * @param institutionCode 소유 기관 / the owning institution
     * @param apiServiceCode  API 서비스 코드 / the API service code
     */
    // req: FR-AZ-T03, FR-TLKD-004, FR-TLKM-006
    record TransactionOwner(String institutionCode, String apiServiceCode) {
    }

    /**
     * 매퍼가 반환하는 메시지 행. / A message row as the mapper returns it.
     *
     * <p>{@code channel} 이 없는 것이 의도다 — 레지스트리가 결정하며 데이터베이스가 아는 것이
     * 아니다. 결과 코드는 원값과 사전 설명을 <b>따로</b> 돌려준다: SQL 에서 이어 붙이면
     * 사전에 없는 코드가 NULL 전파로 전체를 지웠다(D-T20).</p>
     * <p>The absence of {@code channel} is deliberate — the registry decides it, not the database. Result
     * codes arrive as raw value and dictionary text <b>separately</b>: concatenating in SQL let a code absent
     * from the dictionary null the whole expression (D-T20).</p>
     *
     * @param transactionNo    거래번호 / the transaction serial
     * @param messageKey       메시지키 / the message key
     * @param institutionCode  이용기관 / the institution code
     * @param statusCode       상태 원값 / the raw status code
     * @param talkResultCode   톡결과 원값 / the raw talk-result code
     * @param talkResultText   톡결과 사전 설명. 없으면 null / the talk-result description, null when absent
     * @param smsResultCode    문자결과 원값 / the raw SMS-result code
     * @param smsResultText    문자결과 사전 설명. 없으면 null / the SMS-result description, null when absent
     * @param senderNumber     발송번호 — 마스킹됨 / the sender number, masked
     * @param recipientNumber  수신번호 — 마스킹됨 / the recipient number, masked
     * @param requestDate      요청일자 / the request date
     * @param requestTime      요청시간 / the request time
     * @param sentTime         발송시간 / the dispatch time
     * @param reportTime       응답시간 / the receipt time
     * @param tableType        {@code QUE}/{@code LOG} / live or archive
     */
    // req: FR-TLKD-001, FR-TLKD-008, FR-TLKM-005
    record TalkMessageRowRecord(
            String transactionNo,
            String messageKey,
            String institutionCode,
            String statusCode,
            String talkResultCode,
            String talkResultText,
            String smsResultCode,
            String smsResultText,
            String senderNumber,
            String recipientNumber,
            String requestDate,
            String requestTime,
            String sentTime,
            String reportTime,
            String tableType
    ) {
    }

    /**
     * 매퍼가 반환하는 메시지 상세. / A message detail as the mapper returns it.
     *
     * <p>레거시 계약은 19개 필드를 선언하고 질의는 8개만 채웠다 — 문자내역 슬라이스의 D9 와
     * 같은 형태다. 여기서는 20개 필드가 모두 질의에 있다.</p>
     * <p>The legacy contract declared 19 fields and the query filled 8 — the same shape as the 문자내역
     * slice's D9. Here all 20 are in the query.</p>
     *
     * @param messageKey      메시지키 / the message key
     * @param institutionCode 이용기관 / the institution code
     * @param profileKey      프로필 / the profile key
     * @param adFlag          광고여부 / the advertising flag
     * @param statusCode      상태 원값 / the raw status code
     * @param talkResultCode  톡결과 원값 / the raw talk-result code
     * @param talkResultText  톡결과 설명 / the talk-result description
     * @param smsResultCode   문자결과 원값 / the raw SMS-result code
     * @param smsResultText   문자결과 설명 / the SMS-result description
     * @param templateCode    템플릿코드 / the template code
     * @param senderNumber    발신번호 — 마스킹됨 / the sender number, masked
     * @param recipientNumber 수신자번호 — 마스킹됨 / the recipient number, masked
     * @param requestedAt     요청시간 / when requested
     * @param sentAt          송신시간 / when dispatched
     * @param carrierRepliedAt 통신사응답시간 / when the carrier replied
     * @param reportedAt      결과수신시간 / when the receipt arrived
     * @param message         전송메시지 / the message text
     * @param imagePath       이미지경로 / the image path
     * @param imageUrl        이미지URL / the image URL
     * @param wideImageFlag   와이드 이미지 여부 / the wide-image flag
     * @param buttonJson      버튼JSON / the button JSON
     * @param failedType      문자전송타입 / the failback type
     * @param failedSubject   문자전송제목 / the failback subject
     * @param failedImage     문자내이미지주소 / the failback image address
     * @param failedMessage   문자내용 / the failback message
     */
    // req: FR-TLKM-001, FR-TLKM-002, FR-TLKM-003, FR-TLKM-005
    record TalkMessageDetailRecord(
            String messageKey,
            String institutionCode,
            String profileKey,
            String adFlag,
            String statusCode,
            String talkResultCode,
            String talkResultText,
            String smsResultCode,
            String smsResultText,
            String templateCode,
            String senderNumber,
            String recipientNumber,
            String requestedAt,
            String sentAt,
            String carrierRepliedAt,
            String reportedAt,
            String message,
            String imagePath,
            String imageUrl,
            String wideImageFlag,
            String buttonJson,
            String failedType,
            String failedSubject,
            String failedImage,
            String failedMessage
    ) {
    }
}
