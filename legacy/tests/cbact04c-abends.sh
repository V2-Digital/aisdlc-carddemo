#!/usr/bin/env bash
# Runs the real COBOL CBACT04C on abend fixtures; with --write saves SYSOUT/RC as expected.* for the Java AbendTest.
# Default mode checks the saved references still match what COBOL does (exit non-zero otherwise).
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$ROOT/legacy/tests/cbact04c-abends
fail=0
for d in account-missing xref-missing default-missing; do
  out=$(mktemp -d)
  INPUT_DIR=$T/$d "$ROOT/legacy/harness/run.sh" CBACT04C "$out" >/dev/null 2>"$out/stderr"
  rc=$(cat "$out/RC.txt")
  if [[ ${1:-} == --write ]]; then
    cp "$out/SYSOUT.txt" "$T/$d/expected-SYSOUT.txt"; echo "$rc" >"$T/$d/expected-RC.txt"
  fi
  [[ $rc == 16 ]] || { echo "FAIL $d: rc=$rc (want 16)"; fail=1; }
  grep -q 'CEE3ABD: ABEND U0999' "$out/stderr" || { echo "FAIL $d: no CEE3ABD on stderr"; fail=1; }
  cmp -s "$out/SYSOUT.txt" "$T/$d/expected-SYSOUT.txt" || { echo "FAIL $d: SYSOUT differs from expected-SYSOUT.txt"; fail=1; }
  [[ $(cat "$T/$d/expected-RC.txt" 2>/dev/null) == 16 ]] || { echo "FAIL $d: expected-RC.txt missing/not 16"; fail=1; }
  rm -rf "$out"
done
(( fail == 0 )) && echo "cbact04c-abends: OK"
exit $fail
