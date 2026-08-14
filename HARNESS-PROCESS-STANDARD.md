# IT 개발 프로젝트 표준 하네스 프로세스 (Harness Process Standard)

> **버전**: 1.5
> **작성일**: 2026-05-29
> **최근 갱신**: 2026-06-15
> **작성 근거**: Société Générale Gateway (sg-gw) C → Java/Spring Boot 3 마이그레이션 1개월 실증 + 첨부 표준 하네스 구성안
> **적용 대상**: 신규/리뉴얼 IT 개발 프로젝트 (백엔드·풀스택·마이그레이션·데이터)
> **협업 모델**: Team-with-Leader (본 문서 §4 에 정의)

---

## 0. 본 문서의 위치

본 문서는 **개발 표준 하네스 프로세스**의 마스터 정의서다. 한 달간 sg-gw 프로젝트에서 실증한 다음 자산을 8개 표준 스킬(6개 실행 스킬 + 1개 검증 스킬 + 1개 검증 보완 가이드)과 5단계 사용 흐름으로 정리했다.

| sg-gw 실증 자산 | 본 표준의 반영 위치 |
|----------------|--------------------|
| 16종 sub-agent (Phase 1~4 + 상시) | §4 에이전트 팀 구성 |
| 38건 ADR (아키텍처 결정 기록) | §5 코드 생성 필수 항목 / §6 SG Gateway 적용 사례 |
| Phase 게이트 3곳 (1→2 / 2→3 / 릴리즈) | §3 스킬 명세 / §4.5 사람 결재 게이트 |
| 디렉터리 1:1 권한 격리 | §4.4 작업 충돌 방지 |
| Claude → Codex → Claude 교차 검증 (CVSS 9.8 사전 차단) | §3 Skill 5 / §6 |
| 7차원 자체 평가 (90점 / 최대 5회 반복) | §3 Skill 4 / §4.6 |
| 보안 Hook 3단계 (L1 pre-commit / L2 CI / L3 prod-gate) | §5.4 |
| Parity 테스트 (바이트 단위 C↔Java 동치) + ArchUnit | §5.5 |
| 한국어 + 영문 보조 Javadoc 표준 | §5.6 |

---

## 1. 사용자 적용 흐름 — 5단계

본 표준 하네스를 신규 개발 프로젝트에 적용하는 표준 5단계.

```
[1] 하네스 프로젝트 다운로드 (PC)
        │
        │  Git clone 또는 zip 다운로드
        ▼
[2] 개발 프로젝트 디렉터리로 복사
        │
        │  .claude/ / templates/ / hooks/ / scripts/ 등 일괄 복사
        ▼
[3] 개발 프로젝트에서 AI 도구 실행
        │
        │  Claude Code · Codex · Cursor 등
        ▼
[4] 분석·설계 단계 — 스킬 ① ② ③ 순차 실행
        │
        │  ① 프로젝트 기획서 작성
        │  ② 요구사항 정의서 작성 (1:1 대화)
        │  ③ 개발계획서 + 테스트계획서 작성
        ▼
[5] 구현·QA·산출 단계 — 스킬 ④ ⑤ ⑥ 순차 실행
        │
        │  ④ 프로젝트 구현 (에이전트 팀 자율 + 자체 평가 루프)
        │  ⑤ 품질 / 코드 리뷰 (QA 팀 + Codex 교차 검증)
        │  ⑥ 최종 산출물 작성 (md / ppt / pdf / hwp / word / image)
        ▼
[프로젝트 완료] PM 최종 결재 → 릴리즈
```

### 1.1 단계별 소요 시간 (sg-gw 기준 추정)

| 단계 | 소요 (소규모) | 소요 (중·대규모) | 비고 |
|------|--------------|------------------|------|
| ①+②+③ (분석·설계) | 1~2 일 | 3~5 일 | 1:1 대화 회차에 좌우 |
| ④ (구현) | 2~4 주 | 1~3 개월 | sprint 기준 |
| ⑤ (QA / 리뷰) | 3~5 일 | 1~2 주 | 교차 검증 회차 포함 |
| ⑥ (산출물) | 0.5~1 일 | 1~3 일 | 형식 종류·분량에 비례 |

---

## 2. 하네스 프로젝트 패키지 구성

사용자가 `[1]` 단계에서 다운로드받을 패키지의 표준 디렉터리 구조.

```
harness-standards/                          # 본 패키지의 루트
├── HARNESS-PROCESS-STANDARD.md             # 본 문서 (마스터 정의서)
├── PACKAGE-INDEX.md                        # 전체 자산 카탈로그
├── README.md                               # 진입점 + 적용 가이드
├── .claude/                                # AI 도구 자산 (다운로드 시 복사 대상)
│   ├── skills/                             # 8개 표준 스킬 (6 실행 + 1 검증 + 1 어시스턴트)
│   │   ├── 01-plan-project/
│   │   │   └── SKILL.md
│   │   ├── 02-define-requirements/
│   │   │   └── SKILL.md
│   │   ├── 03-draft-dev-plan/
│   │   │   └── SKILL.md
│   │   ├── 04-implement/
│   │   │   └── SKILL.md
│   │   ├── 05-quality-review/
│   │   │   └── SKILL.md
│   │   ├── 06-finalize-deliverables/
│   │   │   └── SKILL.md
│   │   ├── 07-validate-standard/
│   │   │   └── SKILL.md
│   │   └── 08-harness/
│   │       └── SKILL.md
│   └── agents/                             # 팀 에이전트 정의 (13종)
│       ├── _team-leader.md
│       ├── legacy-analyst.md
│       ├── doc-spec-parser.md
│       ├── data-model-designer.md
│       ├── architect.md
│       ├── backend-developer.md
│       ├── frontend-developer.md           # 웹 퍼블리싱 / UI / 접근성
│       ├── adapter-builder.md
│       ├── code-reviewer.md
│       ├── qa-engineer.md
│       ├── security-auditor.md
│       ├── docs-writer.md
│       └── trace-mapper.md
├── templates/                              # 산출물 템플릿
│   ├── planning/
│   │   ├── PROJECT-PROPOSAL.template.md    # 기획서 템플릿
│   │   └── BUSINESS-REQUIREMENTS.template.md
│   ├── requirements/
│   │   ├── REQUIREMENTS-SPEC.template.md   # 요구사항 정의서
│   │   └── USE-CASE.template.md
│   ├── design/
│   │   ├── DEV-PLAN.template.md            # 개발계획서
│   │   ├── TEST-PLAN.template.md           # 테스트계획서
│   │   └── ADR.template.md                 # ADR 양식
│   ├── implementation/
│   │   ├── SPRINT-LOG.template.md
│   │   └── TRACE-CSV.template.csv          # C↔Java 또는 요구사항↔코드 매핑
│   ├── qa/
│   │   ├── REVIEW-REPORT.template.md
│   │   ├── CROSS-VALIDATION.template.md    # Codex 교차 검증 리포트
│   │   └── SECURITY-AUDIT.template.md
│   └── deliverables/
│       ├── EXEC-SUMMARY.template.md
│       ├── RUNBOOK.template.md
│       └── ARCHITECTURE-OVERVIEW.template.md
├── hooks/                                  # 보안 Hook 3단계 + 공통 룰
│   ├── pre-commit-gitleaks.sh              # L1
│   ├── gitleaks.toml                       # L1/L2 기본 룰셋
│   ├── ci-security-auditor.yml             # L2 (GitHub Actions / GitLab CI)
│   └── prod-gate-checklist.md              # L3
└── scripts/                                # 운영 보조 스크립트
    ├── parity-check.sh                     # C↔Java 또는 v1↔v2 동치 검증
    └── generate-codecs.sh                  # 스키마 기반 코드 생성 (선택)
```

