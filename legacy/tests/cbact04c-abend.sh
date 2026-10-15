#!/usr/bin/env bash
# CBACT04C abend paths (missing account / missing xref): real COBOL vs the Java port.
# Compares SYSOUT + exit code of both against the expected files in cbact04c-abend/<case>/.
# Read-only use of the harness. Run from anywhere.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$ROOT/legacy/tests/cbact04c-abend
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
fail=0
(cd "$ROOT/modernized/cbact04c" && mvn -q -o compile 2>/dev/null || mvn -q compile) || { echo "FAIL: java build"; exit 1; }
for c in noacct noxref; do
  # real COBOL (run.sh exits 16 on abend; SYSOUT/RC are still written)
  INPUT_DIR=$T/$c "$ROOT/legacy/harness/run.sh" CBACT04C "$WORK/cobol-$c" >/dev/null 2>&1
  # Java port
  mkdir -p "$WORK/java-$c"
  java -cp "$ROOT/modernized/cbact04c/target/classes" com.v2.carddemo.cbact04c.Main \
    --in "$ROOT/legacy/tests/cbact04c-abend/$c" --out "$WORK/java-$c" --clock 2022071800000000 \
    >"$WORK/java-$c/SYSOUT.txt" 2>/dev/null
  echo $? >"$WORK/java-$c/RC.txt"
  # Inputs here are 4 files only: the harness fills nothing else, so both runs saw identical data.
  for impl in cobol java; do
    for f in SYSOUT.txt RC.txt; do
      if [[ ! -f $WORK/$impl-$c/$f ]]; then echo "FAIL $c: $impl produced no $f"; fail=1
      elif ! diff -q "$T/$c/expected-$f" "$WORK/$impl-$c/$f" >/dev/null; then
        echo "FAIL $c: $impl $f differs from expected"; diff "$T/$c/expected-$f" "$WORK/$impl-$c/$f" | head; fail=1
      fi
    done
  done
  [[ $fail == 0 ]] && echo "ok $c: COBOL and Java agree (RC $(cat "$WORK/cobol-$c/RC.txt"))"
done
exit $fail
