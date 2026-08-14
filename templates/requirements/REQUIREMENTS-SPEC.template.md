# 요구사항 정의서 — {프로젝트명}

> **버전**: 1.0
> **작성일**: {YYYY-MM-DD}
> **선행**: [PROJECT-PROPOSAL.md](../planning/PROJECT-PROPOSAL.md), [BUSINESS-REQUIREMENTS.md](../planning/BUSINESS-REQUIREMENTS.md)
> **추적 매트릭스**: [requirements-matrix.csv](requirements-matrix.csv)
> **질의 로그**: [questions-log.md](questions-log.md)
> **상태**: DRAFT / REVIEWED / **APPROVED (G1)**

---

## 1. 개요

본 문서는 Skill 1 의 기획서 / 비즈니스 요구사항을 Functional / Non-Functional / Constraint 3 분류로 구체화한 요구사항 정의서다.

### 1.1 분류 체계

| 분류 | 접두어 | 설명 |
|------|-------|------|
| Functional Requirement | `FR-` | 시스템이 무엇을 해야 하는가 |
| Non-Functional Requirement | `NFR-<영역>-` | 어떤 품질 속성을 가져야 하는가 |
| Constraint | `CONST-<영역>-` | 절대 변경 불가 제약 |
| Use Case | `UC-` | 시나리오 단위 사용 흐름 |

### 1.2 우선순위 (MoSCoW)

| 등급 | 의미 |
|------|------|
| Must | 본 릴리즈 필수 |
| Should | 본 릴리즈 권고 |
| Could | 가능하면 본 릴리즈, 아니면 다음 |
| Won't | 본 릴리즈 제외 (다음 또는 영구) |

---

## 2. Functional Requirements

### 2.1 사용자 인증 / 인가
| REQ-ID | 요구사항 | 우선순위 | 검증 방법 |
|--------|---------|---------|----------|
| FR-AUTH-001 | 사용자는 이메일 + 비밀번호로 로그인할 수 있다 | Must | E2E 테스트 |
| FR-AUTH-002 | 관리자는 MFA 로그인을 강제할 수 있다 | Must | E2E 테스트 |
| FR-AUTH-003 | 세션은 30분 비활성 시 자동 만료된다 | Must | 단위 테스트 |

### 2.2 거래 처리
| REQ-ID | 요구사항 | 우선순위 | 검증 방법 |
|--------|---------|---------|----------|
| FR-TX-001 | {거래 등록 요구} | Must | 통합 테스트 |
| FR-TX-002 | {거래 조회 요구} | Must | E2E 테스트 |
| FR-TX-003 | {거래 취소 요구 — BR-001 참조} | Must | 통합 테스트 |

### 2.3 외부 연동
| REQ-ID | 요구사항 | 우선순위 | 검증 방법 |
|--------|---------|---------|----------|
| FR-EXT-001 | {외부 연동 요구} | Must | 통합 테스트 |

### 2.4 운영 / 관리
| REQ-ID | 요구사항 | 우선순위 | 검증 방법 |
|--------|---------|---------|----------|
| FR-OPS-001 | {운영 요구} | Should | 운영 테스트 |

---

## 3. Non-Functional Requirements

### 3.1 NFR-PERF (성능)
| REQ-ID | 요구사항 | 측정 |
|--------|---------|------|
| NFR-PERF-01 | 거래 등록 응답 P95 < 500ms | 부하 테스트 |
| NFR-PERF-02 | 일 처리 능력 1,000만 건 | 부하 테스트 |
| NFR-PERF-03 | 동시 사용자 500명 처리 | 부하 테스트 |

### 3.2 NFR-SEC (보안)
| REQ-ID | 요구사항 | 검증 |
|--------|---------|------|
| NFR-SEC-AUTH | OAuth2 + JWT (RS256) | 보안 감사 |
| NFR-SEC-PII | PII 컬럼 AES-256-GCM 암호화 | 보안 감사 / DB 점검 |
| NFR-SEC-TX | 거래 메시지 HMAC 무결성 | 단위 테스트 |
| NFR-SEC-LOG | 시크릿 / PII 평문 로그 금지 | 정적 분석 |

### 3.3 NFR-AVAIL (가용성)
| REQ-ID | 요구사항 | 측정 |
|--------|---------|------|
| NFR-AVAIL-01 | 99.9% SLA (월 다운타임 ≤ 43분) | 모니터링 |
| NFR-AVAIL-02 | RTO ≤ 1시간 | DR 훈련 |
| NFR-AVAIL-03 | RPO ≤ 5분 | 백업 정책 |

### 3.4 NFR-SCALE (확장성)
| REQ-ID | 요구사항 | 검증 |
|--------|---------|------|
| NFR-SCALE-01 | 수평 확장 가능 (stateless) | 아키텍처 리뷰 |
| NFR-SCALE-02 | DB 샤딩 가능한 키 설계 | 아키텍처 리뷰 |

### 3.5 NFR-OPS (운영)
| REQ-ID | 요구사항 | 검증 |
|--------|---------|------|
| NFR-OPS-AUDIT | 감사 로그 7년 보존 + 무결성 | 운영 감사 |
| NFR-OPS-BACKUP | 일 1회 풀백업 + 시간 단위 증분 | 백업 점검 |

---

## 4. Constraints

| REQ-ID | 제약 | 근거 |
|--------|------|------|
| CONST-TECH-01 | Java 17 / Spring Boot 3.4 의무 | ADR-001 |
| CONST-LEGAL-01 | 주민번호 저장 시 AES-256-GCM | 개인정보보호법 |
| CONST-LEGAL-02 | 감사 로그 7년 보존 | 전자금융감독규정 |
| CONST-OPS-01 | 컷오버 후 72시간 롤백 가능 유지 | 운영 정책 |
| CONST-OPS-02 | 운영 배포는 평일 22:00 이후 | 운영 정책 |

---

## 5. Use Cases (요약)

| UC-ID | 시나리오 | 1차 사용자 | 관련 FR |
|-------|---------|-----------|---------|
| UC-001 | 로그인 → 거래 등록 → 결재 → 완료 | 일반 사용자 | FR-AUTH-001, FR-TX-001 |
| UC-002 | 거래 조회 → 취소 → 환불 | 일반 사용자 | FR-TX-002, FR-TX-003 |
| UC-003 | 일일 배치 정산 | 시스템 | FR-OPS-001 |

상세 시나리오는 `use-cases/UC-NNN.md` 참조.

---

## 6. AMBIGUOUS / 보류 항목

| ID | 항목 | 후보안 | PM 회신 | 상태 |
|----|------|--------|---------|------|
| AMB-001 | {예: "빠르게" 의 정의} | A: P95<500ms / B: P95<1s | A 선정 | RESOLVED |
| AMB-002 | {보류 항목} | A / B | 대기 중 | PENDING |

---

## 7. 변경 이력

| 일자 | 버전 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| {YYYY-MM-DD} | 1.0 | 초안 | {이름} |

---

**G1 결재 (분석 게이트)**
| 일자 | 결재자 | 의견 | 상태 |
|------|--------|------|------|
| {YYYY-MM-DD} | PM | | {APPROVED/REJECTED/PENDING} |
