# 종합 판정 — Sprint R1 (이용기관 보고서) + T1/T2 (톡전송 내역)

> **Skill**: 05 품질/코드 리뷰 · **Date**: 2026-08-20 · **대상 커밋**: `bdb6b1d`
> **범위**: 머지 `f60ac13` 이 가져온 R1/T1/T2 산출물 — 프로덕션 73 파일 · 테스트 21 클래스
> **범위 밖**: A1(alimtalk) — 이미 리뷰됨, 그리고 스프린트가 아직 닫히지 않음(PARTIAL)

---

## 0. 판정: **G3 진입 보류 (HOLD)**

**APPROVE 아님. REJECT 도 아님.**

- **REJECT 가 아닌 이유**: Lead(`security-auditor`)가 CVSS ≥ 7.0 결함을 **0 건** 판정했다.
  스킬 §0 의 자동 REJECT 조건(Lead 가 CRITICAL/HIGH 발견)은 성립하지 않는다.
- **APPROVE 가 아닌 이유**: DoD 6 항 중 **3 항 미충족**, 그리고 **빌드가 깨져 있다**.

차단 사유는 대체로 **코드 품질이 아니라 증거의 부재**다. 이 구분이 중요하다 —
아래 5건 중 실제로 잘못된 동작은 V-1 하나뿐이고, 나머지는 "맞는지 알 수 없다" 이다.

---

## 1. DoD 대조

| # | DoD 항목 | 결과 | 근거 |
|---|----------|------|------|
| 1 | 교차 검증 1회 이상 (다른 LLM 벤더) | ⏳ **진행 중** | 동일벤더 적대적 검증 **완료**([cross-validation-3-intravendor.md](cross-validation-3-intravendor.md)) — DoD 미충족. 로컬 모델(ollama, 타벤더·무egress) 설치 진행 중 → 완료 시 충족 |
| 2 | CVSS ≥ 7.0 결함 0건 또는 위험수용 결재 | ⚠ **조건부** | 발견 결함 기준 0건. 그러나 **T-R10(CVSS≈9.1)의 게이트 시험 S-R04 가 존재하지 않아** 닫혔다고 말할 근거가 없음 |
| 3 | 공급망 — SBOM 생성 + 금지 OSS 0건 | ✅ **충족** | `target/bom.json` 127 컴포넌트 · 미선언 라이선스 0 · 금지 라이선스 0 (§4) |
| 4 | 7차원 자체 평가 (Skill 5 재평가) ≥ 90 | ❌ **미충족** | **84.1** (Skill 4 자체평가 89.7~93.0 대비 −5.6~−8.9, 차이 <10 → 자기합리화 의심 없음) |
| 5 | 모든 리포트 작성 완료 | ⚠ **5/6** | 교차검증만 결과 부재(사유 기록됨) |
| 6 | PM 결재 G3 통과 | ⏸ **대기** | 이 문서가 결재 입력이다 |

---

## 2. 차단 사유 5건

| ID | 사유 | 성격 | 출처 |
|----|------|------|------|
| **V-1** | `TalkMessageMapper.xml:298` — 상세 목록 ORDER BY 가 `TABLE_TYPE` 누락. 활성+보관 `UNION ALL` 에서 같은 `(REQDATE, MSGKEY)` 짝이 오프셋 페이징 시 행 중복·소실 **가능**. D-T10 재현 | ⚠ **조건부 — 전제 미검증** (§8) | code-review HIGH-04 / xval A |
| **V-2** | 빌드 RED — 대상 매퍼 통합시험 3종이 embedded-postgres 기동 10초 초과로 **전부 ERROR**. `TalkHistoryMapper` / `TalkMessageMapper` SQL 이 이번 실행에서 **DB 대조 0회**. 매퍼↔DB 증거 33건 소실 | 증거 부재 | qa test-report §2 |
| **V-3** | T-R10 (프로그램 최고 심각도, CVSS≈9.1) 게이트 시험 **미작성** | 증거 부재 | qa test-report §12 |
| **V-4** | 커버리지 게이트 미달 — BUNDLE line **67.5%** / branch **64.4%** (기준 80/70). 게이트 자체가 `mvn verify` 미실행으로 **한 번도 강제된 적 없음** | 증거 부재 | qa test-report §4 |
| **V-5** | 교차 검증 미실행 (DoD 1) | 증거 부재 | cross-validation-3 |

