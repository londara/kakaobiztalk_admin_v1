# 코드 리뷰 — 카카오 알림톡 슬라이스 (Sprint A1 + A2-01)

> **Date**: 2026-08-19 · **Role**: `code-reviewer` · **Iteration**: A1 #4
> **대상 / scope**: `src/main/java/com/webcash/iris/biztalk/alimtalk/**` (18 files),
> `src/main/frontend/src/features/biztalk/**`, `src/main/frontend/src/api/alimTalkApi.ts`
> **판정 / verdict**: ✅ **APPROVE** — 조건부 (CR-A02 이월) / conditional, CR-A02 carried

---

## 0. 이 리뷰의 한계 — 먼저 읽을 것 / the limitation of this review, read first

이것은 **자기 리뷰**다. 스킬 4 §0 은 `code-reviewer` 를 별도 에이전트로 두지만, 이 세션에서는
에이전트 기동이 허용되지 않아 구현자와 리뷰어가 같다. 자기 리뷰는 **약한 증거**다 — 구현할 때
보지 못한 것을 리뷰할 때 보지 못할 가능성이 그대로 남기 때문이다.

This is a **self-review**. Skill 4 §0 assigns `code-reviewer` to a separate agent; in this session agent
dispatch is not permitted, so implementer and reviewer are the same. Self-review is **weak evidence**: what
was invisible while writing tends to stay invisible while reading.

그래서 이 리뷰는 판단보다 **기계적 검사**에 기댔다. 아래 네 건 중 세 건은 코드를 읽어서가 아니라
불변식을 **실행해서** 나왔다. 진짜 교차 검증은 Skill 5(다른 벤더)이고, **아직 실행되지 않았다**.

The review therefore leans on **mechanical checks** rather than judgement: three of the four findings came
from *executing* an invariant, not from reading. Genuine cross-validation is Skill 5 (a different vendor)
and **has not run**.

---

## 1. 판정 요약 / findings

| ID | 심각도 | 항목 | 상태 |
|----|--------|------|------|
| **CR-A01** | **MEDIUM** | `TemplateRegistry` 캐시 키가 `String.hashCode()` — 충돌 시 **바뀌기 전 규칙으로 검증**, 그리고 무한 증가 | ✅ **FIXED** |
| **CR-A02** | LOW | `InMemoryDailySequence.counters` 에 축출이 없다 | ⏭️ **ACCEPTED / 이월** |
| **CR-A03** | MEDIUM | 생성된 payload 는 마스킹되어 발송 불가인데 화면이 그 사실을 말하지 않았다 | ✅ **FIXED** |
| **CR-A04** | INFO | CI 정적 규칙이 이 슬라이스의 결함 부류를 하나도 인코딩하지 않았다 | ✅ **FIXED** |

---

## 2. CR-A01 — 캐시가 지키기로 한 것을 지키지 못했다 (MEDIUM, 수정됨)

**위치**: `domain/TemplateRegistry.java`

이 클래스의 Javadoc 은 스스로 이렇게 적고 있었다:

> 레지스트리의 본문이 바뀌면 캐시가 **낡은 규칙으로 검증**하게 되고, 그것은 검증이 없는 것보다 나쁘다.

그 위험을 막는 장치가 캐시 키의 `body.hashCode()` 였다. 두 가지가 틀렸다.

**① 32비트 해시는 본문 변경을 보장하지 못한다.** `String.hashCode()` 는 비암호학적 32비트 값이고
충돌 문자열은 만들기 쉽다 — `"Aa"` 와 `"BB"` 가 같은 값(2112)을 갖고, 반복 곱셈이므로 같은 접미사를
붙여도 계속 같다. 본문이 그렇게 바뀌면 키가 동일하여 캐시는 **바뀌기 전 매처**를 돌려준다. 문서가
"검증이 없는 것보다 나쁘다"고 부른 바로 그 상태다.

**② 무한 증가.** 본문이 바뀔 때마다 새 키가 생기고 옛 항목은 사라지지 않는다.

