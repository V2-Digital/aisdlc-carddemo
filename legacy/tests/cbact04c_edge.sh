#!/usr/bin/env bash
# CBACT04C edge cases: GnuCOBOL original vs Java rewrite must produce identical outputs.
# Covers real group hit, fallback, negative balance, truncation, overflow wrap, last-account defect,
# and the abend paths (missing DEFAULT / account / xref). Run from the repo root.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FX=$ROOT/legacy/tests/cbact04c_edge
WORK=$(mktemp -d /tmp/cbact04c-edge.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
fail=0

(cd "$ROOT/modernized/cbact04c" && mvn -q -DskipTests package) >"$WORK/mvn.log" 2>&1 \
  || { cat "$WORK/mvn.log"; echo "FAIL: java build"; exit 1; }
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar

for c in main nodefault noacct noxref; do
  INPUT_DIR=$FX/$c "$ROOT/legacy/harness/run.sh" CBACT04C "$WORK/$c.cobol" >/dev/null 2>&1
  crc=$(cat "$WORK/$c.cobol/RC.txt" 2>/dev/null || echo missing)
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar "$JAR" 2022071800 "$WORK/$c.java" \
    TCATBALF="$FX/$c/TCATBALF.txt" XREFFILE="$FX/$c/XREFFILE.txt" \
    ACCTFILE="$FX/$c/ACCTFILE.txt" DISCGRP="$FX/$c/DISCGRP.txt" >/dev/null 2>&1
  jrc=$?
  if [[ $c == main ]]; then want_rc=0; else want_rc=16; fi
  if [[ $crc != "$want_rc" || $jrc != "$want_rc" ]]; then
    echo "FAIL $c: rc cobol=$crc java=$jrc expected=$want_rc"; fail=1
  fi
  if diff -r "$WORK/$c.cobol" "$WORK/$c.java" >"$WORK/$c.diff"; then
    echo "ok   $c ($(ls "$WORK/$c.cobol" | tr '\n' ' '))"
  else
    echo "FAIL $c: outputs differ"; head -20 "$WORK/$c.diff"; fail=1
  fi
done

# sanity: the main case must really exercise non-trivial paths
n=$(wc -l <"$WORK/main.cobol/TRANSACT.txt")
[[ $n -eq 8 ]] || { echo "FAIL main: expected 8 transactions, got $n"; fail=1; }
exit $fail