### 2.1 다운로드 → 복사 명령 예시

```bash
# [1] 다운로드 (Git 채택 시)
git clone <repo> ~/Downloads/harness-standards

# [2] 신규 개발 프로젝트에 복사
PROJ=~/workspaces/project/my-new-project
mkdir -p $PROJ/docs
cp -r ~/Downloads/harness-standards/.claude $PROJ/
cp ~/Downloads/harness-standards/HARNESS-PROCESS-STANDARD.md $PROJ/docs/
cp -r ~/Downloads/harness-standards/templates $PROJ/
cp -r ~/Downloads/harness-standards/hooks $PROJ/
cp -r ~/Downloads/harness-standards/scripts $PROJ/

# [3] git 초기화 + L1 Hook 설치
cd $PROJ
git init -b main
cp hooks/pre-commit-gitleaks.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# [4] AI 도구 실행 (개발 프로젝트 디렉터리에서)
# 필요 시 /08-harness 부터 시작
# Claude Code · Codex · Cursor 중 택일
```

---

## 3. 8 표준 스킬 명세 (6 실행 + 1 검증 + 1 어시스턴트)

각 스킬은 `.claude/skills/<id>/SKILL.md` 양식 (Claude Code Skill spec) 을 따른다. Codex·Cursor 등 다른 AI 도구에서도 동일 SKILL.md 를 시스템 프롬프트로 주입하여 사용 가능.

각 Skill 실행 시에는 `lead_agent`가 단일 책임자로 동작하고, `support_agents`가 전문 영역 검토를 제공한다. Lead는 PM과의 대화, 산출물 통합, 게이트 진입 가능 여부 판단을 책임진다. Support는 자신의 write scope 안에서 증거와 검토 결과를 남긴 뒤 Lead에게 보고한다.

### 3.0 Skill Owner 배정 표준

| Skill | Lead Agent | Support Agents |
|-------|------------|----------------|
| `01-plan-project` | `docs-writer` | `doc-spec-parser`, `security-auditor`, `trace-mapper` |
| `02-define-requirements` | `trace-mapper` | `doc-spec-parser`, `docs-writer`, `security-auditor` |
| `03-draft-dev-plan` | `architect` | `data-model-designer`, `qa-engineer`, `security-auditor`, `trace-mapper` |
| `04-implement` | `team-leader` | `backend-developer`, `frontend-developer`, `adapter-builder`, `qa-engineer`, `code-reviewer`, `security-auditor`, `trace-mapper` |
| `05-quality-review` | `security-auditor` | `code-reviewer`, `qa-engineer`, `trace-mapper` |
| `06-finalize-deliverables` | `docs-writer` | `architect`, `qa-engineer`, `security-auditor`, `trace-mapper` |
| `07-validate-standard` | `code-reviewer` | `security-auditor`, `trace-mapper`, `docs-writer` |
| `08-harness` | `team-leader` | `code-reviewer`, `docs-writer` |

프로젝트 특성상 표준 에이전트로 커버되지 않는 역할이 있으면 PM 결재 후 프로젝트 로컬 `.claude/agents/`에 전용 에이전트를 추가할 수 있다. 단, `write_dirs`, `phase`, `recommended_llm`, 담당 Skill을 frontmatter에 반드시 명시하고, 기존 에이전트와 write scope가 겹치면 `07-validate-standard`에서 WARN 이상으로 기록한다.

### 3.1 Skill 1 — 프로젝트 기획서 작성

**Skill ID**: `01-plan-project`
**활성 조건 (when_to_use)**: 신규 프로젝트 시작 시점, 기획자 부재 또는 기획서 보완 필요
**소요**: 2~6 시간 (1:1 대화 회차에 좌우)

**입력**
- (선택) 기존 기획서 / 화면 시안 / 업무 요구 텍스트
- (선택) 발주처·이해관계자 인터뷰 노트

**1:1 대화 흐름 (기획자 부재 시 표준 12 문항)**

| # | 질문 | 결정 항목 |
|---|------|-----------|
| 1 | 프로젝트 1줄 정의 | 비전 / 미션 |
| 2 | 비즈니스 문제 (왜 지금 하는가?) | 핵심 가치 제안 |
| 3 | 주 사용자 / 페르소나 | 1차 / 2차 사용자 |
| 4 | 핵심 시나리오 (TOP 3 use case) | MVP 범위 |
| 5 | 화면 / 메뉴 구성 (있으면 첨부) | UI 범주 |
| 6 | 외부 연동 (있으면 나열) | 인터페이스 명세 사전 식별 |
| 7 | 비기능 요구 (성능·보안·규제) | NFR baseline |
| 8 | 일정 / 예산 / 인력 제약 | 스코프 조정 근거 |
| 9 | 성공 지표 (KPI) | 측정 방법 |
| 10 | 위험 / 가정 | 사전 식별 위험 목록 |
| 11 | 적용 기술 스택 선호 | 후속 ADR 입력 |
| 12 | 결재 / 거버넌스 구조 | 사람 결재 게이트 매핑 |

> **원칙**: 12문항을 한 번에 던지지 말고, **1문항씩 1:1로 묻고 답을 정리한 후 다음 문항** 진행. AI 는 답변을 기반으로 다음 문항을 동적으로 보완·생략 가능하다.

**출력 산출물**
- `docs/planning/PROJECT-PROPOSAL.md` — 12 문항 답변 기반 기획서 본문
- `docs/planning/BUSINESS-REQUIREMENTS.md` — 비즈니스 요구사항 (수기 또는 본 스킬 자동 생성)
- `docs/planning/stakeholder-map.md` — 이해관계자 맵 (선택)

**완료 기준 (DoD)**
- [ ] 12 문항 모두 답변 또는 명시적 `[보류]` 표시
- [ ] PM (인간) 1차 확인
- [ ] Skill 2 진입 가능 상태

---

### 3.2 Skill 2 — AI 요구사항 정의서 작성 (1:1 대화 구체화)

**Skill ID**: `02-define-requirements`
**활성 조건**: Skill 1 완료 (PROJECT-PROPOSAL.md 존재)
**소요**: 4~12 시간

**동작 절차**
1. **분석 단계** — Skill 1 산출물 자동 파싱 → 명세 공백·모순 식별
2. **1:1 대화** — 식별된 공백을 PM·기획자에 순차 질의
3. **구체화** — Functional / Non-Functional / Constraint 3 분류로 정리
4. **추적성 매핑** — REQ-ID 부여 (예: `FR-001`, `NFR-AUTH-01`)

