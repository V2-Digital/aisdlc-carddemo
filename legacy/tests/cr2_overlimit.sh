#!/usr/bin/env bash
# CR-2 test: over-limit fee in CBACT04C. Run from the repo root.
# Over limit: accounts 1 (2 TCATBAL records), 2, 4. Equal to limit: 3. Within: 5, rest.
set -u
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cr2_overlimit
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
fail=0
check() { # name, condition result (0=ok)
  if [[ $2 -eq 0 ]]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi
}

# fixtures sanity: TCATBAL sorted by acct+type+cat
LC_ALL=C sort -c -k1.1,1.17 "$FIX/TCATBALF.txt" 2>/dev/null; check "fixture TCATBALF key-ordered" $?

INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1
check "AC6 RC is 0" $([[ $(cat "$OUT/RC.txt") == 0 ]]; echo $?)
T=$OUT/TRANSACT.txt; A=$OUT/ACCTFILE.txt

# AC2/3: exactly one fee per over-limit account, correct fields
for id in 00000000001 00000000002 00000000004; do
  n=$(awk -v d="Over-limit fee for a/c $id" 'substr($0,17,6)=="010006" && substr($0,33,34)==d' "$T" | wc -l)
  check "AC2/3 acct $id: one fee record" $([[ $n -eq 1 ]]; echo $?)
  rec=$(awk -v d="Over-limit fee for a/c $id" 'substr($0,17,6)=="010006" && substr($0,33,34)==d' "$T" | head -1)
  card=$(awk -v a="$id" 'substr($0,26,11)==a{print substr($0,1,16)}' "$ROOT/app/data/ASCII/cardxref.txt")
  ok=1
  [[ ${rec:22:10} == "System    " ]] || ok=0
  [[ ${rec:32:100} == "Over-limit fee for a/c $id$(printf '%66s' '')" ]] || ok=0   # F4: tail cleared
  [[ ${rec:132:11} == "0000000250{" ]] || ok=0
  [[ ${rec:262:16} == "$card" ]] || ok=0
  check "AC2 acct $id: fee fields + card $card" $((1-ok))
done
# AC5: no fee for others, total fees = 3
n=$(awk 'substr($0,17,6)=="010006"' "$T" | wc -l)
check "AC5 only 3 fee records in total (got $n)" $([[ $n -eq 3 ]]; echo $?)
n=$(awk 'substr($0,17,6)=="010006" && (substr($0,33,34) ~ /0000000000[35]$/)' "$T" | wc -l)
check "AC5 equal-limit (3) and within-limit (5) accounts: no fee" $([[ $n -eq 0 ]]; echo $?)

# AC5: interest records identical to the baseline program (all but ID/timestamps)
diff <(awk 'substr($0,17,6)!="010006"{print substr($0,17,246)}' "$T") \
     <(awk '{print substr($0,17,246)}' "$FIX/baseline_TRANSACT.txt") >/dev/null
check "AC5 interest records == baseline (cols 17-262)" $?

# AC4/5: ACCTFILE vs baseline: +25.00 only on over-limit accounts 1,2,4 (+ cycle fields 0)
awk -v base="$FIX/baseline_ACCTFILE.txt" '
function num(s,  l,d,v,p) { l=substr(s,length(s),1); d=substr(s,1,length(s)-1)
  p=index("{ABCDEFGHI",l); if(p){v=d (p-1); return v+0}
  p=index("}JKLMNOPQR",l); return -(d (p-1)) }
BEGIN{ while((getline l < base)>0) b[substr(l,1,11)]=l }
{ id=substr($0,1,11); n=num(substr($0,13,12)); m=num(substr($0,13,12)) ; bb=num(substr(b[id],13,12))
  want=(id=="00000000001"||id=="00000000002"||id=="00000000004")?2500:0
  if(n-bb!=want){print "bad balance " id " " n " vs " bb; bad=1}
  if(want==0 && $0!=b[id]){print "changed record " id; bad=1}
  if(want>0 && substr($0,37)!=substr(b[id],37)){print "other fields changed " id; bad=1} }
END{exit bad}' "$A"
check "AC4/5 ACCTFILE: bal = baseline + 25.00 for over-limit only, rest identical" $?
check "AC4 over-limit cycle credit/debit zero" $([[ $(awk 'substr($0,1,11)=="00000000001"{print substr($0,79,24)}' "$A") == "00000000000{00000000000{" ]]; echo $?)

# AC6: TRAN-ID suffixes contiguous 000001..N, unique
N=$(wc -l <"$T")
diff <(cut -c1-16 "$T") <(awk -v n="$N" 'BEGIN{for(i=1;i<=n;i++)printf "2022071800%06d\n",i}') >/dev/null
check "AC6 TRAN-IDs contiguous, unique ($N records)" $?

# AC7: trancatg.txt
C=$ROOT/app/data/ASCII/trancatg.txt
check "AC7 19 records" $([[ $(wc -l <"$C") -eq 19 ]]; echo $?)
check "AC7 all lines 60 bytes + CRLF" $([[ $(awk '{print length($0)}' "$C" | sort -u | tr '\n' ' ') == "61 " ]]; echo $?)
exp=$(printf '010006%-50s0000\r' 'Over-limit Fee')
check "AC7 line 6 is the new category" $([[ $(sed -n 6p "$C") == "$exp" ]]; echo $?)
check "AC7 only that line added vs baseline" $([[ $(git -C "$ROOT" diff baseline --numstat -- app/data/ASCII/trancatg.txt | cut -f1,2) == $'1\t0' ]]; echo $?)

exit $fail
