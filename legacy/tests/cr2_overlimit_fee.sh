#!/usr/bin/env bash
# CR-2: over-limit fee in CBACT04C. Run from the repo root.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
T=$ROOT/legacy/tests/cr2_overlimit_fee
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
FAIL=0
fail() { echo "FAIL: $*"; FAIL=1; }
ok()   { echo "ok:   $*"; }

INPUT_DIR=$T/input "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1 || fail "run.sh exited non-zero"
[[ $(<"$OUT/RC.txt") == 0 ]] && ok "RC 0" || fail "RC is $(<"$OUT/RC.txt")"

# Whole-output regression against the reviewed expected files
for f in TRANSACT ACCTFILE RC; do
  cmp -s "$OUT/$f.txt" "$T/expected/$f.txt" && ok "$f.txt identical to expected" || fail "$f.txt differs from expected"
done

TR=$OUT/TRANSACT.txt
over="00000000001 00000000002 00000000004 00000000005"
none="00000000003 00000000006"

# AC2/3/5: exactly one fee per over-limit account; none for others (incl. balance == limit, acct 3)
for a in $over; do
  n=$(awk -v a="$a" 'substr($0,17,6)=="010006" && substr($0,33,100) ~ ("^Over-limit fee for a/c " a " *$"){c++} END{print c+0}' "$TR")
  [[ $n == 1 ]] && ok "acct $a: 1 fee" || fail "acct $a: $n fees"
done
for a in $none; do
  n=$(awk -v a="$a" 'index(substr($0,33,100),"Over-limit fee for a/c " a){c++} END{print c+0}' "$TR")
  [[ $n == 0 ]] && ok "acct $a: no fee" || fail "acct $a: $n fees"
done
tot=$(grep -c '^................010006' "$TR")
[[ $tot == 4 ]] && ok "4 fee records in total" || fail "fee records: $tot"

# field bytes: source, amount, card (must equal the card of the same account's interest/xref)
while IFS= read -r l; do
  [[ ${l:22:10} == "System    " ]] || fail "source: '${l:22:10}'"
  [[ ${l:132:11} == "0000000250{" ]] || fail "amount: '${l:132:11}'"
  acct=${l:55:11}
  card=$(awk -v a="$acct" 'substr($0,26,11)==a{print substr($0,1,16)}' "$ROOT/app/data/ASCII/cardxref.txt")
  [[ ${l:262:16} == "$card" ]] || fail "card for $acct: '${l:262:16}' vs '$card'"
done < <(grep '^................010006' "$TR")
ok "fee source/amount/card bytes checked"

# AC7: ids unique and 1..N
exp=$(seq -f '2022071800%06g' 1 "$(wc -l <"$TR")")
[[ $(cut -c1-16 "$TR") == "$exp" ]] && ok "tran ids sequential 1..N, unique" || fail "tran ids not 1..N"

# AC4: new balance = old + interest + fee (accounts 1-5; none is the last account)
dec() { # 12-char overpunch -> cents
  local s=$1 last=${1: -1} d sign=
  case $last in [{A-I]) sign=;; *) sign=-;; esac
  case $last in '{'|'}') d=0;; [A-I]) d=$(( $(printf '%d' "'$last") - 64 ));; [J-R]) d=$(( $(printf '%d' "'$last") - 73 ));; esac
  echo "${sign}$((10#${s:0:${#s}-1}))$d" | sed 's/^-0*\([0-9]\)/-\1/'
}
for a in 00000000001 00000000002 00000000003 00000000004 00000000005; do
  old=$(dec "$(awk -v a="$a" 'substr($0,1,11)==a{print substr($0,13,12)}' "$T/input/ACCTFILE.txt")")
  new=$(dec "$(awk -v a="$a" 'substr($0,1,11)==a{print substr($0,13,12)}' "$OUT/ACCTFILE.txt")")
  int=0
  while IFS= read -r amt; do int=$((int + $(dec "$amt"))); done < <(awk -v a="$a" 'substr($0,17,6)=="010005" && index($0,"a/c " a){print substr($0,133,11)}' "$TR")
  fee=0; [[ " $over " == *" $a "* ]] && fee=2500
  [[ $new == $((old + int + fee)) ]] && ok "acct $a balance $old + $int + $fee = $new" || fail "acct $a balance: $old + $int + $fee != $new"
done

# AC6: interest records equal the baseline (program without the fee) except tran id
diff <(grep -v '^................010006' "$TR" | cut -c17-) <(cut -c17- "$T/expected/BASELINE_TRANSACT.txt") >/dev/null \
  && ok "interest records identical to baseline apart from tran id" || fail "interest records differ from baseline"

# AC8: trancatg.txt
f=$ROOT/app/data/ASCII/trancatg.txt
[[ $(wc -l <"$f") == 19 ]] && ok "trancatg.txt 19 records" || fail "trancatg.txt line count $(wc -l <"$f")"
l=$(grep '^010006' "$f")
[[ ${#l} == 61 && $l == $'010006Over-limit Fee                                    0000\r' ]] \
  && ok "010006 record is 60 bytes + CRLF" || fail "010006 record malformed"

exit $FAIL
