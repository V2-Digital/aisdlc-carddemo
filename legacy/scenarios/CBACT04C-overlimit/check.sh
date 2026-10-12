#!/usr/bin/env bash
# CR-2 over-limit fee scenario. Runs CBACT04C on input/ and checks it against expected/
# (built from golden + hand arithmetic by make-fixtures.py) plus direct AC 2-7 assertions.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
GOLD=$ROOT/legacy/golden/CBACT04C-after-CBTRN02C
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
fail=0
ok()  { echo "PASS $*"; }
bad() { echo "FAIL $*"; fail=1; }
chk() { local n=$1; shift; if "$@"; then ok "$n"; else bad "$n"; fi; }

INPUT_DIR=$HERE/input "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1
for f in TRANSACT ACCTFILE SYSOUT RC; do
  chk "byte-identical to expected/$f.txt" cmp -s "$OUT/$f.txt" "$HERE/expected/$f.txt"
done
chk "RC is 0 (AC9)" test "$(tr -d '[:space:]' <"$OUT/RC.txt")" = 0
T=$OUT/TRANSACT.txt A=$OUT/ACCTFILE.txt
chk "51 transactions" test "$(wc -l <"$T")" = 51
chk "AC2 exactly 2 fee records (cat 0006)" test "$(cut -c17-22 "$T" | grep -c '^010006$')" = 2
chk "AC2 fees are for a/c 1 and 3" test "$(awk 'substr($0,17,6)=="010006"{printf "%s ",substr($0,56,11)}' "$T")" = "00000000001 00000000003 "
chk "AC2 a/c 1 has 2 TCATBAL records" test "$(grep -c '^00000000001' "$HERE/input/TCATBALF.txt")" = 2
chk "AC3 fee amount/source/merchant" test "$(awk 'substr($0,17,6)=="010006"&&substr($0,23,10)=="System    "&&substr($0,133,11)=="0000000250{"&&substr($0,144,9)=="000000000"' "$T" | wc -l)" = 2
chk "AC3 IDs unique and 000001..000051" test "$(cut -c1-16 "$T" | awk '{print substr($0,11)+0}' | sort -n | tr '\n' ' ')" = "$(seq 1 51 | tr '\n' ' ')"
for a in 1 3; do
  acct=$(printf %011d $a)
  card=$(awk -v a="$acct" 'substr($0,26,11)==a{print substr($0,1,16)}' "$ROOT/app/data/ASCII/cardxref.txt")
  chk "AC3 a/c $a fee card = xref card" test "$(awk -v a="$acct" 'substr($0,17,6)=="010006"&&substr($0,56,11)==a{print substr($0,263,16)}' "$T")" = "$card"
done
chk "AC4 a/c 1 balance 00000013276F" test "$(sed -n 1p "$A" | cut -c13-24)" = 00000013276F
chk "AC4 a/c 3 balance 00000018826E" test "$(sed -n 3p "$A" | cut -c13-24)" = 00000018826E
for n in 1 2 3; do chk "AC4 a/c $n cycle credit/debit zero" test "$(sed -n ${n}p "$A" | cut -c79-102)" = "00000000000{00000000000{"; done
chk "AC5 a/c 2 (bal = limit) balance = golden, no fee" test "$(sed -n 2p "$A" | cut -c13-24)" = "$(sed -n 2p "$GOLD/ACCTFILE.txt" | cut -c13-24)"
chk "AC6 a/c 3 has fee and no interest" test "$(grep -c 'a/c 00000000003 ' "$T")" = 1
chk "AC7 TRANSACT w/o fees (cols 17-350) = golden minus a/c 3" \
  cmp -s <(grep -v '^................010006' "$T" | cut -c17-350) <(sed 3d "$GOLD/TRANSACT.txt" | cut -c17-350)
chk "AC7 ACCTFILE a/c 4-50 = golden" cmp -s <(sed -n '4,$p' "$A") <(sed -n '4,$p' "$GOLD/ACCTFILE.txt")
[ $fail = 0 ] && echo "ALL PASS" || echo "FAILURES"
exit $fail
