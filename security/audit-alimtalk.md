# 보안 감사 — 카카오 알림톡 슬라이스 (Sprint A1 + A2-01)

> **Date**: 2026-08-19 · **Role**: `security-auditor` · **Iteration**: A1 #4
> **판정 / verdict**: ⚠️ **조건부 승인 / APPROVED WITH CONDITIONS**
> **조건**: SEC-A01(키 회전)은 G3 를 막는다. 코드로 닫을 수 없으며 Ops·벤더 작업이다.

---

## 0. 한계 / limitation

`code-reviewer` 와 마찬가지로 **자기 감사**다. 구현자와 감사자가 같으면 놓친 것이 그대로 남는다.
이 감사는 판단보다 **실행 가능한 통제**를 남기는 데 무게를 두었다 — 사람의 주의력이 아니라 CI 가
막도록. 규제 대응상 필요한 독립 감사는 Skill 5 이고 아직 실행되지 않았다.

Like the code review, this is a **self-audit**: implementer and auditor are the same, so a blind spot stays
blind. It therefore weights **enforceable controls** over judgement — CI blocking rather than human
attention. The independent audit that regulation expects is Skill 5, and it has not run.

---

## 1. 판정 요약 / findings

| ID | CVSS | 항목 | 상태 |
|----|-----:|------|------|
| **SEC-A01** | **7.5 HIGH** | 유출된 발신프로필키가 **아직 회전되지 않았다** (RISK-A03) | 🔴 **OPEN — G3 차단** |
| **SEC-A02** | 5.3 MEDIUM | `application.yml` 에 운영 DB 자격증명과 기본 OTP 키가 평문 | ⚠️ **코드 수정 완료 — 회전 미완** |
| **SEC-A03** | 4.3 MEDIUM | 템플릿 캐시 충돌 시 **바뀌기 전 규칙으로 검증** | ✅ FIXED (CR-A01) |
| **SEC-A04** | — | CI 가 D-A24·D-A30 부류를 인코딩하지 않았다 | ✅ FIXED (CR-A04) |
| **SEC-A05** | INFO | 출력 payload 의 성질을 화면이 알리지 않았다 | ✅ FIXED (CR-A03) |

---

## 2. SEC-A01 — 유출된 키 (HIGH, OPEN, G3 차단)

레거시 `biztalk_admin_50_s001_act.jsp` 등 두 곳에 카카오 발신프로필키가 하드코딩되어 있었고, 그
값은 git 이력에 남아 있다. **이 저장소의 어떤 변경도 그 키를 무효화하지 못한다.** 회전은 벤더 측
운영 작업이다.

**코드가 닫은 것 / what the code does close**:

| 통제 | 위치 |
|------|------|
| 유출된 키를 SHA-256 대조로 **거부** — 회전 후 옛 키를 되돌려 설정하는 것을 막는다 | `SenderProfileKeyResolver` |
| 평문 상수 0 — 해시만 커밋 (유출 방지 클래스가 스스로 D-A24 를 재현하면 안 된다) | 동상 |
| 알 수 없는 기관은 **예외** — 조용한 공용 키 대체 없음 (T-A2) | 동상 |
| 소스에 키 리터럴을 다시 넣으면 CI 실패 | `ci.yml` — `Reject sender profile key literals` |
| 원시 접근자가 vendor 경계 밖으로 나가면 CI 실패 | `ci.yml` — `Confine the raw PII accessor` |

**코드가 닫지 못하는 것**: 유출된 키 자체의 유효성. 벤더가 회전할 때까지 제3자가 이 기관 명의로
알림톡을 발송할 수 있다. **G3 는 이것 없이 통과할 수 없다.**

## 3. SEC-A02 — `application.yml` 평문 자격증명 (MEDIUM, OPEN)

`src/main/resources/application.yml` 에 운영으로 보이는 PostgreSQL 자격증명과 기본 OTP 키가 평문으로
있다. **그 파일 자신의 머리말이 그렇게 하지 말라고 적고 있다.**

### 수정 (2026-08-19) — PM 지시로 실행 / fixed on PM instruction

