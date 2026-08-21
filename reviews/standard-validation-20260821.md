# Standard Validation Report — 2026-08-21

> **Skill**: `07-validate-standard` (read-only audit) · **Lead**: `code-reviewer`
> **Scope**: 표준 하네스 패키지 자기 일관성 + 적용 프로젝트(IRIS BizTalk Portal) 게이트 통과 가능성
> **원칙**: 본 리포트는 어떤 파일도 수정하지 않는다 (§5). 보완은 별도 작업으로 분리한다.
>
> **본 문서는 같은 날 5회차 검증을 담는다.** Pass 1 → 보완 → Pass 2 → 보완 → Pass 3 → 보완(/08) → Pass 4 → Pass 5(확인 회차).
> 파일명 규칙이 일자 단위이므로 회차를 덮어쓰지 않고 **델타와 함께 누적 기록**한다.

## Summary (Pass 5 — 최종)

| 항목 | 값 |
|------|----|
| 모드 | **Package + Project** (양쪽 마커 모두 존재 — §1 판별 모호, 아래 근거 참조) |
| 총점 | **89 / 100** (Pass 1: 71 → 81 → 86 → 90 → **89**) |
| 판정 | **FAIL** |
| 차단 이슈 | **1** (VS-001) |
| 경고 이슈 | **5** (Pass 4: 4 · VS-020 신규) |

### ⚠ Pass 5 의 핵심 발견 — 이번 회차 보완 전체가 **미커밋** 상태다 (VS-020)

Pass 5 는 확인 회차로 시작했으나, **점수가 처음으로 내려갔다**. 원인은 새 결함이 아니라 **이전 회차 판정의 일관성 결여**다.

`git status` 48건 · `HEAD` 는 여전히 `34b4254`. 즉 이번 세션의 보완은 **작업 트리에만 존재**한다:

| 대상 | 작업 트리 | **HEAD (저장소)** |
|------|----------|------------------|
| `application.yml` DB·OTP 기본값 | 제거됨 ✅ | **기본값 4개 그대로 존재** ❌ |
| `.env` | 추적 해제·ignored ✅ | **추적 상태로 존재** ❌ |
| `ci.yml` SAST·AI 감사 job | 추가됨 ✅ | **없음** ❌ |
| `pre-commit-gitleaks.sh` 우회 증적 | 구현됨 ✅ | **없음** ❌ |

**이것이 왜 중요한가**: GitHub Actions 는 **푸시된 커밋**의 워크플로를 읽고, clone·fork 는 **HEAD** 를 받는다. 따라서 커밋 전까지 ① L2 SAST·AI 감사는 **실행되지 않고** ② 새로 clone 하는 사람은 **fail-closed 설정도 못 받고 `.env` 는 받는다** ③ 다른 개발자에게 L1 우회 통제가 **적용되지 않는다**.

**Pass 3~4 판정의 일관성 결여를 정정한다.** `.env` 에 대해서는 "HEAD 에 남아 있다"를 정확히 기록했으나, **같은 검사를 VS-002/003/004/006/007 에는 적용하지 않았다.** 한쪽만 HEAD 를 확인하고 나머지는 작업 트리만 보고 "해소"로 판정한 것이다. 보완 자체는 실재하고 검증되었으나, **"해소"의 범위는 작업 트리까지**이며 저장소 반영은 커밋에 달려 있다.

### Pass 4 요약

`/08-harness` 감사 모드로 VS-007·VS-016 을 적용하고 VS-017 을 오탐으로 철회했다. **차단은 여전히 VS-001 1건**이며, 그 1건은 자격증명 회전이라는 **사람 조치**에만 달려 있다 — 저장소 측에서 할 수 있는 일은 모두 끝났다.

새로 발견된 것은 **VS-019**(`hooks/gitleaks.toml` 이 규칙 없는 stub)이며, 이는 **VS-006 의 심각도 평가를 정정**한다. Pass 1~2 는 "커스텀 룰셋이 L1 에서 적용되지 않는다"를 결함으로 기록했다. 배선 결함(L1·L2 설정 불일치)은 사실이었으나, **그 룰셋에는 적용될 규칙이 애초에 없었다** — 파일 전체가 `useDefault = true` 4행이다. 따라서 VS-006 의 실질 영향은 Pass 1~2 의 서술보다 작았다. 정정해 둔다.

`docs/.harness-state.yaml` 이 생성되어 §4 의 "상태파일 없음" 엣지 케이스가 해소되었고, 기록값은 실제 산출물과 **완전 일치**한다(G1 7/7 · G2 14/14 · Sprint 로그 19 · `deliverables/` 없음 · `harness_version` 1.5 = 표준 1.5 · G3 `pending`).

### Pass 3 의 핵심 — 차단이 1건으로 줄었고, 그 1건은 코드로 닫히지 않는다

`application.yml` 의 fallback 기본값 제거로 **VS-002·VS-003 이 해소**되었다. 남은 차단은 **VS-001 단 1건**이며, 그 성격을 정확히 볼 필요가 있다.

