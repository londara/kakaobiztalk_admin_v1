# IT 개발 표준 하네스 프로세스 (공통 자산)

> **위치**: `~/workspaces/project/harness-standards/`
> **용도**: `~/workspaces/project/` 아래 모든 IT 개발 프로젝트가 공통 참조하는 **표준 하네스 패키지**
> **출처**: Société Générale Gateway (sg-gw) C → Java/Spring Boot 3 마이그레이션 1개월 (29 sprint) 실증
> **작성일**: 2026-05-29

---

## ⚠ 정직한 자산 평가 (먼저 읽어주세요)

본 자산은 **업계 표준 (ISO / IEEE / W3C 인증) 이 아닙니다**. 다음과 같은 한계를 가집니다.

| 항목 | 평가 |
|------|------|
| 검증 표본 | **sg-gw 1 프로젝트만** (통계적 유의성 0) |
| 외부 인증 | 없음 (표준화 단체 인증 무) |
| 일반화 검증 | 미실시 (다른 도메인 / 조직 적용 사례 없음) |
| 업계 통용 부분 | ADR / Conventional Commits / Code Coverage / Phase 게이트 / OWASP / CVSS — 일반 표준 |
| **자체 제작 부분** | **7 차원 자체 평가 / 90점 임계 / 5회 보완 루프 / Team-with-Leader 모델** — sg-gw 회고 기반 자체 안 |

### 본 자산의 정확한 명칭

> "표준" 이 아니라 **"sg-gw 회고 기반 가이드라인 / 참고 패턴"** 입니다.
> 신규 프로젝트에 그대로 적용하기 전 PM 이 본 프로젝트 맥락에 맞게 취사선택 / 변형해야 합니다.

---

## 1. 본 패키지의 정체성

본 디렉터리는 **`~/workspaces/project/` 아래 신규·진행 중 프로젝트가 공통 참조** 하는 하네스 자산이다. 어느 프로젝트에서든 절대 경로 `~/workspaces/project/harness-standards/` 또는 상대 경로 `../harness-standards/` 로 접근 가능.

| 항목 | 설명 |
|------|------|
| **단일 출처** | 모든 프로젝트가 동일 자산 사본을 본다 (drift 방지) |
| **읽기 전용 권장** | 프로젝트별 변경은 본 디렉터리가 아닌 프로젝트 내 사본에서 |
| **버전 관리** | 본 디렉터리는 별도 git repo 로 관리 권장 (조직 표준 위원회) |
| **갱신 주기** | 분기 1회 갱신 권장 |
| **적용 방식** | Skill 기반. seed.yaml / bootstrap 초기 생성 방식은 본 패키지의 필수 흐름에서 제외 |

---

## 2. 패키지 구성 (핵심 자산)

```
harness-standards/
├── HARNESS-PROCESS-STANDARD.md     ← 마스터 정의서 (먼저 읽기)
├── PACKAGE-INDEX.md                ← 전체 자산 카탈로그
├── README.md                       ← 본 파일
│
├── .claude/
│   ├── skills/                     ← 8 표준 스킬 (6 실행 + 1 검증 + 1 어시스턴트)
│   │   ├── 01-plan-project/
│   │   ├── 02-define-requirements/
│   │   ├── 03-draft-dev-plan/
│   │   ├── 04-implement/
│   │   ├── 05-quality-review/
│   │   ├── 06-finalize-deliverables/
│   │   ├── 07-validate-standard/        ← 표준 준수 검증
│   │   └── 08-harness/                  ← 검증 후 보완 가이드
│   │
│   └── agents/                     ← 13 에이전트 정의
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
├── templates/                      ← 21 산출물 템플릿
│   ├── planning/        (2)        — 기획서·비즈니스 요구
│   ├── requirements/    (2)        — 요구사항 정의서·Use Case
│   ├── design/          (4)        — 개발계획·테스트계획·ADR·위협모델
│   ├── implementation/  (5)        — Sprint 로그·추적·회고·Postmortem·상태파일
│   ├── qa/              (5)        — 코드리뷰·교차검증·보안감사·Parity·QA
│   └── deliverables/    (3)        — 임원보고·Runbook·아키텍처
│
├── hooks/                          ← 보안 Hook 3 단계 + 공통 룰
│   ├── pre-commit-gitleaks.sh      — L1 (개발자 PC)
│   ├── gitleaks.toml               — L1/L2 기본 룰셋
│   ├── ci-security-auditor.yml     — L2 (CI/PR)
│   └── prod-gate-checklist.md      — L3 (운영 배포 직전)
│
└── scripts/                        ← 운영 보조
    ├── parity-check.sh             — 바이트 단위 동치 검증
    └── generate-codecs.sh          — 스키마 기반 codec 자동 생성
```