### 참고 — 차단 사유가 **아닌** 것

`CsrfIntegrationTest.echoingCookieValueInHeaderPasses` 실패(403, XSRF-TOKEN 쿠키 미발급).
**이 슬라이스의 결함이 아니다.**

- 원인: `SecurityConfig:188` 이 `CsrfCookieFilter` 를 `addFilterAfter(..., CsrfFilter.class)`
  로 등록한다. `CsrfFilter` 는 토큰 없는 요청을 거부할 때 체인을 잇지 않으므로,
  **쿠키를 발급해야 할 바로 그 요청에서 발급 필터가 실행되지 않는다.** CR-02 교착.
- 머지 무관 확인: `SecurityConfig` 는 merge-base == theirs 로, 우리 쪽만 변경 →
  ours 채택이 정당. `CsrfIntegrationTest` 는 세 커밋 모두 동일 blob.
- 회귀 아님 확인: merge-base `0a987dd`(= `@EnableMethodSecurity` 이전) worktree 에서
  **동일하게 403 으로 실패**. 선재 결함이며 D-A37 수정과 무관.
- 조치: 인증 슬라이스 백로그. 단, **빌드는 여전히 RED** 이므로 G3 전에 닫혀야 한다.

---

## 3. 결함 집계

| 출처 | CRITICAL | HIGH | MEDIUM | LOW | 기타 |
|------|---------:|-----:|-------:|----:|------|
| `code-reviewer` (품질) | 0 | **6** | 14 | 11 | FP 9건 명시 |
| `security-auditor` (CVSS) | **0** | **0** | 2 | 7 | 부적합 3건 |
| `trace-mapper` (추적성) | — | — | — | — | 미추적 3 · 상태불일치 5 · 고아코드 16 |

**강제 룰 위반 0건** — 매퍼 `${}` 0 · PII 평문 로그 0 · 시크릿 0 · `System.out` 0 · 금액 부동소수 0.

### Lead 가 코드로 닫을 수 없다고 올린 결재 조건 3건

1. **SEC-RT-11** — 감사 보존기간이 **두 개**다. `V1__auth_session_audit.sql:85` 는 5년,
   `hooks/prod-gate-checklist.md:60` 은 7년. 체크리스트를 현 상태로 결재하면
   **존재하지 않는 통제를 승인**하게 된다.
2. **SEC-RT-09** — PII 마스킹의 유일한 기제가 사이트 정의 DB 함수 `masking()` 이고,
   통합시험은 스텁을 쓰며 "마스킹 형식이 옳다는 것은 단언하지 않는다"고 자백한다.
   애플리케이션 계층 2선 방어 없음.
3. **SEC-RT-07** — `ReportController` 가 운영자 전용이라 FR-AZ-R03/R04(테넌트 자기 기관 조회)에
   부적합. fail-closed 라 노출은 없으나 `PrincipalScope` 의 테넌트 분기가 **운영 도달 불능**.

---

## 4. 통과한 것 (기록)

감사에서 **명시적으로 PASS** 로 확인된 항목 — 다음 슬라이스가 회귀시키지 않아야 한다.

