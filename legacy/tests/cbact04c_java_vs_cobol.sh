#!/usr/bin/env bash
# Live cross-check: run COBOL CBACT04C (harness) and the Java rewrite on the same inputs, cmp the outputs.
# Scenarios: stock data, and after CBTRN02C (INPUT_DIR=legacy/golden/CBTRN02C).
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
[[ -f $JAR ]] || (cd "$ROOT/modernized/cbact04c" && mvn -q package -DskipTests)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
SAMPLE=$ROOT/app/data/ASCII
fail=0
for scen in stock after-cbtrn02c; do
  in=$TMP/$scen/in; cobol=$TMP/$scen/cobol; java_out=$TMP/$scen/java
  mkdir -p "$in" "$cobol" "$java_out"
  src=(TCATBALF:tcatbal.txt XREFFILE:cardxref.txt DISCGRP:discgrp.txt ACCTFILE:acctdata.txt)
  for s in "${src[@]}"; do cp "$SAMPLE/${s#*:}" "$in/${s%%:*}.txt"; done
  if [[ $scen == after-cbtrn02c ]]; then
    for dd in ACCTFILE TCATBALF; do cp "$ROOT/legacy/golden/CBTRN02C/$dd.txt" "$in/$dd.txt"; done
    INPUT_DIR=$ROOT/legacy/golden/CBTRN02C "$ROOT/legacy/harness/run.sh" CBACT04C "$cobol" 2>/dev/null
  else
    "$ROOT/legacy/harness/run.sh" CBACT04C "$cobol" 2>/dev/null
  fi
  cp "$in/ACCTFILE.txt" "$java_out/ACCTFILE.txt"  # ACCTFILE is I-O: rewritten in place
  rc=0
  env DD_TCATBALF="$in/TCATBALF.txt" DD_XREFFILE="$in/XREFFILE.txt" DD_DISCGRP="$in/DISCGRP.txt" \
      DD_ACCTFILE="$java_out/ACCTFILE.txt" DD_TRANSACT="$java_out/TRANSACT.txt" \
      CARDDEMO_CURRENT_DATE=2022071800000000 \
      java -jar "$JAR" 2022071800 >"$java_out/SYSOUT.txt" 2>/dev/null || rc=$?
  echo "$rc" >"$java_out/RC.txt"
  for f in TRANSACT ACCTFILE SYSOUT RC; do
    if cmp -s "$cobol/$f.txt" "$java_out/$f.txt"; then echo "OK   $scen $f"; else echo "DIFF $scen $f"; fail=1; fi
  done
done
exit $fail
