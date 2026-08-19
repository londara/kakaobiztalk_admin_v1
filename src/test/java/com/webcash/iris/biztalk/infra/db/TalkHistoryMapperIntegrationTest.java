package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.domain.BizTalkApiRegistry;
import com.webcash.iris.biztalk.domain.TalkHistoryCriteria;
import com.webcash.iris.biztalk.domain.TalkPeriodPolicy;
import com.webcash.iris.biztalk.domain.TransactionSerial;
import com.webcash.iris.common.tenant.PrincipalScope;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * {@link TalkHistoryMapper} 통합 검증 — 실제 PostgreSQL, 실제 매퍼 XML.
 * Integration verification for {@link TalkHistoryMapper}: real PostgreSQL, real mapper XML.
 *
 * <h2>이 시험이 존재하게 된 경위 / how this test came to exist</h2>
 * <p>스프린트 T1 계획은 이 검증을 <b>불가능</b>으로 기록했다 — Docker 금지로 Testcontainers 를
 * 쓸 수 없다는 이유였다(RISK-T13). 그 전제는 <b>틀렸다</b>: {@code embedded-postgres} 는 실제
 * PostgreSQL 바이너리를 프로세스로 띄우므로 Docker 가 필요하지 않고, 이 환경에서 실제로
 * 동작한다. 이용기관 보고서 슬라이스도 같은 전제로 매퍼↔DB 경계를 검증하지 못했고, 그
 * 회고는 정확히 그 경계에서 결함 두 건을 발견했다.</p>
 * <p>The Sprint T1 plan recorded this verification as <b>impossible</b>, on the grounds that Docker is
 * prohibited so Testcontainers is unusable (RISK-T13). That premise was <b>wrong</b>:
 * {@code embedded-postgres} launches a real PostgreSQL binary as a process, needs no Docker, and works in
 * this environment. The 이용기관 보고서 slice left the mapper↔DB boundary unverified on the same premise,
 * and its retrospective found two defects at precisely that boundary.</p>
 *
 * <h2>왜 동시각 고정 데이터인가 / why a tied-timestamp fixture</h2>
 * <p>운영 화면 캡처에서 <b>11개 행이 같은 {@code 11:25:04}</b> 를 공유한다. 대량 API 호출이 같은
 * 초에 도착하므로 이 화면에서 동시각은 예외가 아니라 <b>정상</b>이다. 그리고 오프셋 페이징을
 * 쓰기로 했으므로(ADR-TLK-028) 전순서는 표시 성질이 아니라 <b>정확성 전제</b>다 — 동순위
 * 결정자가 없으면 행이 두 페이지에 나오거나 어느 페이지에도 나오지 않는다(D-T10).</p>
 * <p>The production screenshot shows <b>eleven rows sharing {@code 11:25:04}</b>: bulk API calls arrive in
 * the same second, so ties are <b>the normal case</b> here, not an edge case. And since offset paging was
 * chosen (ADR-TLK-028), a total order is not a display property but a <b>correctness precondition</b> —
 * without a tiebreaker a row appears on two pages or on none (D-T10).</p>
 *
 * <p>표본이 아니라 <b>속성</b>으로 검증한다: 모든 페이지의 합집합이 전체 집합과 <b>정확히 한
 * 번씩</b> 일치해야 한다. 사례별 시험은 레거시에서도 통과했을 것이다.</p>
 * <p>Verified as a <b>property</b> rather than a sample: the union of all pages must equal the full set
 * <b>exactly once</b>. A case-by-case test would have passed on the legacy too.</p>
 *
 * // source: IDO.KKB_APITR_HSTR_L001 — ORDER BY RGDT DESC (no tiebreaker)
 * // req: FR-TLK-005, FR-TLK-006, FR-TLK-011, CONST-SEC-T01, RISK-T08, RISK-T13, ADR-TLK-028
 */
class TalkHistoryMapperIntegrationTest {

    /** 운영 캡처의 동시각. / The tied timestamp from the production screenshot. */
    private static final String TIED = "20260819112504";