| 상태 | 값 | 의미 |
|------|----|------|
| git index | 삭제 staged (`D  .env`) | 앞으로의 커밋에서 제외 |
| `.gitignore` | 등록됨 (`git check-ignore` YES) | 재추가 방지 |
| **HEAD 트리** | **`.env` 여전히 존재** (`git cat-file -e HEAD:.env` → 존재) | **삭제가 커밋되지 않았다. 지금 clone 하면 파일을 그대로 받는다** |
| 이력 | 1 커밋(`0acfb39`)에서 복원 가능 | 커밋해도 이력에서는 사라지지 않는다 |
| 자격증명 자체 | **회전 미완료** | 값이 여전히 유효하다 |

즉 `git rm --cached` 는 **인덱스만** 바꿨다. 노출을 실제로 끝내는 것은 ① 삭제 커밋 ② **자격증명 회전** ③ (선택) 이력 재작성이며, ②가 없으면 ①·③ 모두 무의미하다. **AI 가 수행할 수 없는 항목이므로 PM 조치 대기 상태로 남긴다.**

**모드 판별 근거.** `.claude/skills/`(8), `templates/`(21), `hooks/`(4), `scripts/`(2), `HARNESS-PROCESS-STANDARD.md` 존재 → Package mode 충족. 동시에 `docs/`, `src/`, `qa/`, `reviews/`, `security/`, `mapping/`, `parity/` 산출물 존재 → Project mode 충족. 양쪽 기준으로 검증했다. **패키지 자체는 2회차에서도 결함 0건이다.**

**Pass 2 의 핵심.** 판정은 FAIL 그대로지만 **성격이 달라졌다.** Pass 1 은 통제 기반(L2 SAST 부재·룰셋 미연결·보존기간 모순)과 시크릿 노출이 섞여 있었다. 통제 기반은 모두 닫혔고, 남은 FAIL 은 **단일 원인 — 시크릿 3건** 이다. 이 3건은 코드 수정만으로 닫히지 않으며(자격증명 회전 + git 이력 결정이 필요) **PM/임원 결재 사항**이다.

---

## Pass 1 → Pass 2 → Pass 3 델타

| ID | Pass 1 | Pass 2 | Pass 3 | 근거 |
|----|--------|--------|--------|------|
| VS-001 | FAIL | FAIL | **FAIL 유지 (범위 축소)** | 추적 해제·`.gitignore` 등록 완료. 그러나 **HEAD 에 파일 존재**(삭제 미커밋) + 이력 복원 가능 + **회전 미완료** |
| VS-002 | FAIL | FAIL | ✅ **해소** | `application.yml:61-63` → `${IRIS_DB_URL}`·`${IRIS_DB_USERNAME}`·`${IRIS_DB_PASSWORD}` (기본값 0/3). fail-closed 기동이 주석 선언과 일치 |
| VS-003 | FAIL | FAIL | ✅ **해소** | `application.yml:235` → `${IRIS_OTP_SECRET_KEY}` (기본값 제거) |
| VS-018 | — | — | ✅ **발견 즉시 해소** | VS-002 수정으로 `application.yml:50` 의 `application-local.yml.example` 참조가 **동작 필수 경로**가 되었으나 그 파일은 미존재였다. `.env.example`(이름만, 값 없음) 신설 + 한/영 주석 2곳 수정 |

### Pass 3 → Pass 4 델타 (/08-harness 감사 모드 적용분)

| ID | Pass 3 | Pass 4 | 근거 |
|----|--------|--------|------|
| VS-001 | FAIL | **FAIL 유지** | 저장소 측 조치 완료(index `D`, ignored). **HEAD 에 파일 존재·이력 복원 가능·회전 미완료** — 잔여는 전부 사람 조치 |
| VS-007 | WARN | ✅ **해소** | `IRIS_L1_BYPASS=1` 경로 신설 — 당일 증적 파일 + `Approver:` 행 요구. 3경로 실행 검증(증적 없음→차단 / Approver 없음→차단 / 정상→수락+CI 재스캔 경고). **`--no-verify` 는 훅 내부에서 차단 불가함을 코드 주석에 명시** |
| VS-016 | WARN | ✅ **해소** | 5개 governance 표 + **서술 2곳** 갱신. `DEV-PLAN-SENDERNO:216`("G1 미결이면 S2b 앞당기기")과 `risk-register-SENDERNO:130` 은 낡은 **실행 지침**이었으므로 superseded 표기 |
| VS-017 | WARN | ❌ **철회 — 오탐** | `PasswordChangeService:224-242` 가 **이미** 최대 20회 재생성 + 정책 검증 + 실패 시 `IllegalStateException`. 강제 위치가 호출자인 것이 타당하다(정책의 이메일·이력 검사는 생성기가 알 수 없는 값을 요구) |
| VS-010 | WARN | **부분 진행** | `CidrMatcher` 41행 전량 커버(시험 21건 GREEN). 잔여 6 클래스 ~198행 |
| VS-012 | WARN | **WARN 유지** | PM 이 이번 회차 적용 대상에서 제외 |
| VS-015 | WARN | **WARN 유지** | `PROJECT-STRUCTURE.md:7,174,175,230` — 미결 3건 + 낡은 G1/G2 PENDING 표기 |
| VS-019 | — | **WARN 신규** | `hooks/gitleaks.toml` 이 규칙 0건 stub. VS-006 심각도 평가를 정정한다 |
| VS-004 | WARN | ✅ **해소** | `ci.yml` 에 `sast`·`security-auditor-agent`·`l2-verdict` 3 job 추가. job 그래프 기계 검증: 7 job, `needs` 6건 전부 해석 |
| VS-005 | WARN | ✅ **해소** | `prod-gate-checklist.md:60` → **5년**(ADR-006 인용). 규제 확인 미결 항목을 별도 줄로 추가 |
| VS-006 | WARN | ✅ **해소** | L1 `--config hooks/gitleaks.toml` + 룰셋 부재 시 실패. CI gitleaks 도 `config-path` 추가. `bash -n` 통과 |
| VS-007 | WARN | **WARN 유지** | L1 우회 시 결재 증적 요구 로직 여전히 없음 |
| VS-008 | WARN | ✅ **해소** | 4 슬라이스 DEV-PLAN·TEST-PLAN 8건에 Status 줄 추가. `awaiting`/미표기 설계문서 0건 |
| VS-009 | WARN | **부분 해소** | Architect 3행·Leader 2행에 근거 명시. 잔여 1건은 VS-015 로 승계 |
| VS-010 | WARN | **부분 해소** | `auth.crypto` 25행 공백 해소(신규 시험 15건 GREEN). BUNDLE 게이트는 미달 |
| VS-011 | WARN | ❌ **철회 — 오탐** | §2.3 예외 조항 적용. [HARNESS-PROCESS-STANDARD.md:583](../HARNESS-PROCESS-STANDARD.md) 이 중첩 소유를 **의도된 규약으로 명시** |
| VS-012 | WARN | **WARN 유지** | `doc/parsed/` 경로 여전히 단수·미존재 |
| VS-013 | NOTE | ✅ **해소** | CR-02 원인 규명·수정 완료, 관련 4 클래스 46/46 GREEN |
| VS-014 | NOTE | **NOTE 유지** | `deliverables/` 미생성 (Skill 6 미실행 — 정상) |
| VS-015 | — | **WARN 신규** | `PROJECT-STRUCTURE.md` §5 미결 3건 |
| VS-016 | — | **WARN 신규** | DEV-PLAN 6건의 governance 표가 여전히 `G1 PENDING` |
| VS-017 | — | **WARN 신규** | `TemporaryPasswordGenerator` Javadoc 계약 미강제 |

