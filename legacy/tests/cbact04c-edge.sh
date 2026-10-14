#!/usr/bin/env bash
# COBOL vs Java cross-check of CBACT04C paths that have no golden copy:
# negative balance, balance overflow, and the three abends.
# For each case both implementations run on the same inputs; SYSOUT and RC
# (and, when COBOL completes, TRANSACT/ACCTFILE) must be byte-identical.
# (Duplicate xrefs cannot be loaded by the harness: its AIX has no DUPLICATES.)
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
ROOT=$PWD
A=$ROOT/app/data/ASCII
B=$ROOT/legacy/golden/CBTRN02C
mvn -q -f modernized/cbact04c -DskipTests package >/dev/null || { echo "FAIL: mvn package"; exit 1; }
JAR=$(ls "$ROOT"/modernized/cbact04c/target/cbact04c*.jar | grep -v original | head -1)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
fails=0

mk() { # mk <case>: baseline inputs (post-CBTRN02C balances) in $W/<case>/in
  mkdir -p "$W/$1/in"
  cp "$B/TCATBALF.txt" "$B/ACCTFILE.txt" "$W/$1/in/"
  tr -d '\r' <"$A/cardxref.txt" >"$W/$1/in/XREFFILE.txt"
  tr -d '\r' <"$A/discgrp.txt" >"$W/$1/in/DISCGRP.txt"
}

# negative: first category balance -> -123.45 (overpunch N = -5)
mk negative
sed -i '1s/^\(.\{17\}\).\{11\}/\10000001234N/' "$W/negative/in/TCATBALF.txt"
# overflow: account 1 balance 9999999999.99 so that + interest wraps
mk overflow
sed -i '1s/^\(.\{12\}\).\{12\}/\199999999999I/' "$W/overflow/in/ACCTFILE.txt"
# abends
mk no_account;  grep -v '^00000000002' "$W/no_account/in/ACCTFILE.txt" >"$W/x" && mv "$W/x" "$W/no_account/in/ACCTFILE.txt"
mk no_xref;     grep -v '00000000002 *$' "$W/no_xref/in/XREFFILE.txt" >"$W/x" && mv "$W/x" "$W/no_xref/in/XREFFILE.txt"
mk no_default;  grep -v '^DEFAULT   0100*1' "$W/no_default/in/DISCGRP.txt" >"$W/x" && mv "$W/x" "$W/no_default/in/DISCGRP.txt"

for c in negative overflow no_account no_xref no_default; do
  d=$W/$c; mkdir -p "$d/cobol" "$d/java"
  INPUT_DIR=$d/in "$ROOT/legacy/harness/run.sh" CBACT04C "$d/cobol" >/dev/null 2>"$d/cobol.err"
  mkdir -p "$d/jout"
  INPUT_DIR=$d/in OUTPUT_DIR=$d/java COB_CURRENT_DATE=2022071800000000 \
    java -jar "$JAR" 2022071800 >"$d/java/SYSOUT.txt" 2>"$d/java.err"
  echo $? >"$d/java/RC.txt"
  files=(SYSOUT.txt RC.txt)
  [[ $(cat "$d/cobol/RC.txt") == 0 ]] && files+=(TRANSACT.txt ACCTFILE.txt)
  ok=1
  for f in "${files[@]}"; do
    cmp -s "$d/cobol/$f" "$d/java/$f" || { ok=0; echo "  DIFF $c/$f"; diff "$d/cobol/$f" "$d/java/$f" | head -5; }
  done
  if (( ok )); then echo "PASS $c (rc=$(cat "$d/java/RC.txt"), compared: ${files[*]})"; else echo "FAIL $c"; fails=1; fi
done
exit $fails