---

## 3. 5 단계 사용 흐름

```
[1] 하네스 프로젝트 다운로드 (PC)
        │  git clone 또는 zip
[2] 개발 프로젝트 디렉터리로 복사
        │  .claude / templates / hooks / scripts 일괄
[3] 개발 프로젝트에서 AI 도구 실행
        │  Claude Code / Codex / Cursor
[4] 분석·설계 단계 — 스킬 ① ② ③
        │  기획서 → 요구사항 정의서 → 개발/테스트 계획서
[5] 구현·QA·산출 단계 — 스킬 ④ ⑤ ⑥
        │  자율 구현 → QA + 교차검증 → 최종 산출물
[프로젝트 완료] PM 최종 결재 → 릴리즈
```

---

## 4. 빠른 시작 (5 분)

```bash
# 1. 다운로드 (또는 git clone)
git clone <repo> ~/Downloads/harness-standards

# 2. 신규 개발 프로젝트에 복사
PROJ=~/workspaces/project/my-new-project
mkdir -p $PROJ/docs
cp -r ~/Downloads/harness-standards/.claude        $PROJ/
cp -r ~/Downloads/harness-standards/templates       $PROJ/
cp -r ~/Downloads/harness-standards/hooks           $PROJ/
cp -r ~/Downloads/harness-standards/scripts         $PROJ/
cp ~/Downloads/harness-standards/HARNESS-PROCESS-STANDARD.md $PROJ/docs/

# 3. 보안 Hook L1 설치 (개발자 PC)
cd $PROJ
git init -b main
cp hooks/pre-commit-gitleaks.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 4. AI 도구 실행
claude   # 또는 codex / cursor

# 5. 첫 스킬 실행 (Claude Code 기준)
/08-harness
```

---

## 5. 8 표준 스킬 (6 실행 + 1 검증 + 1 어시스턴트)

| Skill | ID | Lead Agent | Phase | 입력 → 출력 |
|-------|-----|------------|------|-----------|
| 1 | `01-plan-project` | `docs-writer` | 1 | (기획서 또는 1:1 12문항) → PROJECT-PROPOSAL.md |
| 2 | `02-define-requirements` | `trace-mapper` | 1 | PROJECT-PROPOSAL → REQUIREMENTS-SPEC + REQ-ID 매트릭스 |
| 3 | `03-draft-dev-plan` | `architect` | 2 | REQUIREMENTS-SPEC → DEV-PLAN + TEST-PLAN + ADR-001 |
| 4 | `04-implement` | `team-leader` | 3 | DEV-PLAN → src/ + tests/ + Sprint 로그 (7차원 평가 루프) |
| 5 | `05-quality-review` | `security-auditor` | 4 | src/ → 코드리뷰 + 교차검증(CVSS) + 보안감사 |
| 6 | `06-finalize-deliverables` | `docs-writer` | 4 | 모든 산출물 → deliverables/ (md/pdf/pptx/hwp) |
| 7 | `07-validate-standard` | `code-reviewer` | 0~4 | 하네스/프로젝트 루트 → 표준 준수 검증 리포트 |
| 8 | `08-harness` | `team-leader` | 0~4 | 검증 보완 가이드 — 감사(점수화·보완 가이드) / 신규 프로젝트 스텝 안내 |

각 스킬 상세: [.claude/skills/](.claude/skills/), 또는 [HARNESS-PROCESS-STANDARD.md §3](HARNESS-PROCESS-STANDARD.md) 참조. `07-validate-standard`는 선형 개발 단계가 아니라 setup 직후와 각 게이트 직전에 반복 실행하는 검증 스킬이다. `08-harness` 는 07 검증 결과를 받아 보완을 우선순위로 가이드하는 후속 스킬이다 (진행 중 프로젝트의 다음 행동 안내도 겸함).

---

## 6. 사람 결재 게이트 (3 곳, 우회 금지)

| 게이트 | 시점 | 결재자 |
|--------|------|--------|
| **G1 분석** | Skill 2 → 3 진입 전 | PM |
| **G2 설계** | Skill 3 → 4 진입 전 | PM + 아키텍트 |
| **G3 릴리즈** | Skill 5 → 6 진입 전 | PM + 정보보호 + 운영 |

---

## 7. 7 차원 자체 평가

