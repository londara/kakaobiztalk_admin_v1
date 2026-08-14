# 표준 하네스 패키지 — 전체 카탈로그

> **본 패키지 목적**: 신규 프로젝트에 즉시 적용 가능한 하네스 자산 (Skill / Agent / Template / Hook / Script) 의 완전한 카탈로그.
> **진입점**: [HARNESS-PROCESS-STANDARD.md](HARNESS-PROCESS-STANDARD.md) (마스터 정의서)
> **다운로드 → 복사 → AI 실행 5단계 흐름** 의 모든 구성 자산.
> **적용 방식**: Skill 기반. seed.yaml / bootstrap 초기 생성 흐름은 본 표준 패키지의 필수 자산에서 제외한다.

---

## 0. 빠른 시작 (5 분)

```bash
# 1. 패키지 다운로드 또는 클론
git clone <repo> ~/Downloads/harness-standards

# 2. 신규 개발 프로젝트에 복사
PROJ=~/workspaces/project/my-new-project
mkdir -p $PROJ/docs
cp -r ~/Downloads/harness-standards/.claude        $PROJ/
cp -r ~/Downloads/harness-standards/templates       $PROJ/
cp -r ~/Downloads/harness-standards/hooks           $PROJ/
cp -r ~/Downloads/harness-standards/scripts         $PROJ/
cp ~/Downloads/harness-standards/HARNESS-PROCESS-STANDARD.md $PROJ/docs/

# 3. 보안 Hook L1 설치
cd $PROJ
git init -b main
cp hooks/pre-commit-gitleaks.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 4. AI 도구 실행 (개발 프로젝트 디렉터리에서)
claude  # 또는 codex, cursor

# 5. 첫 스킬 실행
/08-harness             # 또는 skill 08-harness
```

---

## 1. 패키지 디렉터리 트리

```
harness-standards/
├── HARNESS-PROCESS-STANDARD.md             ← 마스터 정의서 (먼저 읽기)
├── PACKAGE-INDEX.md                        ← 본 파일
├── README.md                               ← 패키지 사용 가이드
│
├── .claude/                                ← AI 도구 자산 (복사 대상)
│   ├── skills/                             ← 8 표준 스킬 (6 실행 + 1 검증 + 1 어시스턴트)
│   │   ├── 01-plan-project/SKILL.md
│   │   ├── 02-define-requirements/SKILL.md
│   │   ├── 03-draft-dev-plan/SKILL.md
│   │   ├── 04-implement/SKILL.md
│   │   ├── 05-quality-review/SKILL.md
│   │   ├── 06-finalize-deliverables/SKILL.md
│   │   ├── 07-validate-standard/SKILL.md
│   │   └── 08-harness/SKILL.md
│   │
│   └── agents/                             ← 13 에이전트
│       ├── _team-leader.md
│       ├── legacy-analyst.md
│       ├── doc-spec-parser.md
│       ├── data-model-designer.md
│       ├── architect.md
│       ├── backend-developer.md
│       ├── frontend-developer.md
│       ├── adapter-builder.md
│       ├── code-reviewer.md
│       ├── qa-engineer.md
│       ├── security-auditor.md
│       ├── docs-writer.md
│       └── trace-mapper.md
│
├── templates/                              ← 21 산출물 템플릿
│   ├── planning/
│   │   ├── PROJECT-PROPOSAL.template.md
│   │   └── BUSINESS-REQUIREMENTS.template.md
│   ├── requirements/
│   │   ├── REQUIREMENTS-SPEC.template.md
│   │   └── USE-CASE.template.md
│   ├── design/
│   │   ├── DEV-PLAN.template.md
│   │   ├── TEST-PLAN.template.md
│   │   ├── ADR.template.md
│   │   └── THREAT-MODEL.template.md
│   ├── implementation/
│   │   ├── SPRINT-LOG.template.md
│   │   ├── TRACE-CSV.template.csv
│   │   ├── SPRINT-RETRO.template.md
│   │   ├── INCIDENT-POSTMORTEM.template.md
│   │   └── HARNESS-STATE.template.yaml
│   ├── qa/
│   │   ├── REVIEW-REPORT.template.md
│   │   ├── CROSS-VALIDATION.template.md
│   │   ├── SECURITY-AUDIT.template.md
│   │   ├── PARITY-REPORT.template.md
│   │   └── QA-TEST-REPORT.template.md
│   └── deliverables/
│       ├── EXEC-SUMMARY.template.md
│       ├── RUNBOOK.template.md
│       └── ARCHITECTURE-OVERVIEW.template.md
│
├── hooks/                                  ← 보안 Hook 3 단계 + 공통 룰
│   ├── pre-commit-gitleaks.sh              ← L1 (개발자 PC)
│   ├── gitleaks.toml                       ← L1/L2 기본 룰셋
│   ├── ci-security-auditor.yml             ← L2 (CI/PR)
│   └── prod-gate-checklist.md              ← L3 (운영 배포 직전)
│
└── scripts/                                ← 운영 보조
    ├── parity-check.sh                     ← 바이트 단위 동치 검증
    └── generate-codecs.sh                  ← 스키마 기반 codec 자동 생성
```

