# Parity 리포트 — Sprint {N} (마이그레이션 프로젝트 한정)

> **작성**: qa-engineer
> **일자**: {YYYY-MM-DD}
> **대상 모듈**: {모듈명}
> **판정**: PASS / PARTIAL / FAIL

---

## 1. 개요

레거시 시스템 (예: C / COBOL / 구버전) 의 동일 입력에 대한 출력과 신규 Java/Python/Go 구현의 출력을 **바이트 단위로 비교**하여 동치 여부를 검증한다.

| 항목 | 내용 |
|------|------|
| 레거시 시스템 | {예: C iBLS} |
| 신규 시스템 | {예: Java Spring Boot 3.4} |
| 비교 도구 | `cmp`, `diff -u`, `xxd`, `hexdump -C` |
| 테스트 케이스 수 | {NN} |
| 합격 기준 | 100% byte-equal (또는 승인된 마스킹 규칙) |

## 2. 테스트 케이스 목록

| Case ID | 시나리오 | 입력 fixture | 레거시 출력 | 신규 출력 | 결과 |
|---------|---------|--------------|-----------|---------|------|
| TC-{N}-001 | {정상 거래} | `fixtures/case-001.bin` | `expected/case-001.bin` | `actual/case-001.bin` | PASS |
| TC-{N}-002 | {경계값} | `fixtures/case-002.bin` | `expected/case-002.bin` | `actual/case-002.bin` | PASS |
| TC-{N}-003 | {예외 거래} | `fixtures/case-003.bin` | `expected/case-003.bin` | `actual/case-003.bin` | DIFF (허용) |
| TC-{N}-004 | {대량 입력} | `fixtures/case-004.bin` | `expected/case-004.bin` | `actual/case-004.bin` | PASS |
| TC-{N}-005 | {경계 시간} | `fixtures/case-005.bin` | `expected/case-005.bin` | `actual/case-005.bin` | PASS |

## 3. 차이 발견 시 분석

### TC-{N}-003: DIFF (허용)
- 차이 위치: 오프셋 200~204 (날짜/시간 필드)
- 차이 사유: 신규 시스템은 KST 명시, 레거시는 UTC. **마스킹 규칙 승인 (ADR-NNN)**
- 처리: `parity-mask-rules.yaml` 에 등록 → 본 위치 차이 무시

### 마스킹 규칙 예시
```yaml
rules:
  - case: TC-*
    offset: 200-204
    reason: KST↔UTC 변환 (ADR-NNN)
    approved_by: PM
    approved_date: 2026-05-29
```

## 4. 통계

| 지표 | 값 |
|------|----|
| 전체 케이스 | {NN} |
| 바이트 동일 | {NN} ({%}) |
| 마스킹 후 동일 | {N} |
| 미해결 차이 | 0 |
| Parity 통과율 | 100% |

## 5. 성능 비교 (참고)

| 케이스 | 레거시 응답 | 신규 응답 | 차이 |
|--------|------------|---------|------|
| TC-001 | {120 ms} | {95 ms} | -21% |
| TC-002 | {300 ms} | {250 ms} | -17% |

## 6. 환경

| 항목 | 레거시 | 신규 |
|------|--------|------|
| OS | {AIX 7.1} | {Linux RHEL 9} |
| Runtime | {C / Pro*C} | {Java 17 / Spring Boot 3.4} |
| DB | {Oracle 11g} | {PostgreSQL 16} |
| Charset | {EUC-KR} | {UTF-8 + EUC-KR 변환} |

## 7. 한계 및 가정

- 시간 의존 필드 (현재 시각 / 시퀀스) 는 마스킹 처리
- 부동소수점 차이는 BigDecimal 변환 결과 검증으로 대체
- 외부 시스템 응답 의존 시 동일 mock 사용

## 8. 후속 권고

- [ ] 운영 환경 일배치 데이터로 추가 fixture 보강
- [ ] 마스킹 규칙 분기별 재검토
- [ ] 부하 환경 parity 추가 (1만 건 동시 입력)

---

**qa-engineer 서명**
| 일자 | 에이전트 | 비고 |
|------|---------|------|
| {YYYY-MM-DD} | qa-engineer | 자동 실행 |
