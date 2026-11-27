#!/usr/bin/env bash
# COBOL evidence for negative balances: CBACT04C truncates interest toward zero.
# -100.00 @ 15.00 -> -1.25 ; -1.27 @ 15.00 -> -0.0158 -> -0.01 (overpunch N / J).
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
FX=$ROOT/tests/cbact04c_negative
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
INPUT_DIR=$FX "$ROOT/harness/run.sh" CBACT04C "$OUT" >/dev/null
fail=0
check() { # name expected actual
  if [[ $2 != "$3" ]]; then echo "FAIL $1: expected '$2' got '$3'"; fail=1; else echo "ok   $1"; fi
}
check rc 0 "$(tr -d '[:space:]' < "$OUT/RC.txt")"
check tran_count 3 "$(wc -l < "$OUT/TRANSACT.txt" | tr -d ' ')"
check amt_neg100 0000000012N "$(sed -n 1p "$OUT/TRANSACT.txt" | cut -c133-143)"
check amt_neg1_27 0000000000J "$(sed -n 2p "$OUT/TRANSACT.txt" | cut -c133-143)"
check amt_zero 0000000000{ "$(sed -n 3p "$OUT/TRANSACT.txt" | cut -c133-143)"
exit $fail
