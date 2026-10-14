#!/usr/bin/env bash
# Run the Java CBACT04C on both golden scenarios and cmp against legacy/golden/.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
[ -f "$JAR" ] || (cd "$ROOT/modernized/cbact04c" && mvn -q package -DskipTests)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
fail=0
run() { # <scenario> <acct> <tcatbal>
  local s=$1 w=$TMP/$1; mkdir -p "$w"
  cp "$2" "$w/ACCTFILE.txt"; cp "$3" "$w/TCATBALF.txt"
  sed 's/\r$//' "$ROOT/app/data/ASCII/cardxref.txt" > "$w/XREFFILE.txt"
  sed 's/\r$//' "$ROOT/app/data/ASCII/discgrp.txt" > "$w/DISCGRP.txt"
  : > "$w/TRANSACT.txt"
  set +e
  DD_TCATBALF=$w/TCATBALF.txt DD_XREFFILE=$w/XREFFILE.txt DD_DISCGRP=$w/DISCGRP.txt \
  DD_ACCTFILE=$w/ACCTFILE.txt DD_TRANSACT=$w/TRANSACT.txt \
    java -Dcarddemo.clock=2022-07-18T00:00:00 -jar "$JAR" 2022071800 > "$w/SYSOUT.txt"
  echo $? > "$w/RC.txt"; set -e
  for f in TRANSACT ACCTFILE SYSOUT RC; do
    if cmp -s "$w/$f.txt" "$ROOT/legacy/golden/$s/$f.txt"; then echo "OK   $s/$f"
    else echo "DIFF $s/$f"; fail=1; fi
  done
}
A=$ROOT/app/data/ASCII
run CBACT04C "$A/acctdata.txt" <(sed 's/\r$//' "$A/tcatbal.txt")
run CBACT04C-after-CBTRN02C "$ROOT/legacy/golden/CBTRN02C/ACCTFILE.txt" "$ROOT/legacy/golden/CBTRN02C/TCATBALF.txt"
exit $fail
