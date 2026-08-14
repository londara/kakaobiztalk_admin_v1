# 교차 검증 리포트 (Cross-Validation) — Sprint {N}

> **작성 LLM**: {Codex / GPT-5 / Gemini} (1차 검토)
> **원 산출 LLM**: {Claude Opus 4.7} (1차 작성)
> **일자**: {YYYY-MM-DD}
> **대상**: Sprint {N} 산출물
> **판정**: APPROVE / CONDITIONAL / REJECT

---

## 1. 개요

본 리포트는 1차 LLM (Claude) 이 작성한 Sprint {N} 산출물을 별도 LLM ({Codex}) 이 독립적으로 검토하여 발견한 결함을 CVSS v3.1 점수로 평가한 결과를 정리한다.

## 2. CVSS v3.1 기준

| 등급 | 점수 | 처리 |
|------|------|------|
| CRITICAL | ≥ 9.0 | 즉시 차단 + 4시간 내 수정 |
| HIGH | 7.0~8.9 | 차단 + 본 Sprint 내 수정 |
| MEDIUM | 4.0~6.9 | 다음 Sprint 까지 수정 |
| LOW | 0.1~3.9 | 백로그 |

## 3. 발견 결함 목록

| ID | 위치 | 결함 요약 | CVSS | 벡터 (AV/AC/PR/UI/S/C/I/A) | 권장 조치 | Claude 검토 의견 |
|----|------|----------|------|---------------------------|---------|----------------|
| K-01 | `src/.../X.java:42` | (예: 시크릿 환경 변수 미사용) | **9.8** | AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H | 환경변수 + Vault 적용 | 동의 → ADR-NNN 추가 |
| K-02 | `src/.../Y.java:88` | (예: SQL injection 가능성) | **8.4** | AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N | PreparedStatement 전환 | 동의 → 수정 |
| K-03 | `src/.../Z.java:120` | (예: PII 평문 로그) | **7.4** | AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N | 마스킹 적용 | 동의 → 수정 |
| K-04 | `src/.../W.java:200` | (예: 세션 고정) | **6.5** | AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N | 로그인 후 세션 재생성 | 동의 → 수정 |

### 등급별 통계

| 등급 | 건수 |
|------|------|
| CRITICAL | 1 |
| HIGH | 2 |
| MEDIUM | 1 |
| LOW | 0 |
| **총** | **4** |

## 4. Claude 의 반박 / 보완 의견

| 결함 ID | Claude 의견 | 최종 합의 |
|--------|-----------|---------|
| K-01 | 동의 — 환경변수 적용 누락 | 수정 진행 |
| K-02 | 부분 동의 — JPA 사용으로 직접 SQL 없음, 하지만 정적 분석 권고 | 정적 분석 도입 |
| K-03 | 동의 — 마스킹 누락 | 수정 진행 |
| K-04 | 동의 — 로그인 후 세션 재생성 누락 | 수정 진행 |

## 5. False Positive 분석

| 결함 ID | False Positive 여부 | 근거 |
|--------|--------------------|-----|
| K-01 | N | 실제 시크릿 코드 노출 확인 |
| K-02 | N | JPA 외 직접 SQL 1건 발견 |
| K-03 | N | 로그 패턴 검증 결과 마스킹 누락 확인 |
| K-04 | N | 세션 처리 코드 미적용 확인 |

## 6. 차단 결정

- CRITICAL 1 건 / HIGH 2 건 발견 → **Skill 4 환송 (REJECT)**
- 수정 후 재검증 일정: {YYYY-MM-DD}

## 7. 본 검증의 한계

- LLM 평가는 정적 코드 + 컨텍스트 기반이며, 런타임 동적 검증은 별도 도구 (DAST) 필요
- CVSS 점수는 LLM 추정이며, 보안 전문가 검토와 차이 있을 수 있음
- 모델 편향 회피를 위해 분기별 1회 또 다른 LLM (예: Gemini) 으로 3차 검증 권고

## 8. 후속 조치

- [ ] CRITICAL/HIGH 수정 완료 후 재검증 (3 일 내)
- [ ] False Positive 룰을 정적 분석 도구에 등록
- [ ] 신규 패턴 발견 시 본 표준의 강제 룰에 반영

---

**서명**
| 일자 | LLM | 비고 |
|------|-----|------|
| {YYYY-MM-DD} | {Codex} | 1차 독립 검토 |
| {YYYY-MM-DD} | {Claude} | 검토 의견 회신 |
| {YYYY-MM-DD} | PM | 최종 확인 |
