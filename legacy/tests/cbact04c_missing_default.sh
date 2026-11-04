#!/usr/bin/env bash
# CBACT04C abends (RC 16) when neither the account's group nor DEFAULT has a DISCGRP record.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c_missing_default
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >"$OUT.log" 2>&1
rc=$?
[[ $rc -eq 16 ]] || fail "expected exit 16, got $rc"
[[ -f $OUT/SYSOUT.txt ]] || fail "no SYSOUT.txt"
for line in 'DISCLOSURE GROUP RECORD MISSING' 'TRY WITH DEFAULT GROUP CODE' \
            'ERROR READING DEFAULT DISCLOSURE GROUP' 'ABENDING PROGRAM'; do
  grep -qF "$line" "$OUT/SYSOUT.txt" || fail "SYSOUT lacks: $line"
done
echo "PASS cbact04c_missing_default"