**출력 산출물**
- `docs/requirements/REQUIREMENTS-SPEC.md` — 요구사항 정의서 본문
- `docs/requirements/use-cases/*.md` — Use case 명세 (시나리오별)
- `docs/requirements/requirements-matrix.csv` — REQ-ID × 출처 × 우선순위 × 추적 매트릭스
- `docs/requirements/questions-log.md` — `[UNKNOWN]` / `[AMBIGUOUS]` 항목 + PM 회신 이력

**구체화 강제 룰**
- 모호한 요구는 반드시 `[AMBIGUOUS]` + 후보안 ≥ 2 + PM 회신 요청
- 측정 불가능한 NFR (예: "빠르게") 는 SLA 수치로 환산 (P95 응답 < 500 ms 등)
- 모든 요구는 1 use case 이상에 매핑되어야 함 (orphan 금지)

**완료 기준 (DoD)**
- [ ] REQ-ID 부여 완료 / orphan 0 건
- [ ] `[AMBIGUOUS]` 항목 모두 PM 회신 완료 또는 명시적 보류
- [ ] PM 결재: **분석 게이트 통과**

---

### 3.3 Skill 3 — 개발계획서 + 테스트계획서 작성

**Skill ID**: `03-draft-dev-plan`
**활성 조건**: Skill 2 완료 (REQUIREMENTS-SPEC.md 결재 완료)
**소요**: 1~3 일

**동작 절차**
1. **요구사항 분해** → Epic / Sprint / Task 트리
2. **기술 스택 결정** → 후보 ≥ 2 + 선정 근거 → **ADR-001** 자동 생성
3. **아키텍처 초안** → 컴포넌트 다이어그램 + DB 스키마 후보
4. **테스트 전략** → 단위 / 통합 / E2E / Parity (마이그레이션 시) 계획
5. **인력 / 일정 / 위험** → Sprint plan + 위험 등록부

**출력 산출물**
- `docs/design/DEV-PLAN.md` — 개발계획서 (스택 / 일정 / 인력 / 위험)
- `docs/design/TEST-PLAN.md` — 테스트계획서 (전략 / 커버리지 목표 / 도구)
- `docs/design/architecture-overview.md` — 아키텍처 초안 (Mermaid 다이어그램 포함)
- `docs/design/adr/ADR-001-tech-stack.md` — 첫 ADR (이후 ADR 누적 시작점)
- `docs/design/risk-register.md` — 위험 등록부 ≥ 10 건

**테스트 계획서 표준 구성**
- 단위 테스트: 커버리지 목표 (예: 라인 ≥ 80%, 브랜치 ≥ 70%)
- 통합 테스트: 외부 의존 (DB / MQ / API) Mock 정책
- 7 차원 자체 평가 임계치 (기본 90 점)
- Parity 테스트 (마이그레이션 프로젝트 한정): 바이트 단위 동치
- 보안 테스트: OWASP / 정적 분석 / Secret 스캔

**완료 기준 (DoD)**
- [ ] DEV-PLAN.md / TEST-PLAN.md PM 결재 완료
- [ ] ADR ≥ 1 건 등록 (스택 결정)
- [ ] Sprint 1 task list 확정 (PM·Leader 합의)
- [ ] PM 결재: **설계 게이트 통과**

---

### 3.4 Skill 4 — 프로젝트 구현 (자율 에이전트 팀 + 자체 평가 루프)

**Skill ID**: `04-implement`
**활성 조건**: Skill 3 완료 (DEV-PLAN.md / TEST-PLAN.md 결재 완료)
**소요**: Sprint 단위 반복 (1 sprint = 1~2 주 권장)

**동작 절차**

```
Sprint 시작
  │
  ▼
[A] 스켈레톤 생성 (1회만)
  │   - 디렉터리 트리 / 빌드 스크립트 / CI 파이프라인
  │   - 1:1 디렉터리 권한 매핑 (에이전트당 독립 Write 디렉터리)
  │
  ▼
[B] Task dispatch (Team Leader 에이전트가 수행)
  │   - DAG 분석 → 병렬 가능 task 식별
  │   - 적절한 LLM 모델 선정 (§4.3 참조)
  │   - 충돌 없는 디렉터리 할당
  │
  ▼
[C] 에이전트 자율 구현
  │   - 구현 + 단위 테스트 + // source: 주석 + ADR (변경 시)
  │   - 결과를 TEAM_CHANNEL 에 보고
  │
  ▼
[D] QA 에이전트 자동 테스트
  │   - 테스트 계획서 기준 단위/통합 테스트 실행
  │   - 결과 피드백 → 에이전트 자가 수정 루프
  │
  ▼
[E] 7차원 자체 평가 (Leader 수행)
  │   - 완성도 20 / 추적성 15 / 보안 20 / 성능 10 / 가독성 15 / 표준준수 10 / 테스트커버리지 10
  │   - 90 점 미만 → 최저 차원 집중 보완 → 재생성 (최대 5회)
  │   - 5회 후도 미달 → PM Escalation
  │
  ▼
[F] Sprint 종료
      - Leader 가 통합 보고 → PM 결재 → 다음 Sprint or Skill 5 진입
```

**필수 강제 항목** (§5 코드 생성 필수 항목 참조)

| 항목 | 내용 | 검증 도구 |
|------|------|---------|
| `// source:` 주석 | 마이그레이션 시 레거시 출처 명시 | code-reviewer 수동 + grep |
| ADR 작성 | 설계 변경 시 의무 | code-reviewer 검토 |
| 한국어+영문 Javadoc | 클래스/메서드 표준 | code-reviewer |
| 금액 BigDecimal | `double`/`float` 금지 | ArchUnit / Checkstyle |
| PII 마스킹 | 실데이터 평문 로그 금지 | security-auditor |
| 시크릿 하드코딩 0 | API 키 / 비밀번호 코드 외부화 | L1 Hook (gitleaks) |
| Conventional Commits | `feat:`/`fix:`/`docs:` + 팀 ID scope | commit hook |

**출력 산출물**
- `src/` — 구현 코드
- `tests/` — 테스트 코드 (단위 / 통합 / E2E)
- `docs/design/adr/ADR-NNN-*.md` — Sprint 중 발생한 ADR
- `docs/sprints/SPRINT-N-LOG.md` — Sprint 로그 (Leader 작성)
- `mapping/trace/c2j.csv` 또는 `requirements-trace.csv` — 추적 매트릭스 갱신

**완료 기준 (DoD)**
- [ ] Sprint task 100% 완료 또는 명시적 이월
- [ ] 7 차원 자체 평가 ≥ 90 점
- [ ] CI 빌드 + 테스트 PASS
- [ ] code-reviewer 판정: APPROVE
- [ ] security-auditor 판정: 승인 또는 조건부승인
- [ ] PM 결재: **Sprint 게이트 통과**

---

### 3.5 Skill 5 — 품질 / 코드 리뷰 (QA 팀 + 교차 검증)

