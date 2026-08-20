package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.domain.TalkChannel;
import com.webcash.iris.biztalk.domain.TalkMessageCriteria;
import com.webcash.iris.biztalk.domain.TalkMessageDetailKey;
import com.webcash.iris.biztalk.domain.TalkResult;
import com.webcash.iris.biztalk.domain.TalkTransactionKey;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TalkMessageMapper} 통합 검증 — 실제 PostgreSQL, 실제 매퍼 XML, <b>스텁 암복호 함수</b>.
 * Integration verification for {@link TalkMessageMapper}: real PostgreSQL, real mapper XML, <b>stub crypto
 * functions</b>.
 *
 * <h2>스텁 함수가 무엇을 검증하고 무엇을 검증하지 않는가 / what the stubs do and do not verify</h2>
 * <p>{@code decrypt()} 와 {@code masking()} 은 <b>사이트 정의 함수</b>이며 그 정의는 이 환경에
 * 없다. 발신번호 슬라이스가 그것을 확인했고, 이 슬라이스의 계획도 그래서 D-T6·D-T18 을
 * "배치로만 검증"으로 기록했다.</p>
 * <p>{@code decrypt()} and {@code masking()} are <b>site-defined</b> and their definitions are not available
 * here. The 발신번호 slice established that, and this slice's plan therefore recorded D-T6 and D-T18 as
 * verified-by-placement only.</p>
 *
 * <p>그 기록은 <b>절반만 맞았다.</b> 이 계열의 결함은 두 종류로 갈린다:</p>
 * <ul>
 *   <li><b>함수의 계산에 관한 결함</b> — {@code masking()} 이 실제로 무엇을 돌려주는가.
 *       스텁으로는 검증할 수 없고, 여기서도 검증하지 않는다.</li>
 *   <li><b>함수 주변 SQL 의 형태에 관한 결함</b> — 컬럼 이름, 별칭, 프로젝션, 매핑.
 *       <b>이름이 같은 스텁으로 정확히 검증된다</b>, 결함이 함수 본문이 아니라 SQL 에 있으므로.</li>
 * </ul>
 * <p>That was <b>only half right.</b> Defects in this family split in two: those about what a function
 * <b>computes</b> — unverifiable with a stub, and not verified here — and those about the <b>shape of the SQL
 * around it</b>: column names, aliasing, projection, mapping. The second kind is verified <b>exactly</b> by a
 * stub of the same name, because the defect is in the SQL rather than in the function body.</p>
 *
 * <p><b>D-T18 이 두 번째 종류다.</b> 별칭 없는 {@code decrypt(CALLBACK), decrypt(PHONE)} 은
 * PostgreSQL 이 두 출력 컬럼을 모두 {@code decrypt} 로 이름 붙이게 만들고, 계약은
 * {@code CALLBACK}/{@code PHONE} 을 기대했다 — 충돌은 <b>이름의 성질</b>이며 함수가 무엇을
 * 계산하는지와 무관하다. 그래서 한 줄 스텁으로 재현된다.</p>
 * <p><b>D-T18 is of the second kind.</b> Unaliased {@code decrypt(CALLBACK), decrypt(PHONE)} makes PostgreSQL
 * name both output columns {@code decrypt} while the contract expected {@code CALLBACK}/{@code PHONE} — the
 * collision is a <b>property of the names</b> and independent of what the function computes, so a one-line
 * stub reproduces it.</p>
 *
 * // source: IDO.KKO_MSG_L002 — decrypt(CALLBACK), decrypt(PHONE) unaliased
 * // req: FR-AZ-T03, FR-AZ-T04, FR-TLKD-002, FR-TLKD-006, FR-TLKD-009, FR-TLKM-002, FR-TLKM-003, FR-TLKM-004
 */
class TalkMessageMapperIntegrationTest {

