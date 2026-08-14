# Sprint L4 Log — 비밀번호 변경·초기화, 교착 해소

> **Sprint**: L4 · **Date**: 2026-08-14
> **Lead**: `team-leader` · **Previous**: [SPRINT-L3-LOG.md](SPRINT-L3-LOG.md)
> **Status**: **PARTIAL** — 실사용 경로는 완성

---

## 1. Sprint 목표 / Sprint goal

Sprint L3 종료 후 진단: 요구사항 43/59 구현, 그러나 **실사용자 0명.**
`PasswordPolicy` 와 해시 저장 SQL 은 완성·검증되었으나 **호출자가 없어**
어떤 계정도 Argon2id 해시를 획득할 수 없었다.

**목표: 그 배선을 완성하고, ADR-LOGIN-011 교착을 결정 없이 우회한다.**

*Diagnosis after L3: 43/59 requirements implemented, zero usable accounts —
`PasswordPolicy` and the hash-writing SQL had no caller. Goal: complete that wiring and
route around the ADR-LOGIN-011 deadlock without requiring the ruling.*

## 2. 완료 항목 / Completed

| Task | Description | Requirements |
|------|-------------|--------------|
| L4-01 | `PasswordChangeService#change` — 현재 비밀번호 + OTP 요구 | FR-PWD-001…006, TM-L018 |
| L4-02 | `PasswordChangeService#resetByOperator` — 임시 비밀번호 발급 | **FR-PWD-007 (신규)**, FR-LOGIN-015 |
| L4-03 | `TemporaryPasswordGenerator` — 4종류 보장, 혼동문자 제외, 16자 | FR-PWD-003/005 |
| L4-04 | `PasswordController` — `/api/auth/password/change` | FR-PWD-001/002 |
| L4-05 | `PasswordAdminController` — `/api/admin/password/reset` | FR-PWD-007 |
| L4-06 | `UserMapper.resetPasswordHash` + XML — `PWD_INIT_YN='N'` | FR-PWD-007, FR-LOGIN-015 |
| L4-07 | `SecurityConfig` — 세션 이전 단계 3개 경로 명시 허용 | NFR-SEC-AUTH-L01 |
| L4-08 | `AuditEvent.ACTION_PASSWORD_RESET` | NFR-OPS-AUDIT-L01 |
| L4-09 | 임시 비밀번호 정책 준수 재시도 루프 (§4 SR-04) | FR-PWD-003/007 |
| L4-10 | `qa/drivers/TempPwdDriver` — 20만 표본 통계 검증 | TEST-PLAN-LOGIN §1.1 |

**신규 파일 5개 · 수정 6개 · Java 파일 총 39개**

## 3. 교착 해소 — 이번 sprint 의 핵심 / The deadlock, resolved

ADR-LOGIN-011 미결정 상태에서 기존 계정은 전원 로그인 불가였다. 이를 **결정 없이**
해소한다:

```
운영자 초기화 → Argon2id 해시 최초 생성 (PWD_INIT_YN='N')
      ↓
사용자 로그인 → 비밀번호+OTP 검증 통과 → passwordChangeRequired=true
      ↓
/api/auth/password/change → 정책 검증 → 새 해시 저장 (PWD_INIT_YN='Y')
      ↓
정상 로그인 가능 · 마이그레이션 완료
```

**왜 ADR 결정 전에 만들어도 낭비가 아닌가:** `resetByOperator` 는 옵션 B(전면 초기화)
에서는 마이그레이션의 <b>주 수단</b>이고, 옵션 A(로그인 시 상향)에서는 휴면·잠금 계정을
위한 <b>예비 경로</b>다. 어느 쪽으로 결정되어도 필요한 기능이다.

*Why building it before the ruling is wasted under neither option: it is the primary
migration mechanism under option B and the fallback for dormant or locked accounts under
option A.*

> 다만 이것은 **결정의 대체가 아니다.** 옵션 A 가 선택되면 사용자 개입 없는 자동
> 마이그레이션이 가능하고, 옵션 B 라면 계정 수만큼의 운영자 작업이 필요하다. 규모에 따라
> 비용 차이가 크다.
> *This is not a substitute for the ruling: option A migrates without user involvement,
> while option B costs one operator action per account.*