**Skill ID**: `05-quality-review`
**활성 조건**: Skill 4 의 Sprint 완료 또는 릴리즈 직전
**소요**: 3~10 일

**동작 절차**

```
[A] QA 에이전트팀 품질 테스트
    │   - 회귀 테스트 / 부하 테스트 / 보안 테스트
    │   - Parity 테스트 (마이그레이션 프로젝트)
    │
[B] code-reviewer 에이전트 정적 리뷰
    │   - 네이밍 / 스레드 안전성 / 거래 무결성 / Javadoc 준수
    │
[C] security-auditor 에이전트 보안 감사
    │   - OWASP / PII / 시크릿 / 감사로그 / 규제 (전자금융감독규정 등)
    │
[D] 교차 검증 (Cross-Validation) — 핵심 차별점
    │   - Claude 가 작성한 코드를 Codex (또는 다른 LLM) 가 독립 리뷰
    │   - 발견 결함을 CVSS v3.1 로 점수화
    │   - CVSS ≥ 7.0 (HIGH/CRITICAL) → 즉시 차단
    │   - sg-gw 실증: CVSS 9.8 (K-01) ~ 6.5 (K-04) 4건 사전 차단
    │
[E] 보안 Hook 3단계 통합
    │   - L1 git pre-commit (gitleaks 등) — 개발자 PC 단계
    │   - L2 CI security-auditor — PR / push 단계
    │   - L3 prod-gate — 운영 배포 직전 결재
    │
[F] 종합 리포트 작성 → PM 결재
```

**출력 산출물**
- `reviews/code-review-sprint-N.md` — 코드 리뷰 리포트
- `reviews/cross-validation-N.md` — Codex ↔ Claude 교차 검증 리포트 (CVSS 점수 포함)
- `security/audit-N.md` — 보안 감사 리포트
- `qa/test-report-N.md` — QA 테스트 결과
- `parity/parity-report-N.md` — Parity 테스트 결과 (해당 시)

**판정 등급**
- **APPROVE**: 모든 게이트 통과
- **CONDITIONAL APPROVE**: HIGH 미만 결함 + 이행 계획 합의
- **REJECT**: CRITICAL 또는 HIGH 결함 존재 → Skill 4 로 되돌림

**완료 기준 (DoD)**
- [ ] CVSS ≥ 7.0 결함 0 건 또는 리스크 수용 결재 완료
- [ ] 7 차원 자체 평가 ≥ 90 점 (최종 평가)
- [ ] PM 결재: **릴리즈 게이트 통과**

---

### 3.6 Skill 6 — 최종 산출물 작성

**Skill ID**: `06-finalize-deliverables`
**활성 조건**: Skill 5 결재 완료
**소요**: 1~3 일

**동작 절차**
1. **1:1 대화로 산출물 목록 확정**
   - 사람이 산출물을 명시 → AI 가 빠진 항목 제안
   - 산출물별 **형식 지정** (md / pdf / pptx / hwp / docx / png / xlsx)
2. **단계별 산출물 자동 조립**
   - 분석: 기획서 / 요구사항 정의서 / 위험 등록부
   - 설계: 개발계획서 / 테스트계획서 / ADR 목록 / 아키텍처 다이어그램
   - 구현: 소스 트리 / 추적 매트릭스 / sprint 로그 통합본
   - 검증: QA 리포트 / 코드 리뷰 / 보안 감사 / 교차 검증 / parity
3. **포맷 변환**
   - md → pdf : `weasyprint` 또는 `pandoc`
   - md → pptx : `python-pptx`
   - md → docx/hwp : `pandoc` (hwp 는 한글 변환 도구 별도)
   - 이미지 합성 : `Pillow` (스크린샷·다이어그램)
4. **가독성 검토**
   - 사람이 쉽게 이해할 수 있도록 용어집 (glossary) 첨부
   - 임원용·기술자용 등 대상별 분리 (필요 시)

**표준 산출물 목록 (체크리스트)**

| 분류 | 산출물 | 기본 형식 | 대상 |
|------|--------|----------|------|
| **기획** | 프로젝트 기획서 | md + pdf | PM / 발주처 |
| **기획** | 비즈니스 요구사항 | md | PM |
| **분석** | 요구사항 정의서 | md + pdf | 전 인력 |
| **분석** | Use Case 명세 | md | 개발 / QA |
| **설계** | 개발계획서 | md + pdf | PM / Leader |
| **설계** | 테스트계획서 | md + pdf | QA |
| **설계** | ADR 모음 | md | 아키텍트 |
| **설계** | 아키텍처 개요 | md + png (Mermaid) | 임원 / 신규 인력 |
| **구현** | 추적 매트릭스 | csv + md | 감사 |
| **구현** | API 명세 | md (OpenAPI) | 연동 팀 |
| **검증** | QA 리포트 | md + pdf | PM |
| **검증** | 코드 리뷰 리포트 | md | 개발 |
| **검증** | 보안 감사 리포트 | md + pdf | 정보보호 |
| **검증** | 교차 검증 (CVSS) | md | 임원 / 정보보호 |
| **운영** | Runbook | md | 운영팀 |
| **운영** | 컷오버 / 롤백 절차서 | md | 운영팀 |
| **임원 보고** | 임원 보고 (요약 + 디테일) | pptx + pdf | 임원 |

**출력 산출물**
- `deliverables/` 디렉터리에 모든 최종본 일괄 배치
- `deliverables/INDEX.md` — 산출물 카탈로그 (이름 / 형식 / 대상 / 작성일 / 결재자)

**완료 기준 (DoD)**
- [ ] INDEX.md 와 실제 파일 1:1 일치
- [ ] PM 1:1 대화로 산출물 목록·형식 확정
- [ ] 임원 보고 자료 사람 발표자 리허설 완료 (해당 시)

---

### 3.7 Skill 7 — 표준 준수 검증

**Skill ID**: `07-validate-standard`

**활성 조건**: 하네스 변경 직후, 신규 프로젝트 setup 직후, G1/G2/G3 게이트 직전, PM 감사 요청 시

**성격**: 선형 개발 단계가 아닌 반복 검증 스킬

**동작 절차**
1. **모드 판별** — 표준 패키지 루트인지, 실제 프로젝트 루트인지 식별
2. **인벤토리 검증** — 8 스킬 / 13 에이전트 / 21 템플릿 / 3 Hook / 2 Script 존재 확인
3. **게이트 검증** — G1 / G2 / G3 결재자, 필수 산출물, DoD 확인
4. **가드레인 검증** — L1 우회 결재 증적, L2 AI 감사 TODO 통과 여부, L3 prod gate 확인
5. **Team-with-Leader 검증** — Leader 책임, write_dirs, 충돌 방지, 통신 로그 규칙 확인
6. **추적성 검증** — REQ-ID, `// source:` / `// req:`, ADR, trace matrix 확인
7. **리포트 작성** — PASS / WARN / FAIL 및 게이트 진입 가능 여부 기록

**출력 산출물**
- `reviews/standard-validation-{YYYYMMDD}.md` — 표준 준수 검증 리포트

