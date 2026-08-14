---
name: 07-validate-standard
description: 표준 하네스 또는 적용 프로젝트의 표준 준수 상태를 읽기 전용으로 검증. 스킬/에이전트/템플릿/보안 Hook/게이트/Team-with-Leader/산출물 증적을 PASS-WARN-FAIL로 점검한다.
when_to_use: 하네스 변경 직후, 신규 프로젝트 setup 직후, G1/G2/G3 게이트 직전, 릴리즈 전, PM이 표준 준수 감사를 요청할 때.
phase: 0,1,2,3,4
lead_agent: code-reviewer
support_agents:
  - security-auditor
  - trace-mapper
  - docs-writer
outputs:
  - reviews/standard-validation-{YYYYMMDD}.md
---

# Skill 07 — 표준 준수 검증 (Validate Standard)

> **목적**: 표준 하네스 자체 또는 하네스를 적용한 프로젝트가 필수 구조와 가드레인을 충족하는지 검증한다.
> **성격**: 선형 개발 단계가 아니라 반복 실행하는 감사 스킬이다.
> **원칙**: seed.yaml / bootstrap 기반 초기 생성 흐름은 사용하지 않는다. Skill 기반 산출물과 실제 파일 상태를 기준으로 판단한다.

---

## 1. 실행 모드 판별

현재 디렉터리에서 다음 기준으로 모드를 먼저 판별한다.

| 모드 | 판별 기준 | 목적 |
|------|----------|------|
| Package mode | `.claude/skills/`, `templates/`, `hooks/`, `scripts/`, `HARNESS-PROCESS-STANDARD.md` 존재 | 표준 하네스 패키지 자체 검증 |
| Project mode | `docs/`, `src/` 또는 프로젝트 산출물 존재 + 하네스 자산 일부 적용 | 실제 프로젝트 적용 상태 검증 |

판별이 모호하면 Package mode와 Project mode 후보를 모두 표시하고, 어떤 기준으로 검증했는지 리포트에 남긴다.

## 2. 검증 범위

### 2.1 패키지 인벤토리

- 8개 스킬 존재: `01-plan-project` ~ `06-finalize-deliverables`, `07-validate-standard`, `08-harness`
- 모든 스킬(01~07, 08-harness)에 `lead_agent`와 `support_agents` frontmatter가 존재하고 실제 에이전트 파일과 매칭
- README / PACKAGE-INDEX / PROCESS 의 Skill catalog 표가 각 SKILL frontmatter 의 lead/support 와 일치
- 13개 표준 에이전트 존재 및 frontmatter 포함
- 현재 배포 자산(템플릿 / 예시 CSV / 활성 에이전트 문서)이 현재 표준 에이전트명(`backend-developer`, `frontend-developer`, `qa-engineer`)과 일치하고, 폐기 별칭(`java-porter`, `parity-tester`)이 남아 있지 않음
- 21개 산출물 템플릿 존재
- 보안 Hook 3단계 존재: L1 pre-commit, L2 CI, L3 prod gate
- Hook / Script / CI 가 참조하는 보조 파일(`hooks/gitleaks.toml` 등)이 패키지에 실제 존재
- 운영 스크립트 존재: `parity-check.sh`, `generate-codecs.sh`
- README / PACKAGE-INDEX / WORKFLOW / PROCESS 문서의 스킬 개수와 실제 파일 트리 일치

### 2.2 워크플로우와 게이트

- Skill 1~6의 진입 조건, 출력 산출물, DoD가 문서에 명시되어 있는가
- README / PACKAGE-INDEX / PROCESS / WORKFLOW 의 빠른 시작 명령이 `docs/` 생성과 `git init` 이후 Hook 설치 순서를 일관되게 따른다
- G1 분석, G2 설계, G3 릴리즈 게이트가 존재하는가
- 게이트별 결재자와 필수 산출물이 명확한가
- `07-validate-standard`가 setup 직후 및 각 게이트 직전에 실행 가능한 검증 스킬로 문서화되어 있는가
- 사람 질의 생성 기준(CQ1~CQ3, §4.8)이 정의되어 있고, 고정 질문 세트(01 12문항·06 7문항)가 그 기준으로 태깅되어 있는가

