package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-T9 의 기제를 실제 PostgreSQL 에서 재현한다 — 작업 T1-01a.
 * Reproduces D-T9's mechanism against a real PostgreSQL — task T1-01a.
 *
 * <h2>이 시험이 필요한 이유 / why this test is needed</h2>
 * <p>{@link com.webcash.iris.biztalk.domain.TransactionSerial} 의 단위 시험은 <b>우리 코드가
 * 잘라내지 않는다</b>는 것을 증명한다. 그것과 <b>PostgreSQL 의 {@code lpad} 가 실제로
 * 잘라낸다</b>는 것은 다른 주장이며, 후자가 D-T9 를 결함으로 만드는 사실이다. 후자를 단언하지
 * 않으면 우리는 "레거시가 왜 틀렸는지"를 문서로만 주장하는 셈이 된다.</p>
 * <p>{@link com.webcash.iris.biztalk.domain.TransactionSerial}'s unit tests prove <b>our code does not
 * truncate</b>. That is a different claim from <b>PostgreSQL's {@code lpad} actually truncating</b>, and
 * the latter is the fact that makes D-T9 a defect. Without asserting it, our account of why the legacy
 * was wrong rests on documentation alone.</p>
 *
 * <h2>Docker 없이 실행하는 방법 / running without Docker</h2>
 * <p>Docker 가 금지되어 Testcontainers 를 쓸 수 없다(RISK-T13). {@code embedded-postgres} 는 실제
 * PostgreSQL 바이너리를 <b>프로세스로</b> 띄우므로 Docker 없이 진짜 SQL 의미를 검증한다.
 * {@code lpad} 는 표준에 가까운 함수이므로 어떤 PostgreSQL 이든 같게 동작한다 — 반면
 * {@code decrypt}/{@code masking} 은 <b>사이트 정의 함수</b>여서 여기서 재현할 수 없고, 그래서
 * D-T6·D-T18 은 배치와 경계에서만 검증된다(TEST-PLAN-TALK §9).</p>
 * <p>Docker is prohibited so Testcontainers is unusable (RISK-T13). {@code embedded-postgres} launches a
 * real PostgreSQL binary <b>as a process</b>, so genuine SQL semantics are verified without Docker.
 * {@code lpad} is a near-standard function that behaves identically on any PostgreSQL — whereas
 * {@code decrypt}/{@code masking} are <b>site-defined</b> and cannot be reproduced here, which is why
 * D-T6 and D-T18 are verified only by placement and at the boundaries (TEST-PLAN-TALK §9).</p>
 *
 * // source: IDO.KKB_AT_MSG_L001 — AND ID = :ID AND SERIALNUM = LPAD(:SERIALNUM,10,'0')
 * // source: biztalk_admin_32_l001_act.jsp — StringUtils.stripStart(serialNum, "0")
 * // req: FR-TLKD-009, ADR-TLK-025, RISK-T13
 */
class LpadTruncationTest {

    /** 운영 화면 캡처에서 관측된 실제 거래고유번호. / A real serial as observed in the screenshot. */
    private static final String OBSERVED_20 = "00000026081900142813";