---

## 2. 5 단계 사용 흐름 ↔ 자산 매핑

| 단계 | 사용자 액션 | 사용 자산 |
|------|------------|----------|
| **[1] 다운로드** | git clone 또는 zip 다운로드 | (없음) |
| **[2] 복사** | 개발 프로젝트로 cp -r | `.claude/`, `templates/`, `hooks/`, `scripts/`, `HARNESS-PROCESS-STANDARD.md` |
| **[3] AI 실행** | Claude Code / Codex / Cursor | `.claude/skills/`, `.claude/agents/` 자동 로드 |
| **[4] 분석·설계** | Skill 1 → 2 → 3 순차 실행 | `01-plan-project`, `02-define-requirements`, `03-draft-dev-plan` SKILL + `templates/planning, requirements, design` |
| **[5] 구현·QA·산출** | Skill 4 → 5 → 6 순차 실행 + 필요 시 Skill 7 검증 | `04-implement`, `05-quality-review`, `06-finalize-deliverables`, `07-validate-standard` SKILL + `templates/implementation, qa, deliverables` + `hooks/`, `scripts/` |

---

## 3. 8 스킬 카탈로그 (6 실행 + 1 검증 + 1 어시스턴트)

| Skill | ID | Lead Agent | Support Agents | 주 책임 | 산출물 |
|-------|-----|------------|----------------|--------|--------|
| 1 | `01-plan-project` | `docs-writer` | `doc-spec-parser`, `security-auditor`, `trace-mapper` | 프로젝트 기획서 1:1 대화 작성 | PROJECT-PROPOSAL.md, BUSINESS-REQUIREMENTS.md |
| 2 | `02-define-requirements` | `trace-mapper` | `doc-spec-parser`, `docs-writer`, `security-auditor` | 요구사항 정의서 구체화 + REQ-ID | REQUIREMENTS-SPEC.md, requirements-matrix.csv |
| 3 | `03-draft-dev-plan` | `architect` | `data-model-designer`, `qa-engineer`, `security-auditor`, `trace-mapper` | 개발/테스트 계획 + ADR-001 | DEV-PLAN.md, TEST-PLAN.md, ADR-001 |
| 4 | `04-implement` | `team-leader` | `backend-developer`, `frontend-developer`, `adapter-builder`, `qa-engineer`, `code-reviewer`, `security-auditor`, `trace-mapper` | 자율 에이전트 팀 + 7차원 평가 루프 | src/, tests/, sprint logs |
| 5 | `05-quality-review` | `security-auditor` | `code-reviewer`, `qa-engineer`, `trace-mapper` | QA + 교차 검증 (CVSS) + Hook 3단계 | code-review-N.md, cross-validation-N.md, audit-N.md |
| 6 | `06-finalize-deliverables` | `docs-writer` | `architect`, `qa-engineer`, `security-auditor`, `trace-mapper` | 산출물 1:1 확인 + 형식 변환 | deliverables/, INDEX.md |
| 7 | `07-validate-standard` | `code-reviewer` | `security-auditor`, `trace-mapper`, `docs-writer` | 표준 준수 읽기 전용 검증 | reviews/standard-validation-*.md |
| 8 | `08-harness` | `team-leader` | `code-reviewer`, `docs-writer` | 검증 보완 가이드 — 감사 가이드 + 스텝 진행 안내 | docs/.harness-state.yaml |

---

## 4. 13 에이전트 카탈로그

