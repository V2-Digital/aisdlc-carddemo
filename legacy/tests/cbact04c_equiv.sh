#!/usr/bin/env bash
# COBOL (GnuCOBOL harness) vs Java rewrite of CBACT04C on non-golden cases:
# account-group hit, negative balance (D4), missing DEFAULT group (abend).
# Run from the repo root. Exits non-zero on any difference.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
SAMPLE=app/data/ASCII
CLOCK=2022071800000000

mvn -q -f modernized/cbact04c package -DskipTests
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar

stock() { # dir
  mkdir -p "$1"
  tr -d '\r' <$SAMPLE/tcatbal.txt >"$1/TCATBALF.txt"
  tr -d '\r' <$SAMPLE/cardxref.txt >"$1/XREFFILE.txt"
  tr -d '\r' <$SAMPLE/acctdata.txt >"$1/ACCTFILE.txt"
  tr -d '\r' <$SAMPLE/discgrp.txt  >"$1/DISCGRP.txt"
}

# case 1: account 1..3 use group ZEROAPR (rate 0 for 01/0001) -> group hit, no fallback
c1=$W/in_group_hit; stock "$c1"
sed -i '1,3s/\(.\{112\}\).\{10\}/\1ZEROAPR   /' "$c1/ACCTFILE.txt"
# case 2: negative balances: accounts 1 and 2 get -1000.01 and -3577.41 in 01/0001
c2=$W/in_negative; stock "$c2"
sed -i '1s/^\(.\{17\}\).\{11\}/\10000010000J/;2s/^\(.\{17\}\).\{11\}/\10000035774J/' "$c2/TCATBALF.txt"
# case 3: DEFAULT 01/0001 removed -> abend RC 16
c3=$W/in_no_default; stock "$c3"
grep -v '^DEFAULT   010001' "$c3/DISCGRP.txt" >"$c3/D" && mv "$c3/D" "$c3/DISCGRP.txt"

fail=0
for c in in_group_hit in_negative in_no_default; do
  cob=$W/cob_$c; jav=$W/jav_$c
  set +e
  INPUT_DIR=$W/$c legacy/harness/run.sh CBACT04C "$cob" >/dev/null 2>&1
  set -e
  jrc=0
  CARDDEMO_CURRENT_DATE=$CLOCK java -jar "$JAR" --in "$W/$c" --out "$jav" \
    --parm 2022071800 --clock $CLOCK >"$W/jsys_$c" 2>"$W/jerr_$c" || jrc=$?
  crc=$(cat "$cob/RC.txt")
  [[ $crc == "$jrc" ]] || { echo "FAIL $c: RC cobol=$crc java=$jrc"; fail=1; }
  diff -q "$cob/SYSOUT.txt" "$W/jsys_$c" >/dev/null || { echo "FAIL $c: SYSOUT differs"; diff "$cob/SYSOUT.txt" "$W/jsys_$c" | head; fail=1; }
  if [[ $crc == 0 ]]; then
    for f in TRANSACT ACCTFILE; do
      cmp -s "$cob/$f.txt" "$jav/$f.txt" || { echo "FAIL $c: $f differs"; fail=1; }
    done
  fi
  echo "ok? $c: RC cobol=$crc java=$jrc"
done
# sanity: the negative case really produced credits (otherwise the test proves nothing)
awk '{print substr($0,133,11)}' "$W/cob_in_negative/TRANSACT.txt" | head -2 | grep -q '[J-R}]$' \
  || { echo "FAIL in_negative: no negative txn in COBOL output"; fail=1; }
exit $fail
