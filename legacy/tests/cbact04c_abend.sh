#!/usr/bin/env bash
# Runs the COBOL CBACT04C with a DISCGRP that has no DEFAULT rows; asserts abend (RC 16,
# SYSOUT tail ABENDING PROGRAM) and saves SYSOUT for comparison with the Java port.
#   legacy/tests/cbact04c_abend.sh [OUT_DIR]
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(dirname "$(dirname "$HERE")")
OUT=${1:-$(mktemp -d)/abend}
IN=$(mktemp -d)
grep -v '^DEFAULT' "$ROOT/app/data/ASCII/discgrp.txt" >"$IN/DISCGRP.txt"
INPUT_DIR=$IN "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1
rc=$(cat "$OUT/RC.txt")
[[ $rc == 16 ]] || { echo "FAIL: RC=$rc, expected 16"; exit 1; }
[[ $(tail -n 1 "$OUT/SYSOUT.txt") == "ABENDING PROGRAM" ]] || { echo "FAIL: SYSOUT tail"; exit 1; }
echo "OK: RC=16, SYSOUT saved to $OUT/SYSOUT.txt"
tail -n 5 "$OUT/SYSOUT.txt"