| 에이전트 | 모델 | Phase | 주 책임 | Write 디렉터리 |
|---------|-----|------|--------|----------------|
| `_team-leader` | Opus | 3,4 | DAG dispatch + 7차원 평가 + 통합 보고 | docs/sprints/, reviews/leader-reports/ |
| `legacy-analyst` | Opus | 1 | 레거시 정적 분석 / 콜그래프 | mapping/analysis/ |
| `doc-spec-parser` | Sonnet | 1 | 업무 문서 → YAML 정형화 | doc/parsed/ |
| `data-model-designer` | Sonnet | 2 | C ↔ Java 매핑 / 엔티티 / DTO | mapping/model/ |
| `architect` | Opus | 2 | Skill 3 Lead + 패키지 / ADR / 스레드 / 트랜잭션 | mapping/architecture/, docs/design/ |
| `backend-developer` | Sonnet | 3 | 신규/포팅 구현 + 단위 테스트 | src/main/, mapping/port-log/ |
| `frontend-developer` | Sonnet | 3 | 웹 퍼블리싱 / UI(React·Vue) / 접근성(WCAG) / 디자인 시스템 | src/main/frontend/, web/ |
| `adapter-builder` | Sonnet | 3 | MQ / TCP / REST / Codec | src/.../adapter/, src/.../codec/ |
| `code-reviewer` | Opus | 4 | 코드 리뷰 + 7차원 독립 재평가 | reviews/ |
| `qa-engineer` | Sonnet | 4 | 단위 / 통합 / E2E / Parity | qa/, parity/, tests/ |
| `security-auditor` | Opus | 4 | OWASP / 규제 / CVSS / Hook L2 | security/ |
| `docs-writer` | Haiku | 1~4 | 사람용 문서 / Runbook / 임원 보고 | docs/, deliverables/ |
| `trace-mapper` | Haiku | 1~4 | Skill 2 Lead + 요구↔코드 / C↔Java 추적 매트릭스 | docs/requirements/, mapping/trace/ |

---

## 5. 21 템플릿 카탈로그

| 분류 | 템플릿 | 작성 시점 | 형식 |
|------|--------|---------|------|
| planning | PROJECT-PROPOSAL | Skill 1 | md |
| planning | BUSINESS-REQUIREMENTS | Skill 1 | md |
| requirements | REQUIREMENTS-SPEC | Skill 2 | md |
| requirements | USE-CASE | Skill 2 | md |
| design | DEV-PLAN | Skill 3 | md |
| design | TEST-PLAN | Skill 3 | md |
| design | ADR | Skill 3~4 (반복) | md |
| design | THREAT-MODEL | Skill 3 (STRIDE·공격표면) | md |
| implementation | SPRINT-LOG | Skill 4 (매 Sprint) | md |
| implementation | TRACE-CSV | Skill 4 (지속) | csv |
| implementation | SPRINT-RETRO | Skill 4 (Sprint 종료) | md |
| implementation | INCIDENT-POSTMORTEM | 운영 사고 발생 시 | md |
| implementation | HARNESS-STATE | /08-harness 신규 모드 (상태파일) | yaml |
| qa | REVIEW-REPORT | Skill 5 | md |
| qa | CROSS-VALIDATION | Skill 5 | md |
| qa | SECURITY-AUDIT | Skill 5 | md |
| qa | PARITY-REPORT | Skill 5 (마이그레이션) | md |
| qa | QA-TEST-REPORT | Skill 5 | md |
| deliverables | EXEC-SUMMARY | Skill 6 | md → pptx/pdf |
| deliverables | RUNBOOK | Skill 6 | md |
| deliverables | ARCHITECTURE-OVERVIEW | Skill 6 | md + Mermaid |

---

## 6. 보안 Hook 3 단계

| 단계 | 파일 | 시점 | 역할 |
|------|------|------|------|
| L1 | `hooks/pre-commit-gitleaks.sh` | git pre-commit | 시크릿 스캔 (개발자 PC) |
| L2 | `hooks/ci-security-auditor.yml` | PR / push | gitleaks + OWASP DC + CodeQL + AI 에이전트 |
| L3 | `hooks/prod-gate-checklist.md` | 운영 배포 직전 | 인간 결재 의무 (PM + 정보보호 + 운영) |

---

## 7. 운영 보조 스크립트

| 스크립트 | 용도 | 사용 시점 |
|---------|------|---------|
| `scripts/parity-check.sh` | 바이트 단위 C↔Java 또는 v1↔v2 동치 검증 | Skill 5 (마이그레이션) |
| `scripts/generate-codecs.sh` | YAML schema → Java codec 자동 생성 | Skill 4 (codec 변경 시) |

---

## 8. 사람 결재 게이트 (3 곳)

| 게이트 | 시점 | 결재자 | 필수 산출물 |
|--------|------|--------|------------|
| **G1 분석** | Skill 2 → 3 진입 전 | PM | REQUIREMENTS-SPEC.md |
| **G2 설계** | Skill 3 → 4 진입 전 | PM + 아키텍트 | DEV-PLAN.md, TEST-PLAN.md, ADR-001 |
| **G3 릴리즈** | Skill 5 → 6 진입 전 | PM + 정보보호 + 운영 | 모든 검증 리포트 + prod-gate-checklist |

