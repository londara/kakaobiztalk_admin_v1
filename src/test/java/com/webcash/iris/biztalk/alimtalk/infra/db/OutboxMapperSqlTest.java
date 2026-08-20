package com.webcash.iris.biztalk.alimtalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.webcash.iris.biztalk.alimtalk.domain.OutboxEntry;
import com.webcash.iris.biztalk.alimtalk.domain.OutboxStatus;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link OutboxMapper} 의 SQL 을 고정한다. / Pins {@link OutboxMapper}'s SQL.
 *
 * <h2>이것이 무엇을 <b>하지 못하는지</b> 먼저 / what this cannot do, first</h2>
 * <p>Docker 가 금지되어(RISK-A12) 실제 PostgreSQL 통합 테스트를 돌릴 수 없다. 그러므로 이 묶음은
 * <b>대체물이며 동등물이 아니다</b>. 식별자 회귀는 막지만 다음을 <b>증명하지 못한다</b>:</p>
 * <ul>
 *   <li>테이블·컬럼이 실제로 존재하는가</li>
 *   <li>{@code FOR UPDATE SKIP LOCKED} 가 실제로 동시성을 막는가</li>
 *   <li>{@code UNIQUE} 제약이 실제로 중복 접수를 거절하는가</li>
 * </ul>
 * <p>With Docker prohibited (RISK-A12) there is no real PostgreSQL test, so this is a
 * <b>substitute, not an equivalent</b>: it catches identifier regressions but cannot show that the table
 * exists, that {@code SKIP LOCKED} truly prevents concurrent claims, or that the {@code UNIQUE}
 * constraint truly rejects a duplicate. Those are carried to A2-15.</p>
 *
 * <h2>DDL 과 대조하는 이유 / why it is compared against the DDL</h2>
 * <p>매퍼와 DDL 은 서로 다른 파일이고 함께 바뀌지 않는다. 컬럼 이름을 DDL 에서만 바꾸면 매퍼는
 * 컴파일되고 테스트도 통과하며, 실패는 <b>운영에서 첫 질의</b>에 나타난다. 그래서 이 테스트는
 * 매퍼의 SQL 문자열을 실제 DDL 파일과 대조한다 — 둘이 어긋나는 순간 빌드가 깨진다.</p>
 * <p>The mapper and the DDL are separate files that do not change together: renaming a column in the DDL
 * alone still compiles and still passes, and the failure appears on the <b>first query in
 * production</b>. So this test compares the mapper's SQL against the actual DDL file, and the build
 * breaks the moment they diverge.</p>
 *
 * // source: mapping/analysis/ANALYSIS-A2-02-existing-schema.md
 * // req: FR-ATS-001, FR-ATS-005, FR-ATS-009, RISK-A12
 */
class OutboxMapperSqlTest {

    private static final Path DDL =
            Path.of("src", "main", "resources", "db", "V2__alimtalk_outbox.sql");

