#!/usr/bin/env bash
# Differential test: GnuCOBOL CBACT04C vs the Java rewrite on a synthetic dataset
# (direct DISCGRP hit, negatives, -0.00x, ACCTFILE overflow, mid-run all-0% account).
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/in" "$W/cobol" "$W/java"

# z <cents> <width>: zoned decimal with trailing overpunch
z() { local c=$1 w=$2 neg=0 d last; ((c<0)) && { neg=1; c=$((-c)); }
  d=$(printf "%0${w}d" "$c"); last=${d: -1}
  local P=({ A B C D E F G H I) N=("}" J K L M N O P Q R)
  if ((neg)); then echo -n "${d:0:w-1}${N[$last]}"; else echo -n "${d:0:w-1}${P[$last]}"; fi; }
pad() { printf "%-${2}s" "$1"; }
tcat() { pad "$(printf '%s%s%s%s' "$1" "$2" "$3" "$(z "$4" 11)")" 50; echo; }
xref() { pad "$(printf '%s%s%s' "$2" "000000001" "$1")" 50; echo; }
acct() { pad "$(printf '%sY%s%s%s%s%s%s%s%s%s' "$1" "$(z "$2" 12)" "$(z 500000 12)" "$(z 500000 12)" 2020-01-01 2030-01-01 2030-01-01 "$(z 11111 12)" "$(z -2222 12)" "1234567890")" 112; printf '%-10s' "$3"; printf '%*s\n' 178 ""; }
disc() { pad "$(printf '%-10s%s%s%s' "$1" "$2" "$3" "$4")" 50; echo; }

{ tcat 00000000001 01 0001 100000
  tcat 00000000002 01 0001 -50
  tcat 00000000002 02 0001 -1000
  tcat 00000000003 03 0001 5000
  tcat 00000000003 03 0002 7000
  tcat 00000000004 01 0001 10000
  tcat 00000000005 01 0001 123456; } > "$W/in/TCATBALF.txt"
{ xref 00000000001 1111111111111111; xref 00000000002 2222222222222222
  xref 00000000003 3333333333333333; xref 00000000004 4444444444444444
  xref 00000000005 5555555555555555; } > "$W/in/XREFFILE.txt"
{ acct 00000000001 150000 GOLD; acct 00000000002 -30000 ""
  acct 00000000003 99900 ""; acct 00000000004 999999999999 ""
  acct 00000000005 70000 ""; } > "$W/in/ACCTFILE.txt"
{ disc GOLD 01 0001 "00120{"; disc DEFAULT 01 0001 "00150{"; disc DEFAULT 02 0001 "00150{"
  disc DEFAULT 03 0001 "00000{"; disc DEFAULT 03 0002 "00000{"; } > "$W/in/DISCGRP.txt"

INPUT_DIR="$W/in" legacy/harness/run.sh CBACT04C "$W/cobol" >/dev/null 2>&1 || true
[[ -f $W/cobol/TRANSACT.txt ]] || { echo "FAIL: COBOL run produced no output"; exit 1; }

JAR=modernized/cbact04c/target/cbact04c.jar
[[ -f $JAR ]] || mvn -q -f modernized/cbact04c package -DskipTests
cp "$W/in/ACCTFILE.txt" "$W/java/ACCTFILE.txt"
set +e
DD_TCATBALF=$W/in/TCATBALF.txt DD_XREFFILE=$W/in/XREFFILE.txt DD_DISCGRP=$W/in/DISCGRP.txt \
DD_ACCTFILE=$W/java/ACCTFILE.txt DD_TRANSACT=$W/java/TRANSACT.txt CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -jar "$JAR" 2022071800 > "$W/java/SYSOUT.txt"
echo $? > "$W/java/RC.txt"
set -e

rc=0
for f in TRANSACT ACCTFILE SYSOUT RC; do
  if cmp -s "$W/cobol/$f.txt" "$W/java/$f.txt"; then echo "ok   $f"
  else echo "FAIL $f"; diff "$W/cobol/$f.txt" "$W/java/$f.txt" | head -20; rc=1; fi
done
exit $rc
