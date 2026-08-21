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

# -----------------------------------------------------------------------------
# 우회 통제 (VS-007) / bypass control
#
# req: 표준 §2.4 — "L1 Hook 이 gitleaks 를 실행하며, 우회 시 결재 증적 파일을 요구하는가"
#
# ⚠ 먼저 한계를 분명히 한다: **`git commit --no-verify` 는 이 훅 안에서 막을 수 없다.**
#   그 옵션은 훅 자체를 실행하지 않으므로, 여기에 어떤 검사를 넣어도 실행되지 않는다.
#   "우회를 막는다"고 적어 두는 것은 있지도 않은 통제를 있다고 적는 것이다 — 이 저장소가
#   반복해서 발견해 온 결함 유형이다. 따라서 이 블록이 하는 일은 두 가지다:
#     1) **승인된 우회 경로를 제공**한다. 정당한 우회(예: 시험 픽스처가 오탐)에는
#        `IRIS_L1_BYPASS=1` 을 쓰게 하고, 그 대가로 결재 증적 파일을 요구한다.
#     2) `--no-verify` 를 **정책 위반으로 명확히 규정**한다. 실제 탐지는 L2 CI 가 한다
#        (`.github/workflows/ci.yml` 의 gitleaks job 은 훅과 무관하게 전체를 다시 스캔한다).
#
#   Stated limitation first: **`git commit --no-verify` cannot be blocked from inside this hook**,
#   because that flag skips the hook entirely. Claiming otherwise would document a control that
#   does not exist. So this block does two things: it provides a *sanctioned* bypass that demands
#   approval evidence, and it defines `--no-verify` as a policy violation whose actual detection
#   belongs to L2 CI, which rescans independently of any client-side hook.
# -----------------------------------------------------------------------------
if [ "${IRIS_L1_BYPASS:-}" = "1" ]; then
  repo_root=$(git rev-parse --show-toplevel)
  approvals_dir="$repo_root/security/bypass-approvals"

  # 증적은 <b>당일</b> 파일이어야 한다. 과거 결재를 재사용하면 1회 승인이 무기한
  # 우회 권한이 된다.
  # Evidence must be dated today: reusing an old approval turns one sign-off into a standing
  # permission to bypass.
  today=$(date +%Y-%m-%d)
  evidence=$(ls "$approvals_dir"/${today}-*.md 2>/dev/null | head -1 || true)

  if [ -z "$evidence" ]; then
    echo "[L1] BLOCKED: IRIS_L1_BYPASS=1 was set without approval evidence."
    echo "[L1] Required: $approvals_dir/${today}-<slug>.md"
    echo "[L1] The file must record: approver, reason, scope (which paths), and expiry."
    echo "[L1] A bypass without a named approver is indistinguishable from a mistake."
    exit 1
  fi

  if ! grep -qiE '^(approver|결재자)[[:space:]]*:' "$evidence"; then
    echo "[L1] BLOCKED: $evidence has no 'Approver:' line."
    echo "[L1] Evidence without an accountable name is not evidence."
    exit 1
  fi

  echo "[L1] BYPASS accepted — evidence: ${evidence#"$repo_root"/}"
  echo "[L1] Note: L2 CI still scans this commit. A bypass here is not a bypass there."
  exit 0
fi

echo "[L1] secret scan on staged changes..."

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "[L1] ERROR: gitleaks is not installed."
  echo "[L1]   macOS : brew install gitleaks"
  echo "[L1]   Linux : https://github.com/gitleaks/gitleaks/releases"
  echo "[L1] Refusing to commit without a secret scan. This check is not optional —"
  echo "[L1] it exists because live credentials were found in the legacy source."
  exit 1
fi

# -----------------------------------------------------------------------------
# VS-006 수정. 이전에는 --config 없이 실행하여 gitleaks 가 기본 룰만 적용했고,
# 오류 안내는 존재하지 않는 루트 .gitleaks.toml 을 가리켰다. 그 결과 저장소에
# 체크인된 hooks/gitleaks.toml 이 L1 에서 <b>한 번도 적용되지 않았다</b>.
# CI(L2) 는 같은 파일을 config-path 로 이미 참조하므로, 이 수정으로 L1·L2 가
# 동일한 룰셋을 쓴다 — 로컬에서 통과한 커밋이 CI 에서 막히는 불일치를 없앤다.
#
# VS-006: previously ran without --config, so only the built-in rules applied and the error
# message pointed at a root .gitleaks.toml that does not exist — the checked-in
# hooks/gitleaks.toml was never used at L1. CI already references it via config-path, so L1 and
# L2 now share one ruleset instead of diverging.
# -----------------------------------------------------------------------------
repo_root=$(git rev-parse --show-toplevel)
config="$repo_root/hooks/gitleaks.toml"

if [ ! -f "$config" ]; then
  echo "[L1] ERROR: ruleset not found: $config"
  echo "[L1] The custom ruleset is part of the standard package; refusing to fall back to"
  echo "[L1] built-in rules silently, because that would narrow the scan without saying so."
  exit 1
fi

if ! gitleaks protect --staged --redact --verbose --config "$config"; then
  echo ""
  echo "[L1] BLOCKED: a potential secret was found in the staged changes."
  echo "[L1] Remove the value and move it to configuration (ADR-007)."
  echo "[L1] If this is a false positive, add a narrowly-scoped rule to hooks/gitleaks.toml"
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
