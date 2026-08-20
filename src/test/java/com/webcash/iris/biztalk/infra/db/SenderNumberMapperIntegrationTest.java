package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webcash.iris.biztalk.domain.SenderNumberCriteria;
import com.webcash.iris.biztalk.domain.SenderNumberEntity;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SenderNumberMapper} 쓰기 경로 통합 검증 — 실제 PostgreSQL, 실제 매퍼 XML.
 * Write-path integration verification for {@link SenderNumberMapper}: real PostgreSQL, real XML.
 *
 * <h2>이 시험이 이 슬라이스에서 가장 중요한 이유 / why this is the slice's most important test</h2>
 * <p>D-S1 은 <b>계층 사이</b>의 결함이다. 목록이 값을 마스킹하고, 그리드가 그 값을 삭제 요청에
 * 싣고, 삭제 쿼리가 {@code decrypt(DP_NO) = :DP_NO} 로 행을 찾는다 — 셋 다 각자 옳았고 합쳐졌을
 * 때 0건을 지웠다. 그리고 0건 삭제는 SQL 오류가 아니므로 아무도 알아채지 못했다. 매퍼를 대역으로
 * 둔 시험은 <b>그 결함을 재현할 수 없다</b>: 대역은 언제나 시험이 정한 건수를 돌려주기 때문이다.</p>
 * <p>D-S1 is a defect <b>between layers</b>: the list masked a value, the grid carried it into the
 * delete request, and the delete matched on the decrypted column — each correct alone, together
 * deleting nothing. A zero-row delete is not a SQL error, so nobody noticed. A test with a mocked
 * mapper <b>cannot reproduce that</b>: a double always returns whatever row count the test chose.</p>
 *
 * <h2>대역 함수를 쓴다는 것과 그 한계 / stand-in functions, and their limit</h2>
 * <p>{@code ENCRYPT} / {@code decrypt} / {@code masking} 은 운영 스키마가 제공하며 이 저장소는 그
 * 정의를 갖고 있지 않다(ADR-005 §4.3 미해결). 여기서는 같은 이름·같은 시그니처의 대역을 만든다 —
 * {@code scripts/dev/local-db-functions.sql} 과 같은 방식이다.</p>
 * <p>These functions are provided by the production schema and this repository does not have their
 * definitions (ADR-005 §4.3, unresolved). Same-name, same-signature stand-ins are created here, the
 * same approach as {@code scripts/dev/local-db-functions.sql}.</p>
 *
 * <p><b>그러므로 이 시험이 증명하는 것과 증명하지 못하는 것을 분명히 해 둔다.</b></p>
 * <table>
 *   <caption>검증 범위 / verification scope</caption>
 *   <tr><th>증명한다 / proves</th><th>증명하지 못한다 / does not prove</th></tr>
 *   <tr><td>문장의 <b>형태</b> — 컬럼 목록과 값 목록의 정합, 별칭, 술어의 유무,
 *           {@code INSERT ... SELECT} 의 열 대응</td>
 *       <td>운영 {@code ENCRYPT} 의 결정성 — 스파이크 S1-01 이 답할 질문이며 V3 DDL 의
 *           선행 검사가 다시 묻는다</td></tr>
 *   <tr><td>0건 삭제가 <b>0을 반환</b>한다는 것과, 그것이 예외가 아니라는 것 — D-S1 의 전제</td>
 *       <td>운영 {@code masking} 의 실제 출력 형식</td></tr>
 *   <tr><td>전역 유일 인덱스가 실제로 <b>기관 간 중복을 거부</b>한다는 것</td>
 *       <td>운영 데이터에 이미 있는 중복의 규모(S2a-01 이 답한다)</td></tr>
 * </table>
 *
 * <p>Proves statement <b>shape</b> — column/value alignment, aliases, the presence or absence of a
 * predicate, the {@code INSERT … SELECT} column correspondence — and that a zero-row delete returns
 * zero without raising, which is D-S1's premise. Does not prove the production functions' behaviour.</p>
 *
 * // source: IDO.KKB_DPNO_LDGR_C001/_D001, IDO.KKB_DPNO_HIS_C001
 * // req: FR-SNDC-004, FR-SNDD-001, FR-SNDD-002, FR-SNDD-004, FR-SNDH-003, CONST-BIZ-D01,
 *         ADR-SND-017, ADR-SND-018, RISK-S13
 */