**완료 기준 (DoD)**
- [ ] FAIL 항목 0건 또는 명시적 PM 리스크 수용
- [ ] WARN 항목의 담당자 / 기한 / 후속 조치 기록
- [ ] 게이트 진입 가능 여부 명시
- [ ] seed.yaml / bootstrap 기반 과거 흐름이 필수 조건으로 남아 있지 않음

> `seed.yaml.example` 기반 초기 스켈레톤 생성 방식은 Skill 기반 전환 이후 표준 필수 흐름에서 제외한다. 필요한 경우 legacy 참고 자료로만 관리한다.

---

### 3.8 Skill 8 — Harness Assistant (검증 보완 가이드)

**Skill ID**: `08-harness`
**활성 조건 (when_to_use)**: 07 표준 검증 직후 보완할 내용을 가이드받을 때(주), 또는 진행 중 프로젝트의 다음 행동 안내가 필요할 때(부).
**모드**: ① 감사 — `/07` 실행 → 점수화 → 우선순위 보완 가이드 → 대화형 적용 → 재검증. ② 신규 프로젝트 — `docs/.harness-state.yaml` 상태파일과 실제 산출물을 교차 확인해 01~06 진입조건·게이트(G1~G3)를 검사하고 다음 행동 1개를 안내.
**원칙**: 기존 스킬 무변경 재사용(얇은 디스패처). 게이트 결재 기록은 사람 전용(§4.8 CQ3). 파일이 진실, 상태는 힌트.
**출력**: `docs/.harness-state.yaml` (신규 모드) / `reviews/standard-validation-{YYYYMMDD}.md` (감사 모드, /07 경유)
**상세**: `.claude/skills/08-harness/SKILL.md` · 설계서 `docs/specs/2026-06-12-harness-assistant-design.md`

---

## 4. 에이전트 팀 구성 표준 (Team-with-Leader 모델)

본 표준은 **Team-with-Leader** 협업 모델을 채택한다. 핵심 원칙은 본 절에서 자체 완결적으로 정의한다.

### 4.0 협업 모델 핵심 명제

> "팀 자율 협업으로 생산성을 높이되, Leader 단일 창구를 통해 추적성과 책임을 보장한다."

- 에이전트를 **팀 단위 (2~5명)** 로 묶어 **팀 내부 자율** 통신을 허용
- 각 팀에 **Leader 1명** 임명 → PM 에 단일 보고 창구
- PM (인간) 1명 — 팀 간 병렬만 관리, 팀 내부 조율은 Leader 위임
- 통신 3 모드: **TEAM_CHANNEL** (공유 로그) / **DIRECT** (Peer-to-Peer) / **LEADER_BROADCAST** (Leader → 전팀원)

### 4.1 팀 구성 원칙

- **팀 크기**: 2~5 에이전트 (6 이상은 부팀으로 분리)
- **팀당 Leader 1 명**: 팀 결과의 단일 보고 창구 (PM 에 통합 보고)
- **PM 1 명**: 인간. 팀 간 병렬만 관리, 팀 내부 조율은 Leader 에 위임

### 4.2 표준 팀 편성 (예시 — sg-gw 적용 모델)

| 팀 | 구성 에이전트 | Leader | 주 책임 영역 |
|----|--------------|--------|-------------|
| **Analysis Team** | legacy-analyst, doc-spec-parser, protocol-decoder, data-model-designer | data-model-designer | 레거시 분석 / 문서 파싱 / 도메인 모델 |
| **Design Team** | architect, data-model-designer, db-migration-engineer | architect | 아키텍처 / ADR / DB |
| **Build Team** | backend-developer, frontend-developer, adapter-builder, code-reviewer | code-reviewer | 구현(백엔드·프론트엔드) + 즉시 리뷰 |
| **Validation Team** | qa-engineer, security-auditor, code-reviewer | security-auditor | QA / 보안 / 교차 검증 |
| **Ops Team** | shell-ops-porter, docs-writer, db-migration-engineer | docs-writer | 운영 자산 / 문서 |

### 4.3 LLM 모델 구분 — 업무별 모델 선정

| 업무 유형 | 권장 모델 | 근거 |
|----------|----------|------|
| 아키텍처 설계 / ADR / 보안 감사 | **고급 모델** (Opus / GPT-5 / Gemini Ultra) | 추론 깊이 필수 |
| 정적 분석 / 콜그래프 / 패턴 식별 | **고급 모델** | 광범위 컨텍스트 처리 |
| 1:1 포팅 / 단순 CRUD / 보일러플레이트 | **표준 모델** (Sonnet / GPT-5 mini / Gemini Pro) | 비용 효율 |
| 문서 정리 / 산출물 조립 / 포맷 변환 | **경량 모델** (Haiku / GPT-5 nano) | 단순 변환 작업 |
| **교차 검증** (Cross-Validation) | **다른 벤더** (Claude 작성 → Codex 검토) | 모델 편향 회피 |

**원칙**: 에이전트 정의에 `model:` 메타 필드 또는 `recommended_llm:` 명시. PM 이 비용·품질 trade-off 평가 후 변경 가능.

### 4.4 작업 충돌 방지 — 1:1 디렉터리 권한 격리

각 에이전트는 **자신의 Write 디렉터리 1개만** 수정 가능. 그 외는 Read-only.

| 에이전트 | Write 권한 디렉터리 (예시) |
|---------|---------------------------|
| legacy-analyst | `mapping/analysis/` |
| doc-spec-parser | `doc/parsed/` |
| architect | `mapping/architecture/`, `docs/design/adr/` |
| backend-developer | `src/`, `mapping/port-log/` |
| frontend-developer | `src/main/frontend/`, `web/` |
| code-reviewer | `reviews/` |
| security-auditor | `security/` |
| qa-engineer | `qa/`, `parity/` |
| docs-writer | `docs/`, `deliverables/` |
| trace-mapper | `mapping/trace/` |

**충돌 방지 보조 룰**
- 동일 파일 동시 수정 금지 (Leader 가 task dispatch 시 파일 단위 잠금)
- **중첩 소유 허용 (의도적)**: 상위 `docs/`(docs-writer)·`src/main/`(backend-developer)는 일반 산출물 소유, 하위 전문 디렉터리(`docs/design/`=architect, `docs/requirements/`=trace-mapper, `docs/sprints/`=team-leader, `src/main/.../adapter|codec/`=adapter-builder)는 각 전문 에이전트가 소유한다. 같은 **파일**을 두 에이전트가 쓰지 않으면 충돌이 아니다.
- Git 커밋은 **에이전트별 브랜치** 권장 (또는 worktree)
- Conventional Commits + 팀 ID scope (`feat(build-team): ...`)

### 4.5 사람 결재 게이트 3 곳 (필수)

본 표준은 인간 결재 3 게이트를 **의무화**한다. AI 자율 진행이라도 다음 시점은 인간 결재 없이는 진입 불가.

| # | 게이트 | 대상 산출물 | 결재자 |
|---|--------|------------|--------|
| **G1** | 분석 게이트 (Skill 2 → 3) | REQUIREMENTS-SPEC.md | PM |
| **G2** | 설계 게이트 (Skill 3 → 4) | DEV-PLAN.md / TEST-PLAN.md / ADR-001 | PM + 아키텍트 |
| **G3** | 릴리즈 게이트 (Skill 5 → 6) | 모든 검증 리포트 | PM + 정보보호 + 운영 |