| 항목 | 결과 |
|------|------|
| `CONST-SEC-T01` 금지 8컬럼 | 매퍼 4종 프로젝션 전수 축출 — **등장 0건**, `SELECT *` 0건 |
| SQL 인젝션 | `${}` 전수 **0건** |
| 인가/테넌시 3속성 | 빈 IS_CD 는 예외 거부(전체 아님) · 요청 IS_CD 는 무시-후-기록(열거 오라클 없음) · `allInstitutions=true` 는 `operator()` 블록 안에서만 생성 → **CONFLICT-R01 이 문법적으로 강제됨** |
| export 마스킹 우회 | 없음 — 목록과 export 가 동일 `findPage` 문장 사용, `TalkExportParityTest` 가 전 필터 조합 단언 |
| Content-Disposition CRLF 주입 | PASS — 검증된 `LocalDate` 재직렬화 + RFC 5987, 와이어 수준 시험 5종 |
| 머지 병합 손실 | **0건** — 추적표 합집합 병합 무결(trace-mapper 독립 확인) |
| 공급망 | SBOM 127 컴포넌트 · 미선언 0 · 금지 0. dual-license 2건(`jakarta.annotation-api` classpath exception, `jna` test-scope 한정)은 허용측 선택 명문화 필요 |

---

## 5. 보안 Hook 3단계 (step [E])

| 단계 | 상태 | 비고 |
|------|------|------|
| **L1** git pre-commit | ❌ **미설치** | `hooks/pre-commit-gitleaks.sh` 와 `gitleaks` 바이너리는 존재하나 `.git/hooks/pre-commit` **부재**. 로컬 시크릿 게이트가 한 번도 동작한 적 없음 |
| L1 스캔 실측 | ⚠ | 수동 실행 결과 **7건 전부 오탐**(TOTP 표준 시험벡터 `MFRGG…`, `new byte[32]` 영키). `hooks/gitleaks.toml` 이 `useDefault=true` 뿐이라 **지금 설치하면 모든 커밋이 막힌다** → allowlist 선행 필요 (`--no-verify` 우회는 스킬이 금지) |
| **L2** CI | ✅ | `.github/workflows/ci.yml` — L1 secret scan + L2 static rules(MD5·자격증명 로깅·클라이언트 IP 헤더·PII 리터럴·발신키 리터럴·raw PII 접근자 봉쇄) |
| **L3** prod-gate | ✅ 존재 | 단 SEC-RT-11 보존기간 불일치 해소 전 결재 불가 |

---

## 6. 권고 — G3 전 필수 (예상 1.5~2일)

| # | 조치 | 닫는 사유 |
|---|------|-----------|
| 1 | `TalkMessageMapper.xml` ORDER BY 에 `TABLE_TYPE` 추가 + 중복·소실 재현 시험 | V-1 |
| 2 | embedded-postgres 기동 타임아웃 10s → 90s 상향(이 머신 initdb 실측 73s) 후 매퍼 통합시험 3종 재실행 | V-2 |
| 3 | T-R10 게이트 시험 S-R04 작성 | V-3 |
| 4 | `CsrfCookieFilter` 를 `CsrfFilter` **앞**으로 이동 + 회귀시험 | 빌드 RED |
| 5 | 감사 보존기간 5년/7년 확정 후 양쪽 문서 일치 | SEC-RT-11 |
| 6 | `hooks/gitleaks.toml` 시험 픽스처 allowlist 추가 → L1 훅 설치 | L1 |
| 7 | 추적표 즉시조치 A-1~A-5 | 추적성 |

**교차 검증(DoD 1)** 은 위와 별개로 PM 결정이 필요하다 — cross-validation-3 §3 의 A~D 중 택일.

---

## 7. PM 결재란

| 항목 | 결정 | 서명 | 일자 |
|------|------|------|------|
| G3 릴리즈 게이트 | ☐ APPROVE ☐ CONDITIONAL ☐ **HOLD(권고)** ☐ REJECT | | |
| 교차 검증 처리 (A/B/C/D) | | | |
| DoD 4 (7차원 84.1 < 90) 위험 수용 여부 | | | |
| 감사 보존기간 확정 (5년 / 7년) | | | |

---

## 8. 교차 검증 반영 (2026-08-20, 동일벤더 적대적 검증)

[cross-validation-3-intravendor.md](cross-validation-3-intravendor.md) — **DoD 1 은 충족하지 않는다**(동일 벤더).
그러나 실질 위험 판정에는 다음 변화가 있다.

