#!/usr/bin/env bash
# Runs live COBOL (harness) and the Java jar on the same inputs; diffs all four outputs.
# Run from the repo root. Exits non-zero on any difference.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
(cd modernized/cbact04c && mvn -q -DskipTests package)
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
A=app/data/ASCII fail=0

mkin() { # <dir> <tcatbal> <acct>
  mkdir -p "$1"; cp "$2" "$1/TCATBALF.txt"; cp "$3" "$1/ACCTFILE.txt"
  cp $A/cardxref.txt "$1/XREFFILE.txt"; cp $A/discgrp.txt "$1/DISCGRP.txt"
}
mkin "$W/stock" $A/tcatbal.txt $A/acctdata.txt
mkin "$W/post" legacy/golden/CBTRN02C/TCATBALF.txt legacy/golden/CBTRN02C/ACCTFILE.txt

for s in stock post; do
  INPUT_DIR="$W/$s" legacy/harness/run.sh CBACT04C "$W/cobol-$s" >/dev/null
  set +e
  java -jar "$JAR" --in "$W/$s" --out "$W/java-$s" --parm 2022071800 --clock 2022071800000000 2>/dev/null
  rc=$?; set -e
  [[ $rc -eq $(cat "$W/cobol-$s/RC.txt") ]] || { echo "FAIL $s: exit code $rc"; fail=1; }
  for f in TRANSACT ACCTFILE SYSOUT RC; do
    if cmp -s "$W/cobol-$s/$f.txt" "$W/java-$s/$f.txt"; then
      echo "ok   $s/$f.txt ($(wc -l <"$W/java-$s/$f.txt") lines)"
    else
      echo "FAIL $s/$f.txt differs"; diff "$W/cobol-$s/$f.txt" "$W/java-$s/$f.txt" | head -5; fail=1
    fi
  done
done
exit $fail