Sprint 단위 결재 (Skill 4 내부) 는 Leader 자율로 처리 가능하되, **위 3 게이트는 사람 결재 우회 금지**.

### 4.6 7차원 자체 평가 (sg-gw 검증)

Leader 에이전트가 매 Sprint 종료 시 산출물을 다음 7차원으로 자가 채점한다.

> **근거**: 아래 가중치·임계 90·5회 반복은 `[근거:sg-gw회고·조정가능]` — 외부 검증 없는 자체안(§4.9). 프로젝트별 조정 가능하며 조정 시 ADR/회고에 기록한다.

| 차원 | 가중치 | 평가 기준 |
|------|-------|----------|
| **완성도** | 20% | 요구사항/Seed 의 모든 필드 반영 |
| **추적성** | 15% | `// source:` 주석 · ADR 링크 · 에이전트명 표기 |
| **보안** | 20% | 하드코딩 0 · PII 마스킹 · 보안 Hook 3 단계 |
| **성능** | 10% | 병렬 DAG · 병목 식별 · SLA 목표 반영 |
| **가독성** | 15% | Javadoc 표준 · Mermaid · 표 정렬 |
| **표준 준수** | 10% | 디렉터리 격리 위반 0 · ADR ≥ 10 건 |
| **테스트 커버리지** | 10% | 테스트 케이스 수 · DoD 기준 |

**루프 규칙**
- 임계치: **90 점 / 100**
- 미달 시: **최저 차원 집중 보완 → 재생성**
- 최대: **5 회 반복**
- 5 회 후 미달 → **PM Escalation** (인간 개입)

> 효과: 90 점 이상 자가 통과분만 PM 검토 단계로 진입 → 개발자 개입 최소화.

### 4.7 에이전트 동작 알람 항목

다음 시점에는 PM·Leader 에게 **자동 알람** 발송.

| 시점 | 알람 채널 |
|------|----------|
| Sprint 시작 / 종료 | TEAM_CHANNEL |
| Task 완료 | TEAM_CHANNEL |
| 7 차원 평가 < 90 | LEADER_BROADCAST |
| 5 회 재시도 후 미달 | PM Escalation (외부 채널 — Slack / Email) |
| Security Hook L2 차단 | PM + security-auditor |
| CRITICAL ADR 작성 | PM + 아키텍트 |
| 파괴적 작업 시도 (DB DROP / 운영 배포 등) | PM (반드시 인간 결재) |
| Sprint 종료 통합 보고 | PM + 발주처 (해당 시) |

### 4.8 사람 질의 생성 기준 (Human-Query Criteria)

AI 가 사람(PM/결재자)에게 던지는 **모든 질문은 다음 세 기준 중 하나 이상**에 해당할 때만 생성한다. 어디에도 해당하지 않으면 묻지 않는다 (**불필요 질의 금지**).

| 코드 | 기준 | 설명 | 주 적용 |
|------|------|------|---------|
| **CQ1** | 후속 산출물 필수 입력 | 답이 후속 스킬/산출물의 **필수 입력**을 채운다 | 01 12문항, 06 7문항 (고정 체크리스트) |
| **CQ2** | 모호·모순 해소 | 측정 불가·orphan·충돌 등 **결함 신호가 있을 때만** | 02 `[AMBIGUOUS]`, 충돌 요구 |
| **CQ3** | 사람 권한 필요 | **AI 단독 결정 금지** 지점 | 게이트 G1~G3, 스택 tie-break(차이<10%), 파괴적 작업, 리스크 수용 |

**대화 방식 (공통)**: ① 1문항씩 순차 ② 선폐쇄형(예/아니오) → 후개방형(어떻게/왜) ③ 모호하면 후보 ≥ 2 제시 ④ 즉답 강요 금지(지연 시 `[보류]`) ⑤ 도메인 트리거(금융 키워드 등) 등장 시 해당 점검 질문 자동 추가.

**고정 질문 세트의 한계와 가감 규칙**: 01/06 의 고정 문항은 sg-gw 회고 기반 큐레이션이다(외부 요구공학 표준 미도출, 검증 표본 1). 새 도메인 적용 시 위 **CQ1~CQ3 기준으로 문항을 가감**하고, 가감 이력을 ADR 또는 Sprint 회고에 남긴다.

### 4.9 근거 표기 범례 (Basis Tags)

본 표준의 규정값(임계·가중치·최소치·목록)은 다음 태그로 근거를 표기한다.

| 태그 | 의미 |
|------|------|
| `[근거:외부표준]` | 업계 표준/규격에 직접 근거 (CVSS v3.1 · OWASP · MoSCoW · ISO 25010 등) |
| `[근거:업계관례]` | 널리 통용되는 기본값 (예: 커버리지 라인 80% / 브랜치 70%) — 프로젝트 조정 가능 |
| `[근거:sg-gw회고·조정가능]` | 외부 검증 없는 **자체안(검증 표본 1)**. 프로젝트별 조정 가능, **조정 시 ADR/회고 기록** |

> 태그가 없는 분류·절차는 산출물/결정으로의 **내부 추적**(예: 01 '결정 항목', 02 출처·검증방법)으로 근거를 갖는다.

---

## 5. 코드 생성 필수 항목

Skill 4 (구현) 단계에서 **코드 생성 시 반드시 강제**되는 항목.

### 5.1 추적성

- 마이그레이션 프로젝트: `// source: <원본 경로>:<라인>` 주석 의무
- Greenfield 프로젝트: `// req: <REQ-ID>` 주석 권장
- 모든 산출물에 `Generated by: <agent> (Team: <team>) → Reviewed by: <leader>` 메타

### 5.2 ADR 의무화

다음 시점에 **ADR 작성 필수**.

| 시점 | ADR 유형 |
|------|---------|
| 기술 스택 선정 | ADR-001-tech-stack |
| 프레임워크 채택 | ADR-NNN-framework |
| DB 스키마 핵심 결정 | ADR-NNN-schema |
| 트랜잭션 모델 | ADR-NNN-transaction |
| 외부 채널 통합 (MQ / REST / SFTP) | ADR-NNN-channel |
| 보안 결정 (암호화 / 인증) | ADR-NNN-security |
| 성능·확장성 결정 | ADR-NNN-performance |
| 기존 결정 변경 | ADR-NNN-revision-of-MMM |

**ADR 표준 양식** (`templates/design/ADR.template.md`)

```
# ADR-NNN: 제목
## 상태: PROPOSED / ACCEPTED / SUPERSEDED
## 컨텍스트: 왜 결정이 필요했는가
## 결정: 무엇을 정했는가
## 대안: 검토한 다른 선택지
## 결과: 긍정적 / 부정적 영향
## 참조: 관련 ADR / 코드 / 요구사항
```

### 5.3 가독성 표준 — 한국어 + 영문 보조

(sg-gw §7.9 참조)