---

## Score (Pass 5)

| 차원 | 점수 | 근거 |
|------|------|------|
| 완성도 | **19** / 20 | 스킬 8/8 · 에이전트 13/13 · 템플릿 21/21 · Hook 4 · 스크립트 2/2 · `gitleaks.toml` 존재. `deliverables/` 미생성(Skill 6 미실행 — 정상). Pass 4 와 동일 |
| 추적성 | **15** / 15 | `// req:`·`// source:` 규칙 운영, ADR 38건, `requirements-matrix.csv`, `docs/.harness-state.yaml`. Pass 4 와 동일 |
| 보안 | **14** / 20 | **−1 (VS-020).** 통제는 구현·검증되었으나 **저장소에 반영되지 않았다** — HEAD 는 여전히 자격증명 기본값 4개와 추적된 `.env` 를 담고 있고, CI 의 SAST·AI 감사 job 도 푸시 전까지 실행되지 않는다. 잔여 VS-001(회전)과 VS-019(룰셋 stub)도 유지 |
| 성능/자동화 | **10** / 10 | CI 7 job + `l2-verdict`. 변동 없음 |
| 가독성/문서 | **15** / 15 | **+1.** VS-016 해소 — governance 표 5곳 + 낡은 **실행 지침** 2곳 정정. 문서가 금일 결재 상태와 일치 |
| 표준 준수 | **8** / 10 | 잔여: VS-015(`PROJECT-STRUCTURE` 미결 3건)·VS-012(`doc/parsed`) |
| 테스트/검증 | **8** / 10 | **+1.** 재측정 완료: **1035건 GREEN**(Failures 0 · Errors 0, Pass 3 대비 +21). `CidrMatcher` 40/41 커버. BUNDLE **BRANCH 68.3%** — 게이트까지 **27 브랜치**만 남았다(Pass 3: 62). LINE 72.0%·`auth.domain` 53.6% 는 여전히 미달이므로 만점은 아니다 |

<details>
<summary>Score (Pass 3) — 이력</summary>



| 차원 | 점수 | 근거 |
|------|------|------|
| 완성도 | **19** / 20 | 스킬 8/8, 에이전트 13/13, 템플릿 21/21, Hook L1·L2·L3. `deliverables/` 미생성(Skill 6 미실행 — 정상) |
| 추적성 | **15** / 15 | `// req:` 182 파일 · `// source:` 156 파일, ADR 38건, `requirements-matrix.csv`, REQ-ID 운영 — 변동 없음 |
| 보안 | **13** / 20 | **+4.** CRITICAL 3건 중 2건 구조적 해소 — fail-closed 기동이 **주석이 아니라 코드로** 성립한다(기본값 0/4). 잔여 VS-001 은 **회전 없이는 코드로 닫히지 않는 항목**이므로 상한이 유지된다. L1 우회 증적(VS-007) 미구현도 반영 |
| 성능/자동화 | **10** / 10 | CI 7 job + `l2-verdict` 차단 게이트. `if: always()` 로 판정 누락이 통과로 읽히는 경로 제거 |
| 가독성/문서 | **14** / 15 | **+1.** 로컬 실행 경로 복구(`.env.example` + 주석 2곳 정정, VS-018). 잔여 −1: governance 표 6건이 금일 결재와 모순(VS-016) |
| 표준 준수 | **8** / 10 | VS-008/009 해소, VS-011 오탐 철회. 잔여: `PROJECT-STRUCTURE` 미결 3건(VS-015)·`doc/parsed`(VS-012) |
| 테스트/검증 | **7** / 10 | 자격증명 변경 **반영 후 재실행 완료: 1014건 GREEN**(Failures 0 · Errors 0). 직전 QA 회차의 "빌드 RED" 미재현. `auth.crypto` 게이트 해소로 위반 4→3건. BUNDLE 게이트는 여전히 미달이므로 상한 유지 |

