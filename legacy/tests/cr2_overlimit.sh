#!/usr/bin/env bash
# CR-2: over-limit fee in CBACT04C. Run from repo root.
set -u
cd "$(dirname "$0")/../.."
T=legacy/tests/cr2_overlimit
G=legacy/golden/CBACT04C-after-CBTRN02C
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
fail=0
ok()  { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; fail=1; }
chk() { local m=$1; shift; if "$@" >/dev/null 2>&1; then ok "$m"; else bad "$m"; fi; }

for n in 1 2; do
  INPUT_DIR=$T/input legacy/harness/run.sh CBACT04C "$W/r$n" >"$W/log$n" 2>&1 || bad "run $n"
done
chk "RC 0" test "$(tr -d ' \n' <"$W/r1/RC.txt")" = 0
chk "two runs byte-identical" diff -r "$W/r1" "$W/r2"
chk "matches expected/" diff -r "$W/r1" $T/expected

TX=$W/r1/TRANSACT.txt; AC=$W/r1/ACCTFILE.txt
chk "exactly 3 fee records" test "$(cut -c17-22 "$TX" | grep -c '^010006$')" = 3
chk "fees only for accts 1,2,4" test "$(awk 'substr($0,17,6)=="010006"{printf "%s ",substr($0,56,11)}' "$TX")" = "00000000001 00000000002 00000000004 "
chk "one fee per account (no dup)" test "$(awk 'substr($0,17,6)=="010006"{print substr($0,56,11)}' "$TX" | sort | uniq -d | wc -l)" = 0
chk "acct 3 (bal=limit) no fee, acct 5.. none" test "$(awk 'substr($0,17,6)=="010006" && substr($0,56,11)>"00000000004"' "$TX" | wc -l)" = 0
# byte-exact fee record for acct 1
fee=$(awk 'substr($0,17,6)=="010006" && substr($0,56,11)=="00000000001"' "$TX")
chk "fee rec length 350" test "${#fee}" = 350
chk "fee fields (src/desc/amt/merchant)" test \
  "${fee:22:10}|${fee:32:100}|${fee:132:11}|${fee:143:9}|${fee:152:110}" = \
  "$(printf 'System    |%-100s|0000000250{|000000000|%110s' 'Over-limit fee for a/c 00000000001' '')"
chk "fee TRAN-ID prefix 2022071800" test "${fee:0:10}" = 2022071800
# TRAN-IDs contiguous 1..52
chk "TRAN-IDs contiguous 000001..000052" test "$(cut -c11-16 "$TX" | paste -sd,)" = "$(seq -f '%06g' 1 52 | paste -sd,)"
chk "52 transactions" test "$(wc -l <"$TX")" = 52
# interest rows identical to golden ignoring TRAN-ID, minus acct 4 (zero rate)
strip() { cut -c17-262,263-277 | cut -c1-246; }
chk "interest rows == golden (minus acct 4, ignoring ids/ts)" diff \
  <(grep -v '^.\{16\}010006' "$TX" | cut -c17-277) \
  <(grep -v 'a/c 00000000004 ' $G/TRANSACT.txt | cut -c17-277)
chk "acct 4 gets no interest tx" test "$(grep -c 'Int. for a/c 00000000004 ' "$TX")" = 0
# ACCTFILE: balances
bal() { sed -n "$2p" "$1" | cut -c13-24; }
# golden bal + 25.00 = +2500 -> compare via digit arithmetic on overpunch-free rendering
num() { # zoned S9(10)V99 -> integer cents
  local s=$1 d=${1:0:11} l=${1:11:1} sign=+ dig
  case $l in
    '{') dig=0;; A) dig=1;; B) dig=2;; C) dig=3;; D) dig=4;; E) dig=5;; F) dig=6;; G) dig=7;; H) dig=8;; I) dig=9;;
    '}') dig=0; sign=-;; J) dig=1; sign=-;; K) dig=2; sign=-;; L) dig=3; sign=-;; M) dig=4; sign=-;; N) dig=5; sign=-;;
    O) dig=6; sign=-;; P) dig=7; sign=-;; Q) dig=8; sign=-;; R) dig=9; sign=-;;
  esac
  echo "${sign}$((10#$d))$dig" | sed 's/^+//;s/^-\(.*\)/-\1/' | awk '{print $0+0}'
}
for i in 1 2 4; do
  g=$(num "$(bal $G/ACCTFILE.txt $i)"); a=$(num "$(bal "$AC" $i)")
  if [ $i = 4 ]; then g=$(num "$(bal $T/input/ACCTFILE.txt 4)"); fi
  chk "acct $i balance = base + 25.00" test $((a-g)) = 2500
done
chk "cycle fields zeroed accts 1,2,4" test "$(sed -n '1p;2p;4p' "$AC" | cut -c79-102 | sort -u)" = "00000000000{00000000000{"
chk "other accts byte-equal golden (3,5..49)" diff \
  <(sed -n '3p;5,49p' "$AC" | cut -c1-24,49-) <(sed -n '3p;5,49p' $G/ACCTFILE.txt | cut -c1-24,49-)
chk "acct 3 balance equals golden" test "$(bal "$AC" 3)" = "$(bal $G/ACCTFILE.txt 3)"
chk "last acct 50 unchanged (preserved defect F3)" test "$(sed -n 50p "$AC")" = "$(sed -n 50p $T/input/ACCTFILE.txt)"
# trancatg
F=app/data/ASCII/trancatg.txt
chk "trancatg 19 rows x 60 bytes + CRLF" test "$(wc -l <$F) $(awk '{print length($0)}' $F | sort -u | tr '\n' ' ')" = "19 61 "
chk "trancatg row 6 = new row" test "$(sed -n 6p $F | tr -d '\r')" = "010006$(printf '%-50s' 'Over-limit Fee')0000"
chk "trancatg CRLF endings" test "$(grep -c $'\r$' $F)" = 19
exit $fail