**왜 이것이 실제 위험인가**: 같은 파일이 `KKB_MSG_TMPL` 에 대해 *"이 저장소의 어떤 코드도 이 표에
쓰지 않는다(AMB-A07)"* 고 적고 있다. 누가 언제 본문을 바꾸는지 모르는 표를 권위로 삼아 놓고, 변경
감지를 32비트 해시의 근사에 맡긴 것이다.

**Why it matters**: the same file records that *nothing in this repository writes* `KKB_MSG_TMPL`
(AMB-A07). Having made an externally-written table authoritative, change detection was then delegated to a
32-bit hash's approximation.

**수정 / fix**: 캐시 키에서 해시를 빼고, 항목이 **컴파일에 쓴 본문 자체**를 들고 있게 하여 조회마다
정확히 대조한다. 템플릿 하나에 항목 하나가 되어 증가 문제도 함께 사라진다.

**회귀 방지 / regression tests** — 둘 다 옛 구현에서 **실패함을 확인**했다 (185건 중 2건 실패):

| 테스트 | 무엇을 고정하나 |
|--------|----------------|
| `collidingBodyIsRecompiled` | 해시가 충돌하는 본문으로 바뀌어도 새 규칙으로 검증한다 |
| `cacheDoesNotGrowWithBodyChanges` | 본문을 50회 바꿔도 항목은 1개 |

> 새 테스트가 옛 코드에서 통과한다면 그 테스트는 아무것도 증명하지 않는다. 임시 사본에 옛 구현을
> 되살려 두 건이 **실패**하는 것을 확인한 뒤에 이 항목을 FIXED 로 적었다.
> A new test that also passes against the old code proves nothing. The old implementation was restored in a
> scratch copy and both tests were confirmed to **fail** before this item was marked FIXED.

## 3. CR-A02 — 메모리 순번에 축출이 없다 (LOW, 이월)

**위치**: `config/AlimTalkConfig.InMemoryDailySequence`

`ConcurrentHashMap<기관|일자, AtomicLong>` 이 비워지지 않는다. 기관 100개면 하루 100개씩 쌓인다.

**이월하는 이유**: 이 빈은 `iris.alimtalk.environment=A`(운영)에서 **기동을 거부한다**. 즉 운영에
도달할 수 없고, A2-02 의 DB 시퀀스가 이 클래스를 통째로 대체한다. 지금 축출 로직을 넣는 것은 곧
삭제될 코드에 복잡도를 더하는 일이다.

**Why carried**: the bean *refuses to start* when `environment=A`, so it cannot reach production, and
A2-02's database sequence replaces the class outright. Adding eviction now adds complexity to code with a
scheduled deletion date.

**조건 / condition**: A2-02 가 이 클래스를 제거하지 **않고** 넘어가면 이 항목은 MEDIUM 으로 승격된다.

## 4. CR-A03 — 화면이 출력의 성질을 말하지 않았다 (MEDIUM, 수정됨)

**위치**: `features/biztalk/AlimTalkPage.tsx` — 출력 textarea

생성된 payload 는 수신번호를 `010****2222` 로, 발신프로필키를 `ProfileKey[REDACTED]` 로 직렬화한다.
의도된 동작이고 백엔드 테스트 `previewCarriesNeitherCredentialNorClearNumber` 가 고정하고 있다.

문제는 **화면이 그것을 말하지 않았다**는 점이다. 운영자는 `JSON 생성` → `복사` 를 누르고 그 JSON 을
발송에 쓰려 시도한다. 벤더에서 실패하고, 화면이 아무 말도 하지 않았으므로 원인을 알 수 없다.

이것은 레거시 D-A20 과 **같은 모양의 결함**이다 — 레거시의 `JSON 생성` 도 무엇을 만들었는지 끝내
말하지 않았고, 그 침묵이 복사 실패가 몇 년간 눈에 띄지 않은 이유였다. 침묵을 물려받은 것이다.

This is the **same shape** as legacy D-A20: the legacy's `JSON 생성` never said what it produced either, and
that silence is why its failing copy went unnoticed for years. The silence was inherited.