    /** 레거시가 선행 0 을 제거한 뒤의 값. / The value after the legacy stripped leading zeros. */
    private static final String STRIPPED_14 = "26081900142813";

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    private static String query(String sql) throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private static boolean queryBoolean(String sql) throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getBoolean(1);
        }
    }

    @Test
    @DisplayName("lpad 는 목표 폭보다 긴 입력을 거부하지 않고 잘라낸다 — D-T9 의 기제")
    void lpadTruncatesRatherThanRefusing() throws Exception {
        // ⚠ 이것이 D-T9 다. 거부하거나 원값을 돌려주는 것이 아니라 <b>조용히 잘라낸다</b>.
        // This is D-T9: it neither refuses nor passes the value through — it <b>silently truncates</b>.
        String truncated = query("SELECT lpad('" + STRIPPED_14 + "', 10, '0')");

        assertThat(truncated)
                .as("PostgreSQL 의 lpad 는 긴 입력을 잘라낸다 / PostgreSQL's lpad truncates a long input")
                .isEqualTo("2608190014")
                .hasSize(10);
    }

    @Test
    @DisplayName("레거시 경로 전체를 재현하면 다른 거래를 가리킨다 — D-T9 의 결과")
    void theLegacyPathPointsAtADifferentTransaction() throws Exception {
        // 레거시 경로: stripStart(20자리, "0") → lpad(…, 10, '0')
        // The legacy path: stripStart(20 chars, "0") → lpad(…, 10, '0')
        String stripped = query("SELECT ltrim('" + OBSERVED_20 + "', '0')");
        assertThat(stripped).isEqualTo(STRIPPED_14);

        String matched = query("SELECT lpad('" + stripped + "', 10, '0')");

        // 원래 찾으려던 거래번호와 다른 값으로 조회하게 된다. CONST-BIZ-T01 관점에서 이것은
        // 표시 결함이 아니라 <b>다른 기관의 메시지에 일치할 수 있는 경로</b>다.
        // The query ends up looking for a different serial than the one requested. Under CONST-BIZ-T01
        // that is not a display bug but <b>a path that can match another institution's messages</b>.
        assertThat(matched)
                .as("레거시는 요청된 거래번호가 아닌 값으로 조회했다 / "
                        + "the legacy queried for a value other than the one requested")
                .isNotEqualTo(STRIPPED_14)
                .isNotEqualTo(OBSERVED_20)
                .isEqualTo("2608190014");
    }

    @Test
    @DisplayName("목표 폭 이하 입력은 정상적으로 채워진다 — 그래서 결함이 오래 숨었다")
    void shortInputPadsNormally() throws Exception {
        // 10자리 이하 일련번호에서는 레거시 규칙이 <b>정확히 동작한다</b>. 결함이 오래 눈에
        // 띄지 않은 이유이며, 사례별 시험이 이것을 놓치는 이유다 — 짧은 값으로 한 번 시험하면
        // 통과한다.
        // For serials of ten characters or fewer the legacy rule is <b>exactly correct</b>. That is why the
        // defect stayed invisible, and why a case-by-case test misses it: one short value passes.
        assertThat(query("SELECT lpad('142813', 10, '0')")).isEqualTo("0000142813");
    }

    @Test
    @DisplayName("우리 규칙은 같은 입력에서 손실이 없다 — ADR-TLK-025")
    void ourRuleIsLossless() throws Exception {
        // 자바에서 패딩하므로 술어에 lpad 가 없다. 20자리 값은 20자리로 조회된다 — 그리고
        // 술어가 컬럼에 대한 함수 호출을 담지 않으므로 인덱스도 쓸 수 있다.
        // Padding happens in Java so there is no lpad in the predicate. A 20-character value is queried as
        // 20 characters — and since the predicate applies no function to the column, an index is usable.
        String ours = com.webcash.iris.biztalk.domain.TransactionSerial
                .parse(OBSERVED_20).orElseThrow().transactionForm();

        assertThat(ours).isEqualTo(OBSERVED_20);
        // 데이터베이스가 그 값을 그대로 비교한다는 것까지 확인한다. PostgreSQL 의 boolean 은
        // 텍스트로 't'/'f' 이므로 문자열이 아니라 boolean 으로 읽는다 — 이 시험이 존재하는 이유가
        // 바로 "실제 데이터베이스는 우리 가정과 다르게 값을 표현한다" 이므로, 여기서 그것을
        // 문자열로 가정하는 것은 스스로의 교훈을 무시하는 일이다.
        // Confirming the database compares that value verbatim. PostgreSQL renders boolean as 't'/'f' in
        // text, so it is read as a boolean rather than a string — this test exists precisely because a real
        // database represents values differently from our assumptions, so assuming a string here would
        // ignore its own lesson.
        assertThat(queryBoolean("SELECT '" + ours + "' = '" + OBSERVED_20 + "'")).isTrue();
    }
}
