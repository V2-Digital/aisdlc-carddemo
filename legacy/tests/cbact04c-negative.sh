#!/usr/bin/env bash
# Pins COBOL truncation (not rounding) of negative interest on real CBACT04C output.
# Facts feed the Java InterestTest: -1164.87 -> -14.56, -0.01 -> 0.00 written '{' (positive zero, never '}'), -0.99 -> -0.01.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-negative
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
mkdir "$TMP/in"
cp "$FIX/TCATBALF.txt" "$TMP/in/TCATBALF.txt"
# Stock DEFAULT 01/0001 rate (15.00) with negative balances.
INPUT_DIR=$TMP/in "$ROOT/legacy/harness/run.sh" CBACT04C "$TMP/out" >/dev/null
fail=0
check() { # <tran-id suffix> <expected 11-byte amount>
  local got
  got=$(grep "^2022071800$1" "$TMP/out/TRANSACT.txt" | cut -c 133-143)
  if [[ $got == "$2" ]]; then echo "ok   tran $1 amount $got"; else echo "FAIL tran $1: got '$got' want '$2'"; fail=1; fi
}
check 000001 0000000145O
check 000002 0000000000{
check 000003 0000000000J
check 000004 0000000000{
[[ $(cat "$TMP/out/RC.txt") == 0 ]] || { echo "FAIL RC"; fail=1; }
exit $fail