- **클래스 / public 메서드**: 한국어 Javadoc 필수 + `<p>English: ...</p>` 1줄
- **인라인 주석**: WHY 중심, 한국어 우선, English 보조
- **알고리즘 단계**: `<ol>` 또는 `// 1) ... // 2) ...` 번호 매김
- **레거시 함수 참조**: `// source:` 명시

### 5.4 보안 Hook 3 단계

| 단계 | 시점 | 역할 |
|------|------|------|
| **L1** | git pre-commit | gitleaks / trufflehog 시크릿 스캔 (개발자 PC) |
| **L2** | CI / PR | security-auditor 에이전트 자동 실행 (PR 차단) |
| **L3** | 운영 배포 직전 | prod-gate 체크리스트 + 인간 결재 |

### 5.5 자동 검증 도구

| 도구 | 목적 | 적용 시점 |
|------|------|----------|
| **ArchUnit** (Java) | 패키지 격리 / `System.out` 금지 등 정적 규칙 | CI 빌드 |
| **Checkstyle / SpotBugs** | 코드 스타일 / 버그 패턴 | CI 빌드 |
| **gitleaks** | 시크릿 스캔 | L1 Hook |
| **OWASP Dependency Check** | 의존성 취약점 | CI |
| **SBOM 생성** (CycloneDX / Syft) | 구성요소 명세 · 공급망 투명성 | CI |
| **OSS 라이선스 스캔** (ScanCode / license-checker) | 라이선스 컴플라이언스 (금지 라이선스 차단) | CI |
| **의존성 핀 · lockfile 검증** | 공급망 무결성 (버전 고정 · 변조 탐지) | CI |
| **Parity Tester** (마이그레이션) | 바이트 단위 입출력 동치 | Skill 5 |
| **JaCoCo** (Java) | 커버리지 측정 | CI |

### 5.6 금융권 / 규제 산업 추가 룰

- 금액 / 금리 / 환율: **`BigDecimal` + 명시적 `RoundingMode`** (double 금지)
- 실데이터 (계좌·고객명·주민번호·카드번호): **평문 로그 금지** (마스킹 필수)
- PII 컬럼: **AES-256-GCM** 등 표준 암호화
- 운영 동작을 바꾸는 수정: **ADR 또는 port-log 의무 기록**

### 5.7 언어별 등가 룰 매핑 (조직 표준 언어)

본 표준의 코드 룰은 Java 로 기술되었으나 **언어 중립 원칙**을 따른다. 조직 표준 언어(Java · Python · JS/TS(React·Node) · C/C++(레거시 유지보수))별 등가 도구·룰은 아래 표를 적용한다. 신규 언어 채택 시 본 표에 행을 추가하고 ADR 로 기록한다.

| 룰 (의도) | Java (기준) | Python | JS/TS (React·Node) | C/C++ (레거시) |
|-----------|-------------|--------|---------------------|----------------|
| 문서 주석 (한국어+영문 보조) | Javadoc | docstring (Google/PEP257) | JSDoc / TSDoc | Doxygen |
| 추적 주석 | `// source:` `// req:` | `# source:` `# req:` | `// source:` `// req:` | `// source:` `// req:` |
| 금액 정밀도 (이진 부동소수 금지) | `BigDecimal`+`RoundingMode` | `decimal.Decimal` | `decimal.js`/`big.js` (프론트는 표시 전용 — 재계산 금지) | 고정소수점/정수 연산 |
| 아키텍처 격리 검증 | ArchUnit | import-linter | dependency-cruiser / eslint-boundaries | include-what-you-use + 리뷰 |
| 정적 분석 | Checkstyle/SpotBugs | ruff/mypy | eslint/tsc --strict | clang-tidy |
| SAST (CI) | CodeQL(java) | CodeQL(python) | CodeQL(javascript) | CodeQL(cpp) |
| 커버리지 | JaCoCo | coverage.py | istanbul/c8 (Vitest/Jest) | gcov/llvm-cov |
| 의존성 핀 (공급망) | Maven/Gradle lock | poetry.lock / uv.lock | package-lock / pnpm-lock | vendoring + 버전 고정 |
| 빌드 | Maven/Gradle | poetry/uv | npm/pnpm | CMake/Make |

> 적용 원칙: ① 커버리지 80/70 등 **수치 기준은 언어 무관 동일** ② CI L2 의 CodeQL `matrix.language` 에 사용 언어를 모두 등록 ③ 프론트엔드(React)는 frontend-developer 강제 룰(WCAG·서버 신뢰경계·번들 시크릿 금지) 추가 적용. `[근거:업계관례]` — 도구 선택은 프로젝트 조정 가능(ADR 기록).

### 5.8 AI 도구 중립성 (Claude 비종속)

본 표준은 특정 AI 벤더에 종속되지 않는다. **Claude Code 는 기준 구현(reference)일 뿐**이다.

- **스킬**: SKILL.md 는 마크다운 절차 문서 — Codex CLI · Cursor · Gemini CLI 등에서 시스템 프롬프트/룰 파일로 주입하여 동일하게 사용 (§3 도입부, WORKFLOW-GUIDE 부록 A)
- **에이전트**: `recommended_llm` 은 등급(고급/표준/경량) 의미 — Opus↔GPT-5↔Gemini Ultra 등 동급으로 대체 가능 (§4.3 표)
- **교차 검증**: 오히려 **타 벤더 사용이 의무** (Skill 5 — 모델 편향 회피)
- **게이트·훅·산출물**: AI 도구와 무관 (git·CI·문서 기반)

단, 외부 LLM 사용 시 Skill 5 의 **외부 전송 데이터 통제(S1)** — 시크릿·PII 스크럽, 조직 승인 모델/리전 한정 — 를 동일하게 적용한다.

---

## 6. SG Gateway 실증 사례 (적용 검증)

### 6.1 적용 규모 (1 개월, 29 sprint)

| 항목 | 수치 |
|------|------|
| 적용 도메인 | 7 개 (HOFI · LCS · GIRO · ARS · Firm · RET · OpenBanking) |
| 외부 채널 | 5 종 (RabbitMQ / TCP 4B prefix / TCP 고정폭 / REST+OAuth2 / SFTP) |
| Sub-agent | 16 종 |
| ADR | 38 건 |
| 테스트 | 1,300+ |
| Flyway 마이그레이션 | V1 ~ V205 |
| 산출물 | md 100+ / pptx 5+ / pdf 8+ |

### 6.2 교차 검증 차단 사례 (CVSS v3.1)

| ID | 결함 | CVSS | 차단 단계 |
|----|------|------|----------|
| K-01 | (마스킹) 시크릿 노출 | 9.8 CRITICAL | Skill 5 / L2 |
| K-02 | (마스킹) 인증 우회 | 8.4 HIGH | Skill 5 / L2 |
| K-03 | (마스킹) PII 평문 로그 | 7.4 HIGH | Skill 5 / L2 |
| K-04 | (마스킹) 세션 고정 | 6.5 MED | Skill 5 / L2 |

→ Codex 가 Claude 의 1차 산출물을 독립 검토하여 **CRITICAL 1 건 / HIGH 2 건 사전 차단**.

