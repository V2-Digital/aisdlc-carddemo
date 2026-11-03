#!/usr/bin/env bash
# CBACT04C abend paths on real COBOL: unknown account, missing XREF, missing DEFAULT disclosure group.
# Each must exit RC 16 with the legacy message lines. Run from the repo root.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
fail=0
check() { # name indir expected-lines...
  local name=$1 in=$2; shift 2
  INPUT_DIR=$in "$ROOT/legacy/harness/run.sh" CBACT04C "$T/out-$name" >/dev/null 2>&1
  local rc; rc=$(tr -d '[:space:]' < "$T/out-$name/RC.txt")
  [[ $rc == 16 ]] || { echo "FAIL $name: RC=$rc, expected 16"; fail=1; }
  for l in "$@"; do
    grep -qxF "$l" "$T/out-$name/SYSOUT.txt" || { echo "FAIL $name: missing SYSOUT line '$l'"; fail=1; }
  done
  echo "ok   $name (RC $rc)"
}
first=$(head -1 "$ROOT/app/data/ASCII/tcatbal.txt" | tr -d '\r')

mkdir -p "$T/acct" "$T/xref" "$T/disc"
echo "99999999999${first:11}" > "$T/acct/TCATBALF.txt"
check unknown-account "$T/acct" "ACCOUNT NOT FOUND: 99999999999" "ERROR READING ACCOUNT FILE" "FILE STATUS IS: NNNN0023" "ABENDING PROGRAM"

echo "$first" > "$T/xref/TCATBALF.txt"; : > "$T/xref/XREFFILE.txt"
check missing-xref "$T/xref" "ACCOUNT NOT FOUND: 00000000001" "ERROR READING XREF FILE" "FILE STATUS IS: NNNN0023" "ABENDING PROGRAM"

echo "$first" > "$T/disc/TCATBALF.txt"; : > "$T/disc/DISCGRP.txt"
check missing-default "$T/disc" "DISCLOSURE GROUP RECORD MISSING" "TRY WITH DEFAULT GROUP CODE" "ERROR READING DEFAULT DISCLOSURE GROUP" "FILE STATUS IS: NNNN0023" "ABENDING PROGRAM"

exit $fail
