#!/usr/bin/env bash
# CR-2: over-limit fee in CBACT04C. Runs scenario cr2-overlimit twice and checks it.
#   legacy/tests/cr2_overlimit_fee.sh
set -uo pipefail
T=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(dirname "$(dirname "$T")")
S=$T/cr2-overlimit
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
fail=0
bad() { echo "FAIL: $*"; fail=1; }

for i in 1 2; do
  INPUT_DIR=$S/input "$ROOT/legacy/harness/run.sh" CBACT04C "$W/run$i" >/dev/null 2>&1 || bad "run $i exited non-zero"
  diff -r "$S/expected" "$W/run$i" >/dev/null || bad "run $i differs from expected/"
done
O=$W/run1
T_=$O/TRANSACT.txt

# one fee tran per over-limit account {1,2,4,50}, none for the rest
for n in $(seq 1 50); do
  id=$(printf %011d "$n")
  c=$(grep -c "^................010006System    Over-limit fee for a/c $id " "$T_")
  case $n in 1|2|4|50) want=1 ;; *) want=0 ;; esac
  [ "$c" -eq "$want" ] || bad "acct $id: $c fee trans, want $want"
done
[ "$(grep -c '^................010006' "$T_")" -eq 4 ] || bad "total fee trans != 4"

# fee fields: amount, desc, timestamps, card num, id sequence; interest desc tail stays blank
out=$(awk '/^................010006/ {
  if (substr($0,133,11)!="0000000250{") print "amount " NR
  if (substr($0,33,100) !~ /^Over-limit fee for a\/c [0-9]{11} *$/) print "desc " NR
  if (substr($0,279,26)!="2022-07-18-00.00.00.000000" || substr($0,305,26)!="2022-07-18-00.00.00.000000") print "ts " NR
  if (substr($0,263,16) !~ /^[0-9]{16}$/) print "card " NR }
{ if (substr($0,1,10)!="2022071800" || substr($0,11,6)+0!=NR) print "id sequence " NR }
/^................010005/ { if (substr($0,57,76)!~/^ *$/) print "interest desc tail " NR }' "$T_")
[ -z "$out" ] || bad "tran field checks: $out"

# balances: new = input + interest posted + 25.00 for {1,2,4}; 50 is the preserved R1 case (not rewritten)
dec() { awk -v s="$1" 'BEGIN{ l=substr(s,length(s)); d=substr(s,1,length(s)-1);
  m="{ABCDEFGHI"; n="}JKLMNOPQR"; if((i=index(m,l))){v=d (i-1);sg=1} else {i=index(n,l);v=d (i-1);sg=-1}
  printf "%d", sg*v }'; }
for n in 1 2 4 50; do
  id=$(printf %011d "$n")
  in=$(dec "$(grep "^$id" "$S/input/ACCTFILE.txt" | cut -c13-24)")
  new=$(dec "$(grep "^$id" "$O/ACCTFILE.txt" | cut -c13-24)")
  int=0
  for a in $(grep "^................010005System    Int. for a/c $id " "$T_" | cut -c133-143); do int=$((int + $(dec "$a"))); done
  if [ "$n" -eq 50 ]; then want=$in   # R1: last account never rewritten (fee tran written, balance not posted)
  else want=$((in + int + 2500)); fi
  [ "$new" -eq "$want" ] || bad "acct $id balance $new, want $want"
  if [ "$n" -ne 50 ]; then
    [ "$(grep "^$id" "$O/ACCTFILE.txt" | cut -c79-102)" = "00000000000{00000000000{" ] || bad "acct $id cycle credit/debit not 0"
  fi
done
# acct 1 must have >=2 non-zero-rate records, acct 4 only a zero-rate one
[ "$(grep -c '^................010005System    Int. for a/c 00000000001 ' "$T_")" -ge 2 ] || bad "acct 1 needs >=2 interest trans"
[ "$(grep -c '^................010005System    Int. for a/c 00000000004 ' "$T_")" -eq 0 ] || bad "acct 4 should have no interest tran"

# everyone else matches the unmodified program (baseline/)
X='^00000000001|^00000000002|^00000000004|^00000000050'
diff <(grep -v -E "$X" "$S/baseline/ACCTFILE.txt") <(grep -v -E "$X" "$O/ACCTFILE.txt") >/dev/null || bad "other accounts differ from baseline"
# acct 50 is the last account (R1, preserved): identical to baseline, i.e. neither interest nor fee is posted
diff <(grep '^00000000050' "$S/baseline/ACCTFILE.txt") <(grep '^00000000050' "$O/ACCTFILE.txt") >/dev/null || bad "acct 50 differs from baseline"
# interest trans: same as baseline apart from the tran id
diff <(cut -c17- "$S/baseline/TRANSACT.txt") <(grep -v '^................010006' "$T_" | cut -c17-) >/dev/null || bad "interest trans differ from baseline"

# trancatg.txt
TC=$ROOT/app/data/ASCII/trancatg.txt
[ "$(wc -l < "$TC")" -eq 19 ] || bad "trancatg.txt not 19 lines"
grep -aqE "^010006Over-limit Fee {36}0000"$'\r'"$" "$TC" || bad "trancatg.txt missing 010006 line"

[ $fail -eq 0 ] && echo "PASS: cr2-overlimit" || { echo "FAILED"; exit 1; }
