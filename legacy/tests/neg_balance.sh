#!/usr/bin/env bash
# CBACT04C with a negative category balance: interest truncates toward zero and is written with a
# negative overpunch. Fixture: legacy/tests/neg_balance/TCATBALF.txt (acct 1 type 01 cat 0001 bal -1512.77).
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/neg_balance
OUT=$(mktemp -d); IN=$(mktemp -d)
trap 'rm -rf "$OUT" "$IN"' EXIT
cp "$FIX/TCATBALF.txt" "$IN/TCATBALF.txt"
INPUT_DIR=$IN "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1 || { echo "FAIL: run.sh"; exit 1; }
amt=$(head -1 "$OUT/TRANSACT.txt" | cut -c133-143)
[[ $(cat "$OUT/RC.txt") == 0* ]] || { echo "FAIL: RC"; exit 1; }
[[ $amt == "0000000189}" ]] || { echo "FAIL: amount '$amt' != 0000000189} (-18.90)"; exit 1; }
echo "PASS neg_balance: -1512.77 @15% -> $amt"
