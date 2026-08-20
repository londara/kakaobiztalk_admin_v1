package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 톡전송 거래내역 매퍼 XML 의 형태 검증.
 * Shape verification for the 톡전송 transaction-history mapper XML.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / why this is not an integration test</h2>
 * <p>Docker 사용이 금지되어 있어(RISK-T13) 여기서는 <b>대체물</b>로 XML 자체를 읽는다 —
 * 동등물이 아니다. 컬럼이 실제로 존재하는지, 타입이 맞는지, {@code lpad} 가 무엇을 하는지는
 * 검증하지 못한다. TEST-PLAN-TALK §9 가 무엇이 이 환경에서 검증되지 않는지 명시한다.</p>
 * <p>Docker is prohibited (RISK-T13), so the XML itself is read as a <b>substitute, not an
 * equivalent</b>: it cannot verify that the columns exist, that their types match, or what
 * {@code lpad} does. TEST-PLAN-TALK §9 states what this environment does not verify.</p>
 *
 * <h2>그럼에도 가치가 있는 이유 / why it still earns its place</h2>
 * <p>이 슬라이스에서 가장 심각한 결함들이 <b>SQL 문장의 형태</b> 문제였기 때문이다.
 * {@code ORDER BY} 에 동순위 결정자가 없는 것(D-T10), 건수 질의가 없는 것(D-T11),
 * {@code CASE WHEN :x = '' THEN 1=1} 형태(D-T31), 그리고 무엇보다 <b>프로젝션에 없어야 할
 * 컬럼</b>(CONST-SEC-T01) — 넷 모두 파일을 읽어 확인할 수 있고, 넷 모두 리뷰가 놓치기 쉽다.</p>
 * <p>Because this slice's most serious defects were <b>statement-shape</b> problems: a missing
 * tiebreaker in {@code ORDER BY} (D-T10), no count query (D-T11), the
 * {@code CASE WHEN :x = '' THEN 1=1} form (D-T31), and above all <b>columns that must not appear in the
 * projection</b> (CONST-SEC-T01). All four are checkable by reading the file, and all four are easy for
 * a review to miss.</p>
 *
 * // req: FR-TLK-005, FR-TLK-006, FR-TLK-011, CONST-SEC-T01, RISK-T13
 */
class TalkHistoryMapperXmlTest {

    private static final String XML = "mybatis/mapper/biztalk/TalkHistoryMapper.xml";

    /**
     * 이 슬라이스가 절대 선택하지 않아야 하는 컬럼.
     * The columns this slice must never select.
     *
     * <p>{@code FT_APITR_HSTR} 는 이 화면의 테이블이 아니라 전체 핀테크 API 의 거래 로그다.
     * 계좌번호·카드번호·거래금액·사업자번호·응답 전문 원문이 같은 행에 있다.</p>
     * <p>{@code FT_APITR_HSTR} is not this screen's table but the whole fintech estate's transaction
     * log: account numbers, card numbers, amounts, business numbers and raw response telegrams sit on
     * the same row.</p>
     */
    // source: IDO.FT_APITR_HSTR_C001 — the insert's column list
    // req: CONST-SEC-T01
    private static final List<String> FORBIDDEN_COLUMNS = List.of(
            "FIN_ACNO", "ACNO", "CANO", "FIN_CARD", "TRAM",
            "BRNO", "INTT_DMND_TTNO", "RSPN_TLGR_CNTN", "BNCD", "FINTECH_RSMS");