</details>

> 차원 점수는 §3.0 **계층 3(AI 자가평가)** 이며 참고 지표다. FAIL 판정은 전부 계층 1·2 근거만 사용했다.

---

## Findings (Pass 3)

| 등급 | ID | 위치 | 내용 | 권고 조치 |
|------|----|------|------|-----------|
| **FAIL** | VS-001 | `.env` (repo root) · `HEAD:.env` | **자격증명 노출이 계속 유효하다.** 저장소 측 조치는 진행됐다 — 추적 해제(index `D`), `.gitignore` 등록(`.env`·`.env.*`·`!.env.example`) 확인. 그러나 ① **삭제가 커밋되지 않아 `HEAD` 트리에 파일이 그대로 있다**(`git cat-file -e HEAD:.env` 존재) — 지금 clone 하면 받는다 ② 커밋해도 `0acfb39` 이력에서 복원 가능 ③ **`IRIS_DB_PASSWORD`·`IRIS_OTP_SECRET_KEY` 회전 미완료**. CVSS **9.8** 유지 | **순서가 중요하다**: ① 자격증명 **회전**(AI 수행 불가 — DB·인프라 접근 필요) ② 삭제 커밋 ③ 이력 재작성(`git filter-repo`) 여부 PM/임원 결재. **①이 없으면 ②·③은 노출을 끝내지 못한다.** 이력 재작성을 거부하면 두 자격증명을 **영구 침해로 취급하는 예외**를 결재로 남길 것 |
| ✅ 해소 | VS-002 | `src/main/resources/application.yml:61-63` | fallback 기본값 제거 완료 — `${IRIS_DB_URL}`·`${IRIS_DB_USERNAME}`·`${IRIS_DB_PASSWORD}`. 기계 검증: 기본값 보유 줄 **0/3**. 2026-08-19 주석이 선언한 fail-closed 기동이 **이제 코드로 성립**한다 | — (VS-001 의 회전에 통합) |
| ✅ 해소 | VS-003 | `src/main/resources/application.yml:235` | OTP 키 fallback 제거 완료 — `${IRIS_OTP_SECRET_KEY}` | 회전 시 **기존 OTP 비밀 재암호화 또는 재등록** 필요 (키 교체만으로는 복호화 불가) |
| WARN | VS-007 | `hooks/pre-commit-gitleaks.sh` (74행) | §2.4 가 요구하는 **우회 시 결재 증적 요구** 로직 없음. `--no-verify` 우회가 무증적 | 증적 파일(예: `security/bypass-approvals/`) 요구 검사 추가, 또는 PM 판단으로 예외 기록 |
| WARN | VS-010 | `pom.xml:257,262` / jacoco (Pass 4 재측정) | **BUNDLE LINE 72.0%**(2709/3764, **303행 부족**) · **BRANCH 68.3%**(1101/1611, **27 브랜치 부족**) · `auth.domain` **53.6%**(252/470, **195행 부족**). `jacoco:check` 위반 3건 유지. `auth.crypto` 는 Pass 2 에서 95.0% 로 위반 목록에서 제거됨 | **BRANCH 게이트가 27 브랜치 앞이다** — 가장 값싼 잔여 승리다. `auth.domain` 잔여 zero-coverage 5 클래스: `PasswordChangeService` 68 · `OtpRegistrationService` 49 · `RateLimiter` 32 · `IpAllowlistPolicy` 18 · `AdminLoginNotifier` 18(+내부 7) · `OtpReplayGuard` 13. 순수 로직인 `OtpReplayGuard`·`IpAllowlistPolicy`·`RateLimiter`(63행)를 먼저 처리하면 BRANCH 게이트가 닫힐 가능성이 높고, LINE·PACKAGE 는 서비스 2종(117행)이 필요하다 |
| WARN | VS-012 | `.claude/agents/doc-spec-parser.md:7`, `data-model-designer.md:42` | `write_dirs: doc/parsed/` — 12개 에이전트는 `docs/` 규약, 이것만 `doc/`(단수)이며 디렉터리 **미존재**. §583 의 중첩 소유 규약에도 미등재 | `docs/parsed/` 로 통일하거나 의도적 분리를 §583 에 명시 |
| WARN | VS-020 | 저장소 전체 (`git status` 48건 · `HEAD` = `34b4254`) | **이번 세션 보완 전체가 미커밋이다.** `git diff HEAD` 기준 `application.yml`(기본값 제거)·`.gitignore`(`.env`)·`ci.yml`(SAST·AI 감사)·`pre-commit-gitleaks.sh`(우회 증적) 4건 전부 **WORKTREE ONLY**. `git show HEAD:src/main/resources/application.yml` 은 DB 자격증명·OTP 키 기본값 4개를 **여전히 포함**한다. 결과: CI 는 새 job 을 실행하지 않고(워크플로는 푸시된 커밋에서 읽힌다), clone 은 fail-closed 설정 없이 `.env` 를 받는다 | 보완분을 커밋한다. **단 순서 주의** — `.env` 삭제 커밋 전에 **자격증명 회전이 선행**되어야 한다(VS-001). 회전 없이 커밋하면 이력에 값이 남은 채 "정리됨"으로 보이게 된다. 문서 변경분과 보안 변경분을 분리 커밋하면 회전 대기 중에도 문서·시험은 반영할 수 있다 |
| WARN | VS-019 | `hooks/gitleaks.toml` (전체 4행) | **패키지가 배포하는 "커스텀 룰셋"에 규칙이 0건이다** — 파일 내용은 `title` + `[extend] useDefault = true` 뿐이다. 즉 L1·L2 모두 gitleaks 기본 규칙만 적용한다. 이 프로젝트는 실제 유출 이력이 있는데도(Kakao `sender_key`, 휴대폰번호 3건, `.env` 의 `IRIS_DB_PASSWORD`·`IRIS_OTP_SECRET_KEY`) **자기 패턴이 룰셋에 하나도 없다**. 게다가 한국 휴대폰번호 검사는 룰셋이 아니라 `pre-commit-gitleaks.sh:53-61` 의 손수 작성한 grep 에 들어 있어, **CI(L2)에서는 그 검사가 실행되지 않는다** | `IRIS_[A-Z_]*(PASSWORD\|SECRET\|KEY)\s*=`, 한국 휴대폰번호(`01[0-9]{8,9}`), Kakao `sender_key` 패턴을 룰로 추가. 휴대폰번호 검사는 bash 에서 룰셋으로 옮기면 L1·L2 가 동일 규칙을 쓴다. **본 항목은 VS-006 의 심각도 평가를 정정한다** — 배선 결함은 사실이었으나 적용될 규칙이 없었으므로 실질 영향은 Pass 1~2 서술보다 작았다 |
| WARN | VS-015 | `docs/design/PROJECT-STRUCTURE.md:7,174-175,230` | **§5 구조 결정 3건이 미결**이고 PM 행이 `PENDING`. 게다가 근거가 낡았다 — Decision 1 은 "`src/test/java` 를 어떤 에이전트도 소유하지 않는다"고 적었으나 `backend-developer.md:8` 이 **이미 `src/test/` 를 소유**한다(권고안 A 가 적용된 상태). Decision 3 은 G1/G2 를 `PENDING` 으로 적으나 금일 결재 완료. 문서 Status 도 `PROPOSED — awaiting PM approval` | Decision 1 은 **이미 해소된 것으로 종결** 처리(ADR 로 근거 기록), Decision 2(DDL 취급)는 PM 확인 필요, Decision 3 은 금일 결재 반영. 낡은 전제를 남긴 채 결재하면 존재하지 않는 쟁점을 승인하게 된다 |
| WARN | VS-016 | `DEV-PLAN-ALIMTALK.md:225`, `-INSTITUTION.md:221`, `-LOGIN.md:145`, `-REPORT.md:175`, `-SENDERNO.md:212,216`, `risk-register-SENDERNO.md:130` | 각 문서 헤더 Status 는 결재 완료로 갱신되었으나, **§10 governance 표와 위험 대응 서술은 여전히 `G1 PENDING`** 이다. `-SENDERNO:216` 은 "G1 이 여전히 미결이면 S2b 를 앞당겨라"는 실행 지침을 담고 있어, 낡은 상태가 **의사결정을 오도**할 수 있다 | 6개 위치의 게이트 상태를 2026-08-21 결재로 동기화. Pass 1 이 헤더만 검사해 놓친 항목이며, 헤더/본문 이중 표기 자체가 드리프트 원인 |
| WARN | VS-017 | `src/main/java/com/webcash/iris/auth/crypto/TemporaryPasswordGenerator.java:56-79` | 클래스 Javadoc 은 "생성된 비밀번호는 `PasswordPolicy` 를 반드시 통과해야 한다"고 선언하나 `generate()` 에 **정책 검사도 재시도 루프도 없다**. 정책은 4자 이상 연속열을 거절하는데 생성기는 그것을 만들 수 있다(약 10⁻⁴/호출). 발생 시 운영자가 발급한 임시 비밀번호를 강제 변경 화면이 거절해 **복구 경로가 스스로 막힌다**. 신규 시험 작성 중 발견 | `generate()` 에 정책 검증 후 재생성 루프 추가. 확률이 낮아 반복 단정으로는 잡히지 않으므로(시험만 간헐 실패) 시험은 간극을 문서화하는 형태로 두었다 — `TemporaryPasswordGeneratorTest#sequentialRunRuleIsNotEnforcedByTheGenerator` |
| NOTE | VS-014 | `deliverables/` | 미존재 → `RUNBOOK.md` 등 Skill 6 산출물 없음. `prod-gate-checklist.md:28` 이 `deliverables/06-ops/RUNBOOK.md` 운영팀 리뷰를 요구 | G3 전 Skill 6 실행. 현 단계에서는 정상 |

