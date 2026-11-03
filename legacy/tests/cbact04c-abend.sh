#!/usr/bin/env bash
# CBACT04C abend paths on the real COBOL: RC 16 and the exact SYSOUT tail.
# These lines are what modernized/cbact04c AbendTest mirrors.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
FIX=legacy/tests/cbact04c-abend
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
fail=0

check() { # name inputdir expected-tail-file
  local name=$1 in=$2 exp=$3
  INPUT_DIR=$in legacy/harness/run.sh CBACT04C "$TMP/$name" >/dev/null 2>&1
  local rc; rc=$(tr -d '[:space:]' <"$TMP/$name/RC.txt" 2>/dev/null)
  if [[ $rc != 16 ]]; then echo "FAIL $name: RC=$rc, expected 16"; fail=1; return; fi
  local n; n=$(wc -l <"$FIX/$exp")
  if ! tail -n "$n" "$TMP/$name/SYSOUT.txt" | diff - "$FIX/$exp" >/dev/null; then
    echo "FAIL $name: SYSOUT tail differs"; tail -n "$n" "$TMP/$name/SYSOUT.txt" | diff - "$FIX/$exp"; fail=1; return
  fi
  echo "ok   $name"
}

# A: account missing
mkdir -p "$TMP/a"; cp "$FIX/a/TCATBALF.txt" "$TMP/a/"
check account "$TMP/a" account.expected

# X: account exists, xref has no row for it (drop acct 1's xref row)
mkdir -p "$TMP/x"; cp "$FIX/x/TCATBALF.txt" "$TMP/x/"
awk '{ if (substr($0,26,11) != "00000000001") print }' app/data/ASCII/cardxref.txt | tr -d '\r' >"$TMP/x/XREFFILE.txt"
check xref "$TMP/x" xref.expected

# D: no DEFAULT disclosure group row
mkdir -p "$TMP/d"; cp "$FIX/d/TCATBALF.txt" "$FIX/d/DISCGRP.txt" "$TMP/d/"
check default "$TMP/d" default.expected

exit $fail
