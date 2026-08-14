# QA 테스트 결과 리포트 #1

> **Skill**: 05 step [A] · **Date**: 2026-08-14 · **Agent**: `qa-engineer`
> **대상**: Sprint L1–L8 (login) + M1–M4 (문자내역) 전체 산출물
> **판정**: **부분 검증** — 실행된 것과 실행되지 않은 것의 비율이 이 리포트의 핵심이다

---

## 1. 실행된 검증 / What actually ran

| 종류 | 도구 | 건수 | 결과 |
|------|------|------|------|
| 프론트엔드 컴포넌트 | vitest + testing-library | **56** | ✅ 56 pass |
| 프론트엔드 타입 | `tsc --noEmit` (strict) | — | ✅ 0 error |
| 프론트엔드 빌드 | vite | — | ✅ 187.22 kB |
| 백엔드 도메인 (JDK-only) | `qa/verify-without-maven.sh` | **196** | ✅ 196 pass |
| 통계적 검증 | TempPwdDriver 20만 표본 | 1 | ✅ (SR-04 발견) |
| 정적 규칙 | CI codegrep 6종 | 6 | ✅ 위반 0 |
| provenance | grep | 58 파일 | ✅ 58/58 |

## 2. 실행되지 않은 검증 / What did not run

| 종류 | 계획 | 상태 | 원인 |
|------|------|------|------|
| 단위 테스트 (JUnit) | `mvn verify` | ❌ | **Maven 미설치** |
| 커버리지 (JaCoCo 80/70) | `mvn verify` | ❌ | 동일 |
| ArchUnit (BigDecimal 등) | `mvn verify` | ❌ | 동일 |
| 통합 테스트 (MyBatis 매핑) | Testcontainers | ❌ | **PostgreSQL 없음** |
| E2E TOP 5 | Playwright | ❌ | 기동 불가 |
| 부하 테스트 | k6 × 2 스크립트 | ❌ | 기동 불가 |
| 쿼리 계획 | `EXPLAIN` 스크립트 | ❌ | DB 없음 |
| Parity | — | ❌ | [parity-report-1.md](../parity/parity-report-1.md) 참조 |
| 접근성 자동 스캔 | axe-core | ✅ 부분 | 7건 실행 (로그인 화면만) |

> **커버리지 수치를 보고할 수 없다.** JaCoCo 가 실행되지 않았으므로 TEST-PLAN 의
> 라인 80% / 브랜치 70% 기준에 대한 판정은 **불가**다. "196 + 56 건 통과"는 커버리지가
> 아니라 실행된 어서션 수다 — 혼동하면 보증 수준을 과대평가한다.

## 3. 이번 검증이 놓친 것 / What this test suite failed to catch

**QA-FIND-01 (SEV-1)**: 프론트엔드 56건 전부가 `fetch` 를 stub 한다. 따라서 **실제 HTTP
경로는 한 번도 실행되지 않았다.** 그 결과 CSRF 토큰 누락(→ [code-review](../reviews/code-review-sprint-M4.md)
CR-01)이라는 **전면 기능 정지 결함**을 56건 중 어느 것도 감지하지 못했다.

```
stubGlobal('fetch') 사용 파일: 5 / 5   (accessibility 12, LoginPage 6, OtpRegister 3,
                                       PasswordChange 3, MessageHistory 6)
```

이것은 테스트가 잘못 작성된 것이 아니다 — 컴포넌트 테스트는 원래 네트워크를 stub 한다.
**문제는 그 위 계층(통합/E2E)이 전혀 없다는 것**이며, 그 공백이 TEST-PLAN 에 계획으로만
존재하고 실행되지 않았다는 사실이 이 결함을 통과시켰다.

*The 56 component tests all stub `fetch`, so no real HTTP path ever executed. The missing layer
is integration/E2E — planned but never run — and that gap is what let a total functional break
through.*

## 4. 권고 / Recommendations

| # | 권고 | 우선순위 |
|---|------|---------|
| 1 | **MockMvc 기반 통합 테스트 1건**만이라도 확보 — CSRF·필터 순서·직렬화를 실제로 통과시킨다. Maven 없이는 불가하므로 환경이 선행 | **Must** |
| 2 | 컴포넌트 테스트에 "실제 헤더 검증" 케이스 추가 (stub 이어도 `init.headers` 를 단정) | Must |
| 3 | 커버리지 미측정 상태를 진행률과 분리해 보고 | Should |
