---
name: 03-draft-dev-plan
description: 구체화된 요구사항을 바탕으로 개발계획서·테스트계획서·아키텍처 초안·ADR-001을 작성. 기술 스택 후보 2+ 비교 후 선정 근거 ADR로 기록.
when_to_use: Skill 2 완료 후 (REQUIREMENTS-SPEC.md 결재 완료). 설계 게이트 진입 전.
phase: 2
lead_agent: architect
support_agents:
  - data-model-designer
  - qa-engineer
  - security-auditor
  - trace-mapper
outputs:
  - docs/design/DEV-PLAN.md
  - docs/design/TEST-PLAN.md
  - docs/design/architecture-overview.md
  - docs/design/threat-model.md
  - docs/design/adr/ADR-001-tech-stack.md
  - docs/design/risk-register.md
---

# Skill 03 — 개발계획서 + 테스트계획서 작성

> **목적**: 요구사항 → Epic / Sprint / Task 분해 + 기술 스택 결정 + 테스트 전략 수립.
> **소요**: 1~3 일
> **선행**: Skill 2 (REQUIREMENTS-SPEC.md 결재)
> **후속**: Skill 4 (구현)

---

## 0. 담당 에이전트

| 역할 | 에이전트 | 책임 |
|------|----------|------|
| Lead | `architect` | 기술 스택 후보 비교, 아키텍처 초안, ADR-001, Sprint DAG 통합 |
| Support | `data-model-designer` | 도메인 모델 / 엔티티 / DTO / 레거시 타입 매핑 설계 |
| Support | `qa-engineer` | TEST-PLAN, 커버리지 목표, E2E/Parity/부하 테스트 전략 작성 |
| Support | `security-auditor` | 보안 ADR 후보, 키 관리, PII, 외부 채널 인증 요구 검토 |
| Support | `trace-mapper` | 요구사항이 Sprint task / 테스트 / ADR로 추적되는지 확인 |

> Lead 에이전트는 설계 변경 판단을 ADR로 남기고, Support 검토 결과를 DEV-PLAN / TEST-PLAN에 통합한다.

## 1. 동작 절차

```
[A] 요구사항 분해 → Epic → Sprint → Task DAG
        │
        ▼
[B] 기술 스택 후보 비교 (≥ 2 안)
        │   - 언어 / 프레임워크 / DB / 메시징 / CI-CD
        │   - 비교표 + 선정 근거 → ADR-001 자동 생성
        ▼
[C] 아키텍처 초안 (Mermaid 다이어그램)
        │   - 컴포넌트 / 의존 / 외부 채널 / 데이터 흐름
        ▼
[C-2] 위협 모델링 (STRIDE + 공격 표면) → 완화책을 보안 ADR로 연결
        ▼
[D] 테스트 전략 수립
        │   - 단위 / 통합 / E2E / Parity (마이그레이션 시)
        │   - 커버리지 목표 / 7 차원 평가 임계치
        ▼
[E] 인력·일정·위험 (Sprint plan + Risk Register ≥ 10건)
        │
        ▼
[F] PM 결재 → 설계 게이트 (G2) 통과
```

## 2. 기술 스택 결정 — ADR-001

후보 ≥ 2 안 비교 의무. 각 후보에 다음 차원 평가.

| 차원 | 가중치 |
|------|-------|
| 팀 숙련도 | 25% |
| 생태계 / 커뮤니티 | 15% |
| 라이선스 / 비용 | 15% |
| 성능 (요구 NFR 대비) | 15% |
| 보안 (취약점 이력) | 15% |
| 운영 / 모니터링 | 15% |

> **근거**: 위 6차원·가중치와 "차점안 차이 < 10%" 룰은 `[근거:sg-gw회고·조정가능]`(HARNESS-PROCESS-STANDARD §4.9). 선정 결과·근거는 ADR-001에 기록한다.
> **결과**: 최고 점수 안을 선정하되, **차점안과 차이 < 10 %** 면 PM 결재 의무.

## 3. 테스트 계획서 표준 구성

| 항목 | 기본값 |
|------|-------|
| 단위 테스트 커버리지 | 라인 ≥ 80% / 브랜치 ≥ 70% |
| 통합 테스트 | 외부 의존 Mock 정책 명시 |
| E2E 테스트 | 핵심 시나리오 TOP 5 |
| 보안 테스트 | OWASP Top 10 자동 검증 / 시크릿 스캔 / SAST |
| 7 차원 자체 평가 임계치 | 90 / 100 |
| Parity 테스트 | (마이그레이션 한정) 바이트 단위 동치 |
| 부하 테스트 | NFR-PERF SLA 의 2배 부하 |