### 2.3 Team-with-Leader 구조

- `_team-leader` 에이전트가 DAG 분석, dispatch, 충돌 중재, 7차원 평가, PM 보고 책임을 가진다
- 각 에이전트 frontmatter에 `write_dirs`가 있다
- 동일 디렉터리에 복수 에이전트가 쓰는 경우 WARN으로 표시한다. 단, 의도적 공동 산출물 디렉터리는 근거가 있으면 예외 처리한다
- TEAM_CHANNEL / DIRECT / LEADER_BROADCAST 또는 동등한 통신/보고 규칙이 있다

### 2.4 가드레인과 보안 Hook

- L1 Hook이 gitleaks를 실행하며, 우회 시 결재 증적 파일을 요구하는가
- L2 CI가 gitleaks, 의존성 스캔, SAST를 수행하는가
- L2 AI 감사 단계가 `TODO`로 조용히 통과하지 않는가
- L3 prod gate가 PM + 정보보호 + 운영 결재를 요구하는가
- CVSS >= 7.0 결함이 릴리즈 차단 조건으로 명시되어 있는가
- 시크릿, PII 평문 로그, 약한 암호화, 인증 우회, SQL Injection 등 REJECT 기준이 있다

### 2.5 추적성과 산출물

- 요구사항에 REQ-ID 체계가 있다
- `// source:` 또는 `// req:` 추적 규칙이 있다
- ADR 작성 의무와 ADR 템플릿이 있다
- trace matrix 또는 동등한 요구-코드 매핑 산출물이 있다
- Sprint log, review report, security audit, cross-validation, runbook 산출물이 있다

### 2.6 품질과 평가

- 7차원 평가 기준과 임계치가 명시되어 있다
- 90점 미만 보완 루프와 최대 5회 후 PM escalation이 있다
- 7차원 가중치·임계 90·5회는 §4.6/§4.9 기준(`[근거:sg-gw회고·조정가능]`)을 상속하며, 프로젝트 조정 시 ADR/회고에 근거가 남아 있는가
- Skill 5에서 독립 재평가 또는 교차검증으로 자기 합리화를 완화한다
- 테스트 커버리지 / 정적 분석 / 의존성 스캔 / parity 검증 조건이 프로젝트 성격에 맞게 정의되어 있다

### 2.7 드리프트와 오래된 자산

- 문서에 남은 오래된 파일 수, 스킬 수, 경로가 실제 파일과 다른 경우 WARN
- `seed.yaml`, bootstrap 전제, v2-team 사용 스크립트 같은 과거 생성 방식이 현재 Skill 기반 흐름에 섞여 있으면 WARN
- Skill owner 배정이 누락되었거나 존재하지 않는 에이전트를 참조하면 FAIL
- 생성 HTML 등 파생 문서가 없거나, Markdown 원본보다 오래되었거나, 내용이 불일치하면 WARN
- 단, `reviews/`, `docs/plans/`, `docs/specs/` 같은 이력성 문서는 과거 상태를 설명할 수 있으므로 historical mention 자체만으로 FAIL 처리하지 않는다

## 3. 판정 기준

### 3.0 판정 근거의 객관성 계층

준수 판정의 근거는 객관성 수준이 다른 3계층으로 구성되며, **진행 차단(FAIL·CVSS≥7.0)은 계층 1·2 근거만** 사용한다.

| 계층 | 근거 | 객관성 | 예 |
|------|------|--------|----|
| 1. 기계 검증 | 파일 존재·grep·CI 도구 수치·게이트 증적 | **객관** (재현 가능) | 스킬/에이전트/템플릿 개수, gitleaks 0건, ArchUnit/커버리지, 결재자+일자 |
| 2. 외부 독립 검증 | 타 벤더 LLM 교차검증 + **CVSS v3.1 공식 산식** | 준객관 (척도가 공인 표준) | cross-validation 결함 점수 |
| 3. AI 자가평가 | 7차원 점수 등 | 주관성 있음 (§4.9 `[sg-gw회고·조정가능]`) | 가독성·완성도 채점 — **참고 지표, 단독 차단 근거 아님**. 완화: Skill 5 독립 재평가(차이>10→PM)·인간 게이트 |

