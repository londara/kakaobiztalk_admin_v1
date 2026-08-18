# 보안 감사 리포트 #2 — 로그인 통합 사이클

> **Skill**: 05 step [C]·[E] · **Date**: 2026-08-17 · **Agent**: `security-auditor` (Lead)
> **대상**: 로그인 실 DB 통합, OtpDevBypass 병합, SecurityContext 수정, 설정 변경 전체
> **판정**: **REJECT** — CVSS ≥ 7.0 결함 2건 (하드코딩 시크릿), 기능 정지 보안 결함 1건

---

## 1. 차단 결함 / Blocking findings

### SEC-01 · HIGH · CVSS 7.5 · AES OTP 키 하드코딩 (기존, 미해결)
[application.yml:108](../src/main/resources/application.yml#L108)
```yaml
secret-key: ${IRIS_OTP_SECRET_KEY:E0lV/9B+V4JTWEk31kwKH46Jwfg9UqzfadjRLu43e4U=}
```
OTP 비밀(`OTP_KEY`)을 복호화하는 AES-256 키가 소스에 있다. 위반: ADR-007, CONST-SEC-L02,
전자금융감독규정. 조치: 기본값 제거 → 시크릿 매니저, **키 폐기·재발급**.

### SEC-02 · HIGH · CVSS 8.2 · DB 자격증명 하드코딩 (신규)
[application.yml:13-15](../src/main/resources/application.yml#L13)
```yaml
url: jdbc:postgresql://136.85.16.4:5432/kakaobiztalkdev
username: biztalk_user
password: biztalk123
```
운영으로 보이는 원격 DB 의 <b>실 자격증명이 평문으로 커밋</b>되어 있다. 이전에는
`${IRIS_DB_URL:...}` 플레이스홀더였으나 이번 사이클에 하드코딩으로 바뀌었다.

`CVSS 8.2` (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:L`) — 저장소 접근자가 DB 에 직접 접속 가능.
위반: CONST-SEC-L02, ADR-007, 개인정보보호법(문자내역 PII 접근). 조치: 즉시 되돌리기
→ `${IRIS_DB_URL}`/`${IRIS_DB_PASSWORD}`, **`biztalk123` 비밀번호 회전**, 저장소 이력 점검.

> 이 값이 gitleaks L1 훅을 통과했다면 훅 규칙 보강이 필요하다(base64 키·짧은 DB 비밀번호).

### SEC-03 · HIGH · 계정 잠금이 동작하지 않는다 (브루트포스 방어 무력화)
[AuthenticationService.java:160,188,344](../src/main/java/com/webcash/iris/auth/domain/AuthenticationService.java#L160)

`authenticate()` 는 `@Transactional` 이다. 비밀번호/OTP 실패 시 `incrementLoginAttempt` /
`incrementOtpFailCount` 로 카운터를 올린 뒤 예외를 던진다 — 그 예외가 <b>트랜잭션을
롤백</b>시켜 증가분이 사라진다. 실측: 3회 bad-password 후 `login_attempt=0`.

결과: **FR-LOGIN-003/010 의 5회 잠금이 절대 발동하지 않는다.** 감사 행은
`REQUIRES_NEW` 라 남지만 잠금은 무력하다. TC-LOGIN-001-06/07 이 실패한다.

`CVSS 5.9` (`AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N`) — 온라인 비밀번호/OTP 브루트포스가
잠금 없이 무제한(속도 제한만 존재). 조치: 증가분을 `REQUIRES_NEW` 로 커밋하거나 예외 전
별도 트랜잭션에서 확정. (감사 롤백 결함과 동일 계열 — 그때는 감사만 고쳤다.)

## 2. 검증되지 않은/미구현 보안 항목

| ID | 내용 | 등급 |
|----|------|------|
| SEC-04 | **AMB-M01** — 사용자→이용기관 매핑이 실 스키마에 <b>존재하지 않는다</b>(실측). 현재 fail-closed(비운영자 거부)로 안전측 처리. 테넌트 격리의 전제가 여전히 미해결 | 조건부 6.5 |
| SEC-05 | **로그인 이력(a_user_lgn_prhs) 미구현** — UC-LOGIN-001 Step 15. 감사(iris_auth_audit)와 별개 테이블. 침해 조사용 클라이언트 지문 누락 | MEDIUM |

## 3. 이번 사이클에 해소된 것 / Resolved this cycle

| 항목 | 상태 |
|------|------|
| CR-01 CSRF 토큰 헤더 | ✅ 복원(병합으로 유실됐던 것) — authApi/messageHistoryApi 모두 |
| 로그인 후 403 (SecurityContext 미설정) | ✅ 로그인 시 ROLE_OPERATOR/ROLE_USER 확립·세션 저장 |
| 세션 고정(changeSessionId) | ✅ 세션 확보 후 회전 |
| 감사 롤백(실패 로그인 미기록) | ✅ recordAuth REQUIRES_NEW |
| **OtpDevBypass 안전장치** | ✅ `@PostConstruct` 가 local 외 프로필에서 활성 시 <b>기동 거부</b>. 기본 false. 운영 활성 불가 확인 |

## 4. 보안 정책 판단이 필요한 PM 결정 (문서화됨, 조건부)

| 항목 | 현재 | 리스크 |
|------|------|--------|
| 비밀번호 = 레거시 무솔트 SHA-256(PWD) | PM "use only PWD" | Argon2 대비 약함(L2 잔존). UC/TC-05 와 모순 — 문서 미갱신 |
| `require-https` 기본 false | PM "8080만 사용" | 운영에서 `IRIS_REQUIRE_HTTPS=true` 누락 시 평문 |
| OTP_KEY 평문 사용(key-encrypted=false) | 레거시 데이터 | 저장 시 암호화 포기 |

## 5. OWASP Top 10 요약

| # | 판정 | 근거 |
|---|------|------|
| A02 암호화 실패 | ❌ | SEC-01, SEC-02, 무솔트 SHA-256 |
| A05 보안 설정 오류 | ❌ | 시크릿 하드코딩, require-https 기본 off |
| A07 인증 실패 | ⚠️ | 2요소·세션·리플레이가드는 견고하나 **잠금 무력(SEC-03)** |
| A03 주입 | ✅ | 매퍼 `${}` 0 (주석 1건 제외), `<choose>` 식별자 고정 |
| A09 로깅 | ✅ | 감사 insert-only·REQUIRES_NEW·전화번호 해시 |

## 6. 판정

CVSS ≥ 7.0 결함 **2건(SEC-01, SEC-02)** + 보안 기능 정지 1건(SEC-03) → **REJECT**.
가장 시급: **SEC-02(신규 DB 자격증명 커밋)** 와 **SEC-03(잠금 무력)**.
