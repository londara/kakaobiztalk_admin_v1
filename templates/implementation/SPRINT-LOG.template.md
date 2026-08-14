# Sprint {N} Log

> **Sprint 번호**: {N}
> **기간**: {YYYY-MM-DD ~ YYYY-MM-DD}
> **Leader**: {Leader 에이전트명}
> **PM 결재 일자**: {YYYY-MM-DD}
> **상태**: IN_PROGRESS / COMPLETED / ESCALATED

---

## 1. Sprint 목표

본 Sprint 의 핵심 Epic / 목표:
- {Epic 1}
- {Epic 2}

대상 REQ-ID:
- FR-XXX-001 ~ FR-XXX-005
- NFR-PERF-01

## 2. Task 분해 (DAG)

| Task ID | 제목 | 담당 에이전트 | 모델 | 의존 | 상태 |
|---------|------|--------------|------|------|------|
| T-{N}-01 | {task 제목} | backend-developer-1 | Sonnet | - | DONE |
| T-{N}-02 | {task 제목} | backend-developer-2 | Sonnet | T-{N}-01 | DONE |
| T-{N}-03 | {task 제목} | adapter-builder | Sonnet | - | DONE |
| T-{N}-04 | {task 제목} | qa-engineer | Haiku | T-{N}-01, 03 | DONE |
| T-{N}-05 | {task 제목} | code-reviewer | Opus | T-{N}-01~04 | DONE |

## 3. Sprint 결과

### 3.1 완료 항목
- [x] T-{N}-01
- [x] T-{N}-02

### 3.2 이월 항목
- [ ] T-{N}-NN ({이월 사유})

### 3.3 발생 ADR
| ADR ID | 제목 | 사유 |
|--------|------|------|
| ADR-{NNN} | {제목} | {설계 변경 / 신규 결정} |

## 4. 7 차원 자체 평가

| 차원 | 점수 (0~100) | 가중 점수 | 코멘트 |
|------|------------|----------|--------|
| 완성도 (20%) | 95 | 19 | task 100% |
| 추적성 (15%) | 90 | 13.5 | // source 누락 0 |
| 보안 (20%) | 92 | 18.4 | Hook L1/L2 통과 |
| 성능 (10%) | 88 | 8.8 | NFR-PERF-01 충족 |
| 가독성 (15%) | 90 | 13.5 | Javadoc 표준 준수 |
| 표준 준수 (10%) | 95 | 9.5 | 디렉터리 격리 0 위반 |
| 테스트 커버리지 (10%) | 85 | 8.5 | 라인 82% / 브랜치 73% |
| **종합** | | **91.2** | **PASS (≥ 90)** |

### 재시도 횟수
- 본 Sprint 재생성 횟수: {0~5}
- 5회 후 미달 여부: N/A

## 5. CI / 테스트 결과

| 항목 | 결과 |
|------|------|
| 빌드 | PASS / FAIL |
| 단위 테스트 | {NNN}/{NNN} PASS |
| 통합 테스트 | {NN}/{NN} PASS |
| Parity (해당 시) | {NN}/{NN} PASS |
| 커버리지 (라인) | 82% |
| 커버리지 (브랜치) | 73% |

## 6. 알람 발생 이력

| 시점 | 이벤트 | 채널 |
|------|--------|------|
| {YYYY-MM-DD HH:MM} | Sprint 시작 | TEAM_CHANNEL |
| {YYYY-MM-DD HH:MM} | T-{N}-NN 7차원 < 90 | LEADER_BROADCAST |
| {YYYY-MM-DD HH:MM} | Sprint 종료 | TEAM_CHANNEL |

## 7. 위험 / 이슈

| ID | 내용 | 영향 | 대응 |
|----|------|------|------|
| ISSUE-001 | {이슈 요약} | M | {대응 방안} |

## 8. 다음 Sprint 계획

- 이월 task: T-{N}-NN
- 우선 처리 Epic: {다음 Epic}
- 추가 위험 모니터링: {위험 ID}

---

**PM 결재**
| 일자 | 결재자 | 의견 | 상태 |
|------|--------|------|------|
| {YYYY-MM-DD} | PM | | {APPROVED/REJECTED/PENDING} |