    private static String read() throws IOException {
        try (InputStream in = TalkHistoryMapperXmlTest.class.getClassLoader()
                .getResourceAsStream(XML)) {
            assertThat(in).as("%s must be on the classpath", XML).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * XML 주석을 걷어낸 본문을 반환한다. / Returns the file with XML comments removed.
     *
     * <p>이 매퍼는 <b>왜 그렇게 쓰지 않았는지</b>를 주석으로 길게 남긴다 — 잘못된 형태를
     * 그대로 인용해 두기 때문에, 주석을 포함한 채로 "없어야 한다"를 단언하면 설명문에 걸려
     * <b>거짓 실패</b>가 난다. 형제 슬라이스의 {@code AggregateMapperXmlTest} 가 처음 실행에서
     * 정확히 그렇게 실패했다.</p>
     * <p>This mapper carries long comments about <b>what was not done and why</b>, quoting the wrong
     * forms verbatim. Asserting absence against the raw file trips over the explanation and produces a
     * <b>false failure</b> — as the sibling slice's {@code AggregateMapperXmlTest} did on its first
     * run.</p>
     */
    private static String readWithoutComments() throws IOException {
        return read().replaceAll("(?s)<!--.*?-->", "");
    }

    @Nested
    @DisplayName("프로젝션 / projection")
    class Projection {

        @Test
        @DisplayName("금지 컬럼이 어디에도 나타나지 않는다 — CONST-SEC-T01")
        void forbiddenColumnsAbsent() throws IOException {
            String sql = readWithoutComments();

            for (String column : FORBIDDEN_COLUMNS) {
                assertThat(sql)
                        .as("%s 는 이 슬라이스의 어떤 질의에도 나타나서는 안 된다 (CONST-SEC-T01) / "
                                + "%s must not appear in any query in this slice", column, column)
                        .doesNotContain(column);
            }
        }

        @Test
        @DisplayName("SELECT * 가 없다")
        void noSelectStar() throws IOException {
            // 이 테이블에서 SELECT * 는 25개 컬럼 전체를 뜻하며, 그중 16개는 이 화면이 볼
            // 이유가 없다. 정적 검사가 패키지 전체에 이 규칙을 강제한다.
            // On this table SELECT * means all 25 columns, 16 of which this screen has no business
            // seeing. A static check enforces the rule across the package.
            assertThat(readWithoutComments()).doesNotContain("SELECT *").doesNotContain("select *");
        }

        @Test
        @DisplayName("resultMap 이 9개 인자로 닫혀 있다")
        void resultMapIsClosedAtNineArgs() throws IOException {
            String xml = read();
            int start = xml.indexOf("id=\"talkHistoryRow\"");
            int end = xml.indexOf("</resultMap>", start);
            assertThat(start).as("talkHistoryRow resultMap must exist").isNotNegative();

            String block = xml.substring(start, end);
            long args = block.lines().filter(line -> line.contains("<arg ")).count();
            assertThat(args)
                    .as("행 프로젝션은 9개 컬럼으로 닫혀 있어야 한다 / "
                            + "the row projection must be closed at nine columns")
                    .isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("정렬과 페이징 / ordering and paging")
    class OrderingAndPaging {

        @Test
        @DisplayName("ORDER BY 에 동순위 결정자가 있다 — D-T10")
        void orderByHasATiebreaker() throws IOException {
            // ⚠ 레거시는 ORDER BY RGDT DESC 하나뿐이었다. 운영 화면 캡처에서 11개 행이 같은
            // 11:25:04 를 공유하므로 동시각은 이 화면에서 정상이며, 페이징과 결합되면 행이
            // 두 페이지에 나오거나 어느 페이지에도 나오지 않는다.
            //
            // The legacy had only ORDER BY RGDT DESC. The production screenshot shows eleven rows
            // sharing 11:25:04, so ties are normal here, and under paging a row appears on two pages
            // or on none.
            assertThat(readWithoutComments())
                    .as("동순위 결정자 없는 정렬은 페이징에서 행을 중복·누락시킨다 (D-T10) / "
                            + "an untied sort duplicates and drops rows under paging")
                    .contains("ORDER BY A.RGDT DESC, A.IS_TUNO DESC");
        }

        @Test
        @DisplayName("건수 질의가 존재한다 — D-T11")
        void countStatementExists() throws IOException {
            // 레거시는 페이지 정보를 넘기고도 건수를 되 읽지 않았고, 계약의 <out> 에도 그런
            // 필드가 없었다. 같은 폴더의 biztalk_admin_32_l001 은 이것을 올바르게 했다.
            // The legacy passed page info and never read the count back, and its contract declared no
            // such output. Its sibling in the same folder did it correctly.
            assertThat(readWithoutComments()).contains("id=\"countAll\"").contains("count(1)");
        }

        @Test
        @DisplayName("오프셋을 SQL 에서 계산하지 않는다 — ADR-TLK-028")
        void offsetIsNotComputedInSql() throws IOException {
            String sql = readWithoutComments();

            assertThat(sql).contains("OFFSET #{c.offset}");
            assertThat(sql)
                    .as("바인딩 파라미터 곱셈은 암묵적 계약이 된다 / "
                            + "multiplying bind parameters becomes an implicit contract")
                    .doesNotContain("#{c.page} * #{c.size}");
        }

        @Test
        @DisplayName("목록과 건수가 같은 술어 조각을 공유한다")
        void pageAndCountShareOnePredicate() throws IOException {
            String xml = readWithoutComments();

            assertThat(xml).contains("<sql id=\"whereClause\">");
            // 두 문장 모두 조각을 포함해야 한다. 한쪽에만 있는 조건이 생기면 페이저가
            // 조용히 틀린다 — 이 프로그램이 여섯 슬라이스 연속으로 만난 실패 양식이다.
            // Both statements must include the fragment: a condition present in only one silently
            // breaks the pager — the failure mode this programme has met in six consecutive slices.
            assertThat(xml.split("<include refid=\"whereClause\"/>", -1).length - 1)
                    .as("술어 조각이 목록과 건수 양쪽에 포함되어야 한다 / "
                            + "the predicate fragment must be included by both statements")
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("술어 형태 / predicate shape")
    class PredicateShape {

        @Test
        @DisplayName("CASE WHEN :x = '' THEN 1=1 형태를 쓰지 않는다 — D-T31")
        void noNeutralisedPredicateForm() throws IOException {
            // 레거시는 조건이 없을 때도 술어를 만들고 SQL 쪽에서 무력화했다. 그 형태는
            // 인덱스를 쓸 수 없게 만든다.
            // The legacy built a predicate even with no filter and neutralised it in SQL. That form
            // prevents index use.
            assertThat(readWithoutComments())
                    .doesNotContain("THEN 1=1")
                    .doesNotContain("THEN 1 = 1");
        }

        @Test
        @DisplayName("술어에 LPAD 가 없다 — D-T9")
        void noLpadInPredicate() throws IOException {
            // 레거시 상세 질의의 LPAD(:SERIALNUM,10,'0') 은 잘림 결함이면서 동시에 인덱스를
            // 쓸 수 없게 만들었다. 패딩은 TransactionSerial 이 자바에서 한다.
            // The legacy detail query's LPAD(:SERIALNUM,10,'0') was both the truncation defect and a
            // sargability problem. Padding happens in Java, in TransactionSerial.
            assertThat(readWithoutComments()).doesNotContain("LPAD").doesNotContain("lpad");
        }

        @Test
        @DisplayName("기관 술어가 존재한다 — D-T2")
        void institutionPredicateExists() throws IOException {
            // ⚠ 레거시에는 이 조건이 <b>존재하지 않았다</b>. 한 그리드가 모든 고객사의 거래를
            // 보여주었고, 인증된 아무 주체나 그것을 볼 수 있었다.
            // The legacy had <b>no such condition</b>. One grid showed every customer's transactions and
            // any authenticated principal could see it.
            assertThat(readWithoutComments())
                    .contains("A.FINTECH_ISCD = #{c.scope.institutionCode}");
        }

        @Test
        @DisplayName("빈 범위는 전체가 아니라 없음이다")
        void emptyScopeMeansNoRows() throws IOException {
            // 빈 값을 "전체"로 읽은 것이 D-T2 의 절반이었다. 허용 목록이 비면 아무 행도
            // 반환하지 않아야 한다.
            // Reading a blank as "all" was half of D-T2. An empty allow-list must return no rows.
            assertThat(readWithoutComments()).contains("AND 1 = 0");
        }

        @Test
        @DisplayName("범위는 API_SVC_CD 로 판단한다 — D-T15")
        void scopeUsesApiServiceCode() throws IOException {
            // 레거시는 API_CD 로 걸러 API_SVC_CD 를 표시했다 — 서로 다른 코드 체계이며,
            // 사용자는 필터가 무엇에 일치했는지 알 수 없었다.
            // The legacy filtered on API_CD and displayed API_SVC_CD — different code systems, and the
            // user could not tell what the filter had matched.
            String sql = readWithoutComments();

            assertThat(sql).contains("A.API_SVC_CD IN");
            assertThat(sql)
                    .as("범위 술어에 API_CD 가 쓰이면 표시 컬럼과 다른 체계가 된다 / "
                            + "using API_CD in the scope predicate reintroduces the mismatch")
                    .doesNotContain("A.API_CD");
        }
    }

    @Nested
    @DisplayName("쓰기 경로 부재 / no write path")
    class NoWritePath {

        @Test
        @DisplayName("삽입·수정·삭제 문장이 없다 — CONST-DATA-T01")
        void noWriteStatements() throws IOException {
            String xml = readWithoutComments();

            assertThat(xml).doesNotContain("<insert").doesNotContain("<update")
                    .doesNotContain("<delete");
        }
    }

    @Nested
    @DisplayName("기관명 해석 / institution-name resolution")
    class InstitutionName {

        @Test
        @DisplayName("상관 서브쿼리 대신 조인을 쓴다 — D-T26")
        void usesJoinNotCorrelatedSubquery() throws IOException {
            String sql = readWithoutComments();

            assertThat(sql).contains("LEFT JOIN FT_FTIS_INFO B");
            assertThat(sql)
                    .as("행마다 평가되는 상관 서브쿼리가 남아 있으면 안 된다 / "
                            + "no per-row correlated subquery may remain")
                    .doesNotContain("(SELECT B.ISNM");
        }

        @Test
        @DisplayName("COALESCE 로 미해석을 감추지 않는다 — D-T26")
        void doesNotHideUnresolvedNames() throws IOException {
            // 미해석 기관명은 애플리케이션이 표식과 함께 표시해야 한다. SQL 에서 기본값을
            // 채우면 "이름 없는 기관"과 "조회가 실패한 기관"을 구분할 수 없게 된다 —
            // 레거시가 빈 칸으로 만든 것과 같은 결과다.
            // An unresolved name must be marked by the application. Defaulting it in SQL would make
            // "an institution with no name" and "a lookup that failed" indistinguishable — the same
            // outcome as the legacy's blank cell.
            assertThat(readWithoutComments()).doesNotContain("COALESCE(B.ISNM");
        }
    }
}