    /** 동시각 블록의 행 수. / Rows in the tied block. */
    private static final int TIED_ROWS = 100;

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
     * 이 시험에 필요한 컬럼만 만든다. / Creates only the columns this test needs.
     *
     * <p><b>25개 컬럼 전체를 만들지 않는 것이 의도다.</b> 계좌·카드·금액·전문 컬럼이 스키마에
     * 없으면, 프로젝션이 그것을 선택하려 하는 순간 <b>질의가 실패한다</b> — CONST-SEC-T01 이
     * 리뷰가 아니라 실행으로 강제된다.</p>
     * <p><b>Deliberately not all 25 columns.</b> With the account, card, amount and telegram columns absent
     * from the schema, the moment a projection tries to select one <b>the query fails</b> — CONST-SEC-T01
     * enforced by execution rather than by review.</p>
     */
    // req: CONST-SEC-T01
    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE FT_APITR_HSTR (
                      TRDD          VARCHAR(8)   NOT NULL,
                      FINTECH_ISCD  VARCHAR(10)  NOT NULL,
                      IS_TUNO       VARCHAR(20)  NOT NULL,
                      API_SVC_CD    VARCHAR(50),
                      API_CD        VARCHAR(50),
                      PRSU          VARCHAR(1),
                      FINTECH_RPCD  VARCHAR(10),
                      RGDT          VARCHAR(14),
                      LAST_AMDT     VARCHAR(14)
                    )""");
            statement.execute("""
                    CREATE TABLE FT_FTIS_INFO (
                      FINTECH_ISCD VARCHAR(10) NOT NULL,
                      ISNM         VARCHAR(100)
                    )""");
        }
    }

    private static void seed(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "INSERT INTO FT_FTIS_INFO VALUES ('K00011', '비즈플레이_법인카드')");

            // 동시각 블록 100건. IS_TUNO 만 다르다 — 정렬이 RGDT 하나뿐이면 순서가 정의되지 않는다.
            // 100 rows on one timestamp, differing only in IS_TUNO: with RGDT alone the order is undefined.
            for (int i = 0; i < TIED_ROWS; i++) {
                statement.execute(String.format(
                        "INSERT INTO FT_APITR_HSTR VALUES "
                                + "('20260819','K00011','%020d','ADV_KKO_AT_SEND','KKO_AT','0',NULL,'%s','%s')",
                        i, TIED, TIED));
            }

            // 범위 밖 API 1건 — SCOPE-T01 이 이것을 제외해야 한다.
            // One out-of-scope API row: SCOPE-T01 must exclude it.
            statement.execute(
                    "INSERT INTO FT_APITR_HSTR VALUES "
                            + "('20260819','K00011','99999999999999999999','ADV_COM_GET_STATUS',"
                            + "'COM','1',NULL,'20260819112505','20260819112505')");

            // 기관 마스터에 없는 기관 1건 — D-T26 이 빈 칸으로 만들던 경우.
            // One row whose institution is absent from the master: the case D-T26 rendered blank.
            statement.execute(
                    "INSERT INTO FT_APITR_HSTR VALUES "
                            + "('20260819','K99999','88888888888888888888','ADV_KKO_FT_SEND',"
                            + "'KKO_FT','9','E01','20260819112506','20260819112506')");
        }
    }

    private static SqlSessionFactory buildSessionFactory(DataSource dataSource) throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TalkHistoryMapper.class);

        // 실제 매퍼 XML 을 읽는다. 손으로 옮겨 쓴 SQL 을 검증하면 무엇도 증명하지 못한다.
        // The real mapper XML is loaded: verifying hand-copied SQL would prove nothing.
        String resource = "mybatis/mapper/biztalk/TalkHistoryMapper.xml";
        try (InputStream in = TalkHistoryMapperIntegrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static TalkHistoryCriteria criteria(int page, int size) {
        return new TalkHistoryCriteria(
                TalkPeriodPolicy.validate("20260819", null, null, null),
                new PrincipalScope(null, true, false),
                null, null, null,
                BizTalkApiRegistry.withDefaults().codes(),
                page, size);
    }

    // 제네릭 헬퍼 하나로 두면 AssertJ 의 assertThat 오버로드와 타입 추론이 충돌한다.
    // 반환 타입별로 나누면 호출부가 읽기도 쉽다.
    // One generic helper collides with AssertJ overload resolution; splitting by return type also reads
    // better at the call sites.
    private static List<TalkHistoryMapper.TalkHistoryRowRecord> page(TalkHistoryCriteria criteria) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkHistoryMapper.class).findPage(criteria);
        }
    }

    private static int count(TalkHistoryCriteria criteria) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkHistoryMapper.class).countAll(criteria);
        }
    }

    private static List<TalkHistoryMapper.ObservedApiService> observedServices(String from, String to) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkHistoryMapper.class).findObservedApiServices(from, to);
        }
    }

    @Nested
    @DisplayName("페이징 정확성 / paging correctness")
    class PagingCorrectness {

        @Test
        @DisplayName("동시각 100건을 페이징해도 각 행이 정확히 한 번 나온다 — D-T10, TC-T001-06")
        void everyRowAppearsExactlyOnceAcrossPages() {
            int size = 7;   // 100 을 나누지 못하는 크기 — 마지막 페이지가 부분적이다
                            // a size that does not divide 100, so the last page is partial

            int total = count(criteria(0, size));
            assertThat(total).isEqualTo(TIED_ROWS + 1); // 동시각 100건 + 범위 안 FT 1건

            List<String> seen = new ArrayList<>();
            int pages = (total + size - 1) / size;
            for (int page = 0; page < pages; page++) {
                final int current = page;
                page(criteria(current, size))
                        .forEach(row -> seen.add(row.transactionNo()));
            }

            Set<String> unique = new HashSet<>(seen);

            // ⚠ 이것이 D-T10 의 회귀 테스트다. 레거시의 ORDER BY RGDT DESC 만으로는 동시각 행의
            // 순서가 정의되지 않으므로, 오프셋으로 자를 때 어떤 행은 두 페이지에 나오고 어떤
            // 행은 어느 페이지에도 나오지 않는다.
            // This is D-T10's regression test. With the legacy's ORDER BY RGDT DESC alone the order of tied
            // rows is undefined, so cutting by offset makes some rows appear twice and others not at all.
            assertThat(seen)
                    .as("중복 없이 / without duplicates")
                    .hasSize(total);
            assertThat(unique)
                    .as("누락 없이 — 합집합이 전체 집합과 정확히 일치해야 한다 / "
                            + "without omissions: the union must equal the full set exactly")
                    .hasSize(total);
        }

        @Test
        @DisplayName("건수 질의와 목록 질의가 같은 조건을 본다 — D-T11")
        void countAndPageAgree() {
            int total = count(criteria(0, 1_000));
            int fetched = page(criteria(0, 1_000)).size();

            assertThat(fetched)
                    .as("한 페이지에 전부 담을 때 건수와 실제 행 수가 같아야 한다 / "
                            + "with one large page the count and the row count must agree")
                    .isEqualTo(total);
        }

        @Test
        @DisplayName("정렬이 결정적이다 — 같은 질의를 두 번 하면 같은 순서다")
        void orderIsDeterministic() {
            List<String> first = page(criteria(0, 20))
                    .stream().map(row -> row.transactionNo()).toList();
            List<String> second = page(criteria(0, 20))
                    .stream().map(row -> row.transactionNo()).toList();

            assertThat(first).isEqualTo(second);
            // 동시각 안에서 IS_TUNO 내림차순이어야 한다.
            // Within a tied timestamp the order must be IS_TUNO descending.
            assertThat(first).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }
    }

    @Nested
    @DisplayName("범위 / scope")
    class Scope {

        @Test
        @DisplayName("범위 밖 API 거래는 반환되지 않는다 — SCOPE-T01, TC-REG-04")
        void outOfScopeApiIsExcluded() {
            // 운영 캡처의 ADV_COM_GET_STATUS 는 톡 발송 서비스가 아니다. 레거시는 채널 술어가
            // 없어 이것을 BizTalk 내역 그리드에 표시했다.
            // ADV_COM_GET_STATUS from the production screenshot is not a talk-send service. The legacy had
            // no channel predicate and showed it in a grid headed BizTalk 내역.
            List<String> codes = page(criteria(0, 1_000))
                    .stream().map(row -> row.apiServiceCode()).distinct().toList();

            assertThat(codes).doesNotContain("ADV_COM_GET_STATUS");
        }

        @Test
        @DisplayName("빈 범위 집합은 전체가 아니라 없음이다 — D-T2 의 절반")
        void emptyScopeReturnsNoRows() {
            // 레거시가 빈 값을 "전체"로 읽은 것이 D-T2 의 절반이었다.
            // Reading a blank as "all" was half of D-T2.
            TalkHistoryCriteria empty = new TalkHistoryCriteria(
                    TalkPeriodPolicy.validate("20260819", null, null, null),
                    new PrincipalScope(null, true, false),
                    null, null, null, Set.of(), 0, 100);

            assertThat(page(empty)).isEmpty();
            assertThat(count(empty)).isZero();
        }

        @Test
        @DisplayName("기관 술어가 실제로 걸린다 — D-T2")
        void institutionPredicateFilters() {
            TalkHistoryCriteria scoped = new TalkHistoryCriteria(
                    TalkPeriodPolicy.validate("20260819", null, null, null),
                    new PrincipalScope("K99999", false, false),
                    null, null, null,
                    BizTalkApiRegistry.withDefaults().codes(), 0, 100);

            assertThat(page(scoped))
                    .singleElement()
                    .satisfies(row -> assertThat(row.institutionCode()).isEqualTo("K99999"));
        }
    }

    @Nested
    @DisplayName("기관명 해석 / institution-name resolution")
    class InstitutionName {

        @Test
        @DisplayName("조인으로 해석된다")
        void resolvesByJoin() {
            // 기관을 명시해 조회한다. 첫 페이지를 그냥 집어 오면 정렬 최상단은 RGDT 가 가장 큰
            // K99999 행(마스터에 없음)이므로 이름이 null 이다 — 이 시험을 처음 썼을 때 정확히
            // 그렇게 실패했고, 통합 시험의 값이 바로 그 지점이다: 어느 행이 먼저 오는지에 대한
            // 나의 가정을 데이터가 즉시 반박했다.
            // Scoped explicitly. Taking the first page instead would return the K99999 row — the highest
            // RGDT, absent from the master — whose name is null. This test failed exactly that way when
            // first written, and that is the value of an integration test: the data immediately refuted my
            // assumption about which row sorts first.
            TalkHistoryCriteria scoped = new TalkHistoryCriteria(
                    TalkPeriodPolicy.validate("20260819", null, null, null),
                    new PrincipalScope("K00011", false, false),
                    null, null, null,
                    BizTalkApiRegistry.withDefaults().codes(), 0, 1);

            assertThat(page(scoped))
                    .singleElement()
                    .satisfies(row ->
                            assertThat(row.institutionName()).isEqualTo("비즈플레이_법인카드"));
        }

        @Test
        @DisplayName("마스터에 없는 기관은 null 로 넘어온다 — 애플리케이션이 표식을 붙인다")
        void unresolvedComesThroughAsNull() {
            // COALESCE 로 감추지 않는 것이 의도다. SQL 이 기본값을 채우면 "이름 없는 기관"과
            // "조회가 실패한 기관"을 구분할 수 없게 된다 — 레거시가 빈 칸으로 만든 것과 같은 결과다.
            // Deliberately not hidden behind COALESCE: defaulting in SQL would make "an institution with no
            // name" and "a lookup that failed" indistinguishable — the legacy's blank cell.
            TalkHistoryCriteria scoped = new TalkHistoryCriteria(
                    TalkPeriodPolicy.validate("20260819", null, null, null),
                    new PrincipalScope("K99999", false, false),
                    null, null, null,
                    BizTalkApiRegistry.withDefaults().codes(), 0, 100);

            assertThat(page(scoped))
                    .singleElement()
                    .satisfies(row -> assertThat(row.institutionName()).isNull());
        }
    }

    @Nested
    @DisplayName("거래일련번호 / the transaction serial")
    class Serial {

        @Test
        @DisplayName("20자리로 정규화된 값이 손실 없이 일치한다 — D-T9")
        void twentyCharacterSerialMatches() {
            // 레거시 경로는 이 값을 10자리로 잘라 다른 거래에 일치시켰다(LpadTruncationTest 참조).
            // The legacy path cut this to ten characters and matched a different transaction
            // (see LpadTruncationTest).
            TransactionSerial serial = TransactionSerial.parse("42").orElseThrow();
            TalkHistoryCriteria byLegacySerial = new TalkHistoryCriteria(
                    TalkPeriodPolicy.validate("20260819", null, null, null),
                    new PrincipalScope(null, true, false),
                    serial, null, null,
                    BizTalkApiRegistry.withDefaults().codes(), 0, 100);

            assertThat(page(byLegacySerial))
                    .singleElement()
                    .satisfies(row ->
                            assertThat(row.transactionNo()).isEqualTo("00000000000000000042"));
        }
    }

    @Nested
    @DisplayName("대조 질의 / the reconciliation query")
    class Reconciliation {

        @Test
        @DisplayName("관측된 API 코드를 건수와 함께 반환한다 — TC-REG-03")
        void returnsObservedCodesWithCounts() {
            List<TalkHistoryMapper.ObservedApiService> observed = observedServices("20260819", "20260819");

            assertThat(observed)
                    .extracting(TalkHistoryMapper.ObservedApiService::apiServiceCode)
                    .containsExactlyInAnyOrder(
                            "ADV_KKO_AT_SEND", "ADV_COM_GET_STATUS", "ADV_KKO_FT_SEND");

            assertThat(observed)
                    .filteredOn(row -> row.apiServiceCode().equals("ADV_KKO_AT_SEND"))
                    .singleElement()
                    .satisfies(row -> assertThat(row.transactionCount()).isEqualTo(TIED_ROWS));
        }
    }
}