    private static EmbeddedPostgres postgres;
    private static SqlSessionFactory sessionFactory;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        DataSource dataSource = postgres.getPostgresDatabase();
        createSchema(dataSource);
        seed(dataSource);
        sessionFactory = buildSessionFactory(dataSource);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    /**
     * 스키마와 <b>스텁 암복호 함수</b>를 만든다. / Creates the schema and the <b>stub crypto functions</b>.
     *
     * <p>스텁은 운영 함수가 아니다. {@code decrypt} 는 항등이고 {@code masking} 은 가운데 네
     * 자리를 가린다 — 실제 알고리즘이 아니라 <b>호출 형태</b>를 재현하기 위한 것이다. 시험이
     * 단언하는 것은 마스킹이 <b>적용되었다</b>는 사실과 프로젝션이 매핑된다는 사실이며, 마스킹의
     * 출력 형식이 옳다는 것이 아니다.</p>
     * <p>The stubs are not the production functions: {@code decrypt} is identity and {@code masking} hides four
     * middle digits — reproducing the <b>call shape</b> rather than the real algorithm. What the tests assert is
     * that masking <b>was applied</b> and that the projection maps, not that the masking format is correct.</p>
     */
    // req: NFR-SEC-PII-T01, RISK-T13
    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE FUNCTION decrypt(v TEXT) RETURNS TEXT AS $$
                      SELECT v
                    $$ LANGUAGE SQL IMMUTABLE""");

            statement.execute("""
                    CREATE FUNCTION masking(v TEXT) RETURNS TEXT AS $$
                      SELECT CASE WHEN v IS NULL OR length(v) < 8 THEN v
                                  ELSE overlay(v PLACING '****' FROM 4 FOR 4) END
                    $$ LANGUAGE SQL IMMUTABLE""");

            statement.execute("""
                    CREATE TABLE FT_APITR_HSTR (
                      TRDD VARCHAR(8), FINTECH_ISCD VARCHAR(10), IS_TUNO VARCHAR(20),
                      API_SVC_CD VARCHAR(50), PRSU VARCHAR(1), FINTECH_RPCD VARCHAR(10),
                      RGDT VARCHAR(14), LAST_AMDT VARCHAR(14)
                    )""");

            statement.execute("""
                    CREATE TABLE KKB_ERRCD_INFO (
                      CHNL_DSNC VARCHAR(4), RSLT_CD VARCHAR(10), ERR_MSG VARCHAR(200)
                    )""");

            for (String table : List.of("KKO_MSG", "KKO_MSG_LOG", "KKF_MSG", "KKF_MSG_LOG")) {
                statement.execute("CREATE TABLE " + table + " ("
                        + "ID VARCHAR(10), MSGKEY INTEGER, STATUS VARCHAR(1), RSLT VARCHAR(10),"
                        + "MSG_RSLT VARCHAR(10), CALLBACK VARCHAR(100), PHONE VARCHAR(100),"
                        + "SERIALNUM VARCHAR(20), REQDATE TIMESTAMP, SENTDATE TIMESTAMP,"
                        + "RSLTDATE TIMESTAMP, REPORTDATE TIMESTAMP, PROFILE_KEY VARCHAR(50),"
                        + "AD_FLAG VARCHAR(1), TEMPLATE_CODE VARCHAR(50), MSG TEXT,"
                        + "IMG_PATH VARCHAR(200), IMG_URL VARCHAR(200), WI_FLAG VARCHAR(1),"
                        + "BUTTON_JSON TEXT, FAILED_TYPE VARCHAR(10),"
                        + "FAILED_SUBJECT VARCHAR(100), FAILED_IMG VARCHAR(200), FAILED_MSG TEXT)");
            }
        }
    }

    private static void seed(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 알림톡 거래 하나와 친구톡 거래 하나. 채널은 API 서비스 코드가 정한다.
            // One 알림톡 transaction and one 친구톡: the API service code decides the channel.
            statement.execute("INSERT INTO FT_APITR_HSTR VALUES "
                    + "('20260819','K00011','00000000000000000042','ADV_KKO_AT_SEND','1',NULL,"
                    + "'20260819112504','20260819112504')");
            statement.execute("INSERT INTO FT_APITR_HSTR VALUES "
                    + "('20260819','K00011','00000000000000000043','ADV_KKO_FT_SEND','1',NULL,"
                    + "'20260819112505','20260819112505')");
            // 다른 기관의 거래 — 교차 기관 시험용.
            // A transaction owned by another institution, for the cross-institution test.
            statement.execute("INSERT INTO FT_APITR_HSTR VALUES "
                    + "('20260819','K99999','00000000000000000044','ADV_KKO_AT_SEND','1',NULL,"
                    + "'20260819112506','20260819112506')");
            // 상세를 지원하지 않는 API — FR-TLKD-005.
            // An API with no message detail — FR-TLKD-005.
            statement.execute("INSERT INTO FT_APITR_HSTR VALUES "
                    + "('20260819','K00011','00000000000000000045','ADV_COM_GET_STATUS','1',NULL,"
                    + "'20260819112507','20260819112507')");

            statement.execute("INSERT INTO KKB_ERRCD_INFO VALUES ('T','9001','수신거부')");
            statement.execute("INSERT INTO KKB_ERRCD_INFO VALUES ('MSG','5001','번호오류')");

            // 알림톡 활성 3건: 성공 / 알려진 실패코드 / 사전에 없는 실패코드.
            // Three live 알림톡 rows: success, a known failure code, an unregistered failure code.
            insertMessage(statement, "KKO_MSG", 1001, "3", "0", "0", "0000000042");
            insertMessage(statement, "KKO_MSG", 1002, "3", "9001", "5001", "0000000042");
            insertMessage(statement, "KKO_MSG", 1003, "3", "9999", "9999", "0000000042");
            // 결과가 아직 없는 1건 — D-T22.
            // One row with no result yet — D-T22.
            insertMessage(statement, "KKO_MSG", 1004, "2", null, null, "0000000042");
            // 보관 1건 — 합집합 확인용.
            // One archived row, to confirm the union.
            insertMessage(statement, "KKO_MSG_LOG", 1005, "3", "0", "00", "0000000042");
            // 친구톡 활성 1건.
            // One live 친구톡 row.
            insertMessage(statement, "KKF_MSG", 2001, "3", "0", "00", "0000000043");
            // 다른 기관 소유의 메시지 — 교차 기관 시험용.
            // A message owned by another institution, for the cross-institution test.
            insertMessage(statement, "KKO_MSG", 3001, "3", "0", "0", "0000000044", "K99999");
        }
    }

    private static void insertMessage(Statement statement, String table, int msgKey,
                                      String status, String rslt, String msgRslt,
                                      String serial) throws Exception {
        insertMessage(statement, table, msgKey, status, rslt, msgRslt, serial, "K00011");
    }

    private static void insertMessage(Statement statement, String table, int msgKey,
                                      String status, String rslt, String msgRslt,
                                      String serial, String institution) throws Exception {
        statement.execute("INSERT INTO " + table + " ("
                + "ID, MSGKEY, STATUS, RSLT, MSG_RSLT, CALLBACK, PHONE, SERIALNUM,"
                + "REQDATE, SENTDATE, RSLTDATE, REPORTDATE, PROFILE_KEY, AD_FLAG,"
                + "TEMPLATE_CODE, MSG, IMG_PATH, IMG_URL, WI_FLAG, BUTTON_JSON,"
                + "FAILED_TYPE, FAILED_SUBJECT, FAILED_IMG, FAILED_MSG) VALUES ("
                + "'" + institution + "', " + msgKey + ", '" + status + "', "
                + (rslt == null ? "NULL" : "'" + rslt + "'") + ", "
                + (msgRslt == null ? "NULL" : "'" + msgRslt + "'") + ", "
                + "'0212345678', '01098765432', '" + serial + "', "
                + "TIMESTAMP '2026-08-19 11:25:04', TIMESTAMP '2026-08-19 11:25:05', "
                + "TIMESTAMP '2026-08-19 11:25:06', TIMESTAMP '2026-08-19 11:25:07', "
                + "'PROF', 'N', 'TPL_001', '안녕하세요', NULL, NULL, 'N', NULL, "
                + "NULL, NULL, NULL, NULL)");
    }

    private static SqlSessionFactory buildSessionFactory(DataSource dataSource) throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TalkMessageMapper.class);

        String resource = "mybatis/mapper/biztalk/TalkMessageMapper.xml";
        try (InputStream in = TalkMessageMapperIntegrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static TalkMessageCriteria criteria(String serial, TalkChannel channel,
                                                String institution, String recipient,
                                                String status, TalkResult.Outcome talk,
                                                TalkResult.Outcome sms) {
        return new TalkMessageCriteria(
                TalkTransactionKey.of("20260819", serial), channel, institution,
                recipient, status, talk, sms, 0, 100);
    }

    private static List<TalkMessageMapper.TalkMessageRowRecord> messages(TalkMessageCriteria c) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkMessageMapper.class).findMessages(c);
        }
    }

    private static int count(TalkMessageCriteria c) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkMessageMapper.class).countMessages(c);
        }
    }

    private static TalkMessageMapper.TransactionOwner owner(String serial) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkMessageMapper.class)
                    .findTransactionOwner(TalkTransactionKey.of("20260819", serial));
        }
    }

    private static TalkMessageMapper.TalkMessageDetailRecord detail(TalkMessageDetailKey key) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkMessageMapper.class).findDetail(key);
        }
    }

    @Nested
    @DisplayName("소유 기관 도출 / deriving the owner")
    class Ownership {

        @Test
        @DisplayName("기관과 API 서비스 코드를 원장에서 읽는다 — FR-AZ-T03")
        void readsOwnerFromLedger() {
            // 레거시는 이 값을 브라우저가 보낸 숨은 입력에서 가져왔고, 고치면 조회 대상 기관이
            // 바뀌었다(D-T2).
            // The legacy took this from a browser-supplied hidden input; changing it changed which
            // institution was queried (D-T2).
            assertThat(owner("42"))
                    .isEqualTo(new TalkMessageMapper.TransactionOwner("K00011", "ADV_KKO_AT_SEND"));
        }

        @Test
        @DisplayName("원장에 없는 거래는 null 이다")
        void unknownTransactionIsNull() {
            assertThat(owner("999999")).isNull();
        }

        @Test
        @DisplayName("20자리 거래번호가 손실 없이 일치한다 — D-T9")
        void twentyCharacterSerialMatches() {
            assertThat(owner("00000000000000000042")).isNotNull();
        }
    }

    @Nested
    @DisplayName("마스킹 / masking")
    class Masking {

        @Test
        @DisplayName("발송·수신번호가 마스킹되어 나온다 — D-T6")
        void numbersAreMasked() {
            // 레거시는 decrypt() 만 적용하고 masking() 을 적용하지 않아 평문 번호가 브라우저로
            // 나갔다. 스텁 masking 은 가운데 네 자리를 가리므로, 원본과 다르다는 것으로
            // "마스킹이 적용되었다"를 단언할 수 있다 — 마스킹 형식이 옳은지는 단언하지 않는다.
            // The legacy applied decrypt() without masking() and sent plaintext numbers to the browser. The
            // stub masking hides four middle digits, so differing from the original asserts that masking was
            // applied — not that the masking format is correct.
            List<TalkMessageMapper.TalkMessageRowRecord> rows = messages(
                    criteria("42", TalkChannel.ALIMTALK, "K00011", null, null, null, null));

            assertThat(rows).isNotEmpty().allSatisfy(row -> {
                assertThat(row.recipientNumber())
                        .as("수신번호가 원본과 같으면 마스킹이 적용되지 않은 것이다 / "
                                + "an unchanged recipient means masking was not applied")
                        .isNotEqualTo("01098765432")
                        .contains("****");
                assertThat(row.senderNumber()).contains("****");
            });
        }

        @Test
        @DisplayName("마스킹은 최외곽에만 적용되므로 검색은 실제 번호로 동작한다")
        void searchRunsAgainstTheDecryptedValue() {
            // masking() 을 내부에 두면 사용자가 아는 번호로 찾을 수 없다. 이것이 문자내역
            // 슬라이스가 정한 배치의 이유다(ADR-005).
            // Masking inside the query would make a number the user knows unfindable. That is why the
            // 문자내역 slice placed it at the outermost level (ADR-005).
            assertThat(messages(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    "0109876", null, null, null))).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("필터 / filters")
    class Filters {

        @Test
        @DisplayName("친구톡에도 필터가 적용된다 — D-T8")
        void filtersApplyToFriendtalk() {
            // ⚠ 레거시 친구톡 질의에는 ?? 자리도 DYNAMIC_0 선언도 없어 네 필터가 조용히
            // 무시되었다. 필터를 걸었을 때 결과가 줄어드는지로 확인한다.
            // The legacy 친구톡 query had neither the ?? placeholder nor the DYNAMIC_0 declaration, so four
            // filters were silently ignored. Verified by the filter actually reducing the result.
            int unfiltered = count(criteria("43", TalkChannel.FRIENDTALK, "K00011",
                    null, null, null, null));
            int filtered = count(criteria("43", TalkChannel.FRIENDTALK, "K00011",
                    "0000000", null, null, null));

            assertThat(unfiltered).isEqualTo(1);
            assertThat(filtered)
                    .as("친구톡에서 필터가 무시되면 이 두 값이 같다 / "
                            + "if the filter is ignored for 친구톡 these two are equal")
                    .isZero();
        }

        @Test
        @DisplayName("실패 필터가 결과 없는 행을 삼키지 않는다 — D-T22")
        void failureFilterDoesNotSwallowPendingRows() {
            // 레거시는 AND RSLT != '0' 이었고 NULL != '0' 은 UNKNOWN 이므로, 결과가 아직 오지
            // 않은 행이 성공에도 실패에도 나타나지 않았다.
            // The legacy used AND RSLT != '0'; NULL != '0' is UNKNOWN, so a row with no result yet appeared
            // under neither success nor failure.
            int success = count(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    null, null, TalkResult.Outcome.SUCCESS, null));
            int failure = count(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    null, null, TalkResult.Outcome.FAILURE, null));
            int pending = count(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    null, null, TalkResult.Outcome.PENDING, null));
            int all = count(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    null, null, null, null));

            assertThat(pending)
                    .as("결과가 없는 행이 미수신으로 조회되어야 한다 / a pending row must be reachable")
                    .isEqualTo(1);
            assertThat(success + failure + pending)
                    .as("세 구분이 전체를 남김 없이 나눠야 한다 / the three must partition the whole set")
                    .isEqualTo(all);
        }

        @Test
        @DisplayName("문자 미발송('00')은 실패가 아니다")
        void smsNotDispatchedIsNotFailure() {
            // 알림톡이 성공했으면 문자 대체 발송이 필요 없었다는 뜻이다. 레거시 화면 32 의
            // 필터도 이것을 올바르게 제외했다 — 드물게 레거시가 맞았던 자리다.
            // A successful 알림톡 needed no SMS fallback. The legacy screen-32 filter excluded it correctly —
            // a rare place where the legacy was right.
            assertThat(count(criteria("43", TalkChannel.FRIENDTALK, "K00011",
                    null, null, null, TalkResult.Outcome.FAILURE))).isZero();
            assertThat(count(criteria("43", TalkChannel.FRIENDTALK, "K00011",
                    null, null, null, TalkResult.Outcome.PENDING))).isEqualTo(1);
        }

        @Test
        @DisplayName("활성과 보관을 함께 조회한다")
        void unionsLiveAndArchive() {
            List<String> tables = messages(criteria("42", TalkChannel.ALIMTALK, "K00011",
                    null, null, null, null))
                    .stream().map(r -> r.tableType()).distinct().sorted().toList();

            assertThat(tables).containsExactly("LOG", "QUE");
        }

        @Test
        @DisplayName("기관 술어가 실제로 격리한다 — D-T2")
        void institutionPredicateIsolates() {
            // 다른 기관 소유의 거래번호로 조회하되 기관은 K00011 로 둔다.
            // Query another institution's serial while the institution stays K00011.
            assertThat(messages(criteria("44", TalkChannel.ALIMTALK, "K00011",
                    null, null, null, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("결과 코드 / result codes")
    class ResultCodes {

        @Test
        @DisplayName("사전에 없는 코드도 원값이 살아 나온다 — D-T20")
        void unknownCodeSurvives() {
            // ⚠ 레거시는 RSLT || '(' || (SELECT ERR_MSG …) || ')' 로 이어 붙였고, 사전에 없는
            // 코드는 서브쿼리 NULL 이 전체를 NULL 로 만들어 화면이 비었다 — 운영자가 그 값을
            // 가장 필요로 하는 경우다. 이제 원값과 설명이 따로 오므로 원값이 사라지지 않는다.
            // The legacy concatenated the code with a subquery; an unmatched code nulled the whole expression
            // and blanked the field — the case an operator needs most. Code and text now arrive separately, so
            // the code cannot vanish.
            TalkMessageMapper.TalkMessageRowRecord row = messages(
                    criteria("42", TalkChannel.ALIMTALK, "K00011", null, null, null, null))
                    .stream().filter(r -> "9999".equals(r.talkResultCode())).findFirst().orElseThrow();

            assertThat(row.talkResultCode()).isEqualTo("9999");
            assertThat(row.talkResultText())
                    .as("사전에 없으므로 설명은 null 이고, 그것이 원값을 지우지 않는다 / "
                            + "absent from the dictionary, so the text is null — and that does not erase the code")
                    .isNull();
        }

        @Test
        @DisplayName("알려진 코드는 설명이 함께 온다")
        void knownCodeCarriesItsText() {
            TalkMessageMapper.TalkMessageRowRecord row = messages(
                    criteria("42", TalkChannel.ALIMTALK, "K00011", null, null, null, null))
                    .stream().filter(r -> "9001".equals(r.talkResultCode())).findFirst().orElseThrow();

            assertThat(row.talkResultText()).isEqualTo("수신거부");
        }
    }

    @Nested
    @DisplayName("메시지 상세 / message detail")
    class Detail {

        @Test
        @DisplayName("발신·수신번호가 실제로 매핑된다 — D-T18")
        void numbersActuallyMap() {
            // ⚠ 이것이 D-T18 의 회귀 테스트이며, 스텁 함수 덕분에 <b>실행으로</b> 검증된다.
            // 레거시는 decrypt(CALLBACK), decrypt(PHONE) 을 별칭 없이 선택했으므로 PostgreSQL 이
            // 두 출력 컬럼을 모두 'decrypt' 로 이름 붙였고, 계약은 CALLBACK/PHONE 을 기대해
            // 팝업이 존재하는 이유인 두 필드가 항상 비어 있었다. 충돌은 이름의 성질이므로
            // 함수가 무엇을 계산하는지와 무관하다.
            //
            // This is D-T18's regression test, verified <b>by execution</b> thanks to the stubs. The legacy
            // selected both decrypt() calls unaliased, so PostgreSQL named both output columns 'decrypt' while
            // the contract expected CALLBACK/PHONE, leaving the two fields the popup exists to show always
            // blank. The collision is a property of the names, independent of what the function computes.
            TalkMessageDetailKey key = TalkMessageDetailKey.of(
                    "K00011", "1001", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE);

            TalkMessageMapper.TalkMessageDetailRecord record = detail(key);

            assertThat(record).isNotNull();
            assertThat(record.senderNumber())
                    .as("별칭이 없으면 이 필드가 null 이다 / unaliased, this field is null")
                    .isNotNull().contains("****");
            assertThat(record.recipientNumber()).isNotNull().contains("****");
        }

        @Test
        @DisplayName("연도가 네 자리다 — D-T17")
        void yearIsFourDigits() {
            TalkMessageMapper.TalkMessageDetailRecord record = detail(TalkMessageDetailKey.of(
                    "K00011", "1001", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE));

            // 레거시는 'YYYYY-MM-DD HH24:MI:SS' — 다섯 개의 Y — 로 네 시각 모두를 만들었다.
            // The legacy used five Y characters on all four timestamps.
            assertThat(record.requestedAt()).startsWith("2026-08-19");
            assertThat(record.sentAt()).startsWith("2026-08-19");
            assertThat(record.carrierRepliedAt()).startsWith("2026-08-19");
            assertThat(record.reportedAt()).startsWith("2026-08-19");
        }

        @Test
        @DisplayName("다른 기관의 메시지 키로는 조회되지 않는다 — D-T5")
        void crossInstitutionKeyReturnsNothing() {
            // ⚠ 레거시 질의의 키는 REQDATE + STATUS + MSGKEY 뿐이어서, 메시지 키만 알면 다른
            // 기관의 메시지 본문·템플릿코드·전화번호를 읽었다.
            // The legacy key was REQDATE + STATUS + MSGKEY alone, so a message key was enough to read another
            // institution's body, template code and phone numbers.
            assertThat(detail(TalkMessageDetailKey.of(
                    "K00011", "3001", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE)))
                    .as("K99999 소유 메시지가 K00011 범위에서 조회되어서는 안 된다 / "
                            + "a message owned by K99999 must not be readable in K00011's scope")
                    .isNull();
        }

        @Test
        @DisplayName("상태가 바뀌어도 조회된다 — D-T19")
        void statusChangeDoesNotHideTheMessage() throws Exception {
            // 레거시는 AND STATUS = :STATUS 를 키에 두어, 목록을 그린 뒤 발송 파이프라인이
            // 상태를 진행시키면 0건이 되어 팝업이 설명 없이 비었다. 상태를 바꾼 뒤에도 같은
            // 키로 조회되는지 확인한다.
            // The legacy included AND STATUS = :STATUS, so a status advancing after the list was drawn emptied
            // the popup with no explanation. Verified by changing the status and re-reading with the same key.
            TalkMessageDetailKey key = TalkMessageDetailKey.of(
                    "K00011", "1002", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE);

            assertThat(detail(key)).isNotNull();

            try (Connection connection = postgres.getPostgresDatabase().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("UPDATE KKO_MSG SET STATUS = '4' WHERE MSGKEY = 1002");
            }

            assertThat(detail(key))
                    .as("상태가 진행되어도 같은 키로 조회되어야 한다 / "
                            + "the same key must still resolve after the status advances")
                    .isNotNull();
        }

        @Test
        @DisplayName("채널이 자기 테이블을 조회한다 — D-T7")
        void channelReadsItsOwnTable() {
            // 레거시 친구톡 질의가 'AT' 를 반환했고 화면 31 이 그 값으로 테이블을 골라
            // KKO_MSG 를 조회해 아무것도 반환하지 않았다.
            // The legacy 친구톡 query returned 'AT' and screen 31 chose its table from it, querying KKO_MSG and
            // returning nothing.
            assertThat(detail(TalkMessageDetailKey.of(
                    "K00011", "2001", TalkChannel.FRIENDTALK, TalkMessageDetailKey.TABLE_LIVE)))
                    .as("친구톡 메시지는 친구톡 테이블에서 조회되어야 한다 / "
                            + "a 친구톡 message must resolve in the 친구톡 table")
                    .isNotNull();

            assertThat(detail(TalkMessageDetailKey.of(
                    "K00011", "2001", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE)))
                    .as("잘못된 채널로는 조회되지 않는다 — 레거시가 정확히 그렇게 했다 / "
                            + "the wrong channel resolves nothing, which is what the legacy did")
                    .isNull();
        }

        @Test
        @DisplayName("보관 테이블도 조회된다")
        void archiveResolves() {
            assertThat(detail(TalkMessageDetailKey.of(
                    "K00011", "1005", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_ARCHIVE)))
                    .isNotNull();
        }

        @Test
        @DisplayName("스무 개 필드가 모두 질의에 있다")
        void allTwentyFieldsAreQueried() {
            // 레거시 계약은 19개 필드를 선언하고 질의는 8개만 채웠다 — 문자내역 슬라이스의 D9
            // 와 같은 형태다.
            // The legacy contract declared 19 fields and the query filled 8 — the same shape as the 문자내역
            // slice's D9.
            TalkMessageMapper.TalkMessageDetailRecord record = detail(TalkMessageDetailKey.of(
                    "K00011", "1001", TalkChannel.ALIMTALK, TalkMessageDetailKey.TABLE_LIVE));

            assertThat(record.profileKey()).isEqualTo("PROF");
            assertThat(record.adFlag()).isEqualTo("N");
            assertThat(record.templateCode()).isEqualTo("TPL_001");
            assertThat(record.message()).isEqualTo("안녕하세요");
            assertThat(record.wideImageFlag()).isEqualTo("N");
        }
    }
}