**수정**: 출력창 아래에 안내를 넣었다 — 마스킹되며 계약 적합성 확인용 표본이고 그대로 발송할 수
없다. 생성 전에는 표시하지 않는다(빈 상자에 대한 설명은 소음이다). 테스트
`출력이 표본임을 화면이 알린다` 가 양쪽을 고정한다.

## 5. CR-A04 — CI 가 이 슬라이스의 결함을 하나도 모르고 있었다 (INFO, 수정됨)

**위치**: `.github/workflows/ci.yml`

`L2 static rules` job 의 설계 원칙은 그 안에 이렇게 적혀 있다: *"일반적인 린트가 아니다. 레거시에서
실제로 발생한 결함을 직접 겨냥한다."* 그런데 규칙 6개가 전부 **login 슬라이스**의 결함이었다.
알림톡의 D-A24(소스에 하드코딩된 발신프로필키)·D-A30(발송마다 키와 수신번호를 로그에 기록)에
대응하는 규칙은 없었다.

**추가한 규칙 3개**:

| 규칙 | 겨냥하는 결함 |
|------|--------------|
| `Reject sender profile key literals` | D-A24 — 해시 대조는 *유출된 그 키*만 막는다. 다른 키를 소스에 붙여넣는 것은 못 막는다 |
| `Reject unmasked credential or PII in log statements` | D-A30 — 원시 접근자는 `exposeForVendorCall()` 하나뿐이므로 그것을 겨냥 |
| `Confine the raw PII accessor to the vendor boundary` | FR-AZ-A05 — 마스킹이 기본값일 때만 유효하다. 선택 사항인 통제는 언젠가 선택되지 않는다 |

각 규칙을 **양방향으로 검증**했다: 현재 코드에서 침묵하고, 합성 결함 파일에서 발화한다. 발화하지
않는 규칙은 규칙이 아니라 장식이다.

Each rule was checked **in both directions**: silent on the current tree, firing on a synthetic defect file.
A rule that never fires is decoration, not a rule.

---

## 6. 통과 항목 / what passed

| 검사 | 결과 |
|------|------|
| `// source:` / `// req:` provenance | 전 파일 (0 missing) |
| 한국어+영문 Javadoc — public 클래스·메서드 | 준수 |
| 기존 CI 정적 규칙 6종 (MD5·SHA-256·자격증명 로그·IP 헤더·외부 QR·전화번호 리터럴) | 전부 pass |
| 시크릿 — 평문 키 리터럴 | 0건 (SHA-256 참조 1건은 의도됨) |
| BigDecimal (금액) | **해당 없음** — 이 슬라이스는 금액을 계산하지 않는다. `#{금액}` 은 템플릿 변수(문자열)이고 산술을 하지 않는다 |
| 디렉터리 격리 | 위반 0 |
| 테넌트 범위 | `TenantContext.require()` 단일 지점, 닫힘으로 실패 |
| 동시성 | `AtomicLong`·`ConcurrentHashMap`, 가변 공유 상태 없음 |
| 순번 고갈 | 조용히 순환하지 않고 예외 (RISK-A04) |

---

## 7. 미검증으로 남는 것 / what remains unverified

이 리뷰가 **말할 수 없는** 것들이다. 통과 목록에 섞어 두면 검증된 것처럼 읽히므로 분리한다.

| 항목 | 왜 |
|------|-----|
| Spring 필터 체인 — 인증·CSRF·실제 403 | MockMvc standalone 은 필터를 태우지 않는다. Boot 컨텍스트가 필요하다 (A1-19 부분 완료) |
| MyBatis 매핑·실제 SQL | PostgreSQL 도달 불가, Docker 금지 (RISK-A12) |
| CI `build`·`dependencies` job | Maven 없음 — SBOM·의존성 취약점 **미실행** |
| 벤더 계약의 실제 동작 | spike A1-01…A1-04 미완 |
| 교차 검증 (다른 벤더 LLM) | Skill 5 미실행 |