### 해소 확인 (Pass 1 → Pass 2)

| ID | 확인 방법 | 결과 |
|----|----------|------|
| VS-004 | `ci.yml` job 파싱 + `needs` 해석 검사 | `sast`·`security-auditor-agent`·`l2-verdict` 존재, 6개 `needs` 전부 해석. `sast` 는 `security-events: write` 를 **job 단위로만** 승격(최소 권한). `l2-verdict` 는 `if: always()` — skip 이 통과로 읽히는 경로 차단 |
| VS-005 | `prod-gate-checklist.md` grep | `(**5년**` 1건, `ISMS-P 증적 요건` 확인 줄 1건. ADR-006(ACCEPTED, PM 2026-08-14) 인용. **숫자만 맞추지 않고** ADR-006:67 의 미확인 규제 근거를 별도 체크 항목으로 승계 |
| VS-006 | `bash -n` + grep | 구문 통과. `--config` 3건, 룰셋 부재 시 **묵시적 기본룰 fallback 대신 실패**. CI 도 `config-path` 추가 → L1·L2 동일 룰셋 |
| VS-008 | 8개 문서 Status grep | 8/8 `APPROVED (G2)`. DEV-PLAN 4건은 이월 조건(CONFLICT-R02·AMB-S07/OI-02·D-T6)도 함께 기재 |
| VS-013 | `mvn -Dtest=...` 4 클래스 | 46/46 GREEN. CR-02 원인은 `csrf()` 후처리기가 공유 컨텍스트의 `CsrfFilter.tokenRepository` 를 교체하는 **시험 오염**이었다 |
| VS-007 | 훅 3경로 실제 실행 | ① 증적 없음 → exit 1 ② `Approver:` 행 없음 → exit 1 ③ 정상 증적 → exit 0 + "L2 CI still scans" 경고. `bash -n` 통과. 시험용 증적 파일은 삭제 확인 |
| VS-016 | grep 재확인 | `docs/design`·`docs/requirements` 에서 낡은 `G1 PENDING`/`awaiting G1|G2` **0건**(`PROJECT-STRUCTURE` 제외 = VS-015). 서술형 실행 지침 2곳도 superseded 표기 |
| 상태파일 | 산출물 교차 확인 | G1 문서 **7/7** · G2 문서 **14/14** · Sprint 로그 **19** · `deliverables/` 없음 · `harness_version` 1.5 = 표준 1.5 · **G3 `pending`**(AI 가 승인하지 않음, §3.2 준수) |

