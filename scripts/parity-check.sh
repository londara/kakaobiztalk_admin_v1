#!/usr/bin/env bash
# Parity Check Script — 마이그레이션 프로젝트 한정
# 본 표준 HARNESS-PROCESS-STANDARD.md §6 SG Gateway 사례 / Skill 5 Parity 테스트
#
# 사용법:
#   scripts/parity-check.sh <module>         # 단일 모듈
#   scripts/parity-check.sh all              # 전체
#   scripts/parity-check.sh --case TC-001    # 단일 케이스
#
# 환경 변수:
#   LEGACY_BIN=legacy/run/run-case.sh
#   JAVA_BIN="./mvnw -q -DskipTests exec:java -Dexec.args='{}'"   # "{}"는 case_id 로 치환
#   FIXTURE_DIR=parity/fixtures
#   EXPECTED_DIR=parity/expected
#   ACTUAL_DIR=parity/actual
#   MASK_RULES=parity/mask-rules.yaml

set -euo pipefail

MODE="module"
MODULE="all"
SINGLE_CASE=""

LEGACY_BIN="${LEGACY_BIN:-legacy/run/run-case.sh}"
JAVA_BIN="${JAVA_BIN:-}"
FIXTURE_DIR="${FIXTURE_DIR:-parity/fixtures}"
EXPECTED_DIR="${EXPECTED_DIR:-parity/expected}"
ACTUAL_DIR="${ACTUAL_DIR:-parity/actual}"
MASK_RULES="${MASK_RULES:-parity/mask-rules.yaml}"
REPORT="${REPORT:-parity/parity-report-$(date +%Y%m%d-%H%M%S).md}"

PASS=0
FAIL=0
MASKED=0
DETAILS=()
CASES=()

mkdir -p "$EXPECTED_DIR" "$ACTUAL_DIR" "$(dirname "$REPORT")"

usage() {
  echo "사용법:"
  echo "  scripts/parity-check.sh <module>"
  echo "  scripts/parity-check.sh all"
  echo "  scripts/parity-check.sh --case TC-001"
}

run_java_case() {
  local case_id="$1"

  if [ -z "$JAVA_BIN" ]; then
    echo "  [java] FAIL — JAVA_BIN 미설정. 예: JAVA_BIN='./mvnw -q -DskipTests exec:java -Dexec.args=\"{}\"'"
    return 1
  fi

  if [[ "$JAVA_BIN" == *"{}"* ]]; then
    local rendered="${JAVA_BIN//\{\}/$case_id}"
    bash -lc "$rendered"
  else
    bash -lc "$JAVA_BIN \"$case_id\""
  fi
}

apply_mask_rules() {
  local case_id="$1"
  local expected_in="$2"
  local actual_in="$3"
  local expected_out="$4"
  local actual_out="$5"

  if ! command -v python3 >/dev/null 2>&1; then
    return 1
  fi

  python3 - "$MASK_RULES" "$case_id" "$expected_in" "$actual_in" "$expected_out" "$actual_out" <<'PY'
import fnmatch
import re
import sys
from pathlib import Path


def parse_rules(raw_text):
    rules = []
    current = None
    in_rules = False

    for raw_line in raw_text.splitlines():
        line = raw_line.split("#", 1)[0].rstrip()
        if not line:
            continue

        stripped = line.lstrip()
        if stripped == "rules:":
            in_rules = True
            continue

        if not in_rules:
            continue

        if stripped.startswith("- "):
            if current:
                rules.append(current)
            current = {}
            stripped = stripped[2:]

        if ":" not in stripped or current is None:
            continue

        key, value = stripped.split(":", 1)
        current[key.strip()] = value.strip().strip("'\"")

    if current:
        rules.append(current)

    return rules


def parse_offsets(raw_value):
    offsets = []
    for part in re.split(r"\s*,\s*", raw_value):
        if not part:
            continue
        if "-" in part:
            start, end = part.split("-", 1)
            offsets.append((int(start), int(end)))
        else:
            value = int(part)
            offsets.append((value, value))
    return offsets


def mask_bytes(payload, ranges):
    for start, end in ranges:
        if end < start:
            start, end = end, start
        lower = max(start, 0)
        upper = min(end + 1, len(payload))
        if lower < upper:
            payload[lower:upper] = b"\x00" * (upper - lower)


rules_path, case_id, expected_in, actual_in, expected_out, actual_out = sys.argv[1:]
rules = parse_rules(Path(rules_path).read_text(encoding="utf-8"))

matched_ranges = []
for rule in rules:
    if fnmatch.fnmatch(case_id, rule.get("case", "")) and "offset" in rule:
        matched_ranges.extend(parse_offsets(rule["offset"]))

if not matched_ranges:
    sys.exit(2)

expected_bytes = bytearray(Path(expected_in).read_bytes())
actual_bytes = bytearray(Path(actual_in).read_bytes())

mask_bytes(expected_bytes, matched_ranges)
mask_bytes(actual_bytes, matched_ranges)

Path(expected_out).write_bytes(expected_bytes)
Path(actual_out).write_bytes(actual_bytes)
PY
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --case)
      if [ "$#" -lt 2 ]; then
        usage
        exit 1
      fi
      MODE="case"
      SINGLE_CASE="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      MODULE="$1"
      shift
      ;;
  esac
