# 코드 리뷰 리포트 — 로그인 통합 사이클

> **Skill**: 05 step [B] · **Date**: 2026-08-17 · **Agent**: `code-reviewer`
> **판정**: **REJECT** — HIGH 기능 결함 1건 + 추적성 결함

---

## CR-L1 · HIGH · 계정 잠금 카운터가 롤백된다
[AuthenticationService.java:160](../src/main/java/com/webcash/iris/auth/domain/AuthenticationService.java#L160)

`@Transactional authenticate()` 안에서 실패 시 카운터를 증가시킨 뒤 예외를 던진다 →
트랜잭션 롤백 → 증가분 소멸. 감사만 `REQUIRES_NEW` 로 살아남고 잠금은 무력.
`incrementLoginAttempt`/`incrementOtpFailCount` 를 별도 커밋 트랜잭션으로 옮겨야 한다.
**세부: [security/audit-2.md SEC-03](../security/audit-2.md).**

## CR-L2 · MEDIUM · UC/테스트케이스와 코드가 모순 (추적성)
| 문서 | 문서 내용 | 코드 |
|------|----------|------|
| UC-LOGIN-001 Step 6, TC-05 | Argon2id, "no SHA-256" | 레거시 SHA-256(PWD) |
| UC-LOGIN-001 Step 11, TC-15 | ≥90일 **또는** PWD_INIT_YN='N' | **N 그리고** ≥90일 |
| UC-LOGIN-001 Step 15 | 로그인 이력 insert | 미구현 |

앞 둘은 이번 세션 PM 결정(정당)이나 <b>UC/TEST 문서가 갱신되지 않았다</b>. 감사 시
"명세 위반"으로 보인다. docs 갱신 또는 코드 환원 필요.

## CR-L3 · MEDIUM · 로그인 이력 미구현
UC-LOGIN-001 Step 15 / `a_user_lgn_prhs`. 감사 테이블과 별개. `SenderNumber` 등 다른
기능은 갖춰졌으나 이 write 경로가 없다.

## CR-L4 · LOW · 세션 이탈 배너 과다 발동
[App.tsx](../src/main/frontend/src/App.tsx) + `SessionRegistry.register`. 남은 세션 행이
있으면(로그아웃 안 함/만료 전) 자기 자신의 재로그인도 "다른 기기" 침해 경고를 띄운다.
sourceIp/userAgent/loginAt 를 비교해 실제 타 기기·활성 세션일 때만 경고해야 한다.

## CR-L5 · LOW · CsrfIntegrationTest 실패
인증 요청에 XSRF 쿠키 재발급이 안 됨. 설계상 SPA 는 최초 쿠키를 계속 쓰므로 실사용 영향은
낮으나, 테스트 단언과 정합되지 않는다. 설계 확정 후 테스트/필터 정리.

## 잘 된 점 / What holds up (이번 사이클 실측)

| 항목 | 확인 |
|------|------|
| **OtpDevBypass 안전장치** | `@PostConstruct` 가 local 외 활성 시 기동 거부. 기본 false. 운영 활성 불가 |
| CSRF 3요소(쿠키 저장소+평문 핸들러+CsrfCookieFilter) | 실 기동에서 쿠키 발급·검증 확인 |
| 로그인 후 인가 | ROLE_OPERATOR 확립 → /api/admin 200 (실측) |
| 병합 충돌 해소 | otpKeyEncrypted + OtpDevBypass 양쪽 보존, verifySecondFactor 에 평문키 처리 이식 |
| SQL 주입 | 매퍼 `${}` 0 |
| provenance | 79/79 |

## 요약

| ID | 등급 | 조치 |
|----|------|------|
| CR-L1 | **HIGH** | 즉시 · 트랜잭션 경계 수정 |
| CR-L2 | MEDIUM | docs 갱신 또는 환원 |
| CR-L3 | MEDIUM | 다음 Sprint |
| CR-L4 | LOW | 다음 Sprint |
| CR-L5 | LOW | 설계 확정 후 |

**판정: REJECT** (HIGH 존재 + 시크릿 2건은 security-auditor 소관).
