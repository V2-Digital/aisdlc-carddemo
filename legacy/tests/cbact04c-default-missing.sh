#!/usr/bin/env bash
# CBACT04C abends (RC 16) when the DEFAULT disclosure group is missing.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-default-missing
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1
fail=0
rc=$(tr -d '[:space:]' < "$OUT/RC.txt")
[[ $rc == 16 ]] || { echo "FAIL: RC '$rc', expected 16"; fail=1; }
tail -6 "$OUT/SYSOUT.txt" | grep -q 'ABENDING PROGRAM' || { echo "FAIL: no ABENDING PROGRAM"; fail=1; }
grep -q 'END OF EXECUTION' "$OUT/SYSOUT.txt" && { echo "FAIL: END message present"; fail=1; }
grep -qx 'ERROR READING DEFAULT DISCLOSURE GROUP' "$OUT/SYSOUT.txt" || { echo "FAIL: missing error text"; fail=1; }
[[ $fail == 0 ]] && echo "PASS cbact04c-default-missing"
exit $fail
