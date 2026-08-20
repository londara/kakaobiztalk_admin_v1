package com.webcash.iris.biztalk.infra.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이용기관 매퍼 XML 의 형태 검증 — 수정 경로.
 * Shape verification for the 이용기관 mapper XML — the edit path.
 *
 * <h2>이 테스트가 통합 테스트가 아닌 이유 / why this is not an integration test</h2>
 * <p>이 환경에는 Docker 가 없어 Testcontainers 를 기동할 수 없다(RISK-I09). 여기서는
 * <b>대체물</b>로 XML 자체를 읽으며 <b>동등물이 아니다</b> — 컬럼이 실제로 존재하는지,
 * {@code to_char} 가 무엇을 반환하는지는 검증하지 못한다.</p>
 * <p>Docker is unavailable here so Testcontainers cannot start (RISK-I09). The XML itself is read
 * as a <b>substitute, not an equivalent</b>: it cannot verify that the columns exist or what
 * {@code to_char} returns.</p>
 *
 * <h2>그럼에도 가치가 있는 이유 / why it still earns its place</h2>
 * <p>이 경로의 결함 셋이 모두 <b>문장의 형태</b> 문제였기 때문이다. 등록과 수정이 한 문장인
 * 것(D-I6), {@code to_char} 패턴에 {@code HH} 가 없는 것(D-I9), 그리고 <b>있어서는 안 되는
 * 컬럼</b>({@code ATK}) — 셋 다 파일을 읽어 확인할 수 있고, 셋 다 리뷰가 놓치기 쉽다.
 * 특히 {@code ATK} 는 "편의상" 한 줄 추가하기 쉬운 종류의 컬럼이며, 추가되는 순간 마스킹된
 * 값이 자격증명으로 저장되는 경로가 열린다(TM-I022).</p>
 * <p>Because all three defects on this path were <b>statement-shape</b> problems: create and update
 * as one statement (D-I6), a {@code to_char} pattern missing its {@code HH} (D-I9), and a
 * <b>column that must not be there</b> ({@code ATK}). All three are checkable by reading the file
 * and all three are easy for a review to miss — and {@code ATK} in particular is the kind of line
 * someone adds "for convenience", which reopens the path for a masked value to be stored as the
 * credential (TM-I022).</p>
 *
 * // source: IDO.KKB_FT_FTIS_INFO_C001, IDO.KKB_FT_FTIS_INFO_L002
 * // req: FR-INSTC-002, FR-INSTC-004, FR-INSTC-006, FR-INSTC-011, FR-INSTC-013, RISK-I09
 */
class InstitutionAdminMapperXmlTest {

    private static final String XML = "mybatis/mapper/biztalk/InstitutionAdminMapper.xml";

