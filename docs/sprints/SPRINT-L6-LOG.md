# Sprint L6 Log — 프론트엔드 구현 및 실제 빌드·테스트 실행

> **Sprint**: L6 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L5-LOG.md](SPRINT-L5-LOG.md)
> **Status**: **PARTIAL** — 프론트엔드 완료, 잔여 4건

---

## 1. Sprint 목표 / Sprint goal

L5 종료 시점의 최대 잔여 항목: **프론트엔드 0%.** 백엔드 7개 엔드포인트가 완성되었으나
사용자가 도달할 화면이 없어 직접 HTTP 호출로만 사용 가능했다.

**목표: 레거시 화면을 React 로 포팅하고, 처음으로 빌드·테스트를 실제 실행한다.**

*Largest gap at the end of L5: the frontend at 0%. Seven backend endpoints existed with no
screen to reach them. Goal: port the legacy screens to React and — for the first time —
actually build and run tests.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L6-01 | Vite + React 18 + TypeScript strict 스캐폴드 | CONST-TECH-L01, ADR-001 |
| L6-02 | `authApi.ts` — 6개 API 클라이언트 | FR-LOGIN-001/023, FR-OTP-001/005, FR-PWD-001 |
| L6-03 | `LoginPage` — 아이디·비밀번호·OTP 단일 화면 | FR-LOGIN-001/022, NFR-USE-L01 |
| L6-04 | `OtpRegisterPage` — 2단계 (신원확인 → 키 등록) | FR-OTP-001…006 |
| L6-05 | `PasswordChangePage` — 강제 변경 경로 포함 | FR-PWD-001/002, FR-LOGIN-014/015 |
| L6-06 | `App.tsx` — 상태 기반 흐름 (라우터 미사용, §4 참조) | NFR-USE-L01 |
| L6-07 | `styles.css` — WCAG 2.1 AA 대비·포커스·터치 타깃 | NFR-COMPAT-01 |
| L6-08 | `LoginPage.test.tsx` — **13건 실행 통과** | TEST-PLAN-LOGIN §1.3 |
| L6-09 | `.gitignore` — `node_modules/`, `dist/` | — |

**신규 파일 12개 · Java 36 + TS/TSX 7 = 총 43개 소스**

## 3. 검증 — step [D] · **백엔드에서 불가능했던 것**

> **Node v24 / npm 11 이 설치되어 있다.** 따라서 프론트엔드는 백엔드와 달리 **실제로
> 컴파일·빌드·테스트가 가능하다.** 이 sprint 는 프로젝트 최초로 온전한 빌드 파이프라인을
> 통과한 산출물을 만들었다.

| Check | Result |
|-------|--------|
| `npm install` | ✅ 166 패키지 |
| **`tsc --noEmit` (strict)** | ✅ **오류 0** |
| **`vite build`** | ✅ **35 모듈 변환, 152 kB (gzip 49 kB)** |
| **`vitest run`** | ✅ **13/13 통과** |
| 백엔드 JDK-only 하네스 | ✅ 61 assertions + 20만 표본 |
| CI 정적 규칙 5건 | ✅ clean |
| `mvn verify` (백엔드) | ❌ 여전히 미실행 |

### 실행된 프론트엔드 테스트 13건

| 검증 내용 | 요구사항 |
|-----------|----------|
| 3개 자격증명 입력란 존재 | FR-LOGIN-001 |
| **L9 회귀: 비밀번호 maxlength 128** (레거시 15) | FR-PWD-005 |
| OTP 6자리 제한 · `inputmode=numeric` | FR-LOGIN-009 |
| OTP 입력에서 비숫자 제거 | FR-LOGIN-009 |
| 정상 로그인 → `onAuthenticated` | FR-LOGIN-001 |
| 강제 변경 → 변경 화면 (세션 미확립) | FR-LOGIN-014/015 |
| OTP 미등록 → 등록 화면 유도 | FR-LOGIN-008 |
| 실패 메시지 `role=alert` 전달 | NFR-USE-L02, WCAG 3.3.1 |
| 실패 후 OTP 만 비움 (아이디·비밀번호 유지) | TM-L004 |
| **아이디저장 시 이메일만 저장 — 비밀번호·OTP 미저장 검증** | FR-LOGIN-022, NFR-SEC-LOG-L01 |
| 체크 해제 시 저장값 제거 | FR-LOGIN-022 |
| 제출 중 버튼 비활성 (중복 제출 방지) | TM-L004 |
| **`credentials: 'same-origin'` 전송 확인** | NFR-SEC-SESSION-L01 |

> 10번째 테스트가 특히 중요하다 — `localStorage` 전체를 문자열화하여 비밀번호와 OTP
> 코드가 **포함되지 않았음**을 증명한다. 레거시는 쿠키에 아이디만 저장했으나, 그 성질을
> 검증하는 테스트는 없었다.
>
> *The tenth test matters most: it stringifies all of localStorage and proves the password
> and OTP are absent. The legacy also stored only the id, but nothing verified it.*

## 4. 설계 판단 / Design decisions

