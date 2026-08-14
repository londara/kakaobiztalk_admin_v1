#!/usr/bin/env bash
# Codec Generator — Schema-driven Codec 자동 생성
# 본 표준 HARNESS-PROCESS-STANDARD.md §6 + sg-gw ADR-016 / §7.10
#
# 입력:  src/main/resources/telegram-schema/*.yaml
# 출력:  src/main/java/com/{org}/{prj}/{codec-pkg}/*.java  (auto-generated)
#
# 사용법:
#   scripts/generate-codecs.sh                # 모든 schema 재생성
#   scripts/generate-codecs.sh cs-header      # 특정 schema 만
#   scripts/generate-codecs.sh --check        # 재생성 후 git diff 검증 (CI 용)

set -euo pipefail

# ──────────────────────────────────────────────────────────
# 설정
# ──────────────────────────────────────────────────────────
SCHEMA_DIR="${SCHEMA_DIR:-src/main/resources/telegram-schema}"
GENERATOR_PKG="${GENERATOR_PKG:-com.{org}.{prj}.codecgen.CodecGenerator}"

MODE="generate"
TARGET=""

for arg in "$@"; do
  case "$arg" in
    --check) MODE="check" ;;
    --help|-h)
      grep '^# ' "$0" | head -20
      exit 0
      ;;
    *) TARGET="$arg" ;;
  esac
done

# ──────────────────────────────────────────────────────────
# 사전 점검
# ──────────────────────────────────────────────────────────
if [ ! -d "$SCHEMA_DIR" ]; then
  echo "[codecgen] schema 디렉터리 없음: $SCHEMA_DIR"
  echo "[codecgen] 신규 schema 작성 가이드 → ADR-016 참조"
  exit 1
fi

# ──────────────────────────────────────────────────────────
# Schema 목록 결정
# ──────────────────────────────────────────────────────────
if [ -n "$TARGET" ]; then
  SCHEMAS=("$SCHEMA_DIR/${TARGET}.yaml")
  if [ ! -f "${SCHEMAS[0]}" ]; then
    echo "[codecgen] schema 없음: ${SCHEMAS[0]}"
    exit 1
  fi
else
  mapfile -t SCHEMAS < <(find "$SCHEMA_DIR" -name "*.yaml" -not -path "*/catalog/*")
fi

echo "[codecgen] ${#SCHEMAS[@]} schema 처리 시작"

# ──────────────────────────────────────────────────────────
# CodecGenerator 실행 (Maven exec:java)
# ──────────────────────────────────────────────────────────
for schema in "${SCHEMAS[@]}"; do
  echo "  [generate] $schema"
  ./mvnw -q exec:java \
    -Dexec.mainClass="$GENERATOR_PKG" \
    -Dexec.args="$schema" \
    -DskipTests || {
      echo "[codecgen] FAIL: $schema"
      exit 1
    }
done

echo "[codecgen] 모든 schema 처리 완료"

# ──────────────────────────────────────────────────────────
# --check 모드: git diff 검증 (CI 용)
# ──────────────────────────────────────────────────────────
if [ "$MODE" = "check" ]; then
  echo "[codecgen] 재생성 후 git diff 검증..."
  if git diff --quiet -- 'src/main/java/**/codec/*.java' 2>/dev/null; then
    echo "[codecgen] PASS — schema 와 generated 일치"
    exit 0
  else
    echo ""
    echo "[codecgen] FAIL — schema 와 generated 불일치"
    echo ""
    echo "  원인:"
    echo "  - schema 수정 후 재생성 누락"
    echo "  - generated 파일 직접 편집 (금지)"
    echo ""
    echo "  조치:"
    echo "  1. scripts/generate-codecs.sh 실행"
    echo "  2. git diff 검토 후 commit"
    echo ""
    echo "  본 표준 §7.10.5 참조"
    git diff --stat -- 'src/main/java/**/codec/*.java'
    exit 1
  fi
fi
