#!/usr/bin/env bash
# CBACT04C Java port vs the real COBOL program on crafted inputs the golden
# scenarios do not cover: negative balances, truncation to -0.00, a real
# disclosure-group hit, rate 0, and the three abend paths.
# Run from the repo root. Exits non-zero on any difference.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
FIX=legacy/tests/cbact04c-edge
JAR=modernized/cbact04c/target/cbact04c.jar
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f $JAR ]]; then
  (cd modernized/cbact04c && mvn -q -DskipTests package) || { echo "FAIL: jar build"; exit 1; }
fi

fail=0
for dir in "$FIX"/*/; do
  case=$(basename "$dir")
  cob=$WORK/$case/cobol; jav=$WORK/$case/java
  mkdir -p "$cob" "$jav"
  INPUT_DIR=$dir legacy/harness/run.sh CBACT04C "$cob" >/dev/null 2>&1
  crc=$(cat "$cob/RC.txt")
  java -jar "$JAR" 2022071800 --TCATBALF="$dir/TCATBALF.txt" --XREFFILE="$dir/XREFFILE.txt" \
    --ACCTFILE="$dir/ACCTFILE.txt" --DISCGRP="$dir/DISCGRP.txt" --out="$jav" \
    --clock=2022071800000000 >"$jav/SYSOUT.txt"
  jrc=$?
  ok=1
  [[ $crc == "$jrc" ]] || { echo "[$case] RC cobol=$crc java=$jrc"; ok=0; }
  cmp -s "$cob/SYSOUT.txt" "$jav/SYSOUT.txt" || { echo "[$case] SYSOUT differs"; diff "$cob/SYSOUT.txt" "$jav/SYSOUT.txt" | head; ok=0; }
  # the harness exports no datasets after an abend (run.sh exits 16 first), so compare outputs on RC 0 only
  if [[ $crc == 0 ]]; then
    for f in TRANSACT ACCTFILE; do
      cmp -s "$cob/$f.txt" "$jav/$f.txt" || { echo "[$case] $f differs"; diff "$cob/$f.txt" "$jav/$f.txt" | head; ok=0; }
    done
  fi
  if (( ok )); then echo "PASS $case (rc=$crc)"; else echo "FAIL $case"; fail=1; fi
done
exit $fail