    private static String sqlOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = OutboxMapper.class.getMethod(methodName, parameterTypes);
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return String.join(" ", insert.value());
        }
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return String.join(" ", select.value());
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return String.join(" ", update.value());
        }
        throw new AssertionError(methodName + " carries no SQL annotation");
    }

    private static String allSql() throws Exception {
        return String.join(
                "\n",
                sqlOf("insert", OutboxEntry.class),
                sqlOf("claim", List.class, LocalDateTime.class, int.class),
                sqlOf("markClaimed", long.class, LocalDateTime.class),
                sqlOf("recordOutcome", long.class, String.class, String.class),
                sqlOf("countUnfinished", String.class));
    }

    private static String ddl() throws Exception {
        return Files.readString(DDL, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("식별자 / identifiers")
    class Identifiers {

        @Test
        @DisplayName("DDL 파일이 존재한다 / the DDL file exists")
        // req: FR-ATS-001
        void ddlFileExists() {
            // 이 어서션이 먼저 있는 이유: DDL 이 사라지면 나머지 대조가 조용히 통과한다
            // (읽을 것이 없으면 어긋날 것도 없다). 그러면 이 묶음 전체가 무의미해진다.
            // This comes first because if the DDL disappears the remaining comparisons pass quietly —
            // nothing to read means nothing to disagree with — and the whole group becomes meaningless.
            assertThat(DDL).exists();
        }

        @Test
        @DisplayName("매퍼와 DDL 이 같은 테이블을 말한다 / mapper and DDL name the same table")
        // req: FR-ATS-001
        void mapperAndDdlNameTheSameTable() throws Exception {
            assertThat(allSql()).contains("KKB_ATK_SEND_OUTBOX");
            assertThat(ddl()).contains("CREATE TABLE KKB_ATK_SEND_OUTBOX");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "OUTBOX_ID", "IS_CD", "TRAN_ID", "MSG_ORDER", "PAYLOAD",
            "STATUS", "ATTEMPTS", "DUE_AT", "CLAIMED_UNTIL", "LAST_ERROR"
        })
        @DisplayName("매퍼가 쓰는 컬럼이 DDL 에 있다 / every column the mapper uses exists in the DDL")
        // req: FR-ATS-001, FR-ATS-005
        void everyColumnTheMapperUsesExistsInTheDdl(String column) throws Exception {
            assertThat(allSql()).as("mapper SQL must reference %s", column).contains(column);
            assertThat(ddl()).as("DDL must declare %s", column).contains(column);
        }

        @Test
        @DisplayName("공유 테이블을 건드리지 않는다 / no shared table is touched")
        // req: RISK-A06
        void noSharedTableIsTouched() throws Exception {
            // ADR-ATK-023 수정 1 로 KKB_ADMIN_SEND_HIS 에 대한 요구를 철회했고 RISK-A06 이
            // 소멸했다. 나중에 편의상 그 표를 다시 건드리면 소멸시킨 리스크가 조용히 돌아온다.
            // Amendment 1 withdrew the requirement on KKB_ADMIN_SEND_HIS and retired RISK-A06. Touching
            // that table again for convenience would quietly bring the retired risk back.
            assertThat(ddl())
                    .as("the DDL must not ALTER any existing table")
                    .doesNotContainIgnoringCase("ALTER TABLE");
            assertThat(allSql()).doesNotContain("KKB_ADMIN_SEND_HIS");
            assertThat(allSql()).doesNotContain("KKO_MSG_LOG");
        }
    }

    @Nested
    @DisplayName("FR-AZ-A02 — 범위 / scoping")
    class Scoping {

        @Test
        @DisplayName("기관별 집계가 IS_CD 로 한정된다 / the per-institution count is bounded by IS_CD")
        // req: FR-ATS-009, FR-AZ-A02
        void countIsBoundedByIsCd() throws Exception {
            // 레거시 KKB_MSG_TMPL_L003 은 범위 없이 전체를 돌려주었다. 범위 없는 집계는 그
            // 자체로 테넌트 경계를 넘는 조회다.
            // The legacy KKB_MSG_TMPL_L003 returned everything unscoped; an unscoped aggregate is itself a
            // cross-tenant read.
            assertThat(sqlOf("countUnfinished", String.class))
                    .contains("WHERE IS_CD = #{isCd}");
        }
    }

    @Nested
    @DisplayName("ADR-ATK-023 — 클레임 질의 / the claim query")
    class ClaimQuery {

        @Test
        @DisplayName("SKIP LOCKED 를 쓴다 / it uses SKIP LOCKED")
        // req: FR-ATS-005, ADR-ATK-023
        void usesSkipLocked() throws Exception {
            // FOR UPDATE 만 쓰면(SKIP LOCKED 없이) 인스턴스들이 한 줄로 서서 처리량이 하나만큼으로
            // 줄어든다. 기능은 맞고 성능만 무너지므로 테스트 없이는 눈에 띄지 않는다.
            // FOR UPDATE without SKIP LOCKED would queue the instances up and cut throughput to that of
            // one: functionally correct, quietly slow, and invisible without a test.
            assertThat(sqlOf("claim", List.class, LocalDateTime.class, int.class))
                    .contains("FOR UPDATE SKIP LOCKED");
        }

        @Test
        @DisplayName("예약 시각과 클레임 만료를 둘 다 본다 / it honours both DUE_AT and CLAIMED_UNTIL")
        // req: FR-ATS-005, FR-ATS-012
        void honoursBothDueAtAndClaimedUntil() throws Exception {
            // DUE_AT 을 빠뜨리면 예약 발송이 즉시 나간다(D-A32 재현). CLAIMED_UNTIL 을 빠뜨리면
            // 응답을 기다리는 행을 다시 집어 중복 발송한다.
            // Omitting DUE_AT sends reservations immediately (reproducing D-A32); omitting CLAIMED_UNTIL
            // re-claims a row whose response is outstanding and sends it twice.
            String sql = sqlOf("claim", List.class, LocalDateTime.class, int.class);

            assertThat(sql).contains("DUE_AT IS NULL OR DUE_AT");
            assertThat(sql).contains("CLAIMED_UNTIL IS NULL OR CLAIMED_UNTIL");
        }

        @Test
        @DisplayName("순서와 상한이 있다 / it is ordered and bounded")
        // req: FR-ATS-005, NFR-PERF-A02
        void isOrderedAndBounded() throws Exception {
            // ORDER BY 가 없으면 오래된 행이 영구히 뒤로 밀릴 수 있다. LIMIT 가 없으면 한 주기가
            // 표 전체를 잡는다.
            // Without ORDER BY an old row can be starved indefinitely; without LIMIT one cycle claims the
            // whole table.
            String sql = sqlOf("claim", List.class, LocalDateTime.class, int.class);

            assertThat(sql).contains("ORDER BY OUTBOX_ID");
            assertThat(sql).contains("LIMIT #{limit}");
        }
    }

    @Nested
    @DisplayName("FR-ATS-005 — 상태 / statuses")
    class Statuses {

        @ParameterizedTest
        @EnumSource(OutboxStatus.class)
        @DisplayName("모든 상태가 DDL 의 CHECK 에 있다 / every status appears in the DDL CHECK")
        // req: FR-ATS-005, CONST-DATA-A01
        void everyStatusAppearsInTheDdlCheck(OutboxStatus status) throws Exception {
            // 열거형에 값을 더하고 DDL 을 잊으면 INSERT 가 실행 시점에 실패하고, 그 실패는
            // 접수 자체를 잃게 만든다. 컴파일러는 이 어긋남을 보지 못한다.
            // Adding an enum value and forgetting the DDL makes the INSERT fail at runtime, losing the
            // acceptance. The compiler cannot see this divergence.
            assertThat(ddl())
                    .as("the CHECK constraint must permit %s", status)
                    .contains("'" + status.name() + "'");
        }

        @Test
        @DisplayName("미완료 집계가 종착 상태를 세지 않는다 / the unfinished count excludes terminal statuses")
        // req: FR-ATS-009, NFR-OPS-A03
        void unfinishedCountExcludesTerminalStatuses() throws Exception {
            String sql = sqlOf("countUnfinished", String.class);

            assertThat(sql).contains("'PENDING'").contains("'FAILED'").contains("'UNKNOWN'");
            assertThat(sql)
                    .as("SENT and DEAD are terminal and must not be counted as unfinished")
                    .doesNotContain("'SENT'")
                    .doesNotContain("'DEAD'");
        }
    }

    @Nested
    @DisplayName("FR-ATS-009 — 중복 접수 / duplicate acceptance")
    class DuplicateAcceptance {

        @Test
        @DisplayName("DDL 에 유일 제약이 있다 / the DDL carries the unique constraint")
        // req: FR-ATS-009, ADR-ATK-026
        void ddlCarriesTheUniqueConstraint() throws Exception {
            // 이것이 재시도 안전성의 DB 측 절반이다. 나머지 절반은 벤더의 멱등성이고 우리가
            // 강제할 수 없다(RISK-A07). 제약을 지우면 같은 (기관, 거래번호, 순번) 이 두 번
            // 접수되어 두 번 발송된다.
            // This is the database half of retry safety; the other half is vendor idempotency, which we
            // cannot enforce (RISK-A07). Dropping the constraint lets the same triple be accepted, and
            // therefore sent, twice.
            assertThat(ddl()).contains("UNIQUE (IS_CD, TRAN_ID, MSG_ORDER)");
        }

        @Test
        @DisplayName("삽입이 유일 제약을 우회하지 않는다 / the insert does not bypass the constraint")
        // req: FR-ATS-009
        void insertDoesNotBypassTheConstraint() throws Exception {
            // ON CONFLICT DO NOTHING 을 붙이면 중복 접수가 조용히 성공한 것처럼 보이고,
            // 운영자는 두 번 보냈다고 믿는다 — 또는 한 번도 안 갔다고 믿는다. 어느 쪽인지
            // 알 수 없게 된다. 예외가 올라와야 호출부가 FR-ATS-009 대로 원래 결과를 돌려줄 수 있다.
            // ON CONFLICT DO NOTHING would make a duplicate acceptance look like a quiet success, leaving
            // the operator believing either that two went out or that none did, with no way to tell. The
            // exception must propagate so the caller can return the original outcome per FR-ATS-009.
            String sql = sqlOf("insert", OutboxEntry.class);

            assertThat(sql).doesNotContainIgnoringCase("ON CONFLICT");
            assertThat(sql).doesNotContainIgnoringCase("IGNORE");
        }
    }
}
