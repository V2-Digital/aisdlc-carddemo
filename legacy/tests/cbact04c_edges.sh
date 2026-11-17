#!/usr/bin/env bash
# Characterization test of the COBOL CBACT04C for edge cases absent from the golden scenarios:
# negative balance (truncation toward zero), non-DEFAULT group with a positive rate (ACCT-GROUP-ID),
# and D1 (last account not rewritten). Two cards per account cannot be loaded by the harness (status 22).
# Fixtures + expected COBOL outputs live in legacy/tests/cbact04c_edges/ (expected/ = COBOL output).
# The Java project asserts byte-equality against the same expected/ files.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$ROOT/legacy/tests/cbact04c_edges
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
IN=$OUT/in; mkdir "$IN"
for dd in TCATBALF XREFFILE DISCGRP ACCTFILE; do cp "$T/$dd.txt" "$IN/"; done
INPUT_DIR=$IN "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT/o" >/dev/null 2>&1 || true
if [[ "${REGEN:-0} " == "1 " ]]; then mkdir -p "$T/expected"; cp "$OUT"/o/*.txt "$T/expected/"; fi
fail=0
for f in TRANSACT ACCTFILE SYSOUT RC; do
  cmp -s "$OUT/o/$f.txt" "$T/expected/$f.txt" || { echo "FAIL: $f differs"; diff "$OUT/o/$f.txt" "$T/expected/$f.txt" | head; fail=1; }
done
# explicit rule checks on real COBOL output
tr=$OUT/o/TRANSACT.txt
[[ $(wc -l < "$tr") -eq 4 ]] || { echo "FAIL: expected 4 transactions"; fail=1; }
[[ $(cut -c133-143 "$tr" | sed -n 3p) == "0000000012N" ]] || { echo "FAIL: negative balance not truncated toward zero"; fail=1; }
[[ $(cut -c133-143 "$tr" | sed -n 2p) == "0000000000{" ]] || { echo "FAIL: -0.00 should be written as {"; fail=1; }
[[ $(cut -c133-143 "$tr" | sed -n 1p) == "0000000005{" ]] || { echo "FAIL: non-default group rate"; fail=1; }
[[ $fail -eq 0 ]] && echo "cbact04c_edges: PASS"
exit $fail
