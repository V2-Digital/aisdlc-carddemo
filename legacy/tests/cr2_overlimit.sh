#!/usr/bin/env bash
# CR-2 scenario: over-limit fee in CBACT04C. Exit non-zero on any failure.
set -u
root=$(cd "$(dirname "$0")/../.." && pwd)
t=$root/legacy/tests/cr2_overlimit
out=$(mktemp -d); fail=0
chk() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2' want '$3'"; fail=1; fi; }
INPUT_DIR=$t/input "$root/legacy/harness/run.sh" CBACT04C "$out" >/dev/null 2>&1
for f in TRANSACT ACCTFILE RC; do
  diff -q "$out/$f.txt" "$t/expected/$f.txt" >/dev/null; chk "expected/$f.txt identical" $? 0
done
tx=$out/TRANSACT.txt
fee() { awk -v a="$1" 'substr($0,17,6)=="010006" && substr($0,23,6)=="System" && index($0,"Over-limit fee for a/c " a)' "$tx"; }
for a in 00000000001 00000000004 00000000005; do
  chk "acct $a: one fee record" "$(fee $a | wc -l)" 1
  chk "acct $a: amount field" "$(fee $a | cut -c1-350 | grep -c '0000000250{')" 1
done
for a in 00000000002 00000000003; do chk "acct $a: no fee" "$(fee $a | wc -l)" 0; done
chk "TRAN-ID suffixes contiguous 1..N" "$(cut -c11-16 "$tx" | tr '\n' ' ')" "$(seq -f '%06g' 1 "$(wc -l <"$tx")" | tr '\n' ' ')"
chk "acct 01 balance = in + interest + 25.00" "$(sed -n 1p "$out/ACCTFILE.txt" | cut -c13-24)" "00000010525{"
chk "acct 05 not rewritten (F3)" "$(sed -n 5p "$out/ACCTFILE.txt")" "$(sed -n 5p "$t/input/ACCTFILE.txt")"
tc=$root/app/data/ASCII/trancatg.txt
chk "trancatg 19 lines" "$(wc -l <"$tc")" 19
chk "trancatg line 6" "$(sed -n 6p "$tc" | tr -d '\r')" "010006$(printf '%-50s' 'Over-limit Fee')0000"
rm -rf "$out"; exit $fail