| 차원 | 가중치 |
|------|-------|
| 완성도 | 20% |
| 추적성 | 15% |
| 보안 | 20% |
| 성능 | 10% |
| 가독성 | 15% |
| 표준 준수 | 10% |
| 테스트 커버리지 | 10% |

> 임계치 **90 / 100**. 미달 시 최저 차원 보완 → 재생성 (최대 5회) → 5회 후 PM Escalation.

---

## 8. 금융권 특화 룰 (유지)

- 금액 / 금리 / 환율 → **BigDecimal + RoundingMode** (double 금지) — ArchUnit 강제
- PII 평문 로그 절대 금지 → 마스킹 의무
- PII 컬럼 저장 → **AES-256-GCM** (ADR-005 의무)
- 시크릿 하드코딩 0 → L1 Hook (gitleaks) 차단
- 감사 로그 7년 보존 + 무결성 → ADR-006
- 외부 채널 인증 → mTLS / OAuth2 / SSH Key → ADR-008
- 메시지 무결성 → HMAC 또는 서명 → ADR-004
- 컷오버 후 **72 시간** 롤백 가능 / 레거시 **6 개월** 보존

규제 적용 시 별도 점검: 전자금융감독규정 / 개인정보보호법 / 신용정보법 / ISMS-P / PCI-DSS / 금융보안원 가이드.

---

## 9. sg-gw 적용 실측 (1 개월, 29 sprint)

| 항목 | 수치 |
|------|------|
| 적용 도메인 | 7 개 (HOFI / LCS / GIRO / ARS / Firm / RET / OpenBanking) |
| 외부 채널 | 5 종 (RabbitMQ / TCP / REST / SFTP) |
| Sub-agent | 16 종 (본 패키지에서 12종으로 정제 후 frontend 포함 13종으로 확장) |
| ADR | 38 건 |
| 테스트 | 1,300+ |
| Codex 교차 검증 차단 결함 | CVSS 9.8 / 8.4 / 7.4 / 6.5 (CRITICAL 1 + HIGH 2) |

---

## 10. 다음 단계

1. **[HARNESS-PROCESS-STANDARD.md](HARNESS-PROCESS-STANDARD.md)** 마스터 정의서 정독
2. **[PACKAGE-INDEX.md](PACKAGE-INDEX.md)** 전체 자산 카탈로그 훑어보기
3. **[WORKFLOW-GUIDE.md](WORKFLOW-GUIDE.md)** 개발자 실행 워크플로우 (01→06 실행 + 07 검증) 숙지
4. 신규 프로젝트에서 **§4 빠른 시작 5 분** 절차 실행 (자료 작성 시 [docs/HARNESS-BRIEFING.md](docs/HARNESS-BRIEFING.md) 참조)
5. 첫 스킬 `/01-plan-project` 실행하여 1:1 대화로 기획서 작성

---

## 11. 변경 이력

| 일자 | 버전 | 내용 |
|------|------|------|
| 2026-06-15 | 1.5 | `00-harness`→`08-harness` 리네임 + "07 검증 후 보완 가이드"로 재정의 (01~06 개발 → 07 검증 → 08 보완) |
| 2026-06-12 | 1.4 | frontend-developer 에이전트(13종)·언어별 등가 룰(§5.7)·AI 도구 중립성(§5.8)·판정 객관성 3계층 추가. |
| 2026-06-12 | 1.3 | 08-harness 검증 보완 가이드 스킬 + HARNESS-STATE 상태파일 템플릿 추가 (8 스킬·21 템플릿). |
| 2026-06-10 | 1.2 | 위협 모델(STRIDE)·공급망 보안(SBOM·OSS 라이선스·의존성 핀)·Sprint 회고·인시던트 postmortem 추가(템플릿 17→20). 사람 질의 생성 기준(§4.8 CQ1~CQ3)·근거 표기 범례(§4.9)·외부 egress 통제·신뢰 경계 룰·규정값 근거 태깅 반영. |
| 2026-06-10 | 1.1 | Skill 기반 표준 검증 스킬 `07-validate-standard` 추가. L1 우회 결재 증적 요구, L2 AI 감사 TODO 통과 방지. 파일 수 하드코딩 대신 검증 스킬 기준으로 전환. |
| 2026-05-29 | 1.0 | 초안 — sg-gw 1 개월 실증 기반 표준 하네스 패키지 작성. |

---

**작성**: PM Claude (Opus 4.7)
**근거**: sg-gw 프로젝트 29 sprint (AS-D1 ~ AS-D14)
**문의**: 본 패키지 적용 / 변경은 표준 위원회 (또는 PM) 결재 후 진행.