| 등급 | 의미 | 처리 |
|------|------|------|
| PASS | 필수 조건 충족 | 다음 단계 진행 가능 |
| WARN | 동작은 가능하지만 표준 드리프트 또는 수동 확인 필요 | 게이트 전 보완 권고 |
| FAIL | 필수 가드레인 또는 산출물 누락 | 해당 게이트 진입 차단 |

즉시 FAIL 조건:
- G1/G2/G3 필수 게이트 누락
- L1/L2/L3 중 해당 프로젝트에 필요한 보안 Hook 누락
- L2 AI 감사가 TODO 상태로 성공 처리됨
- CVSS >= 7.0 미해결 결함 존재
- 시크릿 스캔 우회가 결재 증적 없이 허용됨
- 운영/DB/삭제/force-push 같은 파괴적 작업 승인 정책 부재

## 4. 출력 리포트 형식

`reviews/standard-validation-{YYYYMMDD}.md` 형식으로 작성한다.

```markdown
# Standard Validation Report — {YYYY-MM-DD}

## Summary
| 항목 | 값 |
|------|----|
| 모드 | Package / Project |
| 총점 | __ / 100 |
| 판정 | PASS / WARN / FAIL |
| 차단 이슈 | N |
| 경고 이슈 | N |

## Score
| 차원 | 점수 | 근거 |
|------|------|------|
| 완성도 | __ / 20 | |
| 추적성 | __ / 15 | |
| 보안 | __ / 20 | |
| 성능/자동화 | __ / 10 | |
| 가독성/문서 | __ / 15 | |
| 표준 준수 | __ / 10 | |
| 테스트/검증 | __ / 10 | |

## Findings
| 등급 | ID | 위치 | 내용 | 권고 조치 |
|------|----|------|------|-----------|
| FAIL/WARN | VS-001 | path:line | 설명 | 조치 |

## Gate Decision
- G1 진입 가능: YES/NO/N-A
- G2 진입 가능: YES/NO/N-A
- G3 진입 가능: YES/NO/N-A

## Notes
- seed.yaml / bootstrap 방식은 현재 표준 하네스의 필수 흐름에서 제외한다.
- false positive가 있으면 근거와 함께 예외로 기록한다.
```

## 5. 실행 원칙

- 코드나 문서를 자동 수정하지 않는다. 수정은 별도 작업으로 분리한다.
- 발견 위치는 가능한 한 파일 경로와 라인 번호로 남긴다.
- Package mode에서는 표준 패키지의 자기 일관성을 본다.
- Project mode에서는 프로젝트 산출물과 게이트 통과 가능성을 본다.
- 검증 기준이 프로젝트 성격과 맞지 않으면 FAIL이 아니라 WARN으로 낮추고 PM 판단 항목으로 둔다.

## 6. 완료 기준 (DoD)

- [ ] `reviews/standard-validation-{YYYYMMDD}.md` 리포트 작성 (Summary / Score / Findings / Gate Decision 포함)
- [ ] 모든 Finding에 등급(FAIL/WARN/NOTE)·위치(파일:라인)·권고 조치 기재
- [ ] FAIL 0건 확인, 또는 FAIL별 게이트 차단 사유 명시
- [ ] WARN은 게이트 전 보완 권고로 분류, false positive는 근거와 함께 예외 기록
- [ ] 판정(PASS/WARN/FAIL)과 게이트 진입 가능 여부(G1/G2/G3 또는 N/A) 명시
- [ ] (수정이 필요한 항목은 본 스킬이 아니라 별도 보완 작업으로 분리 — §5 원칙)
