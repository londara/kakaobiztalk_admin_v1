# 테스트계획서 — {프로젝트명}

> **버전**: 1.0
> **작성일**: {YYYY-MM-DD}
> **선행**: [REQUIREMENTS-SPEC.md](../requirements/REQUIREMENTS-SPEC.md), [DEV-PLAN.md](DEV-PLAN.md)
> **상태**: DRAFT / REVIEWED / **APPROVED (G2)**

---

## 1. 테스트 전략

### 1.1 테스트 피라미드

```
        ┌────────────┐
        │   E2E 5%   │
       ┌┴────────────┴┐
       │  통합 25%    │
      ┌┴───────────────┴┐
      │   단위 70%      │
      └─────────────────┘
```

### 1.2 테스트 유형

| 유형 | 도구 | 책임 | 빈도 |
|------|------|------|------|
| 단위 테스트 | {JUnit 5 / Mockito} | 개발 에이전트 | 매 커밋 |
| 통합 테스트 | {Testcontainers / @SpringBootTest} | 개발 + QA | 매 PR |
| E2E 테스트 | {Playwright / Cypress / REST-Assured} | QA | 매 Sprint |
| 컴포넌트 테스트 (프론트) | {Jest / Vitest / Testing Library} | frontend-developer + QA | 매 커밋 |
| 접근성 테스트 (프론트) | {axe-core / lighthouse} — WCAG 2.1 AA | frontend-developer → QA 검증 | 매 PR |
| 부하 테스트 | {JMeter / k6 / Gatling} | QA | Sprint 종료 |
| 보안 테스트 | {OWASP ZAP / gitleaks / Snyk} | security-auditor | 매 PR + L2 Hook |
| Parity 테스트 (해당 시) | `scripts/parity-check.sh` | qa-engineer | 매 Sprint |

## 2. 커버리지 목표

| 지표 | 목표 |
|------|------|
| 라인 커버리지 | ≥ 80% |
| 브랜치 커버리지 | ≥ 70% |
| 메서드 커버리지 | ≥ 85% |
| 핵심 도메인 클래스 | ≥ 95% |

> 커버리지 도구: {JaCoCo / SonarQube}

## 3. 7 차원 자체 평가 (sg-gw 검증)

| 차원 | 가중치 | 평가 기준 |
|------|-------|----------|
| 완성도 | 20% | Sprint task 100% 완료 |
| 추적성 | 15% | // source: / ADR / 에이전트명 |
| 보안 | 20% | 하드코딩 0 / PII 마스킹 / Hook 통과 |
| 성능 | 10% | NFR-PERF SLA 충족 |
| 가독성 | 15% | Javadoc / Mermaid / 표 정렬 |
| 표준 준수 | 10% | 디렉터리 격리 위반 0 / ADR 누락 0 |
| 테스트 커버리지 | 10% | TEST-PLAN 기준 충족 |

- 임계치: **90 / 100**
- 미달 시: 최저 차원 보완 → 재생성 → 최대 5회
- 5회 후도 미달 → PM Escalation

## 4. 교차 검증 (Cross-Validation)

| 항목 | 내용 |
|------|------|
| 시점 | Sprint 종료 시 (Skill 5) |
| 도구 | 다른 LLM 벤더 (Codex / GPT-5 / Gemini) |
| 산출물 | `reviews/cross-validation-N.md` |
| CVSS 등급 | CVSS v3.1 |
| 차단 임계 | CVSS ≥ 7.0 (HIGH 이상) |

## 5. 보안 테스트 (Security Hook 3 단계)

| 단계 | 도구 | 시점 |
|------|------|------|
| L1 | gitleaks / trufflehog | git pre-commit |
| L2 | security-auditor + SAST + 의존성 스캔 + **SBOM·OSS 라이선스** | CI / PR |
| L3 | prod-gate 체크리스트 | 운영 배포 직전 |

