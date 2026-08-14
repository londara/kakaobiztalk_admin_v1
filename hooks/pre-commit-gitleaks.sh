#!/usr/bin/env bash
# 보안 Hook L1 — git pre-commit gitleaks 스캔
# 본 표준 (HARNESS-PROCESS-STANDARD.md §5.4) L1 단계
#
# 설치:
#   cp hooks/pre-commit-gitleaks.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# 또는 husky / pre-commit 프레임워크 사용 시 본 스크립트를 등록.

set -euo pipefail

# ────────────────────────────────────────────────────────────────────
# 설정
# ────────────────────────────────────────────────────────────────────
GITLEAKS_VERSION="${GITLEAKS_VERSION:-v8.18.0}"
CONFIG_FILE="${GITLEAKS_CONFIG:-hooks/gitleaks.toml}"  # 프로젝트 커스텀 룰
ALLOW_BYPASS_ENV="ALLOW_GITLEAKS_BYPASS"               # 긴급 시에만 사용

# ────────────────────────────────────────────────────────────────────
# gitleaks 설치 확인
# ────────────────────────────────────────────────────────────────────
if ! command -v gitleaks >/dev/null 2>&1; then
  echo "[L1 Hook] gitleaks 미설치. 설치 안내:"
  echo "  macOS:   brew install gitleaks"
  echo "  Linux:   curl -sSL https://github.com/gitleaks/gitleaks/releases/download/${GITLEAKS_VERSION}/gitleaks_$(uname -s)_$(uname -m).tar.gz | tar xz -C /usr/local/bin"
  echo "  Docker:  docker run --rm -v \$PWD:/repo zricethezav/gitleaks:latest"
  exit 1
fi

# ────────────────────────────────────────────────────────────────────
# 우회 차단
# ────────────────────────────────────────────────────────────────────
if [ "${!ALLOW_BYPASS_ENV:-}" = "1" ]; then
  APPROVAL_FILE="${GITLEAKS_BYPASS_APPROVAL:-security/gitleaks-bypass-approval.md}"
  echo "[L1 Hook] WARNING — ${ALLOW_BYPASS_ENV}=1 설정으로 우회 시도."
  echo "[L1 Hook] 우회는 PM 결재 증적 파일이 있을 때만 허용됩니다: ${APPROVAL_FILE}"

  if [ ! -f "${APPROVAL_FILE}" ]; then
    echo "[L1 Hook] FAIL — 결재 증적 파일이 없어 우회를 차단합니다."
    echo "[L1 Hook] 필요한 항목: 사유 / 결재자 / 만료일 / 키 회전 계획 / APPROVED 상태"
    exit 1
  fi

  if ! grep -Eq 'APPROVED|승인' "${APPROVAL_FILE}"; then
    echo "[L1 Hook] FAIL — 결재 증적 파일에 APPROVED 또는 승인 상태가 없습니다."
    exit 1
  fi

  logger -t harness-l1-hook "gitleaks bypass by ${USER} at $(date), approval=${APPROVAL_FILE}"
  echo "[L1 Hook] BYPASS APPROVED — 감사 로그와 결재 증적을 남기고 진행합니다."
  exit 0
fi

# ────────────────────────────────────────────────────────────────────
# 스캔 실행 (staged 영역 한정)
# ────────────────────────────────────────────────────────────────────
echo "[L1 Hook] gitleaks 시크릿 스캔 시작 (staged files)..."

GITLEAKS_OPTS=(
  "protect"
  "--staged"
  "--verbose"
  "--no-banner"
  "--redact"
)

if [ -f "${CONFIG_FILE}" ]; then
  GITLEAKS_OPTS+=("--config" "${CONFIG_FILE}")
  echo "[L1 Hook] 커스텀 룰 사용: ${CONFIG_FILE}"
fi

if gitleaks "${GITLEAKS_OPTS[@]}"; then
  echo "[L1 Hook] PASS — 시크릿 없음"
  exit 0
else
  echo ""
  echo "[L1 Hook] FAIL — 시크릿 발견. 커밋이 차단되었습니다."
  echo ""
  echo "  조치 방법:"
  echo "  1. 발견된 시크릿을 환경변수 또는 Vault/KMS 로 이동"
  echo "  2. .env 파일은 .gitignore 등록"
  echo "  3. 이미 푸시된 경우 즉시 키 회전 + git history rewrite"
  echo ""
  echo "  본 표준 §5.4 보안 Hook 3단계 참조"
  echo ""
  exit 1
fi