### False positive / 예외 기록

| 항목 | 판정 | 근거 |
|------|------|------|
| **VS-017 임시 비밀번호 정책 미강제** | **오탐 — 철회** | `PasswordChangeService:224-242` 가 이미 `MAX_TEMPORARY_ATTEMPTS = 20` 회 재생성 + 전체 정책 검증 + 실패 시 `IllegalStateException` 을 수행하며, 주석이 연속열 시나리오를 정확히 설명한다. 강제 위치가 **호출자**인 것은 설계상 타당하다 — 정책의 이메일 포함·이력 재사용 검사는 생성기가 보유하지 않는 값(이메일·해시 이력)을 요구한다. 생성기 내부에 루프를 두면 규칙이 2곳에 생기고 `crypto → domain` 순환이 발생한다. **Pass 3 는 생성기만 읽고 유일한 호출자를 확인하지 않았다** |
| `qa/drivers/SecretCipherDriver.java:8` 리터럴 secret | **예외 — 시험 픽스처** | 같은 파일 6행이 키를 `new byte[32]`(전부 0)로 만든다. 왕복 암복호 검증용 고정값이며 실제 자격증명이 아니다 |
| `qa/load/login-load.js:142,164,186` 리터럴 password | **예외 — 부하시험 계정** | 계정이 `loadtest-${__VU}@example.com` 이고 `otpCode: '000000'` 이다. 합성 계정이다. **단** 이 계정이 공유 환경에 실제 프로비저닝되어 있다면 공유 비밀이 되므로, 부하시험 계정의 존재 여부는 운영팀 확인 항목 |
| `OtpRegistrationService.java:119` | **오탐 — 스캔 패턴 결함** | `String secret = ...generate();` 는 **메서드 호출**이며 리터럴이 아니다. 정규식이 `generate()` 를 값으로 오인했다 |
| **VS-011 `write_dirs` 중첩** | **오탐 — 철회** | [HARNESS-PROCESS-STANDARD.md:583](../HARNESS-PROCESS-STANDARD.md) 이 중첩 소유를 **의도된 규약으로 명시**하며, 발견된 7쌍 중 5쌍(`docs/design/`=architect, `docs/requirements/`=trace-mapper, `docs/sprints/`=team-leader, `src/main/.../adapter|codec/`=adapter-builder)을 **이름까지 열거**한다. 같은 규약을 따르는 `src/main/frontend/`·`reviews/leader-reports/` 2쌍은 §583 의 원칙("같은 **파일**을 두 에이전트가 쓰지 않으면 충돌이 아니다")에 포섭된다. §2.3 예외 조항 적용 |
| Pass 1 의 VS-011 "중첩 3건" 수치 | **자체 오류** | 검증 스크립트가 에이전트별 `write_dirs` 의 **첫 항목만** 읽었다. 정확한 수치는 7쌍이며, 동시에 `backend-developer` 가 `src/test/` 를 소유한다는 사실도 그때 누락되었다 — 그 누락이 VS-015 의 낡은 전제를 Pass 1 에서 못 잡은 원인이다 |
| 폐기 별칭 `java-porter`/`parity-tester` | 예외 — 위반 아님 | 검출 2건은 `07-validate-standard/SKILL.md:42`·`SKILL.km.md:78` 의 **검증 기준 서술문**. 배포 자산 0건 |
| `seed.yaml`/`bootstrap` 잔존 | 예외 — 위반 아님 | 5건 전부 **명시적 제외 선언문**(`README.md:39`, `PACKAGE-INDEX.md:6`, `HARNESS-PROCESS-STANDARD.md:506,508`, 변경이력 `:888`). `node_modules` 매치는 서드파티 |
| `HARNESS-PROCESS-STANDARD.md:887` "템플릿 17→20" | 예외 — historical | v1.2 변경이력. §2.7 상 이력성 서술은 FAIL 처리하지 않는다. 현행 주장(`WORKFLOW-GUIDE.md:326` 21개)은 실제와 일치 |
| `support_agents` 참조 | PASS | 8 스킬 전체가 13 에이전트와 100% 해석 |
| 파생 HTML 신선도 | PASS | `WORKFLOW-GUIDE.html`/`.md` 동일 커밋·동일 mtime |

