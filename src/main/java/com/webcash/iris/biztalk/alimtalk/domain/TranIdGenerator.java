package com.webcash.iris.biztalk.alimtalk.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 거래고유번호 생성 — 10자 안에서 충돌하지 않는 값. / Generates a collision-free {@code tran_id} within 10 characters.
 *
 * <h2>레거시는 <b>구조적으로</b> 충돌했다 / the legacy collided <b>by construction</b></h2>
 * <pre>
 *   단건 / single : "33" + hh24miss          → 초 단위, 날짜 없음 / second precision, no date
 *   대량 / batch  : hh24miss + apiNumber++   → 형식이 다름 / a different format entirely
 * </pre>
 * <p>같은 초에 두 번 발송하면 같은 값이 나온다. 그 값은 {@code KKB_ADMIN_SEND_HIS} 의
 * <b>주키 절반</b>({@code IS_CD} + {@code SERIALNUM}, CONST-DATA-A04)이고 벤더의 상관관계
 * 키이기도 하다. 트랜잭션이 없었으므로(D-A27) 삽입 실패가 조용히 지나갔고, 그래서 이 결함은
 * 발견되지 않았다 — 이력이 비어 있어도 아무도 몰랐다(D-A25).</p>
 * <p>Two sends in the same second produce the same value. That value is <b>half the primary key</b> of
 * {@code KKB_ADMIN_SEND_HIS} (CONST-DATA-A04) and the vendor's correlation handle. With no transaction
 * (D-A27) the failed insert passed unnoticed, which is why the defect was never found — a missing
 * history row told nobody (D-A25).</p>
 *
 * <h2>10자에 무엇을 담을 수 있는가 / what fits in ten characters</h2>
 * <p>계약은 {@code length="10"} 을 선언한다. UUID 는 들어가지 않고, 날짜를 포함한 사람이 읽을
 * 수 있는 타임스탬프에 충분한 난수를 덧붙일 여유도 없다.</p>
 * <pre>
 *   A 2 6 0 8 1 8 0 0 1     ← 정확히 10자 / exactly ten characters
 *   │ └──┬──┘ └─┬─┘
 *   │    │      └──────── 기관·일자별 순번, base-36 3자 (46,656/일)
 *   │    │                per-institution daily sequence, base-36, 3 chars
 *   │    └─────────────── yyMMdd, 6자
 *   └──────────────────── 환경 구분자 / environment discriminator (A=prod, T=staging)
 * </pre>
 * <p><b>설계 정정(Sprint A1).</b> ADR-ATK-026 은 순번에 4자를 배정해 하루 1,679,616건을
 * 주장했다. 1 + 6 + 4 = <b>11</b> 이므로 계약의 10자를 한 자 넘긴다 — ADR 의 구성도 자체가
 * 11자였고, 산술이 틀렸다. {@code TranIdGeneratorTest} 가 이 오류를 잡았다. 순번을 3자로
 * 줄여 상한은 기관·일자별 <b>46,656</b>건이 되었다.</p>
 * <p><b>Design correction (Sprint A1).</b> ADR-ATK-026 allotted four characters to the sequence and
 * claimed 1,679,616 per day. But 1 + 6 + 4 = <b>11</b>, one over the contract's ten — the ADR's own
 * diagram was eleven characters wide and the arithmetic was simply wrong.
 * {@code TranIdGeneratorTest} caught it. The sequence is three characters and the ceiling is
 * <b>46,656</b> per institution per day.</p>
 * <p>이 상한이 충분한 이유: {@code tran_id} 는 <b>수신자당</b>이 아니라 <b>발송 요청당</b>
 * 하나다. 수신자 1000명에게 보내는 단건 발송도 {@code tran_id} 하나를 쓴다. 기관 하나가
 * 하루 46,656회 발송 요청을 낸다는 것은 현실적 물량이 아니다(RISK-A04).</p>
 * <p>Why that suffices: a {@code tran_id} is one per <b>send request</b>, not per <b>recipient</b> — a
 * single send to 1000 recipients consumes one. Forty-six thousand send requests per institution per
 * day is not a plausible volume (RISK-A04).</p>
 * <p>난수 접미사가 아니라 <b>순번</b>을 쓰는 이유: 난수는 충돌을 <i>드물게</i> 만들 뿐이고,
 * 주키에서 "드물다"는 재현되지 않는 운영 장애를 뜻한다. 순번은 충돌을 <b>불가능</b>하게 만든다.
 * 덤으로 운영자가 이력 행을 보고 읽을 수 있다.</p>
 * <p>A <b>sequence</b> rather than a random suffix: randomness makes collision <i>unlikely</i>, and on a
 * primary key "unlikely" means a production failure nobody can reproduce. A sequence makes it
 * <b>impossible</b>, and is legible in a history row as a bonus.</p>
 *
 * <p>환경 구분자가 있는 이유는 단순하다 — 스테이징 발송이 운영 형식의 {@code tran_id} 로 실제
 * 벤더에 도달하는 것은 나중에 돌아보면 명백한 실수다.</p>
 * <p>The environment discriminator exists because a staging send reaching the real vendor with a
 * production-shaped {@code tran_id} is the kind of mistake that is obvious in hindsight.</p>
 *
 * <p><b>Sprint A1 범위</b>: 순번 공급원은 {@link SequenceSource} 로 추상화되어 있고, DB 시퀀스
 * 구현은 A2-02(DDL)에 속한다. 이 클래스 자체는 DB 없이 완전히 검증된다.</p>
 * <p><b>Sprint A1 scope</b>: the sequence source is abstracted behind {@link SequenceSource}; the DB
 * sequence implementation belongs to A2-02 (DDL). This class is fully verifiable without a database.</p>
 *
 * // source: biztalk_admin_50_s001_act.jsp — "33" + hh24miss; hh24miss + apiNumber++
 * // req: FR-ATS-008, FR-ATS-010, FR-ATC-005, CONST-DATA-A04
 */