> **근거**: 커버리지 80%/70%·E2E TOP5·부하 2배는 `[근거:업계관례]`(프로젝트 조정 가능); 7차원 임계 90은 `[근거:sg-gw회고·조정가능]`; OWASP/SAST/시크릿 스캔은 `[근거:외부표준]`. (§4.9)

## 3.5 위협 모델링 (STRIDE · 공격 표면)

설계 단계에서 **위협 모델**을 1건 작성한다. (G2 전 필수)

- **방법**: 신뢰 경계를 가로지르는 모든 데이터 흐름에 **STRIDE 6범주**(Spoofing/Tampering/Repudiation/Info Disclosure/DoS/EoP) 검토 + **공격 표면** 분석.
- **연결**: 각 위협의 완화책을 보안 ADR(ADR-004~008) / NFR-SEC 와 매핑 — orphan 위협 0.
- **차단 연계**: CVSS ≥ 7.0 상응 미해결 위협은 G2/G3 차단 조건.
- **갱신**: 아키텍처·외부 채널·PII 처리 변경 시 갱신(변경은 ADR로 이력).
- **산출물**: `docs/design/threat-model.md` (템플릿 `templates/design/THREAT-MODEL.template.md`)

## 4. 위험 등록부 (Risk Register)

≥ 10 건 식별 의무. `[근거:sg-gw회고·조정가능]` (최소 건수는 자체안 — §4.9). 각 위험은 다음 양식.

```
ID: RISK-NNN
제목: ...
영역: 기술 / 일정 / 인력 / 외부 / 보안
영향: H / M / L
발생 확률: H / M / L
대응 전략: 회피 / 완화 / 전가 / 수용
대응 계획: ...
담당자: ...
모니터링 시점: ...
```

## 5. 입력

- `docs/requirements/REQUIREMENTS-SPEC.md` (G1 결재 완료)
- `docs/requirements/requirements-matrix.csv`
- Skill 1 의 PROJECT-PROPOSAL.md (제약 조건 참조)

## 6. 출력 산출물

| 산출물 | 경로 |
|--------|------|
| 개발계획서 | `docs/design/DEV-PLAN.md` |
| 테스트계획서 | `docs/design/TEST-PLAN.md` |
| 아키텍처 개요 | `docs/design/architecture-overview.md` |
| 위협 모델 (STRIDE) | `docs/design/threat-model.md` |
| ADR-001 (스택 결정) | `docs/design/adr/ADR-001-tech-stack.md` |
| 위험 등록부 | `docs/design/risk-register.md` |
| Sprint 1 task list | `docs/design/sprint-1-tasks.md` |

템플릿: `templates/design/DEV-PLAN.template.md`, `TEST-PLAN.template.md`, `ADR.template.md`, `THREAT-MODEL.template.md`

## 7. 완료 기준 (DoD)

- [ ] DEV-PLAN.md / TEST-PLAN.md / architecture-overview.md 작성
- [ ] ADR-001 작성 (후보 ≥ 2 안 비교 포함)
- [ ] 위협 모델(STRIDE + 공격 표면) 작성 — orphan 위협 0, 완화책 ADR/NFR-SEC 매핑
- [ ] 위험 등록부 ≥ 10 건
- [ ] Sprint 1 task list 확정 (PM + Leader 합의)
- [ ] **G2 설계 게이트 통과** (PM + 아키텍트 결재)

## 8. 금융권 추가 의무 ADR

다음 영역은 별도 ADR 작성 의무.

| 영역 | ADR 예시 |
|------|---------|
| 트랜잭션 모델 | ADR-002-transaction-boundary |
| 영속성 전략 | ADR-003-persistence-strategy |
| 메시지 무결성 | ADR-004-message-integrity |
| PII 암호화 | ADR-005-pii-encryption |
| 감사 로그 | ADR-006-audit-logging |
| 키 관리 | ADR-007-key-management |
| 외부 채널 인증 | ADR-008-channel-auth |
| 재시도 / 멱등성 | ADR-009-retry-idempotency |

## 9. 1:1 대화 원칙

- 후보 안 비교 시 **PM 의견 선청취 후 평가**
- 차점안과 차이 < 10 % 면 **반드시 PM 결재 요청**
- 7 차원 평가 임계치 90 외 다른 값 원하면 협의
- **질의 기준**: 스택 tie-break(차점안 차이 < 10%)·게이트 결재는 **CQ3(사람 권한 필요)**. (기준: HARNESS-PROCESS-STANDARD §4.8)
