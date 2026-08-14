---
name: 06-finalize-deliverables
description: 산출물 작성 전 1:1 대화로 목록·형식 확정. 분석/설계/구현/검증 단계별 산출물을 형식별(md/pdf/pptx/hwp/docx/png)로 자동 조립.
when_to_use: Skill 5 결재 완료 (G3 통과) 후. 최종 산출물 작성 단계.
phase: 4
lead_agent: docs-writer
support_agents:
  - architect
  - qa-engineer
  - security-auditor
  - trace-mapper
outputs:
  - deliverables/
  - deliverables/INDEX.md
---

# Skill 06 — 최종 산출물 작성

> **목적**: 프로젝트 종료 시 사람·시스템·임원·운영팀 각각이 필요한 산출물을 형식별로 자동 작성.
> **소요**: 1~3 일
> **선행**: Skill 5 (G3 릴리즈 게이트 통과)
> **후속**: 프로젝트 종료 / 운영 이관

---

## 0. 담당 에이전트

| 역할 | 에이전트 | 책임 |
|------|----------|------|
| Lead | `docs-writer` | 대상별 최종 산출물 목록 확정, 문서 조립, 형식 변환, INDEX 작성 |
| Support | `architect` | 아키텍처 개요, ADR 요약, 기술 의사결정 설명 검토 |
| Support | `qa-engineer` | QA / Parity / 테스트 리포트의 최종본 확인 |
| Support | `security-auditor` | 보안 감사 / 규제 대응 / prod gate 증적 확인 |
| Support | `trace-mapper` | 요구-코드-테스트-산출물 추적성 최종 점검 |

> Lead 에이전트는 산출물 대상과 형식을 PM에게 확인하고, Support 검토 완료 후 deliverables/INDEX.md를 확정한다.

## 1. 동작 절차

```
[A] 1:1 대화로 산출물 목록 확정
    │   - 사람이 산출물을 명시
    │   - AI 가 빠진 항목 제안 (§4 표준 목록 기준)
    │   - 산출물별 형식 지정 (md / pdf / pptx / hwp / docx / png / xlsx)
    │
    ▼
[B] 단계별 산출물 자동 조립
    │   - 분석: Skill 1, 2 산출물
    │   - 설계: Skill 3 산출물
    │   - 구현: Skill 4 산출물
    │   - 검증: Skill 5 산출물
    │
    ▼
[C] 포맷 변환
    │   - md → pdf : weasyprint / pandoc
    │   - md → pptx : python-pptx
    │   - md → docx : pandoc
    │   - md → hwp : pandoc + hwp 변환 도구 (한컴 변환기)
    │   - 이미지 : Pillow (스크린샷·다이어그램)
    │
    ▼
[D] 가독성 검토
    │   - 용어집 (glossary) 첨부
    │   - 대상별 분리 (임원용 / 기술자용 / 운영팀용)
    │
    ▼
[E] INDEX.md 작성 → PM 최종 확인 → 배포
```

## 2. 표준 산출물 목록 (체크리스트)

| 분류 | 산출물 | 기본 형식 | 대상 |
|------|--------|----------|------|
| **기획** | 프로젝트 기획서 | md + pdf | PM / 발주처 |
| **기획** | 비즈니스 요구사항 | md | PM |
| **분석** | 요구사항 정의서 | md + pdf | 전 인력 |
| **분석** | Use Case 명세 | md | 개발 / QA |
| **분석** | 요구사항 추적 매트릭스 | csv + md | 감사 |
| **설계** | 개발계획서 | md + pdf | PM / Leader |
| **설계** | 테스트계획서 | md + pdf | QA |
| **설계** | ADR 모음 | md | 아키텍트 |
| **설계** | 아키텍처 개요 | md + png (Mermaid) | 임원 / 신규 인력 |
| **설계** | 위험 등록부 | md | PM |
| **구현** | 소스 트리 (zip 또는 git tag) | source | 운영 |
| **구현** | C↔Java 또는 요구사항↔코드 추적 매트릭스 | csv + md | 감사 |
| **구현** | API 명세 (OpenAPI) | yaml + md | 연동 팀 |
| **구현** | Sprint 로그 통합본 | md | PM |
| **검증** | QA 테스트 리포트 | md + pdf | PM |
| **검증** | 코드 리뷰 리포트 | md | 개발 |
| **검증** | 보안 감사 리포트 | md + pdf | 정보보호 |
| **검증** | 교차 검증 리포트 (CVSS) | md | 임원 / 정보보호 |
| **검증** | Parity 리포트 (해당 시) | md + csv | QA / 감사 |
| **운영** | Runbook | md | 운영팀 |
| **운영** | 컷오버 / 롤백 절차서 | md | 운영팀 |
| **운영** | 모니터링 대시보드 사양 | md + png | 운영팀 |
| **운영** | 인시던트 사후분석 (Postmortem, 발생 시) | md | 운영 / 정보보호 |
| **구현** | Sprint 회고 통합본 | md | PM / Leader |
| **임원 보고** | 임원 보고 (요약) | pptx + pdf | 임원 |
| **임원 보고** | 임원 보고 (상세) | pptx + pdf | 임원 / 정보보호 |

