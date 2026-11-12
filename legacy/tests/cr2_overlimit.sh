#!/usr/bin/env bash
# CR-2 test: over-limit fee in CBACT04C. Usage: legacy/tests/cr2_overlimit.sh
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$ROOT/legacy/tests/cr2_overlimit
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
FAIL=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got [$2] want [$3]"; FAIL=1; fi; }

INPUT_DIR=$T/input "$ROOT/legacy/harness/run.sh" CBACT04C "$W/new" >/dev/null 2>&1
check "RC 0" "$(cat "$W/new/RC.txt")" 0
for f in TRANSACT ACCTFILE SYSOUT RC; do
  cmp -s "$W/new/$f.txt" "$T/expected/$f.txt" && echo "ok   expected/$f" || { echo "FAIL expected/$f differs"; FAIL=1; }
done
TX=$W/new/TRANSACT.txt
fee() { grep -a "^................010006" "$TX" | grep -a "a/c $1 " ; }
SP76=$(printf '%76s' '')
# AC3/AC4: exactly one fee for accts 1 and 2
for a in 00000000001 00000000002; do
  check "AC3 one fee $a" "$(fee $a | wc -l | tr -d ' ')" 1
  r=$(fee $a)
  check "AC3 amount $a" "${r:132:11}" "0000000250{"
  check "AC3 source/desc $a" "${r:22:10}|${r:32:100}" "System    |Over-limit fee for a/c $a$(printf '%66s' '')"
  card=$(awk -v a=$a 'substr($0,26,11)==a{print substr($0,1,16)}' "$ROOT/app/data/ASCII/cardxref.txt")
  check "AC4 card $a" "${r:262:16}" "$card"
  check "AC4 ts $a" "${r:278:26}|${r:304:26}" "2022-07-18-00.00.00.000000|2022-07-18-00.00.00.000000"
done
check "total fee records" "$(grep -ac '^................010006' "$TX")" 2
# AC4: IDs unique and sequential
ids=$(cut -c1-16 "$TX"); want=$(for i in $(seq 1 52); do printf '2022071800%06d\n' $i; done)
check "AC4 ids sequential" "$ids" "$want"
# AC5: balances
bal() { awk -v a=$1 'substr($0,1,11)==a{print substr($0,13,12)}' "$2"; }
check "AC5 acct1 bal" "$(bal 00000000001 "$W/new/ACCTFILE.txt")" "00000027541F"   # 2500.00+125.00+104.16+25.00
check "AC5 acct2 bal" "$(bal 00000000002 "$W/new/ACCTFILE.txt")" "00000070250{"   # 7000.00+0+25.00
# AC6: at limit and below limit: unchanged, no fee
check "AC6 acct3 (=limit) no fee" "$(fee 00000000003 | wc -l | tr -d ' ')" 0
check "AC6 acct4 (<limit) no fee" "$(fee 00000000004 | wc -l | tr -d ' ')" 0
# AC8: interest after fee has clean description
r=$(grep -a "Int. for a/c 00000000001 " "$TX" | head -1)
check "AC8 desc" "${r:32:100}" "Int. for a/c 00000000001$(printf '%76s' '')"

# AC7: baseline program on the same input; non-fee records equal with TRAN-ID masked
mkdir -p "$W/base/app"; cp -r "$ROOT/app/." "$W/base/app/"
git -C "$ROOT" show baseline:app/cbl/CBACT04C.cbl > "$W/base/app/cbl/CBACT04C.cbl"
CARDDEMO_HOME=$W/base INPUT_DIR=$T/input "$ROOT/legacy/harness/run.sh" CBACT04C "$W/old" >/dev/null 2>&1
a=$(grep -av '^................010006' "$TX" | cut -c17-)
b=$(cut -c17- "$W/old/TRANSACT.txt")
check "AC7 non-fee records match baseline" "$a" "$b"

# AC9: trancatg.txt
C=$ROOT/app/data/ASCII/trancatg.txt
check "AC9 19 records" "$(wc -l < "$C" | tr -d ' ')" 19
sort -c "$C" 2>/dev/null && echo "ok   AC9 sorted" || { echo "FAIL AC9 not sorted"; FAIL=1; }
check "AC9 line lengths" "$(awk '{print length($0)}' "$C" | sort -u | tr '\n' ' ')" "61 "   # 60 + CR
check "AC9 new line" "$(sed -n 6p "$C" | tr -d '\r')" "$(printf '010006Over-limit Fee%36s0000' '')"
exit $FAIL
