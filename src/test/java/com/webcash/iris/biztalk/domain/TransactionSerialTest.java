package com.webcash.iris.biztalk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link TransactionSerial} 검증 — D-T9 와 D-T25 의 회귀 테스트.
 * Verification for {@link TransactionSerial}: the regression tests for D-T9 and D-T25.
 *
 * <p>레거시는 하나의 식별자를 세 가지로 정규화했고 그중 하나는 <b>손실이 있었다</b>.
 * {@code stripStart(v,"0")} 뒤에 {@code LPAD(:SERIALNUM,10,'0')} 을 적용하면, PostgreSQL 의
 * {@code lpad} 는 입력이 목표 폭보다 길 때 <b>거부하지 않고 잘라내므로</b> 20자리
 * 거래고유번호가 10자리로 절단된다 — <b>다른 거래의 메시지</b>에 일치하거나 아무것도 일치하지
 * 않는다. CONST-BIZ-T01 관점에서 이것은 표시 결함이 아니라 교차 기관 노출 경로다.</p>
 * <p>The legacy normalised one identifier three ways and one of them was <b>lossy</b>: after
 * {@code stripStart(v,"0")}, applying {@code LPAD(:SERIALNUM,10,'0')} truncates rather than refuses in
 * PostgreSQL, so a 20-character serial is cut to ten — matching <b>another transaction's messages</b>,
 * or none. Under CONST-BIZ-T01 that is a cross-institution disclosure path, not a display bug.</p>
 *
 * // source: biztalk_admin_30.js — padStart(20,'0'); biztalk_admin_32_l001_act.jsp — stripStart
 * // source: IDO.KKB_AT_MSG_L001 — LPAD(:SERIALNUM,10,'0'); IDO.KKB_FT_MSG_L001 — raw compare
 * // req: FR-TLK-009, FR-TLKD-009, ADR-TLK-025, AMB-T04
 */
class TransactionSerialTest {

    /** 운영 화면 캡처에서 관측된 실제 거래고유번호. / A real serial as observed in the screenshot. */
    private static final String OBSERVED_20 = "00000026081900142813";

    @Nested
    @DisplayName("정규화 / normalisation")
    class Normalisation {

        @Test
        @DisplayName("빈 값은 오류가 아니라 조건 없음이다")
        void blankIsAbsentNotInvalid() {
            // 거래일련번호는 선택 조건이다. 예외로 만들면 검색 조건을 비운 정상 요청이
            // 실패한다.
            // The serial is an optional filter; throwing would fail a legitimate empty request.
            assertThat(TransactionSerial.parse(null)).isEmpty();
            assertThat(TransactionSerial.parse("")).isEmpty();
            assertThat(TransactionSerial.parse("   ")).isEmpty();
        }

        @Test
        @DisplayName("숫자가 아닌 값은 거부된다")
        void nonNumericIsRefused() {
            assertThatThrownBy(() -> TransactionSerial.parse("26081900142813x"))
                    .isInstanceOf(TransactionSerial.InvalidSerialException.class)
                    .hasMessageContaining("숫자");
        }

        @ParameterizedTest(name = "\"{0}\" → 같은 거래 / the same transaction")
        @ValueSource(strings = {
                "00000026081900142813",
                "26081900142813",
                "0026081900142813",
                "  26081900142813  "
        })
        @DisplayName("패딩 유무와 무관하게 같은 거래에 일치한다 — D-T25")
        void paddingDoesNotChangeIdentity(String input) {
            // D-T25: 목록은 20자리로 채워 정확 일치, 상세는 0 을 떼고 다시 채웠다. 한 식별자에
            // 세 규칙이 있었고, 사용자가 어떻게 입력했는지에 따라 결과가 달라졌다.
            // D-T25: the list padded to 20 and matched exactly, the detail stripped and re-padded.
            // One identifier, three rules, and the result depended on how the user typed it.
            TransactionSerial serial = TransactionSerial.parse(input).orElseThrow();
            assertThat(serial.canonical()).isEqualTo("26081900142813");
            assertThat(serial.transactionForm()).isEqualTo(OBSERVED_20);
        }

