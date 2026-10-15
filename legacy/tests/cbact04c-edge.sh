#!/usr/bin/env bash
# Edge cases for CBACT04C: runs the COBOL (harness) and the Java rewrite on the
# same synthetic inputs and requires identical SYSOUT, exit code, TRANSACT, ACCTFILE.
# Fixtures: legacy/tests/cbact04c-edge/<case>/<DD>.txt (missing DDs = stock data).
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-edge
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/classes"
javac --release 21 -d "$T/classes" "$ROOT"/modernized/cbact04c/src/main/java/com/v2/carddemo/cbact04c/*.java || exit 1
fail=0
for c in e1 e2 e3 grp neg; do
  in=$T/in-$c; mkdir -p "$in"
  for dd in TCATBALF:tcatbal XREFFILE:cardxref ACCTFILE:acctdata DISCGRP:discgrp; do
    n=${dd%%:*}; s=${dd##*:}
    if [[ -f $FIX/$c/$n.txt ]]; then cp "$FIX/$c/$n.txt" "$in/$n.txt"; else cp "$ROOT/app/data/ASCII/$s.txt" "$in/$n.txt"; fi
  done
  INPUT_DIR=$in "$ROOT/legacy/harness/run.sh" CBACT04C "$T/cob-$c" >/dev/null 2>&1
  crc=$(cat "$T/cob-$c/RC.txt" 2>/dev/null || echo none)
  mkdir -p "$T/java-$c"
  java -cp "$T/classes" com.v2.carddemo.cbact04c.Main 2022071800 "$in" "$T/java-$c" >"$T/java-$c/SYSOUT.txt" 2>/dev/null
  jrc=$?
  ok=1
  [[ $crc == "$jrc" ]] || { echo "FAIL $c: rc cobol=$crc java=$jrc"; ok=0; }
  # Java uses the wall clock here; mask timestamps (positions are fixed width) before comparing
  diff <(grep -v '^$' "$T/cob-$c/SYSOUT.txt") <(grep -v '^$' "$T/java-$c/SYSOUT.txt") >/dev/null || { echo "FAIL $c: SYSOUT differs"; ok=0; }
  if [[ $crc == 0 ]]; then
    diff "$T/cob-$c/ACCTFILE.txt" "$T/java-$c/ACCTFILE.txt" >/dev/null || { echo "FAIL $c: ACCTFILE differs"; ok=0; }
    mask() { cut -c1-277 "$1"; }
    diff <(mask "$T/cob-$c/TRANSACT.txt") <(mask "$T/java-$c/TRANSACT.txt") >/dev/null || { echo "FAIL $c: TRANSACT differs"; ok=0; }
  fi
  [[ $ok == 1 ]] && echo "ok   $c (rc=$crc)" || fail=1
done
exit $fail
