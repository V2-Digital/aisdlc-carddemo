#!/usr/bin/env bash
# CR-2: over-limit fee in CBACT04C. Run from the repo root.
# Fixtures: accts 1,2 over limit (1 has two TCATBALF records, 2 has a 0% rate),
# 3 exactly at limit, 4 within limit (last account: balance not rewritten, known defect F4).
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FIX=$ROOT/legacy/tests/data/cr2_overlimit_fee
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
fail=0
check() { # name expected actual
  if [[ "$2" == "$3" ]]; then echo "PASS: $1"; else echo "FAIL: $1"; echo "  expected: [$2]"; echo "  actual:   [$3]"; fail=1; fi
}

INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>&1
check "RC 0" "0" "$(tr -d '[:space:]' < "$OUT/RC.txt")"

T=$OUT/TRANSACT.txt
check "6 transactions" "6" "$(wc -l < "$T" | tr -d ' ')"
for a in 1 2 3 4; do
  acct=$(printf '%011d' "$a")
  n=$(grep -c "^.\{16\}010006System    Over-limit fee for a/c $acct" "$T")
  want=0; [[ $a -le 2 ]] && want=1
  check "fee count acct $a" "$want" "$n"
done
check "no cat-0006 record besides the 2 fees" "2" "$(cut -c17-22 "$T" | grep -c '^010006$')"

# Fee record layout, cols 1-262 exact (ID, type/cat, source, desc, amount, merchant fields)
fee_expect() { # id acct
  printf '%s010006System    %-100s0000000250{000000000%-50s%-50s%-10s' "$1" "Over-limit fee for a/c $2" "" "" ""
}
check "fee layout acct 1" "$(fee_expect 2022071800000001 00000000001)" "$(sed -n 1p "$T" | cut -c1-262)"
check "fee layout acct 2" "$(fee_expect 2022071800000004 00000000002)" "$(sed -n 4p "$T" | cut -c1-262)"
check "fee card acct 1" "9680294154603697" "$(sed -n 1p "$T" | cut -c263-278)"
check "fee card acct 2" "0923877193247330" "$(sed -n 4p "$T" | cut -c263-278)"
check "fee timestamps = interest timestamps" "$(sed -n 2p "$T" | cut -c279-330)" "$(sed -n 1p "$T" | cut -c279-330)"
check "fee trailing filler blank" "" "$(sed -n 1p "$T" | cut -c331-350 | tr -d ' ')"
check "line length 350" "350" "$(sed -n 1p "$T" | tr -d '\n' | wc -c | tr -d ' ')"
# Interest descriptions are not polluted by the longer fee description (F3)
check "interest desc clean after fee" "Int. for a/c 00000000001$(printf '%76s' '')" "$(sed -n 2p "$T" | cut -c33-132)"
# Contiguous IDs
check "IDs contiguous" "$(printf '20220718%08d\n' 1 2 3 4 5 6)" "$(cut -c1-16 "$T")"

# Balances (cols 13-24): old + interest + fee. Acct 4 = last account, not rewritten (F4, not asserted).
A=$OUT/ACCTFILE.txt
check "acct 1 balance 1500.00+10+10+25" "00000015450{" "$(sed -n 1p "$A" | cut -c13-24)"
check "acct 2 balance 300.00+0+25"      "00000003250{" "$(sed -n 2p "$A" | cut -c13-24)"
check "acct 3 balance 500.00+20 (at limit, no fee)" "00000005200{" "$(sed -n 3p "$A" | cut -c13-24)"

# trancatg.txt
C=$ROOT/app/data/ASCII/trancatg.txt
check "trancatg 19 lines" "19" "$(wc -l < "$C" | tr -d ' ')"
check "trancatg new line (6th)" "010006Over-limit Fee$(printf '%36s' '')0000" "$(sed -n 6p "$C" | tr -d '\r')"
check "trancatg line 6 after 010005" "010005" "$(sed -n 5p "$C" | cut -c1-6)"
check "trancatg line 6 CRLF, 62 bytes" "62" "$(sed -n 6p "$C" | wc -c | tr -d ' ')"
check "trancatg line 6 ends CR" "0d" "$(sed -n 6p "$C" | tail -c2 | head -c1 | od -An -tx1 | tr -d ' ')"

exit $fail
