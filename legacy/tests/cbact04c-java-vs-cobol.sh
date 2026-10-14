#!/usr/bin/env bash
# Differential test: crafted inputs run through the real COBOL (harness) and the
# Java rewrite; TRANSACT, ACCTFILE, SYSOUT and RC must be byte-identical.
# Fixtures: legacy/tests/cbact04c-java-vs-cobol/ (negative balances, group-specific
# rate, rounding-sensitive amount, overflow, last-account D1).
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-java-vs-cobol
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

(cd "$ROOT/modernized/cbact04c" && mvn -q package -DskipTests)
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar

INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$WORK/cobol" 2>/dev/null

cp "$FIX/ACCTFILE.txt" "$WORK/ACCTFILE.in.txt"
rc=0
java -jar "$JAR" --parm 2022071800 --clock 2022071800000000 \
  --TCATBALF "$FIX/TCATBALF.txt" --XREFFILE "$FIX/XREFFILE.txt" --DISCGRP "$FIX/DISCGRP.txt" \
  --ACCTFILE "$WORK/ACCTFILE.in.txt" --TRANSACT "$WORK/TRANSACT.java.txt" \
  >"$WORK/SYSOUT.java.txt" 2>/dev/null || rc=$?
echo "$rc" >"$WORK/RC.java.txt"

fail=0
cmp "$WORK/cobol/TRANSACT.txt" "$WORK/TRANSACT.java.txt" || fail=1
cmp "$WORK/cobol/ACCTFILE.txt" "$WORK/ACCTFILE.in.txt" || fail=1
cmp "$WORK/cobol/SYSOUT.txt" "$WORK/SYSOUT.java.txt" || fail=1
cmp "$WORK/cobol/RC.txt" "$WORK/RC.java.txt" || fail=1
# the fixtures must really exercise what they claim to
[[ $(wc -l <"$WORK/cobol/TRANSACT.txt") -ge 4 ]] || { echo "fixture produced too few transactions"; fail=1; }
grep -q 'J\|K\|L\|[M-R]' <(cut -c133-143 "$WORK/cobol/TRANSACT.txt") || { echo "no negative amount exercised"; fail=1; }
if (( fail )); then echo "FAIL: Java differs from COBOL"; exit 1; fi
echo "PASS: Java == COBOL on crafted inputs ($(wc -l <"$WORK/cobol/TRANSACT.txt") transactions)"
