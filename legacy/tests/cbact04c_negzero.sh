#!/usr/bin/env bash
# CBACT04C: a tiny negative balance (-0.01 at DEFAULT rate 1.50%) truncates to
# -0.00; COBOL must write the zero amount with the positive sign '{'.
# The Java rewrite (modernized/cbact04c) encodes zero as '{' on this evidence.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
out=$(mktemp -d); trap 'rm -rf "$out"' EXIT
INPUT_DIR=legacy/tests/cbact04c_negzero legacy/harness/run.sh CBACT04C "$out" 2>/dev/null
[[ $(cat "$out/RC.txt") == 0 ]] || { echo "FAIL: RC != 0"; exit 1; }
[[ $(wc -l <"$out/TRANSACT.txt") -eq 1 ]] || { echo "FAIL: expected 1 transaction"; exit 1; }
amt=$(cut -c133-143 "$out/TRANSACT.txt")
[[ $amt == 0000000000'{' ]] || { echo "FAIL: amount '$amt', expected 0000000000{"; exit 1; }
echo "PASS: truncated negative interest is written as 0000000000{"
