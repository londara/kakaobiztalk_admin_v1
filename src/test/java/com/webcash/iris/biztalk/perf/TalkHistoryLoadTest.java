package com.webcash.iris.biztalk.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.domain.BizTalkApiRegistry;
import com.webcash.iris.biztalk.domain.TalkHistoryCriteria;
import com.webcash.iris.biztalk.domain.TalkHistoryRow;
import com.webcash.iris.biztalk.domain.TalkPeriodPolicy;
import com.webcash.iris.biztalk.infra.db.TalkHistoryMapper;
import com.webcash.iris.biztalk.infra.excel.StreamingWorkbookWriter;
import com.webcash.iris.common.tenant.PrincipalScope;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 부하·규모 측정 — 회고 액션 B1. / Load and scale measurement: retrospective action B1.
 *
 * <h2>이 시험이 증명하는 것과 증명하지 않는 것 / what this does and does not establish</h2>
 * <p><b>증명하지 않는 것:</b> NFR-PERF-T01/T02 의 <b>운영 SLA 통과</b>. 이것은 개발 장비에서
 * 프로세스로 띄운 PostgreSQL 이며, CPU·디스크·메모리·동시 부하가 운영과 다르다. 여기서 나온
 * 밀리초 수치를 운영 P95 로 보고하는 것은 이 슬라이스가 서른네 번 고친 종류의 주장이 된다.</p>
 * <p><b>Not established:</b> that NFR-PERF-T01/T02's <b>production SLA</b> is met. This is a PostgreSQL process
 * on a development machine, with different CPU, disk, memory and concurrency from production. Reporting the
 * milliseconds here as a production P95 would be the class of claim this slice has corrected thirty-four
 * times.</p>
 *
 * <p><b>증명하는 것:</b> 세 가지 <b>구조적</b> 성질이며, 각각은 하드웨어와 무관하게 성립하거나
 * 성립하지 않는다.</p>
 * <ol>
 *   <li><b>인덱스가 실제로 쓰인다.</b> {@code EXPLAIN} 을 읽어 술어가 순차 스캔으로 떨어지지
 *       않는지 확인한다. 레거시가 술어에 {@code LPAD} 를 두어 인덱스를 쓸 수 없게 만든 것
 *       (D-T9)이 하드웨어로 보상되지 않는 문제였다.</li>
 *   <li><b>마지막 페이지가 첫 페이지에 비해 선형 이상으로 나빠지지 않는다.</b> 오프셋 페이징을
 *       선택했으므로(ADR-TLK-028) 깊은 오프셋 비용이 실제로 유계인지가 결정의 전제다.</li>
 *   <li><b>내보내기 힙이 행 수와 무관하다.</b> 이것이 NFR-SCALE-T01 의 전부이며, 절대값이 아니라
 *       <b>비율</b>로 측정되므로 장비가 달라도 결론이 같다.</li>
 * </ol>
 * <p><b>Established:</b> three <b>structural</b> properties, each of which either holds or fails regardless of
 * hardware — that an index is actually used (the legacy's {@code LPAD} in a predicate made that impossible, and
 * no hardware compensates for it); that the last page of a capped range does not degrade worse than linearly,
 * which is the premise of choosing offset paging (ADR-TLK-028); and that export heap is independent of row
 * count, which is all of NFR-SCALE-T01 and is measured as a <b>ratio</b> rather than an absolute.</p>
 *
 * <p>측정된 밀리초는 <b>참고 수치</b>로 기록하고 단언하지 않는다 — 개발 장비의 절대 시간을
 * 통과 기준으로 삼으면 그 시험은 다른 사람의 노트북에서 실패한다.</p>
 * <p>The measured milliseconds are <b>recorded as reference figures and not asserted</b>: making a development
 * machine's absolute timings a pass criterion produces a test that fails on someone else's laptop.</p>
 *
 * <p>기본 실행에서 제외한다({@code @Tag("load")}). 20만 행 적재와 두 번의 내보내기가 있어
 * 단위 시험 주기에 넣기에는 느리다.</p>
 * <p>Excluded from the default run via {@code @Tag("load")}: seeding 200,000 rows and running two exports is too
 * slow for the unit cycle.</p>
 *
 * // req: NFR-PERF-T01, NFR-PERF-T02, NFR-SCALE-T01, ADR-TLK-028, RISK-T08, RISK-T09
 */
@Tag("load")
class TalkHistoryLoadTest {

