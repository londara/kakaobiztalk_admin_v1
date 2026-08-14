# 보안 감사 리포트 — Sprint {N}

> **작성**: security-auditor 에이전트
> **일자**: {YYYY-MM-DD}
> **대상**: Sprint {N} 산출물 + 운영 인프라 설정
> **판정**: APPROVE / CONDITIONAL APPROVE / REJECT

---

## 1. 감사 범위

- 소스 코드: `src/`
- 의존성: `pom.xml` / `build.gradle` / `package.json`
- 설정: `application*.yml`, `.env` 템플릿, 인프라 IaC
- 보안 Hook: L1 (pre-commit) / L2 (CI) / L3 (prod-gate)
- 외부 채널: MQ / TCP / REST / SFTP

## 2. OWASP Top 10 (2021) 점검

| ID | 항목 | 결과 | 비고 |
|----|------|------|------|
| A01 | Broken Access Control | PASS | 권한 매트릭스 + E2E 검증 |
| A02 | Cryptographic Failures | PASS | AES-256-GCM 적용 |
| A03 | Injection | PASS | JPA + PreparedStatement |
| A04 | Insecure Design | PASS | 아키텍처 리뷰 완료 |
| A05 | Security Misconfiguration | PASS | 설정 점검 완료 |
| A06 | Vulnerable Components | PASS | OWASP DC 통과 |
| A07 | Identification & Auth Failures | PASS | MFA + 세션 검증 |
| A08 | Software & Data Integrity Failures | PASS | HMAC + 의존성 무결성 |
| A09 | Logging & Monitoring Failures | PASS | 감사 로그 무결성 |
| A10 | SSRF | PASS | 화이트리스트 검증 |

## 3. 시크릿 / 자격증명 점검

| 항목 | 검증 | 결과 |
|------|------|------|
| 하드코딩 시크릿 | gitleaks 스캔 | 0 건 |
| 환경 변수 외부화 | `application.yml` 검토 | PASS |
| Vault / KMS 사용 | 키 관리 ADR 확인 | ADR-007 준수 |
| 키 회전 주기 | 운영 정책 | 90 일 |

## 4. PII / 민감정보 처리

| 항목 | 결과 | 근거 |
|------|------|------|
| PII 컬럼 식별 | 완료 | `mapping/model/pii-columns.md` |
| AES-256-GCM 적용 | PASS | 모든 PII 컬럼 |
| 마스킹 로그 | PASS | 정규식 검증 통과 |
| 화면 마스킹 | PASS | UI 코드 검증 |
| 응답 마스킹 | PASS | API 응답 검증 |
| 파기 절차 | PASS | 보존 기간 도과 시 자동 파기 |

### PII 컬럼 카탈로그 (예시)

| 컬럼 | 테이블 | 분류 | 암호화 | 보존 |
|------|--------|------|--------|------|
| customer_name | customers | PII | AES-256-GCM | 5년 |
| resident_no | customers | 민감 | AES-256-GCM + KMS | 5년 |
| account_no | accounts | 민감 | AES-256-GCM | 7년 |
| card_no | cards | PCI | AES-256-GCM + Tokenization | 7년 |

## 5. 외부 채널 인증

| 채널 | 인증 방식 | 검증 |
|------|---------|------|
| MQ (RabbitMQ) | TLS + SASL/PLAIN | PASS |
| TCP 외부 | mTLS | PASS |
| REST 외부 | OAuth2 Client Credentials + TLS | PASS |
| SFTP | SSH Key + 호스트 키 확인 | PASS |
| DB | TLS + Vault 자격증명 | PASS |

## 6. 감사 로그 (Audit Log)

| 항목 | 검증 |
|------|------|
| 모든 핵심 이벤트 기록 | PASS (`@Audited` 적용) |
| 보존 기간 7년 | PASS (Glacier 이관 정책) |
| 로그 무결성 (해시 체인) | PASS |
| 접근 통제 | PASS (감사자만 조회) |
| 시간 동기화 (NTP) | PASS |

## 7. 보안 Hook 3 단계 통합

| 단계 | 도구 | 통과 여부 |
|------|------|---------|
| L1 (pre-commit) | gitleaks | PASS |
| L1 (pre-commit) | semgrep (선택) | PASS |
| L2 (CI) | OWASP Dependency Check | PASS |
| L2 (CI) | SAST (CodeQL / SpotBugs) | PASS |
| L2 (CI) | security-auditor 에이전트 | PASS |
| L3 (prod-gate) | 체크리스트 인간 결재 | PENDING |

## 8. 규제 / 컴플라이언스 점검

| 규제 | 적용 | 결과 |
|------|------|------|
| 전자금융감독규정 | Y | PASS (접근통제 / 암호화 / 보관) |
| 개인정보보호법 | Y | PASS (수집·저장·파기) |
| 신용정보법 | Y/N | {결과} |
| ISMS-P | Y/N | {결과} |
| PCI-DSS | Y/N | {결과} |
| 금융보안원 가이드 | Y | PASS |

## 9. 발견 사항

### CRITICAL
| ID | 위치 | 내용 | 권장 조치 |
|----|------|------|---------|
| - | - | 없음 | - |

### HIGH
| ID | 위치 | 내용 | 권장 조치 |
|----|------|------|---------|
| - | - | 없음 | - |

### MEDIUM / LOW
| ID | 위치 | 내용 | 등급 |
|----|------|------|------|
| - | - | - | - |

## 10. 판정

| 항목 | 결과 |
|------|------|
| CVSS ≥ 9.0 결함 | 0 건 |
| CVSS ≥ 7.0 결함 | 0 건 |
| 규제 위반 | 0 건 |
| **종합 판정** | **APPROVE** |

## 11. 후속 권고

- [ ] 분기별 외부 침투 테스트
- [ ] 매월 의존성 취약점 스캔 + 패치
- [ ] 키 회전 자동화 (90일)
- [ ] 감사 로그 백업 검증 (분기)

---

**security-auditor 서명**
| 일자 | 에이전트 / 인간 | 비고 |
|------|-----------------|------|
| {YYYY-MM-DD} | security-auditor | 자동 감사 |
| {YYYY-MM-DD} | 정보보호 책임자 | (선택) 인간 검토 |
