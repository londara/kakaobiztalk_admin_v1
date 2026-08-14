#!/usr/bin/env bash
# =============================================================================
# Maven 없이 JDK-only 클래스를 실제로 실행하여 검증한다.
# Verifies JDK-only classes by actually executing them, without Maven.
#
# req: SPRINT-L1-RETRO A1, TEST-PLAN-LOGIN §1.1
#
# 왜 이 스크립트가 존재하는가 / why this exists:
#   이 환경에는 Maven 이 없어 `mvn verify` 를 실행할 수 없다. 그 결과 Sprint L1·L2 의
#   테스트 82건이 "작성되었으나 실행되지 않은" 상태로 남았다. 그러나 일부 클래스는
#   Spring 애노테이션만 제거하면 순수 JDK 로 컴파일·실행이 가능하다. 이 스크립트는
#   그 부분집합에 대해 실제 실행 증거를 만든다.
#
#   Maven is unavailable here, so `mvn verify` cannot run and 82 tests stayed "written
#   but never executed". Some classes, however, compile and run on the bare JDK once
#   Spring annotations are stripped. This script produces real execution evidence for
#   that subset.
#
# 한계 / limitations:
#   - Spring 컨텍스트, MyBatis 매핑, TOTP 라이브러리 API 는 검증하지 못한다
#   - JUnit 어서션이 아니라 드라이버 프로그램이므로 커버리지는 측정되지 않는다
#   - Maven 설치 후에는 이 스크립트가 아니라 `mvn verify` 가 정본이다
#     Once Maven is installed, `mvn verify` is authoritative and this becomes redundant.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/iris-verify-$$"
mkdir -p "$WORK/src" "$WORK/out"
trap 'rm -rf "$WORK"' EXIT

# Spring 애노테이션·임포트·패키지 선언을 제거하여 순수 JDK 클래스로 만든다.
# Strip Spring annotations, imports and package declarations to leave plain JDK classes.
strip() {
  sed -e '/^import org.springframework/d' \
      -e '/^@Component$/d' \
      -e 's/@Value("[^"]*") //' \
      -e 's/^package .*/ /' \
      -e 's/com\.webcash\.iris\.auth\.domain\.//g' \
      -e 's/com\.webcash\.iris\.biztalk\.domain\.//g' \
      -e 's/com\.webcash\.iris\.common\.tenant\.//g' \
      -e 's/com\.webcash\.iris\.common\.crosscut\.//g' \
      -e '/^import com\.webcash\./d' \
      "$1" > "$WORK/src/$(basename "$1")"
}

SUBSET=(
  "src/main/java/com/webcash/iris/auth/crypto/SecretCipher.java"
  "src/main/java/com/webcash/iris/auth/domain/AccountStatus.java"
  "src/main/java/com/webcash/iris/auth/domain/AuthFailureReason.java"
  "src/main/java/com/webcash/iris/auth/domain/AuthenticationException.java"
  "src/main/java/com/webcash/iris/auth/domain/UserAccount.java"
  "src/main/java/com/webcash/iris/auth/domain/AccountPolicy.java"
  "src/main/java/com/webcash/iris/auth/domain/PasswordPolicy.java"
  "src/main/java/com/webcash/iris/auth/crypto/TemporaryPasswordGenerator.java"
  "src/main/java/com/webcash/iris/auth/domain/RateLimiter.java"
  "src/main/java/com/webcash/iris/auth/domain/OtpReplayGuard.java"
  "src/main/java/com/webcash/iris/auth/domain/CidrMatcher.java"
  "src/main/java/com/webcash/iris/common/tenant/TenantContext.java"
  "src/main/java/com/webcash/iris/biztalk/domain/MessageType.java"
  "src/main/java/com/webcash/iris/biztalk/domain/TableType.java"
  "src/main/java/com/webcash/iris/biztalk/domain/MessageStatus.java"
  "src/main/java/com/webcash/iris/biztalk/domain/MessageHistoryCriteria.java"
  "src/main/java/com/webcash/iris/biztalk/domain/MessageHistoryRow.java"
  "src/main/java/com/webcash/iris/biztalk/domain/PagedResult.java"
  "src/main/java/com/webcash/iris/common/crosscut/ServiceWindow.java"
  "src/main/java/com/webcash/iris/biztalk/domain/CsvExporter.java"
)

for f in "${SUBSET[@]}"; do strip "$ROOT/$f"; done
cp "$ROOT"/qa/drivers/*.java "$WORK/src/"

echo "[verify] compiling $(ls "$WORK/src" | wc -l) sources..."
javac -encoding UTF-8 -d "$WORK/out" "$WORK"/src/*.java

status=0
echo "[verify] SecretCipher..."
java -cp "$WORK/out" SecretCipherDriver  || status=1
echo "[verify] AccountPolicy + PasswordPolicy..."
java -cp "$WORK/out" PolicyDriver || status=1
echo "[verify] TemporaryPasswordGenerator vs PasswordPolicy (200k samples)..."
java -cp "$WORK/out" TempPwdDriver || status=1
echo "[verify] RateLimiter + OtpReplayGuard..."
java -cp "$WORK/out" LimiterDriver || status=1
echo "[verify] CidrMatcher (allowlist boundaries)..."
java -cp "$WORK/out" CidrDriver || status=1
echo "[verify] biztalk domain (criteria, status, routing, tenant)..."
java -cp "$WORK/out" MessageHistoryDriver || status=1
echo "[verify] ServiceWindow (legacy 240000 idiom, per-day windows)..."
java -cp "$WORK/out" ServiceWindowDriver || status=1
echo "[verify] CsvExporter (formula injection, CSV escaping)..."
java -cp "$WORK/out" CsvExporterDriver || status=1

if [ "$status" -ne 0 ]; then
  echo "[verify] FAILED"
  exit 1
fi
echo "[verify] all JDK-only checks passed"
