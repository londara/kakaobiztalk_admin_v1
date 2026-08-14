#!/usr/bin/env bash
# =============================================================================
# L1 보안 훅 — 커밋 전 시크릿 스캔
# L1 security hook — secret scan before commit
#
# req: RISK-L02, NFR-SEC-SECRET-L01, CONST-SEC-L02, TEST-PLAN-LOGIN §5
#
# 왜 이 훅이 존재하는가 / why this hook exists:
#   레거시 apc_login_proc_act.jsp 에 Kakao sender_key 와 개인 휴대폰번호 3건이
#   평문으로 하드코딩되어 있었다(결함 L1). 운영 중인 시스템의 소스에 실제 자격증명이
#   들어 있었다는 뜻이다. 이 훅은 같은 부류의 실수가 커밋 단계에서 걸리게 한다.
#
#   The legacy had a Kakao sender_key and three personal mobile numbers hardcoded in
#   plain source (defect L1) — live credentials inside a running system's source. This
#   hook stops that class of mistake at commit time rather than at audit time.
#
# 설치 / install:
#   ln -s ../../hooks/pre-commit-gitleaks.sh .git/hooks/pre-commit
#   chmod +x hooks/pre-commit-gitleaks.sh
# =============================================================================
set -euo pipefail

echo "[L1] secret scan on staged changes..."

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "[L1] ERROR: gitleaks is not installed."
  echo "[L1]   macOS : brew install gitleaks"
  echo "[L1]   Linux : https://github.com/gitleaks/gitleaks/releases"
  echo "[L1] Refusing to commit without a secret scan. This check is not optional —"
  echo "[L1] it exists because live credentials were found in the legacy source."
  exit 1
fi

if ! gitleaks protect --staged --redact --verbose; then
  echo ""
  echo "[L1] BLOCKED: a potential secret was found in the staged changes."
  echo "[L1] Remove the value and move it to configuration (ADR-007)."
  echo "[L1] If this is a false positive, add a narrowly-scoped rule to .gitleaks.toml"
  echo "[L1] with a comment explaining why — do not disable the hook."
  exit 1
fi

# -----------------------------------------------------------------------------
# 추가 검사: 한국 휴대폰번호 패턴.
# Supplementary check: Korean mobile number pattern.
#
# gitleaks 는 자격증명을 찾지만 개인정보는 찾지 못한다. 레거시 결함 L1 에서 유출된
# 것은 키뿐 아니라 실제 개인 휴대폰번호 3건이었고, 이는 개인정보보호법 사안이다.
# gitleaks finds credentials but not personal data. Legacy defect L1 exposed three
# real mobile numbers alongside the key, which is a 개인정보보호법 matter in its own right.
# -----------------------------------------------------------------------------
staged_files=$(git diff --cached --name-only --diff-filter=ACM || true)
if [ -n "$staged_files" ]; then
  if echo "$staged_files" | xargs -r grep -nE '"01[0-9]{8,9}"' 2>/dev/null; then
    echo ""
    echo "[L1] BLOCKED: a literal mobile number was found in the staged changes."
    echo "[L1] Personal contact details must not be committed (CONST-SEC-L02)."
    echo "[L1] Move recipients to configuration — see FR-LOGIN-021."
    exit 1
  fi
fi

echo "[L1] OK — no secret or personal contact detail detected."