두 건 모두 **이 파일 자신의 머리말과 정면으로 어긋나 있었다.** 머리말은 *"시크릿은 이 파일에 두지
않는다 … 아래 자리표시자에는 의도적으로 기본값이 없다"* 고 적고 있었다.

| 위치 | 무엇이었나 | 무엇이 되었나 |
|------|-----------|--------------|
| `spring.datasource` | 주소·계정·비밀번호가 그대로 (`biztalk_user` / `biztalk123`) | `${IRIS_DB_URL}` · `${IRIS_DB_USERNAME}` · `${IRIS_DB_PASSWORD}` — **기본값 없음** |
| `iris.auth.otp.secret-key` | `${IRIS_OTP_SECRET_KEY:E0lV…}` — 커밋된 AES-256-GCM 키 | `${IRIS_OTP_SECRET_KEY}` — 기본값 제거 |

두 번째 것은 특히 기록할 만하다. **세 줄 위에 "NO DEFAULT: the application must fail to start rather
than encrypt credential material under a key that is published in source" 라고 적혀 있었고, 그 아래에
기본값이 있었다.** 주석과 코드가 정면으로 모순한 경우다. 커밋된 키로 암호화하는 것은 암호화하지 않는
것과 실질적으로 같다 — 키가 소스와 함께 배포되기 때문이다.

The second is worth recording: **three lines above it read "NO DEFAULT: the application must fail to start
rather than encrypt credential material under a key that is published in source", and a default sat directly
beneath.** Encrypting under a committed key is materially equivalent to not encrypting.

**닫힘으로 실패한다 / it now fails closed.** 환경변수가 없으면 기동하지 않는다. 조용히 어떤
데이터베이스에 붙는 것보다 붙지 않는 편이 안전하다. 로컬 실행 경로는
`src/main/resources/application-local.yml.example` 로 제공하고, `application-local.yml` 은 `.gitignore`
에 넣었다.

**재발 방지 / recurrence blocked.** CI 규칙 `Reject secret values in configuration resources` 를 추가했다.
세 가지를 잡는다: ① 시크릿 성격 키의 리터럴 값 ② 시크릿 성격 키의 `${VAR:default}` 기본값
③ 자격증명이 박힌 JDBC URL. 합성 결함으로 세 갈래 모두 발화를 확인했고, 현재 트리에서는 침묵한다.
**주석은 이것을 막지 못했다 — 빌드가 막는다.**

### 그러나 회전은 남아 있다 / but rotation is still outstanding

**이 수정은 회전을 대신하지 않는다.** 두 값 모두 **이미 git 이력에 있다**:

| 값 | 이력 |
|----|------|
| DB 비밀번호 | 커밋 `0db4d0f` |
| OTP AES 키 | 커밋 `0a987dd`, `0db4d0f`, `24993cb`, `6da9bc4`, `f042dee` |

파일을 고쳐도 이미 배포된 값은 되돌아오지 않는다. **필요한 조치 (Ops)**:

1. `biztalk_user` 의 비밀번호를 교체한다.
2. 새 OTP AES-256-GCM 키를 생성하고, **옛 키로 암호화된 OTP 비밀을 모두 재암호화한다** — 키만
   바꾸면 기존 OTP 가 전부 복호화 실패한다.
3. 이력 재작성(`git filter-repo` 등) 여부는 PM 결정. 저장소가 외부에 공유된 적이 있다면 재작성만으로는
   충분하지 않다 — 이미 복제되었을 수 있으므로 ①②가 본질이다.

**This change is not a substitute for rotation.** Both values are already in git history (commits listed
above). Editing the file does not un-publish them. Items ① and ② are what actually close the exposure;
history rewriting is a separate PM decision and is insufficient on its own if the repository was ever shared.

## 4. 통과한 통제 / controls verified

