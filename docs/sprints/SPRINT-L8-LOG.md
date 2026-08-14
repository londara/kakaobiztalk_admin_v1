# Sprint L8 Log — 품질 항목 완료 및 잔여 4건 분석

> **Sprint**: L8 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L7-LOG.md](SPRINT-L7-LOG.md)
> **Status**: **품질 항목 완료 · 잔여 4건은 코드로 해소 불가**

---

## 1. 지시와 실제 상황 / The instruction and the actual situation

PM 지시: "55/59 (93%) 를 완료하라."

**분석 결과: 잔여 4건은 코드 작성으로 닫히지 않는다.** 각 항목의 실제 차단 원인:

| Requirement | Status | 차단 원인 | 코드로 해소 가능? |
|-------------|--------|----------|------------------|
| NFR-PERF-L01 | SPECIFIED_NOT_RUN | k6 스크립트는 작성됨. **애플리케이션이 기동되지 않는다** — `IRIS_OTP_SECRET_KEY` 미발급 + DDL 미적용 + PostgreSQL 미제공 + Maven 미설치로 빌드 자체 불가 | ❌ |
| CONST-SEC-L01 | BLOCKED | **ADR-LOGIN-011 결정 대기.** 기존 계정의 마이그레이션 방식(로그인 시 상향 vs 전면 초기화)은 "비밀번호 DB 가 과거에 유출된 적이 있는가"라는 <b>사실 확인</b>에 달려 있다 | ❌ |
| CONST-DATA-L01 | PENDING_DBA | DDL 스크립트는 작성됨. **운영 중 레거시와 공유하는 DB 이므로 DBA 검토와 롤백 스크립트 필요** | ❌ |
| CONST-LEGAL-L02 | PENDING_DBA | 감사 로그 5년 보존 — 테이블·인덱스 작성됨. 동일하게 DBA 검토 대기 | ❌ |

**따라서 이 sprint 는 잔여 4건을 닫는 대신, 코드로 닫을 수 있는 나머지 품질 항목을
완료했다.** TEST-PLAN-LOGIN 이 요구하나 아직 미충족이던 항목들이며, 프론트엔드는 npm 으로
<b>실제 실행 검증이 가능</b>하다.

*The four remaining items cannot be closed by writing code — each waits on a human decision,
a DBA review, or an environment that does not exist here. This sprint therefore completed the
outstanding TEST-PLAN quality items, which are verifiable.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L8-01 | `QrCode` — **브라우저 내 QR 생성**, 외부 호출 없음 | **FR-OTP-003/004**, **completes L4 fix** |
| L8-02 | `OtpRegisterPage.test.tsx` — 11건, L4 회귀 포함 | FR-OTP-001…006 |
| L8-03 | `PasswordChangePage.test.tsx` — 11건 | FR-PWD-001/002/003, TM-L018 |
| L8-04 | `accessibility.test.tsx` — **axe-core 자동 검증 7건** | TEST-PLAN-LOGIN §1.3, WCAG 2.1 AA |
| L8-05 | QR 실패 시 키 직접 입력 경로 유지 | FR-OTP-003 |

**신규 파일 4개 · 수정 1개**

## 3. L4 결함 대응이 이번에 완결되었다 / The L4 fix is now complete

레거시 `GoogleOTP.getQRBarcodeURL()` 은 OTP 공유 비밀키를 다음과 같이 처리했다:

```
http://chart.apis.google.com/chart?cht=qr&chl=otpauth://...%3Fsecret%3D<SECRET>
```

**평문 HTTP + 제3자 전송.** 대응은 두 단계였다:

| Sprint | 조치 |
|--------|------|
| L3 | 서버가 URI 문자열만 생성하도록 변경. `getQRBarcodeURL()` 은 어떤 형태로도 이식하지 않음 |
| **L8** | **클라이언트가 브라우저 안에서 QR 이미지를 생성.** 비밀키가 어떤 외부 호스트에도 도달하지 않음 |

**회귀 테스트로 고정했다.** `OtpRegisterPage.test.tsx` 의 L4 회귀 테스트는 등록 과정의
모든 요청 URL 을 검사하여 (a) 전부 상대 경로이고 (b) `chart.apis.google.com` 을 포함하지
않으며 (c) 절대 URL(`http://`/`https://`)이 없음을 확인한다.

`canvas` 에 그리는 선택도 의도적이다 — `<img src="data:...">` 는 페이로드가 DOM 속성에
남아 개발자 도구·확장 프로그램·스크린샷 도구에 노출될 여지가 크다.

## 4. 검증 — step [D]

| Check | Result |
|-------|--------|
| 프론트엔드 `tsc --noEmit` (strict) | ✅ **0 errors** |
| 프론트엔드 `vite build` | ✅ 177 kB (gzip 59 kB) |
| **프론트엔드 `vitest run`** | ✅ **39/39 통과** (4 파일) |
| 백엔드 JDK-only 하네스 | ✅ 93 assertions + 20만 표본 |
| CI 정적 규칙 5건 | ✅ clean |
| provenance | ✅ 40/40 |
| `mvn verify` | ❌ 미실행 |
| k6 부하 테스트 | ❌ 미실행 — 기동 불가 |

### 프론트엔드 테스트 39건 구성