class SenderNumberMapperIntegrationTest {

    private static final String CODE = "K0ABCD";
    private static final String OTHER_CODE = "K0EFGH";
    private static final String ACTOR = "op@example.com";

    private static EmbeddedPostgres postgres;
    private static SqlSessionFactory sessionFactory;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        DataSource dataSource = postgres.getPostgresDatabase();
        createFunctions(dataSource);
        createSchema(dataSource);
        sessionFactory = buildSessionFactory(dataSource);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void emptyTables() throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM KKB_DPNO_HIS");
            statement.execute("DELETE FROM KKB_DPNO_ARCV");
            statement.execute("DELETE FROM KKB_DPNO_LDGR");
        }
    }

    /**
     * 운영 함수의 대역을 만든다. / Creates stand-ins for the production functions.
     *
     * <p>{@code ENCRYPT} 는 <b>결정적</b>으로 만든다. 그래야 §3 의 유일 인덱스를 이 시험에서
     * 검증할 수 있다. 운영이 결정적인지는 여기서 알 수 없으며(S1-01), 그것을 확인하는 일은 V3
     * DDL 의 선행 검사가 담당한다 — 비결정적이면 인덱스 생성 자체가 중단된다.</p>
     * <p>{@code ENCRYPT} is made <b>deterministic</b> so the unique index can be verified here at all.
     * Whether production is deterministic cannot be known here (S1-01); V3's precheck asks, and stops
     * the index creation if it is not.</p>
     */
    // req: ADR-005, ADR-SND-018, RISK-S07
    private static void createFunctions(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // 가역적이고 결정적인 대역. 암호학적 성질은 무관하다 — 검증 대상은 SQL 의 형태다.
            // A reversible, deterministic stand-in. Cryptographic properties are irrelevant: what is
            // under test is the shape of the SQL.
            statement.execute("""
                    CREATE FUNCTION ENCRYPT(raw_value text) RETURNS bytea
                        LANGUAGE sql IMMUTABLE STRICT
                    AS $$ SELECT convert_to('enc:' || raw_value, 'UTF8') $$
                    """);
            statement.execute("""
                    CREATE FUNCTION decrypt(cipher bytea) RETURNS text
                        LANGUAGE sql IMMUTABLE STRICT
                    AS $$ SELECT substring(convert_from(cipher, 'UTF8') from 5) $$
                    """);
            statement.execute("""
                    CREATE FUNCTION masking(raw_value text) RETURNS text
                        LANGUAGE sql IMMUTABLE STRICT
                    AS $$ SELECT CASE
                            WHEN length(raw_value) < 3 THEN repeat('*', length(raw_value))
                            ELSE left(raw_value,1) || repeat('*', length(raw_value)-2) || right(raw_value,1)
                          END $$
                    """);
        }
    }

    /**
     * 스키마를 만든다 — V3 DDL 과 같은 형태로. / Creates the schema, in V3's shape.
     *
     * <p>아카이브 테이블을 {@code LIKE KKB_DPNO_LDGR} 로 만드는 것까지 V3 와 같다. 컬럼 타입을
     * 손으로 적으면 원장과 어긋날 수 있고, 그 어긋남은 복원할 때 비로소 드러난다.</p>
     * <p>Including creating the archive with {@code LIKE KKB_DPNO_LDGR}, as V3 does: hand-written
     * types could differ from the ledger's, and the difference would surface only on restore.</p>
     */
    // req: CONST-DATA-D04, ADR-SND-017
    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE KKB_DPNO_LDGR (
                      IS_CD   VARCHAR(10) NOT NULL,
                      DP_NO   BYTEA       NOT NULL,
                      RGDT    VARCHAR(14),
                      RGSR_ID BYTEA,
                      RGSR_NM BYTEA,
                      UDDT    VARCHAR(14),
                      UDT_ID  BYTEA,
                      UDT_NM  BYTEA,
                      DSCP    VARCHAR(200)
                    )""");
            statement.execute("""
                    CREATE TABLE KKB_DPNO_HIS (
                      IS_CD   VARCHAR(10) NOT NULL,
                      DP_NO   BYTEA       NOT NULL,
                      ACN     VARCHAR(1)  NOT NULL,
                      RGDT    VARCHAR(14),
                      RGSR_ID BYTEA,
                      RGSR_NM BYTEA,
                      REASON  VARCHAR(100)
                    )""");
            statement.execute("""
                    CREATE TABLE FT_FTIS_INFO (
                      FINTECH_ISCD VARCHAR(10) NOT NULL,
                      ISNM         VARCHAR(100)
                    )""");
            statement.execute(
                    "INSERT INTO FT_FTIS_INFO VALUES ('" + CODE + "', '○○기관')");
            statement.execute(
                    "INSERT INTO FT_FTIS_INFO VALUES ('" + OTHER_CODE + "', '△△기관')");

            // V3 §2 그대로 / exactly V3 §2.
            statement.execute("CREATE TABLE KKB_DPNO_ARCV (LIKE KKB_DPNO_LDGR)");
            statement.execute("""
                    ALTER TABLE KKB_DPNO_ARCV
                      ADD COLUMN DEL_DT VARCHAR(14) NOT NULL,
                      ADD COLUMN DEL_ID BYTEA,
                      ADD COLUMN REASON VARCHAR(100)""");

            // V3 §3 그대로 / exactly V3 §3.
            statement.execute("CREATE UNIQUE INDEX UX_KKB_DPNO_LDGR_01 ON KKB_DPNO_LDGR (DP_NO)");
        }
    }

    private static SqlSessionFactory buildSessionFactory(DataSource dataSource) throws Exception {
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(SenderNumberMapper.class);

        // 실제 매퍼 XML 을 읽는다. 손으로 옮겨 쓴 SQL 을 검증하면 무엇도 증명하지 못한다.
        // The real mapper XML is loaded: verifying hand-copied SQL would prove nothing.
        String resource = "mybatis/mapper/biztalk/SenderNumberMapper.xml";
        try (InputStream in = SenderNumberMapperIntegrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static int register(String code, String number, String description) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(SenderNumberMapper.class).insertLedger(
                    new SenderNumberMapper.LedgerInsert(code, number, description, ACTOR));
        }
    }

    private static int archive(String code, String number, String reason) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(SenderNumberMapper.class).archive(code, number, ACTOR, reason);
        }
    }

    private static int deleteLive(String code, String number) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(SenderNumberMapper.class).deleteLive(code, number);
        }
    }

    private static int history(String code, String number, String action, String reason) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(SenderNumberMapper.class).insertHistory(
                    new SenderNumberMapper.HistoryInsert(code, number, action, reason, ACTOR));
        }
    }

    private static int countAnywhere(String number) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(SenderNumberMapper.class).countAnywhere(number);
        }
    }

    private static SenderNumberEntity findOne(String code, String number) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(SenderNumberMapper.class).findOne(code, number);
        }
    }

    private static List<SenderNumberEntity> list(String code) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(SenderNumberMapper.class)
                    .findPage(SenderNumberCriteria.of(code, 0, 20));
        }
    }

    private static List<String> query(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    @Nested
    @DisplayName("등록 / registration")
    class Registration {

        @Test
        @DisplayName("등록한 번호를 조회할 수 있다 / a registered number is readable")
            // req: FR-SNDC-001
        void registeredNumberIsReadable() {
            assertThat(register(CODE, "0212345678", "대표번호")).isEqualTo(1);

            SenderNumberEntity row = findOne(CODE, "0212345678");
            assertThat(row).isNotNull();
            assertThat(row.number()).isEqualTo("0212345678");
            assertThat(row.description()).isEqualTo("대표번호");
            assertThat(row.institutionName()).isEqualTo("○○기관");
        }

        @Test
        @DisplayName("D-S16 — 등록자 ID 가 평문으로 저장되지 않는다 / the actor id is not stored in clear")
            // req: NFR-SEC-PII-D01, AMB-S09
        void actorIdIsEncrypted() throws Exception {
            register(CODE, "0212345678", "대표번호");

            // 레거시는 RGSR_ID 에 이메일을 평문으로 넣으면서 RGSR_NM 은 암호화했다 — 같은 사람의
            // 같은 신원이 한 행에서 두 방식으로 저장되었다. AMB-S09 결정 B 로 둘 다 암호화한다.
            // The legacy stored the email in clear in RGSR_ID while encrypting RGSR_NM: one person's
            // identity stored two ways in one row. Under AMB-S09 ruling B both are encrypted.
            List<String> raw = query(
                    "SELECT convert_from(RGSR_ID,'UTF8') FROM KKB_DPNO_LDGR");
            assertThat(raw).hasSize(1);
            assertThat(raw.get(0)).isNotEqualTo(ACTOR).startsWith("enc:");

            List<String> decrypted = query("SELECT decrypt(RGSR_ID) FROM KKB_DPNO_LDGR");
            assertThat(decrypted).containsExactly(ACTOR);
        }

        @Test
        @DisplayName("등록·수정 시각이 모두 채워진다 / both timestamps are populated")
            // req: FR-SND-009
        void timestampsArePopulated() throws Exception {
            register(CODE, "0212345678", "대표번호");

            // 레거시 KKB_DPNO_LDGR_C001 은 RGDT 와 UDDT 를 모두 to_char(now(),'YYYYMMDDHH24MISS')
            // 로 채웠다. 형식(14자리)까지 확인한다 — 이용기관 슬라이스에서 같은 패턴의 HH 누락이
            // 결함이었다(D-I9).
            // The legacy insert filled both with to_char(now(),'YYYYMMDDHH24MISS'). The 14-character
            // format is asserted too: a missing HH in the same pattern was a defect in the
            // institution slice (D-I9).
            assertThat(query("SELECT RGDT FROM KKB_DPNO_LDGR").get(0))
                    .hasSize(14).containsOnlyDigits();
            assertThat(query("SELECT UDDT FROM KKB_DPNO_LDGR").get(0))
                    .hasSize(14).containsOnlyDigits();
        }
    }

    @Nested
    @DisplayName("D-S9 / CONST-BIZ-D01 — 전역 유일 / global uniqueness")
    class Uniqueness {

        @Test
        @DisplayName("다른 기관이 가진 번호도 중복으로 센다 / a number held by another institution counts")
            // req: FR-SNDC-004
        void countIsInstitutionBlind() {
            register(OTHER_CODE, "0212345678", "다른 기관의 번호");

            // 레거시 중복검사는 IS_CD 술어를 함께 걸었으므로 이 값이 0 이었다 — 그래서 같은 번호를
            // 여러 기관이 나란히 등록할 수 있었다(D-S9).
            // The legacy check carried an IS_CD predicate, so this was 0 and the same number could be
            // registered by several institutions (D-S9).
            assertThat(countAnywhere("0212345678")).isEqualTo(1);
        }

        @Test
        @DisplayName("T-T5 — 애플리케이션을 우회한 중복도 DB 가 막는다 / the DB refuses a duplicate that bypasses the application")
            // req: FR-SNDC-004, CONST-BIZ-D01, ADR-SND-018
        void databaseRefusesCrossInstitutionDuplicate() {
            register(OTHER_CODE, "0212345678", "다른 기관의 번호");

            // 이 단정이 ADR-SND-018 의 근거다. AOA_ADMIN 은 같은 테이블에 쓰는 두 번째
            // 애플리케이션이며 우리 코드를 거치지 않는다(RISK-S05) — 애플리케이션에만 규칙을
            // 두면 그 콘솔이 규칙을 우회한다. 여기서는 매퍼를 직접 불러 애플리케이션 검사를
            // 건너뛴 뒤, 데이터베이스가 여전히 거부하는지 확인한다.
            // This assertion is ADR-SND-018's basis. AOA_ADMIN writes the same table without passing
            // through our code (RISK-S05), so a rule held only in the application is one that console
            // bypasses. Here the mapper is called directly — skipping the application check — and the
            // database still refuses.
            // PostgreSQL 은 인용하지 않은 식별자를 소문자로 접는다 — 인덱스 이름은
            // ux_kkb_dpno_ldgr_01 로 보고된다. 이름을 확인하는 이유는 "무언가가 막았다" 가
            // 아니라 <b>이 제약이</b> 막았다는 것을 확인하기 위해서다.
            // PostgreSQL folds unquoted identifiers to lower case, so the index is reported as
            // ux_kkb_dpno_ldgr_01. The name is asserted to show that <b>this constraint</b> refused
            // it, not merely that something did.
            assertThatThrownBy(() -> register(CODE, "0212345678", "같은 번호"))
                    .hasMessageContaining("ux_kkb_dpno_ldgr_01");
        }

        @Test
        @DisplayName("아카이브된 번호는 중복이 아니다 / an archived number is not a duplicate")
            // req: FR-SNDD-008, ADR-SND-017
        void archivedNumberIsNotADuplicate() {
            register(CODE, "0212345678", "대표번호");
            archive(CODE, "0212345678", "해지 / withdrawn");
            deleteLive(CODE, "0212345678");

            // 살아 있는 행만 세므로 FR-SNDD-008(삭제한 번호의 재등록)이 특별 취급 없이 성립한다.
            // Counting live rows only makes FR-SNDD-008 — re-registering a deleted number — work with
            // no special case.
            assertThat(countAnywhere("0212345678")).isZero();
            assertThat(register(CODE, "0212345678", "재등록")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("D-S1 — 삭제가 실제로 지운다 / deletion actually deletes")
    class Deletion {

        @Test
        @DisplayName("삭제하면 원장에서 사라지고 아카이브에 남는다 / gone from the ledger, present in the archive")
            // req: FR-SNDD-001, FR-SNDD-003, ADR-SND-017
        void deletedRowLeavesTheLedgerAndEntersTheArchive() throws Exception {
            register(CODE, "0212345678", "대표번호");

            assertThat(archive(CODE, "0212345678", "해지 / withdrawn")).isEqualTo(1);
            assertThat(deleteLive(CODE, "0212345678")).isEqualTo(1);

            // 원장에 없다는 것이 발송 차단의 <b>전부</b>다. KAKAOTALK 은 행의 존재만으로 판단하며
            // 어떤 상태 컬럼도 읽지 않는다(ADR-SND-017).
            // Absence from the ledger <b>is</b> the send block: KAKAOTALK decides by row presence and
            // reads no status column (ADR-SND-017).
            assertThat(list(CODE)).isEmpty();
            assertThat(findOne(CODE, "0212345678")).isNull();
            assertThat(query("SELECT decrypt(DP_NO) FROM KKB_DPNO_ARCV"))
                    .containsExactly("0212345678");
        }

        @Test
        @DisplayName("아카이브는 원본 열을 그대로 옮긴다 / the archive carries the original columns across")
            // req: FR-SNDD-001, ADR-SND-017
        void archivePreservesTheOriginalRow() throws Exception {
            register(CODE, "0212345678", "대표번호");
            archive(CODE, "0212345678", "해지 / withdrawn");

            // 복원이 이 테이블의 존재 이유다(FR-SNDD-008, C-S02). 복원한 행이 원래 행과 다르면
            // 아카이브가 아니다. DP_NO 를 복호화해 다시 암호화하지 않는 것이 그 성질을 지킨다 —
            // ENCRYPT 가 비결정적이면 재암호화한 값은 다른 바이트열이 된다.
            // Restoration is this table's reason to exist (FR-SNDD-008, C-S02); an archive that
            // restores something else is not an archive. Copying DP_NO as stored — rather than
            // decrypting and re-encrypting — is what keeps that true under a non-deterministic
            // ENCRYPT.
            assertThat(query("SELECT DSCP FROM KKB_DPNO_ARCV")).containsExactly("대표번호");
            assertThat(query("SELECT decrypt(RGSR_ID) FROM KKB_DPNO_ARCV")).containsExactly(ACTOR);
            assertThat(query("SELECT REASON FROM KKB_DPNO_ARCV")).containsExactly("해지 / withdrawn");
            assertThat(query("SELECT DEL_DT FROM KKB_DPNO_ARCV").get(0)).hasSize(14);
            assertThat(query("SELECT decrypt(DEL_ID) FROM KKB_DPNO_ARCV")).containsExactly(ACTOR);

            // 원장의 DP_NO 바이트열과 아카이브의 것이 동일하다 / byte-identical ciphertext.
            assertThat(query("""
                    SELECT count(*)::text FROM KKB_DPNO_LDGR L
                      JOIN KKB_DPNO_ARCV A ON A.DP_NO = L.DP_NO
                    """)).containsExactly("1");
        }

        @Test
        @DisplayName("**D-S1 회귀** — 마스킹된 값으로는 아무 행도 지워지지 않는다 / a masked value deletes nothing")
            // req: FR-SNDD-002, FR-SND-007, NFR-OPS-D02
        void aMaskedValueMatchesNothingAndReportsZero() {
            register(CODE, "01012345678", "휴대전화");

            // 레거시가 실제로 보낸 값을 그대로 재현한다: 목록이 masking(decrypt(DP_NO)) 로
            // 마스킹한 표시값이다.
            // Reproducing exactly what the legacy sent: the display value the list produced with
            // masking(decrypt(DP_NO)).
            String masked = "0*********8";

            int deleted = deleteLive(CODE, masked);

            // **여기가 D-S1 의 전부다.** 0을 반환하고 예외를 던지지 않는다 — SQL 로서는 정상이다.
            // 레거시는 이 0을 확인하지 않았고, 이력을 쓰고 "정상적으로 처리되었습니다" 를 보여
            // 주었다. 이 시험은 <b>SQL 이 여전히 그렇게 동작한다</b>는 것을 확인한다: 방어는
            // 서비스가 이 반환값을 반드시 확인하는 데 있으며, 그것은
            // SenderNumberWriteServiceTest 가 단정한다.
            // **This is all of D-S1.** It returns zero and raises nothing — perfectly normal SQL. The
            // legacy did not check that zero, wrote the history row and displayed success. This test
            // confirms the SQL <b>still behaves that way</b>: the defence is that the service must
            // check this value, which SenderNumberWriteServiceTest asserts.
            assertThat(deleted).isZero();
            assertThat(findOne(CODE, "01012345678")).isNotNull();
        }

        @Test
        @DisplayName("**D-S1 회귀** — 콤마로 이어붙인 목록도 아무 행도 지우지 않는다 / a comma-joined list deletes nothing")
            // req: FR-SNDD-002, FR-SNDD-004
        void aCommaJoinedListMatchesNothing() {
            register(CODE, "0212345678", "대표번호");
            register(CODE, "15881234", "고객센터");

            // 레거시 다중 삭제가 보낸 형태다. 반복문은 각 번호로 delete 를 실행했지만 이력은
            // putAll(input) 으로 만들어 이 문자열 전체를 하나의 "번호" 로 암호화했다(D-S5).
            // The shape the legacy multi-delete sent. Its loop deleted per number, but the history was
            // built with putAll(input), encrypting this whole string as one "number" (D-S5).
            assertThat(deleteLive(CODE, "0212345678,15881234")).isZero();
            assertThat(list(CODE)).hasSize(2);
        }

        @Test
        @DisplayName("다른 기관의 번호는 지워지지 않는다 / another institution's number is not deleted")
            // req: FR-AZ-D03, NFR-SEC-TENANT-D01
        void otherInstitutionsRowIsUntouched() {
            register(OTHER_CODE, "0212345678", "다른 기관의 번호");

            // 문장에 IS_CD 술어가 살아 있는지 확인한다. 삭제 문장에서 그 술어가 빠지면 전역 유일
            // 제약 아래에서는 <b>아무도 알아채지 못한다</b> — 번호가 유일하므로 결과가 같아진다.
            // 그러나 그때 인가는 오직 서비스에만 남는다.
            // Asserts the IS_CD predicate is still in the statement. Dropping it would go
            // <b>unnoticed</b> under global uniqueness, since the number is unique and the outcome
            // matches — but authorization would then rest on the service alone.
            assertThat(deleteLive(CODE, "0212345678")).isZero();
            assertThat(countAnywhere("0212345678")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("D-S5 — 이력은 번호마다 한 건 / one history row per number")
    class History {

        @Test
        @DisplayName("3건을 지우면 각각 한 번호를 담은 이력 3건이 남는다 / three deletions leave three single-number rows")
            // req: FR-SNDD-004, FR-SNDH-003
        void threeDeletionsWriteThreeDistinctRows() throws Exception {
            for (String number : List.of("0212345678", "0312345678", "15881234")) {
                register(CODE, number, "번호");
                archive(CODE, number, "정리 / tidy-up");
                deleteLive(CODE, number);
                history(CODE, number, "D", "정리 / tidy-up");
            }

            List<String> stored = query("SELECT decrypt(DP_NO) FROM KKB_DPNO_HIS ORDER BY 1");
            assertThat(stored).containsExactly("0212345678", "0312345678", "15881234");
            // 레거시는 세 행 모두에 "0212345678,0312345678,15881234" 를 한 "번호" 로 저장했다.
            // The legacy stored the joined list as one "number" in all three rows.
            assertThat(stored).noneMatch(value -> value.contains(","));
        }

        @Test
        @DisplayName("이력이 행위·사유·행위자를 담는다 / the history carries action, reason and actor")
            // req: FR-SNDH-001
        void historyCarriesTheEvent() throws Exception {
            history(CODE, "0212345678", "C", "신규 등록 / new registration");

            assertThat(query("SELECT ACN FROM KKB_DPNO_HIS")).containsExactly("C");
            assertThat(query("SELECT REASON FROM KKB_DPNO_HIS"))
                    .containsExactly("신규 등록 / new registration");
            assertThat(query("SELECT decrypt(RGSR_NM) FROM KKB_DPNO_HIS")).containsExactly(ACTOR);
            assertThat(query("SELECT RGDT FROM KKB_DPNO_HIS").get(0)).hasSize(14);
        }

        @Test
        @DisplayName("같은 번호를 다시 등록해도 이력은 누적된다 / history accumulates across re-registration")
            // req: FR-SNDD-008, FR-SNDH-002
        void historyIsAppendOnlyAcrossReRegistration() throws Exception {
            register(CODE, "0212345678", "처음");
            history(CODE, "0212345678", "C", "최초 등록");
            archive(CODE, "0212345678", "해지");
            deleteLive(CODE, "0212345678");
            history(CODE, "0212345678", "D", "해지");
            register(CODE, "0212345678", "다시");
            history(CODE, "0212345678", "C", "재등록");

            // 이전 삭제가 이력에 남아 있어야 한다(FR-SNDD-008). 이력은 append-only 이므로
            // 재등록이 삭제 기록을 지우지 않는다.
            // The earlier deletion must remain visible (FR-SNDD-008): the history is append-only, so a
            // re-registration does not erase the deletion.
            assertThat(query("SELECT ACN FROM KKB_DPNO_HIS ORDER BY ACN"))
                    .containsExactly("C", "C", "D");
        }
    }
}