    private static String read() throws IOException {
        try (InputStream in = InstitutionAdminMapperXmlTest.class.getClassLoader()
                .getResourceAsStream(XML)) {
            assertThat(in).as("%s must be on the classpath", XML).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * XML 주석을 걷어낸 본문을 반환한다. / Returns the file with XML comments removed.
     *
     * <p>이 매퍼는 레거시 SQL 을 <b>그대로 인용</b>해 주석에 남긴다 — 무엇을 고쳤는지 보이게
     * 하기 위한 것이다. 주석을 포함한 채로 "없어야 한다" 를 단언하면 그 인용문에 걸려
     * 통과하지 않는다. 그래서 주석을 걷어낸 뒤 검사한다.</p>
     * <p>This mapper <b>quotes the legacy SQL verbatim</b> in its comments so the fix is visible. An
     * absence assertion made against the file as-is would trip over the quotation, so comments are
     * stripped first.</p>
     *
     * @return 주석 없는 본문 / the file without comments
     */
    private static String statements() throws IOException {
        return read().replaceAll("(?s)<!--.*?-->", "");
    }

    /** 공백을 하나로 줄인 본문. / The file with runs of whitespace collapsed. */
    private static String flat() throws IOException {
        return statements().replaceAll("\\s+", " ");
    }

    @Nested
    @DisplayName("수정 문장 / the update statement")
    class UpdateStatement {

        @Test
        @DisplayName("INSERT 가 없다 — 수정이 등록으로 바뀌지 않는다 / no INSERT: an update cannot become a create")
            // req: FR-INSTC-004, D-I6
        void hasNoInsert() throws Exception {
            // 레거시는 WITH UPSERT AS (UPDATE … RETURNING *) INSERT … WHERE NOT EXISTS 였다.
            // 그 형태가 D-I6 의 원인이다 — 등록 호출이 기존 기관을 덮어썼다.
            // The legacy was WITH UPSERT AS (UPDATE … RETURNING *) INSERT … WHERE NOT EXISTS, and
            // that shape is the cause of D-I6: a create call overwrote an existing institution.
            assertThat(statements().toUpperCase())
                    .doesNotContain("INSERT")
                    .doesNotContain("UPSERT")
                    .doesNotContain("RETURNING");
        }

        @Test
        @DisplayName("ATK 를 쓰지 않는다 / does not write ATK")
            // req: FR-INSTC-011, TM-I022
        void doesNotWriteTheKey() throws Exception {
            // ATK 가 나타나는 곳은 두 군데뿐이어야 한다: findByCode 의 SELECT(마스킹 전
            // 원본을 읽는다)와 rotateAuthKey 의 SET. 수정 문장에는 없어야 한다.
            // ATK may appear in exactly two places: the SELECT in findByCode (which reads the raw
            // value before masking) and the SET in rotateAuthKey. Never in the update.
            String update = between(flat(), "<update id=\"update\">", "</update>");
            assertThat(update).doesNotContain("ATK");
        }

        @Test
        @DisplayName("기관코드를 SET 하지 않는다 / does not SET the institution code")
            // req: FR-INSTC-002
        void doesNotWriteTheCode() throws Exception {
            String update = between(flat(), "<update id=\"update\">", "</update>");
            // WHERE 절에만 나타난다 — 대상 식별자이며 바꿀 수 있는 값이 아니다.
            // It appears in the WHERE clause only: a target identifier, not a settable value.
            assertThat(update).contains("WHERE FINTECH_ISCD = #{command.code}");
            assertThat(update.substring(0, update.indexOf("WHERE")))
                    .doesNotContain("FINTECH_ISCD");
        }

        @Test
        @DisplayName("등록 시점의 컬럼을 건드리지 않는다 / leaves the registration columns alone")
            // req: FR-INSTC-006
        void doesNotTouchRegistrationColumns() throws Exception {
            String update = between(flat(), "<update id=\"update\">", "</update>");
            assertThat(update).doesNotContain("RGDT").doesNotContain("RGSR_");
        }

        @Test
        @DisplayName("소유하지 않는 운영 컬럼을 건드리지 않는다 / leaves unowned operational columns alone")
            // req: ADR-INST-016 rule 4, TM-I015
        void doesNotTouchUnownedColumns() throws Exception {
            // FT_FTIS_INFO 는 40개 이상의 컬럼을 갖고, 그 대부분은 레거시 발송 런타임의 것이다.
            // FT_FTIS_INFO has 40+ columns, most of them the legacy send runtime's.
            String update = between(flat(), "<update id=\"update\">", "</update>");
            assertThat(update)
                    .doesNotContain("SRVR_IP")
                    .doesNotContain("GRAMT")
                    .doesNotContain("BSNN_STTS_CKYN");
        }
    }

    @Nested
    @DisplayName("시각 / the timestamp")
    class Timestamps {

        @Test
        @DisplayName("HH24 를 포함한 패턴을 쓴다 / uses the pattern with HH24")
            // req: FR-INSTC-006, FR-INSTC-013, ADR-INST-017, D-I9
        void usesHh24() throws Exception {
            assertThat(flat()).contains("to_char(now(),'YYYYMMDDHH24MISS')");
        }

        @Test
        @DisplayName("HH 가 빠진 레거시 패턴이 없다 / the HH-less legacy pattern is absent")
            // req: FR-INSTC-006, D-I9
        void hasNoHhLessPattern() throws Exception {
            // 레거시는 'YYYYMMDD24MISS' 였다. 시(時) 자리에 리터럴 24 가 기록되며, 목록이
            // substring(0,8) 만 보여 4년간 아무도 보지 못했다.
            // The legacy pattern was 'YYYYMMDD24MISS', writing a literal 24 in the hour position —
            // invisible for four years because the grid showed only substring(0,8).
            assertThat(statements()).doesNotContain("YYYYMMDD24MISS");
        }

        @Test
        @DisplayName("시각이 바인딩 파라미터가 아니다 / the timestamp is not a bound parameter")
            // req: FR-INSTC-013, ADR-INST-017
        void timestampIsNotBound() throws Exception {
            // 애플리케이션이 값을 넘길 수 있으면 UTC Clock 이 쓰일 여지가 생긴다. 그러면
            // 포털이 쓴 행이 레거시가 같은 컬럼에 쓴 행보다 9시간 뒤처진다.
            // If the application could supply the value, the UTC clock would be available for use,
            // leaving portal rows nine hours behind the legacy's in the same column.
            assertThat(statements())
                    .doesNotContain("#{command.timestamp}")
                    .doesNotContain("#{timestamp}");
        }
    }

    @Nested
    @DisplayName("상세조회 / the detail read")
    class DetailRead {

        @Test
        @DisplayName("논리 삭제된 기관을 열지 않는다 / does not open a logically deleted institution")
            // req: ADR-INST-014, FR-INSTL-005
        void excludesDeleted() throws Exception {
            String select = between(flat(), "<select id=\"findByCode\"", "</select>");
            assertThat(select).contains("IS_STTS &lt;&gt; 'D'");
        }

        @Test
        @DisplayName("실제 컬럼명으로 조회한다 / reads the real column names")
            // req: CONST-DATA-I04, RISK-I05
        void usesRealColumnNames() throws Exception {
            String select = between(flat(), "<select id=\"findByCode\"", "</select>");
            assertThat(select)
                    .contains("FINTECH_ISCD")
                    .contains("ISNM")
                    .contains("ISENGNM")
                    .contains("IS_STTS")
                    // 계약 필드명은 컬럼명이 아니다 — 이 혼동이 RISK-I05 였다.
                    // Contract field names are not column names; that confusion was RISK-I05.
                    .doesNotContain("IS_NM")
                    .doesNotContain("USE_YN");
        }
    }

    @Nested
    @DisplayName("재발급 문장 / the rotation statement")
    class RotationStatement {

        @Test
        @DisplayName("인증키를 쓰는 유일한 문장이다 / it is the only statement that writes the key")
            // req: FR-ATK-005, FR-INSTC-011
        void isTheOnlyKeyWriter() throws Exception {
            String flat = flat();
            String rotate = between(flat, "<update id=\"rotateAuthKey\">", "</update>");
            assertThat(rotate).contains("SET ATK = #{authKey}");

            // 파일 전체에서 "ATK =" 는 이 한 번만 나타나야 한다.
            // Across the whole file, "ATK =" must appear exactly once.
            assertThat(flat.split("ATK = ", -1)).hasSize(2);
        }

        @Test
        @DisplayName("행위자와 시각을 함께 갱신한다 / updates the actor and the timestamp with it")
            // req: FR-INSTC-007, FR-INSTC-012, FR-INSTC-013
        void recordsActorAndTime() throws Exception {
            String rotate = between(flat(), "<update id=\"rotateAuthKey\">", "</update>");
            assertThat(rotate)
                    .contains("LSED_ID = #{actorId}")
                    .contains("LSED_NM = #{actorId}")
                    .contains("to_char(now(),'YYYYMMDDHH24MISS')");
        }
    }

    /**
     * 두 표지 사이의 본문을 반환한다. / Returns the text between two markers.
     *
     * @param source 본문 / the text
     * @param start  시작 표지 / the opening marker
     * @param end    끝 표지 / the closing marker
     * @return 사이의 본문 / the text in between
     */
    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("marker '%s' must be present", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("marker '%s' must follow '%s'", end, start).isNotNegative();
        return source.substring(from, to);
    }
}