    /** 31일 상한을 채우는 행 수. / Rows filling the 31-day cap. */
    private static final int TOTAL_ROWS = 200_000;

    /** 화면 페이지 크기 — NFR-PERF-T01 이 측정하는 크기. / The page size NFR-PERF-T01 measures. */
    private static final int PAGE_SIZE = 100;

    /** P95 를 뽑기 위한 반복 횟수. / Iterations used to derive a P95. */
    private static final int SAMPLES = 40;

    private static EmbeddedPostgres postgres;
    private static SqlSessionFactory sessionFactory;
    private static final List<String> REPORT = new ArrayList<>();

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
        // 측정 수치를 한 곳에 모아 출력한다. 단언하지 않는 값이므로 <b>보고</b>가 그 역할이다 —
        // 어디에도 남지 않으면 측정하지 않은 것과 같다.
        // The figures are printed together. They are not asserted, so <b>reporting</b> is their purpose: a
        // measurement recorded nowhere is the same as one not taken.
        System.out.println("=== TalkHistoryLoadTest reference figures (dev hardware, not a production SLA) ===");
        REPORT.forEach(System.out::println);
        if (postgres != null) {
            postgres.close();
        }
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE FT_APITR_HSTR (
                      TRDD VARCHAR(8), FINTECH_ISCD VARCHAR(10), IS_TUNO VARCHAR(20),
                      API_SVC_CD VARCHAR(50), PRSU VARCHAR(1), FINTECH_RPCD VARCHAR(10),
                      RGDT VARCHAR(14), LAST_AMDT VARCHAR(14)
                    )""");
            statement.execute("""
                    CREATE TABLE FT_FTIS_INFO (
                      FINTECH_ISCD VARCHAR(10), ISNM VARCHAR(100)
                    )""");
            // 운영에 있을 것으로 기대되는 인덱스. 이 시험의 목적 하나가 <b>술어가 이것을 쓸 수
            // 있는 형태인지</b> 확인하는 것이므로, 인덱스를 두지 않으면 아무것도 확인하지 못한다.
            // The index production is expected to have. One purpose of this test is checking that the predicate
            // is in a shape that <b>can use</b> it, so omitting the index would verify nothing.
            statement.execute(
                    "CREATE INDEX ix_apitr_trdd_rgdt ON FT_APITR_HSTR (TRDD, RGDT DESC, IS_TUNO DESC)");
            statement.execute("CREATE INDEX ix_apitr_tuno ON FT_APITR_HSTR (IS_TUNO)");
            statement.execute("CREATE UNIQUE INDEX ux_ftis ON FT_FTIS_INFO (FINTECH_ISCD)");
        }
    }

    /**
     * 31일에 걸쳐 20만 행을 적재한다. / Seeds 200,000 rows across 31 days.
     *
     * <p>동시각 밀집을 <b>의도적으로</b> 만든다: 초당 여러 행이 같은 {@code RGDT} 를 갖는다.
     * 운영 화면 캡처가 11개 행이 같은 초를 공유하는 모습이었으므로, 그 밀도가 없는 데이터로
     * 페이징을 측정하면 실제와 다른 것을 재는 것이 된다(RISK-T08).</p>
     * <p>Tied timestamps are created <b>deliberately</b>: several rows per second share one {@code RGDT}. The
     * production screenshot showed eleven rows on one second, so measuring paging against data without that
     * density would measure the wrong thing (RISK-T08).</p>
     */
    private static void seed(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement institution = connection.prepareStatement(
                    "INSERT INTO FT_FTIS_INFO VALUES (?, ?)")) {
                for (int i = 0; i < 20; i++) {
                    institution.setString(1, String.format("K%05d", i));
                    institution.setString(2, "기관" + i);
                    institution.addBatch();
                }
                institution.executeBatch();
            }

            String[] apis = {
                "ADV_KKO_AT_SEND", "ADV_KKO_FT_SEND", "ADV_KKO_AT_SEND_M", "ADV_COM_GET_STATUS"
            };

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO FT_APITR_HSTR VALUES (?,?,?,?,?,?,?,?)")) {
                for (int i = 0; i < TOTAL_ROWS; i++) {
                    int day = 1 + (i % 31);
                    // 하루 안에서 6행마다 같은 초를 공유한다 — 운영 캡처의 밀도와 같은 정도.
                    // Within a day, every six rows share one second — comparable to the screenshot's density.
                    int secondOfDay = (i / 6) % 86_400;
                    String trdd = String.format("202608%02d", day);
                    String rgdt = trdd + String.format("%02d%02d%02d",
                            secondOfDay / 3600, (secondOfDay % 3600) / 60, secondOfDay % 60);

                    insert.setString(1, trdd);
                    insert.setString(2, String.format("K%05d", i % 20));
                    insert.setString(3, String.format("%020d", i));
                    insert.setString(4, apis[i % apis.length]);
                    insert.setString(5, String.valueOf(i % 3 == 0 ? 1 : (i % 3 == 1 ? 0 : 9)));
                    insert.setString(6, null);
                    insert.setString(7, rgdt);
                    insert.setString(8, rgdt);
                    insert.addBatch();

                    if (i % 10_000 == 0) {
                        insert.executeBatch();
                    }
                }
                insert.executeBatch();
            }
            connection.commit();

            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE FT_APITR_HSTR");
                statement.execute("ANALYZE FT_FTIS_INFO");
            }
            connection.commit();
        }
    }

    private static SqlSessionFactory buildSessionFactory(DataSource dataSource) throws Exception {
        Configuration configuration = new Configuration(
                new Environment("load", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(TalkHistoryMapper.class);
        String resource = "mybatis/mapper/biztalk/TalkHistoryMapper.xml";
        try (InputStream in = TalkHistoryLoadTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).isNotNull();
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /** 31일 상한을 꽉 채운 조건. / Criteria filling the 31-day cap. */
    private static TalkHistoryCriteria fullRange(int page, int size) {
        return new TalkHistoryCriteria(
                TalkPeriodPolicy.validate("20260801", "20260831", null, null),
                new PrincipalScope(null, true, false),
                null, null, null,
                BizTalkApiRegistry.withDefaults().codes(), page, size);
    }

    private static List<TalkHistoryMapper.TalkHistoryRowRecord> page(TalkHistoryCriteria c) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkHistoryMapper.class).findPage(c);
        }
    }

    private static int count(TalkHistoryCriteria c) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(TalkHistoryMapper.class).countAll(c);
        }
    }

    /**
     * P95 를 밀리초로 반환한다. / Returns the P95 in milliseconds.
     *
     * <p>워밍업을 먼저 돌린다. JIT 와 버퍼 캐시가 식은 상태의 첫 호출을 P95 에 섞으면 측정이
     * 코드가 아니라 준비 상태를 재게 된다.</p>
     * <p>A warm-up runs first: mixing cold JIT and buffer-cache calls into the P95 would measure readiness
     * rather than the code.</p>
     */
    private static long p95(Runnable work) {
        for (int i = 0; i < 5; i++) {
            work.run();
        }
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            work.run();
            samples[i] = (System.nanoTime() - start) / 1_000_000;
        }
        java.util.Arrays.sort(samples);
        return samples[(int) Math.ceil(SAMPLES * 0.95) - 1];
    }

    @Nested
    @DisplayName("질의 구조 / query structure")
    class QueryStructure {

        private String explain(String sql) throws Exception {
            try (Connection connection = postgres.getPostgresDatabase().getConnection();
                 Statement statement = connection.createStatement();
                 var rs = statement.executeQuery("EXPLAIN " + sql)) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
                return plan.toString();
            }
        }

        @Test
        @DisplayName("선택적인 술어는 인덱스를 쓴다 — 전체 범위 조회는 그렇지 않고, 그것이 옳다")
        void selectivePredicateUsesAnIndexAndAFullRangeDoesNot() throws Exception {
            // ⚠ 이 시험의 첫 판본은 <b>모든</b> 조회가 인덱스를 쓴다고 단언했고 실패했다. 그것이
            // 잘못된 성질이었다.
            //
            // 20만 행이 정확히 31일에 걸쳐 있으므로, 31일 상한을 꽉 채운 조회는 <b>테이블 전체</b>를
            // 고른다. 그 경우 순차 스캔이 인덱스 스캔보다 빠르고, 플래너가 그것을 고르는 것은
            // 올바른 동작이다. "항상 인덱스"는 성능 성질이 아니라 미신이다.
            //
            // 옳은 성질은 <b>선택적인 술어가 인덱스를 쓸 수 있는 형태인지</b>다.
            //
            // The first version of this test asserted that <b>every</b> query uses an index, and it failed. That
            // was the wrong property. With 200,000 rows spanning exactly 31 days, a query filling the 31-day cap
            // selects <b>the whole table</b>, where a sequential scan beats an index scan and the planner
            // choosing one is correct behaviour. "Always an index" is superstition, not a performance property.
            // The right property is whether a <b>selective</b> predicate is in a shape that can use an index.
            String fullRange = explain(
                    "SELECT A.TRDD, A.IS_TUNO FROM FT_APITR_HSTR A "
                            + "WHERE A.TRDD BETWEEN '20260801' AND '20260831' "
                            + "  AND A.RGDT BETWEEN '20260801000000' AND '20260831235959' "
                            + "ORDER BY A.RGDT DESC, A.IS_TUNO DESC LIMIT 100");

            String singleDay = explain(
                    "SELECT A.TRDD, A.IS_TUNO FROM FT_APITR_HSTR A "
                            + "WHERE A.TRDD BETWEEN '20260815' AND '20260815' "
                            + "  AND A.RGDT BETWEEN '20260815090000' AND '20260815091000' "
                            + "ORDER BY A.RGDT DESC, A.IS_TUNO DESC LIMIT 100");

            REPORT.add("[plan] 31-day full range (selects the whole table):\n" + fullRange);
            REPORT.add("[plan] single day, 10-minute window (selective):\n" + singleDay);

            assertThat(singleDay)
                    .as("선택적인 술어가 인덱스를 쓰지 못하면 술어의 형태가 잘못된 것이다 / "
                            + "if a selective predicate cannot use an index, the predicate's shape is wrong")
                    .containsIgnoringCase("Index");

            // 전체 범위가 순차 스캔인 것은 결함이 아니지만 <b>운영 규모에서는 관측 대상</b>이다.
            // 개발 데이터는 31일에 20만 행이고 운영은 훨씬 크므로, 페이지마다 전체 스캔이 일어나는
            // 상황이 NFR-PERF-T01 을 위협할 수 있다 — B1 보고서가 이것을 지적한다.
            // A sequential scan for the full range is not a defect, but it <b>is</b> something to watch at
            // production volume: dev data is 200,000 rows over 31 days and production is far larger, so a full
            // scan per page could threaten NFR-PERF-T01. The B1 report raises it.
            REPORT.add("[finding] the 31-day full-range query plans a sequential scan here because it "
                    + "selects the whole table; at production volume this needs re-measuring");
        }

        @Test
        @DisplayName("술어의 LPAD 는 인덱스를 막지 않는다 — 내가 문서에 쓴 주장이 틀렸다")
        void lpadOnAParameterDoesNotPreventIndexUse() throws Exception {
            // ⚠ 이 시험은 <b>우리 문서의 오류</b>를 고정한다. ADR-TLK-025 와 매퍼 XML 주석은
            // 레거시의 SERIALNUM = LPAD(:SERIALNUM,10,'0') 이 "잘림 결함이면서 동시에 인덱스를
            // 쓸 수 없게 만든다"고 적었다. 앞의 절반은 참이고(LpadTruncationTest 가 실행으로 증명)
            // <b>뒤의 절반은 거짓이다</b>.
            //
            // sargability 문제는 함수가 <b>컬럼</b>에 적용될 때 생긴다 — LPAD(IS_TUNO,...) = :x.
            // 레거시는 함수를 <b>파라미터</b>에 적용했고(SERIALNUM = LPAD(:p,...)), PostgreSQL 은
            // 그것을 계획 시점에 상수로 접어 넣으므로 인덱스를 그대로 쓴다.
            //
            // This test pins <b>an error in our own documents</b>. ADR-TLK-025 and the mapper XML comment claimed
            // the legacy's SERIALNUM = LPAD(:SERIALNUM,10,'0') was "both the truncation bug and a sargability
            // problem". The first half is true — LpadTruncationTest proves it by execution — and <b>the second
            // half is false</b>. Sargability breaks when the function is applied to the <b>column</b>
            // (LPAD(IS_TUNO,...) = :x). The legacy applied it to the <b>parameter</b>, which PostgreSQL folds to
            // a constant at plan time, so the index is used normally.
            String javaPadded = explain(
                    "SELECT 1 FROM FT_APITR_HSTR A WHERE A.IS_TUNO = '00000000000000000042'");
            String lpadOnParameter = explain(
                    "SELECT 1 FROM FT_APITR_HSTR A WHERE A.IS_TUNO = LPAD('42', 20, '0')");
            String lpadOnColumn = explain(
                    "SELECT 1 FROM FT_APITR_HSTR A WHERE LPAD(A.IS_TUNO, 20, '0') = '00000000000000000042'");

            REPORT.add("[plan] serial, padded in Java:\n" + javaPadded);
            REPORT.add("[plan] serial, LPAD on the PARAMETER (the legacy's shape):\n" + lpadOnParameter);
            REPORT.add("[plan] serial, LPAD on the COLUMN (what we wrongly described):\n" + lpadOnColumn);

            assertThat(javaPadded).containsIgnoringCase("Index");

            assertThat(lpadOnParameter)
                    .as("레거시 형태도 인덱스를 쓴다 — 우리가 문서에 쓴 sargability 주장은 틀렸다 / "
                            + "the legacy shape uses the index too: our documented sargability claim was wrong")
                    .containsIgnoringCase("Index");

            assertThat(lpadOnColumn)
                    .as("컬럼에 함수를 적용한 형태만 순차 스캔이 된다 — 우리가 <b>묘사한</b> 결함은 "
                            + "레거시에 없었다 / only a function applied to the column falls back to a scan: "
                            + "the defect we <b>described</b> was not the one the legacy had")
                    .containsIgnoringCase("Seq Scan");

            REPORT.add("[finding] D-T9's truncation is real and proven; the sargability half of our claim "
                    + "was false. ADR-TLK-025 and TalkHistoryMapper.xml corrected 2026-08-19");
        }
    }

    @Nested
    @DisplayName("페이징 비용 / paging cost")
    class PagingCost {

        @Test
        @DisplayName("31일 상한에서 마지막 페이지가 첫 페이지 대비 유계다 — ADR-TLK-028")
        void deepOffsetIsBounded() {
            int total = count(fullRange(0, PAGE_SIZE));
            int lastPage = (total / PAGE_SIZE) - 1;

            long first = p95(() -> page(fullRange(0, PAGE_SIZE)));
            long last = p95(() -> page(fullRange(lastPage, PAGE_SIZE)));
            long countMs = p95(() -> count(fullRange(0, PAGE_SIZE)));

            REPORT.add(String.format(
                    "[perf] rows=%d pageSize=%d | first page P95=%dms | last page (offset %d) P95=%dms"
                            + " | count P95=%dms",
                    total, PAGE_SIZE, first, lastPage * PAGE_SIZE, last, countMs));

            // ADR-TLK-028 은 "깊은 오프셋 비용이 기간 상한으로 유계"라는 전제 위에서 오프셋을
            // 선택했다. 그 전제를 여기서 확인한다. 절대 시간이 아니라 <b>배수</b>로 단언하는
            // 이유는 장비가 달라도 결론이 같아야 하기 때문이다.
            //
            // ADR-TLK-028 chose offset paging on the premise that deep-offset cost is bounded by the period cap.
            // That premise is checked here. The assertion is a <b>ratio</b> rather than an absolute time so the
            // conclusion holds on different hardware.
            assertThat(last)
                    .as("마지막 페이지가 첫 페이지의 50배를 넘으면 ADR-TLK-028 의 전제가 깨진다 — "
                            + "그때의 대응은 keyset next/prev 를 번호 페이저와 <b>함께</b> 두는 것이다 / "
                            + "if the last page exceeds 50x the first, ADR-TLK-028's premise fails and the "
                            + "response is keyset next/prev alongside the numbered pager")
                    .isLessThan(Math.max(50L * Math.max(first, 1L), 50L));
        }

        @Test
        @DisplayName("동시각 밀집에서도 페이징이 행을 잃지 않는다 — D-T10 을 규모에서 재확인")
        void pagingStaysCorrectAtVolume() {
            // T1 의 100행 시험과 같은 성질을 20만 행에서 다시 본다. 표본을 늘리는 것이 아니라
            // <b>밀도</b>가 다르다: 여기서는 초당 6행이 같은 RGDT 를 갖는다.
            // The same property as T1's 100-row test, re-checked at 200,000. Not a larger sample but a different
            // <b>density</b>: six rows per second share one RGDT here.
            int size = 997;   // 총건수를 나누지 못하는 크기 / a size that does not divide the total
            int total = count(fullRange(0, size));
            int pages = (total + size - 1) / size;

            java.util.Set<String> seen = new java.util.HashSet<>();
            int emitted = 0;
            for (int p = 0; p < pages; p++) {
                for (TalkHistoryMapper.TalkHistoryRowRecord row : page(fullRange(p, size))) {
                    seen.add(row.transactionNo());
                    emitted++;
                }
            }

            REPORT.add(String.format("[paging] total=%d pages=%d emitted=%d unique=%d",
                    total, pages, emitted, seen.size()));

            assertThat(emitted).as("중복 없이 / without duplicates").isEqualTo(total);
            assertThat(seen).as("누락 없이 / without omissions").hasSize(total);
        }
    }

    @Nested
    @DisplayName("내보내기 규모 / export scale")
    class ExportScale {

        /**
         * 내보내기 힙 사용량을 잰다. / Measures the export's heap usage.
         *
         * <p>절대 바이트가 아니라 <b>두 행 수 사이의 비율</b>이 답이다. NFR-SCALE-T01 은 "힙이
         * 행 수와 무관하다"이므로, 비율이 1 에 가까우면 성립하고 행 수에 비례하면 깨진다 —
         * 장비가 달라도 결론이 같은 형태의 측정이다.</p>
         * <p>The answer is the <b>ratio between two row counts</b>, not an absolute. NFR-SCALE-T01 says heap is
         * independent of row count, so a ratio near 1 holds and a ratio tracking the row count fails — a
         * measurement whose conclusion survives a change of hardware.</p>
         */
        private long heapForExport(int rows) throws Exception {
            StreamingWorkbookWriter writer = new StreamingWorkbookWriter();
            List<TalkHistoryRow> source = new ArrayList<>();
            // 행을 미리 만들지 않는다 — 그러면 측정 대상이 아닌 우리 목록이 힙을 쓴다.
            // 대신 지연 Iterable 로 흘려보내, 작성기만 힙을 쓰게 한다.
            // The rows are not pre-built: that would put our own list on the heap instead of the thing being
            // measured. A lazy Iterable streams them so only the writer holds memory.
            Iterable<TalkHistoryRow> lazy = () -> new java.util.Iterator<>() {
                private int i = 0;

                @Override
                public boolean hasNext() {
                    return i < rows;
                }

                @Override
                public TalkHistoryRow next() {
                    int n = i++;
                    return new TalkHistoryRow("20260819", "K00011", "기관",
                            String.format("%020d", n), "ADV_KKO_AT_SEND", "1", null,
                            "20260819112504", "20260819112504", true);
                }
            };
            assertThat(source).isEmpty();

            Runtime runtime = Runtime.getRuntime();
            System.gc();
            Thread.sleep(150);
            long before = runtime.totalMemory() - runtime.freeMemory();

            OutputStream sink = OutputStream.nullOutputStream();
            int written = writer.write(sink, "s", List.of("a", "b"), lazy,
                    row -> List.of(row.transactionNo(), row.statusCode()));
            assertThat(written).isEqualTo(rows);

            long after = runtime.totalMemory() - runtime.freeMemory();
            return Math.max(after - before, 0);
        }

        @Test
        @DisplayName("내보내기 힙이 행 수와 무관하다 — NFR-SCALE-T01")
        void heapIsIndependentOfRowCount() throws Exception {
            long small = heapForExport(1_000);
            long large = heapForExport(100_000);

            REPORT.add(String.format("[scale] export heap: 1k rows=%dKB, 100k rows=%dKB, ratio=%.2f",
                    small / 1024, large / 1024, small == 0 ? 0.0 : (double) large / small));

            // ⚠ 레거시는 XSSFWorkbook 으로 전체 결과를 첫 바이트 전에 힙에 올렸다. 100배 행에
            // 100배 힙이면 그 형태가 돌아온 것이다. 여기서는 창(window) 밖 행이 임시 파일로
            // 밀려나므로 비율이 행 수 비율보다 훨씬 작아야 한다.
            //
            // The legacy used XSSFWorkbook and put the whole result on the heap before the first byte. A 100x
            // heap for 100x rows would mean that shape is back. Here rows outside the window are flushed to
            // temporary files, so the ratio must be far below the row-count ratio.
            assertThat(large)
                    .as("100배 행에 힙이 25배를 넘으면 스트리밍이 동작하지 않는 것이다 / "
                            + "heap above 25x for 100x rows means the streaming is not working")
                    .isLessThan(Math.max(25L * Math.max(small, 1L), 64L * 1024 * 1024));
        }
    }
}