**라우터 라이브러리를 쓰지 않았다.** 인증 흐름의 4개 상태는 URL 로 직접 진입해서는 안
되는 것들이다 — 비밀번호 변경 화면은 이메일 컨텍스트가 없으면 동작할 수 없고, OTP 등록
2단계는 서버 세션의 대기 비밀키에 의존한다. 상태로 관리하면 그 잘못된 진입이 **불가능**
해진다. 의존성 절감은 부수 효과다.

*No router library: the four states must not be URL-reachable. Password change has no email
context when entered directly, and OTP enrolment's second step depends on a pending secret
in the server session. Holding the flow in state makes invalid entry impossible rather than
merely discouraged; fewer dependencies is a side effect.*

**접근성은 사후 점검이 아니라 구조로 넣었다.** `outline: none` 을 사용하지 않고(레거시
`login.css` 는 사용했다), 오류를 색상만으로 전달하지 않으며(왼쪽 테두리 병행), 오류
영역을 조건부 마운트하지 않는다 — 새로 생성되는 `aria-live` 영역은 일부 스크린리더가
읽지 못한다.

**`type="number"` 를 OTP 에 쓰지 않았다.** 스피너가 붙고 선행 0 이 사라지는 브라우저가
있어 `012345` 같은 코드가 깨진다. `type="text"` + `inputMode="numeric"` + `pattern`
조합이 옳다.

## 5. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L5 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **95** | +3 | 55/62 요구사항. 잔여 4건 중 3건이 배포 구성·부하·PM 결정 |
| 추적성 | 15% | **95** | — | provenance 유지, trace CSV 갱신 |
| 보안 | 20% | **80** | — | **SR-05 여전히 미해결.** 프론트엔드 자체는 자격증명 미저장 검증 완료 |
| 성능 | 10% | **45** | +5 | 프론트엔드 번들 크기 확인(152 kB). 백엔드 부하 테스트는 여전히 불가 |
| 가독성 | 15% | **92** | — | — |
| 표준 준수 | 10% | **95** | — | write scope 준수 (`src/main/frontend/`) |
| 테스트 커버리지 | 10% | **74** | **+8** | **13건 실행 + 백엔드 61건 + 20만 표본.** JUnit 93건은 여전히 미실행 |

**가중 합계: 19.0 + 14.25 + 16.0 + 4.5 + 13.8 + 9.5 + 7.4 = 84.45 / 100**

### 결과: **84.45** (69.75 → 75.95 → 81.35 → 83.75 → 82.55 → 84.45)

L5 의 하락을 회복하고 최고점을 기록했으나 임계 90 미달. 잔여 5.55 점 중:
- **성능 5.5 점** — 백엔드 부하 테스트 불가 (Maven)
- **테스트 커버리지 2.6 점** — JUnit 미실행 (Maven)
- **보안 4.0 점** — SR-05 (하드코딩 키)

즉 **격차의 거의 전부가 Maven 설치와 SR-05 해소, 두 가지**에 묶여 있다. 코드 추가로
움직일 수 있는 부분은 1 점 내외다.

## 6. PM 차단 항목 / Blocking items

| # | Item | Blocks | Priority |
|---|------|--------|----------|
| 1 | **SR-05 — 하드코딩 AES 키** | 보안 차원 4점, G3 게이트 | **최우선** |
| 2 | **Maven 미설치** | 성능 5.5점 + 커버리지 2.6점 = 8.1점 | **최우선** |
| 3 | DDL 검토 | 4개 신규 테이블/컬럼, 기동 전제 | 높음 |
| 4 | `IRIS_OTP_SECRET_KEY` | 기동 전제 | 높음 |
| 5 | `sender_key` 회전 | 운영 중 시스템 노출 | 높음 |
| 6 | AMB-L03 | FR-LOGIN-024 | 중간 |
| 7 | ADR-LOGIN-011 | 마이그레이션 비용 | 중간 |

## 7. 남은 작업 / Remaining — 4건

| Requirement | Item | Blocked on |
|-------------|------|------------|
| FR-LOGIN-024 | IP 허용목록 실제 차단 | **AMB-L03 (PM 결정)** |
| FR-LOGIN-021 | 관리자 로그인 알림 (설정 기반) | 코드 (소규모) |
| NFR-SEC-CHANNEL-L01 | TLS 강제 | **배포 구성** |
| NFR-PERF-L01 | 부하 테스트 | **Maven** |

또한 후속 항목:
- **QR 이미지 렌더링** — 현재 `otpauth://` 링크로 대체. 클라이언트 측 QR 라이브러리 추가
  필요 (FR-OTP-003 은 링크로도 충족되나 UX 개선 여지)
- **axe-core 접근성 자동 검증** — TEST-PLAN-LOGIN §1.3 요구. 수동 구현은 완료했으나
  자동 검사는 미도입
- **OtpRegisterPage / PasswordChangePage 컴포넌트 테스트** — LoginPage 만 작성됨

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 84.45, 최고점. 차단 1·2번 해소 시 90 도달 가능 | **PENDING** |
| 2026-08-14 | `code-reviewer` | **프론트엔드는 APPROVE** — typecheck·build·test 전부 통과. 백엔드는 컴파일 이력 없어 보류 | **PARTIAL APPROVE** |
| 2026-08-14 | `security-auditor` | 프론트엔드 자격증명 미저장 검증 완료. **SR-05 미해결로 REJECT 유지** | **REJECT** |