| 주장 | 원 판정 | 교차검증 판정 | 효과 |
|------|---------|---------------|------|
| A — ORDER BY 전순서 아님 | code-review **HIGH-04** / "현재 운영에서 잘못된 데이터를 반환하는 유일한 결함" | **PARTIALLY CONFIRMED** | 메커니즘은 유효하나 **성립 조건이 저장소 안에서 검증·반증 불가** |
| B — 도메인 예외 5종 전부 500 | code-review HIGH-01 | **CONFIRMED** | 유지 |
| C — 상세 경로 인가 불일치 | code-review **HIGH-06** | **CONFIRMED (단, 축소)** | `findDetail` 에 SERIALNUM 술어 없음은 사실이나 **기관 술어는 존재** → 교차기관 노출 아님. 원 감사 SEC-RT-01 의 **CVSS 3.8 이 정확**하고 code-review 의 HIGH 등급은 과대 |
| D — CVSS ≥ 7.0 결함 0건 | security audit | **반박 실패 = 주장 유지** | 기관간 열람·감사회피·PII 대량반출 세 방향 반박 시도 모두 실패. **0건 주장이 독립 검증을 견딤** |
| E — export 마스킹 우회 없음 | security audit | **CONFIRMED** | export 9열에 PHONE/CALLBACK 부재 실측 |
| F — 엑셀 수식 주입 5.8 | security audit SEC-RT-03 | **CONFIRMED** | CVSS 재계산 일치 |

**신규 결함 0건. CVSS ≥ 7.0 신규 0건.**
추가로 `BizTalkApiRegistry` 부팅 시 검증 부재 가설은 **반박됨** — 생성자가 부팅 시점 fail-fast 로
중복·오타 설정을 거부한다.

### 8-1. V-1 의 지위 변경 — 이번 교차검증의 핵심 산출

원 코드리뷰는 HIGH-04 를 **"현재 운영에서 잘못된 데이터를 반환하는 유일한 결함"** 으로 단정했다.
교차검증은 그 단정을 **지지하지 않는다**. 결함이 성립하려면 다음이 참이어야 한다:

> 같은 `(REQDATE, MSGKEY)` 짝이 QUE 와 LOG 양쪽에 **동시에** 존재할 수 있다

이 명제는 **이 저장소의 코드·스키마·주석만으로는 참도 거짓도 증명되지 않는다.**
아카이빙이 행을 **이동**시키면 짝은 유일하고 정렬은 사실상 전순서이며 **HIGH-04 는 소멸**한다.
아카이빙이 행을 **복사**하면 결함은 실재한다.

**따라서 V-1 은 "확인된 오동작" 이 아니라 "미검증 전제에 걸린 조건부 결함" 이다.**

| 조치 | 내용 |
|------|------|
| 즉시 | 운영 DB 에서 QUE 와 LOG 를 MSGKEY 로 INNER JOIN 한 건수 1회 확인 — 0 이면 REFUTED, 양수면 CONFIRMED |
| 전제와 무관하게 | `ORDER BY` 에 `TABLE_TYPE` 추가는 **비용이 사실상 0 이고 결과가 결정적이 된다** — 확인 여부와 관계없이 적용 권고 |

> **판독 주의**: 이 교차검증은 동일 벤더 모델이 수행했다. 같은 종류의 맹점을 공유할 수 있으므로,
> D("CVSS ≥ 7.0 0건")가 견뎠다는 사실을 "안전이 입증됐다"로 읽어서는 안 된다.
> 로컬 모델(타벤더) 검증이 끝나면 이 절을 갱신한다.

---

*입력: [code-review-sprint-R1-T1-T2.md](code-review-sprint-R1-T1-T2.md) · [audit-3-report-talk.md](../security/audit-3-report-talk.md) · [test-report-3-report-talk.md](../qa/test-report-3-report-talk.md) · [traceability-R1-T1-T2.md](traceability-R1-T1-T2.md) · [cross-validation-3.md](cross-validation-3.md)*