### OWASP Top 10 검증
| ID | 항목 | 검증 방법 |
|----|------|---------|
| A01 | Broken Access Control | E2E + 정적 분석 |
| A02 | Cryptographic Failures | 보안 감사 |
| A03 | Injection | SAST + 단위 테스트 |
| A04 | Insecure Design | 아키텍처 리뷰 + 위협 모델(STRIDE) |
| A05 | Security Misconfiguration | 설정 점검 |
| A06 | Vulnerable Components | 의존성 스캔 + SBOM · OSS 라이선스 |
| A07 | Identification & Auth Failures | E2E 테스트 |
| A08 | Software & Data Integrity Failures | HMAC / 서명 |
| A09 | Logging & Monitoring Failures | 감사 로그 점검 |
| A10 | SSRF | 정적 분석 |

## 6. 부하 테스트 시나리오

| 시나리오 | 부하 | 기간 | 합격 기준 |
|---------|------|------|---------|
| 거래 등록 정상 부하 | {500 RPS} | 30 분 | P95 < 500ms / 에러율 < 0.1% |
| 거래 등록 피크 부하 | {2,000 RPS} | 10 분 | P95 < 1s / 에러율 < 1% |
| 일 처리 능력 | {1,000만 건 / 일} | 24 시간 | NFR-PERF-02 충족 |

## 7. Parity 테스트 (마이그레이션 프로젝트 한정)

| 항목 | 내용 |
|------|------|
| 정의 | 동일 입력에 대한 레거시 출력 vs 신규 출력의 바이트 단위 동치 |
| 도구 | `scripts/parity-check.sh`, `cmp`, `diff -u` |
| 책임 | qa-engineer |
| 케이스 수 | 모듈당 ≥ 5 (Phase 3 DoD) |
| 합격 기준 | 100% (또는 승인된 마스킹 규칙으로 합의) |

## 8. 테스트 환경

| 환경 | 용도 | 데이터 |
|------|------|--------|
| local | 개발자 PC | 모킹 / fixture |
| dev | 개발 서버 | 익명화된 운영 데이터 |
| staging | QA / 통합 | 운영과 동일 구성 + 일부 운영 데이터 |
| prod | 운영 | 실 데이터 |

> **원칙**: 실 PII 데이터는 dev/staging 에 절대 반입 금지. 익명화 또는 합성 데이터 사용.

## 9. 결함 관리

| 등급 | CVSS | 처리 |
|------|------|------|
| CRITICAL | ≥ 9.0 | 4시간 내 수정 |
| HIGH | 7.0~8.9 | 본 Sprint 내 수정 |
| MEDIUM | 4.0~6.9 | 다음 Sprint 까지 |
| LOW | < 4.0 | 백로그 |

## 10. 산출물

| 산출물 | 시점 | 형식 |
|--------|------|------|
| 단위 테스트 결과 | 매 CI | xml + html |
| 통합 테스트 리포트 | 매 PR | md |
| Sprint QA 리포트 | Sprint 종료 | md |
| 부하 테스트 결과 | 안정화 Sprint | md + png 그래프 |
| Parity 리포트 | 매 Sprint (해당 시) | md + csv |
| 보안 감사 리포트 | Skill 5 | md + pdf |
| 교차 검증 리포트 | Skill 5 | md |

## 11. 금융권 추가 테스트

| 테스트 | 목적 |
|--------|------|
| 거래 무결성 | 메시지 위변조 방지 (HMAC) |
| 멱등성 | 동일 요청 중복 처리 0 |
| 트랜잭션 경계 | 분산 트랜잭션 / 보상 트랜잭션 |
| 금액 정밀도 | BigDecimal 라운딩 검증 |
| PII 마스킹 | 로그 / 응답 / 감사 |
| 키 회전 | 키 만료 / 교체 시나리오 |
| 감사 로그 | 7년 보존 + 무결성 |

---

**G2 결재 (설계 게이트)**
| 일자 | 결재자 | 의견 | 상태 |
|------|--------|------|------|
| {YYYY-MM-DD} | PM | | {APPROVED/REJECTED/PENDING} |
| {YYYY-MM-DD} | QA Leader | | {APPROVED/REJECTED/PENDING} |