public final class TranIdGenerator {

    /** 계약이 정한 전체 길이. / The total length the contract fixes. */
    static final int LENGTH = AlimTalkLimits.CONTRACT_TRAN_ID;

    /** 순번에 배정된 자릿수. / Characters allotted to the sequence. */
    static final int SEQUENCE_CHARS = 3;

    /** 순번 상한 — base-36 3자. / Sequence ceiling: base-36 in three characters. */
    static final int SEQUENCE_CEILING = 36 * 36 * 36;

    /** 형식 검증용 패턴. / Pattern for format validation. */
    private static final Pattern VALID = Pattern.compile("[A-Z][0-9]{6}[0-9A-Z]{" + SEQUENCE_CHARS + "}");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final char environment;
    private final SequenceSource sequences;
    private final Clock clock;

    /**
     * 생성기를 만든다. / Creates the generator.
     *
     * @param environment 환경 구분자 / the environment discriminator, e.g. {@code 'A'} or {@code 'T'}
     * @param sequences   기관·일자별 순번 공급원 / the per-institution, per-date sequence source
     * @param clock       일자 판정용 시계 / the clock used to decide the date
     *
     * // req: FR-ATS-008
     */
    public TranIdGenerator(char environment, SequenceSource sequences, Clock clock) {
        if (environment < 'A' || environment > 'Z') {
            throw new IllegalArgumentException("environment discriminator must be an uppercase letter");
        }
        this.environment = environment;
        this.sequences = sequences;
        this.clock = clock;
    }

    /**
     * 이용기관의 다음 거래고유번호를 발급한다. / Issues the next {@code tran_id} for an institution.
     *
     * <p>단건과 다건이 <b>같은</b> 방식을 쓴다(FR-ATS-010). 레거시가 분기마다 다른 형식을 쓴
     * 것은 두 분기가 서로 충돌할 수 있다는 뜻이었다.</p>
     * <p>Single and batch sends use the <b>same</b> scheme (FR-ATS-010). The legacy's per-branch formats
     * meant the two branches could collide with each other.</p>
     *
     * @param institutionCode 이용기관코드 / the institution code
     * @return 10자 거래고유번호 / a ten-character {@code tran_id}
     * @throws IllegalStateException 당일 순번이 고갈되면 / when the day's sequence is exhausted
     *
     * // req: FR-ATS-008, FR-ATS-010
     */
    public String next(String institutionCode) {
        LocalDate today = LocalDate.now(clock);
        long sequence = sequences.next(institutionCode, today);
        if (sequence >= SEQUENCE_CEILING) {
            // 조용히 넘기지 않는다 — 고갈은 발송 실패이며 원인이 명확해야 한다(RISK-A04).
            // Not silently wrapped: exhaustion fails sends and the cause must be obvious (RISK-A04).
            throw new IllegalStateException(
                    "daily tran_id sequence exhausted for institution " + institutionCode
                            + " on " + today + "; ceiling is " + SEQUENCE_CEILING);
        }
        String encoded = Long.toString(sequence, 36).toUpperCase();
        return environment
                + today.format(DATE)
                + "0".repeat(SEQUENCE_CHARS - encoded.length())
                + encoded;
    }

    /**
     * 운영자가 직접 입력한 값의 형식을 검증한다. / Validates an operator-supplied value.
     *
     * <p>PM 은 AMB-A02b 에서 서버 생성을 <b>거절</b>했으므로 이 필드는 편집 가능하게 남는다
     * (RESIDUAL-A02). 편집이 안전한 이유는 뒤에 DB 제약과 중복 검사가 있기 때문이다 — 최악의
     * 결과는 거절이며, 이중 발송이 아니다.</p>
     * <p>PM <b>declined</b> server-side generation (AMB-A02b), so the field stays editable
     * (RESIDUAL-A02). Editing is safe because a database constraint and a dedupe check sit behind it: the
     * worst outcome is a rejection, never a double send.</p>
     *
     * @param candidate 검사할 값 / the value to check
     * @return 형식을 만족하면 {@code true} / {@code true} when well-formed
     *
     * // req: FR-ATS-008, FR-ATC-005, RESIDUAL-A02
     */
    public static boolean isWellFormed(String candidate) {
        return candidate != null
                && candidate.length() == LENGTH
                && VALID.matcher(candidate).matches();
    }

    /**
     * 기관·일자별 순번 공급원. / Supplies a per-institution, per-date sequence.
     *
     * <p>DB 시퀀스로 구현된다(A2-02). 동시성을 <b>제약이 있는 곳</b>에서 처리하려는 의도이며,
     * 애플리케이션 수준 카운터로 두면 화면 50 과의 공존 구간에서 두 작성자가 같은 값을 낼 수
     * 있다 — ADR-SND-018 이 발신번호에서 마주친 것과 같은 문제다.</p>
     * <p>Implemented by a DB sequence (A2-02), so concurrency is handled <b>where the constraint lives</b>.
     * An application-level counter would let two writers produce the same value during the screen-50
     * coexistence window — the problem ADR-SND-018 met on sender numbers.</p>
     *
     * // req: FR-ATS-008
     */
    public interface SequenceSource {

        /**
         * 다음 순번을 돌려준다. / Returns the next sequence value.
         *
         * @param institutionCode 이용기관코드 / the institution code
         * @param date            대상 일자 / the date
         * @return 0 부터 시작하는 순번 / a zero-based sequence value
         */
        long next(String institutionCode, LocalDate date);
    }
}