        @Test
        @DisplayName("0 만으로 이루어진 값은 0 으로 정규화된다")
        void allZeroesNormaliseToZero() {
            // 선행 0 제거가 빈 문자열을 만들지 않아야 한다.
            // Stripping leading zeros must not produce an empty string.
            assertThat(TransactionSerial.parse("0000").orElseThrow().canonical()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("렌더링 / rendering")
    class Rendering {

        @Test
        @DisplayName("거래 형태는 20자리로 채운다")
        void transactionFormPadsTo20() {
            assertThat(TransactionSerial.parse("142813").orElseThrow().transactionForm())
                    .hasSize(TransactionSerial.DEFAULT_TRANSACTION_WIDTH)
                    .isEqualTo("00000000000000142813");
        }

        @Test
        @DisplayName("메시지 형태는 10자리로 채운다")
        void messageFormPadsTo10() {
            assertThat(TransactionSerial.parse("142813").orElseThrow().messageForm())
                    .hasSize(TransactionSerial.DEFAULT_MESSAGE_WIDTH)
                    .isEqualTo("0000142813");
        }

        @Test
        @DisplayName("설정 폭을 넘는 값은 잘라내지 않는다 — D-T9")
        void overWidthIsNotTruncated() {
            // ⚠ 이것이 D-T9 의 핵심이다. 레거시는 여기서 잘라냈다:
            //     stripStart("00000026081900142813","0") → "26081900142813" (14자리)
            //     lpad("26081900142813", 10, '0')        → "2608190014"     (10자리, 절단)
            // 그 값은 다른 거래의 메시지에 일치할 수 있다. 잘라내지 않으면 최악의 경우
            // 0건이 나오고, 0건은 눈에 보인다 — 잘못된 행은 보이지 않는다.
            //
            // This is the heart of D-T9. The legacy truncated here, and the truncated value can match
            // another transaction's messages. Not truncating yields zero rows at worst, and zero rows
            // are visible — a wrong row is not.
            TransactionSerial serial = TransactionSerial.parse(OBSERVED_20).orElseThrow();

            assertThat(serial.messageForm())
                    .as("20자리 거래번호가 10자리로 절단되어서는 안 된다 / "
                            + "a 20-character serial must not be cut to ten")
                    .isEqualTo("26081900142813")
                    .hasSizeGreaterThan(TransactionSerial.DEFAULT_MESSAGE_WIDTH);
        }

        @ParameterizedTest(name = "{0}자리 / {0} characters")
        @ValueSource(ints = {10, 14, 20})
        @DisplayName("10·14·20자리 모두 손실 없이 왕복한다 — TC-T002-10")
        void roundTripsWithoutLossAtEveryObservedLength(int length) {
            // TEST-PLAN-TALK TC-T002-10 의 속성 테스트. 레거시가 서로 다르게 다룬 세 길이다.
            // The property test from TC-T002-10, over the three lengths the legacy handled differently.
            String digits = "9".repeat(length);
            TransactionSerial serial = TransactionSerial.parse(digits).orElseThrow();

            assertThat(serial.canonical()).isEqualTo(digits);
            assertThat(TransactionSerial.parse(serial.transactionForm()).orElseThrow().canonical())
                    .as("거래 형태를 다시 해석해도 같은 값이어야 한다 / "
                            + "re-parsing the transaction form must yield the same value")
                    .isEqualTo(digits);
            assertThat(TransactionSerial.parse(serial.messageForm()).orElseThrow().canonical())
                    .as("메시지 형태를 다시 해석해도 같은 값이어야 한다 / "
                            + "re-parsing the message form must yield the same value")
                    .isEqualTo(digits);
        }
    }

    @Nested
    @DisplayName("설정 폭 / configured widths")
    class ConfiguredWidths {

        @Test
        @DisplayName("폭은 설정으로 바뀐다 — T1-01b 가 실측으로 확정한다")
        void widthsAreConfigurable() {
            // RISK-T13: 이 환경에서는 운영급 데이터를 볼 수 없으므로 폭이 기본값이다.
            // T1-01b 가 DBA 를 통해 실측하면 이 값만 바뀌고 다른 것은 바뀌지 않는다.
            // RISK-T13: production-like data is unreachable here, so the widths are defaults. When
            // T1-01b measures them via a DBA only these numbers change; nothing else does.
            Optional<TransactionSerial> serial = TransactionSerial.parse("142813", 12, 8);
            assertThat(serial.orElseThrow().transactionForm()).isEqualTo("000000142813");
            assertThat(serial.orElseThrow().messageForm()).isEqualTo("00142813");
        }
    }

    @Nested
    @DisplayName("동치성 / equality")
    class Equality {

        @Test
        @DisplayName("같은 숫자열은 같은 값이다")
        void sameDigitsAreEqual() {
            assertThat(TransactionSerial.parse("00142813").orElseThrow())
                    .isEqualTo(TransactionSerial.parse("142813").orElseThrow())
                    .hasSameHashCodeAs(TransactionSerial.parse("142813").orElseThrow());
        }
    }
}