| 통제 | 근거 |
|------|------|
| **PII 마스킹이 기본값** — `RecipientNumber`·`ProfileKey` 의 `toString()`·`@JsonValue` 가 가려진 값을 돌려준다. 노출은 `exposeForVendorCall()` 을 **명시적으로 부를 때만** | 값 객체 + `previewCarriesNeitherCredentialNorClearNumber` |
| 원시 접근자가 vendor 경계 밖에 없다 | CI 규칙 + grep 0건 |
| 로그에 키·수신번호 평문 없음 — 로그는 **건수만** 기록 | grep + CI 규칙 |
| 응답에 발신프로필키가 마스킹된 형태로도 담기지 않는다 (`/send-readiness`) | FR-AZ-A05, MockMvc 테스트 |
| 화면에 발신프로필키 입력란이 **없다** (레거시에는 있었다) | `발신프로필키 입력란이 존재하지 않는다` 테스트 |
| 테넌트 격리 — `TenantContext.require()` 단일 지점, 범위 불명 시 **거절**(전체 조회로 넓어지지 않음) | `noLookupWithoutSession` |
| `@PreAuthorize` 가 실제로 집행된다 — D-A37 로 `@EnableMethodSecurity` 추가 전까지 **무효**였다 | `AlimTalkControllerSecurityTest` |
| 템플릿 정규식 — `Pattern.quote()` 로 리터럴 이스케이프, 변수 30개 상한, 인접 변수 거부 | `TemplateMatcher` 테스트 |
| 순번 고갈 시 조용히 순환하지 않고 예외 | `TranIdGenerator` |
| 시크릿 스캔 — 평문 키 리터럴 0건 | grep, `src`·`docs`·`mapping` |

## 5. D-A37 — 이 슬라이스를 넘는 발견 / a finding beyond this slice

`@EnableMethodSecurity` 가 없어 **프로그램 전체에서** `@PreAuthorize` 가 집행되지 않고 있었다.
Spring Boot 3 에서 기본값이 off 이기 때문이다. 알림톡 컨트롤러를 검증하다 발견했지만 영향은
**다른 3개 슬라이스의 컨트롤러 5개**에 미친다 — 그들은 권한 검사가 동작한다고 믿고 있었다.

`@PreAuthorize` was not being enforced **programme-wide** because `@EnableMethodSecurity` was absent (off by
default in Spring Boot 3). Found while verifying the AlimTalk controller; the impact lands on **five
controllers across three other slices** that believed their checks were running.

**조치 필요 / action required**: 해당 3개 슬라이스 소유자에게 통지. 각 슬라이스는 자신의 권한 검사가
지금까지 무효였다는 전제로 **재검증**해야 한다. 이 감사는 알림톡 밖의 컨트롤러를 검증하지 않았다.

## 6. 잔여 위험 — PM 이 수용을 결정한 것 / residual risks accepted by the PM

| ID | 내용 | 상태 |
|----|------|------|
| RESIDUAL-A01 | 클립보드 복사가 감사되지 않는다 — 수신번호가 든 JSON 이 감사 없이 화면 밖으로 나갈 수 있다 | PM 수용 |
| RESIDUAL-A02 | `tran_id` 가 운영자 입력으로 남는다 — 서버 생성을 PM 이 거절(AMB-A02b) | PM 수용 |

> **범위 주의**: RESIDUAL-A01 은 *클립보드*로 기술되었으나, `/compose` **응답 자체**가 같은 경로다.
> 다만 현재 payload 는 마스킹되어 나가므로(SEC-A05) 실제 노출 표면은 문서가 가정한 것보다 좁다.
> A2-05 에서 실제 발송을 배선할 때 이 전제가 바뀐다 — 그때 RESIDUAL-A01 을 **재평가해야 한다**.
>
> **Scope note**: RESIDUAL-A01 is written in terms of the clipboard, but the `/compose` **response itself**
> is the same path. The payload currently leaves masked (SEC-A05), so the real exposure is narrower than the
> document assumes. Wiring real despatch in A2-05 changes that premise — RESIDUAL-A01 **must be re-assessed**
> at that point.

## 7. 미실행 / not run

| 항목 | 이유 |
|------|------|
| SBOM + 금지 라이선스 검사 | Maven 없음 (RISK-A12) |
| 의존성 취약점 스캔 | 동상 |
| gitleaks 전체 이력 스캔 | 로컬 미설치 — CI job 은 정의되어 있다. **유출된 키는 이력에 남아 있으므로 이 스캔은 반드시 발화한다**; 이력 재작성 여부는 PM 결정 |
| DAST / 침투 테스트 | 배포 환경 없음 |
| 실제 403 응답 검증 | Boot 컨텍스트 필요 (A1-19 부분) |