## 4. 발견·수정한 결함 / Defects found and fixed

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| SR-04 | **생성된 임시 비밀번호가 정책을 위반할 수 있었다.** `TemporaryPasswordGenerator` 는 문자 종류만 보장하고 연속 문자열(`abcd`)을 검사하지 않는다. 그런 비밀번호를 발급하면 사용자가 강제 변경 화면에서 입력해야 하는데 `PasswordPolicy` 가 거절한다 — 계정이 복구 불가 상태에 빠진다 | MEDIUM | ✅ FIXED — 정책 검증 재시도 루프 (최대 20회) |

### SR-04 를 실측했다 / SR-04 was measured, not estimated

20만 표본 실행 결과:

| Metric | Value |
|--------|-------|
| 정책 위반 발생률 | **0.0025 ~ 0.0060%** (실행 2회, 5건 / 12건) |
| 위반 원인 | **전부 연속 문자열** (문자 종류 부족 0건) |
| 실무 환산 | 운영자 초기화 **약 16,700 ~ 40,000회당 1건** |
| 재시도 20회 후 실패 확률 | **~1e-85 이하** |

**추정이 아니라 측정이다.** 이 결함은 코드 리뷰로 발견하기 어렵다 — 두 클래스가 각각
올바르고, 확률적으로만 충돌한다. 발생 시 증상은 "가끔 임시 비밀번호가 거부됨"이며
재현이 사실상 불가능하다. 통계적으로 실행해 본 것이 발견 수단이었다.

*Measured, not estimated. This defect resists code review: both classes are individually
correct and collide only probabilistically. The symptom would be "temporary passwords are
sometimes rejected", effectively irreproducible. Statistical execution was the detection
method.*

## 5. 검증 — step [D]

| Check | Result |
|-------|--------|
| `SecretCipher` 실행 | ✅ 12/12 |
| `AccountPolicy` + `PasswordPolicy` 실행 | ✅ 29/29 |
| **`TemporaryPasswordGenerator` × `PasswordPolicy` 20만 표본** | ✅ **위반율 측정 및 대응 확인** |
| CI 정적 규칙 5건 | ✅ clean |
| provenance 주석 | ✅ 31/31 main 파일 |
| `mvn verify` | ❌ 미실행 — Maven 미설치 |
| Spring 컨텍스트 / MyBatis / TOTP API | ❌ 미검증 |

## 6. 7 차원 자체 평가 — step [E]

| Dimension | Weight | Score | Δ vs L3 | Basis |
|-----------|--------|-------|---------|-------|
| 완성도 | 20% | **90** | +8 | 실사용 경로 완성. 잔여: rate limiter, IP 허용목록, React, 관리자 알림 |
| 추적성 | 15% | **95** | — | 31/31 provenance |
| 보안 | 20% | **90** | +2 | 자격증명 변경에 2요소 적용, 자기초기화 금지, 임시 비밀번호 1회 노출 |
| 성능 | 10% | **40** | — | 부하 테스트 불가 |
| 가독성 | 15% | **92** | — | — |
| 표준 준수 | 10% | **95** | — | — |
| 테스트 커버리지 | 10% | **62** | +4 | 통계 검증 추가. JUnit 미실행 상태 유지 |

**가중 합계: 18.0 + 14.25 + 18.0 + 4.0 + 13.8 + 9.5 + 6.2 = 83.75 / 100**

### 결과: **83.75** (69.75 → 75.95 → 81.35 → 83.75)

네 sprint 연속 임계 미달. 잔여 격차 6.25 점 중 **성능(40)이 6.0 점, 테스트
커버리지(62)가 3.8 점**을 점유한다. 두 차원 합계 9.8 점이 전부 **Maven 부재** 하나에
묶여 있다. 다른 다섯 차원의 합은 90.7 점 수준이다.

*Four sprints below threshold. Of the remaining 6.25 points, 성능 and 테스트 커버리지
account for 9.8 points of shortfall between them, all gated on Maven's absence. The other
five dimensions average around 90.7.*

