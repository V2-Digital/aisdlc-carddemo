#!/usr/bin/env bash
# Cross-check: real COBOL (GnuCOBOL harness) vs Java rewrite on edge inputs
# (negative balance, direct group hit, rate where rounding differs, zero rate, fallback).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIX="$ROOT/legacy/tests/cbact04c_edge"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/in" "$W/cobol" "$W/java"
cp "$FIX"/*.txt "$W/in/"
INPUT_DIR="$W/in" "$ROOT/legacy/harness/run.sh" CBACT04C "$W/cobol" >/dev/null 2>&1 || true
(cd "$ROOT/modernized/cbact04c" && mvn -q -DskipTests package >/dev/null)
rc=0
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar "$ROOT/modernized/cbact04c/target/cbact04c.jar" 2022071800 "$W/in" "$W/java" >/dev/null || rc=$?
echo "$rc" > "$W/java/RC.txt"
fail=0
for f in TRANSACT.txt ACCTFILE.txt RC.txt; do
  if cmp -s "$W/cobol/$f" "$W/java/$f"; then echo "PASS $f"; else echo "FAIL $f"; diff "$W/cobol/$f" "$W/java/$f" | head -5; fail=1; fi
done
n=$(wc -l < "$W/cobol/TRANSACT.txt")
[ "$n" -ge 4 ] || { echo "FAIL: expected >=4 transactions, got $n"; fail=1; }
exit $fail
