# 개발계획서 — {프로젝트명}

> **버전**: 1.0
> **작성일**: {YYYY-MM-DD}
> **선행**: [REQUIREMENTS-SPEC.md](../requirements/REQUIREMENTS-SPEC.md) (G1 결재)
> **상태**: DRAFT / REVIEWED / **APPROVED (G2)**

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | {프로젝트명} |
| 기간 | {YYYY-MM-DD ~ YYYY-MM-DD} |
| Sprint 수 | {N} (Sprint = {1~2} 주) |
| 팀 구성 | {Build Team / Validation Team / Ops Team} (§5 참조) |
| 적용 표준 | harness-standards v1.0 (Team-with-Leader) |

## 2. 기술 스택 (ADR-001 결정)

| 영역 | 선정 | 근거 ADR |
|------|------|---------|
| 언어 / 런타임 | {Java 17} | [ADR-001](adr/ADR-001-tech-stack.md) |
| 프레임워크 | {Spring Boot 3.4} | ADR-001 |
| DB | {PostgreSQL 16} | ADR-001 |
| 메시징 | {RabbitMQ 3.13} | ADR-001 |
| 빌드 | {Maven 3.9} | ADR-001 |
| CI/CD | {GitHub Actions} | ADR-001 |
| 컨테이너 | {Docker / K8s 1.30} | ADR-001 |

상세 비교는 ADR-001 참조 (후보 ≥ 2 안 비교 포함).

## 3. 아키텍처 개요

[architecture-overview.md](architecture-overview.md) 참조.

핵심 컴포넌트:
- {예: API Gateway}
- {예: 거래 처리 도메인}
- {예: 정산 도메인}
- {예: 외부 어댑터 (MQ, REST)}

## 4. 일정 (Sprint Plan)

| Sprint | 기간 | 범위 (Epic) | DoD |
|--------|------|------------|-----|
| Sprint 1 | {Wk 1-2} | 스켈레톤 + 인증/인가 | FR-AUTH-001~003 |
| Sprint 2 | {Wk 3-4} | 거래 처리 코어 | FR-TX-001~003 |
| Sprint 3 | {Wk 5-6} | 외부 연동 | FR-EXT-001 |
| Sprint 4 | {Wk 7-8} | 운영 / 배치 | FR-OPS-001 |
| Sprint 5 | {Wk 9-10} | QA + 안정화 | 7차원 ≥ 90 |
| Sprint 6 | {Wk 11-12} | 컷오버 준비 | G3 통과 |

## 5. 팀 편성 (Team-with-Leader)

| 팀 | 구성 | Leader | Write 디렉터리 |
|----|------|--------|----------------|
| Build Team | backend-developer × 2, adapter-builder, code-reviewer | code-reviewer | `src/`, `mapping/port-log/`, `reviews/` |
| Validation Team | qa-engineer, security-auditor, code-reviewer | security-auditor | `qa/`, `security/`, `parity/` |
| Ops Team | shell-ops-porter, docs-writer, db-migration-engineer | docs-writer | `target/ops/`, `docs/`, `mapping/db/` |

PM 1 인 (인간) — 팀 간 병렬 조율만, 팀 내부는 Leader 자율.

## 6. LLM 모델 배정

| 에이전트 | 모델 | 이유 |
|---------|------|------|
| architect | Opus / GPT-5 | 추론 깊이 필수 |
| backend-developer | Sonnet / GPT-5 mini | 1:1 구현 / CRUD |
| code-reviewer | Opus | 코드 패턴 인식 |
| security-auditor | Opus | 보안 추론 |
| docs-writer | Haiku / GPT-5 nano | 단순 변환 |
| **교차 검증 (Skill 5)** | 다른 벤더 | 모델 편향 회피 |

## 7. 인력 계획

| 역할 | 인원 | 주 책임 |
|------|------|--------|
| PM | 1 (인간) | 결재 / 외부 소통 / 팀 간 조율 |
| 아키텍트 | 1 (인간) | 설계 검토 / ADR 승인 |
| 개발자 | {N} (인간) | 에이전트 출력 검토 / 도메인 지식 |
| QA | {N} (인간) | 테스트 계획 검토 / E2E 시나리오 |
| 운영 | {N} (인간) | 컷오버 / 모니터링 |
| AI 에이전트 | 12~16 (LLM) | 자율 구현 / 자체 평가 |

## 8. 위험 관리

[risk-register.md](risk-register.md) 참조 (≥ 10건).

핵심 위험 TOP 3:
1. {위험 요약 + 영향 + 대응}
2. {위험 요약}
3. {위험 요약}

## 9. 품질 목표

| 지표 | 목표 |
|------|------|
| 단위 테스트 커버리지 (라인) | ≥ 80% |
| 단위 테스트 커버리지 (브랜치) | ≥ 70% |
| 7 차원 자체 평가 | ≥ 90 |
| CVSS ≥ 7.0 결함 | 0 건 (릴리즈 게이트) |
| ADR 누적 | ≥ 10 (설계 변경 추적) |

## 10. 의사소통 / 결재

| 게이트 | 시점 | 결재자 | 산출물 |
|--------|------|--------|--------|
| G1 분석 | Skill 2 완료 | PM | REQUIREMENTS-SPEC.md |
| G2 설계 | Skill 3 완료 | PM + 아키텍트 | 본 문서 + TEST-PLAN |
| Sprint 게이트 | 매 Sprint 종료 | PM | SPRINT-N-LOG |
| G3 릴리즈 | Skill 5 완료 | PM + 정보보호 + 운영 | 모든 검증 리포트 |

## 11. 백업 / 롤백 정책

- 컷오버 후 {72시간} 내 긴급 롤백 가능 상태 유지
- 레거시 시스템 / 인프라 최소 {6개월} 보존
- DB 양방향 이관 쿼리 유지 ({migration/etl/})

## 12. 금융권 추가 항목 (해당 시)

| 항목 | 적용 | 비고 |
|------|------|------|
| 전자금융감독규정 대응 | Y | 별도 ADR 의무 |
| ISMS-P 인증 대응 | Y | 인증 일정 별도 |
| PII 암호화 | Y | AES-256-GCM (ADR-005) |
| 키 관리 | Y | HSM 또는 KMS (ADR-007) |
| 감사 로그 | Y | 7년 보존 (ADR-006) |

---

**G2 결재 (설계 게이트)**
| 일자 | 결재자 | 의견 | 상태 |
|------|--------|------|------|
| {YYYY-MM-DD} | PM | | {APPROVED/REJECTED/PENDING} |
| {YYYY-MM-DD} | 아키텍트 | | {APPROVED/REJECTED/PENDING} |