done

if [ "$MODE" = "case" ]; then
  CASES=("$SINGLE_CASE")
elif [ "$MODULE" = "all" ]; then
  while IFS= read -r file; do
    CASES+=("$(basename "$file" .bin)")
  done < <(find "$FIXTURE_DIR" -maxdepth 1 -type f -name "*.bin" | sort)
else
  while IFS= read -r file; do
    CASES+=("$(basename "$file" .bin)")
  done < <(find "$FIXTURE_DIR" -maxdepth 1 -type f -name "${MODULE}-*.bin" | sort)
fi

if [ "${#CASES[@]}" -eq 0 ]; then
  echo "[parity] 케이스 없음 — FIXTURE_DIR=$FIXTURE_DIR"
  exit 1
fi

for case_id in "${CASES[@]}"; do
  echo ""
  echo "[parity] $case_id ─────────────────────────────────"

  EXPECTED="$EXPECTED_DIR/${case_id}.bin"
  ACTUAL="$ACTUAL_DIR/${case_id}.bin"

  if [ ! -f "$EXPECTED" ]; then
    echo "  [legacy] running $LEGACY_BIN $case_id"
    if "$LEGACY_BIN" "$case_id" > "$EXPECTED" 2>/dev/null; then
      echo "  [legacy] OK ($(wc -c < "$EXPECTED") bytes)"
    else
      echo "  [legacy] FAIL — 레거시 실행 실패"
      FAIL=$((FAIL+1))
      DETAILS+=("- $case_id: LEGACY_FAIL")
      continue
    fi
  fi

  echo "  [java] running"
  if run_java_case "$case_id" > "$ACTUAL" 2>/dev/null; then
    echo "  [java] OK ($(wc -c < "$ACTUAL") bytes)"
  else
    FAIL=$((FAIL+1))
    DETAILS+=("- $case_id: JAVA_FAIL")
    continue
  fi

  if cmp -s "$EXPECTED" "$ACTUAL"; then
    echo "  [cmp] PASS — 바이트 동일"
    PASS=$((PASS+1))
    DETAILS+=("- $case_id: PASS")
    continue
  fi

  if [ -f "$MASK_RULES" ] && \
     apply_mask_rules "$case_id" "$EXPECTED" "$ACTUAL" "$EXPECTED.masked" "$ACTUAL.masked" 2>/dev/null && \
     cmp -s "$EXPECTED.masked" "$ACTUAL.masked"; then
    echo "  [cmp] PASS (masked) — 마스킹 후 동일"
    PASS=$((PASS+1))
    MASKED=$((MASKED+1))
    DETAILS+=("- $case_id: PASS_MASKED")
    continue
  fi

  echo "  [cmp] FAIL — 바이트 차이"
  diff_summary=$(cmp -l "$EXPECTED" "$ACTUAL" 2>&1 | head -5 || true)
  echo "    $diff_summary"
  FAIL=$((FAIL+1))
  DETAILS+=("- $case_id: FAIL ($(echo "$diff_summary" | wc -l) diff lines)")
done

TOTAL=$((PASS+FAIL))
PASS_RATE="0.0"
if [ "$TOTAL" -gt 0 ]; then
  PASS_RATE=$(awk -v pass="$PASS" -v total="$TOTAL" 'BEGIN { printf "%.1f", (pass * 100) / total }')
fi

cat > "$REPORT" <<EOF
# Parity Report — $(date +%Y-%m-%d\ %H:%M:%S)

> 본 표준 §6 SG Gateway 사례 / Skill 5 Parity 검증

## 요약

| 항목 | 값 |
|------|----|
| 모듈 | $MODULE |
| 전체 케이스 | $TOTAL |
| PASS | $PASS |
| FAIL | $FAIL |
| PASS (마스킹 적용) | $MASKED |
| 통과율 | ${PASS_RATE}% |

## 케이스별 결과

$(printf '%s\n' "${DETAILS[@]}")

## 판정

EOF

if [ "$FAIL" -eq 0 ]; then
  echo "**APPROVE** — 모든 케이스 PASS" >> "$REPORT"
  echo ""
  echo "[parity] APPROVE — ${PASS}/${TOTAL} PASS (${PASS_RATE}%)"
  echo "[parity] 리포트: $REPORT"
  exit 0
fi

echo "**REJECT** — ${FAIL} 케이스 차이 발견 → Skill 4 환송 필요" >> "$REPORT"
echo ""
echo "[parity] REJECT — ${FAIL}/${TOTAL} FAIL"
echo "[parity] 리포트: $REPORT"
exit 1