---

## Gate Decision (Pass 5)

| 게이트 | 진입 가능 | 근거 |
|--------|----------|------|
| **G1 분석** | **YES** | REQUIREMENTS-SPEC **7/7** 결재. 낡은 표기 0건 |
| **G2 설계** | **YES** | DEV-PLAN·TEST-PLAN **14/14** 결재 + `ADR-001` ACCEPTED |
| **G3 릴리즈** | **NO** | 차단 3건 + 선행 조건 1건. ① **VS-001** CVSS 9.8 자격증명 회전(**PM/인프라**) ② **VS-010** 커버리지 — BRANCH 27·LINE 303·`auth.domain` 195(**개발**) ③ **VS-014** `deliverables/`·RUNBOOK(**운영팀 입력**) ④ **VS-020** — 위 ①~③ 을 모두 해결해도 **커밋되지 않으면 저장소 상태는 변하지 않는다**. G3 는 배포 게이트이며 배포 대상은 작업 트리가 아니라 커밋이다 |

> **Pass 4 대비 변화**: 차단 건수는 같으나 **VS-020 이 선행 조건으로 추가**되었다. Pass 4 는 "저장소 측 조치는 완료"라고 적었는데, 이는 부정확했다 — 작업 트리 조치는 완료이고 저장소 반영은 미완료다.

<details>
<summary>Gate Decision (Pass 3) — 이력</summary>



| 게이트 | 진입 가능 | 근거 |
|--------|----------|------|
| **G1 분석** | **YES** | 요구사항 정의서 7건 전부 결재(문자내역 08-14, ALIMTALK 08-19, 나머지 5건 08-21). `awaiting G1` 0건 |
| **G2 설계** | **YES** | 필수 산출물 `DEV-PLAN`/`TEST-PLAN`/`ADR-001` 충족, 결재자 근거(single-approver model, [PROJECT-PROPOSAL.md §12](../docs/planning/PROJECT-PROPOSAL.md)) 문서화 완료. 잔여 VS-016(표기 동기화)은 게이트 차단 사유가 아니다 |
| **G3 릴리즈** | **NO** | 차단 3건이나 **성격이 분리되었다**. ① **VS-001** — CVSS 9.8 미해결. §3 즉시 FAIL 및 `prod-gate-checklist.md:24`("CVSS ≥ 7.0 = 0") 위반. **사람 조치(회전) 대기** ② **VS-010** — 커버리지 게이트 미달. `auth.domain` 236행 등 **개발 작업 대기** ③ **VS-014** — `deliverables/`·RUNBOOK 미작성. **Skill 6 실행 대기 + 운영팀 입력 필요**(모니터링 엔드포인트·온콜 연락처·배치 일정·blue-green 토폴로지는 저장소에서 도출 불가) |

> **G3 까지의 잔여 작업이 세 주체로 나뉜다** — 회전은 PM/인프라, 커버리지는 개발, RUNBOOK 은 운영팀 입력. 세 갈래가 병렬 진행 가능하며 서로를 막지 않는다.

</details>

---

## Notes

- **본 리포트는 파일을 수정하지 않았다** (§5). Pass 1 과 Pass 2 사이의 보완은 별도 작업으로 수행되었고, 본 문서는 그 결과를 **재검증**한 기록이다.
- **차단 판정의 근거 계층**(§3.0): VS-001~003 은 계층 1(git 추적 상태·`.gitignore` 내용·YAML 구조 grep)로 금일 재확인했고, 계층 2(CVSS v3.1 산식 + `security/audit-S2a.md`·`reviews/cross-validation-4.md` 교차검증)로 점수화했다. 차원 점수(계층 3)는 단독 차단 근거로 쓰지 않았다.
- **회전이 제거보다 먼저다.** `.env` 와 `application.yml` 모두 git 이력에 있으므로 값 제거만으로는 아무것도 고쳐지지 않는다. 이력 재작성 여부는 PM/임원 결재 사항이며, 거부 시 두 자격증명을 **영구 침해로 취급하는 예외**를 결재로 남겨야 한다.
- **Pass 4 재측정 완료 (`mvn -o verify`)**: **테스트 1035건 · Failures 0 · Errors 0** (Pass 3 대비 +21 = `CidrMatcherTest`). `verify` 실패 원인은 **jacoco 게이트 3건뿐**이다.
  - **BRANCH 게이트가 27 브랜치 앞이다** (68.3% → 70%). 잔여 3개 게이트 중 **가장 먼저 닫힐 수 있는 것**이며, 순수 로직 클래스 3종(`OtpReplayGuard`·`IpAllowlistPolicy`·`RateLimiter`, 63행)으로 도달 가능성이 높다. LINE(303행)·`auth.domain`(195행)은 서비스 2종을 포함해야 한다.
  - `CidrMatcher` 는 41행 중 **40행** 커버(잔여 1행은 `Range` 레코드의 생성 메서드). 접근통제 분기는 전량 검증되었다.