---

## 9. 7 차원 자체 평가

| 차원 | 가중치 | 평가 기준 |
|------|-------|----------|
| 완성도 | 20% | Sprint task 100% 완료 |
| 추적성 | 15% | // source: / ADR / 에이전트명 |
| 보안 | 20% | 하드코딩 0 / PII 마스킹 / Hook 통과 |
| 성능 | 10% | NFR-PERF SLA 충족 |
| 가독성 | 15% | Javadoc / Mermaid / 표 정렬 |
| 표준 준수 | 10% | 디렉터리 격리 위반 0 / ADR 누락 0 |
| 테스트 커버리지 | 10% | TEST-PLAN 기준 충족 |

> 임계치 90 / 미달 시 최저 차원 보완 → 재생성 (최대 5회) → 5회 후 PM Escalation

---

## 10. 금융권 특화 룰 (CLAUDE.md §7 통합)

| 항목 | 룰 |
|------|----|
| 금액 / 금리 / 환율 | **BigDecimal + RoundingMode 명시** (double 금지) — ArchUnit 강제 |
| PII 평문 로그 | 절대 금지 (마스킹 필수) — security-auditor REJECT |
| PII 컬럼 저장 | AES-256-GCM (ADR-005 의무) |
| 시크릿 하드코딩 | 0 건 — L1 Hook (gitleaks) 차단 |
| 감사 로그 | 7년 보존 + 무결성 (ADR-006) |
| 키 관리 | KMS / HSM (ADR-007) + 90일 회전 |
| 외부 채널 인증 | mTLS / OAuth2 / SSH Key (ADR-008) |
| 메시지 무결성 | HMAC 또는 서명 (ADR-004) |
| 컷오버 후 롤백 | 72 시간 가능 유지 (BCP) |
| 레거시 보존 | 6 개월 (마이그레이션) |

규제 적용 시 별도 컴플라이언스 점검:
- 전자금융감독규정 / 개인정보보호법 / 신용정보법 / ISMS-P / PCI-DSS / 금융보안원 가이드

---

## 11. 참조 문서

| 문서 | 목적 |
|------|------|
| [HARNESS-PROCESS-STANDARD.md](HARNESS-PROCESS-STANDARD.md) | 마스터 프로세스 정의서 (5단계 흐름 + 8스킬) |
| [WORKFLOW-GUIDE.md](WORKFLOW-GUIDE.md) | 개발자 실행 워크플로우 가이드 (01→06 실행 + 07 검증) |
| [README.md](README.md) | 패키지 사용 가이드 |
| [docs/HARNESS-BRIEFING.md](docs/HARNESS-BRIEFING.md) | 개발가이드·발표자료 작성용 콘텐츠 소스 |
| `.claude/skills/` | 8 표준 스킬 (01~06 실행, 07 검증, 08-harness 보완 가이드) |
| `.claude/agents/*.md` | 13 에이전트 정의 |
| `templates/` | 21 산출물 템플릿 |
| `hooks/`, `scripts/` | 보안 Hook 3 단계 + 운영 보조 |

---

## 12. 작성 / 갱신

| 일자 | 버전 | 내용 |
|------|------|------|
| 2026-06-15 | 1.5 | `00-harness`→`08-harness` 리네임 + "07 검증 후 보완 가이드"로 재정의 |
| 2026-06-12 | 1.4 | frontend-developer 추가(13 에이전트), 언어별 등가 룰·AI 도구 중립성·객관성 계층 반영 |
| 2026-06-12 | 1.3 | 08-harness 검증 보완 가이드 스킬 + HARNESS-STATE 템플릿 추가 (8 스킬·21 템플릿) |
| 2026-06-10 | 1.2 | 템플릿 17→20 (위협모델·Sprint회고·인시던트postmortem 추가). 공급망 보안(SBOM·OSS 라이선스·의존성 핀), 질의 생성 기준 §4.8(CQ1~CQ3)·근거 표기 범례 §4.9, 규정값 근거 태깅 반영 |
| 2026-06-10 | 1.1 | `07-validate-standard` 스킬 추가, Skill 기반 흐름 명시, L1/L2 보안 가드레인 강화 |
| 2026-05-29 | 1.0 | 초안 — sg-gw 1개월 실증 기반 표준 하네스 패키지 작성. |