## 7. PM 차단 항목 / Blocking items

| # | Item | Blocks | Change |
|---|------|--------|--------|
| 1 | **Maven 미설치** | JUnit 실행, 커버리지, 부하, Spring/MyBatis/TOTP 검증 | 변화 없음 — 7차원 격차의 사실상 전부 |
| 2 | **`IRIS_OTP_SECRET_KEY` 발급** | **애플리케이션 기동** | 미해결 — 없으면 시작 자체가 불가 |
| 3 | DDL 검토 | 4개 신규 테이블/컬럼 | 미해결 |
| 4 | **`sender_key` 회전** | 운영 중 시스템의 실제 노출 | 미해결 |
| 5 | ADR-LOGIN-011 | 마이그레이션 **비용**(자동 vs 계정당 운영자 작업) | **격하** — 더 이상 실사용 차단 요인이 아님 |
| 6 | AMB-L03 | IP 허용목록 범위 | 미해결 |

> **5번이 차단에서 비용 문제로 격하되었다.** 운영자 초기화 경로로 실사용이 가능해졌다.
> 결정은 여전히 필요하나, 이제 "쓸 수 있는가"가 아니라 "얼마나 드는가"의 문제다.
>
> **2번이 실질적 1순위로 올라왔다.** 키가 없으면 `SecretCipher` 생성자가 기동 시점에
> 실패하므로 애플리케이션이 시작되지 않는다. 의도된 설계이나(ADR-007), 배포 전 반드시
> 발급되어야 한다: `openssl rand -base64 32`.

## 8. 남은 작업 / Remaining

| Item | Requirement | Note |
|------|-------------|------|
| Rate limiter | FR-LOGIN-025, RISK-L07 | Argon2id 비용 DoS. 이제 인증 엔드포인트 5개 |
| React 로그인·등록·변경 화면 | FR-LOGIN-001/022, NFR-USE-L01 | 프론트엔드 여전히 0% |
| IP 허용목록 실제 차단 | FR-LOGIN-024, L5 | AMB-L03 미결 |
| 관리자 로그인 알림 | FR-LOGIN-021, L1 | |
| OTP 단일 사용 강제 | TM-L004 | 위협 모델에 완화책으로 기재했으나 **미구현** — §9 참조 |
| 24개 SEC-L* 테스트 | TEST-PLAN-LOGIN §4 | Maven 필요 |
| 세션 reaper 스케줄러 | ADR-LOGIN-012 §4.3 | 쿼리 작성됨 |
| 계정 프로비저닝 | OI-06 | **요구사항 미정** |

## 9. 위협 모델과 구현의 불일치 / Threat model vs implementation gap

`threat-model-LOGIN.md` TM-L004 는 완화책으로 "단일 사용 강제(single-use enforcement per
(account, step))"를 기재했으나 **구현되지 않았다.** 현재는 같은 OTP 코드를 30~60초 창
안에서 여러 번 사용할 수 있으며, 로그인 직후 비밀번호 변경에 같은 코드를 재사용하는
것이 실제로 가능하다(편의상 이점이 있으나 위협 모델의 기재와 어긋난다).

*The threat model lists single-use enforcement as a TM-L004 mitigation, but it is not
implemented. The same code can currently be used more than once inside its window —
convenient for the login-then-change flow, but inconsistent with what the model claims.*

**조치 필요:** 구현하거나, 위협 모델에서 잔여 위험으로 재분류할 것. 문서가 없는 통제를
주장하는 상태는 레거시의 실패 유형(L3/L5/L6)과 같다.

---

**Sprint gate**

| Date | Approver | Comment | Status |
|------|----------|---------|--------|
| 2026-08-14 | PM | 7차원 83.75 < 90. 실사용 경로 완성; 차단 6건 중 2번이 기동 전제 | **PENDING** |
| 2026-08-14 | `code-reviewer` | 통계 검증은 진전. 전체 컴파일 이력 없어 APPROVE 보류 | **PENDING** |
| 2026-08-14 | `security-auditor` | 자격증명 변경 2요소·감사 확인. **TM-L004 불일치(§9) 해소 조건부** | **CONDITIONAL** |