- **Pass 3 측정 (자격증명 변경 직후)**: **테스트 1014건 · Failures 0 · Errors 0**. `verify` 실패 원인은 **jacoco 커버리지 게이트 3건뿐**이며, 위반 목록은 Pass 2 와 동일하다(bundle LINE/BRANCH + `auth.domain`).
  - **주목할 점**: DB·OTP fallback 기본값을 제거해도 **깨지는 시험이 0건**이다. 시험 어느 것도 그 기본값에 의존하지 않았다는 뜻이며, 기본값이 기능적 필요 없이 **순전히 잔존물로 남아 있었다**는 것을 확인한다. `src/test/resources` 에 datasource 재정의가 없다는 사전 확인과 일치한다.
- **Pass 2 측정 (13:13, 자격증명 변경 이전)**: **테스트 1014건 · Failures 0 · Errors 0**. `qa/test-report-4-senderno-S2a.md` 가 보고한 **빌드 RED**(embedded-postgres 기동 초과로 매퍼 통합시험 ERROR)는 **이 실행에서 재현되지 않았다** — 해당 회차의 RED 는 환경 조건(기동 시간)에 기인한 것으로 보이며, 현재 코드 기준 시험은 전부 통과한다. `verify` 의 유일한 실패 원인은 **jacoco 커버리지 게이트 3건**이다(VS-010).
  - 직전 QA 회차와의 차이: 게이트 위반 **4건 → 3건**(`auth.crypto` 해소), CSRF 실패 **1건 → 0건**(CR-02 수정). 직전 회차가 사용한 `-Dmaven.test.failure.ignore=true` 우회는 **더 이상 필요하지 않다**.
  - LINE 총량이 3771 → 3764 로 7행 줄었다. `CsrfCookieFilter` 삭제분과 일치한다.
- **본 감사가 스스로 낸 오류 4건을 기록으로 남긴다.** 계층 1 근거도 **추출 방법이 틀리면 객관적이지 않다**는 것을 보여주므로, 삭제하지 않고 남긴다.
  1. **VS-011 오탐** (Pass 2 철회) — 표준 §583 을 확인하지 않았고, 스크립트가 `write_dirs` 의 **첫 항목만** 읽었다. 그 누락이 `src/test/` 소유 사실을 가려 VS-015 의 낡은 전제를 한 회차 늦게 발견하게 만들었다.
  2. **VS-017 오탐** (Pass 4 철회) — 생성기만 읽고 **유일한 호출자를 확인하지 않았다**. 이미 존재하는 재시도 루프를 "없는 통제"로 보고했다.
  3. **`OtpRegistrationService:119` 오탐** (Pass 3 기록) — 정규식이 `.generate()` 를 리터럴 값으로 오인했다.
  4. **VS-006 심각도 과대평가** (Pass 4 정정) — 배선 결함은 사실이었으나 룰셋이 규칙 0건 stub 이었으므로 실질 영향은 서술보다 작았다(VS-019).
  5. **VS-002/003/004/006/007 의 "해소" 범위 과대 진술** (Pass 5 정정) — `.env` 에는 HEAD 확인을 적용했으나 나머지 보완에는 적용하지 않고 작업 트리만 보고 "해소"로 판정했다. **같은 검사를 일부 항목에만 적용한 것**이 오류다(VS-020).
  - 공통 원인은 하나다: **확인 범위를 좁게 잡았다.** 1~4 는 한 파일만 보고 결론을 냈고(호출자·규약 문서·파일 전체 미확인), 5 는 한 항목에만 HEAD 검사를 적용했다. 5건 중 5건이 "더 넓게 봤으면 달랐을" 판정이다.
- **Pass 5 는 확인 회차였으나 점수가 내려갔다.** 새 결함이 아니라 이전 판정의 정정 때문이다. 반복 감사의 값은 새 결함을 찾는 데만 있는 것이 아니라 **직전 회차의 판정을 의심하는 데** 있다는 사례로 남긴다.
- **프로세스 관찰**: 2026-08-21 결재는 구현·검증이 선행한 **사후 결재**로 각 결재란에 기록되어 있다. 게이트 순서 이탈은 이미 발생한 사실이며 본 검증의 차단 사유는 아니나, G3 결재자(PM + 정보보호 + 운영)가 인지해야 한다.
- 보완은 본 스킬이 아니라 별도 작업으로 분리한다 (§5·§6). `08-harness` 로 점수화·우선순위 액션플랜을 받을 수 있다.