| Suite | Tests | 핵심 검증 |
|-------|-------|-----------|
| `LoginPage` | 13 | L9 회귀(128자), 자격증명 미저장, `credentials` 전송, 강제 변경 라우팅 |
| `OtpRegisterPage` | 11 | **L4 회귀(외부 호출 0건)**, 등록 후 비밀키 DOM 제거, ADM_00026 대응 |
| `PasswordChangePage` | 11 | 현재 비밀번호+OTP 동시 요구, 정책 위반 전체 표시, 요청 본문 4필드 |
| `accessibility` | 7 | axe-core 위반 0, 입력 접근 가능 이름, autocomplete 힌트 |

### axe-core 의 한계를 명시한다

jsdom 은 실제 렌더링을 하지 않으므로 **색상 대비는 검사되지 않는다.** 포커스 순서와
스크린리더 낭독 품질도 자동으로 확인되지 않는다. 이 테스트가 잡는 것은 구조적 위반
(라벨 누락, 중복 id, 잘못된 role 중첩)이며, `color-contrast` 규칙은 결과가 실제 접근성을
반영하지 않으므로 **명시적으로 비활성화**했다 — 통과 표시가 오해를 부르는 것보다 낫다.

*Contrast is not evaluated under jsdom, so the rule is explicitly disabled rather than left to
report a pass that would mislead.*

## 5. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L7 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **97** | — | 55/59. 잔여 4건은 코드 외 요인 |
| 추적성 | 15% | **95** | — | 40/40 provenance |
| 보안 | 20% | **80** | — | **SR-05 미해결** |
| 성능 | 10% | **55** | — | k6 실행 불가 |
| 가독성 | 15% | **93** | +1 | — |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **85** | **+7** | 프론트 39건 + 백엔드 93건 실행. **JUnit 93건은 여전히 미실행** |

**가중 합계: 19.4 + 14.25 + 16.0 + 5.5 + 13.95 + 9.5 + 8.5 = 87.1 / 100**

### 결과: **87.1** — 최고점 (69.75 → … → 86.25 → **87.1**)

잔여 2.9 점:
- **보안 4.0 점** — SR-05
- **성능 4.5 점** — k6 실행 불가
- **테스트 커버리지 1.5 점** — JUnit 미실행

세 항목 합계가 10 점이지만 가중 후 2.9 점이 남는다. **전부 PM·환경 측 조치이며, 추가
코드로 움직이지 않는다.**

## 6. 잔여 4건을 닫기 위해 필요한 것 / What the remaining four actually need

```
NFR-PERF-L01  ← k6 실행  ← 앱 기동  ← ① Maven 설치  ② IRIS_OTP_SECRET_KEY  ③ DDL 적용  ④ PostgreSQL
CONST-SEC-L01 ← ADR-LOGIN-011 결정  ← "비밀번호 DB 유출 이력이 있는가?" (사실 확인)
CONST-DATA-L01 ← DBA 검토 + 롤백 스크립트
CONST-LEGAL-L02 ← DBA 검토 (감사 테이블 5년 보존)
```

**의존 관계상 ①~④ 를 만족시키면 NFR-PERF-L01 하나가 아니라 다음이 함께 해소된다:**
- JUnit 93건 실행 → 테스트 커버리지 +1.5점
- k6 4개 시나리오 실행 → 성능 +4.5점
- Spring 컨텍스트 · MyBatis 매핑 · TOTP 라이브러리 API **최초 검증**
- CONST-DATA-L01 · CONST-LEGAL-L02 동시 해소 (DDL 적용이 곧 검토 완료)

즉 **환경 조치 하나가 잔여 4건 중 3건과 잔여 점수 6점을 동시에 움직인다.** 코드 작업은
그렇게 하지 못한다.

## 7. PM 차단 항목 / Blocking items

| # | Item | 해소 시 효과 |
|---|------|-------------|
| 1 | **Maven 설치 + PostgreSQL + `IRIS_OTP_SECRET_KEY` + DDL 적용** | 잔여 4건 중 **3건** 해소, 7차원 **+6점**, 백엔드 최초 컴파일 검증 |
| 2 | **SR-05 — `application.yml` 하드코딩 AES 키** | 보안 **+4점**, `security-auditor` REJECT 해제 |
| 3 | **ADR-LOGIN-011 결정** (RISK-L01 유출 이력 확인 선행) | CONST-SEC-L01 해소 |
| 4 | `sender_key` 회전 | 운영 중 시스템의 실제 노출 제거 |
| 5 | FR-PWD-007 G1 개정 | 승인되지 않은 요구사항 정리 |

## 8. 남은 품질 항목 / Remaining quality items

| Item | Blocked on |
|------|------------|
| 24개 SEC-L* 부정 경로 테스트 | **Maven** — 작성해도 실행 불가 |
| 통합 테스트 (Testcontainers) | **Maven + Docker** |
| 색상 대비 자동 검증 | 실제 브라우저 필요 (Playwright + axe) |
| E2E 테스트 (Playwright) | 앱 기동 필요 |

**모든 잔여 품질 항목이 동일한 환경 조건에 묶여 있다.** 이 상태에서 테스트를 더 작성하는
것은 "작성되었으나 실행되지 않은" 자산을 늘릴 뿐이다 — 이미 JUnit 93건이 그 상태다.

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 87.1. 품질 항목 완료. 잔여 4건은 코드 외 요인 — §6 참조 | **PENDING** |
| 2026-08-14 | `code-reviewer` | **프론트엔드 APPROVE** (39/39 실행, typecheck·build 통과). 백엔드 컴파일 이력 없음 | **PARTIAL APPROVE** |
| 2026-08-14 | `security-auditor` | L4 결함 대응 완결 확인(외부 호출 0건 회귀 테스트). **SR-05 미해결로 REJECT 유지** | **REJECT** |
