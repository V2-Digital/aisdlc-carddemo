#!/usr/bin/env bash
# Edge-case characterization of CBACT04C under GnuCOBOL (overflow, -0, negatives, abends).
#   legacy/tests/cbact04c-edge.sh            check: run COBOL on the stored inputs, diff vs stored outputs
#   legacy/tests/cbact04c-edge.sh --update   regenerate inputs and outputs (review the diff!)
# Fixtures: legacy/tests/cbact04c-edge/<case>/{in,expected}/. The Java tests in
# modernized/cbact04c compare their output with the same expected/ files.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-edge
MODE=${1:---check}

pad() { printf '%-*s' "$1" "$2"; }
# account record (300): id, status, bal(12), limits, dates, cycle credit/debit, zip, group(blank)
account() { pad 300 "$1Y$2$(printf '%012d%012d' 1000 1000)2020-01-012030-01-012020-01-01$(printf '%012d%012d' 0 0)"; echo; }
account_cyc() { pad 300 "$1Y$2$(printf '%012d%012d' 1000 1000)2020-01-012030-01-012020-01-01${3}${4}"; echo; }
tcat() { pad 50 "$1$2$3$4"; echo; }          # acct type cat bal(11)
xref() { pad 50 "$1$(printf '%9s' '')$2" | sed 's/^\(.\{16\}\).\{9\}/\1000000001/'; }
disc() { pad 50 "$1$2$3$4"; echo; }          # group(10) type cat rate(6)

DEF=$(pad 10 DEFAULT)

gen() { # case-name; reads globals T X A D
  local d=$FIX/$1/in; mkdir -p "$d"
  printf '%s' "$T" >"$d/TCATBALF.txt"; printf '%s' "$X" >"$d/XREFFILE.txt"
  printf '%s' "$A" >"$d/ACCTFILE.txt"; printf '%s' "$D" >"$d/DISCGRP.txt"
}
xr() { pad 50 "${1}000000001$2" ; echo; }    # card(16) cust(9) acct(11)
std_x() { xr 4111111111111111 00000000001; xr 4222222222222222 00000000002; }
std_d() { disc "$DEF" 01 0001 00150{; }
A2="$(account 00000000001 00000000100{; account 00000000002 00000000100{)"

if [[ $MODE == --update ]]; then
  # overflow: 999999999.99 * 9999.99 / 1200 overflows S9(9)V99; balance 9999999999.99 overflows S9(10)V99
  T="$(tcat 00000000001 01 0001 9999999999I; tcat 00000000002 01 0001 0000000000{)"
  X="$(std_x)"; D="$(disc "$DEF" 01 0001 99999I)"
  A="$(account 00000000001 99999999999I; account 00000000002 00000000100{)"; gen overflow
  # negzero: -0.01 * 15% / 12 = -0.000125 -> truncates to zero; what sign is written?
  T="$(tcat 00000000001 01 0001 0000000000J; tcat 00000000002 01 0001 0000000000{)"
  D="$(std_d)"; A="$A2"; gen negzero
  # negative: -2339.97 -> -29.24 (toward zero); account balance goes down
  T="$(tcat 00000000001 01 0001 0000023399P; tcat 00000000002 01 0001 0000000000{)"; gen negative
  # negative rate
  T="$(tcat 00000000001 01 0001 0000023399G; tcat 00000000002 01 0001 0000000000{)"
  D="$(disc "$DEF" 01 0001 00150})"; gen negrate
  # missing-acct: TCATBAL account absent from ACCTFILE
  T="$(tcat 00000000009 01 0001 0000023399G)"; D="$(std_d)"; A="$A2"; gen missing-acct
  # missing-xref
  T="$(tcat 00000000001 01 0001 0000023399G; tcat 00000000002 01 0001 0000000000{)"
  X="$(xr 4111111111111111 00000000002)"; gen missing-xref
  # missing-default
  X="$(std_x)"; D="$(disc "$(pad 10 ZEROAPR)" 01 0001 00000{)"; gen missing-default
  # empty TCATBALF
  T=""; D="$(std_d)"; gen empty
fi

rc_all=0
for c in "$FIX"/*/; do
  c=${c%/}; name=$(basename "$c")
  out=$(mktemp -d)
  INPUT_DIR=$c/in "$ROOT/legacy/harness/run.sh" CBACT04C "$out" >/dev/null 2>"$out/stderr"
  if [[ $MODE == --update ]]; then
    rm -rf "$c/expected"; mkdir -p "$c/expected"
    for f in "$out"/*.txt; do cp "$f" "$c/expected/"; done
    grep -q 'CEE3ABD: ABEND U0999' "$out/stderr" && echo "ABEND U0999" >"$c/expected/STDERR.txt"
    echo "updated $name (RC $(cat "$out/RC.txt"))"
  else
    ok=1
    for f in "$c"/expected/*.txt; do
      b=$(basename "$f")
      if [[ $b == STDERR.txt ]]; then
        grep -q 'CEE3ABD: ABEND U0999' "$out/stderr" || ok=0
      else
        cmp -s "$f" "$out/$b" || { ok=0; echo "  differs: $name/$b"; }
      fi
    done
    if (( ok )); then echo "PASS $name"; else echo "FAIL $name"; rc_all=1; fi
  fi
  rm -rf "$out"
done
exit $rc_all