## 3. 포맷 변환 도구

| 변환 | 도구 | 비고 |
|------|------|------|
| md → pdf | weasyprint, pandoc + wkhtmltopdf | 한글 폰트 (맑은 고딕) 강제 |
| md → pptx | Marp (`marp deck.md -o deck.pptx`) 또는 python-pptx | 슬라이드 구분 `---` / 분할 룰 명시 |
| md → docx | pandoc | Word 호환 |
| md → hwp | pandoc → docx → 한컴오피스 변환 | hwp 직접 변환 한계 |
| png 생성 | Pillow, mermaid-cli, plantuml | 다이어그램 + 합성 |
| xlsx | openpyxl | csv → xlsx 변환 |

## 4. 1:1 대화 표준 질문

| # | 질문 | 입력 |
|---|------|------|
| 1 | 본 프로젝트의 최종 산출 대상은 누구입니까? | 발주처 / 임원 / 운영팀 / 감사 / 외부 |
| 2 | 각 대상에게 어떤 형식이 가장 적합한가요? | md / pdf / pptx / hwp / docx |
| 3 | 임원 보고가 필요합니까? | Y/N → Y 면 발표 시간 / 길이 |
| 4 | 운영팀 인수인계 자료가 필요합니까? | Y/N → Y 면 Runbook + 컷오버 절차 |
| 5 | 감사 / 규제 대응 자료가 필요합니까? | Y/N → Y 면 추적 매트릭스 + 보안 감사 |
| 6 | 본 표준 목록에서 누락된 산출물이 있습니까? | 사용자 자유 입력 |
| 7 | 산출물 보존 기간은? | 영구 / 7년 / 3년 / 1년 |

> **질의 기준**: 위 7문항은 모두 **CQ1(산출물 조립 필수 입력)**. 기준 정의: HARNESS-PROCESS-STANDARD §4.8.

## 5. INDEX.md 양식

```markdown
# 산출물 INDEX

| 분류 | 산출물 | 파일 | 형식 | 대상 | 작성일 | 결재자 | 상태 |
|------|--------|------|------|------|--------|--------|------|
| 기획 | 프로젝트 기획서 | deliverables/01-planning/PROJECT-PROPOSAL.pdf | PDF | 발주처 | 2026-01-15 | PM | APPROVED |
| ... |
```

## 6. 입력

- Skill 1~5 모든 산출물 (전 사이클 누적)
- PM 1:1 대화 가능 상태

## 7. 출력

- `deliverables/` 디렉터리에 모든 최종본 일괄 배치
- `deliverables/INDEX.md` — 산출물 카탈로그
- `deliverables/01-planning/`, `02-analysis/`, `03-design/`, `04-implementation/`, `05-validation/`, `06-ops/`, `07-executive/` (분류별 폴더)

## 8. 완료 기준 (DoD)

- [ ] INDEX.md 와 실제 파일 1:1 일치
- [ ] PM 1:1 대화로 산출물 목록·형식 확정
- [ ] 모든 산출물 PM 최종 확인 (서명·날인 또는 디지털 결재)
- [ ] 임원 보고 자료 사람 발표자 리허설 완료 (해당 시)
- [ ] 운영 이관 자료 운영팀 리뷰 완료 (해당 시)

## 9. 가독성 표준

| 항목 | 룰 |
|------|----|
| 용어집 | 모든 약어·전문용어를 `glossary.md` 에 정리 |
| 임원용 | 슬라이드 ≤ 12 + 부록 / 1줄 요약 / 한글 우선 `[근거:업계관례·조정가능]` |
| 기술자용 | 코드 예시 + Mermaid + ADR 링크 |
| 운영팀용 | 단계별 명령어 + 트러블슈팅 + 연락처 |
| 감사용 | 추적 매트릭스 + 결재 이력 + 보존 기간 |

## 10. 금융권 추가 산출물

| 산출물 | 형식 | 대상 |
|--------|------|------|
| 전자금융감독규정 대응 매트릭스 | md + xlsx | 금감원 / 정보보호 |
| ISMS-P 인증 대응 자료 | md | 인증 심사 |
| PII 처리 절차서 | md + pdf | 개인정보보호위원회 |
| 키 관리 절차서 | md | 정보보호 |
| 감사 로그 보존·열람 절차 | md | 감사 |
| BCP / 롤백 시나리오 | md | 운영 / 임원 |
