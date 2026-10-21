#!/usr/bin/env bash
# CBACT04C abend reference: the real COBOL must end with RC 16 and the exact SYSOUT in
# cbact04c-abends/<case>/SYSOUT.expected (the Java rewrite is tested against the same files).
set -u
cd "$(dirname "$0")/../.."
fail=0
for c in noacct noxref nodefault; do
  d=legacy/tests/cbact04c-abends/$c
  out=$(mktemp -d)
  INPUT_DIR=$d legacy/harness/run.sh CBACT04C "$out" >/dev/null 2>&1
  rc=$(cat "$out/RC.txt")
  [[ $rc == 16 ]] || { echo "FAIL $c: RC=$rc, expected 16"; fail=1; }
  if [[ -n ${UPDATE_EXPECTED:-} ]]; then cp "$out/SYSOUT.txt" "$d/SYSOUT.expected"; fi
  diff "$d/SYSOUT.expected" "$out/SYSOUT.txt" >/dev/null || { echo "FAIL $c: SYSOUT differs"; diff "$d/SYSOUT.expected" "$out/SYSOUT.txt"; fail=1; }
  rm -rf "$out"
done
[[ $fail == 0 ]] && echo "cbact04c-abends: PASS (3 cases, RC 16, SYSOUT exact)"
exit $fail