### 6.3 검증된 패턴

- **Phase 게이트 3 곳** (분석 / 설계 / 릴리즈) → 사람 결재 누락 0 건
- **7 차원 자체 평가** → 5 회 반복 후 평균 92.4 점 (목표 90 초과)
- **디렉터리 1:1 격리** → 에이전트 간 파일 충돌 0 건
- **ADR 38 건** → 설계 변경 추적성 100 %

---

## 7. 적용 시 위험 및 대응

| 위험 | 등급 | 대응 |
|------|------|------|
| Leader 부적격 임명 → 팀 결과 품질 저하 | 높 | 04-review-checklist 의 Leader 적격성 항목 강제 |
| 에이전트 7차원 평가 자기 합리화 (점수 부풀림) | 중 | 교차 검증 (Skill 5 / 다른 LLM) 으로 독립 채점 |
| LLM 모델 비용 폭증 (고급 모델 남용) | 중 | §4.3 모델 구분 표 준수 / 비용 모니터링 |
| 디렉터리 격리 위반 → 동시 충돌 | 중 | 1:1 권한 / Git worktree / 파일 잠금 |
| 사람 결재 게이트 우회 | 높 | Hook L3 + ADR 의무 + 결재 로그 |
| 본 표준 자체의 사례 부족 (greenfield 적용) | 중 | Day-0 단일 통제 모드 1 sprint 기준선 확보 → 본 표준 1 sprint 비교 → 본 전환 (PoC 권고) |
| 보안 Hook 미설치 → 시크릿 유출 | 높 | Skill 4 진입 전 L1 Hook 설치 의무 |

---

## 8. 적용 의무 체크리스트

본 표준을 신규 프로젝트에 적용할 때 PM 이 확인할 최종 체크리스트.

### 사전 (Skill 1 진입 전)
- [ ] 본 표준 패키지 다운로드 + 개발 프로젝트 디렉터리 복사 완료
- [ ] AI 도구 (Claude/Codex/Cursor) 작동 확인
- [ ] Git 저장소 초기화 + L1 Hook 설치
- [ ] PM / Leader / 결재자 명단 확정

### Skill 1~3 (분석·설계)
- [ ] PROJECT-PROPOSAL.md 작성 (12 문항 응답)
- [ ] REQUIREMENTS-SPEC.md 작성 + `[AMBIGUOUS]` 해소
- [ ] DEV-PLAN.md / TEST-PLAN.md / ADR-001 작성
- [ ] **G1 (분석) + G2 (설계) 게이트 통과**

### Skill 4 (구현)
- [ ] 1:1 디렉터리 권한 매핑 완료
- [ ] 에이전트별 LLM 모델 지정
- [ ] 7 차원 자체 평가 임계치 = 90 설정
- [ ] Sprint 별 결재 로그 유지

### Skill 5 (검증)
- [ ] 교차 검증 1 회 이상 실행 (다른 LLM 벤더)
- [ ] CVSS ≥ 7.0 결함 0 건 또는 리스크 수용 결재
- [ ] **G3 (릴리즈) 게이트 통과**

### Skill 6 (산출)
- [ ] 1:1 대화로 산출물 목록·형식 확정
- [ ] deliverables/INDEX.md 완성
- [ ] 임원 보고 자료 리허설 (해당 시)

### Skill 7 (표준 검증)
- [ ] setup 직후 또는 게이트 직전 `07-validate-standard` 실행
- [ ] FAIL 항목 0건 또는 PM 리스크 수용 기록
- [ ] WARN 항목 담당자 / 기한 / 후속 조치 기록

---

## 9. 본 표준의 변경 / 확장 정책

- 본 표준은 **표준 위원회 검토** 후 변경한다.
- 신규 도메인 (예: 데이터 분석 / 모바일 / 임베디드) 적용 시 본 표준을 **상속**하여 도메인별 부속서를 작성한다.
- 본 표준의 변경 이력은 본 문서 하단의 Changelog 에 누적한다.

---

## 10. 참고 문서

- 패키지 카탈로그: [PACKAGE-INDEX.md](PACKAGE-INDEX.md)
- 8 표준 스킬: `.claude/skills/` (01~06 실행, 07 검증, 08-harness 보완 가이드)
- 13 에이전트 정의: `.claude/agents/*.md`
- 21 산출물 템플릿: `templates/`
- 보안 Hook 3 단계: `hooks/`
- 운영 보조 스크립트: `scripts/`
- sg-gw 적용 PPT (임원 보고): `societe_generale_gw/docs/submission/ai-harness-sg-gw-exec.pptx`
- sg-gw 적용 PPT (상세): `societe_generale_gw/docs/submission/ai-harness-sg-gw-application.pptx`

---

## Changelog

| 버전 | 일자 | 내용 |
|------|------|------|
| 1.5 | 2026-06-15 | `00-harness` → `08-harness` 리네임. 역할을 "통합 진입점"에서 "07 검증 후 보완 가이드(개발 흐름 끝 단계)"로 재정의 — 01~06 개발 → 07 검증 → 08 보완 순서 정립 |
| 1.4 | 2026-06-12 | frontend-developer 에이전트 추가(웹 퍼블리싱·UI·WCAG — 13 에이전트). 언어별 등가 룰 매핑 §5.7(Java·Python·JS/TS·C/C++). AI 도구 중립성 §5.8. 준수 판정 객관성 3계층(07 §3.0) 명시 |
| 1.3 | 2026-06-12 | 08-harness 검증 보완 가이드 스킬 추가(감사 가이드·신규 프로젝트 스텝 안내 — 8 스킬). HARNESS-STATE 상태파일 템플릿 추가(21 템플릿). 스킬 번호 규칙 도입(00- 최초) |
| 1.2 | 2026-06-10 | 사람 질의 생성 기준 §4.8(CQ1~CQ3)·근거 표기 범례 §4.9 추가. 위협 모델(STRIDE) Skill 03 도입. 공급망 보안(SBOM·OSS 라이선스·의존성 핀) §5.5·L2·Skill 05 강화. Sprint 회고·인시던트 postmortem 템플릿 추가(템플릿 17→20). 외부 egress 통제·신뢰 경계 룰·규정값 근거 태깅 반영 |
| 1.1 | 2026-06-10 | `07-validate-standard` 검증 스킬 추가, seed.yaml/bootstrap 필수 흐름 제외, L1/L2 가드레인 강화 |
| 1.0 | 2026-05-29 | 초안 작성 — sg-gw 1개월 실증 기반 표준 하네스 프로세스 정리 |

---

**작성**: PM Claude (Opus 4.7) — 2026-05-29
**갱신**: Codex — 2026-06-10 (v1.1) / Claude Opus 4.8 — 2026-06-10 (v1.2)
**근거**: sg-gw 프로젝트 29 sprint (AS-D1 ~ AS-D14) 실증
**다음 작업 (제안)**: `07-validate-standard`를 실제 신규 프로젝트에 1회 적용해 WARN/FAIL 기준을 보정하고, 필요 시 CI 자동 검증 스크립트로 승격한다.
